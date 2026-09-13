package com.anry88.purrrfolio.collection

import com.anry88.purrrfolio.catalog.CardCatalog
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
                ownedUnique = ownedUnique,
                totalCards = themeCards.size,
                completionBonusFish = theme.completionBonusFish,
                completed = completed,
                claimed = claimedThemeIds.contains(theme.id),
            )
        }

    fun formatThemeLine(progress: ThemeProgress): String {
        val status = when {
            progress.claimed -> "✅ бонус получен"
            progress.completed -> "🎁 бонус доступен"
            else -> "в процессе"
        }
        return "• ${progress.themeNameRu}: ${progress.ownedUnique}/${progress.totalCards} — $status"
    }
}
