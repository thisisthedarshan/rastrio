# Rastrio Brand Assets

This directory contains the production brand assets for Rastrio. The system preserves the approved **Registration Strip** identity: a document/paper strip with left-edge registration detail, a negative-space `R`/feed channel, and precise rectilinear geometry.

## Source of Truth

`assets/brand/logo/rastrio-symbol.svg` is the **authoritative logo geometry**.

All fixed-color symbol variants, application-icon compositions, and full-size symbol-derived assets MUST be mechanically derived from that path. The micro/favicon artwork is the only intentional optical variant; it is documented below and MUST NOT replace the normal master above 23 px.

## Canonical construction

- **Construction grid / master viewBox:** `0 0 24 24`.
- **Visual symbol bounds:** `x=5..19`, `y=2..22`.
- **Visual outer aspect ratio:** `14:20` (`0.70`).
- **Primary outer corner radius:** `1.5` grid units on the top corners and lower-left corner.
- **Body thickness:** the main left/top/right body is intentionally heavy; no production interior feature is thinner than approximately `1.75` grid units.
- **Left registration notches:** two rectangular interruptions, each `2.35 × 1.40` units. They occupy `y=6.80..8.20` and `y=10.30..11.70`, with `2.10` units of solid body between them.
- **Negative-space channel:** left wall at `x=9.00`, top at `y=5.50`; the upper chamber resolves through a controlled rounded transition to approximately `x=16.50`.
- **Lower diagonal / feed cue:** the open negative-space diagonal exits from the transition at approximately `(14.15, 13.95)` to `(19.00, 18.80)`. The lower body returns from approximately `(9.65, 15.70)` to `(15.60, 22.00)`.
- **Optical correction:** the lower bowl-to-diagonal transition is a short cubic curve, not a mechanically perfect circle. This keeps the negative channel visually open at small sizes and prevents the mark from reading as a generic block letter.
- **Standalone safe/clear space:** at least `25%` of the visual symbol width (`3.5` construction units) around the mark where the surrounding layout allows it.

The canonical master uses one path with flat fill. Do not add gradients, shadows, filters, strokes, glows, texture, or raster content.

## Wordmark lockup

The horizontal lockup is:

`[ Registration Strip ]  Rastrio`

- Canonical capitalization: `Rastrio`.
- Symbol visual size in the lockup: `14 × 20` construction units.
- Symbol-to-wordmark visual gap: `3.2` construction units.
- Wordmark is vertically optically centered against the 20-unit symbol height.
- Wordmark letters are stored as vector outlines, so the SVG has **no runtime font dependency**.
- The path source is the open-source IBM Plex Sans family; the production outline is frozen in the SVG and must not be replaced by a live `<text>` element.
- Minimum clear space around the complete lockup: at least the width of the symbol's main negative-space channel; in ordinary layouts, use at least the standalone `25%` symbol-width rule.
- Compact use: use the symbol alone when the full lockup would make `Rastrio` smaller than normal readable UI text, or in avatars, launcher icons, favicons, and square project marks.

## Micro-size optical variant

`assets/brand/icons/favicon.svg` is an explicit optical variant for **16–23 px** rendering.

Changes from the full master are deliberate and limited:

- the two left registration notches are removed;
- the negative-space channel is enlarged slightly;
- the bowl/diagonal transition is simplified;
- the overall silhouette and feed direction remain the same.

Use the full canonical master at `24 px` and above unless pixel-grid testing proves the simplified mark is visibly better at a specific 24–32 px context. Do not silently substitute the micro mark at normal logo sizes.

## Android adaptive icon

- Canvas: `108 × 108`.
- Background: solid Registration Blue `#315C73`.
- Foreground: canonical Registration Strip in preview-paper white `#FFFDF7`.
- Canonical 24-unit grid is scaled by `3×` and translated by `(18, 18)`.
- The resulting critical visual mark occupies approximately `x=33..75`, `y=24..84`, fully inside the centered `66 × 66` safe zone (`21..87`).
- No wordmark, shadow, raster plate, or OEM-mask-specific edge treatment is baked into the foreground.

## Asset manifest

| File | Purpose | Color / treatment | Vector / raster | Minimum recommended size | Derivation / authority |
|---|---|---|---|---:|---|
| `brand/logo/rastrio-symbol.svg` | Canonical standalone symbol | `currentColor` | SVG vector | 24 px; 48+ px preferred for full detail | **Source of truth** |
| `brand/logo/rastrio-symbol-black.svg` | One-ink positive mark | `#000000` | SVG vector | 24 px | Exact canonical path; color-only derivative |
| `brand/logo/rastrio-symbol-white.svg` | Reversed mark | `#FFFFFF` | SVG vector | 24 px | Exact canonical path; color-only derivative |
| `brand/logo/rastrio-symbol-blue.svg` | Primary brand-color mark | `#315C73` | SVG vector | 24 px | Exact canonical path; color-only derivative |
| `brand/logo/rastrio-wordmark-horizontal.svg` | Light-background horizontal lockup | Carbon `#23282B` | SVG vector, path-based wordmark | ~120 px wide or larger | Canonical symbol path + frozen wordmark outlines |
| `brand/logo/rastrio-wordmark-horizontal-inverse.svg` | Dark-background horizontal lockup | Paper text `#ECE9E2` | SVG vector, path-based wordmark | ~120 px wide or larger | Same geometry as normal lockup; color-only inverse |
| `brand/icons/favicon.svg` | Browser/tiny project mark | Registration Blue | SVG vector | 16–23 px | Documented micro optical variant |
| `brand/icons/favicon-16.png` | 16 px favicon | Registration Blue / transparent | PNG | exactly 16 px | Mechanical rasterization of `favicon.svg` |
| `brand/icons/favicon-32.png` | 32 px favicon | Registration Blue / transparent | PNG | exactly 32 px | Mechanical rasterization of `favicon.svg` |
| `brand/icons/icon-192.png` | PWA/general app icon | Paper mark on Registration Blue | PNG | exactly 192 px | Canonical master composed mechanically |
| `brand/icons/icon-512.png` | PWA/F-Droid/GitHub source icon | Paper mark on Registration Blue | PNG | exactly 512 px | Canonical master composed mechanically |
| `brand/app/adaptive-foreground.svg` | Android adaptive foreground | `#FFFDF7` | SVG vector | 108 dp canvas | Canonical master path, transformed only |
| `brand/app/adaptive-background.svg` | Android adaptive background | `#315C73` | SVG vector | 108 dp canvas | Production color token |
| `readme/hero-light.webp` | Preferred README hero | Paper/carbon + Registration Blue + restrained amber | WebP raster | 1800×600 source | Promotional, non-authoritative |
| `readme/hero-dark.webp` | Dark README/release companion | Graphite + paper + light Registration Blue | WebP raster | 1800×600 source | Promotional, non-authoritative |
| `readme/motif-light.webp` | Background motif for diagrams/screenshots | Very low-contrast Registration Strip/registration geometry | WebP raster | 1600×900 source | Promotional, non-authoritative |
| `social/rastrio-social-card.webp` | Repository/social preview card | Dark graphite composition | WebP raster | 1200×630 | Promotional, non-authoritative |

## Light and dark usage

- On light/paper surfaces, prefer `rastrio-symbol-black.svg`, `rastrio-symbol-blue.svg`, or the normal horizontal lockup.
- On dark/graphite surfaces, prefer `rastrio-symbol-white.svg` or the inverse horizontal lockup.
- The canonical symbol geometry never changes between light and dark treatments.
- Do not add outlines or glow to compensate for poor contrast; use the correct positive/reversed asset or a solid containing field.

## Promotional artwork

The README and social WebP files are **not** logo sources. They may use diagrammatic document/preview/printer imagery, but they must not be traced back into canonical brand vectors.

The corrected promotional artwork intentionally:

- avoids a platform-logo row that could imply currently shipped platform support;
- does not present Apple/macOS as a current production target;
- uses the light document → preview → printer direction as the preferred README composition;
- does not place a Rastrio watermark on ordinary fictional user printouts.

## Reproducibility

Raster exports in this package were mechanically generated from the vector sources; they were not independently redrawn or regenerated. Any future repository build task SHOULD read `rastrio-symbol.svg` as the canonical symbol source rather than duplicate or reinterpret its geometry.
