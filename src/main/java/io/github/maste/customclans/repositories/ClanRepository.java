package io.github.maste.customclans.repositories;

import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanCreateResult;
import io.github.maste.customclans.models.ClanListEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ClanRepository {

    CompletableFuture<ClanCreateResult> createClan(
            UUID presidentUuid,
            String presidentName,
            String clanName,
            String tag,
            String tagColor,
            Instant createdAt
    );

    CompletableFuture<Optional<Clan>> findById(long clanId);

    CompletableFuture<Optional<Clan>> findByName(String name);

    CompletableFuture<Optional<Clan>> findByNormalizedName(String normalizedName);

    CompletableFuture<Boolean> renameClan(long clanId, String newName);

    /**
     * Updates clan tag and refreshes {@code updated_at}.
     */
    CompletableFuture<Void> updateClanTag(long clanId, String newTag);

    /**
     * Updates clan color and refreshes {@code updated_at}.
     */
    CompletableFuture<Void> updateClanColor(long clanId, String newColor);

    /**
     * Updates clan description and refreshes {@code updated_at}.
     */
    CompletableFuture<Void> updateClanDescription(long clanId, String description);

    /**
     * Updates clan banner fields and refreshes {@code updated_at}.
     */
    CompletableFuture<Void> updateClanBanner(long clanId, String materialName, String patternsJson);

    CompletableFuture<Optional<ClanBannerData>> findClanBanner(long clanId);

    CompletableFuture<List<ClanListEntry>> listActiveClans();

    CompletableFuture<List<Clan>> findAll();

    CompletableFuture<List<String>> listClanNames();

    CompletableFuture<Boolean> transferLeadership(long clanId, UUID currentPresidentUuid, UUID newPresidentUuid);

    CompletableFuture<Void> disbandClan(long clanId);
}
