package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomTradeMenuTest {

    @Test
    fun `shows empty state only when duplicates and waiting trades are both absent`() {
        assertThat(buildRandomTradeMenu(GameLocale.EN, emptyList(), emptyList())).isNull()
    }

    @Test
    fun `shows waiting trades with return buttons when no duplicates remain`() {
        val menu = buildRandomTradeMenu(
            locale = GameLocale.EN,
            duplicates = emptyList(),
            waiting = listOf(WaitingRandomTradeOption(42, "Sleepy Cat")),
        )

        assertThat(menu).isNotNull
        assertThat(menu!!.text).contains("Waiting in the pool", "Sleepy Cat")
        assertThat(menu.text).doesNotContain("Pick a duplicate")
        assertThat(menu.replyMarkup.inlineKeyboard.orEmpty().flatten().single().callbackData)
            .isEqualTo("trade:ret:42")
    }

    @Test
    fun `shows duplicate and waiting trade actions together`() {
        val menu = buildRandomTradeMenu(
            locale = GameLocale.RU,
            duplicates = listOf(RandomTradeCardOption("chef-cat", "Шеф-кот")),
            waiting = listOf(WaitingRandomTradeOption(7, "Кот-учитель")),
        )

        assertThat(menu).isNotNull
        assertThat(menu!!.replyMarkup.inlineKeyboard.orEmpty().flatten().map { it.callbackData })
            .containsExactly("trade:add:chef-cat", "trade:ret:7")
    }
}
