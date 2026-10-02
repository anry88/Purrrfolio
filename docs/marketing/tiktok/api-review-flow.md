# Purrrfolio TikTok API review flow

Public website URL:

`https://purrrfolio.tg-games.com/`

This URL must be used as the TikTok Developer Portal Website/Web/Desktop URL.
It is an externally facing product page and displays visible Privacy Policy and
Terms of Service links without login.

Private review/demo URL:

`https://purrrfolio.tg-games.com/go/tiktok`

Access is protected by `MARKETING_ADMIN_TOKEN`. Do not share the token publicly.
Mention this private URL and the test admin token only in the app review apply
reason/instructions when TikTok needs to verify the API flow.

## TikTok Developer settings

- Web/Desktop URL: `https://purrrfolio.tg-games.com/`
- Redirect URI: `https://purrrfolio.tg-games.com/go/tiktok/oauth/callback`
- Privacy Policy URL: `https://purrrfolio.tg-games.com/privacy`
- Terms of Service URL: `https://purrrfolio.tg-games.com/terms`
- Suggested scopes for the first review:
  - `user.info.basic`
  - `video.upload`
  - `video.publish`
  - `video.list`

## Demo video script

1. Open `https://purrrfolio.tg-games.com/go/tiktok`.
2. Log in with the private marketing admin token.
3. Click `Connect TikTok account`.
4. Authorize the sandbox TikTok account.
5. Return to the Purrrfolio dashboard and show that the account is connected.
6. Click `Query creator info` and show the JSON response.
7. Send the bundled demo video URL to TikTok with `INBOX_UPLOAD` or
   `DIRECT_POST` using `SELF_ONLY`.
8. Copy the returned `publish_id`.
9. Click `Fetch status` and show the processing/delivery status.
10. If `video.list` is already approved for the app, click `Call video.list`.

## Runtime env

```text
MARKETING_ADMIN_TOKEN=<long random private token>
TIKTOK_CLIENT_KEY=<from TikTok Developer Portal>
TIKTOK_CLIENT_SECRET=<from TikTok Developer Portal>
TIKTOK_REDIRECT_URI=https://purrrfolio.tg-games.com/go/tiktok/oauth/callback
TIKTOK_SCOPES=user.info.basic,video.upload,video.publish,video.list
TIKTOK_DEMO_VIDEO_URL=https://purrrfolio.tg-games.com/assets/marketing/purrrfolio-demo.mp4
```

The backend stores TikTok access and refresh tokens in PostgreSQL and refreshes
the access token before API calls when it is close to expiry.
