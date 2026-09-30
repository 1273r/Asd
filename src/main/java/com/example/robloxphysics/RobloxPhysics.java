package com.example.robloxphysics;

import com.example.robloxphysics.net.Net;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(RobloxPhysics.MODID)
public class RobloxPhysics {
    public static final String MODID = "robloxphysics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RobloxPhysics(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(Net::register);
    }
}
