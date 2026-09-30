package com.example.robloxphysics.rbx;

import com.example.robloxphysics.Config;
import net.minecraft.world.phys.Vec3;

/** Studs <-> blocks. The Lua/Studio API is in studs like Roblox; the world is in blocks. */
public final class Units {
    private Units() {}

    public static double stud() {
        try {
            return Config.STUD_SIZE.get();
        } catch (Throwable t) {
            return 0.28; // config not loaded yet
        }
    }

    public static V3 toStuds(Vec3 v) {
        double s = stud();
        return new V3(v.x / s, v.y / s, v.z / s);
    }

    public static Vec3 toBlocks(V3 v) {
        double s = stud();
        return new Vec3(v.x() * s, v.y() * s, v.z() * s);
    }

    public static double toStuds(double blocks) { return blocks / stud(); }

    public static double toBlocks(double studs) { return studs * stud(); }

    /** studs/s -> blocks/tick */
    public static double speedToTick(double studsPerSecond) { return studsPerSecond * stud() / 20.0; }

    /** blocks/tick -> studs/s */
    public static double speedFromTick(double blocksPerTick) { return blocksPerTick * 20.0 / stud(); }

    public static V3 velToStuds(Vec3 v) {
        return new V3(speedFromTick(v.x), speedFromTick(v.y), speedFromTick(v.z));
    }

    public static Vec3 velToBlocks(V3 v) {
        return new Vec3(speedToTick(v.x()), speedToTick(v.y()), speedToTick(v.z()));
    }
}
