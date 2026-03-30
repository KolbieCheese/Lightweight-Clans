package io.github.maste.customclans.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import io.github.maste.customclans.commands.subcommands.AcceptSubcommand;
import io.github.maste.customclans.commands.subcommands.ChatSubcommand;
import io.github.maste.customclans.commands.subcommands.DenySubcommand;
import io.github.maste.customclans.commands.subcommands.DisbandSubcommand;
import io.github.maste.customclans.commands.subcommands.GetSubcommand;
import io.github.maste.customclans.commands.subcommands.HelpSubcommand;
import io.github.maste.customclans.commands.subcommands.LeaveSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.services.ClanService;
import io.github.maste.customclans.services.InviteService;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClanCommandTest {

    private JavaPlugin plugin;
    private MessageManager messages;
    private ClanService clanService;
    private InviteService inviteService;
    private ChatService chatService;
    private PluginConfig pluginConfig;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        messages = mock(MessageManager.class);
        when(messages.raw(any())).thenReturn("/clan test");
        clanService = mock(ClanService.class);
        inviteService = mock(InviteService.class);
        chatService = mock(ChatService.class);
        pluginConfig = mock(PluginConfig.class);
        sender = mock(CommandSender.class);
    }

    @Test
    void helpRejectsExtraArguments() {
        new HelpSubcommand(plugin, messages).execute(sender, new String[]{"extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void leaveRejectsExtraArguments() {
        new LeaveSubcommand(plugin, messages, clanService).execute(sender, new String[]{"extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void disbandRejectsExtraArguments() {
        new DisbandSubcommand(plugin, messages, clanService).execute(sender, new String[]{"extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void chatToggleRejectsExtraArguments() {
        new ChatSubcommand(plugin, messages, chatService).execute(sender, new String[]{"toggle", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void acceptRequiresClanName() {
        new AcceptSubcommand(plugin, messages, inviteService).execute(sender, new String[0]);

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void denyRequiresClanName() {
        new DenySubcommand(plugin, messages, inviteService).execute(sender, new String[0]);

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void getRequiresActionSelector() {
        new GetSubcommand(plugin, messages, clanService, pluginConfig).execute(sender, new String[]{"Azure"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void getRejectsUnknownView() {
        new GetSubcommand(plugin, messages, clanService, pluginConfig).execute(sender, new String[]{"Azure", "stats"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }
}
