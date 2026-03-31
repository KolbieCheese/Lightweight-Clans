package io.github.maste.customclans.api.mapper;

import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.util.ValidationUtil;
import java.util.List;

public final class ClanSnapshotMapper {

    private final BannerSnapshotMapper bannerSnapshotMapper;

    public ClanSnapshotMapper() {
        this.bannerSnapshotMapper = new BannerSnapshotMapper();
    }

    public ClanSnapshot map(Clan clan, List<ClanMember> members) {
        List<ClanMemberSnapshot> memberSnapshots = members.stream()
                .map(this::mapMember)
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
                bannerSnapshotMapper.map(clan.bannerData()),
                clan.createdAt(),
                null
        );
    }

    public ClanMemberSnapshot mapMember(ClanMember member) {
        return new ClanMemberSnapshot(
                member.playerUuid(),
                member.lastKnownName(),
                member.role(),
                member.joinedAt()
        );
    }
}
