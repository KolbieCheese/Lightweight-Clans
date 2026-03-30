package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.ClanInfo;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.services.ClanService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class GetSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;
    private final PluginConfig pluginConfig;

    public GetSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            ClanService clanService,
            PluginConfig pluginConfig
    ) {
        super(plugin, messages, "get", "clans.lookup", false);
        this.clanService = clanService;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "usage.get");
            return;
        }

        String view = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        if (!view.equals("info") && !view.equals("members")) {
            sendUsage(sender, "usage.get");
            return;
        }

        String clanName = String.join(" ", java.util.Arrays.copyOf(args, args.length - 1)).trim();
        if (clanName.isEmpty()) {
            sendUsage(sender, "usage.get");
            return;
        }

        handleAction(sender, clanService.getClanInfo(clanName), result -> {
            if (view.equals("info")) {
                sendInfo(sender, result.value());
                return;
            }
            sendMembers(sender, result.value());
        });
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return clanService.suggestClanNames(args[0]);
        }

        if (args.length == 2) {
            String token = args[1].toLowerCase(java.util.Locale.ROOT);
            return java.util.List.of("info", "members").stream()
                    .filter(option -> option.startsWith(token))
                    .toList();
        }

        String token = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return java.util.List.of("info", "members").stream()
                .filter(option -> option.startsWith(token))
                .toList();
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
