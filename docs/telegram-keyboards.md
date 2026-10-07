# Telegram keyboard behavior (AND-16)

Checked against the official [Telegram Bot API](https://core.telegram.org/bots/api)
on 2026-10-07.

| Option | Result / decision |
| --- | --- |
| Omit reply markup | Does not remove a previously installed keyboard. Insufficient alone. |
| `ReplyKeyboardRemove` | Removes the custom keyboard. Attach it to an ordinary gameplay answer, without a service message. |
| Inline buttons | Attached to one message, without replacing the typing keyboard. Keep contextual buttons and expose `/menu`. |
| Slash commands / command menu | Always available for supported actions. Register `/menu` and `/notifications` alongside existing commands. |
| `one_time_keyboard` | Hides after a tap, but can still be reopened. Does not solve permanent removal in groups. |
| `selective` | Targets mentioned users or the author of a replied-to message. Does not guarantee removal for every group member. Removal is deliberately non-selective. |
| Private deep links | Useful for private-only actions. Core gameplay stays in groups, including Friends drops; no redirect is required. |

Private chat IDs are positive; group and supergroup IDs are negative. The outgoing
client enforces removal for ordinary negative-ID replies and photos, so every
command path and background group response uses the same policy. Inline-only
messages retain their buttons. `/help` in a group explicitly sends removal;
`/menu` sends the action buttons when requested. The private reply menu remains.

Telegram accepts one reply-markup type per message: inline markup and keyboard
removal cannot be combined. Consequently, a legacy keyboard may remain until
the first ordinary answer or `/help` if a player's first interaction only sends
inline buttons. The bot cannot inspect which keyboard each Telegram client
currently shows. No extra message is sent solely to remove a keyboard.

Automated coverage verifies actual sendMessage JSON for private/group/supergroup
IDs, removal on ordinary replies, preservation of inline actions, and removal
in photo multipart payloads. PostgreSQL game-routing checks cover `/help`,
`/menu`, commands and callbacks.

Real Telegram client acceptance remains a manual deployment check: seed a legacy
keyboard in a group and supergroup, send `/help`, confirm normal typing and
no extra service message, then test `/menu`, `/pack`, `/freecard`, gallery and
market callbacks; confirm the private menu remains. Automated API checks cannot
confirm the display behavior of individual Telegram client versions.
