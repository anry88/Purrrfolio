package com.anry88.purrrfolio.catalog

enum class CardRarity(val weight: Int, val emoji: String, val labelRu: String, val labelEn: String) {
    COMMON(55, "⚪", "Обычная", "Common"),
    RARE(25, "🔵", "Редкая", "Rare"),
    EPIC(12, "🟣", "Эпическая", "Epic"),
    LEGENDARY(6, "🟠", "Легендарная", "Legendary"),
    SPECIAL(2, "🔴", "Особая", "Special"),
    ;

    companion object {
        fun fromSlug(value: String): CardRarity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown rarity: $value")
    }
}
