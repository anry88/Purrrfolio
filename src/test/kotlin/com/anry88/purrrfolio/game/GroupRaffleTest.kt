package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class GroupRaffleTest {

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    @Test
    fun `packs grow by one per ten members and cap at ten`() {
        assertEquals(0, GameService.packsForRaffle(9))
        assertEquals(1, GameService.packsForRaffle(10))
        assertEquals(1, GameService.packsForRaffle(19))
        assertEquals(2, GameService.packsForRaffle(20))
        assertEquals(9, GameService.packsForRaffle(99))
        assertEquals(10, GameService.packsForRaffle(100))
        assertEquals(10, GameService.packsForRaffle(500))
    }

    @Test
    fun `raffle is due without history or after cooldown`() {
        assertTrue(GameService.isRaffleDue(null, now))
        assertTrue(GameService.isRaffleDue(now.minusHours(25), now))
        assertTrue(GameService.isRaffleDue(now.minusHours(24), now))
        assertFalse(GameService.isRaffleDue(now.minusHours(23), now))
        assertFalse(GameService.isRaffleDue(now, now))
    }

    @Test
    fun `mention names never break markdown links`() {
        assertEquals("Kitty", GameService.sanitizeMentionName("  Kitty  "))
        assertEquals("BadName", GameService.sanitizeMentionName("[Bad](Name)*_"))
        assertEquals("", GameService.sanitizeMentionName("[]()*"))
    }

    @Test
    fun `pack labels pluralize in both locales`() {
        assertEquals("1 pack", GameService.packsLabel(1, GameLocale.EN))
        assertEquals("5 packs", GameService.packsLabel(5, GameLocale.EN))
        assertEquals("1 пак", GameService.packsLabel(1, GameLocale.RU))
        assertEquals("3 пака", GameService.packsLabel(3, GameLocale.RU))
        assertEquals("5 паков", GameService.packsLabel(5, GameLocale.RU))
        assertEquals("11 паков", GameService.packsLabel(11, GameLocale.RU))
        assertEquals("21 пак", GameService.packsLabel(21, GameLocale.RU))
    }
}
