package dev.jetstreams.client.mixin;

import dev.jetstreams.client.mixinhooks.SkyTint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps horizon fog consistent with the altitude-darkened sky: every environment attribute
 * probe in {@code getBaseColor} passes through here; only SKY_COLOR is tinted.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogEnvironmentMixin {

    @Redirect(method = "getBaseColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;"))
    private Object jetstreams$tintFogSkyColor(EnvironmentAttributeProbe probe,
                                              EnvironmentAttribute<?> attribute, float partialTicks,
                                              ClientLevel level, Camera camera,
                                              int renderDistance, float basePartialTicks) {
        Object value = probe.getValue(attribute, partialTicks);
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            value = SkyTint.apply(level, (Integer) value, camera.position());
        }
        return value;
    }
}
