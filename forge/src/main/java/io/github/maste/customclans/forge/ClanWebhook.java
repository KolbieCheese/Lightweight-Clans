package io.github.maste.customclans.forge;

import com.google.gson.*;
import io.github.maste.customclans.models.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Independent Forge sender: no Bukkit service or Paper bridge is required. */
final class ClanWebhook implements AutoCloseable {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final class Config {
        boolean enabled = false;
        String endpoint = "https://beautyinblocks.com/api/kncraft/clans-webhook";
        String secret = "";
        String serverId = "kncraft";
        int periodicFullSyncSeconds = 7200;
        static Config load(Path path) throws Exception {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, JSON.toJson(new Config()));
            }
            Config c = JSON.fromJson(Files.readString(path), Config.class);
            if (c == null || c.serverId == null || !c.serverId.equals("kncraft") || c.periodicFullSyncSeconds < 30)
                throw new IllegalArgumentException("Invalid clan webhook settings");
            if (c.enabled) {
                URI uri = URI.create(c.endpoint);
                boolean loopback = "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
                if ((!"https".equals(uri.getScheme()) && !loopback) || uri.getHost() == null || uri.getUserInfo() != null || c.secret == null || c.secret.isBlank())
                    throw new IllegalArgumentException("Enabled clan webhook requires HTTPS endpoint and secret");
            }
            return c;
        }
    }
    private final Config config;
    private final Consumer<String> warning;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256), r -> { Thread t = new Thread(r, "LightweightClans-webhook"); t.setDaemon(true); return t; });
    private String lastSnapshot;
    ClanWebhook(Config config, Consumer<String> warning) { this.config = config; this.warning = warning; }

    static JsonObject clan(Clan c, List<ClanMember> members) {
        JsonObject out = new JsonObject();
        out.addProperty("id", c.id()); out.addProperty("name", c.name()); out.addProperty("slug", c.slug());
        out.addProperty("tag", c.tag()); out.addProperty("tagColor", c.tagColor()); out.addProperty("description", c.description());
        out.addProperty("presidentUuid", c.presidentUuid().toString());
        out.addProperty("presidentName", members.stream().filter(m -> m.playerUuid().equals(c.presidentUuid())).map(ClanMember::lastKnownName).findFirst().orElse(""));
        out.addProperty("createdAt", c.createdAt().toString()); out.addProperty("updatedAt", c.updatedAt().toString());
        out.addProperty("memberCount", members.size());
        JsonArray list = new JsonArray();
        members.stream().sorted(Comparator.comparing(m -> m.playerUuid().toString())).forEach(m -> {
            JsonObject member = new JsonObject();
            member.addProperty("playerUuid", m.playerUuid().toString()); member.addProperty("lastKnownName", m.lastKnownName());
            member.addProperty("role", m.role().name()); member.addProperty("joinedAt", m.joinedAt().toString()); list.add(member);
        });
        out.add("members", list);
        if (c.bannerData() != null) {
            JsonObject banner = new JsonObject();
            String material = c.bannerData().materialId().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            banner.addProperty("baseMaterial", material); banner.addProperty("baseColor", material.replace("_banner", ""));
            JsonArray patterns = new JsonArray();
            c.bannerData().patterns().forEach(p -> {
                JsonObject pattern = new JsonObject(); pattern.addProperty("patternId", p.patternId()); pattern.addProperty("colorId", p.colorId()); patterns.add(pattern);
            });
            banner.add("patterns", patterns); out.add("banner", banner);
        }
        return out;
    }

    // Called only from the clan command executor, after a consistent database read.
    void snapshot(JsonArray clans, boolean force) {
        if (!config.enabled) return;
        String content = clans.toString();
        if (!force && content.equals(lastSnapshot)) return;
        JsonObject body = new JsonObject();
        body.addProperty("event", "clan.snapshot"); body.addProperty("serverId", config.serverId);
        body.addProperty("occurredAt", Instant.now().toString()); body.add("clans", clans);
        String raw = body.toString();
        if (raw.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            warning.accept("Clan snapshot exceeds receiver's 1 MiB limit; snapshot not queued."); return;
        }
        try { worker.execute(() -> deliver(raw)); lastSnapshot = content; }
        catch (RejectedExecutionException e) { warning.accept("Clan webhook queue full or stopping; next full sync will retry current state."); }
    }
    static String signature(String secret, String timestamp, String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot sign clan webhook", e); }
    }
    void deliver(String raw) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                String timestamp = Instant.now().toString();
                var request = HttpRequest.newBuilder(URI.create(config.endpoint)).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Minecraft-Server", config.serverId)
                    .header("X-Webhook-Source", "lightweight-clans")
                    .header("X-Webhook-Event", "clan.snapshot")
                    .header("X-Webhook-Timestamp", timestamp)
                    .header("X-Webhook-Signature", signature(config.secret, timestamp, raw))
                    .POST(HttpRequest.BodyPublishers.ofString(raw, StandardCharsets.UTF_8)).build();
                int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 200 && status < 300) return;
                if (status < 500 && status != 408 && status != 429) {
                    warning.accept("Clan webhook rejected (HTTP " + status + "). Check endpoint, secret and server clock."); return;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            catch (Exception e) { if (attempt == 4) warning.accept("Clan webhook failed: " + e.getClass().getSimpleName()); }
            if (attempt < 4) try { Thread.sleep(30000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        warning.accept("Clan webhook exhausted retries; next full sync will retry current state.");
    }
    @Override public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(15, TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch (InterruptedException e) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
