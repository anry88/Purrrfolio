package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.stereotype.Repository

data class PackOpeningReceipt(
    val updateId: Long,
    val userId: Long,
    val cardIds: List<String>,
    val newCardIds: Set<String>,
    val completedCollectionIds: List<String>,
)

@Repository
class PackOpeningReceiptRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        PackOpeningReceipt(
            updateId = rs.getLong("update_id"),
            userId = rs.getLong("user_id"),
            cardIds = rs.getArray("card_ids").toStringList(),
            newCardIds = rs.getArray("new_card_ids").toStringList().toSet(),
            completedCollectionIds = rs.getArray("completed_collection_ids").toStringList(),
        )
    }

    fun findByUpdateId(updateId: Long): PackOpeningReceipt? =
        jdbcTemplate.query(
            "SELECT * FROM pack_opening_receipts WHERE update_id = ?",
            rowMapper,
            updateId,
        ).firstOrNull()

    fun insert(
        updateId: Long,
        userId: Long,
        cardIds: List<String>,
        newCardIds: Set<String>,
        completedCollectionIds: List<String>,
    ) {
        jdbcTemplate.execute(ConnectionCallback { connection ->
            connection.prepareStatement(
                """
                    INSERT INTO pack_opening_receipts
                        (update_id, user_id, card_ids, new_card_ids, completed_collection_ids)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, updateId)
                statement.setLong(2, userId)
                statement.setArray(3, connection.createArrayOf("text", cardIds.toTypedArray()))
                statement.setArray(4, connection.createArrayOf("text", newCardIds.toTypedArray()))
                statement.setArray(5, connection.createArrayOf("text", completedCollectionIds.toTypedArray()))
                statement.executeUpdate()
            }
        })
    }

    private fun java.sql.Array.toStringList(): List<String> =
        ((array as? Array<*>) ?: emptyArray<Any>()).map { it.toString() }
}
