package io.github.maste.customclans.api;

import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
 *
 * <p><strong>Threading and blocking note:</strong> all non-async methods in this interface are
 * synchronous and may block the calling thread while reading from persistence.
 */
public interface LightweightClansApi {

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    Optional<ClanSnapshot> getClanById(long clanId);

    /**
     * Asynchronous lookup variant of {@link #getClanById(long)} for non-blocking integrations.
     */
    CompletableFuture<Optional<ClanSnapshot>> getClanByIdAsync(long clanId);

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    Optional<ClanSnapshot> getClanByName(String name);

    /**
     * Asynchronous lookup variant of {@link #getClanByName(String)} for non-blocking integrations.
     */
    CompletableFuture<Optional<ClanSnapshot>> getClanByNameAsync(String name);

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName);

    /**
     * Asynchronous lookup variant of {@link #getClanByNormalizedName(String)} for non-blocking
     * integrations.
     */
    CompletableFuture<Optional<ClanSnapshot>> getClanByNormalizedNameAsync(String normalizedName);

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    List<ClanSnapshot> getAllClans();

    /**
     * Asynchronous lookup variant of {@link #getAllClans()} for non-blocking integrations.
     */
    CompletableFuture<List<ClanSnapshot>> getAllClansAsync();

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    List<ClanMemberSnapshot> getMembersForClan(long clanId);

    /**
     * Asynchronous lookup variant of {@link #getMembersForClan(long)} for non-blocking integrations.
     */
    CompletableFuture<List<ClanMemberSnapshot>> getMembersForClanAsync(long clanId);

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    Optional<ClanBannerSnapshot> getBannerForClan(long clanId);

    /**
     * Asynchronous lookup variant of {@link #getBannerForClan(long)} for non-blocking integrations.
     */
    CompletableFuture<Optional<ClanBannerSnapshot>> getBannerForClanAsync(long clanId);

    /**
     * Synchronous lookup that may block while reading from persistence.
     */
    Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid);

    /**
     * Asynchronous lookup variant of {@link #getClanForPlayer(UUID)} for non-blocking integrations.
     */
    CompletableFuture<Optional<ClanSnapshot>> getClanForPlayerAsync(UUID playerUuid);
}
