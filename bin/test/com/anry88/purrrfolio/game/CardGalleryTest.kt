package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.i18n.GameLocale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CardGalleryTest {

    private val sleepy = CardDefinition(
        id = "sleepy",
        nameRu = "Соня",
        nameEn = "Sleepy",
        themeId = "cozy-home",
        rarity = CardRarity.COMMON,
        imagePath = "assets/cards/sleepy.png",
    )

    private val cozyHome = ThemeDefinition(
        id = "cozy-home",
        nameRu = "Уютный дом",
        nameEn = "Cozy Home",
        completionBonusFish = 20,
    )

    @Test
    fun `parses collection gallery callback`() {
        val action = GalleryActions.parse("gal:col:3")
        assertThat(action).isEqualTo(GalleryAction.Collection(3))
    }

    @Test
    fun `parses theme gallery callback with hyphenated theme id`() {
        val action = GalleryActions.parse("gal:theme:cozy-home:2")
        assertThat(action).isEqualTo(GalleryAction.Theme("cozy-home", 2))
    }

    @Test
    fun `parses back callback`() {
        val action = GalleryActions.parse(GalleryActions.BACK)
        assertThat(action).isEqualTo(GalleryAction.Back)
    }

    @Test
    fun `returns null for non gallery callbacks`() {
        assertThat(GalleryActions.parse("menu:collection")).isNull()
        assertThat(GalleryActions.parse("lang:en")).isNull()
        assertThat(GalleryActions.parse("garbage")).isNull()
    }

    @Test
    fun `builds collection callback round trip`() {
        assertThat(GalleryActions.collection(7)).isEqualTo("gal:col:7")
        assertThat(GalleryActions.theme("cozy-home", 0)).isEqualTo("gal:theme:cozy-home:0")
    }

    @Test
    fun `caption shows owned count and theme for owned card`() {
        val caption = galleryCaption(sleepy, cozyHome, ownedCount = 2, locale = GameLocale.EN, position = 1, total = 2)
        assertThat(caption).contains("*Sleepy*")
        assertThat(caption).contains("Cozy Home")
        assertThat(caption).contains("×2")
        assertThat(caption).contains("1 / 2")
    }

    @Test
    fun `caption shows missing state for unowned card`() {
        val caption = galleryCaption(sleepy, cozyHome, ownedCount = 0, locale = GameLocale.RU, position = 1, total = 2)
        assertThat(caption).contains("Соня")
        assertThat(caption).contains("Уютный дом")
        assertThat(caption).contains("Ещё не собрана")
    }

    @Test
    fun `keyboard contains navigation and back buttons`() {
        val keyboard = galleryKeyboard(GameLocale.EN, { i -> GalleryActions.collection(i) }, index = 0, total = 3)
        val buttons = keyboard.inlineKeyboard.orEmpty().flatten()
        assertThat(buttons.map { it.text }).containsExactly("◀️", "▶️", "🔙 Back")
        assertThat(buttons.map { it.callbackData })
            .containsExactly("gal:col:-1", "gal:col:1", GalleryActions.BACK)
    }

    @Test
    fun `gallery media references the target card file id`() {
        val media = galleryMedia("sleepy-large-file", "⚪ *Sleepy*")

        assertThat(media["type"]).isEqualTo("photo")
        assertThat(media["media"]).isEqualTo("sleepy-large-file")
        assertThat(media["caption"]).isEqualTo("⚪ *Sleepy*")
        assertThat(media["parse_mode"]).isEqualTo("Markdown")
    }
}