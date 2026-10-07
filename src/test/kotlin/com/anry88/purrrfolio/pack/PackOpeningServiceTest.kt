package com.anry88.purrrfolio.pack

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.i18n.GameLocale
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PackOpeningServiceTest {
    private val service = PackOpeningService(CardCatalog(ObjectMapper().registerModule(kotlinModule())))

    @Test
    fun halloweenDropsOnlyWithinItsInclusive2026WindowInBothChatTypes() {
        val catalog = CardCatalog(ObjectMapper().registerModule(kotlinModule()))
        val halloween = catalog.cardsByCollection("halloween")
        assertEquals(20, halloween.size)
        for (group in listOf(false, true)) {
            for (card in halloween) {
                assertFalse(service.isAvailable(card, LocalDate.of(2026, 10, 14), group), card.id)
                assertTrue(service.isAvailable(card, LocalDate.of(2026, 10, 15), group), card.id)
                assertTrue(service.isAvailable(card, LocalDate.of(2026, 10, 31), group), card.id)
                assertTrue(service.isAvailable(card, LocalDate.of(2026, 11, 15), group), card.id)
                assertFalse(service.isAvailable(card, LocalDate.of(2026, 11, 16), group), card.id)
                assertFalse(service.isAvailable(card, LocalDate.of(2025, 10, 31), group), card.id)
                assertFalse(service.isAvailable(card, LocalDate.of(2027, 10, 31), group), card.id)
            }
        }
    }

    @Test
    fun dateWindowCombinesWithMonthAndGroupRestrictions() {
        val card = CardDefinition(
            id = "restricted", nameRu = "Тест", nameEn = "Test", themeId = "test",
            rarity = CardRarity.COMMON, imagePath = "/test.png",
            availableMonths = listOf(10), groupChatOnly = true,
            availableFrom = LocalDate.of(2026, 10, 15),
            availableThrough = LocalDate.of(2026, 11, 15),
        )
        assertTrue(service.isAvailable(card, LocalDate.of(2026, 10, 15), true))
        assertFalse(service.isAvailable(card, LocalDate.of(2026, 10, 15), false))
        assertFalse(service.isAvailable(card, LocalDate.of(2026, 10, 14), true))
        assertFalse(service.isAvailable(card, LocalDate.of(2026, 11, 1), true))
    }

    @Test
    fun rollingOutsideEventWindowExcludesHalloweenAndPreservesRegularDrops() {
        for (date in listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 16))) {
            val cards = service.rollCards(500, emptySet(), date, isGroupChat = true)
            assertEquals(500, cards.size)
            assertTrue(cards.none { it.themeId == "halloween" })
            assertTrue(cards.all { service.isAvailable(it, date, true) })
        }
    }

    @Test
    fun invalidAvailabilityWindowIsRejected() {
        val card = CardDefinition(
            id = "test", nameRu = "Тест", nameEn = "Test", themeId = "test",
            rarity = CardRarity.COMMON, imagePath = "/test.png",
        )
        assertThrows(IllegalArgumentException::class.java) {
            card.copy(availableFrom = LocalDate.of(2026, 10, 15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            card.copy(availableThrough = LocalDate.of(2026, 11, 15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            card.copy(
                availableFrom = LocalDate.of(2026, 11, 15),
                availableThrough = LocalDate.of(2026, 10, 15),
            )
        }
    }

    @Test
    fun rollsRequestedNumberOfCards() {
        val cards = service.rollCards(count = 3, ownedCardIds = emptySet(), date = LocalDate.of(2026, 9, 1))
        assertEquals(3, cards.size)
    }

    @Test
    fun rarityRollUsesEveryConfiguredWeightBoundary() {
        assertEquals(100, CardRarity.entries.sumOf { it.weight })
        var firstRoll = 0
        for (rarity in CardRarity.entries) {
            assertEquals(rarity, service.rarityForRoll(firstRoll))
            assertEquals(rarity, service.rarityForRoll(firstRoll + rarity.weight - 1))
            firstRoll += rarity.weight
        }
        assertEquals(100, firstRoll)
    }

    @Test
    fun formatRevealEnglishNewCard() {
        val card = CardDefinition(
            id = "sleepy",
            nameRu = "Соня",
            nameEn = "Sleepy",
            themeId = "cozy-home",
            rarity = CardRarity.COMMON,
            imagePath = "/assets/cards/sleepy.png",
            sortOrder = 0,
        )
        val result = service.formatReveal(card, isNew = true, locale = GameLocale.EN)
        assertTrue(result.contains("Sleepy"))
        assertTrue(result.contains("Common"))
        assertTrue(result.contains("✨ NEW"))
    }

    @Test
    fun formatRevealRussianDuplicateCard() {
        val card = CardDefinition(
            id = "baker",
            nameRu = "Пекарь",
            nameEn = "Baker",
            themeId = "professions",
            rarity = CardRarity.LEGENDARY,
            imagePath = "/assets/cards/baker.png",
            sortOrder = 0,
        )
        val result = service.formatReveal(card, isNew = false, locale = GameLocale.RU)
        assertTrue(result.contains("Пекарь"))
        assertTrue(result.contains("Легендарная"))
        assertTrue(!result.contains("НОВАЯ"))
    }

    @Test
    fun specialRevealIsMarkedInBothLanguages() {
        val card = CardDefinition(
            id = "september",
            nameRu = "Сентябрь",
            nameEn = "September",
            themeId = "calendar-cycle",
            rarity = CardRarity.RARE,
            imagePath = "/assets/cards/september.png",
            special = true,
            availableMonths = listOf(9),
        )

        assertTrue(service.formatReveal(card, false, GameLocale.EN).contains("SPECIAL CARD"))
        assertTrue(service.formatReveal(card, false, GameLocale.RU).contains("СПЕШЛ-КАРТОЧКА"))
    }

    @Test
    fun calendarCardsAreOnlyAvailableInTheirConfiguredMonths() {
        val january = CardDefinition(
            id = "january",
            nameRu = "Январь",
            nameEn = "January",
            themeId = "calendar-cycle",
            rarity = CardRarity.COMMON,
            imagePath = "/assets/cards/january.png",
            special = true,
            availableMonths = listOf(1),
        )
        val winter = january.copy(id = "season-winter", availableMonths = listOf(12, 1, 2))
        val regular = january.copy(id = "sleepy", special = false, availableMonths = emptyList())
        val groupOnly = january.copy(
            id = "tea-party-pals",
            availableMonths = emptyList(),
            groupChatOnly = true,
        )

        assertTrue(service.isAvailable(january, LocalDate.of(2026, 1, 1)))
        assertTrue(!service.isAvailable(january, LocalDate.of(2026, 2, 1)))
        assertTrue(service.isAvailable(winter, LocalDate.of(2026, 12, 1)))
        assertTrue(service.isAvailable(winter, LocalDate.of(2026, 2, 1)))
        assertTrue(!service.isAvailable(winter, LocalDate.of(2026, 3, 1)))
        assertTrue(service.isAvailable(regular, LocalDate.of(2026, 7, 1)))
        assertTrue(!service.isAvailable(groupOnly, LocalDate.of(2026, 7, 1), isGroupChat = false))
        assertTrue(service.isAvailable(groupOnly, LocalDate.of(2026, 7, 1), isGroupChat = true))
    }
}
