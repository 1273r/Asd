package com.example.robloxphysics.studio;

import com.example.robloxphysics.dm.BaseScript;
import com.example.robloxphysics.dm.ClassInfo;
import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.dm.DataModel;
import com.example.robloxphysics.dm.Instance;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.rbx.BrickColor;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Color3;
import com.example.robloxphysics.rbx.RbxEnum;
import com.example.robloxphysics.rbx.V3;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * Roblox Studio-style operations (Explorer tree, Properties, editing, scripts, command bar) against a
 * DataModel. Served to developers over the network via the "studio" message. Game thread only.
 */
public final class StudioService {
    private StudioService() {}

    public static final int MAX_NODES = 6000;

    public static JsonElement handle(DataModel dm, String method, JsonObject a) {
        switch (method) {
            case "tree": {
                if (a.has("ver") && a.get("ver").getAsLong() == dm.version) {
                    JsonObject o = new JsonObject();
                    o.addProperty("unchanged", true);
                    return o;
                }
                JsonObject o = new JsonObject();
                o.addProperty("ver", dm.version);
                JsonArray nodes = new JsonArray();
                dm.refreshCharacters();
                for (Instance s : List.copyOf(dm.game.getChildren())) addNodes(nodes, s, dm.game.id);
                o.add("nodes", nodes);
                return o;
            }
            case "props": return props(req(dm, a));
            case "set": {
                Instance i = req(dm, a);
                String prop = a.get("prop").getAsString();
                ClassInfo.Prop p = i.cls.findProp(prop);
                if (p == null) throw new IllegalArgumentException(prop + " is not a valid property");
                p.set(i, parse(dm, p, a.get("value").getAsString()));
                return props(i);
            }
            case "create": {
                ClassInfo ci = ClassInfo.BY_NAME.get(a.get("cls").getAsString());
                if (ci == null || ci.factory == null) throw new IllegalArgumentException("Cannot create " + a.get("cls").getAsString());
                Instance parent = dm.findById(a.get("parent").getAsString());
                if (parent == null) parent = dm.workspace;
                Instance i = ci.factory.apply(dm, ci);
                if (a.has("name")) i.setName(a.get("name").getAsString());
                if (i instanceof BaseScript bs && a.has("source")) bs.source = a.get("source").getAsString();
                i.setParent(parent);
                return new JsonPrimitive(i.id);
            }
            case "delete": {
                req(dm, a).destroy();
                return new JsonPrimitive(true);
            }
            case "duplicate": {
                Instance i = req(dm, a);
                Instance c = i.cloneTree();
                if (c == null) throw new IllegalArgumentException(i.getName() + " is not Archivable");
                c.setParent(i.getParent());
                return new JsonPrimitive(c.id);
            }
            case "reparent": {
                Instance i = req(dm, a);
                Instance p = dm.findById(a.get("parent").getAsString());
                if (p == null) throw new IllegalArgumentException("parent not found");
                i.setParent(p);
                return new JsonPrimitive(true);
            }
            case "getSource": {
                Instance i = req(dm, a);
                JsonObject o = new JsonObject();
                o.addProperty("id", i.id);
                o.addProperty("name", i.getFullName());
                o.addProperty("source", (String) i.get("Source"));
                return o;
            }
            case "setSource": {
                req(dm, a).set("Source", a.get("source").getAsString());
                return new JsonPrimitive(true);
            }
            case "run": {
                Instance i = req(dm, a);
                if (!(i instanceof BaseScript bs)) throw new IllegalArgumentException(i.getName() + " is not a script");
                if (!(Boolean) bs.get("Enabled")) bs.set("Enabled", true);
                bs.restart();
                dm.log.add(LogBuffer.INFO, "Running " + bs.getFullName());
                return new JsonPrimitive(true);
            }
            case "exec": {
                dm.selection.selected.clear();
                if (a.has("sel")) {
                    for (JsonElement e : a.getAsJsonArray("sel")) {
                        Instance s = dm.findById(e.getAsString());
                        if (s != null) dm.selection.selected.add(s);
                    }
                }
                String code = a.get("code").getAsString();
                dm.log.add(LogBuffer.INFO, "> " + (code.length() > 200 ? code.substring(0, 200) + "..." : code));
                dm.rt.execute(code, "CommandBar");
                return new JsonPrimitive(true);
            }
            default:
                throw new IllegalArgumentException("unknown studio method " + method);
        }
    }

    private static Instance req(DataModel dm, JsonObject a) {
        Instance i = dm.findById(a.get("id").getAsString());
        if (i == null) throw new IllegalArgumentException("instance no longer exists");
        return i;
    }

    private static void addNodes(JsonArray out, Instance i, String parentId) {
        if (out.size() >= MAX_NODES) return;
        JsonObject n = new JsonObject();
        n.addProperty("id", i.id);
        n.addProperty("c", i.cls.name);
        n.addProperty("n", i.getName());
        n.addProperty("p", parentId);
        if (i instanceof BaseScript bs) n.addProperty("run", bs.isRunning());
        out.add(n);
        for (Instance c : List.copyOf(i.getChildren())) addNodes(out, c, i.id);
    }

    public static JsonObject props(Instance i) {
        JsonObject o = new JsonObject();
        o.addProperty("id", i.id);
        o.addProperty("class", i.cls.name);
        o.addProperty("fullName", i.getFullName());
        JsonArray arr = new JsonArray();
        for (ClassInfo.Prop p : i.cls.allProps()) {
            if (p.hidden) continue;
            JsonObject po = new JsonObject();
            po.addProperty("n", p.name);
            po.addProperty("t", p.type.name());
            po.addProperty("cat", p.category);
            po.addProperty("ro", p.readOnly() || p.name.equals("Parent"));
            Object v;
            try {
                v = p.get(i);
            } catch (RuntimeException e) {
                v = null;
            }
            po.addProperty("v", display(v));
            if (p.type == PT.ENUM) {
                JsonArray opts = new JsonArray();
                for (RbxEnum.Item it : p.enumType.items) opts.add(it.name());
                po.add("opts", opts);
            }
            arr.add(po);
        }
        o.add("props", arr);
        JsonArray m = new JsonArray();
        i.cls.allMethods().forEach(m::add);
        o.add("methods", m);
        return o;
    }

    public static String display(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) return V3.num(d);
        if (v instanceof Color3 c) return c.toRgbString();
        if (v instanceof RbxEnum.Item e) return e.name();
        if (v instanceof Instance inst) return inst.getName();
        return v.toString();
    }

    static Object parse(DataModel dm, ClassInfo.Prop p, String s) {
        String t = s.trim();
        switch (p.type) {
            case BOOL: return Boolean.parseBoolean(t);
            case NUMBER:
            case INT: return evalNumber(t);
            case STRING: return s;
            case V3: return V3.parse(t);
            case COLOR3: return Color3.parseRgb(t);
            case BRICKCOLOR: return BrickColor.byName(t);
            case ENUM: {
                RbxEnum.Item it = p.enumType.parse(t);
                if (it == null) throw new IllegalArgumentException("'" + t + "' is not a valid " + p.enumType.name);
                return it;
            }
            case CFRAME: {
                String[] parts = t.split("[,\\s]+");
                double[] d = new double[parts.length];
                for (int i = 0; i < d.length; i++) d[i] = Double.parseDouble(parts[i]);
                if (d.length == 3) return CFrame.at(d[0], d[1], d[2]);
                if (d.length == 12) return new CFrame(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10], d[11]);
                throw new IllegalArgumentException("CFrame needs 3 or 12 numbers");
            }
            case INSTANCE: {
                Instance i = dm.findById(t);
                if (i == null && !t.isEmpty()) throw new IllegalArgumentException("instance not found");
                return i;
            }
            default: return t;
        }
    }

    /** numbers with simple arithmetic like Roblox Studio's property fields: "4*2+1" */
    static double evalNumber(String s) {
        return new Object() {
            int pos = -1, ch;
            final String str = s.replace(" ", "");

            void next() { ch = ++pos < str.length() ? str.charAt(pos) : -1; }

            boolean eat(int c) {
                if (ch == c) { next(); return true; }
                return false;
            }

            double parse() {
                next();
                double x = expr();
                if (pos < str.length()) throw new IllegalArgumentException("invalid number: " + s);
                return x;
            }

            double expr() {
                double x = term();
                while (true) {
                    if (eat('+')) x += term();
                    else if (eat('-')) x -= term();
                    else return x;
                }
            }

            double term() {
                double x = factor();
                while (true) {
                    if (eat('*')) x *= factor();
                    else if (eat('/')) x /= factor();
                    else return x;
                }
            }

            double factor() {
                if (eat('-')) return -factor();
                if (eat('+')) return factor();
                double x;
                int start = pos;
                if (eat('(')) {
                    x = expr();
                    eat(')');
                } else {
                    while ((ch >= '0' && ch <= '9') || ch == '.' || ch == 'e') next();
                    if (start == pos) throw new IllegalArgumentException("invalid number: " + s);
                    x = Double.parseDouble(str.substring(start, pos));
                }
                if (eat('^')) x = Math.pow(x, factor());
                return x;
            }
        }.parse();
    }
}
