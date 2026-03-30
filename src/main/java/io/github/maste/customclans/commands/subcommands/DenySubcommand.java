package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import io.github.maste.customclans.services.InviteService;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class DenySubcommand extends AbstractClanSubcommand {

    private final InviteService inviteService;
    private final ClanService clanService;

    public DenySubcommand(JavaPlugin plugin, MessageManager messages, InviteService inviteService, ClanService clanService) {
        super(plugin, messages, "deny", "clans.use", true);
        this.inviteService = inviteService;
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.deny");
            return;
        }

        handleAction(sender, inviteService.denyInvite(asPlayer(sender), String.join(" ", args).trim()), result -> {
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return clanService.suggestClanNames(args[0]);
    }
}
