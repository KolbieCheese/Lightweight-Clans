package io.github.maste.customclans.listeners;

import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.util.SchedulerUtil;
import java.util.logging.Level;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AsyncChatListener implements Listener {

    private final JavaPlugin plugin;
    private final ChatService chatService;
    private final MessageManager messages;

    public AsyncChatListener(JavaPlugin plugin, ChatService chatService, MessageManager messages) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        boolean toggleRouted = chatService.shouldRouteToClanChat(event.getPlayer());
        if (chatService.chatDebugLoggingEnabled()) {
            plugin.getLogger().log(
                    Level.INFO,
                    "AsyncChatListener intercepted chat; sender={0}, toggleRoutedPath={1}",
                    new Object[]{event.getPlayer().getName(), toggleRouted}
            );
        }
        if (toggleRouted) {
            event.setCancelled(true);
            chatService.sendToggleRoutedClanChat(event.getPlayer(), event.message()).whenComplete((result, throwable) ->
                    SchedulerUtil.runSync(plugin, () -> {
                        if (throwable != null) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to route clan chat message", throwable);
                            messages.send(event.getPlayer(), "errors.internal");
                            return;
                        }

                        if (!result.success() && result.messageKey() != null && !result.messageKey().isBlank()) {
                            messages.send(event.getPlayer(), result.messageKey());
                        }
                    }));
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                chatService.renderPublicChat(source, sourceDisplayName, message));
    }
}
