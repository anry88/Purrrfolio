package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.OffsetDateTime

@Repository
class ProcessedUpdateRepository(private val jdbcTemplate: JdbcTemplate) {

    fun claimUpdate(updateId: Long, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
        val staleBefore = now.minus(CLAIM_TIMEOUT)
        val sql = """
            INSERT INTO processed_telegram_updates (update_id, status, claimed_at, processed_at)
            VALUES (?, 'PROCESSING', ?, NULL)
            ON CONFLICT (update_id) DO UPDATE
            SET status = 'PROCESSING', claimed_at = EXCLUDED.claimed_at, processed_at = NULL
            WHERE processed_telegram_updates.status = 'PROCESSING'
              AND processed_telegram_updates.claimed_at <= ?
            RETURNING update_id
        """.trimIndent()
        return jdbcTemplate.query(sql, { rs, _ -> rs.getLong("update_id") }, updateId, now, staleBefore).isNotEmpty()
    }

    fun markProcessed(updateId: Long): Boolean {
        val sql = """
            UPDATE processed_telegram_updates
            SET status = 'PROCESSED', processed_at = NOW()
            WHERE update_id = ? AND status = 'PROCESSING'
        """.trimIndent()
        return jdbcTemplate.update(sql, updateId) == 1
    }

    fun releaseClaim(updateId: Long) {
        jdbcTemplate.update(
            "DELETE FROM processed_telegram_updates WHERE update_id = ? AND status = 'PROCESSING'",
            updateId,
        )
    }

    fun isProcessed(updateId: Long): Boolean {
        val sql = "SELECT COUNT(*) FROM processed_telegram_updates WHERE update_id = ? AND status = 'PROCESSED'"
        val count = jdbcTemplate.queryForObject(sql, Int::class.java, updateId) ?: 0
        return count > 0
    }

    fun cleanOldEntries(olderThan: OffsetDateTime) {
        val sql = "DELETE FROM processed_telegram_updates WHERE COALESCE(processed_at, claimed_at) < ?"
        jdbcTemplate.update(sql, olderThan)
    }

    companion object {
        val CLAIM_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}
