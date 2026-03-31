package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.ClanInfo;
import io.github.maste.customclans.services.ClanService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfoSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;
    private final PluginConfig pluginConfig;

    public InfoSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            ClanService clanService,
            PluginConfig pluginConfig
    ) {
        super(plugin, messages, "info", "clans.lookup", false);
        this.clanService = clanService;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendUsage(sender, "usage.info");
                return;
            }

            handleAction(sender, clanService.getClanInfo(player), result -> sendInfo(sender, result.value()));
            return;
        }

        String clanName = String.join(" ", args).trim();
        if (clanName.isEmpty()) {
            sendUsage(sender, "usage.info");
            return;
        }

        handleAction(sender, clanService.getClanInfo(clanName), result -> sendInfo(sender, result.value()));
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return java.util.List.of();
        }
        return clanService.suggestClanNameWords(args);
    }

    private void sendInfo(CommandSender sender, ClanInfo clanInfo) {
        long onlineCount = clanInfo.members().stream()
                .filter(member -> plugin.getServer().getPlayer(member.playerUuid()) != null)
                .count();

        messages.send(sender, "info.header");
        messages.sendList(
                sender,
                "info.lines",
                Placeholder.unparsed("name", clanInfo.clan().name()),
                Placeholder.unparsed("tag", clanInfo.clan().tag()),
                Placeholder.unparsed("color", pluginConfig.formatColorDisplayName(clanInfo.clan().tagColor())),
                Placeholder.unparsed("president", clanInfo.presidentName()),
                Placeholder.unparsed(
                        "description",
                        clanInfo.clan().description().isBlank() ? "No description set." : clanInfo.clan().description()
                ),
                Placeholder.unparsed("member_count", String.valueOf(clanInfo.members().size())),
                Placeholder.unparsed("max_members", String.valueOf(pluginConfig.maxClanSize())),
                Placeholder.unparsed("online_count", String.valueOf(onlineCount))
        );
    }
}
