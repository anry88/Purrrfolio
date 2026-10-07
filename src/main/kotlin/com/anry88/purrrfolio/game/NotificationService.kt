package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.observability.GameMetrics
import com.anry88.purrrfolio.repository.NotificationRepository
import com.anry88.purrrfolio.repository.PackLedgerRepository
import com.anry88.purrrfolio.repository.UserRepository
import com.anry88.purrrfolio.telegram.TelegramClient
import com.anry88.purrrfolio.telegram.TelegramInlineButton
import com.anry88.purrrfolio.telegram.TelegramReplyMarkup
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class NotificationPreference(val callback: String, val hours: Long? = null) {
    ENABLE("on"), TEN_HOURS("10h", 10), ONE_DAY("1d", 24), THREE_DAYS("3d", 72), OFF("off");
}

@Service
class NotificationService(
    private val properties: PurrrfolioProperties,
    private val notifications: NotificationRepository,
    private val packs: PackLedgerRepository,
    private val users: UserRepository,
    private val telegram: TelegramClient,
    private val metrics: GameMetrics,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactions = TransactionTemplate(transactionManager)
    private val zone = ZoneId.of(properties.gameTimezone)

    @Scheduled(initialDelay = 15_000, fixedDelayString = "\${purrrfolio.notifications.poll-interval-ms:60000}")
    fun scheduled() {
        runCatching { sendDue(OffsetDateTime.now()) }
            .onFailure { logger.error("Failed to process player reminders", it) }
    }

    /** Bounded work per tick. Durable state catches up after downtime without replaying missed days. */
    fun sendDue(now: OffsetDateTime): Int {
        if (!properties.notifications.enabled || !telegram.isConfigured()) return 0
        val local = now.atZoneSameInstant(zone)
        val packDate = local.toLocalDate().takeIf { local.hour >= 12 }
        if (packDate != null) transactions.executeWithoutResult { notifications.preparePacks(packDate, now) }
        var sent = 0
        for (userId in notifications.candidates(now, properties.economy.freeCardIntervalHours, packDate, properties.notifications.batchSize)) {
            runCatching {
                if (transactions.execute { sendToPlayer(userId, now, packDate) } == true) sent++
            }.onFailure {
                metrics.notification("error")
                logger.warn("Failed to process reminder for player {}", userId, it)
            }
        }
        return sent
    }

    private fun sendToPlayer(userId: Long, now: OffsetDateTime, packDate: LocalDate?): Boolean {
        // Shared with free-card claims, pack openings and preference updates. Concurrent workers
        // skip this player while delivery is in flight; no in-memory timers or Redis are required.
        val state = notifications.find(userId, lock = true)
        if (state == null || !state.canNotify(now)) {
            metrics.notification("skipped")
            return false
        }
        val freeCard = state.freeCardDue(now, properties.economy.freeCardIntervalHours)
        val balance = packs.getTotalAvailablePacks(userId)
        val remindPacks = packDate != null && state.packNotificationDueDate == packDate && balance > 0 &&
            (state.packNotificationDate == null || state.packNotificationDate < packDate)
        if (!freeCard && !remindPacks) {
            metrics.notification("skipped")
            return false
        }
        val locale = GameLocale.fromCode(state.language)
        val text = buildList {
            if (freeCard) add(Messages.t("notifications.freeCard", locale))
            if (remindPacks) add(Messages.t("notifications.packs", locale, balance))
        }.joinToString("\n\n")
        val buttons = buildList {
            if (freeCard) add(listOf(TelegramInlineButton(Messages.t("menu.freecard", locale), "menu:freecard")))
            if (remindPacks) add(listOf(TelegramInlineButton(Messages.t("menu.packCount", locale, balance), "menu:open-pack")))
            add(listOf(TelegramInlineButton(Messages.t("menu.notifications", locale), "menu:notifications")))
        }
        try {
            telegram.sendMessage(state.telegramUserId, text, TelegramReplyMarkup(inlineKeyboard = buttons))
            notifications.markSent(state, freeCard, packDate.takeIf { remindPacks })
            metrics.notification(if (freeCard && remindPacks) "combined" else if (freeCard) "freecard" else "packs")
            return true
        } catch (error: Exception) {
            if (TelegramClient.isUnreachableRecipient(error)) {
                users.markTelegramBlocked(state.telegramUserId)
                metrics.notification("blocked")
            } else {
                val delay = maxOf(properties.notifications.retryMinutes * 60, TelegramClient.retryAfterSeconds(error) ?: 0)
                notifications.retryAfter(userId, now.plusSeconds(delay))
                metrics.notification("retry")
                logger.warn("Reminder delivery will be retried for player {}", userId)
            }
            return false
        }
    }

    fun showSettings(chatId: Long, userId: Long, now: OffsetDateTime = OffsetDateTime.now()) {
        val state = notifications.find(userId) ?: return
        val locale = GameLocale.fromCode(state.language)
        val paused = state.snoozeUntil?.isAfter(now) == true
        val text = when {
            !state.enabled -> Messages.t("notifications.off", locale)
            paused -> Messages.t("notifications.paused", locale,
                state.snoozeUntil!!.atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z")))
            else -> Messages.t("notifications.on", locale)
        }
        val choices = if (!state.enabled || paused) listOf(NotificationPreference.ENABLE) else
            listOf(NotificationPreference.TEN_HOURS, NotificationPreference.ONE_DAY, NotificationPreference.THREE_DAYS, NotificationPreference.OFF)
        telegram.sendMessage(chatId, text, TelegramReplyMarkup(inlineKeyboard = choices.map { preference ->
            val labelKey = when (preference) {
                NotificationPreference.ENABLE -> "notifications.on.button"
                NotificationPreference.OFF -> "notifications.off.button"
                else -> "notifications.${preference.callback}"
            }
            listOf(TelegramInlineButton(Messages.t(labelKey, locale), "notify:$userId:${preference.callback}"))
        }))
    }

    fun changePreference(chatId: Long, userId: Long, data: String, now: OffsetDateTime = OffsetDateTime.now()) {
        val parts = data.split(':')
        // Settings posted in groups belong to the requesting player, unlike shared game actions.
        if (parts.size != 3 || parts[0] != "notify" || parts[1].toLongOrNull() != userId) return
        val preference = NotificationPreference.entries.firstOrNull { it.callback == parts[2] } ?: return
        notifications.setPreference(userId, preference != NotificationPreference.OFF, preference.hours?.let(now::plusHours))
        showSettings(chatId, userId, now)
    }
}
