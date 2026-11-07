# Status

2026-11-07 · Aesthory 0.8.0 · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Daily steps: You → Daily steps requests read-only Health Connect access, displays seven local-calendar days with freshness and contributing apps, and refreshes on screen resume or tap. Unfiltered platform aggregates handle overlapping activity sources. Each read replaces the in-memory view, including lower/deleted totals. Missing data, unavailable/update states, denied/revoked access and local privacy explanation are covered. See HEALTH_CONNECT.md.

- Nutrition: You → Nutrition reads screenshots with bundled local OCR, offers editable date/calories/macros, and requires explicit save. Manual entry, daily replacement, persistent duplicate-image receipts and encrypted recovery work offline. Images/OCR text are not retained. See NUTRITION.md.
- Hevy: You → Workouts reads bounded CSV files locally, previews new/unchanged/changed sessions with source units and a reviewable time zone, and shows exercise/set history. Reimports skip unchanged workouts; corrections require explicit whole-workout replacement. Revision checks and transactions prevent stale or partial saves. See WORKOUTS.md.
- Room schema 7 and backup payload 7 preserve earlier records and read payloads 2–6. Workouts and sets join explicit encrypted backups; source CSV files are not retained. History screens now correctly explain that older records remain on the phone, not automatically backed up.
- Existing detailed habits, products/references, weekly targets/review, timers/reminders, private progress photos, routine sharing and built-in coaching remain available.

## Verified

- **134 unit tests passed**, zero failures/errors. Added CSV quoting/newlines, decimal and unit/date variants, invalid/partial files, DST ambiguity, duplicate/replacement, stale-preview, rollback, original-v6 migration and backup integrity/overflow checks.
- **8 Android tests passed** on the owned API35 emulator with Wi-Fi/mobile data disabled. Real bundled nutrition OCR, file-based workout parsing, corrected totals/sets after database reopening, duplicate protection and encrypted recovery passed alongside timer/photo/library checks.
- Debug/test APK builds and lint passed: **0 errors, 25 warnings**; no suppressions.
- Manual UI: selected CSV through Android Files, reviewed two workouts, saved, compared old 60 kg versus imported 65 kg, explicitly replaced one while skipping the other, then reimported with 0 new/0 changed/2 already saved. Reopened exercise history showed the corrected sets. Light/dark and 1.3× text checked; final wording reviewed. Nutrition's saved day remains intact after the upgrade. Generated fixtures/captures stay ignored.
- Steps checks cover direct aggregate forwarding, decreasing refresh totals, missing versus zero, unavailable/denied/revoked access, permission loss during a read and DST day boundaries. The real API35 provider permission flow was denied, granted and revoked/reconnected. Offline framework reads returned seven days. Light/dark and enlarged text were inspected, including the local privacy screen.
- These are emulator/generated-fixture checks, not physical-phone performance measurements or universal export/layout coverage. Full TalkBack/domain review remains pending.

## Next

1. Validate actual Fitbit/Google Health step sharing and phone/watch overlap on a physical device. Add Google Health cloud access only for demonstrated missing records after validating secure mobile OAuth and free access.
2. Expand real calorie-app and Hevy-export coverage; support shared workout text/share receiving, saved-date/start-time corrections and deletion. Current Hevy identity uses local start time; changing that time can produce a new candidate and is explained in review.
3. Broader progress insights and coaching per IMPLEMENTATION_PLAN.md.

Limits: nutrition recognizes conservative English labels. Hevy accepts English/ISO local dates and one time zone per file; ambiguous/repeated exercise-set identities are rejected. Health Connect steps show a live seven-day view held in memory, outside backups. Actual Fitbit/watch overlap and older-provider hardware remain unverified. Live Notion integration is pending. Optional language-model weights remain unevaluated; Groq settings test connectivity only. Timer alerts remain subject to Android delivery restrictions.

## Checks

JDK 21; SDK37; pinned dependencies. Use the checked-in wrapper:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Connected tests uninstall the app: use only an owned test emulator.
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Feature contracts: HEALTH_CONNECT.md, WORKOUTS.md, NUTRITION.md, WEEKLY.md, TIMING.md, HABITS.md, BACKUPS.md.
