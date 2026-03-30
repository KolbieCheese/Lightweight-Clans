package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.services.ClanService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class KickSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;
    private final ChatService chatService;

    public KickSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            ClanService clanService,
            ChatService chatService
    ) {
        super(plugin, messages, "kick", "clans.manage", true);
        this.clanService = clanService;
        this.chatService = chatService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendUsage(sender, "usage.kick");
            return;
        }

        handleAction(sender, clanService.kickMember(asPlayer(sender), args[0]), result -> {
            ClanMember clanMember = result.value();
            Player target = plugin.getServer().getPlayer(clanMember.playerUuid());
            if (target != null) {
                messages.send(target, "kick.success-target", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "clan",
                        result.placeholders().getOrDefault("clan", "")
                ));
            }
        });
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return java.util.List.of();
        }
        return clanService.cachedSnapshot(player.getUniqueId())
                .map(snapshot -> chatService.onlineClanMemberNames(snapshot.clanId(), player.getUniqueId()).stream()
                        .filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                                .startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                        .toList())
                .orElseGet(java.util.List::of);
    }
}
