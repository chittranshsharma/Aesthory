# Status

2026-11-06 · Aesthory 0.6.0 · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Nutrition: You → Nutrition imports a selected screenshot with bundled on-device OCR. Review the preview, choose its date, edit calories/protein/carbs/fat and explicitly confirm consumed daily totals before saving. Manual entry works offline; missing values remain unknown.
- One nutrition snapshot per day; same-day saves require replacement confirmation. Transactional revision checks reject stale confirmations, and persistent image-hash receipts detect repeated screenshots even after replacement. Saved totals and receipts join encrypted backups; screenshot images and OCR text are not retained. See NUTRITION.md.
- Room schema 6 and backup payload 6 retain old records and read older payloads 2–5. No destructive reset. Existing routines, detailed habits, products/references, weekly targets/review, timers/reminders, private progress photos, sharing, encrypted recovery and built-in coaching remain available.

## Verified

- **118 unit tests passed**, zero failures/errors. Nutrition cases cover clear/ambiguous labels, wrong units, invalid/zero/missing values, duplicate imports, explicit replacement, stale confirmations, original-v5 migration and backup validation/recovery.
- **6 Android tests passed** on the owned API35 emulator with Wi-Fi/mobile data disabled. Real bundled OCR read a generated image; corrected totals persisted after database reopening; duplicate/replacement rules and encrypted recovery passed alongside existing timer/photo/library checks.
- Debug and test APK assembly plus lint passed: **0 errors, 23 warnings**; no suppressions.
- Manual UI: Photo Picker → local OCR → date changed to November 4 → calories corrected from 1,850 to 1,900 → explicit save. Reimport displayed the already-saved date; editing the saved entry required replacement confirmation and retained one daily entry. Light/dark and 1.3× text inspected. Captures and fixture images stay ignored.
- These are emulator/generated-fixture checks, not physical-phone performance measurements or validation against every calorie-app layout. Full TalkBack/domain review remains pending.

## Next

1. Hevy file import: bounded CSV preview, explicit units/dates, exercise/set history, duplicate and corrected-export handling. Official export guidance checked; actual user export will expand format coverage, not gate all implementation.
2. Health Connect steps, then broader progress insights and coaching per IMPLEMENTATION_PLAN.md.
3. Expand nutrition recognition against real calorie-app samples; add saved-day date correction/deletion and share-sheet receiving. Current saved-day editing changes totals only; choose the correct date before first save.

Limits: nutrition OCR supports conservative English labels; complex layouts may need manual entry. No Hevy/Health Connect/live Notion integrations yet. Optional language-model weights remain unevaluated; Groq settings currently test connectivity only. Timer alerts remain subject to Android delivery restrictions.

## Checks

JDK 21; SDK37; pinned dependencies. Use the checked-in wrapper:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Connected tests uninstall the app: use only an owned test emulator.
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Feature contracts: NUTRITION.md, WEEKLY.md, TIMING.md, HABITS.md, BACKUPS.md.
