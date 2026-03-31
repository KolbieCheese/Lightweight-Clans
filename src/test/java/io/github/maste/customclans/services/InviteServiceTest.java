package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteDatabase;
import io.github.maste.customclans.util.ActionResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InviteServiceTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabase database;
    private SQLiteClanRepository clanRepository;
    private SQLiteClanMemberRepository memberRepository;
    private SQLiteClanInviteRepository inviteRepository;
    private ChatService chatService;
    private InviteService inviteService;

    @BeforeEach
    void setUp() throws Exception {
        database = new SQLiteDatabase(tempDir.resolve("invite-service.db"), java.util.logging.Logger.getLogger("test"));
        database.initialize();
        clanRepository = new SQLiteClanRepository(database);
        memberRepository = new SQLiteClanMemberRepository(database);
        inviteRepository = new SQLiteClanInviteRepository(database);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        PluginConfig pluginConfig = createPluginConfig(plugin);
        chatService = new ChatService(
                plugin,
                pluginConfig,
                memberRepository,
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );
        inviteService = new InviteService(
                plugin,
                pluginConfig,
                clanRepository,
                memberRepository,
                inviteRepository,
                chatService
        );
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void memberCanInviteAnotherPlayer() {
        Player president = mockPlayer("Alice", true);
        Player member = mockPlayer("Bob", true);
        Player target = mockPlayer("Charlie", true);

        Clan clan = clanRepository.createClan(
                president.getUniqueId(),
                president.getName(),
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join().clan();
        inviteRepository.createInvite(
                new ClanInvite(clan.id(), member.getUniqueId(), president.getUniqueId(), Instant.now().plusSeconds(300)),
                Instant.now()
        ).join();
        inviteService.acceptInvite(member, "Azure Guard").join();

        ActionResult<ClanInvite> result = inviteService.sendInvite(member, target).join();

        assertTrue(result.success());
        assertEquals("invite.sent", result.messageKey());
        assertTrue(inviteRepository.findByClanIdAndInvitedPlayerUuid(clan.id(), target.getUniqueId()).join().isPresent());
    }

    @Test
    void acceptInviteMatchesClanNameCaseInsensitively() {
        Player president = mockPlayer("Alice", true);
        Player target = mockPlayer("Bob", true);

        clanRepository.createClan(
                president.getUniqueId(),
                president.getName(),
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();
        chatService.refreshSnapshot(president.getUniqueId()).join();
        inviteService.sendInvite(president, target).join();

        ActionResult<Clan> result = inviteService.acceptInvite(target, "azure guard").join();

        assertTrue(result.success());
        assertEquals("Azure Guard", result.value().name());
    }

    @Test
    void denyInviteRemovesOnlyMatchingInvite() {
        Player firstPresident = mockPlayer("Alice", true);
        Player secondPresident = mockPlayer("Carol", true);
        Player target = mockPlayer("Bob", true);

        Clan firstClan = clanRepository.createClan(
                firstPresident.getUniqueId(),
                firstPresident.getName(),
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join().clan();
        chatService.refreshSnapshot(firstPresident.getUniqueId()).join();
        Clan secondClan = clanRepository.createClan(
                secondPresident.getUniqueId(),
                secondPresident.getName(),
                "Crimson Guard",
                "CG",
                "red",
                Instant.now()
        ).join().clan();
        chatService.refreshSnapshot(secondPresident.getUniqueId()).join();

        inviteService.sendInvite(firstPresident, target).join();
        inviteService.sendInvite(secondPresident, target).join();

        ActionResult<Void> result = inviteService.denyInvite(target, "azure guard").join();

        assertTrue(result.success());
        assertFalse(inviteRepository.findByClanIdAndInvitedPlayerUuid(firstClan.id(), target.getUniqueId()).join().isPresent());
        assertTrue(inviteRepository.findByClanIdAndInvitedPlayerUuid(secondClan.id(), target.getUniqueId()).join().isPresent());
    }

    private PluginConfig createPluginConfig(JavaPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("max-clan-name-length", 24);
        yaml.set("max-clan-tag-length", 6);
        yaml.set("default-clan-tag-color", "white");
        yaml.set("invite-expiration-seconds", 300);
        yaml.set("max-clan-size", 20);
        yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-enabled", true);
        yaml.set("clan-chat-toggle-enabled", true);
        yaml.set("discordsrv-clan-chat-relay.enabled", false);
        yaml.set("discordsrv-clan-chat-relay.channel", "global");
        yaml.set("discordsrv-clan-chat-relay.format", "[{clan}] {user}: {message}");
        when(plugin.getConfig()).thenReturn(yaml);
        return PluginConfig.load(plugin);
    }

    private Player mockPlayer(String name, boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(online);
        return player;
    }
}
