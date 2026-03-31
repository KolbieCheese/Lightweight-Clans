package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBanner;
import io.github.maste.customclans.models.ClanBannerPattern;
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
import java.util.stream.Collectors;

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
            try (PreparedStatement insertClan = connection.prepareStatement(
                    "INSERT INTO clans (name, normalized_name, tag, tag_color, description, president_uuid, created_at) "
                            + "VALUES (?, ?, ?, ?, '', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                insertClan.setString(1, clanName);
                insertClan.setString(2, normalizedName);
                insertClan.setString(3, tag);
                insertClan.setString(4, tagColor);
                insertClan.setString(5, presidentUuid.toString());
                insertClan.setLong(6, createdAt.toEpochMilli());
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
                insertMember.setLong(5, createdAt.toEpochMilli());
                insertMember.executeUpdate();
            }

            return new ClanCreateResult(
                    ClanCreateResult.Status.CREATED,
                    new Clan(clanId, clanName, tag, tagColor, "", presidentUuid, createdAt)
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
                    "UPDATE clans SET name = ?, normalized_name = ? WHERE id = ?"
            )) {
                updateStatement.setString(1, newName);
                updateStatement.setString(2, normalizedName);
                updateStatement.setLong(3, clanId);
                return updateStatement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanTag(long clanId, String newTag) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE clans SET tag = ? WHERE id = ?")) {
                statement.setString(1, newTag);
                statement.setLong(2, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanColor(long clanId, String newColor) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE clans SET tag_color = ? WHERE id = ?")) {
                statement.setString(1, newColor);
                statement.setLong(2, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateClanDescription(long clanId, String description) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE clans SET description = ? WHERE id = ?")) {
                statement.setString(1, description);
                statement.setLong(2, clanId);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> upsertClanBanner(long clanId, ClanBanner banner) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO clan_banners (clan_id, material, patterns)
                         VALUES (?, ?, ?)
                         ON CONFLICT(clan_id) DO UPDATE SET
                             material = excluded.material,
                             patterns = excluded.patterns
                         """)) {
                statement.setLong(1, clanId);
                statement.setString(2, banner.material());
                statement.setString(3, encodePatterns(banner.patterns()));
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<ClanBanner>> findClanBanner(long clanId) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT material, patterns
                         FROM clan_banners
                         WHERE clan_id = ?
                         """)) {
                statement.setLong(1, clanId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new ClanBanner(
                            resultSet.getString("material"),
                            decodePatterns(resultSet.getString("patterns"))
                    ));
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
                    "UPDATE clans SET president_uuid = ? WHERE id = ?"
            )) {
                updateClan.setString(1, newPresidentUuid.toString());
                updateClan.setLong(2, clanId);
                return updateClan.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disbandClan(long clanId) {
        return database.runAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                statement.setLong(1, clanId);
                statement.executeUpdate();
            }
        });
    }

    private static String encodePatterns(List<ClanBannerPattern> patterns) {
        return patterns.stream()
                .map(pattern -> pattern.color() + ":" + pattern.pattern())
                .collect(Collectors.joining(";"));
    }

    private static List<ClanBannerPattern> decodePatterns(String encodedPatterns) {
        if (encodedPatterns == null || encodedPatterns.isBlank()) {
            return List.of();
        }

        java.util.ArrayList<ClanBannerPattern> patterns = new java.util.ArrayList<>();
        for (String entry : encodedPatterns.split(";")) {
            int separatorIndex = entry.indexOf(':');
            if (separatorIndex <= 0 || separatorIndex == entry.length() - 1) {
                continue;
            }
            String color = entry.substring(0, separatorIndex);
            String pattern = entry.substring(separatorIndex + 1);
            patterns.add(new ClanBannerPattern(color, pattern));
        }
        return List.copyOf(patterns);
    }
}
