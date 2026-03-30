package io.github.maste.customclans.commands;

import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.util.ActionResult;
import io.github.maste.customclans.util.MiniMessageUtil;
import io.github.maste.customclans.util.SchedulerUtil;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractClanSubcommand implements ClanSubcommand {

    protected final JavaPlugin plugin;
    protected final MessageManager messages;
    private final String name;
    private final String permission;
    private final boolean playerOnly;

    protected AbstractClanSubcommand(
            JavaPlugin plugin,
            MessageManager messages,
            String name,
            String permission,
            boolean playerOnly
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.name = Objects.requireNonNull(name, "name");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.playerOnly = playerOnly;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String permission() {
        return permission;
    }

    @Override
    public boolean playerOnly() {
        return playerOnly;
    }

    protected Player asPlayer(CommandSender sender) {
        return (Player) sender;
    }

    protected void sendUsage(CommandSender sender, String usagePath) {
        messages.send(sender, "errors.usage", Placeholder.unparsed("usage", messages.raw(usagePath)));
    }

    protected <T> void handleAction(
            CommandSender sender,
            CompletableFuture<ActionResult<T>> actionFuture,
            Consumer<ActionResult<T>> successHandler
    ) {
        actionFuture.whenComplete((result, throwable) -> SchedulerUtil.runSync(plugin, () -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Unhandled clan command failure", throwable);
                messages.send(sender, "errors.internal");
                return;
            }

            if (result.messageKey() != null && !result.messageKey().isBlank()) {
                messages.send(sender, result.messageKey(), MiniMessageUtil.placeholders(result.placeholders()));
            }

            if (result.success()) {
                successHandler.accept(result);
            }
        }));
    }

    protected Player findOnlinePlayer(String name) {
        Player exact = plugin.getServer().getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
