package com.anry88.purrrfolio.web

import com.anry88.purrrfolio.config.PurrrfolioProperties
import com.anry88.purrrfolio.game.GameService
import com.anry88.purrrfolio.game.RetryableTelegramUpdateException
import com.anry88.purrrfolio.telegram.TelegramUpdate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

@RestController
class WebhookController(
    private val gameService: GameService,
    private val properties: PurrrfolioProperties,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun index(): String {
        val botUrl = "https://t.me/${escape(properties.telegram.botUsername)}"
        return """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Purrrfolio - Telegram Cat Card Collection Game</title>
                <meta name="description" content="Purrrfolio is a Telegram collectible card game about cozy kawaii cats. Open packs, collect themed cards, trade duplicates, and share your favorite cats.">
                <link rel="icon" href="/favicon.ico" sizes="any">
                <link rel="icon" href="/favicon.png" type="image/png">
                <link rel="apple-touch-icon" href="/apple-touch-icon.png">
                <style>
                    :root {
                        color-scheme: light;
                        --bg: #fff7fb;
                        --card: #ffffff;
                        --ink: #2a1d28;
                        --muted: #6d5b68;
                        --line: #efd6e5;
                        --accent: #ff4fa3;
                        --accent-2: #ffbf5f;
                    }
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        background:
                            radial-gradient(circle at top left, rgba(255, 191, 95, 0.28), transparent 36rem),
                            radial-gradient(circle at top right, rgba(255, 79, 163, 0.18), transparent 34rem),
                            var(--bg);
                        color: var(--ink);
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                        line-height: 1.6;
                    }
                    header, main, footer {
                        width: min(1040px, calc(100% - 36px));
                        margin: 0 auto;
                    }
                    header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        gap: 18px;
                        padding: 24px 0;
                    }
                    .brand {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        font-weight: 900;
                        font-size: 1.25rem;
                    }
                    .logo {
                        width: 44px;
                        height: 44px;
                        border-radius: 14px;
                        display: block;
                        object-fit: cover;
                        border: 1px solid var(--line);
                    }
                    nav {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 12px;
                        align-items: center;
                    }
                    a { color: #b21f73; }
                    nav a {
                        color: var(--ink);
                        text-decoration: none;
                        font-weight: 700;
                    }
                    .hero {
                        display: grid;
                        grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
                        gap: 28px;
                        align-items: center;
                        padding: 48px 0 28px;
                    }
                    .panel {
                        background: rgba(255, 255, 255, 0.86);
                        border: 1px solid var(--line);
                        border-radius: 28px;
                        box-shadow: 0 24px 70px rgba(125, 64, 94, 0.12);
                    }
                    .copy {
                        padding: clamp(28px, 5vw, 48px);
                    }
                    .eyebrow {
                        display: inline-flex;
                        gap: 8px;
                        align-items: center;
                        color: #8c4b20;
                        background: #fff1cf;
                        border: 1px solid #ffe0a0;
                        border-radius: 999px;
                        padding: 6px 12px;
                        font-weight: 800;
                    }
                    h1 {
                        margin: 18px 0 12px;
                        font-size: clamp(2.4rem, 7vw, 5.2rem);
                        line-height: 0.95;
                        letter-spacing: -0.06em;
                    }
                    .lead {
                        max-width: 680px;
                        color: var(--muted);
                        font-size: clamp(1.08rem, 2.2vw, 1.35rem);
                    }
                    .actions {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 12px;
                        margin-top: 26px;
                    }
                    .button {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        border-radius: 999px;
                        padding: 13px 18px;
                        font-weight: 900;
                        text-decoration: none;
                    }
                    .primary {
                        color: white;
                        background: var(--accent);
                    }
                    .secondary {
                        color: var(--ink);
                        background: white;
                        border: 1px solid var(--line);
                    }
                    .card-preview {
                        padding: 22px;
                        text-align: center;
                    }
                    .card-preview img {
                        width: min(100%, 320px);
                        border-radius: 24px;
                        border: 1px solid var(--line);
                        box-shadow: 0 18px 45px rgba(80, 44, 64, 0.16);
                    }
                    .caption {
                        margin: 14px 0 0;
                        color: var(--muted);
                        font-weight: 800;
                    }
                    .features {
                        display: grid;
                        grid-template-columns: repeat(3, minmax(0, 1fr));
                        gap: 16px;
                        margin: 26px 0 44px;
                    }
                    .feature {
                        padding: 22px;
                    }
                    .feature h2 {
                        margin: 0 0 8px;
                        font-size: 1.15rem;
                    }
                    .feature p {
                        margin: 0;
                        color: var(--muted);
                    }
                    footer {
                        display: flex;
                        justify-content: space-between;
                        flex-wrap: wrap;
                        gap: 12px;
                        border-top: 1px solid var(--line);
                        padding: 24px 0 36px;
                        color: var(--muted);
                    }
                    footer a { font-weight: 800; }
                    @media (max-width: 780px) {
                        header, footer { align-items: flex-start; }
                        header, .hero, .features { grid-template-columns: 1fr; }
                        header { flex-direction: column; }
                    }
                </style>
            </head>
            <body>
                <header>
                    <div class="brand"><img class="logo" src="/assets/purrrfolio-icon.png" alt="Purrrfolio app icon"><span>Purrrfolio</span></div>
                    <nav aria-label="Main navigation">
                        <a href="$botUrl">Play on Telegram</a>
                        <a href="/privacy">Privacy Policy</a>
                        <a href="/terms">Terms of Service</a>
                    </nav>
                </header>
                <main>
                    <section class="hero">
                        <div class="panel copy">
                            <span class="eyebrow">Telegram collectible card game</span>
                            <h1>Collect cozy cat cards.</h1>
                            <p class="lead">
                                Purrrfolio is a Telegram-first collectible card game about kawaii cats.
                                Open fluffy packs, complete themed collections, trade duplicates, craft
                                extra packs, join group raffles, and share your favorite cards with friends.
                            </p>
                            <div class="actions">
                                <a class="button primary" href="$botUrl">Start collecting in Telegram</a>
                                <a class="button secondary" href="/privacy">Privacy Policy</a>
                                <a class="button secondary" href="/terms">Terms of Service</a>
                            </div>
                        </div>
                        <div class="panel card-preview" aria-label="Purrrfolio card preview">
                            <img src="/assets/cards/tennis-kitty.png" alt="Tennis Kitty collectible card">
                            <p class="caption">Open packs. Find rare cats. Complete collections.</p>
                        </div>
                    </section>
                    <section class="features" aria-label="Game features">
                        <div class="panel feature">
                            <h2>🎁 Open packs</h2>
                            <p>New players receive starter packs, then collect more through free cards, crafting, referrals, raffles, and Telegram Stars.</p>
                        </div>
                        <div class="panel feature">
                            <h2>🧺 Complete sets</h2>
                            <p>Build themed collections across cozy cats, hobbies, food, travel, seasons, countries, and special group-only cards.</p>
                        </div>
                        <div class="panel feature">
                            <h2>🤝 Trade socially</h2>
                            <p>Use random trades, marketplace offers, and shareable card posts with referral links to grow your collection.</p>
                        </div>
                    </section>
                </main>
                <footer>
                    <span>© 2026 Purrrfolio. Operated through the official Telegram bot.</span>
                    <span><a href="/privacy">Privacy Policy</a> · <a href="/terms">Terms of Service</a> · <a href="$botUrl">Telegram Bot</a></span>
                </footer>
            </body>
            </html>
        """.trimIndent()
    }

    @GetMapping("/health")
    fun health() = mapOf("status" to "UP")

    @PostMapping("/bot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun webhook(
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) providedSecret: String?,
        @RequestBody update: TelegramUpdate,
    ) {
        val expected = properties.telegram.webhookSecret
        if (expected.isBlank() || providedSecret == null || !constantTimeEquals(expected, providedSecret)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        try {
            gameService.handle(update)
        } catch (e: RetryableTelegramUpdateException) {
            logger.error("Retryable Telegram webhook failure for update {}", update.updateId, e)
            throw e
        } catch (e: Throwable) {
            // Unexpected terminal failures are acknowledged only when GameService did
            // not classify them as retryable. Normal update claims are released before
            // RetryableTelegramUpdateException reaches this boundary.
            logger.error("Failed to process Telegram webhook update {}", update.updateId, e)
        }
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean = MessageDigest.isEqual(
        expected.toByteArray(Charsets.UTF_8),
        actual.toByteArray(Charsets.UTF_8),
    )

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
}
