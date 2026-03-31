package io.github.maste.customclans.plugin;

import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.commands.ClanCommand;
import io.github.maste.customclans.commands.ClanSubcommand;
import io.github.maste.customclans.commands.subcommands.AcceptSubcommand;
import io.github.maste.customclans.commands.subcommands.BannerSubcommand;
import io.github.maste.customclans.commands.subcommands.ChatSubcommand;
import io.github.maste.customclans.commands.subcommands.ColorSubcommand;
import io.github.maste.customclans.commands.subcommands.CreateSubcommand;
import io.github.maste.customclans.commands.subcommands.DenySubcommand;
import io.github.maste.customclans.commands.subcommands.DescriptionSubcommand;
import io.github.maste.customclans.commands.subcommands.DisbandSubcommand;
import io.github.maste.customclans.commands.subcommands.HelpSubcommand;
import io.github.maste.customclans.commands.subcommands.InviteSubcommand;
import io.github.maste.customclans.commands.subcommands.InfoSubcommand;
import io.github.maste.customclans.commands.subcommands.KickSubcommand;
import io.github.maste.customclans.commands.subcommands.LeaveSubcommand;
import io.github.maste.customclans.commands.subcommands.ListSubcommand;
import io.github.maste.customclans.commands.subcommands.MembersSubcommand;
import io.github.maste.customclans.commands.subcommands.RenameSubcommand;
import io.github.maste.customclans.commands.subcommands.ReloadSubcommand;
import io.github.maste.customclans.commands.subcommands.SetBannerSubcommand;
import io.github.maste.customclans.commands.subcommands.TagSubcommand;
import io.github.maste.customclans.commands.subcommands.TransferSubcommand;
import io.github.maste.customclans.config.MessageManager;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.ClanChatRelay;
import io.github.maste.customclans.integrations.discord.DiscordSrvClanChatRelay;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
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
import io.github.maste.customclans.services.api.LightweightClansApiImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomClansPlugin extends JavaPlugin {

    private static final int CONFIG_SCHEMA_VERSION = 1;
    private static final List<String> MIGRATED_FILENAMES = List.of("config.yml", "messages.yml", "clans.db");
    private SQLiteDatabase database;
    private PluginConfig pluginConfig;
    private MessageManager messageManager;
    private ClanService clanService;
    private ChatService chatService;
    private InviteService inviteService;
    private LightweightClansApi lightweightClansApi;

    @Override
    public void onEnable() {
        migrateLegacyDataFolderIfNeeded();
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
        initializeRuntimeServices(clanRepository, clanMemberRepository, clanInviteRepository);
        registerPublicApi(clanRepository, clanMemberRepository);

        registerCommand();
        registerListeners();
        warmOnlinePlayerCaches();
        scheduleInviteCleanup();
    }

    @Override
    public void onDisable() {
        unregisterPublicApi();
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
                new AcceptSubcommand(this, messageManager, inviteService, clanService),
                new DenySubcommand(this, messageManager, inviteService, clanService),
                new LeaveSubcommand(this, messageManager, clanService),
                new InfoSubcommand(this, messageManager, clanService, pluginConfig),
                new MembersSubcommand(this, messageManager, clanService),
                new ChatSubcommand(this, messageManager, chatService),
                new InviteSubcommand(this, messageManager, inviteService, pluginConfig),
                new RenameSubcommand(this, messageManager, clanService),
                new DescriptionSubcommand(this, messageManager, clanService),
                new TagSubcommand(this, messageManager, clanService),
                new SetBannerSubcommand(this, messageManager, clanService),
                new BannerSubcommand(this, messageManager, clanService),
                new ColorSubcommand(this, messageManager, clanService, pluginConfig),
                new KickSubcommand(this, messageManager, clanService, chatService),
                new TransferSubcommand(this, messageManager, clanService, chatService),
                new DisbandSubcommand(this, messageManager, clanService),
                new ListSubcommand(this, messageManager, clanService),
                new ReloadSubcommand(this, messageManager)
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

    public void reloadPluginState() {
        reloadConfig();
        messageManager.reload();
        pluginConfig = PluginConfig.load(this);

        ClanRepository clanRepository = new SQLiteClanRepository(database);
        ClanMemberRepository clanMemberRepository = new SQLiteClanMemberRepository(database);
        ClanInviteRepository clanInviteRepository = new SQLiteClanInviteRepository(database);
        initializeRuntimeServices(clanRepository, clanMemberRepository, clanInviteRepository);
        unregisterPublicApi();
        registerPublicApi(clanRepository, clanMemberRepository);

        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
        registerCommand();
        registerListeners();
        warmOnlinePlayerCaches();
        scheduleInviteCleanup();
    }

    private void initializeRuntimeServices(
            ClanRepository clanRepository,
            ClanMemberRepository clanMemberRepository,
            ClanInviteRepository clanInviteRepository
    ) {
        ClanChatRelay clanChatRelay = pluginConfig.discordSrvClanChatRelayEnabled()
                ? new DiscordSrvClanChatRelay(this, pluginConfig)
                : new NoopClanChatRelay();

        chatService = new ChatService(this, pluginConfig, clanMemberRepository, clanChatRelay, messageManager.miniMessage());
        clanService = new ClanService(this, pluginConfig, clanRepository, clanMemberRepository, chatService);
        inviteService = new InviteService(this, pluginConfig, clanRepository, clanMemberRepository, clanInviteRepository, chatService);
    }


    private void registerPublicApi(
            ClanRepository clanRepository,
            ClanMemberRepository clanMemberRepository
    ) {
        lightweightClansApi = new LightweightClansApiImpl(clanRepository, clanMemberRepository);
        getServer().getServicesManager().register(
                LightweightClansApi.class,
                lightweightClansApi,
                this,
                ServicePriority.Normal
        );
    }

    private void unregisterPublicApi() {
        getServer().getServicesManager().unregisterAll(this);
        lightweightClansApi = null;
    }

    private void migrateLegacyDataFolderIfNeeded() {
        Path currentDataFolder = getDataFolder().toPath();
        Path parentFolder = currentDataFolder.getParent();
        if (parentFolder == null) {
            return;
        }

        Path legacyDataFolder = parentFolder.resolve("CustomClans");
        if (!Files.isDirectory(legacyDataFolder)) {
            return;
        }

        Path currentConfigPath = currentDataFolder.resolve("config.yml");
        boolean firstLightweightClansBoot = !Files.exists(currentConfigPath);
        int currentSchemaVersion = readConfigSchemaVersion(currentConfigPath);
        int legacySchemaVersion = readConfigSchemaVersion(legacyDataFolder.resolve("config.yml"));
        boolean requiresSchemaMigration = legacySchemaVersion > 0
                && currentSchemaVersion > 0
                && legacySchemaVersion < currentSchemaVersion;

        if (!firstLightweightClansBoot && !requiresSchemaMigration) {
            return;
        }

        try {
            Files.createDirectories(currentDataFolder);
            for (String filename : MIGRATED_FILENAMES) {
                Path source = legacyDataFolder.resolve(filename);
                if (!Files.exists(source)) {
                    continue;
                }
                Path target = currentDataFolder.resolve(filename);
                if (Files.exists(target) && !requiresSchemaMigration) {
                    continue;
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteDirectoryRecursively(legacyDataFolder);
            getLogger().info("Migrated legacy CustomClans data folder into LightweightClans data folder.");
        } catch (IOException ioException) {
            getLogger().log(Level.WARNING, "Failed to migrate legacy CustomClans data folder", ioException);
        }
    }

    private int readConfigSchemaVersion(Path configPath) {
        if (!Files.exists(configPath)) {
            return 0;
        }
        org.bukkit.configuration.file.YamlConfiguration yamlConfiguration = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configPath.toFile());
        return yamlConfiguration.getInt("config-schema-version", 0);
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw runtimeException;
        }
    }
}
