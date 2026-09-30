package com.example.robloxphysics.rbx;

import java.util.LinkedHashMap;
import java.util.Map;

/** A subset of the Roblox BrickColor palette. */
public record BrickColor(String name, int number, Color3 color) {
    public static final Map<String, BrickColor> BY_NAME = new LinkedHashMap<>();
    private static final Map<Integer, BrickColor> BY_NUMBER = new LinkedHashMap<>();

    private static void add(String name, int number, int rgb) {
        BrickColor b = new BrickColor(name, number, Color3.fromInt(rgb));
        BY_NAME.put(name, b);
        BY_NUMBER.put(number, b);
    }

    static {
        add("White", 1, 0xF2F3F3);
        add("Grey", 2, 0xA1A5A2);
        add("Light yellow", 3, 0xF9E999);
        add("Brick yellow", 5, 0xD7C59A);
        add("Light green (Mint)", 6, 0xC2DAB8);
        add("Light reddish violet", 9, 0xE8BAC8);
        add("Nougat", 18, 0xCC8E69);
        add("Bright red", 21, 0xC4281C);
        add("Bright blue", 23, 0x0D69AC);
        add("Bright yellow", 24, 0xF5CD30);
        add("Black", 26, 0x1B2A35);
        add("Dark green", 28, 0x287F47);
        add("Bright green", 37, 0x4B974B);
        add("Dark orange", 38, 0xA05F35);
        add("Bright violet", 104, 0x6B327C);
        add("Bright orange", 106, 0xDA8541);
        add("Bright bluish green", 107, 0x008F9C);
        add("Earth green", 141, 0x27462D);
        add("Sand blue", 135, 0x74869D);
        add("Medium stone grey", 194, 0xA3A2A5);
        add("Dark stone grey", 199, 0x635F62);
        add("Light stone grey", 208, 0xE5E4DF);
        add("Reddish brown", 192, 0x694028);
        add("Institutional white", 1001, 0xF8F8F8);
        add("Really black", 1003, 0x111111);
        add("Really red", 1004, 0xFF0000);
        add("Deep orange", 1005, 0xFFB000);
        add("Alder", 1006, 0xB480FF);
        add("Cyan", 1019, 0x00FFFF);
        add("Lime green", 1020, 0x00FF00);
        add("Toothpaste", 1019 + 1000, 0x00FFFF);
        add("Really blue", 1010, 0x0000FF);
        add("Navy blue", 1011, 0x002060);
        add("Hot pink", 1032, 0xFF00BF);
        add("Magenta", 1015, 0xAA00AA);
        add("New Yeller", 1009, 0xFFFF00);
        add("Pastel light blue", 1024, 0xAFDDFF);
        add("Teal", 1018, 0x12EED4);
        add("Camo", 1021, 0x3A7D15);
        add("Smoky grey", 1022, 0x5B5D69);
        add("Pine Cone", 1032 + 1000, 0x6C584B);
    }

    public static final BrickColor DEFAULT = BY_NAME.get("Medium stone grey");

    public static BrickColor byName(String n) {
        BrickColor b = BY_NAME.get(n);
        if (b != null) return b;
        for (BrickColor c : BY_NAME.values()) if (c.name.equalsIgnoreCase(n)) return c;
        return DEFAULT;
    }

    public static BrickColor byNumber(int n) { return BY_NUMBER.getOrDefault(n, DEFAULT); }

    /** Nearest palette entry. */
    public static BrickColor closest(Color3 c) {
        BrickColor best = DEFAULT;
        double bd = Double.MAX_VALUE;
        for (BrickColor b : BY_NAME.values()) {
            double dr = b.color.r() - c.r(), dg = b.color.g() - c.g(), db = b.color.b() - c.b();
            double d = dr * dr + dg * dg + db * db;
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    public static BrickColor random() {
        Object[] v = BY_NAME.values().toArray();
        return (BrickColor) v[(int) (Math.random() * v.length)];
    }

    @Override
    public String toString() { return name; }
}
