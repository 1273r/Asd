package com.example.robloxphysics.rbx;

/** Roblox Color3 (components 0..1). */
public record Color3(double r, double g, double b) {
    public static Color3 fromRGB(double r, double g, double b) { return new Color3(r / 255.0, g / 255.0, b / 255.0); }

    public static Color3 fromInt(int rgb) { return fromRGB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255); }

    public static Color3 fromHSV(double h, double s, double v) {
        h = ((h % 1.0) + 1.0) % 1.0;
        double f = h * 6.0;
        int i = (int) Math.floor(f);
        double fr = f - i, p = v * (1 - s), q = v * (1 - s * fr), t = v * (1 - s * (1 - fr));
        return switch (i % 6) {
            case 0 -> new Color3(v, t, p);
            case 1 -> new Color3(q, v, p);
            case 2 -> new Color3(p, v, t);
            case 3 -> new Color3(p, q, v);
            case 4 -> new Color3(t, p, v);
            default -> new Color3(v, p, q);
        };
    }

    public static Color3 fromHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        return fromInt(Integer.parseInt(h, 16));
    }

    public int toInt() {
        return (c(r) << 16) | (c(g) << 8) | c(b);
    }

    private static int c(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    public double[] toHSV() {
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h = 0;
        if (d > 1e-12) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new double[]{h, max <= 0 ? 0 : d / max, max};
    }

    public String toHex() { return String.format("%06X", toInt()); }

    public Color3 lerp(Color3 o, double a) { return new Color3(r + (o.r - r) * a, g + (o.g - g) * a, b + (o.b - b) * a); }

    @Override
    public String toString() { return V3.num(r) + ", " + V3.num(g) + ", " + V3.num(b); }

    /** Studio shows colors as [R, G, B] 0-255 */
    public String toRgbString() { return "[" + c(r) + ", " + c(g) + ", " + c(b) + "]"; }

    public static Color3 parseRgb(String s) {
        String t = s.trim();
        if (t.startsWith("#")) return fromHex(t);
        String[] p = t.replace("[", "").replace("]", "").split("[,\\s]+");
        java.util.List<Double> v = new java.util.ArrayList<>();
        for (String q : p) if (!q.isBlank()) v.add(Double.parseDouble(q.trim()));
        if (v.size() != 3) throw new IllegalArgumentException("expected R, G, B (0-255)");
        return fromRGB(v.get(0), v.get(1), v.get(2));
    }
}
