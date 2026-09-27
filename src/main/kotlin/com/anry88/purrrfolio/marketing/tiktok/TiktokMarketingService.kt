package com.anry88.purrrfolio.marketing.tiktok

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

@Service
class TiktokMarketingService(
    private val properties: PurrrfolioProperties,
    private val repository: TiktokMarketingRepository,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val restClient = restClientBuilder.baseUrl("https://open.tiktokapis.com").build()
    private val random = SecureRandom()

    fun isConfigured(): Boolean {
        val tiktok = properties.marketing.tiktok
        return properties.marketing.adminToken.isNotBlank() &&
            tiktok.clientKey.isNotBlank() &&
            tiktok.clientSecret.isNotBlank() &&
            redirectUri().isNotBlank()
    }

    fun authorizationUrl(): String {
        val tiktok = properties.marketing.tiktok
        require(isConfigured()) { "TikTok marketing integration is not configured" }
        val state = randomState()
        repository.saveOauthState(state, Instant.now().plus(Duration.ofMinutes(10)))
        return buildString {
            append("https://www.tiktok.com/v2/auth/authorize/")
            append("?client_key=").append(url(tiktok.clientKey))
            append("&scope=").append(url(tiktok.scopes))
            append("&response_type=code")
            append("&redirect_uri=").append(url(redirectUri()))
            append("&state=").append(url(state))
        }
    }

    fun exchangeCode(code: String, state: String): TiktokToken {
        require(repository.consumeOauthState(state)) { "OAuth state is missing or expired" }
        val tiktok = properties.marketing.tiktok
        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_key", tiktok.clientKey)
            add("client_secret", tiktok.clientSecret)
            add("code", code)
            add("grant_type", "authorization_code")
            add("redirect_uri", redirectUri())
        }
        val response = restClient.post()
            .uri("/v2/oauth/token/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok token response was empty")
        val token = tokenFromResponse(response)
        repository.saveToken(token)
        return token
    }

    fun creatorInfo(): JsonNode {
        val token = currentToken()
        return restClient.post()
            .uri("/v2/post/publish/creator_info/query/")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${token.accessToken}")
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok creator_info response was empty")
    }

    fun uploadByUrl(mode: TiktokPostMode, videoUrl: String, title: String, privacyLevel: String): TiktokPostRecord {
        val token = currentToken()
        val endpoint = when (mode) {
            TiktokPostMode.INBOX_UPLOAD -> "/v2/post/publish/inbox/video/init/"
            TiktokPostMode.DIRECT_POST -> "/v2/post/publish/video/init/"
        }
        val request = when (mode) {
            TiktokPostMode.INBOX_UPLOAD -> mapOf(
                "source_info" to mapOf(
                    "source" to "PULL_FROM_URL",
                    "video_url" to videoUrl,
                ),
            )
            TiktokPostMode.DIRECT_POST -> mapOf(
                "post_info" to mapOf(
                    "title" to title,
                    "privacy_level" to privacyLevel,
                    "disable_duet" to false,
                    "disable_comment" to false,
                    "disable_stitch" to false,
                    "video_cover_timestamp_ms" to 1000,
                ),
                "source_info" to mapOf(
                    "source" to "PULL_FROM_URL",
                    "video_url" to videoUrl,
                ),
            )
        }
        val response = restClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${token.accessToken}")
            .body(request)
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok upload response was empty")
        val publishId = response.path("data").path("publish_id").asText("")
        require(publishId.isNotBlank()) { "TikTok did not return publish_id: ${objectMapper.writeValueAsString(response)}" }
        return TiktokPostRecord(
            publishId = publishId,
            mode = mode,
            videoUrl = videoUrl,
            title = title.takeIf { it.isNotBlank() },
            privacyLevel = privacyLevel.takeIf { mode == TiktokPostMode.DIRECT_POST },
            status = null,
            failReason = response.path("error").path("code").asText(null),
            rawResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response),
        ).also(repository::savePost)
    }

    fun fetchStatus(publishId: String): JsonNode {
        val token = currentToken()
        val response = restClient.post()
            .uri("/v2/post/publish/status/fetch/")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${token.accessToken}")
            .body(mapOf("publish_id" to publishId))
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok status response was empty")
        repository.updatePostStatus(
            publishId = publishId,
            status = response.path("data").path("status").asText(null),
            failReason = response.path("data").path("fail_reason").asText(null),
            rawResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response),
        )
        return response
    }

    fun listVideos(): JsonNode {
        val token = currentToken()
        return restClient.post()
            .uri("/v2/video/list/?fields=id,title,video_description,duration,cover_image_url,share_url,embed_link")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${token.accessToken}")
            .body(mapOf("max_count" to 10))
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok video.list response was empty")
    }

    fun savedToken(): TiktokToken? = repository.findToken()

    fun recentPosts(): List<TiktokPostRecord> = repository.listPosts()

    fun defaultDemoVideoUrl(): String {
        val configured = properties.marketing.tiktok.demoVideoUrl
        if (configured.isNotBlank()) return configured
        return properties.publicBaseUrl.trimEnd('/') + "/assets/marketing/purrrfolio-demo.mp4"
    }

    fun redirectUri(): String {
        val configured = properties.marketing.tiktok.redirectUri
        if (configured.isNotBlank()) return configured
        return properties.publicBaseUrl.trimEnd('/') + "/go/tiktok/oauth/callback"
    }

    private fun currentToken(): TiktokToken {
        val token = repository.findToken() ?: error("TikTok account is not connected yet")
        if (token.accessTokenExpiresAt.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            return token
        }
        return refreshToken(token)
    }

    private fun refreshToken(token: TiktokToken): TiktokToken {
        val tiktok = properties.marketing.tiktok
        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_key", tiktok.clientKey)
            add("client_secret", tiktok.clientSecret)
            add("grant_type", "refresh_token")
            add("refresh_token", token.refreshToken)
        }
        val response = restClient.post()
            .uri("/v2/oauth/token/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("TikTok refresh response was empty")
        return tokenFromResponse(response).also(repository::saveToken)
    }

    private fun tokenFromResponse(response: JsonNode): TiktokToken {
        val now = Instant.now()
        return TiktokToken(
            openId = response.path("open_id").asText(),
            scope = response.path("scope").asText(),
            accessToken = response.path("access_token").asText(),
            accessTokenExpiresAt = now.plusSeconds(response.path("expires_in").asLong()),
            refreshToken = response.path("refresh_token").asText(),
            refreshTokenExpiresAt = now.plusSeconds(response.path("refresh_expires_in").asLong()),
            tokenType = response.path("token_type").asText("Bearer"),
        )
    }

    private fun randomState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun url(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
