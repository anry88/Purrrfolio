package com.anry88.purrrfolio.telegram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdate(
    @JsonProperty("update_id")
    val updateId: Long? = null,
    val message: TelegramMessage? = null,
    @JsonProperty("callback_query")
    val callbackQuery: TelegramCallbackQuery? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessage(
    @JsonProperty("message_id")
    val messageId: Long? = null,
    val text: String? = null,
    val chat: TelegramChat? = null,
    val from: TelegramUser? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramCallbackQuery(
    val id: String? = null,
    val data: String? = null,
    val from: TelegramUser? = null,
    val message: TelegramMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChat(
    val id: Long? = null,
    val type: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUser(
    val id: Long? = null,
    val username: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("language_code")
    val languageCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramSendMessageRequest(
    @JsonProperty("chat_id")
    val chatId: Long,
    val text: String,
    @JsonProperty("parse_mode")
    val parseMode: String? = "Markdown",
    @JsonProperty("reply_markup")
    val replyMarkup: TelegramReplyMarkup? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramReplyMarkup(
    @JsonProperty("inline_keyboard")
    val inlineKeyboard: List<List<TelegramInlineButton>>? = null,
    @JsonProperty("keyboard")
    val keyboard: List<List<TelegramKeyboardButton>>? = null,
    @JsonProperty("resize_keyboard")
    val resizeKeyboard: Boolean? = true,
    @JsonProperty("one_time_keyboard")
    val oneTimeKeyboard: Boolean? = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramInlineButton(
    val text: String,
    @JsonProperty("callback_data")
    val callbackData: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramKeyboardButton(
    val text: String,
)
