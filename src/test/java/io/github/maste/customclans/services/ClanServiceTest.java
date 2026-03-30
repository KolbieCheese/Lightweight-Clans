package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.util.ActionResult;
import java.nio.file.Path;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClanServiceTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabaseHolder holder;
    private ClanService clanService;
    private ChatService chatService;

    @BeforeEach
    void setUp() throws Exception {
        holder = new SQLiteDatabaseHolder(tempDir);
        PluginConfig pluginConfig = holder.pluginConfig();
        chatService = new ChatService(holder.plugin(), pluginConfig, holder.memberRepository(), MiniMessage.miniMessage());
        clanService = new ClanService(pluginConfig, holder.clanRepository(), holder.memberRepository(), chatService);
    }

    @AfterEach
    void tearDown() {
        holder.close();
    }

    @Test
    void createClanRefreshesPlayerSnapshot() {
        Player player = mockPlayer("Alice");

        ActionResult<Clan> result = clanService.createClan(player, "Crimson Knights").join();

        assertTrue(result.success());
        assertTrue(chatService.cachedSnapshot(player.getUniqueId()).isPresent());
        assertEquals("create.success", result.messageKey());
    }

    @Test
    void createClanMakesCreatorPresident() {
        Player player = mockPlayer("Alice");

        clanService.createClan(player, "Crimson Knights").join();

        assertEquals(
                ClanRole.PRESIDENT,
                chatService.cachedSnapshot(player.getUniqueId()).orElseThrow().role()
        );
    }

    @Test
    void presidentCannotLeaveTheirClan() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.leaveClan(player).join();

        assertEquals("leave.president-cannot-leave", result.messageKey());
        assertTrue(chatService.cachedSnapshot(player.getUniqueId()).isPresent());
    }

    @Test
    void publicLookupFindsClanCaseInsensitively() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<io.github.maste.customclans.models.ClanInfo> result = clanService.getClanInfo("crimson knights").join();

        assertTrue(result.success());
        assertEquals("Crimson Knights", result.value().clan().name());
        assertEquals("Alice", result.value().presidentName());
    }

    @Test
    void publicLookupFailsForUnknownClan() {
        ActionResult<io.github.maste.customclans.models.ClanInfo> result = clanService.getClanInfo("Missing Clan").join();

        assertFalse(result.success());
        assertEquals("lookup.not-found", result.messageKey());
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static final class SQLiteDatabaseHolder {

        private final io.github.maste.customclans.repositories.sqlite.SQLiteDatabase database;
        private final io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository clanRepository;
        private final io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository memberRepository;
        private final PluginConfig pluginConfig;
        private final JavaPlugin plugin;

        private SQLiteDatabaseHolder(Path tempDir) throws Exception {
            this.database = new io.github.maste.customclans.repositories.sqlite.SQLiteDatabase(
                    tempDir.resolve("service.db"),
                    java.util.logging.Logger.getLogger("test")
            );
            this.database.initialize();
            this.clanRepository = new io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository(database);
            this.memberRepository = new io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository(database);
            this.plugin = mock(JavaPlugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            this.pluginConfig = new TestPluginConfigFactory().create(plugin);
        }

        private io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository clanRepository() {
            return clanRepository;
        }

        private io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository memberRepository() {
            return memberRepository;
        }

        private PluginConfig pluginConfig() {
            return pluginConfig;
        }

        private JavaPlugin plugin() {
            return plugin;
        }

        private void close() {
            database.close();
        }
    }

    private static final class TestPluginConfigFactory {

        private PluginConfig create(JavaPlugin plugin) {
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.set("max-clan-name-length", 24);
            yaml.set("max-clan-tag-length", 6);
            yaml.set("default-clan-tag-color", "gold");
            yaml.set("invite-expiration-seconds", 300);
            yaml.set("max-clan-size", 20);
            yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-enabled", true);
            yaml.set("clan-chat-toggle-enabled", true);
            yaml.set("debug", false);
            when(plugin.getConfig()).thenReturn(yaml);
            return PluginConfig.load(plugin);
        }
    }
}
