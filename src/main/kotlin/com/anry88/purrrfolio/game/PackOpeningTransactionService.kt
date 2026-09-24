package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.collection.CollectionCompletionRewardService
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.collection.ThemeProgress
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PackOpeningReceipt
import com.anry88.purrrfolio.repository.PackOpeningReceiptRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

sealed interface PackOpeningAttempt {
    data object NoPacks : PackOpeningAttempt

    data class Opened(
        val cards: List<CardDefinition>,
        val newCardIds: Set<String>,
        val affectedProgress: List<ThemeProgress>,
        val completedCollections: List<ThemeDefinition>,
        val remainingPacks: Int,
    ) : PackOpeningAttempt
}

@Service
class PackOpeningTransactionService(
    private val properties: PurrrfolioProperties,
    private val cardCatalog: CardCatalog,
    private val packOpeningService: PackOpeningService,
    private val collectionService: CollectionService,
    private val collectionCompletionRewardService: CollectionCompletionRewardService,
    private val userRepository: UserRepository,
    private val userCardRepository: UserCardRepository,
    private val packLedgerRepository: PackLedgerRepository,
    private val packOpeningReceiptRepository: PackOpeningReceiptRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Serializes openings for one player by locking their user row. The balance
     * check, debit, inventory upsert, and completion reward all commit together.
     */
    fun open(userId: Long, month: Int, fromGroupChat: Boolean, updateId: Long? = null): PackOpeningAttempt =
        transactionTemplate.execute {
            check(userRepository.lockById(userId)) { "Player $userId does not exist" }
            if (updateId != null) {
                packOpeningReceiptRepository.findByUpdateId(updateId)?.let { receipt ->
                    check(receipt.userId == userId) { "Telegram update $updateId belongs to another player" }
                    return@execute restoreAttempt(receipt)
                }
            }
            if (packLedgerRepository.getTotalAvailablePacks(userId) <= 0) {
                return@execute PackOpeningAttempt.NoPacks
            }

            val inventoryBefore = userCardRepository.findByUserId(userId).mapTo(mutableSetOf()) { it.cardId }
            val cards = packOpeningService.rollCards(
                properties.economy.cardsPerPack,
                inventoryBefore,
                month,
                fromGroupChat,
            )
            check(cards.size == properties.economy.cardsPerPack) {
                "Pack roll returned ${cards.size} cards instead of ${properties.economy.cardsPerPack}"
            }

            packLedgerRepository.addPacks(userId, OPENED_PACK_SOURCE, -1)
            userCardRepository.addCards(userId, cards.map { it.id })
            val completedCollections = collectionCompletionRewardService.claimCompletedCollections(userId)

            val inventoryAfter = inventoryBefore + cards.map { it.id }
            val affectedThemeIds = cards.mapTo(linkedSetOf()) { it.themeId }
            val affectedProgress = collectionService.buildThemeProgress(inventoryAfter, emptySet())
                .filter { it.themeId in affectedThemeIds }

            val result = PackOpeningAttempt.Opened(
                cards = cards,
                newCardIds = cards.map { it.id }.filterNotTo(linkedSetOf()) { it in inventoryBefore },
                affectedProgress = affectedProgress,
                completedCollections = completedCollections,
                remainingPacks = packLedgerRepository.getTotalAvailablePacks(userId),
            )
            if (updateId != null) {
                packOpeningReceiptRepository.insert(
                    updateId = updateId,
                    userId = userId,
                    cardIds = cards.map { it.id },
                    newCardIds = result.newCardIds,
                    completedCollectionIds = completedCollections.map { it.id },
                )
            }
            result
        } ?: error("Pack opening transaction returned no result")

    private fun restoreAttempt(receipt: PackOpeningReceipt): PackOpeningAttempt.Opened {
        val cards = receipt.cardIds.map(cardCatalog::card)
        val affectedThemeIds = cards.mapTo(linkedSetOf()) { it.themeId }
        val ownedIds = userCardRepository.findByUserId(receipt.userId).mapTo(mutableSetOf()) { it.cardId }
        return PackOpeningAttempt.Opened(
            cards = cards,
            newCardIds = receipt.newCardIds,
            affectedProgress = collectionService.buildThemeProgress(ownedIds, emptySet())
                .filter { it.themeId in affectedThemeIds },
            completedCollections = receipt.completedCollectionIds.map(cardCatalog::collection),
            remainingPacks = packLedgerRepository.getTotalAvailablePacks(receipt.userId),
        )
    }

    companion object {
        const val OPENED_PACK_SOURCE = "opened"
    }
}
