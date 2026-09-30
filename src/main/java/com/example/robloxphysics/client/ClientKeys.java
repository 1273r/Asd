package com.example.robloxphysics.client;

import com.example.robloxphysics.RobloxPhysics;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RobloxPhysics.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientKeys {
    private ClientKeys() {}

    private static final String CATEGORY = "key.categories.robloxphysics";

    public static final KeyMapping DEV_CONSOLE = new KeyMapping("key.robloxphysics.dev_console",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);
    public static final KeyMapping SHIFT_LOCK = new KeyMapping("key.robloxphysics.shift_lock",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent e) {
        e.register(DEV_CONSOLE);
        e.register(SHIFT_LOCK);
    }
}
