package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class ReferralRewardRepository(private val jdbcTemplate: JdbcTemplate) {

    fun countForReferrerSince(referrerUserId: Long, since: OffsetDateTime): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM referral_rewards WHERE referrer_user_id = ? AND created_at >= ?",
            Int::class.java,
            referrerUserId,
            since,
        ) ?: 0

    fun claim(
        referredUserId: Long,
        referrerUserId: Long,
        packsEach: Int,
        referrerRewarded: Boolean,
    ): Boolean {
        val sql = """
            INSERT INTO referral_rewards (referred_user_id, referrer_user_id, packs_each, referrer_rewarded)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (referred_user_id) DO NOTHING
        """.trimIndent()
        return jdbcTemplate.update(sql, referredUserId, referrerUserId, packsEach, referrerRewarded) == 1
    }
}
