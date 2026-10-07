package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class ChatXpCandidate(val userId: Long, val telegramUserId: Long, val xp: Long)

@Repository
class ChatLeaderboardRepository(private val jdbc: JdbcTemplate) {
    /** Never use a global player/name query as a fallback. */
    fun candidates(chatId: Long, limit: Int): List<ChatXpCandidate> = jdbc.query(
        """SELECT u.id, u.telegram_user_id, u.xp FROM group_chat_members m
            JOIN users u ON u.id = m.user_id WHERE m.chat_id = ?
            ORDER BY u.xp DESC, u.id ASC LIMIT ?""",
        { rs, _ -> ChatXpCandidate(rs.getLong("id"), rs.getLong("telegram_user_id"), rs.getLong("xp")) }, chatId, limit,
    )
}
