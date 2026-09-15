package com.anry88.purrrfolio.catalog

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
    val sortOrder: Int = 0,
)

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
