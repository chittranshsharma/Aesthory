# Aesthory design system

Native Android, Material 3, Operate mode. The user's direction is premium, minimal, appearance-first, with clear measurements and specific actions. No face rankings, fake medical authority, or decorative dashboard metrics.

## Identity

Quiet ivory and graphite with a restrained forest-green accent. Light: background #F7F8F4, surface #FFFFFF, foreground #19221C, primary #315B43, on-primary #FFFFFF. Dark: background #101612, surface #18221C, foreground #EDF4EE, primary #A4D3B2, on-primary #143521. Use Material semantic roles for all controls. Fonts use the native Material type scale and system sans; measurements use tabular figures when supported.

## Layout

Four compact-width destinations: Today, Analyze, Routines, Profile. Native navigation bar with labeled vector icons; system Back returns to Today before exit. Top app bar supplies context. Lists and separators carry daily content; use containers only for a distinct result or input group. At expanded width, constrain reading/form width and leave comfortable margins; a later adaptive navigation rail may be added when tablets become an explicit target.

## First usable surface

Setup: goals/name, optional baseline, local profile creation. Today: real scheduled habits or an actionable empty state. Analyze: goal selector, photo picker, explicit local-processing status, quality feedback, surface-proportion measurements, and presentation options. Optional model coaching is clearly labeled a draft. Routines: persistent routine/habit creation and completion. Profile: editable baseline, encrypted optional Groq key, and optional local model import/removal.

## Interaction floor

48 dp touch targets, visible labels, no placeholder-only inputs, inline errors, predictable Back, scrollable forms, keyboard insets, scalable text, and TalkBack semantics. Material controls handle feedback/motion; respect system animation scaling. Show indeterminate progress for native model work without a reliable percentage. Keep destructive credential/model removal explicit. Never show a non-working feature as an enabled button.

## Verification

Inspect native emulator/device screenshots once in light and dark/enlarged text, fix defects together, then confirm once. No browser mockup is evidence of native rendering. Report hardware and model-weight evaluation limits. Advanced trackers and integrations remain later milestones rather than fake screens.
