package dev.jetstreams.config;

import java.util.List;

/**
 * Root configuration object for Elytra Jet Streams.
 * Serialized as pretty-printed JSON at {@code .minecraft/config/jetstreams.json}.
 *
 * <p>Split into two groups:
 * <ul>
 *   <li>{@link Physics} &mdash; everything that affects world simulation. The server
 *       authoritative copy is synced to clients on join and on reload so that client
 *       flight prediction always matches server physics (no rubber-banding).</li>
 *   <li>{@link Client} &mdash; purely cosmetic settings that stay local to each client.</li>
 * </ul>
 */
public class JetStreamsConfig {

    public Physics physics = new Physics();
    public Client client = new Client();

    /** Server-authoritative simulation settings (synced to clients). */
    public static class Physics {
        // ---------------------------------------------------------------- field
        /** Master switch for the whole jet stream system. */
        public boolean enabled = true;
        /** ALL = every dimension, NO_CEILING = all except ceiling dimensions (Nether), WHITELIST = only listed ones. */
        public String dimensionMode = "NO_CEILING";
        /** Used when dimensionMode = WHITELIST. */
        public List<String> dimensionWhitelist = List.of("minecraft:overworld");
        /** If >= 0, overrides the world seed used to generate the stream layout (reroll the sky highways). */
        public long layoutSeedOverride = -1L;

        /** Stream width range in blocks (spec: 500-1000). */
        public int streamWidthMin = 500;
        public int streamWidthMax = 1000;
        /** Dead zone width range in blocks (spec: 100-500). */
        public int deadZoneMin = 100;
        public int deadZoneMax = 500;
        /** ALTERNATING guarantees an opposite-direction lane next to every stream; RANDOM is fully random. */
        public String directionMode = "ALTERNATING";
        /** Centerline wobble amplitude (blocks) - makes streams curve continuously. */
        public int meanderAmplitude = 140;
        /** Centerline wobble wavelength (blocks). */
        public int meanderWavelength = 2400;

        // ---------------------------------------------------------------- speed
        /** Altitude where jet streams activate (the k constant of the multiplier equation). */
        public double activationAltitude = 300.0;
        /** Firework speed multiplier = exp(multiplierRate * (y - activationAltitude)), y clamped to speedCapAltitude. */
        public double multiplierRate = 0.00032;
        /** Altitude at which the multiplier and cruise speed peak (hard cap). */
        public double speedCapAltitude = 8000.0;
        /** Max boost delta added per tick (b/t) - smooths the high-altitude rocket kick. */
        public double fireworkKickPerTick = 2.5;

        /** No-rocket cruise begins at this altitude (spec: 2000). */
        public double cruiseStartAltitude = 2000.0;
        /** Cruise speed (b/s) at cruiseStartAltitude (spec: 20). */
        public double cruiseSpeedBase = 20.0;
        /** Cruise speed (b/s) at speedCapAltitude (spec: 100). */
        public double cruiseSpeedPeak = 100.0;
        /** Per-tick approach rate toward cruise speed (0..1). */
        public double cruiseGain = 0.08;

        /** Max tailwind (b/s) the stream adds to a glider below cruise altitude. */
        public double windBoostSpeed = 30.0;
        /** Stream acceleration (b/s^2) toward the tailwind target. */
        public double windAccelPerSecondSq = 12.0;
        /** Per-tick horizontal drag multiplier (0..1) when flying against the current. */
        public double headwindDragPerTick = 0.008;

        /** Velocity-vs-flow cosine below which the stream firework boost is fully suppressed. */
        public double boostMinAlignment = 0.15;
        /** Velocity-vs-flow cosine at/above which the full altitude multiplier applies. */
        public double boostFullAlignment = 0.85;

        /** Sharp-turn speed bleed coefficient (0..1 per tick at max turn rate). */
        public double turnDrag = 0.9;
        /** Look yaw change (deg/tick) that counts as a sharp turn. */
        public double turnAngleThresholdDeg = 7.0;

        // -------------------------------------------------------- intersections
        /** Look angle (deg) within the other stream's flow that counts as "steering into it". */
        public double captureAngleDeg = 40.0;
        /** Seconds the player must steer into the other stream to switch currents. */
        public double captureSeconds = 1.25;
        /** Seconds after a switch during which the new lock resists switching back. */
        public double captureCooldownSeconds = 1.5;
        /** Pitch (deg, looking down) beyond which cruise lift disengages and the player dives. */
        public double divePitchDeg = 40.0;
        /** Minimum lateral stream profile (0..1) required to hold no-rocket cruise. */
        public double coreProfileForCruise = 0.55;
        /** Per-tick gravity impulse (b/t) of vanilla elytra flight, used to cancel it during cruise. */
        public double gravityCompensationPerTick = 0.08;

        // ------------------------------------------------------------ perf/chunks
        public boolean chunkPreloadEnabled = true;
        /** Seconds of flight path to keep pre-loaded ahead of a fast glider. */
        public double preloadAheadSeconds = 2.5;
        /** Minimum speed (b/s) before corridor pre-loading engages. */
        public double preloadMinSpeed = 20.0;
        /** Extra chunk columns loaded on each side of the predicted centerline. */
        public int corridorHalfWidthChunks = 1;
        /** Hard cap on corridor length (chunks) ahead of the player. */
        public int maxCorridorChunks = 48;
        /** Hard cap on simultaneously ticketed corridor chunks per player. */
        public int maxTicketedChunksPerPlayer = 160;
        /** New chunk ticket requests per player per tick (throttles world-gen cost). */
        public int preloadTicketsPerTick = 3;
        /** At/above this speed (b/s) no NEW chunks are loaded unless already preloaded. */
        public double hypersonicSpeed = 250.0;
        /** Enables the hypersonic freeze behavior described above. */
        public boolean hypersonicFreeze = true;
        /** Ticket timeout in ticks (chunks self-release after this long). */
        public int preloadExpiryTicks = 600;
        /** Chunk ticket level used for corridor chunks (33 = border/render-ready, 31 = ticking). */
        public int preloadTicketLevel = 33;
    }

    /** Local, cosmetic-only settings. Never synced - editable client side by anyone. */
    public static class Client {
        /** Exponential darkening of the sky with altitude. */
        public boolean skyTint = true;
        public double skyTintStrength = 1.0;
        /** Directional stream particles that visualize where each current flows (none in dead zones). */
        public boolean directionParticles = true;
        public double particleDensity = 1.0;
        /** Large, translucent flow-aligned cirrus bands near cruise altitude. */
        public boolean cirrusBands = true;
        /** Directional + neutral screen tint while gliding (one color per flow direction). */
        public boolean streamTints = true;
        /** Global tint strength multiplier (0..2). */
        public double tintStrength = 0.7;
        /** Subtle warm on-screen edge tint while flying against the current. */
        public boolean headwindIndicator = true;
        /** Tint colors as #RRGGBB - per flow direction plus dead-zone neutral. */
        public String tintNorth = "#7FB4FF";
        public String tintSouth = "#FFB454";
        public String tintEast = "#59E0A0";
        public String tintWest = "#C77DFF";
        public String tintNeutral = "#8C99A8";
    }
}
