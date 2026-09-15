package com.anry88.purrrfolio.player

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class PlayerCardRepository(private val jdbcTemplate: JdbcTemplate) {

    private val playerCardRowMapper = RowMapper { rs: ResultSet, _: Int ->
        PlayerCard(
            id = rs.getLong("id"),
            playerId = rs.getLong("player_id"),
            cardId = rs.getString("card_id"),
            quantity = rs.getInt("quantity"),
            firstObtained = rs.getObject("first_obtained", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )
    }

    fun getPlayerCards(playerId: Long): List<PlayerCard> {
        val sql = "SELECT * FROM player_cards WHERE player_id = ? ORDER BY first_obtained ASC"
        return jdbcTemplate.query(sql, playerCardRowMapper, playerId)
    }

    fun addCards(playerId: Long, cardIds: List<String>) {
        if (cardIds.isEmpty()) return
        
        // Group by cardId to handle multiple copies of the same card in one batch
        val cardCounts = cardIds.groupingBy { it }.eachCount()

        val sql = """
            INSERT INTO player_cards (player_id, card_id, quantity)
            VALUES (?, ?, ?)
            ON CONFLICT (player_id, card_id) DO UPDATE 
            SET quantity = player_cards.quantity + EXCLUDED.quantity,
                updated_at = NOW()
        """.trimIndent()

        val batchArgs = cardCounts.map { (cardId, count) ->
            arrayOf<Any>(playerId, cardId, count)
        }

        jdbcTemplate.batchUpdate(sql, batchArgs)
    }
}
