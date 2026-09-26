package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.catalog.CardDefinition
import com.anry88.purrrfolio.catalog.CardRarity
import com.anry88.purrrfolio.models.MarketListing
import com.anry88.purrrfolio.models.MarketListingStatus
import com.anry88.purrrfolio.models.User
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.repository.MarketRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.OffsetDateTime
import java.util.UUID

class MarketListingExpiryServiceTest {

    private val market = mock(MarketRepository::class.java)
    private val userCards = mock(UserCardRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val catalog = mock(CardCatalog::class.java)
    private val telegram = mock(TelegramClient::class.java)
    private val metrics = mock(GameMetrics::class.java)

    private val now = OffsetDateTime.parse("2026-09-26T12:00:00Z")
    private val cutoff = now.minusDays(7)

    /** chatId -> text, captured from TelegramClient.sendMessage. */
    private val sentMessages = mutableListOf<Pair<Long, String>>()

    @BeforeEach
    fun setUp() {
        doAnswer { invocation ->
            sentMessages.add(invocation.arguments[0] as Long to invocation.arguments[1] as String)
            null
        }.`when`(telegram).sendMessage(anyLong(), anyString(), isNull(), anyString())
    }

    @Test
    fun `stale listing is returned and owner is notified in russian`() {
        val listingId = UUID.randomUUID()
        val listing = listing(listingId, sellerId = 7, cardId = "c1", createdAt = now.minusDays(8))
        `when`(market.findExpiredActiveListings(cutoff, 200))
            .thenReturn(listOf(listing), emptyList())
        `when`(market.cancelActiveListing(listingId)).thenReturn(true)
        `when`(users.findById(7)).thenReturn(user(7, telegramId = 701, language = "ru"))
        `when`(catalog.card("c1")).thenReturn(card("c1", "Барсик", "Barsik"))

        val expired = service().expireStaleListings(now)

        assertEquals(1, expired)
        verify(market).cancelPendingOffersForListing(listingId)
        verify(userCards).addCards(7, listOf("c1"))
        verify(metrics).marketExpired(1)
        val text = singleMessage(701)
        assertTrue(text.contains("Барсик"), "owner message should list the card, was: $text")
        assertTrue(text.contains("возвращены"), "owner message should be russian, was: $text")
    }

    @Test
    fun `fresh listings are left alone`() {
        `when`(market.findExpiredActiveListings(cutoff, 200)).thenReturn(emptyList())

        val expired = service().expireStaleListings(now)

        assertEquals(0, expired)
        verify(market, never()).cancelActiveListing(UUID(0L, 0L))
        assertTrue(sentMessages.isEmpty(), "nobody should be notified")
    }

    @Test
    fun `lost cancellation race returns nothing and notifies nobody`() {
        val listingId = UUID.randomUUID()
        val listing = listing(listingId, sellerId = 7, cardId = "c1", createdAt = now.minusDays(9))
        `when`(market.findExpiredActiveListings(cutoff, 200))
            .thenReturn(listOf(listing), emptyList())
        `when`(market.cancelActiveListing(listingId)).thenReturn(false)

        val expired = service().expireStaleListings(now)

        assertEquals(0, expired)
        verify(userCards, never()).addCards(anyLong(), anyList())
        assertTrue(sentMessages.isEmpty(), "nobody should be notified")
    }

    @Test
    fun `multiple cards of one seller arrive in a single message`() {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val listings = listOf(
            listing(ids[0], sellerId = 7, cardId = "c1", createdAt = now.minusDays(8)),
            listing(ids[1], sellerId = 7, cardId = "c1", createdAt = now.minusDays(10)),
            listing(ids[2], sellerId = 7, cardId = "c2", createdAt = now.minusDays(11)),
        )
        `when`(market.findExpiredActiveListings(cutoff, 200))
            .thenReturn(listings, emptyList())
        ids.forEach { id -> `when`(market.cancelActiveListing(id)).thenReturn(true) }
        `when`(users.findById(7)).thenReturn(user(7, telegramId = 701, language = "en"))
        `when`(catalog.card("c1")).thenReturn(card("c1", "Барсик", "Barsik"))
        `when`(catalog.card("c2")).thenReturn(card("c2", "Мурка", "Murka"))

        val expired = service().expireStaleListings(now)

        assertEquals(3, expired)
        val text = singleMessage(701)
        assertTrue(text.contains("×2 Barsik"), "duplicates should be grouped, was: $text")
        assertTrue(text.contains("Murka"), "was: $text")
    }

    private fun singleMessage(chatId: Long): String {
        val texts = sentMessages.filter { it.first == chatId }.map { it.second }
        assertEquals(1, texts.size, "expected a single aggregated message, got: $texts")
        return texts.single()
    }

    private fun service(): MarketListingExpiryService {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL", "sa", "")
        return MarketListingExpiryService(
            market, userCards, users, catalog, telegram, metrics,
            DataSourceTransactionManager(dataSource),
        )
    }

    private fun listing(id: UUID, sellerId: Long, cardId: String, createdAt: OffsetDateTime) = MarketListing(
        id = id,
        sellerId = sellerId,
        cardId = cardId,
        status = MarketListingStatus.ACTIVE,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun user(id: Long, telegramId: Long, language: String) = User(
        id = id,
        telegramUserId = telegramId,
        language = language,
        lastFreePackOpenedAt = null,
        lastFreeCardAt = null,
        craftPoints = 0,
        registrationSource = "direct",
        availablePacks = 0,
        createdAt = now,
        updatedAt = now,
    )

    private fun card(id: String, nameRu: String, nameEn: String) = CardDefinition(
        id = id,
        nameRu = nameRu,
        nameEn = nameEn,
        themeId = "cozy",
        rarity = CardRarity.COMMON,
        imagePath = "",
    )
}
