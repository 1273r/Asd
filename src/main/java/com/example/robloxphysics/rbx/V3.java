package com.example.robloxphysics.rbx;

/** Immutable Roblox Vector3. */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 ONE = new V3(1, 1, 1);
    public static final V3 X = new V3(1, 0, 0);
    public static final V3 Y = new V3(0, 1, 0);
    public static final V3 Z = new V3(0, 0, 1);

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 mul(V3 o) { return new V3(x * o.x, y * o.y, z * o.z); }
    public V3 div(V3 o) { return new V3(x / o.x, y / o.y, z / o.z); }
    public V3 scale(double s) { return new V3(x * s, y * s, z * s); }
    public V3 neg() { return new V3(-x, -y, -z); }
    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public V3 unit() {
        double l = length();
        return l < 1e-12 ? ZERO : scale(1.0 / l);
    }
    public V3 lerp(V3 o, double a) { return new V3(x + (o.x - x) * a, y + (o.y - y) * a, z + (o.z - z) * a); }
    public V3 abs() { return new V3(Math.abs(x), Math.abs(y), Math.abs(z)); }
    public V3 min(V3 o) { return new V3(Math.min(x, o.x), Math.min(y, o.y), Math.min(z, o.z)); }
    public V3 max(V3 o) { return new V3(Math.max(x, o.x), Math.max(y, o.y), Math.max(z, o.z)); }
    public boolean fuzzyEq(V3 o, double eps) { return sub(o).length() <= eps; }

    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        String s = String.format(java.util.Locale.ROOT, "%.3f", d);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.equals("-0") ? "0" : s;
    }

    @Override
    public String toString() { return num(x) + ", " + num(y) + ", " + num(z); }

    public static V3 parse(String s) {
        String[] p = s.replace("(", "").replace(")", "").split("[,\\s]+");
        java.util.List<Double> v = new java.util.ArrayList<>();
        for (String q : p) if (!q.isBlank()) v.add(Double.parseDouble(q.trim()));
        if (v.size() == 1) return new V3(v.get(0), v.get(0), v.get(0));
        if (v.size() != 3) throw new IllegalArgumentException("expected x, y, z");
        return new V3(v.get(0), v.get(1), v.get(2));
    }
}
