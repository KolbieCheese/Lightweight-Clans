package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.ClanListEntry;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class SQLiteMapper {

    private static final Logger LOGGER = Logger.getLogger(SQLiteMapper.class.getName());

    private SQLiteMapper() {
    }

    static Clan mapClan(ResultSet resultSet) throws SQLException {
        return new Clan(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("tag_color"),
                resultSet.getString("description"),
                mapClanBannerData(resultSet).orElse(null),
                UUID.fromString(resultSet.getString("president_uuid")),
                Instant.ofEpochMilli(resultSet.getLong("created_at"))
        );
    }

    static Optional<ClanBannerData> mapClanBannerData(ResultSet resultSet) throws SQLException {
        return decodeClanBannerData(
                resultSet.getLong("id"),
                resultSet.getString("banner_material"),
                resultSet.getString("banner_patterns_json")
        );
    }

    static Optional<ClanBannerData> decodeClanBannerData(long clanId, String materialName, String patternsJson) {
        if (materialName == null || materialName.isBlank()) {
            return Optional.empty();
        }

        String normalizedMaterialName = materialName.trim().toUpperCase(Locale.ROOT);
        if (!normalizedMaterialName.endsWith("_BANNER") || normalizedMaterialName.endsWith("WALL_BANNER")) {
            LOGGER.warning("Ignoring non-banner clan banner material '" + materialName + "' for clan id " + clanId + ".");
            return Optional.empty();
        }

        List<ClanBannerData.PatternSpec> patterns = parsePatternSpecs(clanId, patternsJson);
        if (patterns == null) {
            return Optional.empty();
        }

        String normalizedMaterialId = normalizedMaterialName.toLowerCase(Locale.ROOT);
        return Optional.of(new ClanBannerData(normalizedMaterialId, patterns));
    }

    private static List<ClanBannerData.PatternSpec> parsePatternSpecs(long clanId, String patternsJson) {
        if (patternsJson == null || patternsJson.isBlank()) {
            return List.of();
        }

        String compact = patternsJson.replaceAll("\\s+", "");
        if ("[]".equals(compact)) {
            return List.of();
        }
        if (!compact.startsWith("[") || !compact.endsWith("]")) {
            LOGGER.warning("Ignoring malformed banner patterns JSON for clan id " + clanId + ".");
            return null;
        }

        String content = compact.substring(1, compact.length() - 1);
        if (content.isBlank()) {
            return List.of();
        }

        java.util.regex.Pattern entryPattern = java.util.regex.Pattern.compile("\\{\"pattern\":\"([^\"]+)\",\"color\":\"([A-Z_]+)\"\\}");
        ArrayList<ClanBannerData.PatternSpec> patternSpecs = new ArrayList<>();
        for (String rawEntry : content.split("(?<=\\}),(?=\\{)")) {
            java.util.regex.Matcher matcher = entryPattern.matcher(rawEntry);
            if (!matcher.matches()) {
                LOGGER.warning("Ignoring malformed banner patterns JSON for clan id " + clanId + ".");
                return null;
            }

            String patternId;
            String colorId;
            try {
                patternId = normalizePatternId(matcher.group(1));
                colorId = normalizeColorId(matcher.group(2));
            } catch (IllegalArgumentException exception) {
                LOGGER.warning("Ignoring invalid banner pattern/color enum for clan id " + clanId + ".");
                return null;
            }
            patternSpecs.add(new ClanBannerData.PatternSpec(patternId, colorId));
        }

        return List.copyOf(patternSpecs);
    }

    private static String normalizePatternId(String rawPattern) {
        String trimmed = rawPattern.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("pattern");
        }
        if (trimmed.contains(":")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeColorId(String rawColor) {
        String trimmed = rawColor.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("color");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    static ClanListEntry mapClanListEntry(ResultSet resultSet) throws SQLException {
        return new ClanListEntry(
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("tag_color"),
                resultSet.getInt("member_count")
        );
    }

    static ClanMember mapClanMember(ResultSet resultSet) throws SQLException {
        return new ClanMember(
                resultSet.getLong("clan_id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("last_known_name"),
                ClanRole.valueOf(resultSet.getString("role")),
                Instant.ofEpochMilli(resultSet.getLong("joined_at"))
        );
    }

    static ClanInvite mapInvite(ResultSet resultSet) throws SQLException {
        return new ClanInvite(
                resultSet.getLong("clan_id"),
                UUID.fromString(resultSet.getString("invited_player_uuid")),
                UUID.fromString(resultSet.getString("invited_by_uuid")),
                Instant.ofEpochMilli(resultSet.getLong("expires_at"))
        );
    }

    static PlayerClanSnapshot mapSnapshot(ResultSet resultSet) throws SQLException {
        return new PlayerClanSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("tag_color"),
                ClanRole.valueOf(resultSet.getString("role")),
                UUID.fromString(resultSet.getString("president_uuid"))
        );
    }
}
