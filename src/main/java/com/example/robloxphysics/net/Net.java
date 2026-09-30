package com.example.robloxphysics.net;

import com.example.robloxphysics.RobloxPhysics;
import com.example.robloxphysics.dm.HumanoidSettings;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.server.ServerHooks;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JSON messages in both directions, split into chunks (client->server packets are limited to ~32KB).
 * Message field "t" selects the handler.
 */
public final class Net {
    private Net() {}

    public static final Gson GSON = new Gson();
    private static final int C2S_CHUNK = 8000;     // chars; UTF-8 may triple this in bytes
    private static final int S2C_CHUNK = 200_000;

    public record Chunk(int msg, int part, int total, String data) implements CustomPacketPayload {
        static StreamCodec<ByteBuf, Chunk> codec(int max) {
            return StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Chunk::msg,
                    ByteBufCodecs.VAR_INT, Chunk::part,
                    ByteBufCodecs.VAR_INT, Chunk::total,
                    ByteBufCodecs.stringUtf8(max), Chunk::data,
                    Chunk::new);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE_C2S; }
    }

    public record ChunkS2C(int msg, int part, int total, String data) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE_S2C; }
    }

    public static final CustomPacketPayload.Type<Chunk> TYPE_C2S =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RobloxPhysics.MODID, "c2s"));
    public static final CustomPacketPayload.Type<ChunkS2C> TYPE_S2C =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RobloxPhysics.MODID, "s2c"));

    private static final StreamCodec<ByteBuf, ChunkS2C> S2C_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChunkS2C::msg,
            ByteBufCodecs.VAR_INT, ChunkS2C::part,
            ByteBufCodecs.VAR_INT, ChunkS2C::total,
            ByteBufCodecs.stringUtf8(S2C_CHUNK), ChunkS2C::data,
            ChunkS2C::new);

    /** Set by the client at startup; never touched on a dedicated server. */
    public static Consumer<JsonObject> clientHandler = o -> {};

    private static int nextMsg = 1;
    private static final Map<UUID, Map<Integer, StringBuilder[]>> serverPartial = new HashMap<>();
    private static final Map<Integer, StringBuilder[]> clientPartial = new HashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional: the client features still work on servers without the mod
        PayloadRegistrar r = event.registrar("2").optional();
        r.playToServer(TYPE_C2S, Chunk.codec(C2S_CHUNK * 3), Net::onServer);
        r.playToClient(TYPE_S2C, S2C_CODEC, (p, ctx) -> onClient(p));
    }

    private static String assemble(Map<Integer, StringBuilder[]> partial, int msg, int part, int total, String data) {
        if (total <= 1) return data;
        StringBuilder[] parts = partial.computeIfAbsent(msg, k -> new StringBuilder[total]);
        if (part < 0 || part >= parts.length) return null;
        parts[part] = new StringBuilder(data);
        for (StringBuilder b : parts) if (b == null) return null;
        partial.remove(msg);
        StringBuilder all = new StringBuilder();
        for (StringBuilder b : parts) all.append(b);
        return all.toString();
    }

    private static void onServer(Chunk c, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        Map<Integer, StringBuilder[]> partial = serverPartial.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        if (partial.size() > 64) partial.clear(); // don't let a client pile up garbage
        String json = assemble(partial, c.msg(), c.part(), c.total(), c.data());
        if (json == null) return;
        try {
            ServerHooks.onMessage(sp, GSON.fromJson(json, JsonObject.class));
        } catch (Exception e) {
            RobloxPhysics.LOGGER.warn("Bad message from {}: {}", sp.getGameProfile().getName(), e.toString());
        }
    }

    private static void onClient(ChunkS2C c) {
        String json = assemble(clientPartial, c.msg(), c.part(), c.total(), c.data());
        if (json == null) return;
        clientHandler.accept(GSON.fromJson(json, JsonObject.class));
    }

    public static void forget(UUID player) { serverPartial.remove(player); }

    // ------------------------------------------------------------------ sending

    public static void send(ServerPlayer sp, JsonObject o) {
        if (!sp.connection.hasChannel(TYPE_S2C)) return; // client without the mod
        String s = GSON.toJson(o);
        int id = nextMsg++;
        int total = Math.max(1, (s.length() + S2C_CHUNK - 1) / S2C_CHUNK);
        for (int i = 0; i < total; i++) {
            String part = s.substring(i * S2C_CHUNK, Math.min(s.length(), (i + 1) * S2C_CHUNK));
            PacketDistributor.sendToPlayer(sp, new ChunkS2C(id, i, total, part));
        }
    }

    /** client -> server (callers check {@code hasChannel(TYPE_C2S)} first) */
    public static void sendToServer(JsonObject o) {
        String s = GSON.toJson(o);
        int id = nextMsg++;
        int total = Math.max(1, (s.length() + C2S_CHUNK - 1) / C2S_CHUNK);
        for (int i = 0; i < total; i++) {
            String part = s.substring(i * C2S_CHUNK, Math.min(s.length(), (i + 1) * C2S_CHUNK));
            PacketDistributor.sendToServer(new Chunk(id, i, total, part));
        }
    }

    public static JsonObject humanoidJson(HumanoidSettings h) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "humanoid");
        o.addProperty("walk", h.walkSpeed);
        o.addProperty("jumpH", h.jumpHeight);
        o.addProperty("jumpP", h.jumpPower);
        o.addProperty("useJP", h.useJumpPower);
        o.addProperty("grav", h.gravity);
        o.addProperty("rot", h.autoRotate);
        o.addProperty("maxZoom", h.maxZoom);
        o.addProperty("minZoom", h.minZoom);
        o.addProperty("lock", h.mouseLockOption);
        o.addProperty("jump", h.jumpRequest);
        return o;
    }

    public static void applyHumanoid(JsonObject o, HumanoidSettings h) {
        h.walkSpeed = o.get("walk").getAsDouble();
        h.jumpHeight = o.get("jumpH").getAsDouble();
        h.jumpPower = o.get("jumpP").getAsDouble();
        h.useJumpPower = o.get("useJP").getAsBoolean();
        h.gravity = o.get("grav").getAsDouble();
        h.autoRotate = o.get("rot").getAsBoolean();
        h.maxZoom = o.get("maxZoom").getAsDouble();
        h.minZoom = o.get("minZoom").getAsDouble();
        h.mouseLockOption = o.get("lock").getAsBoolean();
        if (o.get("jump").getAsBoolean()) h.jumpRequest = true;
    }

    /** Server log lines go to developers who opened the console/Studio. */
    public static void broadcastServerLog(LogBuffer.Line l) {
        ServerHooks.forEachLogSubscriber(sp -> send(sp, logJson(l)));
    }

    public static JsonObject logJson(LogBuffer.Line l) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "log");
        o.addProperty("l", l.level());
        o.addProperty("time", l.time());
        o.addProperty("s", l.text());
        return o;
    }
}
