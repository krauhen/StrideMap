# Error handling guidance

## Status

StrideMap v1 has runtime capture, GPX storage, foreground-service, map, and settings behavior. Error guidance should protect correctness, privacy, and debuggability without silently degrading capture quality.

## Error handling and logging

- Prefer typed, explicit failures over broad catches.
- Preserve root-cause details in logs while avoiding secrets, signing data, personal location traces, private addresses, and sensitive paths.
- Use structured logs for permission state, tracking state transitions, provider availability, saved-point sampling decisions, storage writes, journal recovery, and future sync attempts.
- Avoid `print`/ad-hoc logging in production-oriented code.

## Location and permission failures

- If precise background location is unavailable, clearly block core continuous tracking instead of silently degrading.
- Distinguish foreground-only, approximate-only, disabled location services, provider unavailable, battery restriction, and foreground-service failures.
- Surface actionable user-facing explanations for permission and settings blockers.
- Keep debug details available for development without leaking personal location history.
- If foreground service startup fails, Start must fail visibly and must not leave an orphan live capture.
- If storage write fails during live capture, stop accepting new points, stop location updates, mark the track `interrupted` if it has points, otherwise discard, and show a user-visible error.

## Data validation

- Validate timestamps, coordinate ranges, accuracy values, ordering, duplicate points, impossible jumps, and missing metadata close to ingress.
- Do not silently coerce location data when route correctness or privacy could be affected.
- Treat GPX parser errors as safe user-visible malformed rows; do not expose private paths or raw route contents.
- On startup, stale live-with-points becomes `interrupted`; stale zero-point live is discarded; do not auto-resume.
- Treat sync/network/server failures as deferred until remote sync is explicitly implemented.
