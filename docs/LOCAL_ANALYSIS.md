# Local facial analysis

## Implemented pipeline

Photo Picker grants selected-image access. ImageDecoder applies orientation and bounds decode to a 1536-pixel maximum axis (reject sources above 120 megapixels). No cloud request occurs. A separate bitmap belongs to MediaPipe because MPImage.close() recycles it; Compose retains its own preview.

Bundled Google Face Landmarker float16/v1 detects up to two faces and supplies surface landmarks/expression blendshapes. Model source, Apache-2.0 license and SHA live in `app/src/main/assets/models/NOTICE.md`. These are existing pretrained weights, not Aesthory training. Only image-space x/y are used; model z is not interpreted as skeletal depth.

Capture gates require exactly one face, finite required points, image at least 512×512, points in frame, sufficient face/eye span, roll at most 8°, coarse pose skew at most 0.15 and expression score at most 0.35. These are initial engineering heuristics, not calibrated pose or clinical thresholds. No validated blur, lighting, skin-condition or occlusion classifier exists yet. Rejections show recapture guidance.

## Measurements

Convert coordinates to pixels before distances/angles to preserve image aspect ratio. Measurements describe a photograph's surface projection; perspective, lens, expression, landmark error and framing affect them.

| UI value | MediaPipe indices / calculation |
| --- | --- |
| Face width / height | distance(234,454) / distance(10,152); not classic clinical fWHR |
| Jaw contour / cheek width | distance(172,397) / distance(234,454); not bony gonial width |
| Inner / outer eye span | distance(133,362) / distance(33,263) |
| Lower-face share | distance(2,152) / distance(10,152); not hairline-based facial thirds |
| Image-left/right eye-corner slope | inner-to-outer angle relative to eye-center axis, sorted by image x; not clinical canthal tilt |

No objective PSL score, hidden-bone dimensions, maxillary projection, skeletal diagnosis or surgical plan is generated. Built-in presentation options are goal-based suggestions, not a learned verdict from ratios. Users choose baseline, apparent length/width, softer styling or definition. Generated drafts cannot replace measured facts.

## Optional offline text model

Everyday coaching now uses built-in rules and guides, with no extra model needed. Today suggests among the first unfinished actions of each routine, preserving their order, and uses known duration without inventing missing estimates. Searchable care/style/forum guides and starter routines are included. Extra offline AI lives under You → Extra AI tools → Advanced; it remains experimental. Numerical photo details are collapsed beneath plain-language next steps.

LiteRT-LM Android 0.19.0, CPU backend, 2048-token context, maximum 512 output tokens, thinking disabled. Import one trusted CPU-compatible `.litertlm` file in Profile. No automatic download, account or usage billing. Stream into private storage, cap at 5 GiB, check free space/container magic/major version, hash SHA-256, initialize the temporary file with the runtime, then atomically replace the existing file. Failure/cancellation before replacement preserves the previous file. Native initialization may take time; immediate cancellation is not promised. Removing weights leaves landmarks/routines usable.

Only common policy, selected facial specialist, goal and measured text enter this local text model. The photo is not supplied. Result is a separate coaching draft. Analysis/report/draft state is temporary. “Keep as a progress photo” explicitly saves a separate private image copy with date/view/notes; reports and drafts are not journal records. The journal also accepts Photo Picker images without analysis and is included in encrypted backups. No weight training or model-specific quality evaluation has occurred.

User supplied 8 GB+ RAM, not a specific SoC. Test small quantized CPU models, then compare stronger candidates within measured memory/latency limits. Loading successfully does not establish useful advice. Do not advertise a tiny smoke-test model as expert-quality. Some exports are GPU/NPU-specific despite matching extensions.

## Evaluation before recommending weights

1. Record exact revision/license/SHA, device/SoC/Android, backend, context/output budget and app version. Keep weights outside Git.
2. Verify import, real generation, cancellation, replacement failure, removal and restart on the phone. Measure cold/warm latency, peak memory and thermal behavior; emulator results are not phone benchmarks.
3. Use representative consented neutral photos, repeated captures and varied cameras/lighting/presentation. Check measured UI and no/multiple faces, crop, roll and expression. Geometry unit tests cannot establish real-photo precision.
4. Evaluate held-out coaching cases for grounding, unsupported anatomy/PSL certainty, harmful interventions, age appropriateness, reversible actions, uncertainty and injection. Keep observations separate from opinions.
5. Curate a cited, licensed knowledge collection and domain-reviewed evaluation set before expert-quality claims. Retrieval/prompts improve context, not weights. Fine-tuning needs a separate data/license/consent plan and baseline comparison.

See STATUS.md for executed checks. Physical-device and optional-weight benchmarks remain pending.
