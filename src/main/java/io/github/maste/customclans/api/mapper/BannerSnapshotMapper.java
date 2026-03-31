package io.github.maste.customclans.api.mapper;

import io.github.maste.customclans.api.model.BannerPatternSnapshot;
import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.models.ClanBannerData;
import java.util.List;
import java.util.Locale;

public final class BannerSnapshotMapper {

    public ClanBannerSnapshot map(ClanBannerData data) {
        if (data == null) {
            return null;
        }

        List<BannerPatternSnapshot> patternSnapshots = data.patterns().stream()
                .map(pattern -> new BannerPatternSnapshot(
                        normalizePatternId(pattern.patternId()),
                        normalizeColorId(pattern.colorId())
                ))
                .toList();

        return new ClanBannerSnapshot(
                normalizeMaterialId(data.materialId()),
                deriveBaseColor(data.materialId()),
                patternSnapshots
        );
    }

    private String deriveBaseColor(String materialId) {
        String normalizedMaterialId = normalizeMaterialId(materialId);
        String materialName = normalizedMaterialId.startsWith("minecraft:")
                ? normalizedMaterialId.substring("minecraft:".length())
                : normalizedMaterialId;
        String suffix = "_BANNER";
        if (!materialName.toUpperCase(Locale.ROOT).endsWith(suffix)) {
            return null;
        }

        String colorToken = materialName.substring(0, materialName.length() - suffix.length());
        if (colorToken.isBlank()) {
            return null;
        }

        return colorToken.toLowerCase(Locale.ROOT);
    }

    private String normalizePatternId(String patternId) {
        String normalized = patternId.toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private String normalizeColorId(String colorId) {
        return colorId.toLowerCase(Locale.ROOT);
    }

    private String normalizeMaterialId(String materialId) {
        String normalized = materialId.toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
