package com.example.robloxphysics.dm;

import com.example.robloxphysics.rbx.RbxEnum;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Reflection metadata for a Roblox class: properties, methods and events. */
public final class ClassInfo {
    public static final Map<String, ClassInfo> BY_NAME = new LinkedHashMap<>();

    public enum PT { BOOL, NUMBER, INT, STRING, V3, CFRAME, COLOR3, BRICKCOLOR, ENUM, INSTANCE, ANY }

    public static final class Prop {
        public final String name;
        public final PT type;
        public final RbxEnum enumType;
        final Function<Instance, Object> getter;
        final BiConsumer<Instance, Object> setter;
        public String category = "Data";
        public boolean saved;
        public boolean hidden; // not shown in the Properties window
        public boolean serverOnly;

        Prop(String name, PT type, RbxEnum enumType, Function<Instance, Object> g, BiConsumer<Instance, Object> s) {
            this.name = name;
            this.type = type;
            this.enumType = enumType;
            this.getter = g;
            this.setter = s;
        }

        public boolean readOnly() { return setter == null; }

        public Object get(Instance i) { return getter.apply(i); }

        public void set(Instance i, Object v) {
            if (setter == null) throw new IllegalArgumentException("Unable to assign property " + name + ". Property is read only");
            setter.accept(i, v);
        }
    }

    /** Method implementation. Runs on the game thread unless {@link Method#luaSide}. Args exclude self. */
    public interface Impl {
        Varargs call(Instance self, Varargs args);
    }

    public static final class Method {
        public final String name;
        public final Impl impl;
        public final boolean luaSide;
        public LuaFunction fn; // cached Lua function, created by RbxLib

        Method(String name, Impl impl, boolean luaSide) {
            this.name = name;
            this.impl = impl;
            this.luaSide = luaSide;
        }
    }

    public final String name;
    public final ClassInfo sup;
    public final BiFunction<DataModel, ClassInfo, Instance> factory; // null = not creatable
    public String category = "Instance";
    private final LinkedHashMap<String, Prop> props = new LinkedHashMap<>();
    private final Map<String, Method> methods = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private Prop lastProp;

    private ClassInfo(String name, ClassInfo sup, BiFunction<DataModel, ClassInfo, Instance> factory) {
        this.name = name;
        this.sup = sup;
        this.factory = factory;
        BY_NAME.put(name, this);
    }

    public static ClassInfo define(String name, ClassInfo sup, BiFunction<DataModel, ClassInfo, Instance> factory) {
        return new ClassInfo(name, sup, factory);
    }

    @SuppressWarnings("unchecked")
    public <T extends Instance> ClassInfo prop(String name, PT type, Function<T, Object> g, BiConsumer<T, Object> s) {
        Prop p = new Prop(name, type, null, (Function<Instance, Object>) g, (BiConsumer<Instance, Object>) s);
        props.put(name, p);
        lastProp = p;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Instance> ClassInfo enumProp(String name, RbxEnum e, Function<T, Object> g, BiConsumer<T, Object> s) {
        Prop p = new Prop(name, PT.ENUM, e, (Function<Instance, Object>) g, (BiConsumer<Instance, Object>) s);
        props.put(name, p);
        lastProp = p;
        return this;
    }

    /** marks the last property as persisted in the place file */
    public ClassInfo saved() { lastProp.saved = true; return this; }

    public ClassInfo hidden() { lastProp.hidden = true; return this; }

    public ClassInfo cat(String c) { lastProp.category = c; return this; }

    public ClassInfo method(String name, Impl impl) {
        methods.put(name, new Method(name, impl, false));
        return this;
    }

    /** method that runs on the Lua side (may yield); must use rt.sync for game access */
    public ClassInfo luaMethod(String name, Impl impl) {
        methods.put(name, new Method(name, impl, true));
        return this;
    }

    public ClassInfo event(String name) {
        events.add(name);
        return this;
    }

    public Prop findProp(String n) {
        for (ClassInfo c = this; c != null; c = c.sup) {
            Prop p = c.props.get(n);
            if (p != null) return p;
        }
        return null;
    }

    public Method findMethod(String n) {
        for (ClassInfo c = this; c != null; c = c.sup) {
            Method m = c.methods.get(n);
            if (m != null) return m;
        }
        return null;
    }

    public boolean hasEvent(String n) {
        for (ClassInfo c = this; c != null; c = c.sup) if (c.events.contains(n)) return true;
        return false;
    }

    /** all properties, most-derived last so base class props (Name, Parent) come first */
    public List<Prop> allProps() {
        List<ClassInfo> chain = new ArrayList<>();
        for (ClassInfo c = this; c != null; c = c.sup) chain.add(0, c);
        LinkedHashMap<String, Prop> out = new LinkedHashMap<>();
        for (ClassInfo c : chain) out.putAll(c.props);
        return new ArrayList<>(out.values());
    }

    public List<String> allMethods() {
        List<String> out = new ArrayList<>();
        for (ClassInfo c = this; c != null; c = c.sup) out.addAll(c.methods.keySet());
        return out;
    }

    public boolean isA(String n) {
        for (ClassInfo c = this; c != null; c = c.sup) if (c.name.equals(n)) return true;
        return false;
    }
}
