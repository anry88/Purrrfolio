package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.player.Player
import com.anry88.purrrfolio.player.PlayerCardRepository
import com.anry88.purrrfolio.player.PlayerRepository
import com.anry88.purrrfolio.telegram.TelegramCallbackQuery
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramKeyboardButton
import com.anry88.purrrfolio.telegram.TelegramMessage
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import com.anry88.purrrfolio.telegram.TelegramUpdate
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
            displayName = message.from.firstName
        )

        val text = message.text?.trim().orEmpty()
        val action = resolveAction(text)
        
        // Custom logic for commands that might send photos or do multiple things
        when (action) {
            Action.START -> {
                telegramClient.sendMessage(chatId, GameGuide.WELCOME, mainMenuKeyboard())
            }
            Action.HELP -> {
                telegramClient.sendMessage(chatId, GameGuide.HELP, mainMenuKeyboard())
            }
            Action.COLLECTION -> {
                telegramClient.sendMessage(chatId, buildCollectionPreview(player), mainMenuKeyboard())
            }
            Action.PACK -> {
                handlePackOpening(chatId, player)
            }
            Action.THEMES -> {
                telegramClient.sendMessage(chatId, buildThemesPreview(player), mainMenuKeyboard())
            }
            Action.DAILY -> {
                handleDaily(chatId, player)
            }
            Action.PROFILE -> {
                telegramClient.sendMessage(chatId, buildProfilePreview(player), mainMenuKeyboard())
            }
            Action.TRADE -> {
                telegramClient.sendMessage(chatId, "Обмен дубликатами: `/trade @username sleepy` — предложить карточку другу.\nНужен минимум 2 копии одной карточки.", mainMenuKeyboard())
            }
            Action.MARKET -> {
                telegramClient.sendMessage(chatId, "Биржа: `/market list sleepy 40` — продать дубликат.\n`/market browse` — посмотреть лоты.", mainMenuKeyboard())
            }
            Action.UNKNOWN_COMMAND -> telegramClient.sendMessage(chatId, "Неизвестная команда. Напиши /help.", mainMenuKeyboard())
            Action.UNKNOWN_TEXT -> telegramClient.sendMessage(chatId, "Используй /help, чтобы увидеть доступные команды.", mainMenuKeyboard())
        }
    }

    private fun handleDaily(chatId: Long, player: Player) {
        val now = OffsetDateTime.now()
        val lastDaily = player.lastDailyAt
        if (lastDaily != null && ChronoUnit.HOURS.between(lastDaily, now) < 24) {
            val hoursLeft = 24 - ChronoUnit.HOURS.between(lastDaily, now)
            telegramClient.sendMessage(chatId, "Ежедневная награда уже получена! Возвращайся через $hoursLeft ч.", mainMenuKeyboard())
            return
        }
        
        val newBalance = player.fishBalance + properties.economy.dailyFish
        playerRepository.updateFishBalance(player.id, newBalance)
        playerRepository.updateLastDailyAt(player.id, now)
        
        telegramClient.sendMessage(chatId, "Ежедневная награда: +${properties.economy.dailyFish} 🐟\nТвой баланс: $newBalance 🐟", mainMenuKeyboard())
    }

    private fun handlePackOpening(chatId: Long, player: Player) {
        val cost = properties.economy.packCostFish
        if (player.fishBalance < cost) {
            telegramClient.sendMessage(chatId, "Недостаточно рыбок! Набор стоит $cost 🐟, а у тебя ${player.fishBalance} 🐟.", mainMenuKeyboard())
            return
        }
        
        // Deduct balance
        val newBalance = player.fishBalance - cost
        playerRepository.updateFishBalance(player.id, newBalance)
        telegramClient.sendMessage(chatId, "Открываю набор... Списано $cost 🐟 (остаток: $newBalance 🐟)")
        
        // Roll cards
        val currentInventory = playerCardRepository.getPlayerCards(player.id).map { it.cardId }.toSet()
        val rolledCards = packOpeningService.rollCards(properties.economy.cardsPerPack, currentInventory)
        
        // Save cards
        playerCardRepository.addCards(player.id, rolledCards.map { it.id })
        
        // Send photos
        for (card in rolledCards) {
            val isNew = !currentInventory.contains(card.id)
            val caption = packOpeningService.formatReveal(card, isNew)
            
            val resource = ClassPathResource("static/assets/cards/${card.id}.png")
            if (resource.exists()) {
                telegramClient.sendPhoto(chatId, resource, caption)
            } else {
                telegramClient.sendMessage(chatId, caption)
            }
        }
        telegramClient.sendMessage(chatId, "Набор открыт!", mainMenuKeyboard())
    }

    private enum class Action {
        START, HELP, COLLECTION, PACK, THEMES, TRADE, MARKET, DAILY, PROFILE, UNKNOWN_COMMAND, UNKNOWN_TEXT
    }

    private fun resolveAction(text: String): Action {
        val command = text.substringBefore(' ').substringBefore('@').lowercase()
        return when (command) {
            "/start" -> Action.START
            "/help" -> Action.HELP
            "/collection" -> Action.COLLECTION
            "/pack" -> Action.PACK
            "/themes" -> Action.THEMES
            "/trade" -> Action.TRADE
            "/market" -> Action.MARKET
            "/daily" -> Action.DAILY
            "/profile" -> Action.PROFILE
            "🗂 коллекция" -> Action.COLLECTION
            "🎁 набор" -> Action.PACK
            "📚 темы" -> Action.THEMES
            "🤝 обмен" -> Action.TRADE
            "🏪 биржа" -> Action.MARKET
            "👤 профиль" -> Action.PROFILE
            else -> if (text.startsWith("/")) Action.UNKNOWN_COMMAND else Action.UNKNOWN_TEXT
        }
    }

    private fun handleCallback(callback: TelegramCallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val telegramId = callback.from?.id ?: return
        val player = playerRepository.findByTelegramId(telegramId) ?: return

        when (callback.data) {
            "menu:collection" -> telegramClient.sendMessage(chatId, buildCollectionPreview(player), mainMenuKeyboard())
            "menu:pack" -> handlePackOpening(chatId, player)
            "menu:themes" -> telegramClient.sendMessage(chatId, buildThemesPreview(player), mainMenuKeyboard())
            else -> telegramClient.sendMessage(chatId, "Раздел в разработке.", mainMenuKeyboard())
        }
    }

    private fun buildCollectionPreview(player: Player): String {
        val playerCards = playerCardRepository.getPlayerCards(player.id)
        if (playerCards.isEmpty()) {
            return "Твоя коллекция пока пуста. Открой свой первый /pack !"
        }
        
        val lines = playerCards.take(15).mapNotNull { pc ->
            runCatching { cardCatalog.card(pc.cardId) }.getOrNull()?.let { card ->
                "${card.rarity.emoji} ${card.nameRu} (x${pc.quantity})"
            }
        }.joinToString("\n")
        
        val uniqueCount = playerCards.size
        return """
            🗂 *Твоя коллекция* ($uniqueCount / ${cardCatalog.cards.size})

            $lines
            ${if (playerCards.size > 15) "\n...и еще ${playerCards.size - 15} карт." else ""}
        """.trimIndent()
    }

    private fun buildThemesPreview(player: Player): String {
        val playerCards = playerCardRepository.getPlayerCards(player.id).map { it.cardId }.toSet()
        // theme claims not yet implemented in DB, just pass empty set for now
        val progress = collectionService.buildThemeProgress(playerCards, emptySet())
        val lines = progress.joinToString("\n") { collectionService.formatThemeLine(it) }
        return """
            📚 *Темы коллекции*

            $lines
        """.trimIndent()
    }

    private fun buildProfilePreview(player: Player): String =
        """
            👤 *${player.displayName ?: "Игрок"}*

            🐟 Рыбки: ${player.fishBalance}
            🃏 Уникальных карточек: ${playerCardRepository.getPlayerCards(player.id).size} / ${cardCatalog.cards.size}
            🎯 Завершённых тем: 0 / ${cardCatalog.themes.size}
        """.trimIndent()

    private fun mainMenuKeyboard(): TelegramReplyMarkup =
        TelegramReplyMarkup(
            keyboard = listOf(
                listOf(
                    TelegramKeyboardButton("🗂 Коллекция"),
                    TelegramKeyboardButton("🎁 Набор"),
                ),
                listOf(
                    TelegramKeyboardButton("📚 Темы"),
                    TelegramKeyboardButton("🤝 Обмен"),
                ),
                listOf(
                    TelegramKeyboardButton("🏪 Биржа"),
                    TelegramKeyboardButton("👤 Профиль"),
                ),
            ),
        )
}
