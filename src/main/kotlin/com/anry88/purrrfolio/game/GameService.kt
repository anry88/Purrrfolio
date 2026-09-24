package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.collection.CollectionCompletionRewardService
import com.anry88.purrrfolio.collection.ThemeProgress
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.craft.CraftPolicy
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import com.anry88.purrrfolio.models.GroupRaffleCandidate
import com.anry88.purrrfolio.models.MarketListing
import com.anry88.purrrfolio.models.PaymentStatus
import com.anry88.purrrfolio.models.PaymentSupportStatus
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.models.UserCard
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.GroupRaffleRepository
import com.anry88.purrrfolio.repository.MarketRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import com.anry88.purrrfolio.repository.PaymentSupportRepository
import com.anry88.purrrfolio.repository.ProcessedUpdateRepository
import com.anry88.purrrfolio.repository.RandomTradeRepository
import com.anry88.purrrfolio.repository.TelegramCardFileRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramCallbackQuery
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramInlineQuery
import com.anry88.purrrfolio.telegram.TelegramInlineQueryResultCachedPhoto
import com.anry88.purrrfolio.telegram.TelegramKeyboardButton
import com.anry88.purrrfolio.telegram.TelegramLabeledPrice
import com.anry88.purrrfolio.telegram.TelegramMessage
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import com.anry88.purrrfolio.telegram.TelegramUpdate
import com.anry88.purrrfolio.trade.TradePolicy
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RetryableTelegramUpdateException(cause: Throwable) : RuntimeException(cause)

@Service
class GameService(
    private val properties: PurrrfolioProperties,
    private val cardCatalog: CardCatalog,
    private val collectionService: CollectionService,
    private val collectionCompletionRewardService: CollectionCompletionRewardService,
    private val packOpeningService: PackOpeningService,
    private val packOpeningTransactionService: PackOpeningTransactionService,
    private val playerRegistrationService: PlayerRegistrationService,
    private val cardShareLinkService: CardShareLinkService,
    private val userRepository: UserRepository,
    private val userCardRepository: UserCardRepository,
    private val packLedgerRepository: PackLedgerRepository,
    private val telegramCardFileRepository: TelegramCardFileRepository,
    private val marketRepository: MarketRepository,
    private val randomTradeRepository: RandomTradeRepository,
    private val groupRaffleRepository: GroupRaffleRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentSupportRepository: PaymentSupportRepository,
    private val processedUpdateRepository: ProcessedUpdateRepository,
    private val starsPurchaseService: StarsPurchaseService,
    private val starsRefundService: StarsRefundService,
    private val gameMetrics: GameMetrics,
    private val telegramClient: TelegramClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    private sealed interface GalleryKey {
        data object Collection : GalleryKey
        data class Theme(val themeId: String) : GalleryKey
    }

    private data class ChatGallery(
        val messageId: Long,
        val fileId: String,
        val key: GalleryKey,
        val index: Int,
    )

    private val chatGalleries = ConcurrentHashMap<Long, ChatGallery>()
    private val knownFileIds = ConcurrentHashMap<String, String>()
    /** chatId -> target market listing the user is picking an offer for (keeps callbacks <= 64 bytes). */
    private val pendingMarketOffers = ConcurrentHashMap<Long, UUID>()

    companion object {
        const val COLLECTIONS_PAGE_SIZE = 10
        const val STARS_CURRENCY = "XTR"
        const val SUPPORT_TEXT_LIMIT = 1_000

        fun packsForStars(stars: Int): Int? = when (stars) {
            5 -> 1
            12 -> 3
            16 -> 5
            25 -> 10
            else -> null
        }

        fun starsForPacks(packs: Int): Int? = when (packs) {
            1 -> 5
            3 -> 12
            5 -> 16
            10 -> 25
            else -> null
        }

        data class StarsOrder(val packs: Int, val stars: Int, val buyerTelegramId: Long)

        fun starsPayload(packs: Int, stars: Int, buyerTelegramId: Long, signingSecret: String): String {
            require(signingSecret.isNotBlank()) { "Stars payload signing secret must be configured" }
            val order = "purrrfolio:packs:$packs:stars:$stars:user:$buyerTelegramId"
            return "$order:sig:${starsPayloadSignature(order, signingSecret)}"
        }

        fun parseStarsOrder(payload: String?, signingSecret: String): StarsOrder? {
            if (signingSecret.isBlank()) return null
            val parts = payload?.split(':') ?: return null
            if (
                parts.size != 9 || parts[0] != "purrrfolio" || parts[1] != "packs" ||
                parts[3] != "stars" || parts[5] != "user" || parts[7] != "sig"
            ) return null
            val unsignedOrder = parts.take(7).joinToString(":")
            val expectedSignature = starsPayloadSignature(unsignedOrder, signingSecret)
            if (!MessageDigest.isEqual(expectedSignature.toByteArray(), parts[8].toByteArray())) return null
            val packs = parts[2].toIntOrNull()?.takeIf { starsForPacks(it) != null } ?: return null
            val stars = parts[4].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val buyerId = parts[6].toLongOrNull() ?: return null
            return StarsOrder(packs, stars, buyerId)
        }

        fun packsFromPayload(payload: String?, signingSecret: String): Int? =
            parseStarsOrder(payload, signingSecret)?.packs

        private fun starsPayloadSignature(order: String, signingSecret: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(order.toByteArray(Charsets.UTF_8))
                .take(16)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        fun matchesStarsPayment(
            order: StarsOrder?,
            buyerTelegramId: Long?,
            currency: String?,
            totalAmount: Int?,
        ): Boolean = order != null && order.buyerTelegramId == buyerTelegramId &&
            currency == STARS_CURRENCY && order.stars == totalAmount

        fun matchesPricedStarsPayment(
            order: StarsOrder?,
            buyerTelegramId: Long?,
            currency: String?,
            totalAmount: Int?,
            expectedStars: Int?,
        ): Boolean = expectedStars != null && order?.stars == expectedStars &&
            matchesStarsPayment(order, buyerTelegramId, currency, totalAmount)

        /** Whole minutes until the next free card; 0 means it is due now. */
        fun minutesUntilFreeCard(lastFree: OffsetDateTime?, now: OffsetDateTime, intervalHours: Int): Long {
            if (lastFree == null) return 0
            val nextClaim = lastFree.plusHours(intervalHours.toLong())
            if (!nextClaim.isAfter(now)) return 0
            return (Duration.between(now, nextClaim).seconds + 59) / 60
        }

        const val RAFFLE_COOLDOWN_HOURS = 24L
        const val RAFFLE_MIN_MEMBERS = 10
        const val RAFFLE_MEMBERS_PER_PACK = 10
        const val RAFFLE_MAX_PACKS = 10

        /** Packs for a group raffle by member count: 1 per 10 members, capped at 10. */
        fun packsForRaffle(memberCount: Int): Int =
            (memberCount / RAFFLE_MEMBERS_PER_PACK).coerceIn(0, RAFFLE_MAX_PACKS)

        /** True when the chat never had a raffle or the last one is older than 24h. */
        fun isRaffleDue(lastRaffleAt: OffsetDateTime?, now: OffsetDateTime): Boolean {
            if (lastRaffleAt == null) return true
            return !lastRaffleAt.plusHours(RAFFLE_COOLDOWN_HOURS).isAfter(now)
        }

        /** Strip Markdown-breaking characters so winner mentions never break message parsing. */
        fun sanitizeMentionName(name: String): String =
            name.replace(Regex("[\\[\\]()_*`#]"), "").trim().replace(Regex("\\s+"), " ")

        fun packsLabel(count: Int, locale: GameLocale): String = when (locale) {
            GameLocale.RU -> "$count " + when {
                count % 10 == 1 && count % 100 != 11 -> "пак"
                count % 10 in 2..4 && count % 100 !in 12..14 -> "пака"
                else -> "паков"
            }
            GameLocale.EN -> if (count == 1) "1 pack" else "$count packs"
        }

        fun textCommandAlias(text: String): String? {
            val normalized = text
                .trim()
                .lowercase()
                .trim { !it.isLetterOrDigit() }
                .replace(Regex("\\s+"), " ")
            return when (normalized) {
                "pack", "card pack", "набор", "пак" -> "pack"
                "craft", "крафт" -> "craft"
                "market", "marketplace", "биржа" -> "market"
                "card", "free card", "cat", "kitty", "kitten",
                "карточка", "бесплатная карточка", "котик", "кот", "котейка",
                "кошка", "котёнок", "котенок" -> "freecard"
                else -> null
            }
        }

        internal fun resolveAction(text: String, locale: GameLocale): Action? {
            val trimmed = text.trim()
            if (trimmed.startsWith("/")) {
                val command = trimmed.substringBefore(' ').substringBefore('@').lowercase()
                return when (command) {
                    "/start" -> Action.START
                    "/help" -> Action.HELP
                    "/language" -> Action.LANGUAGE
                    "/collection" -> Action.COLLECTION
                    "/themes" -> Action.COLLECTION // legacy alias
                    "/pack" -> Action.PACK
                    "/freecard", "/card", "/cat", "/kitty", "/kitten" -> Action.FREECARD
                    "/craft" -> Action.CRAFT
                    "/buy" -> Action.BUY
                    "/paysupport" -> Action.PAYSUPPORT
                    "/answer" -> Action.ANSWER
                    "/trade" -> Action.TRADE
                    "/market" -> Action.MARKET
                    else -> null
                }
            }

            when (textCommandAlias(trimmed)) {
                "pack" -> return Action.PACK
                "freecard" -> return Action.FREECARD
                "craft" -> return Action.CRAFT
                "market" -> return Action.MARKET
            }

            val normalized = trimmed.lowercase().replace(" ", "")
            // The reply-keyboard pack button carries the unopened-pack count: "🎁 Набор (3)".
            val menuText = normalized.replace(Regex("\\(\\d+\\)$"), "")
            fun matches(key: String): Boolean {
                val other = if (locale == GameLocale.RU) GameLocale.EN else GameLocale.RU
                fun norm(l: GameLocale) = Messages.t(key, l).lowercase().replace(" ", "")
                return menuText == norm(locale) || menuText == norm(other)
            }

            return when {
                matches("menu.collection") -> Action.COLLECTION
                matches("menu.pack") -> Action.PACK
                matches("menu.freecard") -> Action.FREECARD
                matches("menu.craft") -> Action.CRAFT
                matches("menu.buy") -> Action.BUY
                matches("menu.trade") -> Action.TRADE
                matches("menu.market") -> Action.MARKET
                matches("menu.language") -> Action.LANGUAGE
                else -> null
            }
        }

        fun isGroupChat(type: String?): Boolean = type == "group" || type == "supergroup"

        fun isAuthorizedPaymentAdmin(adminTgId: Long, chatId: Long, telegramUserId: Long): Boolean =
            adminTgId != 0L && chatId == adminTgId && telegramUserId == adminTgId

        fun isRetryableTelegramClientStatus(status: Int): Boolean = status == 408 || status == 429
    }

    fun handle(update: TelegramUpdate) {
        // Payment updates are independently idempotent by Telegram charge id and must
        // remain retryable if processing fails. Do not pre-mark them as processed.
        update.preCheckoutQuery?.let {
            runRetryable { handlePreCheckout(it.id, it.from?.id, it.invoicePayload, it.currency, it.totalAmount) }
            return
        }
        update.message?.takeIf { it.successfulPayment != null }?.let {
            runRetryable { handleSuccessfulPayment(it) }
            return
        }
        update.message?.takeIf { isPaymentSupportCommand(it.text.orEmpty()) }?.let {
            // Support/refund state transitions are independently idempotent and must
            // remain retryable until their Telegram notifications succeed.
            runRetryable { handleMessage(it) }
            return
        }

        val updateId = update.updateId
        if (updateId != null && !processedUpdateRepository.claimUpdate(updateId)) {
            logger.warn("Skipping processed or currently claimed update {}", updateId)
            return
        }

        try {
            update.callbackQuery?.let {
                it.data?.let { data -> gameMetrics.callback(data) }
                handleCallback(it, updateId)
            } ?: update.inlineQuery?.let(::handleInlineQuery)
                ?: update.message?.let { handleMessage(it, updateId) }

            if (updateId != null && !processedUpdateRepository.markProcessed(updateId)) {
                error("Telegram update $updateId lost its processing claim")
            }
        } catch (e: Exception) {
            if (updateId != null) {
                runCatching { processedUpdateRepository.releaseClaim(updateId) }
                    .onFailure { logger.error("Failed to release Telegram update claim {}", updateId, it) }
            }
            if (e is RetryableTelegramUpdateException) throw e
            throw RetryableTelegramUpdateException(e)
        }
    }

    private fun handleInlineQuery(inlineQuery: TelegramInlineQuery) {
        val queryId = inlineQuery.id ?: return
        val request = cardShareLinkService.parseInlineQuery(inlineQuery.query)
        val telegramUserId = inlineQuery.from?.id
        val user = telegramUserId?.let(userRepository::findByTelegramUserId)
        val result = if (request != null && user?.id == request.ownerUserId) {
            val owned = userCardRepository.findByUserIdAndCardId(user.id, request.cardId)
            val card = owned?.takeIf { it.quantity > 0 }
                ?.let { runCatching { cardCatalog.card(request.cardId) }.getOrNull() }
            val fileId = card?.let { knownTelegramFileId(it.id) }
            if (card != null && fileId != null) {
                val locale = gameLocale(user)
                TelegramInlineQueryResultCachedPhoto(
                    id = cardShareLinkService.inlineResultId(card.id),
                    photoFileId = fileId,
                    title = Messages.t("card.shareResult", locale, card.nameFor(locale)),
                    caption = cardShareLinkService.sharedCaption(card, user.id, locale),
                    replyMarkup = TelegramReplyMarkup(
                        inlineKeyboard = listOf(
                            listOf(
                                TelegramInlineButton(
                                    text = Messages.t("card.startCollection", locale),
                                    url = cardShareLinkService.referralUrl(user.id),
                                ),
                            ),
                        ),
                    ),
                )
            } else {
                null
            }
        } else {
            null
        }
        telegramClient.answerInlineQuery(queryId, listOfNotNull(result))
    }

    private fun runRetryable(action: () -> Unit) {
        try {
            action()
        } catch (e: RetryableTelegramUpdateException) {
            throw e
        } catch (e: HttpClientErrorException) {
            if (isRetryableTelegramClientStatus(e.statusCode.value())) {
                throw RetryableTelegramUpdateException(e)
            }
            logger.error("Terminal Telegram client error; acknowledging update to avoid queue poisoning", e)
        } catch (e: IllegalArgumentException) {
            logger.error("Permanent invalid payment/support input; acknowledging update", e)
        } catch (e: Exception) {
            throw RetryableTelegramUpdateException(e)
        }
    }

    private fun handleMessage(message: TelegramMessage, updateId: Long? = null) {
        val chatId = message.chat?.id ?: return
        val telegramId = message.from?.id ?: return
        val fromGroupChat = isGroupChat(message.chat?.type)
        val text = message.text?.trim().orEmpty()

        if (isAdminCommand(text)) {
            if (isAuthorizedPaymentAdmin(properties.telegram.adminTgId, chatId, telegramId)) {
                handleAdminPaymentCommand(chatId, text)
            } else {
                logger.warn("Rejected payment admin command from chat {} user {}", chatId, telegramId)
            }
            return
        }

        val existingUser = userRepository.findByTelegramUserId(telegramId)
        val action = resolveAction(text, existingUser?.let(::gameLocale) ?: GameLocale.EN) ?: return

        // Auto-register so even a first touch (e.g. "кот" in a group chat)
        // is processed instead of gated behind language selection.
        // Telegram Russian -> RU, anything else -> EN. /language switches any time.
        val registration = existingUser?.let { PlayerRegistrationResult(it, created = false) } ?: run {
            val payload = if (text.startsWith("/start")) text.substringAfter(' ', "").trim() else ""
            val source = GameMetrics.normalizeRegistrationSource(payload.ifEmpty { null })
            val initialLocale = GameLocale.fromTelegramLanguageCode(message.from?.languageCode)
            playerRegistrationService.registerIfMissing(telegramId, initialLocale.code, source).also {
                if (it.created) {
                    gameMetrics.registration(source)
                }
            }
        }
        val user = registration.user

        gameMetrics.command(action.name.lowercase(), if (text.startsWith("/")) "command" else "keyboard")

        if (fromGroupChat) {
            trackGroupMember(chatId, user, message)
        }

        when (action) {
            Action.START -> {
                if (registration.created) {
                    telegramClient.sendMessage(
                        chatId,
                        Messages.t("welcome.new", gameLocale(user)),
                        mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
                    )
                    telegramClient.sendMessage(
                        chatId,
                        Messages.t("pack.starter", gameLocale(user), properties.economy.starterPacks),
                        openPackKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
                    )
                    registration.referralReward?.let { reward ->
                        sendReferralRewardMessages(chatId, user, reward)
                    }
                } else {
                    telegramClient.sendMessage(chatId, Messages.t("welcome", gameLocale(user)), mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
                }
            }
            Action.HELP -> telegramClient.sendMessage(chatId, Messages.t("help", gameLocale(user)), helpKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
            Action.LANGUAGE -> handleLanguage(chatId, user)
            Action.COLLECTION -> sendCollectionView(chatId, user, page = 0)
            Action.PACK -> handlePackOpening(chatId, user, fromGroupChat, updateId)
            Action.FREECARD -> handleFreeCard(chatId, user, fromGroupChat)
            Action.CRAFT -> handleCraft(chatId, user)
            Action.BUY -> handleBuy(chatId, user)
            Action.PAYSUPPORT -> handlePaySupport(chatId, user, text)
            Action.ANSWER -> handlePaySupportAnswer(chatId, user, text)
            Action.TRADE -> handleTrade(chatId, user)
            Action.MARKET -> handleMarket(chatId, user)
        }

        // Automatic daily pack raffle: runs after any processed command in a group chat.
        if (fromGroupChat) {
            maybeGroupRaffle(chatId, user)
        }
    }

    private fun sendReferralRewardMessages(chatId: Long, user: User, reward: ReferralRewardResult) {
        telegramClient.sendMessage(
            chatId,
            Messages.t(
                if (reward.referrerRewarded) "referral.joinerReward" else "referral.joinerOnlyReward",
                gameLocale(user),
                reward.packsEach,
            ),
            openPackKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
        )

        if (!reward.referrerRewarded) return
        runCatching {
            telegramClient.sendMessage(
                reward.referrer.telegramUserId,
                Messages.t("referral.referrerReward", gameLocale(reward.referrer), reward.packsEach),
                openPackKeyboard(
                    gameLocale(reward.referrer),
                    packLedgerRepository.getTotalAvailablePacks(reward.referrer.id),
                ),
            )
        }.onFailure { error ->
            logger.warn("Could not notify referrer {} about reward", reward.referrer.id, error)
        }
    }

    private fun handleLanguage(chatId: Long, user: User) {
        val locale = gameLocale(user)
        telegramClient.sendMessage(chatId, Messages.t("language.title", locale), languageKeyboard())
    }

    // ---- Packs ----

    private fun handlePackOpening(chatId: Long, user: User, fromGroupChat: Boolean = false, updateId: Long? = null) {
        val locale = gameLocale(user)
        when (val attempt = packOpeningTransactionService.open(user.id, currentGameMonth(), fromGroupChat, updateId)) {
            PackOpeningAttempt.NoPacks -> {
                telegramClient.sendMessage(
                    chatId,
                    Messages.t("pack.noPacks", locale),
                    buyPromptKeyboard(locale),
                )
            }
            is PackOpeningAttempt.Opened -> {
                gameMetrics.packOpened()
                telegramClient.sendMessage(chatId, Messages.t("pack.opening", locale))
                attempt.cards.forEach { card -> gameMetrics.cardOpened(card.rarity.name, "pack") }

                val seenCardIds = userCardRepository.findByUserId(user.id)
                    .mapTo(mutableSetOf()) { it.cardId }
                    .apply { removeAll(attempt.newCardIds) }
                for (card in attempt.cards) {
                    val isNew = seenCardIds.add(card.id)
                    sendCardReveal(chatId, user.id, card, isNew, locale, logContext = "pack card")
                }

                val progress = attempt.affectedProgress.joinToString("\n") {
                    collectionService.formatThemeLine(it, locale)
                }
                telegramClient.sendMessage(
                    chatId,
                    Messages.t("pack.summary", locale, attempt.newCardIds.size, attempt.cards.size, progress),
                    mainMenuKeyboard(locale, attempt.remainingPacks),
                )
                sendCollectionCompletionRewards(chatId, user, attempt.completedCollections)
            }
        }
    }

    // ---- Free single card (one card every 3h, first one immediately) ----

    private fun handleFreeCard(chatId: Long, user: User, fromGroupChat: Boolean = false) {
        handleFreeCardClaim(chatId, user, fromGroupChat)
    }

    private fun handleFreeCardClaim(chatId: Long, user: User, fromGroupChat: Boolean = false) {
        // Re-read and atomically claim for double-tap and concurrent-update safety.
        val fresh = userRepository.findByTelegramUserId(user.telegramUserId) ?: user
        val locale = gameLocale(fresh)
        val availablePacks = packLedgerRepository.getTotalAvailablePacks(fresh.id)
        val now = OffsetDateTime.now(ZoneId.of(properties.gameTimezone))
        val minutesLeft = minutesUntilFreeCard(fresh.lastFreeCardAt, now, properties.economy.freeCardIntervalHours)
        if (minutesLeft > 0) {
            sendFreeCardWait(chatId, locale, minutesLeft, availablePacks)
            return
        }
        if (!userRepository.claimFreeCardIfDue(fresh.id, now, properties.economy.freeCardIntervalHours)) {
            val latest = userRepository.findByTelegramUserId(fresh.telegramUserId) ?: fresh
            val latestMinutes = minutesUntilFreeCard(latest.lastFreeCardAt, now, properties.economy.freeCardIntervalHours)
            sendFreeCardWait(chatId, locale, latestMinutes, availablePacks)
            return
        }
        val owned = userCardRepository.findByUserId(fresh.id).map { it.cardId }.toSet()
        val card = packOpeningService.rollCards(1, owned, currentGameMonth(), fromGroupChat).firstOrNull()
        if (card == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        userCardRepository.addCards(fresh.id, listOf(card.id))
        val completedCollections = claimCollectionCompletionRewards(fresh.id)
        gameMetrics.cardOpened(card.rarity.name, "free")
        gameMetrics.freeCardClaimed()
        val isNew = !owned.contains(card.id)
        val revealKeyboard = if (availablePacks > 0) {
            openPackKeyboard(locale, availablePacks)
        } else {
            null
        }
        sendCardReveal(chatId, fresh.id, card, isNew, locale, revealKeyboard, logContext = "free card")
        sendCollectionCompletionRewards(chatId, fresh, completedCollections)
        sendFreeCardWait(chatId, locale, properties.economy.freeCardIntervalHours * 60L, availablePacks)
    }

    private fun sendFreeCardWait(chatId: Long, locale: GameLocale, minutesLeft: Long, availablePacks: Int) {
        val hours = minutesLeft / 60
        val minutes = minutesLeft % 60
        telegramClient.sendMessage(
            chatId,
            Messages.t("card.nextFreeIn", locale, hours, minutes),
            mainMenuKeyboard(locale, availablePacks),
        )
    }

    // ---- Daily pack raffle in group chats ----
    //
    // Runs automatically after any processed command from a group/supergroup:
    // 10+ members -> 1 pack per 10 members, capped at 10 packs, at most once
    // per 24h per chat. Winners are registered players seen in that chat,
    // verified as non-bots at award time, and unique within one raffle.

    private fun trackGroupMember(chatId: Long, user: User, message: TelegramMessage) {
        val from = message.from ?: return
        val displayName = (from.firstName?.take(200) ?: from.username ?: "").take(200)
        runCatching {
            groupRaffleRepository.trackMember(chatId, user.id, displayName, from.username?.take(100))
        }.onFailure { logger.warn("Failed to track group member {} in chat {}", user.id, chatId, it) }
    }

    private fun maybeGroupRaffle(chatId: Long, trigger: User) {
        runCatching {
            val now = OffsetDateTime.now(ZoneId.of(properties.gameTimezone))
            // Fast path first: 24h cooldown from the database, no Telegram calls.
            val lastRaffle = groupRaffleRepository.findLastRaffle(chatId)
            if (!isRaffleDue(lastRaffle?.raffledAt, now)) return
            val memberCount = telegramClient.getChatMemberCount(chatId) ?: return
            if (memberCount < RAFFLE_MIN_MEMBERS) return
            val tierPacks = packsForRaffle(memberCount)
            if (tierPacks <= 0) return
            // Oversample: bots and departed members are filtered out below.
            val candidates = groupRaffleRepository
                .findRandomCandidates(chatId, (tierPacks * 3).coerceAtLeast(tierPacks + 5))
            if (candidates.isEmpty()) return
            val winners = candidates
                .filter { isEligibleRaffleWinner(chatId, it.telegramUserId) }
                .distinctBy { it.userId }
                .take(tierPacks)
            if (winners.isEmpty()) return
            // Serialize concurrent triggers; re-check the cooldown inside the lock.
            val raffleId = transactionTemplate.execute<Long> {
                groupRaffleRepository.lockChat(chatId)
                val freshLast = groupRaffleRepository.findLastRaffle(chatId)
                val nowLocked = OffsetDateTime.now(ZoneId.of(properties.gameTimezone))
                if (!isRaffleDue(freshLast?.raffledAt, nowLocked)) return@execute null
                val id = groupRaffleRepository.createRaffle(chatId, memberCount, winners.size)
                winners.forEach { winner ->
                    packLedgerRepository.addPacks(winner.userId, "raffle", 1)
                    groupRaffleRepository.addWinner(id, winner.userId, 1)
                }
                id
            } ?: return
            gameMetrics.raffleHeld(winners.size)
            sendRaffleResult(chatId, trigger, memberCount, winners, raffleId)
        }.onFailure { logger.warn("Group raffle failed in chat {}", chatId, it) }
    }

    private fun isEligibleRaffleWinner(chatId: Long, telegramUserId: Long): Boolean {
        val member = telegramClient.getChatMember(chatId, telegramUserId) ?: return false
        if (member.user?.isBot == true) return false
        return member.status !in setOf("left", "kicked")
    }

    private fun sendRaffleResult(
        chatId: Long,
        trigger: User,
        memberCount: Int,
        winners: List<GroupRaffleCandidate>,
        raffleId: Long,
    ) {
        val locale = gameLocale(trigger)
        val mentions = winners.joinToString("\n") { winner ->
            val safeName = sanitizeMentionName(winner.displayName).ifBlank {
                Messages.t("profile.player", locale)
            }
            "🎁 [$safeName](tg://user?id=${winner.telegramUserId})"
        }
        logger.info("Group raffle {} in chat {}: {} packs to {} winners", raffleId, chatId, winners.size, winners.size)
        telegramClient.sendMessage(
            chatId,
            Messages.t("raffle.result", locale, memberCount, packsLabel(winners.size, locale), mentions),
            mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(trigger.id)),
        )
    }

    private fun sendTradeResultReveal(
        chatId: Long,
        cardId: String,
        userId: Long,
        locale: GameLocale,
        headerKey: String,
    ) {
        val card = runCatching { cardCatalog.card(cardId) }.getOrNull()
        if (card == null) {
            val packsFallback = packLedgerRepository.getTotalAvailablePacks(userId)
            telegramClient.sendMessage(chatId, Messages.t(headerKey, locale, cardId), mainMenuKeyboard(locale, packsFallback))
            return
        }
        val quantity = userCardRepository.findByUserIdAndCardId(userId, cardId)?.quantity ?: 0
        val isNew = quantity <= 1
        val header = Messages.t(headerKey, locale, card.nameFor(locale))
        sendCardReveal(
            chatId = chatId,
            ownerUserId = userId,
            card = card,
            isNew = isNew,
            locale = locale,
            header = header,
            logContext = "trade result card",
        )
    }

    private fun sendCardReveal(
        chatId: Long,
        ownerUserId: Long,
        card: CardDefinition,
        isNew: Boolean,
        locale: GameLocale,
        replyMarkup: TelegramReplyMarkup? = null,
        header: String? = null,
        logContext: String = "card",
    ) {
        val reveal = packOpeningService.formatReveal(card, isNew, locale)
        val caption = listOfNotNull(header, reveal).joinToString("\n\n")
        val resource = ClassPathResource("static/assets/cards/${card.id}.png")
        val cardKeyboard = withShareButton(replyMarkup, card, ownerUserId, locale)
        try {
            if (resource.exists()) {
                val sent = telegramClient.sendPhoto(chatId, resource, caption, cardKeyboard)
                rememberTelegramFileId(card.id, sent)
            } else {
                telegramClient.sendMessage(chatId, caption, cardKeyboard)
            }
        } catch (e: Exception) {
            logger.warn("Failed to send {} photo for {}", logContext, card.id, e)
            telegramClient.sendMessage(chatId, caption, cardKeyboard)
        }
    }

    private fun withShareButton(
        replyMarkup: TelegramReplyMarkup?,
        card: CardDefinition,
        ownerUserId: Long,
        locale: GameLocale,
    ): TelegramReplyMarkup {
        val shareRow = listOf(
            TelegramInlineButton(
                text = Messages.t("card.share", locale),
                switchInlineQuery = cardShareLinkService.inlineQuery(card, ownerUserId),
            ),
        )
        // Telegram does not allow reply and inline keyboards in one markup.
        // Reply keyboards persist, so card messages carry the share action and
        // any existing inline actions only.
        return TelegramReplyMarkup(
            inlineKeyboard = listOf(shareRow) + replyMarkup?.inlineKeyboard.orEmpty(),
        )
    }

    private fun currentGameMonth(): Int =
        OffsetDateTime.now(ZoneId.of(properties.gameTimezone)).monthValue

    private fun knownTelegramFileId(cardId: String): String? = knownFileIds[cardId]
        ?: runCatching { telegramCardFileRepository.findFileId(cardId) }
            .onFailure { logger.warn("Could not load Telegram file_id for card {}", cardId, it) }
            .getOrNull()
            ?.also { knownFileIds[cardId] = it }

    private fun rememberTelegramFileId(cardId: String, message: TelegramMessage?) {
        val fileId = message?.photo?.lastOrNull()?.fileId ?: return
        knownFileIds[cardId] = fileId
        runCatching { telegramCardFileRepository.upsert(cardId, fileId) }
            .onFailure { logger.warn("Could not persist Telegram file_id for card {}", cardId, it) }
    }

    // ---- Pack crafter (duplicates -> points, 15 pts = 1 pack) ----

    private fun handleCraft(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val fresh = userRepository.findByTelegramUserId(user.telegramUserId) ?: user
        val duplicates = userCardRepository.findByUserId(fresh.id)
            .filter { TradePolicy.canOfferDuplicate(it.quantity) }
        val header = Messages.t("craft.title", locale, fresh.craftPoints, CraftPolicy.POINTS_PER_PACK)
        if (duplicates.isEmpty()) {
            telegramClient.sendMessage(
                chatId,
                header + "\n\n" + Messages.t("craft.noDuplicates", locale),
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
            return
        }
        val buttons = duplicates.take(10).mapNotNull { uc ->
            runCatching { cardCatalog.card(uc.cardId) }.getOrNull()?.let { card ->
                val pts = CraftPolicy.pointsFor(card.rarity)
                listOf(TelegramInlineButton(Messages.t("craft.addButton", locale, pts, card.nameFor(locale)), "craft:add:${card.id}"))
            }
        }
        telegramClient.sendMessage(
            chatId,
            header + "\n\n" + Messages.t("craft.pickCard", locale),
            TelegramReplyMarkup(inlineKeyboard = buttons),
        )
    }

    private fun handleCraftAdd(chatId: Long, user: User, cardId: String) {
        // Re-read for double-tap safety.
        val fresh = userRepository.findByTelegramUserId(user.telegramUserId) ?: user
        val locale = gameLocale(fresh)
        val owned = userCardRepository.findByUserIdAndCardId(fresh.id, cardId)
        val card = runCatching { cardCatalog.card(cardId) }.getOrNull()
        if (owned == null || card == null || !TradePolicy.canOfferDuplicate(owned.quantity)) {
            telegramClient.sendMessage(chatId, Messages.t("craft.noDuplicates", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        userCardRepository.removeCard(fresh.id, cardId, 1)
        val gained = CraftPolicy.pointsFor(card.rarity)
        val result = CraftPolicy.addPoints(fresh.craftPoints, gained)
        gameMetrics.craftMelted(card.rarity.name, gained)
        userRepository.updateCraftPoints(fresh.id, result.leftover)
        val lines = mutableListOf(
            Messages.t("craft.added", locale, card.nameFor(locale), CraftPolicy.pointsFor(card.rarity), result.leftover, CraftPolicy.POINTS_PER_PACK),
        )
        if (result.packs > 0) {
            packLedgerRepository.addPacks(fresh.id, "craft", result.packs)
            gameMetrics.craftPackBuilt(result.packs)
            lines.add(Messages.t("craft.crafted", locale, result.packs))
        }
        val availablePacks = packLedgerRepository.getTotalAvailablePacks(fresh.id)
        val remaining = userCardRepository.findByUserId(fresh.id)
            .filter { TradePolicy.canOfferDuplicate(it.quantity) }
        val inlineButtons = remaining.take(10).mapNotNull { uc ->
            runCatching { cardCatalog.card(uc.cardId) }.getOrNull()?.let { dup ->
                val pts = CraftPolicy.pointsFor(dup.rarity)
                listOf(TelegramInlineButton(Messages.t("craft.addButton", locale, pts, dup.nameFor(locale)), "craft:add:${dup.id}"))
            }
        }.toMutableList()
        if (remaining.isNotEmpty()) {
            lines.add(Messages.t("craft.title", locale, result.leftover, CraftPolicy.POINTS_PER_PACK))
            lines.add(Messages.t("craft.pickCard", locale))
        }
        if (result.packs > 0) {
            inlineButtons.add(listOf(TelegramInlineButton(packButtonLabel(locale, availablePacks), "menu:open-pack")))
        }
        val replyMarkup = if (inlineButtons.isEmpty()) {
            mainMenuKeyboard(locale, availablePacks)
        } else {
            TelegramReplyMarkup(inlineKeyboard = inlineButtons)
        }
        telegramClient.sendMessage(chatId, lines.joinToString("\n\n"), replyMarkup)
    }

    // ---- Stars shop ----

    private fun handleBuy(chatId: Long, user: User) {
        val locale = gameLocale(user)
        telegramClient.sendMessage(chatId, Messages.t("buy.title", locale), buyKeyboard(locale))
    }

    private fun handleBuyCallback(chatId: Long, user: User, packs: Int) {
        val locale = gameLocale(user)
        val stars = configuredStarsForPacks(packs)
        if (stars == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        runCatching {
            telegramClient.sendInvoice(
                chatId = chatId,
                title = Messages.t("buy.invoiceTitle", locale, packs),
                description = Messages.t("buy.invoiceDesc", locale),
                payload = starsPayload(packs, stars, user.telegramUserId, paymentPayloadSecret()),
                currency = STARS_CURRENCY,
                prices = listOf(TelegramLabeledPrice(Messages.t("buy.invoiceTitle", locale, packs), stars)),
            )
            gameMetrics.stars("invoice_sent", packs.toString())
        }.onFailure {
            logger.warn("Failed to send Stars invoice to chat {}", chatId, it)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        }
    }

    private fun handlePreCheckout(
        queryId: String?,
        buyerTelegramId: Long?,
        payload: String?,
        currency: String?,
        totalAmount: Int?,
    ) {
        if (queryId == null) return
        val order = parseStarsOrder(payload, paymentPayloadSecret())
        val expectedStars = order?.packs?.let(::configuredStarsForPacks)
        if (!matchesPricedStarsPayment(order, buyerTelegramId, currency, totalAmount, expectedStars)) {
            telegramClient.answerPreCheckoutQuery(queryId, ok = false, errorMessage = "Invalid order")
            gameMetrics.stars("precheckout_failed")
            return
        }
        telegramClient.answerPreCheckoutQuery(queryId, ok = true)
        gameMetrics.stars("precheckout_ok", requireNotNull(order).packs.toString())
    }

    private fun handleSuccessfulPayment(message: TelegramMessage) {
        val chatId = message.chat?.id ?: return
        val telegramId = message.from?.id ?: return
        val payment = message.successfulPayment ?: return
        val user = userRepository.findByTelegramUserId(telegramId)
            ?: error("Paid Telegram user $telegramId does not exist")
        val locale = gameLocale(user)

        val order = parseStarsOrder(payment.invoicePayload, paymentPayloadSecret())
        val packs = order?.packs
        val chargeId = payment.telegramPaymentChargeId
        val totalAmount = payment.totalAmount
        if (
            packs == null || chargeId.isNullOrBlank() || totalAmount == null ||
            !matchesStarsPayment(order, telegramId, payment.currency, totalAmount)
        ) {
            logger.error("Rejected invalid successful Stars payment payload for Telegram user {}", telegramId)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        try {
            val result = starsPurchaseService.fulfill(user.id, chargeId, totalAmount, packs)
            if (result == StarsPurchaseService.Result.ALREADY_REFUNDED) {
                logger.warn("Ignoring replay of refunded Stars charge {}", chargeId)
                gameMetrics.stars("refunded_replay", packs.toString())
                return
            }
            gameMetrics.stars("paid", packs.toString())
            telegramClient.sendMessage(chatId, Messages.t("buy.success", locale, packs), openPackKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        } catch (e: Exception) {
            logger.error("Failed to credit Stars purchase {}", chargeId, e)
            gameMetrics.stars("failed", packs.toString())
            throw e
        }
    }

    private fun configuredStarsForPacks(packs: Int): Int? = with(properties.economy.starsPricing) {
        when (packs) {
            1 -> onePack
            3 -> threePacks
            5 -> fivePacks
            10 -> tenPacks
            else -> null
        }
    }

    private fun paymentPayloadSecret(): String = properties.telegram.paymentPayloadSecret
        .ifBlank { properties.telegram.webhookSecret }

    // ---- Stars payment support (RiverKing-compatible flow) ----

    private fun handlePaySupport(chatId: Long, user: User, text: String) {
        val locale = gameLocale(user)
        val args = text.substringAfter(' ', "").trim()
        val payments = paymentRepository.findRefundableByUserId(user.id)
        if (args.isEmpty()) {
            if (payments.isEmpty()) {
                telegramClient.sendMessage(chatId, Messages.t("paysupport.empty", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
                return
            }
            val list = payments.joinToString("\n") { payment ->
                Messages.t("paysupport.paymentRow", locale, payment.id, payment.packsGranted, payment.stars)
            }
            telegramClient.sendMessage(
                chatId,
                Messages.t("paysupport.list", locale, list),
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
            return
        }

        val parts = args.split(Regex("\\s+"), limit = 2)
        val paymentId = parts.firstOrNull()?.toLongOrNull()
        val reason = parts.getOrNull(1)?.trim().orEmpty()
        if (paymentId == null || reason.isBlank() || reason.length > SUPPORT_TEXT_LIMIT) {
            telegramClient.sendMessage(chatId, Messages.t("paysupport.invalid", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val payment = paymentRepository.findById(paymentId)
            ?.takeIf { it.userId == user.id && it.status == PaymentStatus.COMPLETED }
        if (payment == null) {
            telegramClient.sendMessage(chatId, Messages.t("paysupport.notFound", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val adminId = properties.telegram.adminTgId
        if (adminId == 0L) {
            logger.error("Payment support requested but ADMIN_TG_ID is not configured")
            telegramClient.sendMessage(chatId, Messages.t("paysupport.unavailable", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }

        val existingRequest = paymentSupportRepository.findByPaymentId(payment.id)
        if (existingRequest != null) {
            sendPaymentSupportRequestToAdmin(existingRequest.id, user, payment.id, payment.stars, payment.packsGranted, existingRequest.reason)
            telegramClient.sendMessage(
                chatId,
                Messages.t("paysupport.alreadySubmitted", locale, existingRequest.id),
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
            return
        }
        val request = try {
            paymentSupportRepository.create(user.id, payment.id, reason)
        } catch (_: DuplicateKeyException) {
            val concurrent = paymentSupportRepository.findByPaymentId(payment.id)
                ?: throw IllegalStateException("Support request uniqueness conflict without a row")
            telegramClient.sendMessage(
                chatId,
                Messages.t("paysupport.alreadySubmitted", locale, concurrent.id),
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
            return
        }
        sendPaymentSupportRequestToAdmin(request.id, user, payment.id, payment.stars, payment.packsGranted, reason)
        telegramClient.sendMessage(
            chatId,
            Messages.t("paysupport.submitted", locale, request.id),
            mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
        )
    }

    private fun sendPaymentSupportRequestToAdmin(
        requestId: Long,
        user: User,
        paymentId: Long,
        stars: Int,
        packs: Int,
        reason: String,
    ) {
        telegramClient.sendMessage(
            properties.telegram.adminTgId,
            "Запрос #$requestId от ${user.telegramUserId}, платеж $paymentId " +
                "($stars XTR, $packs наборов): $reason\n" +
                "/refund $requestId — одобрить возврат\n" +
                "/reject $requestId <причина> — отклонить\n" +
                "/ask $requestId <вопрос> — запросить информацию",
            parseMode = null,
        )
    }

    private fun handlePaySupportAnswer(chatId: Long, user: User, text: String) {
        val locale = gameLocale(user)
        val args = text.substringAfter(' ', "").trim()
        val parts = args.split(Regex("\\s+"), limit = 2)
        val explicitId = parts.firstOrNull()?.toLongOrNull()
        val request = if (explicitId != null) {
            paymentSupportRepository.findById(explicitId)
                ?.takeIf { it.userId == user.id && it.status == PaymentSupportStatus.INFO }
        } else {
            paymentSupportRepository.latestInfoRequest(user.id)
        }
        val answer = if (explicitId != null) parts.getOrNull(1)?.trim().orEmpty() else args
        if (request == null && answer.isNotBlank()) {
            val completedReplay = paymentSupportRepository.findPendingAnswer(user.id, explicitId, answer)
            if (completedReplay != null) {
                telegramClient.sendMessage(chatId, Messages.t("paysupport.answerSent", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
                return
            }
        }
        if (request == null || answer.isBlank() || answer.length > SUPPORT_TEXT_LIMIT) {
            telegramClient.sendMessage(chatId, Messages.t("paysupport.answerInvalid", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        if (properties.telegram.adminTgId == 0L) {
            telegramClient.sendMessage(chatId, Messages.t("paysupport.unavailable", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        telegramClient.sendMessage(
            properties.telegram.adminTgId,
            "Ответ по запросу #${request.id} от ${user.telegramUserId}: $answer\n" +
                "/refund ${request.id} — одобрить возврат\n" +
                "/reject ${request.id} <причина> — отклонить\n" +
                "/ask ${request.id} <вопрос> — запросить информацию",
            parseMode = null,
        )
        if (!paymentSupportRepository.submitUserAnswer(request.id, user.id, answer)) {
            telegramClient.sendMessage(chatId, Messages.t("paysupport.notFound", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        telegramClient.sendMessage(chatId, Messages.t("paysupport.answerSent", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
    }

    private fun isPaymentSupportCommand(text: String): Boolean =
        commandName(text) in setOf("/paysupport", "/answer", "/refund", "/reject", "/ask")

    private fun isAdminCommand(text: String): Boolean =
        commandName(text) in setOf("/refund", "/reject", "/ask")

    private fun commandName(text: String): String =
        text.trim().substringBefore(' ').substringBefore('@').lowercase()

    private fun handleAdminPaymentCommand(chatId: Long, text: String) {
        val command = commandName(text)
        val args = text.substringAfter(' ', "").trim()
        val parts = args.split(Regex("\\s+"), limit = 2)
        val requestId = parts.firstOrNull()?.toLongOrNull()
        if (requestId == null) {
            telegramClient.sendMessage(chatId, "Неверный формат команды.", parseMode = null)
            return
        }
        when (command) {
            "/refund" -> handleAdminRefund(chatId, requestId)
            "/reject" -> handleAdminReject(chatId, requestId, parts.getOrNull(1)?.trim().orEmpty())
            "/ask" -> handleAdminAsk(chatId, requestId, parts.getOrNull(1)?.trim().orEmpty())
        }
    }

    private fun handleAdminRefund(chatId: Long, requestId: Long) {
        val request = paymentSupportRepository.findById(requestId)
        val payment = request?.let { paymentRepository.findById(it.paymentId) }
        val user = request?.let { userRepository.findById(it.userId) }
        if (request == null || payment == null || user == null || payment.userId != request.userId) {
            telegramClient.sendMessage(chatId, "Запрос или платеж не найден.", parseMode = null)
            return
        }
        if (payment.status == PaymentStatus.REFUNDED || request.status == PaymentSupportStatus.REFUNDED) {
            notifyUserRefunded(user, requestId)
            telegramClient.sendMessage(chatId, "Запрос #$requestId уже возвращён.", parseMode = null)
            return
        }
        val mayProcess = when (request.status) {
            PaymentSupportStatus.PENDING, PaymentSupportStatus.INFO -> paymentSupportRepository.claimForRefund(requestId)
            PaymentSupportStatus.PROCESSING -> true
            PaymentSupportStatus.REFUNDED, PaymentSupportStatus.REJECTED -> false
        }
        if (payment.status != PaymentStatus.COMPLETED || !mayProcess) {
            telegramClient.sendMessage(chatId, "Запрос #$requestId уже обрабатывается или закрыт.", parseMode = null)
            return
        }

        try {
            telegramClient.refundStarPayment(user.telegramUserId, payment.telegramPaymentId)
        } catch (e: Exception) {
            paymentSupportRepository.resetAfterRefundFailure(requestId, "Telegram refund failed")
            logger.error("Telegram Stars refund failed for request {}", requestId, e)
            if (e !is HttpClientErrorException || isRetryableTelegramClientStatus(e.statusCode.value())) {
                throw e
            }
            telegramClient.sendMessage(chatId, "Возврат #$requestId не выполнен: Telegram отклонил запрос.", parseMode = null)
            return
        }

        try {
            starsRefundService.finalizeRefund(requestId, payment)
        } catch (e: Exception) {
            logger.error("CRITICAL: Stars were refunded but local finalization failed for request {}", requestId, e)
            throw e
        }

        telegramClient.sendMessage(chatId, "Возврат по запросу #$requestId выполнен.", parseMode = null)
        notifyUserRefunded(user, requestId)
    }

    private fun notifyUserRefunded(user: User, requestId: Long) {
        telegramClient.sendMessage(
            user.telegramUserId,
            Messages.t("paysupport.refunded", gameLocale(user), requestId),
            mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
        )
    }

    private fun handleAdminReject(chatId: Long, requestId: Long, reason: String) {
        if (reason.isBlank() || reason.length > SUPPORT_TEXT_LIMIT) {
            telegramClient.sendMessage(chatId, "Используйте /reject <ID> <причина>.", parseMode = null)
            return
        }
        val request = paymentSupportRepository.findById(requestId)
        val user = request?.let { userRepository.findById(it.userId) }
        if (request != null && user != null && request.status == PaymentSupportStatus.REJECTED) {
            val savedReason = request.adminMessage ?: reason
            telegramClient.sendMessage(
                user.telegramUserId,
                Messages.t("paysupport.rejected", gameLocale(user), requestId, savedReason),
                mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
                parseMode = null,
            )
            telegramClient.sendMessage(chatId, "Запрос #$requestId уже отклонён.", parseMode = null)
            return
        }
        if (request == null || user == null || !paymentSupportRepository.resolvePending(requestId, PaymentSupportStatus.REJECTED, reason)) {
            telegramClient.sendMessage(chatId, "Запрос не найден или уже закрыт.", parseMode = null)
            return
        }
        telegramClient.sendMessage(chatId, "Запрос #$requestId отклонён.", parseMode = null)
        telegramClient.sendMessage(
            user.telegramUserId,
            Messages.t("paysupport.rejected", gameLocale(user), requestId, reason),
            mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
            parseMode = null,
        )
    }

    private fun handleAdminAsk(chatId: Long, requestId: Long, question: String) {
        if (question.isBlank() || question.length > SUPPORT_TEXT_LIMIT) {
            telegramClient.sendMessage(chatId, "Используйте /ask <ID> <вопрос>.", parseMode = null)
            return
        }
        val request = paymentSupportRepository.findById(requestId)
        val user = request?.let { userRepository.findById(it.userId) }
        if (request == null || user == null || !paymentSupportRepository.resolvePending(requestId, PaymentSupportStatus.INFO, question)) {
            telegramClient.sendMessage(chatId, "Запрос не найден или уже закрыт.", parseMode = null)
            return
        }
        telegramClient.sendMessage(chatId, "Вопрос по запросу #$requestId отправлен.", parseMode = null)
        telegramClient.sendMessage(
            user.telegramUserId,
            Messages.t("paysupport.ask", gameLocale(user), requestId, question, requestId),
            mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)),
            parseMode = null,
        )
    }

    // ---- Random trade ----

    private fun handleTrade(chatId: Long, user: User) {
        val locale = gameLocale(user)

        val duplicates = userCardRepository.findByUserId(user.id)
            .filter { TradePolicy.canOfferDuplicate(it.quantity) }
            .mapNotNull { uc ->
                runCatching { cardCatalog.card(uc.cardId) }.getOrNull()?.let { card ->
                    RandomTradeCardOption(card.id, card.nameFor(locale), formatTradeCard(card, locale))
                }
            }

        val waiting = runCatching { randomTradeRepository.findWaitingTradesForUser(user.id) }
            .getOrDefault(emptyList())
            .map { trade ->
                val card = runCatching { cardCatalog.card(trade.cardId) }.getOrNull()
                val name = card?.nameFor(locale) ?: trade.cardId
                val line = card?.let { formatTradeCard(it, locale) } ?: trade.cardId
                WaitingRandomTradeOption(trade.id, name, line)
            }

        val menu = buildRandomTradeMenu(locale, duplicates, waiting)
        if (menu == null) {
            telegramClient.sendMessage(chatId, Messages.t("trade.noDuplicates", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }

        telegramClient.sendMessage(
            chatId,
            menu.text,
            menu.replyMarkup,
        )
    }

    private data class RandomTradeResult(
        val receivedCardId: String,
        val peerUserId: Long,
    )

    private data class MarketplaceSettlementResult(
        val targetOwner: User,
        val offerOwner: User,
        val targetCardId: String,
        val offeredCardId: String,
    )

    private data class MarketplaceRejectionResult(
        val targetOwner: User,
        val offerOwner: User,
    )

    private fun handleTradeAdd(chatId: Long, user: User, cardId: String) {
        val locale = gameLocale(user)
        val owned = userCardRepository.findByUserIdAndCardId(user.id, cardId)
        if (owned == null || !TradePolicy.canOfferDuplicate(owned.quantity)) {
            telegramClient.sendMessage(chatId, Messages.t("trade.noDuplicates", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val card = runCatching { cardCatalog.card(cardId) }.getOrNull()
        val cardLine = card?.let { formatTradeCard(it, locale) } ?: cardId
        // The extra copy leaves the collection and enters the shared pool.
        userCardRepository.removeCard(user.id, cardId, 1)
        val match = attemptRandomTradeMatching(user.id, cardId)
        if (match != null) {
            gameMetrics.tradeMatched()
            runCatching {
                sendTradeResultReveal(
                    chatId = user.telegramUserId,
                    cardId = match.receivedCardId,
                    userId = user.id,
                    locale = locale,
                    headerKey = "trade.matched",
                )
            }.onFailure { logger.warn("Failed to notify random-trade owner {}", user.id, it) }
            claimAndNotifyCollectionRewards(user.telegramUserId, user)
            // The waiting side has no other way to learn about the swap.
            runCatching {
                userRepository.findById(match.peerUserId)?.let { peer ->
                    val peerLocale = gameLocale(peer)
                    sendTradeResultReveal(
                        chatId = peer.telegramUserId,
                        cardId = cardId,
                        userId = peer.id,
                        locale = peerLocale,
                        headerKey = "trade.matched",
                    )
                    claimAndNotifyCollectionRewards(peer.telegramUserId, peer)
                }
            }.onFailure { logger.warn("Failed to notify random-trade peer {}", match.peerUserId, it) }
        } else {
            telegramClient.sendMessage(
                chatId,
                Messages.t("trade.addedToPool", locale) + "\n" + cardLine,
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
        }
    }

    private fun handleTradeReturn(chatId: Long, user: User, tradeId: Long?) {
        val locale = gameLocale(user)
        if (tradeId == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val trade = runCatching { randomTradeRepository.findWaitingTradesForUser(user.id) }.getOrDefault(emptyList())
            .firstOrNull { it.id == tradeId }
        if (trade == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val returned = transactionTemplate.execute {
            if (!randomTradeRepository.cancelTrade(tradeId)) return@execute false
            userCardRepository.addCards(user.id, listOf(trade.cardId))
            true
        } == true
        if (!returned) {
            telegramClient.sendMessage(chatId, Messages.t("callback.expired", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val name = runCatching { cardCatalog.card(trade.cardId).nameFor(locale) }.getOrElse { trade.cardId }
        telegramClient.sendMessage(chatId, Messages.t("trade.returned", locale, name), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
    }

    /**
     * Enqueues [cardId] (already removed from inventory by the caller) and returns
     * the match when another player's different card was waiting.
     * Never leaves ghost pool rows: a failed enqueue is cancelled and refunded.
     */
    private fun attemptRandomTradeMatching(userId: Long, cardId: String): RandomTradeResult? {
        return try {
            transactionTemplate.execute {
                val userTrade = randomTradeRepository.addToPool(userId, cardId)
                val match = randomTradeRepository.findFirstMatchForUpdate(userTrade.id, userId, cardId)
                if (match == null) {
                    logger.debug("No random-trade match for user {} card {}; waiting", userId, cardId)
                    return@execute null
                }
                check(randomTradeRepository.matchTrades(userTrade.id, match.id)) {
                    "Random-trade pair changed while locked"
                }
                // Both cards were removed from inventories when enqueued; simply deal them out.
                userCardRepository.addCards(userId, listOf(match.cardId))
                userCardRepository.addCards(match.userId, listOf(cardId))
                logger.info("Random trade matched: user {} card {} with user {} card {}", userId, cardId, match.userId, match.cardId)
                RandomTradeResult(match.cardId, match.userId)
            }
        } catch (e: Exception) {
            logger.error("Failed to enqueue or match random trade for user {} card {}", userId, cardId, e)
            runCatching { userCardRepository.addCards(userId, listOf(cardId)) }
            null
        }
    }

    // ---- Marketplace ----

    private fun handleMarket(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val myListings = marketRepository.findActiveListingsBySeller(user.id)
        val duplicates = userCardRepository.findByUserId(user.id)
            .filter { TradePolicy.canOfferDuplicate(it.quantity) }
            .take(10)

        val buttons = mutableListOf<List<TelegramInlineButton>>()
        myListings.take(5).forEach { listing ->
            val name = runCatching { cardCatalog.card(listing.cardId).nameFor(locale) }.getOrElse { listing.cardId }
            buttons.add(listOf(TelegramInlineButton(Messages.t("market.returnButton", locale, name), "m:ret:${listing.id}")))
        }
        duplicates.forEach { uc ->
            val name = runCatching { cardCatalog.card(uc.cardId).nameFor(locale) }.getOrElse { uc.cardId }
            buttons.add(listOf(TelegramInlineButton(Messages.t("market.listButton", locale, name), "m:list:${uc.cardId}")))
        }
        buttons.add(listOf(TelegramInlineButton(Messages.t("market.browse", locale), "m:brw:0")))

        val myListingsSection = if (myListings.isEmpty()) {
            ""
        } else {
            Messages.t("market.myListings", locale) + "\n" + myListings.take(5).joinToString("\n") { listing ->
                val card = runCatching { cardCatalog.card(listing.cardId) }.getOrNull()
                card?.let { formatTradeCard(it, locale) } ?: listing.cardId
            } + "\n\n"
        }

        val duplicatesSection = if (duplicates.isEmpty()) {
            ""
        } else {
            "\n\n" + Messages.t("market.selectCard", locale) + "\n" + duplicates.joinToString("\n") { uc ->
                val card = runCatching { cardCatalog.card(uc.cardId) }.getOrNull()
                card?.let { formatTradeCard(it, locale) } ?: uc.cardId
            }
        }

        val header = myListingsSection + Messages.t("market.hint", locale) + duplicatesSection
        telegramClient.sendMessage(chatId, header, TelegramReplyMarkup(inlineKeyboard = buttons))
    }

    private fun handleMarketList(chatId: Long, user: User, cardId: String) {
        val locale = gameLocale(user)
        val owned = userCardRepository.findByUserIdAndCardId(user.id, cardId)
        if (owned == null || !TradePolicy.canOfferDuplicate(owned.quantity)) {
            telegramClient.sendMessage(chatId, Messages.t("market.noDuplicates", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        // Keep one copy in the collection; only the duplicate goes to the market.
        userCardRepository.removeCard(user.id, cardId, 1)
        try {
            marketRepository.createListing(user.id, cardId)
            telegramClient.sendMessage(chatId, Messages.t("market.listed", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        } catch (e: Exception) {
            logger.error("Failed to create market listing", e)
            runCatching { userCardRepository.addCards(user.id, listOf(cardId)) }
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        }
    }

    private fun handleMarketReturn(chatId: Long, user: User, listingId: UUID) {
        val locale = gameLocale(user)
        val listing = marketRepository.findListingById(listingId)
        if (listing == null || listing.sellerId != user.id) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val returned = transactionTemplate.execute {
            if (!marketRepository.cancelActiveListing(listingId)) return@execute false
            userCardRepository.addCards(user.id, listOf(listing.cardId))
            true
        } == true
        if (!returned) {
            telegramClient.sendMessage(chatId, Messages.t("callback.expired", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        telegramClient.sendMessage(chatId, Messages.t("market.returned", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
    }

    private fun handleMarketBrowse(chatId: Long, user: User, page: Int) {
        val locale = gameLocale(user)
        val others = marketRepository.findAllActiveListings().filter { it.sellerId != user.id }
        if (others.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noListings", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val pageItems = others.drop(page * 5).take(5)
        if (pageItems.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noListings", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val quantities = ownedQuantities(user.id)
        val text = pageItems.map { listing ->
            val card = runCatching { cardCatalog.card(listing.cardId) }.getOrNull()
            card?.let { formatMarketCard(it, locale, quantities[it.id] ?: 0) } ?: listing.cardId
        }.joinToString("\n")
        val buttons = pageItems.map { listing ->
            val name = runCatching { cardCatalog.card(listing.cardId).nameFor(locale) }.getOrElse { listing.cardId }
            listOf(TelegramInlineButton(Messages.t("market.offerButton", locale, name), "m:pick:${listing.id}"))
        }.toMutableList()
        val nav = mutableListOf<TelegramInlineButton>()
        if (page > 0) nav.add(TelegramInlineButton(Messages.t("collection.prev", locale), "m:brw:${page - 1}"))
        if (others.size > (page + 1) * 5) nav.add(TelegramInlineButton(Messages.t("collection.next", locale), "m:brw:${page + 1}"))
        if (nav.isNotEmpty()) buttons.add(nav)
        telegramClient.sendMessage(
            chatId,
            Messages.t("market.browsingListings", locale, text, Messages.t("market.browseHint", locale)),
            TelegramReplyMarkup(inlineKeyboard = buttons),
        )
    }

    /** Seller picked someone's listing: remember it and ask which of their own listings to offer. */
    private fun handleMarketPick(chatId: Long, user: User, targetId: UUID) {
        val locale = gameLocale(user)
        val target = marketRepository.findListingById(targetId)
        if (target == null || target.sellerId == user.id) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val mine = marketRepository.findActiveListingsBySeller(user.id)
        if (mine.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noDuplicates", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        pendingMarketOffers[chatId] = targetId
        val quantities = ownedQuantities(user.id)
        val targetCard = runCatching { cardCatalog.card(target.cardId) }.getOrNull()
        val targetLine = targetCard?.let { formatMarketCard(it, locale, quantities[it.id] ?: 0) } ?: target.cardId
        val buttons = mine.take(10).map { listing ->
            val card = runCatching { cardCatalog.card(listing.cardId) }.getOrNull()
            val name = card?.nameFor(locale) ?: listing.cardId
            listOf(TelegramInlineButton(name, "m:off:${listing.id}"))
        }
        val myListingsText = mine.take(10).joinToString("\n") { listing ->
            val card = runCatching { cardCatalog.card(listing.cardId) }.getOrNull()
            card?.let { formatTradeCard(it, locale) + specialSuffix(it, locale) } ?: listing.cardId
        }
        telegramClient.sendMessage(
            chatId,
            Messages.t("market.chooseOffer", locale, targetLine) + "\n\n" +
                Messages.t("market.myListings", locale) + "\n" + myListingsText,
            TelegramReplyMarkup(inlineKeyboard = buttons),
        )
    }

    private fun handleMarketOffer(chatId: Long, user: User, offeredId: UUID) {
        val locale = gameLocale(user)
        val targetId = pendingMarketOffers.remove(chatId)
        if (targetId == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val target = marketRepository.findListingById(targetId)
        val offered = marketRepository.findListingById(offeredId)
        if (
            target == null || offered == null ||
            target.status.name != "ACTIVE" || offered.status.name != "ACTIVE" ||
            offered.sellerId != user.id || target.sellerId == user.id
        ) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        try {
            val offer = marketRepository.createTradeOffer(targetId, offeredId)
            gameMetrics.marketOffer("created")
            telegramClient.sendMessage(chatId, Messages.t("market.offerMade", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            // Notify the owner with accept/reject buttons.
            val owner = userRepository.findById(target.sellerId)
            // Best effort: we can only notify if we knew the owner's chat id (= telegram id for 1:1 chats).
            if (owner != null) {
                val ownerLocale = gameLocale(owner)
                val ownerQuantities = ownedQuantities(owner.id)
                val targetCard = runCatching { cardCatalog.card(target.cardId) }.getOrNull()
                val targetLine = targetCard?.let { formatTradeCard(it, ownerLocale) + specialSuffix(it, ownerLocale) } ?: target.cardId
                val offeredCard = runCatching { cardCatalog.card(offered.cardId) }.getOrNull()
                val offeredLine = offeredCard?.let { formatMarketCard(it, ownerLocale, ownerQuantities[it.id] ?: 0) } ?: offered.cardId
                runCatching {
                    telegramClient.sendMessage(
                        owner.telegramUserId,
                        Messages.t("market.offerReceived", ownerLocale, targetLine, offeredLine),
                        TelegramReplyMarkup(
                            inlineKeyboard = listOf(
                                listOf(
                                    TelegramInlineButton(Messages.t("market.accept", ownerLocale), "m:acc:${offer.id}"),
                                    TelegramInlineButton(Messages.t("market.reject", ownerLocale), "m:rej:${offer.id}"),
                                ),
                            ),
                        ),
                    )
                }.onFailure { logger.warn("Failed to notify market listing owner {}", owner.id, it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to create trade offer", e)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        }
    }

    private fun handleMarketCallback(chatId: Long, user: User, data: String) {
        val locale = gameLocale(user)
        runCatching {
            when {
                data.startsWith("m:list:") -> handleMarketList(chatId, user, data.removePrefix("m:list:"))
                data.startsWith("m:ret:") -> handleMarketReturn(chatId, user, UUID.fromString(data.removePrefix("m:ret:")))
                data.startsWith("m:brw:") -> handleMarketBrowse(chatId, user, data.removePrefix("m:brw:").toIntOrNull() ?: 0)
                data.startsWith("m:pick:") -> handleMarketPick(chatId, user, UUID.fromString(data.removePrefix("m:pick:")))
                data.startsWith("m:off:") -> handleMarketOffer(chatId, user, UUID.fromString(data.removePrefix("m:off:")))
                data.startsWith("m:acc:") -> handleMarketAccept(chatId, user, UUID.fromString(data.removePrefix("m:acc:")))
                data.startsWith("m:rej:") -> handleMarketReject(chatId, user, UUID.fromString(data.removePrefix("m:rej:")))
                // Backward-compatible long prefixes from earlier builds.
                data.startsWith("market:accept:") -> handleMarketAccept(chatId, user, UUID.fromString(data.removePrefix("market:accept:")))
                data.startsWith("market:reject:") -> handleMarketReject(chatId, user, UUID.fromString(data.removePrefix("market:reject:")))
                else -> telegramClient.sendMessage(chatId, Messages.t("callback.expired", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            }
        }.onFailure {
            logger.error("Failed to handle market callback {}", data, it)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
        }
    }

    private fun handleMarketAccept(chatId: Long, user: User, offerId: UUID) {
        val locale = gameLocale(user)
        val settled = settleMarketplaceOffer(offerId, user.id)
        if (settled == null) {
            telegramClient.sendMessage(chatId, Messages.t("market.settlementFailed", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        val targetLocale = gameLocale(settled.targetOwner)
        runCatching {
            sendTradeResultReveal(
                chatId = settled.targetOwner.telegramUserId,
                cardId = settled.offeredCardId,
                userId = settled.targetOwner.id,
                locale = targetLocale,
                headerKey = "market.offerAccepted",
            )
        }.onFailure { logger.warn("Failed to notify accepted market target owner {}", settled.targetOwner.id, it) }
        claimAndNotifyCollectionRewards(settled.targetOwner.telegramUserId, settled.targetOwner)
        val peerLocale = gameLocale(settled.offerOwner)
        runCatching {
            sendTradeResultReveal(
                chatId = settled.offerOwner.telegramUserId,
                cardId = settled.targetCardId,
                userId = settled.offerOwner.id,
                locale = peerLocale,
                headerKey = "market.offerAccepted",
            )
        }.onFailure { logger.warn("Failed to notify accepted market offer owner {}", settled.offerOwner.id, it) }
        claimAndNotifyCollectionRewards(settled.offerOwner.telegramUserId, settled.offerOwner)
    }

    private fun handleMarketReject(chatId: Long, user: User, offerId: UUID) {
        val locale = gameLocale(user)
        val rejected = rejectMarketplaceOffer(offerId, user.id)
        if (rejected == null) {
            telegramClient.sendMessage(chatId, Messages.t("market.settlementFailed", locale), mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)))
            return
        }
        runCatching {
            telegramClient.sendMessage(
                rejected.targetOwner.telegramUserId,
                Messages.t("market.offerRejectedByYou", locale),
                mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(rejected.targetOwner.id)),
            )
        }.onFailure { logger.warn("Failed to notify rejecting market target owner {}", rejected.targetOwner.id, it) }
        val peerLocale = gameLocale(rejected.offerOwner)
        runCatching {
            telegramClient.sendMessage(
                rejected.offerOwner.telegramUserId,
                Messages.t("market.offerRejectedNotice", peerLocale),
                mainMenuKeyboard(peerLocale, packLedgerRepository.getTotalAvailablePacks(rejected.offerOwner.id)),
            )
        }.onFailure { logger.warn("Failed to notify rejected market offer owner {}", rejected.offerOwner.id, it) }
    }

    private fun settleMarketplaceOffer(offerId: UUID, actingUserId: Long): MarketplaceSettlementResult? {
        return try {
            transactionTemplate.execute {
                val offer = marketRepository.findOfferByIdForUpdate(offerId) ?: return@execute null
                if (offer.status.name != "PENDING") return@execute null

                // Lock both escrowed cards in a stable order. An ACTIVE listing is the proof that
                // the corresponding owner still has that card reserved for this exchange.
                val lockedListings = marketRepository.findListingsByIdsForUpdate(
                    offer.targetListingId,
                    offer.offeredListingId,
                ).associateBy { it.id }
                val targetListing = lockedListings[offer.targetListingId] ?: return@execute null
                val offeredListing = lockedListings[offer.offeredListingId] ?: return@execute null
                if (targetListing.status.name != "ACTIVE" || offeredListing.status.name != "ACTIVE") return@execute null
                if (targetListing.sellerId != actingUserId || targetListing.sellerId == offeredListing.sellerId) {
                    return@execute null
                }

                val targetOwner = userRepository.findById(targetListing.sellerId) ?: return@execute null
                val offerOwner = userRepository.findById(offeredListing.sellerId) ?: return@execute null

                // Both cards leave escrow and reach their new owners in the same transaction.
                userCardRepository.addCards(targetOwner.id, listOf(offeredListing.cardId))
                userCardRepository.addCards(offerOwner.id, listOf(targetListing.cardId))
                marketRepository.markListingSold(offer.targetListingId)
                marketRepository.markListingSold(offer.offeredListingId)
                marketRepository.acceptTradeOffer(offer.id)
                gameMetrics.marketOffer("accepted")
                MarketplaceSettlementResult(targetOwner, offerOwner, targetListing.cardId, offeredListing.cardId)
            }
        } catch (e: Exception) {
            logger.error("Failed to settle marketplace offer", e)
            null
        }
    }

    private fun rejectMarketplaceOffer(offerId: UUID, actingUserId: Long): MarketplaceRejectionResult? {
        return try {
            transactionTemplate.execute {
                val offer = marketRepository.findOfferByIdForUpdate(offerId) ?: return@execute null
                if (offer.status.name != "PENDING") return@execute null
                val listings = marketRepository.findListingsByIdsForUpdate(
                    offer.targetListingId,
                    offer.offeredListingId,
                ).associateBy { it.id }
                val targetListing = listings[offer.targetListingId] ?: return@execute null
                val offeredListing = listings[offer.offeredListingId] ?: return@execute null
                if (targetListing.sellerId != actingUserId) return@execute null
                val targetOwner = userRepository.findById(targetListing.sellerId) ?: return@execute null
                val offerOwner = userRepository.findById(offeredListing.sellerId) ?: return@execute null
                marketRepository.rejectTradeOffer(offer.id)
                gameMetrics.marketOffer("rejected")
                MarketplaceRejectionResult(targetOwner, offerOwner)
            }
        } catch (e: Exception) {
            logger.error("Failed to reject marketplace offer", e)
            null
        }
    }

    /** One-liner: "⚪ Cat · Common · Cozy Cats" (color emoji, name, rarity, collection) */
    private fun formatTradeCard(card: CardDefinition, locale: GameLocale): String {
        val name = card.nameFor(locale)
        val rarity = when (locale) {
            GameLocale.RU -> card.rarity.labelRu
            GameLocale.EN -> card.rarity.labelEn
        }
        val collection = cardCatalog.collection(card.themeId)
        val collectionName = when (locale) {
            GameLocale.RU -> collection.nameRu
            GameLocale.EN -> collection.nameEn
        }
        return "${card.rarity.emoji} $name · $rarity · $collectionName"
    }

    private fun specialSuffix(card: CardDefinition, locale: GameLocale): String =
        if (card.special) " · " + Messages.t("card.specialTag", locale) else ""

    private fun ownershipSuffix(ownedQty: Int, locale: GameLocale): String =
        " · " + if (ownedQty > 0) Messages.t("gallery.owned", locale, ownedQty)
        else Messages.t("gallery.missing", locale)

    /** Someone else's card on the market: special tag plus the viewer's ownership. */
    private fun formatMarketCard(card: CardDefinition, locale: GameLocale, viewerOwnedQty: Int): String =
        formatTradeCard(card, locale) + specialSuffix(card, locale) + ownershipSuffix(viewerOwnedQty, locale)

    // ---- Collections ----

    private fun sendCollectionView(chatId: Long, user: User, page: Int) {
        val locale = gameLocale(user)
        claimAndNotifyCollectionRewards(chatId, user)
        val ownedIds = userCardRepository.findByUserId(user.id).map { it.cardId }.toSet()
        val progress = collectionService.buildThemeProgress(ownedIds, emptySet())
        val totalPages = (progress.size + COLLECTIONS_PAGE_SIZE - 1) / COLLECTIONS_PAGE_SIZE
        val safePage = page.coerceIn(0, maxOf(totalPages - 1, 0))
        val pageItems = progress.drop(safePage * COLLECTIONS_PAGE_SIZE).take(COLLECTIONS_PAGE_SIZE)
        val lines = pageItems.joinToString("\n") { collectionService.formatThemeLine(it, locale) }

        val buttons = pageItems.map { t ->
            val name = when (locale) {
                GameLocale.RU -> t.themeNameRu
                GameLocale.EN -> t.themeNameEn
            }
            listOf(TelegramInlineButton("$name (${t.ownedUnique}/${t.totalCards})", GalleryActions.theme(t.themeId, 0)))
        }.toMutableList()
        val nav = mutableListOf<TelegramInlineButton>()
        if (safePage > 0) nav.add(TelegramInlineButton(Messages.t("collection.prev", locale), "col:page:${safePage - 1}"))
        if (safePage < totalPages - 1) nav.add(TelegramInlineButton(Messages.t("collection.next", locale), "col:page:${safePage + 1}"))
        if (nav.isNotEmpty()) buttons.add(nav)

        val ownedCount = ownedIds.size
        val header = Messages.t("collection.page", locale, "${safePage + 1}/$totalPages", lines) +
            "\n\n" + Messages.t("collection.title", locale, ownedCount, cardCatalog.cards.size)
        telegramClient.sendMessage(chatId, header, TelegramReplyMarkup(inlineKeyboard = buttons))
    }

    private fun claimAndNotifyCollectionRewards(chatId: Long, user: User) {
        sendCollectionCompletionRewards(chatId, user, claimCollectionCompletionRewards(user.id))
    }

    private fun claimCollectionCompletionRewards(userId: Long): List<ThemeDefinition> =
        runCatching { collectionCompletionRewardService.claimCompletedCollections(userId) }
            .onFailure { logger.error("Failed to grant collection completion rewards for user {}", userId, it) }
            .getOrDefault(emptyList())

    private fun sendCollectionCompletionRewards(
        chatId: Long,
        user: User,
        completedCollections: List<ThemeDefinition>,
    ) {
        if (completedCollections.isEmpty()) return
        val locale = gameLocale(user)
        val names = completedCollections.joinToString(", ") { theme ->
            when (locale) {
                GameLocale.RU -> theme.nameRu
                GameLocale.EN -> theme.nameEn
            }
        }
        gameMetrics.collectionCompleted(completedCollections.size)
        runCatching {
            telegramClient.sendMessage(
                chatId,
                Messages.t("collection.reward", locale, names, completedCollections.size),
                openPackKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
            )
        }.onFailure { logger.warn("Failed to notify collection completion rewards for user {}", user.id, it) }
    }

    private fun sendThemesView(chatId: Long, user: User) {
        // Backward-compatible alias: themes == collections page 0.
        sendCollectionView(chatId, user, page = 0)
    }

    // ---- Routing ----

    internal enum class Action {
        START, HELP, LANGUAGE, COLLECTION, PACK, FREECARD, CRAFT, BUY, PAYSUPPORT, ANSWER, TRADE, MARKET
    }

    private fun handleCallback(callback: TelegramCallbackQuery, updateId: Long? = null) {
        val chatId = callback.message?.chat?.id ?: return
        val telegramId = callback.from?.id ?: return
        val fromGroupChat = isGroupChat(callback.message?.chat?.type)
        val user = userRepository.findByTelegramUserId(telegramId) ?: run {
            // Handle language selection for new user
            callback.id?.let { telegramClient.answerCallbackQuery(it) }
            when (callback.data) {
                "lang:en" -> {
                    val registration = playerRegistrationService.registerIfMissing(telegramId, GameLocale.EN.code, "direct")
                    val newUser = registration.user
                    if (registration.created) gameMetrics.registration("direct")
                    telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN, packLedgerRepository.getTotalAvailablePacks(newUser.id)))
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.EN, properties.economy.starterPacks), openPackKeyboard(GameLocale.EN, packLedgerRepository.getTotalAvailablePacks(newUser.id)))
                }
                "lang:ru" -> {
                    val registration = playerRegistrationService.registerIfMissing(telegramId, GameLocale.RU.code, "direct")
                    val newUser = registration.user
                    if (registration.created) gameMetrics.registration("direct")
                    telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU, packLedgerRepository.getTotalAvailablePacks(newUser.id)))
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.RU, properties.economy.starterPacks), openPackKeyboard(GameLocale.RU, packLedgerRepository.getTotalAvailablePacks(newUser.id)))
                }
                else -> telegramClient.sendMessage(chatId, Messages.t("callback.expired", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
            }
            return
        }

        callback.id?.let { telegramClient.answerCallbackQuery(it) }

        callback.data?.let { data ->
            GalleryActions.parse(data)?.let { action ->
                handleGalleryAction(chatId, user, callback.message?.messageId, action)
                return
            }
        }

        when (val data = callback.data) {
            "lang:en" -> {
                userRepository.updateLanguage(user.id, GameLocale.EN.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN, packLedgerRepository.getTotalAvailablePacks(user.id)))
            }
            "lang:ru" -> {
                userRepository.updateLanguage(user.id, GameLocale.RU.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU, packLedgerRepository.getTotalAvailablePacks(user.id)))
            }
            "menu:collection" -> sendCollectionView(chatId, user, page = 0)
            "menu:pack", "menu:open-pack" -> handlePackOpening(chatId, user, fromGroupChat, updateId)
            "menu:freecard" -> handleFreeCard(chatId, user, fromGroupChat)
            "menu:craft" -> handleCraft(chatId, user)
            "menu:market" -> handleMarket(chatId, user)
            "menu:buy" -> handleBuy(chatId, user)
            "free:card" -> handleFreeCardClaim(chatId, user, fromGroupChat)
            "buy:1" -> handleBuyCallback(chatId, user, 1)
            "buy:3" -> handleBuyCallback(chatId, user, 3)
            "buy:5" -> handleBuyCallback(chatId, user, 5)
            "buy:10" -> handleBuyCallback(chatId, user, 10)
            else -> {
                if (data == null) return
                when {
                    data.startsWith("trade:add:") -> handleTradeAdd(chatId, user, data.removePrefix("trade:add:"))
                    data.startsWith("trade:ret:") -> handleTradeReturn(chatId, user, data.removePrefix("trade:ret:").toLongOrNull())
                    data.startsWith("craft:add:") -> handleCraftAdd(chatId, user, data.removePrefix("craft:add:"))
                    data.startsWith("col:page:") -> sendCollectionView(chatId, user, data.removePrefix("col:page:").toIntOrNull() ?: 0)
                    data.startsWith("m:") || data.startsWith("market:") -> handleMarketCallback(chatId, user, data)
                    else -> telegramClient.sendMessage(chatId, Messages.t("callback.expired", gameLocale(user)), mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
                }
            }
        }
    }

    private fun sendThemesAlias(chatId: Long, user: User) = sendThemesView(chatId, user)

    private fun buildCollectionPreview(user: User, userCards: List<UserCard>): String {
        val locale = gameLocale(user)
        if (userCards.isEmpty()) {
            return Messages.t("collection.empty", locale)
        }

        val lines = userCards.take(15).mapNotNull { uc ->
            runCatching { cardCatalog.card(uc.cardId) }.getOrNull()?.let { card ->
                "${card.rarity.emoji} ${card.nameFor(locale)} (x${uc.quantity})"
            }
        }.joinToString("\n")

        val uniqueCount = userCards.size
        return """
            ${Messages.t("collection.title", locale, uniqueCount, cardCatalog.cards.size)}

            $lines
            ${if (userCards.size > 15) Messages.t("collection.more", locale, userCards.size - 15) else ""}
        """.trimIndent()
    }

    private fun buildThemesPreview(progress: List<ThemeProgress>, locale: GameLocale): String {
        val lines = progress.joinToString("\n") { collectionService.formatThemeLine(it, locale) }
        return """
            ${Messages.t("themes.title", locale)}

            $lines
        """.trimIndent()
    }

    private fun themesKeyboard(progress: List<ThemeProgress>, locale: GameLocale): TelegramReplyMarkup {
        val buttons = progress.map { t ->
            val name = when (locale) {
                GameLocale.RU -> t.themeNameRu
                GameLocale.EN -> t.themeNameEn
            }
            TelegramInlineButton("$name (${t.ownedUnique}/${t.totalCards})", GalleryActions.theme(t.themeId, 0))
        }
        return TelegramReplyMarkup(inlineKeyboard = buttons.map { listOf(it) })
    }

    private fun ownedUniqueCards(userId: Long): List<CardDefinition> =
        userCardRepository.findByUserId(userId)
            .mapNotNull { uc -> runCatching { cardCatalog.card(uc.cardId) }.getOrNull() }
            .distinctBy { it.id }

    private fun ownedQuantities(userId: Long): Map<String, Int> =
        userCardRepository.findByUserId(userId).associate { it.cardId to it.quantity }

    private fun buildProfilePreview(user: User): String {
        val locale = gameLocale(user)
        val availablePacks = packLedgerRepository.getTotalAvailablePacks(user.id)
        return """
            ${Messages.t("profile.title", locale, "Player")}

            ${Messages.t("profile.packs", locale, availablePacks)}
            ${Messages.t("profile.unique", locale, userCardRepository.findByUserId(user.id).size, cardCatalog.cards.size)}
        """.trimIndent()
    }

    private fun gameLocale(user: User): GameLocale = GameLocale.fromCode(user.language)

    /** Pack button label with the unopened-pack count, e.g. "🎁 Набор (3)". */
    private fun packButtonLabel(locale: GameLocale, availablePacks: Int): String =
        if (availablePacks > 0) Messages.t("menu.packCount", locale, availablePacks)
        else Messages.t("menu.pack", locale)

    private fun mainMenuKeyboard(locale: GameLocale, availablePacks: Int = 0): TelegramReplyMarkup =
        TelegramReplyMarkup(
            keyboard = listOf(
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.collection", locale)),
                    TelegramKeyboardButton(packButtonLabel(locale, availablePacks)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.trade", locale)),
                    TelegramKeyboardButton(Messages.t("menu.market", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.buy", locale)),
                    TelegramKeyboardButton(Messages.t("menu.freecard", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.craft", locale)),
                    TelegramKeyboardButton(Messages.t("menu.language", locale)),
                ),
            ),
            resizeKeyboard = true,
        )

    private fun buyPromptKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(TelegramInlineButton(Messages.t("menu.buy", locale), "menu:buy")),
            ),
        )

    private fun helpKeyboard(locale: GameLocale, availablePacks: Int = 0): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(
                    TelegramInlineButton(packButtonLabel(locale, availablePacks), "menu:pack"),
                    TelegramInlineButton(Messages.t("menu.freecard", locale), "menu:freecard"),
                ),
                listOf(
                    TelegramInlineButton(Messages.t("menu.craft", locale), "menu:craft"),
                    TelegramInlineButton(Messages.t("menu.market", locale), "menu:market"),
                ),
            ),
        )

    private fun buyKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(TelegramInlineButton(Messages.t("buy.option1", locale), "buy:1")),
                listOf(TelegramInlineButton(Messages.t("buy.option3", locale), "buy:3")),
                listOf(TelegramInlineButton(Messages.t("buy.option5", locale), "buy:5")),
                listOf(TelegramInlineButton(Messages.t("buy.option10", locale), "buy:10")),
            ),
        )

    private fun openPackKeyboard(locale: GameLocale, availablePacks: Int = 0): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(TelegramInlineButton(packButtonLabel(locale, availablePacks), "menu:open-pack")),
            ),
        )

    private fun languageKeyboard(): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(
                    TelegramInlineButton("English", "lang:en"),
                    TelegramInlineButton("Русский", "lang:ru"),
                ),
            ),
        )

    // ---- Gallery ----

    private fun handleGalleryAction(chatId: Long, user: User, tappedMessageId: Long?, action: GalleryAction) {
        when (action) {
            is GalleryAction.Collection -> {
                val ownedCards = ownedUniqueCards(user.id)
                if (ownedCards.isEmpty()) {
                    telegramClient.sendMessage(chatId, Messages.t("collection.empty", gameLocale(user)), mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
                    return
                }
                val index = action.index.coerceIn(0, ownedCards.size - 1)
                if (index != action.index) return
                val quantities = ownedQuantities(user.id)
                val card = ownedCards[index]
                showGalleryCard(
                    chatId = chatId,
                    user = user,
                    tappedMessageId = tappedMessageId,
                    key = GalleryKey.Collection,
                    card = card,
                    theme = null,
                    ownedCount = quantities[card.id] ?: 0,
                    index = index,
                    total = ownedCards.size,
                    actionForIndex = { i -> GalleryActions.collection(i) },
                )
            }
            is GalleryAction.Theme -> {
                // Per spec only opened cards are shown and paged.
                val quantities = ownedQuantities(user.id)
                val cards = cardCatalog.cardsByCollection(action.themeId)
                    .filter { (quantities[it.id] ?: 0) > 0 }
                if (cards.isEmpty()) {
                    telegramClient.sendMessage(chatId, Messages.t("collection.empty", gameLocale(user)), mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
                    return
                }
                val index = action.index.coerceIn(0, cards.size - 1)
                if (index != action.index) return
                val card = cards[index]
                showGalleryCard(
                    chatId = chatId,
                    user = user,
                    tappedMessageId = tappedMessageId,
                    key = GalleryKey.Theme(action.themeId),
                    card = card,
                    theme = cardCatalog.collection(action.themeId),
                    ownedCount = quantities[card.id] ?: 0,
                    index = index,
                    total = cards.size,
                    actionForIndex = { i -> GalleryActions.theme(action.themeId, i) },
                )
            }
            GalleryAction.Back -> {
                val key = chatGalleries.remove(chatId)?.key
                when (key) {
                    GalleryKey.Collection -> sendCollectionView(chatId, user, page = 0)
                    is GalleryKey.Theme -> sendCollectionView(chatId, user, page = 0)
                    null -> telegramClient.sendMessage(chatId, Messages.t("callback.expired", gameLocale(user)), mainMenuKeyboard(gameLocale(user), packLedgerRepository.getTotalAvailablePacks(user.id)))
                }
            }
        }
    }

    private fun showGalleryCard(
        chatId: Long,
        user: User,
        tappedMessageId: Long?,
        key: GalleryKey,
        card: CardDefinition,
        theme: ThemeDefinition?,
        ownedCount: Int,
        index: Int,
        total: Int,
        actionForIndex: (Int) -> String,
    ) {
        val locale = gameLocale(user)
        val caption = galleryCaption(card, theme, ownedCount, locale, index + 1, total)
        val keyboard = galleryKeyboard(locale, user.id, actionForIndex, index, total, card, ownedCount)
        val resource = ClassPathResource("static/assets/cards/${card.id}.png")

        val existing = chatGalleries[chatId]
        val cachedFileId = knownTelegramFileId(card.id)

        if (existing != null && existing.messageId == tappedMessageId && existing.key == key && cachedFileId != null) {
            try {
                telegramClient.editMessageMedia(chatId, existing.messageId, galleryMedia(cachedFileId, caption), keyboard)
                chatGalleries[chatId] = existing.copy(index = index)
                return
            } catch (e: Throwable) {
                logger.warn("Failed to edit gallery photo in chat {}", chatId, e)
            }
        }

        try {
            if (!resource.exists()) {
                telegramClient.sendMessage(chatId, caption, keyboard)
                chatGalleries.remove(chatId)
                return
            }
            val sent = telegramClient.sendPhoto(chatId, resource, caption, keyboard)
            val messageId = sent?.messageId
            val fileId = sent?.photo?.lastOrNull()?.fileId
            if (messageId != null && fileId != null) {
                rememberTelegramFileId(card.id, sent)
                if (existing != null && existing.key == key) {
                    runCatching { telegramClient.deleteMessage(chatId, existing.messageId) }
                }
                chatGalleries[chatId] = ChatGallery(messageId, fileId, key, index)
            } else {
                chatGalleries.remove(chatId)
            }
        } catch (e: Throwable) {
            logger.warn("Failed to send gallery photo for {}", card.id, e)
            runCatching { telegramClient.sendMessage(chatId, caption, keyboard) }
            chatGalleries.remove(chatId)
        }
    }

    private fun galleryKeyboard(
        locale: GameLocale,
        ownerUserId: Long,
        actionForIndex: (Int) -> String,
        index: Int,
        total: Int,
        card: CardDefinition,
        ownedCount: Int,
    ): TelegramReplyMarkup {
        val buttons = mutableListOf<List<TelegramInlineButton>>()

        // Duplicate actions per spec: random trade and market listing.
        if (ownedCount > 1) {
            buttons.add(
                listOf(
                    TelegramInlineButton(Messages.t("gallery.trade", locale), "trade:add:${card.id}"),
                    TelegramInlineButton(Messages.t("gallery.listMarket", locale), "m:list:${card.id}"),
                ),
            )
        }

        buttons.add(
            listOf(
                TelegramInlineButton(
                    text = Messages.t("card.share", locale),
                    switchInlineQuery = cardShareLinkService.inlineQuery(card, ownerUserId),
                ),
            ),
        )

        // Navigation
        val navButtons = mutableListOf<TelegramInlineButton>()
        if (index > 0) {
            navButtons.add(TelegramInlineButton("◀️", actionForIndex(index - 1)))
        }
        navButtons.add(TelegramInlineButton(Messages.t("gallery.back", locale), "gal:back"))
        if (index < total - 1) {
            navButtons.add(TelegramInlineButton("▶️", actionForIndex(index + 1)))
        }
        buttons.add(navButtons)

        return TelegramReplyMarkup(inlineKeyboard = buttons)
    }

    private fun galleryMedia(fileId: String, caption: String): Map<String, Any> = mapOf(
        "type" to "photo",
        "media" to fileId,
        "caption" to caption,
    )

    private fun showMarketListings(chatId: Long, user: User, listings: List<MarketListing>) {
        val locale = gameLocale(user)
        val listingText = listings.mapIndexed { i, listing ->
            val card = cardCatalog.card(listing.cardId)
            "%d. %s %s".format(i + 1, card.rarity.emoji, card.nameFor(locale))
        }.joinToString("\n")

        telegramClient.sendMessage(
            chatId,
            Messages.t("market.browsingListings", locale, listingText, Messages.t("market.browseHint", locale)),
            mainMenuKeyboard(locale, packLedgerRepository.getTotalAvailablePacks(user.id)),
        )
    }
}
