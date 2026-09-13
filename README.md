# Purrrfolio

Purrrfolio (КотоКоллекция) is a Telegram-first collectible card game about cozy kawaii cats. Players open fluffy packs, complete themed sets, and trade duplicates — all through bot commands, without a Mini App.

The repository currently contains the project scaffold: Kotlin/Spring Boot backend, PostgreSQL schema, JSON card catalog, extracted starter card art, and implementation documentation derived from the concept images in `docs/source/`.

![Starter cards](assets/cards/sleepy.png)

## Play (planned)

Open `@purrrfolio_bot` and use:

- `/start` — register and open the main menu
- `/collection` — inspect owned cards and duplicates
- `/pack` — open a fluffy pack for fish tokens
- `/themes` — track themed collection progress and claim set bonuses
- `/trade` — offer a duplicate to another player
- `/market` — list or buy duplicate cards on the in-game market
- `/daily` — claim daily fish tokens
- `/profile` — balance and collection stats
- `/help` — command help

## Product Principles

- A normal session should fit into 1–3 minutes of Telegram chat.
- Depth comes from rarity, themed sets, duplicates, and social trading — not from a separate client UI.
- Card definitions, themes, pack prices, and rarity weights are data-driven JSON.
- The backend is the authority for inventory, economy, trades, and marketplace settlement.

## Stack

`Kotlin` `Spring Boot` `PostgreSQL` `Flyway` `Telegram Bot API` `Gradle`

## Documentation

- Implementation spec (command-only MVP): [docs/implementation-spec.md](docs/implementation-spec.md)
- Product overview: [docs/product-overview.md](docs/product-overview.md)
- Architecture: [DOCUMENTATION.md](DOCUMENTATION.md)
- Agent guide: [AGENTS.md](AGENTS.md)

## Local Development

### Requirements

- JDK 17+
- Docker (for PostgreSQL) or a local PostgreSQL instance
- Telegram bot token (from [@BotFather](https://t.me/BotFather))

### Quick start

```bash
docker compose up -d
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
# edit application-local.yml with TELEGRAM_BOT_TOKEN and TELEGRAM_WEBHOOK_SECRET

./gradlew test
./gradlew bootRun --args='--spring.profiles.active=local'
```

Regenerate card PNGs from the sprite sheet:

```bash
python3 scripts/extract-cards.py
```

## Repository Layout

```text
src/main/kotlin/com/anry88/purrrfolio/
  catalog/     JSON-driven card and theme definitions
  collection/  themed set progress helpers
  pack/        weighted pack opening logic
  trade/       trade and marketplace policies
  telegram/    Telegram API client and update models
  game/        command routing and player-facing copy
  web/         health checks and bot webhook
assets/cards/  extracted card PNGs (source of truth for art pipeline)
docs/source/   original concept and sprite sheet images
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
