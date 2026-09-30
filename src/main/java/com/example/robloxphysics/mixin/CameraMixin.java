package com.example.robloxphysics.mixin;

import com.example.robloxphysics.client.ClientScripting;
import com.example.robloxphysics.client.RobloxCamera;
import com.example.robloxphysics.rbx.CFrame;
import com.example.robloxphysics.rbx.Units;
import com.example.robloxphysics.rbx.V3;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shift-lock shoulder offset and Camera.CameraType = Scriptable. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void move(float forward, float up, float right);

    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void robloxphysics$setup(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
        CFrame scripted = ClientScripting.scriptedCameraCFrame();
        if (scripted != null) {
            V3 look = scripted.lookVector();
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x(), look.z()));
            float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, look.y()))));
            setRotation(yaw, pitch, 0);
            setPosition(Units.toBlocks(scripted.position()));
            this.detached = true;
            return;
        }
        float off = RobloxCamera.shoulderOffset();
        if (off != 0 && detached) move(0, 0, off);
    }
}
