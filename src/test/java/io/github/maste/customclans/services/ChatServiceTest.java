package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.api.event.ClanChatMessageEvent;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.ClanChatRelay;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.util.ActionResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ChatServiceTest {

    @Test
    void sendClanChatFailsWhenClanChatIsDisabled() {
        ChatService chatService = new ChatService(
                mock(JavaPlugin.class),
                createPluginConfig(false, true),
                mock(ClanMemberRepository.class),
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );

        Player player = mockPlayer("Alice");

        assertEquals("chat.unavailable", chatService.sendClanChat(player, "hello clan").join().messageKey());
    }

    @Test
    void toggleClanChatFailsWhenClanChatIsDisabled() {
        ChatService chatService = new ChatService(
                mock(JavaPlugin.class),
                createPluginConfig(false, true),
                mock(ClanMemberRepository.class),
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );

        Player player = mockPlayer("Alice");

        assertEquals("chat.unavailable", chatService.toggleClanChat(player).join().messageKey());
        assertFalse(chatService.shouldRouteToClanChat(player));
    }

    @Test
    void sendClanChatFiresIntegrationEventBeforeBroadcast() {
        ChatServiceHarness harness = createHarness(true);
        harness.stubClanMembership();

        ActionResult<Void> result = harness.chatService.sendClanChat(harness.sender, "hello clan").join();

        assertTrue(result.success());
        ClanChatMessageEvent event = harness.capturedClanChatEvent();
        assertEquals(harness.sender, event.getSender());
        assertEquals(harness.sender.getUniqueId(), event.getSenderUuid());
        assertEquals("Crimson Knights", event.getClanName());
        assertEquals("CK", event.getClanTag());
        assertEquals("hello clan", event.getPlainMessage());
        assertEquals(Component.text("hello clan"), event.getMessageComponent());
        assertFalse(event.isToggleRouted());
        assertEquals(List.of(harness.sender.getUniqueId(), harness.recipient.getUniqueId()), event.getRecipientUuids());

        InOrder inOrder = inOrder(harness.pluginManager, harness.sender, harness.recipient);
        inOrder.verify(harness.pluginManager).callEvent(any(ClanChatMessageEvent.class));
        inOrder.verify(harness.sender).sendMessage(any(Component.class));
        inOrder.verify(harness.recipient).sendMessage(any(Component.class));

        assertEquals(List.of("hello clan"), harness.relay.messages);
    }

    @Test
    void sendToggleRoutedClanChatFiresSameEventContractWithToggleFlag() {
        ChatServiceHarness harness = createHarness(true);
        harness.stubClanMembership();

        ActionResult<Void> result = harness.chatService.sendToggleRoutedClanChat(
                harness.sender,
                Component.text("hello from toggle")
        ).join();

        assertTrue(result.success());
        ClanChatMessageEvent event = harness.capturedClanChatEvent();
        assertTrue(event.isToggleRouted());
        assertEquals("hello from toggle", event.getPlainMessage());
        assertEquals(Component.text("hello from toggle"), event.getMessageComponent());
        assertEquals(List.of(harness.sender.getUniqueId(), harness.recipient.getUniqueId()), event.getRecipientUuids());
    }

    @Test
    void cancellingIntegrationEventPreventsBroadcastAndRelay() {
        ChatServiceHarness harness = createHarness(true);
        harness.stubClanMembership();
        doAnswer(invocation -> {
            ClanChatMessageEvent event = (ClanChatMessageEvent) invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(harness.pluginManager).callEvent(any(ClanChatMessageEvent.class));

        ActionResult<Void> result = harness.chatService.sendClanChat(harness.sender, "cancel me").join();

        assertTrue(result.success());
        verify(harness.sender, never()).sendMessage(any(Component.class));
        verify(harness.recipient, never()).sendMessage(any(Component.class));
        assertTrue(harness.relay.messages.isEmpty());
    }

    @Test
    void cancellingIntegrationEventCanStillRelayWhenConfigured() {
        ChatServiceHarness harness = createHarness(true, true);
        harness.stubClanMembership();
        doAnswer(invocation -> {
            ClanChatMessageEvent event = (ClanChatMessageEvent) invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(harness.pluginManager).callEvent(any(ClanChatMessageEvent.class));

        ActionResult<Void> result = harness.chatService.sendClanChat(harness.sender, "cancel but relay").join();

        assertTrue(result.success());
        verify(harness.sender, never()).sendMessage(any(Component.class));
        verify(harness.recipient, never()).sendMessage(any(Component.class));
        assertEquals(List.of("cancel but relay"), harness.relay.messages);
    }

    @Test
    void asyncOriginClanChatIsRescheduledBeforeEventAndBroadcast() {
        ChatServiceHarness harness = createHarness(false);
        harness.stubClanMembership();

        CompletableFuture<ActionResult<Void>> resultFuture = harness.chatService.sendClanChat(harness.sender, "queued");

        assertFalse(resultFuture.isDone());
        verify(harness.pluginManager, never()).callEvent(any(Event.class));
        verify(harness.sender, never()).sendMessage(any(Component.class));
        assertTrue(harness.scheduledTask.get() != null);

        harness.scheduledTask.get().run();

        assertTrue(resultFuture.join().success());
        ClanChatMessageEvent event = harness.capturedClanChatEvent();
        assertEquals("queued", event.getPlainMessage());
        verify(harness.sender).sendMessage(any(Component.class));
        verify(harness.recipient).sendMessage(any(Component.class));
        assertEquals(List.of("queued"), harness.relay.messages);
    }

    @Test
    void renderPublicChatColorsOnlyTheClanTag() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ClanMemberRepository clanMemberRepository = mock(ClanMemberRepository.class);
        ChatService chatService = new ChatService(
                plugin,
                createPluginConfig(true, true),
                clanMemberRepository,
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );

        Player player = mockPlayer("Alice");
        UUID playerUuid = player.getUniqueId();
        PlayerClanSnapshot snapshot = new PlayerClanSnapshot(
                1L,
                "Crimson Knights",
                "CK",
                "#FFAA00",
                ClanRole.PRESIDENT,
                playerUuid
        );
        when(clanMemberRepository.findSnapshotByPlayerUuid(playerUuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(snapshot)));

        chatService.refreshSnapshot(playerUuid).join();
        Component displayName = Component.text("Alicia").color(NamedTextColor.LIGHT_PURPLE);
        Component rendered = chatService.renderPublicChat(player, displayName, Component.text("hello"));

        assertEquals("[CK] Alicia: hello", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(4, rendered.children().size());
        assertEquals(net.kyori.adventure.text.format.TextColor.fromHexString("#FFAA00"), rendered.children().get(0).style().color());
        assertEquals(NamedTextColor.LIGHT_PURPLE, rendered.children().get(1).style().color());
        assertEquals(NamedTextColor.GRAY, rendered.children().get(2).style().color());
        assertNull(rendered.children().get(3).style().color());
    }

    private ChatServiceHarness createHarness(boolean primaryThread) {
        return createHarness(primaryThread, false);
    }

    private ChatServiceHarness createHarness(boolean primaryThread, boolean relayForwardWhenCancelled) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ClanMemberRepository clanMemberRepository = mock(ClanMemberRepository.class);
        RecordingClanChatRelay relay = new RecordingClanChatRelay();
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();

        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.isPrimaryThread()).thenReturn(primaryThread);
        doAnswer(invocation -> {
            scheduledTask.set(invocation.getArgument(1));
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        Player sender = mockPlayer("Alice");
        Player recipient = mockPlayer("Bob");
        when(server.getPlayer(sender.getUniqueId())).thenReturn(sender);
        when(server.getPlayer(recipient.getUniqueId())).thenReturn(recipient);

        ChatService chatService = new ChatService(
                plugin,
                createPluginConfig(true, true, relayForwardWhenCancelled),
                clanMemberRepository,
                relay,
                MiniMessage.miniMessage()
        );

        return new ChatServiceHarness(
                pluginManager,
                clanMemberRepository,
                relay,
                chatService,
                sender,
                recipient,
                scheduledTask
        );
    }

    private PluginConfig createPluginConfig(boolean clanChatEnabled, boolean clanChatToggleEnabled) {
        return createPluginConfig(clanChatEnabled, clanChatToggleEnabled, false);
    }

    private PluginConfig createPluginConfig(
            boolean clanChatEnabled,
            boolean clanChatToggleEnabled,
            boolean relayForwardWhenCancelled
    ) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("max-clan-name-length", 24);
        yaml.set("max-clan-tag-length", 6);
        yaml.set("default-clan-tag-color", "white");
        yaml.set("invite-expiration-seconds", 300);
        yaml.set("max-clan-size", 20);
        yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-enabled", clanChatEnabled);
        yaml.set("clan-chat-toggle-enabled", clanChatToggleEnabled);
        yaml.set("discordsrv-clan-chat-relay.enabled", false);
        yaml.set("discordsrv-clan-chat-relay.forward-when-cancelled", relayForwardWhenCancelled);
        yaml.set("discordsrv-clan-chat-relay.channel", "global");
        yaml.set("discordsrv-clan-chat-relay.format", "[{clan}] {user}: {message}");
        when(plugin.getConfig()).thenReturn(yaml);
        return PluginConfig.load(plugin);
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.displayName()).thenReturn(Component.text(name));
        return player;
    }

    private final class ChatServiceHarness {

        private final PluginManager pluginManager;
        private final ClanMemberRepository clanMemberRepository;
        private final RecordingClanChatRelay relay;
        private final ChatService chatService;
        private final Player sender;
        private final Player recipient;
        private final AtomicReference<Runnable> scheduledTask;

        private ChatServiceHarness(
                PluginManager pluginManager,
                ClanMemberRepository clanMemberRepository,
                RecordingClanChatRelay relay,
                ChatService chatService,
                Player sender,
                Player recipient,
                AtomicReference<Runnable> scheduledTask
        ) {
            this.pluginManager = pluginManager;
            this.clanMemberRepository = clanMemberRepository;
            this.relay = relay;
            this.chatService = chatService;
            this.sender = sender;
            this.recipient = recipient;
            this.scheduledTask = scheduledTask;
        }

        private void stubClanMembership() {
            UUID senderUuid = sender.getUniqueId();
            String senderName = sender.getName();
            UUID recipientUuid = recipient.getUniqueId();
            String recipientName = recipient.getName();
            PlayerClanSnapshot snapshot = new PlayerClanSnapshot(
                    1L,
                    "Crimson Knights",
                    "CK",
                    "#FFAA00",
                    ClanRole.PRESIDENT,
                    senderUuid
            );
            ClanMember senderMember = new ClanMember(snapshot.clanId(), senderUuid, senderName, ClanRole.PRESIDENT, Instant.now());
            ClanMember recipientMember = new ClanMember(snapshot.clanId(), recipientUuid, recipientName, ClanRole.MEMBER, Instant.now());

            when(clanMemberRepository.findSnapshotByPlayerUuid(senderUuid))
                    .thenReturn(CompletableFuture.completedFuture(Optional.of(snapshot)));
            when(clanMemberRepository.findByClanId(snapshot.clanId()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(senderMember, recipientMember)));
        }

        private ClanChatMessageEvent capturedClanChatEvent() {
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            verify(pluginManager).callEvent(eventCaptor.capture());
            return (ClanChatMessageEvent) eventCaptor.getValue();
        }
    }

    private static final class RecordingClanChatRelay implements ClanChatRelay {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void relay(Player sender, PlayerClanSnapshot snapshot, String rawMessage) {
            messages.add(rawMessage);
        }
    }
}
