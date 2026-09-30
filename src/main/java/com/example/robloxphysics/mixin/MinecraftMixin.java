package com.example.robloxphysics.mixin;

import com.example.robloxphysics.client.RobloxCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla only keeps mining while the mouse is grabbed; allow it with the free Roblox cursor too. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean robloxphysics$continueAttack(boolean original) {
        Minecraft mc = (Minecraft) (Object) this;
        if (RobloxCamera.freeCursor()) return mc.options.keyAttack.isDown() && mc.player != null && !mc.player.isUsingItem();
        return original;
    }
}
