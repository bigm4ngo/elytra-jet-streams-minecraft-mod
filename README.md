<div align="center">

# Elytra Jet Streams

**High-altitude one-way wind highways for elytra flight — for Minecraft (Fabric) 26.1.x**

Ride curving jet streams, lock into currents at stream crossings, cruise without rockets
at 20→100 b/s, and hit supersonic speeds on firework thrust — while the mod quietly
pre-loads the chunks ahead of you.

</div>

---

## ✈ What is this?

Above **y = 300** (configurable), the world is overlaid with a procedural **2D flow
field**: curving, continuous rivers of wind arranged in a grid of **latitude/longitude
bands**. Every stream flows in **one direction only** — you can travel in all four
cardinal directions across the map, but each individual stream is a one-way channel.

- **Streams** are 500–1000 blocks wide with a soft parabolic core, and **meander** gently
  so they curve instead of running ruler-straight.
- **Dead zones** (100–500 blocks) separate the streams: low-wind gutters where you glide
  on your own power.
- **Crossings** are where an east/west band intersects a north/south band — handled by
  *vector locking*, so you never get flung sideways mid-flight.

The layout is **generated randomly from your world seed the first time it is needed** and
reproducible forever after — zero storage, zero packets, identical on server and client.

## ⚡ Core mechanics

### Altitude-scaled firework thrust
Firework rockets obey the real-ish exponential equation

```
multiplier(y) = e^(0.00032 · (y − 300))        (y clamped at 8000)
```

| Altitude | Rocket multiplier | Approx. top speed |
|---:|---:|---:|
| y = 300 | ×1.00 | ~33 b/s (vanilla) |
| y = 2000 | ×1.72 | ~57 b/s |
| y = 4000 | ×3.27 | ~109 b/s |
| y = 6000 | ×6.20 | ~207 b/s |
| y = 8000 | ×11.75 | **~390 b/s** — supersonic territory |

The boost *curve* (not just a speed cap) is implemented by scaling the rocket's thrust and
target speed rather than its whole velocity blend — vanilla physics is preserved exactly
below the activation altitude.

### No-rocket cruising
Inside a stream core at **y ≥ 2000** the stream carries you: gravity is neutralized and
your along-wind speed converges to the cruise curve

```
cruise(y) = 20 · e^(0.000268 · (y − 2000))      (y clamped at 8000)
```

— exactly **20 b/s at y=2000** rising to **100 b/s at y=8000**, the same exponential
family as the thrust multiplier. Cruise is a *stream* privilege: it disengages when you
leave the core, dive (**pitch > 40°**), or **sneak**, which also gently brakes. Getting
out of a stream is always one deliberate move — never a trap.

### One-way, by design
Streams are **one-way highways**. Stream speed — both the firework altitude multiplier and
no-rocket cruise — only applies while you travel **along** the current:

- fly against or across a stream (or in a dead zone) and rockets behave exactly like vanilla,
  with headwind drag punishing the fight, and
- cruise axis-locks your velocity onto the stream, so its speed can never be carried sideways
  or backwards. The current decides the direction; you decide the pitch.

### Vector locking at intersections
When an eastbound stream crosses a northbound one, the first current you entered **locks**:
cross-forces from the other stream are suppressed and you glide straight through.
To switch currents, **steer into the other stream's direction** (within 40° of its flow)
for ~1.25 s — a capture meter fills, the lock switches, and a short cooldown prevents
flip-flopping. Enter a crossing at speed and you keep your lane; enter slowly and the
stronger/more-aligned current captures you.

### Inertia
At highway speeds sharp turns cost speed: the faster you go, the more a hard yaw change
bleeds momentum — take the curves wide or drop out of the stream to slow down.

## 🎨 Player experience

- **Directional stream particles** — colored streak chains drift along each current's flow,
  reading as motion arrows you can navigate by. They spawn *only where a current flows* —
  dead zones are visually silent. Colors match the tint palette.
- **Cirrus bands** — huge, faint flow-aligned cloud puffs near cruise altitude, placed on
  a deterministic lattice that matches the actual stream layout.
- **Sky darkening** — the sky deepens toward navy exponentially with altitude
  (`1 − e^(−k·(y−300))`, ~88% darkening at y=8000), horizon fog included.
- **Direction tints** — while gliding, a subtle screen tint tells you which cardinal
  direction the current carries you (N/S/E/W, colors configurable) and a softer neutral
  tone appears in dead zones.
- **Headwind indicator** — a faint warm tint creeps in at the screen edges while
  you fight a headwind.

All FX are client-side, toggleable, budgeted, and disabled below the activation altitude.

## 🚀 Performance (built for 390 b/s)

Fast flight normally shreds chunk streaming. This mod counterattacks:

1. **Corridor pre-loading** — while you glide fast, the server requests chunks along your
   predicted path a few per tick (throttled, width- and length-capped), so terrain exists
   *before* you arrive instead of exploding in a burst.
2. **Hypersonic freeze** — at/above **250 b/s** (configurable) the mod stops requesting
   *new* chunks entirely: you ride exclusively on what was pre-loaded while accelerating.
3. **Expiring tickets** — corridor chunks self-release (no world leak), and everything
   a player ticketed is cleaned up on disconnect.
4. **Zero-storage field** — stream layout is pure hash arithmetic from the world seed:
   no files, no network sync, amortized O(1) sampling, thread-safe.
5. **No anticheat fight** — vanilla already allows 300 blocks/tick for elytra movers, and
   our worst case (~19.6 b/t at y=8000) is far inside it, so no rubber-banding at top speed.

Server owners: `chunk-sending.per-tick` (default 8) comfortably covers ~24 chunks/s of
corridor travel; the preloader's per-tick budget spreads the rest of the work out.

## 🖥 Multiplayer & singleplayer

- **Server-authoritative physics, client-predicted.** The server syncs its physics config
  and a derived (non-reversible) field seed to every modded client on join and reload —
  prediction matches authority, no ghost corrections, and raw world seeds never leave the
  server.
- **Dedicated servers**: install on the server; vanilla clients can still join (they ignore
  the mod's packets) but will experience rubber-banding at stream speeds — the mod is
  intended for both sides.
- **Singleplayer / LAN**: works out of the box (the integrated server uses the same code).
- Works in any non-ceiling dimension by default (Overworld, End, modded sky dimensions);
  configurable via `dimensionMode`/`dimensionWhitelist`.

## 🎮 How to fly it

1. Craft fireworks and an elytra as usual.
2. Climb above **y = 300** — look for drifting colored streaks; you're in a stream. Their
   direction *is* the current.
3. Above **y = 2000**, align with the flow and level off: the stream lifts you.
4. Fire rockets to accelerate; the higher you are, the harder they push.
5. At a crossing, keep your lane or steer into the other current for a moment to switch.
6. To descend: **sneak** to brake and sink, or pitch down past **40°** to dive out.
7. `/jetstreams info` shows your current region, multiplier, cruise target and speed.

## 📦 Installation

- **Minecraft 26.1.x**, **Fabric Loader ≥ 0.19**, **Fabric API**, Java **25+**.
- Drop the jar in `mods/` on the client and/or server.

## ⚙ Configuration

Everything lives in `config/jetstreams.json` (created on first launch, hot-reloadable via
`/jetstreams reload`). Physics values are server-authoritative and synced to clients;
cosmetic values stay local.

### 🖱 In-game config screen (ModMenu)

Install [ModMenu](https://modrinth.com/mod/modmenu) and its **ModMenu → Elytra Jet Streams →
Configure** screen gives you three tabs:

- **Visuals** — every cosmetic toggle, the particle density, tint strength, and per-direction
  color pickers. Editable client side by **anyone**, applied instantly.
- **Flight speed** and **World & perf** — the full physics config, including the activation
  altitude `k` constant. Changes are submitted to the server, which only accepts them from
  the singleplayer owner or a server **operator**; without permission the server rejects the
  change with a chat message. Applied settings are saved, re-validated and re-synced to
  every connected player.

ModMenu is **optional** — the mod works fine without it (edit the JSON instead).

### `physics` (synced, operator-editable via ModMenu)

| Key | Default | Description |
|---|---:|---|
| `enabled` | `true` | Master switch for the whole system. |
| `dimensionMode` | `NO_CEILING` | `ALL`, `NO_CEILING` (any sky dimension), or `WHITELIST`. |
| `dimensionWhitelist` | `["minecraft:overworld"]` | Used when `dimensionMode` = `WHITELIST`. |
| `layoutSeedOverride` | `-1` | ≥ 0 rerolls the stream layout for this world. |
| `streamWidthMin` / `Max` | `500` / `1000` | Stream width range (blocks). |
| `deadZoneMin` / `Max` | `100` / `500` | Dead zone width range (blocks). |
| `directionMode` | `ALTERNATING` | `ALTERNATING` guarantees an opposite lane next to every stream; `RANDOM` is fully random. |
| `meanderAmplitude` / `Wavelength` | `140` / `2400` | Centerline wobble (blocks) — how much streams curve. |
| `activationAltitude` | `300` | Stream activation altitude `k` in the multiplier equation. |
| `multiplierRate` | `0.00032` | Exponential rate of the rocket multiplier. |
| `speedCapAltitude` | `8000` | Altitude where multiplier & cruise speed peak. |
| `fireworkKickPerTick` | `2.5` | Max boost delta per tick (b/t) — smooths the high-altitude kick. |
| `boostMinAlignment` | `0.15` | Velocity-vs-flow cosine below which the stream boost is suppressed. |
| `boostFullAlignment` | `0.85` | Cosine at/above which the full multiplier applies. |
| `cruiseStartAltitude` | `2000` | Where no-rocket cruise begins. |
| `cruiseSpeedBase` / `Peak` | `20` / `100` | Cruise speed (b/s) at start / cap altitude. |
| `cruiseGain` | `0.08` | Per-tick approach rate toward cruise speed. |
| `windBoostSpeed` | `30` | Max tailwind (b/s) added below cruise altitude. |
| `windAccelPerSecondSq` | `12` | Stream acceleration toward tailwind target (b/s²). |
| `headwindDragPerTick` | `0.008` | Drag when flying against the current. |
| `turnDrag` | `0.9` | Sharp-turn speed bleed at max turn rate. |
| `turnAngleThresholdDeg` | `7` | Yaw change (°/tick) that counts as a sharp turn. |
| `captureAngleDeg` | `40` | Steer within this angle of the other stream to switch. |
| `captureSeconds` | `1.25` | Time to hold the steer to switch currents. |
| `captureCooldownSeconds` | `1.5` | Lock resists switching again for this long. |
| `divePitchDeg` | `40` | Pitch beyond which cruise lift disengages. |
| `coreProfileForCruise` | `0.55` | Minimum stream core strength to hold cruise. |
| `gravityCompensationPerTick` | `0.08` | Vanilla gravity impulse cancelled during cruise. |
| `chunkPreloadEnabled` | `true` | Corridor pre-loading master switch. |
| `preloadAheadSeconds` | `2.5` | Seconds of path to keep loaded ahead. |
| `preloadMinSpeed` | `20` | Min speed (b/s) before pre-loading engages. |
| `corridorHalfWidthChunks` | `1` | Extra chunk columns each side of the path. |
| `maxCorridorChunks` | `48` | Corridor length cap (chunks). |
| `maxTicketedChunksPerPlayer` | `160` | Simultaneous corridor chunks per player. |
| `preloadTicketsPerTick` | `3` | New chunk requests per player per tick. |
| `hypersonicSpeed` | `250` | Speed (b/s) where the freeze kicks in. |
| `hypersonicFreeze` | `true` | No new chunks at/above the hypersonic threshold. |
| `preloadExpiryTicks` | `600` | Ticket lifetime (ticks). |

### `client` (local — editable by anyone via ModMenu)

| Key | Default | Description |
|---|---:|---|
| `skyTint` / `skyTintStrength` | `true` / `1.0` | Exponential altitude sky darkening. |
| `directionParticles` | `true` | Directional stream particles (none in dead zones). |
| `particleDensity` | `1.0` | Global FX density multiplier. |
| `cirrusBands` | `true` | Flow-aligned cirrus puffs near cruise altitude. |
| `streamTints` | `true` | Per-direction screen tint while gliding. |
| `tintStrength` | `0.7` | Global tint strength multiplier (0–2). |
| `tintNorth` / `South` / `East` / `West` | `#7FB4FF` / `#FFB454` / `#59E0A0` / `#C77DFF` | Stream tint per flow direction. |
| `tintNeutral` | `#8C99A8` | Dead-zone neutral tint. |
| `headwindIndicator` | `true` | Warm edge tint while fighting a headwind. |

## 🛠 Building from source

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runServer      # smoke-test on a dedicated server
```

Requires Java 25 (Loom provisions the toolchain automatically).

## ❓ FAQ

**Why one-way streams?** Real jet streams flow one way; the band grid guarantees an
opposite-direction lane one dead zone away, so four-directional travel stays simple and
chaotic head-on collisions inside a single stream never happen.

**Can I change where streams are?** They're a pure function of the world seed + config.
Set `layoutSeedOverride` to any number to reroll the layout; tune widths, meander and
direction mode in the config.

**Does the field touch chunks or disk?** No. Sampling is pure arithmetic against lazily
cached band boundaries (amortized O(1)), so it is safe from any thread at any speed.

**Does it work with... vanilla servers?** The server needs the mod for gameplay; vanilla
*clients* joining a modded server are safe (packets ignored) but get rubber-banding at
stream speeds. Modded client + vanilla server: streams simply won't exist (server is
authoritative).

## 📄 License

[MIT](LICENSE) — free to use, study, modify and redistribute with attribution.

<div align="center"><sub>Designed for Minecraft 26.1.x · Fabric · v1.1.0</sub></div>
