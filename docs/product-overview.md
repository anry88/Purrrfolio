# Purrrfolio Product Overview

`Purrrfolio` is a Telegram-first collectible card game about cozy kawaii cats. Players open card packs, complete themed sets, and trade duplicates — all through bot commands, without a Mini App.

The repository is intentionally scoped as a command-only MVP before any richer client surfaces are considered.

## Product Scope

The target MVP includes:

- Telegram bot registration and persistent player profiles
- In-game currency (fish tokens) for pack openings and market trades
- Five-tier card rarity with weighted pack rolls
- Themed collections with one-time completion bonuses
- Daily reward loop
- Duplicate trading between players
- Simple marketplace for selling duplicates for fish tokens
- Static card art served from the backend and sent as Telegram photos

The concept art in `docs/source/game-concept.png` also shows a decorated home, Mini App UI, and animated pack openings. Those are **not** part of the MVP and remain future ideas.

## Core Loop

1. Player runs `/start` and receives starter fish tokens.
2. Player opens a fluffy pack with `/pack` and receives three cards.
3. New cards expand themed collection progress shown in `/themes`.
4. Duplicate cards become tradable through `/trade` or `/market`.
5. Completing a theme grants a fish bonus and gives a reason to chase missing cards.
6. `/daily` brings the player back on the next day.

## Engagement Systems

- **Rarity chase** — legendary and special cards are intentionally scarce.
- **Set completion** — themes create medium-term goals beyond random pack openings.
- **Duplicate economy** — trading and marketplace turn bad luck into social interaction.
- **Daily reward** — low-friction return path without heavy quest UI.
- **Seasonal specials (planned)** — limited-time cards injected through events.

## Monetization (planned)

The MVP uses only earnable fish tokens. Future phases may add Telegram Stars for premium packs or event bundles, following the same pattern as other tg-games projects in this workspace.

## Technical Shape

- Kotlin + Spring Boot modular monolith
- PostgreSQL + Flyway
- JSON-driven card/theme catalog
- Telegram webhook as the only player-facing runtime surface in phase 1
