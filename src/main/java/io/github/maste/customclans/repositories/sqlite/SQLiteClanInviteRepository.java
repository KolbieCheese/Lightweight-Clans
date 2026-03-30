package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.InviteAcceptResult;
import io.github.maste.customclans.models.InviteCreateResult;
import io.github.maste.customclans.repositories.ClanInviteRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SQLiteClanInviteRepository implements ClanInviteRepository {

    private final SQLiteDatabase database;

    public SQLiteClanInviteRepository(SQLiteDatabase database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Optional<ClanInvite>> findByClanIdAndInvitedPlayerUuid(long clanId, UUID playerUuid) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
                 )) {
                statement.setLong(1, clanId);
                statement.setString(2, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(SQLiteMapper.mapInvite(resultSet)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<InviteCreateResult> createInvite(ClanInvite invite, Instant now) {
        return database.transactionAsync(connection -> {
            try (PreparedStatement inviteLookup = connection.prepareStatement(
                    "SELECT * FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
            )) {
                inviteLookup.setLong(1, invite.clanId());
                inviteLookup.setString(2, invite.invitedPlayerUuid().toString());
                try (ResultSet resultSet = inviteLookup.executeQuery()) {
                    if (resultSet.next()) {
                        ClanInvite existingInvite = SQLiteMapper.mapInvite(resultSet);
                        if (existingInvite.expiresAt().isAfter(now)) {
                            return new InviteCreateResult(InviteCreateResult.Status.DUPLICATE_FROM_SAME_CLAN);
                        }

                        try (PreparedStatement deleteExpired = connection.prepareStatement(
                                "DELETE FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
                        )) {
                            deleteExpired.setLong(1, invite.clanId());
                            deleteExpired.setString(2, invite.invitedPlayerUuid().toString());
                            deleteExpired.executeUpdate();
                        }
                    }
                }
            }

            try (PreparedStatement insertInvite = connection.prepareStatement(
                    "INSERT INTO clan_invites (clan_id, invited_player_uuid, invited_by_uuid, expires_at) VALUES (?, ?, ?, ?)"
            )) {
                insertInvite.setLong(1, invite.clanId());
                insertInvite.setString(2, invite.invitedPlayerUuid().toString());
                insertInvite.setString(3, invite.invitedByUuid().toString());
                insertInvite.setLong(4, invite.expiresAt().toEpochMilli());
                insertInvite.executeUpdate();
            }

            return new InviteCreateResult(InviteCreateResult.Status.CREATED);
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteByClanIdAndInvitedPlayerUuid(long clanId, UUID playerUuid) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
                 )) {
                statement.setLong(1, clanId);
                statement.setString(2, playerUuid.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> deleteExpiredInvites(Instant now) {
        return database.supplyAsync(() -> {
            try (var connection = database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM clan_invites WHERE expires_at <= ?"
                 )) {
                statement.setLong(1, now.toEpochMilli());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<InviteAcceptResult> acceptInvite(
            long clanId,
            UUID playerUuid,
            String playerName,
            int maxClanSize,
            Instant now
    ) {
        return database.transactionAsync(connection -> {
            ClanInvite invite;
            try (PreparedStatement lookupInvite = connection.prepareStatement(
                    "SELECT * FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
            )) {
                lookupInvite.setLong(1, clanId);
                lookupInvite.setString(2, playerUuid.toString());
                try (ResultSet resultSet = lookupInvite.executeQuery()) {
                    if (!resultSet.next()) {
                        return new InviteAcceptResult(InviteAcceptResult.Status.NO_INVITE, null);
                    }
                    invite = SQLiteMapper.mapInvite(resultSet);
                }
            }

            if (!invite.expiresAt().isAfter(now)) {
                try (PreparedStatement deleteInvite = connection.prepareStatement(
                        "DELETE FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
                )) {
                    deleteInvite.setLong(1, clanId);
                    deleteInvite.setString(2, playerUuid.toString());
                    deleteInvite.executeUpdate();
                }
                return new InviteAcceptResult(InviteAcceptResult.Status.EXPIRED, null);
            }

            try (PreparedStatement memberLookup = connection.prepareStatement(
                    "SELECT 1 FROM clan_members WHERE player_uuid = ?"
            )) {
                memberLookup.setString(1, playerUuid.toString());
                try (ResultSet resultSet = memberLookup.executeQuery()) {
                    if (resultSet.next()) {
                        try (PreparedStatement deleteInvite = connection.prepareStatement(
                                "DELETE FROM clan_invites WHERE invited_player_uuid = ?"
                        )) {
                            deleteInvite.setString(1, playerUuid.toString());
                            deleteInvite.executeUpdate();
                        }
                        return new InviteAcceptResult(InviteAcceptResult.Status.ALREADY_IN_CLAN, null);
                    }
                }
            }

            Clan clan;
            try (PreparedStatement clanLookup = connection.prepareStatement(
                    "SELECT * FROM clans WHERE id = ?"
            )) {
                clanLookup.setLong(1, invite.clanId());
                try (ResultSet resultSet = clanLookup.executeQuery()) {
                    if (!resultSet.next()) {
                        try (PreparedStatement deleteInvite = connection.prepareStatement(
                                "DELETE FROM clan_invites WHERE clan_id = ? AND invited_player_uuid = ?"
                        )) {
                            deleteInvite.setLong(1, clanId);
                            deleteInvite.setString(2, playerUuid.toString());
                            deleteInvite.executeUpdate();
                        }
                        return new InviteAcceptResult(InviteAcceptResult.Status.CLAN_MISSING, null);
                    }
                    clan = SQLiteMapper.mapClan(resultSet);
                }
            }

            try (PreparedStatement memberCount = connection.prepareStatement(
                    "SELECT COUNT(*) AS total FROM clan_members WHERE clan_id = ?"
            )) {
                memberCount.setLong(1, clan.id());
                try (ResultSet resultSet = memberCount.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt("total") >= maxClanSize) {
                        return new InviteAcceptResult(InviteAcceptResult.Status.CLAN_FULL, null);
                    }
                }
            }

            try (PreparedStatement insertMember = connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, player_uuid, last_known_name, role, joined_at) VALUES (?, ?, ?, ?, ?)"
            )) {
                insertMember.setLong(1, invite.clanId());
                insertMember.setString(2, playerUuid.toString());
                insertMember.setString(3, playerName);
                insertMember.setString(4, ClanRole.MEMBER.name());
                insertMember.setLong(5, now.toEpochMilli());
                insertMember.executeUpdate();
            }

            try (PreparedStatement deleteInvite = connection.prepareStatement(
                    "DELETE FROM clan_invites WHERE invited_player_uuid = ?"
            )) {
                deleteInvite.setString(1, playerUuid.toString());
                deleteInvite.executeUpdate();
            }

            return new InviteAcceptResult(InviteAcceptResult.Status.ACCEPTED, clan);
        });
    }
}
