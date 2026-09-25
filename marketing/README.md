# Purrrfolio Marketing Automation

This directory is the repository-owned home for organic marketing automation.
It starts with YouTube Shorts and is designed to add more channels without
mixing credentials, generated media, or raw analytics into the application.

Operational requirements:

- [YouTube automation and schedule](AUTOMATION.md)
- [Subtitle requirements](SUBTITLE_REQUIREMENTS.md)

## Safety boundaries

- OAuth client files, refresh tokens, access tokens, cookies, and platform
  credentials belong in `.secrets/` or another local path outside Git.
- Scripts must never print tokens or client secrets.
- The default YouTube test visibility is `private`.
- Public publishing, bulk actions, comments, follows, direct messages, and any
  other audience-facing automation require explicit user approval immediately
  before the action.
- Automation must use supported platform APIs and respect rate limits and
  platform rules. It must not imitate users, spam, or evade moderation.

## Layout

```text
marketing/
  config/       committed, non-secret channel and policy configuration
  templates/    committed metadata and copy templates
  generated/    local rendered media; gitignored
  metrics/raw/  local API exports; gitignored
scripts/
  youtube_api.py              shared OAuth/API helpers
  youtube_oauth_authorize.py  one-time local OAuth authorization
  youtube_readiness.py        offline credential/scope/policy validation
  youtube_whoami.py           authenticated channel identity validation
  youtube_upload_private.py   dry-run/confirmed private test uploader
  youtube_verify_upload.py    read-only privacy/caption verification
```

Future channel adapters should follow the same split: committed config and
templates, local secrets, local generated assets, and explicit publish gates.

## YouTube setup

Use a Google OAuth Desktop client with YouTube Data API v3 and YouTube
Analytics API enabled. Keep both files local:

```text
.secrets/youtube-client-secret.json
.secrets/youtube-oauth-token.json
```

If authorization is needed or renewed:

```bash
python3 scripts/youtube_oauth_authorize.py \
  --client-secret .secrets/youtube-client-secret.json
```

Run the offline readiness check before any network request:

```bash
python3 scripts/youtube_readiness.py \
  --client-secret .secrets/youtube-client-secret.json \
  --token .secrets/youtube-oauth-token.json
```

Then verify the authenticated channel. This reads channel metadata only and
fails if it is not the configured Purrrfolio channel:

```bash
python3 scripts/youtube_whoami.py \
  --client-secret .secrets/youtube-client-secret.json \
  --token .secrets/youtube-oauth-token.json
```

The expected identity is configured in
[`config/youtube.json`](config/youtube.json). A private test upload is the next
remote-write milestone; no upload is performed by the current scripts.

Validate a prepared run without using the network:

```bash
python3 scripts/youtube_upload_private.py
```

The script refuses non-private visibility, subscriber notifications, an
unexpected channel id, missing campaign attribution, missing media, or missing
caption files. Its `--execute` path also requires the literal confirmation
phrase shown by `--help`; do not run that path without current user approval.

Uploading selectable subtitle tracks through the YouTube Data API requires the
additional `youtube.force-ssl` scope. The authorization script requests it for
new tokens; an older token remains usable for video uploads but must be
reauthorized before automated caption-track uploads.

## Campaign links

Purrrfolio stores a normalized Telegram `/start` payload as
`users.registration_source`. Marketing sources must match
`[a-z0-9_-]{1,64}`. A YouTube Short can use a link such as:

```text
https://t.me/PurrrfolioBot?start=yt_short_20260925_sleepy
```

Use one stable source per creative or campaign. Never put a player referral id
in a public campaign template.

## Next increments

1. Add a resumable uploader locked to `private`/`unlisted` test visibility.
2. Add metadata rendering and validation from the committed template.
3. Pull YouTube Analytics into `marketing/metrics/raw/` and produce a small,
   non-secret comparison report.
4. Add a tracked content calendar and RU/EN creative variants.
5. Add new platforms one adapter at a time after reviewing their current API
   and automation policies.
