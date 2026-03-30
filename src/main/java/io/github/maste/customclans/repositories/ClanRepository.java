package io.github.maste.customclans.repositories;

import io.github.maste.customclans.models.Clan;
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

    CompletableFuture<Boolean> renameClan(long clanId, String newName);

    CompletableFuture<Void> updateClanTag(long clanId, String newTag);

    CompletableFuture<Void> updateClanColor(long clanId, String newColor);

    CompletableFuture<Void> updateClanDescription(long clanId, String description);

    CompletableFuture<List<ClanListEntry>> listActiveClans();

    CompletableFuture<List<String>> listClanNames();

    CompletableFuture<Boolean> transferLeadership(long clanId, UUID currentPresidentUuid, UUID newPresidentUuid);

    CompletableFuture<Void> disbandClan(long clanId);
}
