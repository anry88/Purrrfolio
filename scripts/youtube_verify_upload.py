#!/usr/bin/env python3
"""Verify a private Purrrfolio upload and its subtitle tracks via YouTube API."""

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
DEFAULT_RECEIPT = REPOSITORY_ROOT / ".runtime/marketing/youtube-short-001-upload.json"
DEFAULT_TOKEN = Path(".secrets/youtube-oauth-token.json")
EXPECTED_CHANNEL_ID = "UCNugnVbVAECNpMc8oWetZOg"
EXPECTED_CAPTION_LANGUAGES = {"en", "ru", "tr", "id"}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify private video status and EN/RU/TR/ID subtitle tracks."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--receipt", default=DEFAULT_RECEIPT, type=Path)
    args = parser.parse_args()

    try:
        receipt = load_json(args.receipt, "upload receipt")
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
    if status.get("privacyStatus") != "private":
        raise SystemExit("Uploaded video is not private.")

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
