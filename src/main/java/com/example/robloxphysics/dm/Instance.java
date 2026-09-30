package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.LuaRuntime;
import com.example.robloxphysics.lua.RbxLib;
import com.example.robloxphysics.lua.Signal;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of the Roblox object tree. All tree/property access happens on the game thread
 * (Lua reaches it through {@link LuaRuntime#sync}).
 */
public class Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Instance", null, null)
            .prop("Name", PT.STRING, (Instance i) -> i.name, (Instance i, Object v) -> i.setName((String) v)).saved()
            .prop("ClassName", PT.STRING, (Instance i) -> i.cls.name, null)
            .prop("Parent", PT.INSTANCE, (Instance i) -> i.parent, (Instance i, Object v) -> i.setParent((Instance) v))
            .prop("Archivable", PT.BOOL, (Instance i) -> i.archivable, (Instance i, Object v) -> i.archivable = (Boolean) v).saved()
            .method("FindFirstChild", (self, a) -> RbxLib.toLua(self.findFirstChild(a.checkjstring(1), a.optboolean(2, false))))
            .method("FindFirstChildOfClass", (self, a) -> {
                String c = a.checkjstring(1);
                for (Instance ch : self.getChildren()) if (ch.cls.name.equals(c)) return RbxLib.toLua(ch);
                return LuaValue.NIL;
            })
            .method("FindFirstChildWhichIsA", (self, a) -> {
                String c = a.checkjstring(1);
                boolean rec = a.optboolean(2, false);
                for (Instance ch : rec ? self.getDescendants() : self.getChildren()) if (ch.cls.isA(c)) return RbxLib.toLua(ch);
                return LuaValue.NIL;
            })
            .method("FindFirstAncestor", (self, a) -> {
                String n = a.checkjstring(1);
                for (Instance p = self.parent; p != null; p = p.parent) if (p.name.equals(n)) return RbxLib.toLua(p);
                return LuaValue.NIL;
            })
            .method("FindFirstAncestorOfClass", (self, a) -> {
                String n = a.checkjstring(1);
                for (Instance p = self.parent; p != null; p = p.parent) if (p.cls.name.equals(n)) return RbxLib.toLua(p);
                return LuaValue.NIL;
            })
            .method("FindFirstAncestorWhichIsA", (self, a) -> {
                String n = a.checkjstring(1);
                for (Instance p = self.parent; p != null; p = p.parent) if (p.cls.isA(n)) return RbxLib.toLua(p);
                return LuaValue.NIL;
            })
            .method("GetChildren", (self, a) -> RbxLib.list(self.getChildren()))
            .method("GetDescendants", (self, a) -> RbxLib.list(self.getDescendants()))
            .method("IsA", (self, a) -> LuaValue.valueOf(self.cls.isA(a.checkjstring(1))))
            .method("IsDescendantOf", (self, a) -> LuaValue.valueOf(self.isDescendantOf(RbxLib.optInstance(a.arg(1)))))
            .method("IsAncestorOf", (self, a) -> {
                Instance o = RbxLib.optInstance(a.arg(1));
                return LuaValue.valueOf(o != null && o.isDescendantOf(self));
            })
            .method("GetFullName", (self, a) -> LuaValue.valueOf(self.getFullName()))
            .method("Destroy", (self, a) -> {
                self.destroy();
                return LuaValue.NONE;
            })
            .method("Remove", (self, a) -> {
                self.setParent(null);
                return LuaValue.NONE;
            })
            .method("ClearAllChildren", (self, a) -> {
                for (Instance c : new ArrayList<>(self.getChildren())) c.destroy();
                return LuaValue.NONE;
            })
            .method("Clone", (self, a) -> RbxLib.toLua(self.cloneTree()))
            .method("GetAttribute", (self, a) -> RbxLib.toLua(self.attributes.get(a.checkjstring(1))))
            .method("SetAttribute", (self, a) -> {
                String k = a.checkjstring(1);
                Object v = RbxLib.fromLuaAny(a.arg(2));
                if (v == null) self.attributes.remove(k);
                else self.attributes.put(k, v);
                Signal s = self.attrSignals.get(k);
                if (s != null) s.fire();
                return LuaValue.NONE;
            })
            .method("GetAttributes", (self, a) -> {
                LuaTable t = new LuaTable();
                self.attributes.forEach((k, v) -> t.set(k, RbxLib.toLua(v)));
                return t;
            })
            .method("GetAttributeChangedSignal", (self, a) -> RbxLib.toLua(
                    self.attrSignals.computeIfAbsent(a.checkjstring(1), k -> new Signal(self.dm.rt, k))))
            .method("GetPropertyChangedSignal", (self, a) -> {
                String p = a.checkjstring(1);
                if (self.cls.findProp(p) == null) throw new LuaError(p + " is not a valid property name.");
                return RbxLib.toLua(self.propSignals.computeIfAbsent(p, k -> new Signal(self.dm.rt, k)));
            })
            .luaMethod("WaitForChild", (self, a) -> {
                String n = a.checkjstring(1);
                double timeout = a.optdouble(2, -1);
                LuaRuntime rt = self.dm.rt;
                double start = rt.now();
                boolean warned = false;
                while (true) {
                    Instance c = rt.sync(() -> self.findFirstChild(n, false));
                    if (c != null) return RbxLib.toLua(c);
                    double el = rt.now() - start;
                    if (timeout >= 0 && el >= timeout) return LuaValue.NIL;
                    if (!warned && timeout < 0 && el > 5) {
                        warned = true;
                        rt.log.add(com.example.robloxphysics.lua.LogBuffer.WARN,
                                "Infinite yield possible on '" + rt.sync(self::getFullName) + ":WaitForChild(\"" + n + "\")'");
                    }
                    rt.waitSeconds(0.03);
                }
            })
            .event("Changed").event("ChildAdded").event("ChildRemoved").event("DescendantAdded")
            .event("DescendantRemoving").event("AncestryChanged").event("Destroying");

    public final DataModel dm;
    public final ClassInfo cls;
    public String id;
    protected String name;
    protected Instance parent;
    protected final List<Instance> children = new ArrayList<>();
    protected boolean archivable = true;
    protected boolean destroyed;
    protected boolean parentLocked;
    public final Map<String, Object> attributes = new LinkedHashMap<>();
    final Map<String, Signal> propSignals = new HashMap<>();
    final Map<String, Signal> attrSignals = new HashMap<>();
    private final Map<String, Signal> events = new HashMap<>();
    private volatile LuaUserdata lua;

    public Instance(DataModel dm, ClassInfo cls) {
        this.dm = dm;
        this.cls = cls;
        this.name = cls.name;
        this.id = dm == null ? "i0" : dm.newId();
    }

    // ------------------------------------------------------------ Lua identity

    public LuaUserdata lua() {
        LuaUserdata u = lua;
        if (u == null) {
            synchronized (this) {
                if (lua == null) lua = RbxLib.wrapInstance(this);
                u = lua;
            }
        }
        return u;
    }

    public Signal event(String n) {
        synchronized (events) {
            return events.computeIfAbsent(n, k -> new Signal(dm.rt, k));
        }
    }

    protected Signal existingEvent(String n) {
        synchronized (events) {
            return events.get(n);
        }
    }

    protected void fireEvent(String n, Object... args) {
        Signal s = existingEvent(n);
        if (s != null) s.fire(args);
    }

    // ------------------------------------------------------------ tree

    public String getName() { return name; }

    public void setName(String n) {
        if (n == null) throw new IllegalArgumentException("Name cannot be nil");
        if (n.equals(name)) return;
        name = n;
        changed("Name");
    }

    public Instance getParent() { return parent; }

    public List<Instance> getChildren() { return children; }

    public boolean isDestroyed() { return destroyed; }

    public List<Instance> getDescendants() {
        List<Instance> out = new ArrayList<>();
        collect(this, out);
        return out;
    }

    private static void collect(Instance i, List<Instance> out) {
        for (Instance c : i.getChildren()) {
            out.add(c);
            collect(c, out);
        }
    }

    public Instance findFirstChild(String n, boolean recursive) {
        for (Instance c : getChildren()) if (c.name.equals(n)) return c;
        if (recursive) {
            for (Instance c : getChildren()) {
                Instance f = c.findFirstChild(n, true);
                if (f != null) return f;
            }
        }
        return null;
    }

    public boolean isDescendantOf(Instance a) {
        if (a == null) return false;
        for (Instance p = parent; p != null; p = p.parent) if (p == a) return true;
        return false;
    }

    public String getFullName() {
        if (parent == null || parent instanceof DataModel.Root) return name;
        return parent.getFullName() + "." + name;
    }

    public void setParent(Instance p) {
        if (p == parent) return;
        if (destroyed) throw new IllegalArgumentException("The Parent property of " + name + " is locked, current parent: NULL, new parent " + (p == null ? "NULL" : p.name));
        if (parentLocked) throw new IllegalArgumentException("The Parent property of " + name + " is locked");
        if (p == this || (p != null && p.isDescendantOf(this))) throw new IllegalArgumentException("Attempt to set " + getFullName() + " as its own parent");
        if (p != null && p.dm != dm) throw new IllegalArgumentException("Cannot parent across script contexts");
        if (p != null && !p.canAcceptChild(this)) throw new IllegalArgumentException(cls.name + " cannot be parented to " + p.cls.name);
        Instance old = parent;
        if (old != null) {
            old.children.remove(this);
            old.fireEvent("ChildRemoved", this);
            for (Instance a = old; a != null; a = a.parent) a.fireEvent("DescendantRemoving", this);
        }
        parent = p;
        if (p != null) {
            p.children.add(this);
            p.fireEvent("ChildAdded", this);
            for (Instance a = p; a != null; a = a.parent) a.fireEvent("DescendantAdded", this);
        }
        changed("Parent");
        ancestryChanged(this, p);
        dm.markDirty(this);
    }

    /** Called for this and every descendant when the ancestry of {@code root} changes. */
    private void ancestryChanged(Instance root, Instance newParent) {
        fireEvent("AncestryChanged", root, newParent);
        onAncestryChanged();
        for (Instance c : new ArrayList<>(children)) c.ancestryChanged(root, newParent);
    }

    protected void onAncestryChanged() {}

    protected boolean canAcceptChild(Instance c) { return true; }

    /** Internal parent change without event spam (used when building the tree). */
    public void attach(Instance p) {
        if (parent != null) parent.children.remove(this);
        parent = p;
        if (p != null) p.children.add(this);
    }

    public void destroy() {
        if (destroyed) return;
        if (parentLocked) throw new IllegalArgumentException("Cannot destroy " + cls.name);
        fireEvent("Destroying");
        for (Instance c : new ArrayList<>(children)) {
            try { c.destroy(); } catch (IllegalArgumentException ignored) {}
        }
        if (parent != null) setParent(null);
        destroyed = true;
        onDestroyed();
    }

    protected void onDestroyed() {}

    public boolean isInWorkspace() {
        return dm.workspace != null && (this == dm.workspace || isDescendantOf(dm.workspace));
    }

    // ------------------------------------------------------------ properties

    public Object get(String prop) {
        ClassInfo.Prop p = cls.findProp(prop);
        if (p == null) throw new IllegalArgumentException(prop + " is not a valid member of " + cls.name);
        return p.get(this);
    }

    public void set(String prop, Object v) {
        ClassInfo.Prop p = cls.findProp(prop);
        if (p == null) throw new IllegalArgumentException(prop + " is not a valid member of " + cls.name);
        p.set(this, v);
    }

    /** Fire Changed + GetPropertyChangedSignal. */
    public void changed(String prop) {
        fireEvent("Changed", prop);
        Signal s = propSignals.get(prop);
        if (s != null) s.fire();
        dm.markDirty(this);
    }

    // ------------------------------------------------------------ cloning

    public Instance cloneTree() {
        if (!archivable) return null;
        if (cls.factory == null) throw new IllegalArgumentException(cls.name + " cannot be cloned");
        Instance copy = cls.factory.apply(dm, cls);
        copyPropsTo(copy);
        for (Instance c : children) {
            if (!c.archivable || c.cls.factory == null) continue;
            Instance cc = c.cloneTree();
            if (cc != null) cc.setParent(copy);
        }
        return copy;
    }

    protected void copyPropsTo(Instance copy) {
        for (ClassInfo.Prop p : cls.allProps()) {
            if (p.readOnly() || p.name.equals("Parent") || p.type == PT.INSTANCE) continue;
            try { p.set(copy, p.get(this)); } catch (RuntimeException ignored) {}
        }
        copy.attributes.putAll(attributes);
    }

    @Override
    public String toString() { return name; }
}
