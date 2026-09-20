package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.models.Payment
import com.anry88.purrrfolio.models.PaymentStatus
import com.anry88.purrrfolio.models.PaymentSupportStatus
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import com.anry88.purrrfolio.repository.PaymentSupportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class StarsRefundService(
    private val paymentRepository: PaymentRepository,
    private val paymentSupportRepository: PaymentSupportRepository,
    private val packLedgerRepository: PackLedgerRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** Finalizes local state after Telegram confirms the external Stars refund. */
    fun finalizeRefund(requestId: Long, payment: Payment) {
        transactionTemplate.executeWithoutResult {
            val current = paymentRepository.findById(payment.id)
                ?: error("Payment ${payment.id} disappeared during refund")
            require(
                current.userId == payment.userId &&
                    current.telegramPaymentId == payment.telegramPaymentId &&
                    current.packsGranted == payment.packsGranted
            ) { "Payment changed during refund" }

            packLedgerRepository.addPaymentPacksOnce(
                userId = current.userId,
                source = "refund",
                quantity = -current.packsGranted,
                starsPaid = null,
                paymentId = "refund:${current.telegramPaymentId}",
            )
            when (current.status) {
                PaymentStatus.COMPLETED -> check(paymentRepository.markPaymentRefundedIfCompleted(current.id))
                PaymentStatus.REFUNDED -> Unit // Idempotent local recovery.
                PaymentStatus.PENDING, PaymentStatus.FAILED -> error("Only completed payments can be refunded")
            }
            check(paymentSupportRepository.updateStatus(requestId, PaymentSupportStatus.REFUNDED))
        }
    }
}
