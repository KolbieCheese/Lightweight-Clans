package io.github.maste.customclans.api;

import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LightweightClansApi {

    Optional<ClanSnapshot> getClanById(long clanId);

    Optional<ClanSnapshot> getClanByName(String name);

    Optional<ClanSnapshot> getClanByNormalizedName(String normalizedName);

    List<ClanSnapshot> getAllClans();

    List<ClanMemberSnapshot> getMembersForClan(long clanId);

    Optional<ClanBannerSnapshot> getBannerForClan(long clanId);

    Optional<ClanSnapshot> getClanForPlayer(UUID playerUuid);
}
