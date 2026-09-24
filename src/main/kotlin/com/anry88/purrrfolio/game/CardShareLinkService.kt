package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import org.springframework.stereotype.Service

data class CardShareRequest(
    val ownerUserId: Long,
    val cardId: String,
)

@Service
class CardShareLinkService(
    private val properties: PurrrfolioProperties,
) {
    fun inlineQuery(card: CardDefinition, ownerUserId: Long): String =
        "$INLINE_QUERY_PREFIX:$ownerUserId:${card.id}"

    fun parseInlineQuery(query: String): CardShareRequest? {
        val match = INLINE_QUERY_PATTERN.matchEntire(query.trim()) ?: return null
        val ownerUserId = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return CardShareRequest(ownerUserId, match.groupValues[2])
    }

    fun referralUrl(ownerUserId: Long): String =
        "https://t.me/${properties.telegram.botUsername}?start=ref_$ownerUserId"

    fun sharedCaption(card: CardDefinition, ownerUserId: Long, locale: GameLocale): String {
        val rarity = when (locale) {
            GameLocale.RU -> card.rarity.labelRu
            GameLocale.EN -> card.rarity.labelEn
        }
        return Messages.t(
            "card.shareCaption",
            locale,
            escapeHtml(card.nameFor(locale)),
            escapeHtml(rarity),
            escapeHtml(referralUrl(ownerUserId)),
        )
    }

    fun inlineResultId(cardId: String): String = "share-$cardId".take(64)

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val INLINE_QUERY_PREFIX = "share"
        private val INLINE_QUERY_PATTERN = Regex("^$INLINE_QUERY_PREFIX:(\\d+):([a-z0-9_-]{1,64})$")
    }
}
