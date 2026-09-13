package com.anry88.purrrfolio.pack

import com.anry88.purrrfolio.catalog.CardCatalog
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackOpeningServiceTest {
    private val service = PackOpeningService(CardCatalog(ObjectMapper().registerModule(kotlinModule())))

    @Test
    fun rollsRequestedNumberOfCards() {
        val cards = service.rollCards(count = 3, ownedCardIds = emptySet(), seed = 7L)
        assertEquals(3, cards.size)
    }
}
