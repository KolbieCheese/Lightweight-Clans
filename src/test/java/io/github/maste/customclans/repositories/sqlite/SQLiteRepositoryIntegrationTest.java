package io.github.maste.customclans.repositories.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.maste.customclans.models.ClanCreateResult;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.InviteAcceptResult;
import io.github.maste.customclans.models.InviteCreateResult;
import io.github.maste.customclans.util.ValidationUtil;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
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
    void createClanRejectsDuplicateSlugVariations() {
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
                "Crimson-Knights",
                "CK2",
                "red",
                Instant.now()
        ).join();

        assertEquals(ClanCreateResult.Status.NAME_TAKEN, duplicate.status());
    }

    @Test
    void renameClanRejectsDuplicateSlugVariations() {
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

        boolean renamed = clanRepository.renameClan(secondClan.clan().id(), "Crimson-Knights").join();

        assertFalse(renamed);
        assertEquals("Azure Guard", clanRepository.findById(secondClan.clan().id()).join().orElseThrow().name());
        assertEquals("Crimson Knights", clanRepository.findById(firstClan.clan().id()).join().orElseThrow().name());
    }

    @Test
    void lookupFindsClanBySlugDisplayNameAndLowercaseDisplayName() {
        ClanCreateResult created = clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Crimson Knights",
                "CK",
                "gold",
                Instant.now()
        ).join();

        assertEquals(created.clan().id(), clanRepository.findBySlug("crimson-knights").join().orElseThrow().id());
        assertEquals(created.clan().id(), clanRepository.findByName("Crimson Knights").join().orElseThrow().id());
        assertEquals(created.clan().id(), clanRepository.findByName("crimson knights").join().orElseThrow().id());
    }

    @Test
    void listClanSlugsReturnsCanonicalSlugsOnly() {
        clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Crimson Knights",
                "CK",
                "gold",
                Instant.now()
        ).join();
        clanRepository.createClan(
                UUID.randomUUID(),
                "Bob",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();

        assertEquals(java.util.List.of("azure-guard", "crimson-knights"), clanRepository.listClanSlugs().join());
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
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        ClanCreateResult created = clanRepository.createClan(
                UUID.randomUUID(),
                "Alice",
                "Azure Guard",
                "AG",
                "blue",
                Instant.now()
        ).join();

        String patternsJson = "[" +
                "{\"pattern\":\"STRIPE_TOP\",\"color\":\"BLACK\"}," +
                "{\"pattern\":\"BORDER\",\"color\":\"WHITE\"}" +
                "]";

        clanRepository.updateClanBanner(created.clan().id(), Material.BLUE_BANNER.name(), patternsJson).join();

        try (java.sql.Connection connection = database.openConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT banner_material, banner_patterns_json
                             FROM clans
                             WHERE id = ?
                             """
             )) {
            statement.setLong(1, created.clan().id());
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(Material.BLUE_BANNER.name(), resultSet.getString("banner_material"));
                assertEquals(patternsJson, resultSet.getString("banner_patterns_json"));
            }
        } catch (java.sql.SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void migrationGeneratesSlugForExistingClans() throws Exception {
        Path databasePath = tempDir.resolve("legacy-slug.db");
        seedLegacyDatabase(databasePath, java.util.List.of("Crimson Knights"));

        SQLiteDatabase migratedDatabase = new SQLiteDatabase(databasePath, java.util.logging.Logger.getLogger("test"));
        migratedDatabase.initialize();
        try {
            SQLiteClanRepository migratedRepository = new SQLiteClanRepository(migratedDatabase);
            assertEquals("crimson-knights", migratedRepository.findAll().join().getFirst().slug());
        } finally {
            migratedDatabase.close();
        }
    }

    @Test
    void migrationSuffixesCollidingAndBlankSlugsByIdOrder() throws Exception {
        Path databasePath = tempDir.resolve("legacy-collisions.db");
        seedLegacyDatabase(
                databasePath,
                java.util.List.of("Crimson Knights", "Crimson-Knights", "Crimson   Knights", "***", "---")
        );

        SQLiteDatabase migratedDatabase = new SQLiteDatabase(databasePath, java.util.logging.Logger.getLogger("test"));
        migratedDatabase.initialize();
        try {
            java.util.List<String> slugs = new java.util.ArrayList<>();
            try (java.sql.Connection connection = migratedDatabase.openConnection();
                 java.sql.PreparedStatement statement = connection.prepareStatement(
                         "SELECT slug FROM clans ORDER BY id ASC"
                 );
                 java.sql.ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    slugs.add(resultSet.getString("slug"));
                }
            }

            assertEquals(
                    java.util.List.of("crimson-knights", "crimson-knights-2", "crimson-knights-3", "clan", "clan-2"),
                    slugs
            );
            assertTrue(slugs.stream().noneMatch(String::isBlank));
        } finally {
            migratedDatabase.close();
        }
    }

    private static boolean supportsBannerMaterialApi() {
        try {
            return org.bukkit.Bukkit.getServer() != null && org.bukkit.Bukkit.getItemFactory() != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void seedLegacyDatabase(Path databasePath, java.util.List<String> clanNames) throws Exception {
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE clans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        tag_color TEXT NOT NULL,
                        president_uuid TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE clan_members (
                        clan_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        joined_at INTEGER NOT NULL,
                        UNIQUE (clan_id, player_uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE clan_invites (
                        clan_id INTEGER NOT NULL,
                        invited_player_uuid TEXT NOT NULL,
                        invited_by_uuid TEXT NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
        }

        try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             java.sql.PreparedStatement insertClan = connection.prepareStatement(
                     """
                             INSERT INTO clans (name, normalized_name, tag, tag_color, president_uuid, created_at, updated_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?)
                             """,
                     java.sql.Statement.RETURN_GENERATED_KEYS
             );
             java.sql.PreparedStatement insertMember = connection.prepareStatement(
                     """
                             INSERT INTO clan_members (clan_id, player_uuid, last_known_name, role, joined_at)
                             VALUES (?, ?, ?, ?, ?)
                             """
             )) {
            long now = Instant.now().toEpochMilli();
            for (int index = 0; index < clanNames.size(); index++) {
                String clanName = clanNames.get(index);
                UUID presidentUuid = UUID.nameUUIDFromBytes((clanName + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                insertClan.setString(1, clanName);
                insertClan.setString(2, ValidationUtil.normalizeClanName(clanName));
                insertClan.setString(3, "T" + index);
                insertClan.setString(4, "white");
                insertClan.setString(5, presidentUuid.toString());
                insertClan.setLong(6, now + index);
                insertClan.setLong(7, now + index);
                insertClan.executeUpdate();

                long clanId;
                try (java.sql.ResultSet generatedKeys = insertClan.getGeneratedKeys()) {
                    generatedKeys.next();
                    clanId = generatedKeys.getLong(1);
                }

                insertMember.setLong(1, clanId);
                insertMember.setString(2, presidentUuid.toString());
                insertMember.setString(3, "President" + index);
                insertMember.setString(4, "PRESIDENT");
                insertMember.setLong(5, now + index);
                insertMember.executeUpdate();
            }
        }
    }
}
