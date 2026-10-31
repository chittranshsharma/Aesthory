# Aesthory

An Android appearance coach for skin, hair, facial aesthetics, and physique, supported by personal routines, products, workouts, nutrition, and private progress.

Local records; optional cloud AI; free services; independent routine sharing. See [PRODUCT.md](PRODUCT.md) for scope and [the implementation plan](docs/IMPLEMENTATION_PLAN.md) for the build sequence.

## Current state

Project foundation: pinned Android build, private backup policy, calendar scheduling, and local setup/coaching policies. There is no launcher or interactive UI yet. Specialist prompt assets and model routing do not constitute trained model weights or a connected AI service. Persistence is the next milestone; the first usable UI includes setup. Planned integrations are not yet implemented.

See STATUS.md for the latest executed checks, test count, and remaining advisories.

## Build

Requirements: JDK 21 and an Android SDK with `platforms;android-36` and `build-tools;36.0.0`. Android Studio may provide/manage these. AGP includes Kotlin support; do not apply the legacy Kotlin Android plugin alongside it.

Set `JAVA_HOME` to your JDK 21 installation. Create an ignored `local.properties` with `sdk.dir` pointing to your SDK, using forward slashes and escaping the drive colon on Windows (for example `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`), or set `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On macOS/Linux use `sh ./gradlew` with the same tasks. Gradle downloads its pinned distribution and Maven dependencies on first use; later offline builds require cached dependencies.

- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- Foundation APK: `app/build/outputs/apk/debug/app-debug.apk` (not launchable at this milestone)

No API keys, Google account, personal data, or paid service is needed to run the foundation checks. The project requests no network or health permissions yet. The host setup used for initial verification is recorded in [STATUS.md](docs/STATUS.md).

## Contributing with an agent

Read [AGENTS.md](AGENTS.md), then [STATUS.md](docs/STATUS.md). Continue the next scoped milestone and record actual validation. [Onboarding](docs/ONBOARDING_SPEC.md) and [coaching](docs/COACHING_SPEC.md) have focused specifications. External references and integration caveats live in [RESEARCH.md](docs/RESEARCH.md).

## Third-party tooling

The checked-in Gradle wrapper comes from the official Gradle v9.4.1 tag under Apache-2.0; its scripts retain upstream license headers and the license is in `gradle/wrapper/LICENSE`. Its JAR and distribution were checked against published SHA-256 values. Application code is original; official Android repositories were used as references only. No application license or distribution channel has been selected yet.
