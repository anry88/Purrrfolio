package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.repository.MarketRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime

/**
 * Returns market listings that stayed unsold for longer than [EXPIRY_DAYS] to their owners.
 *
 * Runs once a day around 16:00 server time. Listings are processed in small batches so a
 * large market does not blow up memory, and each owner gets a single aggregated message in
 * their own language (long card lists are truncated with a "…and N more" tail).
 */
@Service
class MarketListingExpiryService(
    private val marketRepository: MarketRepository,
    private val userCardRepository: UserCardRepository,
    private val userRepository: UserRepository,
    private val cardCatalog: CardCatalog,
    private val telegramClient: TelegramClient,
    private val gameMetrics: GameMetrics,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Scheduled(cron = "0 0 16 * * *")
    fun expireStaleListingsScheduled() {
        runCatching {
            expireStaleListings(OffsetDateTime.now())
        }.onFailure { error ->
            logger.error("Failed to expire stale market listings", error)
        }
    }

    /** Expires listings with `created_at <= now - EXPIRY_DAYS`. Returns the number of returned cards. */
    fun expireStaleListings(now: OffsetDateTime): Int {
        val cutoff = now.minusDays(EXPIRY_DAYS)
        val returnedBySeller = mutableMapOf<Long, MutableList<String>>()
        var total = 0
        var batches = 0
        while (batches < MAX_BATCHES) {
            val batch = marketRepository.findExpiredActiveListings(cutoff, BATCH_SIZE)
            if (batch.isEmpty()) break
            batches++
            for (listing in batch) {
                val returned = transactionTemplate.execute {
                    if (!marketRepository.cancelActiveListing(listing.id)) return@execute false
                    marketRepository.cancelPendingOffersForListing(listing.id)
                    userCardRepository.addCards(listing.sellerId, listOf(listing.cardId))
                    true
                } == true
                if (returned) {
                    returnedBySeller.getOrPut(listing.sellerId) { mutableListOf() }.add(listing.cardId)
                    total++
                }
            }
        }
        if (batches >= MAX_BATCHES) {
            logger.warn("Market listing expiry hit the batch cap ({} batches); remaining stale listings will be picked up next run", MAX_BATCHES)
        }
        if (total > 0) {
            gameMetrics.marketExpired(total)
            logger.info("Expired {} stale market listings for {} sellers", total, returnedBySeller.size)
            notifySellers(returnedBySeller)
        }
        return total
    }

    private fun notifySellers(returnedBySeller: Map<Long, List<String>>) {
        for ((sellerId, cardIds) in returnedBySeller) {
            runCatching {
                val owner = userRepository.findById(sellerId) ?: return@runCatching
                val locale = GameLocale.fromCode(owner.language)
                telegramClient.sendMessage(owner.telegramUserId, expiryMessage(cardIds, locale))
            }.onFailure { error ->
                logger.warn("Failed to notify seller {} about expired market listings", sellerId, error)
            }
        }
    }

    private fun expiryMessage(cardIds: List<String>, locale: GameLocale): String {
        val sorted = cardIds.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { displayName(it.key, locale) })
        val shown = mutableListOf<String>()
        var shownCards = 0
        var length = 0
        for ((cardId, count) in sorted) {
            val name = displayName(cardId, locale)
            val line = if (count > 1) "×$count $name" else name
            if (shown.size >= MAX_LISTED_LINES || length + line.length + 1 > MAX_MESSAGE_CHARS) break
            shown.add("• $line")
            length += line.length + 3
            shownCards += count
        }
        if (shownCards < cardIds.size) {
            shown.add(Messages.t("market.expiredMore", locale, cardIds.size - shownCards))
        }
        return Messages.t("market.expiredReturned", locale, shown.joinToString("\n"))
    }

    private fun displayName(cardId: String, locale: GameLocale): String =
        runCatching { cardCatalog.card(cardId).nameFor(locale) }.getOrElse { cardId }

    companion object {
        const val EXPIRY_DAYS = 7L
        const val BATCH_SIZE = 200
        const val MAX_BATCHES = 10_000
        const val MAX_LISTED_LINES = 30
        const val MAX_MESSAGE_CHARS = 3_500
    }
}
