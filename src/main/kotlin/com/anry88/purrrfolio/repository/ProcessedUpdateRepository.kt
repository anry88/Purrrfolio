package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class ProcessedUpdateRepository(private val jdbcTemplate: JdbcTemplate) {

    fun recordUpdate(updateId: Long): Boolean {
        return try {
            val sql = "INSERT INTO processed_telegram_updates (update_id) VALUES (?)"
            jdbcTemplate.update(sql, updateId)
            true
        } catch (_: DuplicateKeyException) {
            // The primary key proves this exact update was already claimed.
            false
        }
    }

    fun isProcessed(updateId: Long): Boolean {
        val sql = "SELECT COUNT(*) FROM processed_telegram_updates WHERE update_id = ?"
        val count = jdbcTemplate.queryForObject(sql, Int::class.java, updateId) ?: 0
        return count > 0
    }

    fun cleanOldEntries(olderThan: OffsetDateTime) {
        val sql = "DELETE FROM processed_telegram_updates WHERE processed_at < ?"
        jdbcTemplate.update(sql, olderThan)
    }
}
