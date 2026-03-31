package io.github.maste.customclans.api.model;

import java.util.Objects;

/**
 * Immutable snapshot of a single banner pattern entry.
 *
 * <p>This DTO is safe for external serialization and does not expose live mutable state.
 * Pattern order is implied by the containing list position.
 */
public record BannerPatternSnapshot(
        String patternId,
        String colorId
) {

    public BannerPatternSnapshot {
        Objects.requireNonNull(patternId, "patternId");
        Objects.requireNonNull(colorId, "colorId");
    }
}
