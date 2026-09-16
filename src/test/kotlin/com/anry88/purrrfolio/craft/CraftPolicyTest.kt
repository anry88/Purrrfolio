package com.anry88.purrrfolio.craft

import com.anry88.purrrfolio.catalog.CardRarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CraftPolicyTest {

    @Test
    fun `points follow rarity`() {
        assertEquals(1, CraftPolicy.pointsFor(CardRarity.COMMON))
        assertEquals(2, CraftPolicy.pointsFor(CardRarity.UNCOMMON))
        assertEquals(3, CraftPolicy.pointsFor(CardRarity.RARE))
        assertEquals(4, CraftPolicy.pointsFor(CardRarity.EPIC))
        assertEquals(5, CraftPolicy.pointsFor(CardRarity.MYTHIC))
        assertEquals(5, CraftPolicy.pointsFor(CardRarity.LEGENDARY))
    }

    @Test
    fun `fifteen points build one pack`() {
        assertEquals(CraftPolicy.CraftResult(1, 0), CraftPolicy.addPoints(14, 1))
        assertEquals(CraftPolicy.CraftResult(1, 0), CraftPolicy.addPoints(0, 15))
    }

    @Test
    fun `overflow carries over to the next pack`() {
        // 14 pts + Uncommon (2) = 16 -> 1 pack + 1 pt left.
        assertEquals(CraftPolicy.CraftResult(1, 1), CraftPolicy.addPoints(14, 2))
        assertEquals(CraftPolicy.CraftResult(0, 7), CraftPolicy.addPoints(5, 2))
        assertEquals(CraftPolicy.CraftResult(2, 0), CraftPolicy.addPoints(10, 20))
    }
}
