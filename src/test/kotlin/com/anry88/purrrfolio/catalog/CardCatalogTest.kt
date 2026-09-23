package com.anry88.purrrfolio.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardCatalogTest {
    private val catalog = CardCatalog(ObjectMapper().registerModule(kotlinModule()))

    @Test
    fun loadsStarterCardsAndCollections() {
        assertEquals(285, catalog.cards.size)
        assertTrue(catalog.collections.isNotEmpty())
        assertEquals("Соня", catalog.card("sleepy").nameRu)
        assertEquals(CardRarity.LEGENDARY, catalog.card("baker").rarity)
        assertEquals(CardRarity.MYTHIC, catalog.card("dreamweaver").rarity)
        assertEquals(CardRarity.UNCOMMON, catalog.card("blossom").rarity)
        assertEquals("Спорт", catalog.collection("sports").nameRu)
        assertEquals(16, catalog.cardsByCollection("sports").size)
        assertEquals(20, catalog.cardsByCollection("professions").size)
        assertEquals(20, catalog.cardsByCollection("food").size)
        assertEquals("Транспорт", catalog.collection("transport").nameRu)
        assertEquals(14, catalog.cardsByCollection("transport").size)
        assertEquals(CardRarity.MYTHIC, catalog.card("starship-ark").rarity)
        assertEquals("Европейские котики", catalog.collection("european-cats").nameRu)
        assertEquals(30, catalog.cardsByCollection("european-cats").size)
        assertEquals(CardRarity.MYTHIC, catalog.card("europe-turkey").rarity)
        assertEquals("Азиатские котики", catalog.collection("asian-cats").nameRu)
        assertEquals(30, catalog.cardsByCollection("asian-cats").size)
        assertEquals("asian-cats", catalog.card("asia-australia").themeId)
        assertEquals(CardRarity.MYTHIC, catalog.card("asia-japan").rarity)
        assertTrue(
            catalog.cardsByCollection("asian-cats").map { it.id }.containsAll(
                setOf(
                    "asia-kazakhstan",
                    "asia-uzbekistan",
                    "asia-kyrgyzstan",
                    "asia-tajikistan",
                    "asia-united-arab-emirates",
                    "asia-indonesia",
                    "asia-thailand",
                    "asia-vietnam",
                    "asia-australia",
                ),
            ),
        )
        assertEquals("Африканские котики", catalog.collection("african-cats").nameRu)
        assertEquals(30, catalog.cardsByCollection("african-cats").size)
        assertEquals(CardRarity.MYTHIC, catalog.card("africa-egypt").rarity)
        assertEquals("Американские котики", catalog.collection("american-cats").nameRu)
        assertEquals(30, catalog.cardsByCollection("american-cats").size)
        assertEquals(CardRarity.MYTHIC, catalog.card("americas-brazil").rarity)
        assertEquals(
            5,
            catalog.cardsByCollection("sports").count {
                it.id in setOf("table-tennis-duo", "rowing-crew", "soccer-squad", "basketball-buddies", "hockey-team")
            },
        )
        val calendarCards = catalog.cardsByCollection("calendar-cycle")
        assertEquals(16, calendarCards.size)
        assertTrue(calendarCards.all { it.special })
        assertTrue(calendarCards.none { it.limited })
        assertTrue(calendarCards.all { it.event == "calendar-cycle" })
        assertEquals(listOf(1), catalog.card("january").availableMonths)
        assertEquals(listOf(12, 1, 2), catalog.card("season-winter").availableMonths)
        assertEquals(listOf(3, 4, 5), catalog.card("season-spring").availableMonths)
        assertEquals(listOf(6, 7, 8), catalog.card("season-summer").availableMonths)
        assertEquals(listOf(9, 10, 11), catalog.card("season-autumn").availableMonths)

        catalog.collections.forEach { theme ->
            val ranks = catalog.cardsByCollection(theme.id).map { it.rarity.displayRank }
            assertEquals(ranks.sorted(), ranks, "Collection ${theme.id} is not sorted by rarity")
        }

        val friendshipCards = catalog.cardsByCollection("friendship")
        assertEquals("Друзья", catalog.collection("friendship").nameRu)
        assertEquals(12, friendshipCards.size)
        assertTrue(friendshipCards.all { it.special })
        assertTrue(friendshipCards.all { it.groupChatOnly })
        assertTrue(friendshipCards.all { it.event == "group-friendship" })
        assertTrue(friendshipCards.all { it.availableMonths.isEmpty() })
        assertEquals(CardRarity.MYTHIC, catalog.card("friendship-festival").rarity)
    }
}
