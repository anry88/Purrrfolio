# Creative Variety for YouTube Shorts

## Goal

Each Short must feel like a new idea, not a reskin of the previous upload. The
creative concept determines the duration and scene rhythm; the pipeline must
never force every story into a 15-second five-scene template.

## Concept selection

Before writing code, generate four genuinely different candidates and score
each from 1–5 for hook strength, novelty versus content history, product truth,
visual fit, and readable pacing. Save the candidates and score in the run
README, then produce the highest-scoring viable idea.

Choose from these families and add new families when the catalog supports them:

| Family | Typical shape | Suggested duration |
| --- | --- | --- |
| `card_micro_story` | One hero card, setup, tiny story, payoff | 20–35 s |
| `guess_the_rarity` | Detail clues, pause to guess, reveal | 20–30 s |
| `collection_tour` | One themed world with slow pans and 2–4 cards | 25–45 s |
| `detail_hunt` | Find a visual detail before the answer appears | 18–30 s |
| `pack_suspense` | Pack setup, tension, one or two meaningful reveals | 18–30 s |
| `card_matchup` | Split-screen comparison and viewer choice | 22–40 s |
| `choose_your_favorite` | Contrasting personalities, one clear question | 20–35 s |
| `cozy_ambience` | Calm world-building around one collection | 25–45 s |
| `feature_explainer` | Crafting, trading, gallery, or sharing walkthrough | 30–55 s |
| `catalog_trivia` | A truthful fact, pattern, or themed mini-quiz | 20–40 s |

Do not repeat the previous concept family or visual grammar. Do not use a
sequential three-card reveal if it appeared in any of the previous four runs.
Vary card count: one hero card is often stronger than three equal reveals.

## Duration and pacing

- Fifteen seconds is the hard minimum, not a target. Most concepts should land
  naturally between 20 and 40 seconds; explainers may run to 60 seconds.
- Derive `durationInFrames` from the approved storyboard and reading time.
- A primary scene should normally remain visible for at least 2.5 seconds.
- Give a hero card at least 4 seconds when the viewer is expected to inspect it.
- Allow at least 3 seconds for a question or CTA and a visible pause before its
  answer. Use 8–18 frame transitions that support rather than hide the content.
- Do not cut merely to hit a round duration. If copy cannot be read comfortably
  on the first viewing, lengthen the scene or remove copy.

## Music direction

The procedural tone generator used for the 2026-09-25 Friday upload is a legacy
experiment and must not be used again for public content.

- Use a curated, professionally produced instrumental from YouTube Audio
  Library with no attribution requirement, curated CC0 music, or a specific
  owner-approved original track.
- Prefer warm acoustic, soft lo-fi, gentle piano/guitar, cheerful light pop,
  or playful organic textures. Reject chip-tune, toy-like plucks, pure-tone
  synthesis, repetitive eight-note loops, shrill leads, and constant percussion.
- Reject mystical, suspenseful, ominous, dramatic, dark, horror, anxious,
  tense, or melancholic cues. Default to warm, cheerful, playful, optimistic
  tracks with a resolved, friendly feel.
- The track should have musical development rather than one short loop repeated
  unchanged. Trim on musical phrases and use 0.4–1.0 second fades.
- Mix background music around −30 to −27 LUFS integrated with true peak below
  −12 dBFS. Lower it further under narration or important sound effects.
- Store the exact title, source, license evidence, positive mood evidence, and
  mix measurements in the run README and manifest. If the rights or technical
  quality are uncertain, do not publish.
- Do not require per-video owner approval. Publish automatically once license,
  documented positive mood, loudness, render, captions, thumbnail, and remote
  verification gates pass; improve later runs from feedback.

## Thumbnail direction

Every future Short requires a custom 1080×1920 (9:16) PNG or JPEG thumbnail.
Keep the essential face/card and text within the centered 4:5 safe crop because
some YouTube surfaces crop vertical thumbnails.

Generate at least two thumbnail candidates and choose the stronger one. Rotate
layouts instead of defaulting to a video frame:

- extreme card close-up with a 2–4 word curiosity hook;
- silhouette or blurred-detail guessing prompt;
- two-card split-screen matchup;
- tilted card fan with one highlighted rarity;
- collection-poster composition with a strong central hero;
- before/after or closed-pack/reveal contrast.

Use high contrast, one focal point, large readable type, and no more than five
words. The hook must be truthful and complement rather than repeat the title.
Do not publish a generic frame grab, dense copy, fake rarity, or misleading
clickbait.
