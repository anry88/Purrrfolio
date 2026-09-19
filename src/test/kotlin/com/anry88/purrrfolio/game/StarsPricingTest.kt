package com.anry88.purrrfolio.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class StarsPricingTest {

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
        assertEquals(3, GameService.packsFromPayload(GameService.starsPayload(3)))
        assertNull(GameService.packsFromPayload(GameService.starsPayload(2)))
        assertNull(GameService.packsFromPayload("garbage"))
        assertNull(GameService.packsFromPayload(null))
    }

    @Test
    fun `free card timer starts immediately and rounds up to minutes`() {
        val now = OffsetDateTime.now()
        assertEquals(0, GameService.minutesUntilFreeCard(null, now, 3))
        assertEquals(180, GameService.minutesUntilFreeCard(now, now, 3))
        assertEquals(1, GameService.minutesUntilFreeCard(now, now.plusHours(2).plusMinutes(59).plusSeconds(1), 3))
        assertEquals(0, GameService.minutesUntilFreeCard(now, now.plusHours(3), 3))
    }
}
