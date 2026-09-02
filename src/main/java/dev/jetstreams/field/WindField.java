package dev.jetstreams.field;

import dev.jetstreams.config.JetStreamsConfig.Physics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public entry point for querying the 2D jet stream flow field.
 *
 * <p>Layout determinism: the server derives a "field seed" from the world seed (passed
 * through a non-reversible mix so raw world seeds never reach clients), and syncs it to
 * modded clients via the physics payload. Both sides then sample the identical field with
 * zero packets. A config {@code layoutSeedOverride} lets admins reroll a world's layout;
 * the config revision is part of the cache key so live reloads rebuild the grids.
 */
public final class WindField {
    private record GridKey(long seed, int revision) {}

    private static final long FIELD_SALT = 0x4A45545354524541L; // "JETSTREA"

    private static final Map<GridKey, BandGrid[]> GRIDS = new ConcurrentHashMap<>();
    private static volatile int configRevision = 0;

    private WindField() {}

    /** Called by ConfigManager on (re)load so cached grids rebuild with the new layout constants. */
    public static void invalidate() {
        configRevision++;
    }

    /**
     * The seed that drives the stream layout for this side. On the server it is mixed from
     * the raw world seed (or the override); on the client it is the value the server synced.
     */
    public static long fieldSeed(Level level, Physics p) {
        if (level.isClientSide() && dev.jetstreams.physics.RemotePhysics.hasFieldSeed()) {
            return dev.jetstreams.physics.RemotePhysics.fieldSeed();
        }
        long raw = p.layoutSeedOverride >= 0
                ? p.layoutSeedOverride
                : (level instanceof ServerLevel serverLevel ? serverLevel.getSeed() : 0L);
        return FieldRandom.mix(raw ^ FIELD_SALT);
    }

    public static BandGrid[] grids(long seed, Physics p) {
        return GRIDS.computeIfAbsent(new GridKey(seed, configRevision), key -> new BandGrid[] {
                BandGrid.create(seed, true, p),  // NS bands: indexed by X, flow along Z
                BandGrid.create(seed, false, p)  // EW bands: indexed by Z, flow along X
        });
    }

    /** True when the jet stream system is active in this dimension. */
    public static boolean enabledFor(Level level, Physics p) {
        if (!p.enabled) {
            return false;
        }
        if ("ALL".equalsIgnoreCase(p.dimensionMode)) {
            return true;
        }
        DimensionType type = level.dimensionType();
        if ("WHITELIST".equalsIgnoreCase(p.dimensionMode)) {
            return p.dimensionWhitelist.contains(level.dimension().identifier().toString());
        }
        // Default NO_CEILING: everywhere a sky makes sense (Overworld, End, modded sky dims).
        return !type.hasCeiling();
    }

    /**
     * Samples the flow field at a world column.
     * Pure arithmetic - no chunk access, no allocation beyond the returned record,
     * safe to call from any thread.
     */
    public static WindSample sample(Level level, double x, double z, Physics p) {
        if (!enabledFor(level, p)) {
            return WindSample.EMPTY;
        }
        BandGrid[] grids = grids(fieldSeed(level, p), p);
        BandGrid.Sample ns = grids[0].sampleBand(x, z, p.meanderAmplitude, p.meanderWavelength);
        BandGrid.Sample ew = grids[1].sampleBand(z, x, p.meanderAmplitude, p.meanderWavelength);
        long nsBand = grids[0].cell(grids[0].indexAt(x)).index();
        long ewBand = grids[1].cell(grids[1].indexAt(z)).index();
        RegionType region;
        if (ns.inStream() && ew.inStream()) {
            region = RegionType.CROSSING;
        } else if (ns.inStream()) {
            region = RegionType.NS_STREAM;
        } else if (ew.inStream()) {
            region = RegionType.EW_STREAM;
        } else {
            region = RegionType.DEAD_ZONE;
        }
        return new WindSample(
                ns.inStream(), ns.profile(), ns.flowX(), ns.flowZ(), nsBand,
                ew.inStream(), ew.profile(), ew.flowX(), ew.flowZ(), ewBand,
                region);
    }

    /** Convenience overload using the active (possibly synced) physics config. */
    public static WindSample sample(Level level, double x, double z) {
        return sample(level, x, z, dev.jetstreams.physics.PhysicsResolver.activeFor(level));
    }

    // ------------------------------------------------------------- speed curves

    /**
     * Firework rocket speed multiplier: {@code exp(rate * (y - k))}, with {@code k} the
     * activation altitude (default 300) and y clamped to the cap altitude (default 8000).
     */
    public static double fireworkMultiplier(Physics p, double y) {
        double yEff = Math.min(y, p.speedCapAltitude);
        double e = p.multiplierRate * (yEff - p.activationAltitude);
        return Math.exp(e);
    }

    /**
     * No-rocket cruise speed (blocks per second) inside a stream core.
     * Exponential ramp of the same family as the firework multiplier:
     * {@code base * exp(ln(peak/base) * (y - cruiseStart) / (cap - cruiseStart))},
     * giving exactly 20 b/s at y=2000 and 100 b/s at y=8000 with the default config.
     */
    public static double cruiseSpeed(Physics p, double y) {
        if (y < p.cruiseStartAltitude) {
            return 0.0;
        }
        double yEff = Math.min(y, p.speedCapAltitude);
        double rate = Math.log(p.cruiseSpeedPeak / p.cruiseSpeedBase)
                / (p.speedCapAltitude - p.cruiseStartAltitude);
        return p.cruiseSpeedBase * Math.exp(rate * (yEff - p.cruiseStartAltitude));
    }

    /**
     * Sky darkening factor in [0,1] - exponential, sharing its rate constant family with the
     * speed curves: {@code 1 - exp(-k * (y - activation))}, chosen so the sky reaches ~88%
     * of full darkening at the cap altitude.
     */
    public static double skyDarkening(Physics p, double y) {
        double yEff = Math.min(y, p.speedCapAltitude);
        if (yEff <= p.activationAltitude) {
            return 0.0;
        }
        double k = -Math.log(0.12) / (p.speedCapAltitude - p.activationAltitude);
        return 1.0 - Math.exp(-k * (yEff - p.activationAltitude));
    }
}
