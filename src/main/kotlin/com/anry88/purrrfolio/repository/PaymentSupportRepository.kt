package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.PaymentSupportRequest
import com.anry88.purrrfolio.models.PaymentSupportStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class PaymentSupportRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        PaymentSupportRequest(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            paymentId = rs.getLong("payment_id"),
            reason = rs.getString("reason"),
            status = PaymentSupportStatus.valueOf(rs.getString("status")),
            adminMessage = rs.getString("admin_message"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun create(userId: Long, paymentId: Long, reason: String): PaymentSupportRequest {
        val sql = """
            INSERT INTO payment_support_requests (user_id, payment_id, reason, status)
            VALUES (?, ?, ?, 'PENDING')
            RETURNING *
        """.trimIndent()
        return jdbcTemplate.query(sql, rowMapper, userId, paymentId, reason).first()
    }

    fun findById(id: Long): PaymentSupportRequest? =
        jdbcTemplate.query("SELECT * FROM payment_support_requests WHERE id = ?", rowMapper, id).firstOrNull()

    fun findByPaymentId(paymentId: Long): PaymentSupportRequest? =
        jdbcTemplate.query("SELECT * FROM payment_support_requests WHERE payment_id = ?", rowMapper, paymentId).firstOrNull()

    fun latestInfoRequest(userId: Long): PaymentSupportRequest? {
        val sql = """
            SELECT * FROM payment_support_requests
            WHERE user_id = ? AND status = 'INFO'
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, rowMapper, userId).firstOrNull()
    }

    fun findPendingAnswer(userId: Long, requestId: Long?, answer: String): PaymentSupportRequest? {
        val (sql, args) = if (requestId != null) {
            """
                SELECT * FROM payment_support_requests
                WHERE id = ? AND user_id = ? AND status = 'PENDING' AND admin_message = ?
                LIMIT 1
            """.trimIndent() to arrayOf<Any>(requestId, userId, answer)
        } else {
            """
                SELECT * FROM payment_support_requests
                WHERE user_id = ? AND status = 'PENDING' AND admin_message = ?
                ORDER BY updated_at DESC
                LIMIT 1
            """.trimIndent() to arrayOf<Any>(userId, answer)
        }
        return jdbcTemplate.query(sql, rowMapper, *args).firstOrNull()
    }

    fun updateStatus(id: Long, status: PaymentSupportStatus, adminMessage: String? = null): Boolean {
        val sql = """
            UPDATE payment_support_requests
            SET status = ?, admin_message = ?, updated_at = NOW()
            WHERE id = ?
        """.trimIndent()
        return jdbcTemplate.update(sql, status.name, adminMessage, id) == 1
    }

    fun resolvePending(id: Long, status: PaymentSupportStatus, adminMessage: String?): Boolean {
        require(status == PaymentSupportStatus.INFO || status == PaymentSupportStatus.REJECTED)
        val sql = """
            UPDATE payment_support_requests
            SET status = ?, admin_message = ?, updated_at = NOW()
            WHERE id = ? AND status IN ('PENDING', 'INFO')
        """.trimIndent()
        return jdbcTemplate.update(sql, status.name, adminMessage, id) == 1
    }

    fun submitUserAnswer(id: Long, userId: Long, answer: String): Boolean {
        val sql = """
            UPDATE payment_support_requests
            SET status = 'PENDING', admin_message = ?, updated_at = NOW()
            WHERE id = ? AND user_id = ? AND status = 'INFO'
        """.trimIndent()
        return jdbcTemplate.update(sql, answer, id, userId) == 1
    }

    fun claimForRefund(id: Long): Boolean {
        val sql = """
            UPDATE payment_support_requests
            SET status = 'PROCESSING', updated_at = NOW()
            WHERE id = ? AND status IN ('PENDING', 'INFO')
        """.trimIndent()
        return jdbcTemplate.update(sql, id) == 1
    }

    fun resetAfterRefundFailure(id: Long, message: String): Boolean {
        val sql = """
            UPDATE payment_support_requests
            SET status = 'PENDING', admin_message = ?, updated_at = NOW()
            WHERE id = ? AND status = 'PROCESSING'
        """.trimIndent()
        return jdbcTemplate.update(sql, message, id) == 1
    }
}
