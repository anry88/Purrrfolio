package com.anry88.purrrfolio.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.imageio.ImageIO
import org.springframework.core.io.ClassPathResource

class CardCatalogTest {
    private val catalog = CardCatalog(ObjectMapper().registerModule(kotlinModule()))

    @Test
    fun halloweenArtIsPackagedAsStandalonePortraitPngs() {
        for (card in catalog.cardsByCollection("halloween")) {
            ClassPathResource("static${card.imagePath}").inputStream.use { stream ->
                ImageIO.createImageInputStream(stream).use { input ->
                    val reader = ImageIO.getImageReaders(input).next()
                    try {
                        reader.input = input
                        assertEquals("png", reader.formatName.lowercase(), card.id)
                        assertEquals(1024, reader.getWidth(0), card.id)
                        assertEquals(1536, reader.getHeight(0), card.id)
                    } finally {
                        reader.dispose()
                    }
                }
            }
        }
    }

    @Test
    fun halloweenCatalogHasCompleteSpecialMetadataAndEveryRarity() {
        val cards = catalog.cardsByCollection("halloween")
        assertEquals("Halloween", catalog.collection("halloween").nameEn)
        assertEquals(20, cards.size)
        assertTrue(cards.all { it.special && it.limited && it.event == "halloween" })
        assertTrue(cards.none { it.groupChatOnly })
        assertTrue(cards.all { it.availableMonths.isEmpty() })
        assertTrue(cards.all { it.availableFrom == LocalDate.of(2026, 10, 15) })
        assertTrue(cards.all { it.availableThrough == LocalDate.of(2026, 11, 15) })
        assertEquals(
            mapOf(CardRarity.COMMON to 6, CardRarity.UNCOMMON to 5, CardRarity.RARE to 4,
                CardRarity.EPIC to 3, CardRarity.MYTHIC to 1, CardRarity.LEGENDARY to 1),
            cards.groupingBy { it.rarity }.eachCount(),
        )
        assertEquals((0..19).toList(), cards.map { it.sortOrder }.sorted())
    }

    @Test
    fun loadsStarterCardsAndCollections() {
        assertEquals(305, catalog.cards.size)
        assertTrue(catalog.collections.isNotEmpty())
        assertEquals("Соня", catalog.card("sleepy").nameRu)
        assertEquals(CardRarity.EPIC, catalog.card("baker").rarity)
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
            assertEquals(
                1,
                catalog.cardsByCollection(theme.id).count { it.rarity == CardRarity.LEGENDARY },
                "Collection ${theme.id} must contain exactly one Legendary card",
            )
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
