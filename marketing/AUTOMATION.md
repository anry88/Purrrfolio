# YouTube Marketing Automation

## Current operating mode

The scheduled pipeline creates one English-first YouTube Short per run, renders
it locally, generates selectable EN/RU/TR/ID subtitle tracks, uploads the video
as a private staging item, attaches every subtitle track, then publishes it as
`public` and verifies the remote result. The user authorized recurring public
publication on 2026-09-25. Subscriber notifications are enabled.

## Baseline schedule

The schedule uses the operator's local `Europe/Belgrade` timezone. The hours
are approximate local equivalents of the selected English-speaking audience
windows; exact DST alignment is intentionally not required.

| Day | Local time | Intent |
| --- | --- | --- |
| Monday | 20:00 | Weekday pre-evening US window |
| Wednesday | 21:00 | Midweek afternoon US window |
| Friday | 22:00 | Friday afternoon/evening US ramp |
| Saturday | 16:00 | Weekend morning US viewing |
| Sunday | 17:00 | Weekend late-morning US viewing |

These are discovery baselines, not permanent truths. YouTube states that the
Audience report shows when a channel's viewers are online over the previous 28
days and can be used to plan upload timing. Once the channel has enough data,
the pipeline should review that report every four weeks and propose schedule
changes rather than changing times silently. See [YouTube Audience
help](https://support.google.com/youtube/answer/9314416?hl=en).

The initial windows also reflect current broad benchmarks: weekday afternoon
publishing and weekend 09:00–11:00 windows, while recent large-scale analysis
finds a modest weekend advantage and emphasizes channel-specific data over a
universal hour. See [SocialPilot's 2026 YouTube timing
study](https://www.socialpilot.co/insights/best-time-to-post-on-youtube),
[Sprinklr's weekend guidance](https://www.sprinklr.com/blog/best-times-to-post-on-social-media/),
and [vidIQ's 40M-video analysis](https://vidiq.com/blog/post/best-time-publish-video-youtube/).

## Per-run workflow

1. Read `AGENTS.md`, the product documentation, this file, and
   `CREATIVE_VARIETY.md` and `SUBTITLE_REQUIREMENTS.md`.
2. Refuse to run if secrets would be printed/committed or unrelated working-tree
   changes would be overwritten.
3. Generate and score four distinct concepts. Select a concept family, visual
   grammar, card count, and card combination not blocked by content history.
4. Create a short English creative brief, storyboard, truthful hook, thumbnail
   hook, and CTA. Let the concept determine the duration.
5. Create a unique internal run source matching `[a-z0-9_-]{1,64}`. Keep it in
   the manifest and content history for run identity only. Do not expose its
   Telegram deep link, campaign code, or `/start` payload in the video,
   subtitles, title, or description.
6. Build a 1080×1920, 30 fps Remotion composition. The minimum duration is 15
   seconds, but 20–40 seconds is the normal range and explainers may reach 60.
   Do not reuse the previous run's scene structure.
7. Add a curated, professionally produced instrumental background track and
   a purpose-built 1080×1920 thumbnail. Keep burned-in copy
   English-only. Generate canonical Caption JSON plus SRT
   and WebVTT for EN/RU/TR/ID.
8. Run asset preparation, TypeScript/lint checks, caption validation, storyboard
   and thumbnail contact-sheet QA, the full render, audio loudness analysis,
   `ffprobe`, and the local upload dry-run.
9. Upload exactly one video as private staging, attach all four caption tracks,
   upload the custom thumbnail, and only then switch it to `public`. Use the
   per-run recovery receipt to
   prevent duplicate videos. Subscriber notifications are enabled. Always pass
   the new run explicitly with `scripts/youtube_publish.py --manifest
   marketing/runs/<run_id>/metadata.json`; never rely on the pilot default.
10. Verify channel id, public status, custom thumbnail, category, audience
    declaration, synthetic media disclosure, title, and caption languages,
    passing the same `--manifest` to `scripts/youtube_verify_upload.py`.
11. Append the successful campaign/card combination to content history. Store
    no OAuth values, client secrets, access tokens, or refresh tokens in Git.
12. Report the video id, creative summary, campaign source, verification result,
    and any failure requiring user action.

## Description and metadata gate

- Start with a unique plain-English sentence containing one or two concrete
  search phrases that accurately describe the video, such as "cozy cat cards"
  and "Telegram collectible card game". Do not keyword-stuff.
- Shorts description URLs are non-clickable. Do not include `https://t.me/`,
  another external URL, a campaign code, or a `/start` payload. Use only the
  simple organic CTA `Find @PurrrfolioBot in Telegram`.
- Do not advertise the three starter packs in future Shorts. Every new player
  already receives them, so this is product onboarding rather than a campaign
  offer. The first pilot is a documented legacy exception and remains unchanged.
- Set Gaming category `20`, English metadata, public visibility, embeddable and
  public statistics enabled, standard YouTube license, `madeForKids=false`, and
  `containsSyntheticMedia=false`. The latter is correct for the current clearly
  stylized card art; reassess it if a future creative uses realistic synthetic
  people, places, or events.
- A clickable Telegram destination should be placed in the channel profile, not
  the Shorts description. Enabling that link is a one-time YouTube Studio
  advanced-feature verification step and cannot be automated through the Data
  API.

## Music gate

- Every future Short must use curated music matched to the concept. The old
  procedural generator is prohibited for new public uploads because it produces
  overly simple, repetitive timbres.
- Use a YouTube Audio Library track explicitly marked as requiring no
  attribution, curated CC0 audio, or a specific owner-approved original track.
  Never use trending/ripped audio, unclear terms, or likely Content ID music.
- Record the track title, exact source reference, license, source type, and
  `attribution_required=false` in the run manifest. Keep license evidence in
  the run notes; do not add attribution URLs to the Shorts description.
- Check duration before rendering, edit on musical phrases, and apply 0.4–1.0
  second fades. Do not repeat a tiny loop unchanged. Target −30 to −27 LUFS
  integrated and true peak below −12 dBFS.
- Treat music mood as a release gate. Prefer warm, cheerful, light, playful,
  optimistic acoustic or softly produced tracks. Reject sources or tags that
  suggest mystical, suspenseful, ominous, dramatic, dark, horror, anxious,
  tense, or melancholic music. Avoid unresolved drones and prominent minor-key
  tension. Record positive mood evidence from the source page in the manifest.
- Publication is automatic after the licensing, mood, render, subtitle,
  thumbnail, and technical gates pass. Do not pause for per-video owner
  listening approval; use feedback to improve the next run.
- The upload preflight must detect an audio stream with `ffprobe`. The first
  silent pilot is the only legacy exception.

## Thumbnail gate

- Render at least two truthful 1080×1920 thumbnail candidates using different
  layouts from `CREATIVE_VARIETY.md`, choose one, and record the choice.
- Keep the focal card/face and 2–5 word hook inside the centered 4:5 safe crop.
- Upload the selected thumbnail with the YouTube Data API while the video is
  still private. If YouTube rejects it, do not publish the default thumbnail.

## Quality and safety gates

- Do not upload when a title, CTA, translation, render, or metadata claim is
  unsupported by shipped product behavior.
- Do not reuse a three-card combination or campaign source.
- Do not scrape, comment, subscribe, follow, direct-message, or automate
  engagement.
- Do not use copyrighted music or third-party visual assets without documented
  permission. A silent render is preferable to unlicensed audio.
- One scheduled run creates at most one remote video.
- On any partial upload, resume from the local receipt; never create a duplicate
  video merely to retry captions.
- If YouTube keeps an API-uploaded video private, stop without duplicating it
  and report that the Google API project needs the YouTube API compliance audit.

## Credentials

Scheduled runs expect local owner-only files:

```text
.secrets/youtube-client-secret.json
.secrets/youtube-oauth-token.json
```

Both paths are gitignored. The token must include upload, read-only, analytics,
and `youtube.force-ssl` scopes.
