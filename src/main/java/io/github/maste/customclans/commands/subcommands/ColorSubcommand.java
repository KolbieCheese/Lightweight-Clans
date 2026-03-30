package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.services.ClanService;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ColorSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;
    private final PluginConfig pluginConfig;

    public ColorSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            ClanService clanService,
            PluginConfig pluginConfig
    ) {
        super(plugin, messages, "color", "clans.manage", true);
        this.clanService = clanService;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsage(sender, "usage.color");
            return;
        }

        handleAction(sender, clanService.updateColor(asPlayer(sender), args[0]), result -> {
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String token = args[0].toLowerCase(java.util.Locale.ROOT);
        boolean hasBypass = sender.hasPermission(pluginConfig.nameModerationConfig().bypassPermission());
        return pluginConfig.namedClanColorNames().stream()
                .filter(color -> hasBypass || !pluginConfig.isRestrictedGoldColor(color))
                .filter(color -> color.startsWith(token))
                .toList();
    }
}
