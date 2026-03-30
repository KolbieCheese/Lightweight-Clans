package io.github.maste.customclans.config;

import io.github.maste.customclans.util.ValidationUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig {

    private static final Map<String, NamedTextColor> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", NamedTextColor.BLACK),
            Map.entry("dark_blue", NamedTextColor.DARK_BLUE),
            Map.entry("dark_green", NamedTextColor.DARK_GREEN),
            Map.entry("dark_aqua", NamedTextColor.DARK_AQUA),
            Map.entry("dark_red", NamedTextColor.DARK_RED),
            Map.entry("dark_purple", NamedTextColor.DARK_PURPLE),
            Map.entry("red", NamedTextColor.RED),
            Map.entry("gold", NamedTextColor.GOLD),
            Map.entry("yellow", NamedTextColor.YELLOW),
            Map.entry("green", NamedTextColor.GREEN),
            Map.entry("aqua", NamedTextColor.AQUA),
            Map.entry("blue", NamedTextColor.BLUE),
            Map.entry("light_purple", NamedTextColor.LIGHT_PURPLE),
            Map.entry("white", NamedTextColor.WHITE),
            Map.entry("gray", NamedTextColor.GRAY),
            Map.entry("dark_gray", NamedTextColor.DARK_GRAY)
    );

    private final int maxClanNameLength;
    private final int maxClanTagLength;
    private final String defaultClanTagColorId;
    private final TextColor defaultClanTagColor;
    private final int inviteExpirationSeconds;
    private final int maxClanSize;
    private final String publicChatFormat;
    private final String clanChatFormat;
    private final boolean clanChatEnabled;
    private final boolean clanChatToggleEnabled;
    private final boolean discordSrvClanChatRelayEnabled;
    private final String discordSrvClanChatChannel;
    private final String discordSrvClanChatFormat;
    private final NameModerationConfig nameModerationConfig;

    private PluginConfig(
            int maxClanNameLength,
            int maxClanTagLength,
            String defaultClanTagColorId,
            TextColor defaultClanTagColor,
            int inviteExpirationSeconds,
            int maxClanSize,
            String publicChatFormat,
            String clanChatFormat,
            boolean clanChatEnabled,
            boolean clanChatToggleEnabled,
            boolean discordSrvClanChatRelayEnabled,
            String discordSrvClanChatChannel,
            String discordSrvClanChatFormat,
            NameModerationConfig nameModerationConfig
    ) {
        this.maxClanNameLength = maxClanNameLength;
        this.maxClanTagLength = maxClanTagLength;
        this.defaultClanTagColorId = defaultClanTagColorId;
        this.defaultClanTagColor = defaultClanTagColor;
        this.inviteExpirationSeconds = inviteExpirationSeconds;
        this.maxClanSize = maxClanSize;
        this.publicChatFormat = publicChatFormat;
        this.clanChatFormat = clanChatFormat;
        this.clanChatEnabled = clanChatEnabled;
        this.clanChatToggleEnabled = clanChatToggleEnabled;
        this.discordSrvClanChatRelayEnabled = discordSrvClanChatRelayEnabled;
        this.discordSrvClanChatChannel = Objects.requireNonNullElse(discordSrvClanChatChannel, "global");
        this.discordSrvClanChatFormat = Objects.requireNonNullElse(discordSrvClanChatFormat, "[{clan}] {user}: {message}");
        this.nameModerationConfig = nameModerationConfig;
    }

    public static PluginConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int maxClanNameLength = Math.max(3, config.getInt("max-clan-name-length", 24));
        int configuredTagLength = Math.max(2, config.getInt("max-clan-tag-length", 4));
        int maxClanTagLength = Math.min(4, configuredTagLength);
        int inviteExpirationSeconds = Math.max(30, config.getInt("invite-expiration-seconds", 300));
        int maxClanSize = Math.max(2, config.getInt("max-clan-size", 20));
        String publicChatFormat = config.getString(
                "public-chat-format",
                "<tag_prefix><white><player_name></white><gray>: </gray><message>"
        );
        String clanChatFormat = config.getString(
                "clan-chat-format",
                "<dark_gray>[Clan]</dark_gray> <tag_prefix><white><player_name></white><gray>: </gray><message>"
        );

        String defaultColorId = ValidationUtil.normalizeClanColor(config.getString("default-clan-tag-color", "gold"));
        TextColor defaultColor = resolveColorValue(defaultColorId);
        if (defaultColor == null) {
            plugin.getLogger().warning("Invalid default clan tag color in config.yml. Falling back to gold.");
            defaultColorId = "gold";
            defaultColor = NamedTextColor.GOLD;
        }

        return new PluginConfig(
                maxClanNameLength,
                maxClanTagLength,
                defaultColorId,
                defaultColor,
                inviteExpirationSeconds,
                maxClanSize,
                publicChatFormat,
                clanChatFormat,
                config.getBoolean("clan-chat-enabled", true),
                config.getBoolean("clan-chat-toggle-enabled", true),
                config.getBoolean("discordsrv-clan-chat-relay.enabled", false),
                config.getString("discordsrv-clan-chat-relay.channel", "global"),
                config.getString(
                        "discordsrv-clan-chat-relay.format",
                        "[{clan}] {user}: {message}"
                ),
                loadNameModerationConfig(config)
        );
    }

    public int maxClanNameLength() {
        return maxClanNameLength;
    }

    public int maxClanTagLength() {
        return maxClanTagLength;
    }

    public String defaultClanTagColorId() {
        return defaultClanTagColorId;
    }

    public TextColor defaultClanTagColor() {
        return defaultClanTagColor;
    }

    public int inviteExpirationSeconds() {
        return inviteExpirationSeconds;
    }

    public int maxClanSize() {
        return maxClanSize;
    }

    public String publicChatFormat() {
        return publicChatFormat;
    }

    public String clanChatFormat() {
        return clanChatFormat;
    }

    public boolean clanChatEnabled() {
        return clanChatEnabled;
    }

    public boolean clanChatToggleEnabled() {
        return clanChatToggleEnabled;
    }

    public boolean discordSrvClanChatRelayEnabled() {
        return discordSrvClanChatRelayEnabled;
    }

    public String discordSrvClanChatChannel() {
        return discordSrvClanChatChannel;
    }

    public String discordSrvClanChatFormat() {
        return discordSrvClanChatFormat;
    }

    public NameModerationConfig nameModerationConfig() {
        return nameModerationConfig;
    }

    public List<String> namedClanColorNames() {
        return new ArrayList<>(NAMED_COLORS.keySet());
    }

    public TextColor resolveClanColor(String colorName) {
        String normalized = ValidationUtil.normalizeClanColor(colorName);
        return resolveColorValue(normalized);
    }

    public String normalizeClanColor(String colorName) {
        String normalized = ValidationUtil.normalizeClanColor(colorName);
        return resolveColorValue(normalized) == null ? "" : normalized;
    }

    private static TextColor resolveColorValue(String normalized) {
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.startsWith("#")) {
            return TextColor.fromHexString(normalized);
        }
        return NAMED_COLORS.get(normalized);
    }

    public String formatColorDisplayName(String colorName) {
        return ValidationUtil.formatClanColorDisplayName(colorName);
    }

    private static NameModerationConfig loadNameModerationConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("name-moderation");
        if (section == null) {
            return new NameModerationConfig(false, "clans.admin.bypass.restricted-names", List.of(), Map.of(), List.of());
        }

        Map<String, List<String>> blockedTerms = new LinkedHashMap<>();
        ConfigurationSection blockedTermsSection = section.getConfigurationSection("blocked-terms");
        if (blockedTermsSection != null) {
            for (String key : blockedTermsSection.getKeys(false)) {
                ConfigurationSection termSection = blockedTermsSection.getConfigurationSection(key);
                List<String> aliases = termSection == null ? List.of() : termSection.getStringList("aliases");
                blockedTerms.put(key, List.copyOf(aliases));
            }
        }

        return new NameModerationConfig(
                section.getBoolean("enabled", true),
                Objects.requireNonNullElse(
                        section.getString("bypass-permission"),
                        "clans.admin.bypass.restricted-names"
                ),
                List.copyOf(section.getStringList("restricted-clan-names")),
                Map.copyOf(blockedTerms),
                List.copyOf(section.getStringList("allowed-exceptions"))
        );
    }

    public record NameModerationConfig(
            boolean enabled,
            String bypassPermission,
            List<String> restrictedClanNames,
            Map<String, List<String>> blockedTerms,
            List<String> allowedExceptions
    ) {
    }
}
