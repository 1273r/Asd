package com.example.robloxphysics.dm;

import com.example.robloxphysics.Config;
import com.example.robloxphysics.dm.ClassInfo.PT;

/** Defaults applied to every player's Humanoid and camera, like Roblox's StarterPlayer. */
public class StarterPlayer extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("StarterPlayer", Instance.CLASS, null)
            .prop("CharacterWalkSpeed", PT.NUMBER, (StarterPlayer s) -> s.walkSpeed, (StarterPlayer s, Object v) -> { s.walkSpeed = (Double) v; s.changed("CharacterWalkSpeed"); }).saved().cat("Character")
            .prop("CharacterJumpHeight", PT.NUMBER, (StarterPlayer s) -> s.jumpHeight, (StarterPlayer s, Object v) -> { s.jumpHeight = (Double) v; s.changed("CharacterJumpHeight"); }).saved().cat("Character")
            .prop("CharacterJumpPower", PT.NUMBER, (StarterPlayer s) -> s.jumpPower, (StarterPlayer s, Object v) -> { s.jumpPower = (Double) v; s.changed("CharacterJumpPower"); }).saved().cat("Character")
            .prop("CharacterUseJumpPower", PT.BOOL, (StarterPlayer s) -> s.useJumpPower, (StarterPlayer s, Object v) -> { s.useJumpPower = (Boolean) v; s.changed("CharacterUseJumpPower"); }).saved().cat("Character")
            .prop("CameraMaxZoomDistance", PT.NUMBER, (StarterPlayer s) -> s.maxZoom, (StarterPlayer s, Object v) -> { s.maxZoom = Math.max(0.5, (Double) v); s.changed("CameraMaxZoomDistance"); s.dm.onCameraSettingsChanged(); }).saved().cat("Camera")
            .prop("CameraMinZoomDistance", PT.NUMBER, (StarterPlayer s) -> s.minZoom, (StarterPlayer s, Object v) -> { s.minZoom = Math.max(0.5, (Double) v); s.changed("CameraMinZoomDistance"); s.dm.onCameraSettingsChanged(); }).saved().cat("Camera")
            .prop("EnableMouseLockOption", PT.BOOL, (StarterPlayer s) -> s.mouseLock, (StarterPlayer s, Object v) -> { s.mouseLock = (Boolean) v; s.changed("EnableMouseLockOption"); s.dm.onCameraSettingsChanged(); }).saved().cat("Camera");

    public double walkSpeed = 16, jumpHeight = 7.2, jumpPower = 50, maxZoom = 128, minZoom = 0.5;
    public boolean useJumpPower, mouseLock = true;

    public StarterPlayer(DataModel dm) {
        super(dm, CLASS);
        this.name = "StarterPlayer";
        try {
            walkSpeed = Config.WALK_SPEED.get();
            jumpHeight = Config.JUMP_HEIGHT.get();
            jumpPower = Config.JUMP_POWER.get();
            useJumpPower = Config.USE_JUMP_POWER.get();
        } catch (Throwable ignored) {}
    }
}
