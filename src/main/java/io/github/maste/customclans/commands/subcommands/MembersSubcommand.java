package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.models.ClanInfo;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.services.ClanService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MembersSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public MembersSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "members", "clans.lookup", false);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendUsage(sender, "usage.members");
                return;
            }

            handleAction(sender, clanService.getClanInfo(player), result -> sendMembers(sender, result.value()));
            return;
        }

        String clanName = String.join(" ", args).trim();
        if (clanName.isEmpty()) {
            sendUsage(sender, "usage.members");
            return;
        }

        handleAction(sender, clanService.getClanInfo(clanName), result -> sendMembers(sender, result.value()));
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return java.util.List.of();
        }
        return clanService.suggestClanNameWords(args);
    }

    private void sendMembers(CommandSender sender, ClanInfo clanInfo) {
        messages.send(sender, "members.header");
        for (ClanMember clanMember : clanInfo.members()) {
            String key = clanMember.role() == ClanRole.PRESIDENT
                    ? "members.president-line"
                    : "members.member-line";
            messages.send(sender, key, Placeholder.unparsed("name", clanMember.lastKnownName()));
        }
    }
}
