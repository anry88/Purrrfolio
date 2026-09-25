# Purrrfolio Marketing Video

Remotion project for reproducible 9:16 Purrrfolio marketing videos.

## Setup

```bash
npm install
```

Selected card art is copied from the repository source of truth into the local,
gitignored `public/cards/` directory before preview or render.

## Pilot commands

```bash
npm run captions
npm run lint
npm run dev
npm run still:pilot
npm run render:pilot
```

`render:pilot` creates the gitignored file
`out/youtube-short-001.mp4`. Caption source and exports live in
`../runs/youtube-short-001/`.

The production and subtitle policies are documented in
[`../AUTOMATION.md`](../AUTOMATION.md) and
[`../SUBTITLE_REQUIREMENTS.md`](../SUBTITLE_REQUIREMENTS.md).
