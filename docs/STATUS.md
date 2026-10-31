# Status

## Current milestone

Step 1 — project foundation complete; appearance-coaching/setup groundwork revised and verified (2026-10-31, Asia/Calcutta). Step 2 persistence is next.

## Completed

- User selected **Aesthory**. Updated the project/app name, application ID/namespace, source/test packages, and appearance-first product scope.
- Created compact AGENTS.md, staged implementation plan, and dated research notes.
- Repository began empty. Current branch: `codex/aesthory-foundation`.
- Single Android app module with AGP 9.2.1 (built-in Kotlin), checksum-pinned Gradle 9.4.1, minSdk 28, compile/target SDK 36.
- Original pure-Kotlin calendar scheduling: daily, selected weekdays, every N days, inclusive date bounds, validated inputs.
- Private default manifest: no network/health permissions, no automatic cloud/device-transfer backup of app records.
- Gradle wrapper scripts/JAR verified against upstream; license retained. No Android sample application code copied.
- Prepared JDK/SDK under `%USERPROFILE%/.cache/ritloom`; global Java 27 installation unchanged. Ignored local.properties points to that SDK.
- Cache directory retains its original name solely as a working tool path; app branding and packages use Aesthory.
- Defined no-email setup with optional measurements, goals, age band, tone, and bring-your-own-Groq-key controls, plus separate optional Health Connect/cloud Google Health data paths.
- Added five packaged specialist prompt assets and common policy; pure-Kotlin profile/measurement validation and model routing. Routing blocks disabled/unconfigured AI, missing cloud/photo consent, disabled preview vision, invalid image counts, absent models, and text-only image fallbacks.
- Reordered the plan to bring setup and specialist/image coaching earlier. The plan now has 14 scoped milestones; focused setup/coaching documents avoid enlarging always-loaded instructions with every detail.

## Verification

Executed using Temurin JDK 21.0.13+9, Gradle 9.4.1, Android platform 36, build-tools 36.0.0:

- `:app:testDebugUnitTest`: **24 tests**, zero failures/errors/skips: 10 calendar, 10 model-routing, 4 profile-baseline tests. Routing cases cover consent/capability boundaries, approved fallback behavior, absent vision, and invalid image requests. Profile cases cover optional data, units, and rejection of nonfinite/invalid measurements.
- `:app:assembleDebug`: passed. Foundation APK produced; no launcher/UI at this milestone.
- Latest combined `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: passed after the rebrand and new Kotlin/assets. Lint: zero errors, five existing warnings. Earlier forced lint also verified Windows SDK-path escaping.
- Remaining lint warnings: newer AGP/Gradle and compile/target API advisories; missing application icon. Keep the verified compatible toolchain for this milestone, review current versions before release, and implement the icon with the UI. None were suppressed.
- Authored text whitespace and manifest/backup XML parsing checked.

No emulator/device run, UI verification, release performance benchmark, actual Fitbit sync, real nutrition screenshot, Hevy file import, or Groq request has occurred. API-key storage/button/setup screens and real image reports are not implemented. No trained weights, curated/retrieved knowledge corpus, clinical accuracy, or app-level encryption is claimed. Prompt assets and routing are groundwork, not a live AI feature.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`. Outputs are ignored build artifacts.

Local Windows check command (PowerShell; adjust the JDK path on another machine):

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.cache\ritloom\jdk-21.0.13+9'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## Next action

Start step 2 in IMPLEMENTATION_PLAN.md: stable Room/KSP, local profile and dated measurement persistence, routines/habits/instruction versions/completion events, repository flows, and transaction/migration tests. Preserve the existing calendar/profile/routing policies. Step 3 delivers the first setup UI and encrypted key settings; specialist text and photo coaching follow steps 5–6. Use ONBOARDING_SPEC.md and COACHING_SPEC.md for the user's new requirements.
