package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.MarketListing
import com.anry88.purrrfolio.models.MarketListingStatus
import com.anry88.purrrfolio.models.TradeOffer
import com.anry88.purrrfolio.models.TradeOfferStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class MarketRepository(private val jdbcTemplate: JdbcTemplate) {

    private val marketListingRowMapper = RowMapper { rs: ResultSet, _: Int ->
        MarketListing(
            id = UUID.fromString(rs.getString("id")),
            sellerId = rs.getLong("seller_id"),
            cardId = rs.getString("card_id"),
            status = MarketListingStatus.valueOf(rs.getString("status")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    private val tradeOfferRowMapper = RowMapper { rs: ResultSet, _: Int ->
        TradeOffer(
            id = UUID.fromString(rs.getString("id")),
            targetListingId = UUID.fromString(rs.getString("target_listing_id")),
            offeredListingId = UUID.fromString(rs.getString("offered_listing_id")),
            status = TradeOfferStatus.valueOf(rs.getString("status")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            resolvedAt = rs.getObject("resolved_at", OffsetDateTime::class.java),
        )
    }

    fun createListing(sellerId: Long, cardId: String): MarketListing {
        val sql = """
            INSERT INTO market_listings (seller_id, card_id, status)
            VALUES (?, ?, 'ACTIVE')
            RETURNING *
        """.trimIndent()
        val results = jdbcTemplate.query(sql, marketListingRowMapper, sellerId, cardId)
        return results.first()
    }

    fun findActiveListingsBySeller(sellerId: Long): List<MarketListing> {
        val sql = """
            SELECT * FROM market_listings 
            WHERE seller_id = ? AND status = 'ACTIVE' 
            ORDER BY created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, marketListingRowMapper, sellerId)
    }

    fun findActiveListingsByCard(cardId: String): List<MarketListing> {
        val sql = """
            SELECT * FROM market_listings 
            WHERE card_id = ? AND status = 'ACTIVE' 
            ORDER BY created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, marketListingRowMapper, cardId)
    }

    fun findListingById(listingId: UUID): MarketListing? {
        val sql = "SELECT * FROM market_listings WHERE id = ?"
        return jdbcTemplate.query(sql, marketListingRowMapper, listingId).firstOrNull()
    }

    fun findListingsByIdsForUpdate(firstId: UUID, secondId: UUID): List<MarketListing> {
        val sql = """
            SELECT * FROM market_listings
            WHERE id IN (?, ?)
            ORDER BY id
            FOR UPDATE
        """.trimIndent()
        return jdbcTemplate.query(sql, marketListingRowMapper, firstId, secondId)
    }

    fun findOfferById(offerId: UUID): TradeOffer? {
        val sql = "SELECT * FROM trade_offers WHERE id = ?"
        return jdbcTemplate.query(sql, tradeOfferRowMapper, offerId).firstOrNull()
    }

    fun findOfferByIdForUpdate(offerId: UUID): TradeOffer? {
        val sql = "SELECT * FROM trade_offers WHERE id = ? FOR UPDATE"
        return jdbcTemplate.query(sql, tradeOfferRowMapper, offerId).firstOrNull()
    }

    fun findAllActiveListings(): List<MarketListing> {
        val sql = """
            SELECT * FROM market_listings 
            WHERE status = 'ACTIVE' 
            ORDER BY created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, marketListingRowMapper)
    }

    /** Oldest active listings first, in small batches so the expiry job handles large markets. */
    fun findExpiredActiveListings(cutoff: OffsetDateTime, limit: Int): List<MarketListing> {
        val sql = """
            SELECT * FROM market_listings
            WHERE status = 'ACTIVE' AND created_at <= ?
            ORDER BY created_at ASC
            LIMIT ?
        """.trimIndent()
        return jdbcTemplate.query(sql, marketListingRowMapper, cutoff, limit)
    }

    /** Settles dangling PENDING offers that reference an expired listing. Returns rows updated. */
    fun cancelPendingOffersForListing(listingId: UUID): Int {
        val sql = """
            UPDATE trade_offers
            SET status = 'CANCELLED', resolved_at = NOW()
            WHERE (target_listing_id = ? OR offered_listing_id = ?) AND status = 'PENDING'
        """.trimIndent()
        return jdbcTemplate.update(sql, listingId, listingId)
    }

    fun updateListingStatus(listingId: UUID, status: MarketListingStatus) {
        val sql = """
            UPDATE market_listings 
            SET status = ?, updated_at = NOW() 
            WHERE id = ?
        """.trimIndent()
        jdbcTemplate.update(sql, status.name, listingId)
    }

    fun cancelActiveListing(listingId: UUID): Boolean {
        val sql = """
            UPDATE market_listings
            SET status = 'CANCELLED', updated_at = NOW()
            WHERE id = ? AND status = 'ACTIVE'
        """.trimIndent()
        return jdbcTemplate.update(sql, listingId) == 1
    }

    fun markListingSold(listingId: UUID) {
        updateListingStatus(listingId, MarketListingStatus.SOLD)
    }

    fun createTradeOffer(targetListingId: UUID, offeredListingId: UUID): TradeOffer {
        val sql = """
            INSERT INTO trade_offers (target_listing_id, offered_listing_id, status)
            VALUES (?, ?, 'PENDING')
            RETURNING *
        """.trimIndent()
        val results = jdbcTemplate.query(sql, tradeOfferRowMapper, targetListingId, offeredListingId)
        return results.first()
    }

    fun findPendingOffersForTarget(targetListingId: UUID): List<TradeOffer> {
        val sql = """
            SELECT * FROM trade_offers 
            WHERE target_listing_id = ? AND status = 'PENDING' 
            ORDER BY created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, tradeOfferRowMapper, targetListingId)
    }

    fun findOffersByUser(userId: Long): List<TradeOffer> {
        val sql = """
            SELECT to.* FROM trade_offers to
            JOIN market_listings ml ON to.offered_listing_id = ml.id
            WHERE ml.seller_id = ?
            ORDER BY to.created_at DESC
        """.trimIndent()
        return jdbcTemplate.query(sql, tradeOfferRowMapper, userId)
    }

    fun updateTradeOfferStatus(offerId: UUID, status: TradeOfferStatus) {
        val sql = """
            UPDATE trade_offers 
            SET status = ?, resolved_at = CASE WHEN ? IN ('ACCEPTED', 'REJECTED', 'CANCELLED') THEN NOW() ELSE resolved_at END
            WHERE id = ?
        """.trimIndent()
        jdbcTemplate.update(sql, status.name, status.name, offerId)
    }

    fun acceptTradeOffer(offerId: UUID) {
        updateTradeOfferStatus(offerId, TradeOfferStatus.ACCEPTED)
    }

    fun rejectTradeOffer(offerId: UUID) {
        updateTradeOfferStatus(offerId, TradeOfferStatus.REJECTED)
    }

    fun cancelTradeOffer(offerId: UUID) {
        updateTradeOfferStatus(offerId, TradeOfferStatus.CANCELLED)
    }
}
