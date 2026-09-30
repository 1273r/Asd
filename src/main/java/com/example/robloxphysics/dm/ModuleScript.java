package com.example.robloxphysics.dm;

/** ModuleScript: runs once on first require() and caches the returned value per script context. */
public class ModuleScript extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("ModuleScript", BaseScript.BASE, ModuleScript::new);

    public String source = "local module = {}\n\nreturn module\n";

    public ModuleScript(DataModel dm, ClassInfo cls) {
        super(dm, cls);
    }

    void setSource(String s) {
        source = s;
        dm.moduleCache.remove(this);
    }
}
