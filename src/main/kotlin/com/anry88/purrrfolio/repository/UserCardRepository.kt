package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.UserCard
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class UserCardRepository(private val jdbcTemplate: JdbcTemplate) {

    private val userCardRowMapper = RowMapper { rs: ResultSet, _: Int ->
        UserCard(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            cardId = rs.getLong("card_id"),
            quantity = rs.getInt("quantity"),
            firstObtained = rs.getObject("first_obtained", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun findByUserId(userId: Long): List<UserCard> {
        val sql = "SELECT * FROM user_cards WHERE user_id = ? ORDER BY first_obtained ASC"
        return jdbcTemplate.query(sql, userCardRowMapper, userId)
    }

    fun findByUserIdAndCardId(userId: Long, cardId: Long): UserCard? {
        val sql = "SELECT * FROM user_cards WHERE user_id = ? AND card_id = ?"
        val results = jdbcTemplate.query(sql, userCardRowMapper, userId, cardId)
        return results.firstOrNull()
    }

    fun addCards(userId: Long, cardIds: List<Long>) {
        if (cardIds.isEmpty()) return
        
        val cardCounts = cardIds.groupingBy { it }.eachCount()

        val sql = """
            INSERT INTO user_cards (user_id, card_id, quantity)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, card_id) DO UPDATE 
            SET quantity = user_cards.quantity + EXCLUDED.quantity,
                updated_at = NOW()
        """.trimIndent()

        val batchArgs = cardCounts.map { (cardId, count) ->
            arrayOf<Any>(userId, cardId, count)
        }

        jdbcTemplate.batchUpdate(sql, batchArgs)
    }

    fun removeCard(userId: Long, cardId: Long, quantity: Int = 1) {
        val sql = """
            UPDATE user_cards 
            SET quantity = quantity - ?, updated_at = NOW() 
            WHERE user_id = ? AND card_id = ? AND quantity >= ?
        """.trimIndent()
        jdbcTemplate.update(sql, quantity, userId, cardId, quantity)
        
        // Clean up zero quantity cards
        val deleteSql = "DELETE FROM user_cards WHERE user_id = ? AND card_id = ? AND quantity <= 0"
        jdbcTemplate.update(deleteSql, userId, cardId)
    }

    fun transferCard(fromUserId: Long, toUserId: Long, cardId: Long, quantity: Int = 1) {
        removeCard(fromUserId, cardId, quantity)
        addCards(toUserId, listOf(cardId))
    }
}
