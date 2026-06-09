# Feature — Initial creation of all defined components

Status: initial v1 implementation completed on 2026-06-08; build/install/launch and Start-capture smoke verified on Pixel 9 API 36 emulator. Professional polish, distance-targeted sampling, Tracks dropdowns/refresh overlay, map point markers, stopped-duration persistence, the first Settings/defaults section, and prominent movement-type coloring were implemented on 2026-06-08. Samsung S23 install-launch visual validation was completed after the polish pass; full real route-point, background, screen-off, battery, and file-visibility validation on Samsung S23 is still pending.

Topic: initial creation of every v1 app component defined in the grill-me session.

Source sessions:

- `agentic/sessions/raw/2026-06-08__stridemap-grilling__capture-reference-track-v1.md`
- `agentic/sessions/summaries/2026-06-08__stridemap-grilling__capture-reference-track-v1.md`
- Prior context: `agentic/sessions/summaries/2026-06-08__stridemap-initial-grilling__gps-tracking-mvp.md`

## Purpose

This feature initializes the full v1 StrideMap application surface agreed in the grill-me session. The goal is not to polish one isolated screen; it is to create the first connected version of all defined components: Capture, Tracks, Map, GPX storage, track lifecycle, permissions/setup gating, and background capture behavior.

This file is the source of truth for v1 behavior. Details intentionally over-specify UI, lifecycle, persistence, permission, and failure behavior so implementation agents do not silently invent product behavior. If behavior is not specified here and materially affects implementation, the agent must ask before choosing.

The product shape is a real GPS reference-track capture app. Its first responsibility is capturing real-world GPS data for a downstream project that needs real traces before higher-level features can be trusted. It records real-world tracks, saves them as user-accessible GPX files, lists local captures, and displays selected/live tracks on an OpenStreetMap map. These captured GPX files become the real-data foundation for later development and evaluation.

This replaces the earlier mock/simulator-first direction as the first product milestone. Synthetic data remains useful for tests, but v1 should prioritize real capture, real files, observable app behavior, and a tight AI-assisted build/run/debug loop.

Initial implementation target:

- Pixel 9 emulator on Android 16 / API 36 for first development validation.
- Samsung Galaxy S23 on Android 16 / API 36 for real-device GPS/background validation.

Recommended development loop:

```text
Gradle build -> install/run on emulator/device -> feed or capture GPS -> observe UI/logs/storage -> fix with AI
```

## Goals

- Create the initial Material 3 Compose app shell with bottom navigation.
- Create the defined v1 screens: Capture, Tracks, Map, plus Settings opened from a gear action.
- Wire the screens together so captured/selected/live tracks flow through the app.
- Capture real GPS tracks on Android.
- Prioritize real-world GPS data capture quality over simulator-first polish or speculative downstream features.
- Save each track as GPX under `Documents/StrideMap/Tracks`.
- Make captured files accessible to the phone user without root.
- Default movement type to `walk`; user may change it before capture.
- Allow an optional user message.
- Continue recording in background/screen sleep through a foreground service.
- Preserve partial tracks if capture is interrupted after at least one point.
- Display local GPX captures in a filterable/sortable Tracks screen.
- Display selected/live tracks on an OpenStreetMap map.
- Add a Settings section for changing safe default values used by future capture/list/map sessions.
- Establish enough structure that later refinement can happen against real captured GPX data.

## Non-goals

- No second-version adaptive tracking algorithm yet.
- No remote sync, API, PostgreSQL, accounts, or cloud work.
- No delete track action in v1.
- No metadata editing after capture in v1.
- No broad advanced-settings surface in v1; Settings is limited to safe defaults and read-only app/storage information unless explicitly expanded.
- No social tracking, fleet tracking, or live safety sharing.
- No claim that emulator/mock GPS proves real production GPS reliability.
- No synthetic simulator as the primary product surface for v1.
- No broad storage permissions for normal capture/export behavior. Exception: user explicitly approved All files access recovery on 2026-06-09 after Samsung's SAF picker refused the existing `Documents/StrideMap/Tracks` folder.
- No silent degradation to approximate, foreground-only, app-private-only, or no-notification recording.

## Locked decisions

| Area | Decision |
| --- | --- |
| UI toolkit | Material 3 Compose |
| Navigation | Bottom navigation using a Material 3 `Scaffold` + `NavigationBar` |
| Tabs | Capture / Tracks / Map; Settings opens from a top-corner gear action, not bottom navigation |
| Initial tab | Capture |
| Map | OpenStreetMap via osmdroid |
| File format | GPX |
| File location | `Documents/StrideMap/Tracks` |
| Movement types | `walk`, `run`, `bike`, `car`, `train` |
| Movement type default | `walk`; user may change before recording; Settings can change the future default |
| Capture interval | Distance-targeted persisted-point sampling: first valid point, then at least 1 second and at least 10 meters from the last persisted point |
| GPS priority | High accuracy |
| Persistence | Persist accepted points as capture progresses; GPX must stay recoverable/parseable after persisted points |
| Background | Continue via foreground service notification |
| Live track limit | One `live` track max |
| Track states | `live`, `stopped`, `interrupted` |
| Restart behavior | Do not auto-start or auto-resume after app/process death |
| Stale live behavior | Mark stale GPX `live` track `interrupted`; do not confuse Activity recreation with dead capture |
| Empty capture | Discard if zero GPS points |
| Poor GPS accuracy | Warn above 25m; still save point with accuracy metadata |
| Time format | GPX timestamps and metadata timestamps are UTC; UI may render local time |
| Filename timestamp | UTC sortable timestamp including seconds |
| Settings style | Android Settings-inspired: grouped rounded sections, one row per entry, leading icon, title, summary/current value, subpages with top app bar and back arrow |
| Settings persistence | Android `SharedPreferences`; no new dependency |
| Movement-type color | Use consistent movement colors across Capture, Tracks, Map, and movement rows while preserving labels/icons so color is never the only cue |

## Implementation blockers before coding

The following choices must be resolved before implementation starts. Agents must not choose defaults silently.

| Area | Blocker decision needed |
| --- | --- |
| Location provider | Use Google Play Services `FusedLocationProviderClient`, Android `LocationManager`, or an app-owned abstraction over both? |
| Storage API | Use MediaStore, SAF, app-private + export, or a hybrid for `Documents/StrideMap/Tracks` on Android 16/API 36? |
| GPX schema | Exact StrideMap extension namespace and required extension fields. |
| GPX write strategy | Exact crash-tolerant write/finalization strategy that keeps files parseable or repairable after each persisted point. |
| Foreground service restart policy | Exact Android service restart mode compatible with “no auto-resume after process death.” |
| Stale live detection | Exact way to distinguish stale live GPX from a currently active foreground service. |
| osmdroid network/cache | Whether tile downloads/network use are acceptable by default and how cache/lifecycle/attribution are configured. |
| Dependencies | Any dependency additions, including osmdroid, Google Play Services Location, GPX parser/writer libraries, or permission helpers, require explicit approval before implementation. |

### Approved implementation decisions

Approved by user on 2026-06-08 before implementation start.

| Area | Approved decision |
| --- | --- |
| Location provider | Use an app-owned `LocationProvider` abstraction. V1 production implementation uses Google Play Services `FusedLocationProviderClient`. No `LocationManager` fallback in v1. |
| Storage API | Use MediaStore public Documents via `MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)`. App creates/writes GPX rows under relative path `Documents/StrideMap/Tracks/`; directories are implicit. Normal capture/export does not depend on broad storage permissions. User explicitly approved `MANAGE_EXTERNAL_STORAGE` on 2026-06-09 as a Settings-only recovery path so the app can directly scan `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` after app data clear/reinstall when SAF cannot grant the folder on Samsung. |
| GPX schema | Use GPX 1.1 with StrideMap extension namespace `https://stridemap.app/gpx/1` and app schema version `1`. Store required metadata under GPX metadata/extensions and point metadata under track-point extensions. Track-point elevation uses standard GPX 1.1 `<ele>` only when Android honestly provides altitude or parsed GPX already contains a valid `<ele>`. |
| GPX write strategy | Use app-private recovery/session journal as source of truth plus full valid GPX snapshot writes to the MediaStore-visible `.gpx` target after each accepted point where possible. Writes use `IS_PENDING=1` while replacing content, read-back validation, then `IS_PENDING=0` after success. Startup reconciles journal/canonical state and marks stale live captures `interrupted` when appropriate. |
| Foreground service restart policy | Use `START_NOT_STICKY`. Do not add a boot receiver. Do not use pending-intent location updates. Do not auto-resume or silently continue capture after app/process death. |
| Stale live detection | Use same-process foreground service/session ownership plus persisted active-session metadata. Activity recreation must check active service/controller ownership and must not mark a live capture interrupted. Startup with no active owner marks live tracks with points `interrupted` and discards zero-point stale captures. |
| osmdroid network/cache | Online OpenStreetMap tiles are acceptable by default for v1. Configure stable user agent, visible attribution, lifecycle forwarding, bounded app cache, and graceful offline/error behavior. No bulk/offline predownload in v1. |
| Dependencies | Approved dependency additions: Kotlin Android/Compose/Material 3 setup, Google Play Services Location, osmdroid, and AndroidX DocumentFile from earlier SAF work. Do not add new dependencies for the MediaStore storage switch. Avoid adding a GPX parser/writer dependency for v1; implement GPX writer/parser with platform APIs. |

Approved implementation simplifications:

- No optional live badge in bottom navigation.
- Initial build used an explicit checklist; the later professional polish pass may collapse it to a compact ready state when all requirements pass while preserving blocker details when Start is disabled.
- Foreground-service notification only needs to return to the app.
- No advanced OEM/Samsung battery tooling beyond warnings/documentation.
- Map UX should be minimal but compliant with selected/live route display, attribution, and follow behavior.

## Implementation record — 2026-06-08 initial v1 build

This section records what was actually built during the initial implementation pass. It is intentionally factual: it includes implementation deviations, fixes made after emulator testing, and validation that has not happened yet.

### Current implementation summary

- The repository now contains a working Android app module with a launchable `com.example.stridemap.MainActivity`.
- The app uses Material 3 Compose with bottom navigation tabs: Capture, Tracks, and Map.
- The app uses an app-owned repository/state layer for setup readiness, live track state, selected track state, scanned entries, and transient messages.
- The app writes public GPX files through MediaStore under `Documents/StrideMap/Tracks/`. A Settings recovery action can open Android's All files access page; once granted, StrideMap read-only scans `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` to rediscover existing GPX files after app data clear/reinstall.
- The app keeps an app-private active-session GPX journal for recovery and stale-live handling.
- The app has a foreground location service using Google Play Services Fused Location Provider through an app-owned location abstraction.
- The app uses osmdroid for the Map tab and renders selected/live routes.
- Movement type has a consistent visual color identity across Capture, Tracks, Map, and movement-related rows.
- The app has JVM unit coverage for domain, GPX, lifecycle, ordering, storage constants, recovery planner, default app state, and GPX security/empty-live cases.

### Build and dependency setup implemented

- Added Android/Kotlin/Compose build setup in the app module using AGP 9.2 built-in Kotlin support.
- Kept namespace/application id as `com.example.stridemap`.
- Added/used dependencies for Compose Material 3, Google Play Services Location, osmdroid, and AndroidX DocumentFile from the earlier SAF implementation path.
- `androidx.core-ktx` was adjusted from `1.19.0` to `1.17.0` because `1.19.0` requires compile SDK 37 while this project targets API 36.
- The current MediaStore implementation no longer needs DocumentFile, but the unused dependency still remains and should be cleaned up in a separate dependency-maintenance task if desired.

### Manifest and platform behavior implemented

- Manifest declares location/background/foreground-service/notification permissions needed by the v1 capture flow.
- Manifest declares `ACCESS_COARSE_LOCATION` so approximate-only permission can be detected and blocked distinctly.
- Manifest declares `INTERNET` for online OSM/osmdroid tiles.
- Manifest declares `CaptureForegroundService` with location foreground-service type.
- Manifest declares `MANAGE_EXTERNAL_STORAGE` only for the explicitly approved, Settings-driven recovery path. It does not declare legacy `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_*` permissions, and normal capture/export still writes through MediaStore.
- The foreground service returns `START_NOT_STICKY` and does not register boot receivers or pending-intent location updates.

### Capture tab implemented

- Capture is the initial screen.
- Message input is implemented and disabled while recording.
- Movement type defaults to `Walk`; user can choose Walk/Run/Bike/Car/Train before recording.
- Implementation uses selectable Material chips for movement type, not the originally suggested exposed dropdown.
- Setup checklist is explicit and visible, with ready/action copy for:
  - Movement type.
  - Precise location.
  - Background location.
  - Notifications.
  - Location services.
  - App directories.
  - Public GPX folder.
  - Foreground service.
- The setup checklist is a pre-start readiness aid only; it is hidden while a capture is running and does not show a “finish the current capture first” checklist item.
- Start is gated by repository readiness and remains blocked when precise/background/notification/location services/storage/live-state conditions fail.
- Approximate-only location is detected and explained separately from missing precise permission.
- Recording UI includes waiting-for-first-point state, live stats, current filename/status, and low-accuracy warning behavior.
- Stop flow includes confirmation and empty-capture discard behavior through the repository/service lifecycle.

### Location and foreground-service capture implemented

- `LocationProvider` abstraction exists for testability.
- Production provider uses `FusedLocationProviderClient` with high-accuracy location requests.
- Saved-point cadence logic now uses distance-targeted persisted sampling: first valid point, then at least 1 second and at least 10 meters.
- Point validation rejects invalid latitude/longitude, invalid accuracy, decreasing timestamps, and exact duplicate timestamp/coordinate points.
- Poor-accuracy points above the warning threshold are accepted and carry metadata.
- Service startup/failure paths catch foreground-service and location-request failures and surface user-visible errors where practical.
- Normal stop is only considered normal after repository stop/finalize succeeds.

### GPX format and persistence implemented

- GPX writer/parser is implemented in app code rather than through an external GPX dependency.
- GPX is version 1.1 with StrideMap namespace `https://stridemap.app/gpx/1` and app schema version `1`.
- Metadata extensions include movement type, track state, message, distance meters, duration seconds, and schema version.
- Point data includes standard GPX 1.1 `<ele>` when Android honestly provides altitude or a parsed GPX already contained valid elevation; elevation is omitted when unavailable. Point extensions include accuracy and Android-provided speed when available.
- Empty live GPX metadata files are valid so a capture can start before the first GPS fix.
- Parser rejects DOCTYPE input before XML parsing and tolerates XML parser security features that may not be supported on Android.
- Public GPX storage uses MediaStore `Files` on `VOLUME_EXTERNAL_PRIMARY` with relative path `Documents/StrideMap/Tracks/` and MIME type `application/gpx+xml`.
- MediaStore writes use `IS_PENDING=1`, truncate/write, read-back validation, then `IS_PENDING=0` on success.
- The app-private active-session journal stores the latest valid GPX snapshot and remains the recovery source of truth.
- Startup/storage recovery can preserve stopped journal state, mark stale live-with-points interrupted, and discard stale zero-point live captures.

### Tracks tab implemented

- Tracks scans local GPX entries from the MediaStore target.
- Tracks supports movement filter chips and date/distance sorting with direction controls.
- Live track appears immediately through a display projection rather than waiting for a file rescan.
- Live entries are kept visually distinct and should sort ahead of stopped/interrupted tracks when applicable.
- Malformed GPX files are shown as safe error rows instead of being silently ignored.
- Malformed row details avoid exposing private paths or route contents.
- Refresh during recording uses defensive list/display logic to avoid duplicate live rows.

### Map tab implemented

- Map uses osmdroid with online OSM tiles.
- osmdroid configuration includes a stable user agent, app cache path, lifecycle forwarding, and attribution overlay.
- Map renders selected or live tracks with route line and markers.
- Live zero-point state shows a waiting state instead of a false `No track selected` state.
- One-point and identical/degenerate tracks are handled without trying to zoom to invalid bounds.
- Viewport updates are posted after layout checks, non-animated, and de-duplicated to avoid freezes/restarts seen during emulator testing.
- Follow behavior exists for live tracks with a follow action.

### Bugs found and fixed during emulator/manual testing

- Live capture did not appear in the track library because `startCapture()` only set `liveTrack` and the list rendered `entries`; fixed with `AppState.displayEntries` and the library using that projection.
- Map could freeze/restart due to unsafe osmdroid `zoomToBoundingBox` calls during Compose `AndroidView.update`; fixed with posted, guarded, non-animated viewport updates and one/degenerate-point handling.
- Direct visible GPX rewrites could leave malformed GPX after interruption; fixed by journal-first recovery and validated public snapshots.
- SAF folder-picker/storage UX did not match the desired “directories created automatically” behavior; storage was switched to MediaStore public Documents.
- Start failed with snackbar `Could not start capture: Generated GPX did not validate`; empty-live GPX unit coverage was added and parser hardening was applied for Android XML feature compatibility. The first attempted fix still failed on-device because additional `DocumentBuilderFactory` hardening setters could throw on Android before parsing; those setters are now guarded as best-effort.

### Validation performed

- `./gradlew :app:testDebugUnitTest` passed after implementation and after subsequent fixes.
- `./gradlew :app:assembleDebug --no-daemon` passed after implementation and after subsequent fixes.
- Pixel 9 AVD exists and was started/rebooted multiple times.
- App installed successfully on Pixel 9 API 36 emulator using `./gradlew :app:installDebug --no-daemon`.
- App launched successfully with `adb shell am start -W -n com.example.stridemap/.MainActivity`.
- `dumpsys activity activities` showed `MainActivity` as top resumed/current focus after launch.
- No broad storage permission was found after the MediaStore switch.
- Start-capture smoke was verified on the Pixel 9 emulator by scrolling to the visible `Start capture` button, tapping it through adb input, and checking UIAutomator output for `Capture started` followed by `Stop capture`.
- Foreground recording notification presence was checked with `adb shell cmd notification list`; a StrideMap notification row was present after Start.

### Implementation record — 2026-06-08 professional polish pass

This section records what was actually implemented in the first visual/product polish pass after the initial functional v1 build. It is the conflict winner for the app-shell naming and visual decisions where it differs from earlier `List`/default-theme wording.

#### Scope and constraints actually followed

- The pass stayed limited to app shell/UI/resource polish.
- No tracking, storage, permission, foreground-service, GPX, repository, or route-lifecycle behavior was intentionally changed.
- No new dependencies were added.
- The implementation used local/inline vector drawing for tab icons and Android vector drawables for launcher artwork.
- The repository was entirely untracked during this work, so normal `git diff` output did not show the patch; validation relied on file inspection plus Gradle/ADB checks.

#### Files changed in the polish pass

- `app/src/main/java/com/example/stridemap/MainActivity.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`

#### Brand/theme implementation details

- Added fixed Compose Material 3 color schemes instead of relying on default Material colors.
- Dark theme is the lead identity:
  - background `#101412`
  - surface `#151A17`
  - surface variant `#222B25`
  - primary muted green `#8FB996`
  - primary container `#254632`
  - on-primary `#102116`
  - on-surface `#E6ECE6`
  - on-surface-variant `#C2CBC1`
- Light theme remains supported with warm/off-white surfaces:
  - background `#F7F8F2`
  - surface `#FFFCF7`
  - primary green `#426B4A`
  - primary container `#C4D8C1`
  - on-surface `#181D1A`
  - on-surface-variant `#424A42`
- XML resource colors were renamed away from template purple/teal to StrideMap palette names: `stridemap_charcoal`, `stridemap_charcoal_raised`, `stridemap_green`, `stridemap_green_dark`, `stridemap_mist`, and `stridemap_cream`.
- Day/night XML themes now use StrideMap charcoal/green colors for primary/secondary/status/navigation behavior instead of Android template purple/teal.

#### App shell and navigation polish implemented

- User-facing top-level tabs are now exactly `Capture`, `Tracks`, and `Map`.
- Internal enum name `AppTab.List` remains as an implementation detail, but its label is now `Tracks`.
- Bottom navigation no longer uses text glyphs (`●`, `≡`, `⌖`).
- Added inline `ImageVector` icons:
  - Capture: circular target/record-location style mark.
  - Tracks: layered route/list-line mark.
  - Map: folded-map outline mark.
- Navigation item icons use `Icon(tab.icon, contentDescription = null)` because labels are always visible and provide the accessible name.

#### Capture screen polish implemented

- Capture helper copy changed from developer/file wording (`Record a real GPX reference track into Documents/StrideMap/Tracks.`) to private-first product copy: `Start a private route record for a walk, ride, run, or trip.`
- Message field was reframed as a lighter personal note:
  - label: `Note`
  - placeholder: `Evening walk`
  - supporting text: `Optional private label`
  - still max 2 lines and disabled while recording.
- Capture layout is now more Start-first:
  - title/helper
  - note field
  - movement type selector
  - primary Start/Stop button
  - live stats when recording
  - setup checklist after the primary action area.
- Primary button copy shortened from `Start capture` / `Stop capture` to `Start` / `Stop`.
- Setup checklist is de-emphasized when ready:
  - if all readiness checks pass, it shows compact `Ready to capture` plus `Precise location, background access, and saving are set.`
  - if Start is blocked, it still expands to explicit blocker rows and actions.
- Checklist wording was productized:
  - `App directories` became `Private app storage`.
  - `Public GPX folder` became `Track export folder`.
  - background service row became `Background recording`.
  - precise-location copy says `clean route capture` instead of GPX/debug language.

#### Tracks/List polish implemented

- User-facing navigation and screen identity now use `Tracks` instead of `List`.
- The existing list/library behavior remains intact: movement filtering, date/distance sort, live/malformed rows, refresh action, selected track routing, and safe malformed-file dialogs.
- Tracks filtering/sorting now uses dropdown controls for Movement, Sort, and adaptive Order labels instead of chip rows.
- The old top-row text `Refresh` button was replaced with a bottom-left floating action button overlay using a refresh icon and accessible label `Refresh tracks`.

#### Map polish implemented

- Route polyline color no longer uses hard-coded blue.
- Map route line now uses the active Material theme primary color by passing `MaterialTheme.colorScheme.primary.toArgb()` into map rendering.
- Existing osmdroid behavior, route markers, live-follow action, selected/live display, empty states, and info card behavior were left otherwise unchanged.

#### Launcher/splash mark implemented

- Replaced Android template launcher foreground art with a custom vector topography/path mark.
- Launcher foreground uses three curved route/topography strokes plus two point marks:
  - primary muted green stroke `#8FB996`
  - light green/cream stroke `#D5E8D5`
  - darker green stroke `#426B4A`
  - point fills `#FFFCF7`
- Launcher background was changed to StrideMap charcoal/green branding instead of Android template artwork.
- No separate custom onboarding or splash screen copy was added; the adaptive launcher icon currently supplies the Android starting-window brand mark.

#### Polish validation performed

- `./gradlew :app:assembleDebug --no-daemon` passed after the polish implementation.
- `./gradlew :app:testDebugUnitTest --no-daemon` passed after the polish implementation.
- Connected devices were checked with ADB; Samsung S23 was present as `RFCW20LALNM`, model `SM-S911B`.
- Samsung S23 platform was checked with ADB:
  - Android release `16`
  - SDK/API `36`
- Debug build was installed with `./gradlew :app:installDebug --no-daemon -Pandroid.injected.device.serial=RFCW20LALNM`; Gradle reported installation on the S23 and also on the already-running Pixel 9 emulator.
- App was launched on the Samsung S23 with `adb -s RFCW20LALNM shell am start -W -n com.example.stridemap/.MainActivity`.
- S23 launch result:
  - `Status: ok`
  - `LaunchState: COLD` on first launch
  - `Activity: com.example.stridemap/.MainActivity`
  - `TotalTime: 831`
- Visual validation was performed on the Samsung S23 through ADB screenshots for the polished UI state:
  - Capture screen with dark charcoal/green theme, note field, movement selector, Start-first action, and compact ready/start flow.
  - Tracks screen with `Tracks` title/tab, filters/sorting, live row, and new bottom-nav icons.
  - Map screen with themed route line and bottom navigation icons.
- App process stayed running after S23 launch/visual checks; no launch crash was observed.

#### Polish validation still pending / not proven

- No full real GPS route validation was completed on the Samsung S23 during the polish pass.
- No background/screen-off capture validation was completed on the Samsung S23 during the polish pass.
- No Samsung/OEM battery behavior validation was completed.
- No public GPX file visibility check on the Samsung S23 was completed.
- No light-theme visual pass was completed on the Samsung S23 during this validation.
- No accessibility audit was completed; checklist status icons still use simple text glyphs (`✓` / `!`) inside rows.
- The Tracks refresh action has since been moved to a bottom-left icon floating action button, but that later overlay change has not yet had a Samsung S23 visual pass.

### Implementation record — 2026-06-08 movement-type coloring pass

- Added a consistent movement-type color identity in the Compose UI using the approved palette:
  - Walk: light `#2E7D4F`, dark `#8FB996`.
  - Run: light `#C2412D`, dark `#FF8A65`.
  - Bike: light `#007C89`, dark `#4DD0E1`.
  - Car: light `#9A6500`, dark `#FFC857`.
  - Train: light `#6750C8`, dark `#B7A7FF`.
- Capture movement chips now use the selected movement color as the selected chip container, with unselected chips retaining colored movement icons.
- Tracks movement filter rows and GPS polling Settings rows tint movement icons with the matching movement color.
- Track rows and the Map info card show solid movement-colored badges that still include the movement icon and label.
- Map route rendering uses the selected/live track movement color for the route line and matching marker accents, with a dark casing line behind multi-point routes for readability over OSM tiles.
- This pass intentionally changed visual scanability only: labels/icons remain present, and no tracking, storage, GPX, permission, dependency, foreground-service, navigation, or sampling behavior was intentionally changed.

### Implementation record — 2026-06-08 distance sampling and UI controls pass

- Replaced fixed 7-second persisted-point cadence with distance-targeted persisted sampling.
- First valid point is accepted; later points require both at least 1 second elapsed and at least 10 meters from the last accepted persisted point.
- Point validation behavior remains separate: invalid coordinates, invalid accuracy/speed, backwards timestamps, duplicate timestamp/coordinate rejection, and poor-accuracy warning acceptance are preserved.
- Movement type remains associated with a separate provider-request setting, but the default provider polling interval is now 1000ms for every movement type.
- The foreground capture service requests provider intervals from the active track movement type instead of hard-coding 7000ms.
- Capture UI now shows local inline vector Start/Stop icons and movement icons on movement choices.
- Tracks UI now uses dropdown controls for Movement, Sort, and adaptive Order labels instead of filter-chip rows.
- Track rows were lightly restyled with movement icons, point count, clearer distance, and visible live/interrupted chips.
- Map rendering now draws the route polyline, small intermediate saved-point markers, and distinct programmatic Start, End, and latest-live markers, while avoiding overlapping start/end markers for one-point tracks.
- No new dependencies, permissions, storage behavior, GPX semantics, foreground-service lifecycle behavior, repository semantics, or navigation behavior were intentionally changed.

### Implementation record — 2026-06-08 GPS polling Settings pass

- Added a top-corner gear action to the app shell; Settings remains outside bottom navigation, which is still Capture / Tracks / Map only.
- Added an Android Settings-style Settings surface with grouped rounded rows, leading icons, current-value summaries, and a GPS polling interval subpage with a back arrow.
- Implemented per-movement GPS polling/update interval settings for Walk, Run, Bike, Car, and Train.
- Settings are persisted with Android `SharedPreferences` and exposed through `AppState.settings` as `UserSettings`.
- Default polling interval is 1000ms for every movement type while preserving separate per-movement Settings values for Walk, Run, Bike, Car, and Train.
- User-selected values are clamped to safe bounds: minimum 1000ms, maximum 60000ms.
- The foreground capture service uses the configured interval for the active track movement type when a future capture starts.
- Changing Settings during an active capture does not mutate the already-started provider request; it applies to the next capture/request.
- This setting is explicitly separate from persisted saved-point sampling. Saved points still use: first valid point, then at least 1 second and at least 10 meters from the last persisted point.

### Implementation record — 2026-06-08 Settings/defaults section

This section records the broader Settings addition implemented after the initial polish/sampling work. The existing top-right gear behavior was preserved; Settings was not added as a bottom tab.

#### Product intent

- Add a top-level Settings section where the user can change default values instead of hard-coded app defaults.
- The visual model should follow global Android Settings from the provided screenshots:
  - vertical list layout;
  - one setting/category per row;
  - leading icon, primary title, and secondary summary/current value;
  - rounded grouped sections on the root page;
  - subpages with a back arrow, title, and the same single-row setting style.
- Keep StrideMap's quiet-premium charcoal/muted-green identity rather than copying Android's exact colors.

#### Navigation decision

- Current implementation direction: do **not** add Settings as a bottom-navigation tab.
- Recommended implementation: add a gear icon action in a top corner, probably top-right, to open Settings.
- Rationale: Settings are important but not a daily primary destination like Capture, Tracks, or Map. A top-corner gear matches user expectation for app defaults without crowding bottom navigation.
- Implementation shape can be either:
  - a top app bar gear action visible from the main app shell; or
  - a compact top-right gear in the Capture screen/app header if a global top app bar is not introduced yet.
- Prefer the global shell gear if it can be added cleanly without making each screen visually heavy.
- Settings should not interrupt an active capture. Changing defaults affects future captures/screen sessions unless a setting is explicitly safe to apply immediately.

#### Implemented top-level Settings rows

Use grouped rounded sections and tappable rows:

1. `Capture defaults`
   - Shows default movement type and after-start behavior.
   - Opens a Capture defaults subpage.
2. `Tracks defaults`
   - Shows default filter, sort, and order.
   - Opens a Tracks defaults subpage.
3. `Map defaults`
   - Shows follow-live and saved-point-dot defaults.
   - Opens a Map defaults subpage.
4. `Storage / App info`
   - Shows the GPX folder path.
   - Opens a storage/app info subpage with refresh/rescan and simple debug info.

#### Capture defaults subpage

- `Default movement type`
  - Options: `Walk`, `Run`, `Bike`, `Car`, `Train`.
  - Default: `Walk`.
  - Applies to new captures only.
- `GPS polling interval`
  - Existing per-movement provider-update hint settings remain linked from Capture defaults.
  - Still does not expose or change saved-point sampling.
- `After starting recording`
  - Options: `Open Map`, `Stay on Capture`.
  - Default: `Open Map` to preserve current behavior.
- `Default note / template`
  - Simple text field.
  - Prefills idle future capture setup and is trimmed/capped defensively.

#### Tracks defaults subpage

- `Default movement filter`
  - Options: `All`, `Walk`, `Run`, `Bike`, `Car`, `Train`.
  - Default: `All`.
- `Default sort`
  - Options: `Date`, `Distance`.
  - Default: `Date`.
- `Default order`
  - Options adapt to selected default sort:
    - Date: `Newest first`, `Oldest first`.
    - Distance: `Longest first`, `Shortest first`.
  - Default: `Newest first`.

#### Map defaults subpage

- `Follow live track`
  - Boolean switch.
  - Default: `On`.
  - Affects initial live-map behavior only; manual panning can still pause follow for the current map session.
- `Show saved point dots`
  - Boolean setting.
  - Default: `On`.
  - Controls intermediate saved-point markers only; start/end/latest markers remain.
- `Route line thickness`
  - Preset values from 4px to 16px.
  - Default: `8px`.

#### Storage / App info subpage

- Shows track folder path from the current storage target, falling back to `Documents/StrideMap/Tracks`.
- Provides a `Refresh / rescan tracks` action that calls the existing track scan path, including the MediaStore folder and, when All files access is granted, the direct recovery folder scan.
- Provides a `Recover existing tracks` action that opens Android's All files access settings for StrideMap. Once the user grants access, rescans include read-only `.gpx` files from `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` as recovered tracks.
- Shows simple package/debug info using existing app state; no package-manager/version expansion was added in this pass.

#### Persistence and model implemented

- Use Android `SharedPreferences`; do not add a datastore/settings dependency for this small first pass.
- `UserSettings` now stores:

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

- Repository/state loads settings during initialization and exposes them through `AppState`.
- Repository owns setters for each setting and persists after every user change via `SharedPreferences`.
- Invalid or missing stored values fall back to safe defaults rather than blocking app launch.
- Capture defaults are applied to idle capture setup and after stop/discard/interruption; active capture movement/provider interval is not mutated mid-run.
- Tracks defaults seed local Tracks screen filter/sort/order state on fresh composition.
- Map defaults seed live-follow state and configure saved-point-dot visibility and route line width during rendering.
- Saved-point sampling remains non-configurable: first valid point, then at least 1 second and 10 meters from the last persisted point.

#### Non-goals for the first Settings pass

- Do not add advanced GPS/sampling tuning controls.
- Do not let Settings alter an already-running capture.
- Do not add delete-track, delete-all, import/export management, sync/account, or theme-selection behavior.
- Do not change the 10m/1s persisted sampling rule from Settings.

#### Verification for Settings implementation

- Unit test settings defaults, serialization/parsing, and invalid-value fallback if helper code is added.
- Manual/build verification:
  - gear icon appears in a top corner and opens Settings;
  - Settings root matches Android Settings-style row pattern;
  - subpages have back arrow and single-row setting entries;
  - changing default movement type affects the next new capture;
  - changing start behavior controls whether Start navigates to Map or stays on Capture;
  - changing Tracks defaults affects the next fresh Tracks screen/app launch;
  - changing map follow default affects the initial live-map follow state;
  - active capture persistence, GPX writing, foreground service behavior, and point cadence remain unchanged.

### Validation still pending / not proven

- User confirmation on their side that Start now succeeds is still pending, but the Start flow was verified directly on the emulator after the guarded XML parser fix.
- No meaningful instrumented UI test suite exists yet; the generated instrumentation test is not a v1 behavior test.
- Emulator validation has proven build/install/launch, visible Start success, Stop-state transition, and foreground notification creation, but not full route-point capture correctness end-to-end.
- Samsung Galaxy S23 install/launch/visual validation is complete, but real GPS, background/screen-off behavior, OEM battery behavior, notification behavior during a real capture, and public file visibility are still pending.
- Long-running capture, process kill during capture, and startup interruption recovery still need scripted/manual validation on emulator and real device.
- MediaStore public files survive uninstall, but a reinstalled app may not own older MediaStore rows; that behavior is documented as a future pitfall, not solved in v1.
- No delete/edit/settings/remote sync behavior was added.

## Impacted artifact map

Implementation affects these artifact areas:

- Android app shell/navigation: Compose `Scaffold`, bottom navigation, top-level tab state, fixed StrideMap theme palette, and top-level Capture/Tracks/Map labels/icons.
- Settings navigation/UI: top-corner gear entry, Android Settings-style root list, subpages, default-setting rows, persistent default values, and storage/app info.
- Capture UI: message input, movement selector with movement-colored chips/icons, setup checklist, Start/Stop controls, live stats, stop dialog.
- Location capture: app-owned location abstraction, production provider, high-accuracy request cadence, point validation.
- Foreground service: manifest declarations, location foreground-service type, notification channel/content, start/stop lifecycle.
- Persistence: GPX writer/parser, storage API integration, `Documents/StrideMap/Tracks` setup, atomic/crash recovery behavior.
- Track model/state: `Track`, `TrackState`, `MovementType`, `LocationPoint`, selected/live track state.
- Tracks/List UI: file scan, parser errors, filter/sort controls, movement-colored row badges, track rows, empty/loading states.
- Map UI: osmdroid integration, movement-colored route line with casing, movement-colored marker accents, bottom info sheet, live follow/pause/resume.
- Tests/validation: unit tests for domain/persistence/state, instrumentation/manual scripts for permissions/service/location, emulator/device validation notes.

## App shell

### Visual appearance

- Use a Material 3 `Scaffold` with a bottom `NavigationBar` pinned to the bottom.
- Bottom nav items must always show icon + label:
  - `Capture` with a recording/location-style icon.
  - `Tracks` with a list/library icon.
  - `Map` with a map icon.
- Do not add `Settings` as a bottom-nav item; Settings should open from a top-corner gear action.
- Selected item uses Material 3 selected state color/indicator.
- Unselected items use standard `onSurfaceVariant` styling.
- Content must use the `Scaffold` padding so screen content is never hidden behind the nav bar or system insets.
- During active recording, bottom nav remains usable. A subtle live indicator/badge on Capture or Map is allowed, but v1 must not add a fourth navigation state.

### Routing

- Top-level destinations are exactly `Capture`, `Tracks`, and `Map`.
- App starts on `Capture` by default.
- Per-tab state should be preserved where practical:
  - Capture form values remain when switching tabs.
  - Tracks filter/sort state and scroll position remain.
  - Map selected track and follow state remain.
- Back behavior must be documented during implementation. Back must never stop an active recording.

### Details

- Shared app state must track selected track, active live track, scanned track list, setup readiness, and transient UI messages.
- Use an app-level `SnackbarHost` for transient success/failure messages such as `Track saved`, `Tracks refreshed`, and `Could not refresh tracks`.
- Snackbars must not be the only place critical permission/storage errors are explained.

## Landing page

The Landing page is the **Capture** tab.

### Visual appearance

Use a clean Material 3 Compose form layout. The page should feel like a simple recorder dashboard: configure the capture, verify readiness, then start/stop.

Layout requirements:

- Use vertical scrolling.
- Use 16dp horizontal padding.
- Use 16–24dp vertical spacing between major sections.
- Use Material 3 typography roles.
- Prefer cards/sections for form, checklist, and recording stats.

Recommended structure from top to bottom:

1. **Page title / section header**
   - Text: `Capture` or `New capture`.
   - Optional helper text using `bodyMedium` / `onSurfaceVariant` explaining that this records a GPX track.

2. **Message input**
   - Use `OutlinedTextField`.
   - Label: `Message`.
   - Placeholder: `Walk to park`.
   - Supporting text: `Optional`.
   - Single-line or max 2–3 lines; implementation must document the choice.
   - Disabled while recording unless message changes during recording are explicitly supported later.

3. **Movement type input**
   - Use Material 3 exposed dropdown menu.
   - Label: `Movement type`.
   - Placeholder/helper should explain `Walk` is the default.
   - Defaults to `Walk` before Start.
   - Human labels: `Walk`, `Run`, `Bike`, `Car`, `Train`.
   - Stored values: `walk`, `run`, `bike`, `car`, `train`.
   - Default value is `Walk`; user may explicitly choose another type before recording.
   - Movement choices must include label + icon and use the movement color prominently when selected; unselected choices should still show movement identity with colored icon/dot/tint where practical.
   - Disabled while recording.

4. **Setup checklist**
   - Render as a Material 3 `Card` titled `Setup checklist`.
   - Each row includes status icon, requirement title, one-line status/helper text, and optional trailing action button.
   - Use explicit `Ready` / `Needs action` wording; never rely on icons/color alone.
   - Tapping a checklist row should perform the action when possible or explain the requirement.
   - Keep visible whenever Start is disabled.
   - If all requirements pass, visual noise may collapse to a compact `Ready to capture` row, but details should remain accessible.

5. **Primary action area**
   - Idle state: full-width `Button` labeled `Start capture`.
   - Recording state: full-width emphasized button labeled `Stop capture`.
   - Disabled Start remains visible, not hidden.
   - Checklist explains exactly why Start is disabled.

6. **Live stats panel**
   - Visible while recording.
   - Shows `Recording` status plus elapsed duration.
   - Include a live indicator using text/icon/color, not color alone.
   - Shows distance, duration, current GPS accuracy, and points saved.
   - Example values: `1.24 km`, `00:18:42`, `±12 m`, `14 points`.
   - Prefer tabular figures for timer/stats to avoid layout jitter.
   - Before first point, show `Waiting for first GPS point…` and points saved as zero.

7. **GPS accuracy warning**
   - If accuracy is worse than 25m, show an inline warning chip/row.
   - Example: `Low GPS accuracy: ±38 m`.
   - Helper: `Point will still be saved with accuracy metadata.`
   - Include text/icon; do not communicate by yellow/red color alone.

8. **Current file/status text**
   - While recording, show filename/status in low-emphasis text.
   - Example: `Saving to: 2026-06-08_15-30-04Z_walk_walk-to-park.gpx`.
   - Allow wrapping or middle ellipsis; avoid horizontal overflow.

The Capture tab does not embed the map. The map has its own tab.

### Routing

- Reached by bottom navigation item `Capture`.
- App starts here by default unless later changed.
- Starting capture shows a confirmation snackbar and immediately routes to Map, so Capture does not add a live-entry card below the Start button.
- Stopping capture keeps the resulting track selected for Map.
- User may switch to Tracks or Map during recording; recording continues.
- Stop dialog cancel/back/outside dismissal behaves like `Keep recording`.

### Details

Start is blocked unless all of these are true:

- Movement type is selected; fresh state defaults to `walk`.
- Fine/precise location is granted.
- Background location is granted.
- Device location services are enabled.
- Notification permission is granted when required by platform.
- App-owned internal recovery journal directory exists, and MediaStore public Documents target is available.
- MediaStore public Documents target for `Documents/StrideMap/Tracks/` is available and writable.
- Foreground service can legally start.
- No existing track is in `live` state.

The setup checklist must distinguish these blocker states:

- Location permission missing.
- Approximate-only location granted.
- Foreground-only location granted but background missing.
- Device location services disabled.
- Notification permission missing.
- Foreground service unavailable or fails to start.
- App directory scaffold creation failed.
- Public MediaStore GPX target unavailable or not writable.
- Existing live track blocks Start.

Checklist row copy should follow these meanings:

- `Precise location`: `Precise location granted.` or `Allow precise location to record an accurate GPX track.` Action: `Grant`.
- `Background location`: `Allow all-the-time location so recording continues with screen off.` Action: `Open settings` if Android requires Settings flow.
- `Notifications`: `Allow notifications for the foreground recording service.` Action: `Grant`.
- `App directories`: app-private recovery and app-specific `Documents/StrideMap/Tracks` scaffold exists, or Android could not create it.
- `Public GPX folder`: `Android will create Documents/StrideMap/Tracks via MediaStore.` If unavailable, explain that public Documents MediaStore access is unavailable. No setup action or folder picker is shown.
- `Movement type`: `Walk selected by default; change before recording if needed.` No separate action required if selector is visible above.
- `No active capture`: `Another live track exists; finish or recovery must resolve it first.`

Permission UX rules:

- Background location may require a separate system/settings flow. Do not imply the app can always request foreground and background location in one dialog.
- If Android requires Settings navigation, show a clear action and re-check on return.
- If the user denies or downgrades permissions, keep Start blocked and explain the specific missing condition.

On Start:

- Create a new GPX/draft in `Documents/StrideMap/Tracks`.
- Set track state to `live`.
- Start foreground service and show notification promptly.
- Show `Capture started` confirmation and switch to the Map tab.
- Request high-accuracy location updates.
- Recording enters `live` immediately but may have zero points until first GPS fix.
- Show `Waiting for first GPS point…` on Map while zero points have been saved.
- Persist each accepted point according to the distance-targeted sampling rules.

Recording behavior:

- Persisted-point sampling is distance-targeted, not fixed-time: accept the first valid point, then accept only when elapsed time is at least 1 second and distance from the last accepted persisted point is at least 10 meters.
- Movement type does not change persistence rules; it selects the separate per-movement provider polling setting, whose default is 1000ms for every movement type.
- Persist every accepted location update that satisfies app sampling rules.
- If Android delivers delayed, batched, or irregular updates, preserve truthful timestamps and surface/log enough information for debugging.
- Save poor-accuracy real points with accuracy metadata.
- Save speed only if Android reports speed as available; do not synthesize speed in v1 unless this file is updated.
- Live stats update as points are saved.

Accepted location points must be validated before persistence:

- Latitude in `[-90, 90]`.
- Longitude in `[-180, 180]`.
- Timestamp present and non-decreasing for the track.
- Accuracy positive when present.
- Reject exact duplicate timestamp/coordinate points unless explicitly justified in implementation notes.
- Preserve poor-accuracy real points, but mark accuracy metadata.

Distance and speed semantics:

- Distance is computed from accepted persisted points only.
- Rejected points do not contribute to distance.
- Use meters internally.
- Use one deterministic distance formula throughout v1.
- UI may display short kilometers.
- Android-provided speed is stored only when available from the provider.

Stop behavior:

- Stop opens a Material 3 `AlertDialog`.
- Standard title: `Stop capture?`.
- Body includes distance, duration, points saved, and filename.
- Dismissive action: `Keep recording`.
- Confirm action: `Stop and save`.
- If user cancels, recording continues unchanged.
- Dialog must not stop the foreground service until confirm is tapped.
- If confirmed with at least one point, mark GPX state `stopped`, finalize metadata, stop location updates, stop foreground service, and keep the track selected for Map.
- If zero GPS points were captured, show empty variant:
  - Title: `Discard empty capture?`
  - Body: `No GPS points have been saved yet. Stopping now will discard this capture.`
  - Confirm action: `Discard`.
  - Use destructive styling only for discard-empty case.
  - Discard the empty file/track and stop foreground service after confirmation.

## Tracks page

The Tracks page is the local capture library. Earlier implementation notes may call this the List page, but the current user-facing label is `Tracks`.

### Visual appearance

Use a standard Material 3 Compose list layout. It should be compact and scannable rather than detail-heavy.

Recommended structure from top to bottom:

1. **Page title / toolbar area**
   - Use `Scaffold` with `TopAppBar` title `Tracks`.
   - Include a refresh icon button with content description `Refresh tracks`.
   - Show loading indicator while refresh/scan is active.

2. **Filter/sort controls**
   - Use horizontally scrollable `FilterChip`s for movement type: `All`, `Walk`, `Run`, `Bike`, `Car`, `Train`.
   - Default: `All`.
   - Sort field selector: `Date` or `Distance`.
   - Default sort: `Date`, descending/newest first.
   - Direction control must have label/content description matching the active sort, such as `Newest first`, `Oldest first`, `Shortest first`, or `Longest first`.

3. **Track rows**
   - Use Material 3 `ListItem` or custom row in a `Card`.
   - Minimum touch target 48dp.
   - Headline: message if present, otherwise readable fallback such as `Walk capture` or filename-derived label.
   - Supporting line: human-readable date/time plus duration if available.
   - Trailing: short kilometer distance, e.g. `2.4 km`.
   - Show movement type as a prominent movement-colored chip/badge with icon and label.
   - Show state chip/label for `live` and `interrupted`.
   - Completed `stopped` tracks may omit state chip or show low-emphasis `Stopped` only if needed.

4. **Live track row**
   - If a live track exists, it is selectable from the list.
   - It should be visually distinct with `Live` chip/icon/text and optional subtle container tint.

5. **Interrupted track row**
   - `Interrupted` should be visible enough to notice.
   - Supporting text may say `Capture ended unexpectedly`.

6. **Malformed file rows**
   - If a GPX in the folder cannot be parsed, show a non-blocking error row.
   - Use error-colored icon/text plus filename.
   - Supporting text should safely summarize parse category, e.g. `Could not read GPX metadata.`
   - Do not display full private paths or raw route contents.
   - Do not silently ignore malformed files.

7. **Empty state**
   - Show icon, title `No tracks yet`, body `Start a capture to create your first GPX track.`, and action `Go to Capture`.

8. **Loading state**
   - Initial scan may show centered `CircularProgressIndicator` with `Loading tracks…` or skeleton rows.
   - Refresh must not blank the existing list; keep current rows and show refresh/loading indicator.

### Routing

- Reached by bottom navigation item `Tracks`.
- On startup, app scans `Documents/StrideMap/Tracks` for GPX files.
- Refresh action rescans the folder.
- Tapping a valid track row selects that track, switches to `Map`, and displays it.
- Tapping a malformed row does not navigate to Map; it opens a simple details dialog or inline expansion with safe filename/error summary.
- The currently selected track should have selected visual state if user returns to Tracks.

### Details

- Default sorting is date descending: newest first.
- User can sort by date or distance.
- User can toggle ascending/descending direction.
- User can filter by movement type.
- Filtering applies to valid parsed tracks only.
- Malformed rows remain visible in an error section or at the end regardless of movement filter.
- Live tracks should appear before stopped/interrupted tracks unless filtered out by movement type.
- Distance sort uses meters; unknown distance sorts last.
- No delete operation in v1.
- No edit operation in v1.
- Data source is GPX files under `Documents/StrideMap/Tracks`.
- GPX metadata/extensions should provide fast display data where possible.
- Distance should be cached in GPX metadata and recomputed from accepted points if missing.
- The list must tolerate missing metadata by falling back to parsed points or filename where possible.
- The scanner must tolerate files being added, removed, or modified outside the app.
- If a selected file is deleted externally, clear selection and show a non-fatal message.
- Refresh during recording must not corrupt or duplicate the active live track.
- If the live GPX is being written, scanner reads must be defensive against transient write state.

## Map page

The Map page is the track viewer and live-route viewer.

### Visual appearance

Use OpenStreetMap through osmdroid. Keep v1 map UI focused on route display.

Global map requirements:

- Use full-screen map inside `Scaffold`, respecting bottom nav and system insets.
- Reserve space so OSM/osmdroid attribution remains visible and is not obscured by bottom sheet/nav.
- Route line must use the selected/live track movement color, explicit width, and high contrast over OSM tiles; a dark casing line is preferred when practical.
- Start/end markers must differ by icon/shape/text, not color only.
- Live latest-point marker must differ from completed end marker.
- Do not clutter v1 with every point marker unless needed later.

States:

1. **No selected/live track**
   - Do not show random OSM tiles as the primary state.
   - Show Material empty state over neutral surface.
   - Title: `No track selected`.
   - Body: `Start a capture or choose a track from the list.`
   - Buttons: `Start capture`, `Open list`.

2. **Selected completed/interrupted track**
   - Show route line.
   - Show start marker.
   - Show end marker.
   - Auto-zoom to route bounds with padding.
   - Show lightweight bottom info sheet/card with message/fallback filename, movement-colored movement type chip, date, distance, duration, and state if interrupted.

3. **Live track**
   - Show route line from saved points.
   - Show start marker.
   - Show latest point marker.
   - Follow latest point automatically until user pans/zooms.
   - Provide a `Follow` button to resume following after panning.
   - `Follow` button should be a Material `FloatingActionButton` or `ExtendedFloatingActionButton`.
   - Button label/content description: `Follow live location`.

4. **Map loading/error**
   - If tiles are loading, app UI remains stable even if map is blank/partial.
   - If map initialization fails, show error panel:
     - `Map unavailable`
     - `Could not load the map view.`
     - Optional `Retry`.

### Routing

- Reached by bottom navigation item `Map`.
- Receives selected track from Tracks row taps.
- Can display the live track when a capture is active.
- Map display priority:
  - If user explicitly selected a stored track, show that track even while recording continues.
  - If no stored track is selected and a live track exists, show the live track.
  - Stopping a live track makes the stopped track the selected track.
- If a live recording exists while a stored track is selected, provide at least a simple way to return to the live track or explicitly document that bottom-tab navigation alone does not change selection.

### Details

- For selected non-live tracks, auto-zoom to track bounds.
- For very short/single-point tracks, use a reasonable default zoom around the point.
- A one-point track should show a marker even if no line can be drawn.
- A zero-point valid metadata file should not be shown as a normal mappable track.
- For live track, update on each saved point.
- For live track, follow latest point automatically.
- If user pans/gestures the map during live capture, pause auto-follow.
- Follow button resumes latest-point following and recenters to latest point.
- Render persisted/live GPX point data. Do not invent interpolated route geometry for v1.
- osmdroid setup must include required attribution display, tile cache configuration, network-unavailable behavior, lifecycle forwarding from Compose/Activity to `MapView`, and no leaking Activity context.

## Data model and GPX contract

Core app model concepts:

- `Track`.
- `TrackState`: `live`, `stopped`, `interrupted`.
- `MovementType`: `walk`, `run`, `bike`, `car`, `train`.
- `LocationPoint`.
- Selected track reference.
- Live track reference.
- Parse/error representation for malformed GPX rows.

Each GPX track stores, via standard GPX metadata and/or stable StrideMap extensions:

- Message.
- Movement type.
- Track state.
- Start timestamp.
- End/latest timestamp where available.
- Duration seconds.
- Distance meters cache.
- App/schema version.

Each GPX point stores:

- Latitude.
- Longitude.
- Elevation as standard GPX 1.1 `<ele>` only when Android reports altitude or parsed GPX already contains a valid `<ele>`; do not synthesize, infer, smooth, or backfill elevation, and omit `<ele>` when unavailable.
- Timestamp.
- Accuracy meters extension when available.
- Speed meters per second extension only if Android provides speed.

Potentially useful later, but not required for v1:

- Bearing.
- Provider/source.
- Mock-location flag.

GPX extension requirements:

- Use one stable namespace, for example `xmlns:stridemap="https://stridemap.app/gpx/1"`.
- The exact namespace is a blocker decision before implementation.
- Track state must be stored inside GPX metadata/extensions, not only inferred from filename or memory.
- Required metadata extension fields:
  - `stridemap:movementType`
  - `stridemap:trackState`
  - `stridemap:message`
  - `stridemap:distanceMeters`
  - `stridemap:durationSeconds`
  - `stridemap:appSchemaVersion`
- Required point extension fields when available:
  - `stridemap:accuracyMeters`
  - `stridemap:speedMetersPerSecond`

Persistence invariants:

- GPX files must remain parseable after every persisted point, including after crash/kill.
- If true streaming GPX cannot maintain valid XML safely, use an internal draft/write strategy that leaves either a recoverable user-accessible GPX or a file that can be repaired/finalized on startup.
- Writes must be crash-tolerant.
- Avoid corrupting an existing valid GPX when updating metadata/state.
- Use temp-file/replace or another explicit atomic strategy where the chosen Android storage API supports it.
- On startup or first setup, verify the app-private recovery directory and MediaStore public Documents target are available.
- `Documents/StrideMap/Tracks/` is created implicitly by MediaStore when the first public GPX row is inserted.
- If the app-private journal directory or MediaStore target is unavailable, block Start and show user-level root cause.
- “Storage access” must mean a concrete Android API decision. Do not request broad external storage permissions unless explicitly approved.

Filename format:

```text
YYYY-MM-DD_HH-mm-ssZ_<movement-type>_<sanitized-message-prefix>.gpx
```

Rules:

- Timestamp is UTC and sortable.
- Timestamp includes seconds to avoid common collisions.
- Include movement type.
- Include first 16 characters of message after sanitization.
- If message is empty, omit the message suffix or use an empty-safe suffix according to the documented implementation convention.
- If filename already exists, append `-2`, `-3`, etc. without overwriting.
- Sanitized message prefix rules:
  - Lowercase ASCII where possible.
  - Replace whitespace with `-`.
  - Remove path separators and control characters.
  - Allow only `[a-z0-9-]`.
  - Collapse repeated dashes.
  - Trim leading/trailing dashes.
  - Max 16 chars after sanitization.

Example:

```text
2026-06-08_15-30-04Z_walk_walk-to-park.gpx
```

## Lifecycle and state rules

Track states:

- `live`: capture is active and points may be appended.
- `stopped`: user intentionally stopped and saved/finalized the track.
- `interrupted`: app/process/service ended unexpectedly or startup found a stale live track with points.

Allowed transitions:

| From | To | Trigger |
| --- | --- | --- |
| none | `live` | Start succeeds |
| `live` | `stopped` | User confirms Stop and at least one point exists |
| `live` | discarded | User confirms Stop before first point |
| `live` | `interrupted` | Startup detects stale live track with points or unrecoverable capture failure after points exist |
| `stopped` | terminal | No edits/reopen in v1 |
| `interrupted` | terminal | No resume in v1 |

One-live invariant:

- The app must allow at most one `live` track.
- If one exists, Start is disabled.
- Starting a new capture must never auto-stop or overwrite an existing live track.

Foreground service requirements:

- Declare location foreground-service type in manifest.
- Start foreground notification promptly after service start.
- Notification text may be simple but must identify active capture and expose a way back to the app.
- If service cannot enter foreground state, fail Start, mark/discard draft according to point count, and surface a user-visible error.
- Stopping from the app must stop location updates before stopping the service.
- If storage write fails during live capture, stop accepting new points, stop location updates, mark track `interrupted` if it has points, otherwise discard, and show a user-visible error.

App restart behavior:

- On startup, scan for stale `live` track state.
- The app must distinguish “stale live GPX from previous dead process” from “currently active foreground service”.
- Do not mark a live track interrupted merely because the Activity was recreated while the service is still recording.
- Mark stale live tracks with one or more points `interrupted`.
- Interrupted tracks with one or more points remain valid tracks and appear in Tracks/Map.
- Interrupted tracks with zero points should be discarded on startup cleanup.
- Preserve the GPX route data.
- Do not auto-resume recording.
- Do not auto-start a new track.
- User must return to Capture, configure inputs, and press Start.
- If Android restarts the service without explicit user action, it must not silently continue appending unless this file is updated.

Graceful shutdown:

- If Android offers a graceful lifecycle hook, use it to preserve/finalize current state as well as possible.
- Do not rely on graceful shutdown for correctness; crash/kill recovery must be handled by startup stale-live detection.

## Permissions and platform behavior

Before Start is enabled, v1 requires:

- Fine/precise location.
- Background location.
- Device location services enabled.
- Notification permission when required by platform.
- Storage access to writable `Documents/StrideMap/Tracks`.
- Selected movement type.
- Legal ability to start location foreground service.

Use a foreground service with a persistent notification for background/screen-sleep capture. If required permissions/access are missing, explain and block Start rather than silently degrading.

No-degradation rule:

- Do not fall back to approximate location.
- Do not fall back to foreground-only recording.
- Do not fall back to app-private-only files.
- Do not run background capture without a notification.
- Any such change requires this file to be updated and explicitly approved.

Battery/OEM note:

- Battery optimization/OEM background restrictions should be detected or documented where practical.
- V1 may surface them as warnings rather than blockers unless they prevent foreground-service capture.

## Global empty/error/loading/accessibility states

- Empty states must include a clear title, one-sentence explanation, and recovery action when possible.
- Error states must explain what failed, why if safely knowable, and what the user can do next.
- Show loading feedback for scans/permission/storage checks that may exceed about 300ms.
- Avoid blank screens during refresh; keep existing content visible where possible.
- All touch targets minimum 48dp.
- Support Dynamic Type/font scaling without clipping.
- Use Material color roles and ensure light/dark contrast.
- Do not communicate status by color alone.
- Icon-only buttons need content descriptions.
- Recording timer/stats should update without stealing accessibility focus.
- Checklist rows announce requirement name + status.
- Decorative icons should be marked decorative if text already conveys state.

## Development and testing implications

- Captured GPX files are the intended real-data foundation for future versions.
- Emulator location playback and scripted GPS injection remain useful for development feedback loops.
- Real-device validation remains required for actual GPS/background/battery reliability.
- Do not commit personal or large real route artifacts unless explicitly approved for a specific future task.
- Unit tests can still use synthetic fixtures, but v1 product behavior should be validated with actual app capture and accessible GPX output.
- Location capture must be behind an app-owned abstraction so tests can inject deterministic fake locations without Android GPS.
- GPX writing/parsing and track state transitions must be unit-testable without emulator/device.
- Emulator/mock success proves app wiring, not production GPS reliability.

## Implementation sequence

0. Resolve blocker decisions: storage API, location provider, GPX namespace/schema, GPX write strategy, foreground-service restart policy, stale-live detection, osmdroid network/cache behavior, and dependency approvals.
1. Define testable domain contracts for `Track`, `TrackState`, `MovementType`, `LocationPoint`, GPX writer/parser, selected/live state, parse errors, and location provider abstraction.
2. Implement GPX writer/parser and lifecycle tests before wiring foreground capture, because capture correctness depends on crash-tolerant persistence.
3. Create the app shell with Material 3 Compose bottom navigation: Capture, Tracks, Map.
3a. Planned next shell expansion: add a top-corner gear action that opens Settings with Android Settings-style root/subpage rows and persisted default values.
4. Define the shared app state/model for selected track, live track, track list, setup readiness, and snackbar messages.
5. Build file access/write path for `Documents/StrideMap/Tracks`.
6. Build Capture UI shell with message input, required movement dropdown, checklist, live stats, and Start/Stop flow.
7. Implement track lifecycle/state handling and one-live invariant.
8. Implement high-accuracy foreground-service capture with per-movement provider polling settings and distance-targeted persisted-point sampling.
9. Persist accepted points live to GPX and finalize/interrupt correctly.
10. Build GPX scanner/parser and Tracks page with filter/sort/refresh/error rows.
11. Add osmdroid Map page for selected/live track rendering.
12. Add live map follow/pause/resume behavior.
13. Validate on emulator/device and document observed limitations.

## Requirement-to-verification matrix

| Requirement | Verification approach |
| --- | --- |
| Movement type default/lock | UI test/manual check: fresh state defaults to Walk; selector can change type before Start; selector disabled while recording |
| Permissions/access required | UI/manual check: checklist blocks Start while missing |
| Approximate-only blocked | Grant approximate only; Start remains blocked with explanation |
| Background missing blocked | Grant foreground only; Start remains blocked with explanation |
| Device location disabled blocked | Disable location services; Start remains blocked |
| Storage unavailable blocked | Revoke/deny/unavailable storage target; Start blocked with explanation |
| Foreground service failure | Force service start failure; Start fails visibly and leaves no live orphan |
| Distance-targeted cadence | Unit/manual check: first point saves, stationary duplicate updates do not persist, later saves require at least 1 second and at least 10 meters; live timer still shows capture elapsed time |
| First fix delay | Start with no GPS fix; UI shows waiting; Stop discards empty on confirmation |
| Point validation | Unit tests reject invalid lat/lon/timestamps/duplicates as specified |
| Live append | Kill app mid-capture and verify partial GPX contains saved points |
| GPX parseable after kill | Kill during capture; verify GPX parses and state becomes interrupted |
| Write failure handling | Simulate write exception; track interrupted/discarded correctly |
| One live track | Attempt second Start while live track exists; Start disabled |
| Activity recreation | Rotate/recreate Activity during recording; service continues; no stale interruption |
| Restart interruption | Start capture, kill/restart app, verify stale track becomes `interrupted` and no capture resumes |
| Stop confirmation | Stop opens dialog with distance/duration/points/filename; cancel continues, confirm stops |
| Empty capture discarded | Start/stop before first point and verify no empty GPX remains |
| Tracks scan | Put GPX in target folder and verify row appears after startup/refresh |
| Type filter | Verify each movement filter includes only matching valid tracks; malformed rows remain visible |
| Date/distance sort | Verify date and distance asc/desc ordering; unknown distance sorts last |
| Malformed GPX row | Place invalid GPX and verify visible safe error row |
| External deletion | Delete selected file outside app; selection clears with non-fatal message |
| Refresh during recording | Refresh while recording; active track is not duplicated/corrupted |
| Map selected track | Tap list row and verify Map shows line + start/end markers |
| One-point GPX | Map shows marker without crashing |
| Empty map state | No selected/live track shows `No track selected` state with actions |
| Live map follow | During capture, verify map follows saved latest point; pan pauses; follow button resumes |
| osmdroid lifecycle | Navigate/recreate Activity without leaking/crashing; attribution remains visible |
| Settings entry point | Manual/UI check: top-corner gear icon appears, has an accessible label such as `Settings`, and opens Settings root |
| Settings root style | Manual/UI check: Android Settings-style grouped rows with leading icons, title, and summary/current value |
| Settings subpages | Manual/UI check: subpages have back arrow and single-row setting entries |
| Default movement setting | Unit/manual check: changed value persists and affects the next new capture only |
| Start destination setting | Manual check: `Open Map` preserves current Start behavior; `Stay on Capture` keeps user on Capture after Start |
| Default note setting | Unit/manual check: changed template persists, is trimmed/capped defensively, and pre-fills future idle capture setup |
| Tracks defaults | Unit/manual check: default movement filter/sort/order persist and initialize a fresh Tracks screen |
| Map follow default | Manual check: setting initializes live-map follow state without preventing user pan/pause behavior |
| Map saved-point dots default | Manual check: disabling hides intermediate saved-point dots while keeping start/end/latest markers |
| Route width default | Unit/manual check: route width persists, clamps to safe values, and changes rendered route thickness |
| Storage/app info | Manual check: Settings shows the track folder path and refresh/rescan action without exposing private raw paths |
| Tracks refresh overlay | Manual/UI check: Tracks refresh appears as a bottom-left icon FAB with `Refresh tracks` content description and does not cover list rows |

## Original go/no-go gates

This feature was considered implementation-ready only when:

- Every blocker in `Implementation blockers before coding` is resolved or intentionally deferred with explicit approval.
- Every locked decision has an implementation owner/path.
- Every requirement in the verification matrix has an owning test, script, or documented manual validation step.
- The storage/API/permission choices are compatible with Android 16/API 36.
- The GPX contract can produce parseable files after normal stop, app kill, and startup interruption recovery.

## Resolved implementation questions

These questions were blockers before coding and were resolved on 2026-06-08:

- Storage: use MediaStore public Documents for normal `Documents/StrideMap/Tracks/` GPX creation; do not use app-private-only export. Exception added on 2026-06-09: after Samsung's SAF picker refused the target folder, user explicitly approved a Settings-only `MANAGE_EXTERNAL_STORAGE` recovery flow that direct-scans `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` for existing GPX files that scoped storage cannot automatically rediscover.
- Location: use an app-owned abstraction with Google Play Services `FusedLocationProviderClient` as the v1 production provider.
- GPX schema: use GPX 1.1 plus StrideMap namespace `https://stridemap.app/gpx/1` and schema version `1`.
- GPX write strategy: app-private active-session journal as recovery source of truth plus validated full public GPX snapshots through MediaStore.
- Foreground service restart mode: `START_NOT_STICKY`, no boot receiver, no pending-intent location updates, no auto-resume.
- Stale live detection: same-process active owner/service state plus persisted journal/session metadata; stale live with points becomes `interrupted`, stale empty live is discarded.
- osmdroid: online OSM tiles are acceptable by default; use stable user agent, cache path, attribution overlay, lifecycle forwarding, and no bulk/offline predownload.
- Malformed GPX errors: show safe filename/category/summary rows; do not show private paths or raw route contents.

## Addendum log

- 2026-06-08: Feature created from grill-me session. Current scope is initial creation of all defined v1 components for the real GPX reference-track capture app: Capture/Tracks/Map tabs, GPX persistence, track lifecycle, permissions/setup gating, foreground-service capture, and OSM display.
- 2026-06-08: Hardened feature file with adversarial implementation, lifecycle, storage, permission, UI, accessibility, and verification details so future implementation agents do not need to infer core behavior.
- 2026-06-08: Storage decision changed from SAF folder picker/DocumentFile to MediaStore public Documents. GPX files are created under `Documents/StrideMap/Tracks/` without user-created directories or broad storage permissions; app-private journal remains the recovery source of truth.
- 2026-06-08: Added implementation record for the initial v1 build: Compose app shell, Capture/Tracks/Map tabs, FLP foreground-service capture, MediaStore GPX storage, app-private journal recovery, osmdroid map, default Walk movement type, explicit setup checklist, tests/validation performed, bugs fixed during emulator testing, and remaining validation gaps.
- 2026-06-08: Updated implementation record after reproducing and fixing Start failure `Could not start capture: Generated GPX did not validate` on the Pixel 9 emulator. Verified by building, installing, launching, scrolling to `Start capture`, tapping it through adb, observing `Capture started` then `Stop capture`, and confirming a StrideMap foreground notification row exists.
- 2026-06-08: Added implementation record for the first professional polish pass: fixed charcoal/muted-green light/dark palette, Tracks user-facing naming, inline vector bottom-nav icons, private-first Capture copy, Start-first Capture layout, compact ready checklist, theme-colored map route, custom topography launcher mark, build/unit verification, and Samsung S23 install-launch visual validation. Full S23 real GPS/background/battery/file-visibility validation remains pending.
- 2026-06-08: Follow-up polish/bugfix after Samsung S23 use: Capture movement chips now wrap across multiple lines instead of horizontal scrolling; Start/Stop buttons use clearer recording/stop-sign icons and explicit `Start recording` / `Stop recording` labels; live elapsed time now uses capture start time rather than saved-route duration, so a stationary live capture with one persisted point can still show a running timer. This preserves distance-targeted storage: while stationary, point count may remain one until movement reaches about 10 meters.
- 2026-06-08: Implemented Settings/defaults section behind the existing top-right gear, not a fourth bottom tab. Added Android Settings-style root rows/subpages, `SharedPreferences` persistence, Capture movement/start/note defaults plus linked GPS polling, Tracks filter/sort/order defaults, Map live-follow/saved-dot/route-width defaults, and Storage/App info with refresh/rescan. Advanced GPS/sampling tuning remains out of scope, and saved-point sampling remains non-configurable.
- 2026-06-08: Fixed stopped-track duration persistence for stationary captures: normal stop now records capture elapsed time from start to stop even when only one GPS point was saved, and GPX write/read preserves that completed duration so rescanned one-point stopped tracks do not reload as `00:00:00`.
- 2026-06-08: Added prominent movement-type coloring across Capture chips, Tracks filter/row badges, Map route/info/marker accents, and GPS polling Settings rows using the approved light/dark Walk/Run/Bike/Car/Train palette. Labels and icons remain present so movement identity is not color-only.
- 2026-06-09: Replaced the SAF recovery attempt with explicitly approved All files access recovery after Samsung's picker showed an empty root/refused `Documents/StrideMap/Tracks`. Settings → Storage/App info now has `Recover existing tracks`, opens Android's All files access settings for StrideMap, and future rescans include read-only `.gpx` files from `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` while keeping MediaStore as the normal write target. Earlier SAF folder and multi-file picker attempts were rejected because Samsung's picker did not expose/grant the target Internal storage folder reliably.
