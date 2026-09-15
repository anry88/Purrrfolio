package com.anry88.purrrfolio.telegram

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TelegramClient(
    properties: PurrrfolioProperties,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val botToken = properties.telegram.botToken
    private val restClient = restClientBuilder.baseUrl("https://api.telegram.org/bot$botToken/").build()

    fun isConfigured(): Boolean = botToken.isNotBlank()

    fun getMe(): TelegramBotIdentity? {
        if (!isConfigured()) {
            return null
        }
        return restClient.get()
            .uri("getMe")
            .retrieve()
            .body(TelegramApiResponse::class.java)
            ?.result
    }

    fun deleteWebhook(dropPendingUpdates: Boolean = true) {
        if (!isConfigured()) {
            return
        }
        restClient.post()
            .uri("deleteWebhook?drop_pending_updates=$dropPendingUpdates")
            .retrieve()
            .toBodilessEntity()
    }

    fun getUpdates(offset: Long, timeoutSeconds: Int = 25): List<TelegramUpdate> {
        if (!isConfigured()) {
            return emptyList()
        }
        return restClient.get()
            .uri("getUpdates?offset=$offset&timeout=$timeoutSeconds")
            .retrieve()
            .body(TelegramUpdatesResponse::class.java)
            ?.result
            .orEmpty()
    }

    fun sendMessage(chatId: Long, text: String, replyMarkup: TelegramReplyMarkup? = null) {
        if (!isConfigured()) {
            logger.warn("Telegram bot token is not configured; skipping sendMessage")
            return
        }
        restClient.post()
            .uri("sendMessage")
            .body(
                TelegramSendMessageRequest(
                    chatId = chatId,
                    text = text,
                    replyMarkup = replyMarkup,
                ),
            )
            .retrieve()
            .toBodilessEntity()
    }

    fun answerCallbackQuery(callbackQueryId: String) {
        if (!isConfigured()) {
            logger.warn("Telegram bot token is not configured; skipping answerCallbackQuery")
            return
        }
        restClient.post()
            .uri("answerCallbackQuery")
            .body(mapOf("callback_query_id" to callbackQueryId))
            .retrieve()
            .toBodilessEntity()
    }

    fun sendPhoto(chatId: Long, photoResource: Resource, caption: String? = null, replyMarkup: TelegramReplyMarkup? = null): TelegramMessage? {
        if (!isConfigured()) {
            logger.warn("Telegram bot token is not configured; skipping sendPhoto")
            return null
        }
        val bodyBuilder = MultipartBodyBuilder()
        bodyBuilder.part("chat_id", chatId)
        bodyBuilder.part("photo", photoResource)
        if (caption != null) {
            bodyBuilder.part("caption", caption)
            bodyBuilder.part("parse_mode", "Markdown")
        }
        if (replyMarkup != null) {
            bodyBuilder.part("reply_markup", objectMapper.writeValueAsString(replyMarkup))
                .contentType(MediaType.APPLICATION_JSON)
        }

        try {
            return restClient.post()
                .uri("sendPhoto")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(TelegramMessageResponse::class.java)
                ?.takeIf { it.ok }
                ?.result
        } catch (e: Exception) {
            logger.warn("Failed to send photo to chat {}", chatId, e)
            throw e
        }
    }

    fun editMessageMedia(chatId: Long, messageId: Long, media: Map<String, Any?>, replyMarkup: TelegramReplyMarkup? = null) {
        if (!isConfigured()) {
            logger.warn("Telegram bot token is not configured; skipping editMessageMedia")
            return
        }
        try {
            restClient.post()
                .uri("editMessageMedia")
                .body(TelegramEditMessageMediaRequest(chatId, messageId, media, replyMarkup))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            logger.warn("Failed to edit message media in chat {}", chatId, e)
            throw e
        }
    }

    fun setMyCommands(commands: List<TelegramBotCommand>, scope: TelegramBotCommandScope) {
        if (!isConfigured()) return
        restClient.post()
            .uri("setMyCommands")
            .body(mapOf("commands" to commands, "scope" to scope))
            .retrieve()
            .toBodilessEntity()
    }

    fun deleteMessage(chatId: Long, messageId: Long) {
        if (!isConfigured()) {
            logger.warn("Telegram bot token is not configured; skipping deleteMessage")
            return
        }
        try {
            restClient.post()
                .uri("deleteMessage")
                .body(mapOf("chat_id" to chatId, "message_id" to messageId))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            logger.warn("Failed to delete message {} in chat {}", messageId, chatId, e)
            throw e
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramApiResponse(
    val ok: Boolean = false,
    val result: TelegramBotIdentity? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdatesResponse(
    val ok: Boolean = false,
    val result: List<TelegramUpdate> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramBotIdentity(
    val id: Long? = null,
    val username: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("is_bot")
    val isBot: Boolean? = null,
)
