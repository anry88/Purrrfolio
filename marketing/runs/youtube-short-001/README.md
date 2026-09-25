# YouTube Short 001 — Fluffy Pack reveal

First end-to-end marketing-flow pilot for Purrrfolio.

## Creative

- Format: YouTube Short, 1080×1920, 30 fps, 15 seconds.
- Master language: English.
- Selectable subtitle tracks: English, Russian, Turkish, Indonesian.
- Cards: Sleepy (Common), Tiny Gardener (Epic), Boxie (Legendary).
- Hook: “What’s inside a Fluffy Pack?”
- CTA: open `@PurrrfolioBot`, with the honest shipped offer of three starter
  packs containing three cards each.
- Audio: intentionally omitted from the first pipeline pilot. Add licensed or
  original audio only after the visual/upload flow is validated.

## Attribution

Campaign source: `yt_short_001_fluffy_pack`

```text
https://t.me/PurrrfolioBot?start=yt_short_001_fluffy_pack
```

The source matches the bot’s `[a-z0-9_-]{1,64}` registration-source rule.

## Local commands

Run from `marketing/video/`:

```bash
npm run captions
npm run lint
npm run still:pilot
npm run render:pilot
```

The rendered MP4 is local and gitignored at
`marketing/video/out/youtube-short-001.mp4`.

From the repository root, validate the complete local upload package without a
network request:

```bash
python3 scripts/youtube_publish.py
```

After publication, verify channel ownership, public status, metadata, and all
four subtitle languages with `scripts/youtube_verify_upload.py`.

## Remote-write gate

The staged upload completed on 2026-09-25 with EN/RU/TR/ID caption tracks. On
the same date the user authorized public publication and recurring public jobs.
The remote video id is stored only in the gitignored recovery receipt under
`.runtime/marketing/`. The local OAuth token includes `youtube.force-ssl` for
automated caption-track upload.
