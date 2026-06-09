# Setup guidance

## Status

StrideMap now has a v1 Android app for real GPS reference-track capture. Setup guidance should support local Android development, emulator smoke checks, and real-device GPS/background validation.

## Local setup

- Prefer Android Studio for emulator/device workflows.
- Initial emulator target: Google Pixel 9 on Android 16 / API 36.
- Real-device validation target: Samsung Galaxy S23 on Android 16 / API 36.
- Standard local run procedure: run the Gradle build first, then start the Pixel 9 emulator/device.
- Do not add dependencies, Gradle plugins, Docker assets, CI configuration, credentials, certificates, signing keys, or data artifacts without explicit approval.

## Safe generic commands

Use project-specific Gradle commands only after the Android project structure exists and the command has been inspected or explicitly requested.

Current StrideMap build command:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Start the standard Pixel 9 emulator:

```bash
"/Users/use/Library/Android/sdk/emulator/emulator" -avd Pixel_9
```

If the emulator is already running, install and launch the current debug build:

```bash
./gradlew :app:installDebug --no-daemon
"/Users/use/Library/Android/sdk/platform-tools/adb" shell am start -W -n com.example.stridemap/.MainActivity
```

Current test/build commands:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Do not run expensive device/emulator test suites unless requested or clearly required for the task.

## Data and access assumptions

- Do not commit personal location traces, exported route files, screenshots with private locations, signing keys, tokens, or `.env*` files.
- Public app GPX files are expected under `Documents/StrideMap/Tracks/` on device via MediaStore.
- Use deterministic synthetic routes for unit tests/fixtures only; v1 product validation is real capture-first.
- Treat emulator location behavior as development validation only; real GPS/background/battery behavior must be checked on the target phone.

## Validation status

- Completed: Pixel 9 emulator build/install/launch/Start smoke/foreground notification; Samsung S23 install/launch/visual validation.
- Pending: real GPS route, background/screen-off, OEM battery behavior, notification during real capture, public GPX visibility, long-running capture, process kill, and recovery.
- Do not run expensive emulator/device suites for docs-only work.
