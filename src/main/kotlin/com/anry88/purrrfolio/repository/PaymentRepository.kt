package com.anry88.purrrfolio.repository

import com.anry88.purrrfolio.models.Payment
import com.anry88.purrrfolio.models.PaymentStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class PaymentRepository(private val jdbcTemplate: JdbcTemplate) {

    private val paymentRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Payment(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            telegramPaymentId = rs.getString("telegram_payment_id"),
            stars = rs.getInt("stars"),
            packsGranted = rs.getInt("packs_granted"),
            status = PaymentStatus.valueOf(rs.getString("status")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    fun createPayment(userId: Long, telegramPaymentId: String, stars: Int, packsGranted: Int): Payment {
        val sql = """
            INSERT INTO payments (user_id, telegram_payment_id, stars, packs_granted, status)
            VALUES (?, ?, ?, ?, 'PENDING')
            RETURNING *
        """.trimIndent()
        val results = jdbcTemplate.query(sql, paymentRowMapper, userId, telegramPaymentId, stars, packsGranted)
        return results.first()
    }

    fun findByTelegramPaymentId(telegramPaymentId: String): Payment? {
        val sql = "SELECT * FROM payments WHERE telegram_payment_id = ?"
        val results = jdbcTemplate.query(sql, paymentRowMapper, telegramPaymentId)
        return results.firstOrNull()
    }

    fun updatePaymentStatus(paymentId: Long, status: PaymentStatus) {
        val sql = """
            UPDATE payments 
            SET status = ?, updated_at = NOW() 
            WHERE id = ?
        """.trimIndent()
        jdbcTemplate.update(sql, status.name, paymentId)
    }

    fun markPaymentCompleted(paymentId: Long) {
        updatePaymentStatus(paymentId, PaymentStatus.COMPLETED)
    }

    fun markPaymentFailed(paymentId: Long) {
        updatePaymentStatus(paymentId, PaymentStatus.FAILED)
    }

    fun markPaymentRefunded(paymentId: Long) {
        updatePaymentStatus(paymentId, PaymentStatus.REFUNDED)
    }

    fun findByUserId(userId: Long): List<Payment> {
        val sql = "SELECT * FROM payments WHERE user_id = ? ORDER BY created_at DESC"
        return jdbcTemplate.query(sql, paymentRowMapper, userId)
    }
}
