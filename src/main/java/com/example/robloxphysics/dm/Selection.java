package com.example.robloxphysics.dm;

import com.example.robloxphysics.lua.RbxLib;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

/** Studio selection, usable from the command bar: game:GetService("Selection"):Get() */
public class Selection extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Selection", Instance.CLASS, null)
            .method("Get", (self, a) -> RbxLib.list(((Selection) self).selected))
            .method("Set", (self, a) -> {
                Selection s = (Selection) self;
                s.selected.clear();
                LuaValue t = a.checktable(1);
                for (int i = 1; i <= t.length(); i++) {
                    Instance inst = RbxLib.optInstance(t.get(i));
                    if (inst != null) s.selected.add(inst);
                }
                s.fireEvent("SelectionChanged");
                return LuaValue.NONE;
            })
            .event("SelectionChanged");

    public final List<Instance> selected = new ArrayList<>();

    public Selection(DataModel dm) {
        super(dm, CLASS);
        this.name = "Selection";
    }
}
