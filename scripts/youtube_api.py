#!/usr/bin/env python3
"""Small standard-library helpers for Purrrfolio YouTube scripts."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


TOKEN_URL = "https://oauth2.googleapis.com/token"
CHANNELS_URL = "https://www.googleapis.com/youtube/v3/channels"
VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
CAPTIONS_URL = "https://www.googleapis.com/youtube/v3/captions"
REQUIRED_SCOPES = frozenset(
    {
        "https://www.googleapis.com/auth/youtube.upload",
        "https://www.googleapis.com/auth/youtube.readonly",
        "https://www.googleapis.com/auth/yt-analytics.readonly",
    }
)
CAPTION_UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
AUTHORIZATION_SCOPES = REQUIRED_SCOPES | {CAPTION_UPLOAD_SCOPE}


class YouTubeApiError(RuntimeError):
    """An API failure whose message is safe to display in a terminal."""


def load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            value = json.load(handle)
    except FileNotFoundError as error:
        raise ValueError(f"{label} file not found: {path}") from error
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Cannot read valid JSON from {label} file: {path}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{label} JSON must be an object: {path}")
    return value


def load_installed_client(path: Path) -> dict[str, Any]:
    data = load_json(path, "OAuth client")
    client = data.get("installed")
    if not isinstance(client, dict):
        raise ValueError("OAuth client JSON must contain an 'installed' object.")
    if not client.get("client_id") or not client.get("client_secret"):
        raise ValueError("OAuth client JSON is missing client_id or client_secret.")
    return client


def load_token(path: Path) -> dict[str, Any]:
    token = load_json(path, "OAuth token")
    if not token.get("refresh_token"):
        raise ValueError("OAuth token JSON does not contain refresh_token.")
    return token


def configured_scopes(token: dict[str, Any]) -> set[str]:
    requested = token.get("scopes_requested", [])
    scopes = set(requested) if isinstance(requested, list) else set()
    granted = token.get("scope")
    if isinstance(granted, str):
        scopes.update(granted.split())
    return scopes


def refresh_access_token(
    client: dict[str, Any], token: dict[str, Any]
) -> str:
    payload = urllib.parse.urlencode(
        {
            "client_id": client["client_id"],
            "client_secret": client["client_secret"],
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
    response = _open_json(request, "OAuth token refresh")
    access_token = response.get("access_token")
    if not isinstance(access_token, str) or not access_token:
        raise YouTubeApiError("OAuth token refresh returned no access token.")
    return access_token


def fetch_owned_channels(access_token: str) -> dict[str, Any]:
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
    return _open_json(request, "YouTube channel lookup")


def fetch_video(access_token: str, video_id: str) -> dict[str, Any]:
    query = urllib.parse.urlencode(
        {
            "part": "snippet,status,contentDetails",
            "id": video_id,
        }
    )
    request = urllib.request.Request(
        f"{VIDEOS_URL}?{query}",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    return _open_json(request, "YouTube video lookup")


def fetch_captions(access_token: str, video_id: str) -> dict[str, Any]:
    query = urllib.parse.urlencode(
        {
            "part": "snippet",
            "videoId": video_id,
        }
    )
    request = urllib.request.Request(
        f"{CAPTIONS_URL}?{query}",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    return _open_json(request, "YouTube caption lookup")


def _open_json(request: urllib.request.Request, action: str) -> dict[str, Any]:
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            value = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(f"{action} failed with HTTP {error.code}.") from error
    except urllib.error.URLError as error:
        raise YouTubeApiError(f"{action} failed: network unavailable.") from error
    except (OSError, json.JSONDecodeError) as error:
        raise YouTubeApiError(f"{action} returned an unreadable response.") from error
    if not isinstance(value, dict):
        raise YouTubeApiError(f"{action} returned an unexpected response.")
    return value
