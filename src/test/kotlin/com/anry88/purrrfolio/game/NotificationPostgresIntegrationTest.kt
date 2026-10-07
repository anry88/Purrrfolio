package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.config.NotificationProperties
import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.repository.NotificationRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.web.client.HttpClientErrorException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
class NotificationPostgresIntegrationTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: DataSourceTransactionManager
    private lateinit var users: UserRepository
    private lateinit var notifications: NotificationRepository
    private lateinit var packs: PackLedgerRepository
    private lateinit var telegram: TelegramClient
    private val now = OffsetDateTime.parse("2026-10-07T12:00:00Z")
    private val sent = mutableListOf<Pair<String, TelegramReplyMarkup>>()
    private var deliveryError: RuntimeException? = null
    private var duringSend: (() -> Unit)? = null

    @BeforeEach
    fun setUp() {
        val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(ds).load().migrate()
        jdbc = JdbcTemplate(ds)
        jdbc.execute("TRUNCATE users, notification_daily_runs CASCADE")
        jdbc.update("UPDATE notification_dispatch_state SET paused_until = NULL")
        transactions = DataSourceTransactionManager(ds)
        users = UserRepository(jdbc)
        notifications = NotificationRepository(jdbc)
        packs = PackLedgerRepository(jdbc)
        telegram = mock(TelegramClient::class.java)
        `when`(telegram.isConfigured()).thenReturn(true)
        sent.clear()
        deliveryError = null
        duringSend = null
        doAnswer { invocation ->
            duringSend?.invoke()
            deliveryError?.let { throw it }
            sent.add(invocation.arguments[1] as String to invocation.arguments[2] as TelegramReplyMarkup)
            null
        }.`when`(telegram).sendMessage(anyLong(), anyString(), any(TelegramReplyMarkup::class.java), anyString())
    }

    private fun service(properties: PurrrfolioProperties = PurrrfolioProperties()) = NotificationService(
        properties, NotificationRepository(jdbc), packs, users, telegram, mock(GameMetrics::class.java), transactions,
    )

    private fun player(language: String = "en"): Long {
        val user = users.create(91001, language)
        jdbc.update("UPDATE users SET created_at = ?, updated_at = ? WHERE id = ?", now.minusDays(30), now.minusDays(20), user.id)
        return user.id
    }

    private fun claim(userId: Long, at: OffsetDateTime) {
        assertThat(users.claimFreeCardIfDue(userId, at, 3)).isTrue()
    }

    @Test
    fun `unclaimed free card repeats every seven days and survives service restart`() {
        val id = player()
        assertThat(service().sendDue(now)).isEqualTo(1)
        val sentAt = notifications.find(id)!!.freeCardRemindedAt!!
        assertThat(service().sendDue(sentAt.plusDays(7).minusSeconds(1))).isZero()
        assertThat(service().sendDue(sentAt.plusDays(7))).isEqualTo(1)
        val secondSentAt = notifications.find(id)!!.freeCardRemindedAt!!
        assertThat(service().sendDue(secondSentAt.plusDays(7))).isEqualTo(1)
        assertThat(sent).hasSize(3)
    }

    @Test
    fun `weekly reminders wait for quiet mode and permanent disable never resumes`() {
        val id = player()
        assertThat(service().sendDue(now)).isEqualTo(1)
        val sentAt = notifications.find(id)!!.freeCardRemindedAt!!
        notifications.setPreference(id, true, sentAt.plusDays(9))
        assertThat(service().sendDue(sentAt.plusDays(7))).isZero()
        assertThat(service().sendDue(sentAt.plusDays(9))).isEqualTo(1)
        notifications.setPreference(id, false, null)
        assertThat(service().sendDue(sentAt.plusDays(100))).isZero()
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `new free claim resets weekly reminder and next ready cycle is notified after three hours`() {
        val id = player()
        assertThat(service().sendDue(now)).isEqualTo(1)
        claim(id, now.plusHours(1))
        assertThat(notifications.find(id)!!.freeCardRemindedAt).isNull()
        assertThat(service().sendDue(now.plusHours(4).minusSeconds(1))).isZero()
        assertThat(service().sendDue(now.plusHours(4))).isEqualTo(1)
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `old first card is notified once and marker survives new service instance`() {
        val id = player()
        assertThat(service().sendDue(now.minusHours(1))).isEqualTo(1)
        assertThat(service().sendDue(now.minusHours(1).plusMinutes(1))).isZero()
        assertThat(service().sendDue(now.plusDays(5))).isZero()
        assertThat(notifications.find(id)!!.freeCardNotifiedFor).isEqualTo(now.minusDays(30))
        assertThat(jdbc.queryForObject("SELECT updated_at FROM users WHERE id = ?", OffsetDateTime::class.java, id))
            .isEqualTo(now.minusDays(20))
        assertThat(sent.single().first).contains("free card")
    }

    @Test
    fun `cooldown notification is due exactly at three hours and rearmed by a claim`() {
        val id = player()
        claim(id, now)
        assertThat(service().sendDue(now.plusHours(3).minusSeconds(1))).isZero()
        assertThat(service().sendDue(now.plusHours(3))).isEqualTo(1)
        assertThat(service().sendDue(now.plusHours(4))).isZero()
        claim(id, now.plusHours(4))
        assertThat(service().sendDue(now.plusHours(7))).isEqualTo(1)
        assertThat(sent).hasSize(2)
    }

    @Test
    fun `packs are combined with free card at noon once per local date`() {
        val id = player("ru")
        packs.addPacks(id, "starter", 3)
        assertThat(service().sendDue(now)).isEqualTo(1)
        assertThat(service().sendDue(now.plusMinutes(1))).isZero()
        assertThat(sent.single().first).contains("Бесплатная карточка", "Неоткрытых наборов: 3")
        assertThat(sent.single().second.inlineKeyboard!!.flatten().map { it.callbackData })
            .containsExactly("menu:freecard", "menu:open-pack", "menu:notifications")
        assertThat(service().sendDue(now.plusDays(1))).isEqualTo(1)
        assertThat(sent.last().first).doesNotContain("Бесплатная карточка")
        assertThat(notifications.find(id)!!.packNotificationDate).isEqualTo(LocalDate.of(2026, 10, 8))
    }

    @Test
    fun `no packs before noon and no new afternoon balances in already prepared snapshot`() {
        val id = player()
        claim(id, now.minusHours(1))
        assertThat(service().sendDue(now.minusSeconds(1))).isZero()
        assertThat(service().sendDue(now)).isZero()
        packs.addPacks(id, "stars", 3)
        assertThat(service().sendDue(now.plusMinutes(1))).isZero()
        assertThat(service().sendDue(now.plusDays(1))).isEqualTo(1)
    }

    @Test
    fun `noon uses game timezone and delayed startup catches up only once`() {
        val id = player()
        claim(id, now)
        packs.addPacks(id, "starter", 2)
        val props = PurrrfolioProperties(gameTimezone = "America/New_York")
        assertThat(service(props).sendDue(now.plusHours(4).minusSeconds(1))).isEqualTo(1) // Free card at 11:59 local.
        assertThat(sent.single().first).doesNotContain("Unopened packs")
        assertThat(service(props).sendDue(now.plusHours(6))).isEqualTo(1) // Restart at 14:00 local.
        assertThat(service(props).sendDue(now.plusHours(6).plusMinutes(1))).isZero()
        assertThat(sent.last().first).contains("Unopened packs: 2")
    }

    @ParameterizedTest
    @CsvSource("10h,10", "1d,24", "3d,72")
    fun `snooze survives restarts and overdue card is sent when it ends`(option: String, hours: Long) {
        val id = player()
        service().changePreference(91001, id, "notify:$id:$option", now)
        assertThat(notifications.find(id)!!.snoozeUntil).isEqualTo(now.plusHours(hours))
        sent.clear() // Settings reply is not a reminder.
        assertThat(service().sendDue(now.plusHours(hours).minusSeconds(1))).isZero()
        assertThat(service().sendDue(now.plusHours(hours))).isEqualTo(1)
        assertThat(service().sendDue(now.plusHours(hours).plusMinutes(1))).isZero()
    }

    @Test
    fun `forever opt out stays off and explicit enable catches up`() {
        val id = player()
        service().changePreference(91001, id, "notify:$id:off", now)
        assertThat(notifications.find(id)!!.enabled).isFalse()
        assertThat(sent.last().second.inlineKeyboard!!.flatten().map { it.callbackData }).containsExactly("notify:$id:on")
        assertThat(service().sendDue(now.plusYears(1))).isZero()
        service().changePreference(91001, id, "notify:$id:on", now.plusDays(2))
        assertThat(service().sendDue(now.plusDays(2))).isEqualTo(1)
    }

    @Test
    fun `settings offer exactly requested choices and reject another players button`() {
        val id = player("ru")
        service().showSettings(-1001, id, now)
        assertThat(sent.last().second.inlineKeyboard!!.flatten().map { it.callbackData })
            .containsExactly("notify:$id:10h", "notify:$id:1d", "notify:$id:3d", "notify:$id:off")
        service().changePreference(-1001, id, "notify:${id + 1}:off", now)
        assertThat(notifications.find(id)!!.enabled).isTrue()
        assertThat(sent).hasSize(1)
    }

    @Test
    fun `claimed card during pause waits for next cooldown instead of sending old availability`() {
        val id = player()
        service().changePreference(91001, id, "notify:$id:10h", now)
        claim(id, now.plusHours(9))
        sent.clear()
        assertThat(service().sendDue(now.plusHours(10))).isZero()
        assertThat(service().sendDue(now.plusHours(12))).isEqualTo(1)
    }

    @Test
    fun `zero or negative net ledger balance produces no pack reminder`() {
        val id = player()
        claim(id, now)
        packs.addPacks(id, "starter", 3)
        packs.addPacks(id, "opened", -3)
        assertThat(service().sendDue(now)).isZero()
        packs.addPacks(id, "refund", -2)
        assertThat(service().sendDue(now.plusMinutes(1))).isZero()
    }

    @Test
    fun `consumed packs in pending paused snapshot do not notify later`() {
        val id = player()
        claim(id, now)
        packs.addPacks(id, "starter", 3)
        notifications.setPreference(id, true, now.plusHours(1))
        assertThat(service().sendDue(now)).isZero()
        packs.addPacks(id, "opened", -3)
        assertThat(service().sendDue(now.plusHours(1))).isZero()
    }

    @Test
    fun `blocked user is persisted and never retried until private contact resumes`() {
        val id = player()
        deliveryError = telegramError(HttpStatus.FORBIDDEN, "bot was blocked by the user")
        assertThat(service().sendDue(now)).isZero()
        assertThat(users.isTelegramBlocked(91001)).isTrue()
        deliveryError = null
        assertThat(service().sendDue(now.plusDays(1))).isZero()
        users.markTelegramReachable(91001)
        assertThat(service().sendDue(now.plusDays(1))).isEqualTo(1)
        assertThat(notifications.find(id)!!.freeCardNotifiedFor).isNotNull()
    }

    @Test
    fun `429 keeps reminder pending and honors retry_after through restart`() {
        val id = player()
        deliveryError = telegramError(HttpStatus.TOO_MANY_REQUESTS, "rate limited", ",\"parameters\":{\"retry_after\":900}")
        assertThat(service().sendDue(now)).isZero()
        assertThat(users.isTelegramBlocked(91001)).isFalse()
        val retryAt = notifications.find(id)!!.retryAt!!
        assertThat(retryAt).isBetween(now.plusMinutes(15), now.plusMinutes(15).plusSeconds(2))
        assertThat(notifications.find(id)!!.freeCardNotifiedFor).isNull()
        deliveryError = null
        assertThat(service().sendDue(now.plusMinutes(14))).isZero()
        assertThat(service().sendDue(retryAt.plusNanos(1_000_000))).isEqualTo(1)
    }

    @Test
    fun `transient delivery failure is retried without losing availability`() {
        val id = player()
        deliveryError = IllegalStateException("connection lost")
        assertThat(service().sendDue(now)).isZero()
        val retryAt = notifications.find(id)!!.retryAt!!
        assertThat(retryAt).isBetween(now.plusMinutes(5), now.plusMinutes(5).plusSeconds(2))
        deliveryError = null
        assertThat(service().sendDue(retryAt.plusNanos(1_000_000))).isEqualTo(1)
    }

    @Test
    fun `concurrent workers skip in flight recipient and do not duplicate delivery`() {
        player()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        duringSend = { entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue() }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<Int> { service().sendDue(now.minusHours(1)) }
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue()
            assertThat(service().sendDue(now.minusHours(1))).isZero()
            release.countDown()
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1)
            assertThat(service().sendDue(now.minusHours(1))).isZero()
            assertThat(sent).hasSize(1)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `batch cap eventually handles all old players without starvation`() {
        repeat(3) { users.create(93000L + it, "en") }
        val props = PurrrfolioProperties(notifications = NotificationProperties(batchSize = 1))
        repeat(3) { assertThat(service(props).sendDue(now)).isEqualTo(1) }
        assertThat(service(props).sendDue(now)).isZero()
        assertThat(sent).hasSize(3)
    }

    @Test
    fun `V19 upgrade queues existing long overdue players without rewriting their cooldown`() {
        val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        ds.setConnectionProperties(java.util.Properties().apply { setProperty("currentSchema", "notification_upgrade") })
        Flyway.configure().dataSource(ds).schemas("notification_upgrade").target("18").load().migrate()
        val oldJdbc = JdbcTemplate(ds)
        val oldUsers = UserRepository(oldJdbc)
        val oldPlayerId = oldJdbc.queryForObject("INSERT INTO users (telegram_user_id,language) VALUES (95001,'en') RETURNING id", Long::class.java)!!
        oldJdbc.update("UPDATE users SET last_free_card_at = ? WHERE id = ?", now.minusDays(30), oldPlayerId)
        try {
            Flyway.configure().dataSource(ds).schemas("notification_upgrade").load().migrate()
            val upgradedService = NotificationService(
                PurrrfolioProperties(), NotificationRepository(oldJdbc), PackLedgerRepository(oldJdbc), oldUsers,
                telegram, mock(GameMetrics::class.java), DataSourceTransactionManager(ds),
            )
            assertThat(upgradedService.sendDue(now)).isEqualTo(1)
            assertThat(upgradedService.sendDue(now.plusDays(1))).isZero()
            assertThat(oldUsers.findById(oldPlayerId)!!.lastFreeCardAt).isEqualTo(now.minusDays(30))
        } finally {
            jdbc.execute("DROP SCHEMA notification_upgrade CASCADE")
        }
    }

    @Test
    fun `disabled scheduler and unconfigured bot leave reminders pending`() {
        val id = player()
        assertThat(service(PurrrfolioProperties(notifications = NotificationProperties(enabled = false))).sendDue(now)).isZero()
        `when`(telegram.isConfigured()).thenReturn(false)
        assertThat(service().sendDue(now)).isZero()
        assertThat(notifications.find(id)!!.freeCardNotifiedFor).isNull()
        assertThat(sent).isEmpty()
        `when`(telegram.isConfigured()).thenReturn(true)
        assertThat(service().sendDue(now)).isEqualTo(1)
    }

    @Test
    fun `whole broadcast stops on 429 and another worker respects persisted pause`() {
        val first = player()
        val second = users.create(91002, "en").id
        var attempts = 0
        duringSend = { attempts++ }
        deliveryError = telegramError(HttpStatus.TOO_MANY_REQUESTS, "rate limited", ",\"parameters\":{\"retry_after\":10}")
        assertThat(service().sendDue(now)).isZero()
        assertThat(attempts).isEqualTo(1)
        assertThat(notifications.find(first)!!.retryAt).isNotNull()
        assertThat(notifications.find(second)!!.retryAt).isNull()
        assertThat(notifications.find(second)!!.freeCardNotifiedFor).isNull()
        deliveryError = null
        assertThat(service().sendDue(now.plusSeconds(9))).isZero()
        assertThat(attempts).isEqualTo(1)
        val pausedUntil = jdbc.queryForObject("SELECT paused_until FROM notification_dispatch_state", OffsetDateTime::class.java)!!
        assertThat(service().sendDue(pausedUntil.plusSeconds(1))).isEqualTo(1)
        assertThat(attempts).isEqualTo(2)
        assertThat(notifications.find(second)!!.freeCardNotifiedFor).isNotNull()
    }

    @Test
    fun `429 without retry_after uses fallback and never shortens a longer pause`() {
        player()
        deliveryError = telegramError(HttpStatus.TOO_MANY_REQUESTS, "rate limited")
        assertThat(service().sendDue(now)).isZero()
        val pausedUntil = jdbc.queryForObject("SELECT paused_until FROM notification_dispatch_state", OffsetDateTime::class.java)!!
        assertThat(pausedUntil).isBetween(now.plusMinutes(5), now.plusMinutes(5).plusSeconds(2))
        notifications.pauseDispatchUntil(now.plusMinutes(30))
        notifications.pauseDispatchUntil(now.plusMinutes(10))
        assertThat(notifications.dispatchAllowed(now.plusMinutes(29))).isFalse()
        assertThat(notifications.dispatchAllowed(now.plusMinutes(30))).isTrue()
    }

    @Test
    fun `real service spaces attempts within a batch including failed deliveries`() {
        repeat(3) { users.create(96000L + it, "en") }
        val starts = mutableListOf<Long>()
        duringSend = {
            starts.add(System.nanoTime())
            deliveryError = if (starts.size == 1) IllegalStateException("transient failure") else null
        }
        assertThat(service().sendDue(now)).isEqualTo(2)
        assertThat(starts).hasSize(3)
        starts.zipWithNext().forEach { (before, after) ->
            assertThat(TimeUnit.NANOSECONDS.toMillis(after - before)).isGreaterThanOrEqualTo(250)
        }
    }

    private fun telegramError(status: HttpStatus, description: String, extra: String = "") = HttpClientErrorException.create(
        status, status.reasonPhrase, HttpHeaders.EMPTY,
        """{"description":"$description"$extra}""".toByteArray(), Charsets.UTF_8,
    )

    companion object {
        @Container @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
