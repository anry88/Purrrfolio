package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class CardShareLinkService(
    private val properties: PurrrfolioProperties,
) {
    fun shareUrl(card: CardDefinition, ownerUserId: Long, locale: GameLocale): String {
        val publicCardUrl = properties.publicBaseUrl.trimEnd('/') + card.imagePath
        val source = "ref_$ownerUserId"
        val botUrl = "https://t.me/${properties.telegram.botUsername}?start=$source"
        val rarity = when (locale) {
            GameLocale.RU -> card.rarity.labelRu
            GameLocale.EN -> card.rarity.labelEn
        }
        val text = Messages.t("card.shareText", locale, card.nameFor(locale), rarity, botUrl)
        return "https://t.me/share/url?url=${encode(publicCardUrl)}&text=${encode(text)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
