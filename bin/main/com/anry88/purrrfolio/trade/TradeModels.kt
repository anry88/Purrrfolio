package com.anry88.purrrfolio.trade

import java.time.Instant

enum class TradeOfferStatus {
    OPEN,
    ACCEPTED,
    CANCELLED,
    EXPIRED,
}

enum class MarketListingStatus {
    ACTIVE,
    SOLD,
    CANCELLED,
}

data class TradeOfferDraft(
    val offerId: String,
    val sellerTelegramId: Long,
    val offeredCardId: String,
    val requestedCardId: String?,
    val requestedFish: Int?,
    val expiresAt: Instant,
)

data class MarketListingDraft(
    val listingId: String,
    val sellerTelegramId: Long,
    val cardId: String,
    val duplicateIndex: Int,
    val priceFish: Int,
    val createdAt: Instant,
)
