package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.anry88.purrrfolio.i18n.GameLocale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CardShareLinkServiceTest {
    private val service = CardShareLinkService(
        PurrrfolioProperties(
            publicBaseUrl = "https://purrrfolio.example/",
            telegram = TelegramProperties(botUsername = "purrrfolio_bot"),
        ),
    )

    @Test
    fun `share link uses public card image and internal player referral id`() {
        val card = CardDefinition(
            id = "sleepy",
            nameEn = "Sleepy",
            nameRu = "Соня",
            themeId = "cozy-home",
            rarity = CardRarity.COMMON,
            imagePath = "/assets/cards/sleepy.png",
        )

        val url = service.shareUrl(card, ownerUserId = 42, locale = GameLocale.RU)
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)

        assertThat(url).startsWith("https://t.me/share/url?")
        assertThat(decoded).contains("https://purrrfolio.example/assets/cards/sleepy.png")
        assertThat(decoded).contains("https://t.me/purrrfolio_bot?start=ref_42")
        assertThat(decoded).doesNotContain("start=share_sleepy")
    }
}
