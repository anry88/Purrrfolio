package com.anry88.purrrfolio.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CardCatalog(
    objectMapper: ObjectMapper,
) {
    private data class CatalogPayload(
        val themes: List<ThemeDefinition>,
        val packs: List<PackDefinition>,
        val cards: List<CardRecord>,
    )

    private data class CardRecord(
        val id: String,
        val nameRu: String,
        val nameEn: String,
        val themeId: String,
        val rarity: String,
        val imagePath: String,
    )

    private val payload: CatalogPayload =
        objectMapper.readValue(ClassPathResource("catalog/cards.json").inputStream, CatalogPayload::class.java)

    val themes: List<ThemeDefinition> = payload.themes
    val packs: List<PackDefinition> = payload.packs
    val cards: List<CardDefinition> = payload.cards.map {
        CardDefinition(
            id = it.id,
            nameRu = it.nameRu,
            nameEn = it.nameEn,
            themeId = it.themeId,
            rarity = CardRarity.fromSlug(it.rarity),
            imagePath = it.imagePath,
        )
    }

    private val cardsById: Map<String, CardDefinition> = cards.associateBy { it.id }
    private val themesById: Map<String, ThemeDefinition> = themes.associateBy { it.id }

    fun card(id: String): CardDefinition =
        cardsById[id] ?: throw IllegalArgumentException("Unknown card: $id")

    fun theme(id: String): ThemeDefinition =
        themesById[id] ?: throw IllegalArgumentException("Unknown theme: $id")

    fun cardsByTheme(themeId: String): List<CardDefinition> = cards.filter { it.themeId == themeId }

    fun cardsByRarity(rarity: CardRarity): List<CardDefinition> = cards.filter { it.rarity == rarity }
}
