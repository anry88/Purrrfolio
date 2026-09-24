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
- `craft/` — duplicate-to-pack crafting policy
- `repository/` — JDBC persistence for players, inventory, economy, trades, payments, rewards, and raffles
- `game/` — command routing and gameplay orchestration
- `telegram/` — Telegram API integration
- `observability/` — Micrometer counters and database gauges
- `web/` — HTTP entrypoints

## Runtime Surfaces

| Surface | Status |
| --- | --- |
| Telegram bot commands | Implemented (`GameService`) |
| Telegram webhook | Implemented (`WebhookController`) |
| Telegram long polling | Implemented for local/optional runtime use (`TelegramPollingRunner`) |
| Mini App | Out of scope |
| Admin UI | Out of scope for MVP |

## Data Flow

### Registration

1. Telegram update arrives at `/bot`.
2. Webhook validates the secret token and claims `update_id` as `PROCESSING`; failures release the claim and successful handlers mark it `PROCESSED`.
3. The first supported command or text alias creates `users` by `telegram_user_id`.
4. Russian Telegram language codes select RU; all others select EN. `/language` can change the stored language later.
5. User creation and the 3-pack starter `pack_ledger` grant commit in one transaction; a normalized `/start <source>` code is stored in `users.registration_source`.
6. For a valid `ref_<users.id>` source, the transaction also claims one `referral_rewards` row and grants 5 packs to both the new player and the referrer. The new player therefore starts with 8 packs. Existing accounts, missing referrers, self-referrals, and retries do not receive another grant.
7. A new `/start` receives a short welcome followed by a dedicated inline button for opening the starter packs; both players receive localized referral-reward messages when applicable.

### Pack opening

1. `/pack` locks the player's `users` row and rechecks the ledger balance, preventing two concurrent taps from spending the same pack.
2. Empty stash → `/buy` prompt. Free single cards are fully separate: `/freecard`
   immediately grants the card when due (first card immediately, then every 3h via
   `users.last_free_card_at`) and shows an hours/minutes countdown otherwise.
3. The negative `pack_ledger` row, weighted roll, `user_cards` upserts, completion rewards, and `pack_opening_receipts` row commit in one transaction.
4. `pack_opening_receipts.update_id` stores the exact rolled/new card ids. A webhook retry restores that result rather than consuming another pack.
5. Bot sends card PNGs from `/static/assets/cards/{id}.png`, each with a share button, then sends a new-card count and progress for the affected collections. Text fallback is used if photo delivery fails.
6. Calendar special cards are filtered by the configured game-timezone month after
   rarity is selected, so their normal rarity weights remain unchanged.
7. Friends special cards are included only when the Telegram update comes from a
   `group` or `supergroup`; private-chat rolls exclude them after rarity selection.

### Stars purchase

1. `/buy` sends a buyer-bound Telegram Stars invoice (XTR, 5/12/16/25 Stars for 1/3/5/10 packs). Its payload is authenticated with HMAC so the buyer, pack count, and price cannot be altered.
2. `pre_checkout_query` is answered OK only after buyer, payload, currency, and amount validation.
3. `successful_payment` revalidates the order and atomically inserts `payments` + one positive `pack_ledger` row. Telegram retries are idempotent by charge id.
4. `/paysupport` lists completed purchases without an existing support request and creates one persisted request per payment.
5. The private `ADMIN_TG_ID` can `/refund`, `/reject`, or `/ask`; users answer requests for information with `/answer`.
6. A confirmed refund calls Telegram first, then atomically marks the payment/request refunded and writes one negative `pack_ledger` reversal. If purchased packs were already opened, the ledger may become negative so future grants repay the refunded entitlement.

### Theme completion

1. `/collection` reads owned unique cards per theme from `user_cards` (10 collections per page).
2. Gallery navigation shows only opened cards; duplicates expose trade/market buttons.
3. Receiving cards and opening `/collection` both check for newly completed collections.
4. A successful claim and its one-pack `collection_completion` ledger grant commit atomically for the current catalog-card-set hash.

### Pack crafter

1. `/craft` shows `craft_points` balance and duplicates priced by rarity (1/2/3/4/5).
2. Each tap burns one duplicate copy and adds points via `CraftPolicy.addPoints`.
3. Every 15 points → 1 pack into `pack_ledger` (source=craft), overflow carries over.

### Trading

1. Seller must own at least two copies (`TradePolicy.MIN_DUPLICATES_TO_TRADE`).
2. Card copy leaves inventory into `random_trade_pool` with WAITING status.
3. First waiting card of another player with a different card id matches → both cards dealt out, MATCHED.
4. Waiting entries return via «↩️ Return» (CANCELLED + inventory credit).

### Marketplace

1. Seller lists duplicate → `market_listings` row, one copy removed from inventory.
2. Seller can return the card (`CANCELLED` + inventory credit).
3. Buyer picks a foreign listing, chooses one of their own listings as offer → `trade_offers` PENDING.
4. Only the target-card owner can accept or reject. Acceptance locks both active
   listings and atomically moves both escrowed cards before marking them SOLD.
5. Exchange results are sent only to the two listing owners, in each owner's language.

## Localization

All player-facing copy is centralized in `i18n/Messages.kt` as EN/RU keyed strings, resolved through `Messages.t(key, locale, vararg args)`.

- New players default to English; `users.language` is set to Russian only when their Telegram `language_code` starts with `ru`.
- `/language` opens an inline keyboard (`lang:en` / `lang:ru` callbacks) that persists the choice via `UserRepository.updateLanguage`.
- Card, theme, and pack display names are localized via the `nameFor(locale)` extensions in `i18n/LocalizedNames.kt`; rarity labels via `CardRarity.labelEn`/`labelRu`.
- Common actions can be routed from slash commands, reply/inline buttons, or exact
  plain-word aliases in Russian and English. Free-card aliases include the common
  cat words documented in `/help`.

## Persistence

PostgreSQL schema is defined in `src/main/resources/db/migration/` (currently V1–V14). V1 is the legacy fish schema; V3 introduces the command-only Stars schema; later migrations fix card/ledger types and add free-card timing, crafting, registration attribution, payment support, versioned collection rewards, group raffles, update-claim lifecycle, idempotent pack-opening receipts, and referral rewards.

Main tables:

- `users`
- `user_cards` (TEXT `card_id` matching `catalog/cards.json`)
- `pack_ledger` (positive grants, negative `opened` consumption rows)
- `collection_completion_rewards` (one claim per user, theme and catalog-card-set hash)
- `random_trade_pool`
- `market_listings`
- `trade_offers`
- `payments`
- `payment_support_requests`
- `processed_telegram_updates`
- `pack_opening_receipts`
- `referral_rewards` (one immutable reward claim per referred user)
- `group_chat_members`
- `group_raffles`
- `group_raffle_winners`

## Catalog and Assets

Card definitions live in `src/main/resources/catalog/cards.json`. Card art is
model-generated: one standalone 1024×1536 PNG per card in `assets/cards/`,
mirrored to `src/main/resources/static/assets/cards/`. Prompts and seeds are
recorded in `docs/source/card-art-prompts.md`; the full card/collection roster
with RU/EN names lives in `docs/collections.md`. Keep both PNG trees in sync
after art changes.

Collection completion rewards are evaluated against this JSON catalog. A
SHA-256 hash of each collection's sorted card ids is stored with the reward
claim, so catalog expansion creates a new eligible version without duplicating
rewards for the unchanged version. The claim and its `collection_completion`
pack-ledger entry are committed atomically.

## Configuration

Application settings are in `src/main/resources/application.yml` under the `purrrfolio.*` prefix:

- `purrrfolio.telegram.*` — bot token, webhook secret, username
- `purrrfolio.telegram.admin-tg-id` / `ADMIN_TG_ID` — private admin identity for payment refunds
- `purrrfolio.telegram.payment-payload-secret` / `PAYMENT_PAYLOAD_SECRET` — HMAC key for Stars orders; a blank value falls back to the webhook secret
- `purrrfolio.economy.*` — starter packs, referral bonus packs (`REFERRAL_BONUS_PACKS`, default 5), free-card interval, cards per pack, and Stars bundle prices

Local overrides: copy `application-local.example.yml` to `application-local.yml` (gitignored).

## Observability

- `GET /health` — liveness-style check on app port
- `GET /` — service metadata
- Spring Actuator on port `9090` — health probes and Prometheus metrics
- `observability/GameMetrics` — Micrometer counters (`purrrfolio.bot.command`,
  `purrrfolio.bot.callback`, `purrrfolio.registration`, `purrrfolio.stars.purchase`,
  `purrrfolio.pack.opened`, `purrrfolio.card.claimed`, `purrrfolio.craft.*`,
  `purrrfolio.trade.matched`, `purrrfolio.market.offer`) plus per-minute
  database gauges (players, registrations by source, packs by ledger source,
  Stars payments, craft bank, pool depth, market listings)
- Grafana board source: `docs/grafana/dashboard.json` (+ scrape snippet in
  `docs/grafana/README.md`); registration attribution comes from deep links
  (`t.me/<bot>?start=<source>`) stored in `users.registration_source`

## Known Reliability Boundaries and Next Engineering Steps

1. Telegram delivery remains at-least-once: the pack receipt prevents another debit or card grant, but a process crash after Telegram accepts a message and before local completion can duplicate a reveal on retry.
2. Extend Testcontainers PostgreSQL coverage beyond registration and pack opening to crafting, random trades, marketplace settlement, payments, completion rewards, and group raffles.
