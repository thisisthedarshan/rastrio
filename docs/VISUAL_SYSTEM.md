# RastrIO Visual System

**Status:** Production visual-system baseline  
**Project:** RastrIO
**Applies to:** Android, Desktop/JVM, Web, GitHub, F-Droid, documentation, project website, screenshots, release material  
**Source authority:** `PRD.md` and `docs/BRAND_GUIDE.md`

---

## 1. Purpose and authority

This document defines RastrIO's production color system, light and dark themes, thermal-paper preview colors, accessibility constraints, iconography language, application/project icon construction, and Compose Multiplatform token guidance.

The approved brand direction remains authoritative. This specification does not redesign the identity. It converts the existing **Registration Strip / Registration Blue / Thermal Amber / Paper and Carbon** direction into implementation-ready values and rules.

Where this document discusses preview, diagnostics, printer status, or rendering state, it preserves the PRD's architectural semantics:

- physical preview represents the authoritative `PreparedPrint` rather than a separately styled approximation;
- exact monochrome raster data is displayed as monochrome output rather than tinted with brand color;
- a manual cut guide is printable content, while an automatic cut boundary is an annotation of a physical operation;
- printer and print-job states must not imply physical success that the printer/transport cannot establish;
- color is presentation only and MUST NOT become part of `ThermalDocument`, `PreparedPrint`, printer-profile semantics, or protocol behavior.

Normative terms such as MUST, MUST NOT, SHOULD, and MAY are used deliberately.

---

## 2. Visual-system principles

RastrIO's production UI should read as a document tool first and a printer-engineering tool second.

The visual system therefore follows these rules:

1. **Paper and carbon dominate.** Large surfaces are warm neutral in light mode and graphite in dark mode.
2. **Registration Blue is interactive.** It identifies primary actions, selected state, links, focus, and primary flow.
3. **Thermal Amber is selective.** It identifies physical-output/hardware emphasis and restrained brand accents. It is not the universal warning color.
4. **Monochrome preview is literal.** Printable one-bit output remains black/white or the exact prepared raster representation.
5. **Semantic colors stay conventional.** Success, warning, error, and information have distinct roles and always receive a non-color cue.
6. **Dark mode is independently tuned.** It does not mechanically invert the light palette.
7. **Borders have two levels.** Subtle dividers may be decorative; meaningful control outlines use a stronger accessible token.
8. **Disabled state is not opacity-only.** Disabled controls use explicit foreground/surface tokens and interaction semantics.
9. **No third decorative accent family is introduced.** Thermal Amber is the approved brand accent and maps to the semantic `brandSecondary` role.

---

## 3. Core production palette

Only colors with a concrete production role are defined. The small four-step brand families are sufficient for light/dark foregrounds and containers; there is no general-purpose 10- or 12-step tonal ramp.

### 3.1 Registration Blue

| Token | HEX | RGB | Primary use |
|---|---:|---:|---|
| `registrationBlue900` | `#163441` | `22, 52, 65` | dark text on pale blue container |
| `registrationBlue700` | `#315C73` | `49, 92, 115` | light-theme primary, links, focus |
| `registrationBlue300` | `#78AFC8` | `120, 175, 200` | dark-theme primary |
| `registrationBlue100` | `#D7E8F0` | `215, 232, 240` | light primary container / selected surface |

Character: restrained, cool, slightly desaturated, technical rather than energetic.

### 3.2 Thermal Amber

| Token | HEX | RGB | Primary use |
|---|---:|---:|---|
| `thermalAmber900` | `#3A240C` | `58, 36, 12` | dark text on light amber container |
| `thermalAmber700` | `#8A5B22` | `138, 91, 34` | light-theme secondary / hardware accent |
| `thermalAmber300` | `#E3AE66` | `227, 174, 102` | dark-theme secondary / hardware accent |
| `thermalAmber100` | `#F3E2C7` | `243, 226, 199` | light secondary container |

Thermal Amber MUST NOT be used as the warning color merely because both are warm hues. Warning uses a more yellow-gold family and must be differentiated by iconography and label as well as color.

### 3.3 Paper and Carbon neutrals

| Token | HEX | RGB | Primary use |
|---|---:|---:|---|
| `paper0` | `#FFFFFF` | `255, 255, 255` | elevated light surfaces where true white is useful |
| `paper25` | `#FBF9F4` | `251, 249, 244` | light primary surface |
| `paper50` | `#F7F4EE` | `247, 244, 238` | light application background |
| `paper100` | `#F0ECE4` | `240, 236, 228` | light secondary surface |
| `paperBoundary` | `#D6D0C6` | `214, 208, 198` | subtle light divider |
| `carbon700` | `#5E666B` | `94, 102, 107` | light secondary text |
| `carbon900` | `#23282B` | `35, 40, 43` | light primary text |
| `graphite800` | `#2A3034` | `42, 48, 52` | dark elevated surface |
| `graphite850` | `#22272A` | `34, 39, 42` | dark secondary surface |
| `graphite900` | `#1A1E21` | `26, 30, 33` | dark primary surface |
| `graphite950` | `#141719` | `20, 23, 25` | dark application background |
| `paperTextDark` | `#ECE9E2` | `236, 233, 226` | dark primary text |
| `paperTextMuted` | `#B8C0C3` | `184, 192, 195` | dark secondary text |

---

## 4. Semantic color model

Application code SHOULD consume semantic tokens rather than raw palette names. Raw palette values are implementation inputs; semantic tokens are the stable UI contract.

A component should normally ask for `textPrimary`, `surfaceSecondary`, or `statusError`, not `carbon900`, `paper100`, or a raw hex value.

### 4.1 Compact implementation token table

This is the preferred direct-implementation table.

| Semantic token | Light | Dark | Role |
|---|---:|---:|---|
| `brandPrimary` | `#315C73` | `#78AFC8` | primary action, link, selected emphasis |
| `brandOnPrimary` | `#FFFFFF` | `#10252F` | content on primary fill |
| `brandPrimaryContainer` | `#D7E8F0` | `#234A5D` | selected/primary container |
| `brandOnPrimaryContainer` | `#163441` | `#D9EEF7` | content on primary container |
| `brandSecondary` | `#8A5B22` | `#E3AE66` | Thermal Amber / hardware emphasis |
| `brandOnSecondary` | `#FFFFFF` | `#332109` | content on secondary fill |
| `brandSecondaryContainer` | `#F3E2C7` | `#523A1D` | physical-output / secondary container |
| `brandOnSecondaryContainer` | `#3A240C` | `#F5E2C5` | content on secondary container |
| `background` | `#F7F4EE` | `#141719` | app/window background |
| `surfacePrimary` | `#FBF9F4` | `#1A1E21` | main content/editor chrome surface |
| `surfaceSecondary` | `#F0ECE4` | `#22272A` | secondary panel / alternate row |
| `surfaceElevated` | `#FFFFFF` | `#2A3034` | menus, dialogs, elevated panels |
| `surfaceDisabled` | `#F0ECE4` | `#2A2F32` | disabled control field |
| `textPrimary` | `#23282B` | `#ECE9E2` | primary prose/control text |
| `textSecondary` | `#5E666B` | `#B8C0C3` | secondary/supporting text |
| `textDisabled` | `#646C70` | `#8D979B` | disabled labels; no opacity-only treatment |
| `borderSubtle` | `#D6D0C6` | `#3B4348` | decorative separators |
| `borderStrong` | `#81898D` | `#657177` | meaningful control boundaries / non-text graphics |
| `focusRing` | `#315C73` | `#78AFC8` | keyboard/accessibility focus |
| `selectionSurface` | `#D7E8F0` | `#234A5D` | selected list/table/item surface |
| `selectionContent` | `#163441` | `#D9EEF7` | content on selected surface |
| `statusSuccess` | `#2F6B4B` | `#6EB88A` | success/ready where semantically true |
| `statusOnSuccess` | `#FFFFFF` | `#0D2A1B` | content on success fill |
| `statusSuccessContainer` | `#DCEFE3` | `#234D34` | success banner/chip background |
| `statusOnSuccessContainer` | `#153A28` | `#DDF4E5` | success container content |
| `statusWarning` | `#7A5700` | `#E4B85F` | warning/caution |
| `statusOnWarning` | `#FFFFFF` | `#302300` | content on warning fill |
| `statusWarningContainer` | `#F5E6B5` | `#514014` | warning banner/chip background |
| `statusOnWarningContainer` | `#3A2A00` | `#F7E8B4` | warning container content |
| `statusError` | `#B13A3A` | `#F28B82` | failure/error/destructive state |
| `statusOnError` | `#FFFFFF` | `#3B1110` | content on error fill |
| `statusErrorContainer` | `#F7DEDC` | `#5D2926` | error banner/chip background |
| `statusOnErrorContainer` | `#511919` | `#FFE1DE` | error container content |
| `statusInfo` | `#2E6C7A` | `#73B8C9` | informational diagnostic/status |
| `statusOnInfo` | `#FFFFFF` | `#0A2B33` | content on information fill |
| `statusInfoContainer` | `#D7EDF0` | `#214B56` | information banner/chip background |
| `statusOnInfoContainer` | `#14343A` | `#DDF5FA` | information container content |

There is intentionally no independent `brandAccent` color. `brandSecondary` is RastrIO's approved accent role. Adding a third decorative chromatic family would weaken the paper/carbon/registration/heat system without solving a product need.

---

## 5. Light theme

### 5.1 Character

Light mode should feel like a clean document workspace: warm paper rather than blue-white, graphite typography, restrained blue interaction color, limited elevation, and thin neutral rules.

### 5.2 Core surfaces

- Window/application background: `background` (`#F7F4EE`).
- Primary panels and editors: `surfacePrimary` (`#FBF9F4`).
- Secondary panels, alternate rows, inactive work areas: `surfaceSecondary` (`#F0ECE4`).
- Dialogs, menus, transient elevated surfaces: `surfaceElevated` (`#FFFFFF`).
- Do not use shadows to separate every panel. Prefer spacing and borders; use elevation only where interaction hierarchy requires it.

### 5.3 Editors and code

Additional functional surface tokens:

| Token | Light value | Use |
|---|---:|---|
| `editorSurface` | `#FBF9F4` | Markdown/text editor background |
| `editorGutterSurface` | `#F0ECE4` | optional gutter / line-number area |
| `codeSurface` | `#ECE8E0` | inline/fenced code background |
| `codeBorder` | `#D2CCC1` | code-block rule where needed |
| `codeText` | `#23282B` | code content |
| `tableHeaderSurface` | `#EAE6DE` | table header row |
| `tableAlternateSurface` | `#F5F2EC` | alternating table row when useful |

Syntax highlighting, if introduced, SHOULD use a small accessible set and MUST remain secondary to content legibility. It is not specified here because the PRD does not require a syntax-highlighting feature.

### 5.4 Interaction states

- Hover/rollover on pointer-capable platforms SHOULD use a small surface-value shift, not a new hue.
- Selection uses `selectionSurface` + `selectionContent`.
- Pressed states MAY deepen the primary container locally but MUST preserve text/icon contrast.
- Focus uses a visible `focusRing`; focus MUST NOT be communicated only through a background tint.
- Disabled controls use `surfaceDisabled` and `textDisabled`; do not apply a global alpha to the entire component.

---

## 6. Dark theme

### 6.1 Character

Dark mode is graphite-based rather than pure-black-first. Primary text is softened off-white. Registration Blue becomes lighter so links, selected state, and focus remain visible without becoming neon. Thermal Amber becomes a pale copper-gold and remains selective.

### 6.2 Core surfaces

- Window/application background: `#141719`.
- Main panels/editor chrome: `#1A1E21`.
- Secondary panels and alternate rows: `#22272A`.
- Elevated panels/dialogs: `#2A3034`.
- Subtle borders: `#3B4348`.
- Meaningful outlines: `#657177` or stronger where the component requires it.

Large areas SHOULD NOT use pure `#000000`; it creates unnecessary visual separation from the paper preview and makes the product feel like a terminal/diagnostic utility.

### 6.3 Editor and code surfaces

| Token | Dark value | Use |
|---|---:|---|
| `editorSurface` | `#171B1D` | Markdown/text editor background |
| `editorGutterSurface` | `#1E2326` | optional gutter / line-number area |
| `codeSurface` | `#101416` | inline/fenced code background |
| `codeBorder` | `#343B3F` | code-block rule where needed |
| `codeText` | `#ECE9E2` | code content |
| `tableHeaderSurface` | `#262C30` | table header row |
| `tableAlternateSurface` | `#1E2326` | alternating table row when useful |

### 6.4 Printer status and diagnostics

Printer states use semantic color plus a shape/icon and explicit label. Example treatment:

- ready/available: success icon + `Ready` only when the application can legitimately describe that state;
- warning/degraded/unknown capability: warning icon + explanatory text;
- failed/disconnected: error icon + explicit state;
- informational/connecting/preparing: information or neutral icon + state text.

`COMPLETED` in the print-job UI must follow the PRD meaning: successful transmission according to the transport contract. The color treatment MUST NOT imply verified physical paper output where the hardware provides no such confirmation.

### 6.5 Selected and disabled state

Dark selected state uses `#234A5D` with `#D9EEF7` content. This is intentionally a lifted blue-charcoal rather than an outline-only selection, so selected rows remain discoverable in dense lists and tables.

Dark disabled state uses `#2A2F32` with `#8D979B`. Do not lower alpha over the entire control, because alpha compositing produces inconsistent contrast across differently colored surfaces.

### 6.6 Raster and image previews

- The app chrome around an image/raster preview remains dark.
- The actual prepared monochrome raster remains literal black/white or the exact defined preview representation.
- Do not recolor black pixels to Registration Blue in dark mode.
- If a source image editor requires transparency indication, use a restrained neutral checker only in the editing surface; it MUST NOT be confused with prepared print data.

---

## 7. Thermal-paper preview system

Thermal-paper preview colors are theme-independent unless explicitly noted. The paper should continue to look like physical paper in dark mode because the preview is a model of expected output, not an ordinary application card.

### 7.1 Preview tokens

| Token | HEX | RGB | Meaning |
|---|---:|---:|---|
| `previewPaper` | `#FFFDF7` | `255, 253, 247` | simulated thermal paper |
| `previewPrintedBlack` | `#101112` | `16, 17, 18` | printed black / prepared one-bit output |
| `previewPaperBoundary` | `#C9C4BA` | `201, 196, 186` | subtle non-content paper edge |
| `previewCutAnnotation` | `#9A5A21` | `154, 90, 33` | non-printing automatic-cut boundary annotation |
| `previewRegistrationOverlay` | `#315C73` | `49, 92, 115` | non-printing alignment/registration UI overlay only |
| `previewSelectionOutline` | `#315C73` | `49, 92, 115` | selected preview element outline |
| `previewSelectionFill` | `#D7E8F0` at 30% alpha | `215, 232, 240` | optional selection fill; never applied to exported raster |

### 7.2 Printed marks versus annotations

RastrIO MUST preserve the difference between data that physically prints and preview-only annotations:

- actual raster/text content uses `previewPrintedBlack` or the exact raster samples;
- a **manual cut guide**, when represented as printable content in `PreparedPrint`, uses the same printed-black treatment as other output;
- an **automatic cut boundary** is not printed content and uses `previewCutAnnotation` plus a cut icon/label or line pattern;
- a **printed registration mark** uses `previewPrintedBlack` because it is part of physical output;
- `previewRegistrationOverlay` is reserved for non-printing UI guides, measurements, selection overlays, or Printer Lab aids.

### 7.3 Paper edge behavior

`previewPaperBoundary` is intentionally low contrast. It identifies the physical paper boundary but is not a meaningful text/interaction color. Against `previewPaper` its contrast is approximately 1.7:1; this is acceptable only because the boundary is decorative/contextual and the paper silhouette itself also has shape and surrounding-surface contrast.

In dark mode, the dark application surface behind the paper provides strong silhouette separation; do not darken the paper to make the edge token stronger.

---

## 8. Accessibility and contrast

### 8.1 Project targets

RastrIO targets, at minimum:

- **4.5:1** for normal text;
- **3:1** for large text and meaningful non-text UI graphics;
- visible keyboard/accessibility focus in both themes;
- non-color cues for success, warning, error, printer status, print state, diagnostics, and selection where ambiguity is possible.

The numeric ratios below use WCAG relative-luminance contrast calculations for sRGB values.

### 8.2 Verified representative combinations

| Foreground / background | Contrast | Classification |
|---|---:|---|
| Light `textPrimary` on `background` | 13.57:1 | normal text safe |
| Light `textSecondary` on `background` | 5.33:1 | normal text safe |
| Light `brandPrimary` on `background` | 6.58:1 | normal text safe |
| Light `brandOnPrimary` on `brandPrimary` | 7.22:1 | normal text safe |
| Light `brandOnSecondary` on `brandSecondary` | 5.83:1 | normal text safe |
| Light success white-on-fill | 6.31:1 | normal text safe |
| Light warning white-on-fill | 6.58:1 | normal text safe |
| Light error white-on-fill | 5.94:1 | normal text safe |
| Light information white-on-fill | 5.93:1 | normal text safe |
| Light `borderStrong` on `surfacePrimary` | 3.38:1 | large-text/icon/non-text graphic safe; not normal text |
| Light `borderSubtle` on `surfacePrimary` | 1.46:1 | decorative only |
| Dark `textPrimary` on `background` | 14.85:1 | normal text safe |
| Dark `textSecondary` on `background` | 9.74:1 | normal text safe |
| Dark `brandPrimary` on `surfacePrimary` | 7.00:1 | normal text safe |
| Dark `brandOnPrimary` on `brandPrimary` | 6.60:1 | normal text safe |
| Dark `brandOnSecondary` on `brandSecondary` | 7.72:1 | normal text safe |
| Dark success dark-on-fill | 6.51:1 | normal text safe |
| Dark warning dark-on-fill | 8.29:1 | normal text safe |
| Dark error dark-on-fill | 6.90:1 | normal text safe |
| Dark information dark-on-fill | 6.70:1 | normal text safe |
| Dark `borderStrong` on `surfacePrimary` | 3.34:1 | large-text/icon/non-text graphic safe; not normal text |
| Dark `borderSubtle` on `surfacePrimary` | 1.66:1 | decorative only |
| `previewPrintedBlack` on `previewPaper` | 18.58:1 | normal text safe |
| `previewCutAnnotation` on `previewPaper` | 5.35:1 | normal text safe |
| `previewPaperBoundary` on `previewPaper` | 1.71:1 | decorative only |
| `previewSelectionOutline` on `previewPaper` | 7.10:1 | normal text / graphic safe |

Container foreground pairs are substantially above 4.5:1 in both themes; their exact intended foreground token MUST be used rather than a universal black or white.

### 8.3 Disabled content

Disabled controls are not a place to hide necessary information. A disabled action SHOULD still explain itself through visible label/help text or nearby state text where the reason matters.

The explicit disabled foreground is chosen to remain readable and to avoid opacity stacking. Do not derive disabled colors by applying `alpha = 0.38f` or similar to arbitrary underlying colors.

### 8.4 Status meaning

Color MUST NOT be the only signal. Recommended pairings:

- success: check-circle icon + label;
- warning: warning triangle + label;
- error/failure: error/cancel icon + label;
- information: information icon + label;
- unknown/offline: neutral device/status icon + explicit wording.

For tabular diagnostics, also expose severity text or an accessible name so sorting/filtering and assistive technology do not depend on color perception.

---

## 9. Compose Multiplatform implementation guidance

The semantic token layer should be owned by shared UI code and should not depend on Android-only APIs. It MAY later map into Material/Compose `ColorScheme`, but RastrIO components should continue to consume RastrIO semantic roles where Material names are insufficient.

### 9.1 Example representation

```kotlin
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class RastrIOColors(
    val brandPrimary: Color,
    val brandOnPrimary: Color,
    val brandPrimaryContainer: Color,
    val brandOnPrimaryContainer: Color,
    val brandSecondary: Color,
    val brandOnSecondary: Color,
    val brandSecondaryContainer: Color,
    val brandOnSecondaryContainer: Color,

    val background: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val surfaceDisabled: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val focusRing: Color,

    val selectionSurface: Color,
    val selectionContent: Color,

    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val statusInfo: Color,

    val previewPaper: Color,
    val previewPrintedBlack: Color,
    val previewPaperBoundary: Color,
    val previewCutAnnotation: Color,
    val previewRegistrationOverlay: Color,
    val previewSelectionOutline: Color,
)

val RastrIOLightColors = RastrIOColors(
    brandPrimary = Color(0xFF315C73),
    brandOnPrimary = Color(0xFFFFFFFF),
    brandPrimaryContainer = Color(0xFFD7E8F0),
    brandOnPrimaryContainer = Color(0xFF163441),
    brandSecondary = Color(0xFF8A5B22),
    brandOnSecondary = Color(0xFFFFFFFF),
    brandSecondaryContainer = Color(0xFFF3E2C7),
    brandOnSecondaryContainer = Color(0xFF3A240C),

    background = Color(0xFFF7F4EE),
    surfacePrimary = Color(0xFFFBF9F4),
    surfaceSecondary = Color(0xFFF0ECE4),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceDisabled = Color(0xFFF0ECE4),

    textPrimary = Color(0xFF23282B),
    textSecondary = Color(0xFF5E666B),
    textDisabled = Color(0xFF646C70),
    borderSubtle = Color(0xFFD6D0C6),
    borderStrong = Color(0xFF81898D),
    focusRing = Color(0xFF315C73),

    selectionSurface = Color(0xFFD7E8F0),
    selectionContent = Color(0xFF163441),

    statusSuccess = Color(0xFF2F6B4B),
    statusWarning = Color(0xFF7A5700),
    statusError = Color(0xFFB13A3A),
    statusInfo = Color(0xFF2E6C7A),

    previewPaper = Color(0xFFFFFDF7),
    previewPrintedBlack = Color(0xFF101112),
    previewPaperBoundary = Color(0xFFC9C4BA),
    previewCutAnnotation = Color(0xFF9A5A21),
    previewRegistrationOverlay = Color(0xFF315C73),
    previewSelectionOutline = Color(0xFF315C73),
)

val RastrIODarkColors = RastrIOColors(
    brandPrimary = Color(0xFF78AFC8),
    brandOnPrimary = Color(0xFF10252F),
    brandPrimaryContainer = Color(0xFF234A5D),
    brandOnPrimaryContainer = Color(0xFFD9EEF7),
    brandSecondary = Color(0xFFE3AE66),
    brandOnSecondary = Color(0xFF332109),
    brandSecondaryContainer = Color(0xFF523A1D),
    brandOnSecondaryContainer = Color(0xFFF5E2C5),

    background = Color(0xFF141719),
    surfacePrimary = Color(0xFF1A1E21),
    surfaceSecondary = Color(0xFF22272A),
    surfaceElevated = Color(0xFF2A3034),
    surfaceDisabled = Color(0xFF2A2F32),

    textPrimary = Color(0xFFECE9E2),
    textSecondary = Color(0xFFB8C0C3),
    textDisabled = Color(0xFF8D979B),
    borderSubtle = Color(0xFF3B4348),
    borderStrong = Color(0xFF657177),
    focusRing = Color(0xFF78AFC8),

    selectionSurface = Color(0xFF234A5D),
    selectionContent = Color(0xFFD9EEF7),

    statusSuccess = Color(0xFF6EB88A),
    statusWarning = Color(0xFFE4B85F),
    statusError = Color(0xFFF28B82),
    statusInfo = Color(0xFF73B8C9),

    // Preview output remains paper-like in both themes.
    previewPaper = Color(0xFFFFFDF7),
    previewPrintedBlack = Color(0xFF101112),
    previewPaperBoundary = Color(0xFFC9C4BA),
    previewCutAnnotation = Color(0xFF9A5A21),
    previewRegistrationOverlay = Color(0xFF315C73),
    previewSelectionOutline = Color(0xFF315C73),
)
```

The full production type SHOULD also carry the semantic container/on-container status colors and functional editor/table tokens if components consume them directly. The abbreviated example above demonstrates the boundary without defining an entire application theme implementation.

### 9.2 Suggested Material `ColorScheme` mapping

Where Material components are used, map them approximately as follows:

| Material role | RastrIO semantic role |
|---|---|
| `primary` | `brandPrimary` |
| `onPrimary` | `brandOnPrimary` |
| `primaryContainer` | `brandPrimaryContainer` |
| `onPrimaryContainer` | `brandOnPrimaryContainer` |
| `secondary` | `brandSecondary` |
| `onSecondary` | `brandOnSecondary` |
| `secondaryContainer` | `brandSecondaryContainer` |
| `onSecondaryContainer` | `brandOnSecondaryContainer` |
| `background` | `background` |
| `surface` | `surfacePrimary` |
| `surfaceVariant` / equivalent | `surfaceSecondary` |
| `onBackground` | `textPrimary` |
| `onSurface` | `textPrimary` |
| `onSurfaceVariant` | `textSecondary` |
| `outline` | `borderStrong` |
| `outlineVariant` | `borderSubtle` |
| `error` | `statusError` |

Do not discard RastrIO-specific tokens such as preview paper, printed black, cut annotations, diagnostics, editor surfaces, or selection merely because Material does not expose an exact equivalent.

---

## 10. Iconography language

### 10.1 Baseline family

RastrIO SHOULD use **Google Material Symbols Sharp** as the default product icon family.

Reasons:

- the Sharp family is closer to the approved rectilinear/softened geometry than a heavily rounded icon set;
- the family provides familiar platform metaphors for common actions;
- the official Material Design Icons repository distributes Material Symbols under the **Apache License 2.0**, which is compatible with RastrIO's Apache-2.0/FOSS/F-Droid goals;
- the family provides 20 px and 24 px designs suitable for normal application controls.

Implementation policy:

- default visual style: **Sharp**, optical size 24, weight approximately 400, unfilled unless selected-state semantics justify fill;
- default UI size: 20–24 dp, normally 24 dp for primary controls;
- use one optical family consistently; do not mix Sharp, Rounded, and Outlined casually;
- vendor only the SVG/vector assets actually used by the application rather than requiring a remote icon font;
- retain the upstream Apache-2.0 license notice with vendored assets;
- use `currentColor`/tint semantics so icons consume RastrIO semantic colors;
- no icon asset may require network access at runtime.

Upstream license/reference: <https://github.com/google/material-design-icons>

### 10.2 Icon construction rules

Product icons SHOULD use:

- 24 × 24 design grid where possible;
- visually consistent 2 dp-class stroke/weight at 24 dp;
- strong silhouettes and open counters;
- square/rectilinear structure with controlled corner softening;
- standard platform metaphors for standard actions;
- filled state only when it communicates selection/state, not as arbitrary decoration.

Do not replace recognizable Bluetooth, USB, settings, warning, or cut metaphors with branded abstractions.

### 10.3 Recommended icon mapping

Names below are semantic source names; the exact generated Kotlin resource name may follow project conventions.

| RastrIO concept | Preferred Material Symbol | Rule |
|---|---|---|
| Markdown | `markdown` | standard symbol; no custom mark |
| document | `description` | use for generic document/file semantics |
| preview | `preview` | generic preview; physical preview may use custom domain icon below |
| printer | `print` | standard printer action/device metaphor |
| Bluetooth | `bluetooth` | standard platform metaphor |
| USB | `usb` | standard platform metaphor |
| image printing | `image` | combine contextually with Print action; do not create a logo-like composite |
| QR | `qr_code_2` | use standard QR symbol |
| todo/checklist | `checklist` | state must also be represented in text/data |
| shopping list | `list_alt` or `checklist` | prefer list semantics; avoid making shopping-cart imagery a general RastrIO motif |
| note | `note_alt` | quick-note workflow |
| memo | `article` | document/memo workflow |
| settings | `settings` | standard settings metaphor |
| printer profile | custom `printer_profile` | domain-specific; see custom rules |
| Printer Lab | `science` | pair with `Printer Lab` label; do not imply a separate product |
| warning | `warning` | semantic color + accessible label |
| failure | `error` or `cancel` | choose based on state semantics |
| success | `check_circle` | only for actual success/ready semantics |
| cut | `content_cut` | standard cut operation |
| raster rendering | custom `raster_mode` | domain-specific grid/pixel metaphor |
| native rendering | custom `native_text_mode` | printer/text-cell metaphor, not generic `code` |

If an upstream icon name is unavailable in the exact vendored Material Symbols revision, select the nearest unambiguous standard symbol and document that substitution in the asset manifest rather than inventing a second general-purpose icon family.

---

## 11. Custom domain icons

Custom icons are justified only when a standard icon would obscure a RastrIO-specific concept. They MUST use the same optical size, weight, corner treatment, and `currentColor` behavior as the baseline family.

### 11.1 `physical_preview`

Represents printer-specific physical preview.

Construction:

- 24 × 24 grid;
- narrow paper strip centered in a preview frame;
- one small cut/registration cue permitted;
- no printer body;
- no raster detail below 24 dp;
- must remain distinct from generic `preview` and `print`.

### 11.2 `printer_profile`

Represents portable printer capability/profile data, not a paired physical `PrinterInstance`.

Construction:

- simplified printer/paper silhouette plus one small profile/settings marker;
- marker should be a notch, short rule set, or compact gear-like cue, not a Bluetooth badge;
- do not include device identity, link, or pairing imagery.

### 11.3 `raster_mode`

Represents prepared raster rendering.

Construction:

- 3 × 3 or 4 × 4 bounded square-cell field;
- use no more cells than remain legible at 20 dp;
- avoid QR-like finder patterns and barcode stripes.

### 11.4 `native_text_mode`

Represents printer-native text strategy.

Construction:

- one or two text-cell bars inside a narrow output strip;
- avoid alphabet-specific glyphs if possible;
- should read as device text cells, not a source-code icon.

### 11.5 `registration_mark`

May be used in Printer Lab or wide-output tooling.

Construction:

- short orthogonal tick/crosshair geometry;
- no target/bullseye aesthetic;
- use only where the UI actually refers to registration/alignment behavior.

---

## 12. RastrIO project/application icon

The canonical project icon follows Brand Guide **Direction A — Registration Strip**. It is a registered paper-strip symbol, not a literal printer and not a generic `R` monogram.

### 12.1 Construction grid

Use a **24 × 24 master design grid** for the canonical symbol.

Recommended full-mark geometry:

- visual outer strip occupies approximately `x = 5..19`, `y = 2..22`;
- outer silhouette is approximately 14 units wide × 20 units high;
- outer corner radius: approximately 1.25–1.5 units;
- internal negative-space channel should occupy approximately 32–38% of strip width;
- at most one diagonal is permitted, descending toward the lower-right to provide the subtle `R`/feed cue;
- full mark may contain **two** small registration notches/cells along one edge;
- no interior feature should be thinner than approximately 1.75 units on the 24-unit grid;
- the silhouette must remain identifiable with all registration details removed.

These dimensions are the vector construction envelope. Optical corrections of up to roughly 0.25 grid units are allowed to balance negative space and apparent centering.

### 12.2 SVG-ready primitive specification

A vector implementation can be constructed from these boolean primitives:

```text
viewBox: 0 0 24 24

A. Outer strip
   rounded rectangle
   x=5, y=2, width=14, height=20, radius=1.5

B. Internal channel (subtract from A)
   start near x=8.5, y=5.5
   create one open rectangular chamber approximately 6 units wide × 6 units high
   continue as a vertical channel approximately 2.25–2.75 units wide
   terminate in one diagonal branch toward x≈15.5, y≈18.5
   keep the channel open and simple enough to survive at 16 px

C. Registration notches (subtract from A; full mark only)
   two horizontal edge notches
   each approximately 2.0–2.5 units wide × 1.25–1.5 units high
   place on the same outer edge with at least 1.5 units separation

D. No additional strokes, shadows, gradients, text, or outlines.
```

The actual canonical SVG SHOULD be authored with an even-odd fill or boolean-subtracted path so the negative-space channel is genuine transparency rather than a paper-colored overlay.

### 12.3 Full, simplified, and micro masters

Three masters are required:

**Full mark — 48 px and above**

- outer strip;
- negative-space channel;
- subtle diagonal feed/`R` cue;
- up to two registration notches.

**Simplified mark — 24–47 px**

- outer strip;
- negative-space channel;
- diagonal cue;
- at most one registration notch if it remains clean at pixel-grid alignment.

**Micro mark — 16–23 px**

- outer strip;
- simplified negative-space channel;
- no tiny registration notches;
- diagonal may be squared off if anti-aliasing makes it muddy;
- must remain recognizable in monochrome and circular crop.

Do not scale the full master down and accept disappearing details as the micro version.

### 12.4 Clear space and safe area

For standalone non-launcher use, minimum clear space around the symbol is **25% of the symbol's visual width** on every side.

For the horizontal symbol + wordmark lockup, minimum clear space is at least the width of the symbol's internal negative-space channel, consistent with the Brand Guide.

### 12.5 Monochrome version

The monochrome master is the source of truth.

Allowed treatments:

- carbon/graphite on light/paper surfaces;
- paper/off-white on graphite/dark surfaces;
- solid Registration Blue on sufficiently light neutral surfaces;
- one-ink black for documentation and thermal-print tests.

Prohibited:

- gradients;
- shadows as structural separation;
- multicolor internal detail;
- background-dependent outlines;
- glow effects.

### 12.6 Light and dark variants

The geometry does not change between light and dark themes.

Recommended use:

- **light UI:** `#23282B` symbol by default; `#315C73` is acceptable for brand-emphasis surfaces;
- **dark UI:** `#ECE9E2` symbol by default; `#78AFC8` is acceptable where primary-color branding is useful;
- do not create a separate dark-mode drawing.

### 12.7 Android adaptive icon

Android adaptive icon layers SHOULD use a **108 × 108 dp** canvas.

Use the stricter current safe-area guidance:

- keep all critical symbol detail within the centered **66 × 66 dp** safe zone;
- target an optical symbol size around **58–62 dp** inside that zone;
- foreground: Registration Strip symbol only;
- background: solid `registrationBlue700` (`#315C73`) for the primary launcher treatment;
- foreground symbol: `previewPaper`/paper white (`#FFFDF7` or `#FBF9F4`), choosing one master and keeping it consistent;
- no text;
- no background shadow baked into the asset;
- no edge-to-edge foreground strip that depends on a particular OEM mask.

Provide a monochrome adaptive-icon layer using the same simplified silhouette/negative-space geometry so Android themed icons can recolor it.

Android design guidance reference: <https://developer.android.com/develop/ui/compose/system/icon_design_adaptive>

### 12.8 GitHub avatar

Use the standalone symbol, never the wordmark.

Preferred avatar treatment:

- square source, at least 512 × 512 px export from the SVG;
- solid Registration Blue background;
- paper/off-white Registration Strip symbol;
- keep the complete symbol inside a central area no larger than approximately 70% of canvas width/height so circular crops retain clear space;
- no tiny registration details that disappear at 32–40 px rendered size.

A monochrome paper-background/graphite-symbol avatar is acceptable for contexts where colored branding is undesirable.

### 12.9 Favicon

Use the micro mark.

- 16 × 16: micro silhouette + simplified channel only;
- 32 × 32: micro or simplified master depending on raster test;
- SVG favicon: micro master with `currentColor` only if host theming is predictable; otherwise use the approved fixed positive version;
- `.ico` export SHOULD include at least 16, 32, and 48 px rasterizations generated from the vector master;
- do not use the full wordmark.

### 12.10 Thermal reproduction test

Because the identity is tied to thermal output, the monochrome symbol SHOULD be physically printed on the reference printer at several sizes before final asset adoption. Record the smallest reproducible size rather than assuming screen behavior transfers to thermal hardware.

---

## 13. Product-specific visual applications

### 13.1 Diagnostics

Diagnostic banners use semantic containers, not solid saturated bars by default. Recommended structure:

```text
[severity icon]  concise diagnostic title
                 supporting explanation / code where useful
```

Diagnostic code text MAY use monospaced typography. The severity color decorates the icon/edge/container but does not replace the severity label or accessible name.

### 13.2 Printer lists and status

Printer list rows should remain neutral. Status color is a secondary cue.

Avoid a dashboard of red/green dots. Use device icon + name + explicit state text; a small semantic icon or status mark may supplement it.

### 13.3 Tables

Tables use neutral hierarchy:

- header: `tableHeaderSurface`;
- body: `surfacePrimary`;
- optional alternating row: `tableAlternateSurface`;
- row selection: `selectionSurface`;
- rules: `borderSubtle` unless the rule itself conveys grouping/interaction, in which case use `borderStrong`.

Do not color each table cell or column merely to add visual variety.

### 13.4 Printer Lab

Printer Lab MAY be denser than the main application, but must use the same tokens and icon family.

It may use:

- measurement rules;
- Registration Blue alignment overlays;
- monospaced numeric values;
- `previewRegistrationOverlay` for non-printing calibration aids;
- status containers for test outcomes.

It must not become a neon engineering dashboard or a separate visual brand.

---

## 14. Asset and implementation governance

### 14.1 Token ownership

A recommended repository organization is:

```text
shared/src/commonMain/.../ui/theme/
├── RastrIOColors.kt
├── RastrIOTheme.kt
└── RastrIOIconography.kt

brand/
├── logo/
│   ├── rastrio-symbol.svg
│   ├── rastrio-symbol-simplified.svg
│   ├── rastrio-symbol-micro.svg
│   ├── rastrio-lockup-horizontal.svg
│   └── rastrio-symbol-monochrome.svg
├── icons/
│   └── custom/
└── licenses/
```

Exact paths may follow repository conventions, but there SHOULD be one canonical token definition and one canonical vector source per official mark variant.

### 14.2 No ad-hoc colors

Product code SHOULD NOT introduce raw hex values outside the theme/token package except for:

- test fixtures;
- imported/user content that legitimately contains color data outside RastrIO UI semantics;
- exact preview/raster representations whose values are part of a rendering test;
- platform resources generated from the canonical token source.

If a new color is required, first determine whether an existing semantic role fits. Add a token only when the role is stable and repeated.

### 14.3 Screenshot and release material

Screenshots and release graphics SHOULD use the same production tokens. Do not create a separate marketing palette.

Use Registration Blue for primary flow, Thermal Amber for limited hardware/physical-output emphasis, and Paper/Carbon neutrals for the frame. Actual application screenshots remain the evidence of the product.

---

## 15. Acceptance checklist

Before treating the visual system as implemented, verify all of the following:

- light and dark semantic tokens are centralized in shared UI code;
- no theme requires Android-only types in the shared semantic layer;
- primary/secondary/status foreground pairs pass the intended contrast threshold;
- `borderSubtle` is not used for meaningful-only affordances;
- focus remains visible on paper-like and graphite surfaces;
- status meaning has icon/text or another non-color cue;
- disabled components do not rely on global alpha alone;
- dark mode keeps the physical paper preview paper-like;
- raster output remains exact black/white/prepared data rather than brand-tinted;
- manual cut guides are visually treated as printable content;
- automatic cut boundaries are visually distinct preview annotations;
- Material Symbols assets are vendored with their Apache-2.0 licensing information;
- custom icons are limited to RastrIO-specific domain concepts;
- Registration Strip full/simplified/micro masters exist and pass 16/20/24/32/48/96/256 px review;
- launcher icon critical geometry remains within Android's safe zone;
- monochrome and circular-crop tests pass;
- favicon uses the micro mark;
- at least one physical thermal-print reproduction test is performed for the project symbol.

---

## 16. Decision summary

```text
Primary family          Registration Blue
Light primary           #315C73
Dark primary            #78AFC8
Secondary/accent        Thermal Amber
Light secondary         #8A5B22
Dark secondary          #E3AE66
Light background        #F7F4EE
Dark background         #141719
Light primary text      #23282B
Dark primary text       #ECE9E2
Preview paper           #FFFDF7
Preview printed black   #101112
Default icon family     Material Symbols Sharp
Custom icon use         physical preview / printer profile / raster-native domain concepts
Project symbol          Registration Strip
Launcher treatment      paper mark on Registration Blue field
Theme model             semantic RastrIO tokens, optionally mapped to Material ColorScheme
```

This system is intentionally restrained. RastrIO should be recognizable through the Registration Strip, paper/carbon contrast, Registration Blue interaction language, controlled Thermal Amber hardware emphasis, and precise geometric iconography—not through a large palette or decorative effects.
