#!/usr/bin/env python3
"""Validate or execute one authorized Purrrfolio YouTube publication."""

from __future__ import annotations

import argparse
import json
import re
import secrets
import shutil
import subprocess
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
    fetch_video,
    fetch_captions,
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
THUMBNAILS_UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/thumbnails/set"
CONFIRMATION_PHRASE = "PUBLISH_PUBLIC"
LEGACY_RUN_IDS = {
    "youtube-short-001",
    "youtube-short-20260925-friday-01",
}


def media_probe(path: Path, entries: str, select_streams: str | None = None) -> dict[str, Any]:
    ffprobe = shutil.which("ffprobe")
    if ffprobe is None:
        raise ValueError("ffprobe is required for media validation.")
    command = [ffprobe, "-v", "error"]
    if select_streams:
        command.extend(["-select_streams", select_streams])
    command.extend(["-show_entries", entries, "-of", "json", str(path)])
    result = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )
    try:
        value = json.loads(result.stdout) if result.returncode == 0 else {}
    except json.JSONDecodeError:
        value = {}
    if not isinstance(value, dict):
        return {}
    return value


def validate_creative_policy(manifest: dict[str, Any], video_path: Path, run_id: str) -> None:
    if run_id in LEGACY_RUN_IDS:
        return
    creative = manifest.get("creative")
    if not isinstance(creative, dict):
        raise ValueError("Future runs must document manifest.creative.")
    for field in ("concept_family", "visual_grammar", "hook_style"):
        if not isinstance(creative.get(field), str) or not creative[field].strip():
            raise ValueError(f"manifest.creative.{field} must be documented.")
    try:
        declared_duration = float(creative["duration_seconds"])
        card_count = int(creative["card_count"])
        candidate_count = int(creative["candidate_count"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Creative duration and card count must be numeric.") from error
    if not 15.0 <= declared_duration <= 60.0:
        raise ValueError("Future Shorts must be 15–60 seconds; 15 seconds is only the minimum.")
    if card_count < 1:
        raise ValueError("A creative must use at least one card.")
    if candidate_count < 4:
        raise ValueError("Each run must evaluate at least four creative candidates.")
    history = load_json(
        REPOSITORY_ROOT / "marketing/state/content-history.json",
        "content history",
    )
    previous_runs = history.get("runs", [])
    if not isinstance(previous_runs, list):
        raise ValueError("Content history runs must be a list.")
    if previous_runs:
        previous = previous_runs[-1]
        if not isinstance(previous, dict):
            raise ValueError("Content history entries must be objects.")
        if previous.get("concept_family") == creative["concept_family"]:
            raise ValueError("Creative concept family repeats the immediately previous run.")
        if previous.get("visual_grammar") == creative["visual_grammar"]:
            raise ValueError("Creative visual grammar repeats the immediately previous run.")
    if creative["visual_grammar"] == "sequential_three_card_reveal" and any(
        item.get("visual_grammar") == "sequential_three_card_reveal"
        for item in previous_runs[-4:]
        if isinstance(item, dict)
    ):
        raise ValueError("Sequential three-card reveal is still inside its four-run cooldown.")
    probe = media_probe(video_path, "format=duration")
    try:
        actual_duration = float(probe["format"]["duration"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Cannot verify rendered video duration.") from error
    if actual_duration < 15.0 or actual_duration > 60.5:
        raise ValueError("Rendered future Short is outside the 15–60 second policy.")
    if abs(actual_duration - declared_duration) > 1.0:
        raise ValueError("Rendered duration does not match manifest.creative.duration_seconds.")


def validate_audio_policy(manifest: dict[str, Any], video_path: Path, run_id: str) -> None:
    policy = manifest.get("audio_policy", "legacy_silent_pilot")
    if policy == "legacy_silent_pilot":
        if run_id != "youtube-short-001":
            raise ValueError("New runs must use audio_policy=required_cleared_instrumental.")
        return
    if policy != "required_cleared_instrumental":
        raise ValueError("Unknown audio_policy in upload manifest.")

    audio = manifest.get("audio")
    if not isinstance(audio, dict):
        raise ValueError("Future runs must document their music in manifest.audio.")
    allowed_sources = {
        "owner_approved_original",
        "cc0_curated",
        "youtube_audio_library_no_attribution",
    }
    if (
        run_id == "youtube-short-20260925-friday-01"
        and audio.get("source_type") == "original"
    ):
        allowed_sources.add("original")
    if audio.get("source_type") not in allowed_sources:
        raise ValueError("Music source is not an approved attribution-free source type.")
    if run_id not in LEGACY_RUN_IDS and "generate_short_music.py" in str(
        audio.get("source_reference", "")
    ):
        raise ValueError("The legacy procedural music generator is prohibited for future uploads.")
    if audio.get("attribution_required") is not False:
        raise ValueError("Automated Shorts require music that needs no public attribution.")
    for field in ("title", "source_reference", "license"):
        if not isinstance(audio.get(field), str) or not audio[field].strip():
            raise ValueError(f"manifest.audio.{field} must be documented.")

    probe = media_probe(video_path, "stream=codec_type", "a:0")
    streams = probe.get("streams", []) if isinstance(probe, dict) else []
    if not any(stream.get("codec_type") == "audio" for stream in streams):
        raise ValueError("Rendered future Short does not contain an audio stream.")
    if run_id in LEGACY_RUN_IDS:
        return

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise ValueError("ffmpeg is required for perceptual-level audio validation.")
    loudness = subprocess.run(
        [
            ffmpeg,
            "-hide_banner",
            "-nostats",
            "-i",
            str(video_path),
            "-filter:a",
            "ebur128=peak=true",
            "-f",
            "null",
            "-",
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=120,
    )
    integrated_matches = re.findall(r"I:\s*(-?\d+(?:\.\d+)?) LUFS", loudness.stderr)
    peak_matches = re.findall(r"Peak:\s*(-?\d+(?:\.\d+)?) dBFS", loudness.stderr)
    if not integrated_matches or not peak_matches:
        raise ValueError("Cannot measure final audio loudness and true peak.")
    integrated_lufs = float(integrated_matches[-1])
    true_peak_dbfs = float(peak_matches[-1])
    if not -30.5 <= integrated_lufs <= -26.5:
        raise ValueError(
            f"Final music loudness {integrated_lufs:.1f} LUFS is outside the quiet -30 to -27 LUFS target."
        )
    if true_peak_dbfs > -12.0:
        raise ValueError(
            f"Final music true peak {true_peak_dbfs:.1f} dBFS exceeds the -12 dBFS ceiling."
        )


def validate_thumbnail(
    manifest: dict[str, Any], run_id: str
) -> Path | None:
    if run_id in LEGACY_RUN_IDS and "thumbnail" not in manifest:
        return None
    thumbnail = manifest.get("thumbnail")
    if not isinstance(thumbnail, dict):
        raise ValueError("Future runs require a custom manifest.thumbnail.")
    for field in ("file", "layout", "hook"):
        if not isinstance(thumbnail.get(field), str) or not thumbnail[field].strip():
            raise ValueError(f"manifest.thumbnail.{field} must be documented.")
    if thumbnail.get("safe_crop") != "centered_4_5":
        raise ValueError("Thumbnail must declare the centered 4:5 safe crop.")
    hook_words = thumbnail["hook"].split()
    if not 2 <= len(hook_words) <= 5:
        raise ValueError("Thumbnail hook must contain 2–5 words.")
    path = resolve_repository_path(thumbnail["file"])
    if not path.is_file():
        raise ValueError(f"Custom thumbnail does not exist: {path}")
    if path.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
        raise ValueError("Custom thumbnail must be PNG or JPEG.")
    if path.stat().st_size > 50 * 1024 * 1024:
        raise ValueError("Custom thumbnail exceeds YouTube's 50MB limit.")
    probe = media_probe(path, "stream=width,height", "v:0")
    streams = probe.get("streams", [])
    if not streams:
        raise ValueError("Cannot inspect custom thumbnail dimensions.")
    width = streams[0].get("width")
    height = streams[0].get("height")
    if (width, height) != (1080, 1920):
        raise ValueError("Custom Shorts thumbnail must be exactly 1080×1920 (9:16).")
    return path


def resolve_repository_path(raw: str) -> Path:
    path = Path(raw)
    return path if path.is_absolute() else REPOSITORY_ROOT / path


def validate_manifest(path: Path) -> tuple[dict[str, Any], Path, list[Path], Path | None]:
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
    if status.get("privacyStatus") != "public":
        raise ValueError("Production publisher requires privacyStatus=public.")
    if manifest.get("notify_subscribers") is not True:
        raise ValueError("Production publisher requires notify_subscribers=true.")
    if not campaign_source or len(campaign_source) > 64 or any(
        character not in "abcdefghijklmnopqrstuvwxyz0123456789_-"
        for character in campaign_source
    ):
        raise ValueError("Campaign source does not match [a-z0-9_-]{1,64}.")
    if f"start={campaign_source}" not in campaign_url:
        raise ValueError("Campaign URL does not contain the configured source.")
    if not isinstance(snippet.get("title"), str) or not snippet["title"].strip():
        raise ValueError("Upload manifest title is empty.")
    description = snippet.get("description", "")
    if campaign_url in description or "https://t.me/" in description:
        raise ValueError(
            "Shorts descriptions must not contain Telegram URLs; use the bot handle and /start payload."
        )
    if "@PurrrfolioBot" not in description:
        raise ValueError("Upload description does not contain the Telegram bot handle.")
    description_policy = manifest.get("description_policy", "legacy_pilot")
    if description_policy == "legacy_pilot":
        if path.parent.name != "youtube-short-001":
            raise ValueError("New runs must use description_policy=organic_handle_only.")
        if f"/start {campaign_source}" not in description:
            raise ValueError("Legacy pilot description does not contain its campaign source.")
    elif description_policy == "organic_handle_only":
        lowered_description = description.lower()
        if "/start" in lowered_description or campaign_source in description:
            raise ValueError("Future Shorts descriptions must not expose campaign /start codes.")
        if "starter pack" in lowered_description:
            raise ValueError("Future Shorts descriptions must not advertise default starter packs.")
    else:
        raise ValueError("Unknown description_policy in upload manifest.")
    if snippet.get("categoryId") != "20":
        raise ValueError("Upload manifest must use YouTube Gaming categoryId=20.")
    if snippet.get("defaultLanguage") != "en":
        raise ValueError("Upload manifest must declare English metadata.")
    required_status = {
        "embeddable": True,
        "license": "youtube",
        "publicStatsViewable": True,
        "selfDeclaredMadeForKids": False,
        "containsSyntheticMedia": False,
    }
    for key, expected in required_status.items():
        if status.get(key) != expected:
            raise ValueError(f"Upload status.{key} must be {expected!r}.")
    if not video_path.is_file():
        raise ValueError(f"Rendered video does not exist: {video_path}")
    run_id = path.parent.name
    validate_creative_policy(manifest, video_path, run_id)
    validate_audio_policy(manifest, video_path, run_id)
    thumbnail_path = validate_thumbnail(manifest, run_id)

    caption_paths: list[Path] = []
    if not isinstance(captions, list) or not captions:
        raise ValueError("Upload manifest must contain at least one caption track.")
    for caption in captions:
        caption_path = resolve_repository_path(caption["file"])
        if not caption_path.is_file():
            raise ValueError(f"Caption file does not exist: {caption_path}")
        caption_paths.append(caption_path)

    return manifest, video_path, caption_paths, thumbnail_path


def begin_resumable_upload(
    access_token: str,
    manifest: dict[str, Any],
    video_path: Path,
) -> str:
    query = urllib.parse.urlencode(
        {
            "uploadType": "resumable",
            "part": "snippet,status",
            "notifySubscribers": str(manifest["notify_subscribers"]).lower(),
        }
    )
    staging_status = dict(manifest["status"])
    staging_status["privacyStatus"] = "private"
    body = json.dumps(
        {
            "snippet": manifest["snippet"],
            "status": staging_status,
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


def upload_thumbnail(
    access_token: str,
    video_id: str,
    thumbnail_path: Path,
) -> dict[str, Any]:
    content_type = "image/png" if thumbnail_path.suffix.lower() == ".png" else "image/jpeg"
    query = urllib.parse.urlencode(
        {
            "videoId": video_id,
            "uploadType": "media",
        }
    )
    image_data = thumbnail_path.read_bytes()
    request = urllib.request.Request(
        f"{THUMBNAILS_UPLOAD_URL}?{query}",
        data=image_data,
        method="POST",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": content_type,
            "Content-Length": str(len(image_data)),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(
            f"Custom thumbnail upload failed with HTTP {error.code}. "
            "The channel may still be awaiting thumbnail eligibility verification."
        ) from error
    except urllib.error.URLError as error:
        raise YouTubeApiError("Custom thumbnail upload failed: network unavailable.") from error
    except (OSError, json.JSONDecodeError) as error:
        raise YouTubeApiError("Custom thumbnail upload returned an unreadable response.") from error
    if not isinstance(result, dict) or not result.get("items"):
        raise YouTubeApiError("Custom thumbnail upload returned no thumbnail resource.")
    return result


def publish_video(
    access_token: str,
    video_id: str,
    manifest: dict[str, Any],
) -> dict[str, Any]:
    """Apply final metadata and make a fully-captioned staged upload public."""
    query = urllib.parse.urlencode({"part": "snippet,status"})
    body = json.dumps(
        {
            "id": video_id,
            "snippet": manifest["snippet"],
            "status": manifest["status"],
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        f"https://www.googleapis.com/youtube/v3/videos?{query}",
        data=body,
        method="PUT",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=UTF-8",
            "Content-Length": str(len(body)),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise YouTubeApiError(
            f"YouTube public publication failed with HTTP {error.code}. "
            "The Google API project may require a YouTube API compliance audit."
        ) from error
    except urllib.error.URLError as error:
        raise YouTubeApiError("YouTube public publication failed: network unavailable.") from error
    except (OSError, json.JSONDecodeError) as error:
        raise YouTubeApiError("YouTube public publication returned an unreadable response.") from error
    if not isinstance(result, dict) or result.get("id") != video_id:
        raise YouTubeApiError("YouTube public publication returned an unexpected response.")
    return result


def verify_private_staging(access_token: str, video_id: str, manifest: dict[str, Any]) -> None:
    """Leave the asset private if required remote assets are not ready."""
    videos = fetch_video(access_token, video_id).get("items", [])
    if len(videos) != 1:
        raise YouTubeApiError("Private staging verification returned no unique video.")
    video = videos[0]
    if video.get("snippet", {}).get("channelId") != manifest["expected_channel_id"]:
        raise YouTubeApiError("Staged video belongs to an unexpected channel.")
    if video.get("status", {}).get("privacyStatus") != "private":
        raise YouTubeApiError("Staged video is not private; refusing the publication step.")
    if video.get("contentDetails", {}).get("hasCustomThumbnail") is not True:
        raise YouTubeApiError("YouTube has not confirmed the custom thumbnail; leaving video private.")
    captions = fetch_captions(access_token, video_id).get("items", [])
    serving_languages = {
        item.get("snippet", {}).get("language")
        for item in captions
        if item.get("snippet", {}).get("isDraft") is False
        and item.get("snippet", {}).get("status") == "serving"
    }
    if not {item["language"] for item in manifest["captions"]} <= serving_languages:
        raise YouTubeApiError("Required captions are not all serving; leaving video private. Resume this receipt later.")


def write_receipt(path: Path, receipt: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Dry-run or execute one public Purrrfolio YouTube publication."
    )
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST, type=Path)
    parser.add_argument("--client-secret", type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    parser.add_argument(
        "--video-id",
        default="",
        help="Reuse an existing staged video, synchronize captions/metadata, and publish it.",
    )
    args = parser.parse_args()

    manifest_path = resolve_repository_path(str(args.manifest))
    try:
        manifest, video_path, caption_paths, thumbnail_path = validate_manifest(manifest_path)
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
        "custom_thumbnail_prepared": thumbnail_path is not None,
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
    if manifest_path.parent.name not in LEGACY_RUN_IDS and manifest["audio"].get("listening_review") != "passed":
        raise SystemExit("Final-mix listening review is not passed. No upload was attempted.")

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
                    "thumbnail_uploaded": False,
                    "published": False,
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
                "thumbnail_uploaded": False,
                "published": False,
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
        if thumbnail_path is not None and not receipt.get("thumbnail_uploaded"):
            upload_thumbnail(access_token, video_id, thumbnail_path)
            receipt["thumbnail_uploaded"] = True
            write_receipt(receipt_path, receipt)
        if not receipt.get("published"):
            if manifest_path.parent.name not in LEGACY_RUN_IDS:
                verify_private_staging(access_token, video_id, manifest)
            publish_video(access_token, video_id, manifest)
            receipt["privacy_status"] = "public"
            receipt["published"] = True
            write_receipt(receipt_path, receipt)
    except (ValueError, YouTubeApiError) as error:
        raise SystemExit(str(error)) from error

    print(
        json.dumps(
            {
                "video_id": video_id,
                "privacy_status": receipt["privacy_status"],
                "caption_tracks_uploaded": len(receipt["caption_ids"]),
                "caption_languages": sorted(receipt["caption_ids"]),
                "custom_thumbnail_uploaded": receipt.get("thumbnail_uploaded", False),
                "receipt": str(receipt_path.relative_to(REPOSITORY_ROOT)),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    print("Public video, caption tracks, and required thumbnail are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
