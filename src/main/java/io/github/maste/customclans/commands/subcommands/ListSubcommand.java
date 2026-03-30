package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.models.ClanListEntry;
import io.github.maste.customclans.services.ClanService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ListSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public ListSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "list", "clans.lookup", false);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.list");
            return;
        }

        handleAction(sender, clanService.listClans(), result -> {
            if (result.value().isEmpty()) {
                messages.send(sender, "list.empty");
                return;
            }

            messages.send(sender, "list.header");
            for (ClanListEntry clan : result.value()) {
                messages.send(
                        sender,
                        "list.line",
                        Placeholder.unparsed("name", clan.name()),
                        Placeholder.unparsed("tag", clan.tag()),
                        Placeholder.unparsed("members", String.valueOf(clan.memberCount()))
                );
            }
        });
    }
}
