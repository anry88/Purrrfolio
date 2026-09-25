#!/usr/bin/env python3
"""Validate or execute one explicitly confirmed private YouTube test upload."""

from __future__ import annotations

import argparse
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from youtube_api import (
    CAPTION_UPLOAD_SCOPE,
    YouTubeApiError,
    configured_scopes,
    fetch_owned_channels,
    load_installed_client,
    load_json,
    load_token,
    refresh_access_token,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = Path("marketing/runs/youtube-short-001/metadata.json")
DEFAULT_TOKEN = Path(".secrets/youtube-oauth-token.json")
UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos"
CAPTIONS_UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/captions"
CONFIRMATION_PHRASE = "UPLOAD_PRIVATE_TEST"


def resolve_repository_path(raw: str) -> Path:
    path = Path(raw)
    return path if path.is_absolute() else REPOSITORY_ROOT / path


def validate_manifest(path: Path) -> tuple[dict[str, Any], Path, list[Path]]:
    manifest = load_json(path, "upload manifest")
    try:
        expected_channel_id = manifest["expected_channel_id"]
        campaign_source = manifest["campaign"]["source"]
        campaign_url = manifest["campaign"]["url"]
        video_path = resolve_repository_path(manifest["video_file"])
        snippet = manifest["snippet"]
        status = manifest["status"]
        captions = manifest["captions"]
    except (KeyError, TypeError) as error:
        raise ValueError(f"Upload manifest is missing a required field: {error}") from error

    if expected_channel_id != "UCNugnVbVAECNpMc8oWetZOg":
        raise ValueError("Upload manifest targets an unexpected YouTube channel.")
    if status.get("privacyStatus") != "private":
        raise ValueError("Trial uploader accepts privacyStatus=private only.")
    if manifest.get("notify_subscribers") is not False:
        raise ValueError("Trial uploader requires notify_subscribers=false.")
    if not campaign_source or len(campaign_source) > 64 or any(
        character not in "abcdefghijklmnopqrstuvwxyz0123456789_-"
        for character in campaign_source
    ):
        raise ValueError("Campaign source does not match [a-z0-9_-]{1,64}.")
    if f"start={campaign_source}" not in campaign_url:
        raise ValueError("Campaign URL does not contain the configured source.")
    if not isinstance(snippet.get("title"), str) or not snippet["title"].strip():
        raise ValueError("Upload manifest title is empty.")
    if campaign_url not in snippet.get("description", ""):
        raise ValueError("Upload description does not contain the campaign URL.")
    if not video_path.is_file():
        raise ValueError(f"Rendered video does not exist: {video_path}")

    caption_paths: list[Path] = []
    if not isinstance(captions, list) or not captions:
        raise ValueError("Upload manifest must contain at least one caption track.")
    for caption in captions:
        caption_path = resolve_repository_path(caption["file"])
        if not caption_path.is_file():
            raise ValueError(f"Caption file does not exist: {caption_path}")
        caption_paths.append(caption_path)

    return manifest, video_path, caption_paths


def begin_resumable_upload(
    access_token: str,
    manifest: dict[str, Any],
    video_path: Path,
) -> str:
    query = urllib.parse.urlencode(
        {
            "uploadType": "resumable",
            "part": "snippet,status",
            "notifySubscribers": "false",
        }
    )
    body = json.dumps(
        {
            "snippet": manifest["snippet"],
            "status": manifest["status"],
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        f"{UPLOAD_URL}?{query}",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=UTF-8",
            "Content-Length": str(len(body)),
            "X-Upload-Content-Type": "video/mp4",
            "X-Upload-Content-Length": str(video_path.stat().st_size),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            location = response.headers.get("Location")
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(
            f"YouTube upload session creation failed with HTTP {error.code}."
        ) from error
    except urllib.error.URLError as error:
        raise YouTubeApiError("YouTube upload session creation failed: network unavailable.") from error
    if not location:
        raise YouTubeApiError("YouTube did not return a resumable upload URL.")
    return location


def upload_video(upload_url: str, access_token: str, video_path: Path) -> dict[str, Any]:
    video = video_path.read_bytes()
    request = urllib.request.Request(
        upload_url,
        data=video,
        method="PUT",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "video/mp4",
            "Content-Length": str(len(video)),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(f"YouTube video upload failed with HTTP {error.code}.") from error
    except urllib.error.URLError as error:
        raise YouTubeApiError("YouTube video upload failed: network unavailable.") from error
    except (OSError, json.JSONDecodeError) as error:
        raise YouTubeApiError("YouTube video upload returned an unreadable response.") from error
    if not isinstance(result, dict) or not result.get("id"):
        raise YouTubeApiError("YouTube video upload returned no video id.")
    return result


def upload_caption(
    access_token: str,
    video_id: str,
    caption: dict[str, Any],
    caption_path: Path,
) -> dict[str, Any]:
    boundary = f"purrrfolio-{secrets.token_hex(16)}"
    metadata = json.dumps(
        {
            "snippet": {
                "videoId": video_id,
                "language": caption["language"],
                "name": caption["name"],
                "isDraft": False,
            }
        },
        ensure_ascii=False,
    ).encode("utf-8")
    caption_data = caption_path.read_bytes()
    body = b"".join(
        [
            f"--{boundary}\r\n".encode("ascii"),
            b"Content-Type: application/json; charset=UTF-8\r\n\r\n",
            metadata,
            b"\r\n",
            f"--{boundary}\r\n".encode("ascii"),
            b"Content-Type: application/octet-stream\r\n\r\n",
            caption_data,
            b"\r\n",
            f"--{boundary}--\r\n".encode("ascii"),
        ]
    )
    query = urllib.parse.urlencode({"uploadType": "multipart", "part": "snippet"})
    request = urllib.request.Request(
        f"{CAPTIONS_UPLOAD_URL}?{query}",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": f"multipart/related; boundary={boundary}",
            "Content-Length": str(len(body)),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(
            f"Caption upload for {caption['language']} failed with HTTP {error.code}."
        ) from error
    except urllib.error.URLError as error:
        raise YouTubeApiError(
            f"Caption upload for {caption['language']} failed: network unavailable."
        ) from error
    except (OSError, json.JSONDecodeError) as error:
        raise YouTubeApiError(
            f"Caption upload for {caption['language']} returned an unreadable response."
        ) from error
    if not isinstance(result, dict) or not result.get("id"):
        raise YouTubeApiError(
            f"Caption upload for {caption['language']} returned no caption id."
        )
    return result


def write_receipt(path: Path, receipt: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Dry-run or execute one private Purrrfolio YouTube test upload."
    )
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST, type=Path)
    parser.add_argument("--client-secret", type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    parser.add_argument(
        "--video-id",
        default="",
        help="Skip video creation and attach caption tracks to this existing private video.",
    )
    args = parser.parse_args()

    manifest_path = resolve_repository_path(str(args.manifest))
    try:
        manifest, video_path, caption_paths = validate_manifest(manifest_path)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    receipt_path = (
        REPOSITORY_ROOT
        / ".runtime/marketing"
        / f"{manifest_path.parent.name}-upload.json"
    )

    summary = {
        "mode": "execute" if args.execute else "dry-run",
        "expected_channel_id": manifest["expected_channel_id"],
        "privacy_status": manifest["status"]["privacyStatus"],
        "notify_subscribers": manifest["notify_subscribers"],
        "title": manifest["snippet"]["title"],
        "video_bytes": video_path.stat().st_size,
        "caption_tracks_prepared": len(caption_paths),
        "caption_upload_scope_required": CAPTION_UPLOAD_SCOPE,
        "campaign_source": manifest["campaign"]["source"],
    }

    if not args.execute:
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        print("Dry-run passed. No network request or channel change was made.")
        return 0

    if args.confirm != CONFIRMATION_PHRASE:
        raise SystemExit(
            f"Execution requires --confirm {CONFIRMATION_PHRASE}. No upload was attempted."
        )
    if args.client_secret is None:
        raise SystemExit("Execution requires --client-secret.")

    try:
        client = load_installed_client(args.client_secret)
        token = load_token(args.token)
        if CAPTION_UPLOAD_SCOPE not in configured_scopes(token):
            raise YouTubeApiError(
                "OAuth token lacks youtube.force-ssl; refusing partial upload without captions."
            )
        access_token = refresh_access_token(client, token)
        channels = fetch_owned_channels(access_token)
        channel_ids = {
            item.get("id")
            for item in channels.get("items", [])
            if isinstance(item, dict)
        }
        if manifest["expected_channel_id"] not in channel_ids:
            raise YouTubeApiError("OAuth token does not own the expected Purrrfolio channel.")
        if args.video_id or receipt_path.exists():
            if receipt_path.exists():
                receipt = load_json(receipt_path, "upload receipt")
                video_id = args.video_id or receipt.get("video_id", "")
                if not video_id or receipt.get("video_id") != video_id:
                    raise YouTubeApiError("Existing receipt belongs to a different video id.")
            elif args.video_id:
                video_id = args.video_id
                receipt = {
                    "video_id": video_id,
                    "privacy_status": "private",
                    "video_upload_skipped": True,
                    "caption_ids": {},
                }
        else:
            if receipt_path.exists():
                raise YouTubeApiError(
                    "A local upload receipt already exists; refusing to create a duplicate video."
                )
            upload_url = begin_resumable_upload(access_token, manifest, video_path)
            result = upload_video(upload_url, access_token, video_path)
            video_id = result["id"]
            receipt = {
                "video_id": video_id,
                "privacy_status": "private",
                "video_upload_skipped": False,
                "caption_ids": {},
            }
            write_receipt(receipt_path, receipt)

        for caption, caption_path in zip(manifest["captions"], caption_paths):
            if caption["language"] in receipt["caption_ids"]:
                continue
            caption_result = upload_caption(
                access_token,
                video_id,
                caption,
                caption_path,
            )
            receipt["caption_ids"][caption["language"]] = caption_result["id"]
            write_receipt(receipt_path, receipt)
    except (ValueError, YouTubeApiError) as error:
        raise SystemExit(str(error)) from error

    print(
        json.dumps(
            {
                "video_id": video_id,
                "privacy_status": "private",
                "caption_tracks_uploaded": len(receipt["caption_ids"]),
                "caption_languages": sorted(receipt["caption_ids"]),
                "receipt": str(receipt_path.relative_to(REPOSITORY_ROOT)),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    print("Private video and caption tracks uploaded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
