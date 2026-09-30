package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.lua.Signal;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Script (server) and LocalScript (client). A script runs while it is Enabled and parented somewhere scripts
 * run: Scripts in Workspace/ServerScriptService on the server, LocalScripts under the local Player or its
 * character on the client.
 */
public class BaseScript extends Instance {
    public static final ClassInfo BASE = ClassInfo.define("LuaSourceContainer", Instance.CLASS, null)
            .prop("Source", PT.STRING, (Instance s) -> s instanceof BaseScript b ? b.source : ((ModuleScript) s).source,
                    (Instance s, Object v) -> {
                        if (s instanceof BaseScript b) b.source = (String) v;
                        else ((ModuleScript) s).setSource((String) v);
                        s.changed("Source");
                    }).saved().hidden();

    public static final ClassInfo SCRIPT_BASE = ClassInfo.define("BaseScript", BASE, null)
            .prop("Enabled", PT.BOOL, (BaseScript s) -> s.enabled, (BaseScript s, Object v) -> s.setEnabled((Boolean) v)).saved().cat("Behavior")
            .prop("Disabled", PT.BOOL, (BaseScript s) -> !s.enabled, (BaseScript s, Object v) -> s.setEnabled(!(Boolean) v)).hidden()
            .prop("Running", PT.BOOL, (BaseScript s) -> s.running, null).cat("Behavior");

    public static final ClassInfo SCRIPT = ClassInfo.define("Script", SCRIPT_BASE, BaseScript::new);
    public static final ClassInfo LOCAL_SCRIPT = ClassInfo.define("LocalScript", SCRIPT_BASE, BaseScript::new);

    public String source = "print(\"Hello world!\")\n";
    private boolean enabled = true;
    private volatile boolean running;
    /** connections made by this script (Lua side only) */
    private final List<Signal.Conn> conns = new ArrayList<>();

    public BaseScript(DataModel dm, ClassInfo cls) {
        super(dm, cls);
    }

    public boolean isLocal() { return cls == LOCAL_SCRIPT; }

    public boolean isRunning() { return running; }

    public void track(Signal.Conn c) { conns.add(c); }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        changed("Enabled");
        update();
    }

    boolean canRun() {
        if (!enabled || destroyed) return false;
        if (!isLocal()) return dm.server && (isInWorkspace() || isDescendantOf(dm.serverScriptService));
        if (dm.server) return false;
        Player lp = dm.players.localPlayer;
        if (lp == null) return false;
        if (isDescendantOf(lp)) return true;
        CharacterModel ch = lp.character();
        return ch != null && isDescendantOf(ch);
    }

    /** start or stop depending on where the script is */
    public void update() {
        boolean should = canRun();
        if (should && !running) start();
        else if (!should && running) stop();
    }

    @Override
    protected void onAncestryChanged() { update(); }

    @Override
    protected void onDestroyed() { if (running) stop(); }

    public void restart() {
        if (running) stop();
        if (canRun()) start();
    }

    void start() {
        running = true;
        changed("Running");
        String src = source;
        String chunk = getFullName();
        dm.rt.post(() -> {
            var env = dm.rt.newScriptEnv();
            env.set("script", lua());
            LuaValue fn;
            try {
                fn = dm.rt.compile(src, chunk, env);
            } catch (LuaError e) {
                dm.log.add(LogBuffer.ERROR, com.example.robloxphysics.lua.LuaRuntime.formatError(e.getMessage()));
                return;
            }
            dm.rt.spawn(fn, LuaValue.NONE, this);
        });
    }

    void stop() {
        running = false;
        changed("Running");
        dm.rt.post(() -> {
            dm.rt.killScript(this);
            for (Signal.Conn c : new ArrayList<>(conns)) c.disconnect();
            conns.clear();
        });
    }
}
