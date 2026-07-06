package io.github.maste.customclans.repositories.sqlite;

import io.github.maste.customclans.models.ClanBannerData;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteMapperTest {

    @Test
    void decodeClanBannerDataAcceptsLowercaseColor() {
        Optional<ClanBannerData> bannerData = SQLiteMapper.decodeClanBannerData(
                1L,
                "black_banner",
                "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"black\"}]"
        );

        assertTrue(bannerData.isPresent());
        assertEquals("black", bannerData.get().patterns().getFirst().colorId());
    }

    @Test
    void decodeClanBannerDataAcceptsUppercaseColor() {
        Optional<ClanBannerData> bannerData = SQLiteMapper.decodeClanBannerData(
                1L,
                "black_banner",
                "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"BLACK\"}]"
        );

        assertTrue(bannerData.isPresent());
        assertEquals("black", bannerData.get().patterns().getFirst().colorId());
    }

    @Test
    void decodeClanBannerDataAcceptsMixedCaseColor() {
        Optional<ClanBannerData> bannerData = SQLiteMapper.decodeClanBannerData(
                1L,
                "black_banner",
                "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"BlAcK\"}]"
        );

        assertTrue(bannerData.isPresent());
        assertEquals("black", bannerData.get().patterns().getFirst().colorId());
    }

    @Test
    void decodeClanBannerDataNormalizesNamespacedPatternIds() {
        Optional<ClanBannerData> bannerData = SQLiteMapper.decodeClanBannerData(
                1L,
                "black_banner",
                "[{\"pattern\":\"MINECRAFT:Stripe_Bottom\",\"color\":\"black\"}]"
        );

        assertTrue(bannerData.isPresent());
        assertEquals("minecraft:stripe_bottom", bannerData.get().patterns().getFirst().patternId());
    }

    @Test
    void decodeClanBannerDataRejectsMalformedColor() {
        Optional<ClanBannerData> bannerData = SQLiteMapper.decodeClanBannerData(
                1L,
                "black_banner",
                "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"bl@ck\"}]"
        );

        assertTrue(bannerData.isEmpty());
    }
}
