package com.anry88.purrrfolio.telegram

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.anry88.purrrfolio.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.junit.jupiter.api.assertThrows

class TelegramKeyboardDeliveryTest {
    private val users = mock(UserRepository::class.java)
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = TelegramClient(
        PurrrfolioProperties(telegram = TelegramProperties(botToken = "test")), builder, ObjectMapper().registerModule(kotlinModule()), users,
    )
    private val reply = TelegramReplyMarkup(keyboard = listOf(listOf(TelegramKeyboardButton("🎁 Pack"))), resizeKeyboard = true)
    private val inline = TelegramReplyMarkup(inlineKeyboard = listOf(listOf(TelegramInlineButton("🎁 Pack", "menu:pack"))))

    @ParameterizedTest
    @CsvSource("private,123", "group,-123", "supergroup,-100123")
    fun `reply keyboard is preserved only in private chat`(type: String, chatId: Long) {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
            .andExpect { request ->
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                if (type == "private") {
                    assertThat(body).contains("\"keyboard\"", "resize_keyboard").doesNotContain("remove_keyboard")
                } else {
                    assertThat(body).contains("\"remove_keyboard\":true").doesNotContain("\"keyboard\"", "resize_keyboard")
                }
            }.andRespond(withSuccess())
        client.sendMessage(chatId, "Pack ready", reply)
        server.verify()
    }

    @Test
    fun `ordinary group message removes legacy keyboard without another request`() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"remove_keyboard\":true")))
            .andRespond(withSuccess())
        client.sendMessage(-100123, "Opening a pack...")
        server.verify()
    }

    @Test
    fun `inline actions remain under group messages`() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
            .andExpect { request ->
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                assertThat(body).contains("inline_keyboard", "menu:pack").doesNotContain("remove_keyboard", "resize_keyboard")
            }.andRespond(withSuccess())
        client.sendMessage(-100123, "Pack ready", inline)
        server.verify()
    }

    @Test
    fun `photo multipart cannot reintroduce a group reply keyboard`() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendPhoto"))
            .andExpect { request ->
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                assertThat(body).contains("\"remove_keyboard\":true").doesNotContain("\"keyboard\"", "resize_keyboard")
            }.andRespond(withSuccess("""{"ok":true,"result":{"message_id":1}}""", org.springframework.http.MediaType.APPLICATION_JSON))
        client.sendPhoto(-100123, object : ByteArrayResource(byteArrayOf(1, 2)) {
            override fun getFilename() = "card.png"
        }, "Cat", reply)
        server.verify()
    }

    @Test
    fun `terminal private failure is persisted and already blocked recipients make no HTTP request`() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("bot was blocked by the user"))
        assertThrows<HttpClientErrorException> { client.sendMessage(123, "Reminder") }
        verify(users).markTelegramBlocked(123)
        `when`(users.isTelegramBlocked(123)).thenReturn(true)
        assertThrows<HttpClientErrorException> { client.sendMessage(123, "Market update") }
        server.verify()
    }
}
