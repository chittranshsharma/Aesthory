# Health Connect steps

Open You → Daily steps, grant read access, then refresh. Seven calendar days are read from the phone's Health Connect provider. The screen shows the check time, time zone, contributing app labels (package IDs when labels are unavailable), and missing-data guidance. Health Connect settings let users change access and data-source priorities.

## Counting and freshness

Each day uses one unfiltered AggregateRequest for StepsRecord.COUNT_TOTAL. Health Connect reconciles overlapping activity sources according to its priorities. Aesthory uses that total directly. It never sums raw records, per-app totals, previous refreshes, workouts or cloud totals. A corrected or deleted source record is reflected by the next successful refresh.

Days start at local midnight in the current system time zone. Past days end at the next local midnight, including 23/25-hour daylight-saving days. Today ends at the captured refresh instant. Missing aggregate values stay unknown, while a reported zero remains zero. The seven-day result is published together after a final permission check. Failures and revoked access clear the previous view.

Reads run off the main thread. Opening/resuming the screen refreshes it. Leaving/backgrounding cancels its refresh and clears its in-memory view. Displayed totals are not persisted, included in portable backups, uploaded or sent to coaching services. Health Connect retains the source records. No Room migration is required.

## Permissions and compatibility

Stable androidx.health.connect:connect-client:1.1.0. Only android.permission.health.READ_STEPS is requested. The app handles provider availability, install/update, permission denial and revocation. Android 14+ uses the framework provider, while supported older devices use the Health Connect app. Both platform permission-usage and legacy rationale routes open Aesthory's local data-use explanation.

Users must enable step sharing in their Fitbit/Google Health or other fitness app and allow that source to write steps into Health Connect. Availability of actual Fitbit/Google Health records depends on that source. This feature reads the local broker. Google Health cloud OAuth is a separate integration.

## Verification boundaries

Repository tests inject clocks and provider results to verify replacement, missing versus zero, denied/unavailable access, revocation and DST boundaries. The owned API35 emulator exercises the real permission screen and framework aggregate read. An empty provider is a valid empty state. Actual phone/watch overlapping records and real Fitbit/Google Health sharing require device validation before source-specific accuracy claims.

## Official references checked 2026-11-07

- [Aggregation and data-source priority](https://developer.android.com/health-and-fitness/health-connect/aggregate-data)
- [SDK availability, permissions and rationale routes](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [Stable library release](https://developer.android.com/jetpack/androidx/releases/health-connect)
