package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.catalog.CardCatalog
import com.anry88.purrrfolio.collection.CollectionCompletionRewardService
import com.anry88.purrrfolio.collection.CollectionService
import com.anry88.purrrfolio.config.EconomyProperties
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.config.TelegramProperties
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.pack.PackOpeningService
import com.anry88.purrrfolio.repository.CollectionCompletionRewardRepository
import com.anry88.purrrfolio.repository.GroupRaffleRepository
import com.anry88.purrrfolio.repository.MarketRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.PackOpeningReceiptRepository
import com.anry88.purrrfolio.repository.PaymentRepository
import com.anry88.purrrfolio.repository.PaymentSupportRepository
import com.anry88.purrrfolio.repository.ProcessedUpdateRepository
import com.anry88.purrrfolio.repository.RandomTradeRepository
import com.anry88.purrrfolio.repository.ReferralRewardRepository
import com.anry88.purrrfolio.repository.TelegramCardFileRepository
import com.anry88.purrrfolio.repository.UserCardRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramChat
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramMessage
import com.anry88.purrrfolio.telegram.TelegramInlineQuery
import com.anry88.purrrfolio.telegram.TelegramInlineQueryResultCachedPhoto
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import com.anry88.purrrfolio.telegram.TelegramUpdate
import com.anry88.purrrfolio.telegram.TelegramUser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
class PackOpeningPostgresIntegrationTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactionManager: DataSourceTransactionManager
    private lateinit var properties: PurrrfolioProperties
    private lateinit var catalog: CardCatalog
    private lateinit var collectionService: CollectionService
    private lateinit var packOpeningService: PackOpeningService
    private lateinit var registrationService: PlayerRegistrationService
    private lateinit var openingService: PackOpeningTransactionService
    private lateinit var userRepository: UserRepository
    private lateinit var userCardRepository: UserCardRepository
    private lateinit var packLedgerRepository: PackLedgerRepository
    private lateinit var packOpeningReceiptRepository: PackOpeningReceiptRepository
    private lateinit var referralRewardRepository: ReferralRewardRepository
    private lateinit var telegramCardFileRepository: TelegramCardFileRepository
    private lateinit var completionRewardService: CollectionCompletionRewardService
    private lateinit var processedUpdateRepository: ProcessedUpdateRepository

    @BeforeEach
    fun setUp() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).load().migrate()
        jdbc = JdbcTemplate(dataSource)
        jdbc.execute("TRUNCATE TABLE users, processed_telegram_updates CASCADE")
        transactionManager = DataSourceTransactionManager(dataSource)
        properties = PurrrfolioProperties(
            publicBaseUrl = "https://purrrfolio.example",
            telegram = TelegramProperties(botToken = "test", botUsername = "PurrrfolioBot"),
            economy = EconomyProperties(starterPacks = 1, cardsPerPack = 3),
        )
        catalog = CardCatalog(ObjectMapper().registerModule(kotlinModule()))
        collectionService = CollectionService(catalog)
        packOpeningService = PackOpeningService(catalog)
        userRepository = UserRepository(jdbc)
        userCardRepository = UserCardRepository(jdbc)
        packLedgerRepository = PackLedgerRepository(jdbc)
        packOpeningReceiptRepository = PackOpeningReceiptRepository(jdbc)
        referralRewardRepository = ReferralRewardRepository(jdbc)
        telegramCardFileRepository = TelegramCardFileRepository(jdbc)
        processedUpdateRepository = ProcessedUpdateRepository(jdbc)
        completionRewardService = CollectionCompletionRewardService(
            catalog,
            userCardRepository,
            CollectionCompletionRewardRepository(jdbc),
            packLedgerRepository,
            transactionManager,
        )
        registrationService = PlayerRegistrationService(
            properties,
            userRepository,
            packLedgerRepository,
            referralRewardRepository,
            transactionManager,
        )
        openingService = PackOpeningTransactionService(
            properties,
            catalog,
            packOpeningService,
            collectionService,
            completionRewardService,
            userRepository,
            userCardRepository,
            packLedgerRepository,
            packOpeningReceiptRepository,
            transactionManager,
        )
    }

    @Test
    fun `concurrent registration grants starter packs exactly once`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..2).map {
                executor.submit<PlayerRegistrationResult> {
                    start.await()
                    registrationService.registerIfMissing(telegramUserId = 7001, language = "en", registrationSource = "direct")
                }
            }
            start.countDown()
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }

            assertThat(results.map { it.user.id }.distinct()).hasSize(1)
            assertThat(results.count { it.created }).isEqualTo(1)
            assertThat(packLedgerRepository.getTotalAvailablePacks(results.first().user.id)).isEqualTo(1)
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pack_ledger", Int::class.java)).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `referral registration keeps three starter packs and grants five extra packs to both players once`() {
        val referralProperties = properties.copy(
            economy = EconomyProperties(starterPacks = 3, referralBonusPacks = 5, cardsPerPack = 3),
        )
        val referralRegistrationService = PlayerRegistrationService(
            referralProperties,
            userRepository,
            packLedgerRepository,
            referralRewardRepository,
            transactionManager,
        )
        val referrer = referralRegistrationService.registerIfMissing(7101, "en", "direct").user

        val first = referralRegistrationService.registerIfMissing(7102, "ru", "ref_${referrer.id}")
        val retry = referralRegistrationService.registerIfMissing(7102, "ru", "ref_${referrer.id}")

        assertThat(first.created).isTrue()
        assertThat(first.referralReward?.referrer?.id).isEqualTo(referrer.id)
        assertThat(first.referralReward?.packsEach).isEqualTo(5)
        assertThat(first.referralReward?.referrerRewarded).isTrue()
        assertThat(retry.created).isFalse()
        assertThat(retry.referralReward).isNull()
        assertThat(packLedgerRepository.getTotalAvailablePacks(first.user.id)).isEqualTo(8)
        assertThat(packLedgerRepository.getTotalAvailablePacks(referrer.id)).isEqualTo(8)
        assertThat(packLedgerRepository.findByUserId(first.user.id).associate { it.source to it.quantity })
            .containsEntry("starter", 3)
            .containsEntry("referral_joiner", 5)
        assertThat(packLedgerRepository.findByUserId(referrer.id).associate { it.source to it.quantity })
            .containsEntry("starter", 3)
            .containsEntry("referral_referrer", 5)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `unknown referral keeps starter packs without granting a bonus`() {
        val referralProperties = properties.copy(
            economy = EconomyProperties(starterPacks = 3, referralBonusPacks = 5, cardsPerPack = 3),
        )
        val referralRegistrationService = PlayerRegistrationService(
            referralProperties,
            userRepository,
            packLedgerRepository,
            referralRewardRepository,
            transactionManager,
        )

        val registration = referralRegistrationService.registerIfMissing(7103, "en", "ref_999999")

        assertThat(registration.referralReward).isNull()
        assertThat(packLedgerRepository.getTotalAvailablePacks(registration.user.id)).isEqualTo(3)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards", Int::class.java)).isZero()
    }

    @Test
    fun `one referrer rewards at most ten newcomers per calendar month`() {
        val referralProperties = properties.copy(
            economy = EconomyProperties(
                starterPacks = 3,
                referralBonusPacks = 5,
                referralMonthlyLimit = 10,
                cardsPerPack = 3,
            ),
        )
        val referralRegistrationService = PlayerRegistrationService(
            referralProperties,
            userRepository,
            packLedgerRepository,
            referralRewardRepository,
            transactionManager,
        )
        val referrer = referralRegistrationService.registerIfMissing(7200, "en", "direct").user

        val newcomers = (1L..11L).map { index ->
            referralRegistrationService.registerIfMissing(7200 + index, "en", "ref_${referrer.id}")
        }

        assertThat(newcomers.take(10)).allSatisfy { registration ->
            assertThat(registration.referralReward).isNotNull()
            assertThat(packLedgerRepository.getTotalAvailablePacks(registration.user.id)).isEqualTo(8)
        }
        assertThat(newcomers.last().referralReward).isNotNull()
        assertThat(newcomers.last().referralReward?.referrerRewarded).isFalse()
        assertThat(packLedgerRepository.getTotalAvailablePacks(newcomers.last().user.id)).isEqualTo(8)
        assertThat(packLedgerRepository.getTotalAvailablePacks(referrer.id)).isEqualTo(53)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards", Int::class.java)).isEqualTo(11)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM referral_rewards WHERE referrer_rewarded",
                Int::class.java,
            ),
        ).isEqualTo(10)
    }

    @Test
    fun `two simultaneous taps cannot spend one pack twice`() {
        val user = registrationService.registerIfMissing(7002, "en", "direct").user
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..2).map {
                executor.submit<PackOpeningAttempt> {
                    start.await()
                    openingService.open(user.id, month = 9, fromGroupChat = false)
                }
            }
            start.countDown()
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }

            assertThat(results.count { it is PackOpeningAttempt.Opened }).isEqualTo(1)
            assertThat(results.count { it is PackOpeningAttempt.NoPacks }).isEqualTo(1)
            assertThat(packLedgerRepository.getTotalAvailablePacks(user.id)).isZero()
            assertThat(userCardRepository.findByUserId(user.id).sumOf { it.quantity }).isEqualTo(3)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `retrying the same Telegram update restores cards without another debit`() {
        val user = registrationService.registerIfMissing(7004, "en", "direct").user

        val first = openingService.open(user.id, month = 9, fromGroupChat = false, updateId = 9100)
        val retry = openingService.open(user.id, month = 9, fromGroupChat = false, updateId = 9100)

        assertThat(first).isInstanceOf(PackOpeningAttempt.Opened::class.java)
        assertThat(retry).isInstanceOf(PackOpeningAttempt.Opened::class.java)
        assertThat((retry as PackOpeningAttempt.Opened).cards.map { it.id })
            .containsExactlyElementsOf((first as PackOpeningAttempt.Opened).cards.map { it.id })
        assertThat(packLedgerRepository.getTotalAvailablePacks(user.id)).isZero()
        assertThat(userCardRepository.findByUserId(user.id).sumOf { it.quantity }).isEqualTo(3)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pack_opening_receipts", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `game flow opens in PostgreSQL saves cards sends photos and completes update`() {
        val user = registrationService.registerIfMissing(7003, "en", "direct").user
        val telegramClient = mock(TelegramClient::class.java)
        val gameService = createGameService(telegramClient)

        gameService.handle(
            TelegramUpdate(
                updateId = 9001,
                message = TelegramMessage(
                    messageId = 1,
                    text = "/pack",
                    chat = TelegramChat(id = 7003, type = "private"),
                    from = TelegramUser(id = 7003, languageCode = "en"),
                ),
            ),
        )

        assertThat(packLedgerRepository.getTotalAvailablePacks(user.id)).isZero()
        assertThat(userCardRepository.findByUserId(user.id).sumOf { it.quantity }).isEqualTo(3)
        assertThat(processedUpdateRepository.isProcessed(9001)).isTrue()

        val photoCalls = mockingDetails(telegramClient).invocations.filter { it.method.name == "sendPhoto" }
        assertThat(photoCalls).hasSize(3)
        val keyboards = photoCalls.mapNotNull { it.arguments[3] as? TelegramReplyMarkup }
        assertThat(keyboards).allSatisfy { keyboard ->
            val shareButton = keyboard.inlineKeyboard.orEmpty().flatten().single()
            assertThat(shareButton.url).isNull()
            assertThat(shareButton.switchInlineQuery).startsWith("share:${user.id}:")
        }

        val sentTexts = mockingDetails(telegramClient).invocations
            .filter { it.method.name == "sendMessage" }
            .mapNotNull { it.arguments.getOrNull(1) as? String }
        assertThat(sentTexts).anyMatch { it.contains("Pack opened") && it.contains("Collection progress") }
    }

    @Test
    fun `inline share sends cached card with hidden referral link and start button`() {
        val user = registrationService.registerIfMissing(7010, "ru", "direct").user
        userCardRepository.addCards(user.id, listOf("tennis-kitty"))
        telegramCardFileRepository.upsert("tennis-kitty", "telegram-photo-file-id")
        val telegramClient = mock(TelegramClient::class.java)
        val gameService = createGameService(telegramClient)

        gameService.handle(
            TelegramUpdate(
                updateId = 9004,
                inlineQuery = TelegramInlineQuery(
                    id = "inline-1",
                    from = TelegramUser(id = 7010, languageCode = "ru"),
                    query = "share:${user.id}:tennis-kitty",
                ),
            ),
        )

        val call = mockingDetails(telegramClient).invocations
            .single { it.method.name == "answerInlineQuery" }
        assertThat(call.arguments[0]).isEqualTo("inline-1")
        val results = call.arguments[1] as List<*>
        val result = results.single() as TelegramInlineQueryResultCachedPhoto
        assertThat(result.photoFileId).isEqualTo("telegram-photo-file-id")
        assertThat(result.title).isEqualTo("👆 Нажми, чтобы отправить: Теннисист")
        assertThat(result.description).isEqualTo("Само превью — это кнопка отправки")
        assertThat(result.caption).contains("Мне выпал котик").contains("<a href=\"https://t.me/PurrrfolioBot?start=ref_${user.id}\"")
        assertThat(result.caption).doesNotContain("/assets/cards/")
        assertThat(result.replyMarkup?.inlineKeyboard.orEmpty().flatten().single().url)
            .isEqualTo("https://t.me/PurrrfolioBot?start=ref_${user.id}")
        assertThat(processedUpdateRepository.isProcessed(9004)).isTrue()
    }

    @Test
    fun `first start atomically grants starter pack and shows dedicated opening button`() {
        val telegramClient = mock(TelegramClient::class.java)
        val gameService = createGameService(telegramClient)

        gameService.handle(
            TelegramUpdate(
                updateId = 9002,
                message = TelegramMessage(
                    messageId = 2,
                    text = "/start ref_42",
                    chat = TelegramChat(id = 7005, type = "private"),
                    from = TelegramUser(id = 7005, languageCode = "en"),
                ),
            ),
        )

        val user = userRepository.findByTelegramUserId(7005)
        assertThat(user).isNotNull
        assertThat(user!!.registrationSource).isEqualTo("ref_42")
        assertThat(packLedgerRepository.getTotalAvailablePacks(user.id)).isEqualTo(1)
        assertThat(processedUpdateRepository.isProcessed(9002)).isTrue()

        val messages = mockingDetails(telegramClient).invocations.filter { it.method.name == "sendMessage" }
        assertThat(messages).hasSize(2)
        assertThat(messages[0].arguments[1] as String).contains("Welcome to Purrrfolio")
        assertThat(messages[1].arguments[1] as String).contains("starter packs")
        val starterKeyboard = messages[1].arguments[2] as TelegramReplyMarkup
        assertThat(starterKeyboard.inlineKeyboard.orEmpty().flatten().single().callbackData)
            .isEqualTo("menu:open-pack")
    }

    @Test
    fun `first start through referral notifies both players and shows full pack balance`() {
        val referrer = registrationService.registerIfMissing(7006, "ru", "direct").user
        val telegramClient = mock(TelegramClient::class.java)
        val gameService = createGameService(telegramClient)

        gameService.handle(
            TelegramUpdate(
                updateId = 9003,
                message = TelegramMessage(
                    messageId = 3,
                    text = "/start ref_${referrer.id}",
                    chat = TelegramChat(id = 7007, type = "private"),
                    from = TelegramUser(id = 7007, languageCode = "en"),
                ),
            ),
        )

        val joiner = checkNotNull(userRepository.findByTelegramUserId(7007))
        assertThat(packLedgerRepository.getTotalAvailablePacks(joiner.id)).isEqualTo(6)
        assertThat(packLedgerRepository.getTotalAvailablePacks(referrer.id)).isEqualTo(6)

        val messages = mockingDetails(telegramClient).invocations.filter { it.method.name == "sendMessage" }
        val joinerMessages = messages.filter { it.arguments[0] == 7007L }
        val referrerMessages = messages.filter { it.arguments[0] == 7006L }
        assertThat(joinerMessages).hasSize(3)
        assertThat(joinerMessages.last().arguments[1] as String).contains("Referral bonus").contains("5")
        val joinerKeyboard = joinerMessages.last().arguments[2] as TelegramReplyMarkup
        assertThat(joinerKeyboard.inlineKeyboard.orEmpty().flatten().single().text).contains("6")
        assertThat(referrerMessages).hasSize(1)
        assertThat(referrerMessages.single().arguments[1] as String).contains("Новый игрок").contains("5")
    }

    private fun createGameService(telegramClient: TelegramClient) =
        GameService(
            properties = properties,
            cardCatalog = catalog,
            collectionService = collectionService,
            collectionCompletionRewardService = completionRewardService,
            packOpeningService = packOpeningService,
            packOpeningTransactionService = openingService,
            playerRegistrationService = registrationService,
            cardShareLinkService = CardShareLinkService(properties),
            userRepository = userRepository,
            userCardRepository = userCardRepository,
            packLedgerRepository = packLedgerRepository,
            telegramCardFileRepository = telegramCardFileRepository,
            marketRepository = mock(MarketRepository::class.java),
            randomTradeRepository = mock(RandomTradeRepository::class.java),
            groupRaffleRepository = mock(GroupRaffleRepository::class.java),
            paymentRepository = mock(PaymentRepository::class.java),
            paymentSupportRepository = mock(PaymentSupportRepository::class.java),
            processedUpdateRepository = processedUpdateRepository,
            starsPurchaseService = mock(StarsPurchaseService::class.java),
            starsRefundService = mock(StarsRefundService::class.java),
            gameMetrics = mock(GameMetrics::class.java),
            telegramClient = telegramClient,
            transactionManager = transactionManager,
        )

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
