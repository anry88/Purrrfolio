CREATE TABLE marketing_tiktok_tokens (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    open_id TEXT NOT NULL,
    scope TEXT NOT NULL,
    access_token TEXT NOT NULL,
    access_token_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token TEXT NOT NULL,
    refresh_token_expires_at TIMESTAMPTZ NOT NULL,
    token_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE marketing_tiktok_oauth_states (
    state TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE marketing_tiktok_posts (
    id BIGSERIAL PRIMARY KEY,
    publish_id TEXT NOT NULL UNIQUE,
    mode TEXT NOT NULL CHECK (mode IN ('INBOX_UPLOAD', 'DIRECT_POST')),
    video_url TEXT NOT NULL,
    title TEXT,
    privacy_level TEXT,
    status TEXT,
    fail_reason TEXT,
    raw_response TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
