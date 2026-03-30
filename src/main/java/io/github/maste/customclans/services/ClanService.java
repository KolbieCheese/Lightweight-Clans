package io.github.maste.customclans.services;

import io.github.maste.customclans.config.PluginConfig;
import io.github.maste.customclans.models.Clan;
import io.github.maste.customclans.models.ClanInfo;
import io.github.maste.customclans.models.ClanMember;
import io.github.maste.customclans.models.ClanRole;
import io.github.maste.customclans.models.PlayerClanSnapshot;
import io.github.maste.customclans.repositories.ClanMemberRepository;
import io.github.maste.customclans.repositories.ClanRepository;
import io.github.maste.customclans.util.ActionResult;
import io.github.maste.customclans.util.ValidationUtil;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

public final class ClanService {

    private final PluginConfig pluginConfig;
    private final ClanRepository clanRepository;
    private final ClanMemberRepository clanMemberRepository;
    private final ChatService chatService;
    private final NameModerationPolicy nameModerationPolicy;

    public ClanService(
            PluginConfig pluginConfig,
            ClanRepository clanRepository,
            ClanMemberRepository clanMemberRepository,
            ChatService chatService
    ) {
        this.pluginConfig = pluginConfig;
        this.clanRepository = clanRepository;
        this.clanMemberRepository = clanMemberRepository;
        this.chatService = chatService;
        this.nameModerationPolicy = new NameModerationPolicy(pluginConfig.nameModerationConfig());
    }

    public CompletableFuture<Void> touchPlayerName(Player player) {
        return clanMemberRepository.updateLastKnownName(player.getUniqueId(), player.getName());
    }

    public CompletableFuture<ActionResult<Clan>> createClan(Player player, String clanName) {
        String trimmedName = clanName.trim();
        if (trimmedName.length() > pluginConfig.maxClanNameLength()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "validation.name-too-long",
                    Map.of("max", String.valueOf(pluginConfig.maxClanNameLength()))
            ));
        }
        if (!ValidationUtil.isValidClanName(trimmedName, pluginConfig.maxClanNameLength())) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.invalid-name"));
        }
        if (nameModerationPolicy.isRestrictedFor(player, trimmedName)) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.restricted-name"));
        }

        String derivedTag = ValidationUtil.deriveDefaultTag(trimmedName, pluginConfig.maxClanTagLength());
        return clanRepository.createClan(
                player.getUniqueId(),
                player.getName(),
                trimmedName,
                derivedTag,
                pluginConfig.defaultClanTagColorId(),
                Instant.now()
        ).thenCompose(result -> switch (result.status()) {
            case CREATED -> chatService.refreshSnapshot(player.getUniqueId()).thenApply(unused -> ActionResult.success(
                    "create.success",
                    Map.of("name", trimmedName, "tag", derivedTag),
                    result.clan()
            ));
            case ALREADY_IN_CLAN -> CompletableFuture.completedFuture(ActionResult.failure("common.already-in-clan"));
            case NAME_TAKEN -> CompletableFuture.completedFuture(ActionResult.failure("create.name-taken"));
        });
    }

    public CompletableFuture<ActionResult<ClanInfo>> getClanInfo(String clanName) {
        String trimmedName = clanName.trim();
        return clanRepository.findByName(trimmedName).thenCompose(optionalClan -> {
            if (optionalClan.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        "lookup.not-found",
                        Map.of("name", trimmedName)
                ));
            }

            Clan clan = optionalClan.get();
            return clanMemberRepository.findByClanId(clan.id()).thenApply(members -> {
                String presidentName = members.stream()
                        .filter(member -> member.role() == ClanRole.PRESIDENT)
                        .map(ClanMember::lastKnownName)
                        .findFirst()
                        .orElse("Unknown");

                return ActionResult.success("", new ClanInfo(clan, presidentName, members));
            });
        });
    }

    public CompletableFuture<ActionResult<Void>> leaveClan(Player player) {
        return getOrLoadMemberSnapshot(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            if (snapshot.role() == ClanRole.PRESIDENT) {
                return CompletableFuture.completedFuture(ActionResult.failure("leave.president-cannot-leave"));
            }

            return clanMemberRepository.removeMember(snapshot.clanId(), player.getUniqueId()).thenApply(removed -> {
                chatService.clearPlayerState(player.getUniqueId());
                return ActionResult.success(
                        "leave.success",
                        Map.of("clan", snapshot.clanName()),
                        null
                );
            });
        });
    }

    public CompletableFuture<ActionResult<Void>> renameClan(Player player, String newName) {
        String trimmedName = newName.trim();
        if (trimmedName.length() > pluginConfig.maxClanNameLength()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "validation.name-too-long",
                    Map.of("max", String.valueOf(pluginConfig.maxClanNameLength()))
            ));
        }
        if (!ValidationUtil.isValidClanName(trimmedName, pluginConfig.maxClanNameLength())) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.invalid-name"));
        }
        if (nameModerationPolicy.isRestrictedFor(player, trimmedName)) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.restricted-name"));
        }

        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanRepository.renameClan(snapshot.clanId(), trimmedName).thenCompose(renamed -> {
                if (!renamed) {
                    return CompletableFuture.completedFuture(ActionResult.failure("rename.name-taken"));
                }

                return refreshClanSnapshots(snapshot.clanId()).thenApply(unused -> ActionResult.success(
                        "rename.success",
                        Map.of("name", trimmedName),
                        null
                ));
            });
        });
    }

    public CompletableFuture<ActionResult<Void>> updateTag(Player player, String newTag) {
        String trimmedTag = newTag.trim();
        if (trimmedTag.length() > pluginConfig.maxClanTagLength()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "validation.tag-too-long",
                    Map.of("max", String.valueOf(pluginConfig.maxClanTagLength()))
            ));
        }
        if (!ValidationUtil.isValidClanTag(trimmedTag, pluginConfig.maxClanTagLength())) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.invalid-tag"));
        }
        if (nameModerationPolicy.isRestrictedFor(player, trimmedTag)) {
            return CompletableFuture.completedFuture(ActionResult.failure("validation.restricted-tag"));
        }

        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanRepository.updateClanTag(snapshot.clanId(), trimmedTag).thenCompose(unused ->
                    refreshClanSnapshots(snapshot.clanId()).thenApply(refreshUnused -> ActionResult.success(
                            "tag.success",
                            Map.of("tag", trimmedTag),
                            null
                    )));
        });
    }

    public CompletableFuture<ActionResult<Void>> updateColor(Player player, String colorName) {
        String normalizedColor = pluginConfig.normalizeClanColor(colorName);
        if (normalizedColor.isBlank()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "validation.invalid-color",
                    Map.of("color", colorName)
            ));
        }

        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanRepository.updateClanColor(snapshot.clanId(), normalizedColor).thenCompose(unused ->
                    refreshClanSnapshots(snapshot.clanId()).thenApply(refreshUnused -> ActionResult.success(
                            "color.success",
                            Map.of("color", pluginConfig.formatColorDisplayName(normalizedColor)),
                            null
                    )));
        });
    }

    public CompletableFuture<ActionResult<ClanMember>> kickMember(Player player, String targetName) {
        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanMemberRepository.findByClanId(snapshot.clanId()).thenCompose(members -> {
                Optional<ClanMember> targetMember = findTargetMember(members, targetName);
                if (targetMember.isEmpty()) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.not-in-your-clan"));
                }

                ClanMember clanMember = targetMember.get();
                if (clanMember.playerUuid().equals(player.getUniqueId())) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.self-target"));
                }
                if (clanMember.role() == ClanRole.PRESIDENT) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.target-is-president"));
                }

                return clanMemberRepository.removeMember(snapshot.clanId(), clanMember.playerUuid()).thenApply(removed -> {
                    chatService.clearPlayerState(clanMember.playerUuid());
                    return ActionResult.success(
                            "kick.success-self",
                            Map.of("player", clanMember.lastKnownName(), "clan", snapshot.clanName()),
                            clanMember
                    );
                });
            });
        });
    }

    public CompletableFuture<ActionResult<ClanMember>> transferLeadership(Player player, String targetName) {
        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanMemberRepository.findByClanId(snapshot.clanId()).thenCompose(members -> {
                Optional<ClanMember> targetMember = findTargetMember(members, targetName);
                if (targetMember.isEmpty()) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.not-in-your-clan"));
                }

                ClanMember clanMember = targetMember.get();
                if (clanMember.playerUuid().equals(player.getUniqueId())) {
                    return CompletableFuture.completedFuture(ActionResult.failure("common.self-target"));
                }

                return clanRepository.transferLeadership(
                        snapshot.clanId(),
                        player.getUniqueId(),
                        clanMember.playerUuid()
                ).thenCompose(transferred -> {
                    if (!transferred) {
                        return CompletableFuture.completedFuture(ActionResult.failure("errors.internal"));
                    }

                    return refreshClanSnapshots(snapshot.clanId()).thenApply(unused -> ActionResult.success(
                            "transfer.success-self",
                            Map.of("player", clanMember.lastKnownName(), "clan", snapshot.clanName()),
                            clanMember
                    ));
                });
            });
        });
    }

    public CompletableFuture<ActionResult<List<ClanMember>>> disbandClan(Player player) {
        return requirePresident(player.getUniqueId()).thenCompose(snapshotResult -> {
            if (!snapshotResult.success()) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        snapshotResult.messageKey(),
                        snapshotResult.placeholders()
                ));
            }

            PlayerClanSnapshot snapshot = snapshotResult.value();
            return clanMemberRepository.findByClanId(snapshot.clanId()).thenCompose(members ->
                    clanRepository.disbandClan(snapshot.clanId()).thenApply(unused -> {
                        chatService.clearPlayerStates(members.stream().map(ClanMember::playerUuid).toList());
                        return ActionResult.success(
                                "disband.success-self",
                                Map.of("clan", snapshot.clanName()),
                                members
                        );
                    })
            );
        });
    }

    public Optional<PlayerClanSnapshot> cachedSnapshot(UUID playerUuid) {
        return chatService.cachedSnapshot(playerUuid);
    }

    private CompletableFuture<ActionResult<PlayerClanSnapshot>> getOrLoadMemberSnapshot(UUID playerUuid) {
        return chatService.getOrLoadSnapshot(playerUuid).thenApply(optionalSnapshot ->
                optionalSnapshot.<ActionResult<PlayerClanSnapshot>>map(snapshot -> ActionResult.success("", snapshot))
                        .orElseGet(() -> ActionResult.failure("common.no-clan"))
        );
    }

    private CompletableFuture<ActionResult<PlayerClanSnapshot>> requirePresident(UUID playerUuid) {
        return getOrLoadMemberSnapshot(playerUuid).thenApply(snapshotResult -> {
            if (!snapshotResult.success()) {
                return snapshotResult;
            }
            return snapshotResult.value().role() == ClanRole.PRESIDENT
                    ? snapshotResult
                    : ActionResult.failure("common.not-president");
        });
    }

    private CompletableFuture<Void> refreshClanSnapshots(long clanId) {
        return clanMemberRepository.findByClanId(clanId)
                .thenCompose(members -> chatService.refreshSnapshots(members.stream().map(ClanMember::playerUuid).toList()));
    }

    private Optional<ClanMember> findTargetMember(Collection<ClanMember> members, String targetName) {
        return members.stream()
                .filter(member -> member.lastKnownName().equalsIgnoreCase(targetName))
                .findFirst();
    }
}
