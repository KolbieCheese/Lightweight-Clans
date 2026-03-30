package io.github.maste.customclans.integrations.discord;

import io.github.maste.customclans.models.PlayerClanSnapshot;
import org.bukkit.entity.Player;

/**
 * Adapter abstraction for forwarding clan chat to external systems.
 *
 * <p>The service layer depends on this interface so integrations remain optional and swappable.
 */
public interface ClanChatRelay {

    void relay(Player sender, PlayerClanSnapshot snapshot, String rawMessage);
}
