package io.github.maste.customclans.integrations.discord;

import io.github.maste.customclans.models.PlayerClanSnapshot;
import org.bukkit.entity.Player;

public final class NoopClanChatRelay implements ClanChatRelay {

    @Override
    public void relay(Player sender, PlayerClanSnapshot snapshot, String rawMessage) {
        // Intentionally no-op when integration is disabled or unavailable.
    }
}
