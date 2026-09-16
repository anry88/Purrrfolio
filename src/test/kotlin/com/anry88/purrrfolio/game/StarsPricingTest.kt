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
    fun `free pack timer starts at registration`() {
        val now = OffsetDateTime.now()
        // Legacy pack timer helper still works generically.
        assertEquals(23, GameService.hoursUntilFreePack(now, now, 23))
        // Free single card: due immediately at start, then every 7h.
        assertEquals(0, GameService.hoursUntilFreePack(null, now, 7))
        assertEquals(7, GameService.hoursUntilFreePack(now, now, 7))
        assertEquals(1, GameService.hoursUntilFreePack(now, now.plusHours(6), 7))
        assertEquals(0, GameService.hoursUntilFreePack(now, now.plusHours(7), 7))
    }
}
