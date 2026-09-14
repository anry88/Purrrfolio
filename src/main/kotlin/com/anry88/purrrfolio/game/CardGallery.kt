package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup

sealed class GalleryAction {
    data class Collection(val index: Int) : GalleryAction()
    data class Theme(val themeId: String, val index: Int) : GalleryAction()
    data object Back : GalleryAction()
}

object GalleryActions {
    private const val PREFIX = "gal:"
    const val BACK = "${PREFIX}back"

    fun collection(index: Int): String = "${PREFIX}col:$index"

    fun theme(themeId: String, index: Int): String = "${PREFIX}theme:$themeId:$index"

    /** Parses inline callback data into a gallery action, or null when it is not a gallery callback. */
    fun parse(data: String): GalleryAction? {
        if (!data.startsWith(PREFIX)) return null
        val parts = data.removePrefix(PREFIX).split(":")
        return when (parts.firstOrNull()) {
            "back" -> GalleryAction.Back
            "col" -> parts.getOrNull(1)?.toIntOrNull()?.let { GalleryAction.Collection(it) }
            "theme" -> {
                val index = parts.getOrNull(parts.lastIndex)?.toIntOrNull() ?: return null
                val themeId = parts.drop(1).dropLast(1).joinToString(":")
                if (themeId.isEmpty()) null else GalleryAction.Theme(themeId, index)
            }
            else -> null
        }
    }
}

fun galleryCaption(
    card: CardDefinition,
    theme: ThemeDefinition?,
    ownedCount: Int,
    locale: GameLocale,
    position: Int,
    total: Int,
): String {
    val rarityLabel = when (locale) {
        GameLocale.RU -> card.rarity.labelRu
        GameLocale.EN -> card.rarity.labelEn
    }
    val header = "${card.rarity.emoji} *${card.nameFor(locale)}* — $rarityLabel"
    val themeLine = theme?.let { "\n📚 ${it.nameFor(locale)}" }.orEmpty()
    val ownedLine = if (ownedCount > 0) Messages.t("gallery.owned", locale, ownedCount)
    else Messages.t("gallery.missing", locale)
    val counter = Messages.t("gallery.counter", locale, position, total)
    return "$header$themeLine\n$ownedLine\n$counter"
}

fun galleryKeyboard(
    locale: GameLocale,
    actionForIndex: (Int) -> String,
    index: Int,
    total: Int,
): TelegramReplyMarkup =
    TelegramReplyMarkup(
        inlineKeyboard = listOf(
            listOf(
                TelegramInlineButton("◀️", actionForIndex(index - 1)),
                TelegramInlineButton("▶️", actionForIndex(index + 1)),
            ),
            listOf(
                TelegramInlineButton(Messages.t("gallery.back", locale), GalleryActions.BACK),
            ),
        ),
    )