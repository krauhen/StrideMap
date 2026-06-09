# Testing guidance

## Status

StrideMap has a v1 Android app and JVM unit tests. Testing must keep emulator/app-wiring evidence separate from real GPS/background/battery evidence. `agentic/knowledge/SPEC.md` should map requirements to verification approaches and record which behaviors are implemented versus still pending real-device validation.

## Current commands

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:installDebug --no-daemon
"/Users/use/Library/Android/sdk/platform-tools/adb" shell am start -W -n com.example.stridemap/.MainActivity
```

Do not run expensive emulator/device tests for documentation-only changes.

## Test strategy

- Prefer unit tests for tracking-domain logic, saved-point sampling, GPX write/parse, recovery planning, settings defaults, ordering, and storage constants.
- Use deterministic route fixtures for walking, running, biking, driving, stops, turns, GPS jitter, dropouts, and timestamp gaps.
- Keep Android framework/instrumented tests separate from pure JVM/domain tests.
- Use emulator validation for buildability, debug routes, local storage behavior, and end-to-end app wiring.
- Use Samsung Galaxy S23 validation for real GPS, background execution, notification behavior, and battery observations.

## GPS and sampler verification themes

- Verify saved-point sampling accepts the first valid point, then requires at least 1 second and at least 10 meters from the last persisted point.
- Verify provider polling intervals are movement/settings-based and separate from saved-point sampling.
- Verify Settings changes affect future captures only and do not mutate an active capture.
- Verify approximate or foreground-only permission blocks core continuous tracking with a clear explanation.
- Verify simulator inputs are deterministic and do not depend on wall-clock timing unless explicitly tested.
- Verify stale live-with-points becomes `interrupted`, stale zero-point live is discarded, and no auto-resume occurs.

## Validation status

- Completed: Pixel 9 emulator build/install/launch/Start smoke/foreground notification.
- Completed: Samsung S23 install/launch/visual validation.
- Pending: real GPS route, background/screen-off, OEM battery behavior, notification during real capture, public GPX visibility, long-running capture, process kill, and startup recovery.
