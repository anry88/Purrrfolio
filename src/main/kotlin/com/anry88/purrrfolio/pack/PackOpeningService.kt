package com.anry88.purrrfolio.pack

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
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
    fun rollCards(count: Int, ownedCardIds: Set<String>, seed: Long? = null): List<CardDefinition> {
        val random = seed?.let { ThreadLocalRandom.current() } ?: ThreadLocalRandom.current()
        return buildList {
            repeat(count) {
                add(drawOne(random))
            }
        }
    }

    fun formatReveal(card: CardDefinition, isNew: Boolean): String {
        val badge = if (isNew) " ✨ NEW" else ""
        return "${card.rarity.emoji} *${card.nameRu}* — ${card.rarity.labelRu}$badge"
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
        val pool = cardCatalog.cardsByRarity(chosenRarity)
        return pool[random.nextInt(pool.size)]
    }
}
