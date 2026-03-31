package io.github.maste.customclans.repositories.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.maste.customclans.models.ClanCreateResult;
import io.github.maste.customclans.models.ClanBannerData;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.InviteAcceptResult;
import io.github.maste.customclans.models.InviteCreateResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabase database;
    private SQLiteClanRepository clanRepository;
    private SQLiteClanMemberRepository clanMemberRepository;
    private SQLiteClanInviteRepository clanInviteRepository;

    @BeforeEach
    void setUp() throws Exception {
        database = new SQLiteDatabase(tempDir.resolve("clans.db"), java.util.logging.Logger.getLogger("test"));
        database.initialize();
        clanRepository = new SQLiteClanRepository(database);
        clanMemberRepository = new SQLiteClanMemberRepository(database);
        clanInviteRepository = new SQLiteClanInviteRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void createClanRejectsCaseInsensitiveDuplicateNames() {
        UUID president = UUID.randomUUID();
        ClanCreateResult created = clanRepository.createClan(
                president,
                "Alice",
                "Crimson Knights",
                "CK",
                "gold",
                Instant.now()
        ).join();

        assertEquals(ClanCreateResult.Status.CREATED, created.status());
        assertTrue(clanMemberRepository.findSnapshotByPlayerUuid(president).join().isPresent());

        ClanCreateResult duplicate = clanRepository.createClan(
                UUID.randomUUID(),
                "Bob",
                "crimson knights",
                "CK2",
                "red",
                Instant.now()
        ).join();

        assertEquals(ClanCreateResult.Status.NAME_TAKEN, duplicate.status());
    }

    @Test
    void renameClanRejectsCaseInsensitiveDuplicateNames() {
        ClanCreateResult firstClan = clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Crimson Knights",
                "CK",
                "gold",
                Instant.now()
        ).join();
        ClanCreateResult secondClan = clanRepository.createClan(
                UUID.randomUUID(),
                "Bob",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();

        boolean renamed = clanRepository.renameClan(secondClan.clan().id(), "crimson knights").join();

        assertFalse(renamed);
        assertEquals("Azure Guard", clanRepository.findById(secondClan.clan().id()).join().orElseThrow().name());
        assertEquals("Crimson Knights", clanRepository.findById(firstClan.clan().id()).join().orElseThrow().name());
    }

    @Test
    void acceptInviteAddsMemberAndDeletesInvite() {
        UUID president = UUID.randomUUID();
        UUID invited = UUID.randomUUID();

        ClanCreateResult created = clanRepository.createClan(
                president,
                "Alice",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();

        ClanInvite invite = new ClanInvite(
                created.clan().id(),
                invited,
                president,
                Instant.now().plusSeconds(300)
        );
        clanInviteRepository.createInvite(invite, Instant.now()).join();

        InviteAcceptResult accepted = clanInviteRepository.acceptInvite(
                created.clan().id(),
                invited,
                "Bob",
                20,
                Instant.now()
        ).join();

        assertEquals(InviteAcceptResult.Status.ACCEPTED, accepted.status());
        assertTrue(clanMemberRepository.findSnapshotByPlayerUuid(invited).join().isPresent());
        assertFalse(clanInviteRepository.findByClanIdAndInvitedPlayerUuid(created.clan().id(), invited).join().isPresent());
    }

    @Test
    void createInviteAllowsDifferentClansToInviteSamePlayer() {
        UUID invited = UUID.randomUUID();
        ClanCreateResult firstClan = clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();
        ClanCreateResult secondClan = clanRepository.createClan(
                UUID.randomUUID(),
                "Carol",
                "Crimson Guard",
                "CG",
                "red",
                Instant.now()
        ).join();

        InviteCreateResult firstInvite = clanInviteRepository.createInvite(
                new ClanInvite(firstClan.clan().id(), invited, firstClan.clan().presidentUuid(), Instant.now().plusSeconds(300)),
                Instant.now()
        ).join();
        InviteCreateResult secondInvite = clanInviteRepository.createInvite(
                new ClanInvite(secondClan.clan().id(), invited, secondClan.clan().presidentUuid(), Instant.now().plusSeconds(300)),
                Instant.now()
        ).join();

        assertEquals(InviteCreateResult.Status.CREATED, firstInvite.status());
        assertEquals(InviteCreateResult.Status.CREATED, secondInvite.status());
        assertTrue(clanInviteRepository.findByClanIdAndInvitedPlayerUuid(firstClan.clan().id(), invited).join().isPresent());
        assertTrue(clanInviteRepository.findByClanIdAndInvitedPlayerUuid(secondClan.clan().id(), invited).join().isPresent());
    }

    @Test
    void bannerPersistsAndReloadsWithPatternOrder() {
        ClanCreateResult created = clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();

        List<ClanBannerData.PatternSpec> expectedPatterns = List.of(
                new ClanBannerData.PatternSpec(PatternType.STRIPE_TOP, DyeColor.BLACK),
                new ClanBannerData.PatternSpec(PatternType.BORDER, DyeColor.WHITE)
        );
        String patternsJson = "[" +
                "{\"pattern\":\"STRIPE_TOP\",\"color\":\"BLACK\"}," +
                "{\"pattern\":\"BORDER\",\"color\":\"WHITE\"}" +
                "]";

        clanRepository.updateClanBanner(created.clan().id(), Material.BLUE_BANNER.name(), patternsJson).join();

        Optional<ClanBannerData> banner = clanRepository.findClanBanner(created.clan().id()).join();
        assertTrue(banner.isPresent());
        assertEquals(Material.BLUE_BANNER, banner.orElseThrow().material());
        assertEquals(expectedPatterns, banner.orElseThrow().patterns());

        assertEquals(Material.BLUE_BANNER, clanRepository.findById(created.clan().id()).join().orElseThrow().bannerData().material());
        assertEquals(
                expectedPatterns,
                clanRepository.findById(created.clan().id()).join().orElseThrow().bannerData().patterns()
        );
    }
}
