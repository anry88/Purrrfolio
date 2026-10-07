# XP and chat ranking — AND-13

Implemented rules and privacy review. The gameplay rules are authoritative in
[implementation-spec.md](implementation-spec.md#опыт-история-получений-и-рейтинг-чата-and-13).

## Score and history

| Rarity | XP per drawn copy |
| --- | ---: |
| Common | 1 |
| Uncommon | 2 |
| Rare | 3 |
| Epic | 5 |
| Mythic | 8 |
| Legendary | 12 |

Every pack/free-card draw counts, including duplicates and special cards. Cards
received through trades or returned from escrow are recorded with zero XP. XP is
cumulative and remains after crafting or Stars refunds. This rewards collecting
rather than moving cards between accounts. Alternate Telegram accounts still
have separate identities; preventing account farms is outside this feature.

The append-only acquisition journal stores a UUID for the opening, player/card,
quantity, source, receipt update ID, received/recorded time and XP per copy; fresh
draws also retain rarity. Repeated copies in one opening share one row/quantity.
Exact pack order remains in its receipt. The cached users.xp balance, inventory,
opening receipt and pack debit/free timer commit together. Retrying an update
replays the saved result without another award, including free-card retries after
the three-hour cooldown. A crash after Telegram delivery can repeat the message.

V21 imports retained pack receipts, then supplements each user/card with the
positive difference between conserved holdings (inventory plus active market or
waiting random-trade escrow) and receipt quantities. Thus historical XP uses the
per-card maximum, without double-counting retained cards and their receipts.
`legacy_pack` identifies known openings; `legacy_baseline` identifies the agreed
estimate, with received_at NULL. Earlier free draws, crafted cards or transfers
cannot be reconstructed exactly. Old refunded purchases are not filtered. The
migration uses a frozen catalog and fails on unknown IDs; repeat Flyway startup
does not award the baseline again.

## Scope and membership

`/xp` shows the caller's experience. `/rank` and aliases `/rating`/`/leaderboard`
show only the current group/supergroup roster known to the bot through commands.
No global names, global leaderboard, registration list, or cross-chat lookup is
exposed. Roster tracking reuses group_chat_members rather than adding public nicks.
Joining a group alone is insufficient: interact with the bot there first.
Leaving or being kicked excludes a player at the next membership check; rejoining
and playing makes them eligible again. Scores are read on every ranking request.

SQL joins users to group_chat_members using the current chat_id only. The
requester and candidates must have fresh, matching getChatMember identity and
explicit human/member status. Accepted: creator, administrator, member, and
restricted with is_member=true. Missing/unknown/left/kicked/bot/lookup failure
cannot authorize inclusion. The Bot API [documents administrator requirements
for reliable membership queries](https://core.telegram.org/bots/api#getchatmember)
and the [restricted membership flag](https://core.telegram.org/bots/api#chatmemberrestricted).

Results use the current membership first_name, with a generic localized fallback.
There are no usernames, profile URLs, Telegram IDs, or inventory/payment details.
Names are capped at 60 characters, controls/bidi overrides removed, and output is
plain text, so user-supplied names cannot create Markdown mentions. Names are not
copied to a new global profile or persisted by the ranking service.

Pagination contains the origin chat ID. It must match the receiving group message;
a callback in a private/other chat is rejected. Requester membership is checked
again for every page. Equal XP shares competition places 1,1,3; internal user ID
provides deterministic tie order without being displayed. Ten rows per page;
stale page indexes are clamped after membership changes.

The synchronous MVP checks at most 100 known candidates, refuses a larger roster,
and stops between membership requests after a 20-second budget. One ranking check
runs per process. These guards bound expensive API work; they do not replace
Telegram client timeouts. Failure gives localized unavailable copy and never
falls back to global or cached personal details. Larger groups need a subsequent
membership-event/snapshot design before raising the bound.

## Threat review and practical limits

| Threat | Control / remaining limit |
| --- | --- |
| Private or other-chat leaderboard lookup | Group-only routing, SQL scope and chat-bound callbacks; no global fallback |
| Leaver, banned player or API uncertainty | Fresh positive membership and matching ID required; unverified entries omitted |
| Requester outside group | Requester checked before querying the roster |
| Name/Markdown injection | Current first name only, length/control filtering, no markup parsing |
| XP farming through transfers/refunds | Only fresh draws award; credits record zero XP; refunds don't change score |
| Duplicate webhook / concurrent free claims | User lock, atomic transaction, unique receipt/update indexes |
| Catalog or XP policy changes rewriting history | XP/rarity snapshot per draw; immutable frozen migration mapping |
| Ranking requests exhausting external API | Bounded roster, deadline and concurrency; unavailable response on saturation |
| Old Telegram messages/forwarding | Telegram participants can retain/forward an already published message; the bot cannot revoke those copies |
| Join/leave between verification and send | A small unavoidable Bot API race remains; no long-lived membership cache is used |

Metrics expose only XP/acquisition totals and bounded command/callback labels.
No player IDs, names or chat IDs are used as metric labels.

## Weekly free-card reminder

The first reminder still arrives when the card becomes ready. Until it is claimed,
it repeats seven days after the previous accepted send. Pauses defer it until they
expire; permanent opt-out or bot block prevents delivery. A new claim clears the
cycle/send markers, enabling the next reminder three hours later. Existing markers
without an old send timestamp are seeded at V22 migration time to avoid an immediate
repeat burst. Daily unopened-pack reminders and 250 ms delivery pacing/shared 429
pause are retained; simultaneous due reminders combine into one message.

## Verification

PostgreSQL integration coverage checks historical receipt/holding maxima including
escrow, repeat migration, refund retention, duplicate draws, pack/free receipt replay,
concurrent free claims and rollback of all grant state on receipt failure. Ranking
checks cover chat-scoped SQL, membership uncertainty, departures, restricted status,
ties, pagination and forged callback rejection. Reminder tests cover seven-day
boundaries/restarts, new cycles, quiet/permanent preferences, blocks and existing
pacing/rate-limit behavior. The full `./gradlew test` suite is required before release.

Local verification on 2026-10-07: `./gradlew test bootJar` passed **182 tests**,
with zero failures, errors or skipped tests. Production release
`purrrfolio:xp-ranking-20261007-72833098f3ec` is deployed and verified. Flyway V21–V22
succeeded; internal/public health UP, webhook queue 0, game timezone UTC and delivery
pacing 250 ms. Telegram registered `/xp` and `/rank`. The initial backfill credited
3,613 XP to 109 of 119 users: 546 receipt-backed copies and 1,018 estimated baseline
copies. Every cached XP balance matched the durable journal; reminder timestamps
were complete. A server-only DB/config backup and previous image were preserved.
See the [release receipt](source/xp-ranking-release.json) for artifact hashes and
source commit.
