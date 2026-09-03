# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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

[1.1.0]: https://github.com/YOUR-USERNAME/elytra-jet-streams/releases/tag/v1.1.0
[1.0.0]: https://github.com/YOUR-USERNAME/elytra-jet-streams/releases/tag/v1.0.0
