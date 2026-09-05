# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3.0] — 2026-09-06

Discoverability release: streams were far rarer in practice than designed (the lattice
was sized by maximum tunnel dimensions while tunnels average their midpoints), plus a
locate command and a reworked colour editor.

### Added
- **`/jetstreams locate <north|south|east|west>`** (operator only): finds the nearest
  point **inside** a stream flowing toward the requested cardinal direction — not
  necessarily the tunnel mouth. Prints coordinates, straight-line distance and tunnel
  dimensions, with a click-to-suggest teleport command.
- **Per-direction tint switches**: five new client toggles (`tintNorthEnabled`,
  `tintSouthEnabled`, `tintEastEnabled`, `tintWestEnabled`, `tintNeutralEnabled`, all
  default on) let you keep some directions tinted and others plain. The ModMenu colour
  section gained a row of on/off switches under the colour buttons; a direction's
  swatch dims while its tint is off.
- **Hex colour input**: the colour editor now takes both input methods — pick on the
  wheel *or* type/paste a `#RRGGBB` code into the new hex field; both update the wheel,
  brightness slider and preview live.

### Fixed
- **Streams are actually findable now** — the big one. The tunnel lattice was spaced by
  *maximum* tunnel dimensions, but tunnels *average* their midpoint sizes, so the real
  air coverage was ~4× below design (measured 2–12% of flight paths in wind; vertical
  dead bands at mid altitudes were nearly stream-free). Slab spacing now tracks the mean
  tunnel height, cell/period spacing uses the mean gap/length, and the default
  existence chance rose 0.7 → 0.8. Measured in-stream coverage at y=350…6500 is now
  10–27% of flight paths (perpendicular encounters every few hundred to ~2000 blocks).
- **Colour wheel glitch**: the wheel was drawn with a blit whose implicit source size
  equalled the destination size, wrapping the UVs past the texture edge — the "misplaced
  wheel / parts of two other wheels" artifact. The wheel is now blitted 1:1 with explicit
  source rect.
- **ModMenu layout**: the colour buttons could overlap the settings above them and the
  Done button at small GUI scales. The colour block is now bottom-anchored and compact;
  nothing overlaps at any height.

### Changed
- Rim approach markers now probe up to **320 blocks ahead** along your heading (was 40),
  so tunnel mouths advertise themselves much earlier.
- Tunnel fat tail capped at ×1.6 (matching the segment period) and tunnel centers are
  clamped into their segment, keeping the O(1) sampling invariant airtight.

## [1.2.0] — 2026-09-01

The 3D tunnel revamp: streams become finite elliptical **tubes** in a calm sky, plus a
colour wheel, cleaner config authority, and much louder borders.

### Added
- **3D jet stream tunnels** (replaces the 2D band grid):
  - streams are now finite, meandering **elliptical tunnels** with true vertical extent —
    100–300 blocks wide × 100–150 tall near y=300, growing smoothly (bell-distributed,
    smoothstep lerp with altitude) to 1000–5000 × 600–2000 at y=6000+;
  - lengths follow the same bell curves: 500–2000 blocks at the bottom, 8000+ up high,
    with a rare fat tail producing 10k–20k+ monsters in the upper sky;
  - **all air between tunnels is neutral zone** — gap-first spacing guarantees the
    configured wall-to-wall gap (≥1000 around a 5000-wide tunnel), with probabilistic
    existence (default 70%) so encounters feel organic: roughly one tunnel every ~300
    blocks at y=300, every 1.5k–2k at y=6000+;
  - tunnels are **fully isolated** (ends land wherever the dice put them) — finding the
    next one is the navigation challenge;
  - sampling stays O(1): two hash lookups per sample, memoized tunnels, no storage.
- **Neutral-zone cruise**: above **y=4000** (configurable) you can cruise without rockets
  even outside streams — starting at **20 b/s** and rising exponentially to ~34 b/s at
  y=8000. Any direction works in neutral air (there's no current to fight), but it stays
  much slower than in-tunnel cruise, and the usual escapes (sneak, dive) still apply.
  Traveling against a tunnel's current still grants nothing.
- **Colour wheel editor**: every tint slot (N/S/E/W + neutral) opens an HSV colour wheel —
  click/drag to pick hue + saturation, brightness slider, live preview swatch and hex
  readout. Cancel restores the previous colour.
- **Rim markers**: big, bright particles line each tunnel's elliptical border, biased along
  the player's own heading so the wall *ahead* lights up; near a tunnel in neutral air, its
  near rim is lit to advertise the mouth. New `rim_marker` particle type.
- `/jetstreams info` now prints the tunnel you're in (dimensions, axis altitude, progress)
  and both cruise targets (tunnel + neutral).

### Changed
- **ModMenu screen is visuals-only**: flight-speed and world/perf physics pages were
  removed. Physics values are now file-only (edit `config/jetstreams.json` +
  `/jetstreams reload`), removing the C2S `update_physics` channel entirely; the
  server-authoritative sync (`sync_physics`) is unchanged.
- **Flow streaks are bigger and brighter** (0.26–0.46 blocks, ~2× the alpha) and spawn
  *inside tunnels only*, aligned to the meandered flow; particle density now scales 0–6.
- **Cirrus clouds actually work now** (fix): they spawn inside large tunnels at cloud-
  visible alpha (0.10–0.17) instead of the old lattice that placed unreachable 5%-alpha
  billboards near y=1900–2650.
- Config: `streamWidthMin/Max`, `deadZoneMin/Max`, `meanderAmplitude/Wavelength` replaced
  by the `tunnel*` family (old keys are ignored safely on migration); new
  `neutralCruise*` keys; `tunnelScaleBaseAltitude`/`tunnelScaleAltitude` drive the
  smoothstep altitude curves.

### Fixed
- Cirrus bands were practically invisible (spawned too high, alpha 0.05–0.09, gated above
  y≈1600) — they are now tied to tunnels and clearly visible.

## [1.1.0] — 2026-09-01

One-way enforcement, directional visual language, and a full config GUI.

### Fixed
- **Streams are now truly one-way.** Stream speed (both the firework altitude multiplier and
  no-rocket cruise) only applies while traveling *along* the current's direction:
  - the firework multiplier scales with a smoothstep of the alignment between your velocity
    and the flow (fully suppressed against/across the stream or in dead zones — vanilla
    rocket physics there), and
  - cruise now axis-locks your velocity onto the stream, decaying any cross-flow drift, so
    cruise speed can never be carried sideways or backwards.
- Removed the small "T"-shaped marker under the crosshair (the old flow chevron HUD element).

### Added
- **Directional stream particles**: colored, flow-aligned streak chains spawn above the
  activation altitude wherever a current flows — and nowhere else (dead zones are visually
  silent). Toggleable (`directionParticles`) and density-configurable, client side.
- **Per-direction color tints**: while gliding, a subtle screen tint shows which cardinal
  direction the current carries you (N/S/E/W colors + a neutral tone for dead zones), with a
  global tint strength slider. Toggleable, client side.
- **ModMenu config screen** (optional dependency — compiled against ModMenu 18, works without it):
  - *Visuals* page: every cosmetic toggle, density/strength sliders and per-direction color
    pickers — editable client side by anyone, applied instantly.
  - *Flight speed* and *World & perf* pages: the full physics config, including the
    activation altitude k constant. Edits are submitted to the server, which validates that
    the player is a singleplayer owner or an operator before saving and re-syncing everyone.
- New C2S `update_physics` packet with server-side permission validation, sanitization and
  full re-sync + broadcast.

### Changed
- Client config keys: `windParticles` → `directionParticles`; new `streamTints`,
  `tintStrength`, `tintNorth/South/East/West/Neutral` color keys (missing keys fall back to
  defaults automatically).
- New physics keys: `boostMinAlignment` (0.15) and `boostFullAlignment` (0.85).
- The `jet_streak` particle type now carries its flow direction as particle data.

## [1.0.0] — 2026-09-01

First public release. Targets Minecraft 26.1.x (Fabric Loader ≥ 0.19, Fabric API, Java 25).

### Added
- Procedural 2D jet stream flow field over world X/Z:
  - one-way streams 500–1000 blocks wide with parabolic cores and gentle meander,
  - dead zones 100–500 blocks wide,
  - NS + EW band families with alternating directions (configurable to random),
  - deterministic layout derived from the world seed — no storage, no packets.
- Vector-locking at stream crossings: dominant current suppresses cross-forces; steer into
  the other stream for ~1.25 s to switch currents, with an anti-flip cooldown.
- Altitude-scaled firework multiplier `e^(0.00032·(y−300))`, capped at y=8000 (~×11.75,
  ≈390 b/s), implemented as thrust+target scaling so vanilla physics is preserved at ×1.
- No-rocket cruising inside stream cores: `20·e^(0.000268·(y−2000))` b/s (20 at y=2000,
  100 at y=8000), with forgiving escapes (sneak to brake, dive past 40° pitch, exit core).
- Headwind drag and high-speed turn inertia.
- Player FX: flow-aligned wind streaks, sparse deterministic cirrus bands, exponential sky
  darkening (sky + horizon fog), subtle headwind edge tint and flow-direction chevron.
- Performance systems: predictive corridor chunk pre-loading with expiring tickets,
  hypersonic freeze (no new chunk loads above 250 b/s unless preloaded), per-tick ticket
  budgets and per-player caps, disconnect cleanup.
- Server → client sync of authoritative physics config + derived field seed on join/reload.
- `/jetstreams info` and `/jetstreams reload` commands.
- Full JSON config (`config/jetstreams.json`) with validation and safe defaults.
- MIT license, README, CI workflow.

[1.2.0]: https://github.com/YOUR-USERNAME/elytra-jet-streams/releases/tag/v1.2.0
[1.1.0]: https://github.com/YOUR-USERNAME/elytra-jet-streams/releases/tag/v1.1.0
[1.0.0]: https://github.com/YOUR-USERNAME/elytra-jet-streams/releases/tag/v1.0.0
