-- Bot-wide reminder pause after Telegram 429, shared by workers and preserved on restart.
CREATE TABLE notification_dispatch_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    paused_until TIMESTAMPTZ
);
INSERT INTO notification_dispatch_state (id) VALUES (1);
