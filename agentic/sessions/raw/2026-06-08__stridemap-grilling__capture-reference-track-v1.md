# Raw session log — Capture reference track v1 grilling

Date: 2026-06-08

## Session purpose

Continue the first StrideMap “grill me” session, shifting the first implementation target from synthetic GPS mocking toward a real-data capture app.

The user proposed a different approach: build a first app version that captures real reference tracks on a device, saves them as accessible files, and uses those real-world tracks as the basis for later development and evaluation.

## Research checkpoint before pivot

Before the capture-mode pivot, external Android GPS development research confirmed this layered approach:

- Use deterministic internal data for fast domain/unit tests.
- Use Android Emulator location playback / `geo` commands / GPX/KML routes for emulator E2E wiring.
- Use real-device validation for production GPS reliability.
- Do not treat emulator or mock-location success as proof of real GPS/background/battery behavior.

The user pushed back that class-name architecture was too abstract. The practical daily AI development loop should be:

```text
Gradle build -> install/run on emulator -> feed emulator GPS -> observe app/logs/storage/UI -> fix with AI
```

Then the user proposed capturing real reference tracks as v1.

## V1 product direction

First version of StrideMap should be a real GPS reference-track capture app.

Purpose:

- Capture real-world GPS tracks.
- Save each track as a phone-accessible GPX file.
- Display stored tracks in a list and on a map.
- Use the generated real-world data to drive the second version of the app.

## Navigation and screens

Resolved navigation:

- Use **bottom navigation** for v1.
- Tabs: **Capture / List / Map**.
- The user originally mentioned a burger/drawer because future entries like Settings or Info may exist, but decided to use bottom navigation now and switch later if needed.

Screens:

1. **Capture screen**
   - Start/stop controls.
   - Optional message text field.
   - Movement type dropdown.
   - Live distance and duration while recording.
   - Current GPS accuracy status.
   - Setup checklist if required permissions/access are missing.

2. **List screen**
   - Lists locally available captured tracks.
   - Compact rows.
   - Row content: message, human-readable date, short kilometer distance, and movement type chip.
   - Tapping a row opens/switches to Map and displays that track.
   - Filter by movement type.
   - Sort controls: sort field `date` or `distance`, plus ascending/descending direction toggle.
   - Newest first is the default date sort.
   - Manual refresh button.
   - Malformed GPX files should appear as error rows, not be silently ignored.
   - No delete action in v1.
   - No metadata edit action in v1.

3. **Map screen**
   - Uses OpenStreetMap via **osmdroid**.
   - If no track is selected and no live track exists, show a placeholder.
   - If a track is selected from List, show it.
   - If a live track exists, it can be selected/displayed.
   - Draw selected track as line plus start/end markers.
   - Auto-zoom to selected track bounds.
   - During live capture, follow latest point automatically.
   - If user pans during live capture, auto-follow pauses.
   - Provide a follow button to resume following.

## Capture inputs

Message:

- Optional free-text message.
- If no message is provided, it may be empty.
- Used in metadata and partly in filename.

Movement type:

- Required before Start is enabled.
- Input component: Material exposed dropdown.
- Allowed values:
  - walk
  - run
  - bike
  - car
  - train

UI components:

- Use **Material 3 Compose** standard Android components.

## Capture lifecycle

Only one live track:

- At most one track can be in `live` state.
- If a live track exists, Start is disabled.

Track states:

- `live`
- `stopped`
- `interrupted`

Start:

- Requires movement type.
- Requires precise/background location permission.
- Requires notification permission for foreground service.
- Requires storage access to `Documents/StrideMap/Tracks`.
- If requirements are missing, Start is disabled and the Capture screen shows a checklist.

Recording:

- Use high accuracy GPS.
- Capture GPS point every **7 seconds**.
- Rationale: at walking speed around 5 km/h, 7 seconds is roughly 10 meters.
- Append each captured point live to the GPX/draft file.
- Live map updates when each saved point is appended.
- Background/screen sleep should continue recording via foreground service notification.
- Foreground notification text should be simple: “StrideMap capturing track” or equivalent.

Stop:

- Stop requires confirmation dialog.
- Dialog should include distance, duration, and filename.
- If user cancels, capture continues unchanged.
- If user confirms, mark GPX state as `stopped` and keep the completed track selected on Map.
- If user stops before any GPS point arrives, discard the empty track/file.

App kill/restart:

- The user clarified: do **not** start a new track automatically after app kill/restart.
- If the app restarts and finds a previously `live` track, mark it `interrupted`.
- Preserve the interrupted track as-is.
- User must configure capture and press Start again to begin another track.
- If Android offers a graceful shutdown/kill hook, use it to save/finalize the live track state as well as possible.

## Storage and file format

File format:

- Save tracks as **GPX**.
- Store message, movement type, distance, duration, state, and other app metadata in GPX metadata/extensions.
- Store per-point accuracy in GPX extensions.
- Store per-point speed in GPX extensions if Android provides it.
- Poor-accuracy points are still saved with metadata.

Storage location:

- Save files somewhere accessible to the phone user without root.
- Resolved target: **Documents/StrideMap/Tracks**.
- Use Android-accessible storage mechanisms appropriate for modern Android, likely MediaStore/SAF.

Filename:

- Timestamp + movement type + first 16 characters of message.
- Message part must be sanitized.
- Sanitization direction: lowercase, safe characters/dashes, max 16 characters.
- Example shape: `2026-06-08_1530_walk_walk-to-xyz.gpx`.

Track scanning:

- On startup, scan `Documents/StrideMap/Tracks` for existing GPX files.
- List screen also has manual refresh.

Distance:

- Cache computed distance in GPX metadata for fast list display.
- Recompute if metadata is missing or stale when needed.

## GPS quality handling

Accuracy display:

- Capture screen shows current GPS accuracy.
- Warn when accuracy is worse than **25 meters**.

Poor accuracy:

- Still save points even if accuracy is poor.
- Preserve accuracy metadata for later evaluation.

## Permissions and setup

Before Start is enabled, v1 requires:

- Precise location.
- Background location.
- Notification permission for foreground service.
- Storage access to the GPX target folder.
- Selected movement type.

UX:

- Show a setup checklist on the Capture screen with status for location/background/notification/storage requirements.
- Disable Start until all required items are satisfied.

## Deferred / non-goals for v1

- No delete track action.
- No edit message/type metadata after capture.
- No visible Info screen; defer until later drawer/settings work.
- No synthetic simulator as the primary v1 product surface.
- No remote sync/API/PostgreSQL work.
- No claim that emulator/mock behavior proves production GPS reliability.

## Open implementation concerns

- Exact Android storage API for `Documents/StrideMap/Tracks` on Android 16/API 36.
- Exact GPX extension schema for app metadata, track state, accuracy, speed, distance, duration, and message.
- Exact foreground service lifecycle and how to mark `live` tracks as `interrupted` robustly on startup.
- osmdroid setup, permissions, tile cache behavior, and attribution requirements.
- How to automate the emulator GPS development loop after v1 capture basics exist.
