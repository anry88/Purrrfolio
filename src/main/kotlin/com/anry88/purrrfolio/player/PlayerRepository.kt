package com.anry88.purrrfolio.player

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class PlayerRepository(private val jdbcTemplate: JdbcTemplate) {

    private val playerRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Player(
            id = rs.getLong("id"),
            telegramId = rs.getLong("telegram_id"),
            username = rs.getString("username"),
            displayName = rs.getString("display_name"),
            fishBalance = rs.getInt("fish_balance"),
            locale = rs.getString("locale"),
            lastDailyAt = rs.getObject("last_daily_at", OffsetDateTime::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )
    }

    fun findByTelegramId(telegramId: Long): Player? {
        val sql = "SELECT * FROM players WHERE telegram_id = ?"
        val results = jdbcTemplate.query(sql, playerRowMapper, telegramId)
        return results.firstOrNull()
    }

    fun findOrCreate(telegramId: Long, username: String?, displayName: String?): Player {
        val existing = findByTelegramId(telegramId)
        if (existing != null) {
            return existing
        }

        val sql = """
            INSERT INTO players (telegram_id, username, display_name, fish_balance, locale)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (telegram_id) DO NOTHING
            RETURNING *
        """.trimIndent()
        
        // Use query instead of queryForObject to handle race conditions where it might return 0 rows if inserted by another thread
        val results = jdbcTemplate.query(sql, playerRowMapper, telegramId, username, displayName, 100, "ru")
        
        return results.firstOrNull() ?: findByTelegramId(telegramId)!!
    }

    fun updateFishBalance(playerId: Long, newBalance: Int) {
        val sql = "UPDATE players SET fish_balance = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.update(sql, newBalance, playerId)
    }

    fun updateLastDailyAt(playerId: Long, lastDailyAt: OffsetDateTime) {
        val sql = "UPDATE players SET last_daily_at = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.update(sql, lastDailyAt, playerId)
    }
}
