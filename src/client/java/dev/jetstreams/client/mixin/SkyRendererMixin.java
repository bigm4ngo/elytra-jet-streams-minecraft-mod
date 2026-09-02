package dev.jetstreams.client.mixin;

import dev.jetstreams.client.mixinhooks.SkyTint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Darkens the sky exponentially with camera altitude while the stream system is active. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void jetstreams$tintSky(ClientLevel level, float partialTicks, Camera camera,
                                    SkyRenderState state, CallbackInfo ci) {
        if (state.skybox == Skybox.NONE) {
            return;
        }
        state.skyColor = SkyTint.apply(level, state.skyColor, camera.position());
    }
}
