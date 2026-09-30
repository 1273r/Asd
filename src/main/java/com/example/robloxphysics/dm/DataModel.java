package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.lua.LuaRuntime;
import com.example.robloxphysics.lua.RbxLib;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Roblox `game` object for one side (server or client). Game thread only unless noted. */
public abstract class DataModel {
    public static final ClassInfo ROOT_CLASS = ClassInfo.define("DataModel", Instance.CLASS, null)
            .prop("PlaceId", PT.INT, (Root r) -> 0, null)
            .prop("GameId", PT.INT, (Root r) -> 0, null)
            .prop("JobId", PT.STRING, (Root r) -> r.dm.jobId, null)
            .prop("CreatorId", PT.INT, (Root r) -> 0, null)
            .method("GetService", (self, a) -> {
                Instance s = self.dm.service(a.checkjstring(1));
                if (s == null) throw new LuaError("'" + a.checkjstring(1) + "' is not a valid Service name");
                return RbxLib.toLua(s);
            })
            .method("FindService", (self, a) -> RbxLib.toLua(self.dm.service(a.checkjstring(1))))
            .method("IsLoaded", (self, a) -> LuaValue.TRUE)
            .method("BindToClose", (self, a) -> {
                self.dm.closeCallbacks.add(a.checkfunction(1));
                return LuaValue.NONE;
            })
            .event("Loaded");

    static {
        // make sure every class has registered its ClassInfo (Instance.new looks classes up by name)
        Object[] all = {Instance.CLASS, Container.FOLDER, Model.CLASS, Workspace.CLASS, Players.CLASS, Player.CLASS,
                BasePart.CLASS, EntityPart.CLASS, Humanoid.CLASS, BaseScript.SCRIPT, BaseScript.LOCAL_SCRIPT,
                ModuleScript.CLASS, RunService.CLASS, Selection.CLASS, StarterPlayer.CLASS};
        if (all.length == 0) throw new AssertionError();
    }

    /** The `game` instance. */
    public static final class Root extends Instance {
        Root(DataModel dm) {
            super(dm, ROOT_CLASS);
            this.name = "Game";
            this.parentLocked = true;
        }
    }

    public final boolean server;
    public final LuaRuntime rt;
    public final LogBuffer log;
    public final Root game;
    public Workspace workspace;
    public Players players;
    public Container replicatedStorage, serverScriptService, serverStorage, starterPlayerScripts;
    public StarterPlayer starterPlayer;
    public RunService runService;
    public Selection selection;
    private final Map<String, Instance> services = new LinkedHashMap<>();

    public final String jobId = java.util.UUID.randomUUID().toString();
    private long nextId = 1;
    public long version;
    public int tickCount;
    public double gravity = 196.2;
    final List<LuaValue> closeCallbacks = new ArrayList<>();
    public final Map<ModuleScript, LuaValue> moduleCache = Collections.synchronizedMap(new IdentityHashMap<>());

    final Map<Entity, CharacterModel> characters = new IdentityHashMap<>();
    private int lastCharacterRefresh = -1;

    protected DataModel(boolean server, LogBuffer log) {
        this.server = server;
        this.log = log;
        this.rt = new LuaRuntime(server ? "Server" : "Client", log);
        this.rt.dm = this;
        this.game = new Root(this);
        try {
            gravity = com.example.robloxphysics.Config.GRAVITY.get();
        } catch (Throwable ignored) {}
        buildServices();
        RbxLib.install(rt, this);
    }

    protected void buildServices() {
        workspace = addService(new Workspace(this));
        players = addService(new Players(this));
        replicatedStorage = addService(new Container(this, Container.REPLICATED_STORAGE));
        serverScriptService = addService(new Container(this, Container.SERVER_SCRIPT_SERVICE));
        serverStorage = addService(new Container(this, Container.SERVER_STORAGE));
        starterPlayer = addService(new StarterPlayer(this));
        starterPlayerScripts = new Container(this, Container.STARTER_PLAYER_SCRIPTS);
        starterPlayerScripts.attach(starterPlayer);
        starterPlayerScripts.parentLocked = true;
        // hidden services (not children of game until requested, like Roblox)
        runService = hiddenService(new RunService(this));
        selection = hiddenService(new Selection(this));
    }

    protected <T extends Instance> T addService(T s) {
        s.attach(game);
        s.parentLocked = true;
        services.put(s.cls.name, s);
        return s;
    }

    protected <T extends Instance> T hiddenService(T s) {
        s.parentLocked = true;
        services.put(s.cls.name, s);
        return s;
    }

    public Instance service(String name) {
        Instance s = services.get(name);
        if (s != null && s.parent == null && !(s instanceof Workspace)) {
            // GetService creates the service in the tree the first time, like Roblox
            s.parentLocked = false;
            s.attach(game);
            s.parentLocked = true;
        }
        return s;
    }

    public String newId() { return "i" + (nextId++); }

    public void reserveId(String id) {
        if (id != null && id.startsWith("i")) {
            try {
                long n = Long.parseLong(id.substring(1));
                if (n >= nextId) nextId = n + 1;
            } catch (NumberFormatException ignored) {}
        }
    }

    public void markDirty(Instance i) { version++; }

    // ------------------------------------------------------------------ world access (side specific)

    public abstract Collection<? extends Level> levels();

    public abstract Level defaultLevel();

    public abstract List<? extends net.minecraft.world.entity.player.Player> mcPlayers();

    /** Workspace.Gravity changed. */
    public abstract void onGravityChanged();

    /** A humanoid property of a player changed (server: replicate to that client). */
    public abstract void onPlayerHumanoidChanged(Player p);

    /** StarterPlayer camera settings changed (server: replicate to clients). */
    public abstract void onCameraSettingsChanged();

    public abstract int maxPlayers();

    public abstract boolean isLocalPlayer(net.minecraft.world.entity.player.Player p);

    public void onCharacterAdded(Player p, CharacterModel m) {}

    public double gravity() { return gravity; }

    public Level levelByName(String dim) {
        for (Level l : levels()) if (l.dimension().location().toString().equals(dim)) return l;
        return defaultLevel();
    }

    // ------------------------------------------------------------------ characters (LivingEntity models)

    public void refreshCharacters() {
        if (lastCharacterRefresh == tickCount) return;
        lastCharacterRefresh = tickCount;
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Level level : levels()) {
            for (Entity e : entities(level)) {
                if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || !e.isAlive() || e.isRemoved()) continue;
                seen.add(e);
                if (!characters.containsKey(e)) {
                    CharacterModel m = new CharacterModel(this, le);
                    characters.put(e, m);
                    m.parentLocked = false;
                    m.setParent(workspace);
                    m.parentLocked = true;
                    if (e instanceof net.minecraft.world.entity.player.Player mp) {
                        Player p = players.byUuid(mp.getUUID());
                        if (p != null) p.characterAdded(m);
                    }
                }
            }
        }
        for (Iterator<Map.Entry<Entity, CharacterModel>> it = characters.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, CharacterModel> en = it.next();
            if (!seen.contains(en.getKey())) {
                it.remove();
                en.getValue().entityGone();
            }
        }
    }

    protected abstract Iterable<Entity> entities(Level level);

    public CharacterModel characterOf(Entity e) {
        refreshCharacters();
        return characters.get(e);
    }

    // ------------------------------------------------------------------ lookup

    public Instance findById(String id) {
        if (id == null) return null;
        if (id.equals(game.id)) return game;
        for (Instance s : services.values()) {
            if (s.id.equals(id)) return s;
        }
        refreshCharacters();
        return find(game, id);
    }

    private static Instance find(Instance root, String id) {
        for (Instance c : root.getChildren()) {
            if (id.equals(c.id)) return c;
            Instance f = find(c, id);
            if (f != null) return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ ticking

    /** Called every game tick on the game thread. */
    public void tick() {
        tickCount++;
        refreshCharacters();
        players.refresh();
        double dt = 0.05;
        for (CharacterModel m : new ArrayList<>(characters.values())) m.humanoid.tick();
        rt.step(() -> {
            if (runService.stepped.hasListeners()) runService.stepped.fireNow(LuaValue.varargsOf(LuaValue.valueOf(rt.now()), LuaValue.valueOf(dt)));
            if (runService.heartbeat.hasListeners()) runService.heartbeat.fireNow(LuaValue.valueOf(dt));
            if (runService.postSimulation.hasListeners()) runService.postSimulation.fireNow(LuaValue.valueOf(dt));
        });
    }

    public void shutdown() {
        if (!closeCallbacks.isEmpty()) {
            rt.runSync(() -> {
                for (LuaValue f : closeCallbacks) {
                    try { f.call(); } catch (LuaError e) { rt.reportError(e, null); }
                }
            });
        }
        rt.shutdown();
    }
}
