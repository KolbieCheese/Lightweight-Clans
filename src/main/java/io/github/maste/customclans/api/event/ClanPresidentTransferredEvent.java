package io.github.maste.customclans.api.event;

import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after clan presidency has been transferred from one member to another.
 *
 * <p>This event is dispatched on the main server thread after the transfer has been persisted and
 * the updated clan state is available through the public API.
 */
public final class ClanPresidentTransferredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanSnapshot before;
    private final ClanSnapshot after;
    private final ClanMemberSnapshot previousPresident;
    private final ClanMemberSnapshot newPresident;

    public ClanPresidentTransferredEvent(
            ClanSnapshot before,
            ClanSnapshot after,
            ClanMemberSnapshot previousPresident,
            ClanMemberSnapshot newPresident
    ) {
        super(false);
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.previousPresident = Objects.requireNonNull(previousPresident, "previousPresident");
        this.newPresident = Objects.requireNonNull(newPresident, "newPresident");
    }

    public ClanSnapshot getBefore() {
        return before;
    }

    public ClanSnapshot getAfter() {
        return after;
    }

    public ClanMemberSnapshot getPreviousPresident() {
        return previousPresident;
    }

    public ClanMemberSnapshot getNewPresident() {
        return newPresident;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
