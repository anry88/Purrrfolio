package com.anry88.purrrfolio.catalog

enum class CardRarity(val weight: Int, val emoji: String, val labelRu: String, val labelEn: String) {
    COMMON(48, "⚪", "Обычная", "Common"),
    UNCOMMON(25, "🟢", "Необычная", "Uncommon"),
    RARE(15, "🔵", "Редкая", "Rare"),
    EPIC(7, "🟣", "Эпическая", "Epic"),
    MYTHIC(3, "🔴", "Мифическая", "Mythic"),
    LEGENDARY(2, "🟠", "Легендарная", "Legendary"),
    ;

    /** XP for a newly drawn card, including duplicates. Independent of drop weights. */
    val xp: Int get() = when (this) {
        COMMON -> 1
        UNCOMMON -> 2
        RARE -> 3
        EPIC -> 5
        MYTHIC -> 8
        LEGENDARY -> 12
    }

    /** Gallery display order: commons first, legendaries last. */
    val displayRank: Int
        get() = when (this) {
            COMMON -> 0
            UNCOMMON -> 1
            RARE -> 2
            EPIC -> 3
            MYTHIC -> 4
            LEGENDARY -> 5
        }

    companion object {
        fun fromSlug(value: String): CardRarity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown rarity: $value")
    }
}
