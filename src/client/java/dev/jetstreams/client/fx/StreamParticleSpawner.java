package dev.jetstreams.client.fx;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.FieldRandom;
import dev.jetstreams.field.WindField;
import dev.jetstreams.field.WindSample;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side FX spawner. All positions derive from the deterministic flow field, so the
 * visuals always agree with the physics without any server packets.
 *
 * <p>Directional stream particles: colored, flow-aligned streaks rendered above the
 * activation altitude wherever a current flows - and nowhere else. Dead zones stay visually
 * silent ("none for neutral"). Budgets scale with {@code particleDensity}; no FX runs at
 * all below the stream activation altitude.
 */
public final class StreamParticleSpawner {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int CIRRUS_CELL = 256;
    private static final int CIRRUS_RADIUS_CELLS = 2;
    private static final long CIRRUS_CELL_COOLDOWN = 1200L;

    /** cellKey -> game time the cell may spawn again. */
    private static final Map<Long, Long> CIRRUS_COOLDOWN = new ConcurrentHashMap<>();

    private StreamParticleSpawner() {}

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

        long now = level.getGameTime();
        WindSample s = WindField.sample(level, player.getX(), player.getZ(), p);
        FlightState st = FlightStateTracker.get(level, player.getUUID());

        // ------------------------------------------------- directional stream FX
        // Spawns whether gliding or not, so players can read the currents around them
        // from any vantage point up high. Dead zones emit nothing.
        if (c.directionParticles && st.dominantFamily >= 0 && s.hasWind()) {
            double profile = Math.max(s.profile(st.dominantFamily), st.windProfile);
            if (profile > 0.05) {
                double wx = s.flowX(st.dominantFamily);
                double wz = s.flowZ(st.dominantFamily);
                int dir = st.flowDir >= 0 ? st.flowDir : flowDirOf(wx, wz);
                double rate = (flying(player) ? 2.4 : 1.2) * c.particleDensity * (0.5 + profile);
                int count = (int) rate + (RANDOM.nextDouble() < (rate - (int) rate) ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    spawnStreak(level, player, wx, wz, profile, dir);
                }
            }
        }

        // ------------------------------------------------------------- cirrus
        if (c.cirrusBands && player.getY() >= p.cruiseStartAltitude - 400.0) {
            if (now % 10 == 0) {
                spawnCirrusAround(level, player, p, c, now);
            }
        }

        if (CIRRUS_COOLDOWN.size() > 512) {
            CIRRUS_COOLDOWN.clear(); // cheap GC for stale cells
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

    private static void spawnStreak(ClientLevel level, LocalPlayer player,
                                    double wx, double wz, double profile, int dir) {
        // Bias the ring downwind so streaks appear along and ahead of the flow path.
        double ahead = 6.0 + RANDOM.nextDouble() * 26.0;
        double side = (RANDOM.nextDouble() - 0.5) * 28.0;
        double dy = (RANDOM.nextDouble() - 0.35) * 12.0;
        double px = -wz; // perpendicular
        double pz = wx;
        double x = player.getX() + wx * ahead + px * side;
        double y = player.getY() + dy;
        double z = player.getZ() + wz * ahead + pz * side;
        double speed = 0.6 + RANDOM.nextDouble() * 0.8 * (0.4 + profile); // b/t along flow
        // Longer chains (5 particles along the flow) read clearly as direction arrows.
        JetFlowOption option = new JetFlowOption(dir);
        for (int k = 0; k < 5; k++) {
            double off = k * 0.9;
            level.addParticle(option,
                    x + wx * off, y, z + wz * off,
                    wx * speed, 0.0, wz * speed);
        }
    }

    private static void spawnCirrusAround(ClientLevel level, LocalPlayer player,
                                          JetStreamsConfig.Physics p, JetStreamsConfig.Client c, long now) {
        long seed = WindField.fieldSeed(level, p);
        int pcx = (int) Math.floor(player.getX() / CIRRUS_CELL);
        int pcz = (int) Math.floor(player.getZ() / CIRRUS_CELL);
        int attempts = 0;
        for (int dx = -CIRRUS_RADIUS_CELLS; dx <= CIRRUS_RADIUS_CELLS && attempts < 2; dx++) {
            for (int dz = -CIRRUS_RADIUS_CELLS; dz <= CIRRUS_RADIUS_CELLS && attempts < 2; dz++) {
                long cx = pcx + dx;
                long cz = pcz + dz;
                long key = (cx & 0xFFFFFL) << 40 | (cz & 0xFFFFFL) << 16;
                Long next = CIRRUS_COOLDOWN.get(key);
                if (next != null && now < next) {
                    continue;
                }
                double roll = FieldRandom.hash01(seed, 0xC125C1, cx * 100003L + cz);
                if (roll > 0.30 * Math.min(2.0, Math.max(0.0, c.particleDensity))) {
                    continue; // sparse by design
                }
                double centerX = (cx + 0.5) * CIRRUS_CELL + (FieldRandom.hash01(seed, 0xC1, cx * 31 + cz) - 0.5) * 160.0;
                double centerZ = (cz + 0.5) * CIRRUS_CELL + (FieldRandom.hash01(seed, 0xC2, cx * 31 + cz) - 0.5) * 160.0;
                WindSample s = WindField.sample(level, centerX, centerZ, p);
                if (!s.hasWind()) {
                    continue; // bands only exist inside streams
                }
                // Pick the family actually flowing here so bands align with the current.
                int family = s.ns() ? 0 : 1;
                double wx = s.flowX(family);
                double wz = s.flowZ(family);
                int band = (int) (FieldRandom.hash01(seed, 0xC3, cx * 7 + cz) * 4);
                double y = p.cruiseStartAltitude - 100.0 + band * 250.0
                        + (FieldRandom.hash01(seed, 0xC4, cx * 13 + cz) - 0.5) * 120.0;
                double speed = 0.015 + FieldRandom.hash01(seed, 0xC5, cx + cz) * 0.02;
                level.addParticle(ModParticles.CIRRUS_BAND,
                        centerX, y, centerZ, wx * speed, 0.0, wz * speed);
                CIRRUS_COOLDOWN.put(key, now + CIRRUS_CELL_COOLDOWN);
                attempts++;
            }
        }
    }
}
