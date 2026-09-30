package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.RbxLib;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.V3;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

public class Model extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Model", Instance.CLASS, Model::new)
            .prop("PrimaryPart", PT.INSTANCE, (Model m) -> m.primaryPart(), (Model m, Object v) -> {
                if (v != null && !(v instanceof BasePart)) throw new IllegalArgumentException("PrimaryPart must be a BasePart");
                m.primary = (BasePart) v;
                m.changed("PrimaryPart");
            })
            .method("GetPivot", (self, a) -> RbxLib.toLua(((Model) self).getPivot()))
            .method("PivotTo", (self, a) -> {
                ((Model) self).pivotTo(RbxLib.checkCFrame(a.arg(1)));
                return LuaValue.NONE;
            })
            .method("MoveTo", (self, a) -> {
                Model m = (Model) self;
                m.pivotTo(m.getPivot().withPosition(RbxLib.checkV3(a.arg(1))));
                return LuaValue.NONE;
            })
            .method("GetExtentsSize", (self, a) -> {
                V3[] b = ((Model) self).bounds();
                return RbxLib.toLua(b == null ? V3.ZERO : b[1].sub(b[0]));
            })
            .method("BreakJoints", (self, a) -> {
                ((Model) self).breakJoints();
                return LuaValue.NONE;
            });

    protected BasePart primary;

    public Model(DataModel dm, ClassInfo cls) {
        super(dm, cls);
    }

    public BasePart primaryPart() {
        if (primary != null && (primary.isDestroyed() || !primary.isDescendantOf(this))) primary = null;
        return primary;
    }

    public List<BasePart> parts() {
        List<BasePart> out = new ArrayList<>();
        for (Instance i : getDescendants()) if (i instanceof BasePart bp) out.add(bp);
        return out;
    }

    V3[] bounds() {
        V3 min = null, max = null;
        for (BasePart bp : parts()) {
            V3 c = bp.getCFrame().position(), h = bp.getSize().scale(0.5);
            V3[] b = {c.sub(h), c.add(h)};
            min = min == null ? b[0] : min.min(b[0]);
            max = max == null ? b[1] : max.max(b[1]);
        }
        return min == null ? null : new V3[]{min, max};
    }

    public CFrame getPivot() {
        BasePart pp = primaryPart();
        if (pp != null) return pp.getCFrame();
        V3[] b = bounds();
        return b == null ? CFrame.IDENTITY : CFrame.at(b[0].add(b[1]).scale(0.5));
    }

    public void pivotTo(CFrame target) {
        CFrame delta = target.mul(getPivot().inverse());
        for (BasePart bp : parts()) bp.setCFrame(delta.mul(bp.getCFrame()));
    }

    public void breakJoints() {
        for (Instance c : getChildren()) {
            if (c instanceof Humanoid h) h.setHealth(0);
        }
    }
}
