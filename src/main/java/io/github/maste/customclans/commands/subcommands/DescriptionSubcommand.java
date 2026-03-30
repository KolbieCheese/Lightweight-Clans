package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class DescriptionSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public DescriptionSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "description", "clans.manage", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.description");
            return;
        }

        handleAction(sender, clanService.updateDescription(asPlayer(sender), String.join(" ", args)), result -> {
        });
    }
}
