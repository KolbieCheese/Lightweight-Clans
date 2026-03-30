package io.github.maste.customclans.models;

import java.util.Objects;

public record ClanListEntry(String name, String tag, String tagColor, int memberCount) {

    public ClanListEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(tagColor, "tagColor");
    }
}
