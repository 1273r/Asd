package com.example.robloxphysics.dm;

/** Plain containers: Folder and the storage-style services. */
public class Container extends Instance {
    public static final ClassInfo FOLDER = ClassInfo.define("Folder", Instance.CLASS, Container::new);
    public static final ClassInfo REPLICATED_STORAGE = ClassInfo.define("ReplicatedStorage", Instance.CLASS, null);
    public static final ClassInfo SERVER_SCRIPT_SERVICE = ClassInfo.define("ServerScriptService", Instance.CLASS, null);
    public static final ClassInfo SERVER_STORAGE = ClassInfo.define("ServerStorage", Instance.CLASS, null);
    public static final ClassInfo STARTER_PLAYER_SCRIPTS = ClassInfo.define("StarterPlayerScripts", Instance.CLASS, null);
    public static final ClassInfo PLAYER_SCRIPTS = ClassInfo.define("PlayerScripts", Instance.CLASS, null);

    public Container(DataModel dm, ClassInfo cls) {
        super(dm, cls);
    }
}
