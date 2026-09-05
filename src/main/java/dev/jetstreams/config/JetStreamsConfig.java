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

        // ------------------------------------------------- tunnel field (3D streams)
        /**
         * Altitude where tunnel dimension scaling starts (the low anchor). Tunnels are
         * smallest and densest here and grow smoothly towards {@link #tunnelScaleAltitude}.
         */
        public double tunnelScaleBaseAltitude = 300.0;
        /** Altitude where tunnel dimensions stop growing (values hold at/above it). */
        public double tunnelScaleAltitude = 6000.0;

        /** Tunnel width (horizontal extent perpendicular to flow) range at the base altitude. */
        public int tunnelWidthMin = 100;
        public int tunnelWidthMax = 300;
        /** Tunnel width range at/above {@link #tunnelScaleAltitude}. */
        public int tunnelWidthMinHigh = 1000;
        public int tunnelWidthMaxHigh = 5000;
        /** Tunnel height (vertical extent) range at the base altitude. */
        public int tunnelHeightMin = 100;
        public int tunnelHeightMax = 150;
        /** Tunnel height range at/above the scale altitude. */
        public int tunnelHeightMinHigh = 600;
        public int tunnelHeightMaxHigh = 2000;
        /** Tunnel length (along flow) range at the base altitude. */
        public int tunnelLengthMin = 500;
        public int tunnelLengthMax = 2000;
        /** Tunnel length range at/above the scale altitude (rare outliers can reach ~2x max). */
        public int tunnelLengthMinHigh = 8000;
        public int tunnelLengthMaxHigh = 14000;
        /**
         * Wall-to-wall neutral gap between neighboring tunnels, at the base altitude and at
         * the scale altitude (gap-first spacing: horizontal cell = maxWidth + maxGap + slack).
         */
        public int tunnelGapMin = 150;
        public int tunnelGapMax = 350;
        public int tunnelGapMinHigh = 800;
        public int tunnelGapMaxHigh = 1200;
        /** Probability [0,1] that a lattice cell actually spawns a tunnel (spacing is probabilistic). */
        public double tunnelExistenceChance = 0.8;
        /** Bell-distribution tightness: 0 = uniform in range, higher = stronger clustering at the middle. */
        public double tunnelDistributionTightness = 1.0;

        /** ALTERNATING guarantees an opposite-direction lane next to every tunnel; RANDOM is fully random. */
        public String directionMode = "ALTERNATING";
        /** Max centerline wobble (blocks, scales with tunnel size) - makes tunnels curve. */
        public double meanderFraction = 0.18;
        /** Centerline wobble wavelength, as a multiple of the horizontal cell size. */
        public double meanderWavelengthCells = 3.0;

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

        /** Altitude where slow out-of-stream (neutral zone) cruising begins. */
        public double neutralCruiseStartAltitude = 4000.0;
        /** Neutral-zone cruise speed (b/s) at its start altitude (any direction, no current). */
        public double neutralCruiseSpeedBase = 20.0;
        /** Neutral cruise exponential rate; 0.00013 gives ~34 b/s at y=8000. */
        public double neutralCruiseRate = 0.00013;

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
        /** Per-direction tint switches (screen tint per flow direction, incl. neutral). */
        public boolean tintNorthEnabled = true;
        public boolean tintSouthEnabled = true;
        public boolean tintEastEnabled = true;
        public boolean tintWestEnabled = true;
        public boolean tintNeutralEnabled = true;
    }
}
