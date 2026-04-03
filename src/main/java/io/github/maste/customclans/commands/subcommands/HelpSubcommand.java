package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class HelpSubcommand extends AbstractClanSubcommand {

    public HelpSubcommand(JavaPlugin plugin, MessageManager messages) {
        super(plugin, messages, "help", "clans.use", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.help");
            return;
        }

        messages.sendList(sender, "help.lines");
        if (sender.hasPermission("clans.admin")) {
            messages.sendList(sender, "help.admin-lines");
        }
    }
}
