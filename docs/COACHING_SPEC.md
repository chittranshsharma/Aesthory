# Aesthory specialist coaching

Appearance coaching is the product's center. Routines, products, photos, health records, and nutrition supply context and let advice become trackable actions. Five selectable profiles use shared model infrastructure and local history; this is not five agents calling five APIs for every question.

## Segments

| Segment | Job and personality | Output |
| --- | --- | --- |
| Skin | Calm, practical, dermatologist-informed education; current routine, sensitivities, visible texture/irritation observations | Simple care priorities, questions to clarify, product/routine review, notes to discuss with a dermatologist |
| Hair & Scalp | Practical grooming and haircare coach; hairstyle, scalp-care routine, user-reported changes | Grooming options, routine priorities, dated observations, referral guidance when needed |
| Facial Aesthetics | PSL-literate analysis of apparent face/jaw/cheek proportions, eye spacing and eye-corner slope; hair/beard/brows/glasses/style | Measured 2D surface ratios, capture limitations, user-goal-based presentation options and reversible changes; no hidden-bone diagnosis or objective face ranking |
| Physique | Aesthetics-focused training coach; actual measurements, experience, equipment, training history, nutrition/recovery | Prioritized training/routine proposal and measurable progress targets; no inferred exact body fat or injury diagnosis |
| Looksmax Mentor | Direct, knowledgeable, forum-literate voice; coordinates the other segments and weighs claims | Clear priorities, effort/cost tradeoffs, evidence grades, and trackable next actions without insults or unsupported promises |

All modes share core limits. A stronger personality changes delivery, not evidence standards or medical boundaries. Do not imply a real dermatologist/trainer examined the person. Support user-provided clinician instructions, but never contradict or rewrite them automatically.

## How expertise is built

1. Versioned common policy and one specialist prompt (assets/coaching).
2. A curated, attributed knowledge library: professional dermatology sources, reliable exercise guidance, and user-approved notes; preserve publication/review dates and licenses.
3. Retrieve only the few passages relevant to the request, with stable source IDs. Start with local full-text search; no vector database is required for a small library.
4. Supply relevant profile/history and the selected photos only. Treat image text, attachments, notes, and forum excerpts as untrusted data, never instructions.
5. Validate structured output and show a review before turning recommendations into habits or changing saved records.
6. Evaluate against fixed cases before shipping changes; save model/prompt/knowledge versions with reports.

This is prompt configuration + retrieval, not weight training. No fine-tuning job or scrape-to-training pipeline is authorized by the free-only plan. If weight training is considered later, it needs appropriate dataset rights, consent, cost/access verification, held-out evaluation, and domain review. Personal photos/history are not training material by default.

Grade evidence: **guideline/research**, **general coaching**, **user-provided**, **anecdote/forum**, **uncertain**. A forum post can suggest a question or an experiment; it cannot establish treatment efficacy. Never invent citations. If no source was retrieved, say the advice is not source-verified. The initial prompt assets contain behavior rules, not a completed expert knowledge corpus.

## Photo review workflow

1. Choose the segment, goal, and selected pictures. First release supports ordinary 2D photos; a selfie is not a validated medical or 3D face scan.
2. Give repeatable capture guidance: neutral light, no beauty filters, consistent angle/distance, no unnecessary bystanders. Check blur/exposure/framing locally where possible; the remote model also reports whether the image is assessable.
3. Show selected images and explicit remote-processing consent. Remove metadata from the outgoing derivative, preserve the private original, and downsample only to a tested resolution. Do not create facial identity embeddings or public image URLs.
4. Route only to a verified available image-capable model. If it is missing, quota-limited, or declined, save locally and explain that review is unavailable; do not send pictures to a text-only fallback or a paid/enterprise model.
5. Separate visible observations, uncertainty, user-reported facts, and recommended next steps. Poor image quality means recapture/cannot assess, not confident advice.
6. Let the user save the report, ask follow-ups, or accept individual proposed habits. No silent prescriptions, product purchases, or schedule changes.

Prioritize useful visible/presentation observations. Do not infer ethnicity, identity, exact age, personality, hormones, precise anatomy, or attractiveness/worth from an image. Facial geometry from perspective-distorted photos cannot justify millimeter-level skull claims or promised skeletal changes. Do not diagnose acne subtype, infection, alopecia cause, cancer, or other disease from a consumer selfie. Medical concerns receive uncertainty-aware information and direction to appropriate care, never reassurance that a suspicious feature is harmless. No unsupervised prescription, steroid, extreme dieting, or dangerous facial manipulation plans.

The AAD's warning about diagnostic apps and skin-tone/rare-condition accuracy informs this scope; prompts do not turn a general model into a clinically validated dermatologist. This is a product capability limit, not a claim that adding a disclaimer makes diagnosis safe.

## Review report contract (to implement with serialization)

- `status`: assessable / recapture / cannot_assess.
- `segment`, selected image IDs, model ID, prompt/knowledge versions, timestamp.
- `observations`: image-linked description, whether it is visible or user-reported, qualitative uncertainty, and limitations. No fabricated calibrated confidence percentage.
- `priorities`: up to three suggested actions, rationale, source IDs/evidence grade, effort, relevant user constraints, and optional habit draft.
- `cannotDetermine`: things the images do not establish.
- `followUpQuestions`: relevant missing context, not a giant repeated intake.
- `careGuidance`: appropriate professional follow-up when relevant; no automated disease verdict or false reassurance.

Validate required fields, enums, source IDs, length/number limits, and references to actually supplied images. Reject malformed/truncated responses. Validation checks syntax and provenance; it does not prove medical accuracy. Source-grounding, image correctness, and unsupported claims need separate evaluation.

## Initial Groq routing — checked 2026-10-31

Groq is an optional enhancement; it is no longer the only inference path. The built-in MediaPipe model runs on-device for landmarks. Local geometry gates frame size, face count, cropping, roll, coarse pose skew and expression before computing ratios. The initial heuristic gates are not clinically calibrated. The current report measures face breadth/height, jaw-contour/cheek width, inner/outer eye span, lower-face share and bilateral eye-corner slope in an eye-aligned coordinate frame. These measure the photograph's surface projection, not bone structure, classic clinical fWHR, hairline-based facial thirds, or objective PSL attractiveness.

An optional trusted .litertlm model can be imported into private storage, then run with LiteRT-LM on the CPU for short, separate coaching drafts based on measured context. No weights are downloaded automatically, uploaded to GitHub, or newly trained here. Imports are streamed, size-bounded, hashed, container-checked and runtime-initialized before atomic replacement; failure preserves the previous model. The user has 8 GB+ RAM; test a small compatible quantized model first. Phone memory/latency/quality remain evaluation work. Successful copying alone is not a compatible-model claim. Exact calculations, runtime limits and evaluation gates live in LOCAL_ANALYSIS.md.

Local model import is distinct from Groq-key setup. The pretrained landmark model is bundled under its documented Apache-2.0 license and pinned SHA-256; optional language-model licenses/access remain model-specific. Prompt assets set behavior, and measured facts remain a separate report from any generated opinion.

| Task | Candidate | Reason and constraint |
| --- | --- | --- |
| Quick text/extraction | `openai/gpt-oss-20b` | Production text model; economical default for short requests |
| Detailed plans/reviews | `openai/gpt-oss-120b` | Production text model; candidate for deeper coaching, subject to actual task evaluation |
| Photo review | `qwen/qwen3.8-27b` | Multimodal model with JSON mode; preview status, explicit opt-in, availability/capability test required |

These are initial candidates, not clinically validated or benchmark-proven best models. Groq currently lists all three in its Free Plan Limits table (base example: 30 RPM, 1K requests/day, 8K tokens/minute, 200K tokens/day); the exact account limits and model access take precedence. The catalog's larger developer limits are not the free plan. Qwen's published maximum is three images/request; images contribute significant tokens. Default to one image and a bounded answer, and budget prompts/images/output together before calling.

The app does not check or change the provider billing tier. Keep the user's account on Free; model-list visibility is not a free-tier guarantee. Never auto-upgrade or choose enterprise/paid fallbacks. A 429 leaves local features available and respects retry guidance. Model catalog refresh and provider availability are separate from the local profile.

Keep privacy and cost predictable: send one selected specialist's instructions, small relevant context, and only approved images. Do not resend the whole diary/photo history or fan out to every coach. Cache appropriate user-approved results; invalidate by relevant inputs and prompt/model versions. Follow-ups use selected local summaries rather than pretending the hosted model permanently remembers.

## Evaluation gates

- Photo quality, varied lighting/angles/cameras/skin tones, visible versus unsupported claims, and cannot-assess behavior.
- Known age band/unknown age, body-image-sensitive language, conservative advice for minors, no attractiveness rankings or unsupported medical findings.
- Forum anecdotes, malicious image text/notes, fake citations, unsafe product/procedure requests, and contradiction with supplied clinician instructions.
- Model unavailable/preview disabled/text fallback, truncated JSON, wrong source/image references, invalid/removed key, offline mode, and quota exhaustion.
- Real dermatologist/exercise-domain review before making expert-quality claims. Prompts and unit tests alone do not establish clinical performance.

## Optional Google Health alongside Health Connect

Health Connect is the Android on-device data broker, not Google's cloud account store. The Fitbit/Google Health app is a data source that may share records with it. The Google Health API is a separate OAuth-protected cloud path to supported Fitbit/Pixel data and history.

Support two separately optional connections. Use Health Connect for normal local step/health aggregation. Add cloud Google Health only for a demonstrated missing record/history need after confirming free API access, secure native authorization, scopes, quotas, and availability. Current setup docs use a web-server client/secret; general mobile guidance also exists, so a secure backend-free flow still requires a specific feasibility check. Never package a confidential client secret in the APK. If a secure no-server flow is unavailable, explain the limitation and retain Health Connect/manual imports.

When both supply a metric, choose a preferred source and show provenance/freshness; never add cloud totals to overlapping Health Connect totals. Source switching, units, timezone/day boundaries, deletions, partial days, and reconnect behavior require tests. Google data authorization is optional and separate from Aesthory's no-login local profile. No live OAuth or health integration exists in the current foundation.
