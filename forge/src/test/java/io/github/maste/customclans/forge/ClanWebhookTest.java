package io.github.maste.customclans.forge;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.maste.customclans.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
class ClanWebhookTest {
    @TempDir Path directory;
    @Test void exactHandoffSignatureVector() {
        assertEquals("sha256=f95c11fb126538745d06adc0156434ef099d5f6afbd49d81d2d2987b2c9be443",
            ClanWebhook.signature("PUBLIC-TEST-KEY-DO-NOT-USE-IN-PRODUCTION", "2026-09-13T23:00:00Z", "{\"event\":\"contract-test\",\"serverId\":\"kncraft\"}"));
    }
    @Test void configDefaultsDisabledAndRejectsIncompleteEnabledSettings() throws Exception {
        Path file = directory.resolve("config.json");
        var settings = ClanWebhook.Config.load(file); assertFalse(settings.enabled);
        settings.enabled = true; Files.writeString(file, ClanWebhook.JSON.toJson(settings));
        assertThrows(IllegalArgumentException.class, () -> ClanWebhook.Config.load(file));
        settings.secret = "test-only"; Files.writeString(file, ClanWebhook.JSON.toJson(settings));
        assertTrue(ClanWebhook.Config.load(file).enabled);
    }
    @Test void signsRealSnapshotsAndPreservesMembersBannersAndEmptyReplacement() throws Exception {
        var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var now = Instant.now();
        var clan = new Clan(42, "Contract Builders", "contract-builders", "TEST", "white", "Escaped \"quote\"", 
            new ClanBannerData("minecraft:red_banner", List.of(new ClanBannerData.PatternSpec("minecraft:stripe_bottom", "white"))), id, now, now);
        JsonArray clans = new JsonArray(); clans.add(ClanWebhook.clan(clan, List.of(new ClanMember(42,id,"ContractPlayer",ClanRole.PRESIDENT,now))));
        List<String> bodies = new CopyOnWriteArrayList<>(); List<String> signatures = new CopyOnWriteArrayList<>(); List<String> timestamps = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/hook", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            signatures.add(exchange.getRequestHeaders().getFirst("X-Webhook-Signature"));
            timestamps.add(exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp"));
            exchange.sendResponseHeaders(202,-1); exchange.close();
        }); server.start();
        var config = new ClanWebhook.Config(); config.enabled=true; config.secret="test-only-key";
        config.endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/hook";
        try (var sender = new ClanWebhook(config, message -> fail(message))) {
            sender.snapshot(clans,false); sender.snapshot(clans,false); sender.snapshot(new JsonArray(),false);
        } finally {server.stop(0);}
        assertEquals(2,bodies.size());
        for(int i=0;i<bodies.size();i++) assertEquals(ClanWebhook.signature(config.secret,timestamps.get(i),bodies.get(i)),signatures.get(i));
        var payload=JsonParser.parseString(bodies.get(0)).getAsJsonObject();
        assertEquals("kncraft",payload.get("serverId").getAsString());
        var record=payload.getAsJsonArray("clans").get(0).getAsJsonObject();
        assertEquals("red_banner",record.getAsJsonObject("banner").get("baseMaterial").getAsString());
        assertEquals("ContractPlayer",record.get("presidentName").getAsString());
        assertEquals(0,JsonParser.parseString(bodies.get(1)).getAsJsonObject().getAsJsonArray("clans").size());
        Path fixture=Path.of("build/webhook-contract/clan-snapshot.json"); Files.createDirectories(fixture.getParent()); Files.writeString(fixture,bodies.get(0));
    }
}
