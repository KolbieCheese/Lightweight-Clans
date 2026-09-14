# Lightweight Clans for Forge 1.20.1

Install `LightweightClans-Forge-1.20.1-<version>.jar` in the server's `mods/` directory. Requires Minecraft **1.20.1**, **Forge 47.4.17 or newer in the 47.x line**, and **Java 17**. Clients do not need this server-side mod. It also works on the logical server in single-player.

The Paper build is a separate download for Paper 26.2; put that JAR in `plugins/`.

## Gameplay

Use `/clan help`. Supports create, invite, accept, deny, leave, list, info, members, rename, description, tag, color, transfer, kick, disband, setbanner, banner, and private chat with `/clan chat toggle`.

Creators become President. Only the President can manage a clan. Any member can invite online players. Presidents must transfer leadership or disband before leaving. Invites expire and joining enforces the configured member limit. Player names or UUIDs identify members for kick/transfer, including offline members.

Operators (permission level 2) can use `/clan admin rename|tag|color|setbanner|disband <clan> [value]`. Use the clan slug or quote a multiword clan name in admin commands. Hold a banner for setbanner. Stored banners preserve base color and patterns; retrieving a banner drops it if the player's inventory is full.

## Platform differences

- Forge uses `config/lightweightclans-common.toml` with name/tag lengths, member limit, invite lifetime, chat toggles, and a reserved-name list. Forge watches this file for changes; there is no `/clan reload` command.
- Paper `config.yml`, `messages.yml`, Bukkit permissions, Bukkit lifecycle/API integrations, DiscordSRV, and the Paper webhook bridge do not apply to Forge. Forge uses built-in command messages and operator permissions. Its reserved-name list is separate from Paper's configurable word/alias moderation.
- Public chat adds a safely colored clan tag to the message body using Forge's chat event; the normal Minecraft sender decoration remains intact. Private clan chat uses system messages delivered only to online clan members. Chat toggle clears on logout/restart.
- Each world stores its database at `<world>/lightweightclans/clans.db`. SQLite and its migrations are shared with Paper and bundled with the mod. Do not point two running servers at one database. Automatic Paper data-folder migration is not performed, and newer Minecraft banner patterns may be unavailable on 1.20.1.

## Build

From the repository root, run `./gradlew build` with JDK 21 to build and test both platforms. Gradle provisions Java 17 for Forge and Java 25 for Paper. `./gradlew :forge:build` builds Forge alone. The distributable (reobfuscated, with nested SQLite dependency) is in `forge/build/libs/`; the `-dev.jar` is not for deployment.

The shared SQLite integration, migration, banner serialization, and validation tests also run on Java 17. Run `./gradlew :forge:runGameTestServer` for headless Forge runtime tests covering database startup, command registration, clan creation, President/operator permissions, and patterned banner restoration. CI requires both suites before releasing. Before production deployment, check multiplayer chat and commands with the other mods installed on your server.
