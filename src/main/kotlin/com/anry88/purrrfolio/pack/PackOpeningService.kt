package com.anry88.purrrfolio.pack

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.i18n.nameFor
import org.springframework.stereotype.Service
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

data class PackOpenResult(
    val cards: List<CardDefinition>,
    val newCardIds: Set<String>,
    val spentFish: Int,
    val remainingFish: Int,
)

@Service
class PackOpeningService(
    private val cardCatalog: CardCatalog,
) {
    fun rollCards(count: Int, ownedCardIds: Set<String>, month: Int): List<CardDefinition> {
        require(month in 1..12) { "month must be between 1 and 12" }
        val random = ThreadLocalRandom.current()
        return buildList {
            repeat(count) {
                add(drawOne(random, month))
            }
        }
    }

    fun formatReveal(card: CardDefinition, isNew: Boolean, locale: GameLocale): String {
        val badge = if (isNew) Messages.t("pack.newCard", locale) else ""
        val rarityLabel = when (locale) {
            GameLocale.RU -> card.rarity.labelRu
            GameLocale.EN -> card.rarity.labelEn
        }
        val specialLabel = if (card.special) Messages.t("card.special", locale) + "\n" else ""
        return "$specialLabel${card.rarity.emoji} *${card.nameFor(locale)}* — $rarityLabel$badge"
    }

    fun isAvailable(card: CardDefinition, month: Int): Boolean =
        card.availableMonths.isEmpty() || month in card.availableMonths

    fun rarityForRoll(roll: Int): CardRarity {
        val totalWeight = CardRarity.entries.sumOf { it.weight }
        require(roll in 0 until totalWeight) { "roll must be between 0 and ${totalWeight - 1}" }
        var remaining = roll
        for (rarity in CardRarity.entries) {
            remaining -= rarity.weight
            if (remaining < 0) return rarity
        }
        error("rarity weights do not cover roll $roll")
    }

    private fun drawOne(random: ThreadLocalRandom, month: Int): CardDefinition {
        val totalWeight = CardRarity.entries.sumOf { it.weight }
        val chosenRarity = rarityForRoll(random.nextInt(max(totalWeight, 1)))
        
        // Find a rarity with available cards
        var pool = cardCatalog.cardsByRarity(chosenRarity).filter { isAvailable(it, month) }
        if (pool.isEmpty()) {
            // Fallback to any available rarity with cards
            pool = CardRarity.entries
                .map { rarity -> cardCatalog.cardsByRarity(rarity).filter { isAvailable(it, month) } }
                .firstOrNull { it.isNotEmpty() } ?: listOf(cardCatalog.card("sleepy"))
        }
        
        return pool[random.nextInt(pool.size)]
    }
}
