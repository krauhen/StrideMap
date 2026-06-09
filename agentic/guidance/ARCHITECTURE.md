# Architecture guidance

## Status

StrideMap v1 is a real GPS reference-track capture Android app. `agentic/knowledge/SPEC.md` is the canonical single-source implementation specification for current behavior. `agentic/knowledge/features/init-creating-all-defined-components.md` remains the detailed historical feature record that feeds the spec.

## Domain context

- Purpose: personal GPS reference-track capture and local track history for the user.
- Non-goals for now: fleet tracking, live safety sharing, and social tracking.
- V1 is not simulator-first; captured GPX files are the real-data foundation for later development.
- Development path: Pixel 9 emulator first, then Samsung Galaxy S23 real-device validation.

## Assumption boundaries

- Do not describe a behavior as proven unless implementation and validation records support it.
- Current location stack is decided: app-owned `LocationProvider`, production `FusedLocationProviderClient`, no `LocationManager` fallback in v1.
- Current storage is decided: MediaStore public GPX files under `Documents/StrideMap/Tracks/` plus app-private active-session journal for recovery.
- Treat emulator/mock behavior as development evidence, not proof of real GPS/background/battery reliability.
- Keep personal location data out of repository artifacts.

## Possible architecture concerns

- Material 3 Compose app shell with bottom tabs exactly `Capture`, `Tracks`, `Map`; Settings opens from a top-corner gear.
- osmdroid/OpenStreetMap map with online tiles allowed by default when attribution, lifecycle forwarding, bounded cache, and graceful offline/error behavior are preserved.
- Foreground service for reliable background/screen-off tracking using `START_NOT_STICKY`; no boot receiver, auto-start, or auto-resume.
- Precise background location requirement and clear blocking flow when unavailable.
- One live track max with states `live`, `stopped`, and `interrupted`; stale live-with-points becomes `interrupted`, stale zero-point live is discarded.
- Saved-point sampling: first valid point, then at least 1 second and at least 10 meters from last persisted point.
- Provider polling interval is movement/settings-based and separate from saved-point sampling.
- Settings persists safe defaults with `SharedPreferences`; Settings changes do not mutate active captures.
- Deferred remote sync/API/PostgreSQL; future sync should model GPX plus metadata/journal semantics.

## Preferred design posture

- Separate Android framework adapters from tracking-domain logic.
- Keep simulator routes deterministic and reusable across tests/debug tooling.
- Make GPX/journal writes explicit and auditable for later sync and debugging.
- Prefer small, testable components over hidden background-service logic.
- Document battery, accuracy, permission, and privacy assumptions alongside architecture decisions.
