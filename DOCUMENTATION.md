# Purrrfolio Architecture

This document describes the command-only MVP architecture for the Purrrfolio Telegram collectible card bot.

## Overview

The server starts from `PurrrfolioApplication.kt`: Spring Boot loads configuration, runs Flyway migrations, exposes health/metrics endpoints, and handles Telegram webhook updates at `POST /bot`.

Core business logic is split into small packages:

- `catalog/` — JSON-driven cards, themes, rarities
- `collection/` — themed set progress
- `i18n/` — localized player copy (EN/RU) and locale helpers
- `pack/` — weighted random pack openings
- `trade/` — trade and marketplace rules
- `game/` — command routing (persistence to be added)
- `telegram/` — Telegram API integration
- `web/` — HTTP entrypoints

## Runtime Surfaces

| Surface | Status |
| --- | --- |
| Telegram bot commands | Scaffold (`GameService`) |
| Telegram webhook | Implemented (`WebhookController`) |
| Mini App | Out of scope |
| Admin UI | Out of scope for MVP |

## Data Flow

### Registration (planned)

1. Telegram update arrives at `/bot`.
2. Webhook validates secret token and deduplicates by `update_id`.
3. Service upserts `users` by `telegram_user_id`, grants 3 starter packs via `pack_ledger`.

### Pack opening (planned)

1. `/pack` first shows the free single-card status: claim button when due (first card immediately, then every 7h via `users.last_free_card_at`), countdown otherwise.
2. If `pack_ledger` sum > 0, a pack opens: negative `pack_ledger` row, roll via `PackOpeningService`.
3. Inventory upsert into `user_cards` (TEXT card ids matching `catalog/cards.json`).
4. Bot sends card PNGs from `/static/assets/cards/{id}.png`.

### Stars purchase

1. `/buy` sends a Telegram Stars invoice (XTR, 5/12/16/25 Stars for 1/3/5/10 packs).
2. `pre_checkout_query` is answered OK after payload/amount validation.
3. `successful_payment` inserts `payments` + positive `pack_ledger` row (idempotent by Telegram charge id).

### Theme completion (planned)

1. `/collection` reads owned unique cards per theme from `user_cards` (10 collections per page).
2. Gallery navigation shows only opened cards; duplicates expose trade/market buttons.

### Trading (planned)

1. Seller must own at least two copies (`TradePolicy.MIN_DUPLICATES_TO_TRADE`).
2. Card copy leaves inventory into `random_trade_pool` with WAITING status.
3. First waiting card of another player with a different card id matches → both cards dealt out, MATCHED.

### Marketplace (planned)

1. Seller lists duplicate → `market_listings` row, one copy removed from inventory.
2. Seller can return the card (`CANCELLED` + inventory credit).
3. Buyer picks a foreign listing, chooses one of their own listings as offer → `trade_offers` PENDING.
4. Owner accepts → atomic card exchange, both listings SOLD; rejects → REJECTED.

## Localization

All player-facing copy is centralized in `i18n/Messages.kt` as EN/RU keyed strings, resolved through `Messages.t(key, locale, vararg args)`.

- New players default to English; the stored `players.locale` is set to Russian only when their Telegram `language_code` starts with `ru`.
- `/language` opens an inline keyboard (`lang:en` / `lang:ru` callbacks) that persists the choice via `PlayerRepository.updateLocale`.
- Card, theme, and pack display names are localized via the `nameFor(locale)` extensions in `i18n/LocalizedNames.kt`; rarity labels via `CardRarity.labelEn`/`labelRu`.

## Persistence

PostgreSQL schema is defined in `src/main/resources/db/migration/` (`V1` legacy fish schema, `V3` Stars schema per PDF v1.3, `V4` card-id/ledger fixes).

Main tables:

- `users`
- `user_cards` (TEXT `card_id` matching `catalog/cards.json`)
- `pack_ledger` (positive grants, negative `opened` consumption rows)
- `random_trade_pool`
- `market_listings`
- `trade_offers`
- `payments`
- `processed_telegram_updates`

## Catalog and Assets

Card definitions live in `src/main/resources/catalog/cards.json`. Card art is
model-generated: one standalone 1024×1536 PNG per card in `assets/cards/`,
mirrored to `src/main/resources/static/assets/cards/`. Prompts and seeds are
recorded in `docs/source/card-art-prompts.md`; keep both PNG trees in sync
after art changes.

## Configuration

Application settings are in `src/main/resources/application.yml` under the `purrrfolio.*` prefix:

- `purrrfolio.telegram.*` — bot token, webhook secret, username
- `purrrfolio.economy.*` — starter fish, daily reward, pack cost, cards per pack

Local overrides: copy `application-local.example.yml` to `application-local.yml` (gitignored).

## Observability

- `GET /health` — liveness-style check on app port
- `GET /` — service metadata
- Spring Actuator on port `9090` — health probes and Prometheus metrics

## Next Engineering Steps

1. Add JDBC repositories and wire `GameService` to PostgreSQL.
2. Implement idempotent webhook processing with `processed_telegram_updates`.
3. Send card images via Telegram `sendPhoto`.
4. Implement `/pack`, `/daily`, `/trade`, and `/market` transactions.
5. Add integration tests with Testcontainers PostgreSQL.
