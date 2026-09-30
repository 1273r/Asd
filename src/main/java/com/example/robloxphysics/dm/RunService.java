package com.example.robloxphysics.dm;

import com.example.robloxphysics.lua.Signal;
import org.luaj.vm2.LuaValue;

public class RunService extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("RunService", Instance.CLASS, null)
            .method("IsServer", (self, a) -> LuaValue.valueOf(self.dm.server))
            .method("IsClient", (self, a) -> LuaValue.valueOf(!self.dm.server))
            .method("IsStudio", (self, a) -> LuaValue.TRUE)
            .method("IsRunning", (self, a) -> LuaValue.TRUE)
            .event("Heartbeat").event("Stepped").event("RenderStepped").event("PostSimulation");

    public final Signal heartbeat, stepped, renderStepped, postSimulation;

    public RunService(DataModel dm) {
        super(dm, CLASS);
        this.name = "Run Service";
        heartbeat = event("Heartbeat");
        stepped = event("Stepped");
        renderStepped = event("RenderStepped");
        postSimulation = event("PostSimulation");
    }
}
