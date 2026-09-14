package com.anry88.purrrfolio.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameLocaleTest {

    @Test
    fun `fromCode returns RU for ru`() {
        assertEquals(GameLocale.RU, GameLocale.fromCode("ru"))
    }

    @Test
    fun `fromCode returns RU for RU (case-insensitive)`() {
        assertEquals(GameLocale.RU, GameLocale.fromCode("RU"))
    }

    @Test
    fun `fromCode returns EN for en`() {
        assertEquals(GameLocale.EN, GameLocale.fromCode("en"))
    }

    @Test
    fun `fromCode returns EN for null`() {
        assertEquals(GameLocale.EN, GameLocale.fromCode(null))
    }

    @Test
    fun `fromTelegramLanguageCode returns RU for ru`() {
        assertEquals(GameLocale.RU, GameLocale.fromTelegramLanguageCode("ru"))
    }

    @Test
    fun `fromTelegramLanguageCode returns RU for ru-RU`() {
        assertEquals(GameLocale.RU, GameLocale.fromTelegramLanguageCode("ru-RU"))
    }

    @Test
    fun `fromTelegramLanguageCode returns EN for en`() {
        assertEquals(GameLocale.EN, GameLocale.fromTelegramLanguageCode("en"))
    }

    @Test
    fun `fromTelegramLanguageCode returns EN for uk (Ukrainian, not Russian)`() {
        assertEquals(GameLocale.EN, GameLocale.fromTelegramLanguageCode("uk"))
    }

    @Test
    fun `fromTelegramLanguageCode returns EN for null`() {
        assertEquals(GameLocale.EN, GameLocale.fromTelegramLanguageCode(null))
    }
}
