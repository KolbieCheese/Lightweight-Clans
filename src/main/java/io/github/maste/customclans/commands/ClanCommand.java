package io.github.maste.customclans.commands;

import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ClanService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private final MessageManager messages;
    private final ClanService clanService;
    private final Map<String, ClanSubcommand> subcommands;

    public ClanCommand(MessageManager messages, ClanService clanService, List<ClanSubcommand> subcommandList) {
        this.messages = messages;
        this.clanService = clanService;
        this.subcommands = new LinkedHashMap<>();
        for (ClanSubcommand subcommand : subcommandList) {
            this.subcommands.put(subcommand.name(), subcommand);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ClanSubcommand subcommand = args.length == 0
                ? subcommands.get("help")
                : subcommands.get(args[0].toLowerCase(Locale.ROOT));

        if (subcommand == null) {
            messages.send(sender, "errors.unknown-subcommand");
            return true;
        }

        if (!sender.hasPermission(subcommand.permission())) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        if (subcommand.playerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }

        if (sender instanceof Player player) {
            clanService.touchPlayerName(player).exceptionally(throwable -> null);
        }

        String[] remainingArgs = args.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(args, 1, args.length);
        subcommand.execute(sender, remainingArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return subcommands.values().stream()
                    .filter(subcommand -> sender.hasPermission(subcommand.permission()))
                    .filter(subcommand -> !subcommand.playerOnly() || sender instanceof Player)
                    .map(ClanSubcommand::name)
                    .filter(name -> name.startsWith(token))
                    .sorted()
                    .collect(Collectors.toList());
        }

        ClanSubcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            return List.of();
        }

        if (!sender.hasPermission(subcommand.permission())) {
            return List.of();
        }
        if (subcommand.playerOnly() && !(sender instanceof Player)) {
            return List.of();
        }

        String[] remainingArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
        return subcommand.tabComplete(sender, remainingArgs);
    }
}
