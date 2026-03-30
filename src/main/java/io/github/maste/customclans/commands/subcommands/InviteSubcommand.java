package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.services.InviteService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InviteSubcommand extends AbstractClanSubcommand {

    private final InviteService inviteService;
    private final PluginConfig pluginConfig;

    public InviteSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            InviteService inviteService,
            PluginConfig pluginConfig
    ) {
        super(plugin, messages, "invite", "clans.invite", true);
        this.inviteService = inviteService;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsage(sender, "usage.invite");
            return;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            messages.send(sender, "common.player-offline");
            return;
        }

        handleAction(sender, inviteService.sendInvite(asPlayer(sender), target), result -> messages.send(
                target,
                "invite.received",
                Placeholder.unparsed("player", asPlayer(sender).getName()),
                Placeholder.unparsed("clan", result.placeholders().getOrDefault("clan", "")),
                Placeholder.unparsed("seconds", String.valueOf(pluginConfig.inviteExpirationSeconds()))
        ));
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return java.util.List.of();
        }
        String token = args[0].toLowerCase();
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(token))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
