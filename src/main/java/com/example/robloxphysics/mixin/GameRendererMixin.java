package com.example.robloxphysics.mixin;

import com.example.robloxphysics.client.RobloxCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With a free cursor, the targeted block/entity is whatever is under the mouse, not the screen center. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void robloxphysics$pick(float partialTick, CallbackInfo ci) {
        RobloxCamera.pickUnderCursor();
    }
}
