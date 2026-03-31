package io.github.maste.customclans.services.api;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.util.ValidationUtil;
import java.util.List;

public final class ApiSnapshotMapper {

    public ClanSnapshot mapClanSnapshot(Clan clan, List<ClanMember> members) {
        List<ClanMemberSnapshot> memberSnapshots = members.stream()
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

    public ClanMemberSnapshot mapClanMemberSnapshot(ClanMember member) {
        return new ClanMemberSnapshot(
                member.playerUuid(),
                member.lastKnownName(),
                member.role(),
                member.joinedAt()
        );
    }

    public ClanBannerSnapshot mapClanBannerSnapshot(ClanBannerData data) {
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
