package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    private val userRowMapper = RowMapper { rs: ResultSet, _: Int ->
        User(
            id = rs.getLong("id"),
            telegramUserId = rs.getLong("telegram_user_id"),
            language = rs.getString("language"),
            lastFreePackOpenedAt = rs.getObject("last_free_pack_opened_at", OffsetDateTime::class.java),
            lastFreeCardAt = rs.getObject("last_free_card_at", OffsetDateTime::class.java),
            availablePacks = rs.getInt("available_packs"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun findByTelegramUserId(telegramUserId: Long): User? {
        val sql = "SELECT * FROM users WHERE telegram_user_id = ?"
        val results = jdbcTemplate.query(sql, userRowMapper, telegramUserId)
        return results.firstOrNull()
    }

    fun findById(id: Long): User? {
        val sql = "SELECT * FROM users WHERE id = ?"
        val results = jdbcTemplate.query(sql, userRowMapper, id)
        return results.firstOrNull()
    }

    fun create(telegramUserId: Long, language: String): User {
        val sql = """
            INSERT INTO users (telegram_user_id, language, available_packs)
            VALUES (?, ?, 0)
            RETURNING *
        """.trimIndent()
        val results = jdbcTemplate.query(sql, userRowMapper, telegramUserId, language)
        return results.first()
    }

    fun updateLanguage(userId: Long, language: String) {
        val sql = "UPDATE users SET language = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.update(sql, language, userId)
    }

    fun updateAvailablePacks(userId: Long, delta: Int) {
        val sql = """
            UPDATE users 
            SET available_packs = available_packs + ?, updated_at = NOW() 
            WHERE id = ? AND available_packs + ? >= 0
        """.trimIndent()
        jdbcTemplate.update(sql, delta, userId, delta)
    }

    fun updateLastFreePackOpenedAt(userId: Long, timestamp: OffsetDateTime) {
        val sql = "UPDATE users SET last_free_pack_opened_at = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.update(sql, timestamp, userId)
    }

    fun updateLastFreeCardAt(userId: Long, timestamp: OffsetDateTime) {
        val sql = "UPDATE users SET last_free_card_at = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.update(sql, timestamp, userId)
    }
}
