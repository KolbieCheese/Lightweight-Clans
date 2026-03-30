# CustomClans

CustomClans is a lightweight, command-only Minecraft clans plugin for Paper `1.21.11+`. It is designed for Java and Bedrock-friendly command usage with persistent SQLite storage, public clan lookup, public chat tags, private clan chat, and a single-leader MVP role model.

## Features

- Command-based clan creation, invitations, accepting, denying, lookup, leaving, and disbanding
- Exactly two roles: `PRESIDENT` and `MEMBER`, with one `PRESIDENT` enforced per clan
- Case-insensitive clan names with preserved display names
- Public chat tags rendered with Adventure Components and `AsyncChatEvent`
- Safe clan tag colors using named Minecraft colors or `#RRGGBB`
- Optional clan chat toggle for routing normal chat into clan chat
- SQLite persistence with repository abstractions for a future MySQL swap
- Asynchronous database access through a dedicated database executor

## Commands

- `/clan create <name>`
- `/clan invite <player>`
- `/clan accept <clan name>`
- `/clan deny <clan name>`
- `/clan leave`
- `/clan get <clan name> info`
- `/clan get <clan name> members`
- `/clan chat <message>`
- `/clan chat toggle`
- `/clan help`
- `/clan rename <newName>`
- `/clan tag <tag>`
- `/clan color <named color|#RRGGBB>`
- `/clan kick <player>`
- `/clan transfer <player>`
- `/clan disband`

## Permissions

- `clans.use`
- `clans.create`
- `clans.chat`
- `clans.invite`
- `clans.lookup`
- `clans.manage`
- `clans.admin`

`clans.manage` only allows a player to attempt management commands. President-only actions still validate that the player is the actual `PRESIDENT` of their clan.

## Configuration

`config.yml` controls:

- clan name and tag length limits
- default clan tag color
- invite expiration time
- max clan size
- public and clan chat formatting
- clan chat availability and toggle availability
- debug logging

`messages.yml` contains all player-facing strings, including usage text, public lookup output, invite flow, and management messages.

## Storage

- Default database: `plugins/CustomClans/clans.db`
- Tables are created automatically on startup.
- SQLite is used by default through repository interfaces so the persistence layer can be swapped later.

## Chat Formatting

Public chat uses `AsyncChatEvent` with a custom renderer. The plugin creates a safe Adventure `Component` for the clan tag and injects it into the configured MiniMessage template through component placeholders. This keeps clan colors limited to the tag and prevents tag values from injecting MiniMessage formatting into the rest of the line.

Clan chat supports both `/clan chat <message>` and `/clan chat toggle`. The `clan-chat-enabled` config option disables clan chat completely, while `clan-chat-toggle-enabled` only controls whether players can keep clan chat mode turned on for normal chat. Toggle mode is session-only and is stored in memory, so it clears on logout or restart. When enabled, the plugin intercepts `AsyncChatEvent`, cancels the public broadcast, and forwards the message only to online clan members.

Clan lookup is always public through `/clan get <clan name> info` and `/clan get <clan name> members`. Clan names are matched case-insensitively, but the original display name is preserved for output and chat tags.

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
