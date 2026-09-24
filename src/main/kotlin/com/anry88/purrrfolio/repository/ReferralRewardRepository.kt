package com.anry88.purrrfolio.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReferralRewardRepository(private val jdbcTemplate: JdbcTemplate) {

    fun claim(referredUserId: Long, referrerUserId: Long, packsEach: Int): Boolean {
        val sql = """
            INSERT INTO referral_rewards (referred_user_id, referrer_user_id, packs_each)
            VALUES (?, ?, ?)
            ON CONFLICT (referred_user_id) DO NOTHING
        """.trimIndent()
        return jdbcTemplate.update(sql, referredUserId, referrerUserId, packsEach) == 1
    }
}
