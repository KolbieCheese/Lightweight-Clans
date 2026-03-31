package io.github.maste.customclans.models;

import java.util.List;
import java.util.Objects;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public record ClanBannerData(
        Material material,
        List<PatternSpec> patterns
) {

    public ClanBannerData {
        Objects.requireNonNull(material, "material");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }

    public record PatternSpec(
            String patternId,
            DyeColor color
    ) {
        public PatternSpec {
            Objects.requireNonNull(patternId, "patternId");
            if (patternId.isBlank()) {
                throw new IllegalArgumentException("patternId");
            }
            Objects.requireNonNull(color, "color");
        }
    }
}
