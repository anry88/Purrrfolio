ALTER TABLE users
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notification_snooze_until TIMESTAMPTZ,
    ADD COLUMN telegram_blocked_at TIMESTAMPTZ,
    ADD COLUMN free_card_notified_for TIMESTAMPTZ,
    ADD COLUMN pack_notification_date DATE,
    ADD COLUMN pack_notification_due_date DATE,
    ADD COLUMN notification_retry_at TIMESTAMPTZ;

-- One durable daily snapshot of eligible balances; multiple instances may prepare it.
CREATE TABLE notification_daily_runs (
    reminder_date DATE PRIMARY KEY,
    prepared_at TIMESTAMPTZ NOT NULL
);

-- NULL notified_for includes existing players and the immediately available first card.
-- Use last_free_card_at (or created_at for the first card) as the availability cycle key.
CREATE INDEX idx_users_free_card_reminders ON users (last_free_card_at, id)
    WHERE notifications_enabled AND telegram_blocked_at IS NULL
      AND free_card_notified_for IS NULL;
CREATE INDEX idx_users_pack_reminders ON users (pack_notification_due_date, id)
    WHERE notifications_enabled AND telegram_blocked_at IS NULL;
