# Backups and saved photos

Aesthory 0.5.0 · format 1 / Room schema 4; reads schema-2/3 backups from 0.3.0/0.4.0. No server, account or paid service is involved.

## Everyday use

- You → My progress photos: choose a still photo, date, area, view and optional note. Saving creates a private copy; the gallery original stays untouched. Coach's “Keep as a progress photo” saves its current preview, not the analysis report.
- Select two photos with the same area/view, then Compare. Earlier/Later follows date, then time added. This is a visual reference, without an inferred progress score. Similar lighting, framing and expression matter; alignment is not implemented.
- Move unwanted photos to Recently deleted; Undo or Bring photo back reverses that. They stay until explicitly deleted permanently and are included in backups. Deleting here does not remove copies in existing backup files.
- You → Backup & restore → Make a backup: choose and repeat a password of 12–256 characters, prepare the file, then choose where to save with Android's system picker. Keep the password separately. It is never stored; there is no password recovery.
- Restore: choose a `.aesthory` file and enter its password. After full checks, review the date/counts and confirm Replace & restore. This replaces current records/photos; it does not merge them. Save a separate current backup first if needed. Setup also offers restoration before creating a profile.

## Included and excluded

The snapshot contains profile, measurements, categories, routines, habits, immutable instruction versions, dated completion events/timed seconds, products, personal references, dated product observations, versioned links, active timer time, reminder intentions/preferences and photo metadata. Archived library entries and all journal originals, including Recently deleted, are included. Thumbnails regenerate during restore.

Export freezes only the backup's timer copy as paused; the live timer keeps its state. Restored timers require review, and restored reminder choices have pause-all enabled. Device scheduling cache/tokens, alarms, notification channels and OS permissions are excluded. See TIMING.md.

API keys, Android Keystore material, imported language-model files, temporary analysis/drafts, ordinary preferences and last-backup status are excluded. Restore leaves existing keys/model files/preferences as they were on the destination phone. A new phone needs optional AI setup again.

Automatic Android cloud backup and device transfer of private app data are disabled by manifest/rules. Explicit export can go to any location the user selects, including a document provider. Aesthory makes no automatic upload. Uninstalling deletes app-private records/photos, but a completed backup saved elsewhere remains available.

## Encryption format

The outer 32-byte header is big-endian: ASCII `AESTHBK1` (8 bytes), format `1` (32-bit integer), PBKDF2 rounds `600000` (32-bit integer), random salt (16 bytes).

PBKDF2-HMAC-SHA256 derives a 256-bit portable key from the exact password. Google Tink Android 1.23.0 `AesGcmHkdfStreaming` provides authenticated streaming encryption with HKDF-HMAC-SHA256, AES-256, 1 MiB segments and zero first-segment offset. The entire outer header is associated authenticated data; Tink supplies its own streaming header/nonces/segment authentication. This is the library's stream format, not a custom chunk cipher. Derived byte arrays and caller password arrays are cleared on completion/failure; this is not a guarantee of erasing immutable UI strings or all runtime copies from RAM.

The authenticated payload is a ZIP containing exactly `records.json` plus `photos/<canonical-UUID>.jpg` for every metadata row. SHA-256, byte length and decoded dimensions must match. No external keyset or device-only key is required. Password strength matters for offline guessing; there is no external security-audit claim. Schema 2/3 upgrades with missing newer fields/tables before validation; future schemas are rejected. Schema-4 backups require app 0.5.0 or later.

Sources: [Tink releases](https://github.com/tink-crypto/tink-java/releases), [streaming implementation](https://github.com/tink-crypto/tink-java/blob/main/src/main/java/com/google/crypto/tink/subtle/AesGcmHkdfStreaming.java), [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography), [OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Storage and recovery invariants

Photos use app-private UUID filenames; saved JPEG copies apply orientation, strip source metadata and cap the longest side at 2048 pixels. Thumbnails use separate paths and a maximum 320-pixel side. Saving an analysis preview retains that preview's lower resolution. This is not archival original-resolution storage. Room/photo files rely on Android's private-storage protection; this feature encrypts exported backups, not the live database/journal at rest.

Backup creation captures a transactional record snapshot, validates it, checks referenced photos and prepares an encrypted private file before opening the export destination. Failed external copying can leave a partial destination file; retry saving the prepared file or delete the incomplete file. Only successful copy completion updates last-saved status.

Restore authenticates the entire ciphertext to EOF before parsing any archive. Strict UTF-8, exact format/schema/table/column allowlists, bounded nesting/types, domain constructors, historical-version references and foreign keys are checked in a staging database. Unexpected/duplicate paths, entries, unsupported versions, missing/corrupt photos and oversized content fail before live changes.

After confirmation, restored photos install under fresh file keys; existing files are never overwritten. One Room transaction replaces the records only after all new files are ready. Failure before commit rolls back records and removes newly installed files. Successful commit is followed by best-effort orphan cleanup. On next startup, interrupted private staging and media not referenced by the live database are cleaned. Temporary decrypted restore staging is private and removed on completion/cancel/failure or next startup; secure deletion on flash is not promised.

Limits: 512 MiB backup and inflated payload; 500 photos; 8 MiB per saved JPEG; 16 MiB records JSON; 100,000 total rows; 501 archive entries; JSON nesting 16. Picker input is capped at 50 MiB/120 megapixels and must be a supported still image. Space checks are conservative and may require substantially more free space than the encrypted file. A library/format/schema change needs an explicit compatibility plan, never a destructive reset.

## Verification

Unit coverage includes multisegment encryption, random ciphertext/Unicode passwords, wrong passwords, header/tag tampering/truncation, future versions, bounded parsing, invalid records, transaction rollback, snapshot roundtrip, live Flow refresh and migration from the exported v1 schema. The Android vault test covers rotated/location-tagged JPEGs, metadata removal, source independence, thumbnail regeneration, encrypted photo/bin recovery, credential/model preservation and failed installation leaving current data intact. Current executed counts and rendered/manual checks live in STATUS.md. Emulator checks do not establish physical-phone performance or a complete accessibility/security audit.

Nutrition update: payload schema 6 adds daily totals and duplicate-image receipts, reads payloads 2–5, and excludes screenshot images and OCR text. Room migration 5→6 preserves existing records. See NUTRITION.md.
