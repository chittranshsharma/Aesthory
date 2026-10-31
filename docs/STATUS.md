# Status

2026-10-31 · Aesthory 0.2.0 · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Replaced the technical/minimal pilot with colorful native Today, Coach, Routines and You screens, clearer everyday language, licensed Barlow headlines, one-tap completion, haptic feedback and light/dark themes. AI settings are optional and collapsed; no model import is required for normal use.
- Added five ready-made routines and built-in daily guidance from actual saved tasks. Suggestions preserve each routine's order, respect done/skipped states, use known duration and suggest reducing overloaded plans. Repeated starter additions preserve history.
- Added 11 searchable offline guides across care, style, fitness, forum terms and adult procedure education. Source links distinguish care evidence from styling/vocabulary. This is a small curated library, not exhaustive forum knowledge or model training.
- Added routine JSON preview/save/import through Android's file picker. Bounded UTF-8/schema/nesting validation, independent transactional copies and duplicate handling. Excludes credentials, photos, measurements and completion history. Instructions are user data, not verified health advice.
- Preserved Room v1 history, dated measurements, local MediaPipe checks, encrypted Groq key settings and optional experimental LiteRT-LM import. Numerical photo details are now expandable. No schema migration/destructive reset was needed.

## Verified

- **64 unit tests passed**, zero failures/errors. Added ordered-guidance, starter idempotence, pack roundtrip/validation/deep nesting, copy independence and history-exclusion checks.
- **3 Android device tests passed** on owned API35 x86_64 emulator: Keystore roundtrip/deletion, bundled model hash and native face inference/preview ownership.
- Debug assembly and lint passed; **0 lint errors, 12 warnings** (update advisories and optional resource/KTX/storage refinements; no suppressions). Final guide-button padding fix was rebuilt/linted; import hardening also passed the full unit suite.
- Native manual flows: optional setup, one-tap starter, ordered completion, 2/2 completion retained after force-stop/relaunch, guide search/open, routine export through system save picker, import preview/add, updated More menu and photo-check entry/Back.
- Rendered phone screens checked in light and dark at 1.3× text. Independent review's two material fixes—guide text clipping and development metadata—were scored resolved. Captures stay in ignored `.impeccable/review/`.
- GitHub Actions runs unit/lint/build checks and provides an APK artifact after each successful push; check the current run after publishing.

## Next

1. Portable encrypted backup/restore with passphrase, tamper/wrong-password tests and transactional recovery; readable routine text export.
2. Private saved progress photos and comparison, then the detailed routine/product/reference editor.
3. Continue the existing plan for nutrition, Hevy and health integrations. None are presented as active.

Remaining limits: no real Groq generation, optional LLM-weight quality test, physical-phone performance benchmark, complete TalkBack audit or clinical review. Photo previews are temporary. Full backup is not implemented; uninstalling deletes records. No newly trained PSL/bone model or expert-equivalent accuracy is claimed.

## Checks

JDK 21; SDK37; pinned tools/dependencies in Gradle catalog. Local JDK is `%USERPROFILE%/.cache/ritloom/jdk-21.0.13+9`.

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports are under `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Follow IMPLEMENTATION_PLAN.md and task-specific specs; keep this file to current done/verified/next.
