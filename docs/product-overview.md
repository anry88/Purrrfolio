# Purrrfolio Product Overview

`Purrrfolio` is a Telegram-first collectible card game about cozy kawaii cats. Players open card packs, complete themed sets, and trade duplicates — all through bot commands, without a Mini App.

The repository is intentionally scoped as a command-only MVP before any richer client surfaces are considered.

## Product Scope

The target MVP includes:

- Telegram bot registration and persistent player profiles
- No in-game currency: 3 starter packs, 1 free pack every 23 hours, extra packs via Telegram Stars
- Five-tier card rarity with weighted pack rolls
- Themed collections with progress tracking
- Duplicate trading between players via a shared random-trade pool
- Simple card-for-card marketplace (list duplicates, offer listed cards, accept/reject)
- Static card art served from the backend and sent as Telegram photos

The concept art in `docs/source/game-concept.png` also shows a decorated home, Mini App UI, and animated pack openings. Those are **not** part of the MVP and remain future ideas.

## Core Loop

1. Player runs `/start`, picks a language and receives 3 starter packs.
2. Player opens a fluffy pack with `/pack` and receives three cards.
3. New cards expand themed collection progress shown in `/collection`.
4. Duplicate cards become tradable through `/trade` or `/market`.
5. `/buy` adds more packs with Telegram Stars; a free pack arrives every 23 hours.

## Engagement Systems

- **Rarity chase** — legendary and special cards are intentionally scarce.
- **Set completion** — themes create medium-term goals beyond random pack openings.
- **Duplicate economy** — trading and marketplace turn bad luck into social interaction.
- **Free-pack loop** — a pack every 23 hours plus Stars bundles bring the player back.
- **Seasonal specials (planned)** — limited-time cards injected through events.

## Monetization

Telegram Stars for pack bundles (1 pack = 5 Stars, 3 = 12, 5 = 16, 10 = 25). No other currency.

## Technical Shape

- Kotlin + Spring Boot modular monolith
- PostgreSQL + Flyway
- JSON-driven card/theme catalog
- Telegram webhook as the only player-facing runtime surface in phase 1
