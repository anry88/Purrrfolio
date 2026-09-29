package com.anry88.purrrfolio.telegram

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class TelegramDeliveryTest {

    private fun telegramError(status: HttpStatus, body: String): HttpClientErrorException =
        HttpClientErrorException.create(
            status,
            status.reasonPhrase,
            HttpHeaders.EMPTY,
            body.toByteArray(Charsets.UTF_8),
            Charsets.UTF_8,
        )

    @Test
    fun `blocked bot is unreachable`() {
        val error = telegramError(
            HttpStatus.FORBIDDEN,
            """{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked by the user"}""",
        )
        assertThat(error).isInstanceOf(HttpClientErrorException.Forbidden::class.java)
        assertThat(TelegramClient.isUnreachableRecipient(error)).isTrue()
    }

    @Test
    fun `missing chat is unreachable`() {
        val error = telegramError(
            HttpStatus.BAD_REQUEST,
            """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""",
        )
        assertThat(TelegramClient.isUnreachableRecipient(error)).isTrue()
    }

    @Test
    fun `deactivated user is unreachable`() {
        val error = telegramError(
            HttpStatus.FORBIDDEN,
            """{"ok":false,"error_code":403,"description":"Forbidden: user is deactivated"}""",
        )
        assertThat(TelegramClient.isUnreachableRecipient(error)).isTrue()
    }

    @Test
    fun `rate limit is not unreachable`() {
        val error = telegramError(
            HttpStatus.TOO_MANY_REQUESTS,
            """{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 5"}""",
        )
        assertThat(TelegramClient.isUnreachableRecipient(error)).isFalse()
    }

    @Test
    fun `other bad request is not unreachable`() {
        val error = telegramError(
            HttpStatus.BAD_REQUEST,
            """{"ok":false,"error_code":400,"description":"Bad Request: message text is empty"}""",
        )
        assertThat(TelegramClient.isUnreachableRecipient(error)).isFalse()
    }

    @Test
    fun `non-telegram errors are not unreachable`() {
        assertThat(TelegramClient.isUnreachableRecipient(IllegalStateException("boom"))).isFalse()
    }
}
