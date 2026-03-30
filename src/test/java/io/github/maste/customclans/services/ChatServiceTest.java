package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
        yaml.set("debug", false);
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
