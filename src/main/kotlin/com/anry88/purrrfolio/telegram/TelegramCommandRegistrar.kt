package com.anry88.purrrfolio.telegram

import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelegramCommandRegistrar(private val telegramClient: TelegramClient) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun registerCommands() {
        if (!telegramClient.isConfigured()) return

        runCatching {
            telegramClient.setMyCommands(
                commandsFor(GameLocale.EN),
                TelegramBotCommandScope(type = "default"),
            )
        }.onFailure { logger.warn("Failed to register default bot commands", it) }

        runCatching {
            telegramClient.setMyCommands(
                commandsFor(GameLocale.RU),
                TelegramBotCommandScope(type = "all_private_chats", languageCode = "ru"),
            )
        }.onFailure { logger.warn("Failed to register RU bot commands", it) }
    }

    private fun commandsFor(locale: GameLocale) = listOf(
        TelegramBotCommand("start", Messages.t("cmd.start", locale)),
        TelegramBotCommand("collection", Messages.t("cmd.collection", locale)),
        TelegramBotCommand("pack", Messages.t("cmd.pack", locale)),
        TelegramBotCommand("buy", Messages.t("cmd.buy", locale)),
        TelegramBotCommand("trade", Messages.t("cmd.trade", locale)),
        TelegramBotCommand("market", Messages.t("cmd.market", locale)),
        TelegramBotCommand("language", Messages.t("cmd.language", locale)),
        TelegramBotCommand("help", Messages.t("cmd.help", locale)),
        TelegramBotCommand("paysupport", Messages.t("cmd.paysupport", locale)),
    )
}
