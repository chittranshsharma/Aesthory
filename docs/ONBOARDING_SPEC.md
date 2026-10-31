# Aesthory setup

Confirmed: Android only, local personal profile, no Aesthory email/password account. Source OAuth and a Groq account belong to optional integrations; neither is an app login. Aesthory's setup is an appearance-coaching baseline, not a diagnostic intake form.

## Flow

1. **Your focus:** select Skin, Hair & Scalp, Facial Aesthetics, Physique, or Looksmax Mentor; optional display name and supportive/direct coaching tone. Prioritize one or two goals. Allow skip and later editing.
2. **Your baseline:** show only relevant expandable fields. All measurements are optional; never block basic tracking because a person does not know a measurement. Explain purpose next to sensitive fields.
3. **Your routine:** time available, current habits/products, budget preference, training experience, equipment, days available, and activity context. Optional allergies, sensitivities, injuries, prescribed treatments, and clinician instructions are user-reported context, never photo inferences.
4. **Your starting photos:** optional face/front/side, hair/scalp, or physique pictures. Local save is separate from sending a photo to AI. Skip, retake, compare framing, and remove must be available.
5. **Your data:** optional Health Connect permissions; optional Google Health cloud connection when supported. Explain purpose and source per metric. Do not request every health permission at setup.
6. **Your AI:** optional Groq key setup below. Continue without AI is always available.
7. **Review:** show what stays local, selected goals, entered measurements, and enabled connections. Finish into Today; offer setup editing from Profile.

Keep each step short, show progress, preserve drafts after process recreation, and make Back predictable. Ask for missing information when its feature needs it rather than presenting a huge mandatory form. Do not store a plaintext API key in navigation arguments, SavedStateHandle, analytics, logs, or backup.

## Baseline fields

| Segment | Fields |
| --- | --- |
| General | Optional display name, units, timezone, coaching tone, prioritized goals; age band when relevant to advice (under 18/adult/prefer not to say) |
| Physique | Height, weight, optional waist/chest/shoulder circumference/upper arm/thigh, optional known body-fat measurement with its date/method; equipment, experience, available days |
| Skin | Self-described skin behavior/concerns, sensitivity and allergies, current products, how long an issue has been present, optional diagnosed conditions/treatments supplied by the user |
| Hair | Self-described texture/scalp behavior, styling goals, grooming preferences, routine, optional shedding concerns/history |
| Face/style | Hairstyle/beard/brow/glasses/style preferences, goals and constraints; optional standardized photos |
| Nutrition/recovery | Existing user/clinician-set targets if any, dietary preferences, sleep schedule and time available; no required calorie deficit |

Sex-related inputs are optional and requested only for a calculation that actually needs them, with an explanation. Never infer them from a photo. Exact date of birth, email, address, skin-tone inference, facial identity templates, and automated attractiveness ranking are unnecessary. For unknown age or under-18 users, keep coaching age-appropriate; do not generate restrictive weight-loss or cosmetic-procedure plans.

Canonical measurements use cm/kg with display-unit conversion at the boundary. Unknown is null, not zero. Validate finite positive measurements and meaningful units; ask the user to correct implausible entries. Never estimate exact body fat, bone dimensions, hormone levels, or medical skin type from a selfie. Store later measurements as dated observations rather than overwriting all history.

## Bring your own Groq key

Screen controls:

- **Use AI** toggle, off initially; **Continue without AI**.
- Masked **Groq API key** field; paste/reveal temporarily; do not solicit a key in this chat or upload a credentials file.
- **Get API key** opens https://console.groq.com/keys in the system browser/Custom Tab. No embedded provider-login WebView.
- Short setup help: create a Groq account, stay on its Free plan, create a key, paste it here. Aesthory cannot make a paid Groq account free or reliably infer its billing tier from a key/model list.
- **Test connection** makes an explicit, minimal request after explaining that it contacts Groq. No request on every keystroke. Query available model IDs, then verify task capability; never claim the model-list result verifies free quotas.
- Status: not configured / checking / connected / invalid key / quota reached / unavailable / offline. Show only a masked identifier after save.
- Advanced model choices: quick text, detailed plan, optional preview vision. Show model status, capability, checked date, and a warning when no usable image model exists.
- **Replace key**, **Delete key**, and **Disable AI**. Disabling prevents queued/new calls; clarify whether saved local coaching history is kept. Deleting removes the encrypted key and in-memory copies as far as the platform permits.

Use Android Keystore-backed encryption for the local credential; never include it in routine exports or portable backups. Individual image submissions need a preview showing selected pictures and that they go to Groq. General AI enablement is not blanket permission to upload the photo library. Keep image review opt-in and retention controls explicit.

## Acceptance gates

- A person can finish setup with no email, key, photos, health permissions, or measurements and still create/track routines.
- Optional fields, units, age bands, drafts, process recreation, accessibility, and later edits work.
- API keys cannot appear in saved UI state, reports, exports, screenshots of the masked field, or logs; encrypted storage and deletion are verified on Android.
- Revoked permissions, offline mode, invalid keys, 429 responses, missing/retired models, and declined photo consent leave local tracking usable.
- The current foundation implements only baseline validation and routing policies. Setup screens, credential storage, provider calls, and persistence are pending.
