package dev.jetstreams.client.fx;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.TunnelField;
import dev.jetstreams.field.WindField;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import dev.jetstreams.registry.JetFlowOption;
import dev.jetstreams.registry.ModParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;

/**
 * Client-side FX spawner for the 3D tunnel field (v1.2.0). All positions derive from the
 * deterministic flow field, so visuals always agree with the physics with zero packets.
 *
 * <p>Three layers:
 * <ul>
 *   <li><b>Flow streaks</b> - tinted, flow-aligned chains drifting through the tunnel core;
 *       spawned only where a current exists, never in neutral air.</li>
 *   <li><b>Rim markers</b> - big, bright white particles lining the tunnel's elliptical
 *       border, biased along the player's own heading so the wall <i>ahead</i> is lit up
 *       before you hit it. When flying in neutral air near a tunnel, its near rim is lit so
 *       players can see (and aim for) the tunnel mouth.</li>
 *   <li><b>Cirrus</b> - huge soft billboards drifting inside big tunnels only.</li>
 * </ul>
 */
public final class StreamParticleSpawner {
    private static final RandomSource RANDOM = RandomSource.create();

    private StreamParticleSpawner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StreamParticleSpawner::tick);
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || client.isPaused()) {
            return;
        }
        JetStreamsConfig cfg = ConfigManager.get();
        JetStreamsConfig.Physics p = PhysicsResolver.activeFor(level);
        JetStreamsConfig.Client c = cfg.client;
        if (!p.enabled || (!c.directionParticles && !c.cirrusBands)) {
            return;
        }
        if (!WindField.enabledFor(level, p) || player.getY() < p.activationAltitude) {
            return;
        }

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        TunnelField.Hit hit = WindField.tunnelHit(level, px, py, pz, p);
        FlightState st = FlightStateTracker.get(level, player.getUUID());

        // ------------------------------------------------- flow streaks (inside tunnels)
        if (c.directionParticles && hit != null) {
            double wx = hit.flowX();
            double wz = hit.flowZ();
            int dir = flowDirOf(wx, wz);
            double rate = (flying(player) ? 0.9 : 0.45) * c.particleDensity * (0.5 + hit.profile());
            int count = (int) rate + (RANDOM.nextDouble() < (rate - (int) rate) ? 1 : 0);
            for (int i = 0; i < count; i++) {
                spawnStreakChain(level, hit, px, py, pz, wx, wz, dir);
            }
        }

        // --------------------------------------------------- rim markers (borders first)
        if (c.directionParticles) {
            TunnelField.Hit rim = hit != null ? hit : probeNear(level, player, px, py, pz, p);
            if (rim != null) {
                double rate = c.particleDensity * (hit != null ? 1.6 : 0.9)
                        * (flying(player) ? 1.0 : 0.6);
                int count = (int) rate + (RANDOM.nextDouble() < (rate - (int) rate) ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    spawnRimMarker(level, rim, px, py, pz, player, hit != null);
                }
            }
        }

        // ------------------------------------------------------------- cirrus (in tunnels)
        if (c.cirrusBands && hit != null && hit.halfH() >= 80.0 && level.getGameTime() % 8 == 0) {
            spawnCirrus(level, hit, px, pz, c);
        }
    }

    private static boolean flying(LocalPlayer player) {
        return player.isFallFlying();
    }

    private static int flowDirOf(double wx, double wz) {
        if (Math.abs(wx) >= Math.abs(wz)) {
            return wx >= 0.0 ? JetFlowOption.EAST : JetFlowOption.WEST;
        }
        return wz >= 0.0 ? JetFlowOption.SOUTH : JetFlowOption.NORTH;
    }

    /** Flow-aligned chain of 5 streaks near the player, biased ahead along the flow. */
    private static void spawnStreakChain(ClientLevel level, TunnelField.Hit hit,
                                         double px, double py, double pz,
                                         double wx, double wz, int dir) {
        double ahead = 4.0 + RANDOM.nextDouble() * 22.0;
        // Lateral spread stays inside the tunnel cross-section so streaks are always IN air.
        double r = Math.sqrt(RANDOM.nextDouble()) * 0.8;
        double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
        double offX = Math.cos(theta) * r * hit.halfW();
        double offY = Math.sin(theta) * r * hit.halfH();
        double x;
        double y;
        double z;
        if (hit.family() == 0) { // NS tunnel: along = z
            z = pz + wz * ahead;
            x = hit.axisPerpAt(z) + offX;
            y = hit.yCenter() + offY;
        } else { // EW tunnel: along = x
            x = px + wx * ahead;
            z = hit.axisPerpAt(x) + offX;
            y = hit.yCenter() + offY;
        }
        double speed = 0.6 + RANDOM.nextDouble() * 0.9;
        JetFlowOption option = new JetFlowOption(dir);
        for (int k = 0; k < 5; k++) {
            double off = k * 1.1;
            level.addParticle(option,
                    x + wx * off, y, z + wz * off,
                    wx * speed, 0.0, wz * speed);
        }
    }

    /**
     * A bright particle ON the tunnel wall: random angle around the elliptical rim, placed
     * along the tunnel ahead of (or near) the player. {@code inside} biases the ring toward
     * the player's heading; in neutral air the near wall is lit to advertise the tunnel.
     */
    private static void spawnRimMarker(ClientLevel level, TunnelField.Hit hit,
                                       double px, double py, double pz,
                                       LocalPlayer player, boolean inside) {
        double playerAlong = hit.family() == 0 ? pz : px;
        double velAlong = hit.family() == 0 ? player.getDeltaMovement().z : player.getDeltaMovement().x;
        double aheadSign = Math.abs(velAlong) > 0.05 ? Math.signum(velAlong) : 1.0;
        // Spread the marker along the tunnel, biased toward the player's travel direction.
        double along = playerAlong + aheadSign * (6.0 + RANDOM.nextDouble() * 46.0);
        // Clamp to the tunnel body (10% end-fade margin) so rims never hang past the ends.
        double maxAlong = hit.centerAlong() + hit.halfL() * 0.88;
        double minAlong = hit.centerAlong() - hit.halfL() * 0.88;
        along = Math.max(minAlong, Math.min(maxAlong, along));

        double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
        double axisPerp = hit.axisPerpAt(along);
        double x;
        double y;
        double z;
        if (hit.family() == 0) { // NS: along axis = z, perp axis = x
            z = along;
            x = axisPerp + Math.cos(theta) * hit.halfW();
            y = hit.yCenter() + Math.sin(theta) * hit.halfH();
        } else { // EW: along axis = x, perp axis = z
            x = along;
            z = axisPerp + Math.cos(theta) * hit.halfW();
            y = hit.yCenter() + Math.sin(theta) * hit.halfH();
        }
        double speed = 0.35 + RANDOM.nextDouble() * 0.5;
        level.addParticle(ModParticles.RIM_MARKER, x, y, z,
                hit.flowX() * speed, 0.0, hit.flowZ() * speed);
    }

    /** Looks for a tunnel near the player (velocity-ahead, lateral, vertical) for approach rim FX. */
    private static TunnelField.Hit probeNear(ClientLevel level, LocalPlayer player,
                                             double px, double py, double pz,
                                             JetStreamsConfig.Physics p) {
        double vx = player.getDeltaMovement().x;
        double vz = player.getDeltaMovement().z;
        double vh = Math.hypot(vx, vz);
        if (vh > 0.01) {
            vx /= vh;
            vz /= vh;
            TunnelField.Hit h = WindField.tunnelHit(level,
                    px + vx * 40.0, py + 2.0, pz + vz * 40.0, p);
            if (h != null) {
                return h;
            }
        }
        double[] offsX = {40, -40, 0, 0, 0, 0};
        double[] offsY = {0, 0, 0, 0, 45, -45};
        double[] offsZ = {0, 0, 40, -40, 0, 0};
        for (int i = 0; i < 6; i++) {
            TunnelField.Hit h = WindField.tunnelHit(level,
                    px + offsX[i], py + offsY[i], pz + offsZ[i], p);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    /** Huge, faint cirrus billboard inside big tunnels, drifting with the flow. */
    private static void spawnCirrus(ClientLevel level, TunnelField.Hit hit,
                                    double px, double pz, JetStreamsConfig.Client c) {
        double along = (hit.family() == 0 ? pz : px)
                + (RANDOM.nextDouble() - 0.5) * Math.min(300.0, hit.halfL());
        double r = Math.sqrt(RANDOM.nextDouble()) * 0.7;
        double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
        double axisPerp = hit.axisPerpAt(along);
        double off = Math.cos(theta) * r * hit.halfW();
        double y = hit.yCenter() + Math.sin(theta) * r * hit.halfH();
        double speed = 0.01 + RANDOM.nextDouble() * 0.015;
        if (hit.family() == 0) {
            level.addParticle(ModParticles.CIRRUS_BAND, axisPerp + off, y, along,
                    hit.flowX() * speed, 0.0, hit.flowZ() * speed);
        } else {
            level.addParticle(ModParticles.CIRRUS_BAND, along, y, axisPerp + off,
                    hit.flowX() * speed, 0.0, hit.flowZ() * speed);
        }
    }
}
