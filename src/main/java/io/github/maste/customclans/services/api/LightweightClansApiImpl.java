package io.github.maste.customclans.services.api;

import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.repositories.ClanRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LightweightClansApiImpl implements LightweightClansApi {

    private final ClanRepository clanRepository;
    private final ClanMemberRepository clanMemberRepository;
    private final ApiSnapshotMapper snapshotMapper;

    public LightweightClansApiImpl(ClanRepository clanRepository, ClanMemberRepository clanMemberRepository) {
        this.clanRepository = clanRepository;
        this.clanMemberRepository = clanMemberRepository;
        this.snapshotMapper = new ApiSnapshotMapper();
    }

    @Override
    public Optional<ClanSnapshot> getClanById(long clanId) {
        return clanRepository.findById(clanId).join().map(this::mapClanSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanSnapshot>> getClanByIdAsync(long clanId) {
        return clanRepository.findById(clanId).thenCompose(this::mapClanSnapshotOptionalAsync);
    }

    @Override
    public Optional<ClanSnapshot> getClanByName(String name) {
        return clanRepository.findByName(name).join().map(this::mapClanSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanSnapshot>> getClanByNameAsync(String name) {
        return clanRepository.findByName(name).thenCompose(this::mapClanSnapshotOptionalAsync);
    }

    @Override
    public Optional<ClanSnapshot> getClanBySlug(String slug) {
        return clanRepository.findBySlug(slug).join().map(this::mapClanSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanSnapshot>> getClanBySlugAsync(String slug) {
        return clanRepository.findBySlug(slug).thenCompose(this::mapClanSnapshotOptionalAsync);
    }

    @Override
    public Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName) {
        return clanRepository.findByNormalizedName(normalizedName).join().map(this::mapClanSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanSnapshot>> getClanByNormalizedNameAsync(String normalizedName) {
        return clanRepository.findByNormalizedName(normalizedName).thenCompose(this::mapClanSnapshotOptionalAsync);
    }

    @Override
    public List<ClanSnapshot> getAllClans() {
        return clanRepository.findAll().join().stream().map(this::mapClanSnapshot).toList();
    }

    @Override
    public CompletableFuture<List<ClanSnapshot>> getAllClansAsync() {
        return clanRepository.findAll().thenCompose(clans -> {
            List<CompletableFuture<ClanSnapshot>> futures = clans.stream().map(this::mapClanSnapshotAsync).toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
        });
    }

    @Override
    public List<ClanMemberSnapshot> getMembersForClan(long clanId) {
        return clanMemberRepository.findByClanId(clanId).join().stream().map(this::mapClanMemberSnapshot).toList();
    }

    @Override
    public CompletableFuture<List<ClanMemberSnapshot>> getMembersForClanAsync(long clanId) {
        return clanMemberRepository.findByClanId(clanId)
                .thenApply(members -> members.stream().map(this::mapClanMemberSnapshot).toList());
    }

    @Override
    public Optional<ClanBannerSnapshot> getBannerForClan(long clanId) {
        return clanRepository.findClanBanner(clanId).join().map(snapshotMapper::mapClanBannerSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanBannerSnapshot>> getBannerForClanAsync(long clanId) {
        return clanRepository.findClanBanner(clanId).thenApply(banner -> banner.map(snapshotMapper::mapClanBannerSnapshot));
    }

    @Override
    public Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid) {
        return clanMemberRepository.findByPlayerUuid(playerUuid).join()
                .flatMap(member -> clanRepository.findById(member.clanId()).join())
                .map(this::mapClanSnapshot);
    }

    @Override
    public CompletableFuture<Optional<ClanSnapshot>> getClanForPlayerAsync(UUID playerUuid) {
        return clanMemberRepository.findByPlayerUuid(playerUuid)
                .thenCompose(member -> {
                    if (member.isEmpty()) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    return clanRepository.findById(member.get().clanId()).thenCompose(this::mapClanSnapshotOptionalAsync);
                });
    }

    private ClanSnapshot mapClanSnapshot(Clan clan) {
        return snapshotMapper.mapClanSnapshot(clan, clanMemberRepository.findByClanId(clan.id()).join());
    }

    private CompletableFuture<ClanSnapshot> mapClanSnapshotAsync(Clan clan) {
        return clanMemberRepository.findByClanId(clan.id())
                .thenApply(members -> snapshotMapper.mapClanSnapshot(clan, members));
    }

    private CompletableFuture<Optional<ClanSnapshot>> mapClanSnapshotOptionalAsync(Optional<Clan> clanOptional) {
        if (clanOptional.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return mapClanSnapshotAsync(clanOptional.get()).thenApply(Optional::of);
    }

    private ClanMemberSnapshot mapClanMemberSnapshot(ClanMember member) {
        return snapshotMapper.mapClanMemberSnapshot(member);
    }
}
