package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TelegramCardFileRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findFileId(cardId: String): String? = jdbcTemplate.query(
        "SELECT file_id FROM telegram_card_files WHERE card_id = ?",
        { rs, _ -> rs.getString("file_id") },
        cardId,
    ).firstOrNull()

    fun upsert(cardId: String, fileId: String) {
        jdbcTemplate.update(
            """
                INSERT INTO telegram_card_files (card_id, file_id)
                VALUES (?, ?)
                ON CONFLICT (card_id) DO UPDATE
                SET file_id = EXCLUDED.file_id, updated_at = NOW()
            """.trimIndent(),
            cardId,
            fileId,
        )
    }
}
