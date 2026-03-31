package io.github.maste.customclans.services.api;

import io.github.maste.customclans.api.mapper.BannerSnapshotMapper;
import io.github.maste.customclans.api.mapper.ClanSnapshotMapper;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanMember;
import java.util.List;

public final class ApiSnapshotMapper {

    private final ClanSnapshotMapper clanSnapshotMapper;
    private final BannerSnapshotMapper bannerSnapshotMapper;

    public ApiSnapshotMapper() {
        this.clanSnapshotMapper = new ClanSnapshotMapper();
        this.bannerSnapshotMapper = new BannerSnapshotMapper();
    }

    public ClanSnapshot mapClanSnapshot(Clan clan, List<ClanMember> members) {
        return clanSnapshotMapper.map(clan, members);
    }

    public ClanMemberSnapshot mapClanMemberSnapshot(ClanMember member) {
        return clanSnapshotMapper.mapMember(member);
    }

    public ClanBannerSnapshot mapClanBannerSnapshot(ClanBannerData data) {
        return bannerSnapshotMapper.map(data);
    }
}
