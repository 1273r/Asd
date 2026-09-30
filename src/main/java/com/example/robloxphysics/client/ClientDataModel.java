package com.example.robloxphysics.client;

import com.example.robloxphysics.dm.BaseScript;
import com.example.robloxphysics.dm.ClassInfo;
import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.dm.DataModel;
import com.example.robloxphysics.dm.HumanoidSettings;
import com.example.robloxphysics.dm.Instance;
import com.example.robloxphysics.dm.Player;
import com.example.robloxphysics.dm.ScriptFiles;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.lua.RbxLib;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.RbxEnum;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Vector3f;
import org.luaj.vm2.LuaValue;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;

/** The client's `game`: LocalPlayer, CurrentCamera, UserInputService, LocalScripts. */
public class ClientDataModel extends DataModel {
    public final CameraInstance camera;
    public final UserInputService uis;
    public final ScriptFiles scriptFiles;
    private boolean scriptsLoaded;

    public ClientDataModel(LogBuffer log) {
        super(false, log);
        camera = new CameraInstance(this);
        camera.attach(workspace);
        workspace.currentCamera = camera;
        uis = new UserInputService(this);
        hiddenService(uis);
        scriptFiles = new ScriptFiles(this, FMLPaths.CONFIGDIR.get().resolve("robloxphysics").resolve("scripts").resolve("client"),
                BaseScript.LOCAL_SCRIPT);
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override
    public Collection<? extends Level> levels() { return mc().level == null ? List.of() : List.of(mc().level); }

    @Override
    public Level defaultLevel() { return mc().level; }

    @Override
    public List<? extends net.minecraft.world.entity.player.Player> mcPlayers() {
        return mc().level == null ? List.of() : mc().level.players();
    }

    @Override
    protected Iterable<Entity> entities(Level level) { return mc().level.entitiesForRendering(); }

    @Override
    public int maxPlayers() { return mc().getConnection() == null ? 0 : mc().getConnection().getOnlinePlayers().size(); }

    @Override
    public boolean isLocalPlayer(net.minecraft.world.entity.player.Player p) { return p == mc().player; }

    @Override
    public void onGravityChanged() { HumanoidSettings.LOCAL.gravity = gravity; }

    @Override
    public void onPlayerHumanoidChanged(Player p) {} // LocalPlayer's settings are HumanoidSettings.LOCAL itself

    @Override
    public void onCameraSettingsChanged() {
        HumanoidSettings.LOCAL.maxZoom = starterPlayer.maxZoom;
        HumanoidSettings.LOCAL.minZoom = starterPlayer.minZoom;
        HumanoidSettings.LOCAL.mouseLockOption = starterPlayer.mouseLock;
    }

    @Override
    public void tick() {
        if (mc().level == null) return;
        gravity = HumanoidSettings.LOCAL.gravity;
        super.tick();
        if (!scriptsLoaded && players.localPlayer != null) {
            scriptsLoaded = true;
            reloadScripts();
        }
    }

    public void reloadScripts() {
        Player lp = players.localPlayer;
        scriptFiles.reload(lp == null ? null : lp.playerScripts);
    }

    /** once per rendered frame */
    public void frame(double dt) {
        if (runService.renderStepped.hasListeners()) {
            rt.step(() -> runService.renderStepped.fireNow(LuaValue.valueOf(dt)));
        }
    }

    // ================================================================== Camera

    public static class CameraInstance extends Instance {
        public static final ClassInfo CLASS = ClassInfo.define("Camera", Instance.CLASS, null)
                .prop("CFrame", PT.CFRAME, (CameraInstance c) -> c.currentCFrame(), (CameraInstance c, Object v) -> {
                    c.scripted = (CFrame) v;
                    c.changed("CFrame");
                })
                .prop("FieldOfView", PT.NUMBER, (CameraInstance c) -> c.fov > 0 ? c.fov : (double) mc().options.fov().get(),
                        (CameraInstance c, Object v) -> {
                            c.fov = Math.max(1, Math.min(120, (Double) v));
                            c.changed("FieldOfView");
                        })
                .enumProp("CameraType", RbxEnum.CAMERA_TYPE, (CameraInstance c) -> c.type, (CameraInstance c, Object v) -> {
                    c.type = (RbxEnum.Item) v;
                    if (!c.type.name().equals("Scriptable")) c.scripted = null;
                    else if (c.scripted == null) c.scripted = c.currentCFrame();
                    c.changed("CameraType");
                })
                .prop("CameraSubject", PT.INSTANCE, (CameraInstance c) -> {
                    Player lp = c.dm.players.localPlayer;
                    var ch = lp == null ? null : lp.character();
                    return ch == null ? null : ch.humanoid;
                }, null)
                .method("ScreenPointToRay", (self, a) -> {
                    Vec3Pair r = rayAt(a.checkdouble(1), a.checkdouble(2));
                    return r == null ? LuaValue.NIL : LuaValue.varargsOf(RbxLib.toLua(r.origin), RbxLib.toLua(r.dir));
                });

        public RbxEnum.Item type = RbxEnum.CAMERA_TYPE.get("Custom");
        CFrame scripted;
        double fov = -1;

        CameraInstance(DataModel dm) {
            super(dm, CLASS);
            this.name = "Camera";
            this.parentLocked = true;
        }

        CFrame currentCFrame() {
            if (scripted != null && type.name().equals("Scriptable")) return scripted;
            var cam = mc().gameRenderer.getMainCamera();
            V3 pos = Units.toStuds(cam.getPosition());
            Vector3f l = cam.getLookVector(), u = cam.getUpVector();
            return CFrame.lookAt(pos, pos.add(new V3(l.x, l.y, l.z)), new V3(u.x, u.y, u.z));
        }

        /** Scriptable camera CFrame, or null when the normal camera should be used */
        public CFrame scriptedCFrame() {
            return type.name().equals("Scriptable") ? scripted : null;
        }

        record Vec3Pair(V3 origin, V3 dir) {}

        static Vec3Pair rayAt(double x, double y) {
            var win = mc().getWindow();
            double sx = x * win.getScreenWidth() / (double) win.getGuiScaledWidth();
            double sy = y * win.getScreenHeight() / (double) win.getGuiScaledHeight();
            var r = RobloxCamera.cursorRayAt(sx, sy, 1);
            if (r == null) return null;
            return new Vec3Pair(Units.toStuds(r[0]), Units.toStuds(r[1]).sub(Units.toStuds(r[0])).unit());
        }
    }

    // ================================================================== UserInputService

    public static class InputObject extends Instance {
        public static final ClassInfo CLASS = ClassInfo.define("InputObject", Instance.CLASS, null)
                .enumProp("KeyCode", RbxEnum.KEY_CODE, (InputObject i) -> i.keyCode, null)
                .enumProp("UserInputType", RbxEnum.USER_INPUT_TYPE, (InputObject i) -> i.inputType, null)
                .enumProp("UserInputState", RbxEnum.USER_INPUT_STATE, (InputObject i) -> i.state, null)
                .prop("Position", PT.V3, (InputObject i) -> i.position, null);

        final RbxEnum.Item keyCode, inputType, state;
        final V3 position;

        InputObject(DataModel dm, RbxEnum.Item key, RbxEnum.Item type, RbxEnum.Item state, V3 pos) {
            super(dm, CLASS);
            this.name = "InputObject";
            this.keyCode = key;
            this.inputType = type;
            this.state = state;
            this.position = pos;
        }
    }

    public static class UserInputService extends Instance {
        public static final ClassInfo CLASS = ClassInfo.define("UserInputService", Instance.CLASS, null)
                .method("IsKeyDown", (self, a) -> {
                    int k = RbxEnum.glfwFromKey(RbxLib.checkEnum(a.arg(1), RbxEnum.KEY_CODE));
                    return LuaValue.valueOf(k > 0 && InputConstants.isKeyDown(mc().getWindow().getWindow(), k));
                })
                .method("IsMouseButtonPressed", (self, a) -> {
                    int b = RbxLib.checkEnum(a.arg(1), RbxEnum.USER_INPUT_TYPE).value();
                    return LuaValue.valueOf(b <= 2 && GLFW.glfwGetMouseButton(mc().getWindow().getWindow(), b) == GLFW.GLFW_PRESS);
                })
                .method("GetMouseLocation", (self, a) -> RbxLib.toLua(mouse()))
                .prop("MouseEnabled", PT.BOOL, (UserInputService u) -> true, null)
                .prop("KeyboardEnabled", PT.BOOL, (UserInputService u) -> true, null)
                .prop("TouchEnabled", PT.BOOL, (UserInputService u) -> false, null)
                .event("InputBegan").event("InputEnded");

        UserInputService(DataModel dm) {
            super(dm, CLASS);
            this.name = "UserInputService";
        }

        static V3 mouse() {
            var mh = mc().mouseHandler;
            var win = mc().getWindow();
            return new V3(mh.xpos() * win.getGuiScaledWidth() / win.getScreenWidth(),
                    mh.ypos() * win.getGuiScaledHeight() / win.getScreenHeight(), 0);
        }

        /** GLFW key event; gameProcessed = typed into a GUI (chat, console...) */
        public void key(int glfwKey, int action, boolean gameProcessed) {
            if (action == GLFW.GLFW_REPEAT) return;
            boolean down = action == GLFW.GLFW_PRESS;
            fire(down, RbxEnum.keyFromGlfw(glfwKey), RbxEnum.USER_INPUT_TYPE.get("Keyboard"), gameProcessed);
        }

        public void mouseButton(int button, int action, boolean gameProcessed) {
            if (button > 2) return;
            boolean down = action == GLFW.GLFW_PRESS;
            fire(down, RbxEnum.KEY_CODE.get("Unknown"), RbxEnum.USER_INPUT_TYPE.fromValue(button), gameProcessed);
        }

        private void fire(boolean down, RbxEnum.Item key, RbxEnum.Item type, boolean gameProcessed) {
            String ev = down ? "InputBegan" : "InputEnded";
            var sig = existingEvent(ev);
            if (sig == null || !sig.hasListeners()) return;
            InputObject io = new InputObject(dm, key, type, RbxEnum.USER_INPUT_STATE.get(down ? "Begin" : "End"), mouse());
            sig.fire(io, gameProcessed);
        }
    }
}
