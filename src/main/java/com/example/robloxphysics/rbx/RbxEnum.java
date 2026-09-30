package com.example.robloxphysics.rbx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Roblox Enum types (Enum.Material.Plastic etc). */
public final class RbxEnum {
    public final String name;
    public final List<Item> items = new ArrayList<>();
    private final Map<String, Item> byName = new LinkedHashMap<>();

    public record Item(RbxEnum type, String name, int value) {
        @Override
        public String toString() { return "Enum." + type.name + "." + name; }
    }

    public static final Map<String, RbxEnum> ALL = new LinkedHashMap<>();

    private RbxEnum(String name, Object... pairs) {
        this.name = name;
        for (int i = 0; i < pairs.length; i += 2) {
            Item it = new Item(this, (String) pairs[i], (Integer) pairs[i + 1]);
            items.add(it);
            byName.put(it.name, it);
        }
        ALL.put(name, this);
    }

    private static RbxEnum seq(String name, String... names) {
        Object[] p = new Object[names.length * 2];
        for (int i = 0; i < names.length; i++) { p[i * 2] = names[i]; p[i * 2 + 1] = i; }
        return new RbxEnum(name, p);
    }

    public Item get(String n) { return byName.get(n); }

    public Item fromValue(int v) {
        for (Item i : items) if (i.value == v) return i;
        return null;
    }

    /** Accepts "Plastic", "Enum.Material.Plastic" or a number. */
    public Item parse(String s) {
        String t = s.trim();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) t = t.substring(dot + 1);
        Item i = get(t);
        if (i != null) return i;
        for (Item it : items) if (it.name.equalsIgnoreCase(t)) return it;
        try { return fromValue(Integer.parseInt(t)); } catch (NumberFormatException e) { return null; }
    }

    // Values match Roblox where it matters for scripts that compare numbers.
    public static final RbxEnum MATERIAL = new RbxEnum("Material",
            "Plastic", 256, "SmoothPlastic", 272, "Neon", 288, "Wood", 512, "WoodPlanks", 528, "Marble", 784,
            "Slate", 800, "Concrete", 816, "Granite", 832, "Brick", 848, "Pebble", 864, "Cobblestone", 880,
            "CorrodedMetal", 1040, "DiamondPlate", 1056, "Foil", 1072, "Metal", 1088, "Grass", 1280, "Sand", 1296,
            "Fabric", 1312, "Ice", 1536, "Glass", 1568, "ForceField", 1584);
    public static final RbxEnum PART_TYPE = new RbxEnum("PartType", "Ball", 0, "Block", 1, "Cylinder", 2);
    public static final RbxEnum EASING_STYLE = seq("EasingStyle", "Linear", "Sine", "Back", "Quad", "Quart", "Quint",
            "Bounce", "Elastic", "Exponential", "Circular", "Cubic");
    public static final RbxEnum EASING_DIRECTION = seq("EasingDirection", "In", "Out", "InOut");
    public static final RbxEnum PLAYBACK_STATE = seq("PlaybackState", "Begin", "Delayed", "Playing", "Paused", "Completed", "Cancelled");
    public static final RbxEnum CAMERA_TYPE = new RbxEnum("CameraType", "Fixed", 0, "Attach", 1, "Watch", 2, "Track", 3,
            "Follow", 4, "Custom", 5, "Scriptable", 6, "Orbital", 7);
    public static final RbxEnum NORMAL_ID = seq("NormalId", "Right", "Top", "Back", "Left", "Bottom", "Front");
    public static final RbxEnum RAYCAST_FILTER = new RbxEnum("RaycastFilterType", "Exclude", 0, "Include", 1, "Blacklist", 0, "Whitelist", 1);
    public static final RbxEnum USER_INPUT_TYPE = new RbxEnum("UserInputType", "MouseButton1", 0, "MouseButton2", 1,
            "MouseButton3", 2, "MouseWheel", 3, "MouseMovement", 4, "Keyboard", 8, "None", 18);
    public static final RbxEnum USER_INPUT_STATE = seq("UserInputState", "Begin", "Change", "End", "Cancel", "None");
    public static final RbxEnum MOUSE_BEHAVIOR = seq("MouseBehavior", "Default", "LockCenter", "LockCurrentPosition");
    public static final RbxEnum HUMANOID_STATE = new RbxEnum("HumanoidStateType", "FallingDown", 0, "Running", 8,
            "RunningNoPhysics", 10, "Climbing", 12, "StrafingNoPhysics", 11, "Ragdoll", 1, "GettingUp", 2, "Jumping", 3,
            "Landed", 7, "Flying", 6, "Freefall", 5, "Seated", 13, "PlatformStanding", 14, "Dead", 15, "Swimming", 4, "Physics", 16, "None", 18);
    public static final RbxEnum MESSAGE_TYPE = seq("MessageType", "MessageOutput", "MessageInfo", "MessageWarning", "MessageError");
    public static final RbxEnum KEY_CODE;

    static {
        List<Object> k = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) { k.add(String.valueOf(c)); k.add((int) c + 32); }
        String[] digits = {"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"};
        for (int i = 0; i < 10; i++) { k.add(digits[i]); k.add(48 + i); }
        Object[] rest = {"Space", 32, "Return", 13, "Tab", 9, "Backspace", 8, "Escape", 27, "LeftShift", 304,
                "RightShift", 303, "LeftControl", 306, "RightControl", 305, "LeftAlt", 308, "RightAlt", 307,
                "Up", 273, "Down", 274, "Right", 275, "Left", 276, "Delete", 127, "Insert", 277, "Home", 278, "End", 279,
                "PageUp", 280, "PageDown", 281, "F1", 282, "F2", 283, "F3", 284, "F4", 285, "F5", 286, "F6", 287, "F7", 288,
                "F8", 289, "F9", 290, "F10", 291, "F11", 292, "F12", 293, "Unknown", 0};
        for (Object o : rest) k.add(o);
        KEY_CODE = new RbxEnum("KeyCode", k.toArray());
    }

    /** GLFW key -> Enum.KeyCode item (null if unmapped) */
    public static Item keyFromGlfw(int glfw) {
        if (glfw >= 65 && glfw <= 90) return KEY_CODE.get(String.valueOf((char) glfw));
        if (glfw >= 48 && glfw <= 57) return KEY_CODE.fromValue(glfw);
        String n = switch (glfw) {
            case 32 -> "Space"; case 257 -> "Return"; case 258 -> "Tab"; case 259 -> "Backspace"; case 256 -> "Escape";
            case 340 -> "LeftShift"; case 344 -> "RightShift"; case 341 -> "LeftControl"; case 345 -> "RightControl";
            case 342 -> "LeftAlt"; case 346 -> "RightAlt"; case 265 -> "Up"; case 264 -> "Down"; case 262 -> "Right";
            case 263 -> "Left"; case 261 -> "Delete"; case 260 -> "Insert"; case 268 -> "Home"; case 269 -> "End";
            case 266 -> "PageUp"; case 267 -> "PageDown";
            default -> (glfw >= 290 && glfw <= 301) ? "F" + (glfw - 289) : null;
        };
        return n == null ? KEY_CODE.get("Unknown") : KEY_CODE.get(n);
    }

    /** Enum.KeyCode item -> GLFW key (-1 if unmapped) */
    public static int glfwFromKey(Item it) {
        String n = it.name;
        if (n.length() == 1 && Character.isLetter(n.charAt(0))) return n.charAt(0);
        if (it.value >= 48 && it.value <= 57) return it.value;
        return switch (n) {
            case "Space" -> 32; case "Return" -> 257; case "Tab" -> 258; case "Backspace" -> 259; case "Escape" -> 256;
            case "LeftShift" -> 340; case "RightShift" -> 344; case "LeftControl" -> 341; case "RightControl" -> 345;
            case "LeftAlt" -> 342; case "RightAlt" -> 346; case "Up" -> 265; case "Down" -> 264; case "Right" -> 262;
            case "Left" -> 263; case "Delete" -> 261; case "Insert" -> 260; case "Home" -> 268; case "End" -> 269;
            case "PageUp" -> 266; case "PageDown" -> 267;
            default -> n.startsWith("F") && n.length() <= 3 ? 289 + Integer.parseInt(n.substring(1)) : -1;
        };
    }
}
