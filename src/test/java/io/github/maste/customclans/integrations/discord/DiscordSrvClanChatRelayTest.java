package io.github.maste.customclans.integrations.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordSrvClanChatRelayTest {

    @BeforeEach
    void resetDiscordUtil() {
        github.scarsz.discordsrv.util.DiscordUtil.reset();
    }

    @Test
    void directChannelRelayQueuesMessageWithoutPermissionGatedFallback() throws ReflectiveOperationException {
        DiscordSrvClanChatRelay relay = new DiscordSrvClanChatRelay(mockPlugin(), createPluginConfig());
        FakeDiscordSrvPlugin discordSrvPlugin = new FakeDiscordSrvPlugin();
        Object destinationChannel = new Object();
        discordSrvPlugin.optionalTextChannel = destinationChannel;

        boolean relayed = relay.invokeDirectChannelRelay(discordSrvPlugin, "[Mods] KolbieCheese: hello clan", "global");

        assertTrue(relayed);
        assertEquals("global", discordSrvPlugin.requestedChannelName);
        assertSame(destinationChannel, github.scarsz.discordsrv.util.DiscordUtil.lastChannel);
        assertEquals("[Mods] KolbieCheese: hello clan", github.scarsz.discordsrv.util.DiscordUtil.lastMessage);
        assertNull(discordSrvPlugin.processChatMessageCall);
    }

    @Test
    void directChannelRelayReturnsFalseWhenNoMappedChannelExists() throws ReflectiveOperationException {
        DiscordSrvClanChatRelay relay = new DiscordSrvClanChatRelay(mockPlugin(), createPluginConfig());
        FakeDiscordSrvPlugin discordSrvPlugin = new FakeDiscordSrvPlugin();

        boolean relayed = relay.invokeDirectChannelRelay(discordSrvPlugin, "[Mods] KolbieCheese: hello clan", "global");

        assertFalse(relayed);
        assertEquals("global", discordSrvPlugin.requestedChannelName);
        assertNull(github.scarsz.discordsrv.util.DiscordUtil.lastChannel);
        assertNull(github.scarsz.discordsrv.util.DiscordUtil.lastMessage);
    }

    @Test
    void processChatMessageFallbackRemainsAvailableForOlderDiscordSrvVersions() throws ReflectiveOperationException {
        DiscordSrvClanChatRelay relay = new DiscordSrvClanChatRelay(mockPlugin(), createPluginConfig());
        FakeDiscordSrvPlugin discordSrvPlugin = new FakeDiscordSrvPlugin();
        Player sender = mock(Player.class);

        boolean relayed = relay.invokeProcessChatMessage(
                discordSrvPlugin,
                sender,
                "[Mods] KolbieCheese: fallback",
                "global"
        );

        assertTrue(relayed);
        assertSame(sender, discordSrvPlugin.processChatMessageCall.sender());
        assertEquals("[Mods] KolbieCheese: fallback", discordSrvPlugin.processChatMessageCall.message());
        assertEquals("global", discordSrvPlugin.processChatMessageCall.channel());
        assertFalse(discordSrvPlugin.processChatMessageCall.cancelled());
    }

    private JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        return plugin;
    }

    private PluginConfig createPluginConfig() {
        JavaPlugin plugin = mock(JavaPlugin.class);
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
        yaml.set("chat-debug-logging-enabled", true);
        yaml.set("discordsrv-clan-chat-relay.enabled", true);
        yaml.set("discordsrv-clan-chat-relay.channel", "global");
        yaml.set("discordsrv-clan-chat-relay.format", "[{clan}] {user}: {message}");
        when(plugin.getConfig()).thenReturn(yaml);
        return PluginConfig.load(plugin);
    }

    private static final class FakeDiscordSrvPlugin {

        private Object optionalTextChannel;
        private String requestedChannelName;
        private ProcessChatMessageCall processChatMessageCall;

        public Object getOptionalTextChannel(String channelName) {
            requestedChannelName = channelName;
            return optionalTextChannel;
        }

        public void processChatMessage(Player sender, String message, String channel, boolean cancelled) {
            processChatMessageCall = new ProcessChatMessageCall(sender, message, channel, cancelled);
        }
    }

    private record ProcessChatMessageCall(Player sender, String message, String channel, boolean cancelled) {
    }
}
