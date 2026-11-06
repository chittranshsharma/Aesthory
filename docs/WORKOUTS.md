# Workout imports

Hevy file import is local: choose the original workout CSV in You → Workouts, review dates, time zone, source units and every set, then explicitly save. No account, paid API, network request or cloud model participates. The source file is read into bounded memory and is not retained by Aesthory.

## Contract

- UTF-8 (optional BOM), RFC4180 quoting through Apache Commons CSV 1.14.1. Up to 5 MiB, 1,000 workouts, 20,000 rows per file and 2,000 sets per workout. Invalid rows reject the entire preview; no partial acceptance or guessed missing numbers.
- Required headers: `title,start_time,end_time,exercise_title,set_index,set_type,reps`, plus exactly one of `weight_kg,weight_lbs`. Optional: description, exercise_notes, superset_id, distance_km or distance_miles, duration_seconds, rpe. Duplicate/unknown headers and ambiguous unit columns are rejected.
- Dates: English `d MMM uuuu, HH:mm[:ss]` or ISO local date/time. Preview starts with the phone's zone; the user can change it before saving. Each file uses one zone. DST gaps/repeated hours are rejected, not silently shifted. Workouts retain local times, zone and epoch milliseconds; durations use instants.
- Units and set indexes remain as exported. Empty numbers remain unknown; zero stays zero. Weights/distances/seconds/RPE must be finite and nonnegative; reps and indexes are integers. No calorie expenditure is inferred from workouts.
- Identity is SHA-256 of canonical local start time. A rename, changed sets or timezone correction becomes a replacement candidate. Conflicting workouts at the same start time inside one file, or repeated exercise-name/set-index pairs, are rejected because this export has no stable workout/exercise-instance IDs.
- Content hashes exclude file formatting and unrelated workouts, so repeated exports skip unchanged sessions. Changed sessions show both imported and saved sets and require an explicit replacement checkbox. An old export after a correction is another visible conflict, never a silent overwrite.
- Saving rechecks every reviewed revision inside one Room transaction. A stale preview refreshes without writing any rows. A storage failure rolls back the entire batch. Missing workouts in a later export never delete saved workouts.
- History shows the latest 1,000 sessions and their per-exercise sets. Older sessions remain stored and included in backups. Imported strings are displayed as data, not rendered markup or instructions.

## Persistence

Room schema 7 adds workouts and workout_sets, linked by a foreign key. Migration 6→7 leaves existing records intact. Backup payload 7 includes both tables; payloads 2–6 upgrade with empty workout tables. Restore validates dates, units, set structure and content hashes before replacing live records. Routine packs exclude workout history.

## Current limits

CSV first; pasted shared text and Android share receiving remain separate work. English/ISO dates only; no locale guessing. This export lacks stable IDs: editing a workout's start time makes it a new candidate, so the review warns users to check history first. Duplicate exercise names with reset indexes require a less ambiguous export. No workout deletion or manual set editing yet; corrections come through explicit reimport. Unconfirmed previews do not survive process death. Physical-phone performance and actual user exports remain unverified until measured/supplied; generated fixtures are not real-user-format coverage.

## Evidence (2026-11-06)

- [Hevy export instructions](https://help.hevyapp.com/hc/en-us/articles/43708290987415-Exporting-Your-Data-from-Hevy) establish the export path; they do not publish a complete schema.
- [Liftosaur's importer source](https://github.com/astashov/liftosaur/blob/master/src/utils/importFromHevy.ts) corroborates the core column names and kg/lb variants. Used as format evidence, not copied implementation. Aesthory uses stricter all-or-nothing validation.
- [Apache Commons CSV release page](https://commons.apache.org/proper/commons-csv/download_csv.cgi) identifies stable 1.14.1; its parser handles quoted delimiters and multiline fields.
