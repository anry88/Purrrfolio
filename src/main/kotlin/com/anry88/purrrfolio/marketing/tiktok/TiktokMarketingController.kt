package com.anry88.purrrfolio.marketing.tiktok

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.view.RedirectView
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Controller
@RequestMapping("/go/tiktok")
class TiktokMarketingController(
    private val properties: PurrrfolioProperties,
    private val service: TiktokMarketingService,
) {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun page(request: HttpServletRequest): ResponseEntity<String> {
        val authenticated = isAuthenticated(request)
        val html = if (!authenticated) loginPage() else dashboardPage()
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html)
    }

    @PostMapping("/login")
    fun login(
        @RequestParam("adminToken") adminToken: String,
        response: HttpServletResponse,
    ): RedirectView {
        if (!constantTimeEquals(adminToken, properties.marketing.adminToken) || properties.marketing.adminToken.isBlank()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid marketing admin token")
        }
        response.addCookie(
            Cookie(AUTH_COOKIE, adminToken).apply {
                path = "/go/tiktok"
                isHttpOnly = true
                secure = true
                maxAge = 60 * 60 * 12
                setAttribute("SameSite", "Strict")
            },
        )
        return RedirectView("/go/tiktok")
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): RedirectView {
        response.addCookie(
            Cookie(AUTH_COOKIE, "").apply {
                path = "/go/tiktok"
                isHttpOnly = true
                secure = true
                maxAge = 0
                setAttribute("SameSite", "Strict")
            },
        )
        return RedirectView("/go/tiktok")
    }

    @GetMapping("/oauth/start")
    fun startOauth(request: HttpServletRequest): ResponseEntity<Void> {
        requireAdmin(request)
        val headers = HttpHeaders()
        headers.location = java.net.URI.create(service.authorizationUrl())
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build()
    }

    @GetMapping("/oauth/callback", produces = [MediaType.TEXT_HTML_VALUE])
    fun oauthCallback(
        @RequestParam("code", required = false) code: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("error", required = false) error: String?,
        @RequestParam("error_description", required = false) errorDescription: String?,
    ): ResponseEntity<String> {
        if (error != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(resultPage("TikTok OAuth failed", escape("$error $errorDescription")))
        }
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing TikTok OAuth code or state")
        }
        val token = service.exchangeCode(code, state)
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(resultPage("TikTok connected", "Connected open_id: ${escape(mask(token.openId))}. You can close this page or return to <a href=\"/go/tiktok\">the dashboard</a>."))
    }

    @PostMapping("/creator-info", produces = [MediaType.TEXT_HTML_VALUE])
    fun creatorInfo(request: HttpServletRequest): ResponseEntity<String> {
        requireAdmin(request)
        return result("Creator info", service.creatorInfo())
    }

    @PostMapping("/upload", produces = [MediaType.TEXT_HTML_VALUE])
    fun upload(
        request: HttpServletRequest,
        @RequestParam("mode") mode: TiktokPostMode,
        @RequestParam("videoUrl") videoUrl: String,
        @RequestParam("title") title: String,
        @RequestParam("privacyLevel", defaultValue = "SELF_ONLY") privacyLevel: String,
    ): ResponseEntity<String> {
        requireAdmin(request)
        val post = service.uploadByUrl(mode, videoUrl.trim(), title.trim(), privacyLevel.trim())
        val body = """
            <p><strong>publish_id:</strong> ${escape(post.publishId)}</p>
            <p><strong>mode:</strong> ${escape(post.mode.name)}</p>
            <p>Use “Fetch status” on the dashboard to show TikTok processing progress in the review video.</p>
            <pre>${escape(post.rawResponse)}</pre>
        """.trimIndent()
        return html(resultPage("TikTok upload initialized", body))
    }

    @PostMapping("/status", produces = [MediaType.TEXT_HTML_VALUE])
    fun status(
        request: HttpServletRequest,
        @RequestParam("publishId") publishId: String,
    ): ResponseEntity<String> {
        requireAdmin(request)
        return result("Post status", service.fetchStatus(publishId.trim()))
    }

    @PostMapping("/videos", produces = [MediaType.TEXT_HTML_VALUE])
    fun videos(request: HttpServletRequest): ResponseEntity<String> {
        requireAdmin(request)
        return result("Recent TikTok videos", service.listVideos())
    }

    private fun dashboardPage(): String {
        val configured = service.isConfigured()
        val token = service.savedToken()
        val posts = service.recentPosts()
        val tokenStatus = when {
            token == null -> "Not connected"
            else -> "Connected. Scopes: ${escape(token.scope)}. Access token expires: ${formatInstant(token.accessTokenExpiresAt)}"
        }
        val configStatus = if (configured) {
            "Configured"
        } else {
            "Not configured. Set MARKETING_ADMIN_TOKEN, TIKTOK_CLIENT_KEY, TIKTOK_CLIENT_SECRET and TIKTOK_REDIRECT_URI."
        }
        val recentPosts = if (posts.isEmpty()) {
            "<p>No uploads yet.</p>"
        } else {
            posts.joinToString(prefix = "<ul>", postfix = "</ul>") {
                """
                <li>
                    <code>${escape(it.publishId)}</code>
                    ${escape(it.mode.name)}
                    status=${escape(it.status ?: "unknown")}
                    ${it.failReason?.let { reason -> "fail=${escape(reason)}" } ?: ""}
                </li>
                """.trimIndent()
            }
        }
        return layout(
            title = "Purrrfolio TikTok API Demo",
            body = """
            <section>
                <h1>Purrrfolio TikTok API Demo</h1>
                <p>This private page is for TikTok API review and Codex-controlled marketing automation. It is not a player-facing feature.</p>
                <p><strong>Configuration:</strong> $configStatus</p>
                <p><strong>Account:</strong> $tokenStatus</p>
                <form method="post" action="/go/tiktok/logout"><button type="submit">Log out</button></form>
            </section>

            <section>
                <h2>1. Connect TikTok</h2>
                <p>Redirect URI to register in TikTok Developer Portal:</p>
                <pre>${escape(service.redirectUri())}</pre>
                <p>Requested scopes: <code>${escape(properties.marketing.tiktok.scopes)}</code></p>
                <p><a class="button" href="/go/tiktok/oauth/start">Connect TikTok account</a></p>
            </section>

            <section>
                <h2>2. Show creator_info</h2>
                <p>TikTok requires creator info to be queried and rendered before direct posting.</p>
                <form method="post" action="/go/tiktok/creator-info">
                    <button type="submit">Query creator info</button>
                </form>
            </section>

            <section>
                <h2>3. Upload or direct post a video by URL</h2>
                <p>The URL must be a public mp4/mov on the verified Purrrfolio domain or URL prefix.</p>
                <form method="post" action="/go/tiktok/upload">
                    <label>Mode
                        <select name="mode">
                            <option value="INBOX_UPLOAD">Upload to TikTok inbox/draft (video.upload)</option>
                            <option value="DIRECT_POST">Direct private post (video.publish)</option>
                        </select>
                    </label>
                    <label>Video URL
                        <input name="videoUrl" value="${escape(service.defaultDemoVideoUrl())}" required>
                    </label>
                    <label>Caption/title
                        <input name="title" maxlength="150" value="Collect cozy cat cards in Purrrfolio 🐾 #catgame #telegrambot" required>
                    </label>
                    <label>Privacy for Direct Post
                        <select name="privacyLevel">
                            <option value="SELF_ONLY">SELF_ONLY</option>
                            <option value="MUTUAL_FOLLOW_FRIENDS">MUTUAL_FOLLOW_FRIENDS</option>
                            <option value="PUBLIC_TO_EVERYONE">PUBLIC_TO_EVERYONE</option>
                        </select>
                    </label>
                    <button type="submit">Send to TikTok API</button>
                </form>
            </section>

            <section>
                <h2>4. Fetch post status</h2>
                <form method="post" action="/go/tiktok/status">
                    <label>publish_id
                        <input name="publishId" placeholder="v_inbox_url~..." required>
                    </label>
                    <button type="submit">Fetch status</button>
                </form>
            </section>

            <section>
                <h2>5. List public TikTok videos</h2>
                <form method="post" action="/go/tiktok/videos">
                    <button type="submit">Call video.list</button>
                </form>
            </section>

            <section>
                <h2>Recent API uploads</h2>
                $recentPosts
            </section>
            """.trimIndent(),
        )
    }

    private fun loginPage(): String = layout(
        title = "Purrrfolio TikTok Admin Login",
        body = """
        <section>
            <h1>Purrrfolio TikTok API Demo</h1>
            <p>This page is private. Enter the marketing admin token to continue.</p>
            <form method="post" action="/go/tiktok/login">
                <label>Admin token
                    <input name="adminToken" type="password" autocomplete="current-password" required autofocus>
                </label>
                <button type="submit">Open TikTok demo</button>
            </form>
        </section>
        """.trimIndent(),
    )

    private fun result(title: String, json: JsonNode): ResponseEntity<String> = html(
        resultPage(
            title = title,
            body = "<pre>${escape(json.toPrettyString())}</pre>",
        ),
    )

    private fun resultPage(title: String, body: String): String = layout(
        title = title,
        body = """
        <section>
            <p><a href="/go/tiktok">← Back to TikTok dashboard</a></p>
            <h1>${escape(title)}</h1>
            $body
        </section>
        """.trimIndent(),
    )

    private fun layout(title: String, body: String): String = """
        <!doctype html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escape(title)}</title>
            <style>
                body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #fff7fb; color: #241b24; }
                main { max-width: 920px; margin: 0 auto; padding: 32px 18px 64px; }
                section { background: white; border: 1px solid #f0d9e6; border-radius: 18px; padding: 22px; margin: 18px 0; box-shadow: 0 10px 30px rgba(125, 64, 94, 0.08); }
                h1, h2 { margin-top: 0; }
                label { display: block; margin: 14px 0; font-weight: 700; }
                input, select { display: block; box-sizing: border-box; width: 100%; margin-top: 6px; padding: 12px; border: 1px solid #dcc4d2; border-radius: 12px; font: inherit; }
                button, .button { display: inline-block; border: 0; border-radius: 999px; background: #ff4fa3; color: white; padding: 12px 18px; font-weight: 800; text-decoration: none; cursor: pointer; }
                pre { overflow: auto; background: #2b2230; color: #f7eaff; padding: 14px; border-radius: 12px; }
                code { background: #f8e8f1; padding: 2px 5px; border-radius: 6px; }
            </style>
        </head>
        <body><main>$body</main></body>
        </html>
    """.trimIndent()

    private fun html(body: String): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(body)

    private fun requireAdmin(request: HttpServletRequest) {
        if (!isAuthenticated(request)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "TikTok marketing admin login required")
        }
    }

    private fun isAuthenticated(request: HttpServletRequest): Boolean {
        val expected = properties.marketing.adminToken
        if (expected.isBlank()) return false
        val actual = request.cookies?.firstOrNull { it.name == AUTH_COOKIE }?.value ?: return false
        return constantTimeEquals(actual, expected)
    }

    private fun constantTimeEquals(actual: String, expected: String): Boolean {
        if (actual.isBlank() || expected.isBlank()) return false
        return MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach {
            append(
                when (it) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> it
                },
            )
        }
    }

    private fun mask(value: String): String = when {
        value.length <= 8 -> "****"
        else -> value.take(4) + "…" + value.takeLast(4)
    }

    private fun formatInstant(value: java.time.Instant): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atOffset(ZoneOffset.UTC))

    companion object {
        private const val AUTH_COOKIE = "purrrfolio_tiktok_admin"
    }
}
