package io.github.maste.customclans.services;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Server;

/**
 * Dispatches public plugin events on the main thread.
 *
 * <p>Service-layer lifecycle operations should call this dispatcher only after persistence work has
 * completed so listeners observe durable state.
 */
public final class ServiceEventDispatcher {

    private final JavaPlugin plugin;

    public ServiceEventDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> dispatch(Event event) {
        if (!plugin.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        Server server = plugin.getServer();
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }

        boolean isPrimaryThread;
        try {
            isPrimaryThread = Bukkit.getServer() == null || Bukkit.isPrimaryThread();
        } catch (Throwable ignored) {
            isPrimaryThread = true;
        }

        if (isPrimaryThread) {
            server.getPluginManager().callEvent(event);
            return CompletableFuture.completedFuture(null);
        }

        try {
            server.getScheduler().runTask(plugin, () -> server.getPluginManager().callEvent(event));
        } catch (Throwable ignored) {
            server.getPluginManager().callEvent(event);
        }
        return CompletableFuture.completedFuture(null);
    }
}
