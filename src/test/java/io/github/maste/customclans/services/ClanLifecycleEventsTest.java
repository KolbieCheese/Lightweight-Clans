package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.api.event.ClanBannerUpdatedEvent;
import io.github.maste.customclans.api.event.ClanCreatedEvent;
import io.github.maste.customclans.api.event.ClanDeletedEvent;
import io.github.maste.customclans.api.event.ClanMemberJoinedEvent;
import io.github.maste.customclans.api.event.ClanMemberKickedEvent;
import io.github.maste.customclans.api.event.ClanMemberLeftEvent;
import io.github.maste.customclans.api.event.ClanPresidentTransferredEvent;
import io.github.maste.customclans.api.event.ClanUpdatedEvent;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteDatabase;
import io.github.maste.customclans.services.api.LightweightClansApiImpl;
import io.github.maste.customclans.util.ActionResult;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClanLifecycleEventsTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabaseHolder holder;
    private ClanService clanService;
    private InviteService inviteService;
    private LightweightClansApiImpl api;

    @BeforeEach
    void setUp() throws Exception {
        holder = new SQLiteDatabaseHolder(tempDir);
        installBukkitServer(holder.server);

        ChatService chatService = new ChatService(
                holder.plugin,
                holder.pluginConfig,
                holder.memberRepository,
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );

        clanService = new ClanService(
                holder.plugin,
                holder.pluginConfig,
                holder.clanRepository,
                holder.memberRepository,
                chatService
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
    void tearDown() throws Exception {
        installBukkitServer(null);
        holder.close();
    }

    @Test
    void lifecycleEventsFireWithCorrectBeforeAfterPayloadsAfterPersistence() {
        Player alice = mockOnlinePlayer("Alice");
        Player bob = mockOnlinePlayer("Bob");
        Player charlie = mockOnlinePlayer("Charlie");

        holder.capturedEvents.clear();
        holder.persistenceVerifiedEvents.clear();
        holder.verifyPersistenceOnEvent = true;

        ActionResult<Clan> created = clanService.createClan(alice, "Crimson Knights").join();
        assertTrue(created.success());

        inviteService.sendInvite(alice, bob).join();
        inviteService.acceptInvite(bob, "Crimson Knights").join();

        clanService.updateDescription(alice, "Updated description").join();
        clanService.setClanBanner(mockPresidentHoldingBanner(alice)).join();

        clanService.transferLeadership(alice, "Bob").join();
        clanService.kickMember(bob, "Alice").join();

        inviteService.sendInvite(bob, charlie).join();
        inviteService.acceptInvite(charlie, "Crimson Knights").join();
        clanService.leaveClan(charlie).join();

        clanService.disbandClan(bob).join();

        assertTrue(holder.capturedEvents.stream().anyMatch(ClanCreatedEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanMemberJoinedEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanMemberLeftEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanMemberKickedEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanPresidentTransferredEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanBannerUpdatedEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanDeletedEvent.class::isInstance));
        assertTrue(holder.capturedEvents.stream().anyMatch(ClanUpdatedEvent.class::isInstance));

        ClanCreatedEvent createdEvent = firstEvent(ClanCreatedEvent.class);
        assertEquals("Crimson Knights", createdEvent.getClan().name());
        assertEquals(1, createdEvent.getClan().memberCount());

        ClanMemberJoinedEvent joinedEvent = firstEvent(ClanMemberJoinedEvent.class);
        assertEquals(1, joinedEvent.getBefore().memberCount());
        assertEquals(2, joinedEvent.getAfter().memberCount());
        assertEquals("Bob", joinedEvent.getMember().lastKnownName());

        ClanBannerUpdatedEvent bannerEvent = firstEvent(ClanBannerUpdatedEvent.class);
        assertEquals(2, bannerEvent.getBefore().memberCount());
        assertNotNull(bannerEvent.getAfter().banner());
        assertEquals("minecraft:red_banner", bannerEvent.getAfter().banner().baseMaterial());
        assertEquals(2, bannerEvent.getAfter().banner().patterns().size());

        ClanPresidentTransferredEvent transferEvent = firstEvent(ClanPresidentTransferredEvent.class);
        assertEquals("Alice", transferEvent.getPreviousPresident().lastKnownName());
        assertEquals("Bob", transferEvent.getNewPresident().lastKnownName());
        assertEquals(transferEvent.getBefore().presidentUuid(), alice.getUniqueId());
        assertEquals(transferEvent.getAfter().presidentUuid(), bob.getUniqueId());

        ClanMemberKickedEvent kickedEvent = firstEvent(ClanMemberKickedEvent.class);
        assertEquals("Alice", kickedEvent.getKickedMember().lastKnownName());
        assertEquals("Bob", kickedEvent.getActor().lastKnownName());
        assertEquals(2, kickedEvent.getBefore().memberCount());
        assertEquals(1, kickedEvent.getAfter().memberCount());

        ClanMemberLeftEvent leftEvent = firstEvent(ClanMemberLeftEvent.class);
        assertEquals("Charlie", leftEvent.getMember().lastKnownName());
        assertEquals(2, leftEvent.getBefore().memberCount());
        assertEquals(1, leftEvent.getAfter().memberCount());

        ClanDeletedEvent deletedEvent = firstEvent(ClanDeletedEvent.class);
        assertEquals("Crimson Knights", deletedEvent.getDeletedClan().name());

        assertFalse(holder.persistenceVerifiedEvents.isEmpty());
    }

    @Test
    void eventsUseMainThreadDispatchPathWhenTriggeredOffThread() {
        holder.primaryThread.set(false);
        Player alice = mockOnlinePlayer("Alice");

        clanService.createClan(alice, "Crimson Knights").join();

        verify(holder.scheduler).runTask(eq(holder.plugin), any(Runnable.class));
        assertTrue(holder.capturedEvents.isEmpty());

        Runnable scheduled = holder.scheduledTask.get();
        assertNotNull(scheduled);
        holder.primaryThread.set(true);
        scheduled.run();

        assertEquals(1, holder.capturedEvents.size());
        assertInstanceOf(ClanCreatedEvent.class, holder.capturedEvents.getFirst());
    }

    @Test
    void eventsCallPluginManagerImmediatelyWhenAlreadyOnMainThread() {
        holder.primaryThread.set(true);
        Player alice = mockOnlinePlayer("Alice");

        clanService.createClan(alice, "Crimson Knights").join();

        verify(holder.scheduler, never()).runTask(eq(holder.plugin), any(Runnable.class));
        assertEquals(1, holder.capturedEvents.size());
        assertInstanceOf(ClanCreatedEvent.class, holder.capturedEvents.getFirst());
    }

    private <T extends Event> T firstEvent(Class<T> type) {
        return holder.capturedEvents.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private Player mockOnlinePlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private Player mockPresidentHoldingBanner(Player president) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack bannerStack = mock(ItemStack.class);
        BannerMeta bannerMeta = mock(BannerMeta.class);
        when(bannerMeta.getPatterns()).thenReturn(List.of(
                new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, PatternType.BORDER),
                new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.WHITE, PatternType.STRIPE_CENTER)
        ));
        when(bannerStack.getAmount()).thenReturn(1);
        when(bannerStack.getType()).thenReturn(Material.RED_BANNER);
        when(bannerStack.getItemMeta()).thenReturn(bannerMeta);
        when(inventory.getItemInMainHand()).thenReturn(bannerStack);
        when(president.getInventory()).thenReturn(inventory);
        return president;
    }

    private static void installBukkitServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    private final class SQLiteDatabaseHolder {
        private final SQLiteDatabase database;
        private final SQLiteClanRepository clanRepository;
        private final SQLiteClanMemberRepository memberRepository;
        private final SQLiteClanInviteRepository inviteRepository;
        private final PluginConfig pluginConfig;

        private final JavaPlugin plugin;
        private final Server server;
        private final BukkitScheduler scheduler;
        private final PluginManager pluginManager;
        private final AtomicBoolean primaryThread = new AtomicBoolean(true);
        private final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();

        private final List<Event> capturedEvents = new ArrayList<>();
        private final List<Class<? extends Event>> persistenceVerifiedEvents = new ArrayList<>();
        private boolean verifyPersistenceOnEvent;

        private SQLiteDatabaseHolder(Path root) throws Exception {
            this.database = new SQLiteDatabase(root.resolve("events.db"), java.util.logging.Logger.getLogger("test"));
            this.database.initialize();
            this.clanRepository = new SQLiteClanRepository(database);
            this.memberRepository = new SQLiteClanMemberRepository(database);
            this.inviteRepository = new SQLiteClanInviteRepository(database);

            this.plugin = mock(JavaPlugin.class);
            this.server = mock(Server.class);
            this.scheduler = mock(BukkitScheduler.class);
            this.pluginManager = mock(PluginManager.class);

            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.isPrimaryThread()).thenAnswer(invocation -> primaryThread.get());
            doAnswer(invocation -> {
                scheduledTask.set(invocation.getArgument(1));
                return mock(BukkitTask.class);
            }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                capturedEvents.add(event);
                if (verifyPersistenceOnEvent) {
                    verifyPersistedStateAtEventTime(event);
                }
                return null;
            }).when(pluginManager).callEvent(any(Event.class));

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
            when(plugin.getConfig()).thenReturn(yaml);
            this.pluginConfig = PluginConfig.load(plugin);
        }

        private void verifyPersistedStateAtEventTime(Event event) {
            if (event instanceof ClanCreatedEvent createdEvent) {
                assertTrue(api.getClanById(createdEvent.getClan().id()).isPresent());
                persistenceVerifiedEvents.add(ClanCreatedEvent.class);
                return;
            }

            if (event instanceof ClanDeletedEvent deletedEvent) {
                assertTrue(api.getClanById(deletedEvent.getDeletedClan().id()).isEmpty());
                persistenceVerifiedEvents.add(ClanDeletedEvent.class);
                return;
            }

            if (event instanceof ClanMemberJoinedEvent joinedEvent) {
                assertPersistedAfterSnapshot(joinedEvent.getAfter());
                persistenceVerifiedEvents.add(ClanMemberJoinedEvent.class);
                return;
            }

            if (event instanceof ClanMemberLeftEvent leftEvent) {
                assertPersistedAfterSnapshot(leftEvent.getAfter());
                persistenceVerifiedEvents.add(ClanMemberLeftEvent.class);
                return;
            }

            if (event instanceof ClanMemberKickedEvent kickedEvent) {
                assertPersistedAfterSnapshot(kickedEvent.getAfter());
                persistenceVerifiedEvents.add(ClanMemberKickedEvent.class);
                return;
            }

            if (event instanceof ClanPresidentTransferredEvent transferredEvent) {
                assertPersistedAfterSnapshot(transferredEvent.getAfter());
                persistenceVerifiedEvents.add(ClanPresidentTransferredEvent.class);
                return;
            }

            if (event instanceof ClanBannerUpdatedEvent bannerUpdatedEvent) {
                assertPersistedAfterSnapshot(bannerUpdatedEvent.getAfter());
                persistenceVerifiedEvents.add(ClanBannerUpdatedEvent.class);
                return;
            }

            if (event instanceof ClanUpdatedEvent updatedEvent) {
                assertPersistedAfterSnapshot(updatedEvent.getAfter());
                persistenceVerifiedEvents.add(ClanUpdatedEvent.class);
            }
        }

        private void assertPersistedAfterSnapshot(ClanSnapshot after) {
            ClanSnapshot persisted = api.getClanById(after.id()).orElseThrow();
            assertEquals(after.id(), persisted.id());
            assertEquals(after.name(), persisted.name());
            assertEquals(after.memberCount(), persisted.memberCount());
            assertEquals(after.presidentUuid(), persisted.presidentUuid());
        }

        private void close() {
            database.close();
        }
    }
}
