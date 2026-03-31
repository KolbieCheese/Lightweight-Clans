# Lightweight Clans

Lightweight Clans is a lightweight, command-only Minecraft clans plugin for Paper `1.21.11+`. It is designed for Java and Bedrock-friendly command usage with persistent SQLite storage, public clan lookup, public chat tags, private clan chat, and a single-leader MVP role model.

## Features

- Command-based clans with one enforced `PRESIDENT` and any number of `MEMBER`s up to the configured limit
- Creator automatically becomes the clan President
- Public clan lookup through `/clan info [clan name]` and `/clan members [clan name]`
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
  - `/clan info [clan name]`
  - `/clan members [clan name]`
  - `/clan list`
- General player actions:
  - `/clan create <clan name>`
  - `/clan accept <clan name>`
  - `/clan deny <clan name>`
- Member actions:
  - `/clan invite <player>`
  - `/clan leave`
  - `/clan chat <message>`
  - `/clan chat toggle`
  - `/clan banner`
- President-only actions:
  - `/clan rename <new clan name>`
  - `/clan description <text>`
  - `/clan tag <new tag>`
  - `/clan color <named color|#RRGGBB>`
  - `/clan setbanner` (must hold a banner in main hand)
  - `/clan transfer <player>`
  - `/clan kick <player>`
  - `/clan disband`

`/clan info` and `/clan members` are intentionally not implemented as separate convenience aliases in this MVP.

## Permissions

- `clans.use` (default `true`): `/clan`, `/clan help`, `/clan accept`, `/clan deny`, `/clan leave`, `/clan banner`
- `clans.create` (default `true`): `/clan create`
- `clans.invite` (default `true`): `/clan invite`
- `clans.chat` (default `true`): `/clan chat`, `/clan chat toggle`
- `clans.lookup` (default `true`): `/clan info [clan name]` and `/clan members [clan name]`
- `clans.manage` (default `true`): `/clan rename`, `/clan description`, `/clan tag`, `/clan color`, `/clan setbanner`, `/clan transfer`, `/clan kick`, `/clan disband`
- `clans.admin` (default `op`): reserved for future admin or bypass features

`clans.manage` only allows a player to attempt management commands. President-only actions are still enforced in the service layer, so a non-President cannot rename, retag, recolor, transfer, kick, or disband even if they somehow receive the permission node.

## Role Rules

- Every clan has exactly one President.
- The clan creator automatically becomes the President.
- Only the President can rename the clan, change the tag, change the tag color, set the clan banner (`/clan setbanner` while holding a banner), transfer the presidency, kick members, or disband the clan.
- Any clan member can invite players, leave the clan, use clan chat, toggle clan chat, and receive a clan banner copy with `/clan banner`.
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

- config schema version for upgrade migrations
- clan name and tag length limits
- default clan tag color
- invite expiration time
- max clan size
- public and clan chat formatting
- clan chat availability and toggle availability
- DiscordSRV clan chat relay behavior via `discordsrv-clan-chat-relay.*`

Discord relay cancellation behavior is controlled by:

- `discordsrv-clan-chat-relay.forward-when-cancelled: false` (default): cancellation suppresses both in-game clan chat and Discord relay.
- `discordsrv-clan-chat-relay.forward-when-cancelled: true`: cancellation suppresses in-game clan chat only; Discord relay still proceeds.

`messages.yml` contains all player-facing strings, including usage text, public lookup output, invite flow, and management messages.

## Storage

- Default database: `plugins/LightweightClans/clans.db`
- Tables and indexes are created automatically on startup.
- Clan names are stored with a normalized form for case-insensitive uniqueness and lookup.
- SQLite is used by default through repository interfaces so the persistence layer can be swapped later.
- Clan banner base color and pattern design are persisted in SQLite and restored for `/clan banner`.
- Existing databases already using the current schema do not need a new migration for this polish pass.
- Migration note (2026-03-31): startup now removes the legacy, unused `clan_banners` table if it exists; banner data is stored only in `clans.banner_material` and `clans.banner_patterns_json`.
- On first boot after renaming from `CustomClans` to `LightweightClans`, the plugin migrates `config.yml`, `messages.yml`, and `clans.db` from `plugins/CustomClans` into `plugins/LightweightClans` and removes the old folder after a successful copy.

## Chat Formatting

Public chat uses `AsyncChatEvent` with a custom renderer. The plugin creates a safe Adventure `Component` for the clan tag and injects it into the configured MiniMessage template through component placeholders. It also uses the renderer-provided display name component for player names, which preserves nickname/display-name plugin compatibility. This keeps clan colors limited to the tag and prevents tag values from injecting MiniMessage formatting into the rest of the line.

Clan chat supports both `/clan chat <message>` and `/clan chat toggle`. The `clan-chat-enabled` config option disables clan chat completely, while `clan-chat-toggle-enabled` only controls whether players can keep clan chat mode turned on for normal chat. Toggle mode is session-only and is stored in memory, so it clears on logout or restart. When enabled, the plugin intercepts `AsyncChatEvent`, cancels the public broadcast, and forwards the message only to online clan members.

## Plugin Integrations

Lightweight Clans ships an export/integration foundation so other plugins can consume clan data and lifecycle updates without touching internal services or repositories. The foundation consists of:

- a read-only Bukkit `ServicesManager` API (`LightweightClansApi`)
- immutable snapshot DTOs in `io.github.maste.customclans.api.model`
- lifecycle events under `io.github.maste.customclans.api.event`
- a chat delivery event (`ClanChatMessageEvent`) for clan chat interception/cancellation

### Fetching `LightweightClansApi` via Bukkit `ServicesManager`

Lightweight Clans registers `io.github.maste.customclans.api.LightweightClansApi` with `ServicePriority.Normal` during plugin enable, and unregisters plugin-owned services on disable/reload.

```java
import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<LightweightClansApi> registration = Bukkit.getServicesManager()
        .getRegistration(LightweightClansApi.class);
if (registration == null) {
    return;
}

LightweightClansApi clansApi = registration.getProvider();
```

All DTO imports for integrations should come from the single canonical export package
`io.github.maste.customclans.api.model`.
Legacy `io.github.maste.customclans.api.ClanSnapshot`, `ClanMemberSnapshot`, and
`ClanBannerSnapshot` have been removed.

### `LightweightClansApi` method list

`LightweightClansApi` includes synchronous and asynchronous read methods:

- Synchronous methods (`getClanById`, `getClanByName`, etc.) are convenience wrappers and may block the caller while reading from the database.
- Async methods (`...Async`) return `CompletableFuture` and are preferred for integrations that must avoid blocking.

- `Optional<ClanSnapshot> getClanById(long clanId)`
- `CompletableFuture<Optional<ClanSnapshot>> getClanByIdAsync(long clanId)`
- `Optional<ClanSnapshot> getClanByName(String name)`
- `CompletableFuture<Optional<ClanSnapshot>> getClanByNameAsync(String name)`
- `Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName)`
- `CompletableFuture<Optional<ClanSnapshot>> getClanByNormalizedNameAsync(String normalizedName)`
- `List<ClanSnapshot> getAllClans()`
- `CompletableFuture<List<ClanSnapshot>> getAllClansAsync()`
- `List<ClanMemberSnapshot> getMembersForClan(long clanId)`
- `CompletableFuture<List<ClanMemberSnapshot>> getMembersForClanAsync(long clanId)`
- `Optional<ClanBannerSnapshot> getBannerForClan(long clanId)`
- `CompletableFuture<Optional<ClanBannerSnapshot>> getBannerForClanAsync(long clanId)`
- `Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid)`
- `CompletableFuture<Optional<ClanSnapshot>> getClanForPlayerAsync(UUID playerUuid)`

### Snapshot contents

- `ClanSnapshot`
  - `id`, `name`, `normalizedName`, `tag`, `tagColor`, `description`
  - `presidentUuid`, `presidentName`, `memberCount`
  - `List<ClanMemberSnapshot> members`
  - `ClanBannerSnapshot banner` (nullable when banner not set)
  - `createdAt`, `updatedAt`
    - `createdAt` is fixed at clan creation time.
    - `updatedAt` starts equal to `createdAt` and changes when persisted mutable clan state changes:
      - rename
      - tag change
      - tag color change
      - description change
      - banner create/change/remove
      - president transfer
      - disband (timestamp touched immediately before clan row deletion)
    - membership join/leave/kick and invite lifecycle changes do **not** modify `updatedAt`.
- `ClanMemberSnapshot`
  - `playerUuid`, `lastKnownName`, `role`, `joinedAt`
- `ClanBannerSnapshot`
  - `baseMaterial`, `baseColor`, `List<BannerPatternSnapshot> patterns`
- `BannerPatternSnapshot`
  - `patternId`, `colorId`

### Full lifecycle event list and meanings

All lifecycle events live in `io.github.maste.customclans.api.event`:

- `ClanCreatedEvent`: fired after a clan is created and persisted.
- `ClanUpdatedEvent`: fired after mutable clan fields (for example name/tag/color/description) are changed and persisted.
- `ClanDeletedEvent`: fired after a clan is fully deleted from plugin-managed storage.
- `ClanMemberJoinedEvent`: fired after a player joins and membership changes are persisted.
- `ClanMemberLeftEvent`: fired after a member leaves voluntarily and persistence completes.
- `ClanMemberKickedEvent`: fired after a member is removed by another member and persistence completes.
- `ClanPresidentTransferredEvent`: fired after presidency is transferred and persistence completes.
- `ClanBannerUpdatedEvent`: fired after banner create/change/remove operations are persisted.

### Threading guarantee

- `ClanCreatedEvent`
- `ClanUpdatedEvent`
- `ClanDeletedEvent`
- `ClanMemberJoinedEvent`
- `ClanMemberLeftEvent`
- `ClanMemberKickedEvent`
- `ClanPresidentTransferredEvent`
- `ClanBannerUpdatedEvent`
- `ClanChatMessageEvent`

All of the events above are fired on the **main server thread**. Lifecycle events are fired only **after persistence completes**, so listeners can immediately read durable post-change state through `LightweightClansApi`.

### Clan chat integration event

`ClanChatMessageEvent` is fired:

- for `/clan chat <message>`
- for clan-chat toggle messages rerouted from `AsyncChatEvent`
- before final clan chat broadcast
- as a cancellable event to stop clan chat delivery

The event payload includes:

- `Player sender`
- `UUID senderUuid`
- `String clanName`
- `String clanTag`
- `String plainMessage`
- `Component messageComponent`
- `boolean toggleRouted`
- immutable `List<UUID> recipientUuids`

Example listener:

```java
@EventHandler
public void onClanChat(ClanChatMessageEvent event) {
    if (event.isToggleRouted()) {
        // Handle clan-chat toggle traffic separately if needed.
    }
}
```

### Banner export structure

Banner state exported through snapshots uses normalized lowercase IDs from `BannerSnapshotMapper`:

- `ClanBannerSnapshot.baseMaterial` is a lowercase namespaced material ID (for example `minecraft:white_banner`).
- `ClanBannerSnapshot.baseColor` is a lowercase color ID (for example `white`).
- ordered `ClanBannerSnapshot.patterns`, where each entry contains:
  - `BannerPatternSnapshot.patternId` as a lowercase namespaced ID when exported (for example `minecraft:stripe_downright`).
  - `BannerPatternSnapshot.colorId` as a lowercase color ID (for example `light_blue`).

Full normalized sample payload:

```json
{
  "baseMaterial": "minecraft:white_banner",
  "baseColor": "white",
  "patterns": [
    {
      "patternId": "minecraft:rhombus",
      "colorId": "black"
    },
    {
      "patternId": "minecraft:stripe_center",
      "colorId": "light_blue"
    },
    {
      "patternId": "minecraft:border",
      "colorId": "white"
    }
  ]
}
```

### Current scope note

Webhook or generic web-endpoint delivery is **not implemented in this step**. Integration is currently Bukkit service + Bukkit events only.

For auto-discovery, the plugin jar also includes `META-INF/snarky-outputs.json` with the stable output id `lightweightclans:clan_chat` and the exact event class name. External integrations such as Snarky Server can parse that manifest to detect Lightweight Clans support without hardcoded plugin-specific logic.

Accepted clan tag colors are:

- named Minecraft colors such as `gold`, `red`, `dark_red`, or `light_purple`
- hex colors in `#RRGGBB` format

Formatting codes, MiniMessage tags, and style modifiers such as bold, italic, underline, strikethrough, and obfuscated text are not accepted as clan color input.

Clan lookup is always public through `/clan info [clan name]` and `/clan members [clan name]`. When no clan name is provided, players default to their own clan; these commands still do not require clan membership when a clan name is specified.

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
