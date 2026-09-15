package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.RandomTradePool
import com.anry88.purrrfolio.models.RandomTradeStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class RandomTradeRepository(private val jdbcTemplate: JdbcTemplate) {

    private val randomTradeRowMapper = RowMapper { rs: ResultSet, _: Int ->
        RandomTradePool(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            cardId = rs.getLong("card_id"),
            status = RandomTradeStatus.valueOf(rs.getString("status")),
            matchedTradeId = rs.getObject("matched_trade_id", Long::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            matchedAt = rs.getObject("matched_at", OffsetDateTime::class.java),
        )
    }

    fun addToPool(userId: Long, cardId: Long): RandomTradePool {
        val sql = """
            INSERT INTO random_trade_pool (user_id, card_id, status)
            VALUES (?, ?, 'WAITING')
            RETURNING *
        """.trimIndent()
        val results = jdbcTemplate.query(sql, randomTradeRowMapper, userId, cardId)
        return results.first()
    }

    fun findWaitingTradesForCard(cardId: Long): List<RandomTradePool> {
        val sql = """
            SELECT * FROM random_trade_pool 
            WHERE card_id = ? AND status = 'WAITING' 
            ORDER BY created_at ASC
        """.trimIndent()
        return jdbcTemplate.query(sql, randomTradeRowMapper, cardId)
    }

    fun findWaitingTradesForUser(userId: Long): List<RandomTradePool> {
        val sql = """
            SELECT * FROM random_trade_pool 
            WHERE user_id = ? AND status = 'WAITING' 
            ORDER BY created_at ASC
        """.trimIndent()
        return jdbcTemplate.query(sql, randomTradeRowMapper, userId)
    }

    fun matchTrades(tradeId1: Long, tradeId2: Long) {
        val sql = """
            UPDATE random_trade_pool 
            SET status = 'MATCHED', 
                matched_trade_id = CASE WHEN id = ? THEN ? ELSE ? END,
                matched_at = NOW()
            WHERE id IN (?, ?)
        """.trimIndent()
        jdbcTemplate.update(sql, tradeId1, tradeId2, tradeId1, tradeId1, tradeId2)
    }

    fun cancelTrade(tradeId: Long) {
        val sql = """
            UPDATE random_trade_pool 
            SET status = 'CANCELLED' 
            WHERE id = ? AND status = 'WAITING'
        """.trimIndent()
        jdbcTemplate.update(sql, tradeId)
    }

    fun findByUserId(userId: Long): List<RandomTradePool> {
        val sql = "SELECT * FROM random_trade_pool WHERE user_id = ? ORDER BY created_at DESC"
        return jdbcTemplate.query(sql, randomTradeRowMapper, userId)
    }

    fun findAllWaiting(): List<RandomTradePool> {
        val sql = "SELECT * FROM random_trade_pool WHERE status = 'WAITING' ORDER BY created_at ASC"
        return jdbcTemplate.query(sql, randomTradeRowMapper)
    }
}
