# Session summary — Capture reference track v1 grilling

Date: 2026-06-08

## Direction change

- User rejected an overly abstract mock/provider-first discussion.
- New v1 target: build a real GPS reference-track capture app first.
- Rationale: capture real-world GPX data on the user’s device, then use those tracks to drive later development/evaluation.
- Synthetic/internal data remains useful for tests, but the daily app-development loop should be grounded in emulator/device behavior and real captured tracks.

## V1 screens and navigation

- Use **Material 3 Compose**.
- Use bottom navigation with three tabs: **Capture / List / Map**.
- Future drawer/settings/info can come later; no visible Info screen in v1.

## Capture screen

- Inputs: optional message, required movement type dropdown.
- Movement types: `walk`, `run`, `bike`, `car`, `train`.
- Start disabled until movement type and required permissions/access are ready.
- Show setup checklist for precise/background location, notification permission, and storage access.
- While recording, show live distance, duration, and current GPS accuracy.
- Warn if GPS accuracy is worse than **25m**, but still save points.

## Capture behavior

- Use high accuracy GPS.
- Fixed capture interval: **7 seconds**.
- Append each point live to the GPX file/draft.
- Save per-point accuracy and speed, if available, in GPX extensions.
- Continue recording in background/screen sleep via foreground service notification.
- Notification text: simple, e.g. “StrideMap capturing track”.
- Only one live track can exist; disable Start if one exists.

## Track lifecycle

- Track states: `live`, `stopped`, `interrupted`.
- Stop requires confirmation dialog showing distance, duration, and filename.
- Cancelling Stop continues capture unchanged.
- Confirmed Stop marks the GPX state as `stopped` and keeps it selected on Map.
- If stopped before any GPS point arrives, discard the empty file.
- On app restart, any previously `live` track is marked `interrupted`; do not auto-resume and do not auto-start a new track.
- User must configure and press Start again for a new capture.

## Storage and GPX

- Save tracks as **GPX** under **Documents/StrideMap/Tracks** so the user can access them without root.
- Store message/type/state/distance/duration/app metadata in GPX metadata/extensions.
- Cache distance in GPX metadata for fast list display; recompute if missing.
- Filename: timestamp + movement type + sanitized first 16 characters of message.
- Scan GPX files on startup; List also has manual refresh.
- Malformed GPX files appear as error rows.

## List screen

- Compact rows: message, human-readable date, short kilometer distance, movement type chip.
- Tapping a row switches to Map and displays that track.
- Filter by movement type.
- Sort by date or distance, with ascending/descending direction toggle.
- Default sort: newest first.
- No delete and no metadata editing in v1.

## Map screen

- Use **OpenStreetMap via osmdroid**.
- Empty state: placeholder when no selected/live track exists.
- Draw selected track as line plus start/end markers.
- Auto-zoom to selected track bounds.
- During live capture, update on each saved 7-second point and follow latest point.
- If user pans during live capture, pause follow; provide a follow button to resume.

## Deferred/open

- Exact Android storage mechanism for Documents/StrideMap/Tracks on Android 16/API 36.
- Exact GPX extension schema.
- Foreground service implementation details and robust startup interruption marking.
- osmdroid setup/attribution/tile cache behavior.
- Automated emulator GPS feedback loop after v1 basics exist.
