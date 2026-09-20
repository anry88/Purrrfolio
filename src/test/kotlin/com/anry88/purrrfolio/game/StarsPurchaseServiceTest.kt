package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.models.Payment
import com.anry88.purrrfolio.models.PaymentStatus
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.OffsetDateTime
import java.util.UUID

class StarsPurchaseServiceTest {

    @Test
    fun `fresh Telegram charge is granted and completed once`() {
        val payments = mock(PaymentRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        val payment = payment(status = PaymentStatus.PENDING)
        `when`(payments.findByTelegramPaymentId("charge-1")).thenReturn(null, payment)

        val result = service(payments, ledger).fulfill(7, "charge-1", 12, 3)

        assertEquals(StarsPurchaseService.Result.CREDITED, result)
        verify(payments).createPayment(7, "charge-1", 12, 3)
        verify(ledger).addPaymentPacksOnce(7, "stars", 3, 12, "charge-1")
        verify(payments).markPaymentCompleted(payment.id)
    }

    @Test
    fun `completed Telegram charge retry does not grant packs twice`() {
        val payments = mock(PaymentRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        `when`(payments.findByTelegramPaymentId("charge-1")).thenReturn(payment(status = PaymentStatus.COMPLETED))

        val result = service(payments, ledger).fulfill(7, "charge-1", 12, 3)

        assertEquals(StarsPurchaseService.Result.ALREADY_CREDITED, result)
        verifyNoInteractions(ledger)
        verify(payments, never()).createPayment(7, "charge-1", 12, 3)
    }

    @Test
    fun `charge id cannot be rebound to another order`() {
        val payments = mock(PaymentRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        `when`(payments.findByTelegramPaymentId("charge-1")).thenReturn(payment(status = PaymentStatus.COMPLETED))

        assertThrows(IllegalArgumentException::class.java) {
            service(payments, ledger).fulfill(99, "charge-1", 25, 10)
        }
        verifyNoInteractions(ledger)
    }

    @Test
    fun `refunded Telegram charge replay is acknowledged without recrediting`() {
        val payments = mock(PaymentRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        `when`(payments.findByTelegramPaymentId("charge-1")).thenReturn(payment(status = PaymentStatus.REFUNDED))

        val result = service(payments, ledger).fulfill(7, "charge-1", 12, 3)

        assertEquals(StarsPurchaseService.Result.ALREADY_REFUNDED, result)
        verifyNoInteractions(ledger)
        verify(payments, never()).markPaymentCompleted(41)
    }

    private fun service(payments: PaymentRepository, ledger: PackLedgerRepository): StarsPurchaseService {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL", "sa", "")
        return StarsPurchaseService(payments, ledger, DataSourceTransactionManager(dataSource))
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
