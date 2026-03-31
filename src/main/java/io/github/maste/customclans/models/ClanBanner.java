package io.github.maste.customclans.models;

import java.util.List;
import java.util.Objects;

public record ClanBanner(
        String material,
        List<ClanBannerPattern> patterns
) {

    public ClanBanner {
        Objects.requireNonNull(material, "material");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }
}
