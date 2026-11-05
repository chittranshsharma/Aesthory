# Weekly targets and review

Open You → Weekly review & targets. Weeks run Monday–Sunday using the phone's current local date. Previous weeks are read-only; current-week targets are optional completed-action and recorded-minute goals. They apply to that week only and do not silently roll forward. Blank removes a target. No paid service or AI is involved.

The review selects the latest check-in ID for each action/date. Done counts once, partial and skipped are shown separately, and undo removes the previous result from totals. Pausing or archiving does not delete historical activity. Future-dated check-ins do not contribute before their date. This is recorded activity, not an adherence percentage: missing check-ins are not failed or zero-time actions.

Recorded time uses saved timer seconds, otherwise the original definition's manual duration value. It never adds both for one result and never uses estimated duration. Partial results contribute time. Unknown time stays unknown; an explicitly recorded zero remains zero. Dates stay attached to their intended calendar day when the phone changes time zone.

Room schema 5 adds weekly_targets. Migration 4→5 preserves existing data. Targets are included in encrypted backup payload schema 5, which also reads payloads 2/3/4. They are personal history and stay out of routine packs. Target bounds: 1–10,000 completed actions and 1–10,080 minutes, or blank; stored week keys must be Mondays. Existing backup record/size limits apply.

Future additions: optional per-action targets, repeat-next-week choices and comparisons. These are not controls in this version.
