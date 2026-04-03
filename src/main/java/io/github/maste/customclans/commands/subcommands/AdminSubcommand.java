package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.services.ClanService;
import io.github.maste.customclans.util.ActionResult;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminSubcommand extends AbstractClanSubcommand {

    private final ClanService clanService;

    public AdminSubcommand(JavaPlugin plugin, MessageManager messages, ClanService clanService) {
        super(plugin, messages, "admin", "clans.admin", false);
        this.clanService = clanService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "usage.admin");
            return;
        }

        AdminAction action = AdminAction.from(args[0]);
        if (action == null) {
            messages.send(sender, "admin.unknown-action");
            sendUsage(sender, "usage.admin");
            return;
        }
        if (!sender.hasPermission(action.permission())) {
            messages.send(sender, "errors.no-permission");
            return;
        }
        if (action.playerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "errors.player-only");
            return;
        }

        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (action) {
            case RENAME -> executeRename(sender, remainingArgs);
            case TAG -> executeTag(sender, remainingArgs);
            case COLOR -> executeColor(sender, remainingArgs);
            case SETBANNER -> executeSetBanner(sender, remainingArgs);
            case DISBAND -> executeDisband(sender, remainingArgs);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(AdminAction.values())
                    .filter(action -> sender.hasPermission(action.permission()))
                    .filter(action -> !action.playerOnly() || sender instanceof Player)
                    .map(AdminAction::command)
                    .filter(command -> command.startsWith(token))
                    .sorted()
                    .toList();
        }

        AdminAction action = AdminAction.from(args[0]);
        if (action == null) {
            return List.of();
        }
        if (!sender.hasPermission(action.permission())) {
            return List.of();
        }
        if (action.playerOnly() && !(sender instanceof Player)) {
            return List.of();
        }

        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        if (remainingArgs.length != 1) {
            return List.of();
        }
        return clanService.suggestClanNames(remainingArgs[0]);
    }

    private void executeRename(CommandSender sender, String[] args) {
        ParsedTargetAndValue parsed = parseTargetAndValue(args);
        if (parsed == null) {
            if (args.length < 2) {
                sendUsage(sender, "usage.admin-rename");
                return;
            }
            sendAdminLookupFailure(sender, args);
            return;
        }

        handleAction(sender, clanService.adminRenameClan(sender, parsed.clanIdentifier(), parsed.value()), result -> {
        });
    }

    private void executeTag(CommandSender sender, String[] args) {
        ParsedTargetAndValue parsed = parseTargetAndValue(args);
        if (parsed == null) {
            if (args.length < 2) {
                sendUsage(sender, "usage.admin-tag");
                return;
            }
            sendAdminLookupFailure(sender, args);
            return;
        }

        handleAction(sender, clanService.adminUpdateTag(sender, parsed.clanIdentifier(), parsed.value()), result -> {
        });
    }

    private void executeColor(CommandSender sender, String[] args) {
        ParsedTargetAndValue parsed = parseTargetAndValue(args);
        if (parsed == null) {
            if (args.length < 2) {
                sendUsage(sender, "usage.admin-color");
                return;
            }
            sendAdminLookupFailure(sender, args);
            return;
        }

        handleAction(sender, clanService.adminUpdateColor(sender, parsed.clanIdentifier(), parsed.value()), result -> {
        });
    }

    private void executeSetBanner(CommandSender sender, String[] args) {
        String clanIdentifier = joinArgs(args);
        if (clanIdentifier.isEmpty()) {
            sendUsage(sender, "usage.admin-setbanner");
            return;
        }

        handleAction(sender, clanService.adminSetBanner(asPlayer(sender), clanIdentifier), result -> {
        });
    }

    private void executeDisband(CommandSender sender, String[] args) {
        String clanIdentifier = joinArgs(args);
        if (clanIdentifier.isEmpty()) {
            sendUsage(sender, "usage.admin-disband");
            return;
        }

        handleAction(sender, clanService.adminDisbandClan(sender, clanIdentifier), result ->
                notifyDisbandTargets(sender, result));
    }

    private ParsedTargetAndValue parseTargetAndValue(String[] args) {
        if (args.length < 2) {
            return null;
        }

        for (int prefixLength = args.length - 1; prefixLength >= 1; prefixLength--) {
            String clanIdentifier = joinArgs(args, 0, prefixLength);
            String value = joinArgs(args, prefixLength, args.length);
            if (value.isEmpty()) {
                continue;
            }
            if (clanService.clanNameExists(clanIdentifier)) {
                return new ParsedTargetAndValue(clanIdentifier, value);
            }
        }

        return null;
    }

    private void notifyDisbandTargets(CommandSender sender, ActionResult<List<ClanMember>> result) {
        String clanName = result.placeholders().getOrDefault("clan", "");
        for (ClanMember clanMember : result.value()) {
            if (sender instanceof Player player && clanMember.playerUuid().equals(player.getUniqueId())) {
                continue;
            }
            Player target = plugin.getServer().getPlayer(clanMember.playerUuid());
            if (target != null) {
                messages.send(target, "admin.disband.target", Placeholder.unparsed("clan", clanName));
            }
        }
    }

    private void sendAdminLookupFailure(CommandSender sender, String[] args) {
        messages.send(sender, "admin.lookup.not-found", Placeholder.unparsed("name", joinArgs(args)));
    }

    private String joinArgs(String[] args) {
        return joinArgs(args, 0, args.length);
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        return String.join(" ", Arrays.copyOfRange(args, startInclusive, endExclusive)).trim();
    }

    private enum AdminAction {
        RENAME("rename", "clans.admin.rename", false),
        TAG("tag", "clans.admin.tag", false),
        COLOR("color", "clans.admin.color", false),
        SETBANNER("setbanner", "clans.admin.setbanner", true),
        DISBAND("disband", "clans.admin.disband", false);

        private final String command;
        private final String permission;
        private final boolean playerOnly;

        AdminAction(String command, String permission, boolean playerOnly) {
            this.command = command;
            this.permission = permission;
            this.playerOnly = playerOnly;
        }

        private String command() {
            return command;
        }

        private String permission() {
            return permission;
        }

        private boolean playerOnly() {
            return playerOnly;
        }

        private static AdminAction from(String token) {
            String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(action -> action.command.equals(normalized))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record ParsedTargetAndValue(String clanIdentifier, String value) {
    }
}
