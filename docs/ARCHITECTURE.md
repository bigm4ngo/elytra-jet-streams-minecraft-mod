# Architecture & Design Notes

Technical reference for contributors. Version 1.2.0, Minecraft 26.1.x / Fabric.

```
┌────────────────────────────  common (both sides)  ──────────────────────────┐
│ field/     WindField ─ TunnelField (3D tunnels) ─ FieldRandom (splitmix64)  │
│ physics/   FlightPhysics (wind, cruise, lock, inertia) · FlightState/Tracker│
│            PhysicsResolver (server config vs synced RemotePhysics)          │
│ chunk/     FlightChunkPreloader (corridor tickets, hypersonic freeze)       │
│ network/   SyncPhysicsPayload (physics JSON + field seed, S2C)              │
│ mixin/     LivingEntityMixin (travelFallFlying TAIL)                        │
│            FireworkRocketEntityMixin (boost delta replacement)              │
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────────────────────  client (cosmetic only)  ───────────────────────┐
│ fx/      StreamParticleSpawner · StreakParticle (+Rim) · CirrusParticle     │
│ hud/     StreamOverlayHud (Fabric HudElement)                              │
│ gui/     JetStreamsConfigScreen (visuals-only) · ColorWheelScreen/Widget    │
│          LabelWidget (ModMenu config UI)                                    │
│ compat/  ModMenuIntegration (optional ModMenu entrypoint)                  │
│ mixin/   SkyRendererMixin · AtmosphericFogEnvironmentMixin → SkyTint        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 1. Flow field — 3D tunnels

### The tunnel lattice
Two independent tunnel families per world (NS tubes flow ±Z, EW tubes flow ±X). Tunnels
are stacked in **vertical slabs** from the base altitude upward; each slab's thickness is
`maxTunnelHeight(y) + verticalGap(y)` at that altitude, so tunnels grow apart as they grow
bigger. Tunnel vertical centers are clamped strictly inside their slab, so a position
query only ever checks **its own slab**.

Each family is an independent lattice of **(cell, segment)** slots:

| Family | Perpendicular cell (width lives here) | Along-flow segment (length lives here) |
|---|---|---|
| NS | X-cells of size `maxWidth + maxGap + slack` | Z-segments of `2·maxLength` |
| EW | Z-cells | X-segments |

A slot's tunnel exists with probability `tunnelExistenceChance`. When it does, every
parameter is hashed from `(fieldSeed, family salt, slot)`:

- **Dimensions** — Box–Muller bell curves between per-altitude min/max ranges that
  smoothstep-lerp from the base altitude (100–300 wide × 100–150 × 500–2000 long) to the
  scale altitude (1000–5000 × 600–2000 × 8000+), then hold above it. Length gets a 12%
  ×(1..2) fat tail → rare 10k–20k+ monsters. `tunnelDistributionTightness` scales σ.
- **Position** — the center is jittered but **clamped** so the full tunnel (width + meander
  amplitude + margin) stays inside its own cell/segment/slab. This is the key invariant:
  it makes neighbor checks unnecessary *and* guarantees the configured wall-to-wall gap
  (gap-first spacing: a 5000-wide tunnel always leaves ≥ ~1000 of neutral air around it).
- **Direction** — `ALTERNATING` flips sign by perpendicular-cell parity (adjacent lanes run
  opposite ways); `RANDOM` hashes it.
- **Meander** — `axis(along) = center + A·sin(2π·along/λ + φ)`, `A ≤ meanderFraction·width`
  (capped by the cell margin), λ in cell multiples. Flow = **centerline tangent**, oriented
  by the one-way sign.

### Cross-section & profile
The tube interior is elliptical:

```
q = (dPerp/halfW)² + (dy/halfH)²        inside ⇔ q ≤ 1
profile = (1 − q) · endFade(u01)
```

`endFade` smoothsteps the first/last 10% of the tunnel's length so tunnels taper rather
than stop dead. `profile` is 1 on the axis, 0 at the wall — the same "core" meaning cruise
and the alignment gate consume. `WindSample` carries both families; the combined region is
`CROSSING` when both claim the position, `DEAD_ZONE` (neutral zone) when neither.

### Sampling cost
A sample = 2 slot lookups (one per family) + up to 2 geometry tests. Slots are memoized
(`Tunnel` records in a bounded ConcurrentHashMap); slab boundaries are memoized cumulative
sums extended on demand. No storage, no packets, thread-safe.

### Seeds
The **field seed** = `splitmix64(worldSeed ^ JETSTREA)` on the server (or
`layoutSeedOverride`), synced to clients in `SyncPhysicsPayload` — MC 26.1 login packets no
longer carry any world seed, and the mix prevents seed-cracking from the field. Config
revisions participate in the field cache key, so `/jetstreams reload` rebuilds layouts.

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
- Leaving to a neutral zone clears the lock.

## 3. Speed curves

```
fireworkMultiplier(y) = exp(0.00032·(min(y,8000) − 300))          → ×11.75 at 8000
tunnelCruise(y)       = 20·exp(ln(5)/6000·(min(y,8000) − 2000))   → 20 b/s @2000, 100 @8000
neutralCruise(y)      = 20·exp(0.00013·(min(y,8000) − 4000))      → 20 b/s @4000, ~34 @8000
skyDarkening(y)       = 1 − exp(−0.000271·(min(y,8000) − 300))    → ~0.88 @8000
```

All share the exponential family by design (the tunnel cruise rate is derived:
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

Two flavors (`FlightState.cruiseKind`):

- **In-tunnel (kind 1, y ≥ 2000, core profile ≥ 0.55):** pulls the along-wind component
  toward `tunnelCruise(y)` at `cruiseGain` per tick — cruising *is* riding the current.
  **Axis lock (v1.1.0):** each cruise tick decays the cross-flow component ×0.90,
  projecting velocity onto the stream axis — cruise speed can only ever be carried *along*
  the current, never sideways across it.
- **Neutral-zone (kind 2, y ≥ 4000, no wind at all):** pulls the *whole horizontal speed*
  toward `neutralCruise(y)` regardless of heading (there is no current to align with), so
  players can traverse the calm gaps between tunnels without rockets — slowly. Standing
  still gets a nudge along the look direction so cruise can start from zero.

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
- There is **no C2S physics channel** (removed in v1.2.0 with the physics ModMenu pages):
  physics edits are file edits + `/jetstreams reload` by an operator.
- Vanilla clients on modded servers ignore the custom channel (safe join), but their local
  prediction lacks stream physics → rubber-banding at speed; the mod targets both sides.
- `environment: "*"` and split source sets keep the jar dedicated-server clean.

## 8. FX budgets

- Flow streaks: ≤ ~6 chains/tick while inside a tunnel (5-particle chains, colored per flow
  direction via the `jet_streak` particle's `JetFlowOption` data), lifetime 24–44 t; none in
  neutral zones. Density slider 0–6 scales everything.
- Rim markers (`rim_marker`): big, bright white particles placed exactly on a tunnel's
  elliptical wall (random angle, ahead-biased along-flow position), ~1–10/tick by density;
  in neutral air a 5-probe neighborhood search (velocity-ahead first) lights the nearest
  tunnel mouth. Lifetime 18–32 t.
- Cirrus: spawned only inside large tunnels (half-height ≥ 80) at ~2/tick, placed within the
  tunnel cross-section, lifetime 500–900 t, alpha 0.10–0.17.
- Direction tints: one full-screen ARGB fill per frame (alpha ≤ 90·strength); headwind edge:
  two gradient fills.
- Sky tint & fog: one ARGB lerp per frame each.
- Everything is disabled below `activationAltitude` and when the local dimension is
  disabled.
