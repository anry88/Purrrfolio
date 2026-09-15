package com.anry88.purrrfolio.models

import java.time.OffsetDateTime
import java.util.UUID

enum class RandomTradeStatus {
    WAITING,
    MATCHED,
    CANCELLED,
}

data class RandomTradePool(
    val id: Long,
    val userId: Long,
    val cardId: String,
    val status: RandomTradeStatus,
    val matchedTradeId: Long?,
    val createdAt: OffsetDateTime,
    val matchedAt: OffsetDateTime?,
)

enum class MarketListingStatus {
    ACTIVE,
    SOLD,
    CANCELLED,
}

data class MarketListing(
    val id: UUID,
    val sellerId: Long,
    val cardId: String,
    val status: MarketListingStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

enum class TradeOfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
}

data class TradeOffer(
    val id: UUID,
    val targetListingId: UUID,
    val offeredListingId: UUID,
    val status: TradeOfferStatus,
    val createdAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
)
