package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.collection.ThemeProgress
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import com.anry88.purrrfolio.models.MarketListing
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.models.UserCard
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.MarketRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import com.anry88.purrrfolio.repository.ProcessedUpdateRepository
import com.anry88.purrrfolio.repository.RandomTradeRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramCallbackQuery
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramKeyboardButton
import com.anry88.purrrfolio.telegram.TelegramLabeledPrice
import com.anry88.purrrfolio.telegram.TelegramMessage
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import com.anry88.purrrfolio.telegram.TelegramUpdate
import com.anry88.purrrfolio.trade.TradePolicy
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class GameService(
    private val properties: PurrrfolioProperties,
    private val cardCatalog: CardCatalog,
    private val collectionService: CollectionService,
    private val packOpeningService: PackOpeningService,
    private val userRepository: UserRepository,
    private val userCardRepository: UserCardRepository,
    private val packLedgerRepository: PackLedgerRepository,
    private val marketRepository: MarketRepository,
    private val randomTradeRepository: RandomTradeRepository,
    private val paymentRepository: PaymentRepository,
    private val processedUpdateRepository: ProcessedUpdateRepository,
    private val telegramClient: TelegramClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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

        fun starsPayload(packs: Int): String = "purrrfolio:packs:$packs"

        fun packsFromPayload(payload: String?): Int? =
            payload?.removePrefix("purrrfolio:packs:")?.toIntOrNull()?.takeIf { starsForPacks(it) != null }

        /** Hours until the next free grant (pack or single card); 0 means it is due now. */
        fun hoursUntilFreePack(lastFree: OffsetDateTime?, now: OffsetDateTime, intervalHours: Int): Int {
            if (lastFree == null) return 0
            val elapsed = ChronoUnit.HOURS.between(lastFree, now).toInt()
            return maxOf(intervalHours - elapsed, 0)
        }
    }

    fun handle(update: TelegramUpdate) {
        val updateId = update.updateId
        if (updateId != null) {
            if (!processedUpdateRepository.recordUpdate(updateId)) {
                logger.warn("Skipping already processed update {}", updateId)
                return
            }
        }
        update.preCheckoutQuery?.let {
            handlePreCheckout(it.id, it.invoicePayload, it.totalAmount)
            return
        }
        update.callbackQuery?.let {
            handleCallback(it)
            return
        }
        update.message?.let {
            // Successful Stars payment arrives as a service message.
            val payment = it.successfulPayment
            if (payment != null) {
                handleSuccessfulPayment(it)
                return
            }
            handleMessage(it)
        }
    }

    private fun handleMessage(message: TelegramMessage) {
        val chatId = message.chat?.id ?: return
        val telegramId = message.from?.id ?: return

        val user = userRepository.findByTelegramUserId(telegramId) ?: run {
            // First time user - show language selection
            handleLanguageSelection(chatId, telegramId)
            return
        }

        val text = message.text?.trim().orEmpty()
        val action = resolveAction(text, gameLocale(user))

        when (action) {
            Action.START -> telegramClient.sendMessage(chatId, Messages.t("welcome", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
            Action.HELP -> telegramClient.sendMessage(chatId, Messages.t("help", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
            Action.LANGUAGE -> handleLanguage(chatId, user)
            Action.COLLECTION -> sendCollectionView(chatId, user, page = 0)
            Action.PACK -> handlePackOpening(chatId, user)
            Action.BUY -> handleBuy(chatId, user)
            Action.PAYSUPPORT -> telegramClient.sendMessage(chatId, Messages.t("paysupport.text", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
            Action.TRADE -> handleTrade(chatId, user)
            Action.MARKET -> handleMarket(chatId, user)
            Action.UNKNOWN_COMMAND -> telegramClient.sendMessage(chatId, Messages.t("unknownCommand", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
            Action.UNKNOWN_TEXT -> telegramClient.sendMessage(chatId, Messages.t("unknownText", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
        }
    }

    private fun handleLanguageSelection(chatId: Long, telegramId: Long) {
        telegramClient.sendMessage(chatId, Messages.t("language.title", GameLocale.EN), languageKeyboard())
    }

    private fun handleLanguage(chatId: Long, user: User) {
        val locale = gameLocale(user)
        telegramClient.sendMessage(chatId, Messages.t("language.title", locale), languageKeyboard())
    }

    // ---- Packs ----

    private fun handlePackOpening(chatId: Long, user: User) {
        val locale = gameLocale(user)
        // Free single card row first (available immediately at start, then every 7h).
        sendFreeCardStatus(chatId, user)

        val availablePacks = packLedgerRepository.getTotalAvailablePacks(user.id)
        if (availablePacks <= 0) {
            // No free packs anymore: packs come from the 3 starter grants and Stars purchases.
            telegramClient.sendMessage(
                chatId,
                Messages.t("pack.noPacks", locale),
                buyPromptKeyboard(locale),
            )
            return
        }

        // Open pack
        packLedgerRepository.addPacks(user.id, "opened", -1)
        telegramClient.sendMessage(chatId, Messages.t("pack.opening", locale))

        // Roll cards
        val currentInventory = userCardRepository.findByUserId(user.id).map { it.cardId }.toSet()
        val rolledCards = packOpeningService.rollCards(properties.economy.cardsPerPack, currentInventory)

        // Save cards
        userCardRepository.addCards(user.id, rolledCards.map { it.id })

        for (card in rolledCards) {
            val isNew = !currentInventory.contains(card.id)
            val caption = packOpeningService.formatReveal(card, isNew, locale)

            val resource = ClassPathResource("static/assets/cards/${card.id}.png")
            try {
                if (resource.exists()) {
                    telegramClient.sendPhoto(chatId, resource, caption)
                } else {
                    telegramClient.sendMessage(chatId, caption)
                }
            } catch (e: Throwable) {
                logger.warn("Failed to send card photo for {}", card.id, e)
                runCatching { telegramClient.sendMessage(chatId, caption) }
            }
        }
        telegramClient.sendMessage(chatId, Messages.t("pack.opened", locale), mainMenuKeyboard(locale))
    }

    // ---- Free single card (one card every 7h, first one immediately) ----

    private fun sendFreeCardStatus(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val fresh = userRepository.findByTelegramUserId(user.telegramUserId) ?: user
        val hoursLeft = hoursUntilFreePack(fresh.lastFreeCardAt, OffsetDateTime.now(), properties.economy.freeCardIntervalHours)
        if (hoursLeft == 0) {
            telegramClient.sendMessage(
                chatId,
                Messages.t("card.freeAvailable", locale),
                TelegramReplyMarkup(
                    inlineKeyboard = listOf(
                        listOf(TelegramInlineButton(Messages.t("card.claim", locale), "free:card")),
                    ),
                ),
            )
        } else {
            telegramClient.sendMessage(
                chatId,
                Messages.t("card.nextFreeIn", locale, hoursLeft),
                mainMenuKeyboard(locale),
            )
        }
    }

    private fun handleFreeCardClaim(chatId: Long, user: User) {
        // Re-read for double-tap safety: only the first tap grants the card.
        val fresh = userRepository.findByTelegramUserId(user.telegramUserId) ?: user
        val locale = gameLocale(fresh)
        val now = OffsetDateTime.now()
        val hoursLeft = hoursUntilFreePack(fresh.lastFreeCardAt, now, properties.economy.freeCardIntervalHours)
        if (hoursLeft > 0) {
            telegramClient.sendMessage(
                chatId,
                Messages.t("card.nextFreeIn", locale, hoursLeft),
                mainMenuKeyboard(locale),
            )
            return
        }
        userRepository.updateLastFreeCardAt(fresh.id, now)
        val owned = userCardRepository.findByUserId(fresh.id).map { it.cardId }.toSet()
        val card = packOpeningService.rollCards(1, owned).firstOrNull()
        if (card == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        userCardRepository.addCards(fresh.id, listOf(card.id))
        val isNew = !owned.contains(card.id)
        val caption = packOpeningService.formatReveal(card, isNew, locale)
        val resource = ClassPathResource("static/assets/cards/${card.id}.png")
        try {
            if (resource.exists()) {
                telegramClient.sendPhoto(chatId, resource, caption, openPackKeyboard(locale))
            } else {
                telegramClient.sendMessage(chatId, caption, openPackKeyboard(locale))
            }
        } catch (e: Throwable) {
            logger.warn("Failed to send free card photo for {}", card.id, e)
            runCatching { telegramClient.sendMessage(chatId, caption, openPackKeyboard(locale)) }
        }
        telegramClient.sendMessage(
            chatId,
            Messages.t("card.nextFreeIn", locale, properties.economy.freeCardIntervalHours),
            mainMenuKeyboard(locale),
        )
    }

    // ---- Stars shop ----

    private fun handleBuy(chatId: Long, user: User) {
        val locale = gameLocale(user)
        telegramClient.sendMessage(chatId, Messages.t("buy.title", locale), buyKeyboard(locale))
    }

    private fun handleBuyCallback(chatId: Long, user: User, packs: Int) {
        val locale = gameLocale(user)
        val stars = starsForPacks(packs)
        if (stars == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        runCatching {
            telegramClient.sendInvoice(
                chatId = chatId,
                title = Messages.t("buy.invoiceTitle", locale, packs),
                description = Messages.t("buy.invoiceDesc", locale),
                payload = starsPayload(packs),
                currency = STARS_CURRENCY,
                prices = listOf(TelegramLabeledPrice(Messages.t("buy.invoiceTitle", locale, packs), stars)),
            )
        }.onFailure {
            logger.warn("Failed to send Stars invoice to chat {}", chatId, it)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
        }
    }

    private fun handlePreCheckout(queryId: String?, payload: String?, totalAmount: Int?) {
        if (queryId == null) return
        val packs = packsFromPayload(payload)
        val expectedStars = packs?.let { starsForPacks(it) }
        if (packs == null || expectedStars == null || totalAmount != expectedStars) {
            telegramClient.answerPreCheckoutQuery(queryId, ok = false, errorMessage = "Invalid order")
            return
        }
        telegramClient.answerPreCheckoutQuery(queryId, ok = true)
    }

    private fun handleSuccessfulPayment(message: TelegramMessage) {
        val chatId = message.chat?.id ?: return
        val telegramId = message.from?.id ?: return
        val payment = message.successfulPayment ?: return
        val user = userRepository.findByTelegramUserId(telegramId) ?: return
        val locale = gameLocale(user)

        val packs = packsFromPayload(payment.invoicePayload)
        val chargeId = payment.telegramPaymentChargeId
        if (packs == null || chargeId == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        // Idempotency by Telegram charge id.
        if (paymentRepository.findByTelegramPaymentId(chargeId) != null) {
            telegramClient.sendMessage(chatId, Messages.t("buy.success", locale, packs), openPackKeyboard(locale))
            return
        }
        try {
            paymentRepository.createPayment(user.id, chargeId, payment.totalAmount ?: 0, packs)
            packLedgerRepository.addPacks(user.id, "stars", packs, payment.totalAmount, chargeId)
            paymentRepository.markPaymentCompleted(paymentRepository.findByTelegramPaymentId(chargeId)!!.id)
            telegramClient.sendMessage(chatId, Messages.t("buy.success", locale, packs), openPackKeyboard(locale))
        } catch (e: Exception) {
            logger.error("Failed to credit Stars purchase {}", chargeId, e)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
        }
    }

    // ---- Random trade ----

    private fun handleTrade(chatId: Long, user: User) {
        val locale = gameLocale(user)

        val duplicates = userCardRepository.findByUserId(user.id)
            .filter { TradePolicy.canOfferDuplicate(it.quantity) }

        if (duplicates.isEmpty()) {
            telegramClient.sendMessage(
                chatId,
                Messages.t("trade.noDuplicates", locale),
                mainMenuKeyboard(locale),
            )
            return
        }

        val buttons = duplicates.take(10).mapNotNull { uc ->
            runCatching { cardCatalog.card(uc.cardId) }.getOrNull()?.let { card ->
                listOf(TelegramInlineButton(Messages.t("trade.offerButton", locale, card.nameFor(locale)), "trade:add:${card.id}"))
            }
        }.toMutableList()
        // Show the user's own waiting pool entries so a quiet pool is visible.
        val waiting = runCatching { randomTradeRepository.findWaitingTradesForUser(user.id) }.getOrDefault(emptyList())
        val waitingLine = if (waiting.isEmpty()) {
            ""
        } else {
            "\n\n" + Messages.t("trade.waiting", locale) + "\n" + waiting.take(5).joinToString("\n") { trade ->
                val name = runCatching { cardCatalog.card(trade.cardId).nameFor(locale) }.getOrElse { trade.cardId }
                "• 🎴 $name"
            }
        }
        telegramClient.sendMessage(
            chatId,
            Messages.t("trade.hint", locale) + "\n\n" + Messages.t("trade.pickCard", locale) + waitingLine,
            TelegramReplyMarkup(inlineKeyboard = buttons),
        )
    }

    private data class RandomTradeResult(
        val receivedCardId: String,
        val peerUserId: Long,
    )

    private fun handleTradeAdd(chatId: Long, user: User, cardId: String) {
        val locale = gameLocale(user)
        val owned = userCardRepository.findByUserIdAndCardId(user.id, cardId)
        if (owned == null || !TradePolicy.canOfferDuplicate(owned.quantity)) {
            telegramClient.sendMessage(chatId, Messages.t("trade.noDuplicates", locale), mainMenuKeyboard(locale))
            return
        }
        val cardName = runCatching { cardCatalog.card(cardId).nameFor(locale) }.getOrElse { cardId }
        // The extra copy leaves the collection and enters the shared pool.
        userCardRepository.removeCard(user.id, cardId, 1)
        val match = attemptRandomTradeMatching(user.id, cardId)
        if (match != null) {
            val matchedName = runCatching { cardCatalog.card(match.receivedCardId).nameFor(locale) }.getOrElse { match.receivedCardId }
            telegramClient.sendMessage(chatId, Messages.t("trade.matched", locale, matchedName), mainMenuKeyboard(locale))
            // The waiting side has no other way to learn about the swap.
            runCatching {
                userRepository.findById(match.peerUserId)?.let { peer ->
                    val peerName = runCatching { cardCatalog.card(cardId).nameFor(gameLocale(peer)) }.getOrElse { cardId }
                    telegramClient.sendMessage(
                        peer.telegramUserId,
                        Messages.t("trade.matched", gameLocale(peer), peerName),
                        mainMenuKeyboard(gameLocale(peer)),
                    )
                }
            }.onFailure { logger.warn("Failed to notify random-trade peer {}", match.peerUserId, it) }
        } else {
            telegramClient.sendMessage(
                chatId,
                Messages.t("trade.addedToPool", locale) + "\n🎴 $cardName",
                mainMenuKeyboard(locale),
            )
        }
    }

    /**
     * Enqueues [cardId] (already removed from inventory by the caller) and returns
     * the match when another player's different card was waiting.
     * Never leaves ghost pool rows: a failed enqueue is cancelled and refunded.
     */
    private fun attemptRandomTradeMatching(userId: Long, cardId: String): RandomTradeResult? {
        val userTrade = try {
            randomTradeRepository.addToPool(userId, cardId)
        } catch (e: Exception) {
            logger.error("Failed to enqueue random trade for user {} card {}", userId, cardId, e)
            runCatching { userCardRepository.addCards(userId, listOf(cardId)) }
            return null
        }
        return try {
            val waitingTrades = randomTradeRepository.findAllWaiting()
            val match = waitingTrades
                .filter { it.id != userTrade.id }
                .firstOrNull { it.userId != userId && it.cardId != cardId }
            if (match != null) {
                // Both cards were removed from inventories when enqueued; simply deal them out.
                userCardRepository.addCards(userId, listOf(match.cardId))
                userCardRepository.addCards(match.userId, listOf(cardId))
                randomTradeRepository.matchTrades(userTrade.id, match.id)
                logger.info("Random trade matched: user {} card {} with user {} card {}", userId, cardId, match.userId, match.cardId)
                RandomTradeResult(match.cardId, match.userId)
            } else {
                logger.debug("No random-trade match for user {} card {}; waiting", userId, cardId)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to match random trade {} for user {} card {}", userTrade.id, userId, cardId, e)
            runCatching { randomTradeRepository.cancelTrade(userTrade.id) }
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

        val header = if (myListings.isEmpty()) {
            Messages.t("market.hint", locale)
        } else {
            Messages.t("market.myListings", locale) + "\n" + myListings.take(5).joinToString("\n") { listing ->
                val name = runCatching { cardCatalog.card(listing.cardId).nameFor(locale) }.getOrElse { listing.cardId }
                "• $name"
            } + "\n\n" + Messages.t("market.hint", locale)
        }
        telegramClient.sendMessage(chatId, header, TelegramReplyMarkup(inlineKeyboard = buttons))
    }

    private fun handleMarketList(chatId: Long, user: User, cardId: String) {
        val locale = gameLocale(user)
        val owned = userCardRepository.findByUserIdAndCardId(user.id, cardId)
        if (owned == null || !TradePolicy.canOfferDuplicate(owned.quantity)) {
            telegramClient.sendMessage(chatId, Messages.t("market.noDuplicates", locale), mainMenuKeyboard(locale))
            return
        }
        // Keep one copy in the collection; only the duplicate goes to the market.
        userCardRepository.removeCard(user.id, cardId, 1)
        try {
            marketRepository.createListing(user.id, cardId)
            telegramClient.sendMessage(chatId, Messages.t("market.listed", locale), mainMenuKeyboard(locale))
        } catch (e: Exception) {
            logger.error("Failed to create market listing", e)
            runCatching { userCardRepository.addCards(user.id, listOf(cardId)) }
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
        }
    }

    private fun handleMarketReturn(chatId: Long, user: User, listingId: UUID) {
        val locale = gameLocale(user)
        val listing = marketRepository.findListingById(listingId)
        if (listing == null || listing.sellerId != user.id) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        marketRepository.cancelListing(listingId)
        userCardRepository.addCards(user.id, listOf(listing.cardId))
        telegramClient.sendMessage(chatId, Messages.t("market.returned", locale), mainMenuKeyboard(locale))
    }

    private fun handleMarketBrowse(chatId: Long, user: User, page: Int) {
        val locale = gameLocale(user)
        val others = marketRepository.findAllActiveListings().filter { it.sellerId != user.id }
        if (others.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noListings", locale), mainMenuKeyboard(locale))
            return
        }
        val pageItems = others.drop(page * 5).take(5)
        if (pageItems.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noListings", locale), mainMenuKeyboard(locale))
            return
        }
        val text = pageItems.map { listing ->
            val name = runCatching { cardCatalog.card(listing.cardId).nameFor(locale) }.getOrElse { listing.cardId }
            "🎴 $name"
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
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        val mine = marketRepository.findActiveListingsBySeller(user.id)
        if (mine.isEmpty()) {
            telegramClient.sendMessage(chatId, Messages.t("market.noDuplicates", locale), mainMenuKeyboard(locale))
            return
        }
        pendingMarketOffers[chatId] = targetId
        val targetName = runCatching { cardCatalog.card(target.cardId).nameFor(locale) }.getOrElse { target.cardId }
        val buttons = mine.take(10).map { listing ->
            val name = runCatching { cardCatalog.card(listing.cardId).nameFor(locale) }.getOrElse { listing.cardId }
            listOf(TelegramInlineButton(name, "m:off:${listing.id}"))
        }
        telegramClient.sendMessage(chatId, Messages.t("market.chooseOffer", locale, targetName), TelegramReplyMarkup(inlineKeyboard = buttons))
    }

    private fun handleMarketOffer(chatId: Long, user: User, offeredId: UUID) {
        val locale = gameLocale(user)
        val targetId = pendingMarketOffers.remove(chatId)
        if (targetId == null) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        val target = marketRepository.findListingById(targetId)
        val offered = marketRepository.findListingById(offeredId)
        if (target == null || offered == null || offered.sellerId != user.id || target.sellerId == user.id) {
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
            return
        }
        try {
            val offer = marketRepository.createTradeOffer(targetId, offeredId)
            telegramClient.sendMessage(chatId, Messages.t("market.offerMade", locale), mainMenuKeyboard(locale))
            // Notify the owner with accept/reject buttons.
            val targetName = runCatching { cardCatalog.card(target.cardId).nameFor(gameLocale(user)) }.getOrElse { target.cardId }
            val offeredName = runCatching { cardCatalog.card(offered.cardId).nameFor(gameLocale(user)) }.getOrElse { offered.cardId }
            val owner = userRepository.findById(target.sellerId)
            // Best effort: we can only notify if we knew the owner's chat id (= telegram id for 1:1 chats).
            if (owner != null) {
                runCatching {
                    telegramClient.sendMessage(
                        owner.telegramUserId,
                        Messages.t("market.offerReceived", gameLocale(owner), targetName, offeredName),
                        TelegramReplyMarkup(
                            inlineKeyboard = listOf(
                                listOf(
                                    TelegramInlineButton(Messages.t("market.accept", gameLocale(owner)), "m:acc:${offer.id}"),
                                    TelegramInlineButton(Messages.t("market.reject", gameLocale(owner)), "m:rej:${offer.id}"),
                                ),
                            ),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to create trade offer", e)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
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
                data.startsWith("m:acc:") -> {
                    val settled = settleMarketplaceOffer(UUID.fromString(data.removePrefix("m:acc:")), user.id)
                    telegramClient.sendMessage(
                        chatId,
                        Messages.t(if (settled) "market.offerAccepted" else "market.settlementFailed", locale),
                        mainMenuKeyboard(locale),
                    )
                }
                data.startsWith("m:rej:") -> {
                    marketRepository.rejectTradeOffer(UUID.fromString(data.removePrefix("m:rej:")))
                    telegramClient.sendMessage(chatId, Messages.t("market.offerRejected", locale), mainMenuKeyboard(locale))
                }
                // Backward-compatible long prefixes from earlier builds.
                data.startsWith("market:accept:") -> {
                    val settled = settleMarketplaceOffer(UUID.fromString(data.removePrefix("market:accept:")), user.id)
                    telegramClient.sendMessage(
                        chatId,
                        Messages.t(if (settled) "market.offerAccepted" else "market.settlementFailed", locale),
                        mainMenuKeyboard(locale),
                    )
                }
                data.startsWith("market:reject:") -> {
                    marketRepository.rejectTradeOffer(UUID.fromString(data.removePrefix("market:reject:")))
                    telegramClient.sendMessage(chatId, Messages.t("market.offerRejected", locale), mainMenuKeyboard(locale))
                }
                else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", locale), mainMenuKeyboard(locale))
            }
        }.onFailure {
            logger.error("Failed to handle market callback {}", data, it)
            telegramClient.sendMessage(chatId, Messages.t("error.general", locale), mainMenuKeyboard(locale))
        }
    }

    private fun settleMarketplaceOffer(offerId: UUID, actingUserId: Long): Boolean {
        return try {
            val offer = marketRepository.findOfferById(offerId) ?: return false
            if (offer.status.name != "PENDING") return false

            val targetListing = marketRepository.findListingById(offer.targetListingId) ?: return false
            val offeredListing = marketRepository.findListingById(offer.offeredListingId) ?: return false
            if (targetListing.status.name != "ACTIVE" || offeredListing.status.name != "ACTIVE") return false
            // Only the owner of the target listing can accept.
            if (targetListing.sellerId != actingUserId) return false

            val targetOwner = userRepository.findById(targetListing.sellerId) ?: return false
            val offerOwner = userRepository.findById(offeredListing.sellerId) ?: return false

            // Atomic card exchange: target owner gets the offered card and vice versa.
            // Cards were already removed from inventories when listed, so we only deal them out.
            userCardRepository.addCards(targetOwner.id, listOf(offeredListing.cardId))
            userCardRepository.addCards(offerOwner.id, listOf(targetListing.cardId))

            marketRepository.markListingSold(offer.targetListingId)
            marketRepository.markListingSold(offer.offeredListingId)
            marketRepository.acceptTradeOffer(offer.id)
            true
        } catch (e: Exception) {
            logger.error("Failed to settle marketplace offer", e)
            false
        }
    }

    // ---- Collections ----

    private fun sendCollectionView(chatId: Long, user: User, page: Int) {
        val locale = gameLocale(user)
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

    private fun sendThemesView(chatId: Long, user: User) {
        // Backward-compatible alias: themes == collections page 0.
        sendCollectionView(chatId, user, page = 0)
    }

    // ---- Routing ----

    private enum class Action {
        START, HELP, LANGUAGE, COLLECTION, PACK, BUY, PAYSUPPORT, TRADE, MARKET, UNKNOWN_COMMAND, UNKNOWN_TEXT
    }

    private fun resolveAction(text: String, locale: GameLocale): Action {
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
                "/buy" -> Action.BUY
                "/paysupport" -> Action.PAYSUPPORT
                "/trade" -> Action.TRADE
                "/market" -> Action.MARKET
                else -> Action.UNKNOWN_COMMAND
            }
        }

        val normalized = trimmed.lowercase().replace(" ", "")
        fun matches(key: String): Boolean {
            val other = if (locale == GameLocale.RU) GameLocale.EN else GameLocale.RU
            fun norm(l: GameLocale) = Messages.t(key, l).lowercase().replace(" ", "")
            return normalized == norm(locale) || normalized == norm(other)
        }

        return when {
            matches("menu.collection") -> Action.COLLECTION
            matches("menu.pack") -> Action.PACK
            matches("menu.buy") -> Action.BUY
            matches("menu.trade") -> Action.TRADE
            matches("menu.market") -> Action.MARKET
            matches("menu.language") -> Action.LANGUAGE
            else -> Action.UNKNOWN_TEXT
        }
    }

    private fun handleCallback(callback: TelegramCallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val telegramId = callback.from?.id ?: return
        val user = userRepository.findByTelegramUserId(telegramId) ?: run {
            // Handle language selection for new user
            callback.id?.let { telegramClient.answerCallbackQuery(it) }
            when (callback.data) {
                "lang:en" -> {
                    val newUser = userRepository.create(telegramId, GameLocale.EN.code)
                    grantStarterPacks(newUser.id)
                    telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.EN, properties.economy.starterPacks), openPackKeyboard(GameLocale.EN))
                }
                "lang:ru" -> {
                    val newUser = userRepository.create(telegramId, GameLocale.RU.code)
                    grantStarterPacks(newUser.id)
                    telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.RU, properties.economy.starterPacks), openPackKeyboard(GameLocale.RU))
                }
                else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
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
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
            }
            "lang:ru" -> {
                userRepository.updateLanguage(user.id, GameLocale.RU.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
            }
            "menu:collection" -> sendCollectionView(chatId, user, page = 0)
            "menu:pack", "menu:open-pack" -> handlePackOpening(chatId, user)
            "menu:buy" -> handleBuy(chatId, user)
            "free:card" -> handleFreeCardClaim(chatId, user)
            "buy:1" -> handleBuyCallback(chatId, user, 1)
            "buy:3" -> handleBuyCallback(chatId, user, 3)
            "buy:5" -> handleBuyCallback(chatId, user, 5)
            "buy:10" -> handleBuyCallback(chatId, user, 10)
            else -> {
                if (data == null) return
                when {
                    data.startsWith("trade:add:") -> handleTradeAdd(chatId, user, data.removePrefix("trade:add:"))
                    data.startsWith("col:page:") -> sendCollectionView(chatId, user, data.removePrefix("col:page:").toIntOrNull() ?: 0)
                    data.startsWith("m:") || data.startsWith("market:") -> handleMarketCallback(chatId, user, data)
                    else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
                }
            }
        }
    }

    private fun grantStarterPacks(userId: Long) {
        // Exactly 3 starter packs; the first free single card is due immediately
        // (last_free_card_at stays NULL until the first claim), then every 7h.
        packLedgerRepository.addPacks(userId, "starter", properties.economy.starterPacks)
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

    private fun mainMenuKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            keyboard = listOf(
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.collection", locale)),
                    TelegramKeyboardButton(Messages.t("menu.pack", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.trade", locale)),
                    TelegramKeyboardButton(Messages.t("menu.market", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.buy", locale)),
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

    private fun buyKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(TelegramInlineButton(Messages.t("buy.option1", locale), "buy:1")),
                listOf(TelegramInlineButton(Messages.t("buy.option3", locale), "buy:3")),
                listOf(TelegramInlineButton(Messages.t("buy.option5", locale), "buy:5")),
                listOf(TelegramInlineButton(Messages.t("buy.option10", locale), "buy:10")),
            ),
        )

    private fun openPackKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(TelegramInlineButton(Messages.t("menu.pack", locale), "menu:open-pack")),
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
                    telegramClient.sendMessage(chatId, Messages.t("collection.empty", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
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
                    telegramClient.sendMessage(chatId, Messages.t("collection.empty", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
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
                    null -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
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
        val keyboard = galleryKeyboard(locale, actionForIndex, index, total, card, ownedCount)
        val resource = ClassPathResource("static/assets/cards/${card.id}.png")

        val existing = chatGalleries[chatId]
        val cachedFileId = knownFileIds[card.id]

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
                if (cachedFileId == null) knownFileIds[card.id] = fileId
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

    private fun galleryCaption(
        card: CardDefinition,
        theme: ThemeDefinition?,
        ownedCount: Int,
        locale: GameLocale,
        index: Int,
        total: Int,
    ): String {
        val rarityLabel = card.rarity.emoji
        val name = card.nameFor(locale)
        val themeName = theme?.let { when (locale) {
            GameLocale.RU -> it.nameRu
            GameLocale.EN -> it.nameEn
        } } ?: ""
        val countText = if (ownedCount > 0) {
            Messages.t("gallery.owned", locale, ownedCount)
        } else {
            Messages.t("gallery.missing", locale)
        }
        val counter = Messages.t("gallery.counter", locale, index, total)

        return """
            $rarityLabel $name
            $themeName
            $countText
            $counter
        """.trimIndent()
    }

    private fun galleryKeyboard(
        locale: GameLocale,
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
            mainMenuKeyboard(locale),
        )
    }
}
