package com.anry88.purrrfolio.web

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.anry88.purrrfolio.game.GameService
import com.anry88.purrrfolio.game.RetryableTelegramUpdateException
import com.anry88.purrrfolio.telegram.TelegramUpdate
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

class WebhookControllerTest {

    @Test
    fun `processing failure is propagated so Telegram can retry`() {
        val gameService = mock(GameService::class.java)
        val update = TelegramUpdate(updateId = 123)
        doThrow(RetryableTelegramUpdateException(IllegalStateException("database offline")))
            .`when`(gameService).handle(update)
        val controller = WebhookController(
            gameService,
            PurrrfolioProperties(telegram = TelegramProperties(webhookSecret = "secret")),
        )

        assertThrows(RetryableTelegramUpdateException::class.java) {
            controller.webhook("secret", update)
        }
    }
}
