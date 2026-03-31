package io.github.maste.customclans.services.api;

import io.github.maste.customclans.api.LightweightClansApi;
import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.repositories.ClanRepository;
import io.github.maste.customclans.util.ValidationUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LightweightClansApiImpl implements LightweightClansApi {

    private final ClanRepository clanRepository;
    private final ClanMemberRepository clanMemberRepository;

    public LightweightClansApiImpl(ClanRepository clanRepository, ClanMemberRepository clanMemberRepository) {
        this.clanRepository = clanRepository;
        this.clanMemberRepository = clanMemberRepository;
    }

    @Override
    public Optional<ClanSnapshot> getClanById(long clanId) {
        return clanRepository.findById(clanId).join().map(this::mapClanSnapshot);
    }

    @Override
    public Optional<ClanSnapshot> getClanByName(String name) {
        return clanRepository.findByName(name).join().map(this::mapClanSnapshot);
    }

    @Override
    public Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName) {
        return clanRepository.findByNormalizedName(normalizedName).join().map(this::mapClanSnapshot);
    }

    @Override
    public List<ClanSnapshot> getAllClans() {
        return clanRepository.findAll().join().stream().map(this::mapClanSnapshot).toList();
    }

    @Override
    public List<ClanMemberSnapshot> getMembersForClan(long clanId) {
        return clanMemberRepository.findByClanId(clanId).join().stream().map(this::mapClanMemberSnapshot).toList();
    }

    @Override
    public Optional<ClanBannerSnapshot> getBannerForClan(long clanId) {
        return clanRepository.findClanBanner(clanId).join().map(this::mapClanBannerSnapshot);
    }

    @Override
    public Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid) {
        return clanMemberRepository.findByPlayerUuid(playerUuid).join()
                .flatMap(member -> clanRepository.findById(member.clanId()).join())
                .map(this::mapClanSnapshot);
    }

    private ClanSnapshot mapClanSnapshot(Clan clan) {
        List<ClanMemberSnapshot> memberSnapshots = clanMemberRepository.findByClanId(clan.id()).join().stream()
                .map(this::mapClanMemberSnapshot)
                .toList();

        String presidentName = memberSnapshots.stream()
                .filter(member -> member.playerUuid().equals(clan.presidentUuid()))
                .map(ClanMemberSnapshot::lastKnownName)
                .findFirst()
                .orElse(null);

        return new ClanSnapshot(
                clan.id(),
                clan.name(),
                ValidationUtil.normalizeClanName(clan.name()),
                clan.tag(),
                clan.tagColor(),
                clan.description(),
                clan.presidentUuid(),
                presidentName,
                memberSnapshots.size(),
                memberSnapshots,
                mapClanBannerSnapshot(clan.bannerData()),
                clan.createdAt(),
                null
        );
    }

    private ClanMemberSnapshot mapClanMemberSnapshot(ClanMember member) {
        return new ClanMemberSnapshot(
                member.playerUuid(),
                member.lastKnownName(),
                member.role(),
                member.joinedAt()
        );
    }

    private ClanBannerSnapshot mapClanBannerSnapshot(ClanBannerData data) {
        if (data == null) {
            return null;
        }

        List<BannerPatternSnapshot> patternSnapshots = data.patterns().stream()
                .map(pattern -> new BannerPatternSnapshot(
                        pattern.pattern().name().toLowerCase(),
                        pattern.color().name().toLowerCase()
                ))
                .toList();

        return new ClanBannerSnapshot(data.material().name(), null, patternSnapshots);
    }
}
