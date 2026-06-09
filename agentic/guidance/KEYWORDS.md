# Keyword index

Flat index of abbreviations and technical terms that appear in StrideMap notes.

- **Adaptive stationary mode** — Later/future tracking idea from earlier MVP notes; not current v1 behavior.
- **Android 16 / API 36** — Initial Android platform target for both emulator and real-device validation.
- **Background location** — Permission and OS capability needed to collect location while the app is not in the foreground.
- **Balanced route shape** — Accuracy/battery posture that preserves meaningful route geometry without chasing maximum precision or maximum battery saving.
- **Deterministic simulator** — Repeatable route input system used to test tracking logic without relying on real GPS.
- **Distance-targeted sampling** — V1 saved-point rule: first valid point, then persist only after at least 1 second and at least 10 meters from the last persisted point.
- **Foreground service** — Android service pattern with a persistent notification, used for reliable long-running background tracking.
- **Fused Location Provider** — Google Play services location API used by the v1 production `LocationProvider` implementation.
- **GPS jitter** — Small noisy position changes that can appear even when the device is stationary or moving smoothly.
- **GPX/KML playback** — Route-file playback formats that may be useful for emulator or mock-location testing.
- **Kalman-style filtering** — Possible smoothing/state-estimation approach for speed, heading, uncertainty, or motion state; not a guaranteed battery-saving mechanism.
- **Local-first storage** — Architecture posture where GPX tracks are captured and usable locally before remote sync exists.
- **LocationManager** — Android platform location API; no v1 fallback is planned because production uses Fused Location Provider.
- **LocationProvider abstraction** — Internal boundary that lets the tracking engine consume real, simulated, or test locations through one interface.
- **Minimum stored-point interval** — Hard cap preventing stored points from being written too frequently; initial value is 1 second.
- **Mock location** — Android/emulator mechanism for providing fake locations through the normal platform location pipeline.
- **MediaStore** — Android public-media/files API used to create GPX files under `Documents/StrideMap/Tracks/` without broad storage permissions or a SAF picker.
- **osmdroid** — Android map library used for OpenStreetMap display in v1.
- **Pixel 9 emulator** — Initial Android Studio emulator target for build, simulator, storage, and app-wiring validation.
- **PostgreSQL sync** — Deferred future direction for remote storage once local tracking is reliable.
- **Precise location** — Fine-grained location permission required for StrideMap core continuous tracking.
- **Public GPX folder** — User-accessible track output location: `Documents/StrideMap/Tracks/`.
- **Route fixture** — Synthetic route data used in tests to reproduce walking, running, biking, driving, stops, turns, jitter, or dropouts.
- **Samsung Galaxy S23** — Real-device validation target for GPS behavior, background reliability, notification behavior, and battery observations.
- **SharedPreferences** — Android key-value persistence used for v1 Settings/defaults.
- **SQLite** — Possible future local storage topic; not the current v1 route source of truth.
- **Stationary check interval** — Earlier MVP concept; not current v1 saved-point sampling behavior.
- **StrideMap** — Working title for the personal Android GPS/location-history app.
- **Target point spacing** — Desired persisted-point spacing; current v1 requires at least 10 meters after the first valid point.
