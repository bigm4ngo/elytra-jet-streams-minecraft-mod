package dev.jetstreams.physics;

/**
 * Per-player, per-side transient flight state for the jet stream system.
 * Never persisted - everything here rebuilds naturally within a few ticks.
 */
public class FlightState {
    /**
     * Currently locked band family: 0 = NS band (flow along Z), 1 = EW band (flow along X),
     * -1 = none (dead zone / not flying). Inside a crossing this family is dominant:
     * cross-forces from the other family are suppressed until the player steers into them
     * for {@code captureSeconds}.
     */
    public int dominantFamily = -1;

    /** Progress [0,1] toward switching currents while steering into the other stream. */
    public double switchProgress;
    /** Seconds left before the current lock accepts another switch. */
    public double lockCooldown;

    /** Smoothed look-yaw change (deg/tick) - drives the sharp-turn speed bleed. */
    public double turnRate;
    public float prevYaw;

    /** Last tick's horizontal speed (b/s). */
    public double lastSpeedBps;
    /** Speed (b/s) the server considers physically possible for this player right now. */
    public double authorizedSpeedBps;

    /** True when the player is inside a stream core holding no-rocket cruise. */
    public boolean cruising;
    /** True while inside any stream wind. */
    public boolean inStream;
    /** True while flying against the dominant current (drives the headwind indicator). */
    public boolean headwind;
    /** Smoothed headwind strength [0,1] for FX. */
    public double headwindFactor;
    /** True while at/above the hypersonic freeze threshold. */
    public boolean hypersonic;

    /** Last applied dominant wind unit vector + profile (for FX). */
    public double windX;
    public double windZ;
    public double windProfile;

    /** Cosine between travel direction and the dominant current (1 = perfectly with-flow). */
    public double alignment;
    /**
     * Multiplier factor [0,1] derived from {@link #alignment}: stream speed bonuses
     * (firework multiplier + cruise) only apply ALONG the current, never across or against it.
     */
    public double alignmentFactor;
    /** Cardinal flow direction of the dominant current: 0=N(-Z), 1=S(+Z), 2=E(+X), 3=W(-X), -1=none. */
    public int flowDir = -1;

    /** Game time of the last firework boost application (drives {@link #isBoosting(long)}). */
    public long lastBoostTick = Long.MIN_VALUE;

    /** True while a firework is actively boosting this player (within 3 ticks of a boost). */
    public boolean isBoosting(long gameTime) {
        if (lastBoostTick == Long.MIN_VALUE) {
            return false;
        }
        return gameTime - lastBoostTick <= 3L;
    }
}
