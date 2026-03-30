package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.services.ChatService;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatSubcommand extends AbstractClanSubcommand {

    private final ChatService chatService;

    public ChatSubcommand(JavaPlugin plugin, MessageManager messages, ChatService chatService) {
        super(plugin, messages, "chat", "clans.chat", true);
        this.chatService = chatService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            handleAction(sender, chatService.toggleClanChat(asPlayer(sender)), result -> {
            });
            return;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("toggle")) {
            sendUsage(sender, "usage.chat-toggle");
            return;
        }

        if (args.length == 0) {
            sendUsage(sender, "usage.chat");
            return;
        }

        String message = String.join(" ", args).trim();
        handleAction(sender, chatService.sendClanChat(asPlayer(sender), message), result -> {
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && "toggle".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of("toggle");
        }
        return List.of();
    }
}
