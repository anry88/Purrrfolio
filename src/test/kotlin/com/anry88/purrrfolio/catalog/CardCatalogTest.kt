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
        assertEquals(10, catalog.cards.size)
        assertTrue(catalog.collections.isNotEmpty())
        assertEquals("Соня", catalog.card("sleepy").nameRu)
        assertEquals(CardRarity.LEGENDARY, catalog.card("baker").rarity)
    }
}
