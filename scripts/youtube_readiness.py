#!/usr/bin/env python3
"""Validate local YouTube credentials and safe publishing policy offline."""

from __future__ import annotations

import argparse
import stat
from pathlib import Path

from youtube_api import (
    CAPTION_UPLOAD_SCOPE,
    REQUIRED_SCOPES,
    configured_scopes,
    load_installed_client,
    load_json,
    load_token,
)


DEFAULT_CONFIG = Path("marketing/config/youtube.json")
DEFAULT_TOKEN = Path(".secrets/youtube-oauth-token.json")


def check_private_mode(path: Path) -> bool:
    mode = stat.S_IMODE(path.stat().st_mode)
    return mode & (stat.S_IRWXG | stat.S_IRWXO) == 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run offline YouTube whoami/upload readiness checks."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument("--token", default=DEFAULT_TOKEN, type=Path)
    parser.add_argument("--config", default=DEFAULT_CONFIG, type=Path)
    args = parser.parse_args()

    failures: list[str] = []

    try:
        config = load_json(args.config, "YouTube config")
        expected_channel = config["channel"]["expected_id"]
        configured_required_scopes = set(config["oauth"]["required_scopes"])
        configured_caption_scope = config["oauth"]["caption_upload_scope"]
        publishing = config["publishing"]
        if not expected_channel:
            failures.append("configured expected channel id is empty")
        if configured_required_scopes != REQUIRED_SCOPES:
            failures.append("configured OAuth scopes do not match script requirements")
        if configured_caption_scope != CAPTION_UPLOAD_SCOPE:
            failures.append("configured caption-upload scope does not match script requirements")
        if publishing.get("default_privacy") != "public":
            failures.append("configured production privacy must be public")
        if publishing.get("public_autopublish_enabled") is not True:
            failures.append("recurring public publishing must be enabled")
        if publishing.get("notify_subscribers") is not True:
            failures.append("subscriber notifications must be enabled")
        if publishing.get("category_id") != "20":
            failures.append("Gaming category must be configured")
        print("[ok] committed YouTube channel and publishing policy loaded")
    except (KeyError, TypeError, ValueError) as error:
        print(f"[fail] configuration: {error}")
        return 1

    try:
        client = load_installed_client(args.client_secret)
        print("[ok] OAuth Desktop client is readable")
    except ValueError as error:
        print(f"[fail] OAuth client: {error}")
        return 1

    try:
        token = load_token(args.token)
        print("[ok] refresh token is present")
    except ValueError as error:
        print(f"[fail] OAuth token: {error}")
        return 1

    if token.get("client_id") != client.get("client_id"):
        failures.append("OAuth token and Desktop client ids do not match")
    else:
        print("[ok] OAuth token belongs to the supplied Desktop client")

    missing_scopes = REQUIRED_SCOPES - configured_scopes(token)
    if missing_scopes:
        failures.append(
            f"OAuth token metadata is missing {len(missing_scopes)} required scope(s)"
        )
    else:
        print("[ok] upload, channel-read, and analytics scopes are recorded")

    if CAPTION_UPLOAD_SCOPE in configured_scopes(token):
        print("[ok] caption-track upload scope is recorded")
    else:
        failures.append("OAuth token metadata is missing youtube.force-ssl")

    try:
        if check_private_mode(args.token):
            print("[ok] OAuth token file is owner-only")
        else:
            failures.append("OAuth token file permissions are not owner-only")
    except OSError as error:
        failures.append(f"cannot inspect OAuth token file permissions: {error}")

    if failures:
        for failure in failures:
            print(f"[fail] {failure}")
        return 1

    print("Ready for authenticated channel validation and staged public publishing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
