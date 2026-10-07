package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.collection.CollectionCompletionRewardService
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime

sealed interface FreeCardAttempt {
    data class Wait(val lastClaimedAt: OffsetDateTime?) : FreeCardAttempt
    data class Claimed(val card: CardDefinition, val isNew: Boolean,
                       val completedCollections: List<ThemeDefinition>, val claimedAt: OffsetDateTime) : FreeCardAttempt
}

@Service
class FreeCardClaimTransactionService(
    private val properties: PurrrfolioProperties,
    private val catalog: CardCatalog,
    private val roller: PackOpeningService,
    private val users: UserRepository,
    private val inventory: UserCardRepository,
    private val receipts: FreeCardReceiptRepository,
    private val completionRewards: CollectionCompletionRewardService,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

    fun claim(userId: Long, now: OffsetDateTime, fromGroupChat: Boolean, updateId: Long?): FreeCardAttempt =
        transactions.execute {
            check(users.lockById(userId))
            if (updateId != null) receipts.find(updateId)?.let { receipt ->
                check(receipt.userId == userId) { "Free-card update belongs to another player" }
                return@execute FreeCardAttempt.Claimed(catalog.card(receipt.cardId), receipt.isNew,
                    receipt.completedCollectionIds.map(catalog::collection), receipt.createdAt)
            }
            if (!users.claimFreeCardIfDue(userId, now, properties.economy.freeCardIntervalHours)) {
                return@execute FreeCardAttempt.Wait(users.findById(userId)?.lastFreeCardAt)
            }
            val owned = inventory.findByUserId(userId).mapTo(mutableSetOf()) { it.cardId }
            val card = roller.rollCards(1, owned, now.toLocalDate(), fromGroupChat).single()
            inventory.addDrawnCards(userId, listOf(card), CardDrawSource.FREE_CARD, updateId, now)
            val completed = completionRewards.claimCompletedCollections(userId)
            if (updateId != null) receipts.insert(updateId,
                FreeCardReceipt(userId, card.id, card.id !in owned, completed.map { it.id }, now))
            FreeCardAttempt.Claimed(card, card.id !in owned, completed, now)
        } ?: error("Free-card transaction returned no result")
}
