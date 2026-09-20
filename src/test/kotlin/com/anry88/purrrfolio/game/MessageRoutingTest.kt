package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageRoutingTest {

    @Test
    fun `ignores arbitrary text and unsupported commands`() {
        assertThat(GameService.resolveAction("hello everyone", GameLocale.EN)).isNull()
        assertThat(GameService.resolveAction("привет всем", GameLocale.RU)).isNull()
        assertThat(GameService.resolveAction("/unknown", GameLocale.EN)).isNull()
        assertThat(GameService.resolveAction("", GameLocale.EN)).isNull()
    }

    @Test
    fun `recognizes supported commands including bot mentions`() {
        assertThat(GameService.resolveAction("/help", GameLocale.EN)).isEqualTo(GameService.Action.HELP)
        assertThat(GameService.resolveAction("/pack@purrrfolio_bot", GameLocale.RU)).isEqualTo(GameService.Action.PACK)
        assertThat(GameService.resolveAction("/start referral", GameLocale.EN)).isEqualTo(GameService.Action.START)
    }

    @Test
    fun `recognizes word aliases and localized keyboard labels`() {
        assertThat(GameService.resolveAction("  котик!  ", GameLocale.RU)).isEqualTo(GameService.Action.FREECARD)
        assertThat(GameService.resolveAction("marketplace", GameLocale.EN)).isEqualTo(GameService.Action.MARKET)
        assertThat(GameService.resolveAction("🎁 Набор", GameLocale.EN)).isEqualTo(GameService.Action.PACK)
        assertThat(GameService.resolveAction("🗂 Collection", GameLocale.RU)).isEqualTo(GameService.Action.COLLECTION)
    }
}
