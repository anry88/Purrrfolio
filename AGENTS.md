# Purrrfolio Agent Guide

AI-oriented repository guide for coding assistants and code-review tools.

## Documentation Split

- `README.md` is the human-facing product overview.
- `docs/product-overview.md` covers product intent and MVP scope.
- `docs/implementation-spec.md` is the authoritative gameplay and command spec for the command-only MVP.
- `docs/github-about.md` is the source of truth for GitHub About description and topics.
- `DOCUMENTATION.md` is the engineering architecture overview.
- Package-level notes live next to the code under `src/main/kotlin/com/anry88/purrrfolio/**`.

## Current State

- This repository contains a working command-only Telegram collectible card bot backend.
- Implemented today: JDBC persistence, Flyway migrations, retryable update claims, atomic registration/starter and monthly-capped referrer grants, concurrency-safe transactional pack openings, idempotent pack-opening receipts, free cards, photo reveals and galleries, collection progress and versioned completion rewards, cached-photo inline card sharing with named referral links, crafting, random-trade matching, marketplace settlement, paginated market lists (5 per page), daily expiry returning 7-day-old listings to owners, Telegram Stars purchases/refunds, payment support, group raffles, campaign/referral-source attribution, and metrics.
- The JSON catalog currently contains 285 cards in 16 collections, including Calendar and group-only Friends special collections; standalone card art is mirrored into the runtime resources.
- Known gaps: Telegram delivery is at-least-once (a crash between Telegram accepting a message and the local acknowledgement can duplicate that message); PostgreSQL integration coverage does not yet cover every trade/payment/raffle path; referral abuse across multiple Telegram accounts is not identity-verified beyond one reward per newly registered account.
- Do not describe planned behavior as shipped until code and tests support the claim.

## Repository Map

- `src/main/kotlin/com/anry88/purrrfolio/catalog/`: JSON card/theme catalog and rarity weights.
- `src/main/kotlin/com/anry88/purrrfolio/collection/`: themed set progress formatting.
- `src/main/kotlin/com/anry88/purrrfolio/craft/`: duplicate-to-pack crafting policy.
- `src/main/kotlin/com/anry88/purrrfolio/i18n/`: localized player copy (EN/RU) and locale helpers.
- `src/main/kotlin/com/anry88/purrrfolio/pack/`: weighted pack roll logic.
- `src/main/kotlin/com/anry88/purrrfolio/repository/`: JDBC repositories for players, cards, packs, trades, payments, rewards, and raffles.
- `src/main/kotlin/com/anry88/purrrfolio/trade/`: trade/market models and policy constants.
- `src/main/kotlin/com/anry88/purrrfolio/telegram/`: Telegram client and update DTOs.
- `src/main/kotlin/com/anry88/purrrfolio/game/`: command routing and player-facing copy.
- `src/main/kotlin/com/anry88/purrrfolio/observability/`: Micrometer counters and database gauges.
- `src/main/kotlin/com/anry88/purrrfolio/web/`: health endpoints and webhook controller.
- `src/main/kotlin/com/anry88/purrrfolio/config/`: Spring configuration properties.
- `src/main/resources/catalog/cards.json`: starter cards, themes, and pack definitions.
- `src/main/resources/db/migration/`: PostgreSQL schema.
- `src/main/resources/static/assets/cards/`: card PNGs served to Telegram.
- `assets/cards/`: model-generated card art, one standalone 1024×1536 PNG per card (prompts in `docs/source/card-art-prompts.md`).
- `docs/source/`: original concept art and sprite sheet (legacy reference).

## Key Runtime Facts

- Runtime stack: Kotlin + Spring Boot + JDBC + Flyway + PostgreSQL.
- Player surface in MVP: Telegram bot commands, reply keyboard, inline callbacks only. **No Mini App.**
- There is no in-game currency. Pack availability is derived from positive and negative rows in `pack_ledger`; duplicate crafting uses `users.craft_points`.
- Card definitions, collection metadata, pack definitions, and rarity weights are data-driven through `cards.json`; economy timing and Stars prices live under `purrrfolio.economy.*` in application configuration.
- Webhook endpoint: `POST /bot` with `X-Telegram-Bot-Api-Secret-Token`.
- Actuator/prometheus on `${MANAGEMENT_PORT:9090}`.
- Secrets and privileged identifiers (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `PAYMENT_PAYLOAD_SECRET`, `ADMIN_TG_ID`, DB credentials) belong in environment or local profile files only.

## First Pass For Any Agent

1. Read [README.md](README.md), [AGENTS.md](AGENTS.md), [DOCUMENTATION.md](DOCUMENTATION.md), and [docs/implementation-spec.md](docs/implementation-spec.md).
2. Inspect the current branch and working tree before editing.
3. Prefer the smallest coherent change that matches the implementation spec.
4. Verify with `./gradlew test` after Kotlin changes.
5. If you change card art, regenerate with the image model per `docs/source/card-art-prompts.md` and keep `assets/cards/` in sync with `src/main/resources/static/assets/cards/`. No sprite sheets, no script rendering.

## Common Change Paths

### New card or theme

You usually need to touch:

- `src/main/resources/catalog/cards.json`
- card PNG in `assets/cards/` and `src/main/resources/static/assets/cards/`
- tests under `src/test/kotlin/com/anry88/purrrfolio/catalog/`

### Bot command behavior

You usually need to touch:

- `src/main/kotlin/com/anry88/purrrfolio/game/GameService.kt`
- `src/main/kotlin/com/anry88/purrrfolio/i18n/Messages.kt` (player-facing copy lives here, not in GameService)
- the relevant repository when persistence behavior changes

### Language and copy

New players default to English. Russian is auto-selected when the Telegram `language_code` starts with `ru`; `/language` switches at any time. The locale is stored in `users.language`. To change any player-facing text, edit `src/main/kotlin/com/anry88/purrrfolio/i18n/Messages.kt`. Menu button labels are matched case- and space-insensitively in both languages, so new button labels must stay emoji-prefixed (e.g. `🎁 Набор` / `🎁 Pack`).

### Economy or rarity

You usually need to touch:

- `src/main/kotlin/com/anry88/purrrfolio/catalog/CardRarity.kt`
- `src/main/kotlin/com/anry88/purrrfolio/pack/PackOpeningService.kt`
- `src/main/resources/catalog/cards.json`
- `docs/implementation-spec.md` if public rules change

### Trade or marketplace

You usually need to touch:

- `src/main/kotlin/com/anry88/purrrfolio/trade/`
- `src/main/resources/db/migration/` if schema changes are required
- `docs/implementation-spec.md`

## Update Rules

- If you change public product promises, update `README.md`.
- If you change MVP gameplay rules, update `docs/implementation-spec.md`.
- If you change architecture boundaries, update `DOCUMENTATION.md` and this file.
- Keep public claims honest: label planned features explicitly until implemented.
- Do not commit secrets, `.env`, or `application-local.yml`.
