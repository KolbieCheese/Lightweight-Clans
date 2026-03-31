package io.github.maste.customclans.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteDatabase;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.services.InviteService;
import io.github.maste.customclans.services.api.LightweightClansApiImpl;
import io.github.maste.customclans.util.ActionResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LightweightClansApiImplIntegrationTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabaseHolder holder;
    private LightweightClansApiImpl api;
    private ChatService chatService;
    private InviteService inviteService;

    @BeforeEach
    void setUp() throws Exception {
        holder = new SQLiteDatabaseHolder(tempDir);
        chatService = new ChatService(
                holder.plugin,
                holder.pluginConfig,
                holder.memberRepository,
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );
        inviteService = new InviteService(
                holder.plugin,
                holder.pluginConfig,
                holder.clanRepository,
                holder.memberRepository,
                holder.inviteRepository,
                chatService
        );
        api = new LightweightClansApiImpl(holder.clanRepository, holder.memberRepository);
    }

    @AfterEach
    void tearDown() {
        holder.close();
    }

    @Test
    void getAllClansExportsCompleteSnapshotIncludingBannerAndMembership() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");

        Clan clan = holder.clanRepository
                .createClan(president.getUniqueId(), president.getName(), "Crimson Knights", "CK", "#FFAA00", Instant.now())
                .join()
                .clan();

        holder.clanRepository.updateClanDescription(clan.id(), "We keep the realm safe.").join();
        holder.clanRepository.updateClanBanner(
                clan.id(),
                "RED_BANNER",
                "[{\"pattern\":\"BORDER\",\"color\":\"BLACK\"},{\"pattern\":\"STRIPE_CENTER\",\"color\":\"WHITE\"}]"
        ).join();

        chatService.refreshSnapshot(president.getUniqueId()).join();
        holder.inviteRepository.createInvite(
                new io.github.maste.customclans.models.ClanInvite(clan.id(), member.getUniqueId(), president.getUniqueId(), Instant.now().plusSeconds(300)),
                Instant.now()
        ).join();
        ActionResult<Clan> accepted = inviteService.acceptInvite(member, "Crimson Knights").join();
        assertTrue(accepted.success());

        List<ClanSnapshot> allClans = api.getAllClans();
        assertEquals(1, allClans.size());

        ClanSnapshot snapshot = allClans.getFirst();
        assertEquals(clan.id(), snapshot.id());
        assertEquals("Crimson Knights", snapshot.name());
        assertEquals("crimson knights", snapshot.normalizedName());
        assertEquals("CK", snapshot.tag());
        assertEquals("#FFAA00", snapshot.tagColor());
        assertEquals("We keep the realm safe.", snapshot.description());
        assertEquals(president.getUniqueId(), snapshot.presidentUuid());
        assertEquals("Alice", snapshot.presidentName());
        assertEquals(2, snapshot.memberCount());
        assertEquals(2, snapshot.members().size());
        assertNotNull(snapshot.createdAt());

        assertNotNull(snapshot.banner());
        assertEquals("minecraft:red_banner", snapshot.banner().baseMaterial());
        assertEquals("red", snapshot.banner().baseColor());
        assertEquals(2, snapshot.banner().patterns().size());

        BannerPatternSnapshot firstPattern = snapshot.banner().patterns().get(0);
        BannerPatternSnapshot secondPattern = snapshot.banner().patterns().get(1);
        assertEquals("minecraft:border", firstPattern.patternId());
        assertEquals("black", firstPattern.colorId());
        assertEquals("minecraft:stripe_center", secondPattern.patternId());
        assertEquals("white", secondPattern.colorId());
    }

    @Test
    void getClanByNameIsCaseInsensitiveAndReturnsExpectedClan() {
        Player president = mockPlayer("Alice");
        holder.clanRepository
                .createClan(president.getUniqueId(), president.getName(), "Crimson Knights", "CK", "white", Instant.now())
                .join();

        ClanSnapshot snapshot = api.getClanByName("cRiMsOn kNiGhTs").orElseThrow();

        assertEquals("Crimson Knights", snapshot.name());
        assertEquals("Alice", snapshot.presidentName());
    }

    @Test
    void snapshotsExposeDefensiveCopiesForMembersAndBannerPatterns() {
        Player president = mockPlayer("Alice");
        Clan clan = holder.clanRepository
                .createClan(president.getUniqueId(), president.getName(), "Crimson Knights", "CK", "white", Instant.now())
                .join()
                .clan();
        holder.clanRepository.updateClanBanner(
                clan.id(),
                "BLUE_BANNER",
                "[{\"pattern\":\"BORDER\",\"color\":\"BLACK\"}]"
        ).join();

        ClanSnapshot snapshot = api.getClanById(clan.id()).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.members().add(null));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.banner().patterns().add(new BannerPatternSnapshot("x", "y")));

        ClanSnapshot reread = api.getClanById(clan.id()).orElseThrow();
        assertEquals(1, reread.members().size());
        assertEquals(1, reread.banner().patterns().size());
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        return player;
    }

    private Player mockOnlinePlayer(String name) {
        Player player = mockPlayer(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static final class SQLiteDatabaseHolder {
        private final SQLiteDatabase database;
        private final SQLiteClanRepository clanRepository;
        private final SQLiteClanMemberRepository memberRepository;
        private final SQLiteClanInviteRepository inviteRepository;
        private final PluginConfig pluginConfig;
        private final JavaPlugin plugin;

        private SQLiteDatabaseHolder(Path tempDir) throws Exception {
            this.database = new SQLiteDatabase(tempDir.resolve("api.db"), java.util.logging.Logger.getLogger("test"));
            this.database.initialize();
            this.clanRepository = new SQLiteClanRepository(database);
            this.memberRepository = new SQLiteClanMemberRepository(database);
            this.inviteRepository = new SQLiteClanInviteRepository(database);
            this.plugin = mock(JavaPlugin.class);
            when(plugin.isEnabled()).thenReturn(true);

            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.set("max-clan-name-length", 24);
            yaml.set("max-clan-tag-length", 6);
            yaml.set("default-clan-tag-color", "white");
            yaml.set("invite-expiration-seconds", 300);
            yaml.set("max-clan-size", 20);
            yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-enabled", true);
            yaml.set("clan-chat-toggle-enabled", true);
            when(plugin.getConfig()).thenReturn(yaml);
            this.pluginConfig = PluginConfig.load(plugin);
        }

        private void close() {
            database.close();
        }
    }
}
