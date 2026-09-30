package com.example.robloxphysics.client;

import com.example.robloxphysics.RobloxPhysics;
import com.example.robloxphysics.dm.HumanoidSettings;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Roblox "Classic" third-person camera, toggled with F5:
 *  - the mouse cursor is free; hold right mouse button and drag to orbit, scroll (or I/O) to zoom,
 *    arrow keys to turn the camera
 *  - WASD moves relative to the camera and the character smoothly turns to face its movement
 *    direction (Humanoid.AutoRotate), interpolated with CFrame:Lerp
 *  - left click / quick right click act on whatever is under the cursor
 *  - Shift Lock (default Left Alt) locks the cursor to the center, offsets the camera over the right
 *    shoulder and makes the character face the camera
 *  - zooming all the way in switches to first person, like Roblox
 */
@EventBusSubscriber(modid = RobloxPhysics.MODID, value = Dist.CLIENT)
public final class RobloxCamera {
    private RobloxCamera() {}

    public static final double DEFAULT_ZOOM = 12.5;      // studs, Roblox default camera distance
    private static final double SHOULDER_OFFSET = 1.75;  // studs, Roblox shift-lock offset
    private static final float PITCH_LIMIT = 80f;        // Roblox clamps camera pitch to +-80 degrees
    private static final double CLICK_MS = 250;

    private static boolean enabled;
    private static boolean shiftLock;
    private static float yaw, pitch;
    private static double zoom = DEFAULT_ZOOM, targetZoom = DEFAULT_ZOOM;
    private static boolean dragging;
    private static double dragLastX, dragLastY, dragStartX, dragStartY, dragDist;
    private static long dragStartMs;
    private static boolean releaseUseNextTick;
    private static double fov = 70;
    private static long lastFrameNanos;

    public static boolean isEnabled() { return enabled; }

    public static boolean isShiftLocked() { return enabled && shiftLock; }

    /** third person with a free cursor: clicks and picking go through the mouse position */
    public static boolean freeCursor() {
        Minecraft mc = Minecraft.getInstance();
        return enabled && !shiftLock && mc.screen == null && mc.player != null;
    }

    public static float yaw() { return yaw; }

    // ------------------------------------------------------------------ enabling

    public static void setEnabled(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (on == enabled || p == null) return;
        enabled = on;
        endDrag();
        if (on) {
            yaw = p.getYRot();
            pitch = 20f;
            HumanoidSettings h = HumanoidSettings.LOCAL;
            zoom = targetZoom = Mth.clamp(DEFAULT_ZOOM, h.minZoom + 1, h.maxZoom);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (!shiftLock) mc.mouseHandler.releaseMouse();
        } else {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            p.setYRot(yaw);
            p.setXRot(Mth.clamp(pitch, -90, 90));
            if (mc.screen == null) mc.mouseHandler.grabMouse();
        }
        mc.levelRenderer.needsUpdate();
    }

    public static void toggleShiftLock() {
        Minecraft mc = Minecraft.getInstance();
        if (!HumanoidSettings.LOCAL.mouseLockOption || mc.player == null) return;
        shiftLock = !shiftLock;
        if (!enabled) return;
        endDrag();
        if (shiftLock) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            if (mc.screen == null) mc.mouseHandler.grabMouse();
        } else {
            mc.mouseHandler.releaseMouse();
        }
    }

    // ------------------------------------------------------------------ keys (F5)

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            enabled = false;
            return;
        }
        // take over F5 before vanilla's handleKeybinds sees it
        while (mc.options.keyTogglePerspective.consumeClick()) setEnabled(!enabled);
        if (enabled && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            // something else changed the view (another mod / vanilla): follow it
            enabled = false;
            endDrag();
        }
        if (releaseUseNextTick) {
            releaseUseNextTick = false;
            KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT), false);
        }
        if (ClientKeys.SHIFT_LOCK.consumeClick()) toggleShiftLock();
    }

    // ------------------------------------------------------------------ per frame: mouse orbit & zoom

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        if (!enabled || mc.player == null) return;
        HumanoidSettings h = HumanoidSettings.LOCAL;
        targetZoom = Mth.clamp(targetZoom, h.minZoom, h.maxZoom);

        if (mc.screen == null) {
            if (shiftLock) {
                // camera follows the (mouse-turned) player, like Roblox's MouseLockController
                if (!mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
                yaw = mc.player.getYRot();
                pitch = Mth.clamp(mc.player.getXRot(), -PITCH_LIMIT, PITCH_LIMIT);
            } else {
                if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
                long win = mc.getWindow().getWindow();
                if (dragging) {
                    double[] x = new double[1], y = new double[1];
                    GLFW.glfwGetCursorPos(win, x, y);
                    double dx = x[0] - dragLastX, dy = y[0] - dragLastY;
                    dragLastX = x[0];
                    dragLastY = y[0];
                    dragDist += Math.abs(dx) + Math.abs(dy);
                    double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
                    double k = sens * sens * sens * 8.0 * 0.15;
                    yaw += (float) (dx * k);
                    pitch = Mth.clamp(pitch + (float) (dy * k), -PITCH_LIMIT, PITCH_LIMIT);
                }
                // Roblox keyboard camera controls
                if (!mc.options.keyChat.isDown()) {
                    float turn = (float) (dt * 120);
                    if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT)) yaw -= turn;
                    if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT)) yaw += turn;
                    if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_I)) targetZoom = Math.max(h.minZoom, targetZoom * (1 - dt * 2));
                    if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_O)) targetZoom = Math.min(h.maxZoom, targetZoom * (1 + dt * 2));
                }
            }
        } else if (dragging) {
            endDrag();
        }
        zoom += (targetZoom - zoom) * Math.min(1.0, dt * 14);
    }

    private static void endDrag() {
        if (!dragging) return;
        dragging = false;
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(win, dragStartX, dragStartY);
    }

    // ------------------------------------------------------------------ camera placement

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.getCameraEntity() != mc.player || ClientScripting.scriptableCamera()) return;
        e.setYaw(yaw);
        e.setPitch(pitch);
    }

    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.getCameraEntity() != mc.player) return;
        e.setDistance((float) (Units.toBlocks(zoom) / Math.max(0.01f, e.getEntityScalingFactor())));
    }

    /** extra right-shoulder offset in blocks (shift lock), applied by CameraMixin */
    public static float shoulderOffset() {
        return isShiftLocked() ? (float) Units.toBlocks(SHOULDER_OFFSET) : 0f;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (e.usedConfiguredFov()) fov = e.getFOV();
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre e) {
        if (freeCursor() && e.getName().equals(VanillaGuiLayers.CROSSHAIR)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ mouse buttons & wheel

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (!freeCursor() || mc.getOverlay() != null) return;
        int button = e.getButton();
        boolean down = e.getAction() == GLFW.GLFW_PRESS;
        if (e.getAction() == GLFW.GLFW_REPEAT) return;
        e.setCanceled(true); // don't let vanilla grab the mouse
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            long win = mc.getWindow().getWindow();
            if (down) {
                double[] x = new double[1], y = new double[1];
                GLFW.glfwGetCursorPos(win, x, y);
                dragStartX = dragLastX = x[0];
                dragStartY = dragLastY = y[0];
                dragDist = 0;
                dragStartMs = System.currentTimeMillis();
                dragging = true;
                GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                GLFW.glfwGetCursorPos(win, x, y);
                dragLastX = x[0];
                dragLastY = y[0];
            } else if (dragging) {
                boolean click = System.currentTimeMillis() - dragStartMs < CLICK_MS && dragDist < 6;
                endDrag();
                if (click) {
                    // a quick right click uses the item / block under the cursor
                    aimAtCursor();
                    KeyMapping.set(key, true);
                    KeyMapping.click(key);
                    releaseUseNextTick = true;
                }
            }
            return;
        }
        if (down) aimAtCursor();
        KeyMapping.set(key, down);
        if (down) KeyMapping.click(key);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        HumanoidSettings h = HumanoidSettings.LOCAL;
        double d = e.getScrollDeltaY();
        if (d > 0 && targetZoom <= h.minZoom + 1e-3 && h.minZoom <= 0.5) {
            setEnabled(false); // zoomed all the way in: first person
        } else {
            targetZoom = Mth.clamp(targetZoom * Math.pow(0.85, d), h.minZoom, h.maxZoom);
        }
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ character rotation (AutoRotate)

    /** Runs before ClientPhysics: turns camera-relative WASD into world movement and rotates the body. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMovementInput(MovementInputUpdateEvent e) {
        if (!enabled || !(e.getEntity() instanceof LocalPlayer p) || p.isPassenger() || shiftLock) return;
        Input in = e.getInput();
        double fwd = in.forwardImpulse, left = in.leftImpulse;
        double cr = Math.toRadians(yaw);
        // world-space move direction from camera-relative input (vanilla moveRelative convention)
        double dx = left * Math.cos(cr) - fwd * Math.sin(cr);
        double dz = fwd * Math.cos(cr) + left * Math.sin(cr);
        double len = Math.sqrt(dx * dx + dz * dz);
        float bodyYaw = p.getYRot();
        if (len > 1e-3 && HumanoidSettings.LOCAL.autoRotate) {
            // smooth turn toward the move direction using CFrame slerp, like the Humanoid controller
            V3 pos = Units.toStuds(p.position());
            CFrame current = CFrame.fromMcYaw(pos, bodyYaw);
            CFrame target = CFrame.lookAt(pos, pos.add(new V3(dx, 0, dz)));
            bodyYaw = current.lerp(target, 0.45).mcYaw();
            // keep yaw continuous with the previous value so interpolation doesn't spin
            bodyYaw = p.getYRot() + Mth.wrapDegrees(bodyYaw - p.getYRot());
            p.setYRot(bodyYaw);
            p.setYBodyRot(bodyYaw);
            p.setYHeadRot(bodyYaw);
            p.setXRot(0);
        }
        // re-express the world direction relative to the body so every movement mode (walk, swim, fly) goes the same way
        double br = Math.toRadians(bodyYaw);
        in.forwardImpulse = (float) (dx * -Math.sin(br) + dz * Math.cos(br));
        in.leftImpulse = (float) (dx * Math.cos(br) + dz * Math.sin(br));
    }

    // ------------------------------------------------------------------ cursor picking

    /** World ray under the mouse cursor (from the camera), or null. */
    public static Vec3[] cursorRay(double maxDist) {
        Minecraft mc = Minecraft.getInstance();
        return cursorRayAt(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), maxDist);
    }

    /** World ray through a window pixel (from the camera). */
    public static Vec3[] cursorRayAt(double px, double py, double maxDist) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        if (!cam.isInitialized()) return null;
        var win = mc.getWindow();
        double w = win.getScreenWidth(), hgt = win.getScreenHeight();
        if (w <= 0 || hgt <= 0) return null;
        double nx = 2 * px / w - 1;
        double ny = 1 - 2 * py / hgt;
        double tan = Math.tan(Math.toRadians(fov) / 2);
        Vector3f f = cam.getLookVector(), u = cam.getUpVector(), l = cam.getLeftVector();
        Vec3 dir = new Vec3(f.x, f.y, f.z)
                .add(new Vec3(-l.x, -l.y, -l.z).scale(nx * tan * w / hgt))
                .add(new Vec3(u.x, u.y, u.z).scale(ny * tan))
                .normalize();
        Vec3 from = cam.getPosition();
        return new Vec3[]{from, from.add(dir.scale(maxDist))};
    }

    /** Replaces the crosshair pick with the thing under the cursor (called from GameRendererMixin). */
    public static void pickUnderCursor() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!freeCursor() || dragging || p == null || mc.level == null) return;
        double blockReach = p.blockInteractionRange(), entityReach = p.entityInteractionRange();
        Vec3 eye = p.getEyePosition();
        double camDist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(eye);
        Vec3[] ray = cursorRay(camDist + Math.max(blockReach, entityReach) + 2);
        if (ray == null) return;
        Vec3 from = ray[0], to = ray[1];
        HitResult result;
        BlockHitResult bh = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double blockDist = bh.getType() == HitResult.Type.MISS ? from.distanceTo(to) : bh.getLocation().distanceTo(from);
        Vec3 clipped = from.add(to.subtract(from).normalize().scale(blockDist));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, from, clipped, new AABB(from, clipped).inflate(1),
                ent -> ent != p && !ent.isSpectator() && ent.isPickable(), blockDist * blockDist);
        if (eh != null && eh.getLocation().distanceTo(eye) <= entityReach + 0.5) {
            result = eh;
        } else if (bh.getType() == HitResult.Type.BLOCK && bh.getLocation().distanceTo(eye) <= blockReach) {
            result = bh;
        } else {
            Vec3 end = eh != null ? eh.getLocation() : bh.getLocation();
            result = BlockHitResult.miss(end, bh.getDirection(), bh.getBlockPos());
        }
        mc.hitResult = result;
        mc.crosshairPickEntity = result instanceof EntityHitResult er ? er.getEntity() : null;
    }

    /** Face the character toward the cursor target before using/attacking, like a Roblox tool aiming at Mouse.Hit. */
    private static void aimAtCursor() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        pickUnderCursor();
        Vec3 target;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            target = mc.hitResult instanceof EntityHitResult er ? er.getEntity().getBoundingBox().getCenter() : mc.hitResult.getLocation();
        } else {
            Vec3[] ray = cursorRay(64);
            if (ray == null) return;
            target = ray[1];
        }
        Vec3 d = target.subtract(p.getEyePosition());
        float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float tp = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        float newYaw = p.getYRot() + Mth.wrapDegrees(ty - p.getYRot());
        p.setYRot(newYaw);
        p.setYBodyRot(newYaw);
        p.setYHeadRot(newYaw);
        p.setXRot(Mth.clamp(tp, -90, 90));
    }
}
