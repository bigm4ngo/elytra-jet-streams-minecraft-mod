package dev.jetstreams.field;

import dev.jetstreams.config.JetStreamsConfig.Physics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One family of jet stream bands: parallel strips separated by dead-zone gutters.
 *
 * <p>Bands are laid out along one world axis ("the perpendicular axis"). Each band cell has
 * a random stream width in {@code [streamWidthMin, streamWidthMax]} and a random dead zone
 * in {@code [deadZoneMin, deadZoneMax]}, both derived deterministically from
 * {@code (seed, family, band index)}, so the whole layout is reproducible with no storage.
 *
 * <p>Band boundaries are cumulative sums, so the grid caches boundaries lazily in a
 * {@link ConcurrentHashMap} and extends them by walking only the missing segment.
 * Continuous flight along a band means boundary queries almost always hit the cache,
 * making the amortized cost of a field sample O(1) with pure hash arithmetic - no chunk
 * access, no allocation, thread safe.
 *
 * <p>Each band also has a meandering centerline: {@code center(u) = c0 + A*sin(2*PI*u/wavelength + phi)},
 * where {@code u} is the coordinate along the band. Wind always flows one way along the
 * centerline tangent - streams curve but never reverse.
 */
public final class BandGrid {
    /** A single band: occupies world range [start, start+streamWidth) with a dead gutter after it. */
    public record Cell(long index, long start, int streamWidth, int deadWidth, int direction) {
        public long streamEnd() {
            return start + streamWidth;
        }

        public long end() {
            return start + streamWidth + deadWidth;
        }

        /** +1 flows toward positive perpendicular axis, -1 toward negative. */
        public int direction() {
            return direction;
        }
    }

    private final long seed;
    private final long salt;
    private final boolean northSouth; // true: bands flow along Z, indexed by X; false: flow along X, indexed by Z
    private final boolean alternating;
    private final int wMin;
    private final int wRange;
    private final int dMin;
    private final int dRange;
    private final double averageCell;
    private final int phase;

    private final Map<Long, Cell> cells = new ConcurrentHashMap<>();
    private final Map<Long, Long> boundaries = new ConcurrentHashMap<>();

    private BandGrid(long seed, boolean northSouth, Physics p) {
        this.seed = seed;
        this.northSouth = northSouth;
        this.salt = northSouth ? 0x4E535F53L : 0x4557534CL; // 'NS' / 'EWS'
        this.alternating = "RANDOM".equalsIgnoreCase(p.directionMode) == false;
        this.wMin = p.streamWidthMin;
        this.wRange = Math.max(0, p.streamWidthMax - p.streamWidthMin + 1);
        this.dMin = p.deadZoneMin;
        this.dRange = Math.max(0, p.deadZoneMax - p.deadZoneMin + 1);
        this.averageCell = (p.streamWidthMin + p.streamWidthMax) / 2.0
                + (p.deadZoneMin + p.deadZoneMax) / 2.0;
        this.phase = FieldRandom.hash01(seed, salt, 0x9E37L) < 0.5 ? 0 : 1;
    }

    public static BandGrid create(long seed, boolean northSouth, Physics p) {
        return new BandGrid(seed, northSouth, p);
    }

    public boolean isNorthSouth() {
        return northSouth;
    }

    private int streamWidth(long index) {
        return wMin + (int) (FieldRandom.hash01(seed, salt, index * 2 + 1) * wRange);
    }

    private int deadWidth(long index) {
        return dMin + (int) (FieldRandom.hash01(seed, salt, index * 2 + 2) * dRange);
    }

    /** Flow sign along the band axis. */
    public int direction(long index) {
        if (alternating) {
            return (Math.floorMod(index, 2) ^ phase) == 0 ? 1 : -1;
        }
        return FieldRandom.hash01(seed, salt, index * 2 + 3) < 0.5 ? 1 : -1;
    }

    /** World coordinate where band {@code index} begins. Amortized O(1) via memoized walk. */
    public long boundary(long index) {
        if (index == 0) {
            boundaries.putIfAbsent(0L, 0L);
            return 0L;
        }
        Long cached = boundaries.get(index);
        if (cached != null) {
            return cached;
        }
        // Locality check: neighbors are usually cached while flying along a band.
        Long near = boundaries.get(index - 1);
        if (near != null) {
            return extendFrom(index - 1, near, index);
        }
        near = boundaries.get(index + 1);
        if (near != null) {
            return extendFrom(index + 1, near, index);
        }
        // Cold path: walk from 0 outward, memoizing every boundary we pass.
        return extendFrom(0, 0L, index);
    }

    private long extendFrom(long fromIndex, long fromValue, long target) {
        long step = target > fromIndex ? 1 : -1;
        long value = fromValue;
        for (long i = fromIndex; i != target; i += step) {
            value += streamWidth(i) + deadWidth(i);
            boundaries.put(i + step, value);
        }
        return value;
    }

    public Cell cell(long index) {
        Cell cell = cells.get(index);
        if (cell != null) {
            return cell;
        }
        long start = boundary(index);
        long end = boundary(index + 1);
        long total = end - start;
        int w = (int) Math.min(streamWidth(index), Math.max(0, total));
        cell = new Cell(index, start, w, (int) Math.max(0, total - w), direction(index));
        Cell prev = cells.putIfAbsent(index, cell);
        return prev != null ? prev : cell;
    }

    /** Band index whose [start, end) range contains {@code coord}. Amortized O(1). */
    public long indexAt(double coord) {
        // Boundaries are non-decreasing in index, so both adjust loops terminate; the guards
        // only protect against pathological configs.
        long guess = (long) Math.floor(coord / averageCell);
        int guard = 0;
        while (boundary(guess) > coord && guard++ < 65536) {
            guess--;
        }
        guard = 0;
        while (boundary(guess + 1) <= coord && guard++ < 65536) {
            guess++;
        }
        return guess;
    }

    /**
     * Samples this family at a world column.
     *
     * @param u        coordinate along the band axis (x for NS bands, z for EW bands)
     * @param v        perpendicular coordinate (z for NS bands, x for EW bands)
     * @param meanderA amplitude in blocks
     * @param meanderL wavelength in blocks
     */
    public Sample sampleBand(double u, double v, int meanderA, int meanderL) {
        long index = indexAt(v);
        Cell cell = cell(index);
        double half = cell.streamWidth() / 2.0;
        if (half <= 0) {
            return new Sample(false, 0, 0, 0);
        }
        double center = cell.start() + half;
        if (meanderA > 0) {
            double phase = FieldRandom.hash01(seed, salt, index * 2 + 4) * Math.PI * 2.0;
            double k = Math.PI * 2.0 / meanderL;
            center += meanderA * Math.sin(k * u + phase);
        }
        double d = v - center;
        if (Math.abs(d) >= half) {
            return new Sample(false, 0, 0, 0);
        }
        double profile = 1.0 - (d / half) * (d / half);
        if (profile < 0.0) profile = 0.0;
        // Tangent of the meandering centerline, oriented along the flow direction.
        double slope = 0.0;
        if (meanderA > 0) {
            double k = Math.PI * 2.0 / meanderL;
            double phase = FieldRandom.hash01(seed, salt, index * 2 + 4) * Math.PI * 2.0;
            slope = meanderA * k * Math.cos(k * u + phase);
        }
        double sign = cell.direction();
        double fx;
        double fz;
        if (northSouth) {
            // flow along Z, tangent (slope, 1)
            double len = Math.hypot(slope, 1.0);
            fx = slope / len * sign;
            fz = 1.0 / len * sign;
        } else {
            // flow along X, tangent (1, slope)
            double len = Math.hypot(1.0, slope);
            fx = 1.0 / len * sign;
            fz = slope / len * sign;
        }
        return new Sample(true, profile, fx, fz);
    }

    public record Sample(boolean inStream, double profile, double flowX, double flowZ) {}
}
