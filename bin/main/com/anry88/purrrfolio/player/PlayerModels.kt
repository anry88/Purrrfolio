package com.anry88.purrrfolio.player

import java.time.OffsetDateTime

data class Player(
    val id: Long = 0,
    val telegramId: Long,
    val username: String?,
    val displayName: String?,
    val fishBalance: Int,
    val locale: String,
    val lastDailyAt: OffsetDateTime?,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

data class PlayerCard(
    val id: Long = 0,
    val playerId: Long,
    val cardId: String,
    val quantity: Int,
    val firstObtained: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
