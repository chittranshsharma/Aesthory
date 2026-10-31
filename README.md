# Aesthory

An Android appearance coach for facial aesthetics, skin, hair and physique, supported by personal routines and private progress. Local records, optional AI, free services, no social feed or required account.

## Usable development build

- Built-in coaching from first launch: five starter routines, next-action suggestions and 11 searchable care/style/looksmaxing guides. No model download or API key needed.
- No-email setup and optional dated baseline measurements.
- Persistent routines, daily habits, instructions, estimated duration, completion and undo.
- On-device landmarks, recapture feedback and six apparent facial-proportion measurements. Reversible styling options follow your chosen goal.
- Routine JSON files: preview, save and import independent copies without private history; repeated imports are detected.
- Extra LiteRT-LM imports and Groq key settings are optional advanced tools. Groq currently tests connection only; extra language-model weights remain unevaluated.
- Colorful native Today/Coach/Routines/You screens, clear everyday language, one-tap check-ins, light/dark themes and scalable text.

The bundled face model is pretrained MediaPipe, not a newly trained PSL or facial-bone model. Selfie proportions are not hidden skeletal measurements or clinical findings. Optional language-model weights are not included, downloaded automatically or quality-validated yet. Photo preview is temporary; a persistent progress-photo journal is planned.

Health Connect/Google Health, nutrition OCR, Hevy, full backups, products/references, broader specialist retrieval and Notion/Obsidian are subsequent milestones, not active integrations. The built-in guide library is curated and limited, not exhaustive forum knowledge or custom model training. See [actual status](docs/STATUS.md), [scope](PRODUCT.md), [plan](docs/IMPLEMENTATION_PLAN.md) and [local analysis](docs/LOCAL_ANALYSIS.md).

## Build and run

Use JDK 21 and Android SDK platform 37/build-tools 36.0.0. Android 9/API 28+ is required. AGP includes Kotlin; do not also apply the legacy Kotlin Android plugin.

Set `JAVA_HOME`. Create ignored `local.properties` with your SDK path, e.g. `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`, or set `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# With emulator/device attached:
.\gradlew.bat :app:connectedDebugAndroidTest
```

On macOS/Linux use `sh ./gradlew` with the same tasks. First build downloads pinned Gradle/Maven dependencies. Install `app/build/outputs/apk/debug/app-debug.apk` or run from Android Studio. GitHub Actions also produces a debug APK artifact after successful checks.

No key/account/paid service or personal data is needed for setup, routines or landmarks. INTERNET is used only by the explicit optional Groq test at this checkpoint. Photos are processed locally. Automatic backup is disabled; uninstalling deletes records because portable recovery is pending. Universal debug APK includes native libraries for multiple ABIs; it is not a release-size benchmark.

## Working with an agent

Read [AGENTS.md](AGENTS.md), then [STATUS.md](docs/STATUS.md); load task-specific specs. [DESIGN.md](DESIGN.md) defines the native interface. [RESEARCH.md](docs/RESEARCH.md) records primary sources and changing constraints. Distinguish implemented behavior from the roadmap.

## Third-party artifacts

Gradle wrapper v9.4.1 is verified against upstream SHA-256; Apache-2.0 license is in `gradle/wrapper/LICENSE`. The official pretrained landmark bundle has license/source/SHA in `app/src/main/assets/models/`. Barlow Condensed bold is bundled from Google Fonts under SIL OFL, retained in `app/src/main/assets/licenses/Barlow-OFL.txt`. Optional model licenses depend on selected weights. Application code is original; official examples are references, not copied apps. No application license/distribution channel has been selected.
