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
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.models.UserCard
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.ProcessedUpdateRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
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
    private val userRepository: UserRepository,
    private val userCardRepository: UserCardRepository,
    private val packLedgerRepository: PackLedgerRepository,
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

    fun handle(update: TelegramUpdate) {
        val updateId = update.updateId
        if (updateId != null) {
            if (!processedUpdateRepository.recordUpdate(updateId)) {
                logger.warn("Skipping already processed update {}", updateId)
                return
            }
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
            Action.COLLECTION -> sendCollectionView(chatId, user)
            Action.PACK -> handlePackOpening(chatId, user)
            Action.THEMES -> sendThemesView(chatId, user)
            Action.TRADE -> telegramClient.sendMessage(chatId, Messages.t("trade.hint", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
            Action.MARKET -> telegramClient.sendMessage(chatId, Messages.t("market.hint", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
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

    private fun handlePackOpening(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val availablePacks = packLedgerRepository.getTotalAvailablePacks(user.id)
        
        if (availablePacks <= 0) {
            // Check if free pack is available
            val now = OffsetDateTime.now()
            val lastFree = user.lastFreePackOpenedAt
            val hoursSinceLastFree = if (lastFree != null) ChronoUnit.HOURS.between(lastFree, now).toInt() else Int.MAX_VALUE
            
            if (hoursSinceLastFree >= properties.economy.freePackIntervalHours) {
                // Grant free pack
                packLedgerRepository.addPacks(user.id, "free", 1)
                userRepository.updateLastFreePackOpenedAt(user.id, now)
                telegramClient.sendMessage(chatId, Messages.t("pack.freeAvailable", locale), mainMenuKeyboard(locale))
            } else {
                val hoursLeft = properties.economy.freePackIntervalHours - hoursSinceLastFree.toInt()
                telegramClient.sendMessage(
                    chatId,
                    Messages.t("pack.noPacks", locale) + "\n" + Messages.t("pack.nextFreeIn", locale, hoursLeft),
                    mainMenuKeyboard(locale),
                )
            }
            return
        }

        // Open pack
        packLedgerRepository.addPacks(user.id, "opened", -1)
        telegramClient.sendMessage(chatId, Messages.t("pack.opening", locale))

        // Roll cards
        val currentInventory = userCardRepository.findByUserId(user.id).map { it.cardId.toString() }.toSet()
        val rolledCards = packOpeningService.rollCards(properties.economy.cardsPerPack, currentInventory)

        // Save cards
        userCardRepository.addCards(user.id, rolledCards.map { it.id.toLong() })

        for (card in rolledCards) {
            val isNew = !currentInventory.contains(card.id.toString())
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

    private enum class Action {
        START, HELP, LANGUAGE, COLLECTION, PACK, THEMES, TRADE, MARKET, UNKNOWN_COMMAND, UNKNOWN_TEXT
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
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.EN, properties.economy.starterPacks), mainMenuKeyboard(GameLocale.EN))
                }
                "lang:ru" -> {
                    val newUser = userRepository.create(telegramId, GameLocale.RU.code)
                    grantStarterPacks(newUser.id)
                    telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
                    telegramClient.sendMessage(chatId, Messages.t("pack.starter", GameLocale.RU, properties.economy.starterPacks), mainMenuKeyboard(GameLocale.RU))
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

        when (callback.data) {
            "lang:en" -> {
                userRepository.updateLanguage(user.id, GameLocale.EN.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
            }
            "lang:ru" -> {
                userRepository.updateLanguage(user.id, GameLocale.RU.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
            }
            "menu:collection" -> sendCollectionView(chatId, user)
            "menu:pack" -> handlePackOpening(chatId, user)
            "menu:themes" -> sendThemesView(chatId, user)
            else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(user)), mainMenuKeyboard(gameLocale(user)))
        }
    }

    private fun grantStarterPacks(userId: Long) {
        packLedgerRepository.addPacks(userId, "starter", properties.economy.starterPacks)
    }

    private fun sendCollectionView(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val userCards = userCardRepository.findByUserId(user.id)
        val keyboard = if (userCards.isEmpty()) {
            mainMenuKeyboard(locale)
        } else {
            TelegramReplyMarkup(
                inlineKeyboard = listOf(
                    listOf(TelegramInlineButton(Messages.t("gallery.viewCards", locale), GalleryActions.collection(0))),
                ),
            )
        }
        telegramClient.sendMessage(chatId, buildCollectionPreview(user, userCards), keyboard)
    }

    private fun sendThemesView(chatId: Long, user: User) {
        val locale = gameLocale(user)
        val ownedCardIds = userCardRepository.findByUserId(user.id).map { it.cardId.toString() }.toSet()
        val progress = collectionService.buildThemeProgress(ownedCardIds, emptySet())
        telegramClient.sendMessage(chatId, buildThemesPreview(progress, locale), themesKeyboard(progress, locale))
    }

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
                val cards = cardCatalog.cardsByCollection(action.themeId)
                if (cards.isEmpty()) return
                val index = action.index.coerceIn(0, cards.size - 1)
                if (index != action.index) return
                val quantities = ownedQuantities(user.id)
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
                    GalleryKey.Collection -> sendCollectionView(chatId, user)
                    is GalleryKey.Theme -> sendThemesView(chatId, user)
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
        val keyboard = galleryKeyboard(locale, actionForIndex, index, total)
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

    private fun buildCollectionPreview(user: User, userCards: List<UserCard>): String {
        val locale = gameLocale(user)
        if (userCards.isEmpty()) {
            return Messages.t("collection.empty", locale)
        }

        val lines = userCards.take(15).mapNotNull { uc ->
            runCatching { cardCatalog.card(uc.cardId.toString()) }.getOrNull()?.let { card ->
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
            .mapNotNull { uc -> runCatching { cardCatalog.card(uc.cardId.toString()) }.getOrNull() }
            .distinctBy { it.id }

    private fun ownedQuantities(userId: Long): Map<String, Int> =
        userCardRepository.findByUserId(userId).associate { it.cardId.toString() to it.quantity }

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
                    TelegramKeyboardButton(Messages.t("menu.language", locale)),
                ),
            ),
            resizeKeyboard = true,
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

    private fun galleryKeyboard(locale: GameLocale, actionForIndex: (Int) -> String, index: Int, total: Int): TelegramReplyMarkup {
        val buttons = mutableListOf<List<TelegramInlineButton>>()
        
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
}
