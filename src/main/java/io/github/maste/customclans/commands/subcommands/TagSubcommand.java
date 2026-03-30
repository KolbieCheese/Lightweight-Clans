package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TagSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public TagSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "tag", "clans.manage", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsage(sender, "usage.tag");
            return;
        }

        handleAction(sender, clanService.updateTag(asPlayer(sender), args[0]), result -> {
        });
    }
}
