# Repository Guidelines

## Project Structure & Module Organization
- `app/` is the only Android module.
- App code lives in `app/src/main/java/dev/bluehouse/enablevolte/`.
- Jetpack Compose UI is split into `pages/`, reusable `components/`, and `ui/theme/`.
- Hidden-API service bindings are in `app/src/main/aidl/`.
- Android resources and locale strings are in `app/src/main/res/`.
- Supporting docs and screenshots are in `docs/` and `assets/`.
- CI workflows are in `.github/workflows/`.

## Build, Test, and Development Commands
Before building, install the patched `android.jar` used by CI into `$ANDROID_SDK_ROOT/platforms/android-36/android.jar`.

- `./gradlew assembleDebug`: build a debug APK for local testing.
- `./gradlew installDebug`: install debug APK on a connected device.
- `./gradlew assembleRelease`: build release APK output.
- `./gradlew bundleRelease`: build Play Store `.aab` bundle.
- `./gradlew testDebugUnitTest`: run JVM unit tests.
- `./gradlew connectedDebugAndroidTest`: run instrumentation tests on device/emulator.
- `ktlint --reporter=checkstyle,output=build/ktlint-report.xml`: run lint exactly like CI.

## Coding Style & Naming Conventions
- Language stack: Kotlin + Jetpack Compose (Java/Kotlin target 11).
- Follow `ktlint` and `.editorconfig`; function/property naming checks are intentionally disabled.
- Use 4-space indentation, trailing commas where Kotlin style supports them, and keep imports explicit.
- Name composables/classes in `PascalCase`, variables/functions in `camelCase`, and resource IDs/files in `snake_case`.
- Keep telephony/Shizuku logic in core files (`Moder.kt`, `Utils.kt`) and UI logic in `pages/` or `components/`.

## Testing Guidelines
- No committed test suites exist yet under `app/src/test` or `app/src/androidTest`.
- For new logic, add unit tests as `*Test` and device tests as `*InstrumentedTest`.
- Validate VoLTE flows manually on Tensor Pixel hardware: Shizuku running, permission granted, feature toggled, IMS status registered.
- No fixed coverage gate today; prioritize tests around changed behavior.

## Commit & Pull Request Guidelines
- Follow existing commit prefixes: `fix:`, `docs:`, `chore:`, `release:` (example: `fix: handle null carrierName (#426)`).
- Keep commits focused to one logical change.
- PRs should include: behavior summary, affected device/carrier/Android version, test evidence (commands + manual checks), and screenshots for UI changes.
- Link related issues and ensure CI is green (lint + debug APK workflow for `app/src/**` changes).

## Security & Configuration Tips
- Do not commit keystores, signing configs, or secrets.
- Use GitHub Actions secrets for signing in release workflows.
