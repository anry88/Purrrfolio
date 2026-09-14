package com.anry88.purrrfolio.collection

data class ThemeProgress(
    val themeId: String,
    val themeNameRu: String,
    val themeNameEn: String,
    val ownedUnique: Int,
    val totalCards: Int,
    val completionBonusFish: Int,
    val completed: Boolean,
    val claimed: Boolean,
)

data class PlayerCollectionSnapshot(
    val fishBalance: Int,
    val ownedCounts: Map<String, Int>,
    val themes: List<ThemeProgress>,
)
