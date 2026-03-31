package io.github.maste.customclans.api.model;

import io.github.maste.customclans.models.ClanRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a clan member.
 *
 * <p>This DTO is safe for external serialization and does not expose live mutable state.
 */
public record ClanMemberSnapshot(
        UUID playerUuid,
        String lastKnownName,
        ClanRole role,
        Instant joinedAt
) {

    public ClanMemberSnapshot {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
