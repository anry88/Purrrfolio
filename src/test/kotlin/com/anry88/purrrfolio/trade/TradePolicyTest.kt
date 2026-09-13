package com.anry88.purrrfolio.trade

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TradePolicyTest {
    @Test
    fun requiresDuplicateBeforeTrade() {
        assertFalse(TradePolicy.canOfferDuplicate(1))
        assertTrue(TradePolicy.canOfferDuplicate(2))
    }
}
