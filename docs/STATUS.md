# Status

2026-11-05 · Aesthory 0.5.0 + weekly review · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Added You → Weekly review & targets: optional saved targets per Monday–Sunday week, previous-week browsing, latest-result completion totals and recorded timer/manual duration totals. Targets are included in schema-5 backups; old backups and databases migrate. See WEEKLY.md.

- Added one running timer with Pause/Resume, goal alerts and a persistent timer strip. Saved clock anchors survive closing/restarting the app without a background service. Phone-reboot recovery pauses an explicitly labelled estimate for review.
- Finish & review lets you correct the time, date and result before saving once against the original action instructions. Goals never auto-complete actions. History includes the actual timed duration; discard asks first.
- Added per-action local reminders, native time pickers, quiet hours, pause-all, grouped notifications and Snooze 10 min. You → Reminders & timers explains permissions, offers a test notification and optional precise timer-alert settings. Notification controls and taps work outside the app.
- Added Room v3→v4 migration and older-backup compatibility. Encrypted exports freeze a paused timer copy without stopping the live timer; restores keep the timer and reminder delivery paused for review. Runtime alarms/tokens and OS permissions stay device-only. Routine packs exclude personal timing state.
- Retained detailed action editing, products/references, historical snapshots, routine sharing, saved progress photos, encrypted backups and built-in coaching. See HABITS.md, BACKUPS.md and TIMING.md for contracts and limits.

## Verified

- Weekly milestone: unit tests, debug assembly and lint passed. Target persistence/backup recovery and correction, undo, week-boundary, missing/zero-time and future-date totals tested. Emulator showed the existing 47-second check-in; saving a five-action target and navigating between weeks retained it. Light/dark and 1.3× text were inspected. Prior five native tests below were run for the timer milestone, not rerun for this change.

- **108 unit tests passed**, zero failures/errors: clocks/restarts, pause/resume, atomic and duplicate saves, simultaneous reminders, snooze/stale callbacks, completion suppression, quiet hours, DST/timezones, migration and paused backup recovery.
- **5 Android tests passed** on the owned API35 x86_64 emulator. Real notification PendingIntents exercise Pause/Resume, reminder delivery and Snooze; vault tests cover encrypted timer/reminder restoration alongside existing photo/library recovery.
- Debug/test APK assembly and lint passed: **0 errors, 22 warnings** (dependency-update, icon, KTX and storage refinements; no suppressions).
- Native manual checks: running time continued after force-stop/reopen; paused time stayed fixed; actual emulator reboot recovered a paused estimate. Reviewed and saved a corrected 47-second check-in. Test notification appeared and opened Today; saved reminder time persisted; notification-off state and Android permission request worked. Pause-all, native time picker, light/dark and 1.3× text checked.
- GitHub Actions checks unit tests/lint/build and uploads a debug APK. Captures, private records, backups and optional weights remain ignored. Emulator results establish neither physical-phone performance nor delivery reliability; full TalkBack/domain review remains pending.

## Next

1. Nutrition screenshot and Hevy export samples are pending from the user; weekly targets/review are implemented.
2. Nutrition screenshot → local OCR → editable review → explicit save, then Hevy file import and Health Connect. A real calorie-app screenshot/export will be needed for layout-specific validation.

Limits: one timer, seven-day timer cap, 100 enabled action reminders. Android may delay alerts; force-stop blocks them until reopening. References support text/Markdown and links, not PDF/image attachments or live Notion/Obsidian sync. Product photos/barcodes, permanent library deletion, AI retrieval/generation, optional-weight evaluation and physical-phone benchmarks remain pending.

## Checks

JDK 21; SDK37; pinned dependencies in Gradle catalog. Local JDK: `%USERPROFILE%/.cache/ritloom/jdk-21.0.13+9`.

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Connected tests only on an owned test device/emulator: this task uninstalls the app.
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Keep this report to current done/verified/next; feature details live in the linked guides.
