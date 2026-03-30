package io.github.maste.customclans.services;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.util.ValidationUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;

public final class NameModerationPolicy {

    private final boolean enabled;
    private final String bypassPermission;
    private final Set<String> shortRestrictedTerms;
    private final Set<String> longRestrictedTerms;
    private final Set<String> allowedExceptions;

    public NameModerationPolicy(PluginConfig.NameModerationConfig config) {
        this.enabled = config.enabled();
        this.bypassPermission = config.bypassPermission();
        this.shortRestrictedTerms = new HashSet<>();
        this.longRestrictedTerms = new HashSet<>();
        this.allowedExceptions = normalizeTerms(config.allowedExceptions());

        List<String> combinedTerms = new ArrayList<>();
        combinedTerms.addAll(config.restrictedClanNames());
        config.blockedTerms().forEach((term, aliases) -> {
            combinedTerms.add(term);
            combinedTerms.addAll(aliases);
        });

        for (String normalized : normalizeTerms(combinedTerms)) {
            if (normalized.length() <= 3) {
                shortRestrictedTerms.add(normalized);
            } else {
                longRestrictedTerms.add(normalized);
            }
        }
    }

    public boolean isRestrictedFor(Player player, String value) {
        if (!enabled || hasBypass(player) || value == null || value.isBlank()) {
            return false;
        }

        String normalizedValue = ValidationUtil.normalizeForModeration(value);
        if (normalizedValue.isBlank()) {
            return false;
        }

        Set<String> normalizedTokens = ValidationUtil.moderationTokens(value);
        if (isException(normalizedValue, normalizedTokens)) {
            return false;
        }

        if (shortRestrictedTerms.contains(normalizedValue) || containsAny(normalizedTokens, shortRestrictedTerms)) {
            return true;
        }

        if (containsLongTerm(normalizedValue) || containsLongTerm(normalizedTokens)) {
            return true;
        }

        return false;
    }

    private boolean hasBypass(Player player) {
        return player != null
                && bypassPermission != null
                && !bypassPermission.isBlank()
                && player.hasPermission(bypassPermission);
    }

    private boolean isException(String normalizedValue, Set<String> normalizedTokens) {
        if (allowedExceptions.isEmpty()) {
            return false;
        }
        return allowedExceptions.contains(normalizedValue) || containsAny(normalizedTokens, allowedExceptions);
    }

    private boolean containsLongTerm(String normalizedValue) {
        return longRestrictedTerms.stream().anyMatch(normalizedValue::contains);
    }

    private boolean containsLongTerm(Set<String> normalizedTokens) {
        return normalizedTokens.stream().anyMatch(this::containsLongTerm);
    }

    private static boolean containsAny(Set<String> candidates, Set<String> restricted) {
        return candidates.stream().anyMatch(restricted::contains);
    }

    private static Set<String> normalizeTerms(List<String> terms) {
        Set<String> normalized = new HashSet<>();
        for (String term : terms) {
            String normalizedTerm = ValidationUtil.normalizeForModeration(term);
            if (!normalizedTerm.isBlank()) {
                normalized.add(normalizedTerm);
            }
        }
        return normalized;
    }
}
