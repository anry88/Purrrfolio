package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup

internal data class RandomTradeCardOption(
    val cardId: String,
    val name: String,
    val cardLine: String,
)

internal data class WaitingRandomTradeOption(
    val tradeId: Long,
    val name: String,
    val cardLine: String,
)

internal data class RandomTradeMenu(
    val text: String,
    val replyMarkup: TelegramReplyMarkup,
)

internal fun buildRandomTradeMenu(
    locale: GameLocale,
    duplicates: List<RandomTradeCardOption>,
    waiting: List<WaitingRandomTradeOption>,
): RandomTradeMenu? {
    if (duplicates.isEmpty() && waiting.isEmpty()) return null

    val buttons = duplicates.take(10).map { card ->
        listOf(
            TelegramInlineButton(
                Messages.t("trade.offerButton", locale, card.name),
                "trade:add:${card.cardId}",
            ),
        )
    }.toMutableList()

    waiting.take(5).forEach { trade ->
        buttons.add(
            listOf(
                TelegramInlineButton(
                    Messages.t("trade.returnButton", locale, trade.name),
                    "trade:ret:${trade.tradeId}",
                ),
            ),
        )
    }

    val duplicatesSection = if (duplicates.isEmpty()) {
        ""
    } else {
        "\n" + duplicates.take(10).joinToString("\n") { it.cardLine }
    }

    val intro = if (duplicates.isEmpty()) {
        Messages.t("trade.title", locale)
    } else {
        Messages.t("trade.hint", locale) + "\n\n" + Messages.t("trade.pickCard", locale) + duplicatesSection
    }
    val waitingSection = if (waiting.isEmpty()) {
        ""
    } else {
        "\n\n" + Messages.t("trade.waiting", locale) + "\n" +
            waiting.take(5).joinToString("\n") { it.cardLine }
    }

    return RandomTradeMenu(
        text = intro + waitingSection,
        replyMarkup = TelegramReplyMarkup(inlineKeyboard = buttons),
    )
}
