package com.anry88.purrrfolio.marketing.tiktok

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class TiktokMarketingRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun saveOauthState(state: String, expiresAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO marketing_tiktok_oauth_states(state, expires_at)
            VALUES (?, ?)
            ON CONFLICT (state) DO UPDATE SET expires_at = EXCLUDED.expires_at, created_at = NOW()
            """.trimIndent(),
            state,
            Timestamp.from(expiresAt),
        )
    }

    fun consumeOauthState(state: String, now: Instant = Instant.now()): Boolean {
        val rows = jdbcTemplate.update(
            """
            DELETE FROM marketing_tiktok_oauth_states
            WHERE state = ? AND expires_at > ?
            """.trimIndent(),
            state,
            Timestamp.from(now),
        )
        jdbcTemplate.update("DELETE FROM marketing_tiktok_oauth_states WHERE expires_at <= ?", Timestamp.from(now))
        return rows == 1
    }

    fun saveToken(token: TiktokToken) {
        jdbcTemplate.update(
            """
            INSERT INTO marketing_tiktok_tokens(
                id, open_id, scope, access_token, access_token_expires_at,
                refresh_token, refresh_token_expires_at, token_type
            )
            VALUES (1, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                open_id = EXCLUDED.open_id,
                scope = EXCLUDED.scope,
                access_token = EXCLUDED.access_token,
                access_token_expires_at = EXCLUDED.access_token_expires_at,
                refresh_token = EXCLUDED.refresh_token,
                refresh_token_expires_at = EXCLUDED.refresh_token_expires_at,
                token_type = EXCLUDED.token_type,
                updated_at = NOW()
            """.trimIndent(),
            token.openId,
            token.scope,
            token.accessToken,
            Timestamp.from(token.accessTokenExpiresAt),
            token.refreshToken,
            Timestamp.from(token.refreshTokenExpiresAt),
            token.tokenType,
        )
    }

    fun findToken(): TiktokToken? = jdbcTemplate.query(
        """
        SELECT open_id, scope, access_token, access_token_expires_at,
               refresh_token, refresh_token_expires_at, token_type
        FROM marketing_tiktok_tokens
        WHERE id = 1
        """.trimIndent(),
    ) { rs, _ ->
        TiktokToken(
            openId = rs.getString("open_id"),
            scope = rs.getString("scope"),
            accessToken = rs.getString("access_token"),
            accessTokenExpiresAt = rs.getTimestamp("access_token_expires_at").toInstant(),
            refreshToken = rs.getString("refresh_token"),
            refreshTokenExpiresAt = rs.getTimestamp("refresh_token_expires_at").toInstant(),
            tokenType = rs.getString("token_type"),
        )
    }.firstOrNull()

    fun savePost(post: TiktokPostRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO marketing_tiktok_posts(
                publish_id, mode, video_url, title, privacy_level, status, fail_reason, raw_response
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (publish_id) DO UPDATE SET
                mode = EXCLUDED.mode,
                video_url = EXCLUDED.video_url,
                title = EXCLUDED.title,
                privacy_level = EXCLUDED.privacy_level,
                status = EXCLUDED.status,
                fail_reason = EXCLUDED.fail_reason,
                raw_response = EXCLUDED.raw_response,
                updated_at = NOW()
            """.trimIndent(),
            post.publishId,
            post.mode.name,
            post.videoUrl,
            post.title,
            post.privacyLevel,
            post.status,
            post.failReason,
            post.rawResponse,
        )
    }

    fun updatePostStatus(publishId: String, status: String?, failReason: String?, rawResponse: String) {
        jdbcTemplate.update(
            """
            UPDATE marketing_tiktok_posts
            SET status = ?, fail_reason = ?, raw_response = ?, updated_at = NOW()
            WHERE publish_id = ?
            """.trimIndent(),
            status,
            failReason,
            rawResponse,
            publishId,
        )
    }

    fun listPosts(limit: Int = 10): List<TiktokPostRecord> = jdbcTemplate.query(
        """
        SELECT publish_id, mode, video_url, title, privacy_level, status, fail_reason, raw_response
        FROM marketing_tiktok_posts
        ORDER BY created_at DESC
        LIMIT ?
        """.trimIndent(),
        { rs, _ ->
            TiktokPostRecord(
                publishId = rs.getString("publish_id"),
                mode = TiktokPostMode.valueOf(rs.getString("mode")),
                videoUrl = rs.getString("video_url"),
                title = rs.getString("title"),
                privacyLevel = rs.getString("privacy_level"),
                status = rs.getString("status"),
                failReason = rs.getString("fail_reason"),
                rawResponse = rs.getString("raw_response"),
            )
        },
        limit.coerceIn(1, 50),
    )
}

data class TiktokToken(
    val openId: String,
    val scope: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val tokenType: String,
)

data class TiktokPostRecord(
    val publishId: String,
    val mode: TiktokPostMode,
    val videoUrl: String,
    val title: String?,
    val privacyLevel: String?,
    val status: String?,
    val failReason: String?,
    val rawResponse: String,
)

enum class TiktokPostMode {
    INBOX_UPLOAD,
    DIRECT_POST,
}
