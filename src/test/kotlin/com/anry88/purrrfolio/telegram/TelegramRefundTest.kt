package com.anry88.purrrfolio.telegram

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.web.client.RestClient
import org.springframework.web.client.HttpClientErrorException

class TelegramRefundTest {

    @Test
    fun `refund fails closed when bot token is missing`() {
        val client = TelegramClient(PurrrfolioProperties(), RestClient.builder(), ObjectMapper())

        assertThrows(IllegalStateException::class.java) {
            client.refundStarPayment(123, "charge-1")
        }
    }

    @Test
    fun `already refunded Telegram response permits local recovery`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.org/bottest-token/refundStarPayment"))
            .andRespond(
                withBadRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"ok":false,"error_code":400,"description":"Bad Request: CHARGE_ALREADY_REFUNDED"}"""),
            )
        val client = TelegramClient(
            PurrrfolioProperties(telegram = TelegramProperties(botToken = "test-token")),
            builder,
            ObjectMapper(),
        )

        assertDoesNotThrow { client.refundStarPayment(123, "charge-1") }
        server.verify()
    }

    @Test
    fun `unrelated Telegram refund error remains a failure`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("https://api.telegram.org/bottest-token/refundStarPayment"))
            .andRespond(withBadRequest().body("invalid charge"))
        val client = TelegramClient(
            PurrrfolioProperties(telegram = TelegramProperties(botToken = "test-token")),
            builder,
            ObjectMapper(),
        )

        assertThrows(HttpClientErrorException.BadRequest::class.java) {
            client.refundStarPayment(123, "charge-1")
        }
        server.verify()
    }
}
