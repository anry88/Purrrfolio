ALTER TABLE users ADD COLUMN free_card_reminded_at TIMESTAMPTZ;
-- The old model recorded the cycle, not the send time. Wait one week after migration for already reminded players.
UPDATE users SET free_card_reminded_at = NOW() WHERE free_card_notified_for IS NOT NULL;
CREATE INDEX idx_users_weekly_free_card_reminder ON users(free_card_reminded_at, id)
    WHERE notifications_enabled AND telegram_blocked_at IS NULL;
