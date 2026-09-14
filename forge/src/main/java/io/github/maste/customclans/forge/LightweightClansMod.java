package io.github.maste.customclans.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.maste.customclans.models.*;
import io.github.maste.customclans.repositories.sqlite.*;
import io.github.maste.customclans.util.ValidationUtil;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/** Server-side Forge adapter. All gameplay access stays on the server thread. */
@Mod("lightweightclans")
public final class LightweightClansMod {
    private MinecraftServer server;
    private SQLiteDatabase database;
    private SQLiteClanRepository clans;
    private SQLiteClanMemberRepository members;
    private SQLiteClanInviteRepository invites;
    private ExecutorService commands;
    private final Map<UUID, PlayerClanSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public LightweightClansMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClansConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void starting(ServerStartingEvent event) {
        server = event.getServer();
        database = new SQLiteDatabase(server.getWorldPath(LevelResource.ROOT)
                .resolve("lightweightclans/clans.db"), Logger.getLogger("LightweightClans"));
        // Forge's module classloader does not discover JDBC services from the game thread automatically.
        try { Class.forName("org.sqlite.JDBC"); database.initialize(); }
        catch (Exception e) { database.close(); throw new IllegalStateException("Cannot initialize clan database", e); }
        clans = new SQLiteClanRepository(database);
        members = new SQLiteClanMemberRepository(database);
        invites = new SQLiteClanInviteRepository(database);
        commands = Executors.newSingleThreadExecutor(r -> new Thread(r, "LightweightClans-commands"));
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) {
        if (commands != null) {
            commands.shutdown();
            try {
                if (!commands.awaitTermination(30, TimeUnit.SECONDS)) commands.shutdownNow();
            } catch (InterruptedException e) { commands.shutdownNow(); Thread.currentThread().interrupt(); }
        }
        if (database != null) database.close();
        snapshots.clear();
        toggled.clear();
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("clan").executes(c -> submit(c.getSource(), "help", ""));
        for (String action : List.of("help", "list", "info", "members", "create", "invite", "accept", "deny", "leave",
                "chat", "rename", "description", "tag", "color", "transfer", "kick", "disband", "banner", "setbanner")) {
            root.then(Commands.literal(action).executes(c -> submit(c.getSource(), action, ""))
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(c -> submit(c.getSource(), action, StringArgumentType.getString(c, "value")))));
        }
        var admin = Commands.literal("admin").requires(s -> s.hasPermission(2));
        for (String action : List.of("rename", "tag", "color", "setbanner", "disband")) {
            admin.then(Commands.literal(action).then(Commands.argument("clan", StringArgumentType.string())
                    .executes(c -> submit(c.getSource(), "admin:" + action + ":" + StringArgumentType.getString(c, "clan"), ""))
                    .then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> submit(c.getSource(),
                            "admin:" + action + ":" + StringArgumentType.getString(c, "clan"), StringArgumentType.getString(c, "value"))))));
        }
        root.then(admin);
        event.getDispatcher().register(root);
    }

    private record Request(CommandSourceStack source, UUID id, String name, boolean admin,
                           Map<String, UUID> online, BannerCodec.Design banner) {}

    private int submit(CommandSourceStack source, String action, String value) {
        if (commands == null || commands.isShutdown()) {
            source.sendFailure(Component.literal("Clans are not available while the server is starting or stopping."));
            return 0;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        Map<String, UUID> online = new HashMap<>();
        server.getPlayerList().getPlayers().forEach(p -> online.put(p.getGameProfile().getName().toLowerCase(Locale.ROOT), p.getUUID()));
        var request = new Request(source, player == null ? null : player.getUUID(), source.getTextName(), source.hasPermission(2),
                Map.copyOf(online), player == null ? null : BannerCodec.capture(player.getMainHandItem(), server));
        commands.execute(() -> {
            try { execute(request, action, value.trim()); refreshSnapshots(request.online().values()); }
            catch (IllegalArgumentException e) { reply(request, e.getMessage()); }
            catch (Exception e) {
                Logger.getLogger("LightweightClans").log(java.util.logging.Level.SEVERE, "Clan command failed", e);
                reply(request, "Clan operation failed. Check the server log.");
            }
        });
        return 1;
    }

    private void execute(Request r, String action, String value) {
        boolean adminAction = action.startsWith("admin:");
        Clan selected = null;
        if (adminAction) {
            require(r.admin(), "Operator permission is required.");
            String[] parts = action.split(":", 3);
            selected = findClan(parts[2]);
            action = parts[1];
        }
        switch (action) {
            case "help" -> {
                reply(r, "/clan create|invite|accept|deny|info|members|list|leave|chat|rename|description|tag|color|setbanner|banner|transfer|kick|disband");
                reply(r, "Use a clan name or slug for lookups and invitations. /clan chat toggle switches private chat on/off.");
                if (r.admin()) reply(r, "/clan admin rename|tag|color|setbanner|disband <clan slug or quoted name> [value]");
            }
            case "list" -> {
                var all = clans.listActiveClans().join();
                reply(r, all.isEmpty() ? "No clans yet." : "Clans:");
                all.forEach(c -> reply(r, c.name() + " [" + c.tag() + "] - " + c.memberCount() + " members"));
            }
            case "info", "members" -> {
                Clan c = value.isEmpty() ? ownClan(r) : findClan(value);
                if (action.equals("info")) reply(r, c.name() + " [" + c.tag() + "] (" + c.slug() + ") " + c.description());
                members.findByClanId(c.id()).join().forEach(m -> reply(r, m.lastKnownName() + " - " + m.role()));
            }
            case "create" -> {
                playerRequired(r); validateName(value, r);
                var result = clans.createClan(r.id(), r.name(), value,
                        ValidationUtil.deriveDefaultTag(value, ClansConfig.MAX_TAG.get()), "white", Instant.now()).join();
                reply(r, switch (result.status()) {
                    case CREATED -> "Created clan " + value + ". You are the President.";
                    case ALREADY_IN_CLAN -> "You already belong to a clan.";
                    case NAME_TAKEN -> "That clan name or slug is already taken.";
                });
            }
            case "invite" -> {
                Clan c = ownClan(r);
                UUID target = r.online().get(value.toLowerCase(Locale.ROOT));
                require(target != null, "That player must be online.");
                require(members.findByPlayerUuid(target).join().isEmpty(), "That player already belongs to a clan.");
                require(members.countByClanId(c.id()).join() < ClansConfig.MAX_SIZE.get(), "Your clan is full.");
                var result = invites.createInvite(new ClanInvite(c.id(), target, r.id(), Instant.now().plusSeconds(ClansConfig.INVITE_SECONDS.get())), Instant.now()).join();
                require(result.status() == InviteCreateResult.Status.CREATED, "That player already has an active invite from your clan.");
                reply(r, "Invited " + value + " to " + c.name() + ".");
                send(target, Component.literal("Invited to " + c.name() + ". Use /clan accept " + c.slug() + " or /clan deny " + c.slug()));
            }
            case "accept", "deny" -> {
                playerRequired(r); Clan c = findClan(value);
                if (action.equals("deny")) {
                    reply(r, invites.deleteByClanIdAndInvitedPlayerUuid(c.id(), r.id()).join() ? "Invite denied." : "No invite from that clan.");
                } else {
                    var result = invites.acceptInvite(c.id(), r.id(), r.name(), ClansConfig.MAX_SIZE.get(), Instant.now()).join();
                    reply(r, switch (result.status()) {
                        case ACCEPTED -> "Joined " + c.name() + ".";
                        case NO_INVITE -> "No invite from that clan.";
                        case EXPIRED -> "That invite has expired.";
                        case CLAN_MISSING -> "That clan no longer exists.";
                        case ALREADY_IN_CLAN -> "You already belong to a clan.";
                        case CLAN_FULL -> "That clan is full.";
                    });
                }
            }
            case "leave" -> {
                Clan c = ownClan(r);
                require(!c.presidentUuid().equals(r.id()), "Transfer leadership or disband before leaving.");
                require(members.removeMember(c.id(), r.id()).join(), "Unable to leave that clan.");
                toggled.remove(r.id()); reply(r, "Left " + c.name() + ".");
            }
            case "chat" -> {
                Clan c = ownClan(r);
                require(ClansConfig.CHAT.get(), "Clan chat is disabled.");
                require(!value.isEmpty(), "Usage: /clan chat <message|toggle>");
                if (value.equalsIgnoreCase("toggle")) {
                    require(ClansConfig.TOGGLE.get(), "Clan chat toggle is disabled.");
                    if (!toggled.remove(r.id())) toggled.add(r.id());
                    reply(r, "Clan chat " + (toggled.contains(r.id()) ? "enabled" : "disabled") + ".");
                } else clanChat(c, r.name(), value);
            }
            case "banner" -> {
                Clan c = ownClan(r);
                require(c.bannerData() != null, "Your clan has no banner. The President can use /clan setbanner.");
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(r.id());
                    if (p != null) {
                        try {
                            var stack = BannerCodec.restore(c.bannerData(), server);
                            if (!p.getInventory().add(stack)) p.drop(stack, false);
                        } catch (IllegalArgumentException e) { p.sendSystemMessage(Component.literal(e.getMessage())); }
                    }
                });
            }
            default -> manage(r, action, value, adminAction ? selected : presidentClan(r));
        }
    }

    private void manage(Request r, String action, String value, Clan c) {
        switch (action) {
            case "rename" -> {
                validateName(value, r);
                require(clans.renameClan(c.id(), value).join(), "That clan name or slug is already taken.");
            }
            case "tag" -> {
                require(ValidationUtil.isValidClanTag(value, ClansConfig.MAX_TAG.get()), "Tag must be alphanumeric and within the configured length.");
                moderate(value, r); clans.updateClanTag(c.id(), value).join();
            }
            case "color" -> {
                String color = ValidationUtil.normalizeClanColor(value);
                require(TextColor.parseColor(color) != null, "Use a Minecraft color name or #RRGGBB.");
                clans.updateClanColor(c.id(), color).join();
            }
            case "description" -> {
                require(value.length() <= 256, "Description must be at most 256 characters.");
                clans.updateClanDescription(c.id(), value).join();
            }
            case "setbanner" -> {
                require(r.banner() != null, "Hold a banner in your main hand.");
                clans.updateClanBanner(c.id(), r.banner().material(), r.banner().patternsJson()).join();
            }
            case "transfer", "kick" -> {
                var target = members.findByClanId(c.id()).join().stream()
                        .filter(m -> m.lastKnownName().equalsIgnoreCase(value) || m.playerUuid().toString().equalsIgnoreCase(value)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("That player is not in your clan."));
                require(!target.playerUuid().equals(c.presidentUuid()), "Choose a member other than the President.");
                boolean changed = action.equals("transfer")
                        ? clans.transferLeadership(c.id(), c.presidentUuid(), target.playerUuid()).join()
                        : members.removeMember(c.id(), target.playerUuid()).join();
                require(changed, "Clan membership changed; try again.");
                if (action.equals("kick")) toggled.remove(target.playerUuid());
            }
            case "disband" -> clans.disbandClan(c.id(), Instant.now()).join();
            default -> throw new IllegalArgumentException("Unknown clan command. Use /clan help.");
        }
        reply(r, "Clan " + action + " completed.");
    }

    private Clan findClan(String name) {
        return clans.findByName(name).join().orElseThrow(() -> new IllegalArgumentException("Clan not found."));
    }
    private Clan ownClan(Request r) {
        playerRequired(r);
        var member = members.findByPlayerUuid(r.id()).join().orElseThrow(() -> new IllegalArgumentException("You do not belong to a clan."));
        return clans.findById(member.clanId()).join().orElseThrow(() -> new IllegalArgumentException("Clan not found."));
    }
    private Clan presidentClan(Request r) {
        Clan c = ownClan(r);
        require(c.presidentUuid().equals(r.id()), "Only the clan President can do that.");
        return c;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void playerRequired(Request r) { require(r.id() != null, "This command requires a player."); }
    private static void validateName(String name, Request r) {
        require(ValidationUtil.isValidClanName(name, ClansConfig.MAX_NAME.get()) && !ValidationUtil.toSlug(name).isEmpty(),
                "Use letters, digits, spaces, underscores or hyphens within the configured name length.");
        moderate(name, r);
    }
    private static void moderate(String value, Request r) {
        if (!r.admin()) require(ClansConfig.RESTRICTED.get().stream().noneMatch(s ->
                ValidationUtil.normalizeForModeration(s).equals(ValidationUtil.normalizeForModeration(value))), "That name or tag is reserved.");
    }
    private void reply(Request r, String message) { server.execute(() -> r.source().sendSuccess(() -> Component.literal(message), false)); }
    private void send(UUID id, Component message) {
        server.execute(() -> { var p = server.getPlayerList().getPlayer(id); if (p != null) p.sendSystemMessage(message); });
    }
    private void refreshSnapshots(Collection<UUID> players) {
        for (UUID id : players) {
            var snapshot = members.findSnapshotByPlayerUuid(id).join();
            if (snapshot.isPresent()) snapshots.put(id, snapshot.get());
            else { snapshots.remove(id); toggled.remove(id); }
        }
    }
    private static MutableComponent tag(String text, String color) {
        var component = Component.literal("[" + text + "] ");
        TextColor parsed = TextColor.parseColor(color);
        return parsed == null ? component : component.withStyle(s -> s.withColor(parsed));
    }
    private void clanChat(Clan c, String sender, String message) {
        var formatted = Component.literal("[Clan] ").append(tag(c.tag(), c.tagColor())).append(Component.literal(sender + ": " + message));
        members.findByClanId(c.id()).join().forEach(m -> send(m.playerUuid(), formatted));
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || commands == null) return;
        UUID id = p.getUUID(); String name = p.getGameProfile().getName();
        commands.execute(() -> { members.updateLastKnownName(id, name).join(); refreshSnapshots(List.of(id)); });
    }
    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        snapshots.remove(event.getEntity().getUUID());
        toggled.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void chat(ServerChatEvent event) {
        UUID id = event.getPlayer().getUUID();
        var snapshot = snapshots.get(id);
        if (toggled.contains(id)) {
            // Cancel before scheduling any work so a private message cannot leak into public chat.
            event.setCanceled(true);
            String name = event.getUsername(), message = event.getRawText();
            if (commands != null && !commands.isShutdown()) commands.execute(() -> {
                if (!ClansConfig.CHAT.get() || !ClansConfig.TOGGLE.get()) { toggled.remove(id); send(id, Component.literal("Clan chat is disabled; message was not sent.")); return; }
                var current = members.findByPlayerUuid(id).join();
                if (current.isEmpty()) { toggled.remove(id); send(id, Component.literal("You no longer belong to a clan; message was not sent.")); return; }
                clans.findById(current.get().clanId()).join().ifPresent(c -> clanChat(c, name, message));
            });
        } else if (snapshot != null) {
            event.setMessage(tag(snapshot.tag(), snapshot.tagColor()).append(event.getMessage()));
        }
    }
}
