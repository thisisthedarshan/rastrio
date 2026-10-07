# RastrIO Preview Specification

**Status:** Normative  
**Specification revision:** 1.1  
**Applies to:** RastrIO Core, Shared Presentation, Shared Compose UI
**Primary modules:** `core-preview`, `core-printer`, `core-layout`, `core-text`, `core-raster`  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related specifications:** `docs/TEXT_RENDERING_SPEC.md`, `docs/TCFG_SPEC.md`, `docs/TESTING.md`, `docs/RESOURCE_LIMITS.md`, `docs/SECURITY.md`

---

## 1. Purpose

This document defines RastrIO's logical-preview and physical-preview contracts.

Its primary purpose is to ensure that preview is a faithful view of Core output rather than an independent document renderer or printer simulator.

RastrIO has two distinct preview modes:

```text
LogicalDocument
      ↓
core-preview
      ↓
LogicalPreview
```

and:

```text
PreparedPrint
      ↓
core-preview
      ↓
PrintPreview
```

The two modes answer different questions.

A **Logical Preview** answers:

> How has the document been laid out on its logical canvas?

A **Physical Preview** answers:

> What physical output plan has RastrIO prepared for the selected printer?

Physical preview is architecture-critical because it is the user-visible representation of the same printer-specific plan that will later be serialized by the active trusted protocol encoder.

For `.tcfg` schema v1 the supported protocol family is ESC/POS, so that encoder is `core-escpos`. A future protocol encoder MUST obey the same preview/plan boundary.

This specification therefore defines:

- preview ownership;
- logical-preview semantics;
- physical-preview semantics;
- portable preview representations;
- preview accuracy classes;
- rendering of prepared operations;
- physical segmentation representation;
- cut semantics;
- diagnostics;
- lazy and tiled rendering;
- bounded-memory requirements;
- caching and invalidation;
- UI boundaries;
- preview/print consistency requirements;
- deterministic testing requirements.

---

## 2. Normative Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

Where this specification provides conceptual Kotlin types, the exact class names, package names, inheritance structure, and implementation details MAY differ.

Changing an implementation detail is permitted only when the behavioral and architectural contracts in this specification remain unchanged.

The authoritative product baseline remains `PRD.md` v2.1 and the technical ownership baseline remains `docs/ARCHITECTURE.md`. This specification adds preview-specific precision but MUST NOT weaken or contradict either.

---

## 3. Scope

This specification covers:

```text
LogicalDocument → LogicalPreview
PreparedPrint   → PrintPreview
```

and the rendering of those portable preview models by Shared Compose UI.

It covers preview behavior for:

- logical document canvases;
- physical paper geometry;
- printable regions;
- native printer text;
- raster text;
- raster images;
- native and raster QR codes;
- separators;
- feed operations;
- manual cut guides;
- automatic cutter operations;
- landscape physical segmentation;
- overlap regions;
- registration marks;
- join guides;
- segment numbering;
- diagnostics;
- unsupported or inconsistent operations;
- very long documents.

It also defines when a preview becomes invalid and must be regenerated.

---

## 4. Non-Goals

Preview is not:

- a second layout engine;
- a second printer-preparation engine;
- an ESC/POS interpreter;
- a printer-emulator implementation;
- a substitute for `PrinterProfile`;
- a substitute for physical hardware testing;
- a Compose text-layout authority;
- a place to choose native versus raster strategies;
- a place to choose code pages;
- a place to re-dither images;
- a place to re-shape or re-wrap physical text;
- a transport simulator;
- a mechanism for modifying a `PreparedPrint`;
- a guarantee that every printer's undocumented mechanical behavior can be simulated exactly.

In particular, RastrIO MUST NOT claim literal pixel-perfect simulation of printer ROM glyph shapes unless sufficient printer-font data exists to provide such a guarantee.

---

# Part I — Architectural Contract

## 5. Preview Ownership

Preview responsibilities are divided as follows.

### 5.1 `core-layout`

`core-layout` owns logical geometry.

It produces:

```text
ThermalDocument
      +
LayoutConstraints
      ↓
LogicalDocument
```

It owns such matters as:

- line breaking;
- wrapping;
- block flow;
- table geometry;
- alignment;
- indentation;
- logical image placement;
- logical QR placement;
- logical canvas coordinates.

`core-preview` MUST NOT independently perform any of these calculations.

---

### 5.2 `core-text`

`core-text` owns shared text measurement and rendering contracts.

It provides the text geometry required by layout and preparation.

For physical output, all output-affecting text shaping, measurement, fallback, and rasterization decisions MUST have been completed before the resulting `PreparedPrint` is considered final.

Preview MUST NOT invoke an alternative text-layout path.

---

### 5.3 `core-printer`

`core-printer` owns printer-specific physical decisions.

These include:

- printable physical width;
- dot conversion;
- native versus raster text;
- code-page selection;
- physical text geometry;
- image raster resolution;
- QR strategy;
- segmentation;
- overlap;
- registration marks;
- segment numbering;
- manual cut guides;
- automatic cuts;
- protocol strategy selection;
- printer-specific diagnostics.

The result is the immutable `PreparedPrint`.

`core-printer` owns the `PreparedPrint` model, prepared-operation types, and the portable read-only contract for any bounded immutable prepared artifacts referenced by that plan.

`core-preview` is downstream of `core-printer`.

---

### 5.4 `core-preview`

`core-preview` owns conversion of already-resolved Core geometry into portable preview representations.

It MUST NOT introduce new output-affecting decisions.

It MAY:

- transform physical coordinates into preview coordinates;
- clip already-resolved primitives to requested tiles;
- scale content for display;
- read finalized prepared raster/artifact content through the portable read-only prepared-artifact contract;
- create non-printing UI annotations;
- create representative glyph visuals for native printer text;
- expose segment metadata;
- expose preview-specific accessibility or inspection metadata;
- lazily materialize preview tiles.

It MUST NOT:

- re-layout;
- re-wrap;
- re-shape physical text;
- re-rasterize finalized prepared text/images/graphics because the prepared bytes are banded, tiled, or spooled;
- select code pages;
- choose native versus raster output;
- change physical image dimensions;
- re-dither;
- re-segment;
- recalculate overlap;
- introduce or remove cuts;
- alter QR dimensions;
- modify prepared alignment;
- interpret a prepared-artifact identifier as an attacker-controlled filesystem path;
- silently regenerate finalized prepared data when that data can no longer be read.

---

### 5.5 Shared Presentation

Shared Presentation:

- requests previews;
- stores current preview state;
- coordinates preparation when options change;
- handles loading and error state;
- selects which preview mode the UI is displaying.

Shared Presentation MUST NOT implement preview geometry or printer layout.

---

### 5.6 Shared Compose UI

Shared Compose UI displays portable preview output.

Compose MAY control:

- viewport size;
- zoom;
- scrolling;
- panning;
- lazy composition;
- visible annotations;
- selection/highlighting;
- user interaction.

Compose MUST NOT use its own text measurement, `TextLayoutResult`, font metrics, device density, or other UI-layout result to determine authoritative printer geometry.

The UI MUST treat preview geometry supplied by Core as authoritative.

---

## 6. Preview/Print Consistency Invariant

This is the central invariant of this specification.

# Preview/Print Consistency Invariant

For physical output, preview and printing MUST consume the same immutable physical plan:

```text
                       PreparedPrint
                         /        \
                        /          \
                       ▼            ▼
                core-preview   protocol encoder
                       │            │
                       ▼            ▼
                PrintPreview    Byte Stream

v1 protocol encoder = core-escpos
```

The physical preview MUST be derived from the exact `PreparedPrint` that is supplied to the active protocol encoder, or from an equivalent immutable value snapshot of that same plan.

Where `PreparedPrint` references bounded immutable prepared artifacts, preview and encoding MUST read the same finalized artifact content or equivalent immutable snapshots of it. Storage representation does not create a second output authority.

There MUST NOT be:

```text
PreparedPrint
    ↓
PreviewLayoutEngine
    ↓
different physical geometry
```

There MUST NOT be a second implementation of:

- line wrapping;
- native-font advance calculations;
- raster fallback decisions;
- image sizing;
- QR sizing;
- finalized raster/graphic generation;
- horizontal segmentation;
- overlap;
- cut placement;
- feed placement;
- physical alignment.

`PrintPreview` is a view of `PreparedPrint`.

It is not an alternate interpretation of the source document.

If preview code encounters information that it requires but which is absent from `PreparedPrint`, preview MUST NOT silently derive a new physical decision from `ThermalDocument`, `LogicalDocument`, `PrinterProfile`, or UI state.

Instead, one of the following MUST occur:

1. the information is added upstream to the physical preparation contract;
2. preview renders a documented approximation that cannot alter output geometry;
3. preview produces a structured diagnostic;
4. preview fails the affected representation.

This invariant applies equally to Android, Desktop, and Web.

---

## 7. Immutability Contract

A finalized `PreparedPrint` MUST be immutable from the perspective of preview and encoding.

Immutability is semantic, not synonymous with one in-memory object graph. Large prepared raster data MAY be held through immutable application-controlled prepared artifacts as defined by `core-printer`.

Generating a `PrintPreview` MUST NOT mutate:

- `PreparedPrint`;
- its segments;
- operation order;
- raster data;
- diagnostics;
- prepared dimensions;
- text geometry;
- cuts;
- overlap definitions;
- registration marks.

Generating several previews from the same `PreparedPrint` MUST NOT change subsequent protocol serialization.

Likewise:

```text
preview(PreparedPrint)
```

followed by:

```text
encode(PreparedPrint)
```

MUST produce the same physical-plan semantics as:

```text
encode(PreparedPrint)
```

without previewing first.

If finalized prepared artifact content becomes unavailable, corrupted, or unreadable during the lifetime required by the preparation, preview MUST fail the affected representation with a structured diagnostic. It MUST NOT silently re-run text shaping, image processing, dithering, checklist-marker generation, QR rasterization, or any other output-affecting preparation in order to repair the artifact.

Preview caches MUST be external derived state and MUST NOT modify the prepared plan.

---

# Part II — Logical Preview

## 8. Logical Preview Definition

Logical preview consumes:

```text
LogicalDocument
```

and produces a portable representation conceptually named:

```text
LogicalPreview
```

The exact Kotlin type name may differ.

A logical preview represents the complete logical layout produced by `core-layout` under the `LayoutConstraints` used to construct that `LogicalDocument`.

---

## 9. Purpose of Logical Preview

Logical preview exists primarily for:

- authoring;
- document inspection;
- inspecting resolved wrapping;
- viewing tables;
- viewing code blocks;
- viewing assembled landscape documents;
- inspecting wide logical canvases;
- Desktop development;
- preview before a physical printer is selected.

It answers questions about the logical document rather than a particular printer protocol.

For example:

```text
180 mm logical landscape document
```

may be displayed as one assembled logical canvas even when a future physical preparation would print it as several narrow strips.

---

## 10. Logical Preview Authority

Logical preview MUST use geometry already present in `LogicalDocument`.

It MUST NOT independently calculate:

- line breaks;
- table widths;
- row heights;
- paragraph positions;
- list indentation;
- image positions;
- QR positions;
- text alignment.

Logical preview MAY transform logical coordinates to screen coordinates for zooming or viewport presentation.

Such a transform MUST NOT be interpreted as modifying document geometry.

---

## 11. What Logical Preview May Represent

Logical preview MAY represent:

- the complete logical canvas;
- paragraph geometry;
- headings;
- text runs;
- lines;
- list items;
- checklists;
- quotes;
- code;
- tables;
- logical images;
- QR placeholders or logical QR representations;
- separators;
- logical alignment;
- explicit line breaks;
- portrait layouts;
- wide landscape layouts;
- logical diagnostics;
- source-selection or editing overlays supplied separately by Presentation.

Where text is still represented semantically or through logical text runs, the preview MAY use appropriate portable text-rendering facilities.

However, authoritative logical text positions and wrapping MUST come from `LogicalDocument`.

---

## 12. What Logical Preview Must Not Represent as Authoritative

Logical preview MUST NOT imply authoritative knowledge of printer-specific matters that have not yet been prepared.

Unless those values are already an intentional part of the logical model, logical preview MUST NOT invent:

- printer dots;
- ESC/POS commands;
- code pages;
- printer ROM glyphs;
- native/raster physical strategy;
- physical raster bands;
- physical paper segmentation;
- overlap between strips;
- printer registration marks;
- cutter commands;
- Bluetooth or USB behavior;
- printer buffer behavior.

Logical preview is protocol-independent.

---

## 13. Logical Preview and Wide Documents

For landscape or otherwise wide logical documents, logical preview SHOULD support inspection of the assembled logical canvas.

For example:

```text
LogicalDocument: 180 mm wide
```

may be displayed as:

```text
┌────────────────────────────────────────────────────────────┐
│                  assembled logical document                │
└────────────────────────────────────────────────────────────┘
```

This representation exists before physical strip segmentation.

Logical preview MUST NOT preemptively divide semantic content into printer-width slices.

Physical segmentation belongs to `core-printer`.

---

# Part III — Physical Preview

## 14. Physical Preview Definition

Physical preview consumes only the authoritative physical plan:

```text
PreparedPrint
```

and produces a portable representation conceptually named:

```text
PrintPreview
```

Physical preview represents what RastrIO expects the selected printer to produce geometrically and operationally.

It includes physical-output content as well as clearly differentiated non-printing annotations for operations such as automatic cuts.

---

## 15. Physical Preview Purpose

Physical preview MUST allow the user to inspect:

- selected physical width;
- printable area;
- resolved text wrapping;
- resolved alignment;
- physical positions;
- native versus raster decisions where relevant;
- raster output;
- QR output;
- feeds;
- separators;
- segment boundaries;
- overlap;
- registration marks;
- join guidance;
- manual cut guides;
- automatic cuts;
- diagnostics.

Its primary contract is not that every displayed glyph has the exact appearance of printer firmware.

Its primary contract is that physical geometry and output decisions already made by preparation are represented faithfully.

---

## 16. `PreparedPrint` Completeness Requirement for Preview

Any value required to determine what physically appears on paper MUST be resolved before physical preview.

If an output-affecting value is absent, the correct repair is normally to enrich preparation rather than let preview calculate it independently.

Examples include:

- physical x/y placement;
- operation dimensions;
- resolved line bounds;
- printable width;
- raster dimensions;
- raster/prepared-artifact references and stable content identity;
- prepared graphic-marker geometry where applicable;
- segment crop geometry;
- overlap;
- automatic-cut position;
- manual-cut-guide placement.

Preview MAY derive purely visual viewport information, such as:

```text
screenX = physicalX × zoom
```

because such transformations do not change print geometry.

---

# Part IV — Portable Preview Representation

## 17. Platform-Neutral Output

`LogicalPreview` and `PrintPreview` MUST be platform-neutral Core output.

They MUST NOT expose:

```text
android.graphics.Bitmap
android.graphics.Canvas
android.graphics.Paint
androidx.compose.*
Compose TextLayoutResult
Skia surface handles
browser CanvasRenderingContext2D
DOM nodes
platform-native font handles
```

A portable preview representation MAY use concepts such as:

```text
PreviewDocument
PreviewSegment
PreviewTile
PreviewPrimitive
PreviewAnnotation
PreviewRaster
PreviewText
PreviewBounds
PreviewDiagnostic
```

Exact type names are implementation details.

---

## 18. Coordinate Systems

Preview code MUST distinguish at least conceptually between:

1. logical document coordinates;
2. prepared physical coordinates;
3. preview viewport coordinates.

These coordinate spaces MUST NOT be conflated.

### 18.1 Logical coordinates

Owned by `LogicalDocument`.

Used for logical preview and mapping assembled document geometry.

### 18.2 Prepared physical coordinates

Owned by `PreparedPrint`.

Used for physical paper, segments, text operations, raster operations, cuts, and physical annotations.

Physical coordinates SHOULD use deterministic portable units.

Integer printer-dot coordinates SHOULD be preferred wherever the prepared operation is dot-addressed.

### 18.3 Viewport coordinates

Owned by the UI/rendering layer.

They represent zoomed or transformed screen coordinates.

Viewport scaling MUST NOT change any prepared physical value.

---

## 19. Preview Primitive Model

A preview implementation SHOULD distinguish between:

### Printed content

Content expected to result in visible marks or physical paper movement, such as:

- native text;
- raster text;
- raster images;
- raster QR;
- native QR visual representation;
- separators;
- registration marks;
- printed segment numbers;
- printed join or trim guides;
- manual cut guides.

### Operational annotations

Information representing an operation that is not itself printed content, such as:

- automatic cut boundaries;
- segment names used only by the UI;
- printable-area overlays;
- overlap highlighting;
- diagnostic markers;
- native-font accuracy indicators.

These two categories MUST remain semantically distinguishable.

A preview annotation MUST NOT accidentally enter ESC/POS encoding.

---

# Part V — Physical Operation Treatment

## 20. Paper Width

Physical preview MUST obtain its authoritative physical width from the prepared target information in `PreparedPrint`.

The UI MUST NOT:

- infer printer width from screen width;
- use a hard-coded 58 mm or 80 mm assumption;
- inspect `PrinterProfile` and independently derive a competing width;
- derive width from Compose text layout.

Where `PreparedPrint` distinguishes total media width from printable width, preview SHOULD display both distinctly.

Where preparation only knows the authoritative printable width, preview MUST NOT invent exact non-printable hardware margins.

In such a case the displayed printable strip remains authoritative while any surrounding depiction of physical paper edges MUST be treated as illustrative rather than calibrated physical margin data.

---

## 21. Printable Area

The printable area's dimensions and origin MUST come from `PreparedPrint`.

All prepared output operations MUST be displayed relative to that area using their resolved physical coordinates.

If an operation intentionally extends outside a nominal printable area and preparation permits it, preview SHOULD show that fact and any associated diagnostic.

Preview MUST NOT silently clamp the operation and imply that the prepared geometry was different.

Display clipping for viewport optimization is permitted; semantic geometry clipping is not.

---

## 22. Native Printer Text

Native printer text presents a special accuracy case.

A native text operation may include information such as:

```text
text
resolved x/y
resolved line geometry
selected native printer font
code page
style state
width/height scale
alignment
expected advances / cell geometry
```

Physical preview MUST preserve the prepared:

- line breaks;
- line placement;
- x/y position;
- alignment;
- expected advance geometry;
- cell or run dimensions;
- width/height scale;
- style intent.

Physical preview MUST NOT run the original text through Compose text layout to determine where characters fit.

### 22.1 Exact printer font available

If RastrIO has an exact validated printer-font model containing sufficient glyph and metric data, preview MAY render native text using that exact model.

In this case the implementation MAY describe the glyph appearance as exact to that model.

### 22.2 Exact printer font unavailable

If printer ROM glyph shapes are unavailable, preview MUST use a representative visual rendering while preserving the authoritative prepared geometry.

The v1 default representative preview family is the project-controlled bundled **Noto Sans** family defined by `docs/TEXT_RENDERING_SPEC.md`.

Preview SHOULD select the nearest available bundled face corresponding to the prepared style intent, while treating that face purely as a visual substitute for the printer ROM glyph shape.

However:

- the replacement font MUST NOT change line breaks;
- its natural advance widths MUST NOT reposition prepared runs;
- it MUST NOT cause a different physical alignment;
- it MUST NOT become a new printer measurement authority.

When necessary, representative glyphs SHOULD be positioned, clipped, or scaled inside prepared text cells or run bounds.

The preview contract in this situation is:

```text
geometry: authoritative
glyph shape: representative
```

not:

```text
printer ROM pixels: guaranteed identical
```

---

## 23. Raster Text

Raster text MUST be previewed from the final raster representation stored in or referenced by `PreparedPrint`.

Preview MUST NOT:

- shape the text again;
- choose a font again;
- rasterize the text again;
- perform fallback again.

If preparation stores raster text as bands, tiles, or bounded immutable prepared artifacts, preview MUST reconstruct the visible representation from those exact finalized data units.

Preview MAY stream only the artifact ranges required for the visible tile. It MUST NOT require the whole artifact to be materialized in memory.

The underlying monochrome dot pattern used by preview MUST match the prepared raster pattern that the active protocol encoder receives.

---

## 24. Raster Images

Raster image preview MUST use the final prepared monochrome raster.

Preview MUST NOT independently:

- resize;
- crop;
- rotate;
- alter gamma;
- alter brightness;
- alter contrast;
- threshold;
- dither.

If the prepared image is:

```text
384 × 1200 dots
```

the authoritative preview raster is that exact prepared dot matrix, irrespective of the dimensions or color content of the original source image.

Display scaling MAY resample the raster for the user's screen.

Prepared image data MAY be read lazily from a bounded immutable prepared artifact. Display resampling MUST operate on the finalized prepared raster, never on the original source asset.

Such display resampling MUST NOT alter the underlying preview source data.

At zoom levels intended for dot inspection, implementations SHOULD avoid smoothing that obscures the actual monochrome pattern.

---

## 25. QR Codes

Physical preview MUST reflect the QR strategy already selected by preparation.

### 25.1 Raster QR

For a `RasterQr` operation, preview MUST use the exact final raster data.

Its raster-data accuracy contract is the same as other prepared raster operations.

### 25.2 Native QR

For a `NativeQr` operation, preview MUST preserve the prepared:

- position;
- intended physical dimensions;
- payload semantics;
- error-correction setting;
- prepared native strategy parameters relevant to output.

If RastrIO can reproduce the exact module matrix that the target printer will generate under the selected native strategy, it MAY display that matrix as exact.

Otherwise, the displayed QR pattern is representative of the encoded QR content while the prepared placement and geometry remain authoritative.

Preview MUST NOT convert a native QR operation into a raster print operation merely for display.

The fact that preview draws pixels on a screen does not change the prepared physical strategy.

---

## 25.3 Checklist Markers

Checklist state is semantic source content, but physical checklist-marker pixels/geometry are finalized during preparation according to `docs/TEXT_RENDERING_SPEC.md`.

Physical preview MUST therefore display the finalized prepared checklist marker rather than substituting:

```text
emoji
Unicode checkbox glyph
icon font glyph
platform UI checkbox widget
```

When preparation rasterizes the complete checklist physical line/block to preserve marker/text composition, preview MUST display that exact finalized raster content.

The canonical distinction remains:

```text
unchecked = outlined square
checked   = outlined square + check stroke
```

but preview MUST NOT independently redraw those primitives when the physical plan already contains their finalized raster representation.

For logical preview, a checklist marker MAY be drawn from the logical checklist state using geometry already established by `LogicalDocument`; logical preview still MUST NOT use a UI checkbox widget as layout authority.

---

## 26. Separators

A prepared separator MUST be rendered according to its resolved physical geometry.

Preview MUST NOT infer separator width from current screen width.

Where separator geometry is defined as a physical rule, preview SHOULD display that exact rule.

Where separator output is represented through a different prepared strategy, preview MUST display the expected result according to that prepared operation without selecting an alternative strategy.

---

## 27. Feed Operations

Feed operations represent intentional physical paper advancement.

A prepared feed MUST therefore contribute the corresponding expected vertical space to physical preview.

The expected physical advance MUST originate in preparation.

Preview MUST NOT independently reinterpret:

```text
feed N
```

using arbitrary UI line height.

If a printer operation has inherently device-dependent mechanical tolerance, the preview represents the expected prepared advance, not a guarantee of zero mechanical variation on real hardware.

Feeds that result in meaningful blank physical distance MUST remain visible in physical preview.

---

## 28. Manual Cut Guides

A `ManualCutGuide` is printed content.

Therefore:

- it MUST appear in physical preview as printable output;
- its geometry MUST come from `PreparedPrint`;
- it MUST also be serialized for printing;
- it MUST participate in physical spacing;
- it MUST NOT be treated as a UI-only annotation.

If the guide includes line work, marks, or other printable geometry, preview MUST represent the prepared form.

---

## 29. Automatic Cut Boundaries

An `AutomaticCut` represents a physical cutter operation.

It is not printed content.

Physical preview MUST therefore represent it using a clearly non-printing annotation.

Conceptually:

```text
printed paper
──────────────────────────

- - - - AUTO CUT - - - -

next printed paper
```

The exact UI presentation MAY differ.

The automatic-cut annotation MUST:

- occur at the prepared cut position;
- indicate full or partial cut semantics where known;
- remain semantically separate from printed content;
- never become part of raster output;
- never become literal printed text;
- never be introduced when `PreparedPrint` contains no corresponding cut.

---

# Part VI — Landscape Segmentation

## 30. Physical Segments

Wide documents may be prepared as several physical `PrintSegment` values.

Physical preview MUST treat those segments as authoritative.

It MUST NOT take an assembled logical document and independently calculate printer-width slices.

The segmentation pipeline is:

```text
LogicalDocument
      ↓
core-printer
      ↓
PreparedPrint
      ↓
PrintSegment 1
PrintSegment 2
PrintSegment 3
...
```

Preview consumes the final segment definitions.

---

## 31. Physical Strip View

Physical preview SHOULD support a physical-strip representation for segmented jobs.

For example:

```text
Segment 1          Segment 2          Segment 3
┌──────────┐       ┌──────────┐       ┌──────────┐
│          │       │          │       │          │
│          │       │          │       │          │
│          │       │          │       │          │
└──────────┘       └──────────┘       └──────────┘
```

Each displayed strip MUST use the corresponding `PrintSegment`'s prepared physical geometry.

---

## 32. Assembled Physical View

A physical preview MAY additionally provide an assembled visualization of prepared strips.

Such a view MUST be reconstructed from the prepared segment mappings.

It MUST NOT:

- repeat logical layout;
- re-segment the source;
- change overlap;
- infer missing content from the source document.

This view is an inspection projection of `PreparedPrint`, not a replacement for `LogicalPreview`.

---

## 33. Overlap

When adjacent physical segments contain intentional overlap, physical preview MUST represent the exact prepared overlap extent.

The preview MAY visually highlight overlap regions.

Any highlighting is a non-printing annotation unless the prepared plan explicitly contains printed marks there.

Given adjacent segments:

```text
Segment A: [0 ............... 50]
Segment B:             [45 ............... 95]
```

the overlap:

```text
45 .. 50
```

must come from `PreparedPrint`.

Preview MUST NOT independently select 3 mm, 5 mm, or another overlap amount.

---

## 34. Segment Coverage

Physical preview MUST preserve the mapping between each physical segment and the logical region that it represents.

Segmentation MUST NOT visually lose logical content.

Content duplicated because of intentional overlap MUST remain duplicated in the corresponding physical-strip views.

Content MUST NOT be duplicated elsewhere solely because of preview implementation.

---

## 35. Registration Marks

Registration marks included by preparation are physical output.

They MUST therefore:

- appear in physical preview;
- use prepared geometry;
- be represented in encoding;
- appear on the exact segments for which they were prepared.

Preview MUST NOT generate registration marks merely because a segmented view is being displayed.

A separate UI-only indicator MAY be used for navigation, but it MUST remain distinguishable from printed registration marks.

---

## 36. Join Guides and Trim Guides

Join or trim guidance MUST have explicit prepared semantics.

If a guide is prepared as printed content:

- it MUST appear as physical output;
- preview MUST use its prepared geometry;
- encoding MUST serialize the corresponding prepared operation.

If a guide is represented as non-printing assembly metadata:

- preview MAY display it as an annotation;
- it MUST NOT appear in encoded print output.

Preview MUST NOT guess whether a guide is printed or informational.

The distinction belongs upstream in the prepared plan.

---

## 37. Segment Numbering

If segment numbering has been requested as printed output and preparation includes corresponding operations, preview MUST show the exact prepared numbering.

Examples may include:

```text
1 / 3
2 / 3
3 / 3
```

Preview MUST NOT invent printed segment numbers.

The UI MAY separately label preview cards or strip containers for navigation, for example:

```text
Segment 2
```

Such labels MUST be clearly external to the printable paper representation.

---

# Part VII — Accuracy Contract

## 38. General Accuracy Principle

"Accurate preview" does not mean that every characteristic of physical hardware can always be reproduced digitally.

RastrIO distinguishes several types of accuracy.

Preview implementations and UI copy MUST respect those distinctions.

---

## 39. Physical Geometry Accuracy

Prepared physical geometry is authoritative.

Physical preview MUST faithfully represent prepared:

- printable width;
- x/y coordinates;
- dimensions;
- line positions;
- line wrapping;
- alignment;
- operation order;
- blank feed space;
- segment geometry;
- overlap;
- cut positions;
- registration-mark positions.

Conversion from physical units or dots to screen pixels MAY involve display rounding.

Such screen-space rounding MUST NOT alter the underlying model geometry.

At sufficiently high zoom, the implementation SHOULD preserve the ability to distinguish adjacent printer-dot positions where practical.

---

## 40. Raster Data Accuracy

For prepared raster operations, the underlying preview data MUST be exact.

This includes:

- raster text;
- raster images;
- raster QR codes;
- other future prepared monochrome raster operations.

If the prepared raster contains a black dot at coordinate `(x, y)`, the preview source representation MUST contain the same black dot at that prepared coordinate.

Likewise for white dots.

The underlying preview raster MUST NOT differ because the preview implementation re-ran an image algorithm.

UI scaling interpolation is a display concern and does not change this contract.

---

## 41. Native Text Accuracy

For native printer text, RastrIO guarantees the prepared physical contract rather than unconditionally guaranteeing exact printer-ROM glyph pixels.

Authoritative aspects include:

- wrapping;
- line count;
- placement;
- alignment;
- run/cell geometry;
- selected native font identity;
- style intent;
- width/height scale;
- code-page decision;
- operation ordering.

Glyph appearance is exact only when an exact validated model of that printer font exists.

Otherwise:

```text
native glyph shape = representative
native geometry    = authoritative
```

The UI SHOULD avoid language such as:

> Exact font preview

when the printer ROM font is only approximated.

---

## 42. Raster Text Accuracy

Raster text uses the same contract as raster images:

```text
raster pixels = exact prepared pixels
geometry      = exact prepared geometry
```

Because shaping and rasterization have already occurred before `PreparedPrint` is finalized, preview does not need a font approximation for raster text.

---

## 43. Native QR Accuracy

For native QR:

```text
position and intended dimensions = authoritative
prepared strategy                 = authoritative
printer-generated module pixels   = exact only if reproducible
```

RastrIO MUST NOT overstate module-level visual fidelity when the printer firmware itself generates the symbol and its exact implementation is unavailable.

---

## 44. Segmentation Accuracy

Segmentation is authoritative.

Preview MUST exactly reflect:

- segment count;
- segment ordering;
- crop/mapping region;
- overlap extent;
- registration marks;
- printed segment numbers;
- associated cuts and guides.

A preview implementation that computes different segment boundaries is invalid even if the resulting view appears visually plausible.

---

## 45. Cutter Accuracy

Preview can represent cutter semantics exactly at the prepared-plan level:

```text
cut operation exists
cut type
cut position
operation order
```

It cannot guarantee the mechanical precision of a physical cutter.

For example, real hardware may exhibit small mechanical variation between commanded and physical cut position.

The preview therefore guarantees command-plan semantics, not zero mechanical tolerance.

---

## 46. Printer Mechanics

Physical preview does not guarantee simulation of undocumented real-world printer behavior such as:

- paper skew;
- roller slip;
- thermal head variation;
- heat density variation;
- paper expansion;
- cutter mechanical tolerance;
- firmware timing artifacts.

Known behavior that materially affects preparation SHOULD be modeled in validated printer capability/profile data and resolved before `PreparedPrint` is created.

Preview MUST NOT secretly compensate for such behavior on its own.

---

## 47. Accuracy Metadata

The portable preview model SHOULD make material accuracy distinctions inspectable where useful.

An implementation MAY represent concepts equivalent to:

```text
EXACT_RASTER
EXACT_GEOMETRY_REPRESENTATIVE_GLYPHS
EXACT_OPERATION_SEMANTICS
REPRESENTATIVE_VISUAL
```

The exact enum names are not normative.

Such metadata MUST describe preview fidelity only.

It MUST NOT alter preparation.

---

# Part VIII — Diagnostics and Failure Representation

## 48. Diagnostics

`PrintPreview` MUST expose diagnostics relevant to the prepared output.

These SHOULD originate primarily from:

```text
PreparedPrint.diagnostics
```

and MAY be supplemented by preview-specific diagnostics when preview itself cannot represent an otherwise prepared construct.

Preview diagnostics MUST be structured.

Preview-specific diagnostics use the reserved family:

```text
PRVxxx
```

A diagnostic that originates from preparation, layout, text, rasterization, or another upstream subsystem retains its upstream diagnostic code when merely surfaced by preview. `PRVxxx` is for failures or warnings owned by preview itself, such as an unreadable prepared artifact, unsupported preview primitive, or preview-tile generation failure.

They SHOULD retain:

- diagnostic code;
- severity;
- message;
- relevant segment or operation identity where available;
- source location where appropriate and safe.

Printable content SHOULD NOT be unnecessarily embedded in diagnostic text.

---

## 49. Warning Diagnostics

Warnings MAY be displayed inline or adjacent to the affected preview region.

Examples include:

- native font glyphs represented approximately;
- Unicode text raster fallback;
- QR raster fallback;
- document segmented into multiple strips;
- content near a profile-declared physical limit.

A warning MUST NOT cause preview to change the prepared operation.

---

## 50. Fatal Diagnostics

A fatal diagnostic indicates that an affected operation or preview cannot safely proceed.

The UI MUST NOT present a knowingly incomplete physical rendering as if it were an authoritative successful preview.

Depending on scope, a fatal error MAY invalidate:

- one operation;
- one segment;
- the entire physical preview.

The visible failure state MUST make the affected extent clear.

---

## 51. Unsupported or Inconsistent Prepared Operations

`core-preview` MUST NOT silently drop an operation it cannot represent.

For an unsupported or internally inconsistent prepared operation, it MUST produce structured failure behavior.

Where geometry is known, the preview MAY display a diagnostic placeholder occupying the prepared bounds.

For example:

```text
┌──────────────────────────┐
│ Preview unavailable      │
│ PRVxxx                   │
└──────────────────────────┘
```

Such a placeholder is a preview-only representation.

It is not printable content.

If safely representing the remaining job would imply misleading physical output, preview SHOULD fail the affected segment or entire preview rather than silently omit the operation.

---

# Part IX — Rendering Architecture

## 52. Core Output, UI Rendering

The required architecture is:

```text
PreparedPrint
     ↓
core-preview
     ↓
portable PrintPreview / preview tiles
     ↓
Shared Compose UI
     ↓
screen
```

Not:

```text
PreparedPrint
     ↓
Compose
     ↓
recalculate printer layout
```

and not:

```text
ThermalDocument
     ↓
Compose text/layout
     ↓
physical preview
```

Compose renders the preview representation.

It does not reconstruct it.

---

## 53. Preview Scene Representation

A small preview MAY be represented as a complete portable scene.

A scene may conceptually contain:

```text
canvas metadata
segments
bounds
print primitives
annotation primitives
diagnostics
```

However, a complete scene for an arbitrarily long thermal document MUST NOT be required.

The implementation MUST support incremental representation.

---

# Part X — Long Documents and Bounded Memory

## 54. Long-Document Requirement

Thermal output may be extremely long.

Preview architecture MUST NOT require one giant bitmap such as:

```text
576 × 100000 ARGB
```

simply to display a long receipt.

Likewise it MUST NOT require eagerly materializing an unbounded number of high-cost preview objects when only a small viewport is visible.

---

## 55. Tiling

Long previews SHOULD be divided into bounded tiles.

For example:

```text
Segment
│
├── Tile 0
├── Tile 1
├── Tile 2
├── Tile 3
└── ...
```

A tile represents a bounded physical region.

Its dimensions MUST obey preview resource limits defined by `docs/RESOURCE_LIMITS.md`.

Tile boundaries are preview implementation boundaries only.

They MUST NOT alter physical output semantics.

A printed primitive crossing a tile boundary MUST render continuously when adjacent tiles are reconstructed.

---

## 56. Tile Clipping

Preview tile generation MAY clip already-prepared operations to the tile bounds.

Clipping MUST be purely representational.

For example, if a raster image spans:

```text
y = 500 .. 1500
```

and preview tiles cover:

```text
Tile A: 0 .. 1023
Tile B: 1024 .. 2047
```

the preview renderer MAY obtain the appropriate portions for each tile.

It MUST NOT resize or regenerate the image independently per tile.

---

## 57. Lazy Rendering

Preview tiles SHOULD be generated only when needed.

Shared UI SHOULD request or compose:

- visible tiles;
- a bounded look-ahead region where useful;
- a small bounded cache.

Scrolling through a long preview SHOULD NOT require rendering every tile beforehand.

---

## 58. Segment-Level Laziness

For segmented documents, preview SHOULD support laziness at both levels:

```text
PrintPreview
    ↓
visible PrintSegment
    ↓
visible tiles within segment
```

A job containing many long segments MUST NOT require all segments to be rasterized for display at once.

---

## 59. Bounded-Memory Contract

Preview-specific memory use MUST remain bounded by explicit resource policy rather than total document rendered height.

It is acceptable for the underlying `PreparedPrint` itself to contain metadata proportional to the document and references to bounded immutable prepared artifacts.

Preview MUST NOT require all referenced prepared artifacts to be decoded/materialized concurrently.

It is not acceptable for preview to introduce an additional requirement equivalent to:

```text
ARGB memory ∝ entire physical document height
```

merely to make the document scrollable.

A typical bounded implementation MAY hold:

```text
current visible tiles
+
small prefetch window
+
bounded cache
```

Current provisional preview budgets are defined in `docs/RESOURCE_LIMITS.md` and summarized in Section 110 of this specification.

They are application resource-policy values, not portable document/profile compatibility semantics.

---

## 60. Raster Band Integration

Prepared raster output may be organized as in-memory raster bands or referenced through bounded immutable prepared artifacts.

Preview SHOULD use those finalized data directly through the read-only prepared-artifact contract.

Prepared raster bands and preview tiles do not have to be identical.

A preview tile MAY span:

- part of one raster band;
- one raster band;
- several raster bands.

Preview MUST nevertheless use the exact finalized prepared raster data.

If the data is spool-backed, preview MAY issue bounded reads for only the intersecting rows/chunks.

It MUST NOT invoke a second dither, shape, raster, graphic-generation, or packing pipeline simply because preview tile dimensions differ from printer raster-band dimensions.

---

## 61. Raster Caching

Preview MAY cache derived screen-oriented raster tiles.

Caching is permitted only as an optimization.

A cache MUST NOT become an authority for print state.

A raster-preview cache key SHOULD contain all values required to prevent reuse across incompatible plans, such as conceptually:

```text
PreparedPrint identity/content key
segment identity
tile bounds
preview scale or rasterization scale
preview renderer version where relevant
```

Exact cache-key structure is implementation-defined.

---

## 62. Cache Bounds

Raster caches MUST be bounded.

An implementation MUST have a defined eviction mechanism, such as:

- least-recently-used;
- size-bounded;
- entry-count-bounded;
- equivalent deterministic resource policy.

Unlimited cache growth is prohibited.

The current provisional rendered-tile cache ceiling is **32 MiB** from `docs/RESOURCE_LIMITS.md`.

Platforms MAY impose a stricter cache ceiling. The cache MUST remain bounded even when entry count varies with tile size or display format.

---

## 63. Cache Correctness

Removing a cache entry MUST NOT affect prepared-output correctness.

The corresponding tile MUST be reproducible by rereading the immutable `PreparedPrint` and its still-valid finalized prepared artifacts.

Cache eviction MUST NOT trigger regeneration of output-affecting content.

If a required prepared artifact is no longer valid/readable, tile reconstruction MUST fail with a structured diagnostic rather than silently reproducing the raster from source content.

Stale cache content from one `PreparedPrint` MUST NOT be displayed as the preview of another.

---

# Part XI — Adjustment and Invalidation

## 64. Print Adjustment Loop

The required adjustment loop is:

```text
PrintOptions change
      ↓
PreparationEngine
      ↓
new LogicalDocument when required
      ↓
new PreparedPrint
      ↓
new PrintPreview
```

Any option that can alter physical output invalidates the existing prepared physical preview.

Examples include:

```text
Atkinson
→ Floyd–Steinberg
```

```text
3 mm overlap
→ 5 mm overlap
```

```text
Auto text strategy
→ Prefer raster
```

---

## 65. No Preview Mutation to Simulate New Output

The UI MUST NOT simulate a changed physical setting by mutating the current `PrintPreview`.

Examples of prohibited behavior include:

```text
user changes overlap
→ UI moves segment images itself
```

or:

```text
user selects raster text
→ UI swaps native glyphs for a local font rendering
```

or:

```text
user changes image dithering
→ UI applies a visual image filter to the old preview
```

The correct behavior is always:

```text
new options
→ new preparation
→ new PreparedPrint
→ new preview
```

---

## 66. Output-Affecting Invalidation

Changes that may require new preparation include, where supported:

- text strategy;
- native/raster preference;
- dithering;
- brightness;
- contrast;
- gamma;
- density when it changes prepared output;
- image scaling strategy;
- landscape overlap;
- registration marks;
- printed segment numbering;
- manual cut guides;
- QR strategy preference;
- selected printer/profile;
- profile override affecting physical output;
- layout-affecting target dimensions.

Presentation MUST consider the old physical preview stale while a replacement preparation is underway.

---

## 67. Non-Output Preview State

Changes that affect only how the user views an already-prepared plan do not require preparation.

Examples include:

- zoom;
- pan;
- scroll position;
- inspector selection;
- showing or hiding non-printing overlays;
- preview background styling;
- expanded/collapsed diagnostic panels.

These values MUST NOT modify `PreparedPrint`.

---

## 68. Transport-Only Changes

Transport/session configuration that does not change physical output does not invalidate physical preview.

Examples include:

- Bluetooth write chunk size;
- USB write chunk size;
- write timeout;
- connection pacing.

Changing transport MUST NOT require preview re-layout or re-dithering.

---

## 69. Stale Preview Handling

While a new preparation is being produced, the application MAY temporarily keep an old preview visible for UX continuity.

If it does, the UI MUST NOT present the old preview as matching the new options.

It SHOULD clearly represent the state as stale, updating, or otherwise non-current.

Once the new `PreparedPrint` is accepted, the corresponding new preview becomes authoritative.

---

# Part XII — Compose Rendering Rules

## 70. Compose Is Not an Authoritative Measurement System

Compose MAY draw text.

Compose MUST NOT measure physical text for printer layout.

For native text, the UI may receive:

```text
text
prepared bounds
prepared baseline
prepared cell/run geometry
representative preview style
```

It may then draw a representative glyph image within those bounds.

It MUST NOT use the width returned by Compose to decide:

- whether the text fits;
- where the next line starts;
- whether wrapping occurs;
- where another operation begins.

---

## 71. Viewport Scaling

A physical preview may be scaled to available screen width.

For example:

```text
prepared paper width: 384 dots
screen width:         768 px
zoom:                 2.0
```

This is a display transform.

The underlying physical width remains 384 prepared dots.

Responsive UI MUST NOT alter physical layout.

---

## 72. Device Density

Android density, Desktop scale factor, browser device-pixel ratio, or equivalent UI metrics MUST NOT affect prepared geometry.

The same `PreparedPrint` rendered on two screens of different density MUST describe the same physical job.

Only the viewport transform may differ.

---

## 73. Annotation Presentation

The UI MAY choose appropriate visual styles for non-printing annotations such as:

- automatic cuts;
- printable-area borders;
- overlap shading;
- segment labels;
- diagnostics.

Their semantics MUST remain identifiable.

In particular, an automatic-cut annotation MUST NOT be visually indistinguishable from a printed dashed separator if that could mislead the user into believing the dashed line will print.

---

# Part XIII — Determinism

## 74. Deterministic Preview

For the same immutable input and same preview-rendering request, Core preview generation SHOULD be deterministic.

Conceptually:

```text
preview(preparedPrint, tileRequest)
```

must produce equivalent output every time.

The same applies to logical preview:

```text
preview(logicalDocument, tileRequest)
```

No output should depend on:

- wall-clock time;
- random state;
- platform UI font measurement;
- screen density;
- current locale unless explicitly part of input;
- mutable global printer state.

---

## 75. Representative Native Fonts and Determinism

Where native printer glyphs are represented through a substitute preview font, deterministic tests MUST use the pinned bundled Noto Sans resource/face selected by the v1 representative-font policy and control the renderer assumptions sufficiently for the tested contract.

The bundled font's natural advance MUST NOT become physical geometry; prepared native-text geometry remains authoritative.

Golden tests SHOULD focus separately on:

1. authoritative geometry;
2. representative glyph rendering where stable enough to test.

A platform-specific variation in anti-aliasing MUST NOT be allowed to disguise a geometry regression.

---

# Part XIV — Logical and Physical Preview Relationship

## 76. Distinct Preview Models

Logical preview and physical preview MUST remain conceptually separate.

They may share portable drawing primitives and rendering infrastructure, but they MUST NOT be collapsed into a model that obscures their different authority.

Logical preview is based on:

```text
LogicalDocument
```

Physical preview is based on:

```text
PreparedPrint
```

A user may legitimately see different arrangements because physical preparation can include:

- printer-specific text strategy;
- target physical width;
- raster fallback;
- physical segmentation;
- overlap;
- marks;
- cuts.

---

## 77. No Reverse Authority

`PrintPreview` MUST NOT become an input into printer encoding.

The canonical relation is:

```text
PreparedPrint
    ├──→ PrintPreview
    └──→ protocol encoder

v1 protocol encoder = core-escpos
```

not:

```text
PreparedPrint
    ↓
PrintPreview
    ↓
protocol encoder
```

Preview is derived output.

The physical plan remains the authority.

---

# Part XV — Testing Contract

## 78. General Testing Requirement

Preview is architecture-critical and requires automated contract and golden testing.

Every practical preview bug fix SHOULD include a regression test.

Golden fixtures SHOULD use stable, reviewable portable representations wherever possible.

Tests MUST distinguish:

- logical preview behavior;
- physical preview behavior;
- raster pixel fidelity;
- geometry;
- annotation semantics;
- bounded-memory behavior.

---

## 79. Same `PreparedPrint` Determinism Test

Given the same immutable `PreparedPrint`:

```text
preview(P)
preview(P)
```

the resulting portable preview representation MUST be equivalent.

Where tile generation is used:

```text
renderTile(P, request)
renderTile(P, request)
```

MUST produce equivalent tile content.

---

## 80. Physical Width Tests

Tests MUST verify that physical preview width is taken from `PreparedPrint`.

Fixtures SHOULD include at least:

- narrow synthetic profile;
- wider synthetic profile;
- non-byte-aligned raster width where applicable.

Changing the target width during preparation MUST produce a different `PreparedPrint` and corresponding preview rather than causing preview itself to recalculate layout.

---

## 81. Text Geometry Tests

Tests MUST verify:

- prepared line breaks remain unchanged;
- native text bounds match prepared geometry;
- baselines/line placement match prepared geometry;
- alignment matches prepared geometry;
- width/height scaling is represented correctly;
- raster fallback does not cause preview-only reflow.

A useful regression test SHOULD deliberately provide a representative UI font with materially different natural metrics.

The physical preview must still use the prepared text positions rather than the UI font's natural wrapping.

---

## 82. No Independently Recalculated Wrapping Test

A contract test MUST prove that preview performs no independent physical wrapping.

For example, construct a `PreparedPrint` containing explicitly prepared lines:

```text
Line 1: "ABCDEFGHIJ"
Line 2: "KLMNOP"
```

even if a local UI font would naturally wrap them differently.

The preview MUST preserve exactly the prepared line division.

The test SHOULD fail if Compose, Skia, or another presentation renderer is used as a wrapping authority.

---

## 83. Raster Fidelity Tests

For every raster operation type, tests MUST verify that preview consumes the exact prepared raster data.

Required cases include:

- all black;
- all white;
- checkerboard;
- odd width;
- width not divisible by 8;
- full printer width;
- long raster;
- raster spanning several preview tiles;
- raster spanning several prepared bands.

Reassembling preview tiles for a raster region MUST reproduce the prepared raster at corresponding physical coordinates.

Preview MUST NOT re-dither.

---

## 84. Raster Text Tests

Tests for raster text MUST verify:

```text
PreparedPrint raster text pixels
==
physical preview source pixels
```

Preview tests MUST be able to run without invoking text shaping.

A test implementation SHOULD make shaping unavailable during preview to demonstrate that `PreparedPrint` is complete.

---

## 85. Native Text Accuracy Tests

Tests MUST separately verify:

- exact prepared geometry;
- representative glyph behavior.

Absence of exact printer-ROM glyph data MUST NOT cause geometry tests to become approximate.

Where an exact native-font model exists, additional glyph golden tests MAY be added.

---

## 86. Manual Cut Guide Tests

Tests MUST verify that:

- a prepared manual guide appears as printed content;
- its physical geometry is preserved;
- removing the prepared operation removes it from preview;
- the guide is not merely a UI annotation.

---

## 87. Automatic Cut Marker Tests

Tests MUST verify that:

- an `AutomaticCut` creates a non-printing preview annotation;
- the annotation occurs at the prepared location;
- full/partial semantics are preserved where available;
- the annotation does not appear in raster content;
- a visual auto-cut marker cannot accidentally become encoded printable data.

---

## 88. Feed Tests

Tests MUST verify that feed operations create the expected prepared blank physical extent.

A feed MUST not be calculated from UI font line height.

Adjacent operations following a feed MUST appear at their prepared physical y-coordinate.

---

## 89. Separator Tests

Tests MUST verify that separator geometry comes from the prepared operation.

The separator MUST not stretch or shrink when the UI viewport changes.

---

## 90. Segmentation Tests

Physical preview tests MUST cover:

- one segment;
- two segments;
- three or more segments;
- segment ordering;
- exact segment width;
- logical-to-segment coordinate mapping;
- continuous coverage;
- no lost content;
- no duplicated content outside intentional overlap.

Segment boundaries MUST exactly match `PreparedPrint`.

---

## 91. Overlap Tests

Tests MUST cover multiple overlap values.

For example:

```text
3 mm
5 mm
```

The overlap shown in each corresponding preview MUST match the preparation result.

Changing overlap MUST require a new `PreparedPrint`.

A UI-only mutation of overlap MUST not be accepted as valid behavior.

---

## 92. Registration-Mark Tests

Tests MUST verify that prepared registration marks:

- occur on the intended segment;
- use exact prepared coordinates;
- appear as printed content;
- remain continuous through tiling where applicable.

Preview MUST contain no printed registration marks when the prepared plan contains none.

---

## 93. Join-Guide Tests

Where join guides are implemented, tests MUST establish whether each guide is:

```text
printed
```

or:

```text
annotation-only
```

and verify preview and encoder behavior accordingly.

The preview implementation MUST NOT infer the category.

---

## 94. Segment Numbering Tests

Tests MUST distinguish:

1. printed segment numbers in `PreparedPrint`;
2. UI-only preview labels.

Printed numbers MUST match prepared physical geometry.

UI-only labels MUST not appear in output data.

---

## 95. QR Tests

Raster QR tests MUST verify exact raster fidelity.

Native QR tests MUST verify:

- prepared dimensions;
- position;
- native strategy classification;
- error-correction semantics;
- representative-versus-exact visual contract.

Preview MUST NOT silently change native QR to raster physical output.

---

## 96. Diagnostics Tests

Tests MUST verify that:

- prepared warnings are exposed;
- fatal diagnostics are not hidden;
- unsupported preview operations produce structured diagnostics;
- significant operations are not silently omitted;
- diagnostic handling does not mutate `PreparedPrint`.

---

## 96.1 Prepared-Artifact Tests

Tests MUST cover prepared raster content backed by the portable immutable artifact contract.

Verify that:

- preview can render a tile spanning one artifact chunk/band;
- preview can render a tile spanning several artifact chunks/bands;
- only bounded required ranges are read where the adapter supports ranged access;
- preview never invokes text shaping, image processing, QR rasterization, dithering, or checklist-marker generation to reconstruct finalized content;
- preview and the protocol encoder observe identical prepared raster hashes/content;
- an unreadable/invalidated prepared artifact produces a structured `PRVxxx` diagnostic;
- cache eviction followed by tile reconstruction rereads finalized artifact content rather than regenerating it.

## 96.2 Checklist Preview Tests

Tests MUST verify:

- logical preview preserves checklist state and logical marker geometry;
- physical preview uses finalized prepared checklist marker/raster content;
- no emoji, icon-font glyph, or UI checkbox widget becomes the physical marker;
- checked and unchecked physical marker output matches the prepared raster exactly.

## 96.3 Preview Resource-Policy Tests

Tests SHOULD exercise the current provisional baselines:

```text
2 megapixel tile-area ceiling
1,024 pixel preferred tile height
32 MiB rendered-tile cache ceiling
```

Verify that stricter platform policy can reduce these values without changing prepared physical geometry.

---

## 97. Immutability Tests

Tests MUST prove that preview generation does not alter `PreparedPrint`.

This SHOULD include:

```text
P0 = PreparedPrint
preview(P0)
P1 = same object/value after preview
```

and comparison of:

- segments;
- operations;
- raster data;
- diagnostics;
- cut operations;
- text geometry.

Where immutable persistent structures are used, structural equality is sufficient.

---

## 98. Preview/Encoder Independence Test

Tests SHOULD verify that:

```text
encode(P)
```

and:

```text
preview(P)
encode(P)
```

produce identical encoded results.

This catches preview implementations that accidentally mutate prepared state.

---

## 99. PrintOptions Invalidation Tests

For each output-affecting `PrintOptions` value, tests SHOULD establish:

```text
Options A
→ PreparedPrint A
→ Preview A
```

and:

```text
Options B
→ PreparedPrint B
→ Preview B
```

Presentation tests MUST confirm that Preview A is not manually modified into Preview B.

---

## 100. Transport Independence Test

Given the same `PreparedPrint`, changing only transport/session settings MUST NOT require a different physical preview.

For example:

```text
Bluetooth write chunk = 256
```

versus:

```text
Bluetooth write chunk = 1024
```

must not alter physical preview geometry.

---

## 101. Long-Document Bounded-Memory Tests

Tests MUST cover an intentionally very long document.

The implementation MUST demonstrate that displaying a small viewport does not allocate one print-job-sized ARGB bitmap.

Tests SHOULD verify:

- lazy tile generation;
- bounded tile cache;
- eviction;
- continuity across tile boundaries;
- scrolling to distant regions without materializing every intermediate tile;
- memory release after cache eviction where testable;
- segment-level laziness for segmented long documents.

The exact resource thresholds belong in `docs/RESOURCE_LIMITS.md`.

---

## 102. Tile Reconstruction Tests

Given several preview tiles covering a contiguous region, reconstruction MUST preserve the same physical geometry as rendering the equivalent bounded region directly.

Tests SHOULD include primitives crossing tile boundaries:

- text geometry;
- raster images;
- separators;
- registration marks;
- manual cut guides.

No seam may be introduced into the authoritative model merely because of tile boundaries.

---

## 103. Cross-Platform Contract Tests

Portable preview-model tests SHOULD run in common Core tests where possible.

Android, Desktop, and Web rendering tests MAY differ in presentation details but MUST consume equivalent Core preview geometry.

No target may introduce a platform-specific physical layout implementation.

---

# Part XVI — Recommended Conceptual API Shape

## 104. Logical Preview API

A conceptual API may resemble:

```kotlin
interface LogicalPreviewEngine {
    fun createPreview(
        document: LogicalDocument,
    ): LogicalPreview
}
```

For large documents, an indexed/lazy form may be preferable:

```kotlin
interface LogicalPreviewEngine {
    fun describe(
        document: LogicalDocument,
    ): LogicalPreviewDescriptor

    fun renderTile(
        document: LogicalDocument,
        request: LogicalPreviewTileRequest,
    ): PreviewTile
}
```

These signatures are illustrative rather than normative.

The important requirement is that tile generation consumes existing logical geometry rather than performing layout.

---

## 105. Physical Preview API

A conceptual physical API may resemble:

```kotlin
interface PhysicalPreviewEngine {
    fun describe(
        preparedPrint: PreparedPrint,
    ): PrintPreviewDescriptor

    fun renderTile(
        preparedPrint: PreparedPrint,
        request: PrintPreviewTileRequest,
    ): PreviewTile
}
```

A preview implementation MAY instead retain an immutable prepared snapshot in a preview session object.

Regardless of API shape:

- `PreparedPrint` remains authoritative;
- preview does not modify it;
- preview tile generation does not consult `ThermalDocument` to reconstruct physical layout.

---

## 106. Preview Descriptor

A physical preview descriptor may conceptually contain:

```kotlin
data class PrintPreviewDescriptor(
    val target: PreviewTarget,
    val segments: List<PreviewSegmentDescriptor>,
    val diagnostics: List<Diagnostic>,
)
```

A segment descriptor may contain concepts such as:

```kotlin
data class PreviewSegmentDescriptor(
    val id: SegmentId,
    val physicalBounds: PhysicalRect,
    val printableBounds: PhysicalRect,
    val tileIndex: PreviewTileIndex,
    val annotations: List<PreviewAnnotationDescriptor>,
)
```

Exact types remain implementation-defined.

A descriptor SHOULD contain enough inexpensive metadata for UI virtualization without requiring full-raster materialization.

---

## 107. Preview Tiles

A portable preview tile may contain:

```text
tile bounds
printed primitives
annotation primitives
diagnostic references
optional raster payload/reference
```

It MUST be possible to determine which physical coordinates a tile represents.

The UI MUST NOT infer this from bitmap dimensions alone.

---

# Part XVII — Security, Privacy, and Resource Considerations

## 108. Printable Content Privacy

Preview may necessarily display private printable content to the local user.

However, preview systems MUST NOT log printable content by default.

This includes:

- text bodies;
- Markdown;
- QR payloads;
- images;
- raster text;
- document contents.

Diagnostics and logs SHOULD prefer:

- diagnostic codes;
- operation type;
- dimensions;
- segment number;
- raster size;
- tile size;
- profile identifier where appropriate.

---

## 109. Cache Privacy

Preview raster caches may contain representations of private document content.

Caches MUST follow the application's storage and privacy policy.

An implementation SHOULD prefer in-memory caching unless persistent caching has a clear requirement and documented lifecycle.

Persistent preview caches, if introduced, require explicit security/privacy review.

---

## 110. Resource Limits

`docs/RESOURCE_LIMITS.md` owns the authoritative resource policy.

The current provisional preview baselines are:

```text
preview tile area            = 2 megapixels maximum
preferred preview tile height = 1,024 pixels
rendered-tile cache          = 32 MiB maximum
```

These values are implementation safety/tuning policy, not `.td`, `.tcfg`, or `PreparedPrint` compatibility semantics.

The exact number of concurrently materialized tiles need not be a permanent constant. It MUST be bounded by the active tile-area, cache-memory, viewport/prefetch, and platform-memory policies.

A platform MAY use stricter values than these baselines.

Preview MUST reject or degrade safely when those limits would otherwise be exceeded.

Safe degradation MAY include reducing prefetch, evicting derived tiles, rendering fewer tiles concurrently, or lowering screen-display raster scale where that does not change authoritative prepared geometry.

Resource-limit handling MUST NOT silently alter prepared physical output, regenerate a different raster, omit printable operations, or mutate `PreparedPrint`.

---

# Part XVIII — Required Behavioral Examples

## 111. Native Text Example

Prepared physical plan:

```text
paper width: 384 dots

NativeText
    line 1:
        x = 0
        y = 0
        width = 240
        text = "Hello world"

    line 2:
        x = 0
        y = 24
        width = 180
        text = "Next line"
```

Physical preview MUST place those lines at the prepared coordinates.

If the preview machine's normal UI font would place `"Hello world"` at width 267 px, that value is irrelevant to printer geometry.

The UI renderer may fit representative glyph visuals into the prepared 240-dot run.

It MUST NOT change wrapping.

---

## 112. Raster Example

Prepared operation:

```text
RasterImage
    x = 16
    y = 200
    width = 320 dots
    height = 640 dots
    data = prepared monochrome raster
```

Physical preview MUST display the exact prepared black/white raster beginning at `(16, 200)`.

It MUST NOT retrieve the original JPEG and reproduce the image pipeline.

---

## 113. Automatic Cut Example

Prepared plan:

```text
NativeText
Feed
AutomaticCut(FULL)
NativeText
```

Preview should conceptually appear as:

```text
┌──────────────────────────┐
│ first output             │
│                          │
└──────────────────────────┘
        FULL CUT

┌──────────────────────────┐
│ next output              │
└──────────────────────────┘
```

The `FULL CUT` indication is a non-printing preview annotation.

No literal `"FULL CUT"` text is introduced into `PreparedPrint` or printer output.

---

## 114. Manual Cut Example

Prepared plan:

```text
NativeText
ManualCutGuide
NativeText
```

The manual guide itself is expected to print.

Physical preview MUST therefore show it inside the printable paper representation.

---

## 115. Segmentation Example

A 180 mm logical canvas may be prepared for a narrow printer as:

```text
PreparedPrint
├── Segment 1
├── Segment 2
├── Segment 3
└── Segment 4
```

with intentional overlap.

Physical preview MUST show four strips from those exact prepared segments.

It MUST NOT inspect the 180 mm `LogicalDocument` and independently decide that only three strips are necessary.

---

# Part XIX — Compliance Rules

## 116. A Preview Implementation Is Compliant Only If

A physical preview implementation is compliant with this specification only if all of the following are true:

1. it consumes `PreparedPrint`;
2. it does not independently perform physical layout;
3. it preserves prepared geometry;
4. it preserves prepared segmentation;
5. it uses exact finalized prepared raster/artifact data;
6. it does not re-shape raster text;
7. it does not re-dither images;
8. it does not regenerate missing finalized raster/graphic content;
9. it does not choose native/raster strategy;
10. it does not choose code pages;
11. it represents manual guides as printable content;
12. it represents automatic cuts as non-printing annotations;
13. it exposes significant diagnostics;
14. it does not silently discard unsupported operations;
15. it supports bounded-memory rendering for long documents;
16. it does not depend on Compose for authoritative printer measurement;
17. it does not mutate `PreparedPrint`;
18. output-affecting option changes require new preparation;
19. the same physical plan remains suitable for the active protocol encoder (`core-escpos` for v1).

---

## 117. Prohibited Shortcuts

The following implementation shortcuts are explicitly prohibited:

```text
"Preview only" wrapping logic
```

```text
Using Compose Text width as printer text width
```

```text
Re-running Floyd–Steinberg for preview
```

```text
Recomputing landscape strips in the UI
```

```text
Moving an existing segment when overlap settings change
```

```text
Drawing an automatic cut marker into printable raster data
```

```text
Loading the original image instead of the prepared raster
```

```text
Shaping RasterText again for display
```

```text
Reading PrinterProfile in Compose to calculate paper layout
```

```text
Treating preview as the encoder's intermediate representation
```

All of these violate the one-authoritative-physical-plan architecture.

---

# Part XX — Resolved Preview Baseline and Implementation Freedoms

The architectural and policy questions that previously blocked stable preview behavior are resolved below. Remaining items are deliberately non-normative implementation freedoms unless a future specification revision says otherwise.

### 118.1 Preview primitive hierarchy

The precise sealed-class or interface hierarchy for:

```text
PreviewPrimitive
PreviewAnnotation
PreviewTile
PreviewSegment
```

remains an implementation decision.

It MUST remain platform-neutral and MUST preserve the printed-content versus non-printing-annotation distinction.

### 118.2 Tile dimensions

The current provisional resource policy is:

```text
maximum tile area       = 2 megapixels
preferred tile height   = 1,024 pixels
```

These are tuning/safety values from `docs/RESOURCE_LIMITS.md`, not output-format semantics.

Tile width/height MAY vary to fit the prepared paper/segment width, viewport requirements, platform constraints, and the 2-megapixel ceiling.

### 118.3 Cache budget

The current provisional rendered-tile cache ceiling is:

```text
32 MiB
```

A platform MAY use a stricter value.

Concurrent tile count remains derived from actual tile memory cost, viewport/prefetch policy, and the active cache/memory ceiling rather than being a fixed portable-format constant.

### 118.4 Physical paper width versus printable width

The v1 printer-profile contract requires reliable printable geometry but does not require every profile to contain calibrated total media width and non-printable margin geometry.

Therefore:

- prepared printable width/area is authoritative;
- total paper/media edges are authoritative only when preparation contains validated media geometry;
- otherwise any surrounding paper-edge depiction is illustrative;
- preview MUST NOT invent calibrated non-printable margins.

A future profile schema may add validated media/margin geometry without changing this authority rule.

### 118.5 Representative native-preview font

When exact printer-ROM glyph data is unavailable, the v1 representative preview family is the bundled pinned **Noto Sans** family defined by `docs/TEXT_RENDERING_SPEC.md`.

It is a visual approximation only.

Prepared native-text wrapping, cell/run bounds, advances, alignment, and physical coordinates remain authoritative and MUST NOT be recomputed from Noto Sans metrics.

### 118.6 Native QR visual fidelity

Exact native-printer QR module pixels remain strategy/profile/firmware dependent.

The resolved contract is:

```text
position / intended dimensions / prepared strategy = authoritative
module matrix = exact only when the selected printer strategy is reproducible
```

Otherwise preview may show a representative QR visual while clearly retaining the native strategy classification.

This is not an unresolved architecture issue.

### 118.7 Preview diagnostic namespace

Preview-owned structured diagnostics use:

```text
PRVxxx
```

Upstream diagnostics retain their original subsystem code when surfaced by preview.

### 118.8 Prepared-artifact storage representation

The exact backing mechanism for an immutable prepared artifact remains an implementation/platform choice:

```text
bounded memory
application-controlled spool storage
other deterministic immutable chunk store
```

Preview sees only the portable read-only artifact contract.

It MUST NOT depend on a platform filesystem object, infer file paths, or regenerate missing finalized content.

---

# 119. Final Contract

RastrIO has two different preview responsibilities:

```text
LogicalDocument
      ↓
LogicalPreview
```

for viewing resolved logical layout, and:

```text
PreparedPrint
      ↓
PrintPreview
```

for viewing resolved physical output.

The core architectural contract is:

> `LogicalPreview` displays geometry already established by `LogicalDocument`. `PrintPreview` displays the immutable physical decisions already established by `PreparedPrint`. Neither preview mode is permitted to become a second layout or printer-preparation engine.

For physical output:

```text
                     PreparedPrint
                     /           \
                    /             \
                   ▼               ▼
             PrintPreview    protocol encoder
                                  │
                                  ▼
                             Byte Stream

v1 protocol encoder = core-escpos
```

is the only valid authority flow.

Raster output is previewed from the exact finalized prepared raster/artifact data.

Native printer text preserves exact prepared wrapping and geometry while using representative glyph shapes unless an exact validated printer-font model exists.

Segmentation, overlap, marks, feeds, manual guides, and automatic cuts come from `PreparedPrint`.

Long documents are rendered incrementally with bounded memory. Preview may stream finalized prepared artifacts but never re-run output-affecting preparation.

Changing any output-affecting `PrintOptions` value creates a new preparation result and a new preview.

Shared Compose UI renders this Core output.

It does not reconstruct printer layout.

These requirements are mandatory because RastrIO's physical preview is not merely illustrative UI: it is the user-visible representation of the same immutable physical plan that RastrIO sends to the printer.

## Phase 3C1 Logical Structured-Text Consumer Contract

Logical preview consumers use the finalized absolute geometry and snapshots in
LogicalListBlock/LogicalListItem, marker values, LogicalQuoteBlock,
LogicalTextBlock.kind=CODE, and LogicalSeparator. Ordered markers share the
container's common measured column and are already positioned within it;
preview MUST NOT derive marker width, indentation, hanging indents, wrapping,
checklist stroke points or separator dimensions from semantic document text.
Checklist outline centerlines/stroke widths and check points are explicit;
check strokes use the specified butt caps and bevel joins. Unordered markers
are the supplied solid square bounds, independent of glyph/font availability.

Logical run measurements include finalized position-dependent tab advances.
Preview MUST NOT pass tabs through a platform's default tab expansion. It may
apply the normal logical-to-screen coordinate transform to supplied geometry.
This is a logical consumer contract only; Phase 3C1 implements no preview UI,
physical rendering or printer preparation.


## Phase 3C2 Logical Table and Placeholder Consumer Contract

Logical consumers use `LogicalTableBlock` columns, rows, cells, content bounds and
finalized text lines/runs. Header/body identity is explicit; the logical v1 policy
adds no implicit header style, cell border or gap. Column bounds span table height.
Consumers must not resize columns, wrap cell text, infer row heights or realign content.
TextLayoutPolicyV1's approved equal-column, 0.25-em padding and explicit block-spacing
rules are specified in TEXT_RENDERING_SPEC's Phase 3C2 section.

Logical image placeholders retain asset/sizing/alt-text intent and explicitly unresolved
intrinsic dimensions. Their approved square geometry is a placeholder, not a decoded
image aspect. Consumers may indicate unresolved status but must not resolve URIs or
open assets implicitly. The square outer box is finalized logical geometry. Later
preparation fits aspect-preserving resolved image content inside that box without
changing outer height, neighboring geometry or document flow. Neither preparation nor
preview may silently re-layout the document after decoding. Unused box space remains
part of the document. Intrinsic pixel dimensions do not redefine logical outer geometry.
Logical QR placeholders retain opaque payload, error correction and optional requested
size; consumers use finalized square bounds without encoding modules or interpreting URLs.
Both placeholder models retain authoritative alignment and before/after spacing.

These models belong to the assembled explicit logical canvas in both orientations,
including 180 mm wide landscape documents. Consumers must not pre-segment tables or
re-wrap cell text in hypothetical physical strips. Phase 3C2 implements no preview UI
or physical printer preparation.
