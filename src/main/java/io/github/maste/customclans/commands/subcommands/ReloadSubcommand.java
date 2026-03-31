package io.github.maste.customclans.commands.subcommands;

import io.github.maste.customclans.commands.AbstractClanSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.plugin.CustomClansPlugin;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;

public final class ReloadSubcommand extends AbstractClanSubcommand {

    public ReloadSubcommand(CustomClansPlugin plugin, MessageManager messages) {
        super(plugin, messages, "reload", "clans.admin", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            sendUsage(sender, "usage.reload");
            return;
        }

        try {
            ((CustomClansPlugin) plugin).reloadPluginState();
            messages.send(sender, "reload.success");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload LightweightClans configuration", exception);
            messages.send(sender, "errors.internal");
        }
    }
}
