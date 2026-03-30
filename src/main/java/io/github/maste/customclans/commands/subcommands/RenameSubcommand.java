package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class RenameSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public RenameSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "rename", "clans.manage", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.rename");
            return;
        }

        handleAction(sender, clanService.renameClan(asPlayer(sender), String.join(" ", args).trim()), result -> {
        });
    }
}
