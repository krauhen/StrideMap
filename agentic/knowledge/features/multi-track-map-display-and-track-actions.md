# Feature — Multi-track Map display and Tracks actions

Status: implemented in current code; structured manual/device validation still pending unless separately documented. This file supersedes earlier draft/grill wording where conflicts existed.

Topic: multi-track Map display controlled by Tracks long-press Actions, read-only short-tap Preview, permanent track delete, and post-capture message edit/rename.

Source prompt: user request and grill-me decisions on 2026-06-09.

## Purpose

Earlier v1 used a single selected-track model: short-tapping a Tracks row selected that track and navigated to Map, and Map displayed `selectedTrack ?: liveTrack`.

This feature separates track Preview from Map display:

- Short press/tap on a track row opens read-only Preview metadata.
- Long press on a track row opens Actions.
- `Display` is a selected/deselected toggle in Actions only.
- Selected `Display` means the track is shown on Map; deselected means it is not shown on Map.
- Map can render multiple Display-selected tracks at once.
- Live tracks are not shown automatically; live appears on Map only while its `Display` toggle is selected.
- Delete and Edit message are non-live-only Actions.

## Terminology

- `Preview`: short press/tap on a track row. Opens a read-only metadata bottom sheet. Preview does not include actions and never changes Map display.
- `Actions`: long press on a track row. Opens a compact popup menu.
- `Display`: selected/deselected toggle in Actions. Selected = shown on Map. Deselected = not shown on Map.
- `Edit message`: non-live Action that edits the track message, updates GPX metadata, and renames the GPX file.
- `Delete`: non-live Action that permanently deletes the track after confirmation.

## Spec integration

This implemented feature changed these original v1 rules:

- Supersedes the original `No delete track action in v1` non-goal for eligible non-live tracks.
- Supersedes the original `No metadata editing after capture in v1` non-goal for message-only edits on eligible non-live tracks.
- Supersedes Tracks short-tap behavior where tapping a row selects the track and navigates to Map.
- Supersedes Map's single `selectedTrack ?: liveTrack` display model with session-only displayed-track state plus separate Preview state.

This feature does **not** supersede route lifecycle immutability: editing a message must not reopen capture or alter points, movement type, timestamps, distance, duration, route geometry, or track state.

## Current implementation facts

- `agentic/knowledge/SPEC.md` is the canonical current app spec and now records this implemented behavior.
- `agentic/knowledge/features/init-creating-all-defined-components.md` records the original v1 feature source and says delete/edit are non-goals for the original v1.
- `app/src/main/java/com/example/stridemap/MainActivity.kt` renders Tracks and Map UI.
- `TrackListScreen` renders Tracks rows with short-tap Preview and long-press Actions.
- `TrackMapScreen` renders `appState.displayedTracks`.
- `AppState.displayedTrackIds` is session-only state in `StrideMapRepository.kt`; live tracks are not displayed automatically.
- `Track.message` already exists in `core/TrackModels.kt` and is written/read through `gpx/GpxCodec.kt` metadata.
- User-facing delete exists for eligible non-live valid tracks and for malformed rows when storage identity allows safe delete.
- Post-capture message edit exists for eligible non-live valid tracks and rewrites GPX metadata with filename rename.
- Recovered GPX files discovered through the Settings-only All-files-access recovery path support edit/delete when the app has the required All-files-access capability and a safe direct-file write/delete path; otherwise the operation fails non-destructively.

## Implementation summary

- Tracks short press opens read-only Preview for valid and malformed rows without changing Map display.
- Tracks long press opens Actions with `Display`, `Edit message`, and `Delete` according to row eligibility.
- Display state is a session-only set of track ids; deleting tracks cleans it up, and editing/renaming tracks migrates it.
- Map renders all valid Display-selected tracks, supports per-session saved-track colors, a distinct live style, route/marker info, empty displayed-state hint, and live follow only for Display-selected live tracks.
- Edit message preserves route geometry, points, movement type, timestamps, duration, distance, and track state while updating message metadata and the GPX filename.
- Delete is permanent, confirmed, and non-fatal on failure.

## Validation status

- AppState JVM tests cover explicit displayed tracks, multiple saved displayed tracks, live-not-automatic display behavior, malformed/missing-id filtering, rename display-state migration, and delete display-state cleanup.
- Existing GPX/parser tests cover malformed-row safety and metadata round trips that support this feature.
- Structured manual validation is still pending for Preview/Actions UI, multi-track Map interactions, route info card, edit/rename/rescan behavior, delete behavior, malformed delete, and recovered direct-file edit/delete with All files access.

## Decided requirements

### Tracks interactions

1. Short press/tap opens Preview.
2. Preview is a read-only modal bottom sheet.
3. Preview does not include Actions.
4. Preview does not change Map display.
5. Preview shows all available metadata from parsed track and storage records, including:
   - message or fallback title;
   - movement type;
   - track state;
   - start/end date-time;
   - duration/captured time;
   - distance;
   - point count when available;
   - filename, path, URI, or storage reference where available;
   - parse/error details for malformed rows.
6. Long press opens Actions as a compact popup menu.
7. Actions contain `Display`, `Edit message`, and `Delete` when each action is eligible.
8. `Display` is toggled in Actions only. It is not directly toggleable from the row or Preview.
9. `Display` is available for live, stopped, and interrupted tracks.
10. `Edit message` and `Delete` are available only for non-live tracks.

### Display state

1. Displayed-track selection is session-only and is not restored after app restart.
2. Display state is separate from Preview state.
3. Opening Preview never changes displayed-track state.
4. Deleting a track removes it from displayed-track state on success.
5. Live track display is explicit: active live tracks appear on Map only while their `Display` toggle is selected.

### Map behavior

1. Map renders all Display-selected tracks.
2. Map remains visible when no tracks are displayed.
3. If no tracks are displayed, Map shows a hint telling the user to select `Display` from Tracks.
4. Map auto-fits all currently displayed tracks when entering the Map tab.
5. While already staying on Map, later Display changes do not automatically refit or yank the viewport.
6. There is no manual `Fit displayed` button; leaving and re-entering Map is the refit path.
7. Map does not add a displayed-tracks legend/list overlay.
8. Existing saved-point-dot setting applies to all displayed tracks.
9. There is no hard limit on the number of displayed tracks.
10. If many displayed tracks hurt Map performance or readability, show a non-blocking warning; do not auto-hide tracks or block selection. The implementation may choose the trigger based on measured rendering/readability behavior rather than a fixed product cap.

### Map route styling and route info

1. Displayed saved tracks get unique per-track route colors.
2. Unique saved-track colors remain stable for each track during the app session.
3. Live route uses a special live color/style distinct from the saved-track palette.
4. Route info card shows a color swatch matching the route color/style.
5. Tapping a displayed route or marker shows a small route info surface for that track.
6. Route info appears as a small bottom card above bottom navigation.
7. Route info contains the current compact Map metadata style, shown only on click:
   - movement type;
   - date/time;
   - distance;
   - captured duration/time.
8. Route info dismisses on the next route/map tap or explicit close. It does not auto-hide on a timer.

### Live follow

1. Live follow remains available only when the live track is Display-selected.
2. When live follow is on, the viewport follows the live track only; saved displayed tracks may be offscreen.
3. Manual pan/zoom pauses live follow until the user resumes it, preserving existing behavior.

### Delete

1. Delete is permanent; no trash/archive system is added.
2. Delete uses a standard confirmation dialog with clear permanent-delete wording.
3. On successful delete:
   - remove the track from displayed-track state;
   - close related Preview/Actions/edit surfaces;
   - remove the track from the Tracks list UI immediately;
   - rescan/reconcile storage as needed afterward.
4. On delete failure, show a non-fatal snackbar and keep the track row visible.

### Edit message and rename

1. `Edit message` opens a dialog text field with Cancel/Save.
2. A successful edit updates GPX message metadata.
3. A successful edit also renames the GPX file so the filename message slug aligns with the new message.
4. Empty edited messages are allowed.
5. If message is empty, rows/Preview use a date/movement fallback title and filename rename should use the app's safe empty-message fallback naming convention.
6. After successful edit, close edit/action UI and return to Tracks with the updated row visible.
7. On edit failure, show a non-fatal snackbar and keep the track row visible.

### Recovered and malformed tracks

1. Recovered direct-scan GPX files support edit/delete when All-files-access capability and a safe direct-file contract are available.
2. If recovered-file edit/delete cannot be performed safely, fail non-destructively with a snackbar and keep the row visible.
3. Malformed GPX rows support Preview.
4. Malformed GPX rows support Delete only when the app has reliable storage identity for the underlying GPX file.
5. Malformed GPX rows do not support Display or Edit message.

## Non-goals

- Editing movement type, timestamps, points, distance, duration, route geometry, or track state.
- Reopening stopped/interrupted captures.
- Bulk delete.
- Delete all.
- Trash/archive recovery.
- Remote sync/API behavior.
- Sharing/export changes.
- Changing live capture behavior, sampling, foreground service behavior, or permissions.
- Changing bottom navigation destinations.
- Adding a Map displayed-tracks legend/list overlay.
- Adding a manual Map fit button.
- Persisting Display-selected tracks across app restart.

## Implemented state model

- The single selected-track-driven Map display model is replaced by a session-only set of Display-selected track identities.
- Preview state remains separate for the currently previewed row.
- Preview state must not mutate Display-selected track identities.
- Track identity must remain stable enough for session color assignment, Display toggling, delete cleanup, and edit/rename reconciliation.
- Delete/edit operations must clear or update stale state for renamed/deleted tracks.

## Implemented storage/write behavior

- MediaStore-backed tracks support user-facing delete, metadata rewrite, and rename operations while preserving the existing safety posture around valid GPX snapshots.
- Recovered direct-scan tracks use a safe direct-file write/delete contract before edit/delete can succeed.
- Message edits must preserve every non-message GPX field unless the rename operation requires storage identity changes.
- Rename must handle both MediaStore row rename and direct-file rename.
- Any edit/delete/rename failure must be non-destructive and user-visible through snackbar feedback.

## Implemented artifact map

- `MainActivity.kt`
  - Tracks row gesture handling.
  - Actions popup menu UI.
  - Preview bottom sheet UI.
  - Edit-message dialog UI.
  - Delete confirmation dialog.
  - Map multi-track rendering.
  - Route-tap bottom card.
  - Empty-map hint.
  - Live-follow visibility and pause/resume behavior.
- `StrideMapRepository.kt`
  - Session-only displayed-track state.
  - Separate Preview state.
  - Methods for toggling Display, deleting tracks, and editing/renaming messages.
  - Cleanup of displayed/preview/edit/action state after delete/edit.
- `core/TrackModels.kt`
  - Existing track identity/message fields support display toggles and edit/rename reconciliation.
- `gpx/GpxCodec.kt`
  - Message metadata rewrite without losing route/state metadata.
  - Preserve malformed-file safety.
- `storage/AndroidTrackStorage.kt`
  - User-facing delete.
  - Metadata rewrite/update.
  - MediaStore rename.
  - Direct-file edit/delete/rename for recovered tracks when safely available.
- Tests
  - AppState tests cover displayed-track sets, explicit live Display behavior, malformed/missing-id filtering, rename migration, and delete cleanup.
  - Existing GPX parser/writer tests cover metadata round trips and malformed-file safety that support this feature.
  - Structured manual validation remains pending for long-press Actions, Preview, edit dialog, delete confirmation, multi-track Map, route info card, recovered direct-file edit/delete, and live follow.

## Requirement-to-verification matrix

| Requirement | Verification |
| --- | --- |
| Short tap opens read-only Preview and does not affect Map display | Manual UI validation; repository state test if Preview state is modeled |
| Long press opens compact Actions popup | Manual UI validation |
| Display toggle is Actions-only | Manual UI validation; no row/Preview Display toggle |
| Display toggle adds/removes tracks from Map | Repository state test plus manual Map validation |
| Live track appears on Map only when Display-selected | Repository/UI state test plus active-capture manual validation |
| Multiple tracks can display on Map | Repository test for displayed-track set; manual Map validation with at least two tracks |
| Map fits displayed tracks when entering Map | Manual Map validation with multiple tracks |
| Display changes while already on Map do not auto-refit | Manual Map validation |
| No Map legend/list and no Fit displayed button | Manual UI validation |
| Empty Map shows hint | Manual UI validation with no Display-selected tracks |
| Tapping route/marker shows bottom route info card | Manual Map validation |
| Route info card includes compact metadata and color swatch | Manual Map validation |
| Route info dismisses on next route/map tap or close | Manual Map validation |
| Saved-point-dot setting applies to all displayed tracks | Manual/settings validation |
| Unique saved-track colors are stable per session | Manual Map validation after toggling/re-entering Map |
| Live route uses special style and live follow appears only when live is Display-selected | Active-capture manual validation |
| Manual pan/zoom pauses live follow | Active-capture manual validation |
| Delete permanently removes eligible non-live track after confirmation | Storage/repository test where feasible; manual rescan validation |
| Delete removes displayed track from Map and Tracks UI immediately | Repository/UI validation |
| Edit message updates GPX metadata and renames GPX file | GPX/storage test where feasible; manual row/Preview/rescan validation |
| Empty edited message uses fallback title/name | Unit/manual validation |
| Recovered direct-scan tracks can edit/delete when access allows | Manual validation with recovered GPX and All-files access |
| Malformed rows allow Preview and safe Delete only | Manual/unit validation with malformed GPX fixture |
| Edit/delete failure is non-destructive | Simulated/manual failure keeps row visible and shows snackbar |
| Many displayed tracks do not get auto-hidden/blocked | Manual validation; warning behavior if implementation detects degraded performance/readability |

## Decision log

- 2026-06-09: Feature request captured: multiple Map tracks, long-press Actions, Delete, Edit message, Display toggle, and short-tap Preview.
- 2026-06-09: Terminology locked: `Preview` = short-press read-only metadata bottom sheet; `Actions` = long-press compact popup menu; `Display` = selected/deselected toggle.
- 2026-06-09: Display applies to live, stopped, and interrupted tracks.
- 2026-06-09: Delete/Edit message are non-live-only.
- 2026-06-09: Display is toggled in Actions only, not on the row and not in Preview.
- 2026-06-09: Display-selected tracks are session-only and not restored after app restart.
- 2026-06-09: Preview shows all available metadata, including storage/file metadata where available.
- 2026-06-09: Map behavior locked: fit on entering Map; no auto-refit while already on Map; no Fit button; no displayed-tracks legend/list.
- 2026-06-09: Empty Map shows map plus hint to select `Display` from Tracks.
- 2026-06-09: Route tap shows compact bottom route info card with movement type, date/time, distance, captured duration/time, and matching color swatch.
- 2026-06-09: Route info dismisses on next route/map tap or explicit close; no timed auto-hide.
- 2026-06-09: Existing saved-point-dot setting applies to all displayed tracks.
- 2026-06-09: No hard displayed-track limit; warn non-blockingly if many tracks degrade Map performance/readability.
- 2026-06-09: Saved tracks use unique stable per-session route colors; live uses a special live color/style.
- 2026-06-09: Live follow exists only when live is Display-selected; when following live, viewport follows live only; manual pan/zoom pauses follow until resumed.
- 2026-06-09: Delete is permanent after standard confirmation; successful delete clears display state and removes the row immediately.
- 2026-06-09: Edit message uses a dialog text field with Cancel/Save, allows empty messages, updates GPX metadata, renames the GPX file, and returns to Tracks after success.
- 2026-06-09: Recovered direct-scan GPX files support edit/delete when a safe All-files-access direct-file contract is available.
- 2026-06-09: Malformed rows support Preview and safe Delete only; no Display or Edit message.
- 2026-06-09: Edit/delete failures show a snackbar and keep the row visible.
