# Aesthory implementation plan

Build in this dependency order. Appearance coaching is the core; routines and tracking support it. Each milestone has an observable gate. STATUS.md records actual completion; descriptions here do not claim that a feature exists. Details live in PRODUCT.md, ONBOARDING_SPEC.md, and COACHING_SPEC.md.

## 1. Reproducible foundation — complete; coaching groundwork added

- Compact AGENTS.md, pinned single-module Android build, verified Gradle wrapper, isolated JDK/SDK, private default manifest, and tested calendar recurrence.
- Rebrand to user-selected Aesthory. Add optional baseline measurements, coach modes, specialist policy assets, and local model-selection gates.
- Gate: tests, debug assembly, lint. No launcher, live AI, UI, persistence, or clinical-accuracy claim at this milestone.

## 2. Local profile, routines, and history — complete

- Add verified stable Room/KSP, exported schemas, repositories/Flow, and focused transaction/migration tests.
- Persist no-login profile, goals/age band/tone, dated measurements, categories, routine membership/order, habits, instruction versions, schedules, and completion events.
- Typed tracking configurations: checkbox/count/duration/value/rating/checklist. Preserve units and the definition used when recorded.
- Optional measurements remain empty when unknown. Credentials belong in a separate encrypted store, never plaintext Room columns.
- Gate: create/edit/reorder/archive/reload routines; complete/undo after repository recreation; retain historical instructions and dated measurement updates. Test invalid values, calendar boundaries, pauses, and transactions.

## 3. First UI: setup, Today, and AI settings — usable pilot

- Revised per user feedback: engaging, colorful native Material 3, ordinary language, immediate built-in guidance and optional advanced AI settings. DESIGN.md and UI_BRIEF.md hold the direction.
- Compose/ViewModel: resumable optional baseline setup, Today, routine list/basic editor and unconfigured states. Four tabs need no navigation library yet; add Navigation 3 when nested routes warrant it. Five focus choices are not five working chat screens.
- Optional masked Groq key entry, Get API key browser link, Keystore-backed encryption, explicit connection test/model listing, replace/delete/disable, and quota/offline/errors. Add networking permission/libraries with this real feature.
- No app email login. Provider authorization is separate; no unimplemented connector may appear working.
- Gate: finish without key/photos/measurements/permissions; create/complete/restart a routine; verify key deletion and no leakage. Check light/dark, enlarged text, TalkBack, touch targets, and Back on Android.

## 4. Sharing and recovery before extensive private data

Routine JSON files, readable text plans and portable encrypted backup/restore are implemented. Full backups use password-derived authenticated streaming encryption, staged validation and transactional record replacement with independent photo files. Schema 3 includes libraries/versioned links and reads schema-2 backups from 0.3.0. Sharing excludes private library content. Wrong-password, tamper, migration, rollback and native recovery checks pass; see BACKUPS.md and HABITS.md.

- Versioned routine JSON packs and readable text; selected public product/reference details only. Exclude API keys, private photos, nutrition, and completion history by default.
- Validate size/schema/IDs/links; preview; import atomically into independent copies with explicit duplicate/category handling.
- Separate full backup: passphrase-based portable key derivation, authenticated encryption, attachment hashes/limits, validated restore, rollback. A device-only key cannot be the sole route to portable recovery.
- Gate: round-trip packs/backups; test wrong passwords, tampering, future versions, duplicates, interrupted restore, exclusions, and no partial persisted state.

## 5. Specialist text coaching and knowledge

Built-in rules, five starter routines and 11 searchable guides now work without a language model. Guide sources, forum terminology and procedure education are distinguished; procedure material is age-gated. This is a small curated library, not exhaustive forum knowledge or weight training. Hosted generation/retrieval evaluation below remains pending.

- Load common policy plus one Skin/Hair/Face/Physique/Mentor prompt with small user-approved context; no five-model fan-out.
- Curate licensed/attributed dermatology and training sources, dates, and evidence grades; small local full-text retrieval first. Forums remain anecdotal/untrusted.
- Validate typed output/source IDs; user reviews priorities and proposed habits. Prompt configuration and retrieval are not weight training.
- Evaluate candidate models on actual tasks; verify account access and limits, cancellation, 429 behavior, and local usage budgets. Model listing cannot establish a free billing tier.
- Gate: real UI/API/storage flow, malformed JSON, injection, fake citations, removed key, offline mode, and unsupported/unsafe advice cases. No clinician-equivalent claim.

## 6. Private photos and AI photo review

Local pilot delivered early: Photo Picker preview, bundled MediaPipe, capture gates, aspect-correct proportions, goal-based styling options and optional imported LiteRT-LM draft path. Saved private photos now have date/area/view/notes, basic earlier/later comparison, Recently deleted/undo, metadata stripping, bounded thumbnails and encrypted recovery. See LOCAL_ANALYSIS.md and BACKUPS.md. Alignment, CameraX, app lock, hosted photo review, actual optional-model evaluation and remaining gates below are pending.

- Photo Picker, private originals, metadata-stripped outgoing derivatives, bounded thumbnails, view/date tags, notes, delete/undo, alignment and comparison. CameraX only when guided capture is built.
- Quality checks, selected-image preview, explicit remote consent. No public image URL or facial-identity database.
- Available vision model plus preview opt-in; no text-only/paid fallback. Report assessability, observations/uncertainty, source-graded actions, cannot-determine items, and relevant care guidance.
- App lock and intentional screenshot/recents/backup behavior. Save actual image references and model/prompt/source versions.
- Gate: galleries, low storage, rotation/restart/restore/access control; varied lighting/skin tones, poor-quality images, unsupported medical/anatomical claims, wrong references, model loss, and quota errors. Real domain review before expert-quality claims.

## 7. Detailed habit editor, timers, and reminders

Detailed editor and timers/reminders delivered: all six tracking types, optional detail sections, versioned links/history, manual/timed check-ins, ordering/state recovery, persistent timer anchors, explicit review, local reminder times, quiet hours, snooze and permission/reboot/timezone handling. Backups restore paused timer/reminder state. Weekly targets and measured physical-phone delivery/performance remain pending. See TIMING.md.

- Expandable tracking/reference/duration/instructions/schedule/category/product sections; shorter busy-day variants, reusable ordered routines, and all tracking types.
- Weekly targets, local time intent, quiet hours, grouped reminders, snooze, reboot/timezone/DST, and permission/battery states.
- Separate due logic from delivery. Persist timer anchors and derive elapsed time across process death.
- Gate: complete/partial/skip/undo preserve history; timers survive restart; deterministic date tests plus real-device reminders. No exact-delivery promise for deferrable work.

## 8. Products and personal references

Local library delivered: product name/brand/type/size, label/personal notes, web link, opening/expiry/label-month dates, stock and dated observations; text/Markdown references, source dates/links, bounded reviewed file import, search and archive/recovery. Habit snapshots and backups preserve old linked content after library edits. Purchase details, product photos/barcodes, PDF/image attachments, permanent library deletion and AI retrieval remain pending. See HABITS.md.

- Product library with label dates/period after opening, stock, observations, optional purchase details, and links to instruction steps.
- Local text/Markdown/selected attachments, search, original sources/dates, safe links, and retrieval permissions. PDF viewing and extracting usable text are separate capabilities.
- Gate: reuse products across routines, preserve historical observations after edits, import/remove references safely, and cite original sources in coaching.

## 9. Nutrition screenshots

- Android share receiver/Photo Picker and bundled on-device ML Kit OCR. Start with a real screenshot from the user's calorie app.
- Editable review: date, consumed calories/macros, units, missing/uncertain values, goals versus remaining amounts. Manual entry works without AI.
- Screenshot hashes/provenance; updated daily snapshots replace after review, never silently add.
- Gate: real-layout fixtures, duplicates, wrong dates, corrected totals, OCR uncertainty, offline operation, and invalid AI output.

## 10. Hevy imports

- Obtain actual CSV/shared-text samples; use a maintained CSV parser, preserving units/dates/exercises/sets/provenance.
- Preview, transactions, duplicates, explicit corrected-export conflicts, exercise history and workout summaries. Food intake and activity energy remain separate metrics.
- Gate: repeated import is idempotent; quoted commas/newlines, unit/date variants, partial files, and rollback are tested. No paid API.

## 11. Health Connect and optional Google Health cloud

- Verify records actually shared by the user's Fitbit/Google Health app. Health Connect is the local broker; Google Health API is separate OAuth cloud access.
- Steps read access first; aggregation/source/freshness, foreground/manual refresh, revoked/missing data. Background/history permissions only for demonstrated needs.
- Investigate cloud access for missing history/device records: free access, scopes/quotas, secure native OAuth, and no packaged confidential client secret. If secure no-server authorization is unavailable, retain local/manual options and document the gap.
- Gate: both sources can be selected independently; preference/reconciliation avoids double counting. Test overlap, units, dates/timezones, deletions, partial sync, reconnect, and revocation. No retired Fitbit Web/Google Fit APIs.

## 12. Progress, experiments, and reviews

- Charts/weekly summaries from real measurements, workouts, nutrition, habits, and photo observations; source/completeness visible.
- One-change-at-a-time experiments: baseline/start/adherence/observations; association is not proof of cause.
- Coach-to-habit actions, product start/stop timeline, comparable photo sets, and goal review. Add widgets if daily use warrants them.
- Gate: histories survive edits/imports; distinguish missing from zero; grounded claims. No invented face/body scores or diagnostic progress metrics.

## 13. Optional Notion/Obsidian connections

- Implement chosen file/vault import/export or safe Notion access after validating the exact scope and credentials.
- Separate attachments/text extraction from page access; no bundled confidential OAuth secret or broad permissions.
- Gate: selected scope, revoke/reconnect, duplicates/updates, source links, untrusted content, and original text preservation.

## 14. Release quality and measured optimization

- Review tooling/target API upgrades, icon, release shrinking/signing, migrations/backups, accessibility, battery, permissions, key handling, and outgoing payloads.
- Model/prompt/source regression cases with varied people/cameras/lighting; qualified domain review before expert-quality claims.
- Baseline Profiles and hardware startup/scroll/image-memory measurements with realistic data. Set budgets from evidence.
- Gate: installable release candidate with recoverable private data and repeatable checks. Distribution, account provisioning, and any costs remain explicit separate decisions.

## Every milestone

Verify the actual UI/API/storage path where it exists, errors, and recovery; add focused tests for meaningful logic/security boundaries. Record exact results and next action in STATUS.md. A source review or unit test alone cannot establish live integration, clinical performance, or rendered UI.
