package com.example.robloxphysics.lua;

import com.example.robloxphysics.RobloxPhysics;
import com.example.robloxphysics.dm.BaseScript;
import com.example.robloxphysics.dm.DataModel;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.OrphanedThread;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One Lua VM (one per side: "Server" and "Client"), mirroring a Roblox script context.
 *
 * Threading model: LuaJ implements coroutines with Java threads, and Minecraft objects must only be
 * touched from the game thread. So all Lua runs on a dedicated worker thread (plus LuaJ's coroutine
 * threads), and every call from Lua into the game is marshalled to the game thread with {@link #sync}.
 * The game thread only ever waits for Lua inside {@link #runSync}, where it services those calls, so
 * there is exactly one thread doing work at a time and no deadlock.
 */
public final class LuaRuntime {
    public static final double TIMEOUT_SECONDS = 10.0; // Roblox's script timeout

    public final String side; // "Server" / "Client"
    public final LogBuffer log;
    public final Thread gameThread;
    public DataModel dm;
    public final Globals globals;

    private final LinkedBlockingQueue<Runnable> toWorker = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Runnable> toGame = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean servicing;
    private volatile boolean broken;
    private volatile boolean shutdown;
    volatile boolean abort;

    /** game-thread queue of jobs to run on the Lua side at the next step */
    private final List<Runnable> pending = new ArrayList<>();

    // ---------------- scheduler state (Lua side only) ----------------
    static final LuaValue IDLE = LuaValue.userdataOf(new Object());
    static final LuaValue WAIT = LuaValue.userdataOf(new Object());
    static final LuaValue PARK = LuaValue.userdataOf(new Object()); // waiting for a signal / manual resume

    public final class Task {
        public final LuaThread co;
        public BaseScript owner;
        double wakeAt = Double.NaN;
        double sleepStart;
        boolean dead;
        final boolean runner;

        Task(LuaThread co, boolean runner) {
            this.co = co;
            this.runner = runner;
        }
    }

    private record Timer(double at, LuaValue fn, Varargs args, BaseScript owner) {}

    private record Ready(Task task, Varargs args) {}

    private final Map<LuaThread, Task> byThread = new IdentityHashMap<>();
    private final List<Task> sleeping = new ArrayList<>();
    private final List<Timer> timers = new ArrayList<>();
    private final ArrayDeque<Ready> ready = new ArrayDeque<>();
    private final ArrayDeque<Task> pool = new ArrayDeque<>();
    Task current;
    private final long startNanos = System.nanoTime();
    private final LuaFunction runnerFn;

    public LuaRuntime(String side, LogBuffer log) {
        this.side = side;
        this.log = log;
        this.gameThread = Thread.currentThread();
        this.globals = createGlobals();
        this.runnerFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                while (true) {
                    LuaValue fn = a.arg1();
                    Varargs args = a.subargs(2);
                    try {
                        fn.invoke(args);
                    } catch (OrphanedThread o) {
                        throw o;
                    } catch (LuaError e) {
                        reportError(e, current == null ? null : current.owner);
                    } catch (RuntimeException e) {
                        reportError(new LuaError(e), current == null ? null : current.owner);
                    } catch (StackOverflowError e) {
                        reportError(new LuaError("stack overflow"), current == null ? null : current.owner);
                    }
                    a = globals.yield(IDLE);
                }
            }
        };
        this.worker = new Thread(this::workerLoop, "Lua-" + side);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    // =====================================================================================
    //  Threading
    // =====================================================================================

    private void workerLoop() {
        while (!shutdown) {
            try {
                Runnable r = toWorker.poll(1, TimeUnit.SECONDS);
                if (r != null) r.run();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                RobloxPhysics.LOGGER.error("Lua worker error", t);
            }
        }
    }

    public boolean isGameThread() { return Thread.currentThread() == gameThread; }

    /** Run a job on the Lua side and wait for it, servicing game-thread calls meanwhile. Game thread only. */
    public void runSync(Runnable job) {
        if (broken || shutdown) return;
        if (!isGameThread()) { // already on the Lua side
            job.run();
            return;
        }
        if (servicing) { // re-entrant request from inside a bridged call: defer
            post(job);
            return;
        }
        servicing = true;
        try {
            final Object doneMarker = new Object();
            final boolean[] done = {false};
            toWorker.add(() -> {
                try {
                    job.run();
                } catch (Throwable t) {
                    RobloxPhysics.LOGGER.error("Lua job failed", t);
                } finally {
                    toGame.add(() -> done[0] = true);
                }
            });
            long start = System.nanoTime();
            long deadline = start + (long) (TIMEOUT_SECONDS * 1e9);
            long hardDeadline = Long.MAX_VALUE;
            while (!done[0]) {
                Runnable r;
                try {
                    r = toGame.poll(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break;
                }
                if (r != null) r.run();
                long now = System.nanoTime();
                if (now > deadline && hardDeadline == Long.MAX_VALUE) {
                    abort = true;
                    hardDeadline = now + 5_000_000_000L;
                }
                if (now > hardDeadline) {
                    broken = true;
                    log.add(LogBuffer.ERROR, side + " script context stopped responding and was shut down. Use the console command 'reset' to restart it.");
                    break;
                }
            }
        } finally {
            servicing = false;
        }
    }

    /** Queue a job for the Lua side; runs during the next step. Any thread. */
    public void post(Runnable job) {
        if (!isGameThread()) {
            job.run();
            return;
        }
        synchronized (pending) {
            pending.add(job);
        }
    }

    /** Execute on the game thread and return the result. Called from the Lua side. */
    public <T> T sync(Supplier<T> s) {
        if (isGameThread()) return s.get();
        CompletableFuture<T> f = new CompletableFuture<>();
        toGame.add(() -> {
            try {
                f.complete(s.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.join();
        } catch (CompletionException e) {
            Throwable c = e.getCause();
            if (c instanceof LuaError le) throw le;
            if (c instanceof RuntimeException re) throw new LuaError(re.getMessage() == null ? re.toString() : re.getMessage());
            throw new LuaError(String.valueOf(c));
        }
    }

    public void syncRun(Runnable r) {
        sync(() -> {
            r.run();
            return null;
        });
    }

    public boolean isBroken() { return broken; }

    public void shutdown() {
        shutdown = true;
        worker.interrupt();
    }

    // =====================================================================================
    //  Scheduler
    // =====================================================================================

    public double now() { return (System.nanoTime() - startNanos) / 1e9; }

    /** One scheduler step. Game thread. */
    public void step(Runnable extra) {
        if (broken || shutdown) return;
        runSync(() -> {
            drainPending();
            double now = now();
            // timers (task.delay)
            List<Timer> dueTimers = new ArrayList<>();
            for (Iterator<Timer> it = timers.iterator(); it.hasNext(); ) {
                Timer t = it.next();
                if (t.at <= now) { dueTimers.add(t); it.remove(); }
            }
            for (Timer t : dueTimers) spawn(t.fn, t.args, t.owner);
            // sleeping threads (wait / task.wait)
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = sleeping.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (t.dead) { it.remove(); continue; }
                if (t.wakeAt <= now) { due.add(t); it.remove(); }
            }
            for (Task t : due) {
                if (!t.dead) resume(t, LuaValue.varargsOf(LuaValue.valueOf(now - t.sleepStart), LuaValue.valueOf(now)));
            }
            drainReady();
            if (extra != null) extra.run();
            drainReady();
            drainPending();
        });
    }

    private void drainPending() {
        for (int guard = 0; guard < 16; guard++) {
            List<Runnable> jobs;
            synchronized (pending) {
                if (pending.isEmpty()) return;
                jobs = new ArrayList<>(pending);
                pending.clear();
            }
            for (Runnable r : jobs) {
                try {
                    r.run();
                } catch (Throwable t) {
                    RobloxPhysics.LOGGER.error("Lua pending job failed", t);
                }
            }
            drainReady();
        }
    }

    private void drainReady() {
        for (int guard = 0; guard < 10000 && !ready.isEmpty(); guard++) {
            Ready r = ready.poll();
            if (!r.task.dead) resume(r.task, r.args);
        }
    }

    private Task newRunner() {
        Task t = pool.poll();
        if (t != null) return t;
        LuaThread co = new LuaThread(globals, runnerFn);
        t = new Task(co, true);
        byThread.put(co, t);
        return t;
    }

    /** task.spawn semantics: run fn now in a new script thread. Lua side. Returns the thread. */
    public LuaThread spawn(LuaValue fn, Varargs args, BaseScript owner) {
        if (fn.isthread()) {
            Task t = byThread.get((LuaThread) fn);
            if (t != null) {
                resume(t, args);
                return t.co;
            }
            ((LuaThread) fn).resume(args);
            return (LuaThread) fn;
        }
        Task t = newRunner();
        t.owner = owner;
        resume(t, LuaValue.varargsOf(fn, args));
        return t.co;
    }

    /** task.defer semantics. Lua side. */
    public LuaThread defer(LuaValue fn, Varargs args, BaseScript owner) {
        Task t = newRunner();
        t.owner = owner;
        pool.remove(t);
        ready.add(new Ready(t, LuaValue.varargsOf(fn, args)));
        return t.co;
    }

    public void delay(double seconds, LuaValue fn, Varargs args, BaseScript owner) {
        timers.add(new Timer(now() + Math.max(0, seconds), fn, args, owner));
    }

    private void resume(Task t, Varargs args) {
        Task prev = current;
        current = t;
        Varargs r;
        try {
            r = t.co.resume(args);
        } finally {
            current = prev;
        }
        if (!r.arg1().toboolean()) {
            t.dead = true;
            byThread.remove(t.co);
            reportError(new LuaError(r.arg(2)), t.owner);
            return;
        }
        if ("dead".equals(t.co.getStatus())) {
            t.dead = true;
            byThread.remove(t.co);
            return;
        }
        LuaValue y = r.arg(2);
        if (y == IDLE) {
            t.owner = null;
            t.wakeAt = Double.NaN;
            if (pool.size() < 64) pool.push(t);
            else byThread.remove(t.co); // let it be collected
        } else if (y == WAIT) {
            sleeping.add(t);
        }
        // PARK: someone else holds a reference and will call resumeLater
    }

    /** The script thread currently executing, or null (e.g. user coroutine). */
    public Task currentTask() {
        LuaThread r = globals.running;
        return r == null ? null : byThread.get(r);
    }

    public BaseScript currentScript() {
        Task t = currentTask();
        if (t != null) return t.owner;
        return current == null ? null : current.owner;
    }

    /** wait()/task.wait(): yields the current script thread. Returns elapsed time. Lua side. */
    public Varargs waitSeconds(double seconds) {
        Task t = currentTask();
        if (t == null) throw new LuaError("attempt to yield outside of a script thread (use task.spawn)");
        double now = now();
        t.sleepStart = now;
        t.wakeAt = now + Math.max(seconds, 1.0 / 60.0);
        return globals.yield(WAIT);
    }

    /** Park the current script thread until {@link #resumeLater} is called. Lua side. */
    public Varargs park() {
        Task t = currentTask();
        if (t == null) throw new LuaError("attempt to yield outside of a script thread (use task.spawn)");
        return globals.yield(PARK);
    }

    public void resumeLater(Task t, Varargs args) {
        if (t != null && !t.dead) ready.add(new Ready(t, args));
    }

    public void cancel(LuaThread co) {
        Task t = byThread.get(co);
        if (t == null) return;
        t.dead = true;
        byThread.remove(co);
        sleeping.remove(t);
        pool.remove(t);
    }

    /** Kill everything owned by a script (script stopped / destroyed). Lua side. */
    public void killScript(BaseScript s) {
        for (Task t : new ArrayList<>(byThread.values())) {
            if (t.owner == s && t != current) {
                t.dead = true;
                byThread.remove(t.co);
                sleeping.remove(t);
                pool.remove(t);
            }
        }
        timers.removeIf(t -> t.owner == s);
        ready.removeIf(r -> r.task.owner == s);
    }

    public int threadCount() { return byThread.size(); }

    public int sleepingCount() { return sleeping.size() + timers.size(); }

    // =====================================================================================
    //  Running code
    // =====================================================================================

    /**
     * A per-script global environment (so `script` and script globals are private), falling back to the
     * shared globals. It must be a Globals: LuaJ only runs debug hooks (our timeout) for closures whose
     * environment is a Globals.
     */
    public Globals newScriptEnv() {
        Globals env = new Globals();
        LuaTable meta = new LuaTable();
        meta.set("__index", globals);
        env.setmetatable(meta);
        env.debuglib = globals.debuglib;
        env.compiler = globals.compiler;
        return env;
    }

    /** Compile Luau source into a function with the given chunk name. Lua side. */
    public LuaValue compile(String source, String chunkName, LuaTable env) {
        String lua = Luau.translate(source);
        return globals.load(lua, chunkName, env == null ? globals : env);
    }

    /** Execute a command-bar / console snippet. Game thread. */
    public void execute(String source, String chunkName) {
        runSync(() -> {
            LuaValue fn;
            try {
                // allow "= expr" / plain expressions like a REPL
                String src = source.trim();
                if (src.startsWith("=")) src = "print(" + src.substring(1) + ")";
                try {
                    fn = compile("return " + src, chunkName, null);
                    String finalSrc = src;
                    LuaValue exprFn = fn;
                    fn = new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs a) {
                            Varargs r = exprFn.invoke();
                            if (r.narg() > 0 && !(finalSrc.contains("(") && r.narg() == 1 && r.arg1().isnil())) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 1; i <= r.narg(); i++) {
                                    if (i > 1) sb.append("  ");
                                    sb.append(RbxLib.tostring(r.arg(i)));
                                }
                                log.add(LogBuffer.OUTPUT, sb.toString());
                            }
                            return NONE;
                        }
                    };
                } catch (LuaError e) {
                    fn = compile(src, chunkName, null);
                }
            } catch (LuaError e) {
                log.add(LogBuffer.ERROR, formatError(e.getMessage()));
                return;
            }
            spawn(fn, LuaValue.NONE, null);
        });
    }

    /** LuaJ writes "chunk:12 message"; Roblox writes "chunk:12: message". */
    public static String formatError(String msg) {
        if (msg == null) return "error";
        return msg.replaceFirst("^(\\S+:\\d+) ", "$1: ");
    }

    public void reportError(LuaError e, BaseScript owner) {
        String msg = e.getMessage() == null ? "error" : e.getMessage();
        String[] lines = msg.split("\n");
        log.add(LogBuffer.ERROR, formatError(lines[0]));
        // LuaJ traceback lines look like "\tChunk.Name:12: in function <...>"; show them Roblox-style
        List<String> stack = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\S+):(\\d+):").matcher(lines[i]);
            if (m.find()) stack.add("Script '" + m.group(1) + "', Line " + m.group(2));
        }
        if (stack.isEmpty() && owner != null) stack.add("Script '" + owner.getFullName() + "'");
        if (!stack.isEmpty()) {
            log.add(LogBuffer.INFO, "Stack Begin");
            for (String l : stack) log.add(LogBuffer.INFO, l);
            log.add(LogBuffer.INFO, "Stack End");
        }
    }

    // =====================================================================================
    //  Globals / sandbox
    // =====================================================================================

    private final class TimeoutDebugLib extends DebugLib {
        @Override
        public void onInstruction(int pc, Varargs v, int top) {
            if (abort) {
                abort = false;
                throw new LuaError("Script timeout: exhausted allowed execution time");
            }
            super.onInstruction(pc, v, top);
        }
    }

    private Globals createGlobals() {
        Globals g = new Globals();
        g.load(new JseBaseLib());
        g.load(new PackageLib()); // libraries register themselves in package.loaded while loading
        g.load(new Bit32Lib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new CoroutineLib());
        g.load(new JseMathLib());
        g.load(new TimeoutDebugLib());
        LuaC.install(g); // text chunks only: no undumper, so binary chunks cannot be loaded
        // sandbox: no file system access
        g.set("package", LuaValue.NIL); // file-based require; RbxLib installs Roblox's require(ModuleScript)
        g.set("require", LuaValue.NIL);
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        LuaValue debug = g.get("debug");
        LuaTable safeDebug = new LuaTable();
        safeDebug.set("traceback", debug.get("traceback"));
        safeDebug.set("info", debug.get("getinfo"));
        g.set("debug", safeDebug);
        return g;
    }
}
