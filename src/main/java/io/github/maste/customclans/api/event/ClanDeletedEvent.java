package io.github.maste.customclans.api.event;

import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a clan has been deleted.
 *
 * <p>This event is dispatched on the main server thread after the clan has been fully removed from
 * plugin-managed storage.
 */
public final class ClanDeletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanSnapshot deletedClan;

    public ClanDeletedEvent(ClanSnapshot deletedClan) {
        super(false);
        this.deletedClan = Objects.requireNonNull(deletedClan, "deletedClan");
    }

    public ClanSnapshot getDeletedClan() {
        return deletedClan;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
