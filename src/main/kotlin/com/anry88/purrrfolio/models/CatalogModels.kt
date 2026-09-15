package com.anry88.purrrfolio.models

import java.time.OffsetDateTime

data class Rarity(
    val id: Long,
    val code: String,
    val probabilityWeight: Int,
    val frameTheme: String?,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class Collection(
    val id: Long,
    val code: String,
    val localizedName: Map<String, String>,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class Card(
    val id: Long,
    val collectionId: Long,
    val englishTitle: String,
    val rarityId: Long,
    val imageRef: String,
    val sortOrder: Int,
    val active: Boolean,
    val special: Boolean,
    val limited: Boolean,
    val event: String?,
    val source: String?,
    val metadata: Map<String, Any>?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
