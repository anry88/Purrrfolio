-- Daily pack raffles inside group chats.
--
-- The Bot API cannot enumerate all chat members, so the winner pool is limited
-- to registered players the bot has actually seen in a given group chat.
-- group_chat_members tracks (chat, player) pairs from handled group messages.
-- group_raffles records one row per conducted raffle (24h cooldown per chat);
-- group_raffle_winners records the unique winners of each raffle.
CREATE TABLE group_chat_members (
    chat_id             BIGINT NOT NULL,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name        TEXT NOT NULL DEFAULT '',
    username            TEXT,
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX idx_group_chat_members_user
    ON group_chat_members(user_id);

CREATE TABLE group_raffles (
    id                  BIGSERIAL PRIMARY KEY,
    chat_id             BIGINT NOT NULL,
    member_count        INTEGER NOT NULL CHECK (member_count >= 0),
    packs_granted       INTEGER NOT NULL CHECK (packs_granted >= 0),
    raffled_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_group_raffles_chat_time
    ON group_raffles(chat_id, raffled_at DESC);

CREATE TABLE group_raffle_winners (
    raffle_id           BIGINT NOT NULL REFERENCES group_raffles(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    packs               INTEGER NOT NULL DEFAULT 1 CHECK (packs > 0),
    PRIMARY KEY (raffle_id, user_id)
);

CREATE INDEX idx_group_raffle_winners_user
    ON group_raffle_winners(user_id);
