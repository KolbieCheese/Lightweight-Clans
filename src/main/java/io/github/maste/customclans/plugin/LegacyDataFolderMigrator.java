package io.github.maste.customclans.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;

final class LegacyDataFolderMigrator {

    private LegacyDataFolderMigrator() {}

    static void migrateIfNeeded(
            Path currentDataFolder,
            Logger logger,
            int configSchemaVersion,
            List<String> migratedFilenames
    ) {
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
        int legacySchemaVersion = readConfigSchemaVersion(legacyDataFolder.resolve("config.yml"));
        boolean requiresSchemaMigration = legacySchemaVersion > 0
                && legacySchemaVersion < configSchemaVersion;

        if (!firstLightweightClansBoot && !requiresSchemaMigration) {
            return;
        }

        try {
            Files.createDirectories(currentDataFolder);
            for (String filename : migratedFilenames) {
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
            logger.info("Migrated legacy CustomClans data folder into LightweightClans data folder.");
        } catch (IOException ioException) {
            logger.log(Level.WARNING, "Failed to migrate legacy CustomClans data folder", ioException);
        }
    }

    private static int readConfigSchemaVersion(Path configPath) {
        if (!Files.exists(configPath)) {
            return 0;
        }

        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(configPath.toFile());
        return yamlConfiguration.getInt("config-schema-version", 0);
    }

    private static void deleteDirectoryRecursively(Path directory) throws IOException {
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
