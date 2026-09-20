package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.models.PaymentStatus
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class StarsPurchaseService(
    private val paymentRepository: PaymentRepository,
    private val packLedgerRepository: PackLedgerRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    enum class Result {
        CREDITED,
        ALREADY_CREDITED,
        ALREADY_REFUNDED,
    }

    /**
     * Persists the payment, grants its packs, and marks it complete in one DB
     * transaction. The charge id and ledger uniqueness make Telegram retries safe.
     */
    fun fulfill(userId: Long, chargeId: String, stars: Int, packs: Int): Result =
        transactionTemplate.execute {
            val existing = paymentRepository.findByTelegramPaymentId(chargeId)
            if (existing != null) {
                require(existing.userId == userId && existing.stars == stars && existing.packsGranted == packs) {
                    "Telegram charge id is already bound to a different order"
                }
                when (existing.status) {
                    PaymentStatus.COMPLETED -> return@execute Result.ALREADY_CREDITED
                    PaymentStatus.REFUNDED -> return@execute Result.ALREADY_REFUNDED
                    PaymentStatus.PENDING, PaymentStatus.FAILED -> Unit
                }
            } else {
                paymentRepository.createPayment(userId, chargeId, stars, packs)
            }

            packLedgerRepository.addPaymentPacksOnce(userId, "stars", packs, stars, chargeId)
            val payment = paymentRepository.findByTelegramPaymentId(chargeId)
                ?: error("Payment disappeared during fulfillment")
            paymentRepository.markPaymentCompleted(payment.id)
            Result.CREDITED
        } ?: error("Stars payment transaction returned no result")
}
