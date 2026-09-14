package com.anry88.purrrfolio.i18n

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessagesTest {

    private val en = GameLocale.EN
    private val ru = GameLocale.RU

    @Test
    fun `welcome returns non-empty text in both locales`() {
        val enText = Messages.t("welcome", en)
        val ruText = Messages.t("welcome", ru)
        assertTrue(enText.isNotBlank())
        assertTrue(ruText.isNotBlank())
        assertTrue(enText.contains("Purrrfolio"))
        assertTrue(ruText.contains("Purrrfolio"))
    }

    @Test
    fun `help returns non-empty text in both locales`() {
        assertDoesNotThrow { Messages.t("help", en) }
        assertDoesNotThrow { Messages.t("help", ru) }
    }

    @Test
    fun `pack insufficient formats with args`() {
        val result = Messages.t("pack.insufficient", en, 50, 30)
        assertEquals("Not enough fish! A pack costs 50 🐟, you have 30 🐟.", result)
    }

    @Test
    fun `pack insufficient formats in Russian`() {
        val result = Messages.t("pack.insufficient", ru, 50, 30)
        assertEquals("Недостаточно рыбок! Набор стоит 50 🐟, а у тебя 30 🐟.", result)
    }

    @Test
    fun `collection title formats with args`() {
        val enResult = Messages.t("collection.title", en, 5, 10)
        val ruResult = Messages.t("collection.title", ru, 5, 10)
        assertEquals("🗂 *Your collection* (5 / 10)", enResult)
        assertEquals("🗂 *Твоя коллекция* (5 / 10)", ruResult)
    }

    @Test
    fun `daily reward formats with two args`() {
        val enResult = Messages.t("daily.reward", en, 25, 125)
        val ruResult = Messages.t("daily.reward", ru, 25, 125)
        assertEquals("Daily reward: +25 🐟\nYour balance: 125 🐟", enResult)
        assertEquals("Ежедневная награда: +25 🐟\nТвой баланс: 125 🐟", ruResult)
    }

    @Test
    fun `menu labels contain expected text`() {
        assertEquals("🎁 Pack", Messages.t("menu.pack", en))
        assertEquals("🎁 Набор", Messages.t("menu.pack", ru))
    }

    @Test
    fun `all locale keys are present for both locales`() {
        val knownKeys = listOf(
            "welcome", "help",
            "menu.collection", "menu.pack", "menu.themes", "menu.trade", "menu.market", "menu.profile", "menu.language",
            "language.title", "language.changed",
            "pack.insufficient", "pack.opening", "pack.opened", "pack.newCard",
            "collection.empty", "collection.title", "collection.more",
            "themes.title",
            "profile.title", "profile.player", "profile.fish", "profile.unique", "profile.themes",
            "trade.hint", "market.hint",
            "daily.already", "daily.reward",
            "unknownCommand", "unknownText", "callback.underDevelopment",
            "themeStatus.claimed", "themeStatus.completed", "themeStatus.inProgress",
        )
        for (key in knownKeys) {
            assertDoesNotThrow({ Messages.t(key, en) }, "Key '$key' missing for EN")
            assertDoesNotThrow({ Messages.t(key, ru) }, "Key '$key' missing for RU")
        }
    }

    @Test
    fun `unknown key throws`() {
        assertThrows<IllegalArgumentException> { Messages.t("nonexistent.key", en) }
    }
}
