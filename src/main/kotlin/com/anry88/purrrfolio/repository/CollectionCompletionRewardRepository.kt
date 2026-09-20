package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class CollectionCompletionRewardRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun claim(userId: Long, themeId: String, catalogVersion: String, totalCards: Int): Boolean {
        val sql = """
            INSERT INTO collection_completion_rewards (user_id, theme_id, catalog_version, total_cards)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, theme_id, catalog_version) DO NOTHING
        """.trimIndent()
        return jdbcTemplate.update(sql, userId, themeId, catalogVersion, totalCards) == 1
    }
}

