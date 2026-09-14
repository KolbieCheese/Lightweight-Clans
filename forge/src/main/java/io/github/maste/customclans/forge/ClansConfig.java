package io.github.maste.customclans.forge;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

final class ClansConfig {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue MAX_NAME, MAX_TAG, MAX_SIZE, INVITE_SECONDS;
    static final ForgeConfigSpec.BooleanValue CHAT, TOGGLE;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTRICTED;
    static {
        var b = new ForgeConfigSpec.Builder();
        MAX_NAME = b.defineInRange("maxClanNameLength", 30, 1, 100);
        MAX_TAG = b.defineInRange("maxClanTagLength", 4, 1, 16);
        MAX_SIZE = b.defineInRange("maxClanSize", 20, 1, 1000);
        INVITE_SECONDS = b.defineInRange("inviteExpirationSeconds", 300, 1, 86400);
        CHAT = b.define("clanChatEnabled", true);
        TOGGLE = b.define("clanChatToggleEnabled", true);
        RESTRICTED = b.comment("Reserved names/tags, matched after normalization. Operators bypass this list.")
                .defineListAllowEmpty("restrictedNames", List.of("admin", "moderator", "owner", "staff", "official", "support"), v -> v instanceof String);
        SPEC = b.build();
    }
    private ClansConfig() {}
}
