package com.example.robloxphysics.dm;

import com.example.robloxphysics.dm.ClassInfo.PT;
import com.example.robloxphysics.lua.RbxLib;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Players extends Instance {
    public static final ClassInfo CLASS = ClassInfo.define("Players", Instance.CLASS, null)
            .prop("LocalPlayer", PT.INSTANCE, (Players p) -> p.localPlayer, null)
            .prop("MaxPlayers", PT.INT, (Players p) -> p.dm.maxPlayers(), null)
            .method("GetPlayers", (self, a) -> RbxLib.list(((Players) self).playerList()))
            .method("GetPlayerFromCharacter", (self, a) -> {
                Instance c = RbxLib.optInstance(a.arg(1));
                for (Player p : ((Players) self).playerList()) if (c != null && p.character() == c) return RbxLib.toLua(p);
                return LuaValue.NIL;
            })
            .method("GetPlayerByUserId", (self, a) -> {
                long id = a.checklong(1);
                for (Player p : ((Players) self).playerList()) if (p.userId() == id) return RbxLib.toLua(p);
                return LuaValue.NIL;
            })
            .event("PlayerAdded").event("PlayerRemoving");

    public Player localPlayer;

    public Players(DataModel dm) {
        super(dm, CLASS);
        this.name = "Players";
    }

    public List<Player> playerList() {
        List<Player> out = new ArrayList<>();
        for (Instance c : children) if (c instanceof Player p) out.add(p);
        return out;
    }

    public Player byUuid(UUID id) {
        for (Player p : playerList()) if (p.uuid.equals(id)) return p;
        return null;
    }

    /** Sync Player instances with the actual player list. */
    public void refresh() {
        Set<UUID> present = new HashSet<>();
        for (net.minecraft.world.entity.player.Player mp : dm.mcPlayers()) {
            present.add(mp.getUUID());
            if (byUuid(mp.getUUID()) == null) {
                Player p = new Player(dm, mp.getUUID(), mp.getGameProfile().getName());
                if (!dm.server && dm.isLocalPlayer(mp)) {
                    localPlayer = p;
                    p.settings = HumanoidSettings.LOCAL;
                }
                p.setParent(this);
                fireEvent("PlayerAdded", p);
                CharacterModel ch = dm.characterOf(mp);
                if (ch != null) p.characterAdded(ch);
            }
        }
        for (Player p : playerList()) {
            if (!present.contains(p.uuid)) {
                fireEvent("PlayerRemoving", p);
                p.parentLocked = false;
                p.destroy();
                if (p == localPlayer) localPlayer = null;
            }
        }
    }
}
