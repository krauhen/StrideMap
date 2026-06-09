# Coding guidance

## Status

StrideMap v1 is a real GPS reference-track capture Android app. Treat `agentic/knowledge/SPEC.md` as the canonical implementation specification when it exists. Use `agentic/knowledge/features/init-creating-all-defined-components.md` and session summaries as supporting history/source material for keeping the spec current.

## Android style

- Prefer clear, typed Kotlin with explicit public boundaries.
- Keep modules/classes small and organized around one responsibility.
- Separate Android framework integration from tracking-domain logic where practical.
- Prefer dependency injection or simple constructor injection for components that need fake providers in tests.
- Do not introduce dependencies without explicit approval.

## Location tracking practices

- Keep the app-owned `LocationProvider` boundary between Android location APIs and tracking-domain logic.
- V1 production location uses Google Play Services `FusedLocationProviderClient`; do not add a `LocationManager` fallback unless explicitly approved.
- Make simulator/mock routes deterministic and repeatable for tests, but do not make simulator behavior the primary product surface.
- Do not treat emulator or mock behavior as proof of real GPS behavior.
- Core continuous tracking requires precise background location; approximate-only or foreground-only access must block and explain.
- Use a foreground service with a persistent notification for reliable background/screen-off tracking.
- Foreground service restart policy is `START_NOT_STICKY`; do not add boot receivers, pending-intent location updates, auto-start, or auto-resume in v1.

## Sampling and storage practices

- Saved-point sampling is non-configurable in v1: accept the first valid point, then only persist a later point when at least 1 second has elapsed and it is at least 10 meters from the last persisted point.
- Provider polling interval is movement/settings-based and separate from saved-point sampling.
- Changing Settings during an active capture must not mutate the already-started provider request.
- Public GPX files are written through MediaStore under `Documents/StrideMap/Tracks/`; no SAF picker or broad storage permissions in v1.
- The app-private active-session GPX journal is the recovery source of truth.
- Store coordinates, timestamps, accuracy, movement type, track state, and metadata needed for later debugging/sync.
- Keep remote sync/API/PostgreSQL deferred; future sync should model GPX plus metadata/journal semantics rather than assuming current SQLite rows.

## Documentation comments

- Explain assumptions that affect battery, accuracy, permissions, or privacy.
- Document open questions separately from implemented behavior.
- Avoid storing or displaying personal location data in examples.
