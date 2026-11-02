# Aesthory

An Android appearance coach for facial aesthetics, skin, hair and physique, supported by personal routines and private progress. Local records, optional AI, free services, no social feed or required account.

## Usable development build

- Built-in coaching from first launch: five starter routines, next-action suggestions and 11 searchable care/style/looksmaxing guides. No model download or API key needed.
- No-email setup and optional dated baseline measurements.
- Persistent routines, daily habits, instructions, estimated duration, completion and undo.
- Detailed habit editor: custom days/intervals, six tracking types, categories/tags, busy-day instructions, partial/skip logging, ordering and saved-instruction history.
- Running timers: saved pause/resume state, app-restart recovery, original-instruction history and explicit review before logging. Phone reboot recovery pauses an estimated time for review.
- Local reminders: action schedules, quiet hours, snooze, pause-all, grouped notifications and permission checks. Optional precise timer-goal alerts; routine reminders follow Android's approximate delivery.
- Reusable products and notes/links with local text/Markdown import, dated observations, search, archive/recovery and versioned habit links. No extra account or AI needed.
- On-device landmarks, recapture feedback and six apparent facial-proportion measurements. Reversible styling options follow your chosen goal.
- Routine JSON files: preview, save and import independent copies without private history; repeated imports are detected.
- Readable routine text plans; private product/reference content stays out of both sharing formats.
- Private progress-photo journal: date, area, view, notes, earlier/later comparison and Recently deleted with undo.
- Password-protected full backups through Android's file picker, checked before an explicit replace-and-restore confirmation. Restore also works from setup.
- Extra LiteRT-LM imports and Groq key settings are optional advanced tools. Groq currently tests connection only; extra language-model weights remain unevaluated.
- Colorful native Today/Coach/Routines/You screens, clear everyday language, one-tap check-ins, light/dark themes and scalable text.

The bundled face model is pretrained MediaPipe, not a newly trained PSL or facial-bone model. Selfie proportions are not hidden skeletal measurements or clinical findings. Optional language-model weights are not included, downloaded automatically or quality-validated yet. Analysis previews are temporary unless you choose “Keep as a progress photo”; journal photos persist independently of analysis.

Health Connect/Google Health, nutrition OCR, Hevy, weekly targets, broader specialist retrieval and live Notion/Obsidian connections are subsequent milestones, not active integrations. References currently support text/Markdown and web links, not PDF/image attachments. The built-in guide library is curated and limited, not exhaustive forum knowledge or custom model training. See [actual status](docs/STATUS.md), [habit/library guide](docs/HABITS.md), [timers/reminders](docs/TIMING.md), [scope](PRODUCT.md), [plan](docs/IMPLEMENTATION_PLAN.md) and [local analysis](docs/LOCAL_ANALYSIS.md).

## Build and run

Use JDK 21 and Android SDK platform 37/build-tools 36.0.0. Android 9/API 28+ is required. AGP includes Kotlin; do not also apply the legacy Kotlin Android plugin.

Set `JAVA_HOME`. Create ignored `local.properties` with your SDK path, e.g. `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`, or set `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Only on an owned test emulator/device: the runner uninstalls the app.
.\gradlew.bat :app:connectedDebugAndroidTest
```

On macOS/Linux use `sh ./gradlew` with the same tasks. First build downloads pinned Gradle/Maven dependencies. Install `app/build/outputs/apk/debug/app-debug.apk` or run from Android Studio. GitHub Actions also produces a debug APK artifact after successful checks.

No key/account/paid service or personal data is needed for setup, routines or landmarks. INTERNET is used only by the explicit optional Groq test at this checkpoint. Photos are processed locally. Automatic backup is disabled. Use You → Backup & restore before uninstalling or moving phones: saved backups include records, journal photos and Recently deleted, exclude API keys/extra model files, and require their exact password. Forgotten passwords cannot be recovered. See [backup format and recovery](docs/BACKUPS.md). Universal debug APK includes native libraries for multiple ABIs; it is not a release-size benchmark.

## Working with an agent

Read [AGENTS.md](AGENTS.md), then [STATUS.md](docs/STATUS.md); load task-specific specs. [DESIGN.md](DESIGN.md) defines the native interface. [RESEARCH.md](docs/RESEARCH.md) records primary sources and changing constraints. Distinguish implemented behavior from the roadmap.

## Third-party artifacts

Gradle wrapper v9.4.1 is verified against upstream SHA-256; Apache-2.0 license is in `gradle/wrapper/LICENSE`. The official pretrained landmark bundle has license/source/SHA in `app/src/main/assets/models/`. Barlow Condensed bold is bundled from Google Fonts under SIL OFL, retained in `app/src/main/assets/licenses/Barlow-OFL.txt`. Optional model licenses depend on selected weights. Application code is original; official examples are references, not copied apps. No application license/distribution channel has been selected.
