package dev.jetstreams.physics;

import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.RegionType;
import dev.jetstreams.field.WindField;
import dev.jetstreams.field.WindSample;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The heart of the mod: stream wind acceleration, one-way vector locking at crossings,
 * altitude-scaled no-rocket cruise, headwind drag and high-speed turn inertia.
 *
 * <p>Runs at the tail of elytra travel on <b>both</b> sides (client prediction + server
 * authority) with identical deterministic inputs, so flight stays smooth without extra
 * packets. All vertical units below are blocks/tick unless suffixed Bps.
 */
public final class FlightPhysics {
    private FlightPhysics() {}

    /** Called from {@code LivingEntityMixin} at the tail of {@code travelFallFlying}. */
    public static void apply(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Level level = entity.level();
        JetStreamsConfig.Physics p = PhysicsResolver.activeFor(level);
        FlightState st = FlightStateTracker.get(level, player.getUUID());

        // Turn-rate bookkeeping happens every tick, even outside streams.
        float yaw = entity.getYRot();
        float dyaw = Math.abs(Mth.wrapDegrees(yaw - st.prevYaw));
        st.prevYaw = yaw;
        st.turnRate = st.turnRate * 0.75 + dyaw * 0.25;

        if (!entity.isFallFlying()) {
            st.inStream = false;
            st.cruising = false;
            st.headwind = false;
            st.headwindFactor *= 0.8;
            if (!level.isClientSide()) {
                st.authorizedSpeedBps *= 0.9;
            }
            return;
        }
        // On clients, wait for the server's physics/seed sync so prediction matches authority.
        if (level.isClientSide() && !RemotePhysics.hasFieldSeed()) {
            return;
        }
        if (!WindField.enabledFor(level, p)) {
            return;
        }
        double y = entity.getY();
        if (y < p.activationAltitude) {
            st.inStream = false;
            st.cruising = false;
            st.headwind = false;
            st.headwindFactor *= 0.8;
            return;
        }

        WindSample s = WindField.sample(level, entity.getX(), y, entity.getZ(), p);
        Vec3 vel = entity.getDeltaMovement();
        double vx = vel.x;
        double vy = vel.y;
        double vz = vel.z;
        double horizBps = Math.hypot(vx, vz) * 20.0;
        final double dt = 0.05;

        int family = selectFamily(st, s, vx, vz);
        double profile = family >= 0 ? s.profile(family) : 0.0;
        double wx = family >= 0 ? s.flowX(family) : 0.0;
        double wz = family >= 0 ? s.flowZ(family) : 0.0;
        st.inStream = profile > 0.0;
        st.windX = wx;
        st.windZ = wz;
        st.windProfile = profile;

        // --- one-way gating: stream speed applies only ALONG the current ---
        // The cosine between the travel direction and the flow drives a smoothstep factor
        // that scales the firework multiplier; against/across the stream it collapses to 0
        // (vanilla rocket physics) while the existing headwind drag punishes fighting it.
        double vhPre = Math.hypot(vx, vz);
        double dot = vhPre > 0.5 ? Mth.clamp((vx * wx + vz * wz) / vhPre, -1.0, 1.0) : 1.0;
        st.alignment = dot;
        double a = (dot - p.boostMinAlignment) / Math.max(1e-6, p.boostFullAlignment - p.boostMinAlignment);
        a = Mth.clamp(a, 0.0, 1.0);
        st.alignmentFactor = profile > 0.0 ? a * a * (3.0 - 2.0 * a) : 0.0;
        st.flowDir = profile > 0.0 ? flowDirIndex(wx, wz) : -1;

        if (s.region() == RegionType.CROSSING && st.dominantFamily >= 0) {
            updateLock(st, entity, s, horizBps, p, dt);
        } else {
            st.switchProgress = Math.max(0.0, st.switchProgress - dt * 2.0);
        }

        boolean boosting = st.isBoosting(level.getGameTime());

        if (profile > 0.0) {
            // --- stream push: accelerate along the current, capped at windBoostSpeed ---
            double tail = vx * wx + vz * wz;
            double target = (p.windBoostSpeed / 20.0) * profile;
            double gain = p.windAccelPerSecondSq / 400.0; // b/t per tick
            double add = Math.min(gain, Math.max(0.0, target - tail));
            vx += wx * add;
            vz += wz * add;

            // --- headwind: fighting the current costs speed ---
            st.headwind = tail < -0.1 * Math.max(0.02, Math.hypot(vx, vz));
            if (st.headwind) {
                double drag = 1.0 - p.headwindDragPerTick * profile;
                vx *= drag;
                vz *= drag;
                st.headwindFactor = Math.min(1.0, st.headwindFactor + 0.1 * profile);
            } else {
                st.headwindFactor *= 0.85;
            }

            // --- inertia: sharp turns bleed speed at high velocity ---
            if (horizBps > 60.0 && st.turnRate > p.turnAngleThresholdDeg) {
                double t = Math.min(st.turnRate, 30.0) / 30.0;
                double f = 1.0 - (p.turnDrag * 0.05) * t;
                vx *= f;
                vz *= f;
            }
        } else {
            st.headwind = false;
            st.headwindFactor *= 0.85;
        }

        // --- no-rocket cruise: inside stream cores (fast, along-flow) or in neutral
        // --- zones up high (slow, any direction)
        st.cruising = false;
        st.cruiseKind = 0;
        boolean sneaking = player.isShiftKeyDown();
        boolean diving = entity.getXRot() > (float) p.divePitchDeg;
        // Cancel the gravity sag vanilla just applied at this pitch:
        // updateFallFlyingMovement adds gravity * (-1 + 0.75 * cos^2(pitch)).
        double pitchRad = Math.toRadians(entity.getXRot());
        double lift = Math.cos(pitchRad) * Math.cos(pitchRad);
        double sag = p.gravityCompensationPerTick * (-1.0 + lift * 0.75);
        if (profile >= p.coreProfileForCruise && y >= p.cruiseStartAltitude && !sneaking && !diving) {
            double vh = Math.hypot(vx, vz);
            boolean withFlow = vh < 0.05 || (vx * wx + vz * wz) / vh > 0.35;
            if (withFlow) {
                vy = Mth.clamp((vy - sag) * 0.918, -0.025, 0.05);
                if (!boosting) {
                    // Pull the along-wind speed component toward the cruise curve.
                    double targetBt = WindField.cruiseSpeed(p, y) / 20.0;
                    double tail = vx * wx + vz * wz;
                    double delta = Mth.clamp((targetBt - tail) * p.cruiseGain, -0.2, 0.2);
                    vx += wx * delta;
                    vz += wz * delta;
                }
                // Stream axis lock: decay the cross-flow velocity component so cruise
                // speed can only be carried ALONG the current, never sideways across it.
                double along = vx * wx + vz * wz;
                double crossX = vx - wx * along;
                double crossZ = vz - wz * along;
                vx = wx * along + crossX * 0.90;
                vz = wz * along + crossZ * 0.90;
                st.cruising = true;
                st.cruiseKind = 1;
            }
        }
        if (!st.cruising && !s.hasWind() && y >= p.neutralCruiseStartAltitude
                && !sneaking && !diving) {
            // Neutral-zone cruise: no current exists here, so direction is free - but the
            // pace is much slower than tunnel cruise. Same gravity cancel, same escapes.
            vy = Mth.clamp((vy - sag) * 0.918, -0.025, 0.05);
            if (!boosting) {
                double vh = Math.hypot(vx, vz);
                double targetBt = WindField.neutralCruiseSpeed(p, y) / 20.0;
                if (vh > 0.05) {
                    // Pull the horizontal speed (any direction) toward the neutral curve.
                    double newVh = vh + Mth.clamp((targetBt - vh) * p.cruiseGain, -0.2, 0.2);
                    double scale = newVh / vh;
                    vx *= scale;
                    vz *= scale;
                } else {
                    // Standing still: nudge along the look direction so cruise can start.
                    Vec3 look = entity.getLookAngle();
                    double lh = Math.hypot(look.x, look.z);
                    if (lh > 0.05) {
                        double push = targetBt * p.cruiseGain;
                        vx += look.x / lh * push;
                        vz += look.z / lh * push;
                    }
                }
            }
            st.cruising = true;
            st.cruiseKind = 2;
        }
        if (sneaking) {
            // Sneak = gentle brake to descend out of the current.
            vx *= 0.98;
            vz *= 0.98;
        }

        entity.setDeltaMovement(vx, vy, vz);

        double newBps = Math.hypot(vx, vz) * 20.0;
        st.lastSpeedBps = newBps;
        st.hypersonic = newBps >= p.hypersonicSpeed;
        if (!level.isClientSide()) {
            st.authorizedSpeedBps = Math.max(newBps * 1.15 + 5.0, st.authorizedSpeedBps * 0.97);
        }
    }

    /**
     * Chooses which band family applies. In a crossing, keeps the locked family;
     * when unlocked, aligns with whichever current matches the player's velocity
     * (or the stronger wind when nearly stationary).
     */
    private static int selectFamily(FlightState st, WindSample s, double vx, double vz) {
        if (!s.ns() && !s.ew()) {
            st.dominantFamily = -1;
            st.switchProgress = 0.0;
            return -1;
        }
        if (s.ns() && s.ew()) {
            if (st.dominantFamily != 0 && st.dominantFamily != 1) {
                double vh = Math.hypot(vx, vz);
                if (vh < 0.05) {
                    st.dominantFamily = s.nsProfile() >= s.ewProfile() ? 0 : 1;
                } else {
                    double dNs = vx * s.nsFlowX() + vz * s.nsFlowZ();
                    double dEw = vx * s.ewFlowX() + vz * s.ewFlowZ();
                    st.dominantFamily = dNs >= dEw ? 0 : 1;
                }
            }
            return st.dominantFamily;
        }
        int family = s.ns() ? 0 : 1;
        st.dominantFamily = family;
        return family;
    }

    /**
     * Vector locking: the dominant stream carries the player through the crossing.
     * Steering into the other current for {@code captureSeconds} switches the lock;
     * a short cooldown afterwards prevents rapid flip-flopping.
     */
    private static void updateLock(FlightState st, LivingEntity entity, WindSample s,
                                   double speedBps, JetStreamsConfig.Physics p, double dt) {
        st.lockCooldown = Math.max(0.0, st.lockCooldown - dt);
        int other = st.dominantFamily == 0 ? 1 : 0;
        double otherFx = s.flowX(other);
        double otherFz = s.flowZ(other);
        Vec3 look = entity.getLookAngle();
        double lh = Math.hypot(look.x, look.z);
        boolean intent = false;
        if (lh > 0.01 && speedBps > 4.0) {
            double dot = Mth.clamp((look.x / lh) * otherFx + (look.z / lh) * otherFz, -1.0, 1.0);
            intent = Math.toDegrees(Math.acos(dot)) <= p.captureAngleDeg;
        }
        double capture = Math.max(0.05, p.captureSeconds);
        if (intent && st.lockCooldown <= 0.0) {
            st.switchProgress = Math.min(1.0, st.switchProgress + dt / capture);
        } else {
            st.switchProgress = Math.max(0.0, st.switchProgress - 2.0 * dt / capture);
        }
        if (st.switchProgress >= 1.0 && st.lockCooldown <= 0.0) {
            st.dominantFamily = other;
            st.switchProgress = 0.0;
            st.lockCooldown = p.captureCooldownSeconds;
        }
    }

    /** Maps a unit flow vector to a cardinal direction: 0=N(-Z), 1=S(+Z), 2=E(+X), 3=W(-X). */
    private static int flowDirIndex(double wx, double wz) {
        if (Math.abs(wx) >= Math.abs(wz)) {
            return wx >= 0.0 ? dev.jetstreams.registry.JetFlowOption.EAST
                             : dev.jetstreams.registry.JetFlowOption.WEST;
        }
        return wz >= 0.0 ? dev.jetstreams.registry.JetFlowOption.SOUTH
                         : dev.jetstreams.registry.JetFlowOption.NORTH;
    }

    /**
     * Firework boost delta for one tick, rewritten from vanilla's
     * {@code look*0.1 + (look*1.5 - v)*0.5} so the <i>target speed</i> scales with the
     * altitude multiplier instead of the blend fighting it (naively scaling the whole
     * delta would cap terminal speed at ~34 b/s no matter the multiplier).
     *
     * <p>At multiplier 1.0 this reproduces vanilla exactly; the kick clamp only softens
     * the huge initial blend delta at extreme altitudes into a sustained burn.
     */
    public static Vec3 fireworkBoostDelta(Vec3 look, Vec3 preVelocity, double multiplier, double maxKickBt) {
        double dx = look.x * 0.1 * multiplier + (look.x * 1.5 * multiplier - preVelocity.x) * 0.5;
        double dy = look.y * 0.1 * multiplier + (look.y * 1.5 * multiplier - preVelocity.y) * 0.5;
        double dz = look.z * 0.1 * multiplier + (look.z * 1.5 * multiplier - preVelocity.z) * 0.5;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > maxKickBt) {
            double scale = maxKickBt / len;
            dx *= scale;
            dy *= scale;
            dz *= scale;
        }
        return new Vec3(dx, dy, dz);
    }
}
