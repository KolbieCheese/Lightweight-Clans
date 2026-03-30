package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.ClanListEntry;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class SQLiteMapper {

    private SQLiteMapper() {
    }

    static Clan mapClan(ResultSet resultSet) throws SQLException {
        return new Clan(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("tag_color"),
                resultSet.getString("description"),
                UUID.fromString(resultSet.getString("president_uuid")),
                Instant.ofEpochMilli(resultSet.getLong("created_at"))
        );
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
