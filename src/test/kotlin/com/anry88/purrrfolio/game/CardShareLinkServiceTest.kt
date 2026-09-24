package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.anry88.purrrfolio.i18n.GameLocale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CardShareLinkServiceTest {
    private val service = CardShareLinkService(
        PurrrfolioProperties(
            publicBaseUrl = "https://purrrfolio.example/",
            telegram = TelegramProperties(botUsername = "PurrrfolioBot"),
        ),
    )

    @Test
    fun `inline share hides referral URL under localized caption text`() {
        val card = CardDefinition(
            id = "sleepy",
            nameEn = "Sleepy",
            nameRu = "Соня",
            themeId = "cozy-home",
            rarity = CardRarity.COMMON,
            imagePath = "/assets/cards/sleepy.png",
        )

        val query = service.inlineQuery(card, ownerUserId = 42)
        val caption = service.sharedCaption(card, ownerUserId = 42, locale = GameLocale.RU)

        assertThat(query).isEqualTo("share:42:sleepy")
        assertThat(service.parseInlineQuery(query)).isEqualTo(CardShareRequest(42, "sleepy"))
        assertThat(caption).contains("Мне выпал котик <b>Соня</b>")
        assertThat(caption).contains("<a href=\"https://t.me/PurrrfolioBot?start=ref_42\">")
        assertThat(caption).doesNotContain("/assets/cards/")
        assertThat(caption).doesNotContain("https://purrrfolio.example")
    }

    @Test
    fun `invalid or edited inline share query is rejected`() {
        assertThat(service.parseInlineQuery("share:42:sleepy extra")).isNull()
        assertThat(service.parseInlineQuery("share:-1:sleepy")).isNull()
        assertThat(service.parseInlineQuery("other:42:sleepy")).isNull()
    }
}
