package com.anry88.purrrfolio.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Low-cardinality Micrometer instruments for the Telegram UI plus
 * database-backed gauges refreshed once per minute.
 *
 * Mirrors the Frontline Nations operating pattern: event counters describe
 * traffic seen by this instance, gauges are rebuilt from the database so
 * board totals survive restarts. Never put Telegram IDs, raw /start
 * payloads, payment charge IDs or callback data into tags.
 */
@Component
class GameMetrics(
    private val registry: MeterRegistry,
    private val jdbc: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val playerGauges = PERIODS.associateWith { period -> gauge("purrrfolio.players", "period", period) }
    private val registrationGauges = ConcurrentHashMap<RegistrationGaugeKey, AtomicLong>()
    private val packGauges = ConcurrentHashMap<String, AtomicLong>()
    private val paymentGauges = PAYMENT_STATES.associateWith { status -> gauge("purrrfolio.stars.payments", "status", status) }
    private val starAmountGauges = PAYMENT_STATES.associateWith { status -> gauge("purrrfolio.stars.amount", "status", status) }
    private val craftPointsGauge = gauge("purrrfolio.craft.points")
    private val poolWaitingGauge = gauge("purrrfolio.trade.pool.waiting")
    private val marketActiveGauge = gauge("purrrfolio.market.listings.active")

    fun command(command: String, source: String = "message") {
        registry.counter("purrrfolio.bot.command", "command", normalizeCommand(command), "source", source).increment()
    }

    fun callback(data: String) {
        registry.counter("purrrfolio.bot.callback", "action", callbackAction(data)).increment()
    }

    fun registration(source: String) {
        registry.counter("purrrfolio.registration", "source", normalizeRegistrationSource(source)).increment()
    }

    fun stars(stage: String, pack: String? = null) {
        registry.counter("purrrfolio.stars.purchase", "stage", stage, "pack", pack ?: "unknown").increment()
    }

    fun packOpened() {
        registry.counter("purrrfolio.pack.opened").increment()
    }

    fun freeCardClaimed() {
        registry.counter("purrrfolio.card.claimed").increment()
    }

    fun craftMelted(rarity: String, points: Int) {
        registry.counter("purrrfolio.craft.melted", "rarity", rarity.lowercase()).increment()
        registry.counter("purrrfolio.craft.points", "direction", "earned").increment(points.toDouble())
    }

    fun craftPackBuilt(packs: Int) {
        registry.counter("purrrfolio.craft.packs").increment(packs.toDouble())
    }

    fun tradeMatched() {
        registry.counter("purrrfolio.trade.matched").increment()
    }

    fun marketOffer(stage: String) {
        registry.counter("purrrfolio.market.offer", "stage", stage).increment()
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 60_000)
    fun refreshDatabaseGauges() {
        runCatching {
            val now = Instant.now()
            playerGauges.getValue("total").set(count("SELECT COUNT(*) FROM users"))
            playerGauges.getValue("day").set(countSince("updated_at", now.minus(1, ChronoUnit.DAYS)))
            playerGauges.getValue("week").set(countSince("updated_at", now.minus(7, ChronoUnit.DAYS)))
            playerGauges.getValue("month").set(countSince("updated_at", now.minus(30, ChronoUnit.DAYS)))

            refreshRegistrationGauges("total", null)
            refreshRegistrationGauges("day", now.minus(1, ChronoUnit.DAYS))
            refreshRegistrationGauges("week", now.minus(7, ChronoUnit.DAYS))
            refreshRegistrationGauges("month", now.minus(30, ChronoUnit.DAYS))

            refreshPackGauges()
            PAYMENT_STATES.forEach { status ->
                paymentGauges.getValue(status).set(
                    jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = ?", Long::class.java, status) ?: 0,
                )
                starAmountGauges.getValue(status).set(
                    jdbc.queryForObject(
                        "SELECT COALESCE(SUM(stars), 0) FROM payments WHERE status = ?",
                        Long::class.java, status,
                    ) ?: 0,
                )
            }
            craftPointsGauge.set(
                jdbc.queryForObject("SELECT COALESCE(SUM(craft_points), 0) FROM users", Long::class.java) ?: 0,
            )
            poolWaitingGauge.set(count("SELECT COUNT(*) FROM random_trade_pool WHERE status = 'WAITING'"))
            marketActiveGauge.set(count("SELECT COUNT(*) FROM market_listings WHERE status = 'ACTIVE'"))
        }.onFailure { error ->
            logger.warn("Could not refresh observability gauges", error)
        }
    }

    private fun count(sql: String): Long = jdbc.queryForObject(sql, Long::class.java) ?: 0

    private fun countSince(column: String, cutoff: Instant): Long = jdbc.queryForObject(
        "SELECT COUNT(*) FROM users WHERE $column >= ?",
        Long::class.java,
        Timestamp.from(cutoff),
    ) ?: 0

    private fun refreshPackGauges() {
        val rows: Map<String, Long> = jdbc.query(
            "SELECT source, COALESCE(SUM(quantity), 0) AS total FROM pack_ledger GROUP BY source",
        ) { rs, _ -> rs.getString("source") to rs.getLong("total") }.toMap()
        PACK_SOURCES.forEach { source ->
            packGauges.computeIfAbsent(source) { gauge("purrrfolio.packs", "source", source) }
                .set(packSourceGaugeValue(source, rows.getOrDefault(source, 0)))
        }
    }

    private fun refreshRegistrationGauges(period: String, cutoff: Instant?) {
        val sql = buildString {
            append("SELECT registration_source, COUNT(*) AS registrations FROM users ")
            if (cutoff != null) append("WHERE created_at >= ? ")
            append("GROUP BY registration_source")
        }
        val rows: List<Pair<String, Long>> = if (cutoff == null) {
            jdbc.query(sql) { rs, _ -> rs.getString("registration_source") to rs.getLong("registrations") }
        } else {
            jdbc.query(sql, { rs, _ -> rs.getString("registration_source") to rs.getLong("registrations") }, Timestamp.from(cutoff))
        }
        val normalized = rows.groupBy({ normalizeRegistrationSource(it.first) }, { it.second })
            .mapValues { (_, counts) -> counts.sum() }
        val tracked = normalized.entries.sortedByDescending { it.value }.take(MAX_SOURCE_SERIES)
        val other = normalized.entries.drop(MAX_SOURCE_SERIES).sumOf { it.value }
        val values = buildMap {
            tracked.forEach { (source, count) -> put(RegistrationGaugeKey(period, source), count) }
            if (other > 0) {
                val key = RegistrationGaugeKey(period, "other")
                put(key, getOrDefault(key, 0) + other)
            }
        }
        registrationGauges.keys.filter { it.period == period && it !in values }.forEach { key ->
            registrationGauges.remove(key)?.set(0)
            registry.find("purrrfolio.registrations")
                .tags("period", key.period, "source", key.source)
                .gauge()
                ?.let(registry::remove)
        }
        values.forEach { (key, value) ->
            registrationGauges.computeIfAbsent(key) {
                gauge("purrrfolio.registrations", "period", key.period, "source", key.source)
            }.set(value)
        }
    }

    private fun gauge(name: String, vararg tags: String): AtomicLong {
        val value = AtomicLong()
        Gauge.builder(name, value) { it.get().toDouble() }.tags(*tags).register(registry)
        return value
    }

    companion object {
        private val PERIODS = listOf("day", "week", "month", "total")
        private val PAYMENT_STATES = listOf("PENDING", "COMPLETED", "FAILED", "REFUNDED")
        private val PACK_SOURCES = listOf("starter", "stars", "craft", "opened")

        fun packSourceGaugeValue(source: String, ledgerTotal: Long): Long =
            if (source == "opened") -ledgerTotal else ledgerTotal

        /** Bounds /start payloads to lowercase campaign codes; everything else is direct/other. */
        fun normalizeRegistrationSource(raw: String?): String {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty() || normalized == "direct") return "direct"
            return normalized.takeIf(REGISTRATION_SOURCE_PATTERN::matches) ?: "other"
        }

        fun normalizeCommand(raw: String): String {
            val command = raw.trim().substringBefore('@').removePrefix("/").lowercase()
            return command.takeIf { it in KNOWN_COMMANDS } ?: "unknown"
        }

        fun callbackAction(data: String): String = when {
            data.startsWith("gal:") || data.startsWith("col:page:") -> "gallery"
            data.startsWith("trade:") -> "trade"
            data.startsWith("m:") || data.startsWith("market:") -> "market"
            data.startsWith("buy:") -> "buy"
            data.startsWith("menu:") -> "menu"
            data.startsWith("lang:") -> "language"
            data == "free:card" -> "freecard"
            data.startsWith("craft:") -> "craft"
            else -> "unknown"
        }

        private val KNOWN_COMMANDS = setOf(
            "start", "collection", "pack", "freecard", "craft", "buy",
            "trade", "market", "language", "help", "paysupport",
        )
        private val REGISTRATION_SOURCE_PATTERN = Regex("[a-z0-9_-]{1,64}")
        private const val MAX_SOURCE_SERIES = 24
    }

    private data class RegistrationGaugeKey(val period: String, val source: String)
}
