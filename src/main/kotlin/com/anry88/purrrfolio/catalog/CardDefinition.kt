package com.anry88.purrrfolio.catalog

data class CardDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val themeId: String,
    val rarity: CardRarity,
    val imagePath: String,
)

data class ThemeDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val completionBonusFish: Int,
)

data class PackDefinition(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val costFish: Int,
    val cardsCount: Int,
)
