# API guidance

## Status

Remote sync and API design are deferred. Do not imply that server infrastructure, authentication, PostgreSQL, or sync contracts already exist.

## Planning scope

- Keep API notes conditional until real local capture, GPX visibility, background behavior, and recovery are proven on target devices.
- Future sync may target a remote PostgreSQL-backed service, but that is not part of v1.
- Local GPX capture/storage/validation has priority over remote API design.

## API design assumptions

- Treat GPX plus StrideMap metadata/journal semantics as the future sync source, not an assumed SQLite row schema.
- Prefer explicit, versionable payloads for tracks, points, state transitions, and recovery semantics.
- Include timestamps, coordinates, accuracy/quality metadata, movement type, track state, duration/distance metadata, device/app metadata where needed, and sync status fields.
- Plan for batching, retries, idempotency, conflict handling, and offline-first behavior.
- Treat authentication, authorization, network access, and operational endpoints as open decisions until explicitly scoped.

## Documentation expectations

- Document endpoint intent, payload shape, status semantics, privacy expectations, and error behavior before implementation.
- Do not add real personal location data to API examples.
- Do not change server, database, or dependency setup without explicit approval.
