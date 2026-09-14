# KNCraft Forge website integration fix

These are rebuilt Forge mods for Java 17 / Minecraft 1.20.1 / Forge 47.4.0+, not a website deployment. Both JARs must be replaced. They contain no secrets. Keep the existing website secrets unchanged.

## Install on KNCraft

1. Stop the KNCraft server and back up the existing two JARs and configuration files.
2. Replace the old WebhookIntegrations and LightweightClans Forge JARs in `mods/` with the two JARs in this archive. Keep only one JAR per mod. Do not install Paper JARs or the clans `-dev.jar`. Do not delete or replace the world or `world/lightweightclans/clans.db`; all existing clans remain in that database.
3. Adapt `webhookintegrations.EXAMPLE.json` into `config/webhookintegrations.json`. Preserve unrelated custom settings. Replace every `REPLACE_WITH_KNC_PLAYER_ACTIVITY_WEBHOOK` with the retained activity secret matching the website's `KNC_PLAYER_ACTIVITY_WEBHOOK`. The new per-event `headers` objects are essential. Paper YAML is not loaded by Forge.
4. Adapt `lightweightclans-webhook.EXAMPLE.json` into the NEW `config/lightweightclans-webhook.json`. Replace `REPLACE_WITH_KNC_CLANS_WEBHOOK_SECRET` with the retained clan secret matching the website's `KNC_CLANS_WEBHOOK_SECRET`. Leave enabled=true. The new file belongs to LightweightClans itself, not WebhookIntegrations. Old Paper `clansWebhook` settings have no effect on these Forge mods.
5. Start the server. Look for `Signed clan website synchronization enabled.` and verify that neither mod reports HTTP 401. The authenticated clan snapshot is sent on startup, after clan commands change data, after a member name changes on login, and every 7200 seconds. Members, descriptions, leadership, colors and banner patterns are included. Disbanding the last clan sends an empty full snapshot.
6. Join and leave normally. Check https://beautyinblocks.com/api/kncraft/recent-activity and the KNCraft home page. Allow up to approximately a minute for distributed KV/cache propagation. Check https://beautyinblocks.com/api/kncraft/clans and the KNCraft Clans page. With no clans in the server database, an empty directory is expected, but updatedAt should be set after the startup sync.

Changing activity JSON supports `/wi reload`; replacing JARs and changing the new clan webhook file require restart. Do not use `/wi send` as an authenticated activity test: it remains a plain Discord-style manual message without event credentials. No extra clan sender/bridge should be enabled: this release sends its own snapshots.

## Validation completed

- Forge activity tests verify authentication headers on initial delivery and retry, JSON escaping, configuration validation, and existing behavior.
- Clan tests verify the supplied public HMAC vector, actual HTTP headers/signatures, membership/banner serialization, duplicate suppression and empty snapshots.
- Both final packaged JARs loaded together on a disposable local Forge 47.4.0 server. Authenticated activity and a signed startup clan snapshot reached an isolated localhost receiver, followed by a clean shutdown.
- Java-generated activity and clan JSON were accepted by the website's actual handlers in memory, with names, members and banner patterns preserved and all keys scoped to KNCraft.
- No test data was sent to production. Installation and real player/clan changes on the production server are still required for end-to-end acceptance.

Activity delivery has three attempts for transient failures. Clan delivery has five attempts with 30-second delays and a fresh signing timestamp per attempt; the body occurrence time stays stable. Queues are bounded and in memory; periodic full sync reconciles clan state after failed deliveries. Large snapshots over 1 MiB are rejected locally with a warning. No tokens, signatures or response bodies are logged. Outbound redirects are not followed.

Clan webhook configuration requires HTTPS except for numeric 127.0.0.1, which is permitted for isolated local tests. Missing/invalid enabled settings disable only website synchronization and log a warning; clan gameplay remains available.

SHA256SUMS.txt identifies the exact packaged JARs. The source changes remain in the local WebhookIntegrations-BIB and Custom-Clan-Plugin repositories, uncommitted. This is a local prerelease build; no GitHub release has been published.
