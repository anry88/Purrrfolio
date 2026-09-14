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
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.player.Player
import com.anry88.purrrfolio.player.PlayerCard
import com.anry88.purrrfolio.player.PlayerCardRepository
import com.anry88.purrrfolio.player.PlayerRepository
import com.anry88.purrrfolio.telegram.TelegramCallbackQuery
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramKeyboardButton
import com.anry88.purrrfolio.telegram.TelegramMessage
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import com.anry88.purrrfolio.telegram.TelegramUpdate
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

@Service
class GameService(
    private val properties: PurrrfolioProperties,
    private val cardCatalog: CardCatalog,
    private val collectionService: CollectionService,
    private val packOpeningService: PackOpeningService,
    private val playerRepository: PlayerRepository,
    private val playerCardRepository: PlayerCardRepository,
    private val telegramClient: TelegramClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val processedUpdateIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

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

    fun handle(update: TelegramUpdate) {
        val updateId = update.updateId
        if (updateId != null && !processedUpdateIds.add(updateId)) {
            logger.warn("Skipping already processed update {}", updateId)
            return
        }
        if (processedUpdateIds.size > 1000) {
            processedUpdateIds.clear()
        }
        update.callbackQuery?.let {
            handleCallback(it)
            return
        }
        update.message?.let {
            handleMessage(it)
        }
    }

    private fun handleMessage(message: TelegramMessage) {
        val chatId = message.chat?.id ?: return
        val telegramId = message.from?.id ?: return

        val player = playerRepository.findOrCreate(
            telegramId = telegramId,
            username = message.from.username,
            displayName = message.from.firstName,
            languageCode = message.from.languageCode,
        )

        val text = message.text?.trim().orEmpty()
        val action = resolveAction(text, gameLocale(player))

        when (action) {
            Action.START -> telegramClient.sendMessage(chatId, Messages.t("welcome", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
            Action.HELP -> telegramClient.sendMessage(chatId, Messages.t("help", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
            Action.LANGUAGE -> handleLanguage(chatId, player)
            Action.COLLECTION -> sendCollectionView(chatId, player)
            Action.PACK -> handlePackOpening(chatId, player)
            Action.THEMES -> sendThemesView(chatId, player)
            Action.DAILY -> handleDaily(chatId, player)
            Action.PROFILE -> telegramClient.sendMessage(chatId, buildProfilePreview(player), mainMenuKeyboard(gameLocale(player)))
            Action.TRADE -> telegramClient.sendMessage(chatId, Messages.t("trade.hint", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
            Action.MARKET -> telegramClient.sendMessage(chatId, Messages.t("market.hint", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
            Action.UNKNOWN_COMMAND -> telegramClient.sendMessage(chatId, Messages.t("unknownCommand", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
            Action.UNKNOWN_TEXT -> telegramClient.sendMessage(chatId, Messages.t("unknownText", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
        }
    }

    private fun handleDaily(chatId: Long, player: Player) {
        val locale = gameLocale(player)
        val now = OffsetDateTime.now()
        val lastDaily = player.lastDailyAt
        if (lastDaily != null && ChronoUnit.HOURS.between(lastDaily, now) < 24) {
            val hoursLeft = 24 - ChronoUnit.HOURS.between(lastDaily, now)
            telegramClient.sendMessage(chatId, Messages.t("daily.already", locale, hoursLeft), mainMenuKeyboard(locale))
            return
        }

        val newBalance = player.fishBalance + properties.economy.dailyFish
        playerRepository.updateFishBalance(player.id, newBalance)
        playerRepository.updateLastDailyAt(player.id, now)

        telegramClient.sendMessage(
            chatId,
            Messages.t("daily.reward", locale, properties.economy.dailyFish, newBalance),
            mainMenuKeyboard(locale),
        )
    }

    private fun handlePackOpening(chatId: Long, player: Player) {
        val locale = gameLocale(player)
        val cost = properties.economy.packCostFish
        if (player.fishBalance < cost) {
            telegramClient.sendMessage(
                chatId,
                Messages.t("pack.insufficient", locale, cost, player.fishBalance),
                mainMenuKeyboard(locale),
            )
            return
        }

        // Deduct balance
        val newBalance = player.fishBalance - cost
        playerRepository.updateFishBalance(player.id, newBalance)
        telegramClient.sendMessage(chatId, Messages.t("pack.opening", locale, cost, newBalance))

        // Roll cards
        val currentInventory = playerCardRepository.getPlayerCards(player.id).map { it.cardId }.toSet()
        val rolledCards = packOpeningService.rollCards(properties.economy.cardsPerPack, currentInventory)

        // Save cards
        playerCardRepository.addCards(player.id, rolledCards.map { it.id })

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

    private fun handleLanguage(chatId: Long, player: Player) {
        val locale = gameLocale(player)
        telegramClient.sendMessage(chatId, Messages.t("language.title", locale), languageKeyboard())
    }

    private enum class Action {
        START, HELP, LANGUAGE, COLLECTION, PACK, THEMES, TRADE, MARKET, DAILY, PROFILE, UNKNOWN_COMMAND, UNKNOWN_TEXT
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
                "/pack" -> Action.PACK
                "/themes" -> Action.THEMES
                "/trade" -> Action.TRADE
                "/market" -> Action.MARKET
                "/daily" -> Action.DAILY
                "/profile" -> Action.PROFILE
                else -> Action.UNKNOWN_COMMAND
            }
        }

        // Reply-keyboard button presses arrive as plain text labels ("🎁 Набор", "🌐 Language").
        // Match the full label case- and space-insensitively, accepting labels from either
        // supported language so stale buttons still work after a locale switch.
        val normalized = trimmed.lowercase().replace(" ", "")
        fun matches(key: String): Boolean {
            val other = if (locale == GameLocale.RU) GameLocale.EN else GameLocale.RU
            fun norm(l: GameLocale) = Messages.t(key, l).lowercase().replace(" ", "")
            return normalized == norm(locale) || normalized == norm(other)
        }

        return when {
            matches("menu.collection") -> Action.COLLECTION
            matches("menu.pack") -> Action.PACK
            matches("menu.themes") -> Action.THEMES
            matches("menu.trade") -> Action.TRADE
            matches("menu.market") -> Action.MARKET
            matches("menu.profile") -> Action.PROFILE
            matches("menu.language") -> Action.LANGUAGE
            else -> Action.UNKNOWN_TEXT
        }
    }

    private fun handleCallback(callback: TelegramCallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val telegramId = callback.from?.id ?: return
        val player = playerRepository.findByTelegramId(telegramId) ?: return

        callback.id?.let { telegramClient.answerCallbackQuery(it) }

        callback.data?.let { data ->
            GalleryActions.parse(data)?.let { action ->
                handleGalleryAction(chatId, player, callback.message?.messageId, action)
                return
            }
        }

        when (callback.data) {
            "lang:en" -> {
                playerRepository.updateLocale(player.id, GameLocale.EN.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
            }
            "lang:ru" -> {
                playerRepository.updateLocale(player.id, GameLocale.RU.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
            }
            "menu:collection" -> sendCollectionView(chatId, player)
            "menu:pack" -> handlePackOpening(chatId, player)
            "menu:themes" -> sendThemesView(chatId, player)
            else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
        }
    }

    private fun sendCollectionView(chatId: Long, player: Player) {
        val locale = gameLocale(player)
        val playerCards = playerCardRepository.getPlayerCards(player.id)
        val keyboard = if (playerCards.isEmpty()) {
            mainMenuKeyboard(locale)
        } else {
            TelegramReplyMarkup(
                inlineKeyboard = listOf(
                    listOf(TelegramInlineButton(Messages.t("gallery.viewCards", locale), GalleryActions.collection(0))),
                ),
            )
        }
        telegramClient.sendMessage(chatId, buildCollectionPreview(player, playerCards), keyboard)
    }

    private fun sendThemesView(chatId: Long, player: Player) {
        val locale = gameLocale(player)
        val ownedCardIds = playerCardRepository.getPlayerCards(player.id).map { it.cardId }.toSet()
        val progress = collectionService.buildThemeProgress(ownedCardIds, emptySet())
        telegramClient.sendMessage(chatId, buildThemesPreview(progress, locale), themesKeyboard(progress, locale))
    }

    private fun handleGalleryAction(chatId: Long, player: Player, tappedMessageId: Long?, action: GalleryAction) {
        when (action) {
            is GalleryAction.Collection -> {
                val ownedCards = ownedUniqueCards(player.id)
                if (ownedCards.isEmpty()) {
                    telegramClient.sendMessage(chatId, Messages.t("collection.empty", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
                    return
                }
                val index = action.index.coerceIn(0, ownedCards.size - 1)
                if (index != action.index) return
                val quantities = ownedQuantities(player.id)
                val card = ownedCards[index]
                showGalleryCard(
                    chatId = chatId,
                    player = player,
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
                val cards = cardCatalog.cardsByTheme(action.themeId)
                if (cards.isEmpty()) return
                val index = action.index.coerceIn(0, cards.size - 1)
                if (index != action.index) return
                val quantities = ownedQuantities(player.id)
                val card = cards[index]
                showGalleryCard(
                    chatId = chatId,
                    player = player,
                    tappedMessageId = tappedMessageId,
                    key = GalleryKey.Theme(action.themeId),
                    card = card,
                    theme = cardCatalog.theme(action.themeId),
                    ownedCount = quantities[card.id] ?: 0,
                    index = index,
                    total = cards.size,
                    actionForIndex = { i -> GalleryActions.theme(action.themeId, i) },
                )
            }
            GalleryAction.Back -> {
                val key = chatGalleries.remove(chatId)?.key
                when (key) {
                    GalleryKey.Collection -> sendCollectionView(chatId, player)
                    is GalleryKey.Theme -> sendThemesView(chatId, player)
                    null -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
                }
            }
        }
    }

    private fun showGalleryCard(
        chatId: Long,
        player: Player,
        tappedMessageId: Long?,
        key: GalleryKey,
        card: CardDefinition,
        theme: ThemeDefinition?,
        ownedCount: Int,
        index: Int,
        total: Int,
        actionForIndex: (Int) -> String,
    ) {
        val locale = gameLocale(player)
        val caption = galleryCaption(card, theme, ownedCount, locale, index + 1, total)
        val keyboard = galleryKeyboard(locale, actionForIndex, index, total)
        val resource = ClassPathResource("static/assets/cards/${card.id}.png")

        val existing = chatGalleries[chatId]
        if (existing != null && existing.messageId == tappedMessageId && existing.key == key && resource.exists()) {
            val media = mapOf(
                "type" to "photo",
                "media" to existing.fileId,
                "caption" to caption,
                "parse_mode" to "Markdown",
            )
            try {
                telegramClient.editMessageMedia(chatId, existing.messageId, media, keyboard)
                chatGalleries[chatId] = existing.copy(index = index)
                return
            } catch (e: Throwable) {
                logger.warn("Failed to edit gallery photo in chat {}", chatId, e)
            }
        }

        try {
            if (resource.exists()) {
                val sent = telegramClient.sendPhoto(chatId, resource, caption, keyboard)
                val messageId = sent?.messageId
                val fileId = sent?.photo?.lastOrNull()?.fileId
                if (messageId != null && fileId != null) {
                    chatGalleries[chatId] = ChatGallery(messageId, fileId, key, index)
                } else {
                    chatGalleries.remove(chatId)
                }
            } else {
                telegramClient.sendMessage(chatId, caption, keyboard)
                chatGalleries.remove(chatId)
            }
        } catch (e: Throwable) {
            logger.warn("Failed to send gallery photo for {}", card.id, e)
            runCatching { telegramClient.sendMessage(chatId, caption, keyboard) }
            chatGalleries.remove(chatId)
        }
    }

    private fun buildCollectionPreview(player: Player, playerCards: List<PlayerCard>): String {
        val locale = gameLocale(player)
        if (playerCards.isEmpty()) {
            return Messages.t("collection.empty", locale)
        }

        val lines = playerCards.take(15).mapNotNull { pc ->
            runCatching { cardCatalog.card(pc.cardId) }.getOrNull()?.let { card ->
                "${card.rarity.emoji} ${card.nameFor(locale)} (x${pc.quantity})"
            }
        }.joinToString("\n")

        val uniqueCount = playerCards.size
        return """
            ${Messages.t("collection.title", locale, uniqueCount, cardCatalog.cards.size)}

            $lines
            ${if (playerCards.size > 15) Messages.t("collection.more", locale, playerCards.size - 15) else ""}
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

    private fun ownedUniqueCards(playerId: Long): List<CardDefinition> =
        playerCardRepository.getPlayerCards(playerId)
            .mapNotNull { pc -> runCatching { cardCatalog.card(pc.cardId) }.getOrNull() }
            .distinctBy { it.id }

    private fun ownedQuantities(playerId: Long): Map<String, Int> =
        playerCardRepository.getPlayerCards(playerId).associate { it.cardId to it.quantity }

    private fun buildProfilePreview(player: Player): String {
        val locale = gameLocale(player)
        return """
            ${Messages.t("profile.title", locale, player.displayName ?: Messages.t("profile.player", locale))}

            ${Messages.t("profile.fish", locale, player.fishBalance)}
            ${Messages.t("profile.unique", locale, playerCardRepository.getPlayerCards(player.id).size, cardCatalog.cards.size)}
            ${Messages.t("profile.themes", locale, 0, cardCatalog.themes.size)}
        """.trimIndent()
    }

    private fun gameLocale(player: Player): GameLocale = GameLocale.fromCode(player.locale)

    private fun mainMenuKeyboard(locale: GameLocale): TelegramReplyMarkup =
        TelegramReplyMarkup(
            keyboard = listOf(
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.collection", locale)),
                    TelegramKeyboardButton(Messages.t("menu.pack", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.themes", locale)),
                    TelegramKeyboardButton(Messages.t("menu.trade", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.market", locale)),
                    TelegramKeyboardButton(Messages.t("menu.profile", locale)),
                ),
                listOf(
                    TelegramKeyboardButton(Messages.t("menu.language", locale)),
                ),
            ),
        )

    private fun languageKeyboard(): TelegramReplyMarkup =
        TelegramReplyMarkup(
            inlineKeyboard = listOf(
                listOf(
                    TelegramInlineButton("🇬🇧 English", "lang:en"),
                    TelegramInlineButton("🇷🇺 Русский", "lang:ru"),
                ),
            ),
        )
}