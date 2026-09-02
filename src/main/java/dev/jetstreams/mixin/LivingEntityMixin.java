package dev.jetstreams.mixin;

import dev.jetstreams.physics.FlightPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies jet stream physics (wind push, cruise lift, vector locking, inertia) at the tail
 * of elytra flight. Runs identically on client and server, so prediction matches authority.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "travelFallFlying", at = @At("TAIL"))
    private void jetstreams$afterTravelFallFlying(Vec3 input, CallbackInfo ci) {
        FlightPhysics.apply((LivingEntity) (Object) this);
    }
}
