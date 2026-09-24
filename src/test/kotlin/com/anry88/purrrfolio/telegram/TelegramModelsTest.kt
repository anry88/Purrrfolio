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
    fun `deserializes sender bot flag`() {
        val payload =
            """
            {
              "update_id": 918273647,
              "message": {
                "message_id": 44,
                "text": "кот",
                "chat": {"id": -100, "type": "supergroup"},
                "from": {
                  "id": 1087968824,
                  "first_name": "GroupAnonymousBot",
                  "is_bot": true
                }
              }
            }
            """.trimIndent()

        val update = objectMapper.readValue(payload, TelegramUpdate::class.java)

        assertThat(update.message?.from?.isBot).isTrue()
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

    @Test
    fun `deserializes inline query`() {
        val payload =
            """
            {
              "update_id": 918273648,
              "inline_query": {
                "id": "inline-1",
                "from": {"id": 456, "first_name": "Cat"},
                "query": "share:42:sleepy",
                "offset": ""
              }
            }
            """.trimIndent()

        val update = objectMapper.readValue(payload, TelegramUpdate::class.java)

        assertThat(update.inlineQuery?.id).isEqualTo("inline-1")
        assertThat(update.inlineQuery?.from?.id).isEqualTo(456)
        assertThat(update.inlineQuery?.query).isEqualTo("share:42:sleepy")
    }

    @Test
    fun `deserializes sendPhoto response with photo file ids`() {
        val payload =
            """
            {
              "ok": true,
              "result": {
                "message_id": 100,
                "chat": {"id": 123, "type": "private"},
                "photo": [
                  {"file_id": "small-file", "width": 320, "height": 320},
                  {"file_id": "large-file", "width": 640, "height": 640}
                ]
              }
            }
            """.trimIndent()

        val response = objectMapper.readValue(payload, TelegramMessageResponse::class.java)

        assertThat(response.ok).isTrue()
        assertThat(response.result?.messageId).isEqualTo(100)
        assertThat(response.result?.photo?.lastOrNull()?.fileId).isEqualTo("large-file")
    }

    @Test
    fun `serializes editMessageMedia request with photo media`() {
        val request = TelegramEditMessageMediaRequest(
            chatId = 123,
            messageId = 100,
            media = mapOf("type" to "photo", "media" to "large-file", "caption" to "*Sleepy*"),
            replyMarkup = TelegramReplyMarkup(
                inlineKeyboard = listOf(
                    listOf(TelegramInlineButton("▶️", "gal:col:1")),
                ),
            ),
        )

        val json = objectMapper.writeValueAsString(request)

        assertThat(json).contains("\"chat_id\":123")
        assertThat(json).contains("\"message_id\":100")
        assertThat(json).contains("\"media\":\"large-file\"")
        assertThat(json).contains("\"callback_data\":\"gal:col:1\"")
    }

    @Test
    fun `serializes scoped bot commands payload`() {
        val scope = TelegramBotCommandScope(type = "all_private_chats", languageCode = "ru")
        val payload = mapOf(
            "commands" to listOf(TelegramBotCommand("pack", "Открыть набор")),
            "scope" to scope,
        )

        val json = objectMapper.writeValueAsString(payload)

        assertThat(json).contains("\"type\":\"all_private_chats\"")
        assertThat(json).contains("\"language_code\":\"ru\"")
        assertThat(json).contains("\"command\":\"pack\"")
        assertThat(json).contains("\"description\":\"Открыть набор\"")
    }

    @Test
    fun `serializes switch inline query button without visible URL`() {
        val button = TelegramInlineButton(
            text = "📤 Поделиться карточкой",
            switchInlineQuery = "share:42:sleepy",
        )

        val json = objectMapper.writeValueAsString(button)

        assertThat(json).contains("\"switch_inline_query\":\"share:42:sleepy\"")
        assertThat(json).doesNotContain("\"url\"")
        assertThat(json).doesNotContain("callback_data")
    }

    @Test
    fun `serializes cached photo inline answer with HTML caption`() {
        val request = TelegramAnswerInlineQueryRequest(
            inlineQueryId = "inline-1",
            results = listOf(
                TelegramInlineQueryResultCachedPhoto(
                    id = "share-sleepy",
                    photoFileId = "telegram-file-id",
                    caption = "<a href=\"https://t.me/PurrrfolioBot?start=ref_42\">Start</a>",
                    replyMarkup = TelegramReplyMarkup(
                        inlineKeyboard = listOf(
                            listOf(TelegramInlineButton("Start", url = "https://t.me/PurrrfolioBot?start=ref_42")),
                        ),
                    ),
                ),
            ),
        )

        val json = objectMapper.writeValueAsString(request)

        assertThat(json).contains("\"inline_query_id\":\"inline-1\"")
        assertThat(json).contains("\"photo_file_id\":\"telegram-file-id\"")
        assertThat(json).contains("\"parse_mode\":\"HTML\"")
        assertThat(json).contains("\"is_personal\":true")
        assertThat(json).doesNotContain("resize_keyboard")
        assertThat(json).doesNotContain("one_time_keyboard")
    }
}
