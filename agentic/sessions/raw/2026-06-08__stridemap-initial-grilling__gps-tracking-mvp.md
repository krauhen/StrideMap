# Raw session log — StrideMap initial GPS tracking MVP grilling

Date: 2026-06-08

## Repository context loaded

The user asked to read `~/Repositories/Android/StrideMap/AGENTS.md` and reference files, then write a session log and summary.

Files read before writing this log:

- `AGENTS.md`
- `agentic/README.md`
- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`
- `agentic/guidance/ARCHITECTURE.md`
- `agentic/guidance/SETUP.md`
- `agentic/guidance/API.md`
- `agentic/guidance/ERROR.md`
- `agentic/guidance/KEYWORDS.md`
- `agentic/guidance/MCP.md`

Important context mismatch noted:

- The repository guidance needed to be adapted for an Android GPS app.
- The active user discussion is for an Android app named **StrideMap**.
- For this requested documentation-only task, the existing session-note conventions under `agentic/sessions/raw/` and `agentic/sessions/summaries/` were followed.

## Original app idea

The user wants to develop an Android application that tracks their GPS location continuously.

Initial assumptions from the user:

- Continuous GPS tracking may drain the smartphone battery.
- The app should use a polling interval that adapts to current movement speed.
- Example movement speeds:
  - Walking: ~4 km/h.
  - Running: ~10 km/h.
  - Biking: ~30 km/h.
  - Driving: ~100 km/h.
- Local development storage should use SQLite or a similar local database.
- Stored positions should include metadata such as timestamps.
- Later, once mature, the app should send location data to a remote PostgreSQL server reachable from everywhere.

The user explicitly asked: “Please grill me. Use the opencode question tool but iterative one question at a time.”

## Grilling decisions and answers

### Primary purpose

Question asked: what is the primary purpose of tracking location all the time?

Recommended answer given: personal activity/location history for yourself only.

User chose: **Personal history**.

Implication:

- The app is scoped as self-tracking / personal history rather than safety sharing, fleet tracking, fitness-only tracking, or research data collection.
- Privacy and compliance scope remains simpler than multi-user or employment tracking scenarios.

### Tracking strictness

Question asked: should the app do truly continuous 24/7 tracking, or pause/slow down aggressively when stationary?

Recommended answer given: adaptive with stationary mode.

User chose: **Adaptive stationary mode**.

Implication:

- Track continuously while moving.
- Slow down or pause GPS work when stationary.
- Use lower-power wake signals where possible rather than keeping GPS hot at all times.

### Accuracy versus battery

Question asked: should adaptive tracking prioritize exact route shape or battery minimization?

Recommended answer given: balanced, preserve meaningful route shape.

User chose: **Balanced route shape**.

Implication:

- The app should preserve useful route geometry without collecting unnecessary noisy points.
- Battery remains important, but not at the cost of a route history that becomes too coarse to be useful.

### Polling strategy / Kalman-style idea

The user clarified:

- “I want the application to increase the polling intervall in relation to the last X covered distance.”
- “Like a kallman filter approach.”

Question asked: does this mean the app should target roughly constant distance between saved points instead of constant time between GPS polls?

Recommended answer given: yes, distance-targeted sampling, with Kalman-style smoothing/prediction used to estimate motion state rather than directly being the battery-saving mechanism.

User chose: **Distance-targeted sampling**.

Implication:

- The core sampler should aim for one useful stored point every N meters.
- Faster motion implies shorter time intervals.
- Slower motion implies longer time intervals.
- Kalman-style filtering may smooth noisy positions and infer speed/heading/uncertainty, but interval decisions should still be bounded by practical min/max caps.

### Target point spacing

Question asked: what should the target spacing between stored points be for normal movement?

Recommended answer given: 20 meters.

User chose: **10 meters**.

Implication:

- StrideMap should initially target a fairly detailed route trace.
- This increases storage, upload volume, and possible battery pressure compared with 20m or 50m spacing.

### Minimum interval cap

Question asked: because 10m spacing at 100 km/h implies a point about every 0.36 seconds, what hard minimum time between stored GPS points should cap this?

Recommended answer given: 2 seconds.

User chose: **1 second**.

Implication:

- The app should not store points more frequently than once per second, even if 10m spacing would imply faster sampling.
- At 100 km/h, 1-second spacing corresponds to about 27.8m between points.

### Stationary interval cap

Question asked: when stationary or barely moving, what maximum interval should the app use before checking/taking another location?

Recommended answer given: 5 minutes stationary plus motion/activity detection to wake sooner.

User chose: **1 minute**.

Implication:

- Stationary mode remains relatively responsive.
- Battery savings while stationary will be less aggressive than with 5- or 15-minute caps.

### Android background execution

Question asked: is a persistent foreground-service notification acceptable while tracking runs?

Recommended answer given: yes, always visible.

User chose: **Yes, always visible**.

Implication:

- StrideMap should use a foreground service for reliable background tracking.
- A visible notification is an accepted UX requirement.

### Permission stance

Question asked: should the app require precise background location, or degrade gracefully if only approximate/foreground permission is granted?

Recommended answer given: precise background required.

User chose: **Precise background required**.

Implication:

- Continuous tracking should be disabled or clearly blocked unless precise background location is granted.
- Approximate or foreground-only permission is not enough for the core app behavior.

## Critical path discussion

The user requested: “Mental mode critical path.”

Question asked: should the first build target prove reliable background collection on one personal phone, or design the full sync/server architecture now?

Recommended answer given: single-phone local MVP first, because Android background reliability is the riskiest assumption and Postgres sync is easier after collection works.

User answered with a more specific development path:

- First make the app work on a virtual device: **Google Pixel 9** in Android Studio.
- The user acknowledged there may be no real GPS service available in the emulator.
- Once the application builds successfully, move the app to a **Samsung Galaxy S23**.
- Both target **Android 16 / API 36**.

Implication:

- Emulator work should validate buildability, algorithm behavior, local storage, and mock-location handling.
- Real-device testing on the S23 is required for actual GPS, background behavior, and battery observations.

## Emulator/mock strategy

Question asked: for the Pixel 9 emulator MVP, should location be treated as simulated/mock input only, with a debug screen or route injection to verify the adaptive sampler?

Recommended answer given: mock-first debug routes.

User chose: **Mock-first debug routes**.

Implication:

- The emulator should be used for deterministic route simulation.
- This validates adaptive sampling and DB writes.
- The emulator should not be treated as proof of real GPS/battery/background behavior.

The user later added:

- “The mock has to be high quality and garantied to work like with a real GPS module.”

Pushback given:

- A mock can be high-quality and deterministic.
- It cannot be guaranteed to behave exactly like a real GPS module.
- Real devices include sensor fusion, chipset quirks, multipath errors, permission/background throttling, and OEM battery policies.

Question asked: what level of mock realism is desired?

Recommended answer given: deterministic GPS simulator + real-device validation.

User chose: **Simulator + real validation**.

Implication:

- Build a high-quality GPS simulator/mock for deterministic development.
- Validate real behavior later on the Samsung Galaxy S23.
- Do not claim emulator parity with real GPS hardware.

Open question asked but dismissed:

- Should StrideMap consume Android’s normal location pipeline via mock locations, or inject fake locations directly into its own tracking engine behind an interface?
- Recommended answer was **both, interface first**:
  - Internal provider abstraction for deterministic tests.
  - Android mock locations / emulator route playback for end-to-end validation.
- The user dismissed this question, so the decision remains open.

## Naming discussion

The user said they still needed a catchy name.

Question asked: what name vibe should the app have?

Recommended answer: quiet personal archive.

User chose: **Fitness/outdoors**.

Question asked: should the name lean more “activity tracker” or “life map”?

Recommended answer: life map.

User chose: **Life map**.

Question asked: pick a working title.

Recommended answer: TrailPulse.

User responded: **“I like StrideMap.”**

Decision:

- Working title is **StrideMap**.

## Storage and sync direction

From the initial user description and later critical-path narrowing:

- Local-first development storage should use SQLite or similar.
- Position records should include at minimum coordinates and timestamp metadata.
- Remote sync to PostgreSQL is desired later, once local collection works.
- Remote sync/server design is deferred until the local MVP and real-device collection are proven.

## Files changed before this log

Earlier in the conversation, a standalone session note was written to:

- `SESSIONS.md`

That file captured the same high-level decisions and open questions in a root-level markdown note.

## Current unresolved decisions

1. Exact Android location stack:
   - Fused Location Provider vs platform `LocationManager` vs an abstraction that can swap providers.
2. Mock architecture:
   - Internal `LocationProvider` abstraction only, Android mock-location pipeline only, GPX/KML route playback only, or both interface-first and Android mock E2E.
3. Adaptive algorithm details:
   - How to calculate speed: raw GPS speed, distance/time between filtered points, Kalman-estimated state, or hybrid.
   - How many previous points / what distance window should define “last X covered distance.”
4. Database schema:
   - Exact tables and metadata fields beyond timestamp and coordinates.
5. Debug tooling:
   - Whether to include an in-app debug screen for route simulation and DB inspection.
6. Battery validation method on Samsung Galaxy S23:
   - How long to test, what metrics to record, and what success thresholds count as acceptable.
7. Remote sync architecture:
   - API shape, auth, batching, conflict handling, and Postgres schema remain intentionally deferred.

## Recommended next question

Resolve mock architecture first:

Should StrideMap use an internal `LocationProvider` abstraction first, so deterministic simulated routes can feed the tracking engine in tests/debug mode, while Android mock locations or emulator route playback are used separately for end-to-end validation?
