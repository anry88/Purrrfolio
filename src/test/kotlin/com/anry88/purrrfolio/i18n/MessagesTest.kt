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
    fun `pack no packs returns text in both locales`() {
        val enResult = Messages.t("pack.noPacks", en)
        val ruResult = Messages.t("pack.noPacks", ru)
        assertTrue(enResult.isNotBlank())
        assertTrue(ruResult.isNotBlank())
    }

    @Test
    fun `pack starter formats with args`() {
        val enResult = Messages.t("pack.starter", en, 3)
        val ruResult = Messages.t("pack.starter", ru, 3)
        assertTrue(enResult.contains("3"))
        assertTrue(ruResult.contains("3"))
    }

    @Test
    fun `collection title formats with args`() {
        val enResult = Messages.t("collection.title", en, 5, 10)
        val ruResult = Messages.t("collection.title", ru, 5, 10)
        assertEquals("🗂 *Your collection* (5 / 10)", enResult)
        assertEquals("🗂 *Твоя коллекция* (5 / 10)", ruResult)
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
            "menu.collection", "menu.pack", "menu.buy", "menu.trade", "menu.market", "menu.language",
            "language.title", "language.changed",
            "pack.noPacks", "pack.opening", "pack.opened", "pack.newCard", "pack.starter",
            "card.freeAvailable", "card.claim", "card.nextFreeIn",
            "buy.title", "buy.option1", "buy.option3", "buy.option5", "buy.option10",
            "buy.invoiceTitle", "buy.invoiceDesc", "buy.success",
            "paysupport.text",
            "collection.empty", "collection.title", "collection.more", "collection.page",
            "collection.next", "collection.prev",
            "profile.title", "profile.unique", "profile.packs",
            "themeStatus.claimed", "themeStatus.completed", "themeStatus.inProgress", "themes.title",
            "trade.hint", "trade.added", "trade.addedToPool", "trade.matched", "trade.noDuplicates",
            "trade.pickCard", "trade.offerButton", "trade.waiting",
            "market.hint", "market.listed", "market.returned", "market.noListings", "market.myListings",
            "market.noDuplicates", "market.selectCard", "market.browsingListings", "market.browseHint",
            "market.chooseOffer", "market.offerMade", "market.offerReceived",
            "market.offerAccepted", "market.offerRejected", "market.settlementFailed",
            "market.browse",
            "market.listButton", "market.returnButton", "market.offerButton", "market.accept", "market.reject",
            "error.general",
            "unknownCommand", "unknownText", "callback.underDevelopment",
            "gallery.back", "gallery.viewCards", "gallery.owned", "gallery.missing", "gallery.counter",
            "gallery.trade", "gallery.listMarket",
            "cmd.start", "cmd.pack", "cmd.collection", "cmd.buy", "cmd.trade", "cmd.market",
            "cmd.language", "cmd.help", "cmd.paysupport",
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
