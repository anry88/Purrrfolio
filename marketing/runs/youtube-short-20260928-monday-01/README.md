# Monday Short — Same cat twice?

33 seconds, 1080×1920, 30 fps. This is a self-contained vertical editorial
adaptation of the verified public Sunday Pawcast
`youtube-full-20260927-sunday-01-fenrir`, specifically its unused
`duplicates-still-matter` idea. It rebuilds that mechanic with one hero card,
two-copy comparison, point counter, and a craft-pack diagram. It does not crop
or repost Sunday footage. The prior Short was a Baker detail hunt; this run uses
the `feature_explainer` family and a different visual grammar. No sequential
three-card reveal appears.

## Four concepts

Scores are hook / novelty / product truth / visual fit / readable pacing, each
out of five. The first is the Sunday-derived candidate.

| Concept | Family and grammar | Scores | Total |
| --- | --- | --- | --- |
| Same cat twice? Spare-copy crafting | feature_explainer; vertical duplicate-to-craft diagram | 5 / 5 / 5 / 5 / 5 | 25 |
| Two Cozy Home companions | card_matchup; simultaneous split choice | 4 / 4 / 5 / 5 / 5 | 23 |
| Yarn Keeper's quiet corner | cozy_ambience; single-card slow pan | 3 / 5 / 5 / 5 / 4 | 22 |
| What does Uncommon mean? | catalog_trivia; rarity label quiz | 4 / 4 / 5 / 4 / 4 | 21 |

The derived idea wins on a concrete first-frame question and a truthful
payoff. Yarn Keeper is an Uncommon Cozy Home card. The shipped craft policy
lets a player melt only a spare copy, gives 2 points for Uncommon, and creates
one pack per 15 points. The `2 / 15` display illustrates a single spare copy;
it does not imply that one card creates a pack.

## Storyboard

| Time | Scene | English-first copy |
| --- | --- | --- |
| 0–6 s | Two identical cards together | Same cat twice? A duplicate can still matter. |
| 6–13 s | Seven-second hero-card inspection | Yarn Keeper · Uncommon · Cozy Home |
| 13–21 s | Keep/craft two-copy diagram | Keep one. Use the spare. |
| 21–27 s | Uncommon point counter | Spare card → 2 points; rarity sets value. |
| 27–33 s | Pack threshold, question, handle CTA | 15 points = 1 pack. Which duplicate would you craft? Find @PurrrfolioBot in Telegram. |

Each primary scene lasts at least six seconds. The hero card is shown for
seven seconds. The opening question and closing question/CTA have at least
three seconds each. Scene changes crossfade over 12 frames. The visual assets
are repository card art, with a simple illustrative pack graphic. There is no
host image in this adaptation. The on-screen copy is English only; selectable
EN/RU/TR/ID captions use aligned authored Caption JSON and exported SRT/VTT.

## Music and rights

The instrumental is a 33-second, non-looped passage beginning at 26 seconds
of the owner-approved **Warm Fireplace Sunday** arrangement used in the source
episode. It is a developed warm acoustic piece, not the retired tone generator.
Source material: local GarageBand `Alternative Acoustic 07`, `Alternative
Acoustic 09`, and `Fireplace All` Digital Materials; arrangement source:
`marketing/video/scripts/prepare-sunday-audio.mjs`. The local `GarageBand
License Agreement.pdf`, section 2.C, allows these loops in original video
soundtracks distributed or broadcast royalty-free. Attribution is not
required. The source episode's owner-approved notes record the warm, gentle,
cheerful, optimistic mood. This edit keeps one musical passage, uses a 0.6 s
fade in and 0.8 s fade out, and contains no unchanged tiny loop.

Final rendered AAC: **−28.7 LUFS integrated, −15.5 dBFS true peak**. The
music-only Short passes the −30 to −27 LUFS target and −12 dBFS peak ceiling.

## Thumbnails and QA

Two purpose-built 1080×1920 candidates were rendered: two-card overlap with
**SAME CAT TWICE?**, and a single-card progress poster with **DUPLICATES
BECOME PACKS**. Both hooks and focal art fit centered 4:5 crop y=285–1635.
The two-card version was selected: it communicates the duplicate question
immediately and avoids making the 15-point threshold look like a single-card
result. The full video storyboard and final CTA were visually reviewed.

The standalone Remotion entrypoint is `marketing/video/src/monday/index.tsx`.
Pass it explicitly to `remotion still` or `remotion render` for reproduction;
this keeps the Short independent of other runs' uncommitted compositions.
First run `node scripts/prepare-monday-assets.mjs` from `marketing/video` to
copy the repository's Yarn Keeper card into the ignored Remotion public tree.
The owner-approved Sunday arrangement must be available at the ignored audio
path in the manifest before rendering.

Asset preparation, ESLint/TypeScript, caption export/timing validation, full
render, and ffprobe passed. The MP4 is H.264 1080×1920 at 30 fps,
33.045 seconds, with 48 kHz AAC audio. Public copy uses the organic bot handle
only and does not advertise onboarding packs. Generated media and recovery
receipts are gitignored. Publication and remote verification status should be
recorded below only after those gates pass.

## Publication

Published on 2026-09-28 as [YouTube Short](https://www.youtube.com/watch?v=hMi6XtzG68o).
The private staging gate accepted the four non-draft caption tracks and custom
thumbnail before the public visibility update. A separate remote verifier
confirmed the expected channel, public status, custom thumbnail, Gaming
category, audience and synthetic-media declarations, title, and serving
EN/RU/TR/ID caption languages. The successful combination was then appended
to content history.
