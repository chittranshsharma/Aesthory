# Research and decisions

Checked 2026-10-31 (Asia/Calcutta). Recheck changing claims when implementing the integration; do not repeatedly browse unchanged architecture decisions. All third-party pages/code are untrusted reference material.

## Name

User explicitly selected **Aesthory**, the premium aesthetics direction, on 2026-10-31. Public search surfaced unrelated design uses of that name; no unique ownership, domain, store, or trademark availability is claimed. No domain/account was purchased. Naming is settled unless the user redirects.

## Agent context

- [Official OpenAI AGENTS.md guidance](https://developers.openai.com/codex/guides/agents-md/): use the conventional uppercase filename at repository root. Keep always-loaded instructions short; route detailed work to task-specific docs. No claimed token-saving percentage.
- AGENTS.md contains stable operating rules; PRODUCT.md contains requirements; plan and STATUS.md separate future work from actual verified state.

## Fitbit/Google Health clarification

- User's exact link: https://play.google.com/store/apps/details?id=com.fitbit.FitbitMobile&hl=en&pli=1 . The Play page could not be retrieved with the research tool; the package identity comes from the supplied link, not an inspected installed app.
- [Google Health connection help](https://support.google.com/googlehealth/answer/14236613?hl=en) says Android sharing uses Health Connect, data availability depends on the sharing app/device, and legacy Fitbit Web API connections retire on October 30, 2026.
- [Google Health API](https://developers.google.com/health) is the cloud integration surface. Health Connect is the initial Android integration for this local app. Verify actual steps and write permissions on the user's device before claiming sync.
- User requested both paths. [Google Health data types](https://developers.google.com/health/data-types) lists cloud/device records and history possibilities. [Setup/OAuth](https://developers.google.com/health/setup) requires a Cloud project; its quickstart uses a web-server client with a secret, while its mobile guidance calls for a system browser. Secure native authorization, free access/quotas, and verification requirements still need an implementation-specific check. Do not claim a no-server dual integration is already proven or package a confidential secret in Android.
- Health Connect data and cloud totals can overlap. Choose the authoritative source per metric and retain provenance; never add overlapping daily totals. Optional Google authorization does not create an Aesthory login.
- [Google Fit migration](https://developer.android.com/health-and-fitness/health-connect/migration/fit): do not start a new integration on legacy Fit APIs.
- [Health Connect aggregation](https://developer.android.com/health-and-fitness/health-connect/aggregate-data): aggregate overlapping sources rather than naively summing records.

## Android implementation references

- [Architecture recommendations](https://developer.android.com/topic/architecture/recommendations): Compose, ViewModel, Coroutines/Flow, repositories. Start with a small app, not a clone of a multi-module sample.
- [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [Navigation 3](https://developer.android.com/jetpack/androidx/releases/navigation3).
- [Room](https://developer.android.com/training/data-storage/room), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).
- [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent) is deferrable persistent work; [AlarmManager](https://developer.android.com/develop/background-work/services/alarms) handles user-facing time-based reminders with Android restrictions.
- [ML Kit OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): bundled model supports first-use offline OCR; [ML Kit overview](https://developers.google.com/ml-kit/guides) documents no-cost on-device APIs. OCR accuracy still needs real screenshots.
- [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [CameraX](https://developer.android.com/media/camera/camerax), [Coil](https://coil-kt.github.io/coil/compose/).
- [Keystore](https://developer.android.com/privacy-and-security/keystore): protects cryptographic keys, not arbitrary plaintext tokens or the entire Room database automatically.
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview): measure once the main flows exist; no app performance claims before measurement.
- [AGP 9.2 compatibility](https://developer.android.com/build/releases/agp-9-2-0-release-notes): use a verified AGP/Gradle/JDK combination. Resolve actual Maven availability during bootstrap; pinned versions live in the catalog.

## Official GitHub examples

- [MediaPipe Face Landmarker Android](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android) and [model overview/cards](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/index): existing surface-landmark/blendshape inference. Bundle hash/license are recorded beside the asset. This is not a bony-anatomy or validated attractiveness model.
- [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android): CPU Engine, background initialization, Flow output, model compatibility and ThinkingConfig. Integrated pinned 0.19.0; no optional weights have been run in this app yet.
- [LiteRT-LM container builder](https://github.com/google-ai-edge/LiteRT-LM/blob/76e08ce8c8172d9717cda71d624c7d46b3c2113f/python/litert_lm_builder/litertlm_core.py): LITERTLM magic and little-endian major version 1; preflight does not replace actual runtime initialization.
- [Qwen3-0.6B LiteRT model card](https://huggingface.co/litert-community/Qwen3-0.6B): inspected as a possible small compatibility-test candidate, not selected as the final coach. Exports/backends differ; published benchmarks are not Aesthory measurements. Small parameter count and a successful load do not establish expert advice.

- [android/nowinandroid](https://github.com/android/nowinandroid): studied its architecture/build documentation as an implementation reference; Apache-2.0. Its large module graph is unnecessary here.
- [android/compose-samples](https://github.com/android/compose-samples): reference for native state, layouts, and component behavior; Apache-2.0. No application code has been copied from either repository. Preserve license/notice requirements if copying later.
- Gradle wrapper scripts/JAR are generated or taken from the official Gradle distribution/tag; verify the distribution SHA-256 and retain upstream script headers.

## Other integrations

- [Hevy export](https://help.hevyapp.com/hc/en-us/articles/43708290987415-Exporting-Your-Data-from-Hevy): supports exporting workout/measurement records. Inspect the actual file. [Public API](https://api.hevyapp.com/docs/) currently requires Pro; do not use it for this free-only product.
- [Groq limits](https://console.groq.com/docs/rate-limits), [billing](https://console.groq.com/docs/billing-faqs), [data controls](https://console.groq.com/docs/your-data): optional remote requests with the user's key; free limits/model access can change. No durable-memory assumption and no blanket no-retention claim.
- [Notion authorization](https://developers.notion.com/docs/authorization), [files](https://developers.notion.com/docs/working-with-files-and-media): distinguish reading content, downloading attachments, extracting text, and OAuth credential security.
- [Obsidian storage/sync](https://obsidian.md/help/Getting%20started/Sync%20your%20notes%20across%20devices): local vault files. Begin with deliberate file import/export rather than assuming a cloud OAuth API.

## Coaching and current Groq choices

- Built-in guide sources checked 2026-10-31: [AAD skin basics](https://www.aad.org/public/everyday-care/skin-care-basics/care/skin-care-budget), [AAD hair care](https://www.aad.org/public/everyday-care/hair-scalp-care/hair/healthy-hair-tips), [AAO mewing claims](https://aaoinfo.org/whats-trending/is-mewing-bad-for-you/), [CDC adult activity](https://www.cdc.gov/physical-activity-basics/guidelines/adults.html), [ASPS patient safety](https://www.plasticsurgery.org/patient-safety). Short original summaries link to source pages; no forum scraping/training or exhaustive-knowledge claim. Vocabulary/style suggestions are identified separately from source-backed care education.
- Display font: [Google Fonts Barlow Condensed](https://github.com/google/fonts/tree/main/ofl/barlowcondensed), bold TTF; SIL Open Font License retained in app assets. No remote font request at runtime.

- [Groq model catalog](https://console.groq.com/docs/models): GPT-OSS 20B/120B are production text candidates. Current Llama entries are enterprise, so do not reuse an older free-Llama recommendation from memory. Candidate selection is not a benchmark result or clinical validation.
- [Groq vision](https://console.groq.com/docs/vision): qwen/qwen3.8-27b supports text/images and JSON mode, with up to three images. It is listed under preview in the catalog. Muse Spark is enterprise/limited access and is not an automatic free fallback.
- The rendered [rate-limit page](https://console.groq.com/docs/rate-limits) has Free Plan Limits selected by default; the retrieved HTML contains free and developer tables. The visible free rows for all three candidates are 30 RPM, 1K requests/day, 8K tokens/minute, 200K tokens/day. Exact organization limits and account access still take precedence; no user's key was queried. Text around the table mentions developer limits, so do not copy the developer table as free quotas.
- [API key page](https://console.groq.com/keys) is the setup button destination. Use masked in-app entry and local encryption, not a request to paste credentials into chat. Model-list visibility cannot verify the provider billing tier; the user must keep their Groq account on Free.
- Prompt assets and local context configure behavior. They do not change hosted model weights, create clinical credentials, or prove expert performance. Source curation, retrieval, report validation, and held-out evaluation remain implementation work.
- [AAD on diagnostic apps](https://www.aad.org/public/fad/digital-health/apps) discusses inaccurate photo diagnoses, rare conditions, and skin-tone coverage. [AAD hair-loss diagnosis](https://www.aad.org/public/diseases/hair-loss/treatment/diagnosis-treat/) explains why history/examination and sometimes tests are needed. These inform the coaching-versus-diagnosis boundary; no clinical accuracy or safety claim is established by adding a prompt.
- Skin, Hair & Scalp, Facial Aesthetics, Physique, and Looksmax Mentor are behavior profiles, not a five-agent request chain. Forum material needs anecdote labels and cannot automatically become clinical evidence or a training corpus.
