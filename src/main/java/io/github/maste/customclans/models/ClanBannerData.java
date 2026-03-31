package io.github.maste.customclans.models;

import java.util.List;
import java.util.Objects;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;

public record ClanBannerData(
        Material material,
        List<PatternSpec> patterns
) {

    public ClanBannerData {
        Objects.requireNonNull(material, "material");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }

    public record PatternSpec(
            PatternType pattern,
            DyeColor color
    ) {
        public PatternSpec {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(color, "color");
        }
    }
}
