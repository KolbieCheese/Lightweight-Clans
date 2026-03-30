package io.github.maste.customclans.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class MiniMessageUtil {

    private MiniMessageUtil() {
    }

    public static Component clanTagPrefix(String tag, TextColor color) {
        if (tag == null || tag.isBlank()) {
            return Component.empty();
        }
        return Component.text("[" + tag + "] ", color);
    }

    public static Component renderChatLine(
            MiniMessage miniMessage,
            String format,
            Component tagPrefix,
            String playerName,
            Component message
    ) {
        return renderChatLine(miniMessage, format, tagPrefix, Component.text(playerName), message);
    }

    public static Component renderChatLine(
            MiniMessage miniMessage,
            String format,
            Component tagPrefix,
            Component playerName,
            Component message
    ) {
        return miniMessage.deserialize(
                format,
                Placeholder.component("tag_prefix", tagPrefix),
                Placeholder.component("player_name", playerName),
                Placeholder.component("message", message)
        );
    }

    public static TagResolver placeholders(Map<String, String> placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        placeholders.forEach((key, value) -> resolvers.add(Placeholder.unparsed(key, value)));
        return TagResolver.resolver(resolvers);
    }
}
