# Aesthory

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated by the user: native Kotlin and Jetpack Compose with Material 3. Room for structured records, Coroutines/Flow and ViewModels for state, Android Keystore for encrypted credentials. DataStore remains available for future ordinary preferences when needed. Android 9+ (API 28), compile/target API 37; current Compose dependencies require API 37. Bundled MediaPipe face landmarks and optional LiteRT-LM support local analysis/coaching. The user reports an 8 GB+ RAM phone; exact model/chipset and on-phone benchmarks remain unknown.

## Users and purpose

Initially the owner, improving skin, hair, facial presentation, and physique with distinct specialist coaching segments, private photo reviews, and measurable routines. Workouts, steps, nutrition, products, and habit tracking support that appearance-coaching purpose. Other people may install independent copies and exchange routine packs. Success means actionable, personalized priorities grounded in actual observations and records, with useful private progress history.

## Confirmed constraints

- Android only. Free services and tools; no subscriptions, paid Hevy API, or required backend.
- Personal records live on the device under one local profile initially. No social feed, messaging, multiplayer, or community.
- No app email/password login. Setup collects chosen goals and relevant optional baseline metrics; measurements and photos can be added later. Optional source authorization does not create an Aesthory account.
- Optional Groq processing is remote and must be presented as such. The user supplies their own key; no bundled developer key.
- User specifically wants PSL-style facial proportions, visible jaw/cheek/eye structure and actionable aesthetics feedback, not only skin analysis. Local analysis must work without Groq. Distinguish photo-derived surface geometry and subjective presentation preferences from hidden skeletal anatomy or objective beauty rankings.
- The health source is the app linked by the user: `com.fitbit.FitbitMobile`, currently Fitbit/Google Health. Support Health Connect first and investigate an additional optional Google Health API connection for missing cloud/device history. These are separate local and cloud data paths. Preserve provenance, select an authoritative source per metric, and avoid duplicates.
- Nutrition is logged in another app. Import its screenshot, extract calories/protein/fat/carbs, review, then save.
- Hevy remains the workout logger; import its exports/shared text without paid API access.
- Import personal reference content from Notion or Obsidian/files. Routine definitions must also be editable inside Aesthory.
- Private progress pictures, exportable/importable habits, and optional AI organization/reviews.
- Modern, minimal interface. Extensive detail belongs beneath simple daily controls.

## Routine organization

A routine is an ordered group of habits, such as evening skincare. A habit contains:

| Section | Requirements |
| --- | --- |
| Overview | Name, description, category, tags, active/paused/archived state |
| Instructions | Ordered steps, optional checklist, linked products and attachments |
| Schedule | Daily, selected weekdays, every N days, or a weekly target; date bounds and local time intent |
| Duration | Estimate, optional timer, recorded actual duration, optional shorter variant |
| Tracking | Checkbox, count, timer, numeric value + unit, rating, or checklist |
| Targets | Target value/frequency and optional personal goal |
| Products | Reusable product records, application notes, optional amount |
| References | Selected notes, URLs, Markdown, documents, screenshots, source date |
| Reminders | Quiet hours, snooze, grouped routine reminder, permission/restriction state |
| History | Dated done/partial/skipped events; missed is derived after its due window |

Only the basics are required when creating a habit. Advanced sections expand on demand. Preserve completion history and the definition/version that applied when recorded. Support editing, undo, pausing, and archiving.

## Product library

Reusable products have name, brand, type, optional photo/barcode, label ingredients, size, purchase/opened/expiry dates, label period after opening, stock state, linked habits, application notes, and dated observations. Optional price/retailer/link are personal records. Never infer a safe dose, compatibility, or diagnosis from a product entry.

## Records and import rules

- Nutrition snapshots are daily consumed totals, separate from targets and remaining amounts. Missing values stay missing. A later snapshot replaces the selected day's total only after review. Repeated screenshots must not duplicate totals.
- Workout imports preserve exercises, sets, units, dates, source IDs/hashes, and import provenance. Validate file variants before saving.
- Step totals use Health Connect aggregation/source attribution rather than adding overlapping device streams. Explain missing permissions and unavailable data.
- Progress photos use private files, date/category/view tags, optional notes, and comparison/alignment tools. No automatic upload to AI.
- Reference import starts with local text/Markdown and attachments. Actual Notion/Obsidian access methods remain an integration decision; do not promise a backend-free public OAuth connection with a packaged client secret.

## Sharing and recovery

Routine packs carry versioned instructions, schedules, categories, and only selected public reference/product details. Preview before export/import; recipient gets an independent copy. Exclude API keys, completion history, nutrition, and private photos by default. A readable text export accompanies the machine-readable pack.

Full backups are a separate explicit flow with authenticated encryption, portable key derivation, restore validation, and integrity checks. Automatic OS cloud backup is disabled for private app data.

## AI boundaries

Five specialist segments: Skin, Hair & Scalp, Facial Aesthetics, Physique, and Looksmax Mentor. Facial Aesthetics includes apparent face/jaw/cheek proportions, eye spacing and eye-corner slope, alongside presentation options. Different profiles share local goals/history but have explicit boundaries. Use dermatologist-informed education and practical appearance coaching; do not impersonate a licensed dermatologist or claim a selfie establishes a diagnosis.

Private photo assessment is core: user-selected 2D images, quality checks, visible observations/ratios and uncertainty, prioritized suggestions, and proposed habits. Do not call ordinary photos a validated 3D/medical scan or infer identity, age, ethnicity, hormones, exact body fat, skeletal dimensions, or a scientifically objective PSL score. See COACHING_SPEC.md for the report contract, source grading, and evaluation gates.

The first local path uses an existing licensed pretrained landmark bundle, deterministic geometry, and preference-based presentation options. It is not a custom-trained aesthetics model. A compatible imported LiteRT-LM model can produce a separate offline coaching draft from measurements without sending the original photo to a provider. This must not overwrite measured facts or imply evaluated medical/attractiveness performance.

Specialist instructions and retrieval of curated sources configure the hosted model; this is not model-weight training. Forum material can inform questions, language, and user preferences while remaining labeled anecdotal. No blanket scraping/training on forum content or private photos.

Other actions: turn selected notes into proposed routines, interpret ambiguous OCR text, summarize actual records, and answer questions grounded in selected local history. Save only after review. Minimize sent data; show cloud-processing state; handle quotas/offline conditions without breaking tracking. Context length is not durable memory. Treat uploaded material as untrusted data.

Setup includes optional Groq enablement, masked key entry, a Get API key button to https://console.groq.com/keys, explicit connection testing, key replacement/deletion, and model capability checks. Never ask for a key in chat or treat a visible model as proof of a free billing plan. Keep Groq optional and the user's account on its Free plan.

## Brand commitments

**Aesthory**, explicitly selected by the user from the premium aesthetics direction. The prior name was rejected. The directory remains `ascend`; it is not a user-facing brand. Public search found unrelated design uses of Aesthory; store/domain/trademark availability has not been established. Do not reopen the naming decision unless the user redirects.

## Proposed navigation

Today; Coaches; Routines; Progress. Library (products/references/packs) is accessible within routines/coaches and from search; settings/profile from the top app bar. Coaches opens the five specialist segments and photo-review entry point. This is a revised implementation proposal based on the appearance-first direction, not a finished visual design.

## Evidence and open decisions

No personal photos, calorie-app screenshot, Hevy export sample, health records, or product data have been supplied. The user reports 8 GB+ RAM. A development emulator is available; it is not evidence of performance on the user's phone. Unknowns: exact phone/chipset/Android version, nutrition layout, selected Notion/Obsidian workflow, broader encryption scope, and distribution channel. Never fabricate these.
