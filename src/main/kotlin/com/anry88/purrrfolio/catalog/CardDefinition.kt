package com.anry88.purrrfolio.catalog

import java.time.LocalDate

data class CardDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val themeId: String,
    val rarity: CardRarity,
    val imagePath: String,
    val special: Boolean = false,
    val limited: Boolean = false,
    val event: String? = null,
    val source: String? = null,
    val availableMonths: List<Int> = emptyList(),
    val groupChatOnly: Boolean = false,
    val sortOrder: Int = 0,
    val availableFrom: LocalDate? = null,
    val availableThrough: LocalDate? = null,
) {
    init {
        require((availableFrom == null) == (availableThrough == null)) {
            "Card $id must specify both availability dates or neither"
        }
        require(availableFrom == null || !availableThrough!!.isBefore(availableFrom)) {
            "Card $id availability ends before it starts"
        }
    }
}

data class ThemeDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val sortOrder: Int = 0,
    val completionBonusFish: Int = 0,
)

data class PackDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val costFish: Int = 50,
    val cardsCount: Int = 3,
)
