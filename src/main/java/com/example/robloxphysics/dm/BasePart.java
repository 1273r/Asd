package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Color3;
import com.example.robloxphysics.rbx.BrickColor;
import com.example.robloxphysics.rbx.RbxEnum;
import com.example.robloxphysics.rbx.V3;

/** Shared BasePart API for real Parts and for the body parts of Minecraft entities. */
public abstract class BasePart extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("BasePart", Instance.CLASS, null)
            .prop("Position", PT.V3, (BasePart p) -> p.getCFrame().position(),
                    (BasePart p, Object v) -> p.setCFrame(p.getCFrame().withPosition((V3) v))).cat("Transform")
            .prop("Orientation", PT.V3, (BasePart p) -> p.getCFrame().orientationDegrees(),
                    (BasePart p, Object v) -> p.setCFrame(CFrame.fromOrientationDegrees(p.getCFrame().position(), (V3) v))).cat("Transform")
            .prop("CFrame", PT.CFRAME, (BasePart p) -> p.getCFrame(), (BasePart p, Object v) -> p.setCFrame((CFrame) v)).hidden().saved()
            .prop("Size", PT.V3, (BasePart p) -> p.getSize(), (BasePart p, Object v) -> p.setSize((V3) v)).saved().cat("Transform")
            .prop("Color", PT.COLOR3, (BasePart p) -> p.getColor(), (BasePart p, Object v) -> p.setColor((Color3) v)).saved().cat("Appearance")
            .prop("BrickColor", PT.BRICKCOLOR, (BasePart p) -> BrickColor.closest(p.getColor()),
                    (BasePart p, Object v) -> p.setColor(((BrickColor) v).color())).hidden()
            .enumProp("Material", RbxEnum.MATERIAL, (BasePart p) -> p.getMaterial(), (BasePart p, Object v) -> p.setMaterial((RbxEnum.Item) v)).saved().cat("Appearance")
            .prop("Transparency", PT.NUMBER, (BasePart p) -> p.getTransparency(), (BasePart p, Object v) -> p.setTransparency((Double) v)).saved().cat("Appearance")
            .prop("Anchored", PT.BOOL, (BasePart p) -> p.isAnchored(), (BasePart p, Object v) -> p.setAnchored((Boolean) v)).saved().cat("Behavior")
            .prop("CanCollide", PT.BOOL, (BasePart p) -> p.canCollide(), (BasePart p, Object v) -> p.setCanCollide((Boolean) v)).saved().cat("Collision")
            .prop("AssemblyLinearVelocity", PT.V3, (BasePart p) -> p.getVelocity(), (BasePart p, Object v) -> p.setVelocity((V3) v)).cat("Assembly")
            .prop("Velocity", PT.V3, (BasePart p) -> p.getVelocity(), (BasePart p, Object v) -> p.setVelocity((V3) v)).hidden()
            .event("Touched").event("TouchEnded");

    protected BasePart(DataModel dm, ClassInfo cls) {
        super(dm, cls);
    }

    public abstract CFrame getCFrame();

    public abstract void setCFrame(CFrame cf);

    public abstract V3 getSize();

    public abstract void setSize(V3 v);

    public abstract Color3 getColor();

    public abstract void setColor(Color3 c);

    public abstract RbxEnum.Item getMaterial();

    public abstract void setMaterial(RbxEnum.Item m);

    public abstract double getTransparency();

    public abstract void setTransparency(double t);

    public abstract boolean isAnchored();

    public abstract void setAnchored(boolean a);

    public abstract boolean canCollide();

    public abstract void setCanCollide(boolean c);

    public abstract V3 getVelocity();

    public abstract void setVelocity(V3 v);
}
