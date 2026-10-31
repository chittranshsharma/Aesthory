---
name: Aesthory
description: Native everyday appearance coaching with training-club energy.
colors:
  light-primary: "#2845CE"
  light-on-primary: "#FFFFFF"
  light-primary-container: "#DEE4FF"
  light-on-primary-container: "#101D58"
  light-secondary: "#9B351D"
  light-on-secondary: "#FFFFFF"
  light-secondary-container: "#FFDACE"
  light-on-secondary-container: "#431B0D"
  light-tertiary: "#25633D"
  light-on-tertiary: "#FFFFFF"
  light-tertiary-container: "#D4F2D9"
  light-on-tertiary-container: "#143B23"
  light-background: "#F4F5FB"
  light-on-background: "#161D34"
  light-surface: "#FFFFFF"
  light-on-surface: "#161D34"
  light-surface-variant: "#E2E5F1"
  light-on-surface-variant: "#42495F"
  light-surface-container: "#ECEEF8"
  light-surface-container-low: "#F0F2FA"
  light-surface-container-lowest: "#FFFFFF"
  light-surface-container-high: "#E5E8F3"
  light-surface-container-highest: "#DEE2EF"
  light-outline: "#727990"
  light-outline-variant: "#C3C8DB"
  light-surface-tint: "#2845CE"
  dark-primary: "#BAC7FF"
  dark-on-primary: "#112571"
  dark-primary-container: "#263E9D"
  dark-on-primary-container: "#E3E8FF"
  dark-secondary: "#FFB69D"
  dark-on-secondary: "#581F0D"
  dark-secondary-container: "#70301C"
  dark-on-secondary-container: "#FFDBCD"
  dark-tertiary: "#9BD9AE"
  dark-on-tertiary: "#06391D"
  dark-tertiary-container: "#224D32"
  dark-on-tertiary-container: "#D1F4DB"
  dark-background: "#10162A"
  dark-on-background: "#E9EDFF"
  dark-surface: "#192137"
  dark-on-surface: "#E9EDFF"
  dark-surface-variant: "#343E58"
  dark-on-surface-variant: "#C3CBE3"
  dark-surface-container: "#202940"
  dark-surface-container-low: "#192137"
  dark-surface-container-lowest: "#0C1122"
  dark-surface-container-high: "#29334A"
  dark-surface-container-highest: "#333D54"
  dark-outline: "#919BB5"
  dark-outline-variant: "#424D67"
  dark-surface-tint: "#BAC7FF"
typography:
  display-large:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  display-medium:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  display-small:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  headline-large:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  headline-medium:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  headline-small:
    fontFamily: "Barlow Condensed"
    fontWeight: 700
  body:
    fontFamily: "Android system sans (Material 3 default)"
rounded:
  medium: "16dp"
  large: "16dp"
  extra-large: "24dp"
spacing:
  compact: "4dp"
  small: "8dp"
  related: "12dp"
  section: "16dp"
  card: "20dp"
  page-horizontal: "24dp"
components:
  daily-plan-light:
    backgroundColor: "{colors.light-primary}"
    textColor: "{colors.light-on-primary}"
    rounded: "{rounded.medium}"
    padding: "20dp"
  daily-plan-dark:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-on-primary}"
    rounded: "{rounded.medium}"
    padding: "20dp"
  starter-coral-light:
    backgroundColor: "{colors.light-secondary-container}"
    textColor: "{colors.light-on-secondary-container}"
    rounded: "{rounded.medium}"
    padding: "20dp"
  starter-mint-light:
    backgroundColor: "{colors.light-tertiary-container}"
    textColor: "{colors.light-on-tertiary-container}"
    rounded: "{rounded.medium}"
    padding: "20dp"
---

# Design System: Aesthory

## Overview

**Creative North Star: "The Personal Training Club"**

A friendly personal training-club companion: bold condensed headings, cobalt action panels, coral and mint starter ideas, and readable native controls. The personality is energetic and approachable; everyday language and usable guidance come before optional AI configuration.

This records the implemented Android Compose system, replacing the retired ivory/forest design. Frontmatter is normative for explicit app tokens; dimensions use native dp. Typography sizes, line heights, spacing and control defaults not overridden in code remain the pinned Material 3 implementation's defaults, expressed in sp at runtime. No browser or CSS equivalence is implied.

**Key Characteristics:**
- Colorful Material surfaces with explicit light and dark palettes.
- Condensed display type paired with system body text.
- Real daily progress, short actions, expandable detail.

## Colors

Cobalt gives the daily plan a clear center; coral and mint make starter choices approachable against cool neutral surfaces.

### Primary

Cobalt is the light primary action/plan color. In dark mode, its light periwinkle counterpart uses deep-blue on-primary text. The plan's inverse button swaps the active primary/on-primary pair.

### Secondary

Warm coral containers alternate through starter routines; their matching on-secondary-container role supplies readable content.

### Tertiary

Mint containers alternate through starter routines and hold the included-coach reassurance on You.

### Neutral

Cool light backgrounds and white surfaces become ink-blue backgrounds and layered slate surfaces in dark mode. Outline-variant dividers separate repeated content. Error and unspecified semantic roles inherit Material defaults; the frontmatter lists only explicit app colors.

**The Paired Roles Rule.** Use each Material container with its corresponding on-color in the active theme; do not paste light-theme colors into dark screens.

## Typography

**Display Font:** Bundled Barlow Condensed bold; Google Fonts origin with SIL OFL retained in app assets.

**Body Font:** Android system sans through Material 3 defaults.

All six display/headline roles override only family and weight. Today, Coach, You and photo-check introductions use displaySmall; the daily-plan title uses headlineLarge; section/starter/routine headings use headlineMedium. Titles and body/label roles retain Material defaults. No custom scale ratio, tracking, line height or fixed text-size override is defined.

**The Two Voices Rule.** Use Barlow Condensed bold for display/headline roles and the unchanged Material system styles for titles, body copy and controls.

## Layout

Phone-first, one vertical reading column. `Page` centers content within a maximum 680 dp width, then applies 24 dp horizontal and 20 dp vertical padding, with 16 dp between children. It scrolls and respects keyboard insets. There is no tablet navigation rail or breakpoint system.

Cards use 20 dp internal padding, with related content generally separated by 8 or 12 dp. Section headings use 8 dp top spacing and 4 dp between title and subtitle. Topic/focus chips wrap with 8 dp gaps and a minimum 48 dp height. Prominent full-width photo actions use a minimum 56 dp height; no fixed-height card/title crop is imposed.

## Elevation & Depth

Depth comes mainly from Material tonal surfaces, colored cards and dividers. Cards, buttons, fields, dialogs, top app bar and navigation retain Material elevation/state behavior; no app-specific shadow values are defined. Expandable content uses Compose `animateContentSize()` with its library defaults; completion requests native Confirm haptic feedback. No custom timing/easing tokens are defined.

**The Tonal Grouping Rule.** Use colored containers for a distinct plan or starter group; use rows and dividers for repeated actions and guides.

## Shapes

Medium and large Material shapes share softly rounded corners (16 dp); extra-large uses 24 dp. Other shape roles and button/chip silhouettes retain Material defaults. Guide rows explicitly use the medium shape. There is no custom ornamental silhouette, hard offset shadow or shipping raster artwork.

## Components

- **Buttons:** Native Button, FilledTonalButton, OutlinedButton and TextButton. Most use Material colors and interaction states. The daily-plan action explicitly inverts primary/on-primary; photo-entry tonal buttons fill the column. Busy state disables mutation controls.
- **Chips:** Native FilterChip for focus, age, photo goal and guide topics; selected state follows Material roles. Guide filters wrap rather than squeezing into one row.
- **Cards:** Daily plan uses primary/on-primary; starters alternate secondary/tertiary containers; You uses tertiaryContainer. Shared medium corners and card padding connect them without boxing every row.
- **Fields:** Labeled native OutlinedTextField for search and editing, with Material focus/error/disabled behavior. Key entry is masked. Search is single-line; details and forms can scroll.
- **Navigation:** Labeled native bottom bar: Today, Coach, Routines, You, with vector icons and Material selected indicator. Top app bar repeats the current destination. Back from another destination returns toward Today; the photo check returns to Coach.
- **Guide rows:** Full-width TextButton, 12 dp padding, wrapping weighted title column, trailing Read/Close action with 12 dp start spacing; summary, actions and sources expand below a divider.
- **Daily progress:** Actual completed/total counts and a linear indicator; the on-primary track uses 25% alpha. Completion updates the panel and may reveal the next action.
- **Private photo journal:** A lazy vertical list uses the existing page spacing, with 16 dp card padding and fitted photo previews. Date, area and view identify each entry; labeled checkboxes select at most two matching area/view photos. Comparison switches Earlier/Later around one fitted image. The scrollable photo-details dialog shows the selected image, date action, wrapping area/view chips and optional note before explicit save. Recently deleted provides recovery; permanent deletion requires confirmation.
- **Backup and restore:** Native scrollable AlertDialogs keep password guidance and masked, labeled fields together; creation asks for confirmation and shows a mismatch error. Prepare/Check stays disabled until valid. Restore preview shows the backup date, real counts and replacement consequences before the explicit Replace & restore action, with Keep current data alongside it. Mutation actions disable during work and restoration shows progress. Back labels name You or setup according to the entry path.

## Do's and Don'ts

### Do:
- **Do** use semantic Material roles and the system-selected light or dark theme.
- **Do** keep guide titles wrapping and preserve space for the Read/Close action.
- **Do** show real completion counts and clear, reversible actions.
- **Do** keep advanced AI and numeric photo detail collapsed until requested.

### Don't:
- **Don't** restore the retired ivory/forest minimal identity or old Analyze/Profile destination labels.
- **Don't** introduce fictional scores, streaks or decorative dashboard metrics.
- **Don't** make model setup a prerequisite for the daily plan, guides or local photo check.
- **Don't** translate dp/sp into CSS pixels or claim the documentation proves accessibility or hardware performance.

Source of truth: `AesthoryTheme.kt`, `EverydayScreens.kt`, `AesthoryRoot.kt`, `ProgressScreen.kt` and `BackupScreen.kt` under `app/src/main/kotlin/app/aesthory/ui/`; direction: `docs/UI_BRIEF.md`. Native behavior remains authoritative. `.impeccable/design.json` contains extensions and native component metadata, not web renderings. Documentation does not establish clinical validity, model speed or comprehensive accessibility verification.
