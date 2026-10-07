package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.UserCard
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

enum class CardDrawSource { PACK, FREE_CARD }

@Repository
class UserCardRepository(private val jdbcTemplate: JdbcTemplate) {
    private val transactions = TransactionTemplate(DataSourceTransactionManager(requireNotNull(jdbcTemplate.dataSource)))

    private val userCardRowMapper = RowMapper { rs: ResultSet, _: Int ->
        UserCard(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            cardId = rs.getString("card_id"),
            quantity = rs.getInt("quantity"),
            firstObtained = rs.getObject("first_obtained", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun findByUserId(userId: Long): List<UserCard> {
        val sql = "SELECT * FROM user_cards WHERE user_id = ? ORDER BY first_obtained ASC"
        return jdbcTemplate.query(sql, userCardRowMapper, userId)
    }

    fun findByUserIdAndCardId(userId: Long, cardId: String): UserCard? {
        val sql = "SELECT * FROM user_cards WHERE user_id = ? AND card_id = ?"
        val results = jdbcTemplate.query(sql, userCardRowMapper, userId, cardId)
        return results.firstOrNull()
    }

    /** Every inventory credit is recorded, including transfers and escrow returns (zero XP). */
    fun addCards(userId: Long, cardIds: List<String>, source: String = "inventory_credit") =
        credit(userId, cardIds, source, emptyMap(), null, OffsetDateTime.now())

    /** Only fresh draws award XP. The outer opening transaction also commits its receipt and cooldown/debit. */
    fun addDrawnCards(userId: Long, cards: List<CardDefinition>, source: CardDrawSource,
                      updateId: Long?, receivedAt: OffsetDateTime = OffsetDateTime.now()) =
        credit(userId, cards.map { it.id }, source.name.lowercase(), cards.associate { it.id to it.rarity }, updateId, receivedAt)

    private fun credit(userId: Long, cardIds: List<String>, source: String, rarities: Map<String, CardRarity>,
                       updateId: Long?, receivedAt: OffsetDateTime) {
        if (cardIds.isEmpty()) return
        require(source.isNotBlank())
        transactions.executeWithoutResult {
            check(jdbcTemplate.query("SELECT id FROM users WHERE id = ? FOR UPDATE", { rs, _ -> rs.getLong(1) }, userId).isNotEmpty())
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
            val openingId = UUID.randomUUID()
            jdbcTemplate.batchUpdate(
                """INSERT INTO card_acquisitions(opening_id,user_id,card_id,quantity,rarity,xp_each,source,telegram_update_id,received_at)
                    VALUES (?,?,?,?,?,?,?,?,?)""",
                cardCounts.map { (cardId, count) ->
                    arrayOf<Any?>(openingId, userId, cardId, count, rarities[cardId]?.name,
                        rarities[cardId]?.xp ?: 0, source, updateId, receivedAt)
                },
            )
            val earned = cardCounts.entries.sumOf { (cardId, count) -> count.toLong() * (rarities[cardId]?.xp ?: 0) }
            if (earned > 0) jdbcTemplate.update("UPDATE users SET xp = xp + ?, updated_at = NOW() WHERE id = ?", earned, userId)
        }
    }

    fun removeCard(userId: Long, cardId: String, quantity: Int = 1) {
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

    fun transferCard(fromUserId: Long, toUserId: Long, cardId: String, quantity: Int = 1) {
        removeCard(fromUserId, cardId, quantity)
        addCards(toUserId, List(quantity) { cardId }, "transfer")
    }
}
