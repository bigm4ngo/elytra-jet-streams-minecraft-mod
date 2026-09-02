package dev.jetstreams.client.fx;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.FieldRandom;
import dev.jetstreams.field.WindField;
import dev.jetstreams.field.WindSample;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side FX spawner. All positions derive from the deterministic flow field, so the
 * visuals always agree with the physics without any server packets.
 *
 * <p>Budgets: a handful of streaks per tick near the glider and a sparse cirrus lattice
 * near cruise altitude, both scaled by {@code particleDensity}. Costs are negligible and
 * no FX runs at all below the stream activation altitude.
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
        if (!p.enabled || (!c.windParticles && !c.cirrusBands)) {
            return;
        }
        if (!WindField.enabledFor(level, p) || player.getY() < p.activationAltitude) {
            return;
        }

        long now = level.getGameTime();
        WindSample s = WindField.sample(level, player.getX(), player.getZ(), p);
        FlightState st = FlightStateTracker.get(level, player.getUUID());
        boolean flying = player.isFallFlying();

        // ------------------------------------------------------------ streaks
        if (c.windParticles && flying && s.hasWind() && st.dominantFamily >= 0) {
            double profile = Math.max(s.profile(st.dominantFamily), st.windProfile);
            if (profile > 0.1) {
                double wx = s.flowX(st.dominantFamily);
                double wz = s.flowZ(st.dominantFamily);
                int count = (int) Math.ceil(2.0 * c.particleDensity * (0.4 + profile));
                for (int i = 0; i < count; i++) {
                    spawnStreak(level, player, wx, wz, profile);
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

    private static void spawnStreak(ClientLevel level, LocalPlayer player, double wx, double wz, double profile) {
        // Bias the ring downwind so streaks appear ahead of the flight path.
        double ahead = 8.0 + RANDOM.nextDouble() * 22.0;
        double side = (RANDOM.nextDouble() - 0.5) * 22.0;
        double dy = (RANDOM.nextDouble() - 0.35) * 10.0;
        double px = -wz; // perpendicular
        double pz = wx;
        double x = player.getX() + wx * ahead + px * side;
        double y = player.getY() + dy;
        double z = player.getZ() + wz * ahead + pz * side;
        double speed = 0.5 + RANDOM.nextDouble() * 0.7 * (0.4 + profile); // b/t along flow
        // Chain of 3 particles along the flow reads as one elongated streak.
        for (int k = 0; k < 3; k++) {
            double off = k * 0.8;
            level.addParticle(dev.jetstreams.registry.ModParticles.JET_STREAK,
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
                level.addParticle(dev.jetstreams.registry.ModParticles.CIRRUS_BAND,
                        centerX, y, centerZ, wx * speed, 0.0, wz * speed);
                CIRRUS_COOLDOWN.put(key, now + CIRRUS_CELL_COOLDOWN);
                attempts++;
            }
        }
    }
}
