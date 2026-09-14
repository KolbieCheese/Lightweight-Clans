package io.github.maste.customclans.forge;

import io.github.maste.customclans.models.ClanBannerData;
import java.util.List;
import com.mojang.authlib.GameProfile;
import io.github.maste.customclans.repositories.sqlite.SQLiteDatabase;
import io.github.maste.customclans.repositories.sqlite.SQLiteClanRepository;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("lightweightclans")
@PrefixGameTestTemplate(false)
public class ClansGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void createAndPresidentPermissions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClanTestOwner"));
        var outsider = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClanTestOther"));
        String clanName = "Test " + UUID.randomUUID().toString().substring(0, 8);
        var path = server.getWorldPath(LevelResource.ROOT).resolve("lightweightclans/clans.db");
        helper.startSequence()
                .thenExecute(() -> server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(0), "clan create " + clanName))
                .thenExecuteAfter(30, () -> {
                    try (var db = new SQLiteDatabase(path, Logger.getLogger("ClanGameTest"))) {
                        var c = new SQLiteClanRepository(db).findByName(clanName).join();
                        helper.assertTrue(c.isPresent() && c.get().presidentUuid().equals(owner.getUUID()), "Create must persist the player as President");
                    }
                    server.getCommands().performPrefixedCommand(outsider.createCommandSourceStack().withPermission(0), "clan admin disband " + clanName.replace(' ', '-').toLowerCase(java.util.Locale.ROOT));
                    server.getCommands().performPrefixedCommand(outsider.createCommandSourceStack().withPermission(0), "clan disband");
                    server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(0), "clan leave");
                })
                .thenExecuteAfter(30, () -> {
                    try (var db = new SQLiteDatabase(path, Logger.getLogger("ClanGameTest"))) {
                        helper.assertTrue(new SQLiteClanRepository(db).findByName(clanName).join().isPresent(), "Unauthorized disband and President leave must not delete the clan");
                    }
                    server.getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(0), "clan disband");
                })
                .thenExecuteAfter(30, () -> {
                    try (var db = new SQLiteDatabase(path, Logger.getLogger("ClanGameTest"))) {
                        helper.assertTrue(new SQLiteClanRepository(db).findByName(clanName).join().isEmpty(), "President must be able to disband");
                    }
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void patternedBannerRoundTrip(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var original = new ItemStack(Items.BLUE_BANNER);
        var entries = new ListTag();
        var pattern = new CompoundTag();
        pattern.putString("Pattern", "bs");
        pattern.putInt("Color", 15);
        entries.add(pattern);
        original.getOrCreateTagElement("BlockEntityTag").put("Patterns", entries);
        var captured = BannerCodec.capture(original, server);
        helper.assertTrue(captured != null && captured.patternsJson().contains("minecraft:stripe_bottom"), "Banner must store the registry pattern ID");
        var restored = BannerCodec.restore(new ClanBannerData(captured.material(),
                List.of(new ClanBannerData.PatternSpec("minecraft:stripe_bottom", "black"))), server);
        helper.assertTrue(ItemStack.isSameItemSameTags(original, restored), "Patterned banner must round-trip exactly");
        helper.assertTrue(BannerCodec.capture(new ItemStack(Items.STONE), server) == null, "Non-banners must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandsRegistered(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var root = server.getCommands().getDispatcher().getRoot().getChild("clan");
        helper.assertTrue(root != null, "Clan command must register on a dedicated server");
        for (String command : List.of("create", "invite", "accept", "deny", "leave", "chat", "banner", "setbanner", "transfer", "kick", "admin")) {
            helper.assertTrue(root.getChild(command) != null, "Missing command: " + command);
        }
        helper.succeed();
    }
}
