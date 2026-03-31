package io.github.maste.customclans.api.event;

import io.github.maste.customclans.api.model.ClanSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an existing clan has been updated.
 *
 * <p>This event is dispatched on the main server thread after the updated clan state is durable and
 * available through the public API.
 */
public final class ClanUpdatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanSnapshot before;
    private final ClanSnapshot after;
    private final Set<String> changedFields;

    public ClanUpdatedEvent(ClanSnapshot before, ClanSnapshot after, Set<String> changedFields) {
        super(false);
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.changedFields = changedFields == null ? null : Set.copyOf(changedFields);
    }

    public ClanSnapshot getBefore() {
        return before;
    }

    public ClanSnapshot getAfter() {
        return after;
    }

    public Optional<Set<String>> getChangedFields() {
        return Optional.ofNullable(changedFields);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
