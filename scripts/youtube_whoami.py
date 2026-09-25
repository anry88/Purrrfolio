#!/usr/bin/env python3
import argparse
import json
import urllib.parse
import urllib.request
from pathlib import Path


TOKEN_URL = "https://oauth2.googleapis.com/token"
CHANNELS_URL = "https://www.googleapis.com/youtube/v3/channels"


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def refresh_access_token(client, token):
    payload = urllib.parse.urlencode(
        {
            "client_id": client["client_id"],
            "client_secret": client.get("client_secret", ""),
            "refresh_token": token["refresh_token"],
            "grant_type": "refresh_token",
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        TOKEN_URL,
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))["access_token"]


def fetch_channels(access_token):
    query = urllib.parse.urlencode(
        {
            "part": "snippet,statistics,status",
            "mine": "true",
        }
    )
    request = urllib.request.Request(
        f"{CHANNELS_URL}?{query}",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(
        description="Show which YouTube channel is authorized by the saved OAuth token."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument(
        "--token",
        default=Path(".secrets/youtube-oauth-token.json"),
        type=Path,
    )
    args = parser.parse_args()

    client_data = load_json(args.client_secret)
    client = client_data.get("installed")
    if not client:
        raise SystemExit("OAuth JSON must contain an 'installed' client.")

    token = load_json(args.token)
    if "refresh_token" not in token:
        raise SystemExit("Token file does not contain refresh_token.")

    access_token = refresh_access_token(client, token)
    channels = fetch_channels(access_token)
    items = channels.get("items", [])

    if not items:
        print("No YouTube channel returned for this OAuth token.")
        return

    for item in items:
        snippet = item.get("snippet", {})
        statistics = item.get("statistics", {})
        status = item.get("status", {})
        print(json.dumps(
            {
                "channel_id": item.get("id"),
                "title": snippet.get("title"),
                "custom_url": snippet.get("customUrl"),
                "privacy_status": status.get("privacyStatus"),
                "subscriber_count": statistics.get("subscriberCount"),
                "video_count": statistics.get("videoCount"),
                "view_count": statistics.get("viewCount"),
            },
            ensure_ascii=False,
            indent=2,
        ))


if __name__ == "__main__":
    main()
