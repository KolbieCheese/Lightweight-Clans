package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CreateSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public CreateSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "create", "clans.create", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.create");
            return;
        }

        String clanName = String.join(" ", args).trim();
        handleAction(sender, clanService.createClan(asPlayer(sender), clanName), result -> {
        });
    }
}
