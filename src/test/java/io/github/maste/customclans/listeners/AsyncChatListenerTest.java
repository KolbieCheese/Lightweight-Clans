package io.github.maste.customclans.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.util.ActionResult;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class AsyncChatListenerTest {

    @Test
    void toggleRoutedMessagesCancelPublicChatAndDelegateToClanChatService() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        ChatService chatService = mock(ChatService.class);
        MessageManager messages = mock(MessageManager.class);
        Player player = mock(Player.class);
        Component message = Component.text("hello clan");

        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AsyncChatListenerTest"));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        when(chatService.shouldRouteToClanChat(player)).thenReturn(true);
        when(chatService.sendToggleRoutedClanChat(player, message))
                .thenReturn(CompletableFuture.completedFuture(ActionResult.success("", null)));

        AsyncChatListener listener = new AsyncChatListener(plugin, chatService, messages);
        AsyncChatEvent event = createAsyncChatEvent(player, message);

        listener.onAsyncChat(event);

        assertTrue(event.isCancelled());
        verify(chatService).sendToggleRoutedClanChat(player, message);
    }

    @Test
    void nonClanMessagesKeepPublicRendererPath() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChatService chatService = mock(ChatService.class);
        MessageManager messages = mock(MessageManager.class);
        Player player = mock(Player.class);
        Component displayName = Component.text("Alicia");
        Component message = Component.text("hello world");
        Component rendered = Component.text("rendered line");

        when(chatService.shouldRouteToClanChat(player)).thenReturn(false);
        when(chatService.renderPublicChat(player, displayName, message)).thenReturn(rendered);

        AsyncChatListener listener = new AsyncChatListener(plugin, chatService, messages);
        AsyncChatEvent event = createAsyncChatEvent(player, message);

        listener.onAsyncChat(event);

        assertFalse(event.isCancelled());
        Component result = event.renderer().render(player, displayName, message, mock(Audience.class));
        assertEquals(rendered, result);
        verify(chatService).renderPublicChat(player, displayName, message);
    }

    @Test
    void cancelledMessagesDoNotEnterClanOrPublicRenderingPaths() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChatService chatService = mock(ChatService.class);
        MessageManager messages = mock(MessageManager.class);
        Player player = mock(Player.class);
        Component message = Component.text("blocked");

        AsyncChatListener listener = new AsyncChatListener(plugin, chatService, messages);
        AsyncChatEvent event = createAsyncChatEvent(player, message);
        event.setCancelled(true);

        listener.onAsyncChat(event);

        assertTrue(event.isCancelled());
        verify(chatService, never()).shouldRouteToClanChat(any(Player.class));
        verify(chatService, never()).sendToggleRoutedClanChat(any(Player.class), any(Component.class));
        verify(chatService, never()).renderPublicChat(any(Player.class), any(Component.class), any(Component.class));
    }

    private AsyncChatEvent createAsyncChatEvent(Player player, Component message) {
        return new AsyncChatEvent(
                true,
                player,
                Set.of(),
                ChatRenderer.defaultRenderer(),
                message,
                message,
                SignedMessage.system(PlainTextHelper.serialize(message), message)
        );
    }

    private static final class PlainTextHelper {

        private PlainTextHelper() {
        }

        private static String serialize(Component component) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(component);
        }
    }
}
