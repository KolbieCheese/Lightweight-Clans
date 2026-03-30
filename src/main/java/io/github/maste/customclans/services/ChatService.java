package io.github.maste.customclans.services;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.util.ActionResult;
import io.github.maste.customclans.util.MiniMessageUtil;
import io.github.maste.customclans.util.SchedulerUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatService {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final ClanMemberRepository clanMemberRepository;
    private final MiniMessage miniMessage;
    private final ConcurrentMap<UUID, PlayerClanSnapshot> snapshots;
    private final Set<UUID> clanChatToggles;

    public ChatService(
            JavaPlugin plugin,
            PluginConfig pluginConfig,
            ClanMemberRepository clanMemberRepository,
            MiniMessage miniMessage
    ) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.clanMemberRepository = clanMemberRepository;
        this.miniMessage = miniMessage;
        this.snapshots = new ConcurrentHashMap<>();
        this.clanChatToggles = ConcurrentHashMap.newKeySet();
    }

    public Optional<PlayerClanSnapshot> cachedSnapshot(UUID playerUuid) {
        return Optional.ofNullable(snapshots.get(playerUuid));
    }

    public CompletableFuture<Optional<PlayerClanSnapshot>> getOrLoadSnapshot(UUID playerUuid) {
        PlayerClanSnapshot cachedSnapshot = snapshots.get(playerUuid);
        if (cachedSnapshot != null) {
            return CompletableFuture.completedFuture(Optional.of(cachedSnapshot));
        }
        return refreshSnapshot(playerUuid);
    }

    public CompletableFuture<Optional<PlayerClanSnapshot>> refreshSnapshot(UUID playerUuid) {
        return clanMemberRepository.findSnapshotByPlayerUuid(playerUuid).thenApply(optionalSnapshot -> {
            if (optionalSnapshot.isPresent()) {
                snapshots.put(playerUuid, optionalSnapshot.get());
            } else {
                clearPlayerState(playerUuid);
            }
            return optionalSnapshot;
        });
    }

    public CompletableFuture<Void> refreshSnapshots(Collection<UUID> playerUuids) {
        if (playerUuids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Optional<PlayerClanSnapshot>>> futures = new ArrayList<>();
        for (UUID playerUuid : playerUuids) {
            futures.add(refreshSnapshot(playerUuid));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<ActionResult<Boolean>> toggleClanChat(Player player) {
        if (!pluginConfig.clanChatEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure("chat.unavailable"));
        }
        if (!pluginConfig.clanChatToggleEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure("chat.toggle-unavailable"));
        }

        return getOrLoadSnapshot(player.getUniqueId()).thenApply(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return ActionResult.failure("common.no-clan");
            }

            UUID playerUuid = player.getUniqueId();
            boolean enabled;
            if (clanChatToggles.contains(playerUuid)) {
                clanChatToggles.remove(playerUuid);
                enabled = false;
            } else {
                clanChatToggles.add(playerUuid);
                enabled = true;
            }

            return enabled
                    ? ActionResult.success("chat.toggle-on", Boolean.TRUE)
                    : ActionResult.success("chat.toggle-off", Boolean.FALSE);
        });
    }

    public CompletableFuture<ActionResult<Void>> sendClanChat(Player sender, String rawMessage) {
        if (!pluginConfig.clanChatEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure("chat.unavailable"));
        }
        if (rawMessage == null || rawMessage.isBlank()) {
            return CompletableFuture.completedFuture(ActionResult.failure("chat.no-message"));
        }
        return sendClanChat(sender, Component.text(rawMessage));
    }

    public CompletableFuture<ActionResult<Void>> sendClanChat(Player sender, Component message) {
        if (!pluginConfig.clanChatEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure("chat.unavailable"));
        }
        return getOrLoadSnapshot(sender.getUniqueId()).thenCompose(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure("common.no-clan"));
            }

            PlayerClanSnapshot snapshot = optionalSnapshot.get();
            return clanMemberRepository.findByClanId(snapshot.clanId()).thenApply(members -> {
                SchedulerUtil.runSync(plugin, () -> broadcastClanMessage(sender, snapshot, message, members));
                return ActionResult.success("", null);
            });
        });
    }

    public boolean shouldRouteToClanChat(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (!pluginConfig.clanChatEnabled()) {
            clanChatToggles.remove(playerUuid);
            return false;
        }
        if (!pluginConfig.clanChatToggleEnabled() || !clanChatToggles.contains(playerUuid)) {
            return false;
        }
        if (!snapshots.containsKey(playerUuid)) {
            clanChatToggles.remove(playerUuid);
            return false;
        }
        return true;
    }

    public Component renderPublicChat(Player player, Component sourceDisplayName, Component message) {
        PlayerClanSnapshot snapshot = snapshots.get(player.getUniqueId());
        return MiniMessageUtil.renderChatLine(
                miniMessage,
                pluginConfig.publicChatFormat(),
                snapshot == null ? Component.empty() : tagPrefix(snapshot),
                sourceDisplayName,
                message
        );
    }

    public void clearPlayerState(UUID playerUuid) {
        snapshots.remove(playerUuid);
        clanChatToggles.remove(playerUuid);
    }

    public void clearPlayerStates(Collection<UUID> playerUuids) {
        for (UUID playerUuid : playerUuids) {
            clearPlayerState(playerUuid);
        }
    }

    public List<String> onlineClanMemberNames(long clanId, UUID excludePlayerUuid) {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(excludePlayerUuid))
                .filter(player -> {
                    PlayerClanSnapshot snapshot = snapshots.get(player.getUniqueId());
                    return snapshot != null && snapshot.clanId() == clanId;
                })
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void broadcastClanMessage(
            Player sender,
            PlayerClanSnapshot snapshot,
            Component message,
            List<ClanMember> members
    ) {
        Component clanChatMessage = MiniMessageUtil.renderChatLine(
                miniMessage,
                pluginConfig.clanChatFormat(),
                tagPrefix(snapshot),
                sender.getName(),
                message
        );

        members.stream()
                .map(member -> plugin.getServer().getPlayer(member.playerUuid()))
                .filter(player -> player != null && player.isOnline())
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(player -> player.sendMessage(clanChatMessage));
    }

    private Component tagPrefix(PlayerClanSnapshot snapshot) {
        TextColor color = Optional.ofNullable(pluginConfig.resolveClanColor(snapshot.tagColor()))
                .orElse(pluginConfig.defaultClanTagColor());
        return MiniMessageUtil.clanTagPrefix(
                snapshot.tag(),
                color
        );
    }
}
