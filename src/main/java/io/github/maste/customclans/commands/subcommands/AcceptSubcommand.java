package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.InviteService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class AcceptSubcommand extends AbstractClanSubcommand {

    private final InviteService inviteService;

    public AcceptSubcommand(JavaPlugin plugin, MessageManager messages, InviteService inviteService) {
        super(plugin, messages, "accept", "clans.use", true);
        this.inviteService = inviteService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.accept");
            return;
        }

        handleAction(sender, inviteService.acceptInvite(asPlayer(sender), String.join(" ", args).trim()), result -> {
        });
    }
}
