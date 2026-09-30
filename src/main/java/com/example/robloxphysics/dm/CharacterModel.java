package com.example.robloxphysics.dm;

import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Units;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** Workspace model for a Minecraft living entity (players and mobs), like a Roblox character. */
public class CharacterModel extends Model {
    public final LivingEntity entity;
    public final Humanoid humanoid;
    public final EntityPart rootPart, head;

    public CharacterModel(DataModel dm, LivingEntity e) {
        super(dm, Model.CLASS);
        this.entity = e;
        this.id = "c" + e.getUUID();
        this.name = e instanceof net.minecraft.world.entity.player.Player p ? p.getGameProfile().getName() : e.getName().getString();
        humanoid = new Humanoid(dm, this);
        humanoid.id = id + "/Humanoid";
        rootPart = new EntityPart(dm, this, EntityPart.ROOT);
        rootPart.id = id + "/HumanoidRootPart";
        head = new EntityPart(dm, this, EntityPart.HEAD);
        head.id = id + "/Head";
        for (Instance i : List.of(humanoid, rootPart, head)) {
            i.attach(this);
            i.parentLocked = true;
        }
        primary = rootPart;
    }

    @Override
    public BasePart primaryPart() { return rootPart; }

    @Override
    public CFrame getPivot() { return rootPart.getCFrame(); }

    @Override
    public void pivotTo(CFrame target) { rootPart.setCFrame(target); }

    @Override
    public void destroy() {
        // Destroying a character kills it (players) or removes it (mobs), like Roblox
        if (!dm.server) throw new IllegalArgumentException("Characters can only be destroyed by the server");
        if (entity instanceof ServerPlayer sp) sp.kill();
        else entity.discard();
    }

    /** the entity died or was unloaded */
    void entityGone() {
        humanoid.checkDied();
        parentLocked = false;
        for (Instance c : getChildren()) c.parentLocked = false;
        super.destroy();
    }

    /** Teleport so the HumanoidRootPart ends up at cf (studs). */
    static void teleport(Entity e, CFrame cf, double rootOffsetBlocks) {
        Vec3 p = Units.toBlocks(cf.position()).subtract(0, rootOffsetBlocks, 0);
        float yaw = cf.mcYaw();
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(sp.serverLevel(), p.x, p.y, p.z, Set.of(), yaw, sp.getXRot());
        } else if (e.level().isClientSide) {
            e.setPos(p);
            e.setYRot(yaw);
            if (e instanceof LivingEntity le) {
                le.yBodyRot = yaw;
                le.yHeadRot = yaw;
            }
        } else {
            e.moveTo(p.x, p.y, p.z, yaw, e.getXRot());
            e.setYHeadRot(yaw);
        }
    }
}
