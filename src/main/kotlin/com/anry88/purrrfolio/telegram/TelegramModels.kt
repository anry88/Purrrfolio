package com.anry88.purrrfolio.telegram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdate(
    @JsonProperty("update_id")
    val updateId: Long? = null,
    val message: TelegramMessage? = null,
    @JsonProperty("callback_query")
    val callbackQuery: TelegramCallbackQuery? = null,
    @JsonProperty("inline_query")
    val inlineQuery: TelegramInlineQuery? = null,
    @JsonProperty("pre_checkout_query")
    val preCheckoutQuery: TelegramPreCheckoutQuery? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessage(
    @JsonProperty("message_id")
    val messageId: Long? = null,
    val text: String? = null,
    val chat: TelegramChat? = null,
    val from: TelegramUser? = null,
    val photo: List<TelegramPhotoSize>? = null,
    @JsonProperty("successful_payment")
    val successfulPayment: TelegramSuccessfulPayment? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramPhotoSize(
    @JsonProperty("file_id")
    val fileId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramCallbackQuery(
    val id: String? = null,
    val data: String? = null,
    val from: TelegramUser? = null,
    val message: TelegramMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramInlineQuery(
    val id: String? = null,
    val from: TelegramUser? = null,
    val query: String = "",
    val offset: String = "",
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
    @JsonProperty("is_bot")
    val isBot: Boolean? = null,
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
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramReplyMarkup(
    @JsonProperty("inline_keyboard")
    val inlineKeyboard: List<List<TelegramInlineButton>>? = null,
    @JsonProperty("keyboard")
    val keyboard: List<List<TelegramKeyboardButton>>? = null,
    @JsonProperty("resize_keyboard")
    val resizeKeyboard: Boolean? = null,
    @JsonProperty("one_time_keyboard")
    val oneTimeKeyboard: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelegramInlineButton(
    val text: String,
    @JsonProperty("callback_data")
    val callbackData: String? = null,
    val url: String? = null,
    @JsonProperty("switch_inline_query")
    val switchInlineQuery: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramAnswerInlineQueryRequest(
    @JsonProperty("inline_query_id")
    val inlineQueryId: String,
    val results: List<TelegramInlineQueryResultCachedPhoto>,
    @JsonProperty("cache_time")
    val cacheTime: Int = 0,
    @JsonProperty("is_personal")
    val isPersonal: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramInlineQueryResultCachedPhoto(
    val type: String = "photo",
    val id: String,
    @JsonProperty("photo_file_id")
    val photoFileId: String,
    val title: String? = null,
    val caption: String,
    @JsonProperty("parse_mode")
    val parseMode: String = "HTML",
    @JsonProperty("reply_markup")
    val replyMarkup: TelegramReplyMarkup? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramKeyboardButton(
    val text: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessageResponse(
    val ok: Boolean = false,
    val result: TelegramMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramEditMessageMediaRequest(
    @JsonProperty("chat_id")
    val chatId: Long,
    @JsonProperty("message_id")
    val messageId: Long,
    val media: Map<String, Any?>,
    @JsonProperty("reply_markup")
    val replyMarkup: TelegramReplyMarkup? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramPreCheckoutQuery(
    val id: String? = null,
    val from: TelegramUser? = null,
    val currency: String? = null,
    @JsonProperty("total_amount")
    val totalAmount: Int? = null,
    @JsonProperty("invoice_payload")
    val invoicePayload: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramSuccessfulPayment(
    val currency: String? = null,
    @JsonProperty("total_amount")
    val totalAmount: Int? = null,
    @JsonProperty("invoice_payload")
    val invoicePayload: String? = null,
    @JsonProperty("telegram_payment_charge_id")
    val telegramPaymentChargeId: String? = null,
    @JsonProperty("provider_payment_charge_id")
    val providerPaymentChargeId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramLabeledPrice(
    val label: String,
    val amount: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChatMemberCountResponse(
    val ok: Boolean = false,
    val result: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChatMemberResponse(
    val ok: Boolean = false,
    val result: TelegramChatMember? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChatMember(
    val user: TelegramChatMemberUser? = null,
    val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChatMemberUser(
    val id: Long? = null,
    @JsonProperty("is_bot")
    val isBot: Boolean? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    val username: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramBotCommand(
    val command: String,
    val description: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramBotCommandScope(
    val type: String,
    @JsonProperty("language_code")
    val languageCode: String? = null,
)
