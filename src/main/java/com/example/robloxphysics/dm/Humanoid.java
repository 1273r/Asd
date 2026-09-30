package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.RbxLib;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaValue;

/**
 * Humanoid of a character. Health uses Roblox's 100-point scale (Minecraft's 20 HP x 5).
 * For players, movement values live in {@link HumanoidSettings} and are replicated to the client physics.
 */
public class Humanoid extends Instance {
    public static final double HEALTH_SCALE = 5.0;

    public static final ClassInfo CLASS = ClassInfo.define("Humanoid", Instance.CLASS, null)
            .prop("Health", PT.NUMBER, (Humanoid h) -> h.e().getHealth() * HEALTH_SCALE, (Humanoid h, Object v) -> h.setHealth((Double) v)).cat("Health")
            .prop("MaxHealth", PT.NUMBER, (Humanoid h) -> h.e().getMaxHealth() * HEALTH_SCALE, (Humanoid h, Object v) -> {
                AttributeInstance a = h.e().getAttribute(Attributes.MAX_HEALTH);
                if (a != null) a.setBaseValue(Math.max(1, (Double) v / HEALTH_SCALE));
                h.changed("MaxHealth");
            }).cat("Health")
            .prop("WalkSpeed", PT.NUMBER, (Humanoid h) -> h.walkSpeed(), (Humanoid h, Object v) -> h.setWalkSpeed((Double) v)).cat("Movement")
            .prop("JumpHeight", PT.NUMBER, (Humanoid h) -> h.settings().jumpHeight, (Humanoid h, Object v) -> {
                h.settings().jumpHeight = Math.max(0, (Double) v);
                h.settingsChanged("JumpHeight");
            }).cat("Movement")
            .prop("JumpPower", PT.NUMBER, (Humanoid h) -> h.settings().jumpPower, (Humanoid h, Object v) -> {
                h.settings().jumpPower = Math.max(0, (Double) v);
                h.settingsChanged("JumpPower");
            }).cat("Movement")
            .prop("UseJumpPower", PT.BOOL, (Humanoid h) -> h.settings().useJumpPower, (Humanoid h, Object v) -> {
                h.settings().useJumpPower = (Boolean) v;
                h.settingsChanged("UseJumpPower");
            }).cat("Movement")
            .prop("AutoRotate", PT.BOOL, (Humanoid h) -> h.settings().autoRotate, (Humanoid h, Object v) -> {
                h.settings().autoRotate = (Boolean) v;
                h.settingsChanged("AutoRotate");
            }).cat("Movement")
            .prop("Jump", PT.BOOL, (Humanoid h) -> !h.e().onGround(), (Humanoid h, Object v) -> {
                if ((Boolean) v) h.jump();
            }).cat("Control")
            .prop("MoveDirection", PT.V3, (Humanoid h) -> {
                Vec3 d = h.e().getDeltaMovement();
                V3 flat = new V3(d.x, 0, d.z);
                return flat.length() < 1e-3 ? V3.ZERO : flat.unit();
            }, null).cat("Control")
            .prop("DisplayName", PT.STRING, (Humanoid h) -> h.model.name, null)
            .prop("RootPart", PT.INSTANCE, (Humanoid h) -> h.model.rootPart, null)
            .method("TakeDamage", (self, a) -> {
                Humanoid h = (Humanoid) self;
                double dmg = a.checkdouble(1);
                if (!h.dm.server) h.setHealth(h.e().getHealth() * HEALTH_SCALE - dmg);
                else h.e().hurt(h.e().damageSources().generic(), (float) (dmg / HEALTH_SCALE));
                return LuaValue.NONE;
            })
            .method("MoveTo", (self, a) -> {
                Humanoid h = (Humanoid) self;
                if (h.e() instanceof Mob m) {
                    Vec3 p = Units.toBlocks(RbxLib.checkV3(a.arg(1)));
                    m.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
                }
                return LuaValue.NONE;
            })
            .method("GetState", (self, a) -> {
                LivingEntity e = ((Humanoid) self).e();
                String s = !e.isAlive() ? "Dead" : e.isInWater() ? "Swimming" : e.onClimbable() ? "Climbing"
                        : e.onGround() ? "Running" : e.getDeltaMovement().y > 0 ? "Jumping" : "Freefall";
                return RbxLib.toLua(com.example.robloxphysics.rbx.RbxEnum.HUMANOID_STATE.get(s));
            })
            .event("Died").event("HealthChanged").event("Jumping").event("Running");

    private final CharacterModel model;
    private final HumanoidSettings mobSettings = new HumanoidSettings();
    private float lastHealth = -1;
    private boolean diedFired;

    Humanoid(DataModel dm, CharacterModel model) {
        super(dm, CLASS);
        this.model = model;
        this.name = "Humanoid";
    }

    LivingEntity e() { return model.entity; }

    private Player player() {
        if (e() instanceof net.minecraft.world.entity.player.Player mp) return dm.players.byUuid(mp.getUUID());
        return null;
    }

    HumanoidSettings settings() {
        Player p = player();
        return p != null ? p.settings : mobSettings;
    }

    private void settingsChanged(String prop) {
        Player p = player();
        if (p != null) p.settingsChanged();
        changed(prop);
    }

    double walkSpeed() {
        if (player() == null) {
            AttributeInstance a = e().getAttribute(Attributes.MOVEMENT_SPEED);
            // vanilla player speed attribute 0.1 ~= 4.317 blocks/s; mobs use the same scale
            if (a != null) return a.getBaseValue() * 43.17 / Units.stud();
        }
        return settings().walkSpeed;
    }

    void setWalkSpeed(double v) {
        v = Math.max(0, v);
        if (player() == null) {
            AttributeInstance a = e().getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(v * Units.stud() / 43.17);
            changed("WalkSpeed");
            return;
        }
        settings().walkSpeed = v;
        settingsChanged("WalkSpeed");
    }

    void setHealth(double v) {
        LivingEntity e = e();
        float hp = (float) (v / HEALTH_SCALE);
        if (hp <= 0) {
            if (dm.server) e.kill();
            else e.setHealth(0);
        } else {
            e.setHealth(Math.min(hp, e.getMaxHealth()));
        }
        changed("Health");
    }

    void jump() {
        LivingEntity e = e();
        if (e instanceof Mob m) {
            m.getJumpControl().jump();
        } else if (e instanceof net.minecraft.world.entity.player.Player) {
            settings().jumpRequest = true;
            Player p = player();
            if (p != null) p.settingsChanged();
        }
    }

    /** per-tick health tracking for HealthChanged / Died */
    void tick() {
        float h = e().getHealth();
        if (lastHealth >= 0 && h != lastHealth) {
            fireEvent("HealthChanged", (double) h * HEALTH_SCALE);
            changed("Health");
        }
        lastHealth = h;
        checkDied();
    }

    void checkDied() {
        if (!diedFired && (e().isDeadOrDying() || !e().isAlive())) {
            diedFired = true;
            fireEvent("Died");
        }
    }
}
