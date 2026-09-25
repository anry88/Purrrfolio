# YouTube Marketing Automation

## Current operating mode

The scheduled pipeline creates one English-first YouTube Short per run, renders
it locally, generates selectable EN/RU/TR/ID subtitle tracks, uploads the video
as `private`, and verifies the remote result.

Public/unlisted publication and `publishAt` scheduling are disabled until the
user explicitly authorizes public autopublishing. Subscriber notifications are
always disabled for private test uploads.

## Baseline schedule

The schedule uses `America/New_York` so it follows US Eastern daylight-saving
changes and targets the largest initial English-speaking test segment.

| Day | Time (ET) | Intent |
| --- | --- | --- |
| Monday | 14:00 | Weekday pre-evening window |
| Wednesday | 15:00 | Midweek afternoon Shorts window |
| Friday | 16:00 | Friday afternoon/evening ramp |
| Saturday | 10:00 | Weekend morning viewing |
| Sunday | 11:00 | Weekend late-morning viewing |

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
   `SUBTITLE_REQUIREMENTS.md`.
2. Refuse to run if secrets would be printed/committed or unrelated working-tree
   changes would be overwritten.
3. Select a new three-card combination not already present in
   `state/content-history.json`.
4. Create a short English creative brief with one honest hook and one CTA.
5. Create a unique registration source matching `[a-z0-9_-]{1,64}` and include
   its Telegram deep link in metadata.
6. Build or adapt a 1080×1920, 30 fps, 12–20 second Remotion composition.
7. Keep burned-in copy English-only. Generate canonical Caption JSON plus SRT
   and WebVTT for EN/RU/TR/ID.
8. Run asset preparation, TypeScript/lint checks, caption validation, still QA,
   the full render, `ffprobe`, and the local upload dry-run.
9. Upload exactly one video as `private`, with notifications disabled, and
   attach all four caption tracks. Use the per-run recovery receipt to prevent
   duplicate videos.
10. Verify channel id, private status, title, and caption languages through the
    YouTube API.
11. Append the successful campaign/card combination to content history. Store
    no OAuth values, client secrets, access tokens, or refresh tokens in Git.
12. Report the video id, creative summary, campaign source, verification result,
    and any failure requiring user action.

## Quality and safety gates

- Do not publish, schedule public visibility, or switch to `unlisted`.
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

## Credentials

Scheduled runs expect local owner-only files:

```text
.secrets/youtube-client-secret.json
.secrets/youtube-oauth-token.json
```

Both paths are gitignored. The token must include upload, read-only, analytics,
and `youtube.force-ssl` scopes.
