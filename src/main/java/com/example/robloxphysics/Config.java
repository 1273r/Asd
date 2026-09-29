package com.example.robloxphysics;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All values are in Roblox units (studs, studs/s, studs/s^2) so they can be compared directly
 * against Roblox docs. STUD_SIZE converts studs to Minecraft blocks (1 stud = 0.28 m,
 * Roblox's official meter conversion; 1 block = 1 m).
 */
public class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Master switch.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue STUD_SIZE = B
            .comment("Blocks per stud. Roblox: 1 stud = 0.28 meters.")
            .defineInRange("studSizeBlocks", 0.28, 0.01, 10.0);

    public static final ModConfigSpec.DoubleValue GRAVITY = B
            .comment("Workspace.Gravity in studs/s^2. Roblox default: 196.2")
            .defineInRange("gravity", 196.2, 0.0, 5000.0);

    public static final ModConfigSpec.DoubleValue WALK_SPEED = B
            .comment("Humanoid.WalkSpeed in studs/s. Roblox default: 16")
            .defineInRange("walkSpeed", 16.0, 0.0, 500.0);

    public static final ModConfigSpec.BooleanValue USE_JUMP_POWER = B
            .comment("true = use jumpPower, false = use jumpHeight (Roblox R15 default is JumpHeight).")
            .define("useJumpPower", false);

    public static final ModConfigSpec.DoubleValue JUMP_HEIGHT = B
            .comment("Humanoid.JumpHeight in studs. Roblox default: 7.2")
            .defineInRange("jumpHeight", 7.2, 0.0, 500.0);

    public static final ModConfigSpec.DoubleValue JUMP_POWER = B
            .comment("Humanoid.JumpPower in studs/s (only if useJumpPower). Roblox legacy default: 50")
            .defineInRange("jumpPower", 50.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue TERMINAL_VELOCITY = B
            .comment("Max fall speed in studs/s. 0 = unlimited. (Approximation - tune to taste.)")
            .defineInRange("terminalVelocity", 200.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue GROUND_ACCEL = B
            .comment("Horizontal acceleration toward input on ground, studs/s^2. (Approximation of the Humanoid controller.)")
            .defineInRange("groundAcceleration", 250.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue GROUND_BRAKE = B
            .comment("Deceleration with no input on ground, studs/s^2.")
            .defineInRange("groundBraking", 250.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue AIR_ACCEL = B
            .comment("Horizontal acceleration toward input in air, studs/s^2.")
            .defineInRange("airAcceleration", 100.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue AIR_BRAKE = B
            .comment("Deceleration with no input in air, studs/s^2.")
            .defineInRange("airBraking", 100.0, 0.0, 100000.0);

    public static final ModConfigSpec.BooleanValue DISABLE_FALL_DAMAGE = B
            .comment("Roblox has no fall damage by default.")
            .define("disableFallDamage", true);

    public static final ModConfigSpec.BooleanValue DISABLE_SPRINT = B
            .comment("Roblox has no sprint.")
            .define("disableSprint", true);

    public static final ModConfigSpec.BooleanValue NO_JUMP_DELAY = B
            .comment("Remove vanilla's 10-tick jump cooldown so holding space bunny-hops like Roblox.")
            .define("removeJumpDelay", true);

    public static final ModConfigSpec.BooleanValue SCALE_MOB_GRAVITY = B
            .comment("Scale every non-player living entity's gravity attribute to match Roblox gravity.")
            .define("scaleMobGravity", true);

    public static final ModConfigSpec SPEC = B.build();
}
