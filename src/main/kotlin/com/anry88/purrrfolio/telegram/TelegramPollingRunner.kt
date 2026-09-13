package com.anry88.purrrfolio.telegram

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.game.GameService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

@Component
@ConditionalOnProperty(prefix = "purrrfolio.telegram", name = ["polling-enabled"], havingValue = "true")
class TelegramPollingRunner(
    private val properties: PurrrfolioProperties,
    private val telegramClient: TelegramClient,
    private val gameService: GameService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val started = AtomicBoolean(false)
    private val nextOffset = AtomicLong(0)

    @EventListener(ApplicationReadyEvent::class)
    fun startPolling() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        if (properties.telegram.botToken.isBlank()) {
            logger.warn("Telegram polling is enabled but bot token is blank")
            return
        }

        val identity = telegramClient.getMe()
        if (identity?.username != null) {
            logger.info("Telegram bot ready for polling: @{}", identity.username)
        } else {
            logger.warn("Telegram getMe failed; polling will still attempt to start")
        }

        telegramClient.deleteWebhook(dropPendingUpdates = false)

        thread(name = "telegram-polling", isDaemon = true) {
            logger.info("Telegram long polling started")
            while (true) {
                try {
                    val updates = telegramClient.getUpdates(nextOffset.get())
                    for (update in updates) {
                        update.updateId?.plus(1)?.let(nextOffset::set)
                        gameService.handle(update)
                    }
                } catch (exception: Exception) {
                    logger.error("Telegram polling iteration failed", exception)
                    Thread.sleep(2_000)
                }
            }
        }
    }
}
