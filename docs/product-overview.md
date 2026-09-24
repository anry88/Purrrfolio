# Purrrfolio Product Overview

`Purrrfolio` is a Telegram-first collectible card game about cozy kawaii cats. Players open card packs, complete themed sets, and trade duplicates — all through bot commands, without a Mini App.

The repository is intentionally scoped as a command-only MVP before any richer client surfaces are considered.

## Implemented Product Scope

The command-only backend currently supports:

- Telegram bot registration and persistent player profiles
- No in-game currency: 3 starter packs of 3 cards, 1 free single card every 3 hours, extra packs via Telegram Stars
- Six-tier card rarity with weighted pack rolls
- Themed collections with progress tracking
- One free pack for each completed collection version; expanded collections can reward again
- Duplicate trading between players via a shared random-trade pool
- Simple card-for-card marketplace (list duplicates, offer listed cards, accept/reject)
- Static card art served from the backend and sent as Telegram photos
- Duplicate crafting into pack progress
- Telegram Stars pack purchases, payment support, and admin-approved refunds
- Daily group-chat pack raffles and group-only Friends cards
- RU/EN localization, campaign-source attribution, and gameplay metrics

The concept art in `docs/source/game-concept.png` also shows a decorated home, Mini App UI, and animated pack openings. Those are **not** part of the MVP and remain future ideas.

## Core Loop

1. Player runs `/start`; the bot detects RU from Telegram's language code or defaults to EN, registers the player, and grants 3 starter packs.
2. Player opens a fluffy pack with `/pack` and receives three cards.
3. New cards expand themed collection progress shown in `/collection`.
4. Duplicate cards become tradable through `/trade` or `/market`.
5. `/buy` adds more packs with Telegram Stars; a free single card drops every 3 hours.

## Current Boundaries

- The player surface is Telegram commands, reply keyboards, inline callbacks, and photo messages; there is no Mini App.
- The catalog currently contains 285 cards across 16 collections. The larger 1000-card content target remains future work.
- Deep-link campaign attribution is implemented, but share buttons and player-to-player referral rewards are not.
- Pack opening and initial registration still need stronger transaction boundaries and PostgreSQL integration coverage before broad production growth.

## Engagement Systems

- **Rarity chase** — legendary and special cards are intentionally scarce.
- **Set completion** — themes create medium-term goals beyond random pack openings.
- **Duplicate economy** — trading and marketplace turn bad luck into social interaction.
- **Free-card loop** — a single card every 3 hours plus Stars bundles bring the player back.
- **Seasonal specials** — calendar cards enter the normal rarity pools only during their matching month or season.
- **Social specials** — Friends cards enter the normal rarity pools only when a pack or free card is requested from a group chat.

## Monetization

Telegram Stars for pack bundles (1 pack = 5 Stars, 3 = 12, 5 = 16, 10 = 25). No other currency.

## Technical Shape

- Kotlin + Spring Boot modular monolith
- PostgreSQL + Flyway
- JSON-driven card/theme catalog
- Telegram webhook as the only player-facing runtime surface in phase 1
