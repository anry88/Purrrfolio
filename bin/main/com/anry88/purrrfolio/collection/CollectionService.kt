package com.anry88.purrrfolio.collection

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import org.springframework.stereotype.Service

@Service
class CollectionService(
    private val cardCatalog: CardCatalog,
) {
    fun buildThemeProgress(ownedCardIds: Set<String>, claimedThemeIds: Set<String>): List<ThemeProgress> =
        cardCatalog.themes.map { theme ->
            val themeCards = cardCatalog.cardsByTheme(theme.id)
            val ownedUnique = themeCards.count { ownedCardIds.contains(it.id) }
            val completed = ownedUnique == themeCards.size
            ThemeProgress(
                themeId = theme.id,
                themeNameRu = theme.nameRu,
                themeNameEn = theme.nameEn,
                ownedUnique = ownedUnique,
                totalCards = themeCards.size,
                completionBonusFish = theme.completionBonusFish,
                completed = completed,
                claimed = claimedThemeIds.contains(theme.id),
            )
        }

    fun formatThemeLine(progress: ThemeProgress, locale: GameLocale): String {
        val themeName = when (locale) {
            GameLocale.RU -> progress.themeNameRu
            GameLocale.EN -> progress.themeNameEn
        }
        val status = when {
            progress.claimed -> Messages.t("themeStatus.claimed", locale)
            progress.completed -> Messages.t("themeStatus.completed", locale)
            else -> Messages.t("themeStatus.inProgress", locale)
        }
        return "• $themeName: ${progress.ownedUnique}/${progress.totalCards} — $status"
    }
}
