package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class FreeCardReceipt(val userId: Long, val cardId: String, val isNew: Boolean,
                           val completedCollectionIds: List<String>, val createdAt: OffsetDateTime)

@Repository
class FreeCardReceiptRepository(private val jdbc: JdbcTemplate) {
    fun find(updateId: Long): FreeCardReceipt? = jdbc.query(
        "SELECT * FROM free_card_receipts WHERE update_id = ?", { rs, _ ->
            FreeCardReceipt(rs.getLong("user_id"), rs.getString("card_id"), rs.getBoolean("is_new"),
                (rs.getArray("completed_collection_ids").array as Array<*>).map { it.toString() },
                rs.getObject("created_at", OffsetDateTime::class.java))
        }, updateId,
    ).firstOrNull()

    fun insert(updateId: Long, receipt: FreeCardReceipt) {
        jdbc.execute(org.springframework.jdbc.core.ConnectionCallback { connection ->
            val collections = connection.createArrayOf("text", receipt.completedCollectionIds.toTypedArray())
            try {
                connection.prepareStatement("""INSERT INTO free_card_receipts
                    (update_id,user_id,card_id,is_new,completed_collection_ids,created_at) VALUES (?,?,?,?,?,?)""").use {
                    it.setLong(1, updateId); it.setLong(2, receipt.userId); it.setString(3, receipt.cardId)
                    it.setBoolean(4, receipt.isNew); it.setArray(5, collections); it.setObject(6, receipt.createdAt)
                    it.executeUpdate()
                }
            } finally { collections.free() }
        })
    }
}
