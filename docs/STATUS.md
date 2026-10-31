# Status

2026-10-31 · Aesthory 0.3.0 · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Everyday coaching still works without a key/model: five starter routines, ordered next actions, 11 searchable guides and colorful native Today/Coach/Routines/You screens.
- Added You → My progress photos: private saved copies, date/area/view/notes, earlier/later comparison for matching views, edit details, Recently deleted, undo/bring back and confirmed permanent deletion. Coach previews can be explicitly saved to the journal.
- Added You → Backup & restore, also reachable from setup. Password-protected portable files include structured records/history and all journal photos, including Recently deleted. Keys, extra models and preferences are excluded. Android's file picker chooses the destination.
- Restore fully authenticates and validates the file before showing counts/date and an explicit replacement confirmation. New image files stage independently; one database transaction replaces records. Failure before commit preserves current records/photos. See BACKUPS.md for format, limits and recovery invariants.
- Added Room v1→v2 migration without destructive reset, metadata-stripped/oriented JPEG copies and separate bounded thumbnails. Preserved existing routine/history, local MediaPipe, optional experimental LiteRT-LM and encrypted Groq key settings.

## Verified

- **76 unit tests passed**, zero failures/errors: includes multisegment encryption, wrong passwords, tampering/truncation, snapshot validation/roundtrip, rollback, live Flow refresh, original-v1 migration and comparison rules.
- **4 Android tests passed** on owned API35 x86_64 emulator. New vault test verifies orientation/location-metadata removal, gallery-source independence, encrypted photo/bin recovery, regenerated thumbnails, key/model preservation and failed restore leaving current data intact. Existing Keystore and bundled-model checks also passed.
- Debug assembly and lint passed: **0 lint errors, 16 warnings** (update advisories and optional icon/KTX/storage refinements; no suppressions). The final Back-label correction was rebuilt/linted and the unit suite rerun.
- Native manual flows: setup restore entry, save photos/date, force-stop/relaunch retention, earlier/later comparison, move to Recently deleted/Undo, password-protected backup through Android's save picker, wrong-password feedback, checked restore preview and successful restore through the real file picker.
- Native phone captures reviewed in light/dark and at 1.3× text. Independent finish review found no material layout defects; the return-label correction was applied. Captures/public QA photos stay ignored and are not shipped.
- GitHub Actions runs unit/lint/build checks and publishes a debug APK artifact after successful pushes. Device/manual results above are local emulator checks, not CI or physical-phone benchmarks.

## Next

1. Detailed habit editor: everyday tracking, schedules, instructions, duration and optional product/reference sections; readable routine text export.
2. Products/references, then nutrition screenshot review, Hevy file import and Health Connect per IMPLEMENTATION_PLAN.md. These are not active integrations.

Remaining limits: saved copies cap the longest side at 2048 pixels; backups cap at 512 MiB and journals at 500 photos. Forgotten backup passwords cannot be recovered. Exported backups are encrypted; the live Room/photo store uses Android private storage, without separate app-level at-rest encryption. App lock, alignment, hosted generation, optional-weight evaluation, full TalkBack audit, physical-phone benchmarks and clinical review remain pending. No trained PSL/bone model or expert-equivalent accuracy is claimed.

## Checks

JDK 21; SDK37; pinned tools/dependencies in Gradle catalog. Local JDK: `%USERPROFILE%/.cache/ritloom/jdk-21.0.13+9`.

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Connected tests only on an owned test device/emulator: the runner uninstalls the app.
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Load only task-specific specs; keep this report to current done/verified/next.
