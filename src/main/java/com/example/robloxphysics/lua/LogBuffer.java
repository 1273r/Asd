package com.example.robloxphysics.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Ring buffer of Output/Developer Console lines. Thread safe. */
public final class LogBuffer {
    public static final int OUTPUT = 0, INFO = 1, WARN = 2, ERROR = 3;

    public record Line(long time, int level, String text) {}

    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private final int max;
    private final List<Consumer<Line>> listeners = new ArrayList<>();
    private long version;

    public LogBuffer(int max) { this.max = max; }

    public void add(int level, String text) {
        add(new Line(System.currentTimeMillis(), level, text));
    }

    public void add(Line l) {
        List<Consumer<Line>> ls;
        synchronized (this) {
            lines.addLast(l);
            while (lines.size() > max) lines.removeFirst();
            version++;
            ls = new ArrayList<>(listeners);
        }
        for (Consumer<Line> c : ls) {
            try { c.accept(l); } catch (Throwable ignored) {}
        }
    }

    public synchronized List<Line> snapshot() { return new ArrayList<>(lines); }

    public synchronized long version() { return version; }

    public synchronized void clear() { lines.clear(); version++; }

    public synchronized void listen(Consumer<Line> c) { listeners.add(c); }

    public synchronized void unlisten(Consumer<Line> c) { listeners.remove(c); }
}
