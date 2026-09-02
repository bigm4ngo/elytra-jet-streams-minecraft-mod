package dev.jetstreams.field;

/** What kind of wind region a world position falls into. */
public enum RegionType {
    /** Between streams - negligible wind. */
    DEAD_ZONE,
    /** Inside a north/south band only. */
    NS_STREAM,
    /** Inside an east/west band only. */
    EW_STREAM,
    /** Inside both an NS and an EW band - vector locking applies here. */
    CROSSING
}
