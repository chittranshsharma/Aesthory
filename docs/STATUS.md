# Status

2026-11-02 · Aesthory 0.4.0 · Android only · target phone 8 GB+ RAM; exact hardware unknown.

## Done

- Added a detailed action editor under Routines → Details → Edit action. Optional sections cover instructions/reason/busy-day version, dates/selected weekdays/day intervals, all six tracking types, duration estimate, categories, tags, products and references. Save stays visible; Back protects unsaved changes.
- Added You → Products & references, also reachable while editing a habit. Reusable products hold brand/type/size, label/personal notes, links, opening/expiry/label-month dates, stock and dated observations. Notes/links support source dates, reviewed UTF-8 text/Markdown file import, search and archive/recovery.
- Saved habit versions snapshot linked details, so later library edits never rewrite past instructions. Added partial/skip logging, ordering, pause/archive/recover and a history viewer with the original steps and linked content. Dated observations keep the product name used at the time.
- Added readable routine text plans. JSON/text sharing retains routine definitions and excludes library content, observations and private history. Existing version-1 pack fingerprints stay stable; richer definitions use pack version 2.
- Added non-destructive Room v1→v2→v3 migrations. Full encrypted backups now include libraries, observations and link snapshots; v0.3.0 backups still restore. Preserved saved photos, everyday built-in coaching and optional AI settings.

## Verified

- **88 unit tests passed**, zero failures/errors. New cases cover historical snapshots, edit rollback, ordering/state recovery, library backup/sharing exclusions, original-v2 migration/backup compatibility, file/URL validation and time-goal roundtrips.
- **4 Android tests passed** on the owned API35 x86_64 emulator. Extended vault coverage restores products, references, observations and use notes alongside photos while preserving keys/models and failed-restore recovery. Final rerun used instrumentation directly to retain manual test fixtures.
- Final debug/test APK assembly and lint passed: **0 errors, 16 warnings** (existing update/icon/KTX/storage refinements; no suppressions).
- Native manual flows: actual v0.3.0 backup recovery; product creation; Markdown picker/import/review; linked habit creation; rotation with draft retained; partial/full checklist logging; history after tracking edits; product observation/archive/bring back; readable text export through the save picker; dirty Back/discard; force-stop/relaunch retention.
- Light/dark phone captures and 1.3× text checked. Fixed clipped section labels, kept Save visible, made checklist rows tappable, and added spacing between wrapped choices. Independent finish review: ship, with the spacing fix confirmed. Photos/captures and test files remain ignored.
- GitHub Actions runs unit/lint/build checks and uploads a debug APK after successful pushes. Emulator results are not physical-phone benchmarks or a complete accessibility audit.

## Next

1. Running timers, weekly targets and local reminders; preserve timer state across restart and handle notification/timezone/battery conditions.
2. Nutrition screenshot review, then Hevy file import and Health Connect per IMPLEMENTATION_PLAN.md. A real calorie-app screenshot/export will be needed for layout-specific validation.

Limits: time spent is logged manually; no running timer/reminder yet. References support text/Markdown and links, not PDF/image attachments or live Notion/Obsidian sync. Product photos/barcodes/purchase details, permanent library deletion, AI retrieval/generation, optional-weight evaluation, physical-phone benchmarks and full TalkBack/domain review remain pending. Archived library content remains stored and backed up. See HABITS.md and BACKUPS.md for bounds and recovery behavior.

## Checks

JDK 21; SDK37; pinned dependencies in Gradle catalog. Local JDK: `%USERPROFILE%/.cache/ritloom/jdk-21.0.13+9`.

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Connected tests only on an owned test device/emulator: this task uninstalls the app.
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/`; APK: `app/build/outputs/apk/debug/app-debug.apk`. Keep this report to current done/verified/next; task-specific details live in HABITS.md.
