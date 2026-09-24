package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.PackLedger
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class PackLedgerRepository(private val jdbcTemplate: JdbcTemplate) {

    private val packLedgerRowMapper = RowMapper { rs: ResultSet, _: Int ->
        PackLedger(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            source = rs.getString("source"),
            quantity = rs.getInt("quantity"),
            starsPaid = (rs.getObject("stars_paid") as? Number)?.toInt(),
            paymentId = rs.getString("payment_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        )
    }

    fun addPacks(userId: Long, source: String, quantity: Int, starsPaid: Int? = null, paymentId: String? = null) {
        val sql = """
            INSERT INTO pack_ledger (user_id, source, quantity, stars_paid, payment_id)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        jdbcTemplate.update(sql, userId, source, quantity, starsPaid, paymentId)
    }

    fun addPaymentPacksOnce(userId: Long, source: String, quantity: Int, starsPaid: Int?, paymentId: String): Boolean {
        val sql = """
            INSERT INTO pack_ledger (user_id, source, quantity, stars_paid, payment_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (source, payment_id) DO NOTHING
        """.trimIndent()
        return jdbcTemplate.update(sql, userId, source, quantity, starsPaid, paymentId) == 1
    }

    fun findByUserId(userId: Long): List<PackLedger> {
        val sql = "SELECT * FROM pack_ledger WHERE user_id = ? ORDER BY created_at DESC"
        return jdbcTemplate.query(sql, packLedgerRowMapper, userId)
    }

    fun getTotalAvailablePacks(userId: Long): Int {
        val sql = "SELECT COALESCE(SUM(quantity), 0) FROM pack_ledger WHERE user_id = ?"
        return jdbcTemplate.queryForObject(sql, Int::class.java, userId) ?: 0
    }
}
