# Agentic guidance

This directory contains repository-specific context for agents working on **StrideMap**, a personal Android GPS/location-history app.

The current v1 source of truth is `knowledge/features/init-creating-all-defined-components.md`. It describes the implemented real GPS reference-track capture app and wins over older simulator-first, SQLite-first, or adaptive-stationary MVP notes unless explicitly superseded.

## Required loading order

1. Read `AGENTS.md` at the repository root.
2. Read this file.
3. Read the topic files in `guidance/` that match the task.
4. For broad or risky work, read all files in `guidance/`.
5. For feature, product, or preparation work, check `knowledge/features/`, `knowledge/requests/`, and recent summaries.

## Directory purposes

- `guidance/` stores durable operating rules split by topic.
- `sessions/raw/` stores raw session notes or transcripts.
- `sessions/summaries/` stores distilled reusable handoffs. Session summaries should usually be concise: target 50-75 lines unless the user explicitly asks for a denser handoff.
- `knowledge/` stores longer-lived project knowledge.
- `knowledge/features/` stores feature intent, design assumptions, behavior, constraints, implementation ideas, and verification strategy.
- `knowledge/todos/` stores deferred tasks, issues, cleanup ideas, and follow-ups for future triage.
- `knowledge/requests/` stores early feature ideas, rough asks, and requirements before promotion to feature knowledge.

## Guidance file index

- `guidance/SETUP.md` — local Android setup, Gradle/ADB commands, and validation targets.
- `guidance/CODING.md` — Android/Kotlin coding style and v1 tracking/storage boundaries.
- `guidance/API.md` — deferred sync/API planning guidance.
- `guidance/TESTING.md` — unit/build commands and GPS/device validation planning.
- `guidance/ERROR.md` — permission, tracking, storage, GPX, and sync error handling.
- `guidance/ARCHITECTURE.md` — StrideMap v1 architecture and domain assumptions.
- `guidance/KEYWORDS.md` — flat index of abbreviations and technical terms.
- `guidance/WORKFLOW.md` — safety, scope boundaries, and agent workflow.
- `guidance/MCP.md` — project-specific MCP/tooling notes when such guidance exists.

## Current v1 snapshot

- Product: real-world GPS data capture app first; captured GPX files are the foundation for downstream projects, evaluation, and future app features.
- UI: Material 3 Compose, bottom tabs exactly `Capture`, `Tracks`, `Map`; Settings opens from a top-corner gear.
- Map: OpenStreetMap via osmdroid with online tiles allowed by default when attribution/cache/lifecycle safeguards are preserved.
- Storage: MediaStore public GPX files under `Documents/StrideMap/Tracks/`; app-private active-session GPX journal is the recovery source of truth.
- Location: app-owned `LocationProvider`; production uses Google Play Services `FusedLocationProviderClient`; no `LocationManager` fallback in v1.
- Lifecycle: one live track max; `live`/`stopped`/`interrupted`; `START_NOT_STICKY`; no boot receiver, auto-start, or auto-resume.
- Sampling: non-configurable saved-point rule of first valid point, then at least 1 second and at least 10 meters.
- Settings: `SharedPreferences` defaults for Capture, Tracks, Map, and Storage/App info; Settings do not mutate an active capture.
