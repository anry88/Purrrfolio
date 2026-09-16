package com.anry88.purrrfolio.craft

import com.anry88.purrrfolio.catalog.CardRarity

/** Pack crafter: duplicates melt into points, 15 points build one pack. */
object CraftPolicy {
    const val POINTS_PER_PACK = 15

    fun pointsFor(rarity: CardRarity): Int = when (rarity) {
        CardRarity.COMMON -> 1
        CardRarity.UNCOMMON -> 2
        CardRarity.RARE -> 3
        CardRarity.EPIC -> 4
        CardRarity.MYTHIC -> 5
        CardRarity.LEGENDARY -> 5
    }

    data class CraftResult(val packs: Int, val leftover: Int)

    /** Adds [added] points to [current], crafting packs with overflow carried over. */
    fun addPoints(current: Int, added: Int): CraftResult {
        require(current >= 0 && added >= 0)
        val total = current + added
        return CraftResult(packs = total / POINTS_PER_PACK, leftover = total % POINTS_PER_PACK)
    }
}
