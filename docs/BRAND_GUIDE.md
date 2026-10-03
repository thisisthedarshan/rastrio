# Rastrio Brand Guide

**Status:** Brand baseline  
**Project:** Rastrio  
**License context:** Apache-2.0 / FOSS project  
**Primary product target:** Android  
**Secondary product/development targets:** Desktop/JVM and Web  
**Canonical written name:** `Rastrio`

---

## 1. Purpose

This document defines the visual and verbal identity of Rastrio for product UI, GitHub, F-Droid, documentation, project web surfaces, screenshots, release material, and other official project communication.

It is a design brief, not a marketing manifesto. The brand exists to make the project recognizable, coherent, and credible while remaining subordinate to the product itself.

The identity MUST reflect Rastrio as a polished thermal-document utility with a technically rigorous printing engine underneath it. It MUST NOT imply capabilities, services, or product categories outside the product specification.

The brand system SHOULD remain practical for an open-source project: reproducible with open tooling, usable without proprietary typefaces or design services, and robust in monochrome, low-resolution, and documentation-heavy contexts.

---

## 2. Brand Positioning

### 2.1 What Rastrio is

Rastrio is an open-source, offline-first document authoring, preview, and printing application for thermal printers.

Its identity should communicate four things at a glance:

1. **Document-first utility** — users work with Markdown, notes, lists, images, QR content, and structured documents rather than raw printer commands.
2. **Physical precision** — output is prepared deliberately for real hardware, with preview and printing treated as two views of the same resolved plan.
3. **Technical credibility** — printer profiles, layout, rasterization, and protocol support are engineered rather than hidden behind vague magic.
4. **Accessible practicality** — inexpensive hardware should become useful without requiring users to understand ESC/POS, code pages, raster packing, or transport details.

### 2.2 Positioning statement

Rastrio should be presented as a **precise document-to-thermal-output tool**: a utility that turns ordinary digital content into deliberate physical print output with a trustworthy preview step.

This positioning is intentionally different from:

- POS and restaurant billing software;
- receipt generators;
- label-design suites;
- raw printer diagnostics;
- enterprise print-management platforms;
- cloud printing services;
- AI-assisted writing or design tools.

### 2.3 Brand promise

The visual identity should reinforce a simple product expectation:

> What is prepared on screen has a clear, inspectable relationship to what is sent to the printer.

This is a design principle, not a marketing slogan. It should influence icon geometry, screenshot framing, diagrams, and motion.

---

## 3. Brand Personality

Rastrio should feel:

- **Precise** — aligned, measured, intentional, and geometrically disciplined.
- **Technical** — credible around developers and hardware without resembling diagnostic firmware software.
- **Approachable** — usable by someone who simply wants to print a note or document.
- **Utilitarian** — controls, labels, and visuals exist for a reason.
- **Modern** — contemporary typography and spacing without trend-driven decoration.
- **Understated** — confidence through restraint rather than visual noise.
- **Reliable** — stable hierarchy, predictable interaction patterns, strong contrast.
- **Open** — compatible with the culture of FOSS documentation, inspectability, and portability.
- **Tactile** — subtly connected to paper, feed direction, cuts, edges, and physical output.
- **Distinctive** — recognizable through a small set of repeatable motifs rather than novelty.

### 3.1 Personality spectrum

Rastrio should sit approximately here:

```text
playful        ───────●── serious
ornamental     ────────●─ utilitarian
consumer       ─────●──── developer
soft           ──────●─── precise
futuristic     ───●─────── contemporary
industrial     ─────●───── document-centric
```

The project should not feel cold or hostile, but technical confidence takes priority over friendliness-for-its-own-sake.

---

## 4. Visual Concept

### 4.1 Central idea: the registered paper strip

The recommended visual system is built around the idea of a **digital document becoming a registered physical strip**.

The core visual vocabulary combines:

- a narrow vertical paper/document strip;
- controlled feed direction;
- alignment or registration marks;
- sparse raster cells/dots;
- hard logical edges with modest corner softening;
- deliberate negative space;
- a visible transition from structured digital geometry to physical output.

This concept is more specific to Rastrio than a generic printer symbol. It connects document layout, rasterization, preview, segmentation, feed, and real printed paper without turning the identity into an illustration of a desktop printer.

### 4.2 Conceptual contrast

Rastrio should visually balance two worlds:

```text
DIGITAL                         PHYSICAL
structured document            thermal strip
layout geometry                paper width
pixels / raster cells          printed marks
preview                        output
alignment                      feed / cut
portable intent                printer-specific preparation
```

Brand graphics MAY show these as adjacent or transitioning states, but should avoid literal “before/after” effects everywhere.

### 4.3 Signature motifs

Preferred motifs:

- vertical strip silhouettes;
- short registration ticks;
- cropped corner marks;
- sparse one-bit dot fields;
- stepped raster edges;
- ruled baselines;
- rectangular frames representing preview bounds;
- negative-space channels suggesting paper feed.

These motifs SHOULD remain sparse. A page full of dots or faux receipt texture quickly becomes decorative noise.

---

## 5. Logo Directions

The final logo must work as a standalone symbol without text, including at launcher-icon and GitHub-avatar sizes.

Three distinct directions are defined below. They are intentionally different in construction rather than superficial variations of one idea.

### 5.1 Direction A — Registration Strip

#### Construction

A compact vertical strip forms the main silhouette. One edge is interrupted by two or three short registration notches or raster cells. Negative space inside the strip creates a simple internal path that can subtly imply the stem/bowl structure of an `R` without becoming a literal letterform.

The silhouette should remain recognizable before the viewer notices the hidden `R` association.

Conceptually:

```text
┌─────┐
│ ▪ ▪ │  ← sparse raster / registration marks
│ ┌─┐ │
│ │ │ │  ← negative-space channel
│ └─┘ │
│  ╲  │  ← subtle R-like leg / feed direction
└─────┘
```

The final symbol should be simpler than this ASCII sketch.

#### Symbolism

- paper strip;
- alignment and registration;
- raster preparation;
- physical feed direction;
- an optional hidden `R` for project recognition.

#### Strengths

- directly tied to the product’s document-to-paper model;
- can be highly distinctive without depicting a printer;
- strong in monochrome;
- naturally fits square and adaptive icon masks;
- supports a useful family of supporting graphics.

#### Weaknesses

- easy to over-detail;
- a too-literal `R` can make it look like a generic monogram;
- too many dots can resemble QR or barcode imagery.

#### 16–24 px behavior

The production small-size behavior is now defined by `assets/ASSETS.md`:

- **16–23 px:** use the approved micro optical variant, which preserves the outer silhouette/feed direction, removes the registration notches, enlarges the negative-space channel, and simplifies the bowl/diagonal transition;
- **24 px and above:** use the full canonical Registration Strip master unless pixel-grid testing documents a specific exceptional context.

Do not invent additional small-size redraws.

#### Android launcher icon

Excellent suitability. Center the mark with generous safe-area padding and avoid using the full adaptive-icon canvas edge-to-edge.

#### GitHub avatar

Excellent suitability. The symbol should remain legible in a circular crop without relying on the wordmark.

#### Monochrome viability

Excellent. The mark should be designed in one color first.

#### Light/dark behavior

Use positive and reversed versions. Do not add shadows or outlines solely to make the mark survive dark mode.

---

### 5.2 Direction B — Preview / Output Gate

#### Construction

Two adjacent rectangular frames represent a prepared preview and physical paper output. A shared alignment axis or connecting channel runs through both, showing that they derive from the same underlying geometry.

A small offset or clipped lower edge differentiates the physical strip from the preview frame.

Conceptually:

```text
┌───┐  ┌───┐
│   │──│   │
│   │  │   │
└───┘  └─┬─┘
          │
```

The logo should be reduced to a compact geometric symbol, not a diagram.

#### Symbolism

- preview and print correspondence;
- one plan expressed in two outputs;
- digital/physical parity;
- measured geometry.

#### Strengths

- strongly reflects Rastrio’s architecture and product promise;
- feels technical without showing protocol details;
- lends itself to documentation diagrams and motion.

#### Weaknesses

- abstract for users who have not encountered the product;
- can resemble sync, mirroring, or file-transfer icons;
- harder to make distinctive at favicon scale.

#### 16–24 px behavior

Collapse to two bold blocks with one shared alignment notch. Avoid thin connector lines.

#### Android launcher icon

Good, but less immediately iconic than Direction A.

#### GitHub avatar

Good if the spacing is optically exaggerated for small sizes.

#### Monochrome viability

Excellent.

#### Light/dark behavior

Straightforward positive/reversed use. The internal gap must remain open in both modes.

---

### 5.3 Direction C — Raster Fold

#### Construction

A compact field of square raster cells transitions into a continuous paper strip. The upper or left portion appears digital and granular; the lower or right portion resolves into a clean physical sheet.

The transition should be structural, not gradient-based.

Conceptually:

```text
▪ ▪ ▪ ┐
 ▪ ▪  │
▪ ▪   │
      │
      │
      ┘
```

#### Symbolism

- rasterization;
- digital-to-physical transformation;
- images and text becoming printable monochrome geometry;
- thermal output.

#### Strengths

- visually distinctive;
- directly references one-bit raster behavior;
- strong basis for banners and release art;
- avoids generic printer imagery.

#### Weaknesses

- the dot field can disappear at small sizes;
- can drift toward generic pixel-art or image-processing branding;
- less representative of native printer text and document semantics.

#### 16–24 px behavior

Use three or four large cells maximum and a strong continuous lower shape.

#### Android launcher icon

Good, provided the raster section is simplified aggressively.

#### GitHub avatar

Good at medium size; only adequate at very small size.

#### Monochrome viability

Very good.

#### Light/dark behavior

Works well with positive/reversed treatments. Avoid opacity-based dots that disappear on dark backgrounds.

---

## 6. Recommended Logo Direction

**Direction A — Registration Strip** is the canonical direction.

It best represents the project because it combines the physical paper object with the technical ideas of alignment, raster preparation, and controlled output while remaining usable as a compact application icon.

Direction B is valuable as a supporting diagrammatic motif, especially for explaining preview/print correspondence. Direction C is valuable as a supporting texture or illustration motif for raster-related features. Neither should become a second logo.

### 6.1 Required properties of the final symbol

The production symbol system MUST:

- be designed in monochrome first;
- remain recognizable at 16 px through the approved micro optical variant;
- work inside circular, square, rounded-square, and Android adaptive masks;
- avoid internal detail thinner than the equivalent of one robust UI stroke at target size;
- contain no text;
- avoid a literal printer body, paper-roll clip art, receipt currency symbols, or barcode as the primary form;
- remain visually balanced when reversed on dark backgrounds;
- be reproducible as simple vector geometry;
- avoid gradients as a structural requirement.

### 6.2 Optical character

The mark should feel constructed, not hand-drawn.

Use:

- orthogonal geometry;
- one controlled diagonal at most;
- modest corner softening;
- intentional asymmetry if needed for the `R`/feed cue;
- generous internal negative space.

Avoid perfect mechanical symmetry if it makes the mark anonymous.

---

## 7. Wordmark System

### 7.1 Canonical visual treatment

Use **`Rastrio`** as the canonical wordmark capitalization.

Rationale:

- it matches normal prose and repository naming;
- it is easier to read than all caps in documentation-heavy contexts;
- it feels like a software tool rather than a hardware manufacturer;
- it avoids the casual tone of all-lowercase branding.

`RASTRIO` MAY be used as a small technical label in diagrams or metadata, but it is not the primary wordmark.

`rastrio` SHOULD NOT be used as the main visual wordmark except where lowercase is structurally required, such as package names, command names, or URLs.

### 7.2 Typeface character

The wordmark uses the approved modern grotesk/humanist character established by **IBM Plex Sans**:

- clean counters;
- moderate width;
- strong lowercase legibility;
- restrained technical character;
- no exaggerated geometric circles;
- no futuristic cuts or stencil effects.

The production horizontal wordmark is now **frozen as vector outlines** in the canonical logo assets. Its outline source is the open-source IBM Plex Sans family, with the production geometry and spacing defined by `assets/ASSETS.md`. Official lockups MUST use those vector assets rather than recreating the wordmark with a live `<text>` element or substituting another installed font.

IBM Plex is distributed under the SIL Open Font License 1.1. If IBM Plex font files are separately redistributed with Rastrio applications, documentation, or design sources, the applicable font license and notices MUST be retained. The outlined production wordmark itself has no runtime font dependency.

### 7.3 Spacing

The wordmark should feel slightly tighter than ordinary UI text but never compressed.

Recommended approach:

- use normal or slightly negative tracking at display sizes;
- optically inspect `Ra`, `st`, `tr`, and `io` pairs;
- maintain clear separation between the symbol and wordmark;
- do not use wide letterspacing to manufacture a “tech” appearance.

### 7.4 Symbol relationship

The symbol and wordmark should share an optical cap-height relationship rather than literal bounding-box equality.

Preferred horizontal lockup:

```text
[ symbol ]  Rastrio
```

The symbol should appear approximately equal to or slightly taller than the wordmark cap height plus ascender area.

### 7.5 Horizontal lockup

Primary use for:

- README header;
- website navigation;
- documentation landing pages;
- release graphics;
- F-Droid feature graphics where text is useful.

Maintain clear space around the complete lockup of at least the visual width of the symbol’s internal negative-space channel.

### 7.6 Compact lockup

Use symbol only for:

- Android launcher icon;
- GitHub avatar;
- favicon;
- small documentation marks;
- social/project avatars.

A stacked symbol-over-wordmark lockup MAY be used in square promotional graphics, but should not become the default product signature.

### 7.7 Monochrome lockup

Every official lockup MUST have:

- solid dark-on-light version;
- solid light-on-dark version;
- single-ink version suitable for printed documentation and thermal-print demonstrations.

The identity must not rely on brand color to remain recognizable.

---

## 8. Typography

The typography system should remain deliberately small.

### 8.1 Brand and project typography

**Recommended:** IBM Plex Sans

Use for:

- project website headings;
- release graphics;
- branded diagrams;
- README hero graphics;
- wordmark source if a text-based wordmark is retained.

Recommended weights:

- Regular for body/supporting text;
- Medium for labels;
- SemiBold for headings and the wordmark;
- Bold only for occasional strong hierarchy.

Why it fits:

- technical without looking like a terminal font;
- credible in developer documentation;
- readable for ordinary users;
- broad family suitable for diagrams and product communication;
- open-source distribution model appropriate for a FOSS project.

### 8.2 Application UI typography

**Preferred default:** platform-appropriate Compose/system typography.

Do not force the brand font into every application surface merely for consistency. UI readability, text scaling, script coverage, platform conventions, and rendering quality are more important than brand uniformity.

A branded typeface MAY be used selectively for:

- top-level app title;
- empty-state heading;
- onboarding/project-about surfaces;
- marketing screenshots.

Ordinary controls, editors, forms, dialogs, and settings SHOULD follow the shared Compose typography system and platform accessibility behavior.

### 8.3 Documentation typography

For GitHub Markdown and generated technical documentation, use the host platform’s native/documentation stack unless there is a controlled project website.

On a project website, IBM Plex Sans is appropriate for prose and navigation.

Do not embed a custom webfont if it materially harms performance or offline documentation use without clear benefit.

### 8.4 Code and technical material

**Recommended:** JetBrains Mono

JetBrains Mono is distributed under the SIL Open Font License 1.1. Its license should be retained with any redistributed font files.

Use for:

- code samples;
- protocol bytes;
- `.td` / `.tcfg` examples;
- geometry annotations;
- developer diagrams where monospaced alignment matters.

System monospace is an acceptable fallback.

### 8.5 Secondary international fallback

Where broader script coverage is required for branded material, **Noto Sans** is the preferred fallback family. Noto families are distributed under the SIL Open Font License 1.1; use the appropriate script-specific family and retain its license when redistributing font files.

The application itself should rely on the platform/text stack needed for correct language coverage rather than assuming one brand font covers all content.

---

## 9. Color Direction

Rastrio’s color system should come from the physical/technical contrast of **paper, carbon, registration, and heat**, not from generic SaaS palette conventions.

This section defines the brand intent. Exact production palette values, light/dark semantic tokens, preview colors, accessibility pairings, and Compose token guidance are defined by `docs/VISUAL_SYSTEM.md` and MUST be used for implementation rather than re-derived from the descriptive ranges below.

### 9.1 Primary family — Registration Blue

Use a restrained, slightly desaturated blue with a technical-print / registration character rather than a bright corporate blue.

Role:

- primary interactive emphasis;
- selected states;
- links;
- focus treatment;
- brand accent in diagrams and website elements;
- optional launcher-icon field or mark.

Character:

- cool;
- precise;
- calm;
- high-confidence rather than energetic.

Avoid electric cyan, neon blue, and heavily saturated royal blue.

### 9.2 Secondary family — Thermal Amber

Use a warm amber/copper family as a secondary accent referencing heat and physical print energy.

Role:

- selective highlights;
- calibration/physical-output emphasis;
- illustration detail;
- optional progress or “prepared/ready” accents where semantically appropriate.

It MUST NOT be overloaded as the warning color unless the semantic palette is deliberately coordinated to keep states distinct.

Avoid orange-heavy branding that makes the app look like a delivery, food, or commerce product.

### 9.3 Neutral family — Paper and Carbon

The neutral palette is central to the identity.

Light theme should be built around:

- paper white rather than blue-white;
- soft warm-gray surfaces;
- carbon/graphite primary text;
- restrained separators.

Dark theme should be built around:

- deep graphite rather than pure black for large surfaces;
- near-white text rather than bright paper white everywhere;
- slightly lifted charcoal preview panels;
- carefully controlled edge contrast.

Pure black and pure white remain useful for content previews where they represent actual monochrome print data.

### 9.4 Semantic colors

Semantic status colors must remain conventional enough to be immediately understood.

#### Success

Use a moderate green family. Avoid using the primary Registration Blue as success merely for brand consistency.

#### Warning

Use a distinct amber/yellow family separated from the Thermal Amber brand accent by context, value, or saturation. If this distinction becomes ambiguous in testing, reserve the brand amber for non-semantic visuals and use a conventional warning token exclusively for warnings.

#### Error

Use a clear red family with sufficient contrast in both themes.

#### Information

Use blue or blue-cyan derived from, but not necessarily identical to, the primary brand family.

### 9.5 Accessibility rules for color

- Text color combinations MUST meet the project’s chosen WCAG contrast target; as a baseline, normal text should target at least 4.5:1 and large text / meaningful UI graphics at least 3:1.
- Disabled states must remain legible and should not depend on low opacity alone.
- Error, warning, and success states must include iconography, labels, or other non-color cues.
- Raster previews MUST preserve the actual black/white output representation and must not be tinted merely to match the brand.
- Focus indicators must remain visible against both paper-like and graphite surfaces.

---

## 10. Shape Language

### 10.1 Geometry

The system should be **rectilinear with modest softening**.

Preferred traits:

- rectangles and strips over circles;
- clear alignment axes;
- stepped or clipped details used sparingly;
- small-radius corners rather than pill-shaped containers;
- intentional negative space;
- strong edge relationships.

### 10.2 Corner radius

Use small-to-medium radii.

Conceptual hierarchy:

- paper/preview frames: small radius or nearly square;
- cards/panels: modest radius;
- dialogs/sheets: modest-to-medium radius consistent with platform guidance;
- chips/status pills: only when the component semantics call for them.

Avoid making every container a large rounded rectangle.

### 10.3 Line weights

Use a small set of consistent strokes:

- fine rule for document separators and layout guides;
- standard UI stroke for icons and controls;
- heavier stroke only for the logo or major diagram emphasis.

Do not imitate low-resolution receipt printing by making all UI lines jagged or pixelated.

### 10.4 Strips and rectangles

Vertical strips are the preferred brand shape because they evoke thermal paper and feed direction.

Horizontal strips may represent:

- segmentation;
- prepared bands;
- progress through a print job.

Use them semantically rather than as arbitrary decoration.

### 10.5 Raster dots and cells

Raster cells should be square or near-square.

Use them:

- as a small logo detail;
- in diagrams explaining raster output;
- as a subtle visual transition in release art;
- as a bounded background device in large surfaces.

Do not use dense dot matrices behind body text or UI controls.

### 10.6 Negative space

Negative space is a major part of the identity.

It should communicate:

- paper channels;
- feed paths;
- alignment;
- separation between logical and physical stages.

The brand should feel more “measured” than “filled.”

---

## 11. Icon and Illustration Direction

### 11.1 Product icons

Product icons should use:

- simple line or line-plus-fill construction;
- consistent optical stroke weight;
- squared/softened geometry;
- clear silhouettes at 20–24 dp;
- familiar platform metaphors where available.

Custom icons are appropriate for domain-specific concepts such as:

- physical preview;
- raster mode;
- paper width;
- cut guide;
- printer profile;
- segmentation;
- registration marks.

Do not replace standard platform icons with branded abstractions where that would reduce usability.

### 11.2 Thermal/document motifs

Preferred motifs:

- paper edge;
- cut/perforation line;
- alignment tick;
- print band;
- monochrome raster;
- preview frame;
- logical/physical split;
- small printer-head-like bar only as an abstract detail.

Avoid:

- cash-register symbols;
- shopping carts as general project branding;
- currency signs;
- restaurant receipts;
- giant printer clip art;
- barcode stripes as the main visual language;
- glowing “data streams.”

### 11.3 Illustration style

Illustrations should be diagrammatic, flat, and restrained.

Recommended:

- two-dimensional geometry;
- limited brand palette;
- no photorealism;
- no glossy 3D devices;
- no isometric office scenes;
- no mascot unless the project later makes a deliberate community decision to introduce one.

A useful illustration should explain a product idea, workflow, or output characteristic rather than merely occupy space.

---

## 12. Screenshots and README Visuals

### 12.1 Screenshot treatment

Screenshots should present Rastrio as working software.

Preferred treatment:

- real application UI;
- realistic document examples;
- generous neutral background;
- clear crop around the task being demonstrated;
- subtle device/window framing only when it adds platform context;
- one short caption if explanation is necessary.

Do not bury UI in decorative mockups.

### 12.2 Physical output pairing

Where practical, release material may pair:

```text
[ physical preview ]  →  [ photographed thermal output ]
```

This is especially appropriate for demonstrating raster, QR, layout, and hardware compatibility work.

The photograph should be documentary, not lifestyle imagery.

### 12.3 README hero visual

The README hero should prefer one of these structures:

#### Option A — product-first

```text
[ Rastrio symbol + wordmark ]
[ concise factual project description ]
[ application screenshot showing editor + preview ]
```

#### Option B — concept-first

```text
[ Rastrio symbol + wordmark ]
[ document geometry → paper strip brand graphic ]
[ small real UI screenshots below ]
```

Avoid a giant slogan with no product evidence.

### 12.4 Documentation diagrams

Architecture and workflow diagrams should use:

- neutral boxes;
- Registration Blue for primary flow or selected path;
- Thermal Amber only for physical-output or hardware-emphasis moments;
- consistent arrows;
- monospaced labels for concrete types such as `ThermalDocument` and `PreparedPrint`;
- no gradients or decorative shadows.

Architecture diagrams are technical artifacts first and brand surfaces second.

### 12.5 Background graphics

Acceptable:

- faint registration ticks;
- sparse one-bit cell patterns;
- clipped strip silhouettes;
- light document-grid geometry.

Avoid:

- full-page graph paper;
- matrix-code wallpaper;
- fake terminal text;
- random hexadecimal/ESC-POS byte streams;
- neon circuit traces.

---

## 13. Light and Dark Usage

### 13.1 Light mode

Light mode should feel like a clean document workspace rather than a sterile dashboard.

Use:

- paper-like primary surfaces;
- graphite typography;
- restrained blue interaction color;
- minimal elevation;
- thin separators;
- true black/white where print preview requires it.

### 13.2 Dark mode

Dark mode should feel like the same product, not a separate “developer theme.”

Use:

- deep graphite base;
- slightly elevated neutral panels;
- softened off-white text;
- Registration Blue adjusted for dark-background contrast;
- Thermal Amber used sparingly.

The physical paper preview MAY remain visually paper-like when that helps users understand expected print output. It should not automatically invert just because the application is in dark mode.

### 13.3 Logo rules

On light backgrounds:

- prefer carbon/graphite symbol and wordmark;
- primary-color symbol is optional.

On dark backgrounds:

- prefer paper/off-white or appropriately lightened primary symbol;
- preserve internal negative space;
- do not add a glow.

On photography or complex imagery:

- use a solid containing field if contrast is uncertain;
- do not use drop shadows as the only separation mechanism.

---

## 14. Motion Concept

Motion is supportive, not central to the identity.

### 14.1 Motion principles

Use motion to communicate:

- document compilation;
- preview resolution;
- paper/feed progression;
- transition from logical to physical preview;
- print-job progress;
- appearance of segments or cut boundaries.

### 14.2 Character

Motion should be:

- short;
- direct;
- spatially coherent;
- easy to interrupt;
- respectful of reduced-motion settings.

Prefer simple easing and translation/clip changes over elastic or bouncy effects.

### 14.3 Signature motion idea

A brand-level transition MAY use a **feed-and-register** motion:

1. a document frame moves a short distance along one axis;
2. alignment ticks snap into place;
3. the physical strip resolves.

This should take fractions of a second and should never simulate a long printer animation before the user can act.

### 14.4 Avoid

- looping paper-feed animations;
- fake thermal-burning effects;
- particle systems;
- glowing scan lines;
- over-animated loading states;
- motion required to understand status.

---

## 15. Voice and Tone

Rastrio writing should be precise, plain, and technically credible.

### 15.1 Core voice

Use language that is:

- concise;
- factual;
- calm;
- specific about what happened;
- explicit when behavior depends on printer capability;
- honest about uncertainty and hardware limitations.

Avoid startup hype, anthropomorphic copy, and exaggerated claims.

### 15.2 README

README copy should answer quickly:

- what Rastrio does;
- what platforms are currently supported;
- what hardware/protocol family it targets;
- what is stable versus planned;
- how to build or install it;
- how to contribute.

Prefer:

> Author, preview, and print documents on ESC/POS-compatible thermal printers.

Over:

> Reinventing the future of intelligent printing.

The first is a factual description; the second is not appropriate for the project.

### 15.3 F-Droid description

F-Droid copy should emphasize:

- open source;
- offline operation;
- no account requirement;
- document authoring/preview/printing;
- supported printer connection methods that are actually released.

Do not list roadmap features as if already available.

### 15.4 UI copy

UI labels should prefer concrete actions:

- `Prepare preview`
- `Select printer`
- `Print`
- `Save document`
- `Import printer profile`

Avoid vague action labels such as:

- `Go`
- `Do it`
- `Optimize`
- `Magic print`

Diagnostics should distinguish:

- document problem;
- unsupported content;
- preparation problem;
- transport problem;
- uncertain physical outcome.

### 15.5 Technical documentation

Technical documentation should:

- preserve canonical type names;
- define ownership and boundaries precisely;
- use normative language when specifying behavior;
- separate verified hardware behavior from inference;
- avoid decorative brand language inside architecture specifications.

Branding must never reduce technical precision.

### 15.6 Release notes

Release notes should prioritize:

1. user-visible behavior;
2. compatibility changes;
3. fixes affecting print correctness;
4. migration/security considerations;
5. notable internal engineering work.

Use direct statements such as:

> Fixed physical-preview alignment for centered raster images on narrow profiles.

Avoid:

> We’re thrilled to announce an incredible alignment upgrade!

---

## 16. Accessibility

Accessibility is a brand requirement, not an optional polish layer.

### 16.1 Contrast

- Normal text should target at least WCAG 4.5:1 contrast.
- Large text and meaningful non-text UI graphics should target at least 3:1.
- Focus indicators must be clearly visible in both themes.
- Brand colors must have dedicated accessible foreground pairings rather than relying on one universal text color.

### 16.2 Color-independent meaning

Do not encode printer state, diagnostics, errors, or completion status using color alone.

Pair color with:

- icon;
- text label;
- shape;
- placement;
- pattern only where appropriate.

### 16.3 Logo recognizability

The logo must remain recognizable:

- in one color;
- at 16 px;
- under grayscale conversion;
- on both light and dark backgrounds;
- when cropped into a circle;
- when viewed without the wordmark.

### 16.4 Small-size reproduction

The production asset system uses two intentional geometry classes:

- **canonical full master** at 24 px and above;
- **approved micro optical variant** at 16–23 px / favicon use.

The micro variant is defined by `assets/ASSETS.md`; it removes the left registration notches, opens the negative-space channel, and simplifies the bowl/diagonal transition. Do not create a separate 24–47 px logo master unless a future documented pixel-grid test demonstrates a concrete need.

### 16.5 UI accessibility

Brand decisions must not interfere with:

- scalable text;
- keyboard navigation;
- Android touch-target sizing;
- semantic labels;
- high-contrast requirements;
- reduced-motion preferences.

The application’s editor and previews are functional surfaces; brand styling must never reduce their legibility.

---

## 17. Usage Examples

### 17.1 GitHub repository

Recommended hierarchy:

```text
[Registration Strip symbol] Rastrio

Open-source thermal-document authoring, preview, and printing.

[build] [license] [platform/release badges]

[real application screenshot]
```

Use the symbol as the repository/social preview identity. Keep badges visually secondary.

### 17.2 Android launcher icon

- use the standalone Registration Strip mark;
- use a solid neutral or Registration Blue field;
- maintain substantial adaptive-icon safe area;
- do not place the word `Rastrio` inside the launcher icon;
- do not depict a complete printer.

### 17.3 F-Droid listing

Feature graphic:

- symbol + wordmark;
- one editor/preview screenshot or a restrained document-to-strip illustration;
- paper/carbon neutral base;
- one brand accent;
- no slogan required.

Screenshots should demonstrate actual workflows in a consistent order, for example:

```text
Home → authoring → physical preview → printer selection → print status
```

Only show workflows implemented in the release being documented.

### 17.4 Desktop and Web

Use the same symbol and wordmark as Android.

Do not create platform-specific logo variants.

Window title and website masthead may use the horizontal lockup. Favicon uses the micro symbol.

### 17.5 Developer documentation

Use branding sparingly:

- small symbol/wordmark at the documentation root;
- consistent heading typography on the project website;
- brand-colored links/focus states;
- technical diagrams using the same visual grammar.

Specification pages themselves should remain documentation-first.

### 17.6 Architecture diagrams

Example visual mapping:

```text
ThermalDocument      neutral document frame
        ↓
LogicalDocument      neutral/blue layout frame
        ↓
PreparedPrint        Registration Blue emphasis
      ↙   ↘
 Preview   ESC/POS   equal visual weight
             ↓
          Printer    Thermal Amber physical/hardware accent
```

This color use illustrates stages without implying that color is part of the data model.

### 17.7 Printer Lab

Printer Lab can use a denser technical visual language than the main app:

- calibration ticks;
- measurement annotations;
- raster cells;
- monospaced values;
- restrained status colors.

It should still use the same typography, icon family, spacing, and color system. It must not look like a separate engineering application.

### 17.8 Release graphics

A release image may combine:

- large symbol;
- version number;
- one key real screenshot;
- one restrained strip/raster motif;
- concise feature labels.

Avoid promotional layouts that obscure the product UI.

---

## 18. Things Rastrio Should Never Look Like

Rastrio should never visually drift into any of the following categories.

### 18.1 POS / restaurant software

Avoid:

- receipt totals as the primary motif;
- restaurant order tickets;
- cash-register imagery;
- currency symbols;
- red/green transactional dashboards;
- “checkout” visual language.

Receipts are one supported content type, not the product identity.

### 18.2 Generic startup SaaS

Avoid:

- gradient blobs;
- giant rounded cards everywhere;
- purple-blue neon gradients;
- abstract orbit shapes;
- floating glass panels;
- generic “platform” illustrations;
- empty superlatives.

### 18.3 AI branding

Avoid:

- sparkle icons;
- glowing nodes;
- neural-network patterns;
- magic-wand metaphors;
- copy implying generative intelligence.

### 18.4 Crypto / Web3 branding

Avoid:

- black + electric neon as the default palette;
- token/coin motifs;
- faceted 3D symbols;
- speculative-finance aesthetics.

### 18.5 Industrial control panel

Avoid making the main application resemble:

- firmware flashing software;
- serial-console utilities;
- printer service menus;
- SCADA-style dashboards;
- dense hardware telemetry panels.

Advanced diagnostics belong in Printer Lab, not in the product’s primary visual identity.

### 18.6 Generic printer utility

Avoid:

- Windows-era printer clip art;
- printer-with-paper pictograms as the main logo;
- inkjet/laser imagery unrelated to thermal hardware;
- page-curl effects;
- fake print buttons as brand marks.

### 18.7 Retro receipt aesthetic

Avoid overusing:

- typewriter fonts;
- distressed thermal textures;
- jagged low-resolution text;
- faux paper stains;
- nostalgic cash-register visuals.

Thermal printing is the medium, not a retro theme.

---

## 19. Production Logo Contract

The Registration Strip identity has moved from design direction to a production asset system. **Do not redesign or independently redraw the official mark during normal implementation work.**

`assets/ASSETS.md` is the authority for canonical geometry, derivation rules, production filenames, micro-size behavior, wordmark lockups, Android adaptive-icon construction, and raster exports.

### 19.1 Canonical source

The authoritative full-size symbol is:

```text
assets/brand/logo/rastrio-symbol.svg
```

All normal-size fixed-color symbol variants and symbol-derived compositions MUST be mechanically derived from that geometry. The approved 16–23 px favicon artwork is an intentional optical variant and MUST NOT become an alternate normal-size master.

The official horizontal wordmarks are path-based vector assets. They MUST NOT be rebuilt from live text at runtime.

### 19.2 Production verification

When the canonical geometry itself is deliberately changed as a brand-governance change, verify at minimum:

```text
16 px   20 px   24 px   32 px   48 px   96 px   256 px
```

Check:

- paper/light surfaces;
- graphite/dark surfaces;
- Registration Blue field;
- grayscale/one-ink reproduction;
- circular crop;
- Android adaptive-icon masks and safe area;
- representative thermal-printer reproduction where useful.

Ordinary application development does not require re-approving the logo. It requires using the canonical assets correctly.

---

## 20. Brand Governance

### 20.1 Canonical assets

The production asset hierarchy is established under `assets/`. The authoritative construction and complete manifest live in `assets/ASSETS.md`.

Key sources are:

```text
assets/brand/logo/rastrio-symbol.svg
assets/brand/logo/rastrio-wordmark-horizontal.svg
assets/brand/logo/rastrio-wordmark-horizontal-inverse.svg
assets/brand/icons/favicon.svg
assets/brand/app/adaptive-foreground.svg
assets/brand/app/adaptive-background.svg
```

`assets/brand/logo/rastrio-symbol.svg` is the authoritative normal-size logo geometry. Generated PNG/WebP exports and color variants are derivatives, not alternate masters.

### 20.2 Source format

SVG is the canonical source format for logos and simple diagrams.

Raster exports should be generated from canonical vectors rather than edited independently.

### 20.3 Asset discipline

Official variants should be limited to justified cases such as:

- positive;
- reversed;
- primary-color;
- monochrome;
- full-size;
- simplified/micro.

Do not accumulate arbitrary alternate logos per release, platform, or contributor preference.

### 20.4 Platform consistency

Android, Desktop, Web, GitHub, F-Droid, and documentation should use the same core identity.

Platform conventions may affect icon mask, spacing, or UI typography, but must not produce different brands.

---

## 21. Decision Summary

The brand baseline is:

```text
Core idea       registered paper strip / digital-to-physical precision
Logo direction  Registration Strip
Wordmark        Rastrio
Brand type      IBM Plex Sans
Wordmark source frozen vector outlines derived from IBM Plex Sans
UI type         Compose/system typography by default
Code type       JetBrains Mono
Primary color   restrained Registration Blue family
Accent color    controlled Thermal Amber family
Neutrals        paper white ↔ carbon/graphite
Geometry        rectilinear, modestly softened, strip-oriented
Texture         sparse 1-bit raster / registration motifs
Motion          short feed-and-register transitions
Voice           factual, concise, technically credible
```

The identity should look at home beside source code, technical specifications, an Android application, a physical thermal print, and a GitHub README without changing character.

---

## 22. Production Status and Remaining Tuning

There are no unresolved brand decisions that block implementation.

The following production decisions are now resolved by the downstream visual/asset specifications:

- canonical Registration Strip SVG geometry and optical corrections — `assets/ASSETS.md`;
- production Registration Blue / Thermal Amber / Paper / Carbon color values — `docs/VISUAL_SYSTEM.md`;
- light/dark semantic color tokens and accessibility pairings — `docs/VISUAL_SYSTEM.md`;
- Android adaptive-icon background/foreground construction and safe area — `assets/ASSETS.md`;
- horizontal wordmark geometry and spacing — frozen vector outlines defined by `assets/ASSETS.md`;
- 16–23 px micro/favicon treatment — `assets/ASSETS.md`.

The remaining work is ordinary implementation tuning rather than an unresolved identity decision. In particular, exact application/website typography scales MAY be refined through real UI prototypes, platform text-scaling behavior, and accessibility testing. Such tuning MUST preserve the typographic hierarchy and character in this guide and MUST NOT alter the canonical logo/wordmark geometry.

If this guide and a downstream production specification disagree about an implementation value that the downstream specification explicitly owns, use the downstream specification while preserving this guide's brand principles. A deliberate identity change requires updating the relevant documents and canonical assets together.

