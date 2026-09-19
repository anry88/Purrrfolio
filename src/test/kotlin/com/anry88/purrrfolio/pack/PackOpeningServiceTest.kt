package com.anry88.purrrfolio.pack

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.i18n.GameLocale
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackOpeningServiceTest {
    private val service = PackOpeningService(CardCatalog(ObjectMapper().registerModule(kotlinModule())))

    @Test
    fun rollsRequestedNumberOfCards() {
        val cards = service.rollCards(count = 3, ownedCardIds = emptySet(), month = 9)
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

        assertTrue(service.isAvailable(january, 1))
        assertTrue(!service.isAvailable(january, 2))
        assertTrue(service.isAvailable(winter, 12))
        assertTrue(service.isAvailable(winter, 2))
        assertTrue(!service.isAvailable(winter, 3))
        assertTrue(service.isAvailable(regular, 7))
        assertTrue(!service.isAvailable(groupOnly, 7, isGroupChat = false))
        assertTrue(service.isAvailable(groupOnly, 7, isGroupChat = true))
    }
}
