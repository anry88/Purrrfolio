package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.i18n.Messages
import com.anry88.purrrfolio.repository.ChatLeaderboardRepository
import com.anry88.purrrfolio.telegram.*
import org.springframework.stereotype.Service
import java.util.concurrent.Semaphore

data class ChatXpEntry(val userId: Long, val xp: Long, val name: String, val rank: Int)
sealed interface ChatLeaderboardResult {
    data class Page(val entries: List<ChatXpEntry>, val page: Int, val pages: Int, val ownXp: Long?) : ChatLeaderboardResult
    data object Unavailable : ChatLeaderboardResult
}

@Service
class ChatLeaderboardService(private val repository: ChatLeaderboardRepository, private val telegram: TelegramClient) {
    // Bound the number of external membership lookups and concurrent expensive commands.
    private val gate = Semaphore(1)

    fun page(chatId: Long, requesterTelegramId: Long, requesterUserId: Long,
             requestedPage: Int, locale: GameLocale): ChatLeaderboardResult {
        if (!gate.tryAcquire()) return ChatLeaderboardResult.Unavailable
        try {
            val requester = telegram.getChatMember(chatId, requesterTelegramId)
            if (!isCurrentMember(requester, requesterTelegramId)) return ChatLeaderboardResult.Unavailable
            val candidates = repository.candidates(chatId, MAX_MEMBERS + 1)
            if (candidates.size > MAX_MEMBERS) return ChatLeaderboardResult.Unavailable
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
            val verified = mutableListOf<Pair<com.anry88.purrrfolio.repository.ChatXpCandidate, String>>()
            for (candidate in candidates) {
                if (System.nanoTime() > deadline) return ChatLeaderboardResult.Unavailable
                val member = if (candidate.telegramUserId == requesterTelegramId) requester
                    else telegram.getChatMember(chatId, candidate.telegramUserId)
                // A failed lookup never authorizes disclosure of a cached name or score.
                if (!isCurrentMember(member, candidate.telegramUserId)) continue
                val name = member?.user?.firstName.orEmpty()
                    .filterNot { it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }
                    .take(60).ifBlank { Messages.t("rank.player", locale) }
                verified += candidate to name
            }
            var rank = 0
            var previousXp: Long? = null
            val entries = verified.mapIndexed { index, (candidate, name) ->
                if (candidate.xp != previousXp) rank = index + 1
                previousXp = candidate.xp
                ChatXpEntry(candidate.userId, candidate.xp, name, rank)
            }
            val pages = maxOf(1, (entries.size + PAGE_SIZE - 1) / PAGE_SIZE)
            val page = requestedPage.coerceIn(0, pages - 1)
            return ChatLeaderboardResult.Page(entries.drop(page * PAGE_SIZE).take(PAGE_SIZE), page, pages,
                entries.firstOrNull { it.userId == requesterUserId }?.xp)
        } finally { gate.release() }
    }

    companion object {
        const val PAGE_SIZE = 10
        const val MAX_MEMBERS = 100
        fun isCurrentMember(member: TelegramChatMember?, expectedTelegramId: Long): Boolean =
            member?.user?.id == expectedTelegramId && member.user?.isBot == false && when (member.status) {
                "creator", "administrator", "member" -> true
                "restricted" -> member.isMember == true
                else -> false
            }

        /** Bind pagination to the origin chat; reject private/forwarded/forged callbacks. */
        fun callbackPage(data: String, chatId: Long): Int? {
            val parts = data.split(':')
            if (parts.size != 3 || parts[0] != "rank" || parts[1].toLongOrNull() != chatId) return null
            return parts[2].toIntOrNull()?.takeIf { it in 0..MAX_MEMBERS / PAGE_SIZE }
        }
    }
}
