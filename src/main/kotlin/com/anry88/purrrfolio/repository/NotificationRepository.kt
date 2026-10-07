package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

data class NotificationState(
    val userId: Long,
    val telegramUserId: Long,
    val language: String,
    val enabled: Boolean,
    val snoozeUntil: OffsetDateTime?,
    val blockedAt: OffsetDateTime?,
    val lastFreeCardAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val freeCardNotifiedFor: OffsetDateTime?,
    val packNotificationDate: LocalDate?,
    val packNotificationDueDate: LocalDate?,
    val retryAt: OffsetDateTime?,
) {
    fun canNotify(now: OffsetDateTime): Boolean = enabled && blockedAt == null &&
        (snoozeUntil == null || !snoozeUntil.isAfter(now)) &&
        (retryAt == null || !retryAt.isAfter(now))

    fun freeCardDue(now: OffsetDateTime, intervalHours: Int): Boolean =
        freeCardNotifiedFor == null &&
            (lastFreeCardAt == null || !lastFreeCardAt.plusHours(intervalHours.toLong()).isAfter(now))
}

@Repository
class NotificationRepository(private val jdbc: JdbcTemplate) {
    fun find(userId: Long, lock: Boolean = false): NotificationState? = jdbc.query(
        "SELECT * FROM users WHERE id = ?" + if (lock) " FOR UPDATE SKIP LOCKED" else "",
        { rs, _ ->
            NotificationState(
                userId = rs.getLong("id"),
                telegramUserId = rs.getLong("telegram_user_id"),
                language = rs.getString("language"),
                enabled = rs.getBoolean("notifications_enabled"),
                snoozeUntil = rs.getObject("notification_snooze_until", OffsetDateTime::class.java),
                blockedAt = rs.getObject("telegram_blocked_at", OffsetDateTime::class.java),
                lastFreeCardAt = rs.getObject("last_free_card_at", OffsetDateTime::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                freeCardNotifiedFor = rs.getObject("free_card_notified_for", OffsetDateTime::class.java),
                packNotificationDate = rs.getObject("pack_notification_date", LocalDate::class.java),
                packNotificationDueDate = rs.getObject("pack_notification_due_date", LocalDate::class.java),
                retryAt = rs.getObject("notification_retry_at", OffsetDateTime::class.java),
            )
        }, userId,
    ).firstOrNull()

    fun candidates(now: OffsetDateTime, intervalHours: Int, packDate: LocalDate?, limit: Int): List<Long> = jdbc.query(
        """
        SELECT u.id FROM users u
        WHERE notifications_enabled AND telegram_blocked_at IS NULL
          AND (notification_snooze_until IS NULL OR notification_snooze_until <= ?)
          AND (notification_retry_at IS NULL OR notification_retry_at <= ?)
          AND (
            (free_card_notified_for IS NULL AND (last_free_card_at IS NULL OR last_free_card_at <= ?))
            OR (pack_notification_due_date = ? AND (pack_notification_date IS NULL OR pack_notification_date < ?)
                AND (SELECT COALESCE(SUM(quantity), 0) FROM pack_ledger WHERE user_id = u.id) > 0)
          )
        ORDER BY COALESCE(notification_retry_at, last_free_card_at, created_at), u.id
        LIMIT ?
        """.trimIndent(),
        { rs, _ -> rs.getLong("id") },
        now, now, now.minusHours(intervalHours.toLong()), packDate, packDate, limit,
    )

    /** Must run inside a transaction: publish the daily run and its recipients atomically. */
    fun preparePacks(date: LocalDate, now: OffsetDateTime) {
        val prepared = jdbc.query(
            "INSERT INTO notification_daily_runs (reminder_date, prepared_at) VALUES (?, ?) ON CONFLICT DO NOTHING RETURNING reminder_date",
            { rs, _ -> rs.getObject("reminder_date", LocalDate::class.java) }, date, now,
        ).isNotEmpty()
        if (prepared) jdbc.update(
            """
            UPDATE users u SET pack_notification_due_date = ?
            WHERE notifications_enabled AND telegram_blocked_at IS NULL
              AND (SELECT COALESCE(SUM(quantity), 0) FROM pack_ledger WHERE user_id = u.id) > 0
            """.trimIndent(), date,
        )
    }

    fun setPreference(userId: Long, enabled: Boolean, snoozeUntil: OffsetDateTime?) {
        jdbc.update(
            "UPDATE users SET notifications_enabled = ?, notification_snooze_until = ?, notification_retry_at = NULL WHERE id = ?",
            enabled, snoozeUntil, userId,
        )
    }

    /** Called under the user lock, after Telegram accepts the message. */
    fun markSent(state: NotificationState, freeCard: Boolean, packDate: LocalDate?) {
        jdbc.update(
            """
            UPDATE users SET
                free_card_notified_for = CASE WHEN ? THEN ? ELSE free_card_notified_for END,
                pack_notification_date = COALESCE(?, pack_notification_date),
                notification_retry_at = NULL
            WHERE id = ?
            """.trimIndent(), freeCard, state.lastFreeCardAt ?: state.createdAt, packDate, state.userId,
        )
    }

    fun retryAfter(userId: Long, retryAt: OffsetDateTime) {
        jdbc.update("UPDATE users SET notification_retry_at = ? WHERE id = ?", retryAt, userId)
    }
}
