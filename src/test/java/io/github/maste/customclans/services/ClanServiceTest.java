package io.github.maste.customclans.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.integrations.discord.NoopClanChatRelay;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.services.InviteService;
import io.github.maste.customclans.util.ActionResult;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClanServiceTest {

    @TempDir
    Path tempDir;

    private SQLiteDatabaseHolder holder;
    private ClanService clanService;
    private ChatService chatService;
    private InviteService inviteService;

    @BeforeEach
    void setUp() throws Exception {
        holder = new SQLiteDatabaseHolder(tempDir);
        PluginConfig pluginConfig = holder.pluginConfig();
        chatService = new ChatService(
                holder.plugin(),
                pluginConfig,
                holder.memberRepository(),
                new NoopClanChatRelay(),
                MiniMessage.miniMessage()
        );
        clanService = new ClanService(
                holder.plugin(),
                pluginConfig,
                holder.clanRepository(),
                holder.memberRepository(),
                chatService
        );
        inviteService = new InviteService(
                holder.plugin(),
                pluginConfig,
                holder.clanRepository(),
                holder.memberRepository(),
                holder.inviteRepository(),
                chatService
        );
    }

    @AfterEach
    void tearDown() {
        holder.close();
    }

    @Test
    void createClanRefreshesPlayerSnapshot() {
        Player player = mockPlayer("Alice");

        ActionResult<Clan> result = clanService.createClan(player, "Crimson Knights").join();

        assertTrue(result.success());
        assertTrue(chatService.cachedSnapshot(player.getUniqueId()).isPresent());
        assertEquals("create.success", result.messageKey());
    }

    @Test
    void createClanMakesCreatorPresident() {
        Player player = mockPlayer("Alice");

        clanService.createClan(player, "Crimson Knights").join();

        assertEquals(
                ClanRole.PRESIDENT,
                chatService.cachedSnapshot(player.getUniqueId()).orElseThrow().role()
        );
    }

    @Test
    void presidentCannotLeaveTheirClan() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.leaveClan(player).join();

        assertEquals("leave.president-cannot-leave", result.messageKey());
        assertTrue(chatService.cachedSnapshot(player.getUniqueId()).isPresent());
    }

    @Test
    void publicLookupFindsClanCaseInsensitively() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<io.github.maste.customclans.models.ClanInfo> result = clanService.getClanInfo("crimson knights").join();

        assertTrue(result.success());
        assertEquals("Crimson Knights", result.value().clan().name());
        assertEquals("Alice", result.value().presidentName());
    }

    @Test
    void publicLookupFailsForUnknownClan() {
        ActionResult<io.github.maste.customclans.models.ClanInfo> result = clanService.getClanInfo("Missing Clan").join();

        assertFalse(result.success());
        assertEquals("lookup.not-found", result.messageKey());
    }

    @Test
    void nonPresidentCannotRenameClan() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.renameClan(member, "Golden Guard").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotUpdateTag() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.updateTag(member, "GG").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotUpdateDescription() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.updateDescription(member, "We are friendly").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotUpdateColor() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.updateColor(member, "#00FF00").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotTransferLeadership() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<io.github.maste.customclans.models.ClanMember> result = clanService.transferLeadership(member, "Alice").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotKickMembers() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<io.github.maste.customclans.models.ClanMember> result = clanService.kickMember(member, "Alice").join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonPresidentCannotDisbandClan() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<java.util.List<io.github.maste.customclans.models.ClanMember>> result = clanService.disbandClan(member).join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void memberCanLeaveClan() {
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.leaveClan(member).join();

        assertTrue(result.success());
        assertEquals("leave.success", result.messageKey());
        assertTrue(chatService.cachedSnapshot(president.getUniqueId()).isPresent());
        assertTrue(chatService.cachedSnapshot(member.getUniqueId()).isEmpty());
    }

    @Test
    void validHexColorIsAcceptedAndNormalized() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateColor(player, "#00aa00").join();

        assertTrue(result.success());
        assertEquals("color.success", result.messageKey());
        assertEquals("#00AA00", clanService.getClanInfo("Crimson Knights").join().value().clan().tagColor());
    }

    @Test
    void invalidColorInputIsRejectedSafely() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateColor(player, "<bold>").join();

        assertFalse(result.success());
        assertEquals("validation.invalid-color", result.messageKey());
        assertEquals("white", clanService.getClanInfo("Crimson Knights").join().value().clan().tagColor());
    }

    @Test
    void goldColorIsRestrictedWithoutBypassPermission() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateColor(player, "gold").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-color", result.messageKey());
    }

    @Test
    void goldHexColorIsRestrictedWithoutBypassPermission() {
        Player player = mockPlayer("Alice");
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateColor(player, "#ffaa00").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-color", result.messageKey());
    }

    @Test
    void bypassPermissionAllowsRestrictedGoldColor() {
        Player player = mockPlayer("Alice");
        when(player.hasPermission("clans.admin.bypass.restricted-names")).thenReturn(true);
        clanService.createClan(player, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateColor(player, "gold").join();

        assertTrue(result.success());
        assertEquals("gold", clanService.getClanInfo("Crimson Knights").join().value().clan().tagColor());
    }

    @Test
    void createClanBlocksRestrictedName() {
        Player player = mockPlayer("Alice");

        ActionResult<Clan> result = clanService.createClan(player, "Admin").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-name", result.messageKey());
    }

    @Test
    void createClanBlocksObfuscatedRestrictedName() {
        Player player = mockPlayer("Alice");

        ActionResult<Clan> result = clanService.createClan(player, "@dmin").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-name", result.messageKey());
    }

    @Test
    void createClanAllowsInnocentException() {
        Player player = mockPlayer("Alice");

        ActionResult<Clan> result = clanService.createClan(player, "Passion").join();

        assertTrue(result.success());
        assertEquals("create.success", result.messageKey());
    }

    @Test
    void renameClanBlocksConfiguredAlias() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<Void> result = clanService.renameClan(president, "f*ck").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-name", result.messageKey());
    }

    @Test
    void updateTagBlocksRestrictedTag() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateTag(president, "Fuk").join();

        assertFalse(result.success());
        assertEquals("validation.restricted-tag", result.messageKey());
    }

    @Test
    void updateTagRejectsTagsLongerThanFourCharacters() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateTag(president, "ABCDE").join();

        assertFalse(result.success());
        assertEquals("validation.tag-too-long", result.messageKey());
        assertEquals("4", result.placeholders().get("max"));
    }

    @Test
    void descriptionRejectsBlankValues() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateDescription(president, "   ").join();

        assertFalse(result.success());
        assertEquals("validation.description-too-short", result.messageKey());
    }

    @Test
    void descriptionRejectsValuesLongerThan500Characters() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();
        String tooLong = "a".repeat(501);

        ActionResult<Void> result = clanService.updateDescription(president, tooLong).join();

        assertFalse(result.success());
        assertEquals("validation.description-too-long", result.messageKey());
    }

    @Test
    void presidentCanUpdateClanDescription() {
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<Void> result = clanService.updateDescription(president, "Raid-focused PvE clan").join();

        assertTrue(result.success());
        assertEquals("description.success", result.messageKey());
        assertEquals(
                "Raid-focused PvE clan",
                clanService.getClanInfo("Crimson Knights").join().value().clan().description()
        );
    }

    @Test
    void listClansReturnsMemberCounts() {
        Player alice = mockPlayer("Alice");
        Player bob = mockOnlinePlayer("Bob");
        createClanWithMember(alice, bob, "Crimson Knights");

        ActionResult<java.util.List<io.github.maste.customclans.models.ClanListEntry>> result = clanService.listClans().join();

        assertTrue(result.success());
        assertFalse(result.value().isEmpty());
        assertEquals("Crimson Knights", result.value().getFirst().name());
        assertEquals(2, result.value().getFirst().memberCount());
    }

    @Test
    void bypassPermissionSkipsRestrictedNameChecks() {
        Player player = mockPlayer("Alice");
        when(player.hasPermission("clans.admin.bypass.restricted-names")).thenReturn(true);

        ActionResult<Clan> createResult = clanService.createClan(player, "Admin").join();
        ActionResult<Void> renameResult = clanService.renameClan(player, "f*ck").join();
        ActionResult<Void> tagResult = clanService.updateTag(player, "Fuk").join();

        assertTrue(createResult.success());
        assertTrue(renameResult.success());
        assertTrue(tagResult.success());
    }

    @Test
    void presidentCanSetBannerWithValidBannerMeta() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(president.getInventory()).thenReturn(inventory);
        ItemStack banner = mock(ItemStack.class);
        BannerMeta bannerMeta = mock(BannerMeta.class);
        when(inventory.getItemInMainHand()).thenReturn(banner);
        when(banner.getAmount()).thenReturn(1);
        when(banner.getType()).thenReturn(Material.RED_BANNER);
        when(banner.getItemMeta()).thenReturn(bannerMeta);
        when(bannerMeta.getPatterns()).thenReturn(List.of());

        ActionResult<Void> result = clanService.setClanBanner(president).join();

        assertTrue(result.success());
        assertEquals("banner.set-success", result.messageKey());
    }

    @Test
    void nonPresidentDeniedForSetBanner() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        ActionResult<Void> result = clanService.setClanBanner(member).join();

        assertFalse(result.success());
        assertEquals("common.not-president", result.messageKey());
    }

    @Test
    void nonMemberDeniedForSetBanner() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player stranger = mockPlayer("Stranger");

        ActionResult<Void> result = clanService.setClanBanner(stranger).join();

        assertFalse(result.success());
        assertEquals("common.no-clan", result.messageKey());
    }

    @Test
    void setBannerFailsWhenHandIsEmpty() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(president.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(null);

        ActionResult<Void> result = clanService.setClanBanner(president).join();

        assertFalse(result.success());
        assertEquals("banner.must-hold-banner", result.messageKey());
    }

    @Test
    void setBannerFailsForNonBannerItem() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(president.getInventory()).thenReturn(inventory);
        ItemStack item = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getAmount()).thenReturn(1);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);

        ActionResult<Void> result = clanService.setClanBanner(president).join();

        assertFalse(result.success());
        assertEquals("banner.must-hold-banner", result.messageKey());
    }

    @Test
    void memberCanRetrieveBanner() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        Player member = mockOnlinePlayer("Bob");
        createClanWithMember(president, member, "Crimson Knights");

        holder.clanRepository().updateClanBanner(
                chatService.cachedSnapshot(president.getUniqueId()).orElseThrow().clanId(),
                Material.RED_BANNER.name(),
                "[]"
        ).join();

        ActionResult<ItemStack> result = clanService.getClanBannerItem(member).join();

        assertTrue(result.success());
        assertEquals("banner.receive-success", result.messageKey());
        assertEquals(Material.RED_BANNER, result.value().getType());
    }

    @Test
    void retrieveBannerFailsWhenNoBannerSet() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();

        ActionResult<ItemStack> result = clanService.getClanBannerItem(president).join();

        assertFalse(result.success());
        assertEquals("banner.not-set", result.messageKey());
    }

    @Test
    void nonMemberRetrieveBannerFails() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player stranger = mockPlayer("Stranger");

        ActionResult<ItemStack> result = clanService.getClanBannerItem(stranger).join();

        assertFalse(result.success());
        assertEquals("common.no-clan", result.messageKey());
    }

    @Test
    void reconstructedBannerPreservesMaterialAndReturnsNoPatternsWhenStoredEmpty() {
        Assumptions.assumeTrue(supportsBannerMaterialApi());
        Player president = mockPlayer("Alice");
        clanService.createClan(president, "Crimson Knights").join();
        holder.clanRepository().updateClanBanner(
                chatService.cachedSnapshot(president.getUniqueId()).orElseThrow().clanId(),
                Material.BLUE_BANNER.name(),
                "[]"
        ).join();

        ActionResult<ItemStack> result = clanService.getClanBannerItem(president).join();

        assertTrue(result.success());
        assertEquals(Material.BLUE_BANNER, result.value().getType());
        BannerMeta bannerMeta = (BannerMeta) result.value().getItemMeta();
        assertEquals(List.of(), bannerMeta.getPatterns());
    }

    private Player mockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(player.getName()).thenReturn(name);
        return player;
    }

    private Player mockOnlinePlayer(String name) {
        Player player = mockPlayer(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private void createClanWithMember(Player president, Player member, String clanName) {
        clanService.createClan(president, clanName).join();
        inviteService.sendInvite(president, member).join();
        inviteService.acceptInvite(member, clanName).join();
    }

    private static boolean supportsBannerMaterialApi() {
        try {
            return org.bukkit.Bukkit.getServer() != null && org.bukkit.Bukkit.getItemFactory() != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static final class SQLiteDatabaseHolder {

        private final io.github.maste.customclans.repositories.sqlite.SQLiteDatabase database;
        private final io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository clanRepository;
        private final io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository memberRepository;
        private final io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository inviteRepository;
        private final PluginConfig pluginConfig;
        private final JavaPlugin plugin;

        private SQLiteDatabaseHolder(Path tempDir) throws Exception {
            this.database = new io.github.maste.customclans.repositories.sqlite.SQLiteDatabase(
                    tempDir.resolve("service.db"),
                    java.util.logging.Logger.getLogger("test")
            );
            this.database.initialize();
            this.clanRepository = new io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository(database);
            this.memberRepository = new io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository(database);
            this.inviteRepository = new io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository(database);
            this.plugin = mock(JavaPlugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            this.pluginConfig = new TestPluginConfigFactory().create(plugin);
        }

        private io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository clanRepository() {
            return clanRepository;
        }

        private io.github.maste.customclans.repositories.sqlite.SQLiteClanMemberRepository memberRepository() {
            return memberRepository;
        }

        private io.github.maste.customclans.repositories.sqlite.SQLiteClanInviteRepository inviteRepository() {
            return inviteRepository;
        }

        private PluginConfig pluginConfig() {
            return pluginConfig;
        }

        private JavaPlugin plugin() {
            return plugin;
        }

        private void close() {
            database.close();
        }
    }

    private static final class TestPluginConfigFactory {

        private PluginConfig create(JavaPlugin plugin) {
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.set("max-clan-name-length", 24);
            yaml.set("max-clan-tag-length", 6);
            yaml.set("default-clan-tag-color", "white");
            yaml.set("invite-expiration-seconds", 300);
            yaml.set("max-clan-size", 20);
            yaml.set("public-chat-format", "<tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-format", "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>");
            yaml.set("clan-chat-enabled", true);
            yaml.set("clan-chat-toggle-enabled", true);
            yaml.set("name-moderation.enabled", true);
            yaml.set("name-moderation.bypass-permission", "clans.admin.bypass.restricted-names");
            yaml.set("name-moderation.restricted-clan-names", java.util.List.of(
                    "admin",
                    "moderator",
                    "owner",
                    "staff",
                    "official",
                    "support"
            ));
            yaml.set("name-moderation.blocked-terms.ass.aliases", java.util.List.of("a$$", "@ss"));
            yaml.set("name-moderation.blocked-terms.bitch.aliases", java.util.List.of("b1tch", "biitch"));
            yaml.set("name-moderation.blocked-terms.fuck.aliases", java.util.List.of("fuk", "fucc", "f*ck", "phuck"));
            yaml.set("name-moderation.allowed-exceptions", java.util.List.of("passion", "classic"));
            when(plugin.getConfig()).thenReturn(yaml);
            return PluginConfig.load(plugin);
        }
    }
}
