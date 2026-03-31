package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.util.ValidationUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class SQLiteDatabase implements AutoCloseable {

    private final Path databasePath;
    private final Logger logger;
    private final ExecutorService executorService;

    public SQLiteDatabase(Path databasePath, Logger logger) {
        this.databasePath = databasePath;
        this.logger = logger;
        this.executorService = Executors.newSingleThreadExecutor(new DatabaseThreadFactory());
    }

    public void initialize() throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception exception) {
            throw new SQLException("Unable to create database directory", exception);
        }

        try (Connection connection = openConnection()) {
            initializeSchema(connection);
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        createBaseTables(connection);
        migrateClansTable(connection);
        migrateClanInvitesTable(connection);
        createIndexes(connection);
        validateLeadershipState(connection);
    }

    private void createBaseTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        tag_color TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        president_uuid TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        clan_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        joined_at INTEGER NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                        UNIQUE (clan_id, player_uuid)
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_invites (
                        clan_id INTEGER NOT NULL,
                        invited_player_uuid TEXT NOT NULL,
                        invited_by_uuid TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS clan_banners (
                        clan_id INTEGER PRIMARY KEY,
                        material TEXT NOT NULL,
                        patterns TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    private void migrateClansTable(Connection connection) throws SQLException {
        if (!hasColumn(connection, "clans", "normalized_name")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE clans ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''");
            }
        }
        if (!hasColumn(connection, "clans", "description")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE clans ADD COLUMN description TEXT NOT NULL DEFAULT ''");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE clans
                SET normalized_name = ?
                WHERE id = ?
                """);
             PreparedStatement lookup = connection.prepareStatement("SELECT id, name FROM clans");
             ResultSet resultSet = lookup.executeQuery()) {
            while (resultSet.next()) {
                statement.setString(1, ValidationUtil.normalizeClanName(resultSet.getString("name")));
                statement.setLong(2, resultSet.getLong("id"));
                statement.addBatch();
            }
            statement.executeBatch();
        }

        try (PreparedStatement duplicates = connection.prepareStatement("""
                SELECT normalized_name
                FROM clans
                GROUP BY normalized_name
                HAVING normalized_name = '' OR COUNT(*) > 1
                LIMIT 1
                """);
             ResultSet resultSet = duplicates.executeQuery()) {
            if (resultSet.next()) {
                throw new SQLException("Clan name migration failed because duplicate or blank normalized clan names exist.");
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS idx_clans_name_unique");
        }
    }

    private void migrateClanInvitesTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "clan_invites")) {
            return;
        }

        if (!hasLegacyInviteConstraint(connection)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE clan_invites RENAME TO clan_invites_legacy");
            statement.execute("""
                    CREATE TABLE clan_invites (
                        clan_id INTEGER NOT NULL,
                        invited_player_uuid TEXT NOT NULL,
                        invited_by_uuid TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    INSERT INTO clan_invites (clan_id, invited_player_uuid, invited_by_uuid, expires_at)
                    SELECT clan_id, invited_player_uuid, invited_by_uuid, expires_at
                    FROM clan_invites_legacy
                    """);
            statement.execute("DROP TABLE clan_invites_legacy");
        }
    }

    private void createIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_clans_normalized_name_unique ON clans(normalized_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_clan_members_clan_id ON clan_members(clan_id)");
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_clan_members_president_unique
                    ON clan_members(clan_id)
                    WHERE role = 'PRESIDENT'
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_clan_invites_clan_player_unique
                    ON clan_invites(clan_id, invited_player_uuid)
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_clan_invites_player_uuid ON clan_invites(invited_player_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_clan_invites_expires_at ON clan_invites(expires_at)");
        }
    }

    private void validateLeadershipState(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.id
                FROM clans c
                LEFT JOIN clan_members m
                    ON c.id = m.clan_id
                    AND m.role = 'PRESIDENT'
                GROUP BY c.id
                HAVING COUNT(m.player_uuid) <> 1
                LIMIT 1
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                throw new SQLException("Clan leadership validation failed because a clan does not have exactly one PRESIDENT.");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.id
                FROM clans c
                JOIN clan_members m
                    ON c.id = m.clan_id
                    AND m.role = 'PRESIDENT'
                WHERE c.president_uuid <> m.player_uuid
                LIMIT 1
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                throw new SQLException("Clan leadership validation failed because clans.president_uuid does not match the PRESIDENT member.");
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLegacyInviteConstraint(Connection connection) throws SQLException {
        List<String> indexNames = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA index_list(clan_invites)")) {
            while (resultSet.next()) {
                if (resultSet.getInt("unique") == 1) {
                    indexNames.add(resultSet.getString("name"));
                }
            }
        }

        for (String indexName : indexNames) {
            List<String> columns = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA index_info(" + indexName + ")")) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("name"));
                }
            }

            if (columns.size() == 1 && "invited_player_uuid".equalsIgnoreCase(columns.get(0))) {
                return true;
            }
        }

        return false;
    }

    public <T> CompletableFuture<T> supplyAsync(SQLSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executorService);
    }

    public CompletableFuture<Void> runAsync(SQLRunnable runnable) {
        return supplyAsync(() -> {
            runnable.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> transactionAsync(SQLTransaction<T> transaction) {
        return supplyAsync(() -> {
            try (Connection connection = openConnection()) {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    T result = transaction.apply(connection);
                    connection.commit();
                    connection.setAutoCommit(previousAutoCommit);
                    return result;
                } catch (Exception exception) {
                    connection.rollback();
                    connection.setAutoCommit(previousAutoCommit);
                    throw exception;
                }
            }
        });
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        configureConnection(connection);
        return connection;
    }

    private void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
        }
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface SQLSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface SQLRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface SQLTransaction<T> {
        T apply(Connection connection) throws Exception;
    }

    private final class DatabaseThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "customclans-sqlite");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((unused, throwable) ->
                    logger.severe("Uncaught SQLite worker exception: " + throwable.getMessage()));
            return thread;
        }
    }
}
