package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void sendClanChatFailsWhenClanChatIsDisabled() {
        ChatService chatService = new ChatService(
                mock(JavaPlugin.class),
                createPluginConfig(false, true),
                mock(ClanMemberRepository.class),
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
                MiniMessage.miniMessage()
        );

        Player player = mockPlayer("Alice");

        assertEquals("chat.unavailable", chatService.toggleClanChat(player).join().messageKey());
        assertFalse(chatService.shouldRouteToClanChat(player));
    }

    @Test
    void renderPublicChatColorsOnlyTheClanTag() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ClanMemberRepository clanMemberRepository = mock(ClanMemberRepository.class);
        ChatService chatService = new ChatService(
                plugin,
                createPluginConfig(true, true),
                clanMemberRepository,
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

    private PluginConfig createPluginConfig(boolean clanChatEnabled, boolean clanChatToggleEnabled) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("max-clan-name-length", 24);
        yaml.set("max-clan-tag-length", 6);
        yaml.set("default-clan-tag-color", "gold");
        yaml.set("invite-expiration-seconds", 300);
        yaml.set("max-clan-size", 20);
        yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
        yaml.set("clan-chat-enabled", clanChatEnabled);
        yaml.set("clan-chat-toggle-enabled", clanChatToggleEnabled);
        when(plugin.getConfig()).thenReturn(yaml);
        return PluginConfig.load(plugin);
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        return player;
    }
}
