package dev.jetstreams.mixin;

import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.WindField;
import dev.jetstreams.physics.FlightPhysics;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Altitude-scaled firework boost.
 *
 * <p>Vanilla boost per tick is {@code look*0.1 + (look*1.5 - v)*0.5}. We capture the
 * player's velocity before/after the firework tick and replace the applied delta with
 * {@link FlightPhysics#fireworkBoostDelta}: thrust and target speed scale with
 * {@code exp(0.00032*(y-300))} (clamped at y=8000), while the velocity blend stays vanilla.
 * At multiplier 1.0 the replacement is bit-identical to vanilla; above that, terminal speed
 * scales linearly with the multiplier (≈390 b/s at y=8000) instead of saturating at ~34 b/s.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Shadow
    private @Nullable LivingEntity attachedToEntity;

    @Unique
    private boolean jetstreams$record;
    @Unique
    private Vec3 jetstreams$preVel = Vec3.ZERO;
    @Unique
    private Vec3 jetstreams$look = Vec3.ZERO;

    @Inject(method = "tick", at = @At("HEAD"))
    private void jetstreams$beforeTick(CallbackInfo ci) {
        this.jetstreams$record = false;
        LivingEntity target = this.attachedToEntity;
        if (target != null && target.isFallFlying()) {
            this.jetstreams$record = true;
            this.jetstreams$preVel = target.getDeltaMovement();
            this.jetstreams$look = target.getLookAngle();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void jetstreams$afterTick(CallbackInfo ci) {
        if (!this.jetstreams$record) {
            return;
        }
        this.jetstreams$record = false;
        LivingEntity target = this.attachedToEntity;
        if (target == null || !target.isFallFlying()) {
            return;
        }
        Level level = target.level();
        JetStreamsConfig.Physics p = PhysicsResolver.activeFor(level);
        if (!WindField.enabledFor(level, p)) {
            return;
        }
        Vec3 post = target.getDeltaMovement();
        Vec3 vanillaDelta = post.subtract(this.jetstreams$preVel);
        if (vanillaDelta.lengthSqr() < 1.0e-9) {
            return; // no boost was applied this tick
        }
        double multiplier = WindField.fireworkMultiplier(p, target.getY());
        Vec3 scaled = FlightPhysics.fireworkBoostDelta(
                this.jetstreams$look, this.jetstreams$preVel, multiplier, p.fireworkKickPerTick);
        target.setDeltaMovement(post.subtract(vanillaDelta).add(scaled));

        if (!level.isClientSide() && target instanceof ServerPlayer player) {
            FlightState st = FlightStateTracker.get(level, player.getUUID());
            st.lastBoostTick = level.getGameTime();
            // Vanilla-shaped terminal estimate: v* = 1.6666 * multiplier blocks/tick.
            double terminalBps = 1.6666 * multiplier * 20.0;
            st.authorizedSpeedBps = Math.max(st.authorizedSpeedBps, terminalBps + 45.0);
        }
    }
}
