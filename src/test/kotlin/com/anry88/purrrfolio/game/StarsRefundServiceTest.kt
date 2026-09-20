package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.models.Payment
import com.anry88.purrrfolio.models.PaymentStatus
import com.anry88.purrrfolio.models.PaymentSupportStatus
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import com.anry88.purrrfolio.repository.PaymentSupportRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.OffsetDateTime
import java.util.UUID

class StarsRefundServiceTest {

    @Test
    fun `confirmed refund revokes packs and closes request`() {
        val payments = mock(PaymentRepository::class.java)
        val support = mock(PaymentSupportRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        val payment = payment(PaymentStatus.COMPLETED)
        `when`(payments.findById(payment.id)).thenReturn(payment)
        `when`(payments.markPaymentRefundedIfCompleted(payment.id)).thenReturn(true)
        `when`(support.updateStatus(9, PaymentSupportStatus.REFUNDED)).thenReturn(true)

        service(payments, support, ledger).finalizeRefund(9, payment)

        verify(ledger).addPaymentPacksOnce(7, "refund", -3, null, "refund:charge-1")
        verify(payments).markPaymentRefundedIfCompleted(payment.id)
        verify(support).updateStatus(9, PaymentSupportStatus.REFUNDED)
    }

    @Test
    fun `local retry after prior refund is idempotent`() {
        val payments = mock(PaymentRepository::class.java)
        val support = mock(PaymentSupportRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        val refunded = payment(PaymentStatus.REFUNDED)
        `when`(payments.findById(refunded.id)).thenReturn(refunded)
        `when`(support.updateStatus(9, PaymentSupportStatus.REFUNDED)).thenReturn(true)

        service(payments, support, ledger).finalizeRefund(9, refunded)

        verify(ledger).addPaymentPacksOnce(7, "refund", -3, null, "refund:charge-1")
        verify(payments, never()).markPaymentRefundedIfCompleted(refunded.id)
    }

    private fun service(
        payments: PaymentRepository,
        support: PaymentSupportRepository,
        ledger: PackLedgerRepository,
    ): StarsRefundService {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL", "sa", "")
        return StarsRefundService(payments, support, ledger, DataSourceTransactionManager(dataSource))
    }

    private fun payment(status: PaymentStatus) = Payment(
        id = 41,
        userId = 7,
        telegramPaymentId = "charge-1",
        stars = 12,
        packsGranted = 3,
        status = status,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
