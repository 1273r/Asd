package com.example.robloxphysics.server;

import com.example.robloxphysics.dm.BaseScript;
import com.example.robloxphysics.dm.CharacterModel;
import com.example.robloxphysics.dm.DataModel;
import com.example.robloxphysics.dm.Player;
import com.example.robloxphysics.dm.ScriptFiles;
import com.example.robloxphysics.lua.LogBuffer;
import com.example.robloxphysics.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ServerDataModel extends DataModel {
    public final MinecraftServer server;
    public final ScriptFiles scriptFiles;

    public ServerDataModel(MinecraftServer server, LogBuffer log) {
        super(true, log);
        this.server = server;
        this.scriptFiles = new ScriptFiles(this,
                server.getWorldPath(LevelResource.ROOT).resolve("scripts").resolve("server"), BaseScript.SCRIPT);
    }

    @Override
    public Collection<? extends Level> levels() {
        List<Level> out = new ArrayList<>();
        server.getAllLevels().forEach(out::add);
        return out;
    }

    @Override
    public Level defaultLevel() { return server.overworld(); }

    @Override
    public List<? extends net.minecraft.world.entity.player.Player> mcPlayers() { return server.getPlayerList().getPlayers(); }

    @Override
    protected Iterable<Entity> entities(Level level) { return ((ServerLevel) level).getAllEntities(); }

    @Override
    public int maxPlayers() { return server.getMaxPlayers(); }

    @Override
    public boolean isLocalPlayer(net.minecraft.world.entity.player.Player p) { return false; }

    @Override
    public void onGravityChanged() {
        for (Player p : players.playerList()) {
            p.settings.gravity = gravity;
            onPlayerHumanoidChanged(p);
        }
    }

    @Override
    public void onCameraSettingsChanged() {
        for (Player p : players.playerList()) {
            p.settings.maxZoom = starterPlayer.maxZoom;
            p.settings.minZoom = starterPlayer.minZoom;
            p.settings.mouseLockOption = starterPlayer.mouseLock;
            onPlayerHumanoidChanged(p);
        }
    }

    @Override
    public void onPlayerHumanoidChanged(Player p) {
        if (p.mc() instanceof ServerPlayer sp) {
            Net.send(sp, Net.humanoidJson(p.settings));
            p.settings.jumpRequest = false;
        }
    }

    @Override
    public void onCharacterAdded(Player p, CharacterModel m) {
        onPlayerHumanoidChanged(p);
    }

    public void onJoin(ServerPlayer sp) {
        players.refresh();
        Player p = players.byUuid(sp.getUUID());
        if (p != null) onPlayerHumanoidChanged(p);
    }

    public void reloadScripts() {
        scriptFiles.reload(serverScriptService);
    }
}
