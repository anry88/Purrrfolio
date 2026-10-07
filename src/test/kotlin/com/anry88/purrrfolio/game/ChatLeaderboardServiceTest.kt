package com.anry88.purrrfolio.game

import com.anry88.purrrfolio.i18n.GameLocale
import com.anry88.purrrfolio.repository.ChatLeaderboardRepository
import com.anry88.purrrfolio.repository.ChatXpCandidate
import com.anry88.purrrfolio.telegram.TelegramChatMember
import com.anry88.purrrfolio.telegram.TelegramChatMemberUser
import com.anry88.purrrfolio.telegram.TelegramClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ChatLeaderboardServiceTest {
    private val repository = mock(ChatLeaderboardRepository::class.java)
    private val telegram = mock(TelegramClient::class.java)
    private val service = ChatLeaderboardService(repository, telegram)
    private val chat = -123L

    private fun member(id: Long, name: String, status: String = "member", isMember: Boolean? = null) =
        TelegramChatMember(TelegramChatMemberUser(id, false, name, "private_username"), status, isMember)

    @Test
    fun `membership must positively confirm matching human identity and restricted membership`() {
        assertThat(ChatLeaderboardService.isCurrentMember(member(1, "A"), 1)).isTrue()
        assertThat(ChatLeaderboardService.isCurrentMember(member(1, "A", "restricted", true), 1)).isTrue()
        for (status in listOf("left", "kicked", "unknown", "restricted")) {
            assertThat(ChatLeaderboardService.isCurrentMember(member(1, "A", status), 1)).isFalse()
        }
        assertThat(ChatLeaderboardService.isCurrentMember(member(2, "A"), 1)).isFalse()
        assertThat(ChatLeaderboardService.isCurrentMember(null, 1)).isFalse()
        assertThat(ChatLeaderboardService.isCurrentMember(TelegramChatMember(TelegramChatMemberUser(1, true), "member"), 1)).isFalse()
        assertThat(ChatLeaderboardService.isCurrentMember(TelegramChatMember(TelegramChatMemberUser(1), "member"), 1)).isFalse()
    }

    @Test
    fun `rank excludes departed unknown and bots and ties share competition places`() {
        `when`(telegram.getChatMember(chat, 1)).thenReturn(member(1, "Fresh\nName\u202e"))
        `when`(telegram.getChatMember(chat, 2)).thenReturn(member(2, "Left secret", "left"))
        `when`(telegram.getChatMember(chat, 3)).thenReturn(null)
        `when`(telegram.getChatMember(chat, 4)).thenReturn(member(4, "Tie"))
        `when`(telegram.getChatMember(chat, 5)).thenReturn(member(5, "Third"))
        `when`(repository.candidates(chat, ChatLeaderboardService.MAX_MEMBERS + 1)).thenReturn(listOf(
            ChatXpCandidate(2, 2, 100), ChatXpCandidate(3, 3, 50),
            ChatXpCandidate(1, 1, 20), ChatXpCandidate(4, 4, 20), ChatXpCandidate(5, 5, 10),
        ))
        val result = service.page(chat, 1, 1, 0, GameLocale.EN) as ChatLeaderboardResult.Page
        assertThat(result.entries.map { it.name }).containsExactly("FreshName", "Tie", "Third")
        assertThat(result.entries.map { it.rank }).containsExactly(1, 1, 3)
        assertThat(result.entries.map { it.xp }).containsExactly(20, 20, 10)
        assertThat(result.ownXp).isEqualTo(20)
        verify(repository).candidates(chat, ChatLeaderboardService.MAX_MEMBERS + 1)
    }

    @Test
    fun `requester membership failure prevents any leaderboard query or disclosure`() {
        `when`(telegram.getChatMember(chat, 1)).thenReturn(member(1, "Left", "left"))
        assertThat(service.page(chat, 1, 1, 0, GameLocale.RU)).isEqualTo(ChatLeaderboardResult.Unavailable)
        verifyNoInteractions(repository)
    }

    @Test
    fun `pagination freshly verifies all displayed members and clamps obsolete page`() {
        val candidates = (1L..12L).map { ChatXpCandidate(it, it, 20L - it) }
        `when`(repository.candidates(chat, ChatLeaderboardService.MAX_MEMBERS + 1)).thenReturn(candidates)
        for (candidate in candidates) `when`(telegram.getChatMember(chat, candidate.telegramUserId))
            .thenReturn(member(candidate.telegramUserId, "Player ${candidate.userId}"))
        val page = service.page(chat, 1, 1, 1, GameLocale.EN) as ChatLeaderboardResult.Page
        assertThat(page.entries.map { it.rank }).containsExactly(11, 12)
        assertThat(page.pages).isEqualTo(2)
        assertThat(page.page).isEqualTo(1)
        `when`(telegram.getChatMember(chat, 12)).thenReturn(member(12, "Departed", "kicked"))
        val refreshed = service.page(chat, 1, 1, 99, GameLocale.EN) as ChatLeaderboardResult.Page
        assertThat(refreshed.entries).hasSize(1)
        assertThat(refreshed.entries.single().userId).isEqualTo(11)
    }

    @Test
    fun `pagination rejects another chat nonnumeric negative and excessive pages`() {
        assertThat(ChatLeaderboardService.callbackPage("rank:-123:1", chat)).isEqualTo(1)
        for (data in listOf("rank:-124:0", "rank:-123:-1", "rank:-123:9999", "rank:-123:x", "rank:-123:1:extra")) {
            assertThat(ChatLeaderboardService.callbackPage(data, chat)).isNull()
        }
    }
}
