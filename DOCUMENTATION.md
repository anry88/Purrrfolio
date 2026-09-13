# Purrrfolio Architecture

This document describes the command-only MVP architecture for the Purrrfolio Telegram collectible card bot.

## Overview

The server starts from `PurrrfolioApplication.kt`: Spring Boot loads configuration, runs Flyway migrations, exposes health/metrics endpoints, and handles Telegram webhook updates at `POST /bot`.

Core business logic is split into small packages:

- `catalog/` — JSON-driven cards, themes, rarities
- `collection/` — themed set progress
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
3. Service upserts `players` by `telegram_id`, grants starter fish balance.

### Pack opening (planned)

1. `/pack` checks `fish_balance >= pack.costFish`.
2. Transaction debits fish, inserts `pack_openings`, rolls cards via `PackOpeningService`.
3. Inventory upsert into `player_cards`.
4. Bot sends card PNGs from `/static/assets/cards/{id}.png`.

### Theme completion (planned)

1. `/themes` reads owned unique cards per theme from `player_cards`.
2. When complete and unclaimed, player claims bonus through inline button.
3. Transaction inserts `theme_completion_claims` and credits fish.

### Trading (planned)

1. Seller must own at least two copies (`TradePolicy.MIN_DUPLICATES_TO_TRADE`).
2. Offer stored in `trade_offers` with TTL.
3. Buyer accepts → atomic quantity transfer between `player_cards` rows.

### Marketplace (planned)

1. Seller lists duplicate → `market_listings` row, quantity decremented.
2. Buyer purchases → fish transfer minus fee, card quantity incremented.

## Persistence

PostgreSQL schema is defined in `src/main/resources/db/migration/V1__initial_schema.sql`.

Main tables:

- `players`
- `player_cards`
- `theme_completion_claims`
- `pack_openings`
- `trade_offers`
- `market_listings`
- `processed_telegram_updates`

## Catalog and Assets

Card definitions live in `src/main/resources/catalog/cards.json`. Images are extracted from `docs/source/card-sprite-sheet.png` into:

- `assets/cards/`
- `src/main/resources/static/assets/cards/`

Use `scripts/extract-cards.py` to regenerate PNGs after art changes.

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
