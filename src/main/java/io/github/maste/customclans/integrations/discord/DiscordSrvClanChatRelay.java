package io.github.maste.customclans.integrations.discord;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
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
        debugAttempt(sender, snapshot, rawMessage);
        Plugin discordSrvPlugin = resolveDiscordSrvPlugin();
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
            if (invokeDirectChannelRelay(discordSrvPlugin, message, gameChannelName)) {
                debugQueued(sender, snapshot, "direct-channel");
                return;
            }

            if (invokeProcessChatMessage(discordSrvPlugin, sender, message, gameChannelName)) {
                debugDelegated(sender, snapshot, "processChatMessage");
                return;
            }

            debugUnsupportedApi(sender, snapshot);
        } catch (ReflectiveOperationException reflectiveException) {
            debugFailure(sender, snapshot, "exception:" + reflectiveException.getClass().getSimpleName());
            plugin.getLogger().log(Level.WARNING, "Failed to relay clan chat message to DiscordSRV", reflectiveException);
        }
    }

    private Plugin resolveDiscordSrvPlugin() {
        if (plugin.getServer() == null) {
            return null;
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        return pluginManager == null ? null : pluginManager.getPlugin(DISCORDSRV_PLUGIN_NAME);
    }

    private void debugFailure(Player sender, PlayerClanSnapshot snapshot, String reason) {
        if (!chatDebugLoggingEnabled) {
            return;
        }
        plugin.getLogger().log(
                Level.INFO,
                "DiscordSRV relay failed; sender={0}, clan={1}, channel={2}, senderIsOp={3}, senderHasChatPermission={4}, reason={5}",
                new Object[]{
                        sender.getName(),
                        snapshot.tag(),
                        gameChannelName,
                        sender.isOp(),
                        sender.hasPermission("discordsrv.chat"),
                        reason
                }
        );
    }

    private void debugAttempt(Player sender, PlayerClanSnapshot snapshot, String rawMessage) {
        if (!chatDebugLoggingEnabled) {
            return;
        }
        plugin.getLogger().log(
                Level.INFO,
                "DiscordSRV relay attempt; sender={0}, clan={1}, channel={2}, senderIsOp={3}, senderHasChatPermission={4}, messageLength={5}",
                new Object[]{
                        sender.getName(),
                        snapshot.tag(),
                        gameChannelName,
                        sender.isOp(),
                        sender.hasPermission("discordsrv.chat"),
                        rawMessage.length()
                }
        );
    }

    private void debugQueued(Player sender, PlayerClanSnapshot snapshot, String path) {
        if (!chatDebugLoggingEnabled) {
            return;
        }
        plugin.getLogger().log(
                Level.INFO,
                "DiscordSRV relay queued; sender={0}, clan={1}, channel={2}, senderIsOp={3}, senderHasChatPermission={4}, path={5}",
                new Object[]{
                        sender.getName(),
                        snapshot.tag(),
                        gameChannelName,
                        sender.isOp(),
                        sender.hasPermission("discordsrv.chat"),
                        path
                }
        );
    }

    private void debugDelegated(Player sender, PlayerClanSnapshot snapshot, String path) {
        if (!chatDebugLoggingEnabled) {
            return;
        }
        plugin.getLogger().log(
                Level.INFO,
                "DiscordSRV relay delegated; sender={0}, clan={1}, channel={2}, senderIsOp={3}, senderHasChatPermission={4}, path={5}",
                new Object[]{
                        sender.getName(),
                        snapshot.tag(),
                        gameChannelName,
                        sender.isOp(),
                        sender.hasPermission("discordsrv.chat"),
                        path
                }
        );
    }

    private void debugUnsupportedApi(Player sender, PlayerClanSnapshot snapshot) {
        if (!unsupportedApiLogged) {
            unsupportedApiLogged = true;
            plugin.getLogger().warning(
                    "DiscordSRV found, but no supported direct-send or processChatMessage signature was detected. Clan relay disabled."
            );
        }
        debugFailure(sender, snapshot, "unsupported-relay-api");
    }

    private String formatMessage(String clanTag, String userName, String message) {
        return relayFormat
                .replace("{clan}", clanTag)
                .replace("{user}", userName)
                .replace("{message}", message);
    }

    boolean invokeDirectChannelRelay(Object discordSrvPlugin, String message, String channel)
            throws ReflectiveOperationException {
        Object destinationChannel = resolveDiscordTextChannel(discordSrvPlugin, channel);
        if (destinationChannel == null) {
            return false;
        }

        ClassLoader classLoader = discordSrvPlugin.getClass().getClassLoader();
        Class<?> discordUtilType = Class.forName("github.scarsz.discordsrv.util.DiscordUtil", true, classLoader);

        Method queueMessageMethod = findCompatibleStaticMethod(
                discordUtilType,
                "queueMessage",
                destinationChannel.getClass(),
                String.class
        );
        if (queueMessageMethod == null) {
            queueMessageMethod = findCompatibleStaticMethod(
                    discordUtilType,
                    "sendMessage",
                    destinationChannel.getClass(),
                    String.class
            );
        }
        if (queueMessageMethod == null) {
            return false;
        }

        queueMessageMethod.setAccessible(true);
        queueMessageMethod.invoke(null, destinationChannel, message);
        return true;
    }

    boolean invokeProcessChatMessage(Object discordSrvPlugin, Player sender, String message, String channel)
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

    private Object resolveDiscordTextChannel(Object discordSrvPlugin, String channel) throws ReflectiveOperationException {
        Class<?> implementationType = discordSrvPlugin.getClass();

        Method optionalTextChannelMethod = findMethod(implementationType, "getOptionalTextChannel", String.class);
        if (optionalTextChannelMethod != null) {
            optionalTextChannelMethod.setAccessible(true);
            return optionalTextChannelMethod.invoke(discordSrvPlugin, channel);
        }

        Method destinationChannelMethod = findMethod(
                implementationType,
                "getDestinationTextChannelForGameChannelName",
                String.class
        );
        if (destinationChannelMethod != null) {
            destinationChannelMethod.setAccessible(true);
            return destinationChannelMethod.invoke(discordSrvPlugin, channel);
        }

        return null;
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

    private Method findCompatibleStaticMethod(Class<?> owner, String methodName, Class<?>... argumentTypes) {
        Method publicMethod = findCompatibleMethod(owner.getMethods(), methodName, argumentTypes);
        if (publicMethod != null) {
            return publicMethod;
        }
        return findCompatibleMethod(owner.getDeclaredMethods(), methodName, argumentTypes);
    }

    private Method findCompatibleMethod(Method[] methods, String methodName, Class<?>... argumentTypes) {
        for (Method method : methods) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !method.getName().equals(methodName)
                    || method.getParameterCount() != argumentTypes.length) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!wrap(parameterTypes[i]).isAssignableFrom(wrap(argumentTypes[i]))) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }
}
