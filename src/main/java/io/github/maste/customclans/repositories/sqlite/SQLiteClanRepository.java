package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanCreateResult;
import io.github.maste.customclans.models.ClanListEntry;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.repositories.ClanRepository;
import io.github.maste.customclans.util.ValidationUtil;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;

public final class SQLiteClanRepository implements ClanRepository {

    private final SQLiteDatabase database;

    public SQLiteClanRepository(SQLiteDatabase database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<ClanCreateResult> createClan(
            UUID presidentUuid,
            String presidentName,
            String clanName,
            String tag,
            String tagColor,
            Instant createdAt
    ) {
        String normalizedName = ValidationUtil.normalizeClanName(clanName);
        return database.transactionAsync(connection -> {
            try (PreparedStatement membershipCheck = connection.prepareStatement(
                    "SELECT 1 FROM clan_members WHERE player_uuid = ?"
            )) {
                membershipCheck.setString(1, presidentUuid.toString());
                try (ResultSet resultSet = membershipCheck.executeQuery()) {
                    if (resultSet.next()) {
                        return new ClanCreateResult(ClanCreateResult.Status.ALREADY_IN_CLAN, null);
                    }
                }
            }

            try (PreparedStatement nameCheck = connection.prepareStatement(
                    "SELECT id FROM clans WHERE normalized_name = ?"
            )) {
                nameCheck.setString(1, normalizedName);
                try (ResultSet resultSet = nameCheck.executeQuery()) {
                    if (resultSet.next()) {
                        return new ClanCreateResult(ClanCreateResult.Status.NAME_TAKEN, null);
                    }
                }
            }

            long clanId;
            long createdAtMillis = createdAt.toEpochMilli();
            try (PreparedStatement insertClan = connection.prepareStatement(
                    "INSERT INTO clans (name, normalized_name, tag, tag_color, description, president_uuid, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, '', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                insertClan.setString(1, clanName);
                insertClan.setString(2, normalizedName);
                insertClan.setString(3, tag);
                insertClan.setString(4, tagColor);
                insertClan.setString(5, presidentUuid.toString());
                insertClan.setLong(6, createdAtMillis);
                insertClan.setLong(7, createdAtMillis);
                insertClan.executeUpdate();

                try (ResultSet generatedKeys = insertClan.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new IllegalStateException("Failed to retrieve generated clan id");
                    }
                    clanId = generatedKeys.getLong(1);
                }
            }

            try (PreparedStatement insertMember = connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, player_uuid, last_known_name, role, joined_at) VALUES (?, ?, ?, ?, ?)"
            )) {
                insertMember.setLong(1, clanId);
                insertMember.setString(2, presidentUuid.toString());
                insertMember.setString(3, presidentName);
                insertMember.setString(4, ClanRole.PRESIDENT.name());
                insertMember.setLong(5, createdAtMillis);
                insertMember.executeUpdate();
            }

            return new ClanCreateResult(
                    ClanCreateResult.Status.CREATED,
                    new Clan(clanId, clanName, tag, tagColor, "", null, presidentUuid, createdAt, createdAt)
            );
        });
    }

    @Override
    public CompletableFuture<Optional<Clan>> findById(long clanId) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE id = ?")) {
                statement.setLong(1, clanId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(SQLiteMapper.mapClan(resultSet)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Clan>> findByName(String name) {
        String normalizedName = ValidationUtil.normalizeClanName(name);
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE normalized_name = ?")) {
                statement.setString(1, normalizedName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(SQLiteMapper.mapClan(resultSet)) : Optional.empty();
                }
            }
        });
    }


    @Override
    public CompletableFuture<Optional<Clan>> findByNormalizedName(String normalizedName) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE normalized_name = ?")) {
                statement.setString(1, normalizedName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(SQLiteMapper.mapClan(resultSet)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> renameClan(long clanId, String newName) {
        String normalizedName = ValidationUtil.normalizeClanName(newName);
        return database.transactionAsync(connection -> {
            try (PreparedStatement nameCheck = connection.prepareStatement(
                    "SELECT id FROM clans WHERE normalized_name = ? AND id <> ?"
            )) {
                nameCheck.setString(1, normalizedName);
                nameCheck.setLong(2, clanId);
                try (ResultSet resultSet = nameCheck.executeQuery()) {
                    if (resultSet.next()) {
                        return false;
                    }
                }
            }

            try (PreparedStatement updateStatement = connection.prepareStatement(
                    "UPDATE clans SET name = ?, normalized_name = ?, updated_at = ? WHERE id = ?"
            )) {
                updateStatement.setString(1, newName);
                updateStatement.setString(2, normalizedName);
                updateStatement.setLong(3, Instant.now().toEpochMilli());
                updateStatement.setLong(4, clanId);
                return updateStatement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanTag(long clanId, String newTag) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET tag = ?, updated_at = ? WHERE id = ?"
                 )) {
                statement.setString(1, newTag);
                statement.setLong(2, Instant.now().toEpochMilli());
                statement.setLong(3, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanColor(long clanId, String newColor) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET tag_color = ?, updated_at = ? WHERE id = ?"
                 )) {
                statement.setString(1, newColor);
                statement.setLong(2, Instant.now().toEpochMilli());
                statement.setLong(3, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanDescription(long clanId, String description) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET description = ?, updated_at = ? WHERE id = ?"
                 )) {
                statement.setString(1, description);
                statement.setLong(2, Instant.now().toEpochMilli());
                statement.setLong(3, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanBanner(long clanId, String materialName, String patternsJson) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE clans
                         SET banner_material = ?, banner_patterns_json = ?, updated_at = ?
                         WHERE id = ?
                         """)) {
                statement.setString(1, materialName);
                statement.setString(2, patternsJson);
                statement.setLong(3, Instant.now().toEpochMilli());
                statement.setLong(4, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<ClanBannerData>> findClanBanner(long clanId) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, banner_material, banner_patterns_json
                         FROM clans
                         WHERE id = ?
                         """)) {
                statement.setLong(1, clanId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return SQLiteMapper.decodeClanBannerData(
                            resultSet.getLong("id"),
                            resultSet.getString("banner_material"),
                            resultSet.getString("banner_patterns_json")
                    );
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<ClanListEntry>> listActiveClans() {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT c.name, c.tag, c.tag_color, COUNT(m.player_uuid) AS member_count
                         FROM clans c
                         LEFT JOIN clan_members m ON m.clan_id = c.id
                         GROUP BY c.id
                         ORDER BY LOWER(c.name) ASC
                         """);
                 ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<ClanListEntry> clans = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    clans.add(SQLiteMapper.mapClanListEntry(resultSet));
                }
                return List.copyOf(clans);
            }
        });
    }


    @Override
    public CompletableFuture<List<Clan>> findAll() {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans ORDER BY lower(name) ASC");
                 ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<Clan> clans = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    clans.add(SQLiteMapper.mapClan(resultSet));
                }
                return List.copyOf(clans);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> listClanNames() {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT name
                         FROM clans
                         ORDER BY LOWER(name) ASC
                         """);
                 ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<String> clanNames = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    clanNames.add(resultSet.getString("name"));
                }
                return List.copyOf(clanNames);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> transferLeadership(long clanId, UUID currentPresidentUuid, UUID newPresidentUuid) {
        return database.transactionAsync(connection -> {
            try (PreparedStatement clanCheck = connection.prepareStatement("SELECT president_uuid FROM clans WHERE id = ?")) {
                clanCheck.setLong(1, clanId);
                try (ResultSet resultSet = clanCheck.executeQuery()) {
                    if (!resultSet.next() || !currentPresidentUuid.toString().equals(resultSet.getString("president_uuid"))) {
                        return false;
                    }
                }
            }

            try (PreparedStatement memberCheck = connection.prepareStatement(
                    "SELECT 1 FROM clan_members WHERE clan_id = ? AND player_uuid = ?"
            )) {
                memberCheck.setLong(1, clanId);
                memberCheck.setString(2, newPresidentUuid.toString());
                try (ResultSet resultSet = memberCheck.executeQuery()) {
                    if (!resultSet.next()) {
                        return false;
                    }
                }
            }

            try (PreparedStatement demoteOld = connection.prepareStatement(
                    "UPDATE clan_members SET role = ? WHERE clan_id = ? AND player_uuid = ?"
            )) {
                demoteOld.setString(1, ClanRole.MEMBER.name());
                demoteOld.setLong(2, clanId);
                demoteOld.setString(3, currentPresidentUuid.toString());
                demoteOld.executeUpdate();
            }

            try (PreparedStatement promoteNew = connection.prepareStatement(
                    "UPDATE clan_members SET role = ? WHERE clan_id = ? AND player_uuid = ?"
            )) {
                promoteNew.setString(1, ClanRole.PRESIDENT.name());
                promoteNew.setLong(2, clanId);
                promoteNew.setString(3, newPresidentUuid.toString());
                promoteNew.executeUpdate();
            }

            try (PreparedStatement updateClan = connection.prepareStatement(
                    "UPDATE clans SET president_uuid = ?, updated_at = ? WHERE id = ?"
            )) {
                updateClan.setString(1, newPresidentUuid.toString());
                updateClan.setLong(2, Instant.now().toEpochMilli());
                updateClan.setLong(3, clanId);
                return updateClan.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disbandClan(long clanId) {
        return database.transactionAsync(connection -> {
            try (PreparedStatement touchStatement = connection.prepareStatement(
                    "UPDATE clans SET updated_at = ? WHERE id = ?"
            )) {
                touchStatement.setLong(1, Instant.now().toEpochMilli());
                touchStatement.setLong(2, clanId);
                touchStatement.executeUpdate();
            }

            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                deleteStatement.setLong(1, clanId);
                deleteStatement.executeUpdate();
            }
            return null;
        });
    }

}
