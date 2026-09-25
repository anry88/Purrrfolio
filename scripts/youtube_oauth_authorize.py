#!/usr/bin/env python3
"""Authorize a local Google OAuth Desktop client for YouTube automation."""

from __future__ import annotations

import argparse
import json
import secrets
import sys
import time
import urllib.parse
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from youtube_api import AUTHORIZATION_SCOPES, TOKEN_URL, load_installed_client


AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"


class OAuthCallback(BaseHTTPRequestHandler):
    server_version = "PurrrfolioOAuth/1.0"

    def log_message(self, format, *args):  # noqa: A002
        return

    def do_GET(self):  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        self.server.oauth_result = {
            key: values[0] for key, values in params.items() if values
        }
        ok = "code" in self.server.oauth_result
        body = (
            "<html><body><h1>Authorization complete</h1>"
            "<p>You can close this tab and return to the terminal.</p></body></html>"
            if ok
            else "<html><body><h1>Authorization failed</h1>"
            "<p>Return to the terminal for details.</p></body></html>"
        )
        self.send_response(200 if ok else 400)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body.encode("utf-8"))))
        self.end_headers()
        self.wfile.write(body.encode("utf-8"))


def exchange_code(client, code, redirect_uri):
    payload = urllib.parse.urlencode(
        {
            "code": code,
            "client_id": client["client_id"],
            "client_secret": client["client_secret"],
            "redirect_uri": redirect_uri,
            "grant_type": "authorization_code",
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        TOKEN_URL,
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(
        description="Authorize YouTube for Purrrfolio marketing automation."
    )
    parser.add_argument("--client-secret", required=True, type=Path)
    parser.add_argument(
        "--output",
        default=Path(".secrets/youtube-oauth-token.json"),
        type=Path,
    )
    parser.add_argument("--port", default=8765, type=int)
    parser.add_argument(
        "--print-auth-url",
        action="store_true",
        help="Print the browser authorization URL if automatic opening is unavailable.",
    )
    args = parser.parse_args()

    try:
        client = load_installed_client(args.client_secret)
    except ValueError as error:
        raise SystemExit(str(error)) from error

    state = secrets.token_urlsafe(24)
    redirect_uri = f"http://127.0.0.1:{args.port}/"
    query = urllib.parse.urlencode(
        {
            "client_id": client["client_id"],
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": " ".join(sorted(AUTHORIZATION_SCOPES)),
            "access_type": "offline",
            "include_granted_scopes": "true",
            "prompt": "consent",
            "state": state,
        }
    )
    auth_url = f"{AUTH_URL}?{query}"

    httpd = HTTPServer(("127.0.0.1", args.port), OAuthCallback)
    httpd.oauth_result = None

    print("Opening Google authorization page...")
    opened = webbrowser.open(auth_url)
    if args.print_auth_url:
        print(auth_url)
    elif not opened:
        raise SystemExit(
            "Browser did not open. Re-run with --print-auth-url to continue manually."
        )

    while httpd.oauth_result is None:
        httpd.handle_request()

    result = httpd.oauth_result
    if result.get("state") != state:
        raise SystemExit("OAuth state mismatch; refusing to save token.")
    if "error" in result:
        raise SystemExit("Google returned an OAuth error; token was not saved.")
    if "code" not in result:
        raise SystemExit("No authorization code received.")

    token = exchange_code(client, result["code"], redirect_uri)
    if "refresh_token" not in token:
        raise SystemExit(
            "No refresh_token returned. Revoke access and authorize again if needed."
        )

    token["created_at"] = int(time.time())
    token["expires_at"] = token["created_at"] + int(token.get("expires_in", 0))
    token["scopes_requested"] = sorted(AUTHORIZATION_SCOPES)
    token["client_id"] = client["client_id"]
    token["token_uri"] = client.get("token_uri", TOKEN_URL)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as handle:
        json.dump(token, handle, indent=2)
        handle.write("\n")
    args.output.chmod(0o600)

    print(f"Saved YouTube OAuth token to {args.output}")
    print("Do not commit or paste this file.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("Interrupted.")
