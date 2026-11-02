# Habits, products and references

Aesthory 0.4.0 · one local profile · no account, model, key or network required.

## Everyday flow

Routines → Add an action, or Details → Edit action. A name and category are enough; General is created when no category exists. Extra sections open on demand:

- Instructions: ordered text, a personal reason and a shorter busy-day version.
- Schedule: every day, selected weekdays or every N days, with a start and optional end date.
- Tracking: check it off, count, time spent, measurement with unit/optional goal, rating or checklist.
- Time & category: optional whole-minute estimate, reusable category and comma-separated tags.
- Products & references: choose up to 20 of each from the library; product use notes belong to this habit. Manage the library without leaving the draft.

Time goals are entered in minutes and stored in seconds; completion logs seconds. Time spent is manual logging, not a running timer. Numeric/checklist check-ins offer Save what I did for partial work, plus Complete and Skip for today. Checkbox completion stays one tap on Today.

Details also supports moving actions up/down, pause/bring back, archive and past check-ins. Show archived actions makes restoration available. The history viewer shows the latest 200 events, including partial/skip/undo, with the definition and linked content originally used. All history remains stored and backed up.

Editors hide the bottom navigation while open and ask before discarding changes through Back. Ordinary Android process-state restoration can recover form fields; force-stop/uninstall is not a saved-draft feature. Save explicitly.

## Your library

You → Products & references, also reachable inside the habit editor. Search products or notes/links; archive and bring them back without breaking existing links.

Products need only a name. Optional fields: brand, type, size/amount, copied label ingredients/instructions, personal notes, web link, opening date, label expiry, label period after opening and stock status. The displayed opening-date-plus-months calculation is a reminder from entered label data, not a safety/condition assessment. Dated observations are append-only and keep the product name used at the time. The detail screen shows the latest 100; backups retain all.

References hold a title, plain text/Markdown as written, optional source date and/or HTTP(S) source link. Text selection works in the reader. Links open only after a tap, in the user's browser; no embedded web rendering, scraping or automatic AI upload. Reject non-web schemes and credential-bearing URLs.

Bring in a text file uses Android's picker and reviews the contents before saving. UTF-8 only, at most 128 KiB/32,000 characters, no binary control characters; PDF/images are not attachments in this milestone. A Markdown file exported from another app is usable, but there is no live Notion/Obsidian connection.

## Persistence and sharing

Room schema 3 adds products, references, product observations, per-habit-version link snapshots, purpose/tags/short instructions. Migrations 1→2→3 retain existing records without destructive reset. Saving a habit adds a version and its snapshots transactionally; a missing linked record rolls the whole edit back. Library edits/archiving never rewrite previous snapshots or observations. Archive retains private content; it is not permanent deletion.

Full encrypted backups include all of these records, archived library entries, observations and versioned links. Current backup payload schema is 3; schema-2 backups from 0.3.0 upgrade with empty libraries and default new fields. The outer encryption format remains 1. Older app versions cannot read schema-3 payloads; keep the original backup and update the app. See BACKUPS.md.

Routine JSON and readable text exports include the habit's instructions, schedule, tracking, purpose/tags/short variant. They exclude product/reference libraries and links, observations and private history. The user must still review their written instructions for private content. Rich definitions use routine pack version 2; original definitions without new fields retain version 1 and the same fingerprint so existing imported files remain idempotent. Both versions import here. The text plan is for reading, not re-import.

## Bounds and remaining scope

Habit title 120 characters; instructions 8,000; reason 1,000; shorter version 2,000; up to 10 unique 40-character tags; estimate 1–1,440 whole minutes; up to 50 unique 240-character checklist items; measurement units 40 characters. Product notes/label text allow 8,000 each, observation 2,000, reference 32,000, URLs 2,048. Backups retain their existing total size/row bounds.

Weekly targets, clock-time scheduling, running timers, reminders, product photos/barcodes/purchase details, PDF/image attachments, permanent library deletion and AI retrieval are not implemented. Inputs are personal records, not verified treatment instructions or evidence of causation. Physical-device performance and a complete accessibility audit remain unmeasured; executed checks live in STATUS.md.
