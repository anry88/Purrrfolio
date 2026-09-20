package com.anry88.purrrfolio.collection

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.ThemeDefinition
import com.anry88.purrrfolio.repository.CollectionCompletionRewardRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class CollectionCompletionRewardService(
    private val cardCatalog: CardCatalog,
    private val userCardRepository: UserCardRepository,
    private val rewardRepository: CollectionCompletionRewardRepository,
    private val packLedgerRepository: PackLedgerRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Awards one pack for every collection that is complete for its current
     * catalog version and has not already been rewarded for that version.
     */
    fun claimCompletedCollections(userId: Long): List<ThemeDefinition> =
        transactionTemplate.execute {
            val ownedCardIds = userCardRepository.findByUserId(userId).mapTo(mutableSetOf()) { it.cardId }
            cardCatalog.collections.mapNotNull { theme ->
                val themeCards = cardCatalog.cardsByCollection(theme.id)
                if (themeCards.isEmpty() || themeCards.any { it.id !in ownedCardIds }) {
                    return@mapNotNull null
                }

                val version = catalogVersion(themeCards.map { it.id })
                if (!rewardRepository.claim(userId, theme.id, version, themeCards.size)) {
                    return@mapNotNull null
                }

                packLedgerRepository.addPacks(userId, PACK_SOURCE, 1)
                theme
            }
        } ?: emptyList()

    companion object {
        const val PACK_SOURCE = "collection_completion"

        fun catalogVersion(cardIds: Collection<String>): String {
            val canonical = cardIds.sorted().joinToString("\u0000")
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}
