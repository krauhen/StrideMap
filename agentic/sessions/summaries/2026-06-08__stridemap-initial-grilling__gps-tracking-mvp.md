# Session summary — StrideMap initial GPS tracking MVP grilling

Date: 2026-06-08

## Context loaded

- Read `AGENTS.md`, `agentic/README.md`, and all `agentic/guidance/*.md` files before writing this summary.
- Existing repo guidance needed to be adapted for an Android GPS app.
- For this documentation-only task, followed the session-note convention under `agentic/sessions/raw/` and `agentic/sessions/summaries/`.

## Product direction

- Working title: **StrideMap**.
- Purpose: personal GPS/location history for the user.
- Name vibe: fitness/outdoors + life map.
- Not currently scoped as fleet tracking, live safety sharing, or social tracking.

## Tracking decisions

- Tracking mode: **adaptive stationary mode**.
- Accuracy/battery posture: **balanced route shape**.
- Sampling model: **distance-targeted sampling** rather than fixed time polling.
- Target stored-point spacing: **10 meters**.
- Minimum stored-point interval: **1 second**.
- Stationary check interval: **1 minute**.
- Kalman-style filtering was discussed as a way to smooth/predict motion state; it should not be treated as a magic battery-saving guarantee.

## Android decisions

- Use a persistent foreground-service notification for reliable background tracking.
- Require precise background location permission for core continuous tracking.
- If precise background location is missing, the app should explain/block continuous tracking rather than silently degrade.

## Development critical path

- First target: Google Pixel 9 emulator in Android Studio.
- Target platform: Android 16 / API 36.
- After successful emulator build/core behavior, move to Samsung Galaxy S23, also Android 16 / API 36.
- Emulator proves build, algorithm, mock routes, and local storage behavior.
- Real S23 testing is required for actual GPS, background reliability, and battery behavior.

## Mock/simulator decision

- User wants a high-quality mock that behaves like a real GPS module.
- Pushback recorded: emulator/mock cannot be guaranteed identical to real GPS because real devices add sensor fusion, chipset quirks, multipath, throttling, and OEM battery policy.
- Resolved direction: **simulator + real validation**.
- Build deterministic simulator/mock routes for development, then validate on Samsung Galaxy S23.

## Storage/sync direction

- Local-first storage: SQLite or similar.
- Store coordinates plus metadata such as timestamp.
- Remote PostgreSQL sync is desired later.
- Sync/API/Postgres architecture is deferred until local tracking works reliably.

## Open decisions

- Exact Android location stack: Fused Location Provider, `LocationManager`, or provider abstraction.
- Mock injection architecture: internal provider abstraction, Android mock-location pipeline, GPX/KML playback, or a combined approach.
- Adaptive algorithm details: raw speed vs filtered speed vs distance/time window vs Kalman-estimated state.
- Meaning of “last X covered distance”: number of points, meters, time window, or uncertainty window.
- Initial database schema and metadata fields.
- Whether to build an in-app debug screen for route simulation and DB inspection.
- Battery validation plan and success thresholds for Samsung Galaxy S23.
- Remote sync API/auth/batching/conflict strategy.

## Recommended next step

Resolve mock architecture first. Recommended direction: create an internal `LocationProvider` abstraction for deterministic simulator tests/debug mode, and separately support Android mock locations or emulator route playback for end-to-end validation.
