package io.github.maste.customclans.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataFolderMigratorTest {

    private static final Logger TEST_LOGGER = Logger.getLogger(LegacyDataFolderMigratorTest.class.getName());
    private static final List<String> MIGRATED_FILENAMES = List.of("config.yml", "messages.yml", "clans.db");

    @TempDir
    Path tempDir;

    @Test
    void firstBootWithOnlyLegacyFolderCopiesSupportedFiles() throws IOException {
        Path pluginsDirectory = tempDir.resolve("plugins");
        Path legacyFolder = pluginsDirectory.resolve("CustomClans");
        Path currentFolder = pluginsDirectory.resolve("LightweightClans");

        Files.createDirectories(legacyFolder);
        Files.writeString(legacyFolder.resolve("config.yml"), "config-schema-version: 1\nlegacy: true\n");
        Files.writeString(legacyFolder.resolve("messages.yml"), "prefix: legacy\n");
        Files.writeString(legacyFolder.resolve("clans.db"), "sqlite");

        LegacyDataFolderMigrator.migrateIfNeeded(currentFolder, TEST_LOGGER, 1, MIGRATED_FILENAMES);

        assertEquals("config-schema-version: 1\nlegacy: true\n", Files.readString(currentFolder.resolve("config.yml")));
        assertEquals("prefix: legacy\n", Files.readString(currentFolder.resolve("messages.yml")));
        assertEquals("sqlite", Files.readString(currentFolder.resolve("clans.db")));
        assertFalse(Files.exists(legacyFolder));
    }

    @Test
    void existingTargetFilesAreNotOverwrittenWithoutSchemaMigration() throws IOException {
        Path pluginsDirectory = tempDir.resolve("plugins");
        Path legacyFolder = pluginsDirectory.resolve("CustomClans");
        Path currentFolder = pluginsDirectory.resolve("LightweightClans");

        Files.createDirectories(legacyFolder);
        Files.createDirectories(currentFolder);
        Files.writeString(legacyFolder.resolve("config.yml"), "config-schema-version: 1\nsource: legacy\n");
        Files.writeString(legacyFolder.resolve("messages.yml"), "prefix: legacy\n");
        Files.writeString(legacyFolder.resolve("clans.db"), "legacy-db");

        Files.writeString(currentFolder.resolve("config.yml"), "config-schema-version: 1\nsource: current\n");
        Files.writeString(currentFolder.resolve("messages.yml"), "prefix: current\n");
        Files.writeString(currentFolder.resolve("clans.db"), "current-db");

        LegacyDataFolderMigrator.migrateIfNeeded(currentFolder, TEST_LOGGER, 1, MIGRATED_FILENAMES);

        assertEquals("config-schema-version: 1\nsource: current\n", Files.readString(currentFolder.resolve("config.yml")));
        assertEquals("prefix: current\n", Files.readString(currentFolder.resolve("messages.yml")));
        assertEquals("current-db", Files.readString(currentFolder.resolve("clans.db")));
        assertTrue(Files.exists(legacyFolder));
    }

    @Test
    void schemaMigrationOverwritesExistingTargetFiles() throws IOException {
        Path pluginsDirectory = tempDir.resolve("plugins");
        Path legacyFolder = pluginsDirectory.resolve("CustomClans");
        Path currentFolder = pluginsDirectory.resolve("LightweightClans");

        Files.createDirectories(legacyFolder);
        Files.createDirectories(currentFolder);
        Files.writeString(legacyFolder.resolve("config.yml"), "config-schema-version: 1\nsource: legacy\n");
        Files.writeString(legacyFolder.resolve("messages.yml"), "prefix: legacy\n");
        Files.writeString(legacyFolder.resolve("clans.db"), "legacy-db");

        Files.writeString(currentFolder.resolve("config.yml"), "config-schema-version: 1\nsource: current\n");
        Files.writeString(currentFolder.resolve("messages.yml"), "prefix: current\n");
        Files.writeString(currentFolder.resolve("clans.db"), "current-db");

        LegacyDataFolderMigrator.migrateIfNeeded(currentFolder, TEST_LOGGER, 2, MIGRATED_FILENAMES);

        assertEquals("config-schema-version: 1\nsource: legacy\n", Files.readString(currentFolder.resolve("config.yml")));
        assertEquals("prefix: legacy\n", Files.readString(currentFolder.resolve("messages.yml")));
        assertEquals("legacy-db", Files.readString(currentFolder.resolve("clans.db")));
        assertFalse(Files.exists(legacyFolder));
    }

    @Test
    void successfulCopyCleansUpLegacyFolderRecursively() throws IOException {
        Path pluginsDirectory = tempDir.resolve("plugins");
        Path legacyFolder = pluginsDirectory.resolve("CustomClans");
        Path currentFolder = pluginsDirectory.resolve("LightweightClans");

        Files.createDirectories(legacyFolder.resolve("nested"));
        Files.writeString(legacyFolder.resolve("config.yml"), "config-schema-version: 1\n");
        Files.writeString(legacyFolder.resolve("nested/leftover.txt"), "cleanup me");

        LegacyDataFolderMigrator.migrateIfNeeded(currentFolder, TEST_LOGGER, 1, MIGRATED_FILENAMES);

        assertTrue(Files.exists(currentFolder.resolve("config.yml")));
        assertFalse(Files.exists(legacyFolder));
    }
}
