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
    fun rollCards(count: Int, ownedCardIds: Set<String>): List<CardDefinition> {
        val random = ThreadLocalRandom.current()
        return buildList {
            repeat(count) {
                add(drawOne(random))
            }
        }
    }

    fun formatReveal(card: CardDefinition, isNew: Boolean, locale: GameLocale): String {
        val badge = if (isNew) Messages.t("pack.newCard", locale) else ""
        val rarityLabel = when (locale) {
            GameLocale.RU -> card.rarity.labelRu
            GameLocale.EN -> card.rarity.labelEn
        }
        return "${card.rarity.emoji} *${card.nameFor(locale)}* — $rarityLabel$badge"
    }

    private fun drawOne(random: ThreadLocalRandom): CardDefinition {
        val totalWeight = CardRarity.entries.sumOf { it.weight }
        var roll = random.nextInt(max(totalWeight, 1))
        var chosenRarity = CardRarity.COMMON
        for (rarity in CardRarity.entries) {
            roll -= rarity.weight
            if (roll < 0) {
                chosenRarity = rarity
                break
            }
        }
        
        // Find a rarity with available cards
        var pool = cardCatalog.cardsByRarity(chosenRarity)
        if (pool.isEmpty()) {
            // Fallback to any available rarity with cards
            pool = CardRarity.entries
                .map { cardCatalog.cardsByRarity(it) }
                .firstOrNull { it.isNotEmpty() } ?: listOf(cardCatalog.card("sleepy"))
        }
        
        return pool[random.nextInt(pool.size)]
    }
}
