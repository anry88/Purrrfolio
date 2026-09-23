package com.anry88.purrrfolio.catalog

enum class CardRarity(val weight: Int, val emoji: String, val labelRu: String, val labelEn: String) {
    COMMON(48, "⚪", "Обычная", "Common"),
    UNCOMMON(25, "🟢", "Необычная", "Uncommon"),
    RARE(15, "🔵", "Редкая", "Rare"),
    EPIC(7, "🟣", "Эпическая", "Epic"),
    MYTHIC(3, "🔴", "Мифическая", "Mythic"),
    LEGENDARY(2, "🟠", "Легендарная", "Legendary"),
    ;

    /** Gallery display order: commons first, mythics last (differs from enum ordinal). */
    val displayRank: Int
        get() = when (this) {
            COMMON -> 0
            UNCOMMON -> 1
            RARE -> 2
            EPIC -> 3
            LEGENDARY -> 4
            MYTHIC -> 5
        }

    companion object {
        fun fromSlug(value: String): CardRarity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown rarity: $value")
    }
}
