#!/usr/bin/env python3
"""Verify a public Purrrfolio upload and its subtitle tracks via YouTube API."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from youtube_api import (
    YouTubeApiError,
    fetch_captions,
    fetch_video,
    load_installed_client,
    load_json,
    load_token,
    refresh_access_token,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = REPOSITORY_ROOT / "marketing/runs/youtube-short-001/metadata.json"
DEFAULT_TOKEN = Path(".secrets/youtube-oauth-token.json")
EXPECTED_CHANNEL_ID = "UCNugnVbVAECNpMc8oWetZOg"
EXPECTED_CAPTION_LANGUAGES = {"en", "ru", "tr", "id"}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify public video metadata and EN/RU/TR/ID subtitle tracks."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST, type=Path)
    args = parser.parse_args()

    try:
        manifest_path = (
            args.manifest
            if args.manifest.is_absolute()
            else REPOSITORY_ROOT / args.manifest
        )
        receipt_path = args.receipt or (
            REPOSITORY_ROOT
            / ".runtime/marketing"
            / f"{manifest_path.parent.name}-upload.json"
        )
        receipt = load_json(receipt_path, "upload receipt")
        manifest = load_json(manifest_path, "upload manifest")
        video_id = receipt["video_id"]
        client = load_installed_client(args.client_secret)
        token = load_token(args.token)
        access_token = refresh_access_token(client, token)
        video_response = fetch_video(access_token, video_id)
        caption_response = fetch_captions(access_token, video_id)
    except (KeyError, TypeError, ValueError, YouTubeApiError) as error:
        raise SystemExit(str(error)) from error

    videos = video_response.get("items", [])
    if len(videos) != 1:
        raise SystemExit("Expected exactly one uploaded video.")
    video = videos[0]
    snippet = video.get("snippet", {})
    status = video.get("status", {})
    if snippet.get("channelId") != EXPECTED_CHANNEL_ID:
        raise SystemExit("Uploaded video belongs to an unexpected channel.")
    if status.get("privacyStatus") != "public":
        raise SystemExit("Uploaded video is not public.")
    if snippet.get("categoryId") != "20":
        raise SystemExit("Uploaded video is not in the Gaming category.")
    if status.get("selfDeclaredMadeForKids") is not False:
        raise SystemExit("Uploaded video audience declaration is not set to not made for kids.")
    if status.get("containsSyntheticMedia") is True:
        raise SystemExit("Uploaded video synthetic-media disclosure is unexpected.")
    for field, expected in (
        ("embeddable", True),
        ("publicStatsViewable", True),
        ("license", "youtube"),
    ):
        if status.get(field) != expected:
            raise SystemExit(f"Uploaded video {field} does not match the manifest policy.")
    expected_snippet = manifest.get("snippet", {})
    for field in ("title", "description", "categoryId", "defaultLanguage"):
        if snippet.get(field) != expected_snippet.get(field):
            raise SystemExit(f"Uploaded video {field} does not match the manifest.")
    if set(snippet.get("tags", [])) != set(expected_snippet.get("tags", [])):
        raise SystemExit("Uploaded video tags do not match the manifest.")
    if "https://t.me/" in snippet.get("description", ""):
        raise SystemExit("Uploaded Shorts description still contains an external Telegram URL.")

    captions = caption_response.get("items", [])
    languages = {
        item.get("snippet", {}).get("language")
        for item in captions
        if isinstance(item, dict)
    }
    missing = EXPECTED_CAPTION_LANGUAGES - languages
    if missing:
        raise SystemExit(
            f"Uploaded video is missing {len(missing)} expected caption track(s)."
        )
    for caption in captions:
        caption_snippet = caption.get("snippet", {})
        if caption_snippet.get("language") in EXPECTED_CAPTION_LANGUAGES and (
            caption_snippet.get("isDraft") is not False
            or caption_snippet.get("status") != "serving"
        ):
            raise SystemExit("An expected caption track is draft or not serving.")

    print(
        json.dumps(
            {
                "video_id": video_id,
                "channel_id": snippet.get("channelId"),
                "title": snippet.get("title"),
                "privacy_status": status.get("privacyStatus"),
                "caption_languages": sorted(languages),
                "verification_passed": True,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
