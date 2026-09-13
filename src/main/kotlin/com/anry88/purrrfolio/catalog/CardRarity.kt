package com.anry88.purrrfolio.catalog

enum class CardRarity(val weight: Int, val emoji: String, val labelRu: String) {
    COMMON(55, "⚪", "Обычная"),
    RARE(25, "🔵", "Редкая"),
    EPIC(12, "🟣", "Эпическая"),
    LEGENDARY(6, "🟠", "Легендарная"),
    SPECIAL(2, "🔴", "Особая"),
    ;

    companion object {
        fun fromSlug(value: String): CardRarity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown rarity: $value")
    }
}
