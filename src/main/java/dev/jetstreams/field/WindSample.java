package dev.jetstreams.field;

/**
 * Result of sampling the wind field at one world position.
 *
 * <p>Two tunnel families exist (v1.2.0 3D tunnel field):
 * <ul>
 *   <li><b>NS tunnels</b> &mdash; horizontal tubes whose wind flows along Z
 *       (north or south).</li>
 *   <li><b>EW tunnels</b> &mdash; horizontal tubes whose wind flows along X
 *       (east or west).</li>
 * </ul>
 *
 * @param ns        true when the position is inside an NS tunnel
 * @param nsProfile elliptical cross-section profile in [0,1] (1 at the axis, 0 at the rim)
 * @param nsFlowX   X component of the NS flow unit vector
 * @param nsFlowZ   Z component of the NS flow unit vector
 * @param nsBand    tunnel id (slot hash) of the NS tunnel candidate, 0 when none
 * @param ew        true when the position is inside an EW tunnel
 * @param ewProfile elliptical cross-section profile in [0,1]
 * @param ewFlowX   X component of the EW flow unit vector
 * @param ewFlowZ   Z component of the EW flow unit vector
 * @param ewBand    tunnel id (slot hash) of the EW tunnel candidate, 0 when none
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
