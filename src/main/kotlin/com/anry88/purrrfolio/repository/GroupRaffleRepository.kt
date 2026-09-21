package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.GroupRaffle
import com.anry88.purrrfolio.models.GroupRaffleCandidate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class GroupRaffleRepository(private val jdbcTemplate: JdbcTemplate) {

    private val raffleRowMapper = RowMapper { rs: ResultSet, _: Int ->
        GroupRaffle(
            id = rs.getLong("id"),
            chatId = rs.getLong("chat_id"),
            memberCount = rs.getInt("member_count"),
            packsGranted = rs.getInt("packs_granted"),
            raffledAt = rs.getObject("raffled_at", OffsetDateTime::class.java),
        )
    }

    private val candidateRowMapper = RowMapper { rs: ResultSet, _: Int ->
        GroupRaffleCandidate(
            userId = rs.getLong("user_id"),
            telegramUserId = rs.getLong("telegram_user_id"),
            displayName = rs.getString("display_name") ?: "",
        )
    }

    /** Remember that a registered player was seen in a group chat (or refresh the sighting). */
    fun trackMember(chatId: Long, userId: Long, displayName: String, username: String?) {
        val sql = """
            INSERT INTO group_chat_members (chat_id, user_id, display_name, username, last_seen_at)
            VALUES (?, ?, ?, ?, NOW())
            ON CONFLICT (chat_id, user_id)
            DO UPDATE SET display_name = EXCLUDED.display_name,
                          username = EXCLUDED.username,
                          last_seen_at = NOW()
        """.trimIndent()
        jdbcTemplate.update(sql, chatId, userId, displayName, username)
    }

    /** Latest raffle in a chat, if any (drives the 24h cooldown). */
    fun findLastRaffle(chatId: Long): GroupRaffle? {
        val sql = "SELECT * FROM group_raffles WHERE chat_id = ? ORDER BY raffled_at DESC LIMIT 1"
        return jdbcTemplate.query(sql, raffleRowMapper, chatId).firstOrNull()
    }

    /**
     * Random registered players seen in this chat. The caller filters out bots and
     * departed members via getChatMember, so [limit] should exceed the pack count.
     */
    fun findRandomCandidates(chatId: Long, limit: Int): List<GroupRaffleCandidate> {
        val sql = """
            SELECT m.user_id, u.telegram_user_id, m.display_name
            FROM group_chat_members m
            JOIN users u ON u.id = m.user_id
            WHERE m.chat_id = ?
            ORDER BY RANDOM()
            LIMIT ?
        """.trimIndent()
        return jdbcTemplate.query(sql, candidateRowMapper, chatId, limit)
    }

    fun countCandidates(chatId: Long): Int {
        val sql = "SELECT COUNT(*) FROM group_chat_members WHERE chat_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, chatId) ?: 0
    }

    /**
     * Serialize concurrent raffles of the same chat; must be called inside a transaction.
     * pg_advisory_xact_lock returns void, so it runs as an update-style statement.
     */
    fun lockChat(chatId: Long) {
        jdbcTemplate.update({ con ->
            con.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").apply {
                setString(1, "raffle:$chatId")
            }
        })
    }

    fun createRaffle(chatId: Long, memberCount: Int, packsGranted: Int): Long {
        val sql = """
            INSERT INTO group_raffles (chat_id, member_count, packs_granted)
            VALUES (?, ?, ?)
            RETURNING id
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, Long::class.java, chatId, memberCount, packsGranted)
            ?: error("Failed to insert group raffle for chat $chatId")
    }

    fun addWinner(raffleId: Long, userId: Long, packs: Int) {
        val sql = "INSERT INTO group_raffle_winners (raffle_id, user_id, packs) VALUES (?, ?, ?)"
        jdbcTemplate.update(sql, raffleId, userId, packs)
    }
}
