#!/usr/bin/env python3
"""Show and validate the YouTube channel owned by the saved OAuth token."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from youtube_api import (
    YouTubeApiError,
    fetch_owned_channels,
    load_installed_client,
    load_json,
    load_token,
    refresh_access_token,
)


DEFAULT_CONFIG = Path("marketing/config/youtube.json")
DEFAULT_TOKEN = Path(".secrets/youtube-oauth-token.json")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Show which YouTube channel is authorized by the OAuth token."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--config", default=DEFAULT_CONFIG, type=Path)
    args = parser.parse_args()

    try:
        config = load_json(args.config, "YouTube config")
        expected_id = config["channel"]["expected_id"]
        client = load_installed_client(args.client_secret)
        token = load_token(args.token)
        access_token = refresh_access_token(client, token)
        channels = fetch_owned_channels(access_token)
    except (KeyError, TypeError, ValueError, YouTubeApiError) as error:
        raise SystemExit(str(error)) from error

    items = channels.get("items", [])
    if not isinstance(items, list) or not items:
        raise SystemExit("No YouTube channel was returned for this OAuth token.")

    matches = [item for item in items if item.get("id") == expected_id]
    if not matches:
        returned_ids = [item.get("id") for item in items if item.get("id")]
        raise SystemExit(
            "Authorized channel does not match the configured Purrrfolio channel "
            f"(returned {len(returned_ids)} channel(s))."
        )

    item = matches[0]
    snippet = item.get("snippet", {})
    statistics = item.get("statistics", {})
    status = item.get("status", {})
    print(
        json.dumps(
            {
                "channel_id": item.get("id"),
                "title": snippet.get("title"),
                "custom_url": snippet.get("customUrl"),
                "privacy_status": status.get("privacyStatus"),
                "subscriber_count": statistics.get("subscriberCount"),
                "video_count": statistics.get("videoCount"),
                "view_count": statistics.get("viewCount"),
                "matches_expected_channel": True,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
