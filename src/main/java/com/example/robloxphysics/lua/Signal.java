package com.example.robloxphysics.lua;

import com.example.robloxphysics.dm.BaseScript;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.List;

/** RBXScriptSignal. Connections are only touched on the Lua side. */
public final class Signal {
    public final LuaRuntime rt;
    public final String name;
    private final List<Conn> conns = new ArrayList<>();
    private volatile int count;

    public static final class Conn {
        public final Signal signal;
        final LuaValue fn;
        final BaseScript owner;
        final boolean once;
        final LuaRuntime.Task waiter;
        public boolean connected = true;

        Conn(Signal s, LuaValue fn, BaseScript owner, boolean once, LuaRuntime.Task waiter) {
            this.signal = s;
            this.fn = fn;
            this.owner = owner;
            this.once = once;
            this.waiter = waiter;
        }

        public void disconnect() { signal.disconnect(this); }
    }

    public Signal(LuaRuntime rt, String name) {
        this.rt = rt;
        this.name = name;
    }

    public boolean hasListeners() { return count > 0; }

    /** Fire with Java values (converted on the Lua side). Any thread. */
    public void fire(Object... args) {
        if (count == 0 || rt == null) return;
        rt.post(() -> fireNow(RbxLib.toLuaArgs(args)));
    }

    /** Fire with Lua values. Lua side. */
    public void fireNow(Varargs args) {
        if (count == 0) return;
        for (Conn c : new ArrayList<>(conns)) {
            if (!c.connected) continue;
            if (c.once) disconnect(c);
            if (c.waiter != null) rt.resumeLater(c.waiter, args);
            else rt.spawn(c.fn, args, c.owner);
        }
    }

    public Conn connect(LuaValue fn, boolean once) {
        BaseScript owner = rt.currentScript();
        Conn c = new Conn(this, fn, owner, once, null);
        conns.add(c);
        count = conns.size();
        if (owner != null) owner.track(c);
        return c;
    }

    /** Signal:Wait() - yields current thread until the next fire. Lua side. */
    public Varargs await() {
        LuaRuntime.Task t = rt.currentTask();
        if (t == null) throw new org.luaj.vm2.LuaError("attempt to yield outside of a script thread");
        Conn c = new Conn(this, LuaValue.NIL, t.owner, true, t);
        conns.add(c);
        count = conns.size();
        return rt.park();
    }

    void disconnect(Conn c) {
        c.connected = false;
        conns.remove(c);
        count = conns.size();
    }

    public void disconnectAll() {
        for (Conn c : new ArrayList<>(conns)) c.connected = false;
        conns.clear();
        count = 0;
    }
}
