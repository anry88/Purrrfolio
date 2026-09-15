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
        val cards = service.rollCards(count = 3, ownedCardIds = emptySet())
        assertEquals(3, cards.size)
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
}
