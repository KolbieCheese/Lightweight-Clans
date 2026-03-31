package io.github.maste.customclans.models;

import java.util.Objects;

public record ClanBannerPattern(
        String color,
        String pattern
) {

    public ClanBannerPattern {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(pattern, "pattern");
    }
}
