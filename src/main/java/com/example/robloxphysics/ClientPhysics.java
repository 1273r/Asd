package com.example.robloxphysics;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Replaces the local player's movement model with a Roblox-style one.
 *
 * Minecraft's player physics are client-authoritative, so we run the whole model here.
 *
 * Per tick:
 *  1) MovementInputUpdateEvent (before vanilla travel):
 *       - read WASD, then zero it so vanilla adds no acceleration/friction of its own
 *       - accelerate the horizontal velocity toward WalkSpeed in the wish direction
 *       - configure the jump attribute so a jump matches Roblox's JumpHeight
 *  2) Vanilla moves the player with the velocity we set (collisions handled by vanilla).
 *  3) PlayerTickEvent.Post: undo vanilla's friction / 0.98 drag / 0.08 gravity and
 *     apply Roblox gravity with no drag instead.
 *
 * Vertical integration uses midpoint velocity, so each tick's displacement equals the exact
 * continuous parabola (h = v0^2 / 2g) instead of the ~20% overshoot naive Euler would give.
 */
@EventBusSubscriber(modid = RobloxPhysics.MODID, value = Dist.CLIENT)
public class ClientPhysics {
    private static boolean active;     // horizontal model ran this tick
    private static boolean vertical;   // vertical model ran this tick
    private static double usedX, usedZ;

    private static boolean canControl(LocalPlayer p) {
        return p.isAlive()
                && !p.isPassenger()
                && !p.isSpectator()
                && !p.isSleeping()
                && !p.getAbilities().flying
                && !p.isFallFlying()
                && !p.isInWater()
                && !p.isInLava()
                && !p.isSwimming()
                && !p.onClimbable();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        active = false;
        vertical = false;
        if (!Config.ENABLED.get()) return;
        if (!(event.getEntity() instanceof LocalPlayer p) || !canControl(p)) return;

        final double stud = Config.STUD_SIZE.get();
        final double speedUnit = stud / 20.0;    // studs/s   -> blocks/tick
        final double accelUnit = stud / 400.0;   // studs/s^2 -> blocks/tick^2
        final double G = Config.GRAVITY.get() * accelUnit;

        // ---- Jump: v0 = sqrt(2 g h) (or JumpPower). Vanilla moves with the velocity set on
        // the jump tick, so we hand it the midpoint velocity v0 - G/2.
        double jumpStuds = Config.USE_JUMP_POWER.get()
                ? Config.JUMP_POWER.get()
                : Math.sqrt(2.0 * Config.GRAVITY.get() * Config.JUMP_HEIGHT.get());
        AttributeInstance jumpAttr = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.setBaseValue(Math.max(0.0, jumpStuds * speedUnit - G / 2.0));
        }
        if (Config.NO_JUMP_DELAY.get()) {
            p.noJumpDelay = 0; // access-transformed
        }
        if (Config.DISABLE_SPRINT.get()) {
            p.setSprinting(false);
        }

        // ---- Horizontal
        Input in = event.getInput();
        float fwd = in.forwardImpulse;
        float left = in.leftImpulse;
        if (p.isUsingItem() && !p.isPassenger()) { // vanilla applies this after the event
            fwd *= 0.2F;
            left *= 0.2F;
        }
        in.forwardImpulse = 0.0F; // stop vanilla from adding its own acceleration
        in.leftImpulse = 0.0F;

        double len = Math.sqrt(fwd * fwd + left * left);
        boolean hasInput = len > 1.0E-3;
        double tx = 0.0, tz = 0.0;
        if (hasInput) {
            double mag = Math.min(1.0, len); // sneaking (0.3) etc. scales speed like vanilla
            double nx = left / len, nz = fwd / len;
            double yaw = Math.toRadians(p.getYRot());
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            double walk = Config.WALK_SPEED.get() * speedUnit * mag;
            tx = (nx * cos - nz * sin) * walk; // same rotation as vanilla moveRelative
            tz = (nz * cos + nx * sin) * walk;
        }

        boolean ground = p.onGround();
        double accel = (hasInput
                ? (ground ? Config.GROUND_ACCEL.get() : Config.AIR_ACCEL.get())
                : (ground ? Config.GROUND_BRAKE.get() : Config.AIR_BRAKE.get())) * accelUnit;

        Vec3 cur = p.getDeltaMovement(); // also carries knockback etc. from the server
        double dx = tx - cur.x, dz = tz - cur.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double nvx, nvz;
        if (dist <= accel || dist < 1.0E-9) {
            nvx = tx;
            nvz = tz;
        } else {
            nvx = cur.x + dx / dist * accel;
            nvz = cur.z + dz / dist * accel;
        }
        p.setDeltaMovement(nvx, cur.y, nvz);
        usedX = nvx;
        usedZ = nvz;
        active = true;

        vertical = !p.isNoGravity()
                && !p.hasEffect(MobEffects.SLOW_FALLING)
                && !p.hasEffect(MobEffects.LEVITATION);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!active || !(event.getEntity() instanceof LocalPlayer p)) return;
        active = false;

        final double stud = Config.STUD_SIZE.get();
        final double G = Config.GRAVITY.get() * stud / 400.0;

        Vec3 d = p.getDeltaMovement();

        // Vanilla zeroes a velocity component when it hits a wall; otherwise it only scaled it
        // by friction. Restore our un-frictioned velocity unless a collision zeroed it.
        double x = d.x == 0.0 ? 0.0 : usedX;
        double z = d.z == 0.0 ? 0.0 : usedZ;
        double y = d.y;

        if (vertical) {
            double g = p.getAttributeValue(Attributes.GRAVITY);
            double newY;
            if (p.verticalCollision && d.y <= 0.0) {
                // landed or bumped head: velocity was zeroed. Next tick's midpoint velocity
                // from rest is -G/2 (must stay negative so vanilla keeps onGround set).
                newY = -G / 2.0;
            } else {
                // vanilla did: y' = (y - g) * 0.98 ; recover y, then apply Roblox gravity, no drag
                newY = d.y / 0.98 + g - G;
            }
            double terminal = Config.TERMINAL_VELOCITY.get();
            if (terminal > 0.0) {
                newY = Math.max(newY, -terminal * stud / 20.0);
            }
            y = newY;
        }

        p.setDeltaMovement(x, y, z);
    }
}
