package dev.jetstreams.field;

/**
 * Result of sampling the wind field at one world column.
 *
 * <p>Two band families exist:
 * <ul>
 *   <li><b>NS bands</b> &mdash; strips that run north&ndash;south; their wind flows along Z
 *       (north or south). Membership varies with world X.</li>
 *   <li><b>EW bands</b> &mdash; strips that run east&ndash;west; their wind flows along X
 *       (east or west). Membership varies with world Z.</li>
 * </ul>
 *
 * @param ns        true when the position is inside an NS band's stream core
 * @param nsProfile lateral strength profile in [0,1] (1 at the centerline, 0 at the edge)
 * @param nsFlowX   X component of the NS flow unit vector
 * @param nsFlowZ   Z component of the NS flow unit vector
 * @param nsBand    band index along the X axis
 * @param ew        true when the position is inside an EW band's stream core
 * @param ewProfile lateral strength profile in [0,1]
 * @param ewFlowX   X component of the EW flow unit vector
 * @param ewFlowZ   Z component of the EW flow unit vector
 * @param ewBand    band index along the Z axis
 * @param region    combined region classification
 */
public record WindSample(
        boolean ns, double nsProfile, double nsFlowX, double nsFlowZ, long nsBand,
        boolean ew, double ewProfile, double ewFlowX, double ewFlowZ, long ewBand,
        RegionType region) {

    public static final WindSample EMPTY =
            new WindSample(false, 0, 0, 0, 0, false, 0, 0, 0, 0, RegionType.DEAD_ZONE);

    /** True when at least one stream carries wind at this position. */
    public boolean hasWind() {
        return region != RegionType.DEAD_ZONE;
    }

    /** Flow X component of the given family (0 = NS, 1 = EW). */
    public double flowX(int family) {
        return family == 0 ? nsFlowX : ewFlowX;
    }

    /** Flow Z component of the given family (0 = NS, 1 = EW). */
    public double flowZ(int family) {
        return family == 0 ? nsFlowZ : ewFlowZ;
    }

    /** Lateral profile of the given family (0 = NS, 1 = EW). */
    public double profile(int family) {
        return family == 0 ? nsProfile : ewProfile;
    }
}
