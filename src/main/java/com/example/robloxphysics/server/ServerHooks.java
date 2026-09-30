package com.example.robloxphysics.server;

import com.example.robloxphysics.RobloxPhysics;
import com.example.robloxphysics.dm.Player;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.net.Net;
import com.example.robloxphysics.studio.StudioService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side lifecycle: the server script context, the dev console, chat. */
@EventBusSubscriber(modid = RobloxPhysics.MODID)
public final class ServerHooks {
    private ServerHooks() {}

    private static ServerDataModel dm;
    private static LogBuffer log;
    private static final Set<UUID> logSubscribers = new HashSet<>();

    public static ServerDataModel dm() { return dm; }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        log = new LogBuffer(2000);
        log.listen(Net::broadcastServerLog);
        log.listen(l -> {
            switch (l.level()) {
                case LogBuffer.ERROR -> RobloxPhysics.LOGGER.error("[Lua] {}", l.text());
                case LogBuffer.WARN -> RobloxPhysics.LOGGER.warn("[Lua] {}", l.text());
                default -> RobloxPhysics.LOGGER.info("[Lua] {}", l.text());
            }
        });
        dm = new ServerDataModel(e.getServer(), log);
        dm.log.add(LogBuffer.INFO, "Server script context started. Scripts folder: " + dm.scriptFiles.dir());
        dm.reloadScripts();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        if (dm != null) dm.shutdown();
        dm = null;
        logSubscribers.clear();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (dm != null) dm.tick();
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent e) {
        if (dm != null && e.getEntity() instanceof ServerPlayer sp) dm.onJoin(sp);
    }

    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent e) {
        logSubscribers.remove(e.getEntity().getUUID());
        Net.forget(e.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ chat

    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        String msg = e.getRawText();
        if (dm != null) {
            Player p = dm.players.byUuid(sp.getUUID());
            if (p != null) p.chatted(msg);
        }
        if (msg.toLowerCase(Locale.ROOT).contains("wifies")) {
            // Easter egg: whoever says it explodes. Run after the chat message is broadcast.
            sp.server.execute(() -> explode(sp));
        }
    }

    /** Roblox-style Explosion on a character: blast effect + kill, without damaging blocks. */
    public static void explode(ServerPlayer sp) {
        if (!sp.isAlive()) return;
        ServerLevel level = sp.serverLevel();
        level.explode(null, sp.getX(), sp.getY() + sp.getBbHeight() * 0.5, sp.getZ(), 4.0F, Level.ExplosionInteraction.NONE);
        if (sp.isAlive()) sp.hurt(level.damageSources().explosion(null, null), Float.MAX_VALUE);
        if (sp.isAlive()) sp.kill();
    }

    // ------------------------------------------------------------------ developer console messages

    /** Server-side script execution and logs are for operators (or the singleplayer host), like game owners in Roblox. */
    public static boolean isDeveloper(ServerPlayer sp) {
        return sp.hasPermissions(2) || sp.server.isSingleplayerOwner(sp.getGameProfile());
    }

    public static void forEachLogSubscriber(Consumer<ServerPlayer> c) {
        if (dm == null || logSubscribers.isEmpty()) return;
        for (UUID id : List.copyOf(logSubscribers)) {
            ServerPlayer sp = dm.server.getPlayerList().getPlayer(id);
            if (sp != null && isDeveloper(sp)) c.accept(sp);
        }
    }

    public static void onMessage(ServerPlayer sp, JsonObject o) {
        if (dm == null) return;
        String t = o.get("t").getAsString();
        switch (t) {
            case "hello" -> {
                JsonObject r = new JsonObject();
                r.addProperty("t", "hello");
                r.addProperty("dev", isDeveloper(sp));
                Net.send(sp, r);
            }
            case "subscribeLog" -> {
                if (!isDeveloper(sp)) {
                    deny(sp);
                    return;
                }
                boolean on = o.get("on").getAsBoolean();
                if (on && logSubscribers.add(sp.getUUID())) {
                    JsonObject r = new JsonObject();
                    r.addProperty("t", "logHistory");
                    JsonArray lines = new JsonArray();
                    for (LogBuffer.Line l : dm.log.snapshot()) lines.add(Net.logJson(l));
                    r.add("lines", lines);
                    Net.send(sp, r);
                } else if (!on) {
                    logSubscribers.remove(sp.getUUID());
                }
            }
            case "exec" -> {
                if (!isDeveloper(sp)) {
                    deny(sp);
                    return;
                }
                String code = o.get("code").getAsString();
                if (code.trim().equalsIgnoreCase("reload")) {
                    dm.log.add(LogBuffer.INFO, "> reload");
                    dm.reloadScripts();
                    return;
                }
                if (code.trim().equalsIgnoreCase("reset")) {
                    dm.log.add(LogBuffer.INFO, "> reset");
                    restart();
                    return;
                }
                dm.log.add(LogBuffer.INFO, "> " + (code.length() > 300 ? code.substring(0, 300) + "..." : code));
                dm.rt.execute(code, "Server");
            }
            case "studio" -> {
                if (!isDeveloper(sp)) {
                    deny(sp);
                    return;
                }
                JsonObject r = new JsonObject();
                r.addProperty("t", "studio");
                r.add("rid", o.get("rid"));
                try {
                    r.add("r", StudioService.handle(dm, o.get("m").getAsString(),
                            o.has("a") ? o.getAsJsonObject("a") : new JsonObject()));
                } catch (RuntimeException ex) {
                    r.addProperty("err", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                }
                Net.send(sp, r);
            }
            default -> {
            }
        }
    }

    private static void deny(ServerPlayer sp) {
        JsonObject r = new JsonObject();
        r.addProperty("t", "denied");
        Net.send(sp, r);
    }

    /** Recreate the server script context (e.g. after a runaway script). */
    private static void restart() {
        var server = dm.server;
        dm.shutdown();
        dm = new ServerDataModel(server, log);
        dm.log.add(LogBuffer.INFO, "Server script context restarted.");
        dm.reloadScripts();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) dm.onJoin(sp);
    }
}
