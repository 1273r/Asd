package com.example.robloxphysics.lua;

import com.example.robloxphysics.dm.ClassInfo;
import com.example.robloxphysics.dm.DataModel;
import com.example.robloxphysics.dm.Instance;
import com.example.robloxphysics.dm.ModuleScript;
import com.example.robloxphysics.rbx.BrickColor;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Color3;
import com.example.robloxphysics.rbx.RbxEnum;
import com.example.robloxphysics.rbx.V3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/** Roblox globals, value types and Instance bindings for LuaJ. */
public final class RbxLib {
    private RbxLib() {}

    // =====================================================================================
    //  small helpers for defining functions
    // =====================================================================================

    interface VF { Varargs call(Varargs a); }

    static LuaValue fn(VF f) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) { return f.call(a); }
        };
    }

    // =====================================================================================
    //  metatables
    // =====================================================================================

    private static LuaTable meta(String typeName) {
        LuaTable mt = new LuaTable();
        mt.set("__type", typeName);
        mt.set("__metatable", "The metatable is locked");
        return mt;
    }

    /** __index that looks up fields via a function, then methods in a table */
    private static <T> void indexer(LuaTable mt, Class<T> cls, Map<String, Function<T, Object>> fields, LuaTable methods) {
        mt.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                T v = cls.cast(self.checkuserdata(cls));
                String k = key.checkjstring();
                Function<T, Object> f = fields.get(k);
                if (f != null) return toLua(f.apply(v));
                LuaValue m = methods.rawget(k);
                if (!m.isnil()) return m;
                throw new LuaError(k + " is not a valid member of " + mt.get("__type").tojstring());
            }
        });
    }

    static final LuaTable V3_MT = meta("Vector3");
    static final LuaTable CF_MT = meta("CFrame");
    static final LuaTable C3_MT = meta("Color3");
    static final LuaTable BC_MT = meta("BrickColor");
    static final LuaTable ENUMITEM_MT = meta("EnumItem");
    static final LuaTable ENUM_MT = meta("Enum");
    static final LuaTable ENUMS_MT = meta("Enums");
    static final LuaTable SIGNAL_MT = meta("RBXScriptSignal");
    static final LuaTable CONN_MT = meta("RBXScriptConnection");
    static final LuaTable INSTANCE_MT = meta("Instance");

    static {
        // ---------------- Vector3
        LuaTable vm = new LuaTable();
        vm.set("Dot", fn(a -> LuaValue.valueOf(checkV3(a.arg(1)).dot(checkV3(a.arg(2))))));
        vm.set("Cross", fn(a -> toLua(checkV3(a.arg(1)).cross(checkV3(a.arg(2))))));
        vm.set("Lerp", fn(a -> toLua(checkV3(a.arg(1)).lerp(checkV3(a.arg(2)), a.checkdouble(3)))));
        vm.set("FuzzyEq", fn(a -> LuaValue.valueOf(checkV3(a.arg(1)).fuzzyEq(checkV3(a.arg(2)), a.optdouble(3, 1e-5)))));
        vm.set("Abs", fn(a -> toLua(checkV3(a.arg(1)).abs())));
        vm.set("Min", fn(a -> toLua(checkV3(a.arg(1)).min(checkV3(a.arg(2))))));
        vm.set("Max", fn(a -> toLua(checkV3(a.arg(1)).max(checkV3(a.arg(2))))));
        vm.set("Floor", fn(a -> {
            V3 v = checkV3(a.arg(1));
            return toLua(new V3(Math.floor(v.x()), Math.floor(v.y()), Math.floor(v.z())));
        }));
        vm.set("Ceil", fn(a -> {
            V3 v = checkV3(a.arg(1));
            return toLua(new V3(Math.ceil(v.x()), Math.ceil(v.y()), Math.ceil(v.z())));
        }));
        vm.set("Angle", fn(a -> {
            V3 x = checkV3(a.arg(1)).unit(), y = checkV3(a.arg(2)).unit();
            return LuaValue.valueOf(Math.acos(Math.max(-1, Math.min(1, x.dot(y)))));
        }));
        indexer(V3_MT, V3.class, Map.of(
                "X", V3::x, "Y", V3::y, "Z", V3::z,
                "x", V3::x, "y", V3::y, "z", V3::z,
                "Magnitude", V3::length, "magnitude", V3::length,
                "Unit", V3::unit, "unit", V3::unit), vm);
        V3_MT.set("__add", arith((x, y) -> x.add(y)));
        V3_MT.set("__sub", arith((x, y) -> x.sub(y)));
        V3_MT.set("__mul", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) {
                if (a.isnumber()) return toLua(checkV3(b).scale(a.todouble()));
                if (b.isnumber()) return toLua(checkV3(a).scale(b.todouble()));
                return toLua(checkV3(a).mul(checkV3(b)));
            }
        });
        V3_MT.set("__div", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) {
                if (b.isnumber()) return toLua(checkV3(a).scale(1.0 / b.todouble()));
                if (a.isnumber()) return toLua(new V3(a.todouble(), a.todouble(), a.todouble()).div(checkV3(b)));
                return toLua(checkV3(a).div(checkV3(b)));
            }
        });
        V3_MT.set("__unm", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue a) { return toLua(checkV3(a).neg()); }
        });
        V3_MT.set("__tostring", tostr(V3.class, V3::toString));

        // ---------------- CFrame
        LuaTable cm = new LuaTable();
        cm.set("Inverse", fn(a -> toLua(checkCFrame(a.arg(1)).inverse())));
        cm.set("Lerp", fn(a -> toLua(checkCFrame(a.arg(1)).lerp(checkCFrame(a.arg(2)), a.checkdouble(3)))));
        cm.set("ToWorldSpace", fn(a -> toLua(checkCFrame(a.arg(1)).toWorldSpace(checkCFrame(a.arg(2))))));
        cm.set("ToObjectSpace", fn(a -> toLua(checkCFrame(a.arg(1)).toObjectSpace(checkCFrame(a.arg(2))))));
        cm.set("PointToWorldSpace", fn(a -> toLua(checkCFrame(a.arg(1)).pointToWorld(checkV3(a.arg(2))))));
        cm.set("PointToObjectSpace", fn(a -> toLua(checkCFrame(a.arg(1)).pointToObject(checkV3(a.arg(2))))));
        cm.set("VectorToWorldSpace", fn(a -> toLua(checkCFrame(a.arg(1)).vectorToWorld(checkV3(a.arg(2))))));
        cm.set("VectorToObjectSpace", fn(a -> toLua(checkCFrame(a.arg(1)).vectorToObject(checkV3(a.arg(2))))));
        cm.set("ToEulerAnglesXYZ", fn(a -> nums(checkCFrame(a.arg(1)).toEulerXYZ())));
        cm.set("ToEulerAnglesYXZ", fn(a -> nums(checkCFrame(a.arg(1)).toOrientation())));
        cm.set("ToOrientation", fn(a -> nums(checkCFrame(a.arg(1)).toOrientation())));
        cm.set("GetComponents", fn(a -> nums(checkCFrame(a.arg(1)).components())));
        cm.set("components", cm.get("GetComponents"));
        cm.set("FuzzyEq", fn(a -> {
            double[] x = checkCFrame(a.arg(1)).components(), y = checkCFrame(a.arg(2)).components();
            double eps = a.optdouble(3, 1e-5);
            for (int i = 0; i < 12; i++) if (Math.abs(x[i] - y[i]) > eps) return LuaValue.FALSE;
            return LuaValue.TRUE;
        }));
        indexer(CF_MT, CFrame.class, Map.ofEntries(
                Map.entry("Position", CFrame::position), Map.entry("p", CFrame::position),
                Map.entry("X", (CFrame c) -> c.x), Map.entry("Y", (CFrame c) -> c.y), Map.entry("Z", (CFrame c) -> c.z),
                Map.entry("LookVector", CFrame::lookVector), Map.entry("lookVector", CFrame::lookVector),
                Map.entry("RightVector", CFrame::rightVector), Map.entry("UpVector", CFrame::upVector),
                Map.entry("XVector", CFrame::rightVector), Map.entry("YVector", CFrame::upVector),
                Map.entry("ZVector", (CFrame c) -> c.lookVector().neg()),
                Map.entry("Rotation", CFrame::rotation)), cm);
        CF_MT.set("__mul", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) {
                CFrame c = checkCFrame(a);
                if (b.touserdata() instanceof V3 v) return toLua(c.pointToWorld(v));
                return toLua(c.mul(checkCFrame(b)));
            }
        });
        CF_MT.set("__add", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) { return toLua(checkCFrame(a).add(checkV3(b))); }
        });
        CF_MT.set("__sub", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) { return toLua(checkCFrame(a).add(checkV3(b).neg())); }
        });
        CF_MT.set("__tostring", tostr(CFrame.class, CFrame::toString));

        // ---------------- Color3
        LuaTable col = new LuaTable();
        col.set("Lerp", fn(a -> toLua(checkColor3(a.arg(1)).lerp(checkColor3(a.arg(2)), a.checkdouble(3)))));
        col.set("ToHSV", fn(a -> nums(checkColor3(a.arg(1)).toHSV())));
        col.set("ToHex", fn(a -> LuaValue.valueOf(checkColor3(a.arg(1)).toHex().toLowerCase())));
        indexer(C3_MT, Color3.class, Map.of("R", Color3::r, "G", Color3::g, "B", Color3::b,
                "r", Color3::r, "g", Color3::g, "b", Color3::b), col);
        C3_MT.set("__tostring", tostr(Color3.class, Color3::toString));

        // ---------------- BrickColor
        indexer(BC_MT, BrickColor.class, Map.of("Name", BrickColor::name, "Number", b -> (double) b.number(),
                "Color", BrickColor::color, "r", b -> b.color().r(), "g", b -> b.color().g(), "b", b -> b.color().b()), new LuaTable());
        BC_MT.set("__tostring", tostr(BrickColor.class, BrickColor::name));

        // ---------------- Enums
        indexer(ENUMITEM_MT, RbxEnum.Item.class, Map.of("Name", RbxEnum.Item::name, "Value", i -> (double) i.value(),
                "EnumType", i -> i.type()), new LuaTable());
        ENUMITEM_MT.set("__tostring", tostr(RbxEnum.Item.class, RbxEnum.Item::toString));
        ENUM_MT.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                RbxEnum e = (RbxEnum) self.checkuserdata(RbxEnum.class);
                String k = key.checkjstring();
                if (k.equals("GetEnumItems")) {
                    return fn(a -> {
                        LuaTable t = new LuaTable();
                        int i = 1;
                        for (RbxEnum.Item it : e.items) t.set(i++, toLua(it));
                        return t;
                    });
                }
                RbxEnum.Item it = e.get(k);
                if (it == null) throw new LuaError(k + " is not a valid member of \"Enum." + e.name + "\"");
                return toLua(it);
            }
        });
        ENUM_MT.set("__tostring", tostr(RbxEnum.class, e -> e.name));
        ENUMS_MT.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                String k = key.checkjstring();
                if (k.equals("GetEnums")) {
                    return fn(a -> {
                        LuaTable t = new LuaTable();
                        int i = 1;
                        for (RbxEnum e : RbxEnum.ALL.values()) t.set(i++, new LuaUserdata(e, ENUM_MT));
                        return t;
                    });
                }
                RbxEnum e = RbxEnum.ALL.get(k);
                if (e == null) throw new LuaError(k + " is not a valid member of \"Enums\"");
                return new LuaUserdata(e, ENUM_MT);
            }
        });
        ENUMS_MT.set("__tostring", fn(a -> LuaValue.valueOf("Enums")));

        // ---------------- Signals
        LuaTable sm = new LuaTable();
        sm.set("Connect", fn(a -> connUd(checkSignal(a.arg(1)).connect(a.checkfunction(2), false))));
        sm.set("ConnectParallel", sm.get("Connect"));
        sm.set("Once", fn(a -> connUd(checkSignal(a.arg(1)).connect(a.checkfunction(2), true))));
        sm.set("Wait", fn(a -> checkSignal(a.arg(1)).await()));
        sm.set("connect", sm.get("Connect"));
        sm.set("wait", sm.get("Wait"));
        indexer(SIGNAL_MT, Signal.class, Map.of(), sm);
        SIGNAL_MT.set("__tostring", tostr(Signal.class, s -> "Signal " + s.name));
        LuaTable cn = new LuaTable();
        cn.set("Disconnect", fn(a -> {
            ((Signal.Conn) a.arg(1).checkuserdata(Signal.Conn.class)).disconnect();
            return LuaValue.NONE;
        }));
        cn.set("disconnect", cn.get("Disconnect"));
        indexer(CONN_MT, Signal.Conn.class, Map.of("Connected", c -> c.connected), cn);
        CONN_MT.set("__tostring", fn(a -> LuaValue.valueOf("Connection")));

        // ---------------- Instance
        INSTANCE_MT.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) { return instIndex(self, key); }
        });
        INSTANCE_MT.set("__newindex", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key, LuaValue value) {
                instNewIndex(self, key, value);
                return NONE;
            }
        });
        INSTANCE_MT.set("__tostring", tostr(Instance.class, Instance::getName));
    }

    private interface V3Op { V3 op(V3 a, V3 b); }

    private static LuaValue arith(V3Op op) {
        return new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue a, LuaValue b) { return toLua(op.op(checkV3(a), checkV3(b))); }
        };
    }

    private static <T> LuaValue tostr(Class<T> c, Function<T, String> f) {
        return new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue a) { return LuaValue.valueOf(f.apply(c.cast(a.checkuserdata(c)))); }
        };
    }

    private static Varargs nums(double[] d) {
        LuaValue[] v = new LuaValue[d.length];
        for (int i = 0; i < d.length; i++) v[i] = LuaValue.valueOf(d[i]);
        return LuaValue.varargsOf(v);
    }

    // =====================================================================================
    //  Instances
    // =====================================================================================

    public static LuaUserdata wrapInstance(Instance i) { return new LuaUserdata(i, INSTANCE_MT); }

    static LuaValue methodFn(ClassInfo.Method m) {
        LuaValue f = m.fn;
        if (f != null) return f;
        m.fn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                if (!(a.arg1().touserdata() instanceof Instance inst)) {
                    throw new LuaError("Expected ':' not '.' calling member function " + m.name);
                }
                Varargs rest = a.subargs(2);
                if (m.luaSide) return m.impl.call(inst, rest);
                return inst.dm.rt.sync(() -> m.impl.call(inst, rest));
            }
        };
        return m.fn;
    }

    static LuaValue instIndex(LuaValue self, LuaValue key) {
        Instance inst = (Instance) self.checkuserdata(Instance.class);
        String k = key.checkjstring();
        ClassInfo.Method m = inst.cls.findMethod(k);
        if (m != null) return methodFn(m);
        LuaRuntime rt = inst.dm.rt;
        ClassInfo.Prop p = inst.cls.findProp(k);
        if (p != null) return rt.sync(() -> toLua(p.get(inst)));
        if (inst.cls.hasEvent(k)) return toLua(inst.event(k));
        Instance c = rt.sync(() -> inst.findFirstChild(k, false));
        if (c != null) return c.lua();
        String full = rt.sync(inst::getFullName);
        throw new LuaError(k + " is not a valid member of " + inst.cls.name + " \"" + full + "\"");
    }

    static void instNewIndex(LuaValue self, LuaValue key, LuaValue value) {
        Instance inst = (Instance) self.checkuserdata(Instance.class);
        String k = key.checkjstring();
        ClassInfo.Prop p = inst.cls.findProp(k);
        if (p == null) {
            throw new LuaError(k + " is not a valid member of " + inst.cls.name + " \"" + inst.dm.rt.sync(inst::getFullName) + "\"");
        }
        if (p.readOnly()) throw new LuaError("Unable to assign property " + k + ". Property is read only");
        Object v = fromLua(value, p);
        inst.dm.rt.syncRun(() -> p.set(inst, v));
    }

    // =====================================================================================
    //  conversion
    // =====================================================================================

    public static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof LuaValue v) return v;
        if (o instanceof Boolean b) return LuaValue.valueOf(b);
        if (o instanceof Number n) return LuaValue.valueOf(n.doubleValue());
        if (o instanceof String s) return LuaValue.valueOf(s);
        if (o instanceof Instance i) return i.lua();
        if (o instanceof V3 v) return new LuaUserdata(v, V3_MT);
        if (o instanceof CFrame c) return new LuaUserdata(c, CF_MT);
        if (o instanceof Color3 c) return new LuaUserdata(c, C3_MT);
        if (o instanceof BrickColor b) return new LuaUserdata(b, BC_MT);
        if (o instanceof RbxEnum.Item e) return new LuaUserdata(e, ENUMITEM_MT);
        if (o instanceof RbxEnum e) return new LuaUserdata(e, ENUM_MT);
        if (o instanceof Signal s) return new LuaUserdata(s, SIGNAL_MT);
        if (o instanceof Collection<?> c) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (Object x : c) t.set(i++, toLua(x));
            return t;
        }
        return LuaValue.valueOf(o.toString());
    }

    public static Varargs toLuaArgs(Object[] args) {
        LuaValue[] v = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) v[i] = toLua(args[i]);
        return LuaValue.varargsOf(v);
    }

    static LuaValue connUd(Signal.Conn c) { return new LuaUserdata(c, CONN_MT); }

    public static LuaTable list(Collection<? extends Instance> items) {
        LuaTable t = new LuaTable();
        int i = 1;
        for (Instance x : items) t.set(i++, x.lua());
        return t;
    }

    private static String typeName(LuaValue v) {
        if (v.isuserdata()) {
            LuaValue mt = v.getmetatable();
            if (mt != null && mt.istable()) {
                LuaValue t = ((LuaTable) mt).rawget("__type");
                if (t.isstring()) return t.tojstring();
            }
            return "userdata";
        }
        return v.typename();
    }

    private static <T> T check(LuaValue v, Class<T> c, String name) {
        Object o = v.touserdata();
        if (c.isInstance(o)) return c.cast(o);
        throw new LuaError(name + " expected, got " + typeName(v));
    }

    public static V3 checkV3(LuaValue v) { return check(v, V3.class, "Vector3"); }

    public static CFrame checkCFrame(LuaValue v) { return check(v, CFrame.class, "CFrame"); }

    public static Color3 checkColor3(LuaValue v) { return check(v, Color3.class, "Color3"); }

    static Signal checkSignal(LuaValue v) { return check(v, Signal.class, "RBXScriptSignal"); }

    public static Instance checkInstance(LuaValue v) { return check(v, Instance.class, "Instance"); }

    public static Instance optInstance(LuaValue v) {
        return v.touserdata() instanceof Instance i ? i : null;
    }

    public static RbxEnum.Item checkEnum(LuaValue v, RbxEnum e) {
        if (v.touserdata() instanceof RbxEnum.Item it && it.type() == e) return it;
        if (v.isstring() || v.isnumber()) {
            RbxEnum.Item it = e.parse(v.tojstring());
            if (it != null) return it;
        }
        throw new LuaError("Enum." + e.name + " expected, got " + typeName(v));
    }

    /** property assignment conversion */
    public static Object fromLua(LuaValue v, ClassInfo.Prop p) {
        switch (p.type) {
            case BOOL:
                if (!v.isboolean()) throw new LuaError("Unable to assign property " + p.name + ". bool expected, got " + typeName(v));
                return v.toboolean();
            case NUMBER:
            case INT:
                if (!v.isnumber()) throw new LuaError("Unable to assign property " + p.name + ". number expected, got " + typeName(v));
                return v.todouble();
            case STRING:
                if (!v.isstring()) throw new LuaError("Unable to assign property " + p.name + ". string expected, got " + typeName(v));
                return v.tojstring();
            case V3: return checkV3(v);
            case CFRAME: return checkCFrame(v);
            case COLOR3: return checkColor3(v);
            case BRICKCOLOR:
                if (v.touserdata() instanceof BrickColor b) return b;
                if (v.isstring()) return BrickColor.byName(v.tojstring());
                throw new LuaError("BrickColor expected, got " + typeName(v));
            case ENUM: return checkEnum(v, p.enumType);
            case INSTANCE:
                if (v.isnil()) return null;
                return checkInstance(v);
            default: return fromLuaAny(v);
        }
    }

    public static Object fromLuaAny(LuaValue v) {
        if (v.isnil()) return null;
        if (v.isboolean()) return v.toboolean();
        if (v.type() == LuaValue.TNUMBER) return v.todouble();
        if (v.isstring()) return v.tojstring();
        if (v.isuserdata()) return v.touserdata();
        throw new LuaError(typeName(v) + " is not a supported attribute type");
    }

    /** tostring() honoring __tostring */
    public static String tostring(LuaValue v) {
        LuaValue mt = v.getmetatable();
        if (mt != null) {
            LuaValue ts = mt.get("__tostring");
            if (ts.isfunction()) return ts.call(v).tojstring();
        }
        return v.tojstring();
    }

    // =====================================================================================
    //  globals
    // =====================================================================================

    public static void install(LuaRuntime rt, DataModel dm) {
        LuaTable g = rt.globals;
        LuaValue game = dm.game.lua();
        g.set("game", game);
        g.set("Game", game);
        g.set("workspace", dm.workspace.lua());
        g.set("Workspace", dm.workspace.lua());
        g.set("shared", new LuaTable());

        g.set("print", fn(a -> {
            rt.log.add(LogBuffer.OUTPUT, join(a));
            return LuaValue.NONE;
        }));
        g.set("warn", fn(a -> {
            rt.log.add(LogBuffer.WARN, join(a));
            return LuaValue.NONE;
        }));
        g.set("typeof", fn(a -> LuaValue.valueOf(typeName(a.arg(1)))));
        g.set("tick", fn(a -> LuaValue.valueOf(System.currentTimeMillis() / 1000.0)));
        g.set("time", fn(a -> LuaValue.valueOf(rt.now())));
        g.set("elapsedTime", g.get("time"));
        g.set("wait", fn(a -> rt.waitSeconds(a.optdouble(1, 0))));
        g.set("spawn", fn(a -> {
            rt.defer(a.checkfunction(1), LuaValue.NONE, rt.currentScript());
            return LuaValue.NONE;
        }));
        g.set("delay", fn(a -> {
            rt.delay(a.checkdouble(1), a.checkfunction(2), LuaValue.NONE, rt.currentScript());
            return LuaValue.NONE;
        }));
        g.set("unpack", g.get("table").get("unpack"));
        g.set("loadstring", fn(a -> {
            try {
                return rt.compile(a.checkjstring(1), a.optjstring(2, "loadstring"), null);
            } catch (LuaError e) {
                return LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(e.getMessage()));
            }
        }));
        g.set("require", fn(a -> {
            Instance inst = checkInstance(a.arg(1));
            if (!(inst instanceof ModuleScript ms)) throw new LuaError("Attempted to call require with invalid argument(s).");
            LuaValue cached = dm.moduleCache.get(ms);
            if (cached != null) return cached;
            String src = rt.sync(() -> ms.source);
            String chunk = rt.sync(ms::getFullName);
            var env = rt.newScriptEnv();
            env.set("script", ms.lua());
            Varargs r = rt.compile(src, chunk, env).invoke();
            if (r.narg() != 1) throw new LuaError("Module code did not return exactly one value");
            dm.moduleCache.put(ms, r.arg1());
            return r.arg1();
        }));

        // task library
        LuaTable task = new LuaTable();
        task.set("wait", fn(a -> rt.waitSeconds(a.optdouble(1, 0)).arg1()));
        task.set("spawn", fn(a -> rt.spawn(a.arg(1), a.subargs(2), rt.currentScript())));
        task.set("defer", fn(a -> rt.defer(a.arg(1), a.subargs(2), rt.currentScript())));
        task.set("delay", fn(a -> {
            rt.delay(a.checkdouble(1), a.checkfunction(2), a.subargs(3), rt.currentScript());
            return LuaValue.NONE;
        }));
        task.set("cancel", fn(a -> {
            rt.cancel(a.checkthread(1));
            return LuaValue.NONE;
        }));
        g.set("task", task);

        // Instance
        LuaTable inst = new LuaTable();
        inst.set("new", fn(a -> {
            String cn = a.checkjstring(1);
            Instance parent = optInstance(a.arg(2));
            return rt.sync(() -> {
                ClassInfo ci = ClassInfo.BY_NAME.get(cn);
                if (ci == null || ci.factory == null) throw new LuaError("Unable to create an Instance of type \"" + cn + "\"");
                Instance i = ci.factory.apply(dm, ci);
                if (parent != null) i.setParent(parent);
                return i.lua();
            });
        }));
        g.set("Instance", inst);

        // Vector3
        LuaTable v3 = new LuaTable();
        v3.set("new", fn(a -> toLua(new V3(a.optdouble(1, 0), a.optdouble(2, 0), a.optdouble(3, 0)))));
        v3.set("zero", toLua(V3.ZERO));
        v3.set("one", toLua(V3.ONE));
        v3.set("xAxis", toLua(V3.X));
        v3.set("yAxis", toLua(V3.Y));
        v3.set("zAxis", toLua(V3.Z));
        g.set("Vector3", v3);

        // CFrame
        LuaTable cf = new LuaTable();
        cf.set("new", fn(a -> {
            int n = a.narg();
            if (n == 0) return toLua(CFrame.IDENTITY);
            if (a.arg(1).touserdata() instanceof V3 pos) {
                if (n >= 2) return toLua(CFrame.lookAt(pos, checkV3(a.arg(2))));
                return toLua(CFrame.at(pos));
            }
            if (n >= 12) {
                double[] d = new double[12];
                for (int i = 0; i < 12; i++) d[i] = a.checkdouble(i + 1);
                return toLua(new CFrame(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10], d[11]));
            }
            if (n >= 7) {
                return toLua(CFrame.fromQuat(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3),
                        a.checkdouble(4), a.checkdouble(5), a.checkdouble(6), a.checkdouble(7)));
            }
            return toLua(CFrame.at(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3)));
        }));
        LuaValue angles = fn(a -> toLua(CFrame.angles(a.optdouble(1, 0), a.optdouble(2, 0), a.optdouble(3, 0))));
        cf.set("Angles", angles);
        cf.set("fromEulerAnglesXYZ", angles);
        LuaValue orient = fn(a -> toLua(CFrame.fromOrientation(a.optdouble(1, 0), a.optdouble(2, 0), a.optdouble(3, 0))));
        cf.set("fromOrientation", orient);
        cf.set("fromEulerAnglesYXZ", orient);
        cf.set("lookAt", fn(a -> toLua(CFrame.lookAt(checkV3(a.arg(1)), checkV3(a.arg(2)),
                a.arg(3).isnil() ? V3.Y : checkV3(a.arg(3))))));
        cf.set("fromAxisAngle", fn(a -> toLua(CFrame.fromAxisAngle(checkV3(a.arg(1)), a.checkdouble(2)))));
        cf.set("fromMatrix", fn(a -> {
            V3 x = checkV3(a.arg(2)), y = checkV3(a.arg(3));
            V3 z = a.arg(4).isnil() ? x.cross(y).unit() : checkV3(a.arg(4));
            return toLua(CFrame.fromMatrix(checkV3(a.arg(1)), x, y, z));
        }));
        cf.set("identity", toLua(CFrame.IDENTITY));
        g.set("CFrame", cf);

        // Color3
        LuaTable c3 = new LuaTable();
        c3.set("new", fn(a -> toLua(new Color3(a.optdouble(1, 0), a.optdouble(2, 0), a.optdouble(3, 0)))));
        c3.set("fromRGB", fn(a -> toLua(Color3.fromRGB(a.optdouble(1, 0), a.optdouble(2, 0), a.optdouble(3, 0)))));
        c3.set("fromHSV", fn(a -> toLua(Color3.fromHSV(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3)))));
        c3.set("fromHex", fn(a -> toLua(Color3.fromHex(a.checkjstring(1)))));
        g.set("Color3", c3);

        // BrickColor
        LuaTable bc = new LuaTable();
        bc.set("new", fn(a -> {
            if (a.arg(1).isnumber() && a.narg() == 1) return toLua(BrickColor.byNumber(a.checkint(1)));
            if (a.arg(1).touserdata() instanceof Color3 c) return toLua(BrickColor.closest(c));
            if (a.arg(1).isnumber()) return toLua(BrickColor.closest(new Color3(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3))));
            return toLua(BrickColor.byName(a.checkjstring(1)));
        }));
        bc.set("random", fn(a -> toLua(BrickColor.random())));
        bc.set("Random", bc.get("random"));
        for (String[] s : new String[][]{{"White", "White"}, {"Black", "Black"}, {"Red", "Bright red"},
                {"Blue", "Bright blue"}, {"Green", "Dark green"}, {"Yellow", "Bright yellow"}, {"Gray", "Medium stone grey"}}) {
            String n = s[1];
            bc.set(s[0], fn(a -> toLua(BrickColor.byName(n))));
        }
        g.set("BrickColor", bc);

        // Enum
        g.set("Enum", new LuaUserdata(RbxEnum.ALL, ENUMS_MT));

        // Luau standard library extras
        LuaValue math = g.get("math");
        math.set("clamp", fn(a -> {
            double x = a.checkdouble(1), lo = a.checkdouble(2), hi = a.checkdouble(3);
            if (lo > hi) throw new LuaError("max must be greater than or equal to min");
            return LuaValue.valueOf(Math.max(lo, Math.min(hi, x)));
        }));
        math.set("sign", fn(a -> LuaValue.valueOf(Math.signum(a.checkdouble(1)))));
        math.set("round", fn(a -> LuaValue.valueOf(Math.floor(a.checkdouble(1) + 0.5))));
        LuaValue string = g.get("string");
        string.set("split", fn(a -> {
            String s = a.checkjstring(1), sep = a.optjstring(2, ",");
            LuaTable t = new LuaTable();
            int i = 1;
            if (sep.isEmpty()) {
                for (char ch : s.toCharArray()) t.set(i++, String.valueOf(ch));
                return t;
            }
            int start = 0, idx;
            while ((idx = s.indexOf(sep, start)) >= 0) {
                t.set(i++, s.substring(start, idx));
                start = idx + sep.length();
            }
            t.set(i, s.substring(start));
            return t;
        }));
        LuaValue table = g.get("table");
        table.set("find", fn(a -> {
            LuaTable t = a.checktable(1);
            LuaValue v = a.arg(2);
            for (int i = a.optint(3, 1); i <= t.length(); i++) if (t.get(i).eq_b(v)) return LuaValue.valueOf(i);
            return LuaValue.NIL;
        }));
        table.set("clear", fn(a -> {
            LuaTable t = a.checktable(1);
            for (LuaValue k : t.keys()) t.set(k, LuaValue.NIL);
            return LuaValue.NONE;
        }));
        table.set("create", fn(a -> {
            LuaTable t = new LuaTable();
            for (int i = 1; i <= a.checkint(1); i++) t.set(i, a.arg(2));
            return t;
        }));
        table.set("freeze", fn(a -> a.arg1()));
        table.set("isfrozen", fn(a -> LuaValue.FALSE));
        LuaTable os = new LuaTable();
        os.set("time", fn(a -> LuaValue.valueOf(System.currentTimeMillis() / 1000)));
        os.set("clock", fn(a -> LuaValue.valueOf(rt.now())));
        os.set("difftime", fn(a -> LuaValue.valueOf(a.checkdouble(1) - a.optdouble(2, 0))));
        g.set("os", os);
    }

    static String join(Varargs a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= a.narg(); i++) {
            if (i > 1) sb.append(' ');
            sb.append(tostring(a.arg(i)));
        }
        return sb.toString();
    }
}
