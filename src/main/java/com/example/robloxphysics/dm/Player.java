package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.RbxLib;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.UUID;

public class Player extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Player", Instance.CLASS, null)
            .prop("DisplayName", PT.STRING, (Player p) -> p.name, null)
            .prop("UserId", PT.INT, (Player p) -> (double) p.userId(), null)
            .prop("Character", PT.INSTANCE, (Player p) -> p.character(), null)
            .prop("AccountAge", PT.INT, (Player p) -> 0.0, null)
            .method("Kick", (self, a) -> {
                Player p = (Player) self;
                if (!p.dm.server) throw new LuaError("Kick can only be called from the server (or on LocalPlayer)");
                if (p.mc() instanceof ServerPlayer sp) {
                    String msg = a.optjstring(1, "");
                    sp.connection.disconnect(Component.literal(msg.isEmpty() ? "You have been kicked from the game" : msg));
                }
                return LuaValue.NONE;
            })
            .method("LoadCharacter", (self, a) -> {
                Player p = (Player) self;
                if (!p.dm.server) throw new LuaError("LoadCharacter can only be called by the backend server");
                if (p.mc() instanceof ServerPlayer sp && !sp.isAlive()) {
                    sp.server.getPlayerList().respawn(sp, false, Entity.RemovalReason.KILLED);
                }
                return LuaValue.NONE;
            })
            .method("DistanceFromCharacter", (self, a) -> {
                Entity e = ((Player) self).mc();
                if (e == null) return LuaValue.valueOf(0);
                V3 pos = RbxLib.checkV3(a.arg(1));
                return LuaValue.valueOf(Units.toStuds(e.position()).sub(pos).length());
            })
            .event("Chatted").event("CharacterAdded").event("CharacterRemoving");

    public final UUID uuid;
    public HumanoidSettings settings = new HumanoidSettings();
    public final Container playerScripts;
    private CharacterModel lastCharacter;

    public Player(DataModel dm, UUID uuid, String name) {
        super(dm, CLASS);
        this.uuid = uuid;
        this.name = name;
        this.id = "p" + uuid;
        this.parentLocked = false;
        HumanoidSettings s = settings;
        StarterPlayer sp = dm.starterPlayer;
        s.walkSpeed = sp.walkSpeed;
        s.jumpHeight = sp.jumpHeight;
        s.jumpPower = sp.jumpPower;
        s.useJumpPower = sp.useJumpPower;
        s.maxZoom = sp.maxZoom;
        s.minZoom = sp.minZoom;
        s.mouseLockOption = sp.mouseLock;
        s.gravity = dm.gravity;
        playerScripts = new Container(dm, Container.PLAYER_SCRIPTS);
        playerScripts.name = "PlayerScripts";
        playerScripts.attach(this);
        playerScripts.parentLocked = true;
    }

    public long userId() {
        return Math.abs(uuid.getMostSignificantBits() % 10_000_000_000L);
    }

    public net.minecraft.world.entity.player.Player mc() {
        for (net.minecraft.world.entity.player.Player p : dm.mcPlayers()) if (p.getUUID().equals(uuid)) return p;
        return null;
    }

    public CharacterModel character() {
        Entity e = mc();
        if (e == null || !e.isAlive()) return null;
        return dm.characterOf(e);
    }

    void characterAdded(CharacterModel m) {
        if (m == lastCharacter) return;
        if (lastCharacter != null) fireEvent("CharacterRemoving", lastCharacter);
        lastCharacter = m;
        changed("Character");
        fireEvent("CharacterAdded", m);
        dm.onCharacterAdded(this, m);
    }

    public void chatted(String msg) { fireEvent("Chatted", msg); }

    /** Called when a humanoid movement property changes. */
    public void settingsChanged() { dm.onPlayerHumanoidChanged(this); }
}
