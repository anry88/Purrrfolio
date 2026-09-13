package com.anry88.purrrfolio.trade

object TradePolicy {
    const val MIN_DUPLICATES_TO_TRADE = 2
    const val MIN_MARKET_PRICE_FISH = 10
    const val MAX_MARKET_PRICE_FISH = 5_000
    const val TRADE_OFFER_TTL_HOURS = 24L
    const val MARKET_FEE_PERCENT = 5

    fun canOfferDuplicate(ownedCount: Int): Boolean = ownedCount >= MIN_DUPLICATES_TO_TRADE

    fun marketFee(priceFish: Int): Int = priceFish * MARKET_FEE_PERCENT / 100
}
