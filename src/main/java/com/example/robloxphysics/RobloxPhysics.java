package com.example.robloxphysics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(RobloxPhysics.MODID)
public class RobloxPhysics {
    public static final String MODID = "robloxphysics";

    public RobloxPhysics(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
