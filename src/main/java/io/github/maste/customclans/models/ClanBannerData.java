package io.github.maste.customclans.models;

import java.util.List;
import java.util.Objects;

public record ClanBannerData(
        String materialId,
        List<PatternSpec> patterns
) {

    public ClanBannerData {
        Objects.requireNonNull(materialId, "materialId");
        if (materialId.isBlank()) {
            throw new IllegalArgumentException("materialId");
        }
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }

    public record PatternSpec(
            String patternId,
            String colorId
    ) {
        public PatternSpec {
            Objects.requireNonNull(patternId, "patternId");
            if (patternId.isBlank()) {
                throw new IllegalArgumentException("patternId");
            }
            Objects.requireNonNull(colorId, "colorId");
            if (colorId.isBlank()) {
                throw new IllegalArgumentException("colorId");
            }
        }
    }
}
