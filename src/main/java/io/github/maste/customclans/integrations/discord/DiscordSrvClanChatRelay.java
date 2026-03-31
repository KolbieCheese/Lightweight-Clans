package io.github.maste.customclans.integrations.discord;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DiscordSRV adapter for clan chat relay.
 *
 * <p>This class isolates DiscordSRV-specific behavior from the core chat service. If DiscordSRV is
 * missing or API signatures differ, the plugin logs a warning and continues without breaking clan chat.
 */
public final class DiscordSrvClanChatRelay implements ClanChatRelay {

    private static final String DISCORDSRV_PLUGIN_NAME = "DiscordSRV";

    private final JavaPlugin plugin;
    private final boolean chatDebugLoggingEnabled;
    private final String gameChannelName;
    private final String relayFormat;
    private volatile boolean missingPluginLogged;
    private volatile boolean unsupportedApiLogged;

    public DiscordSrvClanChatRelay(JavaPlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.chatDebugLoggingEnabled = pluginConfig.chatDebugLoggingEnabled();
        this.gameChannelName = pluginConfig.discordSrvClanChatChannel();
        this.relayFormat = pluginConfig.discordSrvClanChatFormat();
    }

    @Override
    public void relay(Player sender, PlayerClanSnapshot snapshot, String rawMessage) {
        Plugin discordSrvPlugin = Bukkit.getPluginManager().getPlugin(DISCORDSRV_PLUGIN_NAME);
        if (discordSrvPlugin == null || !discordSrvPlugin.isEnabled()) {
            if (!missingPluginLogged) {
                missingPluginLogged = true;
                plugin.getLogger().info("DiscordSRV relay enabled, but DiscordSRV is not installed/enabled. Clan chat relay skipped.");
            }
            debugFailure(sender, snapshot, "discordsrv-unavailable");
            return;
        }

        String message = formatMessage(snapshot.tag(), sender.getName(), rawMessage);

        try {
            if (invokeProcessChatMessage(discordSrvPlugin, sender, message, gameChannelName)) {
                if (chatDebugLoggingEnabled) {
                    plugin.getLogger().log(
                            Level.INFO,
                            "DiscordSRV relay succeeded; sender={0}, clan={1}, channel={2}",
                            new Object[]{sender.getName(), snapshot.tag(), gameChannelName}
                    );
                }
                return;
            }

            if (!unsupportedApiLogged) {
                unsupportedApiLogged = true;
                plugin.getLogger().warning("DiscordSRV found, but no supported processChatMessage signature was detected. Clan relay disabled.");
            }
            debugFailure(sender, snapshot, "unsupported-process-chat-signature");
        } catch (ReflectiveOperationException reflectiveException) {
            debugFailure(sender, snapshot, "exception:" + reflectiveException.getClass().getSimpleName());
            plugin.getLogger().log(Level.WARNING, "Failed to relay clan chat message to DiscordSRV", reflectiveException);
        }
    }

    private void debugFailure(Player sender, PlayerClanSnapshot snapshot, String reason) {
        if (!chatDebugLoggingEnabled) {
            return;
        }
        plugin.getLogger().log(
                Level.INFO,
                "DiscordSRV relay failed; sender={0}, clan={1}, channel={2}, reason={3}",
                new Object[]{sender.getName(), snapshot.tag(), gameChannelName, reason}
        );
    }

    private String formatMessage(String clanTag, String userName, String message) {
        return relayFormat
                .replace("{clan}", clanTag)
                .replace("{user}", userName)
                .replace("{message}", message);
    }

    private boolean invokeProcessChatMessage(Plugin discordSrvPlugin, Player sender, String message, String channel)
            throws ReflectiveOperationException {
        Class<?> implementationType = discordSrvPlugin.getClass();

        Method fourArgMethod = findMethod(
                implementationType,
                "processChatMessage",
                Player.class,
                String.class,
                String.class,
                boolean.class
        );
        if (fourArgMethod != null) {
            fourArgMethod.setAccessible(true);
            fourArgMethod.invoke(discordSrvPlugin, sender, message, channel, false);
            return true;
        }

        Method threeArgMethod = findMethod(
                implementationType,
                "processChatMessage",
                Player.class,
                String.class,
                String.class
        );
        if (threeArgMethod != null) {
            threeArgMethod.setAccessible(true);
            threeArgMethod.invoke(discordSrvPlugin, sender, message, channel);
            return true;
        }

        return false;
    }

    private Method findMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException noPublicMethod) {
            try {
                return owner.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException noDeclaredMethod) {
                return null;
            }
        }
    }
}
