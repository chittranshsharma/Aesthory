# Timers and reminders

Aesthory 0.5.0 · local Android functionality; no model, key, server or paid service.

## Everyday use

- Start timer from an action's details on Today or in Routines. A Time spent action also offers Start timer as its main daily action. One timer runs at a time; other actions offer Open running timer.
- The timer strip stays accessible outside editors. Pause/Resume persists immediately. Finish & review pauses first, lets you adjust minutes/seconds, date, completion/partial status and any count/checklist/result, then saves explicitly. Discard requires confirmation. Reaching a goal never auto-completes a habit.
- Goals come from Time spent tracking or the optional estimated duration. With neither, the timer is a stopwatch. A timer can run past its goal, up to seven days.
- Notifications can show the running clock and Pause/Resume outside the app. Notification taps open the timer or Today; an open unsaved editor is protected instead of silently discarded.
- You → Reminders & timers controls notification permission, a test notification, pause-all, quiet hours and optional precise timer-goal access. An action's reminder chooses a local clock time and follows its calendar schedule. Inactive actions/routines and completed/skipped occurrences do not trigger reminders.
- A reminder offers Snooze 10 min. Related reminders group in Android's notification shade. Lock-screen public content is generic. Quiet hours postpone routine reminders; timer-goal alerts follow the phone's Timers channel and Do Not Disturb settings.

## Restart and clock behavior

Room stores accumulated milliseconds, elapsed-realtime/wall anchors, boot count, original habit version, start day and session UUID before reporting a timer started. No per-second database writes or continuously running foreground service are needed. While the same boot continues, elapsed realtime includes sleep and ignores wall-clock changes; process death, closing, force-stop and reopening do not reset the calculation.

After a phone reboot (or an unavailable/changed clock identity), wall time supplies a bounded estimate, including possible powered-off time. Recovery pauses the timer and explicitly asks the user to review the estimate. Paused time never accumulates. Finishing atomically inserts one event with the original definition and actual timed seconds, then removes the session. Stale notification actions cannot finish, discard or pause a different session. Data changes do not rewrite historical instructions.

Routine alarms use inexact AlarmManager delivery. Timer goals use exact alarms only when allowed, with an inexact fallback. Notification access is optional; denied/blocked access does not break timekeeping. Precise access cannot override notification, Do Not Disturb, battery or manufacturer restrictions. Force-stop blocks app alarms until reopening. Delivery is not promised at an exact moment.

Boot, package replacement, wall-clock and timezone receivers replan alerts. Local times follow the current phone timezone. DST gaps shift forward; overlaps choose one occurrence per local day. Past reminders are not replayed in a burst on reopening. Receiver delivery validates the saved token, scheduled day, current action state, latest completion and quiet hours. Snooze requires the latest delivered token. Reminder runtime is device-only and is not a source of completion history.

## Storage, backup and bounds

Room schema 4 adds the active timer, reminder intentions/preferences and device scheduling cache, plus optional timed seconds on completion events. Migrations 1→2→3→4 preserve old records. Full backups read schema 2/3 and write schema 4. Export freezes a copy of the active timer as paused without interrupting the live session. Restore keeps it paused for review and restores reminder intentions with pause-all enabled. It excludes scheduling tokens/alarms, OS permissions and notification-channel choices. Routine packs/text plans still exclude these personal settings and timer state.

Limits: one timer, seven days of timed work, 100 enabled reminders, valid minute-of-day values, and the existing backup row/size limits. Reminders are per action, not a weekly-target scheduler. There is no lock-screen activity, full-screen alarm, battery-exemption demand, auto-logging or auto-upload.

## Verification contract

Test anchors across recreated controllers, wall-clock edits, sleep, pause/resume and reboot; duplicate save/goal/callback/snooze actions; permission denial; completion/state suppression; DST/timezone/quiet periods; original-v3 migration and older backup recovery. Native tests must exercise real notification PendingIntents and persistent state; manual checks must include app/process restart and rendered light/dark/enlarged text. STATUS.md records actual executed results. Emulator evidence does not establish physical-phone battery use or delivery reliability.

Primary platform references: [elapsed-realtime clock](https://developer.android.com/reference/android/os/SystemClock), [alarm scheduling and exact access](https://developer.android.com/develop/background-work/services/alarms), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [background receivers](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()), [NotificationCompat](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder).
