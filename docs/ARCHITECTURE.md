# Architecture & Design Notes

Technical reference for contributors. Version 1.1.0, Minecraft 26.1.x / Fabric.

```
┌────────────────────────────  common (both sides)  ──────────────────────────┐
│ field/     WindField ─ BandGrid ×2 (NS, EW) ─ FieldRandom (splitmix64)      │
│ physics/   FlightPhysics (wind, cruise, lock, inertia) · FlightState/Tracker│
│            PhysicsResolver (server config vs synced RemotePhysics)          │
│ chunk/     FlightChunkPreloader (corridor tickets, hypersonic freeze)       │
│ network/   SyncPhysicsPayload (physics JSON + field seed, S2C)              │
│ mixin/     LivingEntityMixin (travelFallFlying TAIL)                        │
│            FireworkRocketEntityMixin (boost delta replacement)              │
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────────────────────  client (cosmetic only)  ───────────────────────┐
│ fx/      StreamParticleSpawner · StreakParticle · CirrusParticle            │
│ hud/     StreamOverlayHud (Fabric HudElement)                              │
│ gui/     JetStreamsConfigScreen + LabelWidget (ModMenu config UI)          │
│ compat/  ModMenuIntegration (optional ModMenu entrypoint)                  │
│ mixin/   SkyRendererMixin · AtmosphericFogEnvironmentMixin → SkyTint        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 1. Flow field

### Band grids
Two independent band families per world:

| Family | Runs | Flows | Membership varies with |
|---|---|---|---|
| NS bands | north–south | ±Z | world **X** |
| EW bands | east–west | ±X | world **Z** |

Each grid is a list of cells `[start, start+streamWidth) ∪ [streamEnd, end)` where
`streamWidth ~ U[500,1000]` and `deadWidth ~ U[100,500]` are hashed per cell index from
`(fieldSeed, family salt, index)`. Cell starts are cumulative sums, so boundaries are
memoized in a `ConcurrentHashMap` and extended by walking only the missing segment
(`BandGrid.boundary`). Continuous flight hits cached boundaries → amortized O(1),
allocation-free sampling; even a cold teleport walk costs <1 ms.

Directions alternate per cell (with a random world-phase) so an opposite-direction lane is
always one dead zone away; `directionMode: RANDOM` allows same-direction neighbours.

### Meander
Each cell's centerline wobbles in the perpendicular axis:

```
center(u) = c0 + A·sin(2π·u/λ + φ_cell)        A=140, λ=2400 (defaults)
```

Flow direction is the **centerline tangent** (never the radial direction), oriented by the
cell's one-way sign — streams curve smoothly but never reverse or split.

### Sampling
`WindField.sample(level, x, z)` returns a `WindSample` with, per family: membership,
parabolic lateral profile `1 − (d/halfWidth)²`, and the unit flow vector. The combined
region is `CROSSING` when both families claim the position, `DEAD_ZONE` when neither.

### Seeds
The **field seed** = `splitmix64(worldSeed ^ JETSTREA)` on the server (or
`layoutSeedOverride`), synced to clients in `SyncPhysicsPayload` — MC 26.1 login packets no
longer carry any world seed, and the mix prevents seed-cracking from the field. Config
revisions participate in the grid cache key, so `/jetstreams reload` rebuilds layouts.

## 2. Vector locking (crossings)

`FlightState.dominantFamily ∈ {0=NS, 1=EW, -1}` per player per side:

- Entering a single stream sets dominance to that family.
- Entering a crossing with no lock: the current best aligned with the player's velocity
  (stronger profile when nearly stationary) captures them.
- In a crossing, the non-dominant family's force is **fully suppressed** — the applied wind
  is always the dominant family's vector.
- Switching: when the player's look direction is within `captureAngleDeg` (40°) of the
  other family's flow while moving >4 b/s, `switchProgress` fills over `captureSeconds`
  (1.25 s); at 100% the lock flips and `lockCooldown` (1.5 s) blocks an immediate flip
  back. Progress decays 2× faster than it builds.
- Leaving to a dead zone clears the lock.

## 3. Speed curves

```
fireworkMultiplier(y) = exp(0.00032·(min(y,8000) − 300))          → ×11.75 at 8000
cruiseSpeed(y)        = 20·exp(ln(5)/6000·(min(y,8000) − 2000))   → 20 b/s @2000, 100 @8000
skyDarkening(y)       = 1 − exp(−0.000271·(min(y,8000) − 300))    → ~0.88 @8000
```

All three share the exponential family by design (the cruise rate is derived:
`ln(peak/base)/(cap − start)`), which keeps the HUD/multiplier/cruise/sky progression
feeling like one coherent atmosphere.

## 4. Firework boost (the subtle part)

Vanilla boost per tick: `Δ = look·0.1 + (look·1.5 − v)·0.5`. The `(1.5L − v)` blend is a
**brake above 1.5 b/t** — scaling the whole delta by the multiplier would keep terminal
speed pinned at ~34 b/s (the pull just gets there faster).

Instead (`FireworkRocketEntityMixin` + `FlightPhysics.fireworkBoostDelta`):

```
Δ' = look·(0.1·m) + (look·(1.5·m) − v)·0.5      terminal v* = 1.666·m b/t ≈ 33.4·m b/s
```

- thrust **and** target scale with `m`; the velocity blend coefficient stays vanilla →
  terminal speed grows linearly with the multiplier (≈390 b/s at y=8000);
- at `m = 1` this is bit-identical to vanilla;
- the per-tick delta is length-clamped (`fireworkKickPerTick`, 2.5 b/t) so a fresh rocket
  at extreme altitude is a sustained burn rather than a catapult.

**One-way gate (v1.1.0):** `m` is really `altitudeMult · alignmentFactor`, where
`alignmentFactor` is a smoothstep of the cosine between the player's velocity and the
dominant flow, mapped over `[boostMinAlignment, boostFullAlignment]` (0.15 → 0.85).
Against/across the stream — or anywhere outside a stream — the factor is 0 and rockets are
vanilla. FlightPhysics computes it per tick from pre-push velocity; the mixin reads it.

Implementation is a differential HEAD/TAIL inject around `FireworkRocketEntity.tick`:
capture pre-velocity + look, then *replace* the applied delta — robust against exact
formula drift and identical on both sides.

## 5. Cruise lift

`updateFallFlyingMovement` applies a pitch-dependent sag
`gravity·(−1 + 0.75·cos²(pitch))` and vertical drag ×0.98. At the travel tail, cruise
cancels that exact sag and damps residual vertical velocity:

```
vy = clamp((vy − sag)·0.918, −0.025, +0.05)
```

Equilibrium is level flight (|vy| ≤ 0.5 b/s) with a bounded, gentle climb/dive while
steering — and hard escapes: sneak (brake + release) or pitch beyond `divePitchDeg` (40°).
Cruise also pulls the along-wind velocity component toward `cruiseSpeed(y)` at `cruiseGain`
per tick, so cruising *is* riding the current; fighting it disengages lift.

**Axis lock (v1.1.0):** each cruise tick decays the cross-flow velocity component ×0.90,
projecting the horizontal velocity onto the stream axis — cruise speed can only ever be
carried *along* the current, never sideways across it.

## 6. Chunk corridor pre-loader

Per gliding player above the activation altitude and `preloadMinSpeed`:

1. Predict path = linear extrapolation of horizontal velocity.
2. Corridor = `min(maxCorridorChunks, speed·preloadAheadSeconds/16)` chunks ahead,
   `2·corridorHalfWidthChunks + 1` columns wide, sorted by distance.
3. Add up to `preloadTicketsPerTick` (3) **not-yet-loaded** chunks per tick via
   `addTicketWithRadius(TICKET_TYPE, pos, 0)` (level 33 = render-ready full chunk).
4. **Hypersonic freeze** at `hypersonicSpeed` (250 b/s): no new requests — the player rides
   on pre-loaded chunks only (vanilla's own per-player view distance is untouched).
5. Tickets have no vanilla timeout; the loader removes them after `preloadExpiryTicks`
   (600) or on disconnect — nothing leaks, no dimension pinning (LOADING-only flag).

At the default top speed (~390 b/s ≈ 24 chunks/s), a 3-wide corridor at 3 tickets/tick
(60 chunks/s capacity) keeps up with margin, while spreading generation cost evenly.

## 7. Sync & compatibility

- Physics constants live in `physics` config; the **server's copy is authoritative** and is
  JSON-synced (S2C) on join and reload; clients substitute it via `PhysicsResolver` so
  prediction == authority. Cosmetic `client` config never syncs.
- Vanilla clients on modded servers ignore the custom channel (safe join), but their local
  prediction lacks stream physics → rubber-banding at speed; the mod targets both sides.
- `environment: "*"` and split source sets keep the jar dedicated-server clean.

## 8. FX budgets

- Streaks: ≤ ~5 spawns/tick while in-stream (5-particle chains, colored per flow direction
  via the `jet_streak` particle's `JetFlowOption` data), lifetime 22–42 t; none in dead zones.
- Cirrus: 256-block lattice, hashed sparsity, per-cell cooldown 1200 t, ≤ 2 spawns/sweep,
  lifetime 400–800 t; only inside stream cores, only near cruise altitudes.
- Direction tints: one full-screen ARGB fill per frame (alpha ≤ 90·strength); headwind edge:
  two gradient fills.
- Sky tint & fog: one ARGB lerp per frame each.
- Everything is disabled below `activationAltitude` and when the local dimension is
  disabled.
