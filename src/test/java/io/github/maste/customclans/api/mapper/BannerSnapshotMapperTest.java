package io.github.maste.customclans.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.maste.customclans.api.model.ClanBannerSnapshot;
import io.github.maste.customclans.models.ClanBannerData;
import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;
import org.junit.jupiter.api.Test;

class BannerSnapshotMapperTest {

    private final BannerSnapshotMapper mapper = new BannerSnapshotMapper();

    @Test
    void mapsMaterialBaseColorAndOrderedPatterns() {
        ClanBannerData data = new ClanBannerData(
                Material.LIGHT_BLUE_BANNER,
                List.of(
                        new ClanBannerData.PatternSpec(PatternType.CROSS, DyeColor.BLACK),
                        new ClanBannerData.PatternSpec(PatternType.BORDER, DyeColor.WHITE)
                )
        );

        ClanBannerSnapshot snapshot = mapper.map(data);

        assertNotNull(snapshot);
        assertEquals("minecraft:light_blue_banner", snapshot.baseMaterial());
        assertEquals("light_blue", snapshot.baseColor());
        assertEquals(2, snapshot.patterns().size());
        assertEquals("minecraft:cross", snapshot.patterns().get(0).patternId());
        assertEquals("black", snapshot.patterns().get(0).colorId());
        assertEquals("minecraft:border", snapshot.patterns().get(1).patternId());
        assertEquals("white", snapshot.patterns().get(1).colorId());
    }

    @Test
    void returnsNullWhenBannerDataMissing() {
        assertNull(mapper.map(null));
    }
}
