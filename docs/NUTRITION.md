# Nutrition screenshot import

Open You → Nutrition → Import a screenshot. Android's Photo Picker grants access to the selected image; no broad gallery permission or account is required. Enter totals myself also works without an image.

## Review and save

The bundled Latin-script ML Kit recognizer runs on the phone. A preview and expandable detected text stay in memory for this review. English single-total lines can suggest calories (kcal), protein, carbohydrates and fat (g). Goals, remaining allowance, budgets, exercise energy, percentages, per-serving values, multiple numbers and conflicting repeated labels are treated conservatively. Unclear values remain blank. No missing nutrients or calories are calculated from other fields.

The review starts with today's local date; dates are not guessed from OCR. Choose the screenshot's actual date, edit every field as needed, and confirm these are consumed daily totals. Blank means unknown and zero remains zero. Save is explicit. Numbers accept up to two decimal places and a decimal dot or comma; manual entry does not accept thousands separators. Limits are 0–20,000 kcal and 0–5,000 g per macro, at least one known value, and no future date. These are input bounds, not recommended intake.

Back asks before discarding a review. Rotation retains the in-memory scan and field draft; unsaved reviews are not durable across process death. Saved records survive reopening. Review this day edits totals for that fixed date; choose the correct date before the first save. Moving or deleting an already-saved day is not included yet. History shows the latest 366 entries; backups keep all records.

## No double-counting

There is exactly one saved snapshot per calendar day. A new snapshot never increments old numbers. If that date exists, show its current totals and ask to replace all four fields, including blanks. A revision token is checked inside the transaction so a stale confirmation cannot overwrite a newer entry without another review.

SHA-256 receipts identify identical screenshot bytes even after another image replaces that day's totals. Reimporting a saved image is blocked and names its original saved date. Re-encoded or cropped images can have different hashes; the one-row-per-day replacement rule still prevents adding them together. The user must confirm the correct date. Manual corrections retain previous image receipts and do not masquerade as fresh OCR results.

## Privacy and recovery

No nutrition image, OCR text or values are sent to Groq. Images and extracted text are not stored in the database or included in backups. Numeric totals, dates, image hashes and revision metadata are personal records: full encrypted backups include them, routine packs do not. Room schema 6 adds nutrition_days and nutrition_imports; migration 5→6 preserves existing data. Backup payload 6 reads payloads 2–5. Older apps need updating before reading new backups.

The input reader accepts PNG/JPEG/WebP, at most 20 MiB and 40 megapixels, and decodes at most 2400 pixels on the longest edge. OCR has a 30-second wait bound and runs off the main thread. Malformed/unsupported files leave saved records untouched. Recognizer resources close after processing.

## Verification and remaining work

Unit tests cover parsing, wrong units, ambiguous layouts, blanks/zero, duplicates, replacement conflicts, backup validation and original-v5 migration. Native tests use a generated screenshot with real OCR, reviewed corrections, database reopening and duplicate protection. Status records actual executed checks. A real calorie-app screenshot will expand layout coverage later; it is not needed to use manual review. Share-sheet receiving, non-Latin recognition, nutrition charts, meal-level logging and calorie recommendations are outside this milestone.

Dependency evidence (2026-11-06): Google's [Android text-recognition guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) documents bundled `com.google.mlkit:text-recognition:16.0.1` and immediate model availability. No dynamic model download is used.
