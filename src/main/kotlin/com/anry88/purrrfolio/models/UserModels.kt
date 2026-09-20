package com.anry88.purrrfolio.models

import java.time.OffsetDateTime

data class User(
    val id: Long,
    val telegramUserId: Long,
    val language: String,
    val lastFreePackOpenedAt: OffsetDateTime?,
    val lastFreeCardAt: OffsetDateTime?,
    val craftPoints: Int,
    val registrationSource: String,
    val availablePacks: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class UserCard(
    val id: Long,
    val userId: Long,
    val cardId: String,
    val quantity: Int,
    val firstObtained: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class PackLedger(
    val id: Long,
    val userId: Long,
    val source: String,
    val quantity: Int,
    val starsPaid: Int?,
    val paymentId: String?,
    val createdAt: OffsetDateTime,
)

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
}

data class Payment(
    val id: Long,
    val userId: Long,
    val telegramPaymentId: String,
    val stars: Int,
    val packsGranted: Int,
    val status: PaymentStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

enum class PaymentSupportStatus {
    PENDING,
    INFO,
    PROCESSING,
    REFUNDED,
    REJECTED,
}

data class PaymentSupportRequest(
    val id: Long,
    val userId: Long,
    val paymentId: Long,
    val reason: String,
    val status: PaymentSupportStatus,
    val adminMessage: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
