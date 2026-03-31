package io.github.maste.customclans.services;

import io.github.maste.customclans.api.event.ClanMemberJoinedEvent;
import io.github.maste.customclans.api.model.ClanMemberSnapshot;
import io.github.maste.customclans.api.model.ClanSnapshot;
import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanInvite;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import io.github.maste.customclans.repositories.ClanInviteRepository;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.repositories.ClanRepository;
import io.github.maste.customclans.services.api.ApiSnapshotMapper;
import io.github.maste.customclans.util.ActionResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InviteService {

    private final PluginConfig pluginConfig;
    private final ClanRepository clanRepository;
    private final ClanMemberRepository clanMemberRepository;
    private final ClanInviteRepository clanInviteRepository;
    private final ChatService chatService;
    private final JavaPlugin plugin;
    private final ApiSnapshotMapper snapshotMapper;

    public InviteService(
            JavaPlugin plugin,
            PluginConfig pluginConfig,
            ClanRepository clanRepository,
            ClanMemberRepository clanMemberRepository,
            ClanInviteRepository clanInviteRepository,
            ChatService chatService
    ) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.clanRepository = clanRepository;
        this.clanMemberRepository = clanMemberRepository;
        this.clanInviteRepository = clanInviteRepository;
        this.chatService = chatService;
        this.snapshotMapper = new ApiSnapshotMapper();
    }

    public CompletableFuture<ActionResult<ClanInvite>> sendInvite(Player player, Player target) {
        if (player.getUniqueId().equals(target.getUniqueId())) {
            return CompletableFuture.completedFuture(ActionResult.failure("invite.self"));
        }
        if (!target.isOnline()) {
            return CompletableFuture.completedFuture(ActionResult.failure("common.player-offline"));
        }

        return requireMember(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanMemberRepository.countByClanId(snapshot.clanId()).thenCompose(memberCount -> {
                if (memberCount >= pluginConfig.maxClanSize()) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.clan-full"));
                }

                return chatService.getOrLoadSnapshot(target.getUniqueId()).thenCompose(targetSnapshot -> {
                    if (targetSnapshot.isPresent()) {
                        return CompletableFuture.completedFuture(ActionResult.failure(
                                "invite.target-in-clan",
                                Map.of("player", target.getName())
                        ));
                    }

                    Instant now = Instant.now();
                    ClanInvite invite = new ClanInvite(
                            snapshot.clanId(),
                            target.getUniqueId(),
                            player.getUniqueId(),
                            now.plusSeconds(pluginConfig.inviteExpirationSeconds())
                    );

                    return clanInviteRepository.createInvite(invite, now).thenApply(createResult -> switch (createResult.status()) {
                        case CREATED -> ActionResult.success(
                                "invite.sent",
                                Map.of("player", target.getName(), "clan", snapshot.clanName()),
                                invite
                        );
                        case DUPLICATE_FROM_SAME_CLAN -> ActionResult.failure(
                                "invite.duplicate",
                                Map.of("player", target.getName())
                        );
                    });
                });
            });
        });
    }

    public CompletableFuture<ActionResult<Clan>> acceptInvite(Player player, String clanName) {
        String trimmedName = clanName.trim();
        return clanRepository.findByName(trimmedName).thenCompose(optionalClan -> {
            if (optionalClan.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure("accept.no-invite"));
            }

            Clan clan = optionalClan.get();
            return loadRequiredClanSnapshot(clan.id()).thenCompose(before -> clanInviteRepository.acceptInvite(
                    clan.id(),
                    player.getUniqueId(),
                    player.getName(),
                    pluginConfig.maxClanSize(),
                    Instant.now()
            ).thenCompose(result -> switch (result.status()) {
                case ACCEPTED -> chatService.refreshSnapshot(player.getUniqueId())
                        .thenCompose(unused -> loadRequiredClanSnapshot(clan.id()))
                        .thenCompose(after -> publishEvent(new ClanMemberJoinedEvent(
                                before,
                                after,
                                toMemberSnapshot(after, player.getUniqueId())
                        )).thenApply(eventUnused -> ActionResult.success(
                                "accept.success",
                                Map.of("clan", result.clan().name()),
                                result.clan()
                        )));
                case NO_INVITE -> CompletableFuture.completedFuture(ActionResult.failure("accept.no-invite"));
                case EXPIRED -> CompletableFuture.completedFuture(ActionResult.failure(
                        "accept.expired",
                        Map.of("clan", clan.name())
                ));
                case CLAN_MISSING -> CompletableFuture.completedFuture(ActionResult.failure("accept.clan-missing"));
                case ALREADY_IN_CLAN -> chatService.refreshSnapshot(player.getUniqueId())
                        .thenApply(unused -> ActionResult.<Clan>failure("accept.already-in-clan"));
                case CLAN_FULL -> CompletableFuture.completedFuture(ActionResult.failure("accept.clan-full"));
            }));
        });
    }

    public CompletableFuture<ActionResult<Void>> denyInvite(Player player, String clanName) {
        String trimmedName = clanName.trim();
        return clanRepository.findByName(trimmedName).thenCompose(optionalClan -> {
            if (optionalClan.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure("deny.no-invite"));
            }

            Clan clan = optionalClan.get();
            return clanInviteRepository.findByClanIdAndInvitedPlayerUuid(clan.id(), player.getUniqueId()).thenCompose(optionalInvite -> {
                if (optionalInvite.isEmpty()) {
                    return CompletableFuture.completedFuture(ActionResult.failure("deny.no-invite"));
                }

                ClanInvite invite = optionalInvite.get();
                if (!invite.expiresAt().isAfter(Instant.now())) {
                    return clanInviteRepository.deleteByClanIdAndInvitedPlayerUuid(clan.id(), player.getUniqueId())
                            .thenApply(unused -> ActionResult.<Void>failure(
                                    "deny.expired",
                                    Map.of("clan", clan.name())
                            ));
                }

                return clanInviteRepository.deleteByClanIdAndInvitedPlayerUuid(clan.id(), player.getUniqueId())
                        .thenApply(unused -> ActionResult.success(
                                "deny.success",
                                Map.of("clan", clan.name()),
                                null
                        ));
            });
        });
    }

    public CompletableFuture<Integer> cleanupExpiredInvites() {
        return clanInviteRepository.deleteExpiredInvites(Instant.now());
    }

    private CompletableFuture<ActionResult<PlayerClanSnapshot>> requireMember(UUID playerUuid) {
        return chatService.getOrLoadSnapshot(playerUuid).thenApply(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return ActionResult.<PlayerClanSnapshot>failure("common.no-clan");
            }
            return ActionResult.success("", optionalSnapshot.get());
        });
    }

    private CompletableFuture<ClanSnapshot> loadRequiredClanSnapshot(long clanId) {
        return clanRepository.findById(clanId).thenCompose(optionalClan -> {
            if (optionalClan.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Clan not found for id " + clanId));
            }
            return clanMemberRepository.findByClanId(clanId)
                    .thenApply(members -> snapshotMapper.mapClanSnapshot(optionalClan.get(), members));
        });
    }

    private ClanMemberSnapshot toMemberSnapshot(ClanSnapshot snapshot, UUID playerUuid) {
        return snapshot.members().stream()
                .filter(member -> member.playerUuid().equals(playerUuid))
                .findFirst()
                .orElseGet(() -> new ClanMemberSnapshot(playerUuid, "Unknown", io.github.maste.customclans.models.ClanRole.MEMBER, Instant.EPOCH));
    }

    private CompletableFuture<Void> publishEvent(Event event) {
        if (!plugin.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (Bukkit.isPrimaryThread()) {
            plugin.getServer().getPluginManager().callEvent(event);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.getServer().getPluginManager().callEvent(event);
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }
}
