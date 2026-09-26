# Baker paw-print hunt — Saturday Short

32 seconds, 1080×1920, 30 fps. One hero card: Baker (Epic), from the shipped Professions catalog. A visual detail hunt gives viewers eight seconds to inspect the illustration before three restrained answer annotations, full-card identification, and an organic Telegram CTA. No guaranteed pack result or reward offer.

## Concept candidates

Scores 1–5: hook / novelty against history / product truth / visual fit / readable pacing.

| Candidate | Family; visual grammar | Cards | Duration | Scores | Total |
| --- | --- | --- | --- | --- | --- |
| Find Baker's paw prints | detail_hunt; hero_inspection_with_annotations | Baker | 32 s | 5 / 5 / 5 / 5 / 5 | 25 |
| Pick your kitchen sidekick | choose_your_favorite; simultaneous_personality_triptych | Chef, Baker, Barista | 30 s | 4 / 5 / 5 / 4 / 5 | 23 |
| Guess the space cat's rarity | guess_the_rarity; cropped_clues_then_full_card | Astronaut | 28 s | 4 / 5 / 5 / 4 / 4 | 22 |
| A tiny baker's big morning | card_micro_story; slow_pan_storybook | Baker | 26 s | 3 / 5 / 4 / 5 / 4 | 21 |

The detail hunt wins on a concrete question and visible payoff without invented gameplay. It changes both family and grammar from Friday and from the two recent sequential three-card reveals. The current policy allows one hero card, so this run uses one unused card rather than the superseded three-card requirement. Internal run identity is reserved in content-history pending_runs until publication succeeds.

## Storyboard

| Time | English copy | Visual purpose |
| --- | --- | --- |
| 0–4 s | Can you spot the paw prints? | Invite inspection of the original bakery illustration |
| 4–12 s | Take a closer look. | Eight-second search pause; gentle pan, no answers |
| 12–15 s | On the hat. | Ring the hat's paw badge |
| 15–18 s | On the apron. | Ring the apron paw |
| 18–21 s | On the jars. | Ring a background jar paw |
| 21–26 s | Baker · Epic / Professions collection | Complete card, honest catalog identity |
| 26–32 s | Collect cat cards in Telegram. / Find @PurrrfolioBot. | Six-second organic CTA |

These are details on one illustration, not separate card reveals. Repository card art is unchanged. English-only burned-in copy; no voiceover. Authored EN/RU/TR/ID Caption JSON uses identical timing and reproducible SRT/WebVTT exports.

## Music and rights

Curated recording: **bright song**, by **haruta**. The creator's [asset page](https://opengameart.org/content/bright-song) declares CC0, describes it as a very bright melody, and includes listener feedback calling it uplifting and catchy. It links the exact [MP3](https://opengameart.org/sites/default/files/bright_song.mp3) and [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/). Evidence checked 2026-09-26. Approved type: cc0_curated; attribution_required=false. Pre-existing produced recording, not the prohibited procedural generator.

Source: 127.5559 s. Opening 32 s used once with 0.6 s entry and 0.8 s exit fades. No tiny repeated loop. Excerpt is normalized and reduced to 0.70 in composition. The final AAC mix measures **−28.4 LUFS integrated and −13.8 dBFS true peak**, passing the quiet background-music gates. Exact source checksum/edit are in manifest. Music, WAV, thumbnails, stills and final MP4 are ignored local media, never committed. The owner rejected the previous anxious cue and explicitly authorized automatic publication with a happier replacement; per-video listening approval is not required.

## Thumbnail candidates

1. Extreme close-up: **Spot the paw prints**. Face and hat badge, restrained answer ring, high-contrast type.
2. Collection poster: **Hidden in plain sight**. Tilted complete card and bold curiosity ribbon.

Purpose-built 1080×1920 compositions; essential face and hooks within centered 4:5 crop y=285–1635. Both full thumbnails and the centered 4:5 crops passed visual review. Candidate 1 selected: the close-up has a larger face, clearer hat detail, and a shorter high-contrast hook than the poster. The thumbnail contact sheet is a local ignored artifact.

## QA and publication

Asset preparation, ESLint/TypeScript, identical caption timings, UTF-8 SRT/WebVTT exports, storyboard contact sheet, thumbnail comparison, complete render and ffprobe passed. The rendered MP4 is H.264 1080×1920 at 30 fps, 32.042667 s, with AAC audio. Visual QA caught and fixed a literal newline escape in the CTA, then corrected the apron and jar annotation positions against the source art. Public copy was checked for source/payload/URL leakage and offers. Offline credential scope/permissions checks passed without exposing OAuth values.

No remote video or per-run receipt existed before this replacement. Existing pilot and Friday run files are unchanged. The publisher verifies that YouTube confirms the custom thumbnail and four non-draft serving caption tracks while private, before switching public. Mock checks confirmed valid staging passes and missing thumbnail/non-serving captions block.

Use this run's explicit manifest and recovery receipt; never alter legacy runs or duplicate an upload. No external URL, source code, campaign payload or starter-pack offer in public copy. Stylized art has containsSyntheticMedia=false under current policy. Required metadata comes from the current channel config.


## Reproduction and safe continuation

From the repository root, download the exact manifest source reference to the ignored music path, then prepare the excerpt:

```sh
ffmpeg -i marketing/video/public/audio/generated/bright-song-haruta.mp3 -t 32 -af 'afade=t=in:d=0.6,afade=t=out:st=31.2:d=0.8,loudnorm=I=-28.5:TP=-9:LRA=8' -ar 48000 marketing/video/public/audio/generated/youtube-short-20260926-saturday-01.wav
```

The composition applies volume=0.70 to this already normalized WAV. From marketing/video, run prepare:assets, the explicit run caption exporter, lint, both registered thumbnail compositions and PurrrfolioBakerSaturday20260926 render. Never infer final mix quality from the volume multiplier; measure the MP4. All generated media remain ignored.

Rerun the explicit dry-run, then execute the authorized publishing command and verifier with this manifest. Move the pending history entry to successful runs only after remote verification. A failed thumbnail/public-visibility gate must leave the existing remote video private and reuse its receipt on any continuation.
