# Raw session log — SGS23 polling defaults and existing-track recovery

Date: 2026-06-09

## Session purpose

Respond to real-device validation feedback from the Samsung Galaxy S23 after walking around the block and capturing several tracks with different movement types.

The session covered two related areas:

1. Make provider GPS polling default to 1 second for every movement type while keeping separate Settings values per movement type.
2. Make Tracks recovery/rescan find existing GPX files still present under `Documents/StrideMap/Tracks` after app data was cleared on the device.

## Starting context loaded

Mandatory project context was loaded from:

- `agentic/README.md`
- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`
- `agentic/guidance/ARCHITECTURE.md`
- `agentic/guidance/ERROR.md` when the storage/rescan bug investigation began
- `agentic/knowledge/features/init-creating-all-defined-components.md`
- `agentic/sessions/summaries/2026-06-08__stridemap-grilling__capture-reference-track-v1.md`

Important source-of-truth constraints at the start:

- StrideMap v1 is a real GPS reference-track capture app, not simulator-first.
- Saved-point sampling is non-configurable: first valid point, then at least 1 second and at least 10 meters from the last persisted point.
- Provider polling is separate from saved-point sampling and is Settings-based.
- Settings changes affect future captures only.
- Public GPX output is through MediaStore under `Documents/StrideMap/Tracks/`.
- App-private active-session GPX journal is the capture recovery source of truth.

## Part 1 — One-second provider polling defaults

### User request

After real walking/capture tests, the user said:

> The polling intervall should be one second for all movement types per default. I want to keep the settings options and separate values for each of the movement types. Plan these changes.

### Codebase findings

Polling defaults and Settings wiring were found in:

- `app/src/main/java/com/example/stridemap/location/LocationProvider.kt`
  - `LocationRequestSpec.defaultIntervalMillisForMovement()` derived defaults from a 10m target distance and nominal movement speeds.
  - Old defaults were Walk `9000ms`, Run `3600ms`, Bike `1286ms`, Car `1000ms`, Train `1000ms`.
- `app/src/main/java/com/example/stridemap/StrideMapRepository.kt`
  - `locationRequestSpecFor(type)` uses `state.settings.gpsPollingIntervalMillis(type)` when starting provider capture.
  - `UserSettings.default()` built a per-movement GPS polling map from `LocationRequestSpec::defaultIntervalMillisForMovement`.
  - SharedPreferences persisted per-movement GPS polling keys.
- `app/src/main/java/com/example/stridemap/MainActivity.kt`
  - Settings UI already had one GPS polling row/value per movement type.
- `app/src/main/java/com/example/stridemap/location/GooglePlayServicesLocationProvider.kt`
  - Applies the request spec to Play Services interval and min update interval.
- `app/src/main/java/com/example/stridemap/core/PointValidation.kt`
  - Contains saved-point cadence and was explicitly left unchanged.

Tests to update:

- `app/src/test/java/com/example/stridemap/location/LocationRequestSpecTest.kt`
- `app/src/test/java/com/example/stridemap/AppStateTest.kt`

### Migration decision

The user said no migration was needed because the app is still in dev phase.

Decision:

- Change fresh defaults to `1000ms` for every movement type.
- Keep separate per-movement Settings values/rows.
- Do not migrate existing SharedPreferences values.
- Existing installs may retain old stored polling values until app data is cleared or the Settings values are manually changed.

### TDD implementation

The TDD skill was loaded.

Tests were changed first:

- `LocationRequestSpecTest.kt`
  - Expected `1_000L` for Walk/Run/Bike/Car/Train.
  - Renamed the movement-default test to `movementTypeDefaultsToOneSecondProviderPolling`.
- `AppStateTest.kt`
  - Expected fresh settings to include `1_000L` GPS polling defaults for all movement types.

The targeted test run failed as expected with assertions in:

- `AppStateTest.freshStateIncludesDefaultGpsPollingSettings`
- `LocationRequestSpecTest.movementTypeDefaultsToOneSecondProviderPolling`
- `LocationRequestSpecTest.exposesCurrentDefaultsForSettingsUi`

Production change:

- `LocationProvider.kt`
  - Removed nominal-speed/target-distance constants and imports.
  - Changed `LocationRequestSpec.defaultIntervalMillisForMovement(movementType: MovementType): Long` to return `MinimumProviderIntervalMillis` for every movement type.

Documentation updated:

- `agentic/knowledge/features/init-creating-all-defined-components.md`
  - Movement type still has separate provider-request Settings values.
  - Default provider polling is now 1000ms for every movement type.
  - Saved-point sampling remains unchanged.

Verification:

- Targeted tests passed.
- `./gradlew :app:testDebugUnitTest --no-daemon` passed.
- `./gradlew :app:assembleDebug --no-daemon` passed.
- Debug build installed and launched successfully on Samsung Galaxy S23.

### Device caveat and resolution

The user reported Settings still showed non-1-second values.

Cause:

- Expected due to the no-migration decision.
- The existing device install retained old SharedPreferences values from before the default change.

The user approved clearing app data.

Command run:

```bash
adb shell pm clear com.example.stridemap
adb shell am start -W -n com.example.stridemap/.MainActivity
```

Result:

- App data cleared successfully.
- App relaunched successfully.
- User was warned permissions likely needed to be granted again.

## Part 2 — Existing GPX files not visible after app data clear

### User report

After app data clear, older GPX files still physically present on the phone were not listed in Tracks.

Refreshing/rescanning from Tracks or Settings did not bring them back.

### Code investigation

Relevant code paths:

- `AndroidTrackStorage.listGpxFiles()`
  - Queried MediaStore only.
  - Filtered exact `RELATIVE_PATH = Documents/StrideMap/Tracks/` and either MIME type `application/gpx+xml` or display name ending in `.gpx`.
- `AndroidTrackStorage.findFileUri()`
  - Exact MediaStore lookup by relative path and display name.
- `AndroidTrackStorage.insertPendingFile()`
  - Creates MediaStore rows with `RELATIVE_PATH`, MIME type, and `IS_PENDING=1`.
- `StorageContracts.kt`
  - Contains public track storage path constants.
- `StrideMapRepository.initialize()` and `scanTracks()`
  - Call storage list/read/parse.
- Tracks refresh FAB and Settings rescan
  - Both call `StrideMapRepository.scanTracks(false)`.
- `GpxParser.parse()`
  - Visible invalid GPX files should become malformed rows, so an empty Tracks list pointed to query/visibility/read access rather than parser rejection.

### SGS23 diagnostics

ADB filesystem check showed many `.gpx` files physically present under:

```text
/sdcard/Documents/StrideMap/Tracks
```

MediaStore queries showed rows under `Documents/StrideMap/Tracks/`, including older rows and one new post-clear row.

Important observation:

- Older rows had `owner_package_name=NULL` or were not visible/readable to the current app identity after data clear.
- The newest post-clear GPX row belonged to `com.example.stridemap`.
- `run-as com.example.stridemap ls -la /sdcard/Documents/StrideMap/Tracks` showed only the post-clear GPX visible to the app UID.

Package permissions did not include broad storage access.

### Scoped-storage conclusion

Library/documentation research confirmed:

- On modern Android/API 36, non-media `.gpx` files in public Documents are “documents and other files.”
- Android routes this category through the Storage Access Framework for user-selected access.
- `MediaStore.Files` visibility under scoped storage is limited and is not a reliable automatic rediscovery path for old GPX documents after app data clear/reinstall.
- Android 13+ media permissions do not cover GPX/documents.
- `MANAGE_EXTERNAL_STORAGE` would technically allow broad discovery, but is special all-files access and carries Play policy risk.

Product-level options presented:

1. Keep current no-SAF/no-broad-storage constraint and accept empty Tracks after app data clear.
2. Add SAF recovery/import.
3. Add share/open-with GPX import.
4. Add All files access recovery with `MANAGE_EXTERNAL_STORAGE`.

The user stated that if tracks are present, rescan should find and list them.

The recommendation was to first try a user-initiated SAF recovery/import flow.

## Part 3 — SAF recovery attempts

### First SAF folder recovery

The user approved SAF recovery.

Implementation added:

- `RecoveredTrackFolder`
- `RecoveredTrackFolderContract`
- `TrackFileRef.canDelete = false`
- `TrackSnapshotStore.recoveredTrackFolder()`
- `TrackSnapshotStore.setRecoveredTrackFolder(uri)`
- Persisted tree URI in `AndroidTrackStorage` SharedPreferences key `recovered_track_folder_uri`
- Included `.gpx` children from the selected SAF tree in `AndroidTrackStorage.listGpxFiles()`
- `StrideMapRepository.setRecoveredTrackFolder(uri)` and `AppState.recoveredTrackFolder`
- Settings row `Recover existing tracks` using `ActivityResultContracts.OpenDocumentTree()` and `takePersistableUriPermission(READ)`

Docs were updated to describe user-initiated SAF folder recovery while MediaStore remained the write target.

Verification passed:

- Targeted storage tests.
- Full unit tests.
- Assemble.
- Install and launch on SGS23.

User was told to select `Documents/StrideMap/Tracks` from Settings → Storage/App info → Recover existing tracks.

### Multi-file SAF attempt

The Samsung picker showed an empty device root and did not expose the expected folders/files.

The implementation was changed to a multi-file picker:

- `ActivityResultContracts.OpenMultipleDocuments()`
- Stored selected file URI strings in SharedPreferences.
- Used `OpenableColumns.DISPLAY_NAME` to recover display names.
- Produced read-only `TrackFileRef`s for selected `.gpx` files.

Tests were changed first and failed red due to unresolved symbols, then implementation was completed.

Verification again passed:

- Targeted tests.
- Full unit tests.
- Assemble.
- Install and launch on SGS23.

### SAF folder picker with initial URI

The user clarified the files were in Internal storage → Documents → StrideMap → Tracks, and the picker still did not allow selecting Internal storage.

Documentation research confirmed `ActivityResultContracts.OpenDocumentTree` accepts an initial `Uri?` and AndroidX passes `DocumentsContract.EXTRA_INITIAL_URI`.

Implementation switched back to a folder model initialized at:

```text
content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FStrideMap%2FTracks
```

with document id:

```text
primary:Documents/StrideMap/Tracks
```

Changes:

- Added `SafTrackFolderLocation` constants and `initialTreeUri()`.
- Restored `RecoveredTrackFolder` and `RecoveredTrackFolderContract`.
- Scanned SAF children via `DocumentsContract.getTreeDocumentId`, `buildChildDocumentsUriUsingTree`, and `COLUMN_DOCUMENT_ID`/`COLUMN_DISPLAY_NAME`.
- Settings launched `OpenDocumentTree()` with `SafTrackFolderLocation.initialTreeUri()`.

Verification again passed.

User then reported the Samsung picker still showed no directories/files and a German message equivalent to “This folder cannot be used.”

Conclusion:

- The physical path exists, but the Samsung SAF picker refused to grant that folder.
- SAF was not reliable enough for the desired “rescan finds existing files” UX on this device.

## Part 4 — All files access recovery

### User approval

The assistant proposed adding All files access recovery:

- Declare `MANAGE_EXTERNAL_STORAGE`.
- Add a Settings action to open StrideMap’s All files access settings page.
- Once granted, directly scan `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx`.

The user approved:

> Then implement this. "add All files access recovery"

### TDD test changes

`PublicTrackStorageLocationTest.kt` was changed first to expect:

- `DirectTrackRecoveryLocation.AbsolutePath == "/storage/emulated/0/Documents/StrideMap/Tracks"`
- `DirectTrackRecoveryLocation.DisplayPath == "/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx"`
- Direct recovery accepts `.gpx` and `.GPX` filenames.
- Direct recovery rejects non-GPX filenames and null.
- `RecoveredTrackFolder()` defaults to the direct path and display text `All files access direct scan`.

The targeted test failed red as expected because `DirectTrackRecoveryLocation` did not exist and `RecoveredTrackFolder` still required a URI.

### Implementation

Manifest:

- Added:

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

`StorageContracts.kt`:

- Replaced SAF-specific `SafTrackFolderLocation` with `DirectTrackRecoveryLocation`:
  - `AbsolutePath = "/storage/emulated/0/Documents/StrideMap/Tracks"`
  - `DisplayPath = "/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx"`
  - `.gpx` filename filter.
- Changed `RecoveredTrackFolder` to default to the direct recovery path and display text `All files access direct scan`.
- Added `TrackSnapshotStore.hasAllFilesRecoveryAccess()`.
- Removed `setRecoveredTrackFolder(uri)` from the store contract.

`AndroidTrackStorage.kt`:

- Removed SAF `DocumentsContract` recovery scanning.
- Added `Environment.isExternalStorageManager()` access check.
- `recoveredTrackFolder()` now returns `RecoveredTrackFolder()` only when All files access is granted.
- `recoveredGpxFiles()` now:
  - Returns empty when All files access is missing.
  - Directly scans `File(DirectTrackRecoveryLocation.AbsolutePath)`.
  - Filters regular files ending in `.gpx` case-insensitively.
  - Sorts by filename.
  - Maps recovered files to `TrackFileRef(fileName, file.toURI().toString(), canDelete = false)`.
- `readText(file)` now handles `file:` URIs via `File(uri.path).readText(Charsets.UTF_8)` and still handles content URIs through `ContentResolver.openInputStream`.
- MediaStore writes and current app-owned GPX listing were left unchanged.

`StrideMapRepository.kt`:

- `refreshSetup()` now records:
  - `recoveredTrackFolder = storage.recoveredTrackFolder()`
  - `hasAllFilesRecoveryAccess = storage.hasAllFilesRecoveryAccess()`
- Removed `setRecoveredTrackFolder(uri)`.
- Added `AppState.hasAllFilesRecoveryAccess`.
- All files access is not a Start blocker; it is only for recovery/listing old GPX files.

`MainActivity.kt`:

- Removed `OpenDocumentTree()` recovery launcher.
- Added Settings action that opens:
  - `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with `package:${context.packageName}`.
  - Falls back to `Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`.
- The `Recover existing tracks` row now says:
  - Granted: `Direct scan enabled: /storage/emulated/0/Documents/StrideMap/Tracks/*.gpx`
  - Missing: `Grant All files access to scan /storage/emulated/0/Documents/StrideMap/Tracks/*.gpx`
- `MainActivity.onResume()` now refreshes setup and triggers `scanTracks(false)` when All files access transitions from missing to granted.

Documentation:

- `agentic/knowledge/features/init-creating-all-defined-components.md` was updated to record that the normal write target remains MediaStore, but user explicitly approved `MANAGE_EXTERNAL_STORAGE` as a Settings-only recovery path after Samsung SAF folder selection failed.

### Verification

Targeted storage contract test:

```bash
./gradlew :app:testDebugUnitTest --tests com.example.stridemap.storage.PublicTrackStorageLocationTest --no-daemon
```

Result: passed.

Full unit suite:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

Result: passed.

Debug build:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Result: passed.

Device install and launch:

```bash
./gradlew :app:installDebug --no-daemon -Pandroid.injected.device.serial=<SGS23>
adb -s <SGS23> shell am start -W -n com.example.stridemap/.MainActivity
```

Result:

- Installed on Samsung Galaxy S23.
- App launched successfully.
- `Status: ok`
- Cold launch.

### Final user validation

The user enabled All files access from the new recovery path and confirmed:

> Okay that worked, nice.

This confirms the direct All files access recovery path solved the real-device inability to rediscover existing `.gpx` files under `Documents/StrideMap/Tracks` after app data clear.

## Part 5 — Map and main-tab UI polish

### One-line Map overlay

The user asked to shorten the Map track overlay so it shows only movement type, date/time, distance, and tracked time, without the custom message.

TDD was followed:

- Added `CoreDomainTest.mapOverlaySummaryUsesOneLineEssentialsWithoutMessage()` first.
- The test expected `MapOverlayText.summary(...)` to produce `Walk • 2026-06-08 15:30 • 1.23 km • 00:12:34` for a track with a long custom message.
- The test failed red because `MapOverlayText` did not exist.

Implementation:

- Added `app/src/main/java/com/example/stridemap/core/MapOverlayText.kt`.
- `MapOverlayText.summary(...)` formats movement label, `yyyy-MM-dd HH:mm`, distance, and duration as one line.
- The formatter intentionally does not read or display `track.message`.
- `TrackInfoCard` in `MainActivity.kt` now renders the one-line summary with ellipsis.
- The overlay then gained a leading movement-type icon using the existing `movementIcon(...)` and `movementTypeColor(...)` helpers.

Validation:

- Targeted core test passed.
- Full unit suite passed.
- Debug assemble passed.
- Installed successfully on SGS23.

### Main-tab headers and Settings placement

The user asked to remove the headers in the three main tabs, keep Settings reachable, give the icon a visible background, and replace the star-like settings icon with a conventional gear.

First implementation:

- Removed the main-tab `TopAppBar` from the root `Scaffold`.
- Added `SettingsActionButton`, a circular elevated Material `Surface` with a 48dp `IconButton`.
- Replaced `SettingsGearIcon` with a conventional stroke gear outline.
- Initially overlaid the button top-right across all tabs.
- Moved Map live/follow FABs down to avoid that first top-right Settings overlay.
- Tests/build passed and the app was installed on SGS23.

The user then reported the shared floating gear overlapped Capture intro text and Tracks filters. The user wanted Settings only on Capture, the Map info overlay at the top, and Tracks refresh to be automatic instead of a visible refresh FAB.

Second implementation:

- Removed the root shared Settings overlay.
- Passed `onOpenSettings` only into `CaptureScreen`.
- Placed `SettingsActionButton` inline with the Capture intro text in a top `Row`, so it participates in layout and does not overlap content.
- Removed the Tracks refresh FAB.
- Added `LaunchedEffect(selectedTab)` to call `StrideMapRepository.scanTracks(false)` when the Tracks tab becomes selected.
- Moved `TrackInfoCard` to the top center of Map with `statusBarsPadding()` and side/top gaps.
- Moved the Map live/follow FAB to bottom-right.

Validation:

- Full unit suite passed.
- Debug assemble passed.
- Installed successfully on SGS23.
- User confirmed this was “everything as wanted.”

### Removing osmdroid zoom buttons

The user noticed osmdroid showed plus/minus zoom buttons when the map was moved and said pinch zoom was enough.

Implementation:

- In `OsmRouteMap`, kept `setMultiTouchControls(true)` for pinch gestures.
- Added `setBuiltInZoomControls(false)` on the remembered `MapView` to hide the on-map `+`/`-` controls.

Validation:

- `./gradlew :app:assembleDebug --no-daemon` passed.
- Build emitted the expected deprecation warning for `setBuiltInZoomControls(false)` and the existing `LocalLifecycleOwner` warning.
- `./gradlew :app:installDebug --no-daemon` installed successfully on SGS23.

## Current behavior after session

- Fresh GPS provider polling defaults are 1 second for all movement types.
- Settings still preserves separate GPS polling values per movement type.
- Existing SharedPreferences values are not migrated; app data clear or manual Settings updates are needed on dev installs that saved old defaults.
- Tracks rescan still includes MediaStore GPX files from the app-owned public folder path.
- If All files access is granted, Tracks rescan additionally direct-scans `/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx` and lists those GPX files as read-only recovered tracks.
- Normal capture/export still writes through MediaStore under `Documents/StrideMap/Tracks/`.
- `MANAGE_EXTERNAL_STORAGE` is now declared and used only for the explicitly approved recovery flow.
- All files access is not part of Start readiness and does not block capture.
- Main tabs no longer have top headers.
- Settings appears only on Capture, inline with the intro text, using a circular elevated gear button.
- Tracks auto-refreshes when the Tracks tab is selected; there is no Tracks refresh FAB.
- Map shows a one-line top overlay with movement icon, movement type, date/time, distance, and tracked time; custom messages are not shown there.
- Map live/follow action is bottom-right.
- osmdroid pinch zoom remains enabled, but the built-in plus/minus zoom controls are hidden.

## Files changed during the session

Polling defaults:

- `app/src/main/java/com/example/stridemap/location/LocationProvider.kt`
- `app/src/test/java/com/example/stridemap/location/LocationRequestSpecTest.kt`
- `app/src/test/java/com/example/stridemap/AppStateTest.kt`
- `agentic/knowledge/features/init-creating-all-defined-components.md`

Track recovery / All files access:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/stridemap/MainActivity.kt`
- `app/src/main/java/com/example/stridemap/StrideMapRepository.kt`
- `app/src/main/java/com/example/stridemap/storage/AndroidTrackStorage.kt`
- `app/src/main/java/com/example/stridemap/storage/StorageContracts.kt`
- `app/src/test/java/com/example/stridemap/storage/PublicTrackStorageLocationTest.kt`
- `agentic/knowledge/features/init-creating-all-defined-components.md`

Map/main-tab UI polish:

- `app/src/main/java/com/example/stridemap/MainActivity.kt`
- `app/src/main/java/com/example/stridemap/core/MapOverlayText.kt`
- `app/src/test/java/com/example/stridemap/core/CoreDomainTest.kt`

## Notes for future agents

- Do not change saved-point sampling when adjusting provider polling; they are separate concepts.
- MediaStore remains the canonical write path for new GPX captures.
- The All files access path exists because Samsung's SAF picker failed to grant/select the intended folder on the real device.
- Recovered direct-scan files should remain read-only inside StrideMap unless the product explicitly approves delete/edit behavior.
- `MANAGE_EXTERNAL_STORAGE` is a policy-sensitive permission; this was explicitly accepted for a personal/dev app recovery flow, not as a general storage strategy.
- Keep the Map overlay short and route-focused; do not re-add custom track messages there unless explicitly requested.
- Keep pinch zoom enabled even though built-in osmdroid zoom buttons are hidden.
- The repository was observed as entirely untracked during this session, so normal `git diff` was not useful for viewing modifications.
