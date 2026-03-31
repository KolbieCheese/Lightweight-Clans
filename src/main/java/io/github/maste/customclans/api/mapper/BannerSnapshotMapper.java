package io.github.maste.customclans.api.mapper;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.models.ClanBannerData;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

public final class BannerSnapshotMapper {

    public ClanBannerSnapshot map(ClanBannerData data) {
        if (data == null) {
            return null;
        }

        List<BannerPatternSnapshot> patternSnapshots = data.patterns().stream()
                .map(pattern -> new BannerPatternSnapshot(
                        pattern.pattern().getKey().asString(),
                        pattern.color().name().toLowerCase(Locale.ROOT)
                ))
                .toList();

        return new ClanBannerSnapshot(
                data.material().getKey().asString(),
                deriveBaseColor(data.material()),
                patternSnapshots
        );
    }

    private String deriveBaseColor(Material material) {
        String materialName = material.name();
        String suffix = "_BANNER";
        if (!materialName.endsWith(suffix)) {
            return null;
        }

        String colorToken = materialName.substring(0, materialName.length() - suffix.length());
        if (colorToken.isBlank()) {
            return null;
        }

        return colorToken.toLowerCase(Locale.ROOT);
    }
}
