# CustomClans

CustomClans is a lightweight, command-only Minecraft clans plugin for Paper `1.21.11+`. It is designed for Java and Bedrock-friendly command usage with persistent SQLite storage, public clan lookup, public chat tags, private clan chat, and a single-leader MVP role model.

## Features

- Command-based clans with one enforced `PRESIDENT` and any number of `MEMBER`s up to the configured limit
- Creator automatically becomes the clan President
- Public clan lookup by name through `/clan get <clan name> info|members`
- Case-insensitive clan names for create, rename, invites, and lookup while preserving the chosen display name
- Safe public chat tag rendering with Adventure Components and `AsyncChatEvent`
- Safe clan tag colors using named Minecraft colors or `#RRGGBB`
- Clan invites accepted or denied by clan name
- Optional clan chat toggle for routing normal chat into clan chat
- SQLite persistence with repository abstractions for a future storage swap
- Asynchronous database access through a dedicated database executor

## Commands

- Public:
  - `/clan help`
  - `/clan get <clan name> info`
  - `/clan get <clan name> members`
- General player actions:
  - `/clan create <clan name>`
  - `/clan accept <clan name>`
  - `/clan deny <clan name>`
- Member actions:
  - `/clan invite <player>`
  - `/clan leave`
  - `/clan chat <message>`
  - `/clan chat toggle`
- President-only actions:
  - `/clan rename <new clan name>`
  - `/clan tag <new tag>`
  - `/clan color <named color|#RRGGBB>`
  - `/clan transfer <player>`
  - `/clan kick <player>`
  - `/clan disband`

`/clan info` and `/clan members` are intentionally not implemented as separate convenience aliases in this MVP.

## Permissions

- `clans.use` (default `true`): `/clan`, `/clan help`, `/clan accept`, `/clan deny`, `/clan leave`
- `clans.create` (default `true`): `/clan create`
- `clans.invite` (default `true`): `/clan invite`
- `clans.chat` (default `true`): `/clan chat`, `/clan chat toggle`
- `clans.lookup` (default `true`): `/clan get <clan name> info|members`
- `clans.manage` (default `true`): `/clan rename`, `/clan tag`, `/clan color`, `/clan transfer`, `/clan kick`, `/clan disband`
- `clans.admin` (default `op`): reserved for future admin or bypass features

`clans.manage` only allows a player to attempt management commands. President-only actions are still enforced in the service layer, so a non-President cannot rename, retag, recolor, transfer, kick, or disband even if they somehow receive the permission node.

## Role Rules

- Every clan has exactly one President.
- The clan creator automatically becomes the President.
- Only the President can rename the clan, change the tag, change the tag color, transfer the presidency, kick members, or disband the clan.
- Any clan member can invite players, leave the clan, use clan chat, and toggle clan chat.
- The President cannot leave without first transferring leadership or disbanding the clan.

## Clan Names And Invites

- Clan names are unique regardless of capitalization.
- Clan name lookups are case-insensitive.
- The original display name is preserved for output and chat tags.
- Invites are accepted or denied by clan name with `/clan accept <clan name>` and `/clan deny <clan name>`.
- Duplicate active invites from the same clan to the same player are rejected.
- Invited players must be online for this MVP.

## Configuration

`config.yml` controls:

- clan name and tag length limits
- default clan tag color
- invite expiration time
- max clan size
- public and clan chat formatting
- clan chat availability and toggle availability

`messages.yml` contains all player-facing strings, including usage text, public lookup output, invite flow, and management messages.

## Storage

- Default database: `plugins/CustomClans/clans.db`
- Tables and indexes are created automatically on startup.
- Clan names are stored with a normalized form for case-insensitive uniqueness and lookup.
- SQLite is used by default through repository interfaces so the persistence layer can be swapped later.
- Existing databases already using the current schema do not need a new migration for this polish pass.

## Chat Formatting

Public chat uses `AsyncChatEvent` with a custom renderer. The plugin creates a safe Adventure `Component` for the clan tag and injects it into the configured MiniMessage template through component placeholders. It also uses the renderer-provided display name component for player names, which preserves nickname/display-name plugin compatibility. This keeps clan colors limited to the tag and prevents tag values from injecting MiniMessage formatting into the rest of the line.

Clan chat supports both `/clan chat <message>` and `/clan chat toggle`. The `clan-chat-enabled` config option disables clan chat completely, while `clan-chat-toggle-enabled` only controls whether players can keep clan chat mode turned on for normal chat. Toggle mode is session-only and is stored in memory, so it clears on logout or restart. When enabled, the plugin intercepts `AsyncChatEvent`, cancels the public broadcast, and forwards the message only to online clan members.

Accepted clan tag colors are:

- named Minecraft colors such as `gold`, `red`, `dark_red`, or `light_purple`
- hex colors in `#RRGGBB` format

Formatting codes, MiniMessage tags, and style modifiers such as bold, italic, underline, strikethrough, and obfuscated text are not accepted as clan color input.

Clan lookup is always public through `/clan get <clan name> info` and `/clan get <clan name> members`. Those commands do not require clan membership.

## MVP Limitations

- Invited players must be online.
- `clans.admin` is reserved but does not currently unlock any extra commands or bypasses.
- Only the configured tag prefix is colored in public chat; player names and message text remain on the normal format unless you change the chat templates.

## Architecture

- `plugin`: bootstrap only
- `commands`: parsing, permissions, and dispatch
- `services`: business logic and gameplay rules
- `repositories`: persistence contracts
- `repositories/sqlite`: SQLite implementations
- `listeners`: chat and session hooks
- `config`: typed config and message loading
- `util`: validation and formatting helpers

Commands call services, services call repositories, and listeners delegate to services rather than embedding business rules directly.

## Building

The project targets Java `21` and Gradle. Build with:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The shadow jar is produced without a classifier so the SQLite JDBC dependency is bundled into the final plugin jar.

## Versioning And Releases

- Local builds default to `pluginBaseVersion-SNAPSHOT`.
- GitHub Actions builds automatically use `pluginBaseVersion-build.<run number>`.
- Every push to `main` publishes a GitHub release with the shaded plugin jar attached.
- To start a new release line, update `pluginBaseVersion` in `gradle.properties`.
