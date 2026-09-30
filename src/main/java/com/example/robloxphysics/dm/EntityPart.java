package com.example.robloxphysics.dm;

import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Color3;
import com.example.robloxphysics.rbx.RbxEnum;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** HumanoidRootPart / Head of a character: a view onto the Minecraft entity. */
public class EntityPart extends BasePart {
    public static final ClassInfo CLASS = ClassInfo.define("MeshPart", BasePart.CLASS, null);
    static final int ROOT = 0, HEAD = 1;

    private final CharacterModel model;
    private final int kind;

    EntityPart(DataModel dm, CharacterModel model, int kind) {
        super(dm, CLASS);
        this.model = model;
        this.kind = kind;
        this.name = kind == ROOT ? "HumanoidRootPart" : "Head";
    }

    private LivingEntity e() { return model.entity; }

    /** block offset from entity feet to this part's center */
    private double offset() {
        return kind == ROOT ? e().getBbHeight() * 0.5 : e().getEyeHeight();
    }

    @Override
    public CFrame getCFrame() {
        LivingEntity e = e();
        V3 pos = Units.toStuds(e.position().add(0, offset(), 0));
        if (kind == ROOT) return CFrame.fromMcYaw(pos, e.yBodyRot);
        Vec3 look = e.getViewVector(1f);
        return CFrame.lookAt(pos, pos.add(new V3(look.x, look.y, look.z)));
    }

    @Override
    public void setCFrame(CFrame cf) {
        CharacterModel.teleport(e(), cf, offset());
        changed("CFrame");
    }

    @Override
    public V3 getSize() {
        AABB b = e().getBoundingBox();
        if (kind == HEAD) return new V3(Units.toStuds(b.getXsize()), Units.toStuds(b.getXsize()), Units.toStuds(b.getZsize()));
        return new V3(Units.toStuds(b.getXsize()), Units.toStuds(b.getYsize()), Units.toStuds(b.getZsize()));
    }

    @Override
    public void setSize(V3 v) { throw new IllegalArgumentException("Size of a character part cannot be changed"); }

    @Override
    public Color3 getColor() { return Color3.fromRGB(163, 162, 165); }

    @Override
    public void setColor(Color3 c) {}

    @Override
    public RbxEnum.Item getMaterial() { return RbxEnum.MATERIAL.get("Plastic"); }

    @Override
    public void setMaterial(RbxEnum.Item m) {}

    @Override
    public double getTransparency() { return e().isInvisible() ? 1 : 0; }

    @Override
    public void setTransparency(double t) {
        if (kind == ROOT) e().setInvisible(t >= 1);
        changed("Transparency");
    }

    @Override
    public boolean isAnchored() { return e() instanceof Mob m && m.isNoAi(); }

    @Override
    public void setAnchored(boolean a) {
        if (e() instanceof Mob m) {
            m.setNoAi(a);
            m.setNoGravity(a);
            if (a) m.setDeltaMovement(Vec3.ZERO);
        }
        changed("Anchored");
    }

    @Override
    public boolean canCollide() { return true; }

    @Override
    public void setCanCollide(boolean c) {}

    @Override
    public V3 getVelocity() { return Units.velToStuds(e().getDeltaMovement()); }

    @Override
    public void setVelocity(V3 v) {
        e().setDeltaMovement(Units.velToBlocks(v));
        e().hurtMarked = true; // makes the server send the new velocity to the owning client
        changed("AssemblyLinearVelocity");
    }
}
