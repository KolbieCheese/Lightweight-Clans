package io.github.maste.customclans.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a clan and related public data.
 *
 * <p>This DTO is safe for external serialization and does not expose live mutable state.
 */
public record ClanSnapshot(
        long id,
        String name,
        String normalizedName,
        String tag,
        String tagColor,
        String description,
        UUID presidentUuid,
        String presidentName,
        int memberCount,
        List<ClanMemberSnapshot> members,
        ClanBannerSnapshot banner,
        Instant createdAt,
        Instant updatedAt
) {

    public ClanSnapshot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(tagColor, "tagColor");
        description = description == null ? "" : description;
        Objects.requireNonNull(presidentUuid, "presidentUuid");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
