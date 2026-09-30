package com.example.robloxphysics.client;

import com.example.robloxphysics.RobloxPhysics;
import com.example.robloxphysics.dm.HumanoidSettings;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.net.Net;
import com.example.robloxphysics.rbx.CFrame;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client script context lifecycle, server messages, and input forwarding to UserInputService. */
@EventBusSubscriber(modid = RobloxPhysics.MODID, value = Dist.CLIENT)
public final class ClientScripting {
    private ClientScripting() {}

    public static final LogBuffer CLIENT_LOG = new LogBuffer(2000);
    /** mirror of the server's output (developers only) */
    public static final LogBuffer SERVER_LOG = new LogBuffer(2000);

    private static ClientDataModel dm;
    /** null = unknown / server doesn't have the mod */
    public static Boolean serverDeveloper;
    private static long lastFrame;

    static {
        Net.clientHandler = ClientScripting::onMessage;
    }

    public static ClientDataModel dm() { return dm; }

    public static boolean scriptableCamera() { return scriptedCameraCFrame() != null; }

    public static CFrame scriptedCameraCFrame() {
        return dm == null ? null : dm.camera.scriptedCFrame();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        Net.clientHandler = ClientScripting::onMessage;
        HumanoidSettings.LOCAL.resetToConfig();
        serverDeveloper = null;
        CLIENT_LOG.clear();
        SERVER_LOG.clear();
        dm = new ClientDataModel(CLIENT_LOG);
        CLIENT_LOG.add(LogBuffer.INFO, "Client script context started. LocalScripts folder: " + dm.scriptFiles.dir());
        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        send(hello);
    }

    /** true if the server has this mod (server console available) */
    public static boolean serverHasMod() {
        var c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(Net.TYPE_C2S);
    }

    public static boolean send(JsonObject o) {
        if (!serverHasMod()) return false;
        Net.sendToServer(o);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (dm != null) dm.shutdown();
        dm = null;
        HumanoidSettings.LOCAL.resetToConfig();
    }

    /** Recreate the client script context (e.g. after a runaway script). */
    public static void restart() {
        if (dm != null) dm.shutdown();
        dm = new ClientDataModel(CLIENT_LOG);
        CLIENT_LOG.add(LogBuffer.INFO, "Client script context restarted.");
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (dm != null && Minecraft.getInstance().level != null) dm.tick();
        if (ClientKeys.DEV_CONSOLE.consumeClick() && Minecraft.getInstance().screen == null) {
            Minecraft.getInstance().setScreen(new DevConsoleScreen());
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre e) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
        lastFrame = now;
        if (dm != null && Minecraft.getInstance().level != null) dm.frame(dt);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (dm != null && dm.camera.fov > 0 && e.usedConfiguredFov()) e.setFOV(dm.camera.fov);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (dm == null) return;
        dm.uis.key(e.getKey(), e.getAction(), Minecraft.getInstance().screen != null);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Post e) {
        if (dm == null) return;
        dm.uis.mouseButton(e.getButton(), e.getAction(), Minecraft.getInstance().screen != null);
    }

    // ------------------------------------------------------------------ server messages

    private static void onMessage(JsonObject o) {
        String t = o.get("t").getAsString();
        switch (t) {
            case "hello" -> serverDeveloper = o.get("dev").getAsBoolean();
            case "humanoid" -> Net.applyHumanoid(o, HumanoidSettings.LOCAL);
            case "log" -> SERVER_LOG.add(new LogBuffer.Line(o.get("time").getAsLong(), o.get("l").getAsInt(), o.get("s").getAsString()));
            case "logHistory" -> {
                SERVER_LOG.clear();
                for (JsonElement el : o.getAsJsonArray("lines")) {
                    JsonObject l = el.getAsJsonObject();
                    SERVER_LOG.add(new LogBuffer.Line(l.get("time").getAsLong(), l.get("l").getAsInt(), l.get("s").getAsString()));
                }
            }
            case "denied" -> {
                serverDeveloper = false;
                SERVER_LOG.add(LogBuffer.ERROR, "You need to be an operator to use the server console.");
            }
            default -> {
            }
        }
    }
}
