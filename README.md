<div align="center">

# Elytra Jet Streams

**High-altitude one-way wind *tunnels* for elytra flight — for Minecraft (Fabric) 26.1.x**

Ride curving 3D jet stream tunnels, lock into currents at tunnel crossings, cruise without
rockets in streams *and* above the neutral-cruise altitude, and hit supersonic speeds on
firework thrust — while the mod quietly pre-loads the chunks ahead of you.

</div>

---

## ✈ What is this?

Above **y = 300** (configurable), the sky is filled with procedurally generated **3D
tunnels**: finite, meandering horizontal tubes of fast air, surrounded on all sides by
**neutral zones** — dead-calm air where nothing pushes you. The tunnels are the mod's
one-way highways.

- **Tunnels** are **elliptical tubes**: at cruise altitudes (y≈300) roughly 100–300 blocks
  wide and 100–150 tall; at the scale altitude (y=6000+) up to **5000 wide and 2000 tall**.
  They run **500–2000 blocks** at the bottom, **8000+ blocks** (with rare 10–20k+ monsters)
  up high, and **meander** gently so they curve instead of running ruler-straight.
- **Everything between tunnels is neutral zone.** Spacing scales with altitude: around
  y=300 you expect a tunnel roughly every ~300 blocks; near y=6000+ every 1.5k–2k blocks
  — with probabilistic gaps, since each tunnel has an existence chance.
- **Tunnels are fully isolated** — they end wherever the dice put their ends. Finding the
  next one (bright rim markers help) *is* the navigation game.
- **Crossings** happen where an east/west tunnel intersects a north/south one — handled by
  *vector locking*, so you never get flung sideways mid-flight.
- **Dimensions follow smooth bell curves** that lerp from the base altitude to the scale
  altitude: widths/heights/lengths are bell-distributed (typical sizes common, extremes
  rare; length additionally gets a fat tail), so the sky "grows" smoothly with altitude and
  complements the speed multipliers.

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
Two cruise systems share one idea — gravity neutralized, speed converging to a curve:

- **In-tunnel cruise** (inside a stream core at **y ≥ 2000**): your along-wind speed
  converges to
  ```
  cruise(y) = 20 · e^(0.000268 · (y − 2000))      (y clamped at 8000)
  ```
  — exactly **20 b/s at y=2000** rising to **100 b/s at y=8000**. It axis-locks your
  velocity onto the current: cruise speed can only be carried *along* the flow.
- **Neutral-zone cruise** (anywhere outside tunnels at **y ≥ 4000**): no current exists,
  so any direction works — but the pace is far slower:
  ```
  neutral(y) = 20 · e^(0.00013 · (y − 4000))      → 20 b/s at y=4000, ~34 b/s at y=8000
  ```
  It pulls your whole horizontal speed (whatever heading you hold) toward the curve, so
  even hopping between tunnels gets a helping hand up high.

Both disengage when you dive (**pitch > 40°**) or **sneak**, which also gently brakes.
Fighting a tunnel's current never grants cruise: one-way means one-way.

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

- **Flow streaks** — colored streak chains drift along each tunnel's flow, reading as
  motion arrows you can navigate by. They spawn *only inside tunnels* — neutral zones are
  visually silent. Colors match the tint palette.
- **Rim markers** — big, bright particles lining each tunnel's elliptical border, biased
  along *your* heading so the wall ahead lights up before you hit it. Flying in neutral
  air near a tunnel? Its near rim is lit too, advertising the tunnel mouth. Borders are
  unmistakable at every density — and at max density the lining feels like a lit tube.
- **Cirrus bands** — huge, soft cloud billboards drifting *inside large tunnels*, aligned
  with the flow (a fix from v1.1.0's unreachable lattice: clouds now live where the air
  actually moves).
- **Sky darkening** — the sky deepens toward navy exponentially with altitude
  (`1 − e^(−k·(y−300))`, ~88% darkening at y=8000), horizon fog included.
- **Direction tints** — while gliding, a subtle screen tint tells you which cardinal
  direction the current carries you (N/S/E/W) and a softer neutral tone appears in
  neutral zones. Every direction's tint can be switched off individually (including the
  neutral one), so you keep only the cues you want.
- **Headwind indicator** — a faint warm tint creeps in at the screen edges while
  you fight a headwind.
- **Colour wheel + hex** — every tint slot (N/S/E/W + neutral) opens an HSV colour wheel
  editor (click/drag for hue + saturation, slider for brightness) *and* accepts a pasted
  `#RRGGBB` code in the hex field; both update the preview live.

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
2. Climb above **y = 300** — look for bright rim markers and drifting colored streaks;
   that's a tunnel. The streaks' direction *is* the current.
3. Above **y = 2000**, align with the flow inside a tunnel and level off: the stream lifts
   you. Above **y = 4000** even the empty air between tunnels will carry you (slowly).
4. Fire rockets to accelerate; the higher you are, the harder they push.
5. At a crossing, keep your lane or steer into the other current for a moment to switch.
6. To descend: **sneak** to brake and sink, or pitch down past **40°** to dive out.
7. Lost? `/jetstreams locate <north|south|east|west>` (operator only) reports the nearest
   point **inside** a stream flowing that way — coordinates, distance and tunnel size,
   with a click-to-teleport suggestion.
8. `/jetstreams info` shows your region, the tunnel's dimensions, multipliers and cruise
   targets.

## 📦 Installation

- **Minecraft 26.1.x**, **Fabric Loader ≥ 0.19**, **Fabric API**, Java **25+**.
- Drop the jar in `mods/` on the client and/or server.

## ⚙ Configuration

Everything lives in `config/jetstreams.json` (created on first launch, hot-reloadable via
`/jetstreams reload`). Physics values are server-authoritative and synced to clients;
cosmetic values stay local.

### 🖱 In-game config screen (ModMenu)

Install [ModMenu](https://modrinth.com/mod/modmenu) and its **ModMenu → Elytra Jet Streams →
Configure** screen gives you one tab: **Visuals** — every cosmetic toggle, particle density,
tint strengths, and the per-direction colour editors (colour wheel **or** pasted hex code),
plus a row of per-direction tint on/off switches. Editable client side by **anyone**,
applied instantly, saved locally. The colour block is anchored above the Done button so
it never covers another setting.

Since v1.2.0, **physics settings are no longer in the screen** — they live exclusively in
the config file (operator territory, applied via `/jetstreams reload`). This keeps the
authority model simple: what you see in the screen only affects your own client.

ModMenu is **optional** — the mod works fine without it (edit the JSON instead).

### `physics` (synced, file-only edits + `/jetstreams reload`)

| Key | Default | Description |
|---|---:|---|
| `enabled` | `true` | Master switch for the whole system. |
| `dimensionMode` | `NO_CEILING` | `ALL`, `NO_CEILING` (any sky dimension), or `WHITELIST`. |
| `dimensionWhitelist` | `["minecraft:overworld"]` | Used when `dimensionMode` = `WHITELIST`. |
| `layoutSeedOverride` | `-1` | ≥ 0 rerolls the tunnel layout for this world. |
| `tunnelScaleBaseAltitude` | `300` | Altitude where tunnels start (bell curves sit at their low end here). |
| `tunnelScaleAltitude` | `6000` | Altitude where the bell curves reach their high end (and hold above). |
| `tunnelWidthMin` / `Max` | `100` / `300` | Tunnel width range (blocks) at the base altitude. |
| `tunnelWidthMinHigh` / `MaxHigh` | `1000` / `5000` | Tunnel width range at the scale altitude and above. |
| `tunnelHeightMin` / `Max` | `100` / `150` | Tunnel height range at the base altitude. |
| `tunnelHeightMinHigh` / `MaxHigh` | `600` / `2000` | Tunnel height range at the scale altitude and above. |
| `tunnelLengthMin` / `Max` | `500` / `2000` | Tunnel length range at the base altitude. |
| `tunnelLengthMinHigh` / `MaxHigh` | `8000` / `14000` | Tunnel length range at the scale altitude and above (a rare fat tail can push lengths to ~1.6× the max). |
| `tunnelGapMin` / `Max` | `150` / `350` | Guaranteed minimum wall-to-wall gap between neighboring tunnels at the base altitude. |
| `tunnelGapMinHigh` / `MaxHigh` | `800` / `1200` | Same guarantee at the scale altitude. |
| `tunnelExistenceChance` | `0.8` | Probability a lattice slot actually contains a tunnel (probabilistic spacing). |
| `tunnelDistributionTightness` | `1.0` | Bell-curve sigma scaler — higher clusters sizes toward the middle of each range. |
| `directionMode` | `ALTERNATING` | `ALTERNATING` guarantees an opposite-direction lane next to every tunnel; `RANDOM` is fully random. |
| `meanderFraction` | `0.18` | Centerline wobble as a fraction of tunnel width. |
| `meanderWavelengthCells` | `3.0` | Meander wavelength in cell widths (how far streams curve). |
| `activationAltitude` | `300` | Stream activation altitude `k` in the multiplier equation. |
| `multiplierRate` | `0.00032` | Exponential rate of the rocket multiplier. |
| `speedCapAltitude` | `8000` | Altitude where multiplier & cruise speeds peak. |
| `fireworkKickPerTick` | `2.5` | Max boost delta per tick (b/t) — smooths the high-altitude kick. |
| `boostMinAlignment` | `0.15` | Velocity-vs-flow cosine below which the stream boost is suppressed. |
| `boostFullAlignment` | `0.85` | Cosine at/above which the full multiplier applies. |
| `cruiseStartAltitude` | `2000` | Where in-tunnel cruise begins. |
| `cruiseSpeedBase` / `Peak` | `20` / `100` | Tunnel cruise speed (b/s) at start / cap altitude. |
| `neutralCruiseStartAltitude` | `4000` | Where neutral-zone cruise begins (outside tunnels). |
| `neutralCruiseSpeedBase` | `20` | Neutral cruise speed (b/s) at its start altitude. |
| `neutralCruiseRate` | `0.00013` | Exponential growth rate of the neutral cruise curve (~34 b/s at y=8000). |
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
| `coreProfileForCruise` | `0.55` | Minimum stream core strength to hold tunnel cruise. |
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
| `directionParticles` | `true` | Flow streaks + tunnel rim markers (none in neutral zones). |
| `particleDensity` | `1.0` | Global FX density multiplier (0–6; max feels like MAX). |
| `cirrusBands` | `true` | Cirrus billboards inside large tunnels. |
| `streamTints` | `true` | Per-direction screen tint while gliding. |
| `tintStrength` | `0.7` | Global tint strength multiplier (0–2). |
| `tintNorth` / `South` / `East` / `West` | `#7FB4FF` / `#FFB454` / `#59E0A0` / `#C77DFF` | Stream tint per flow direction (pick via colour wheel or hex code). |
| `tintNeutral` | `#8C99A8` | Neutral-zone tint (pick via colour wheel or hex code). |
| `tintNorthEnabled` … `tintNeutralEnabled` | `true` | Per-direction tint switches (including the neutral zone tint) — turn off just the directions you find noisy. |
| `headwindIndicator` | `true` | Warm edge tint while fighting a headwind. |

## 🛠 Building from source

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runServer      # smoke-test on a dedicated server
```

Requires Java 25 (Loom provisions the toolchain automatically).

## ❓ FAQ

**Why one-way tunnels?** Real jet streams flow one way; the ALTERNATING direction mode
guarantees an opposite-direction lane one gap away, so four-directional travel stays
simple and chaotic head-on collisions inside a single tunnel never happen.

**Can I change where tunnels are?** They're a pure function of the world seed + config.
Set `layoutSeedOverride` to any number to reroll the layout; tune the dimension ranges,
gap, existence chance and direction mode in the config.

**Do tunnels follow the speed scaling?** Yes — widths, heights, lengths and spacing all
follow bell curves that lerp smoothly (smoothstep) from the base altitude to the scale
altitude, complementing the exponential speed multipliers as you climb.

**Does the field touch chunks or disk?** No. Sampling is pure arithmetic against lazily
cached slab boundaries and memoized tunnel records (amortized O(1), a couple of hash
lookups per sample), so it is safe from any thread at any speed.

**Does it work with... vanilla servers?** The server needs the mod for gameplay; vanilla
*clients* joining a modded server are safe (packets ignored) but get rubber-banding at
stream speeds. Modded client + vanilla server: streams simply won't exist (server is
authoritative).

## 📄 License

[MIT](LICENSE) — free to use, study, modify and redistribute with attribution.

<div align="center"><sub>Designed for Minecraft 26.1.x · Fabric · v1.2.0</sub></div>
