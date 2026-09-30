package com.example.robloxphysics.dm;

import com.example.robloxphysics.lua.LogBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads *.lua / *.luau files from a folder as Script/LocalScript instances (they start running as soon as
 * they are parented somewhere scripts run). {@link #reload} replaces them with fresh copies from disk.
 */
public final class ScriptFiles {
    private final DataModel dm;
    private final Path dir;
    private final ClassInfo cls;
    private final List<Instance> loaded = new ArrayList<>();

    public ScriptFiles(DataModel dm, Path dir, ClassInfo cls) {
        this.dm = dm;
        this.dir = dir;
        this.cls = cls;
    }

    public Path dir() { return dir; }

    /** Game thread. */
    public void reload(Instance parent) {
        for (Instance i : loaded) {
            try { i.destroy(); } catch (IllegalArgumentException ignored) {}
        }
        loaded.clear();
        if (parent == null) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            dm.log.add(LogBuffer.ERROR, "Could not create script folder " + dir + ": " + e.getMessage());
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return Files.isRegularFile(p) && (n.endsWith(".lua") || n.endsWith(".luau"));
            }).sorted().toList();
        } catch (IOException e) {
            dm.log.add(LogBuffer.ERROR, "Could not list " + dir + ": " + e.getMessage());
            return;
        }
        for (Path f : files) {
            try {
                String name = f.getFileName().toString();
                name = name.substring(0, name.lastIndexOf('.'));
                BaseScript s = (BaseScript) cls.factory.apply(dm, cls);
                s.setName(name);
                s.source = Files.readString(f, StandardCharsets.UTF_8);
                loaded.add(s);
                s.setParent(parent);
            } catch (IOException e) {
                dm.log.add(LogBuffer.ERROR, "Could not read " + f + ": " + e.getMessage());
            }
        }
        if (!files.isEmpty()) dm.log.add(LogBuffer.INFO, "Loaded " + files.size() + " script(s) from " + dir);
    }
}
