package io.github.maste.customclans.plugin;

import io.github.maste.customclans.commands.ClanCommand;
import io.github.maste.customclans.commands.ClanSubcommand;
import io.github.maste.customclans.commands.subcommands.AcceptSubcommand;
import io.github.maste.customclans.commands.subcommands.ChatSubcommand;
import io.github.maste.customclans.commands.subcommands.ColorSubcommand;
import io.github.maste.customclans.commands.subcommands.CreateSubcommand;
import io.github.maste.customclans.commands.subcommands.DenySubcommand;
import io.github.maste.customclans.commands.subcommands.DisbandSubcommand;
import io.github.maste.customclans.commands.subcommands.GetSubcommand;
import io.github.maste.customclans.commands.subcommands.HelpSubcommand;
import io.github.maste.customclans.commands.subcommands.InviteSubcommand;
import io.github.maste.customclans.commands.subcommands.KickSubcommand;
import io.github.maste.customclans.commands.subcommands.LeaveSubcommand;
import io.github.maste.customclans.commands.subcommands.RenameSubcommand;
import io.github.maste.customclans.commands.subcommands.TagSubcommand;
import io.github.maste.customclans.commands.subcommands.TransferSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.listeners.AsyncChatListener;
import io.github.maste.customclans.listeners.PlayerSessionListener;
import io.github.maste.customclans.repositories.ClanInviteRepository;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.repositories.ClanRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository;
import io.github.maste.customclans.repositories.sqlite.SQLiteDatabase;
import io.github.maste.customclans.services.ChatService;
import io.github.maste.customclans.services.ClanService;
import io.github.maste.customclans.services.InviteService;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomClansPlugin extends JavaPlugin {

    private SQLiteDatabase database;
    private PluginConfig pluginConfig;
    private MessageManager messageManager;
    private ClanService clanService;
    private ChatService chatService;
    private InviteService inviteService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageManager = new MessageManager(this);
        this.pluginConfig = PluginConfig.load(this);

        try {
            this.database = new SQLiteDatabase(getDataFolder().toPath().resolve("clans.db"), getLogger());
            this.database.initialize();
        } catch (SQLException sqlException) {
            getLogger().log(Level.SEVERE, "Unable to initialize the SQLite database", sqlException);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ClanRepository clanRepository = new SQLiteClanRepository(database);
        ClanMemberRepository clanMemberRepository = new SQLiteClanMemberRepository(database);
        ClanInviteRepository clanInviteRepository = new SQLiteClanInviteRepository(database);

        this.chatService = new ChatService(this, pluginConfig, clanMemberRepository, messageManager.miniMessage());
        this.clanService = new ClanService(pluginConfig, clanRepository, clanMemberRepository, chatService);
        this.inviteService = new InviteService(pluginConfig, clanRepository, clanMemberRepository, clanInviteRepository, chatService);

        registerCommand();
        registerListeners();
        warmOnlinePlayerCaches();
        scheduleInviteCleanup();
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    private void registerCommand() {
        PluginCommand clanCommand = getCommand("clan");
        if (clanCommand == null) {
            throw new IllegalStateException("The /clan command is missing from plugin.yml");
        }

        List<ClanSubcommand> subcommands = List.of(
                new HelpSubcommand(this, messageManager),
                new CreateSubcommand(this, messageManager, clanService),
                new AcceptSubcommand(this, messageManager, inviteService),
                new DenySubcommand(this, messageManager, inviteService),
                new LeaveSubcommand(this, messageManager, clanService),
                new GetSubcommand(this, messageManager, clanService, pluginConfig),
                new ChatSubcommand(this, messageManager, chatService),
                new InviteSubcommand(this, messageManager, inviteService, pluginConfig),
                new RenameSubcommand(this, messageManager, clanService),
                new TagSubcommand(this, messageManager, clanService),
                new ColorSubcommand(this, messageManager, clanService, pluginConfig),
                new KickSubcommand(this, messageManager, clanService, chatService),
                new TransferSubcommand(this, messageManager, clanService, chatService),
                new DisbandSubcommand(this, messageManager, clanService)
        );

        ClanCommand executor = new ClanCommand(messageManager, clanService, subcommands);
        clanCommand.setExecutor(executor);
        clanCommand.setTabCompleter(executor);
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new AsyncChatListener(this, chatService, messageManager), this);
        pluginManager.registerEvents(new PlayerSessionListener(this, clanService, chatService), this);
    }

    private void warmOnlinePlayerCaches() {
        getServer().getOnlinePlayers().forEach(player -> {
            clanService.touchPlayerName(player).exceptionally(throwable -> {
                getLogger().log(Level.WARNING, "Failed to touch clan member name during startup", throwable);
                return null;
            });
            chatService.refreshSnapshot(player.getUniqueId()).exceptionally(throwable -> {
                getLogger().log(Level.WARNING, "Failed to refresh clan snapshot during startup", throwable);
                return null;
            });
        });
    }

    private void scheduleInviteCleanup() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () ->
                inviteService.cleanupExpiredInvites().exceptionally(throwable -> {
                    getLogger().log(Level.WARNING, "Failed to clean expired clan invites", throwable);
                    return 0;
                }), 20L * 60L, 20L * 60L);
    }
}
