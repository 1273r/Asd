package com.example.robloxphysics.dm;

import com.example.robloxphysics.Config;

/**
 * Per-player Humanoid movement values (studs units). The server keeps one per player and replicates it to
 * that player's client; {@link #LOCAL} is what the local player's physics actually uses.
 */
public final class HumanoidSettings {
    public double walkSpeed, jumpHeight, jumpPower, gravity;
    public boolean useJumpPower, autoRotate = true;
    public double maxZoom = 128, minZoom = 0.5;
    public boolean mouseLockOption = true;
    /** Humanoid.Jump = true was set; consumed by the physics */
    public volatile boolean jumpRequest;

    /** The local player's settings (client). */
    public static final HumanoidSettings LOCAL = new HumanoidSettings();

    public HumanoidSettings() {
        resetToConfig();
    }

    public void resetToConfig() {
        try {
            walkSpeed = Config.WALK_SPEED.get();
            jumpHeight = Config.JUMP_HEIGHT.get();
            jumpPower = Config.JUMP_POWER.get();
            useJumpPower = Config.USE_JUMP_POWER.get();
            gravity = Config.GRAVITY.get();
        } catch (Throwable t) {
            walkSpeed = 16;
            jumpHeight = 7.2;
            jumpPower = 50;
            useJumpPower = false;
            gravity = 196.2;
        }
        autoRotate = true;
        maxZoom = 128;
        minZoom = 0.5;
        mouseLockOption = true;
    }

    public void copyFrom(HumanoidSettings o) {
        walkSpeed = o.walkSpeed;
        jumpHeight = o.jumpHeight;
        jumpPower = o.jumpPower;
        useJumpPower = o.useJumpPower;
        gravity = o.gravity;
        autoRotate = o.autoRotate;
        maxZoom = o.maxZoom;
        minZoom = o.minZoom;
        mouseLockOption = o.mouseLockOption;
    }
}
