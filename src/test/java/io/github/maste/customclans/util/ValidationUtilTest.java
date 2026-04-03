package io.github.maste.customclans.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ValidationUtilTest {

    @Test
    void validatesClanNamesAndTags() {
        assertTrue(ValidationUtil.isValidClanName("Thirty Char Clan Name ExampleX", 30));
        assertTrue(ValidationUtil.isValidClanTag("CK", 6));

        assertFalse(ValidationUtil.isValidClanName("Crimson@Knights", 30));
        assertFalse(ValidationUtil.isValidClanName("This Clan Name Is Definitely Too Long Now", 30));
        assertFalse(ValidationUtil.isValidClanTag("C-K", 6));
        assertFalse(ValidationUtil.isValidClanTag("TOOLONG", 4));
    }

    @Test
    void derivesDefaultTagsFromInitialsAndFallbackCharacters() {
        assertEquals("CK", ValidationUtil.deriveDefaultTag("Crimson Knights", 6));
        assertEquals("ABC", ValidationUtil.deriveDefaultTag("Alpha Beta Core", 3));
        assertEquals("SOLO", ValidationUtil.deriveDefaultTag("Solo", 6));
    }

    @Test
    void normalizesClanNamesSlugsAndColors() {
        assertEquals("crimson knights", ValidationUtil.normalizeClanName("  Crimson Knights  "));
        assertEquals("crimson-knights", ValidationUtil.toSlug("  Crimson   Knights  "));
        assertEquals("crimsonknights", ValidationUtil.toSlug("Crimson@Knights!"));
        assertEquals("red-dragon", ValidationUtil.toSlug("Red---Dragon"));
        assertEquals("", ValidationUtil.toSlug("***"));
        assertEquals("dark_red", ValidationUtil.normalizeClanColor("Dark Red"));
        assertEquals("#FFAA00", ValidationUtil.normalizeClanColor("#ffaa00"));
        assertEquals("dark red", ValidationUtil.formatClanColorDisplayName("dark_red"));
    }

    @Test
    void normalizesModerationInputAndTokens() {
        assertEquals("admin", ValidationUtil.normalizeForModeration("@dm1n"));
        assertEquals("fuck", ValidationUtil.normalizeForModeration("f*ck"));
        assertEquals("bitch", ValidationUtil.normalizeForModeration("b!tch"));
        assertEquals(Set.of("a", "bitch"), ValidationUtil.moderationTokens("A b!tch"));
    }
}
