package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BannerSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public BannerSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "banner", "clans.use", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.banner");
            return;
        }

        Player player = asPlayer(sender);
        handleAction(sender, clanService.getClanBannerItem(player), result -> {
            ItemStack bannerItem = result.value();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(bannerItem);
            if (overflow.isEmpty()) {
                return;
            }

            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            messages.send(sender, "banner.inventory-overflow");
        });
    }
}
