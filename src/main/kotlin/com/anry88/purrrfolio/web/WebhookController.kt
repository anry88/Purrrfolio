package com.anry88.purrrfolio.web

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.game.GameService
import com.anry88.purrrfolio.telegram.TelegramUpdate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

@RestController
class WebhookController(
    private val gameService: GameService,
    private val properties: PurrrfolioProperties,
) {
    @GetMapping("/")
    fun index() = mapOf(
        "name" to "Purrrfolio",
        "status" to "operational",
        "bot" to "@${properties.telegram.botUsername}",
    )

    @GetMapping("/health")
    fun health() = mapOf("status" to "UP")

    @PostMapping("/bot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun webhook(
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) providedSecret: String?,
        @RequestBody update: TelegramUpdate,
    ) {
        val expected = properties.telegram.webhookSecret
        if (expected.isBlank() || providedSecret == null || !constantTimeEquals(expected, providedSecret)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        gameService.handle(update)
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean = MessageDigest.isEqual(
        expected.toByteArray(Charsets.UTF_8),
        actual.toByteArray(Charsets.UTF_8),
    )
}
