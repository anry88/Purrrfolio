package com.anry88.purrrfolio.trade

object TradePolicy {
    const val MIN_DUPLICATES_TO_TRADE = 2
    const val RANDOM_TRADE_TIMEOUT_HOURS = 24L
    const val TRADE_OFFER_TIMEOUT_HOURS = 24L

    fun canOfferDuplicate(ownedCount: Int): Boolean = ownedCount >= MIN_DUPLICATES_TO_TRADE
}
