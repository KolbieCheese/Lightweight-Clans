package io.github.maste.customclans.api.event;

import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a clan has been created and persisted.
 *
 * <p>This event is dispatched on the main server thread after the new clan state is durable and
 * available through the public API.
 */
public final class ClanCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanSnapshot clan;

    public ClanCreatedEvent(ClanSnapshot clan) {
        super(false);
        this.clan = Objects.requireNonNull(clan, "clan");
    }

    public ClanSnapshot getClan() {
        return clan;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
