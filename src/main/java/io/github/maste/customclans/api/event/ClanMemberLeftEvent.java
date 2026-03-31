package io.github.maste.customclans.api.event;

import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a clan member leaves voluntarily.
 *
 * <p>This event is dispatched on the main server thread after membership changes have been
 * persisted and the updated clan state is available through the public API.
 */
public final class ClanMemberLeftEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanSnapshot before;
    private final ClanSnapshot after;
    private final ClanMemberSnapshot member;

    public ClanMemberLeftEvent(ClanSnapshot before, ClanSnapshot after, ClanMemberSnapshot member) {
        super(false);
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.member = Objects.requireNonNull(member, "member");
    }

    public ClanSnapshot getBefore() {
        return before;
    }

    public ClanSnapshot getAfter() {
        return after;
    }

    public ClanMemberSnapshot getMember() {
        return member;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
