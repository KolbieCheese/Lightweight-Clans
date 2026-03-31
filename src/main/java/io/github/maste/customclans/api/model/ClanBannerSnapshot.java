package io.github.maste.customclans.api.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a clan banner.
 *
 * <p>This DTO is safe for external serialization and does not expose live mutable state.
 */
public record ClanBannerSnapshot(
        String baseMaterial,
        String baseColor,
        List<BannerPatternSnapshot> patterns
) {

    public ClanBannerSnapshot {
        Objects.requireNonNull(baseMaterial, "baseMaterial");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }
}
