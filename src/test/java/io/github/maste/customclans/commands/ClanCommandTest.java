package io.github.maste.customclans.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.commands.subcommands.CreateSubcommand;
import io.github.maste.customclans.commands.subcommands.AcceptSubcommand;
import io.github.maste.customclans.commands.subcommands.ChatSubcommand;
import io.github.maste.customclans.commands.subcommands.ColorSubcommand;
import io.github.maste.customclans.commands.subcommands.DenySubcommand;
import io.github.maste.customclans.commands.subcommands.DisbandSubcommand;
import io.github.maste.customclans.commands.subcommands.DescriptionSubcommand;
import io.github.maste.customclans.commands.subcommands.GetSubcommand;
import io.github.maste.customclans.commands.subcommands.HelpSubcommand;
import io.github.maste.customclans.commands.subcommands.InviteSubcommand;
import io.github.maste.customclans.commands.subcommands.KickSubcommand;
import io.github.maste.customclans.commands.subcommands.LeaveSubcommand;
import io.github.maste.customclans.commands.subcommands.ListSubcommand;
import io.github.maste.customclans.commands.subcommands.RenameSubcommand;
import io.github.maste.customclans.commands.subcommands.ReloadSubcommand;
import io.github.maste.customclans.commands.subcommands.TagSubcommand;
import io.github.maste.customclans.commands.subcommands.TransferSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.plugin.CustomClansPlugin;
import io.github.maste.customclans.util.ActionResult;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.services.ClanService;
import io.github.maste.customclans.services.InviteService;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    void reloadRejectsExtraArguments() {
        CustomClansPlugin customClansPlugin = mock(CustomClansPlugin.class);
        new ReloadSubcommand(customClansPlugin, messages).execute(sender, new String[]{"extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void reloadRunsPluginRefreshFlow() {
        CustomClansPlugin customClansPlugin = mock(CustomClansPlugin.class);
        doNothing().when(customClansPlugin).reloadPluginState();

        new ReloadSubcommand(customClansPlugin, messages).execute(sender, new String[0]);

        verify(customClansPlugin).reloadPluginState();
        verify(messages).send(sender, "reload.success");
    }

    @Test
    void chatToggleRejectsExtraArguments() {
        new ChatSubcommand(plugin, messages, chatService).execute(sender, new String[]{"toggle", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void acceptRequiresClanName() {
        new AcceptSubcommand(plugin, messages, inviteService, clanService).execute(sender, new String[0]);

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void denyRequiresClanName() {
        new DenySubcommand(plugin, messages, inviteService, clanService).execute(sender, new String[0]);

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

    @Test
    void descriptionRequiresContent() {
        new DescriptionSubcommand(plugin, messages, clanService).execute(sender, new String[0]);

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void listRejectsExtraArguments() {
        new ListSubcommand(plugin, messages, clanService).execute(sender, new String[]{"extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void inviteRejectsExtraArguments() {
        new InviteSubcommand(plugin, messages, inviteService, pluginConfig).execute(sender, new String[]{"Bob", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void tagRejectsExtraArguments() {
        new TagSubcommand(plugin, messages, clanService).execute(sender, new String[]{"TAG", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void colorRejectsExtraArguments() {
        new ColorSubcommand(plugin, messages, clanService, pluginConfig).execute(sender, new String[]{"gold", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void kickRejectsExtraArguments() {
        new KickSubcommand(plugin, messages, clanService, chatService).execute(sender, new String[]{"Bob", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void transferRejectsExtraArguments() {
        new TransferSubcommand(plugin, messages, clanService, chatService).execute(sender, new String[]{"Bob", "extra"});

        verify(messages).send(eq(sender), eq("errors.usage"), any(TagResolver.class));
    }

    @Test
    void publicGetInfoCanBeUsedByNonPlayerSender() {
        when(clanService.getClanInfo("Azure Guard"))
                .thenReturn(CompletableFuture.completedFuture(ActionResult.failure("lookup.not-found")));

        new GetSubcommand(plugin, messages, clanService, pluginConfig).execute(sender, new String[]{"Azure", "Guard", "info"});

        verify(clanService).getClanInfo("Azure Guard");
    }

    @Test
    void publicGetMembersCanBeUsedByNonPlayerSender() {
        when(clanService.getClanInfo("Azure Guard"))
                .thenReturn(CompletableFuture.completedFuture(ActionResult.failure("lookup.not-found")));

        new GetSubcommand(plugin, messages, clanService, pluginConfig).execute(sender, new String[]{"Azure", "Guard", "members"});

        verify(clanService).getClanInfo("Azure Guard");
    }

    @Test
    void tabCompletionHidesPlayerOnlyCommandsFromConsole() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);

        ClanCommand clanCommand = new ClanCommand(
                messages,
                clanService,
                List.of(
                        new HelpSubcommand(plugin, messages),
                        new RenameSubcommand(plugin, messages, clanService),
                        new CreateSubcommand(plugin, messages, clanService)
                )
        );

        List<String> suggestions = clanCommand.onTabComplete(console, mock(Command.class), "clan", new String[]{""});

        verify(console).hasPermission("clans.use");
        verify(console).hasPermission("clans.manage");
        verify(console).hasPermission("clans.create");
        org.junit.jupiter.api.Assertions.assertEquals(List.of("help"), suggestions);
    }

    @Test
    void tabCompletionStillShowsPlayerOnlyCommandsForPlayers() {
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);

        ClanCommand clanCommand = new ClanCommand(
                messages,
                clanService,
                List.of(
                        new HelpSubcommand(plugin, messages),
                        new RenameSubcommand(plugin, messages, clanService),
                        new CreateSubcommand(plugin, messages, clanService)
                )
        );

        List<String> suggestions = clanCommand.onTabComplete(player, mock(Command.class), "clan", new String[]{""});

        org.junit.jupiter.api.Assertions.assertEquals(List.of("create", "help", "rename"), suggestions);
    }

    @Test
    void playerWithManagePermissionCanRunPresidentCommandPath() {
        Player player = mock(Player.class);
        when(player.hasPermission("clans.manage")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(clanService.renameClan(eq(player), eq("Golden Guard")))
                .thenReturn(CompletableFuture.completedFuture(ActionResult.success("rename.success", null)));
        when(clanService.touchPlayerName(player)).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(messages).send(eq(player), eq("rename.success"), any(TagResolver.class));

        ClanCommand clanCommand = new ClanCommand(
                messages,
                clanService,
                List.of(new RenameSubcommand(plugin, messages, clanService))
        );

        clanCommand.onCommand(player, mock(Command.class), "clan", new String[]{"rename", "Golden", "Guard"});

        verify(clanService).renameClan(player, "Golden Guard");
        verify(messages, never()).send(player, "errors.no-permission");
    }
}
