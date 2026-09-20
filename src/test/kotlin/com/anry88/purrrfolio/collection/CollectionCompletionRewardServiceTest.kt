package com.anry88.purrrfolio.collection

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.models.UserCard
import com.anry88.purrrfolio.repository.CollectionCompletionRewardRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.OffsetDateTime
import java.util.UUID

class CollectionCompletionRewardServiceTest {
    private val catalog = CardCatalog(ObjectMapper().registerModule(kotlinModule()))

    @Test
    fun `complete collection grants one pack only once per catalog version`() {
        val cards = mock(UserCardRepository::class.java)
        val rewards = mock(CollectionCompletionRewardRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        val cozyIds = catalog.cardsByCollection("cozy-home").map { it.id }
        `when`(cards.findByUserId(7)).thenReturn(cozyIds.mapIndexed { index, id -> userCard(index, id) })
        `when`(rewards.claim(anyLong(), anyString(), anyString(), anyInt())).thenReturn(true, false)
        val service = service(cards, rewards, ledger)

        assertEquals(listOf("cozy-home"), service.claimCompletedCollections(7).map { it.id })
        assertEquals(emptyList<String>(), service.claimCompletedCollections(7).map { it.id })

        verify(ledger).addPacks(7, CollectionCompletionRewardService.PACK_SOURCE, 1)
    }

    @Test
    fun `incomplete collection does not create a claim or grant a pack`() {
        val cards = mock(UserCardRepository::class.java)
        val rewards = mock(CollectionCompletionRewardRepository::class.java)
        val ledger = mock(PackLedgerRepository::class.java)
        val almostComplete = catalog.cardsByCollection("cozy-home").dropLast(1).map { it.id }
        `when`(cards.findByUserId(7)).thenReturn(almostComplete.mapIndexed { index, id -> userCard(index, id) })

        assertEquals(emptyList<String>(), service(cards, rewards, ledger).claimCompletedCollections(7).map { it.id })

        verifyNoInteractions(rewards, ledger)
    }

    @Test
    fun `adding a card changes collection version while card order does not`() {
        val original = CollectionCompletionRewardService.catalogVersion(listOf("a", "b"))
        assertEquals(original, CollectionCompletionRewardService.catalogVersion(listOf("b", "a")))
        assertNotEquals(original, CollectionCompletionRewardService.catalogVersion(listOf("a", "b", "c")))
    }

    private fun service(
        cards: UserCardRepository,
        rewards: CollectionCompletionRewardRepository,
        ledger: PackLedgerRepository,
    ): CollectionCompletionRewardService {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL", "sa", "")
        return CollectionCompletionRewardService(
            catalog,
            cards,
            rewards,
            ledger,
            DataSourceTransactionManager(dataSource),
        )
    }

    private fun userCard(index: Int, cardId: String): UserCard {
        val now = OffsetDateTime.now()
        return UserCard(index.toLong() + 1, 7, cardId, 1, now, now)
    }
}
