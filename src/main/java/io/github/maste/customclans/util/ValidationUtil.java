package io.github.maste.customclans.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern CLAN_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9 _-]+$");
    private static final Pattern CLAN_TAG_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Za-z0-9]");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6})$");

    private ValidationUtil() {
    }

    public static boolean isValidClanName(String name, int maxLength) {
        return name != null
                && !name.isBlank()
                && name.length() <= maxLength
                && CLAN_NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isValidClanTag(String tag, int maxLength) {
        return tag != null
                && !tag.isBlank()
                && tag.length() <= maxLength
                && CLAN_TAG_PATTERN.matcher(tag).matches();
    }

    public static String deriveDefaultTag(String clanName, int maxLength) {
        StringBuilder initials = new StringBuilder();
        Matcher matcher = WORD_PATTERN.matcher(clanName);
        int wordCount = 0;
        while (matcher.find() && initials.length() < maxLength) {
            wordCount++;
            initials.append(Character.toUpperCase(matcher.group().charAt(0)));
        }
        if (wordCount > 1 && !initials.isEmpty()) {
            return initials.toString();
        }

        StringBuilder fallback = new StringBuilder();
        Matcher fallbackMatcher = ALPHANUMERIC_PATTERN.matcher(clanName);
        while (fallbackMatcher.find() && fallback.length() < maxLength) {
            fallback.append(fallbackMatcher.group().toUpperCase(Locale.ROOT));
        }
        if (!fallback.isEmpty()) {
            return fallback.toString();
        }

        return "CLAN".substring(0, Math.min(4, maxLength));
    }

    public static String normalizeClanName(String clanName) {
        return clanName == null ? "" : clanName.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeClanColor(String color) {
        if (color == null) {
            return "";
        }

        String trimmed = color.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_COLOR_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return "#" + matcher.group(1).toUpperCase(Locale.ROOT);
        }

        return trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static String formatClanColorDisplayName(String color) {
        String normalized = normalizeClanColor(color);
        return normalized.startsWith("#") ? normalized : normalized.replace('_', ' ');
    }
}
