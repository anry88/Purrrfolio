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
        val cards: List<CardRecord>,
        val packs: List<PackDefinition> = emptyList(),
    )

    private data class CardRecord(
        val id: String,
        val nameEn: String,
        val nameRu: String,
        val themeId: String,
        val rarity: String,
        val imagePath: String,
        val special: Boolean = false,
        val limited: Boolean = false,
        val event: String? = null,
        val source: String? = null,
        val availableMonths: List<Int> = emptyList(),
        val groupChatOnly: Boolean = false,
        val sortOrder: Int = 0,
    )

    private val payload: CatalogPayload =
        objectMapper.readValue(ClassPathResource("catalog/cards.json").inputStream, CatalogPayload::class.java)

    val collections: List<ThemeDefinition> = payload.themes
    val cards: List<CardDefinition> = payload.cards.map {
        CardDefinition(
            id = it.id,
            nameRu = it.nameRu,
            nameEn = it.nameEn,
            themeId = it.themeId,
            rarity = CardRarity.fromSlug(it.rarity),
            imagePath = it.imagePath,
            special = it.special,
            limited = it.limited,
            event = it.event,
            source = it.source,
            availableMonths = it.availableMonths,
            groupChatOnly = it.groupChatOnly,
            sortOrder = it.sortOrder,
        )
    }

    private val cardsById: Map<String, CardDefinition> = cards.associateBy { it.id }
    private val collectionsById: Map<String, ThemeDefinition> = collections.associateBy { it.id }

    fun card(id: String): CardDefinition =
        cardsById[id] ?: throw IllegalArgumentException("Unknown card: $id")

    fun collection(id: String): ThemeDefinition =
        collectionsById[id] ?: throw IllegalArgumentException("Unknown collection: $id")

    fun cardsByCollection(collectionId: String): List<CardDefinition> =
        cards.filter { it.themeId == collectionId }
            .sortedWith(compareBy({ it.rarity.displayRank }, { it.sortOrder }))

    fun cardsByRarity(rarity: CardRarity): List<CardDefinition> = cards.filter { it.rarity == rarity }
}
