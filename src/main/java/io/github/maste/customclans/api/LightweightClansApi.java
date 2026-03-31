package io.github.maste.customclans.api;

import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only integration API for Lightweight Clans snapshots.
 *
 * <p>Snapshot DTOs returned by this API are exported from the canonical package
 * {@code io.github.maste.customclans.api.model}. Legacy DTO types previously available directly
 * under {@code io.github.maste.customclans.api} are no longer exported.
 *
 * <p>Lifecycle events exposed under {@code io.github.maste.customclans.api.event} are guaranteed to
 * be dispatched on the main server thread <strong>after</strong> persistence operations complete, so
 * listeners can immediately consume durable state through this API.
 */
public interface LightweightClansApi {

    Optional<ClanSnapshot> getClanById(long clanId);

    Optional<ClanSnapshot> getClanByName(String name);

    Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName);

    List<ClanSnapshot> getAllClans();

    List<ClanMemberSnapshot> getMembersForClan(long clanId);

    Optional<ClanBannerSnapshot> getBannerForClan(long clanId);

    Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid);
}
