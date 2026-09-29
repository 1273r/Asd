package com.example.robloxphysics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = RobloxPhysics.MODID)
public class CommonEvents {
    private static final ResourceLocation GRAVITY_MOD =
            ResourceLocation.fromNamespaceAndPath(RobloxPhysics.MODID, "roblox_gravity");
    private static final double VANILLA_GRAVITY = 0.08; // blocks/tick^2

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!Config.ENABLED.get() || !Config.DISABLE_FALL_DAMAGE.get()) return;
        if (event.getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    /** Players are handled by ClientPhysics; everything else just gets a scaled gravity attribute. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!Config.ENABLED.get() || !Config.SCALE_MOB_GRAVITY.get()) return;
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;

        AttributeInstance attr = living.getAttribute(Attributes.GRAVITY);
        if (attr == null || attr.hasModifier(GRAVITY_MOD)) return;

        double robloxGravity = Config.GRAVITY.get() * Config.STUD_SIZE.get() / 400.0; // blocks/tick^2
        double ratio = robloxGravity / VANILLA_GRAVITY;
        attr.addTransientModifier(new AttributeModifier(
                GRAVITY_MOD, ratio - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
