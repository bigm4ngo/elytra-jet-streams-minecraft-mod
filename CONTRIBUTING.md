# Contributing to Elytra Jet Streams

Thanks for your interest in improving the sky highways! 🌬️

## Getting started

```bash
git clone https://github.com/YOUR-USERNAME/elytra-jet-streams
cd elytra-jet-streams
./gradlew build       # first run downloads Minecraft + toolchain
./gradlew runServer   # smoke-test on a dedicated server (headless friendly)
./gradlew runClient   # full client in a dev environment
```

Requirements: Java 25 (auto-provisioned via Gradle toolchains), an internet connection on
first build. The project uses **split source sets**: gameplay/simulation code in
`src/main/java`, rendering/FX/HUD in `src/client/java` — keep that boundary intact so the
jar stays dedicated-server safe.

## Design rules of the codebase

1. **The field is pure math.** `field/` must never touch chunks, entities or IO. If you
   need world data for a feature, derive it from the seed + coordinates.
2. **Physics is deterministic and double-computed.** `FlightPhysics.apply` runs on both
   sides; any input you add must be identical on client and server (that's why config is
   synced — don't read client-only state in physics).
3. **No allocation in hot paths.** Sampling runs up to a few times per player per tick;
   keep it allocation-free (return records, reuse nothing global).
4. **Respect the budget culture.** FX and chunk tickets are budgeted per tick. New
   features that spawn things must have caps and expiry.
5. **Performance regressions are bugs.** A mod built around 390 b/s flight gets profiled.

## Submitting changes

- Fork, branch (`feat/my-feature`), commit in logical units, open a PR.
- Describe *why*, not just *what*; screenshots/GIFs for visual changes are love.
- `./gradlew build` must pass (this also runs remapJar + sources jar).
- Update `README.md` config tables and `CHANGELOG.md` for user-facing changes.

## Reporting bugs

Open an issue with: Minecraft version, mod version, singleplayer/dedicated, a short
reproduction, and `logs/latest.log`. For "moved too quickly"/rubber-band reports include
the y-level and rough speed (`/jetstreams info`).

## Versioning

SemVer-ish: `MAJOR.MINOR.PATCH` where MINOR bumps are MC-version migrations.
