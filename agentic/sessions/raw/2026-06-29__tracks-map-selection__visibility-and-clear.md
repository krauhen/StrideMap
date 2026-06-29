# Raw session log — Tracks map-selection visibility and clear action

Date: 2026-06-29

## Session purpose

Improve the Tracks list so tracks currently displayed on the Map are harder to overlook, and add an easy way to clear all displayed-map selections without manually finding and hiding each selected track.

User request:

> I need a better visualization in the tracks list which tracks are currently displayed in the map, now it is likely to overseen.
> Also there should be a way to easily clear this selection which are displayed.
> Now i have to search all and disable them manually.
>
> Plan, and then implement with careful, minimal increment.

Follow-up request after implementation:

> Install then this to my SGS23 via adb.

## Starting context loaded

Mandatory project context was loaded from:

- `agentic/README.md`
- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`
- `agentic/knowledge/features/init-creating-all-defined-components.md`

Important constraints preserved:

- Minimal focused UI/state increment only.
- No new dependencies.
- No storage, tracking, GPX, foreground-service, permission, sampling, or navigation model changes.
- Existing long-press track action menu behavior should remain available.

## Codebase findings

Relevant implementation files:

- `app/src/main/java/com/example/stridemap/MainActivity.kt`
  - `TrackListScreen(...)` renders filters, sorted valid track rows, malformed rows, preview/action/delete/edit sheets/dialogs.
  - `TrackRow(...)` already accepted `displayed: Boolean`, used a subtle `primaryContainer`, and overline text `Displayed`.
  - `TrackActionsMenu(...)` already exposed long-press `Display on Map` / `Hide from Map` through `StrideMapRepository.toggleDisplayed(track)`.
  - `TrackMapScreen(...)` renders `appState.displayedTracks` and live track only when its id is in `displayedTrackIds`.
- `app/src/main/java/com/example/stridemap/StrideMapRepository.kt`
  - `AppState.displayedTrackIds` stores current map display selection.
  - `AppState.displayedTracks` derives valid selected tracks from `displayEntries`.
  - `scanTracks(...)`, rename, and delete paths already prune/migrate `displayedTrackIds`.
- `app/src/test/java/com/example/stridemap/AppStateTest.kt`
  - Existing tests covered explicit displayed tracks, live not automatic, malformed/missing ids ignored, rename migration, and delete cleanup.

## Plan used

1. Make displayed tracks visually louder in the Tracks list.
2. Add a one-tap “clear displayed tracks” control above the list.
3. Keep the existing long-press Display/Hide behavior unchanged.
4. Verify with unit tests/build and no dependency or tracking/storage changes.

## Implementation

Changed `app/src/main/java/com/example/stridemap/StrideMapRepository.kt`:

- Added `StrideMapRepository.clearDisplayedTracks()`.
  - No-ops when `displayedTrackIds` is already empty.
  - Otherwise clears selection and sets transient message `Cleared map display`.
- Added pure state helper `internal fun AppState.clearDisplayedTracks(): AppState = copy(displayedTrackIds = emptySet())`.

Changed `app/src/main/java/com/example/stridemap/MainActivity.kt`:

- Imported `androidx.compose.foundation.BorderStroke`.
- In `TrackListScreen(...)`, computed `displayedCount = appState.displayedTracks.size`.
- Added `DisplayedTracksBanner(...)` between the filter/sort control row and the list/empty state when `displayedCount > 0`.
  - Banner copy: `N track(s) shown on Map`.
  - Banner uses `primaryContainer`, a primary border, the map icon, and a `Clear` text button.
  - `Clear` calls `StrideMapRepository.clearDisplayedTracks`.
- Strengthened `TrackRow(...)` visual selection state:
  - Displayed rows now get a `2.dp` primary border in addition to existing primary-container background.
  - Overline changed from `Displayed` to `Shown on Map`.
  - Trailing metadata now includes an `On Map` assist chip with the map icon when displayed.

Changed `app/src/test/java/com/example/stridemap/AppStateTest.kt`:

- Added `clearingDisplayedTracksRemovesOnlyMapSelection()`.
  - Verifies entries remain unchanged.
  - Verifies `displayedTrackIds` and derived `displayedTracks` become empty.

## Verification

Commands run:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Results:

- Unit tests passed.
- Debug assemble passed.
- A parallel Gradle invocation briefly produced noisy Kotlin compile errors while another build completed, but rerun/assemble completed successfully. Existing warnings remained:
  - `LocalLifecycleOwner` deprecation warning.
  - `setBuiltInZoomControls(false)` deprecation warning.

Device install/launch:

```bash
adb devices
./gradlew :app:installDebug --no-daemon -Pandroid.injected.device.serial=RFCW20LALNM
adb -s RFCW20LALNM shell am start -W -n app.stridemap.personal/com.example.stridemap.MainActivity
```

Results:

- Samsung Galaxy S23 was connected as serial `RFCW20LALNM` / model `SM-S911B`.
- Debug APK installed successfully.
- Initial launch command using the old package `com.example.stridemap/.MainActivity` failed because current `applicationId` is `app.stridemap.personal` while namespace/activity class remains `com.example.stridemap.MainActivity`.
- Resolved/used the correct component `app.stridemap.personal/com.example.stridemap.MainActivity`.
- Launch succeeded:
  - `Status: ok`
  - `LaunchState: COLD`
  - `Activity: app.stridemap.personal/com.example.stridemap.MainActivity`

## Files changed

- `app/src/main/java/com/example/stridemap/MainActivity.kt`
- `app/src/main/java/com/example/stridemap/StrideMapRepository.kt`
- `app/src/test/java/com/example/stridemap/AppStateTest.kt`

## Current behavior after session

- Tracks currently displayed on Map are now much more visible in the Tracks list.
- A visible banner appears whenever at least one valid track is displayed on Map.
- The banner provides one-tap clearing of all Map-displayed track selections.
- Existing per-row long-press Display/Hide behavior remains unchanged.
- No product/storage/tracking model changes were introduced.
