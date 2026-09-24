-- Telegram file_ids let inline mode resend an already uploaded card without
-- exposing the public image URL in the shared message.
CREATE TABLE telegram_card_files (
    card_id TEXT PRIMARY KEY,
    file_id TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
