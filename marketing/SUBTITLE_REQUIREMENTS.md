# Subtitle Requirements

These requirements apply to every Purrrfolio marketing video unless a run brief
explicitly narrows the target languages.

## Master video

- Burned-in copy is English only.
- Spoken narration, when present, is English by default.
- Do not burn multiple translations into the same frame.
- Card names remain their canonical English catalog names in every language.

## Required selectable tracks

Every scheduled YouTube run must provide these four tracks:

| Code | Track name | Requirement |
| --- | --- | --- |
| `en` | English | Accurate master transcript/caption track |
| `ru` | Русский | Natural Russian translation |
| `tr` | Türkçe | Natural Turkish translation |
| `id` | Bahasa Indonesia | Natural Indonesian translation |

Additional languages may be added, but none of the required four may be
removed without an explicit product decision.

## Canonical format

- Caption sources are committed UTF-8 JSON arrays using Remotion's `Caption`
  shape: `text`, `startMs`, `endMs`, `timestampMs`, `confidence`.
- Translated tracks use the same timing boundaries as the English source.
- `timestampMs` and `confidence` are `null` for authored captions.
- Captions are ordered, non-overlapping, non-empty, and end no later than the
  video duration.
- Deterministic tooling exports both SRT and WebVTT. Generated files must be
  reproducible from the JSON sources.

## Translation rules

- Preserve meaning, tone, card rarity, pack counts, and calls to action.
- Do not invent prices, rewards, guarantees, scarcity, or gameplay features.
- Localize rarity labels and ordinary prose; keep brand names, card names,
  `Purrrfolio`, and `@PurrrfolioBot` unchanged.
- Keep each cue readable within its assigned duration. Rewrite naturally when
  a literal translation is too long.
- Validate apostrophes, diacritics, Cyrillic, Turkish dotted/dotless I, and
  Indonesian punctuation in the exported UTF-8 files.

## Upload and verification

- Caption tracks are uploaded with their correct BCP 47 language code and a
  human-readable name.
- Tracks are uploaded as non-draft captions with authored time codes.
- Automated caption upload requires OAuth scope
  `https://www.googleapis.com/auth/youtube.force-ssl`.
- After upload, the verifier must confirm the expected channel, `private`
  status, and all required caption languages.
- Any missing/invalid track, missing scope, failed render, or API error stops
  the run. The automation must not silently upload a partial public asset.

YouTube's caption upload endpoint and required scope are documented in the
[official captions.insert reference](https://developers.google.com/youtube/v3/docs/captions/insert).
