# AGENTS.md

This file is the landing page for agents and LLMs working in this repository.

It is intentionally not the full operating manual. Before starting work, agents must read the task-relevant files under `./agentic/` and must not plan, edit, test, or answer implementation questions from `AGENTS.md` alone.

---

## Project

This repository contains **StrideMap**, a personal Android GPS/location-history app.

Current v1 source of truth:

- `agentic/knowledge/features/init-creating-all-defined-components.md`

Current v1 product direction:

- Real GPS reference-track capture app, not simulator-first.
- Personal location history for the user; not fleet tracking, live safety sharing, or social tracking.
- Material 3 Compose shell with bottom tabs exactly `Capture`, `Tracks`, and `Map`; Settings opens from a top-corner gear.
- OpenStreetMap via osmdroid; online OSM tiles are allowed by default with attribution, lifecycle, cache, and offline/error safeguards.
- GPX files are public through MediaStore under `Documents/StrideMap/Tracks/`; no SAF picker and no broad storage permissions in v1.
- App-private active-session GPX journal is the recovery source of truth.
- Initial development/validation target: Google Pixel 9 emulator on Android 16 / API 36.
- Real-device validation target: Samsung Galaxy S23 on Android 16 / API 36.
- Standard local run procedure: run the Gradle build first, then start the Pixel 9 emulator/device.

Standard local commands:

```bash
./gradlew :app:assembleDebug --no-daemon
"/Users/use/Library/Android/sdk/emulator/emulator" -avd Pixel_9
```

If the Pixel 9 emulator is already running, install and launch the current debug build with:

```bash
./gradlew :app:installDebug --no-daemon
"/Users/use/Library/Android/sdk/platform-tools/adb" shell am start -W -n com.example.stridemap/.MainActivity
```

Useful validation commands:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Key v1 tracking decisions:

- Production location uses an app-owned `LocationProvider` abstraction backed by Google Play Services `FusedLocationProviderClient`; no `LocationManager` fallback in v1.
- Saved-point sampling is non-configurable: first valid point, then at least 1 second and at least 10 meters from the last persisted point.
- Provider polling interval is movement/settings-based and separate from saved-point sampling; Settings changes do not mutate active capture.
- Use a persistent foreground-service notification for reliable background/screen-off recording.
- Foreground service uses `START_NOT_STICKY`; no boot receiver, no auto-start, and no auto-resume.
- Require precise background location; approximate-only or foreground-only access blocks Start with explanation.

Lifecycle/storage decisions:

- One live track max.
- Track states are `live`, `stopped`, and `interrupted`.
- Stale live captures with points become `interrupted`; zero-point stale live captures are discarded.
- Movement types are `walk`, `run`, `bike`, `car`, and `train`; Walk is the default, and Settings can change the future default.
- Settings uses `SharedPreferences` and includes Capture, Tracks, Map, and Storage/App info defaults.

Validation direction:

- Pixel 9 emulator validation completed for build/install/launch/Start smoke/foreground notification.
- Samsung S23 install/launch/visual validation completed.
- Pending: real GPS route, background/screen-off, OEM battery behavior, notification during real capture, public GPX visibility, long-running capture, process kill, and recovery.
- Do not assume emulator or mock GPS exactly matches real GPS.

Storage/sync direction:

- Remote sync/API/PostgreSQL are deferred.
- Future sync should treat GPX plus metadata/journal semantics as the source, not current SQLite rows.
- Earlier adaptive stationary mode, SQLite storage, and PostgreSQL sync notes are later/future context unless the current feature source of truth updates them.

---

## Mandatory context loading

Before starting any task, follow this sequence:

1. Identify the task type.
2. Read `agentic/README.md`.
3. Read the matching files under `agentic/guidance/`.
4. If the task relates to existing feature, product, or preparation knowledge, read the relevant files under `agentic/knowledge/`.
5. If the task continues or references a previous coding/product session, read the relevant files under `agentic/sessions/summaries/`.
6. If the session summary is insufficient, read the relevant raw session under `agentic/sessions/raw/`.
7. Only then start planning or execution.

If the correct files are unclear, read these defaults first:

- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`

For broad or risky tasks, read all files in `agentic/guidance/` before changing code or documentation.

---

## Agentic directory structure

```text
agentic/
├── README.md
├── guidance/
│   ├── SETUP.md
│   ├── CODING.md
│   ├── API.md
│   ├── TESTING.md
│   ├── ERROR.md
│   ├── ARCHITECTURE.md
│   ├── KEYWORDS.md
│   ├── WORKFLOW.md
│   └── MCP.md
├── sessions/
│   ├── raw/
│   └── summaries/
└── knowledge/
    ├── features/
    ├── todos/
    └── requests/
```

---

## Directory purposes

### `agentic/README.md`

Index for the `agentic/` documentation area.

Use it to understand the available documentation groups and how they relate to each other. `AGENTS.md` remains the mandatory first-read landing page.

### `agentic/guidance/`

Standing operating instructions for agents.

Read files from this directory before changing documentation, code, tests, architecture notes, setup notes, or workflows.

### `agentic/sessions/`

Session records for product, coding, architecture, and documentation work.

- `agentic/sessions/summaries/` stores condensed reusable handoffs.
- `agentic/sessions/raw/` stores near-raw session records when summaries are not detailed enough.

Use summaries first when restoring context from prior work.

Session file naming convention:

```text
YYYY-MM-DD__ticket-or-topic__short-slug.md
```

### `agentic/knowledge/`

Durable project knowledge.

- `agentic/knowledge/features/` stores feature intent, design assumptions, behavior, constraints, implementation ideas, and verification strategy.
- `agentic/knowledge/todos/` records deferred tasks and follow-ups; entries are not authorization to implement.
- `agentic/knowledge/requests/` records rough asks and early-stage requirements before promotion to feature knowledge.

Feature directory naming convention:

```text
<ticket-id>__<feature-slug>/
```

If there is no ticket ID:

```text
<feature-slug>/
```

Each feature should use multiple granularity files with the same headings and sections:

```text
short.md
mid.md
dense.md
```

---

## Task routing

Use this list to decide which files to read.

### Continuing the initial GPS tracking MVP work

- `agentic/knowledge/features/init-creating-all-defined-components.md`
- `agentic/sessions/summaries/2026-06-08__stridemap-grilling__capture-reference-track-v1.md`
- `agentic/sessions/raw/2026-06-08__stridemap-grilling__capture-reference-track-v1.md` if more detail is needed.
- `agentic/sessions/summaries/2026-06-08__stridemap-initial-grilling__gps-tracking-mvp.md`
- `agentic/sessions/raw/2026-06-08__stridemap-initial-grilling__gps-tracking-mvp.md` only for earlier background if the current feature/source session is insufficient.

### Android app code change

- `agentic/guidance/CODING.md`
- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/TESTING.md`
- Relevant feature/session files.

### Location tracking, background service, permissions, storage, sync, or simulator design

- `agentic/guidance/ARCHITECTURE.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`
- Relevant feature/session files.

### API or remote sync planning

- `agentic/guidance/API.md`
- `agentic/guidance/ARCHITECTURE.md`
- `agentic/guidance/TESTING.md`

### Test change or test run

- `agentic/guidance/TESTING.md`
- `agentic/guidance/WORKFLOW.md`

### Error handling, logging, permission failure, or validation change

- `agentic/guidance/ERROR.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`

### New feature request capture

- Relevant files under `agentic/knowledge/requests/`.
- `agentic/guidance/WORKFLOW.md`

### TODO/follow-up capture

- Relevant files under `agentic/knowledge/todos/`.
- `agentic/guidance/WORKFLOW.md`

### Documentation-only work

- The target documentation file.
- `agentic/guidance/WORKFLOW.md`

### Keyword or terminology work

- `agentic/guidance/KEYWORDS.md`
- `agentic/guidance/WORKFLOW.md`

---

## Universal safety rules

These rules always apply.

- Do not touch unrelated modified files.
- Do not commit secrets.
- Do not commit `.env`, `.env.testing`, `key.pem`, `cert.pem`, or secrets.
- Do not drop untracked changes.
- Do not delete, move, or rename files without explicit approval.
- Do not change dependencies without explicit approval.
- Do not change Docker, CI/CD, or infrastructure files without explicit approval.
- Do not add data artifacts or copied customer data without explicit approval.
- Do not include personal details, names, contact information, addresses, or copied CV/project details in generated docs.
- Preserve existing project conventions unless the task explicitly asks to change them.
- Prefer minimal, focused changes.
- Ask before non-trivial or ambiguous changes.
