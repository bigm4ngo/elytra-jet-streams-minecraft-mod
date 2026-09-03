package dev.jetstreams.field;

import dev.jetstreams.config.JetStreamsConfig.Physics;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 3D jet stream tunnel field (v1.2.0).
 *
 * <p>Streams are finite, meandering horizontal <b>tunnels</b> scattered probabilistically
 * through the sky above the activation altitude. Every tunnel is fully determined by
 * {@code (seed, family, slab, cell/segment)} hashes - no storage, O(1) sampling:
 *
 * <ul>
 *   <li><b>Slabs</b> are vertical layers stacked upward from the activation altitude; their
 *       thickness grows with altitude ({@code maxTunnelHeight + verticalGap}). A tunnel never
 *       leaves its slab, so a query only ever checks its own slab.</li>
 *   <li><b>NS family</b>: tunnels flowing along Z, one per (X-cell, slab, Z-segment) hash.
 *       Their width lives inside their X-cell (clamped, gap-first: cell = maxWidth + maxGap +
 *       slack) so neighboring lanes never touch. Their length runs along Z inside a
 *       Z-segment ({@code 2 * maxLength} period), so a query checks exactly one segment.</li>
 *   <li><b>EW family</b>: mirrored - flows along X, cell/segment roles swapped.</li>
 *   <li><b>Cross-section</b>: elliptical falloff {@code profile = 1 - q} with
 *       {@code q = (dPerp/halfW)^2 + (dy/halfH)^2}, fading to zero at the rim, multiplied by
 *       a soft end-fade so tunnels taper rather than stop dead.</li>
 *   <li><b>Dimensions</b> (width/height/length/gap) are bell-distributed between per-altitude
 *       min/max ranges that lerp smoothly (smoothstep) from the base altitude to the scale
 *       altitude and hold above it. Length gets a rare fat-tail multiplier so 10k-20k+
 *       monsters appear up high.</li>
 * </ul>
 *
 * <p>All air between tunnels is neutral zone. Tunnels are fully isolated: when one ends,
 * finding the next one is the navigation game.
 */
public final class TunnelField {
    /** Salt so the two families and the slabs never correlate. */
    private static final long SALT_NS = 0x54554E4E454C314CL; // 'TUNNEL1'
    private static final long SALT_EW = 0x54554E4E454C324CL; // 'TUNNEL2'
    private static final double IN_CELL_MARGIN = 20.0;

    /** A fully materialized tunnel (per-tunnel parameters, cached). */
    record Tunnel(
            int family,        // 0 = NS (flows along Z), 1 = EW (flows along X)
            int sign,          // +1 / -1 flow direction
            double centerPerp, // x0 for NS, z0 for EW (clamped into the cell)
            double centerAlong,// zC for NS, xC for EW (clamped into the segment)
            double halfW,      // half width (perpendicular, horizontal)
            double halfH,      // half height (vertical)
            double halfL,      // half length (along flow)
            double amp,        // meander amplitude
            double lambda,     // meander wavelength
            double phase,      // meander phase
            double yCenter     // vertical center of the tunnel
    ) {}

    /** One tunnel sample at a query point (best or per family). */
    public record Hit(double profile, double flowX, double flowZ, int family,
                      double halfW, double halfH, double halfL,
                      double dPerp, double dy, double u01, double yCenter,
                      double centerPerp, double centerAlong,
                      double amp, double lambda, double phase, int sign) {
        public boolean inside() {
            return profile > 0.0;
        }

        /** Meandered axis position (perpendicular coordinate) at an along-flow coordinate. */
        public double axisPerpAt(double along) {
            return centerPerp + amp * Math.sin(2.0 * Math.PI * (along - centerAlong) / lambda + phase);
        }
    }

    private static final class Slabs {
        final Map<Long, Double> boundaries = new ConcurrentHashMap<>();

        /** Bottom altitude of slab {@code s}; slab 0 straddles the base altitude. */
        double boundary(long s, Physics p) {
            Double cached = boundaries.get(s);
            if (cached != null) {
                return cached;
            }
            double value;
            if (s <= 0) {
                // Slab 0 is centered on the base altitude so tunnels exist right AT it -
                // "streams every ~300 blocks" must hold from the moment they activate.
                value = p.tunnelScaleBaseAltitude
                        - TunnelField.slabThickness(p.tunnelScaleBaseAltitude, p) / 2.0;
            } else {
                double below = boundary(s - 1, p);
                Double above = boundaries.get(s + 1);
                if (above != null) {
                    // walk down from the upper neighbor
                    value = above - TunnelField.slabThickness((below + above) / 2.0, p);
                } else {
                    // walk up from the lower neighbor, thickness evaluated mid-slab
                    double thickness = TunnelField.slabThickness(below, p);
                    value = below + TunnelField.slabThickness(below + thickness / 2.0, p);
                }
            }
            boundaries.put(s, value);
            return value;
        }
    }

    private final long seed;
    private final Physics p;
    private final Slabs slabs = new Slabs();
    private final Map<Long, Tunnel> cache = new ConcurrentHashMap<>();

    private TunnelField(long seed, Physics p) {
        this.seed = seed;
        this.p = p;
    }

    public static TunnelField create(long seed, Physics p) {
        return new TunnelField(seed, p);
    }

    // ------------------------------------------------------------- altitude math

    /** Smooth 0..1 progression from the base altitude to the scale altitude. */
    private static double scale01(double y, Physics p) {
        double t = (y - p.tunnelScaleBaseAltitude)
                / Math.max(1.0, p.tunnelScaleAltitude - p.tunnelScaleBaseAltitude);
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerpDim(double y, Physics p, double low, double high) {
        return low + (high - low) * scale01(y, p);
    }

    static double maxTunnelHeightAt(Physics p, double y) {
        return lerpDim(y, p, p.tunnelHeightMax, p.tunnelHeightMaxHigh);
    }

    private static double midGapAt(Physics p, double y) {
        double low = (p.tunnelGapMin + p.tunnelGapMax) / 2.0;
        double high = (p.tunnelGapMinHigh + p.tunnelGapMaxHigh) / 2.0;
        return lerpDim(y, p, low, high);
    }

    private static double gapMinAt(Physics p, double y) {
        return lerpDim(y, p, p.tunnelGapMin, p.tunnelGapMinHigh);
    }

    private static double gapMaxAt(Physics p, double y) {
        return lerpDim(y, p, p.tunnelGapMax, p.tunnelGapMaxHigh);
    }

    /**
     * Vertical slab spacing: max tunnel height + a HALF-strength gap. Vertical packing is
     * intentionally denser than horizontal: near the base altitude the tunnel columns
     * should nearly tile the sky, so flying at any y actually encounters tunnels.
     */
    private static double slabThickness(double y, Physics p) {
        return maxTunnelHeightAt(p, y) + 0.5 * midGapAt(p, y);
    }

    /** Horizontal cell size: max width + max gap + in-cell slack (gap-first spacing). */
    private static double cellSize(double y, Physics p) {
        double wMax = lerpDim(y, p, p.tunnelWidthMax, p.tunnelWidthMaxHigh);
        double gMax = lerpDim(y, p, p.tunnelGapMax, p.tunnelGapMaxHigh);
        return wMax + gMax + 2.0 * IN_CELL_MARGIN;
    }

    /** Along-flow segment period: long enough that any tunnel fits inside one segment. */
    private static double segmentPeriod(double y, Physics p) {
        double lMax = lerpDim(y, p, p.tunnelLengthMax, p.tunnelLengthMaxHigh);
        return 2.0 * lMax;
    }

    /** Per-family result: the hit (nullable) plus the lattice slot id of the candidate. */
    private record FamilyHit(Hit hit, long slot) {}

    // ------------------------------------------------------------------ sampling

    /**
     * Samples both families at {@code (x, y, z)} and builds the {@link WindSample} the
     * physics layer consumes. {@code y} below the base altitude is always neutral.
     */
    public WindSample sample(double x, double y, double z) {
        if (y < p.tunnelScaleBaseAltitude) {
            return WindSample.EMPTY;
        }
        long slab = slabIndexAt(y);
        FamilyHit ns = sampleFamily(0, slab, x, y, z);
        FamilyHit ew = sampleFamily(1, slab, x, y, z);

        boolean inNs = ns.hit() != null && ns.hit().inside();
        boolean inEw = ew.hit() != null && ew.hit().inside();
        RegionType region = inNs && inEw ? RegionType.CROSSING
                : inNs ? RegionType.NS_STREAM
                : inEw ? RegionType.EW_STREAM
                : RegionType.DEAD_ZONE;

        return new WindSample(
                inNs, inNs ? ns.hit().profile() : 0.0,
                inNs ? ns.hit().flowX() : 0.0, inNs ? ns.hit().flowZ() : 0.0, ns.slot(),
                inEw, inEw ? ew.hit().profile() : 0.0,
                inEw ? ew.hit().flowX() : 0.0, inEw ? ew.hit().flowZ() : 0.0, ew.slot(),
                region);
    }

    /** Best tunnel hit at the position (for FX: rim walls, cirrus). Null in neutral air. */
    public Hit best(double x, double y, double z) {
        if (y < p.tunnelScaleBaseAltitude) {
            return null;
        }
        long slab = slabIndexAt(y);
        FamilyHit ns = sampleFamily(0, slab, x, y, z);
        FamilyHit ew = sampleFamily(1, slab, x, y, z);
        Hit a = ns.hit();
        Hit b = ew.hit();
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.profile() >= b.profile() ? a : b;
    }

    private long slabIndexAt(double y) {
        // Slabs are thin relative to world scale; walk from the estimate and correct.
        double base = p.tunnelScaleBaseAltitude;
        long guess = (long) Math.floor((y - base) / Math.max(1.0, slabThickness(y, p)));
        int guard = 0;
        while (slabs.boundary(guess, p) > y && guard++ < 4096) {
            guess--;
        }
        guard = 0;
        while (slabs.boundary(guess + 1, p) <= y && guard++ < 4096) {
            guess++;
        }
        return guess;
    }

    /** Samples one family's tunnel candidate for the (cell, segment) the point falls in. */
    private FamilyHit sampleFamily(int family, long slab, double x, double y, double z) {
        double midY = 0.5 * (slabs.boundary(slab, p) + slabs.boundary(slab + 1, p));
        long salt = family == 0 ? SALT_NS : SALT_EW;

        // Perpendicular cell + along-flow segment (roles swap with the family).
        double cell = cellSize(midY, p);
        double period = segmentPeriod(midY, p);
        long cellIndex;
        long segmentIndex;
        if (family == 0) {
            cellIndex = (long) Math.floor(x / cell);
            segmentIndex = (long) Math.floor(z / period);
        } else {
            cellIndex = (long) Math.floor(z / cell);
            segmentIndex = (long) Math.floor(x / period);
        }
        long slot = mixSlot(family, slab, cellIndex, segmentIndex);
        Tunnel tunnel = tunnelFor(family, salt, slab, slot, cellIndex, segmentIndex, midY);
        if (tunnel == null) {
            return new FamilyHit(null, 0L);
        }
        return new FamilyHit(hitOf(tunnel, x, y, z), slot);
    }

    /** Deterministically materializes the (unique) tunnel candidate of a lattice slot. */
    private Tunnel tunnelFor(int family, long salt, long slab, long slot, long cellIndex,
                             long segmentIndex, double midY) {
        double chance = p.tunnelExistenceChance;
        if (FieldRandom.hash01(seed, salt, slot) >= chance) {
            return null;
        }
        Tunnel cached = cache.get(slot);
        if (cached != null) {
            return cached;
        }
        Tunnel t = buildTunnel(family, salt, slab, cellIndex, segmentIndex, midY, slot);
        if (cache.size() > 8192) {
            cache.clear();
        }
        cache.put(slot, t);
        return t;
    }

    private static long mixSlot(int family, long slab, long cellIndex, long segmentIndex) {
        long h = FieldRandom.mix(family * 0x9E3779B97F4A7C15L + 0x5A5A);
        h = FieldRandom.mix(h ^ (slab * 0xC2B2AE3D27D4EB4FL));
        h = FieldRandom.mix(h ^ (cellIndex * 0x9E3779B97F4A7C15L));
        h = FieldRandom.mix(h ^ (segmentIndex + 0x165667B19E3779F9L));
        return h;
    }

    private Tunnel buildTunnel(int family, long salt, long slab, long cellIndex,
                               long segmentIndex, double midY, long slot) {
        double yBase = slabs.boundary(slab, p);
        double yTop = slabs.boundary(slab + 1, p);

        // Bell-distributed dimensions at this altitude.
        double tight = Math.max(0.05, p.tunnelDistributionTightness);
        double wMin = lerpDim(midY, p, p.tunnelWidthMin, p.tunnelWidthMinHigh);
        double wMax = lerpDim(midY, p, p.tunnelWidthMax, p.tunnelWidthMaxHigh);
        double hMin = lerpDim(midY, p, p.tunnelHeightMin, p.tunnelHeightMinHigh);
        double hMax = lerpDim(midY, p, p.tunnelHeightMax, p.tunnelHeightMaxHigh);
        double lMin = lerpDim(midY, p, p.tunnelLengthMin, p.tunnelLengthMinHigh);
        double lMax = lerpDim(midY, p, p.tunnelLengthMax, p.tunnelLengthMaxHigh);

        double width = bell(seed, salt, slot * 7 + 1, wMin, wMax, tight);
        double height = bell(seed, salt, slot * 7 + 2, hMin, hMax, tight);
        double length = bell(seed, salt, slot * 7 + 3, lMin, lMax, tight);
        // Fat tail: rare monsters up to ~2x the max range up high.
        if (FieldRandom.hash01(seed, salt, slot * 7 + 4) < 0.12) {
            length *= 1.0 + FieldRandom.hash01(seed, salt, slot * 7 + 5) * 1.0;
        }

        double cell = cellSize(midY, p);
        double period = segmentPeriod(midY, p);

        // Direction: ALTERNATING flips sign by perpendicular-cell parity (opposite lanes).
        boolean alternating = !"RANDOM".equalsIgnoreCase(p.directionMode);
        int sign;
        if (alternating) {
            sign = Math.floorMod(cellIndex, 2L) == 0 ? 1 : -1;
        } else {
            sign = FieldRandom.hash01(seed, salt, slot * 7 + 6) < 0.5 ? 1 : -1;
        }

        // Meander - amplitude capped so the tunnel (incl. wobble) stays inside its cell
        // while STILL leaving at least half the minimum gap to each cell boundary.
        double gapMin = gapMinAt(p, midY);
        double ampCap = Math.max(5.0, (cell - gapMin - width) / 2.0 - IN_CELL_MARGIN);
        double amp = Math.min(Math.min(p.meanderFraction * width, ampCap), gapMin / 2.0);
        double lambda = Math.max(cell, p.meanderWavelengthCells * cell);

        // Center clamping: the full tunnel (width + wobble) keeps >= gapMin/2 to each cell
        // boundary, so two neighboring tunnels are guaranteed >= gapMin of neutral air
        // (gap-first spacing), which is also what makes neighbor checks unnecessary.
        double cellStart = cellIndex * cell;
        double cellEnd = cellStart + cell;
        double marginPerp = gapMin / 2.0 + width / 2.0 + amp;
        double centerPerp = cellStart + marginPerp
                + FieldRandom.hash01(seed, salt, slot * 7 + 7)
                        * Math.max(0.0, (cellEnd - cellStart) - 2.0 * marginPerp);

        double segStart = segmentIndex * period;
        double segEnd = segStart + period;
        double marginAlong = length / 2.0 + IN_CELL_MARGIN;
        double centerAlong = segStart + marginAlong
                + FieldRandom.hash01(seed, salt, slot * 7 + 8)
                        * Math.max(0.0, period - 2.0 * marginAlong);

        // Vertical center clamped into the slab (tunnels never cross slab boundaries);
        // only a thin margin here - vertical packing is deliberately denser than horizontal.
        double vMargin = height / 2.0 + 5.0;
        double yCenter = yBase + vMargin
                + FieldRandom.hash01(seed, salt, slot * 7 + 9)
                        * Math.max(0.0, (yTop - yBase) - 2.0 * vMargin);
        yCenter = Mth.clamp(yCenter, yBase + vMargin, yTop - vMargin);

        return new Tunnel(family, sign, centerPerp, centerAlong,
                width / 2.0, height / 2.0, length / 2.0,
                amp, lambda,
                FieldRandom.hash01(seed, salt, slot * 7 + 10) * Math.PI * 2.0,
                yCenter);
    }

    /** Elliptical cross-section test + soft end fade + meandered flow tangent. */
    private static Hit hitOf(Tunnel t, double x, double y, double z) {
        double along;
        double perp;
        double dPerp;
        double axisSlope; // d(perp)/d(along) of the meandered axis at this point
        double alongArg = (t.family == 0 ? z : x) - t.centerAlong;
        double u01 = (alongArg + t.halfL) / (2.0 * t.halfL);
        if (u01 < 0.0 || u01 > 1.0) {
            return null; // outside the tunnel's finite length
        }
        double wobble = t.amp * Math.sin(2.0 * Math.PI * alongArg / t.lambda + t.phase);
        axisSlope = t.amp * (2.0 * Math.PI / t.lambda)
                * Math.cos(2.0 * Math.PI * alongArg / t.lambda + t.phase);
        if (t.family == 0) { // NS: along = z, perp = x
            along = z;
            perp = x;
            dPerp = x - (t.centerPerp + wobble);
        } else { // EW: along = x, perp = z
            along = x;
            perp = z;
            dPerp = z - (t.centerPerp + wobble);
        }
        double dy = y - t.yCenter;
        double q = (dPerp * dPerp) / (t.halfW * t.halfW) + (dy * dy) / (t.halfH * t.halfH);
        if (q > 1.0) {
            return null;
        }
        // Soft taper at both ends (first/last ~10% of the length).
        double endFade = Math.min(u01, 1.0 - u01) / 0.10;
        endFade = Mth.clamp(endFade, 0.0, 1.0);
        endFade = endFade * endFade * (3.0 - 2.0 * endFade);
        double profile = (1.0 - q) * endFade;
        if (profile <= 0.0) {
            return null;
        }

        double fx;
        double fz;
        if (t.family == 0) { // flows along Z
            double len = Math.hypot(axisSlope, 1.0);
            fx = axisSlope / len * t.sign;
            fz = 1.0 / len * t.sign;
        } else { // flows along X
            double len = Math.hypot(1.0, axisSlope);
            fx = 1.0 / len * t.sign;
            fz = axisSlope / len * t.sign;
        }
        return new Hit(profile, fx, fz, t.family(),
                t.halfW(), t.halfH(), t.halfL(),
                dPerp, dy, u01, t.yCenter(),
                t.centerPerp(), t.centerAlong(), t.amp(), t.lambda(), t.phase(), t.sign());
    }

    /** Box-Muller bell curve in [min, max]; tightness scales the sigma (higher = more central). */
    public static double bell(long seed, long salt, long index, double min, double max, double tightness) {
        double u1 = Math.max(1.0e-9, FieldRandom.hash01(seed, salt, index));
        double u2 = FieldRandom.hash01(seed, salt, index ^ 0x51ED270B);
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        z = Mth.clamp(z * tightness, -2.4, 2.4);
        double t01 = (z / 2.4 + 1.0) / 2.0;
        return min + (max - min) * t01;
    }
}
