package com.anry88.purrrfolio.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TelegramModelsTest {
    private val objectMapper = ObjectMapper().registerModule(kotlinModule())

    @Test
    fun `deserializes Telegram snake case update fields`() {
        val payload =
            """
            {
              "update_id": 918273645,
              "message": {
                "message_id": 42,
                "text": "/start",
                "chat": {"id": 123, "type": "private"},
                "from": {
                  "id": 456,
                  "username": "cat_owner",
                  "first_name": "Cat",
                  "language_code": "ru"
                }
              }
            }
            """.trimIndent()

        val update = objectMapper.readValue(payload, TelegramUpdate::class.java)

        assertThat(update.updateId).isEqualTo(918273645)
        assertThat(update.message?.messageId).isEqualTo(42)
        assertThat(update.message?.from?.firstName).isEqualTo("Cat")
        assertThat(update.message?.from?.languageCode).isEqualTo("ru")
    }

    @Test
    fun `deserializes callback query`() {
        val payload =
            """
            {
              "update_id": 918273646,
              "callback_query": {
                "id": "callback-1",
                "data": "menu:collection",
                "from": {"id": 456, "first_name": "Cat"},
                "message": {
                  "message_id": 43,
                  "chat": {"id": 123, "type": "private"}
                }
              }
            }
            """.trimIndent()

        val update = objectMapper.readValue(payload, TelegramUpdate::class.java)

        assertThat(update.updateId).isEqualTo(918273646)
        assertThat(update.callbackQuery?.id).isEqualTo("callback-1")
        assertThat(update.callbackQuery?.message?.messageId).isEqualTo(43)
    }
}
