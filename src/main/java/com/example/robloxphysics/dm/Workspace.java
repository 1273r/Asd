package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.rbx.Units;

import java.util.List;

public class Workspace extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Workspace", Instance.CLASS, null)
            .prop("Gravity", PT.NUMBER, (Workspace w) -> w.dm.gravity, (Workspace w, Object v) -> {
                w.dm.gravity = Math.max(0, (Double) v);
                w.dm.onGravityChanged();
                w.changed("Gravity");
            }).saved().cat("Physics")
            .prop("DistributedGameTime", PT.NUMBER, (Workspace w) -> w.dm.tickCount / 20.0, null)
            .prop("StudSize", PT.NUMBER, (Workspace w) -> Units.stud(), null).cat("Physics")
            .prop("CurrentCamera", PT.INSTANCE, (Workspace w) -> w.currentCamera, null);

    public Instance currentCamera; // client only

    public Workspace(DataModel dm) {
        super(dm, CLASS);
        this.name = "Workspace";
    }

    @Override
    public List<Instance> getChildren() {
        dm.refreshCharacters();
        return children;
    }
}
