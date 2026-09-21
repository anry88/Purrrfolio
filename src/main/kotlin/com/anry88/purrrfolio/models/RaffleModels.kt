package com.anry88.purrrfolio.models

import java.time.OffsetDateTime

data class GroupRaffle(
    val id: Long,
    val chatId: Long,
    val memberCount: Int,
    val packsGranted: Int,
    val raffledAt: OffsetDateTime,
)

data class GroupRaffleWinner(
    val raffleId: Long,
    val userId: Long,
    val packs: Int,
)

/** A registered player seen in a group chat: a candidate for the daily pack raffle. */
data class GroupRaffleCandidate(
    val userId: Long,
    val telegramUserId: Long,
    val displayName: String,
)
