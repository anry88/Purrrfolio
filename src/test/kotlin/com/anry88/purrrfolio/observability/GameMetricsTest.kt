package com.anry88.purrrfolio.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameMetricsTest {

    @Test
    fun `commands normalize to known set`() {
        assertEquals("start", GameMetrics.normalizeCommand("/start"))
        assertEquals("pack", GameMetrics.normalizeCommand("/pack@PurrrfolioBot"))
        assertEquals("freecard", GameMetrics.normalizeCommand("freecard"))
        assertEquals("unknown", GameMetrics.normalizeCommand("/drop table"))
        assertEquals("unknown", GameMetrics.normalizeCommand(""))
    }

    @Test
    fun `callbacks map to bounded actions`() {
        assertEquals("gallery", GameMetrics.callbackAction("gal:col:3"))
        assertEquals("gallery", GameMetrics.callbackAction("col:page:1"))
        assertEquals("trade", GameMetrics.callbackAction("trade:add:sleepy"))
        assertEquals("trade", GameMetrics.callbackAction("trade:ret:42"))
        assertEquals("market", GameMetrics.callbackAction("m:brw:0"))
        assertEquals("market", GameMetrics.callbackAction("market:accept:xxx"))
        assertEquals("buy", GameMetrics.callbackAction("buy:3"))
        assertEquals("menu", GameMetrics.callbackAction("menu:pack"))
        assertEquals("language", GameMetrics.callbackAction("lang:ru"))
        assertEquals("freecard", GameMetrics.callbackAction("free:card"))
        assertEquals("craft", GameMetrics.callbackAction("craft:add:rainy"))
        assertEquals("trade", GameMetrics.callbackAction("trade:add:sleepy:extra:long:tail"))
        assertEquals("unknown", GameMetrics.callbackAction("???"))
    }

    @Test
    fun `registration sources stay bounded`() {
        assertEquals("direct", GameMetrics.normalizeRegistrationSource(null))
        assertEquals("direct", GameMetrics.normalizeRegistrationSource(""))
        assertEquals("direct", GameMetrics.normalizeRegistrationSource("direct"))
        assertEquals("blog", GameMetrics.normalizeRegistrationSource("  Blog "))
        assertEquals("tg-games", GameMetrics.normalizeRegistrationSource("TG-GAMES"))
        assertEquals("other", GameMetrics.normalizeRegistrationSource("not a code!"))
        assertEquals("other", GameMetrics.normalizeRegistrationSource("x".repeat(65)))
    }

    @Test
    fun `opened pack source is reported as positive count`() {
        assertEquals(11, GameMetrics.packSourceGaugeValue("opened", -11))
        assertEquals(3, GameMetrics.packSourceGaugeValue("refund", -3))
        assertEquals(3, GameMetrics.packSourceGaugeValue("starter", 3))
        assertEquals(2, GameMetrics.packSourceGaugeValue("craft", 2))
    }
}
