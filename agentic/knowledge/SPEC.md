# StrideMap Technical Specification

Status: canonical current-application specification  
Last updated: 2026-06-09  
Primary audience: Android/Kotlin implementation agents and maintainers

## 1. Purpose and authority

This document is the single source document for implementing StrideMap as it exists now. It consolidates the current implementation, feature source material, session summaries, and observed validation state into an implementation-ready technical specification.

When this document conflicts with older session notes, older simulator-first notes, early SQLite/storage notes, or pre-polish wording, this document wins for current v1 behavior. Older documents remain useful as historical rationale and deferred-context records.

### 1.1 Source material

This specification synthesizes:

- `agentic/knowledge/features/init-creating-all-defined-components.md`.
- `agentic/sessions/summaries/2026-06-09__stridemap-sgs23__polling-and-track-recovery.md`.
- `agentic/sessions/summaries/2026-06-08__stridemap-grilling__capture-reference-track-v1.md`.
- `agentic/sessions/summaries/2026-06-08__stridemap-initial-grilling__gps-tracking-mvp.md`.
- Current app implementation under `app/`, Gradle configuration, Android manifest, and JVM tests.

### 1.2 Product summary

StrideMap is a personal Android GPS/location-history app. The current v1 product is a real GPS reference-track capture app, not a simulator-first prototype. Its main responsibility is to capture trustworthy user-owned real-world GPX traces that can be used as the foundation for later app features, downstream projects, and evaluation.

The app records tracks from the Android location stack, persists accepted points as GPX, stores public track files under `Documents/StrideMap/Tracks/`, shows local captured tracks in a library, and renders selected/live tracks on an OpenStreetMap map.

### 1.3 Current validation status

Implemented and previously validated:

- JVM unit suite passed after the initial build and subsequent recovery/settings changes.
- Debug assemble passed after the initial build and subsequent changes.
- Pixel 9 API 36 emulator build/install/launch/Start smoke was validated.
- Pixel 9 foreground recording notification presence was checked after Start.
- Samsung Galaxy S23 API 36 install/launch/visual validation was completed.
- User performed informal Samsung S23 walking captures around the block; these produced actionable feedback for provider polling defaults and existing-track recovery, but they are not yet considered full real-route correctness/background/screen-off validation.
- Samsung S23 All files access recovery flow was approved by the user and confirmed to work.
- Main-tab header removal, Capture-only inline Settings gear, automatic Tracks refresh on tab selection, top Map overlay placement, and bottom-right Map live/follow actions were implemented after Samsung S23 feedback; the user confirmed the tab/header/overlay arrangement was wanted.
- Hiding osmdroid built-in plus/minus zoom controls was implemented, debug-assembled, and installed on Samsung S23; pinch zoom remains enabled.

Still pending or not proven:

- Full real GPS route correctness on Samsung S23.
- Background and screen-off capture validation on Samsung S23.
- OEM battery behavior validation.
- Foreground notification behavior during a real outdoor capture.
- Public GPX visibility checks across file managers and app data clear/reinstall scenarios beyond the approved recovery flow.
- A structured Samsung S23 route-validation pass with expected route shape, background/screen-off behavior, notification behavior, and public GPX visibility.
- Long-running capture, process kill, startup stale-live recovery, and app/service failure scenarios under real device conditions.
- Accessibility audit and light-theme visual pass.

## 2. Scope, goals, and non-goals

### 2.1 In scope for current v1

- Material 3 Compose Android app shell.
- Bottom navigation tabs: `Capture`, `Tracks`, `Map`.
- Settings opened from an inline gear action on Capture, not from bottom navigation and not as a global main-tab overlay.
- Real GPS track capture through an app-owned `LocationProvider` abstraction backed by Google Play Services Fused Location Provider.
- High-accuracy foreground-service capture with a persistent notification.
- Precise background location requirement for core continuous recording.
- Public GPX output under `Documents/StrideMap/Tracks/` through MediaStore.
- App-private active-session GPX journal as the recovery source of truth.
- One live track at a time.
- Track lifecycle states: `live`, `stopped`, `interrupted`.
- Distance-targeted persisted-point sampling: first valid point, then both at least 1 second and at least 10 meters from the last persisted point.
- User-selectable movement type: Walk, Run, Bike, Car, Train.
- Optional private note for a capture.
- Local track library with movement filtering, date/distance sorting, automatic refresh when Tracks is selected, Settings-level rescan, live-row projection, and malformed-file rows.
- OpenStreetMap/osmdroid map display for selected and live tracks.
- Android Settings-style Settings surface for Capture, Tracks, Map, and Storage/App info defaults.
- Settings-only All files access recovery path for existing `.gpx` files after app data clear/reinstall when scoped storage cannot rediscover them.
- JVM unit tests for domain logic, GPX codec, storage constants, recovery planner, settings, and state projections.

### 2.2 Explicit non-goals for current v1

- No fleet tracking, social tracking, or live safety sharing.
- No accounts, authentication, cloud API, server, PostgreSQL, or remote sync.
- No delete-track action.
- No metadata editing after capture.
- No broad advanced GPS/sampling tuning UI.
- No configurable persisted-point sampling rule; Settings must not expose the 1-second/10-meter persistence rule.
- No boot receiver, no auto-start, no auto-resume, and no silent continuation after process death.
- No `LocationManager` fallback for production location capture.
- No app-private-only fallback for normal track output.
- No silent downgrade to approximate-only or foreground-only recording.
- No bulk/offline OSM tile predownload.
- No personal route artifacts committed to the repository.

### 2.3 Deferred/future context

- Adaptive stationary mode, Kalman-style filtering, and SQLite route storage are earlier/future topics, not current v1 behavior.
- Remote PostgreSQL sync remains deferred until local capture, GPX visibility, background behavior, and recovery are proven.
- Future sync should model GPX plus StrideMap metadata/journal semantics, not assume current implementation rows as an API contract.

## 3. Technology stack and project structure

### 3.1 Build and runtime stack

- Root project: `StrideMap`.
- Android module: `:app`.
- Namespace/application id: `com.example.stridemap`.
- Android compile SDK / target SDK / min SDK: API 36 in current build configuration.
- Language/runtime: Kotlin with Java 17 compile options.
- UI: Jetpack Compose + Material 3.
- Location: Google Play Services Location (`FusedLocationProviderClient`) behind app-owned abstraction.
- Map: osmdroid with OpenStreetMap MAPNIK tiles.
- Persistence: MediaStore public files, app-private journal files, SharedPreferences for app/settings metadata.
- Tests: JVM unit tests with JUnit; template instrumentation test exists but is not behavioral coverage.

### 3.2 Notable dependency state

Approved/used dependencies include Compose Material 3, Google Play Services Location, osmdroid, and AndroidX support libraries. `androidx.documentfile` remains in dependency metadata from an earlier SAF recovery attempt, but current normal storage uses MediaStore and current recovery uses direct read-only scan behind All files access.

Do not add, remove, or upgrade dependencies without explicit approval.

### 3.3 Package and source layout

Current package layout:

- `com.example.stridemap`
  - `MainActivity.kt`: launch activity, Compose app shell, screens, settings, theme, osmdroid host integration.
  - `StrideMapRepository.kt`: application state, settings, capture orchestration, scanning, selection, recovery, storage/service interaction.
- `com.example.stridemap.core`
  - Domain models, sampling/cadence, validation, distance, filename, ordering, and map/overlay helpers.
- `com.example.stridemap.session`
  - Pure capture/session lifecycle and state transitions.
- `com.example.stridemap.storage`
  - MediaStore public GPX storage, direct recovery scan contracts, app-private journal, storage constants, recovery planning.
- `com.example.stridemap.gpx`
  - GPX 1.1 writer/parser and StrideMap extension contract.
- `com.example.stridemap.location`
  - App-owned `LocationProvider`, request spec, and Google Play Services adapter.
- `com.example.stridemap.capture`
  - Foreground recording service.

## 4. Architecture overview

### 4.1 Architectural posture

StrideMap is local-first and capture-first. The app treats local GPX files plus app-private recovery journal semantics as durable state. UI state, capture state, and storage state are coordinated by a repository layer rather than being spread across screens.

Architectural boundaries:

- Android framework integration should stay behind adapters where practical.
- Location capture enters the app through `LocationProvider` rather than screens calling platform location APIs directly.
- GPX writing/parsing is in app code and unit-testable.
- Capture lifecycle rules are represented in pure state/session logic where practical.
- MediaStore and direct file recovery details are isolated in storage components.
- Compose UI renders repository state and calls repository/service entry points.

### 4.2 High-level component responsibilities

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Compose shell, permissions launchers, app lifecycle hooks, Settings navigation, osmdroid cache/user-agent setup, rendering Capture/Tracks/Map. |
| `StrideMapRepository` | Single app orchestration layer for `AppState`, setup readiness, settings persistence, track scan/select, capture start/stop, journal recovery, GPX snapshot writes, service coordination, transient messages. |
| `CaptureSessionController` | Pure lifecycle/state transitions: create live track, enforce one-live invariant, append accepted points, stop/save, stop/discard, mark interrupted, handle stale live recovery. |
| `LocationProvider` | Stable boundary for consuming Android or test location sources. |
| `GooglePlayServicesLocationProvider` | Production FLP adapter that requests high-accuracy location updates. |
| `CaptureForegroundService` | Long-running capture owner, foreground notification, location update subscription, callback bridge into repository, `START_NOT_STICKY` lifecycle. |
| `AndroidTrackStorage` | Public GPX MediaStore writes/listing, direct read-only recovery scan, URI/file reads, app-private active journal. |
| `GpxCodec` | GPX 1.1 serialization/deserialization, XML safety, StrideMap extension metadata. |

### 4.3 State model

The app state should expose at least:

- Setup readiness and blockers.
- Current live track, if any.
- Selected track for Map.
- Scanned GPX entries and malformed rows.
- Display entries that merge scanned entries with the active live track without duplication.
- Current Settings values.
- Transient user messages/snackbar events.
- Storage/recovery access state.

The repository owns mutations to this state. Screens should not implement independent business state machines for capture lifecycle, GPX persistence, or recovery.

## 5. Domain model

### 5.1 Movement type

Movement types are:

| Product label | Serialized value | Current use |
| --- | --- | --- |
| Walk | `walk` | Default movement type and movement identity. |
| Run | `run` | Movement identity and provider polling settings row. |
| Bike | `bike` | Movement identity and provider polling settings row. |
| Car | `car` | Movement identity and provider polling settings row. |
| Train | `train` | Movement identity and provider polling settings row. |

Movement type affects labeling, coloring, GPX metadata, library filters, map styling, and provider polling interval settings. It does not affect the persisted-point sampling rule.

### 5.2 Track state

Track states:

| State | Meaning |
| --- | --- |
| `live` | Capture is active or a GPX/journal draft represents an active capture. Points may be appended. |
| `stopped` | User intentionally stopped and saved/finalized the capture. Terminal in v1. |
| `interrupted` | App/process/service/storage failure or startup recovery found stale live data with points. Terminal in v1. |

Allowed transitions:

| From | To | Trigger |
| --- | --- | --- |
| none | `live` | Start succeeds and draft/session is created. |
| `live` | `stopped` | User confirms Stop and at least one point exists. |
| `live` | discarded | User confirms Stop before first GPS point. |
| `live` | `interrupted` | Startup detects stale live with points or unrecoverable live-capture failure after points exist. |
| `stopped` | terminal | No edit/reopen in v1. |
| `interrupted` | terminal | No resume in v1. |

### 5.3 Location point

A persisted location point contains:

- Latitude in decimal degrees.
- Longitude in decimal degrees.
- Timestamp.
- Optional horizontal accuracy in meters.
- Optional Android-provided speed in meters per second.
- Optional elevation in meters only when Android honestly provides altitude or a parsed GPX already contains valid elevation.

Do not synthesize, infer, smooth, or backfill elevation in current v1.

### 5.4 Track metadata

A track contains:

- Stable id/reference used by app state.
- Filename/display name.
- Optional user note/message.
- Movement type.
- Track state.
- Persisted accepted points.
- Created/start timestamp.
- Updated/latest timestamp.
- Cached distance in meters.
- Optional completed duration in seconds for stopped/interrupted tracks.

Distance is computed from accepted persisted points only. Rejected updates do not contribute to distance.

## 6. Capture behavior

### 6.1 Start gating

Start must be blocked unless all required conditions pass:

- Movement type is selected; fresh state defaults to Walk.
- Fine/precise location is granted.
- Approximate-only location is not the effective state.
- Background location is granted.
- Device location services are enabled.
- Notification permission is granted when required by the platform.
- App-private recovery journal directory is available.
- MediaStore public Documents target is available/writable for `Documents/StrideMap/Tracks/`.
- Foreground service can legally start.
- No existing live track blocks the one-live invariant.

All files access recovery permission is not a Start blocker. It only affects optional read-only rediscovery of existing public `.gpx` files after app data clear/reinstall.

### 6.2 Permission UX

The Capture readiness UI must distinguish:

- Missing location permission.
- Approximate-only location.
- Foreground-only location when background is missing.
- Disabled device location services.
- Missing notification permission.
- Storage/journal/MediaStore unavailability.
- Existing live capture.
- Foreground service startup failure where detectable.

The app must block and explain rather than silently degrading. Background location may require opening Android settings instead of requesting everything in one dialog.

### 6.3 Start sequence

On Start:

1. Read the current idle capture inputs and current Settings defaults.
2. Generate a new filename using UTC timestamp, movement type, and sanitized note prefix.
3. Create a new live track/session with zero points.
4. Persist app-private active journal state.
5. Create/write a valid public GPX draft/snapshot where possible.
6. Start the foreground recording service.
7. Request high-accuracy location updates using the active capture's provider polling interval.
8. Show user-visible confirmation such as `Capture started`.
9. Route according to `afterStartDestination`; current default is `Open Map`.

Recording enters `live` immediately even if no GPS point has arrived yet.

### 6.4 Location request and provider polling

Production capture uses `FusedLocationProviderClient` through `GooglePlayServicesLocationProvider`. The app-owned `LocationProvider` contract exists for testability and future alternative inputs.

Provider polling interval is settings-based and movement-specific. Current default provider polling interval is 1000ms for every movement type. User-selected provider intervals are clamped to safe bounds: minimum 1000ms, maximum 60000ms.

Provider polling is not the persisted-point sampling rule. A 1000ms provider interval can still produce fewer persisted points when movement is below the 10-meter threshold.

If Android delivers delayed, batched, or irregular location updates, preserve the provider timestamp truthfully. Do not rewrite timestamps to match receipt time. Log or surface enough non-private diagnostic context to debug cadence issues without exposing route traces.

### 6.5 Persisted-point sampling

Persisted-point sampling is fixed and non-configurable in v1:

- Accept the first valid location point.
- Accept a subsequent point only when both are true:
  - elapsed time from the last accepted persisted point is at least 1 second;
  - distance from the last accepted persisted point is at least 10 meters.

Rejected valid-but-too-soon/too-close updates may still influence live service internals or logs, but they must not be stored as GPX track points or counted toward track distance.

### 6.6 Point validation

Validate points before persistence:

- Latitude must be in `[-90, 90]`.
- Longitude must be in `[-180, 180]`.
- Timestamp must be present and non-decreasing for the track.
- Accuracy, when present, must be positive.
- Speed, when present, must be non-negative.
- Elevation, when present, must be finite.
- Exact duplicate timestamp+coordinate points are rejected unless a future spec explicitly allows them.

Poor accuracy over 25m is accepted but flagged. The UI should show a warning such as low GPS accuracy, and GPX should preserve accuracy metadata.

### 6.7 Live stats semantics

Live stats are based on capture state:

- Elapsed live time should use capture start time, not only saved-route duration, so a stationary capture with one point still shows a running timer.
- Distance is computed from accepted persisted points only.
- Point count is accepted persisted point count.
- Accuracy displays current/latest accuracy when available.
- Before first point, show a waiting state rather than a false completed/empty track state.

### 6.8 Stop sequence

Stop opens a Material 3 confirmation dialog. Cancel/dismiss keeps recording unchanged.

If at least one point exists:

1. User confirms `Stop`/`Stop and save`.
2. Stop location updates.
3. Finalize state as `stopped`.
4. Persist completed duration and distance metadata.
5. Write validated public GPX snapshot and update/clear active journal as appropriate.
6. Stop foreground service.
7. Keep the stopped track selected for Map.

Completed duration is capture start-to-stop elapsed time, not route geometry duration. A stopped track with one accepted point must still persist and reload a non-zero duration when the capture was active for non-zero time.

If zero points exist:

1. Show discard-empty variant.
2. User confirms discard.
3. Discard empty draft/track.
4. Clear active journal as appropriate.
5. Stop foreground service.

### 6.9 Failure/interruption behavior

If foreground service startup fails, Start must fail visibly and must not leave an orphan live capture.

If storage write fails during live capture:

- Stop accepting new points.
- Stop location updates.
- Mark the track `interrupted` if it has points.
- Discard the track if it has zero points.
- Surface a user-visible error.

If app/process/service death is discovered on startup:

- Stale live with points becomes `interrupted`.
- Stale live with zero points is discarded.
- Do not auto-resume.
- Do not auto-start a new track.

Activity recreation while the same-process service/controller still owns the capture must not mark the live track interrupted.

## 7. GPX contract

### 7.1 Format

StrideMap writes GPX 1.1 with:

- Standard GPX namespace.
- Creator `StrideMap`.
- StrideMap extension namespace: `https://stridemap.app/gpx/1`.
- App schema version: `1`.

The GPX writer/parser is implemented in app code; no external GPX codec dependency is used in current v1.

### 7.2 Metadata extensions

GPX metadata/extensions should store:

- `stridemap:movementType`.
- `stridemap:trackState`.
- `stridemap:message`.
- `stridemap:distanceMeters`.
- `stridemap:durationSeconds`.
- `stridemap:appSchemaVersion`.

Metadata timestamps are UTC. UI may render local time.

### 7.3 Track point representation

Each GPX track point stores:

- `lat` and `lon` attributes.
- `<time>` timestamp.
- Optional standard GPX 1.1 `<ele>` only when valid elevation exists.
- Optional `stridemap:accuracyMeters` extension.
- Optional `stridemap:speedMetersPerSecond` extension when Android reports speed.

Do not invent interpolated geometry, synthetic speeds, or synthetic elevation.

### 7.4 Parser safety

The parser must reject DOCTYPE input before XML parsing and configure XML parser hardening best-effort, tolerating Android parser features that may not be supported. Malformed GPX files should be represented safely in Tracks rather than crashing the app or leaking private route contents.

### 7.5 Empty live GPX

An empty live GPX metadata file is valid so a capture can start before the first GPS fix. Empty live captures are discarded when the user stops before the first point or when startup recovery finds stale zero-point live state.

## 8. Storage and recovery

### 8.1 Normal public storage

Normal capture/export writes public GPX files through MediaStore:

- Collection: `MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)`.
- Relative path: `Documents/StrideMap/Tracks/`.
- Display path: `Documents/StrideMap/Tracks`.
- MIME type: `application/gpx+xml`.

No SAF picker and no broad storage permission are required for normal new captures.

### 8.2 Public write strategy

Public GPX snapshots use a full valid GPX write strategy:

1. Set `IS_PENDING=1` while replacing content when supported.
2. Open output stream with truncate/write semantics.
3. Write the full GPX snapshot.
4. Read back and validate that the generated GPX parses.
5. Set `IS_PENDING=0` after success.

The app should avoid corrupting an existing valid GPX during metadata/state updates. The app-private journal is the recovery source of truth when public writes are interrupted or cannot be trusted.

### 8.3 App-private active-session journal

The active-session journal is stored in app-private files, currently under a StrideMap recovery directory with an active capture GPX snapshot. SharedPreferences metadata tracks journal/session state.

The journal is authoritative for recovery while a capture is active. Public MediaStore GPX is the user-visible snapshot, not the only source of truth during live capture.

### 8.4 Startup recovery

On startup or repository initialization:

- Verify app-private recovery storage and public MediaStore target availability.
- Reconcile active journal/canonical state.
- Preserve stopped journal state when needed.
- Mark stale live-with-points as `interrupted` and write valid GPX state.
- Discard stale live zero-point captures.
- Do not auto-resume or auto-start capture.

### 8.5 Existing-track recovery after app data clear

Scoped storage may not reliably rediscover older GPX files after app data clear/reinstall even if files physically remain in `/sdcard/Documents/StrideMap/Tracks`.

Rationale: after app data clear on Samsung S23, older `.gpx` files could remain physically present while MediaStore/scoped-storage queries and app-UID filesystem access did not reliably expose them to the current app install. Android media permissions do not cover GPX documents. SAF folder and multi-file recovery attempts were rejected or unusable through Samsung's picker for the target folder, so All files access was explicitly accepted as a personal/development recovery exception.

Current approved recovery behavior:

- Manifest declares `MANAGE_EXTERNAL_STORAGE` only for a Settings-driven recovery path.
- Settings → Storage/App info exposes `Recover existing tracks`.
- The action opens Android's All files access settings page for StrideMap when possible, with fallback to the general page.
- When `Environment.isExternalStorageManager()` is true, rescans include read-only `.gpx` files from `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx`.
- Direct recovery uses case-insensitive `.gpx` filtering.
- Recovered file refs are read-only in StrideMap.
- Direct recovery should scan regular files only, sort deterministically, map refs as read-only `file:` references, and read recovered files as UTF-8.
- Direct recovery must not mutate recovered files.
- All files access is not required for normal capture and must not be added as a Start blocker.

### 8.6 Filename convention

Filenames use UTC sortable timestamp, movement type, and optional sanitized note prefix:

```text
YYYY-MM-DD_HH-mm-ssZ_<movement-type>_<sanitized-note-prefix>.gpx
```

Rules:

- Timestamp is UTC and includes seconds.
- Include movement type.
- Include up to 16 sanitized characters from the note when present.
- Remove path separators and control characters.
- Prefer lowercase ASCII and `[a-z0-9-]` for the note prefix.
- Collapse repeated dashes and trim leading/trailing dashes.
- Avoid overwriting existing files; append collision suffixes as needed.

## 9. Android manifest and platform behavior

### 9.1 Manifest permissions

Current behavior requires or declares:

- `ACCESS_FINE_LOCATION`.
- `ACCESS_COARSE_LOCATION` so approximate-only can be detected and explained.
- `ACCESS_BACKGROUND_LOCATION`.
- `FOREGROUND_SERVICE`.
- `FOREGROUND_SERVICE_LOCATION`.
- `POST_NOTIFICATIONS` where required.
- `INTERNET` for online OSM/osmdroid tiles.
- `MANAGE_EXTERNAL_STORAGE` only for Settings-driven read-only GPX recovery.

Do not add legacy `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_*` for normal GPX capture unless a future spec explicitly changes storage strategy.

### 9.2 Foreground service

Foreground capture service requirements:

- Service is non-exported.
- Declares location foreground-service type.
- Starts a persistent notification promptly.
- Notification identifies active StrideMap recording and returns to the app.
- Uses notification channel for capture recording.
- Returns `START_NOT_STICKY`.
- Does not use boot receivers.
- Does not use pending-intent location updates.
- Does not auto-resume after process death.

### 9.3 Location services check

The implementation may use Android `LocationManager` only to check whether device location services/providers are enabled. It must not use `LocationManager` as a production capture fallback in current v1.

## 10. User interface specification

### 10.1 App shell

The app uses Material 3 Compose with a `Scaffold`, bottom `NavigationBar`, and fixed StrideMap light/dark palettes.

The three main tabs should not have persistent top headers or root top app bars. Settings subpages may use their own top bars and back navigation.

Top-level tabs are exactly:

- `Capture`.
- `Tracks`.
- `Map`.

Settings opens from a circular inline gear action on Capture. Settings is not a fourth bottom tab and should not be a floating global overlay across Tracks or Map.

Back/navigation behavior must never stop an active recording. Active capture can only stop through the explicit Stop confirmation flow.

Internal naming caveat: current code may still use `AppTab.List` internally for the Tracks tab. Product/spec terminology is `Tracks`.

### 10.2 Visual identity

The brand direction is quiet-premium charcoal with muted green. Dark theme is the lead identity.

Known palette values from current implementation records include:

- Dark background: `#101412`.
- Dark surface: `#151A17`.
- Dark surface variant: `#222B25`.
- Muted green primary: `#8FB996`.
- Primary container: `#254632`.
- Light background: `#F7F8F2`.
- Light surface: `#FFFCF7`.
- Light primary green: `#426B4A`.
- Light primary container: `#C4D8C1`.

Movement color identity:

| Movement | Light | Dark |
| --- | --- | --- |
| Walk | `#2E7D4F` | `#8FB996` |
| Run | `#C2412D` | `#FF8A65` |
| Bike | `#007C89` | `#4DD0E1` |
| Car | `#9A6500` | `#FFC857` |
| Train | `#6750C8` | `#B7A7FF` |

Movement identity must not be color-only; labels/icons remain required.

### 10.3 Capture screen

Capture is the initial screen. It is a recorder dashboard: configure note and movement type, verify readiness, start/stop.

Implemented/required elements:

- Private-first helper copy, not developer/file-system-first copy.
- Optional note field, default template from Settings, disabled while recording.
- Movement selector using chips with icons/colors; default Walk; disabled while recording.
- Movement choices should wrap across multiple lines on narrow devices rather than requiring horizontal scrolling.
- Primary Start/Stop recording button near the top of the capture flow.
- Current polished button copy is `Start recording` / `Stop recording`; exact copy may evolve, but it must clearly communicate recording state.
- Setup checklist/card that collapses to compact ready state when all blockers pass and expands when Start is blocked.
- Live stats panel during recording.
- Waiting-for-first-point state.
- Low-accuracy warning when accuracy is worse than 25m.
- Current filename/status text while recording.
- Stop/discard confirmation dialog.

Start button must remain visible while disabled; readiness copy explains why.

### 10.4 Tracks screen

Tracks is the local capture library.

Implemented/required behavior:

- Startup, Settings-level rescan, and automatic refresh when the Tracks tab becomes selected scan local GPX entries.
- Display rows from `AppState.displayEntries`, including a live projection when recording.
- Live tracks sort ahead of stopped/interrupted tracks where applicable.
- Movement filter supports All/Walk/Run/Bike/Car/Train.
- Sort supports Date and Distance.
- Order label adapts to sort: newest/oldest or longest/shortest.
- Current UI direction uses dropdown controls for Movement, Sort, and adaptive Order labels rather than filter-chip rows. Preserve accessible labels and current values for each control.
- The Tracks tab currently has no visible refresh FAB/button; entering the tab triggers a non-destructive track scan. Manual rescan remains available from Settings → Storage/App info.
- Valid track rows show message or fallback label, date/time, duration where available, distance, movement badge, and state chip when live/interrupted.
- Malformed GPX files appear as safe error rows/details and are not silently ignored.
- Tapping a valid track selects it and navigates to Map.
- Tapping malformed rows does not navigate to Map.
- No delete/edit action exists in v1.
- Track scanning must tolerate GPX files being added, removed, or modified outside the app.
- If the selected file disappears externally, clear selection and show a non-fatal message.
- Refresh during recording must not blank existing rows, duplicate the live row, or treat transient public-write state as fatal.

### 10.5 Map screen

Map is the selected/live route viewer using osmdroid/OpenStreetMap.

Implemented/required behavior:

- Configure stable osmdroid user agent.
- Use app cache directories for osmdroid cache/tiles.
- Forward lifecycle events to `MapView` and detach on dispose.
- Keep OSM attribution visible.
- Use online MAPNIK tiles by default.
- Show Material empty state when no selected/live track exists.
- Map empty state should include actions to start capture and open Tracks when practical.
- The selected/live track info overlay appears at the top center with status-bar padding.
- The Map overlay is intentionally one line: movement type, local date/time, distance, and tracked time. It must not display the custom capture note/message unless explicitly re-approved.
- Live-map actions such as `Show live` and `Follow live location` appear bottom-right.
- Pinch zoom remains enabled; osmdroid built-in plus/minus zoom controls are hidden.
- Show waiting state for live zero-point capture.
- Render selected/live track route line using movement color plus high-contrast casing for multi-point routes.
- Render start/end/latest-live markers with distinct meaning beyond color alone.
- Optionally render intermediate saved-point dots according to Settings.
- Handle one-point and degenerate tracks without invalid bounds/zoom crashes.
- Auto-zoom selected non-live tracks to route bounds.
- Follow live latest point until user pans/zooms; provide Follow action to resume.
- Map display priority: if the user explicitly selected a stored track, show that track even while recording continues. If no stored track is selected and a live track exists, show the live track. Provide a simple way to return to the live track while recording, or explicitly document the current behavior.
- Avoid unsafe `zoomToBoundingBox` calls during Compose update; viewport updates should be guarded, posted after layout, non-animated where necessary, and de-duplicated.

### 10.6 Settings

Settings is opened by the Capture screen's inline circular gear action and follows Android Settings-style grouped rows and subpages. Settings remains outside bottom navigation; Settings pages keep their own top bars/back affordances.

Root sections:

1. `Capture defaults`.
2. `Tracks defaults`.
3. `Map defaults`.
4. `Storage / App info`.

Settings persistence uses Android `SharedPreferences`. Missing/invalid values fall back to safe defaults.

Settings changes affect future captures/screen sessions unless explicitly safe to apply immediately. Settings changes must not mutate an already-started provider request or active capture movement type.

## 11. Settings data contract

Current `UserSettings` shape:

```kotlin
data class UserSettings(
    val defaultMovementType: MovementType = MovementType.Walk,
    val afterStartDestination: AfterStartDestination = AfterStartDestination.Map,
    val defaultCaptureNote: String = "",
    val defaultTrackSortField: TrackSortField = TrackSortField.Date,
    val defaultTrackSortAscending: Boolean = false,
    val defaultTrackMovementFilter: MovementType? = null,
    val followLiveByDefault: Boolean = true,
    val showSavedPointDots: Boolean = true,
    val routeLineWidth: Float = 8f,
)
```

Additional per-movement GPS polling interval settings are persisted separately in SharedPreferences and exposed through app state/settings UI.

Development-phase migration note: the 1-second provider polling defaults apply to fresh settings. Existing development installs with previously persisted SharedPreferences values may retain old per-movement polling intervals until app data is cleared or the user changes them manually. No migration was requested for this development-phase change.

### 11.1 Capture defaults

- Default movement type: Walk/Run/Bike/Car/Train; default Walk; applies to new captures only.
- GPS polling interval: per movement type; default 1000ms; clamped 1000ms-60000ms; applies to future captures only.
- After starting recording: Open Map or Stay on Capture; default Open Map.
- Default note/template: trimmed/capped defensively; pre-fills idle future capture setup.

### 11.2 Tracks defaults

- Default movement filter: All or specific movement; default All.
- Default sort: Date or Distance; default Date.
- Default order: newest first for Date by default; labels adapt by sort.

### 11.3 Map defaults

- Follow live track: boolean; default On; seeds initial live-map follow behavior.
- Show saved point dots: boolean; default On; hides only intermediate saved-point dots when Off.
- Route line thickness: preset/clamped float; default 8px.

### 11.4 Storage / App info

- Shows track folder path.
- Provides refresh/rescan action.
- Provides Recover existing tracks action for All files access recovery.
- Shows simple package/debug info.
- Must avoid exposing private raw paths or personal route content unnecessarily.

## 12. Error handling and privacy

### 12.1 Error handling principles

- Prefer typed failures and explicit blocker states.
- Preserve root-cause details in logs without leaking personal location traces, private route contents, secrets, signing data, or sensitive paths.
- User-facing errors should explain what failed and what action can be taken.
- Snackbars are acceptable for transient confirmations/errors but must not be the only explanation for critical permission/storage blockers.

### 12.2 Location errors

The app distinguishes:

- Fine location missing.
- Approximate-only grant.
- Background location missing.
- Device location services disabled.
- Provider unavailable/request failure.
- Foreground service failure.
- Battery/OEM restrictions where detected or documented.

Core continuous capture must block rather than silently degrade for precise/background/foreground-service requirements.

`MANAGE_EXTERNAL_STORAGE` is policy-sensitive and approved only for this personal/development Settings-driven recovery flow. It must not be generalized into normal storage strategy, delete/edit support, sync import, or Start readiness without explicit product approval.

### 12.3 Storage and GPX errors

- Public write failure during Start should fail visibly and avoid orphan live state.
- Public write failure during live capture should interrupt/discard according to point count.
- Parser failures should create safe malformed rows.
- Malformed rows should not expose full private paths or raw route contents.
- Direct recovery files are read-only; recovery should not mutate existing files unless a future spec explicitly approves it.

### 12.4 Privacy constraints

- Do not commit real personal location traces.
- Do not use private addresses or copied personal route details in examples.
- Do not treat screenshots with private map context as safe artifacts without explicit approval.
- Public GPX files are intentionally user-accessible on-device, but repository artifacts should remain synthetic/generic.

## 13. Testing and verification strategy

### 13.1 Unit-test coverage expected/current

JVM unit tests should cover and currently include coverage for:

- Point validation.
- Poor-accuracy warning behavior.
- Persisted-point cadence: first point, 1 second, 10 meters.
- Haversine/distance calculations.
- Live elapsed time vs route duration.
- Filename sanitization and collision behavior.
- Track ordering and display projections.
- GPX writer/parser round trips.
- GPX namespace/schema fields.
- GPX point extensions and elevation omission/order.
- Empty live GPX parsing.
- Stopped one-point duration persistence.
- DOCTYPE rejection and malformed GPX safety.
- Capture session start, one-live invariant, append, stop/save, stop/discard.
- Stale live recovery handling.
- Setup readiness blocker logic.
- MediaStore path/MIME/query constants.
- Direct recovery path constants.
- Journal recovery planner.
- Location request defaults and clamping.
- AppState defaults, UserSettings clamping, and live-entry de-duplication.

### 13.2 Instrumented/manual validation

Emulator validation proves app wiring, not real GPS reliability. Real-device validation on Samsung S23 is required for GPS/background/battery claims.

Samsung S23 battery/OEM validation still needs a defined method: test duration, metrics to record, and acceptance thresholds are not yet specified.

Standard local commands:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:installDebug --no-daemon
"/Users/use/Library/Android/sdk/platform-tools/adb" shell am start -W -n com.example.stridemap/.MainActivity
```

Use 600-second timeouts for normal Gradle test/build shell runs unless a task specifies otherwise.

### 13.3 Requirement-to-verification matrix

| Requirement | Verification approach | Current status |
| --- | --- | --- |
| Capture starts only when readiness passes | Unit readiness tests + manual permission checks | Implemented; manual edge cases still useful |
| Approximate-only blocks Start | Manual permission scenario + setup blocker tests | Implemented intent; needs explicit device validation |
| Background missing blocks Start | Manual Android settings scenario | Implemented intent; needs explicit device validation |
| Provider polling defaults 1000ms | Unit tests for request spec/settings | Implemented and tested |
| Persisted sampling first point + 1s + 10m | Unit tests + real route validation | Unit-tested; real route pending |
| GPX parseable empty live draft | Unit tests | Implemented and tested |
| Stop one-point duration persistence | Unit tests + rescan manual check | Implemented and tested |
| Live foreground notification | Emulator notification check + real-device check | Emulator checked; real capture pending |
| MediaStore public GPX write | Unit storage constants + manual device/file visibility | Implemented; full visibility validation pending |
| All files recovery read-only scan | Unit constants + SGS23 user confirmation | Implemented and confirmed |
| Stale live recovery | Unit recovery tests + process-kill manual test | Unit-tested; process-kill pending |
| Tracks malformed rows | GPX parser tests + manual invalid file | Unit-tested; manual optional |
| Tracks filter/sort | Unit ordering + manual UI | Implemented |
| Tracks auto-refresh on tab selection | Code inspection + manual tab navigation | Implemented; user accepted final tab/header arrangement |
| Map degenerate routes | Unit/helper + manual map checks | Implemented after emulator freeze fix |
| Map one-line top overlay excludes custom message | Unit test for overlay text helper + manual UI | Implemented and tested |
| Live map follow | Manual route validation | Implemented; real-route validation pending |
| osmdroid built-in zoom buttons hidden while pinch zoom remains | Code inspection + debug assemble/install | Implemented; assemble/install passed |
| Settings defaults persist | Unit/settings tests + manual UI | Implemented |
| Settings do not mutate active capture | Unit/manual active capture scenario | Implemented by design; manual validation useful |
| Accessibility basics | Manual/audit | Pending deeper audit |

## 14. Implementation caveats and naming notes

- Product tab name is `Tracks`; internal enum naming may still say `List`.
- Package/application id remains `com.example.stridemap`.
- `DocumentFile` dependency remains from a superseded SAF recovery path; current behavior does not rely on SAF for recovery.
- `MANAGE_EXTERNAL_STORAGE` is an approved exception only for Settings-driven recovery. It must not become normal capture storage or a Start blocker.
- Provider polling is movement/settings-based, but current default interval is 1000ms for all movement types.
- Saved-point sampling remains non-configurable and separate from provider polling.
- A `ForegroundServiceUnavailable` blocker concept exists in design/code context, but current UI may treat background recording readiness as effectively static until failure paths surface errors.
- Backup/data extraction XML files are template/sample rules unless future documentation explicitly defines backup policy.
- Emulator/mock success must not be documented as proof of real-world GPS/background reliability.
- Names such as `StrideMap`, package id `com.example.stridemap`, GPX creator `StrideMap`, extension namespace `https://stridemap.app/gpx/1`, and folder `Documents/StrideMap/Tracks` describe the current implementation. Treat them as current identifiers, not permanent brand commitments. Any product rename must update user-facing labels and storage/GPX identifiers through an explicit migration/compatibility plan.
- Exact button/helper copy may evolve, but must preserve the specified meaning, accessibility, and recording/storage semantics.

## 15. Maintenance rules for this specification

When updating this file:

1. Inspect current implementation and tests, not only planning notes.
2. Read recent session summaries before raw session logs.
3. Preserve clear distinctions between implemented behavior, intended behavior, deferred work, and pending validation.
4. Prefer product-stable terminology over internal names.
5. Keep storage/permission/security claims exact and Android-version-aware.
6. Update the validation matrix when tests, manual validation, or known gaps change.
7. Do not include personal location data or private route artifacts.
