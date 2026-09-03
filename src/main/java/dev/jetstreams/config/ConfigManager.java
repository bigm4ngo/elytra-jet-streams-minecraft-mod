package dev.jetstreams.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.jetstreams.JetStreams;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves {@code config/jetstreams.json}. Missing fields keep their code defaults,
 * which makes forward/backward compatible config files trivial.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "jetstreams.json";

    private static JetStreamsConfig config = new JetStreamsConfig();

    private ConfigManager() {}

    public static JetStreamsConfig get() {
        return config;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                JetStreamsConfig read = GSON.fromJson(Files.readString(path), JetStreamsConfig.class);
                if (read != null) {
                    config = read;
                    if (config.physics == null) config.physics = new JetStreamsConfig.Physics();
                    if (config.client == null) config.client = new JetStreamsConfig.Client();
                }
            } catch (Exception e) {
                JetStreams.LOGGER.error("Failed to read {}, keeping defaults", path, e);
            }
        }
        sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(config));
        } catch (IOException e) {
            JetStreams.LOGGER.error("Failed to write {}", path(), e);
        }
    }

    /**
     * Replaces the physics section from a JSON payload (ModMenu "apply to server" or an
     * operator command), sanitizing and persisting it. Returns false on malformed JSON.
     */
    public static boolean applyPhysicsJson(String json) {
        try {
            JetStreamsConfig.Physics parsed = GSON.fromJson(json, JetStreamsConfig.Physics.class);
            if (parsed == null) {
                return false;
            }
            config.physics = parsed;
            sanitize();
            save();
            return true;
        } catch (Exception e) {
            JetStreams.LOGGER.error("Rejected malformed physics config", e);
            return false;
        }
    }

    /** Clamps user-supplied values into safe ranges so a typo cannot destabilize the sim. */
    private static void sanitize() {
        JetStreamsConfig.Physics p = config.physics;
        p.streamWidthMin = clampInt(p.streamWidthMin, 64, 4096);
        p.streamWidthMax = clampInt(p.streamWidthMax, p.streamWidthMin, 8192);
        p.deadZoneMin = clampInt(p.deadZoneMin, 0, 4096);
        p.deadZoneMax = clampInt(p.deadZoneMax, p.deadZoneMin, 8192);
        p.meanderAmplitude = clampInt(p.meanderAmplitude, 0, 2000);
        p.meanderWavelength = clampInt(p.meanderWavelength, 256, 100000);
        p.activationAltitude = clampDouble(p.activationAltitude, -64.0, 4000.0);
        p.multiplierRate = clampDouble(p.multiplierRate, 0.0, 0.01);
        p.fireworkKickPerTick = clampDouble(p.fireworkKickPerTick, 0.1, 20.0);
        p.speedCapAltitude = clampDouble(p.speedCapAltitude, p.activationAltitude + 100.0, 40000.0);
        p.cruiseStartAltitude = clampDouble(p.cruiseStartAltitude, p.activationAltitude, 30000.0);
        p.cruiseSpeedBase = clampDouble(p.cruiseSpeedBase, 1.0, 400.0);
        p.cruiseSpeedPeak = clampDouble(p.cruiseSpeedPeak, p.cruiseSpeedBase, 400.0);
        p.cruiseGain = clampDouble(p.cruiseGain, 0.005, 1.0);
        p.windBoostSpeed = clampDouble(p.windBoostSpeed, 0.0, 400.0);
        p.windAccelPerSecondSq = clampDouble(p.windAccelPerSecondSq, 0.1, 200.0);
        p.headwindDragPerTick = clampDouble(p.headwindDragPerTick, 0.0, 0.2);
        p.boostMinAlignment = clampDouble(p.boostMinAlignment, -1.0, Math.min(0.95, p.boostFullAlignment));
        p.boostFullAlignment = clampDouble(p.boostFullAlignment, Math.max(-0.95, p.boostMinAlignment), 1.0);
        p.turnDrag = clampDouble(p.turnDrag, 0.0, 5.0);
        p.turnAngleThresholdDeg = clampDouble(p.turnAngleThresholdDeg, 1.0, 90.0);
        p.captureAngleDeg = clampDouble(p.captureAngleDeg, 5.0, 90.0);
        p.captureSeconds = clampDouble(p.captureSeconds, 0.05, 10.0);
        p.captureCooldownSeconds = clampDouble(p.captureCooldownSeconds, 0.0, 30.0);
        p.divePitchDeg = clampDouble(p.divePitchDeg, 5.0, 85.0);
        p.coreProfileForCruise = clampDouble(p.coreProfileForCruise, 0.0, 1.0);
        p.gravityCompensationPerTick = clampDouble(p.gravityCompensationPerTick, 0.0, 0.5);
        p.preloadAheadSeconds = clampDouble(p.preloadAheadSeconds, 0.5, 15.0);
        p.preloadMinSpeed = clampDouble(p.preloadMinSpeed, 0.0, 400.0);
        p.corridorHalfWidthChunks = clampInt(p.corridorHalfWidthChunks, 0, 8);
        p.maxCorridorChunks = clampInt(p.maxCorridorChunks, 4, 256);
        p.maxTicketedChunksPerPlayer = clampInt(p.maxTicketedChunksPerPlayer, 8, 2048);
        p.preloadTicketsPerTick = clampInt(p.preloadTicketsPerTick, 1, 32);
        p.hypersonicSpeed = clampDouble(p.hypersonicSpeed, 50.0, 2000.0);
        p.preloadExpiryTicks = clampInt(p.preloadExpiryTicks, 100, 20000);
        p.preloadTicketLevel = clampInt(p.preloadTicketLevel, 22, 33);

        JetStreamsConfig.Client c = config.client;
        c.skyTintStrength = clampDouble(c.skyTintStrength, 0.0, 2.0);
        c.particleDensity = clampDouble(c.particleDensity, 0.0, 4.0);
        c.tintStrength = clampDouble(c.tintStrength, 0.0, 2.0);
        c.tintNorth = sanitizeColor(c.tintNorth, "#7FB4FF");
        c.tintSouth = sanitizeColor(c.tintSouth, "#FFB454");
        c.tintEast = sanitizeColor(c.tintEast, "#59E0A0");
        c.tintWest = sanitizeColor(c.tintWest, "#C77DFF");
        c.tintNeutral = sanitizeColor(c.tintNeutral, "#8C99A8");
    }

    /** Normalizes a #RRGGBB string; falls back to the default on malformed input. */
    public static String sanitizeColor(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        boolean hex = v.length() == 6 && v.chars().allMatch(ch -> Character.isDigit(ch)
                || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'));
        return hex ? "#" + v.toUpperCase(java.util.Locale.ROOT) : fallback;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampDouble(double v, double min, double max) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
