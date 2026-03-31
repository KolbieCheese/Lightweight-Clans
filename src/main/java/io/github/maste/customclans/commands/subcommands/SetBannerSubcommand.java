package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SetBannerSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public SetBannerSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "setbanner", "clans.manage", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.setbanner");
            return;
        }

        handleAction(sender, clanService.setClanBanner(asPlayer(sender)), result -> {
        });
    }
}
