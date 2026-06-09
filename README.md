# StrideMap

StrideMap is a personal Android GPS app for recording real reference tracks.

The current v1 app records real GPS captures, saves user-accessible GPX files under `Documents/StrideMap/Tracks/`, lists local tracks, and displays selected/live routes on OpenStreetMap via osmdroid. It is not simulator-first, and it does not include remote sync, accounts, SQLite route storage, or PostgreSQL/API work yet.

## Current v1 shape

- Material 3 Compose shell with bottom tabs exactly `Capture`, `Tracks`, and `Map`.
- Settings opens from a top-corner gear and persists safe defaults with `SharedPreferences`.
- Movement types are Walk, Run, Bike, Car, and Train; Walk is the default for new captures.
- One live track is allowed at a time; states are `live`, `stopped`, and `interrupted`.
- Foreground-service background recording uses `START_NOT_STICKY`; no boot receiver, auto-start, or auto-resume.
- Production location uses an app-owned `LocationProvider` abstraction backed by Google Play Services `FusedLocationProviderClient`; no `LocationManager` fallback in v1.
- Saved-point sampling is fixed: first valid point, then at least 1 second and at least 10 meters from the last persisted point.
- Provider polling interval is movement/settings-based and separate from saved-point sampling; Settings changes apply to future captures only.
- Public GPX storage uses MediaStore public Documents; the app-private active-session GPX journal is the recovery source of truth.

## Validation commands

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:installDebug --no-daemon
"/Users/use/Library/Android/sdk/platform-tools/adb" shell am start -W -n com.example.stridemap/.MainActivity
```

Primary source of truth for v1 behavior: `agentic/knowledge/features/init-creating-all-defined-components.md`.
