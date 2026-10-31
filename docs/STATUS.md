# Status

Updated 2026-10-31, Asia/Calcutta. User selected **Aesthory**, authorized tested pushes to `chittranshsharma/Aesthory`, and supplied an Android target with **8 GB+ RAM**. Exact phone/SoC is unknown.

## Current checkpoint

Step 2 persistence is complete. The first native UI is usable, with a local facial-analysis pilot brought forward from step 6. This is an early development build.

- Kotlin/Compose/Material 3, ViewModel/Flow, Room 2.8.5/KSP, one app module. JDK 21, Gradle 9.4.1, AGP 9.2.1, compile/target 37, minSdk 28. Dependencies are pinned.
- Two-step no-email setup: optional name, focus, age band, tone, height/weight and additional measurements. Edit baseline in Profile; later measurements append dated records.
- Room v1 exported schema: profile, measurement history, categories, ordered routines, versioned habit instructions/schedules/tracking, append-only completion/undo events. Repository supports editing/reordering/pausing/archiving; current UI exposes basic daily checkbox creation, instructions, estimated minutes, logging and undo.
- Four native destinations: Today, Analyze, Routines, Profile. Forest/ivory light theme, dark theme, scalable controls and system Back.
- Photo Picker → bounded local image decode → bundled pretrained MediaPipe landmarks → capture gates → six surface-proportion values and goal-based reversible styling options. Photos/previews are in memory only; a persistent photo journal is pending. See LOCAL_ANALYSIS.md for definitions and limitations.
- Optional imported CPU-compatible `.litertlm` model: streamed, size-bounded, hashed, container checked, runtime initialized before atomic replacement. LiteRT-LM 0.19.0 generates a separate bounded coaching draft from measured text; no photo upload or automatic weight download. Actual optional weights/quality have not been tested yet.
- Optional masked Groq key: Android Keystore-backed AES-GCM storage, replace/delete, browser key-page link and explicit HTTPS model-list connection test. No chat/image-generation request or billing verification yet. Local tracking/landmarks need no key.
- Backup disabled; INTERNET for the explicit Groq test, no health/camera/broad-storage permissions. Room itself is not encrypted. Keys and optional large model files are excluded from Git.
- GitHub Actions checks model SHA, unit tests, lint and debug assembly, uploading reports/APK. Remote CI results must be checked after push; local results are below.

## Verification actually executed

Host: Windows, isolated Temurin 21.0.13+9 and SDK under `%USERPROFILE%/.cache/ritloom`; host Java 27 unchanged. API 35 Google APIs x86_64 emulator, 2 GB RAM, WHPX, owned AVD `Aesthory_Test_API35`.

- `:app:testDebugUnitTest`: **47 tests**, zero failures/errors/skips. Calendar 10, routing 10, profile 4, Room repository 9, geometry 6, model import 4, tracking 2, key validation 2.
- Room checks include close/reopen persistence, historical definitions/measurements, undo ordering, reorder validation, pause/archive, invalid values, Unicode checklists and transaction rollback. This is the first schema; no prior-version migration exists to test.
- `:app:assembleDebug :app:lintDebug`: passed. Lint: **zero errors, 11 warnings** (tool/library update advisories, optional resource/monochrome icon/KTX refinements and storage-allocation guidance). No suppressions. Universal debug APK is about 161 MB with native ABIs; release shrinking/splits remain unmeasured.
- `:app:connectedDebugAndroidTest`: **3 tests**, zero failures/errors/skips: isolated encrypted-key roundtrip/deletion, pinned model hash, native inference on a blank bitmap with no-face rejection and preview-bitmap ownership regression.
- Manual native flow: finish setup with optional inputs empty; create Evening/Moisturize routine/habit; log completion; force-stop/relaunch; verify `1 of 1 complete` persists. Dialog and keyboard flows checked.
- Photo Picker/inference: screenshot returns one-face guidance; official public sample portrait runs and returns expression-specific recapture with a working preview. No private photos used/committed. Valid measured geometry is unit-tested; a representative neutral-photo positive measurement UI still needs checking.
- Rendered light setup and dark Today/Analyze at 1.3× font scale inspected. Chips wrap and text/navigation remain readable. Font/night mode restored afterward. Full TalkBack, low-storage/process-kill photo recovery and physical-device performance remain pending.
- Fixed actual preview crash: MPImage.close recycles its bitmap. Inference now owns a copy; Compose retains its preview. Device regression draws the preview after inference.

No real Groq key/request, optional LLM weight generation, expert evaluation, newly trained model, validated PSL score, hidden-bone reconstruction, clinical performance, release benchmark or health sync is claimed. Prompts configure behavior; they are not training.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`, `app/build/reports/androidTests/connected/debug/index.html`. Captures/build outputs stay ignored.

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# With emulator/device attached:
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Next action

Prioritize a neutral-photo positive report and actual small CPU model import/generation on the user's 8 GB+ phone; use LOCAL_ANALYSIS.md before recommending weights. Finish step 3 error/accessibility/settings checks, then step 4 routine sharing and encrypted backup/restore before growing private history. Persistent progress photos, grounded specialist coaching, detailed editor/products/references, nutrition OCR, Hevy, Health Connect/cloud Google Health and Notion/Obsidian remain planned. Do not present those connectors as active.
