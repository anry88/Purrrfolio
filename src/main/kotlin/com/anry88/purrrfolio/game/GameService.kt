package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.player.Player
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

    fun handle(update: TelegramUpdate) {
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
            Action.COLLECTION -> telegramClient.sendMessage(chatId, buildCollectionPreview(player), mainMenuKeyboard(gameLocale(player)))
            Action.PACK -> handlePackOpening(chatId, player)
            Action.THEMES -> telegramClient.sendMessage(chatId, buildThemesPreview(player), mainMenuKeyboard(gameLocale(player)))
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
            } catch (e: Exception) {
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

        when (callback.data) {
            "lang:en" -> {
                playerRepository.updateLocale(player.id, GameLocale.EN.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.EN), mainMenuKeyboard(GameLocale.EN))
            }
            "lang:ru" -> {
                playerRepository.updateLocale(player.id, GameLocale.RU.code)
                telegramClient.sendMessage(chatId, Messages.t("language.changed", GameLocale.RU), mainMenuKeyboard(GameLocale.RU))
            }
            "menu:collection" -> telegramClient.sendMessage(chatId, buildCollectionPreview(player), mainMenuKeyboard(gameLocale(player)))
            "menu:pack" -> handlePackOpening(chatId, player)
            "menu:themes" -> telegramClient.sendMessage(chatId, buildThemesPreview(player), mainMenuKeyboard(gameLocale(player)))
            else -> telegramClient.sendMessage(chatId, Messages.t("callback.underDevelopment", gameLocale(player)), mainMenuKeyboard(gameLocale(player)))
        }
    }

    private fun buildCollectionPreview(player: Player): String {
        val locale = gameLocale(player)
        val playerCards = playerCardRepository.getPlayerCards(player.id)
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

    private fun buildThemesPreview(player: Player): String {
        val locale = gameLocale(player)
        val playerCards = playerCardRepository.getPlayerCards(player.id).map { it.cardId }.toSet()
        // theme claims not yet implemented in DB, just pass empty set for now
        val progress = collectionService.buildThemeProgress(playerCards, emptySet())
        val lines = progress.joinToString("\n") { collectionService.formatThemeLine(it, locale) }
        return """
            ${Messages.t("themes.title", locale)}

            $lines
        """.trimIndent()
    }

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