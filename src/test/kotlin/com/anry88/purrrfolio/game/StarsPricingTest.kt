package com.anry88.purrrfolio.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class StarsPricingTest {

    private val signingSecret = "test-payment-payload-secret"

    @Test
    fun `stars map to pack bundles per spec`() {
        assertEquals(1, GameService.packsForStars(5))
        assertEquals(3, GameService.packsForStars(12))
        assertEquals(5, GameService.packsForStars(16))
        assertEquals(10, GameService.packsForStars(25))
        assertNull(GameService.packsForStars(7))
    }

    @Test
    fun `packs map to stars per spec`() {
        assertEquals(5, GameService.starsForPacks(1))
        assertEquals(12, GameService.starsForPacks(3))
        assertEquals(16, GameService.starsForPacks(5))
        assertEquals(25, GameService.starsForPacks(10))
        assertNull(GameService.starsForPacks(2))
    }

    @Test
    fun `payload round-trips valid bundles only`() {
        val payload = GameService.starsPayload(3, 12, 12345, signingSecret)
        assertEquals(3, GameService.packsFromPayload(payload, signingSecret))
        assertEquals(12, GameService.parseStarsOrder(payload, signingSecret)?.stars)
        assertEquals(12345, GameService.parseStarsOrder(payload, signingSecret)?.buyerTelegramId)
        assertNull(GameService.packsFromPayload(GameService.starsPayload(2, 7, 12345, signingSecret), signingSecret))
        assertNull(GameService.packsFromPayload("purrrfolio:packs:3", signingSecret))
        assertNull(GameService.packsFromPayload("purrrfolio:packs:3:stars:12:user:not-a-number", signingSecret))
        assertNull(GameService.packsFromPayload("garbage", signingSecret))
        assertNull(GameService.packsFromPayload(null, signingSecret))
        assertNull(GameService.packsFromPayload(payload, "wrong-secret"))
        assertNull(GameService.packsFromPayload(payload.replace(":stars:12:", ":stars:1:"), signingSecret))
    }

    @Test
    fun `Stars payment must match buyer currency and payload amount`() {
        val order = GameService.parseStarsOrder(GameService.starsPayload(3, 12, 12345, signingSecret), signingSecret)
        assertEquals(true, GameService.matchesStarsPayment(order, 12345, "XTR", 12))
        assertEquals(false, GameService.matchesStarsPayment(order, 99, "XTR", 12))
        assertEquals(false, GameService.matchesStarsPayment(order, 12345, "USD", 12))
        assertEquals(false, GameService.matchesStarsPayment(order, 12345, "XTR", 25))
        assertEquals(false, GameService.matchesStarsPayment(null, 12345, "XTR", 12))
        val underpriced = GameService.parseStarsOrder(GameService.starsPayload(3, 1, 12345, signingSecret), signingSecret)
        assertEquals(false, GameService.matchesPricedStarsPayment(underpriced, 12345, "XTR", 1, 12))
    }

    @Test
    fun `permanent Telegram client errors are not retried forever`() {
        assertEquals(false, GameService.isRetryableTelegramClientStatus(400))
        assertEquals(false, GameService.isRetryableTelegramClientStatus(403))
        assertEquals(true, GameService.isRetryableTelegramClientStatus(408))
        assertEquals(true, GameService.isRetryableTelegramClientStatus(429))
    }

    @Test
    fun `free card timer starts immediately and rounds up to minutes`() {
        val now = OffsetDateTime.now()
        assertEquals(0, GameService.minutesUntilFreeCard(null, now, 3))
        assertEquals(180, GameService.minutesUntilFreeCard(now, now, 3))
        assertEquals(1, GameService.minutesUntilFreeCard(now, now.plusHours(2).plusMinutes(59).plusSeconds(1), 3))
        assertEquals(0, GameService.minutesUntilFreeCard(now, now.plusHours(3), 3))
    }

    @Test
    fun `plain words route to common game actions in both languages`() {
        assertEquals("pack", GameService.textCommandAlias("  Набор! "))
        assertEquals("craft", GameService.textCommandAlias("крафт"))
        assertEquals("market", GameService.textCommandAlias("Marketplace"))
        assertEquals("freecard", GameService.textCommandAlias("котейка 🐾"))
        assertEquals("freecard", GameService.textCommandAlias("Kitty"))
        assertNull(GameService.textCommandAlias("hello"))
    }

    @Test
    fun `telegram group and supergroup types enable group drops`() {
        assertEquals(true, GameService.isGroupChat("group"))
        assertEquals(true, GameService.isGroupChat("supergroup"))
        assertEquals(false, GameService.isGroupChat("private"))
        assertEquals(false, GameService.isGroupChat(null))
    }

    @Test
    fun `payment admin commands require the configured private chat and sender`() {
        assertEquals(true, GameService.isAuthorizedPaymentAdmin(42, 42, 42))
        assertEquals(false, GameService.isAuthorizedPaymentAdmin(42, 42, 99))
        assertEquals(false, GameService.isAuthorizedPaymentAdmin(42, -100123, 42))
        assertEquals(false, GameService.isAuthorizedPaymentAdmin(0, 0, 0))
    }
}
