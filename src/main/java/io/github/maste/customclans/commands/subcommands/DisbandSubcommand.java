package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.services.ClanService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DisbandSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public DisbandSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "disband", "clans.manage", true);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.disband");
            return;
        }

        handleAction(sender, clanService.disbandClan(asPlayer(sender)), result -> {
            String clanName = result.placeholders().getOrDefault("clan", "");
            for (ClanMember clanMember : result.value()) {
                if (clanMember.playerUuid().equals(asPlayer(sender).getUniqueId())) {
                    continue;
                }
                Player target = plugin.getServer().getPlayer(clanMember.playerUuid());
                if (target != null) {
                    messages.send(target, "disband.success-target", Placeholder.unparsed("clan", clanName));
                }
            }
        });
    }
}
