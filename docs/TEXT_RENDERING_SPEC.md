# Rastrio Text Rendering Specification

**Status:** Normative implementation specification  
**Specification revision:** 1.1  
**Target path:** `docs/TEXT_RENDERING_SPEC.md`  
**Applies to:** Rastrio v1 architecture  
**Governing document:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**License context:** Apache-2.0 project

---

## 1. Purpose

This specification defines the portable text contract used by Rastrio for text measurement, shaping, logical layout integration, printer-native text, raster text fallback, physical preparation, and preview consistency.

Its primary goal is to ensure that text geometry is resolved exactly once through the shared Core pipeline and that the same resolved physical plan drives both preview and printing.

The governing architecture is:

```text
ThermalDocument
      +
LayoutConstraints
      ↓
core-layout
      ↓
LogicalDocument
      +
PrinterProfile
      +
PrintOptions
      +
required text/render services
      ↓
core-printer
      ↓
PreparedPrint
     /       \
    ↓         ↓
core-preview  core-escpos
```

The following rules are fundamental:

1. `.td` stores semantic text intent and limited author layout intent. It MUST NOT contain ESC/POS commands, printer code pages, native printer-font commands, physical printer widths in dots, or printer-specific text segmentation.
2. `core-layout` owns logical geometry under explicit `LayoutConstraints`.
3. `core-text` owns portable text measurement, shaping, coverage, font-metric, and raster-text contracts.
4. `core-printer` owns physical strategy selection, including native-versus-raster text, code-page selection, printer-font selection, scaling, and any re-resolution required by fallback.
5. `PreparedPrint` is the immutable authoritative physical plan.
6. `core-preview` MUST display the resolved `PreparedPrint` plan and MUST NOT create an alternate physical text layout.
7. `core-escpos` MUST serialize resolved operations and MUST NOT re-measure, re-wrap, re-shape, select a different code page, or change native/raster strategy.
8. Compose UI MUST NOT independently measure text for authoritative printer geometry.

Any implementation that violates these ownership boundaries is non-conforming even if its output appears correct for a particular printer.

---

## 2. Normative Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative requirements.

Where this document gives conceptual Kotlin types, field names are illustrative unless explicitly stated otherwise. The semantic responsibilities and boundaries are normative.

Where the governing documents delegate a text policy to this specification, this document resolves it explicitly. Platform defaults are never normative merely because a selected backend exposes them.

---

## 3. Scope

This specification covers:

- Unicode-aware text terminology;
- portable text-style and typography metrics;
- text measurement inputs and outputs;
- shaping-service boundaries;
- deterministic layout expectations;
- line breaking and wrapping;
- explicit line breaks;
- long unbroken content;
- whitespace;
- paragraph spacing ownership;
- code blocks;
- list indentation;
- alignment;
- native printer font geometry;
- code-page and native coverage detection;
- grapheme-aware behavior;
- combining marks;
- complex scripts including Indic scripts;
- bidirectional/RTL handling boundaries;
- emoji handling;
- missing-glyph behavior;
- font fallback;
- raster text preparation;
- line-level native/raster fallback;
- physical-preview accuracy;
- portable Core APIs;
- `PreparedPrint` text completeness;
- required automated and golden tests.

---

## 4. Non-Goals

This specification does not define:

- the `.td` archive or JSON schema;
- the `.tcfg` JSON schema;
- a general-purpose typography or desktop-publishing engine;
- arbitrary CSS-like text layout;
- printer transport behavior;
- Bluetooth or USB behavior;
- ESC/POS command bytes;
- a platform UI text-layout system;
- platform UI text APIs as authoritative shaping/layout engines;
- per-glyph mixing of printer-native and raster text in Rastrio v1;
- a universal guarantee that printer ROM glyph shapes can be reproduced pixel-for-pixel in preview;
- platform-specific font APIs as public Core types;
- a guarantee that unrelated platform font installations produce identical raster output.

This specification also does not authorize downstream components to reinterpret text merely because an operating-system or UI text API is available.

---

## 5. Ownership and Architectural Boundaries

### 5.1 `core-document`

`core-document` owns semantic text nodes and author intent.

Examples include:

```text
Text
Strong
Emphasis
Strike
InlineCode
LineBreak
Paragraph
Heading
CodeBlock
Alignment
```

These constructs express meaning or author intent. They do not select printer commands.

A semantic `Strong` node, for example, does not mean "emit ESC/POS bold." The selected physical strategy may use a native bold mode, a different native font, raster text, or a diagnostic depending on the target profile.

### 5.2 `core-text`

`core-text` owns portable text-domain contracts needed by layout and physical preparation, including:

- text metrics;
- font metrics;
- Unicode segmentation abstractions;
- shaping inputs and outputs;
- glyph-coverage queries;
- native printer-font geometry models;
- raster-text request/result models;
- font-resource identity;
- deterministic text measurement behavior;
- portable rendering-service interfaces when an implementation cannot live entirely in common code.

`core-text` MUST NOT depend on Android, Compose, Skia-specific handles, browser canvas objects, or other platform UI/rendering types.

### 5.3 `core-layout`

`core-layout` owns:

- block flow;
- paragraph flow;
- line breaking;
- semantic vertical spacing;
- list indentation;
- line alignment;
- code-block layout;
- table text placement;
- logical text boxes;
- logical portrait and landscape geometry.

`core-layout` consumes explicit `LayoutConstraints` and text metrics supplied through portable `core-text` contracts.

`core-layout` MUST NOT select an ESC/POS code page or emit printer commands.

### 5.4 `core-printer`

`core-printer` owns all printer-specific text decisions, including:

- deriving target-specific `LayoutConstraints`;
- selecting native versus raster text;
- selecting a native printer font;
- selecting supported native styles;
- selecting a code page or other native encoding strategy;
- determining whether Unicode content is reliably printable natively;
- triggering raster fallback;
- resolving fallback-induced layout changes before finalization;
- selecting physical scale;
- converting resolved geometry into printer units;
- producing final `NativeText` and `RasterText` operations.

### 5.5 `core-preview`

`core-preview` consumes already-resolved geometry from `LogicalDocument` or `PreparedPrint`.

For physical preview, it MUST use `PreparedPrint` as the authority.

It MUST NOT use Compose measurement, browser measurement, Android `Paint`, Skia paragraph layout, or another independent text engine to decide physical line breaks or x/y positions.

### 5.6 `core-escpos`

`core-escpos` is a serializer.

It MAY mechanically encode text according to a code page already selected and validated by preparation, but it MUST NOT:

- choose another code page;
- replace unsupported characters;
- alter line breaks;
- alter alignment;
- choose a printer font;
- choose raster fallback;
- reshape text;
- rerasterize text;
- rescale text;
- create new text geometry.

If a prepared operation is inconsistent with its selected encoding or protocol strategy, the encoder MUST fail with a structured diagnostic rather than repairing the operation by making a new physical decision.

### 5.7 Shared Compose UI

Compose is presentation technology, not authoritative print-layout technology.

The UI MAY use Compose text measurement for ordinary UI controls such as labels, buttons, editors, and non-authoritative screen layout.

The UI MUST NOT use Compose text measurement to determine:

- printer wrapping;
- printer line height;
- printer glyph advance;
- physical alignment;
- raster fallback;
- native printer-font geometry.

---

## 6. Terminology

### 6.1 Code point

A **code point** is a Unicode scalar value represented by a numeric Unicode value such as `U+0041`.

A code point is not necessarily a user-perceived character and is not necessarily a printer cell.

Core logic MUST NOT assume one Unicode code point equals one visible glyph or one native-printer character cell.

### 6.2 Grapheme cluster

A **grapheme cluster** is the unit that approximates one user-perceived character for cursoring, breaking, and safe text segmentation.

A grapheme cluster may contain:

- one code point;
- a base character plus one or more combining marks;
- an emoji sequence;
- a zero-width-joiner sequence;
- another multi-code-point sequence.

Rastrio MUST NOT intentionally split a grapheme cluster when performing emergency line wrapping or font fallback unless a future specification explicitly defines a safe exception.

### 6.3 Glyph

A **glyph** is a rendered visual shape selected from a font or printer-native glyph repertoire.

There is not necessarily a one-to-one relationship among code points, grapheme clusters, and glyphs.

A shaping engine may map:

```text
multiple code points → one glyph
one code point       → multiple glyphs
multiple code points → reordered glyph sequence
```

### 6.4 Glyph run

A **glyph run** is a contiguous shaped sequence sharing rendering properties sufficient for deterministic placement, such as font resource, size, direction, and relevant style.

A raster line MAY contain multiple glyph runs because raster font fallback may use multiple fonts.

This does not constitute native/raster mixing. A physical line is still either printer-native text or raster text under the v1 fallback rule.

### 6.5 Shaping

**Shaping** is the transformation from Unicode text plus typography and script context into positioned glyphs or equivalent renderer output.

Shaping includes behavior necessary for scripts and sequences where code-point-by-code-point drawing is incorrect, including combining marks and complex-script forms.

### 6.6 Line breaking

**Line breaking** is the layout decision that divides a paragraph or text-bearing block into physical/logical lines under an available width.

Line breaking is owned by `core-layout`.

`core-text` supplies the measurement and segmentation information required to make that decision.

### 6.7 Font metrics

**Font metrics** are geometry used to position text, including applicable concepts such as:

- ascent;
- descent;
- leading or line gap;
- baseline;
- advance;
- line height;
- nominal cell width and height for fixed-cell native printer fonts.

Metrics are geometry. They are distinct from glyph appearance.

### 6.8 Native printer font

A **native printer font** is a font or character-cell mode implemented by printer firmware and selected through the printer protocol.

Its capability and geometry come from `PrinterProfile` or validated profile overrides.

Native printer fonts MUST NOT be inferred from a UI font with a similar name.

### 6.9 Raster font

A **raster font** is a font resource used by Rastrio's shaping/rasterization path rather than by printer firmware.

Raster fonts may be project-controlled bundled resources or explicitly resolved platform fonts.

### 6.10 Raster text

**Raster text** is text whose final glyph appearance is rendered by Rastrio into printer-ready monochrome raster content before ESC/POS serialization.

The ESC/POS encoder MUST NOT shape or draw the text.

### 6.11 Native text

**Native text** is a physical text operation that preserves text as printer-native character data and uses a selected native printer font, style, scale, and encoding strategy.

### 6.12 Logical line

A **logical line** is a line of text geometry emitted by `core-layout` under its active `LayoutConstraints`.

### 6.13 Physical line

A **physical line** is the resolved printer-specific line represented in `PreparedPrint`.

For v1 text fallback, native/raster strategy is resolved at this line boundary.

---

## 7. Coordinate and Measurement Principles

Rastrio text geometry has two related domains:

1. logical geometry used by `core-layout`;
2. physical printer geometry represented in `PreparedPrint`.

The implementation MUST make conversions explicit.

### 7.1 No hidden UI coordinate system

Core text contracts MUST NOT use screen pixels, Android density-independent pixels, Compose `Dp`, browser CSS pixels, or equivalent UI units as implicit printer geometry.

### 7.2 Logical units

`core-layout` MUST use the project's portable `Length`/dimension model or another explicitly documented portable logical unit.

The exact internal numeric representation MAY evolve, but output-affecting rounding MUST be deterministic.

### 7.3 Physical units

`core-printer` is responsible for converting prepared logical geometry into target physical geometry, including printer dots where required.

Printer DPI and printable width come from `PrinterProfile` or validated overrides.

### 7.4 Rounding

A conforming implementation MUST define one deterministic rounding policy for each logical-to-physical conversion path.

The policy MUST NOT depend on the host platform's UI scaling or locale.

If changing rounding can change physical output, it is part of preparation behavior and requires regression/golden coverage.

---

## 8. Typography Model

### 8.1 `TextStyle`

A portable text style conceptually describes semantic or resolved style inputs such as:

```kotlin
data class TextStyle(
    val weight: TextWeight,
    val emphasis: Boolean,
    val strike: Boolean,
    val underline: Boolean,
    val role: TextRole,
)
```

The exact fields may differ.

`TextStyle` MUST NOT contain Android, Compose, Skia, browser, or ESC/POS types.

Semantic style does not itself determine a printer strategy.

### 8.2 `TypographyMetrics`

`TypographyMetrics` is the portable geometry contract consumed by layout.

Conceptually:

```kotlin
data class TypographyMetrics(
    val ascent: Length,
    val descent: Length,
    val lineGap: Length,
    val lineHeight: Length,
    val baselineOffset: Length,
    val defaultAdvance: Length?,
)
```

Additional fields MAY be present where required.

Normative requirements:

- `lineHeight` MUST be sufficient for deterministic vertical flow.
- Baseline-related values MUST use one documented coordinate convention.
- Metrics MUST be immutable values once used for a layout result.
- Metrics MUST NOT contain a native platform font object.
- Metrics MUST NOT be lazily re-read from a mutable platform font after layout.
- Metrics used for physical preparation MUST correspond to the actual native or raster strategy being prepared.

For a fixed-cell native printer font, a specialized metric model MAY expose:

```text
cell advance
nominal cell width
nominal cell height
baseline/line-box information where known
supported width scales
supported height scales
style effects on geometry
```

For raster fonts, metrics come from the selected shaping/rasterization backend and resolved font resource.

### 8.3 `TypographyContext`

`TypographyContext` is the resolved collection of text styles and metric providers used by a layout pass.

Conceptually:

```kotlin
data class TypographyContext(
    val body: ResolvedTypography,
    val headingStyles: Map<HeadingLevel, ResolvedTypography>,
    val code: ResolvedTypography,
    val listMarker: ResolvedTypography,
)
```

The exact representation may differ.

A `TypographyContext` MUST contain enough stable information for `core-layout` to produce deterministic line and block geometry for the current pass.

It MUST NOT hide a platform UI text-layout object.

### 8.4 Geometry identity

A layout result SHOULD retain an identifier or fingerprint for the typography configuration used to generate it when practical.

The goal is to detect accidental reuse of a `LogicalDocument` with incompatible metrics.

---

## 9. `LayoutConstraints`

`LayoutConstraints` are runtime layout inputs, not `.td` document state.

Conceptually:

```kotlin
data class LayoutConstraints(
    val canvasWidth: Length,
    val orientation: Orientation,
    val typography: TypographyContext,
)
```

The actual model MAY include additional explicit fields needed for deterministic layout.

### 9.1 Required properties

`LayoutConstraints` MUST make output-affecting layout inputs explicit.

At minimum, text layout requires:

- logical canvas width;
- orientation;
- resolved typography metrics/context.

If paragraph spacing, list indentation, code indentation, or another layout constant affects geometry, the value MUST come from an explicit or centrally versioned layout policy used by Core. It MUST NOT be taken from a platform UI default.

### 9.2 Physical preparation

For printer-specific preparation, `core-printer` resolves `LayoutConstraints` from:

```text
ThermalDocument
PrinterProfile / EffectivePrinterProfile
PrintOptions
required render services
```

The resulting layout may therefore be target-constrained while remaining protocol-independent.

### 9.3 Logical preview

A logical preview without a selected printer MUST use an explicit logical preview preset or document-defined width intent.

It MUST NOT silently borrow a default thermal-printer width.

---

## 10. Portable Text Service Boundary

Some shaping and rasterization capabilities may require a platform-specific or library-specific backend.

Core MAY depend on a narrow injected service with portable inputs and outputs.

A conceptual separation is:

```kotlin
interface TextShaper {
    fun shape(request: ShapeRequest): ShapeResult
}

interface TextRasterizer {
    fun rasterize(request: RasterTextRequest): RasterTextResult
}
```

An implementation MAY combine these interfaces internally, but their public Core contract MUST remain platform-neutral.

### 10.1 Forbidden Core boundary types

Portable Core APIs MUST NOT expose:

```text
android.graphics.Paint
android.graphics.Bitmap
android.graphics.Typeface
android.text.*
Compose TextLayoutResult
Compose Paragraph
Compose FontFamily handles
Skia Paragraph handles
Skia Typeface handles
browser CanvasRenderingContext2D
DOM text measurement objects
platform-native glyph buffer handles
```

### 10.2 Portable input

A shaping or raster request may contain portable data such as:

```text
UTF-8/Unicode text
text range
resolved style
font resource identifier
font size or logical scale
requested direction when supported
language/script metadata when supported
maximum raster bounds
deterministic raster settings
```

### 10.3 Portable output

A shaping result may contain portable values such as:

```text
glyph identifiers meaningful only to the selected font resource
glyph advances
glyph offsets
run boundaries
cluster mapping
font resource identifiers
font metrics
total advance
bounding geometry
diagnostics
```

A raster result may contain:

```text
width
height
baseline metadata where required
row stride
monochrome pixels or stable portable coverage data
font/resource fingerprint
diagnostics
```

A Core result MUST NOT depend on the continued lifetime of a platform graphics object.

### 10.4 Reference shaping and raster backend

The v1 reference raster-text backend is:

```text
shaping:       HarfBuzz
font loading /
glyph raster:  FreeType
```

The exact dependency versions and build configuration MUST be pinned by the repository/toolchain and recorded in deterministic golden-fixture metadata.

`core-text` exposes only portable contracts. HarfBuzz handles, FreeType handles, native pointers, and platform graphics objects MUST NOT cross those contracts.

Android and Desktop/JVM stable-release raster preparation SHOULD use the same pinned reference backend and bundled font bytes where practical.

Web MAY initially use a different adapter while the Web target remains later-phase work. Such an adapter MUST satisfy the same semantic contracts, but it MUST NOT be assumed pixel-identical until the repository proves that with controlled tests.

Changing the shaping/raster backend, version, font-loading behavior, hinting policy, or other output-affecting backend configuration requires deliberate golden review.

### 10.5 Backend determinism

For identical input text, font bytes, font configuration, shaping configuration, raster settings, and backend version, a backend used in deterministic golden tests MUST produce stable output.

If a backend cannot make that guarantee across platforms, exact raster goldens MUST be scoped to the controlled backend/platform combination, and the project MUST still guarantee that preview uses the exact raster stored in `PreparedPrint`.

The reference raster path MUST disable platform-dependent subpixel/LCD rendering.

For controlled v1 goldens, glyph coverage MAY be rendered to deterministic grayscale/alpha coverage and converted to monochrome using a pinned threshold policy. Text rasterization MUST NOT inherit screen-specific subpixel order, display gamma, operating-system font smoothing, or UI accessibility text scaling.

---

## 11. Text Measurement Contract

### 11.1 Measurement inputs

A measurement request MUST make relevant inputs explicit.

Conceptually:

```kotlin
data class TextMeasureRequest(
    val text: String,
    val style: ResolvedTextStyle,
    val font: FontReference,
    val shapingContext: ShapingContext,
)
```

Depending on implementation, it may also include:

- a text range;
- script or language hints;
- direction where supported;
- requested font fallback chain;
- native-printer metric context;
- raster metric context.

A measurement API MUST NOT silently read a Compose theme or operating-system UI text scale.

### 11.2 Measurement outputs

A measurement result MUST provide sufficient portable geometry for layout decisions.

Conceptually it may contain:

```kotlin
data class TextMeasurement(
    val advance: Length,
    val metrics: TypographyMetrics,
    val graphemeBoundaries: List<Int>,
    val breakOpportunities: List<BreakOpportunity>,
    val shapedRuns: List<TextRun>,
    val diagnostics: List<Diagnostic>,
)
```

Not every caller needs every field, and the exact representation may differ.

The required semantic outcomes are:

- deterministic width/advance for the supplied text under the supplied typography;
- stable line-height/baseline metrics;
- grapheme-safe segmentation information;
- shaping information when shaping is required;
- enough coverage information to detect missing glyphs.

### 11.3 Measurement is not physical strategy selection

A text measurement API MUST NOT independently decide whether a line will be native or raster unless it is explicitly being called by `core-printer` as part of strategy resolution.

`core-layout` may measure under a typography context supplied by preparation, but printer strategy remains owned by `core-printer`.

### 11.4 No post-layout remeasurement by UI

Once a `LogicalDocument` or `PreparedPrint` contains resolved text geometry, downstream rendering MUST use that geometry.

A UI may draw representative glyphs into those boxes. It MUST NOT change box sizes based on a second measurement.

---

## 12. Unicode Segmentation and Grapheme Awareness

Rastrio MUST be Unicode-aware at text boundaries.

### 12.1 Required behavior

Text operations MUST NOT assume:

```text
String length == glyph count
UTF-16 code-unit count == character count
code-point count == printer cell count
grapheme count == glyph count
```

### 12.2 Grapheme-safe boundaries

The following operations MUST be grapheme-aware where they split or traverse visible text:

- emergency wrapping of unbroken content;
- font fallback boundaries where practical;
- truncation if introduced by a future feature;
- diagnostic source ranges intended to point to visible characters.

### 12.3 Unicode segmentation baseline

Extended grapheme-cluster boundaries for v1 use **Unicode 18.0 / UAX #29** with repository-controlled data/tables pinned to that version.

A platform update MUST NOT silently change authoritative grapheme boundaries.

An intentional Unicode-data upgrade requires regression review for:

```text
wrapping
fallback boundaries
emoji/ZWJ clustering
diagnostic ranges
golden fixtures
```

### 12.4 Combining sequences

A base character plus combining marks MUST be treated as a shaping unit for raster rendering.

The sequence MUST NOT be split merely to make a line fit.

For native printer text, a combining sequence is considered reliably native only if the selected profile/font/encoding capability explicitly supports the required behavior.

Absent such a capability declaration, preparation MUST treat the sequence as requiring raster fallback or produce an explicit diagnostic if rasterization cannot render it.

---

## 13. Shaping

### 13.1 When shaping is required

Shaping is required whenever correct rendering depends on contextual glyph selection, mark positioning, ligature/conjunct behavior, glyph reordering, or equivalent script rules.

Examples include many combining sequences and Indic scripts.

### 13.2 Raster shaping

Raster text intended to support such content MUST be shaped before rasterization.

The implementation MUST NOT rasterize complex text by drawing one Unicode code point at a time at fixed advances.

### 13.3 Native printer shaping

Printer-native text MAY be used for shaped or complex content only when the `PrinterProfile` declares a reliable native capability sufficient for that content and the selected encoding/strategy is compatible with it.

In the absence of explicit profile evidence, complex-script text MUST NOT be assumed to work natively merely because the printer accepts some corresponding byte values.

### 13.4 Shaping result immutability

Any shaped data required to create final raster text MUST be consumed during preparation.

`core-preview` and `core-escpos` MUST NOT perform a new shaping pass for the same physical operation.

---

## 14. Line Breaking and Wrapping

### 14.1 Ownership

`core-layout` owns line breaking.

It uses measurements and segmentation from `core-text` under explicit `LayoutConstraints`.

### 14.2 Width constraint

Each ordinary text line MUST be laid out against its available logical width after indentation and block-specific geometry have been applied.

A line MUST NOT be wrapped according to screen width.

### 14.3 Normal break opportunities

Where break opportunities exist in the supplied semantic text, `core-layout` SHOULD prefer them over emergency grapheme breaks.

The v1 baseline uses the Unicode Line Breaking Algorithm from **Unicode 18.0 / UAX #14**, with repository-controlled Unicode data/tables pinned to that version.

The implementation MAY use generated tables or a library, but the effective Unicode data version and tailoring MUST be explicit and regression-tested. It MUST NOT silently change because an operating system updates.

Rastrio v1 does not add dictionary-based hyphenation.

Where a script requires locale/dictionary tailoring that the selected v1 implementation does not provide, Core MUST remain deterministic and fall back to the normal emergency grapheme-boundary rule rather than silently using a platform-specific word breaker.

### 14.4 No automatic hyphenation in v1

Automatic dictionary-based hyphenation is not part of the v1 baseline.

Core MUST NOT silently insert hyphens that are absent from document content.

A visible hyphen already present in content remains ordinary text.

### 14.5 Explicit line breaks

A `.td` `LineBreak` node is authoritative.

It MUST terminate the current line at that point.

The layout engine MUST NOT remove or merge an explicit line break because additional width is available.

Consecutive explicit line breaks MUST preserve the intended empty-line structure subject to the document model's semantic rules.

### 14.6 Long unbroken text

Long unbroken text MUST have deterministic behavior and MUST NOT produce platform-dependent overflow.

For the v1 layout contract:

1. use a normal legal break opportunity if one exists;
2. otherwise use an emergency break at a grapheme-cluster boundary;
3. do not insert a hyphen;
4. do not split inside a grapheme cluster;
5. if one grapheme cluster itself exceeds the available width, preserve the cluster and emit a layout diagnostic describing the overflow condition.

The engine MAY place such an oversized cluster on a line wider than the nominal content area rather than corrupting it.

A future clipping, scaling, or substitution policy requires an explicit specification change.

### 14.7 Deterministic line choice

For the same text, styles, break policy, measurements, and available width, the selected line boundaries MUST be deterministic.

---

## 15. Whitespace

### 15.1 Semantic input is authoritative

`core-text` and `core-layout` MUST NOT perform undocumented Markdown-style whitespace normalization.

Normalization required by Markdown semantics belongs to `core-markdown` before `ThermalDocument` layout.

After semantic compilation, text content is treated as the document model's authoritative text.

### 15.2 Spaces

Ordinary spaces are measured according to the selected typography.

Multiple spaces that remain in the semantic document MUST NOT be collapsed merely because a UI renderer would collapse them.

### 15.3 Wrap-boundary spaces

When a soft line wrap occurs at breakable whitespace, layout MAY omit the break separator from the visible start of the following line according to the selected deterministic break policy.

This behavior MUST be covered by golden tests and MUST be the same across platforms for the same Core configuration.

### 15.4 Leading and trailing spaces

Leading or trailing spaces present in semantic text MUST NOT be silently stripped by a platform renderer.

If a specific block type intentionally normalizes them, that behavior belongs to the document/compiler/layout specification and must be deterministic.

### 15.5 Non-breaking spaces and similar characters

Characters whose semantics prohibit an ordinary break MUST NOT be treated as ordinary ASCII spaces solely for convenience.

If the selected break implementation lacks support for a particular Unicode spacing behavior, the limitation MUST be documented and tested.

---

## 16. Tabs

Rastrio v1 uses canonical **four-column tab stops**.

A tab character advances to the next tab stop; it is not defined as a blind replacement with four spaces.

Tab stops are anchored at the current text-content origin for the line/block.

For monospaced code typography:

```text
tabStopWidth = 4 × resolved monospace cell width
```

For proportional typography:

```text
tabStopWidth = 4 × resolved U+0020 SPACE advance
```

Tab stops are spaced by four resolved columns. The selected stop is the smallest tab stop strictly greater than the current inline advance on the authoritative logical grid. A tab therefore always advances by a positive amount after quantization, but under proportional typography that amount need not equal or exceed one full SPACE advance. For example, a SPACE advance of 0.5 mm gives stops every 2.0 mm; from an inline advance of 1.75 mm, the tab advances 0.25 mm to the 2.0 mm stop. Fixed-cell/code content measured in whole cells naturally aligns with fixed columns.

Rules:

- authoritative geometry MUST NOT use terminal, Compose, browser, Android, or desktop default tab settings;
- the tab calculation uses resolved Core typography metrics;
- tabs MUST participate in measurement before wrapping;
- repeated tabs MUST be deterministic;
- a tab near the wrap boundary MAY cause wrapping according to the normal line-break policy;
- code blocks and ordinary text use the same four-column rule, differing only in the metric used to define one column;
- no separate semantic tab node is required by `.td` v1.

Tests MUST cover tabs at column positions 0, 1, 3, 4, and 7; repeated tabs; tabs near wrap boundaries; and tabs under both proportional and monospace typography.

---

## 17. Paragraph Flow and Spacing

Paragraph vertical flow is owned by `core-layout`, not `core-text`.

### 17.1 Required behavior

Paragraph spacing MUST:

- be deterministic;
- use Core layout policy;
- be independent of Compose or platform typography defaults;
- contribute explicitly to logical y geometry;
- be reflected in `LogicalDocument` and therefore in physical preparation.

### 17.2 No hidden font padding

Platform-specific "include font padding" or equivalent defaults MUST NOT silently add vertical space to authoritative printer geometry.

Any ascent/descent/line-gap contribution used by Core must come from resolved portable typography metrics.

### 17.3 V1 spacing policy

The v1 baseline uses a centrally versioned `TextLayoutPolicyV1`.

Default vertical spacing is expressed relative to the resolved line advance so it remains meaningful across native and raster typography.

```text
body paragraph before spacing    = 0
body paragraph after spacing     = 0.5 × body line advance

adjacent list-item extra spacing = 0
code-block before spacing        = 0.5 × body line advance
code-block after spacing         = 0.5 × body line advance
code-block inter-line extra      = 0
```

Nested semantic blocks may introduce their own documented spacing, but platform UI defaults MUST NOT contribute additional space.

For lists:

```text
marker/content gap = 1 × resolved U+0020 SPACE advance
nested-list indent = 2 × resolved body em
```

If an implementation's portable typography model does not expose an explicit `em`, it MUST derive one through a documented deterministic metric from the resolved typography context.

Rounding from proportional spacing values into final logical/physical units MUST use the project's deterministic rounding policy.

These are v1 layout-policy defaults, not `.td` persisted values. Changing them is output-affecting and requires deliberate golden updates.

---

## 18. Code Blocks and Inline Code

### 18.1 Inline code

`InlineCode` is semantic style intent.

The resolved typography SHOULD use the project's code typography policy but MUST remain portable and deterministic.

Printer-native output is permitted only if the selected native font/style can represent the content reliably.

### 18.2 Fenced/code blocks

`CodeBlock` layout is owned by `core-layout`.

Code blocks MUST preserve explicit line boundaries represented by the document model.

Whitespace remaining in the semantic code content MUST be preserved.

### 18.3 Code wrapping

Printed output cannot rely on horizontal scrolling.

Unless a future document option specifies a different policy, an over-wide code line MUST follow the same deterministic emergency-wrap requirement as other text while preserving grapheme integrity and indentation as far as the available width permits.

No syntax-highlighting requirement exists in the v1 PRD.

### 18.4 Tabs in code

Tabs in code blocks use the canonical four-column tab-stop policy defined in Section 16.

They MUST NOT use platform-default tab stops.

---

## 19. Lists and Text Indentation

List structure is semantic document content; list geometry is owned by `core-layout`.

### 19.1 Indentation

List indentation MUST be explicit Core geometry.

It MUST NOT be derived from a UI list component.

### 19.2 Markers

For ordered lists, unordered lists, and checklists, the marker and the item content MAY be represented as separate logical spans or boxes.

Whatever representation is used, continuation lines SHOULD use a deterministic hanging-indent geometry so wrapped content remains aligned with the content start rather than the marker start.

### 19.3 Checklist markers

A semantic checklist item MUST NOT depend on an emoji font, Unicode checkbox glyph, Nerd Font, icon font, or platform-installed symbol font.

The Markdown/compiler layer identifies checklist state semantically:

```text
unchecked
checked
```

Core layout reserves deterministic marker geometry.

The canonical v1 visual marker is a simple monochrome graphic primitive:

```text
unchecked: outlined square
checked:   outlined square + check stroke
```

The exact marker geometry MUST be defined centrally using portable dimensions relative to the resolved body typography and MUST be golden-tested.

The marker MUST NOT be represented by an arbitrary font glyph merely because `☐`, `☑`, or a similar character happens to exist in a selected font.

During physical preparation, the marker is converted to deterministic prepared raster/graphic content before `PreparedPrint` finalization.

Because portable ESC/POS output cannot assume arbitrary horizontal composition of raster graphics beside native printer text, the v1 preparation policy SHOULD rasterize the complete physical checklist line/block when necessary to preserve the canonical graphical marker and text geometry.

This is not considered accidental native/raster glyph mixing; the checklist marker is semantic graphic content, and the finalized operation remains authoritative.

A future printer/profile-specific native checklist-marker optimization MAY be introduced only if it produces equivalent geometry and is explicitly modeled and tested.

### 19.4 Available width

Text measurement for list item content MUST use the width remaining after:

```text
outer list indentation
nested-list indentation
marker geometry
marker/content gap
```

### 19.5 Nested lists

Nested list indentation MUST remain deterministic and bounded by the available canvas width.

When indentation leaves insufficient width for a grapheme cluster, normal overflow diagnostics apply.

---

## 20. Alignment

Supported block-level alignment is:

```text
left
center
right
```

### 20.1 Logical alignment

For a resolved line with available width `W` and measured line advance `L`, conceptual x placement is:

```text
left   → x = 0
center → x = (W - L) / 2
right  → x = W - L
```

Block indentation and containing-box x offsets are applied outside this local formula.

Actual implementations MUST apply the project's deterministic unit and rounding rules.

### 20.2 No UI re-centering

A preview renderer MUST use the x position from Core geometry.

It MUST NOT center text again using a platform text measurement result.

### 20.3 Native printer alignment commands

A printer may support protocol-level alignment commands.

`core-printer` MAY choose such a strategy, but the choice and its resulting geometry MUST be represented in `PreparedPrint`.

`core-escpos` may emit the already-selected alignment command; it MUST NOT decide alignment from the text or block type.

---

## 21. Native Printer Font Geometry

Native printer text is a capability of the selected `PrinterProfile`.

### 21.1 Required profile-derived data

A native printer font used for authoritative layout MUST have enough validated geometry to support deterministic measurement.

Depending on printer behavior, this may include:

```text
stable native font identifier
nominal cell advance
nominal cell width
nominal cell height
line height / line-feed geometry where applicable
supported width scales
supported height scales
supported style modes
whether a style changes advance or line height
supported code pages / repertoires
known reliability limitations
```

The profile schema defines how these values are serialized.

### 21.2 Cell-based measurement

For a fixed-cell native font, simple native text may be measured from validated cell geometry.

The implementation MUST NOT substitute metrics from a visually similar screen font.

### 21.3 Variable or uncertain geometry

If a printer-native mode has variable or insufficiently characterized geometry, it MUST NOT be used for authoritative native layout unless the profile can describe it sufficiently.

Rastrio SHOULD fall back to raster text or produce a diagnostic rather than claim accurate native geometry from guesses.

### 21.4 Scale

Native width/height scaling MUST be selected during preparation.

Scaling MUST affect measurement exactly as defined by the validated printer capability.

The preview and encoder MUST consume the same resolved scaling choice.

### 21.5 Style effects

Bold, underline, strike, or other semantic style requests may map differently by printer.

A native style MAY be used only when the profile declares a reliable native capability and preparation has selected it.

If the requested style cannot be represented reliably, raster fallback MAY be required.

---

## 22. Native Character Coverage and Code Pages

### 22.1 Capability source

Native character/code-page support comes from `PrinterProfile` or validated effective-profile data.

It MUST NOT be inferred from the host operating system's encoders alone.

### 22.2 Coverage query

`core-text` or `core-profile` may expose a portable coverage abstraction conceptually equivalent to:

```kotlin
interface GlyphCoverage {
    fun canRepresent(
        text: String,
        nativeFont: NativeFontId,
        codePage: CodePageId
    ): CoverageResult
}
```

The exact API may differ.

A result SHOULD distinguish at least:

```text
fully representable
not representable
unknown/unreliable
```

`unknown/unreliable` MUST NOT be treated as reliably native.

### 22.3 Code-page selection

`core-printer` selects the code page.

Selection MUST be deterministic for the same effective profile, content, and print options.

If multiple code pages can represent the same line, the implementation MUST use a stable selection rule. A profile-defined preference order MAY participate.

The exact tie-break rule is not mandated here and should be kept stable once implemented.

### 22.4 Validation before encoding

Before `PreparedPrint` is finalized, preparation MUST establish that every native character can be represented by the selected code page/font combination without lossy substitution.

No encoder may silently replace unsupported text with:

```text
?
□
space
omitted bytes
```

unless a future explicit user-selected substitution policy is introduced.

### 22.5 Mechanical encoding

An implementation MAY store exact encoded character bytes in `PreparedPrint`.

Alternatively, `core-escpos` MAY perform the mechanical Unicode-to-selected-code-page conversion.

If encoding occurs in `core-escpos`, the mapping and code page MUST already be selected and validated, and any mismatch MUST fail rather than trigger a new strategy choice.

---

## 23. Unicode Support Detection

"Unicode support" is not a single Boolean printer property.

For a candidate native physical line, reliable native representation depends on at least:

```text
selected native font
selected code page/repertoire
requested style
scale
combining behavior
shaping requirements
profile-declared reliability
printer quirks
```

`core-printer` MUST evaluate native suitability using the effective profile.

### 23.1 Conservative rule

If correct rendering requires behavior not explicitly known to be supported by the selected native strategy, preparation MUST NOT assume support.

The fallback path is raster text when available.

### 23.2 No code-point-cell assumption

Even if every code point has a nominal mapping in a character set, native strategy is invalid if the sequence requires shaping or mark positioning that the printer cannot reliably perform.

---

## 24. Combining Characters

Combining-mark sequences require grapheme-aware handling.

### 24.1 Raster path

The shaping backend MUST receive the full sequence needed for correct mark placement.

The sequence MUST not be rendered as independent fixed cells.

### 24.2 Native path

Native representation is allowed only when the profile declares support sufficient for the sequence.

Merely finding separate byte values for a base character and mark is not sufficient evidence.

### 24.3 Fallback

If the sequence is not reliably native, the containing physical line follows the v1 whole-line raster fallback rule.

---

## 25. Indic and Other Complex Scripts

Rastrio's stable Android release requires Unicode raster fallback, and the test plan explicitly includes Indic and complex-script text.

### 25.1 Required raster behavior

A raster backend used for Indic text MUST support shaping sufficient for the selected script/font combination.

It MUST preserve:

- cluster integrity;
- contextual forms;
- conjunct/ligature formation where required;
- mark positioning;
- glyph ordering produced by the shaping backend.

### 25.2 Native behavior

A printer-native strategy for Indic or another complex script MUST NOT be selected unless the profile explicitly describes a reliable native capability for that behavior.

Absent such capability data, the physical line MUST be rasterized if the raster backend and font chain support the text.

### 25.3 Failure behavior

If neither a reliable native strategy nor a raster font/shaper can represent the content, preparation MUST return a structured diagnostic and MUST NOT silently corrupt or drop the text.

---

## 26. Bidirectional and RTL Text

Full Unicode bidirectional paragraph layout is **not required for the first stable Android release**.

The v1 baseline therefore follows these rules:

1. Core text contracts MUST remain capable of carrying explicit direction/script information for future support.
2. Portable models MUST NOT expose platform-specific bidi objects.
3. Rastrio MUST NOT fake RTL by reversing code points, grapheme clusters, or glyph runs.
4. Printer-native RTL MUST NOT be assumed from byte/code-page coverage alone.
5. A verified raster backend MAY support a specifically tested RTL case, but support MUST be explicit and covered by deterministic fixtures.
6. A paragraph/run containing significant strong RTL/bidirectional content that is outside the verified support set MUST produce a structured diagnostic before final preparation.
7. Unsupported bidi content MUST NOT be silently reordered, dropped, mirrored incorrectly, or printed in logical storage order while claiming faithful output.

A later release may make Unicode Bidirectional Algorithm support mandatory. Doing so requires an explicit specification update and pinned Unicode/bidi behavior.

---

## 27. Emoji and Symbol-Like Characters

Emoji may consist of:

- a single code point;
- a base plus variation selector;
- modifiers;
- regional indicators;
- zero-width-joiner sequences;
- multi-code-point grapheme clusters.

### 27.1 Grapheme handling

Emoji sequences MUST be treated as extended grapheme clusters for wrapping and fallback boundaries.

A zero-width-joiner sequence MUST NOT be intentionally split to fit a line.

### 27.2 V1 support scope

A full emoji font is not part of the v1 bundled font baseline.

Rastrio v1 MAY render simple monochrome symbol-like characters when they are covered by the pinned bundled font set and can be shaped/rasterized deterministically.

This does not imply general emoji support.

Examples of acceptable basic symbol coverage may include ordinary monochrome arrows, stars, geometric symbols, or check marks when present in the controlled font resource.

Emoji-presentation requests, color-only emoji glyphs, unsupported ZWJ sequences, unsupported flags, and unsupported modifier sequences MUST produce a structured missing-glyph/unsupported-emoji diagnostic.

Rastrio MUST NOT silently substitute a platform emoji, color bitmap, unrelated symbol, or `?`.

### 27.3 Native printer text

Emoji SHOULD be considered unsupported for native printer text unless the selected `PrinterProfile` explicitly declares reliable support for the exact required repertoire/behavior.

### 27.4 Raster text

Emoji or symbol-like clusters MAY be rasterized only when the selected controlled font/fallback chain and renderer can produce a deterministic monochrome representation.

A color-only font resource is insufficient for the v1 reference path.

### 27.5 Checklist independence

Checklist checkboxes are not emoji and are not governed by emoji coverage.

They are semantic graphic markers as defined in Section 19.3.

---

## 28. Missing Glyph Policy

A missing glyph is an output-integrity problem.

### 28.1 Required order

When a glyph or grapheme cannot be rendered by the initially selected raster font:

1. attempt deterministic configured raster font fallback;
2. if a fallback font can shape/render the cluster correctly, use it;
3. otherwise emit a structured diagnostic;
4. prevent silent loss or corruption.

### 28.2 No silent replacement

The following are non-conforming unless explicitly requested by a future user-facing policy:

```text
replace with '?'
replace with tofu
drop the character
replace with a space
strip combining marks
transliterate automatically
```

### 28.3 Fatal versus recoverable

The precise diagnostic severity may depend on workflow, but a final print preparation MUST NOT claim successful faithful preparation if required text is missing.

---

## 29. Raster Font Fallback and Bundled Font Policy

Raster font fallback is distinct from native/raster physical fallback.

### 29.1 Default bundled font policy

The default authoritative raster path MUST use project-controlled bundled FOSS font resources.

The v1 baseline font roles are:

```text
standard proportional family:
    Noto Sans
    - Light
    - Regular
    - Bold
    - Italic
    - Bold Italic

monospace family:
    JetBrains Mono
    - Regular
    - Bold
    - Italic
    - Bold Italic where the selected pinned release provides it

script-specific fallback:
    pinned Noto Sans script families required by Rastrio's claimed support
```

At minimum, the stable Android baseline MUST include a controlled fallback sufficient for the repository's required Indic/Devanagari shaping tests.

Rastrio MUST NOT bundle a Nerd Font merely for icon/symbol availability.

Exact font file versions and content hashes belong in a repository font manifest or equivalent build-controlled metadata. Golden fixtures MUST record the resolved font resource identity/hash.

The bundled font resources and their required license notices MUST remain compatible with Apache-2.0 distribution, FOSS packaging, and F-Droid expectations.

### 29.2 Ordered fallback chain

The default raster fallback chain is deterministic:

```text
requested bundled primary family/style
→ pinned script-specific bundled fallback(s)
→ permitted bundled symbol coverage
→ failure/diagnostic
```

The default path MUST NOT silently fall through to arbitrary operating-system fonts.

### 29.3 Cluster integrity

Fallback SHOULD occur at a shaping-safe cluster or run boundary.

The implementation MUST avoid selecting different fonts for code points that must be shaped together when doing so would break rendering.

### 29.4 Metrics

If fallback changes ascent, descent, line height, or horizontal advance, those metrics are output-affecting.

They MUST participate in layout before `PreparedPrint` is finalized.

### 29.5 User-selected font files

Rastrio MAY allow the user to explicitly select/import a font resource.

When the application can access the font bytes:

- those bytes SHOULD be treated as an explicit font resource for the preparation;
- a stable content fingerprint SHOULD be computed;
- shaping and rasterization MUST still use the normal portable `core-text` contract;
- the final prepared raster remains immutable;
- the font MUST be treated as untrusted input and subjected to applicable resource/security controls.

An explicitly selected font may change layout and output. This is intentional user choice, not a transparent fallback.

### 29.6 Explicit platform-font use

Platform-installed fonts MAY be offered as an explicit opt-in feature.

They MUST NOT participate in the default fallback chain.

Because exact platform font files and versions may differ:

- cross-device re-preparation may produce different metrics or pixels;
- portable golden tests MUST NOT depend on them;
- the UI SHOULD identify the choice as non-portable/reproducibility-sensitive;
- resolved metrics and final raster are frozen into the current `PreparedPrint`;
- if a stable font identity/fingerprint cannot be established, the preparation metadata SHOULD record that the source was platform-resolved.

An unavailable explicitly selected platform font MUST produce a diagnostic. Rastrio MUST NOT silently replace it with another system font.

### 29.7 No synthetic style by accident

The backend MUST NOT silently synthesize bold, italic, or another style because a requested bundled face is missing.

Any synthetic style transform, if introduced, requires an explicit deterministic policy and golden coverage.

---

## 30. Raster Text Generation

### 30.1 Goal

Raster text generation converts resolved text into final printer-ready raster geometry before ESC/POS encoding.

### 30.2 Inputs

A raster-text request conceptually includes:

```text
text
resolved line content
resolved style
font/fallback chain
font size/scale
shaping context
target line width
baseline/line-box contract
monochrome conversion settings
resource bounds
```

### 30.3 Shaping before rasterization

The rasterizer MUST shape text as required before drawing glyphs.

### 30.4 Output

The final `RasterText` operation MUST contain or reference immutable finalized raster data sufficient for `core-preview` and the active protocol encoder to consume without reshaping or re-rasterizing.

Large raster text MAY use the bounded immutable prepared-artifact contract defined by `core-printer`; it does not need to be retained as one in-memory bitmap.

Conceptually:

```kotlin
data class RasterTextResult(
    val width: Int,
    val height: Int,
    val baseline: Int?,
    val raster: MonochromeRaster,
    val diagnostics: List<Diagnostic>,
)
```

The representation may be banded/tiled or backed by application-controlled spool storage for bounded memory, provided both preview and encoding consume the same finalized prepared content.

### 30.5 Monochrome output

The physical printer output is monochrome.

If the shaping/raster backend first produces grayscale or alpha coverage, the conversion to final monochrome pixels MUST occur before `PreparedPrint` is finalized.

The conversion MUST be deterministic for controlled inputs.

### 30.6 No encoder rendering

`core-escpos` MUST NOT invoke a font rasterizer.

It serializes final raster bands using the already-selected raster protocol strategy.

### 30.7 Bounded memory

Long raster-text output MUST be compatible with Rastrio's bounded-memory design.

The implementation SHOULD support line-level or band-level raster data rather than requiring one receipt-sized bitmap.

---

## 31. Native-versus-Raster Strategy

### 31.1 Strategy owner

`core-printer` owns the strategy decision.

### 31.2 Native eligibility

A physical line is eligible for native text only when all required conditions are satisfied, including:

- every grapheme can be reliably represented;
- the selected code page/encoding is valid;
- required shaping is supported natively or not required;
- requested style can be represented reliably;
- native font geometry is sufficiently known;
- scaling is supported;
- profile quirks do not mark the strategy unreliable.

### 31.3 Raster eligibility

Raster text requires:

- a usable shaping/raster backend;
- font coverage;
- resource limits satisfied;
- a printer raster strategy selected by the profile/preparation path.

### 31.4 Forced preferences

`PrintOptions` may expose text strategy preferences such as native/raster preference.

A preference MUST NOT force an invalid native representation.

If "Native" is ever exposed as a strict option, unsupported content must produce a diagnostic rather than silent substitution.

---

## 32. V1 Whole-Line Raster Fallback Rule

The v1 rule is:

> If any glyph or grapheme on a resolved physical line requires raster fallback because native rendering cannot represent it reliably, the complete physical line is rasterized.

This is a normative simplification.

### 32.1 Consequences

A line MUST NOT contain a mixture such as:

```text
NativeText("abc")
RasterText("…")
NativeText("xyz")
```

for ordinary v1 fallback.

Instead it becomes one raster line or an equivalent set of raster bands representing the complete line.

### 32.2 Why the line boundary matters

Whole-line fallback avoids:

- baseline mismatches;
- native/raster advance disagreement;
- inter-run spacing errors;
- mixed renderer kerning assumptions;
- printer-state complexity.

### 32.3 Finer-grained future mixing

Future versions MAY support native/raster mixing at glyph-run or other safe boundaries.

That is not a v1 requirement and MUST NOT be introduced as an undocumented encoder behavior.

Any such feature requires updates to:

- this specification;
- `PreparedPrint` semantics;
- preview behavior;
- tests;
- likely profile capabilities.

---

## 33. Fallback-Induced Re-layout

Raster fallback may produce different metrics from native text.

Therefore a candidate layout cannot be treated as final merely because line breaking has already occurred once.

### 33.1 Mandatory invariant

Raster fallback MUST NOT silently change wrapping after `PreparedPrint` is finalized.

### 33.2 Re-resolution

If the chosen fallback strategy changes any geometry that can affect layout, including:

```text
advance
line height
baseline
font scale
available width assumptions
```

`core-printer` MUST cause the affected layout to be re-resolved before finalizing `PreparedPrint`.

### 33.3 Stability

Preparation MUST reach a stable result in which:

- line boundaries are final;
- native/raster strategy for each physical line is final;
- metrics correspond to the final strategy;
- physical positions are final.

The implementation MAY achieve this through a deterministic multi-pass or equivalent algorithm.

### 33.4 Non-convergence

If an implementation uses iterative re-resolution, it MUST have a deterministic bounded failure mode.

It MUST NOT loop indefinitely.

The exact iteration bound is an implementation/resource-limit decision and should be documented with the implementation.

---

## 34. `LogicalDocument` Text Requirements

A `LogicalDocument` is protocol-independent.

Its text geometry SHOULD contain enough information to prevent downstream layout ambiguity.

Depending on implementation, a laid-out line may include:

```text
source/document range
line text or semantic references
x
y
available width
measured advance
line height
baseline
resolved semantic style
indentation
alignment
explicit-break metadata where useful
```

It MUST NOT contain ESC/POS command bytes.

It MAY be target-constrained through the `LayoutConstraints` used to produce it.

---

## 35. `PreparedPrint` Text Requirements

Before `core-escpos` runs, every output-affecting text decision MUST already be resolved.

### 35.1 Common physical line data

Each physical text operation MUST have enough information to establish:

- exact text content or immutable source reference;
- physical segment;
- resolved x location;
- resolved y location;
- line width/advance;
- line height;
- baseline or equivalent vertical geometry where needed;
- alignment;
- style;
- scale;
- native-versus-raster strategy.

### 35.2 Native text operation

A `NativeText` operation MUST resolve at least:

```text
text content
physical x/y geometry
line/cell geometry
selected native printer font
selected code page or encoding strategy
validated text coverage
selected supported native styles
width scale
height scale
alignment strategy
```

If protocol state such as alignment or font selection is represented as state transitions rather than per-operation fields, the prepared plan MUST still determine those transitions unambiguously.

### 35.3 Raster text operation

A `RasterText` operation MUST resolve at least:

```text
source text or stable diagnostic reference
physical x/y geometry
final raster width
final raster height
final monochrome raster data or immutable band references
font/resource identity sufficient for diagnostics/reproducibility
scale
alignment
```

The raster operation MUST be sufficient for preview and encoding without shaping again.

### 35.4 Encoding completeness

If the native text operation does not store final encoded bytes, it MUST still store the exact selected encoding/code-page identity and enough validated state to make encoding purely mechanical.

### 35.5 Immutability

After creation, a `PreparedPrint` MUST NOT be mutated in place when text options change.

An output-affecting change requires a new preparation result.

---

## 36. Physical Preview Accuracy Contract

"Accurate preview" does not mean the same thing for raster and printer-native text.

### 36.1 Raster text

For raster text, physical preview MUST be based on the exact final raster represented by the `RasterText` operation.

The preview MUST therefore preserve:

- raster pixel dimensions;
- bit pattern;
- physical x/y placement;
- scaling already chosen by preparation;
- line/segment geometry.

The UI MAY scale the raster visually to fit the screen, but screen scaling MUST NOT modify the underlying prepared raster.

Where possible, preview display SHOULD avoid smoothing that makes a 1-bit raster appear to contain printable gray pixels.

### 36.2 Native printer text

For native printer text, the authoritative preview guarantees are:

- line breaks;
- line placement;
- x/y geometry;
- cell/advance geometry;
- line height;
- alignment;
- selected native font identity;
- selected scale;
- style intent/selected native style;
- operation selection;
- surrounding feed/cut/image/QR placement.

Exact printer ROM glyph appearance is guaranteed only if exact font data or an equivalent exact font model is available.

### 36.3 Representative native glyph rendering

When exact printer ROM glyph data is unavailable, preview MAY use a representative display font.

That representative font MUST NOT be measured to alter prepared geometry.

It should be drawn into the geometry established by `PreparedPrint`.

Visible glyph ink may differ from the physical printer, but the preview must preserve the prepared boxes, positions, wrapping, and dimensions.

### 36.4 Exact native font model

If a profile or controlled resource includes an exact printer-font bitmap/model, preview MAY render pixel-identical native glyph shapes, subject to verified printer behavior.

This is optional and not a v1 requirement.

### 36.5 No alternate preview layout

A physical preview implementation that reconstructs paragraphs from text and reflows them with a UI text engine is non-conforming.

---

## 37. Logical Preview Contract

Logical preview is distinct from physical preview.

A logical preview consumes `LogicalDocument`.

It shows the document under its chosen logical `LayoutConstraints` and is useful before a printer is selected.

Logical preview MUST still use Core geometry rather than re-running line layout in Compose.

A logical preview is not a promise that a later selected printer will use the same native/raster strategy or the same physical units.

---

## 38. Determinism

### 38.1 Core determinism

For the same:

```text
ThermalDocument
LayoutConstraints
effective typography metrics
font resources
shaping/raster settings
PrinterProfile
PrintOptions
controlled renderer version
```

Core transformations SHOULD produce the same result.

### 38.2 What must be exact

Exact deterministic assertions are required where output is part of the contract, including:

- line breaks;
- logical geometry;
- native coverage decisions;
- native/raster strategy;
- selected code page;
- prepared physical geometry;
- controlled raster text output;
- preview geometry derived from `PreparedPrint`.

### 38.3 Font-source determinism

The default authoritative raster path uses bundled project-controlled fonts.

Portable goldens MUST use those controlled resources and the pinned reference backend.

Explicit user-selected/platform fonts are permitted by Section 29, but they are outside the portable pixel-reproducibility guarantee unless their exact bytes and backend configuration are controlled.

The current `PreparedPrint` still remains authoritative because its final raster is frozen before preview/encoding.

### 38.4 Locale independence

Text measurement and strategy decisions MUST NOT change because the host application is running under a different UI locale unless language/script context is an explicit input to the shaping/layout contract.

### 38.5 Floating-point caution

Implementations SHOULD avoid making exact line-break decisions depend on unstable platform-specific floating-point behavior.

If floating-point values are used internally, final rounding/comparison behavior must be deterministic and regression-tested.

---

## 39. Font Resource Identity

A portable font reference SHOULD identify a font resource without exposing a platform font object.

Conceptually:

```kotlin
data class FontReference(
    val id: String,
    val family: String?,
    val style: FontStyleDescriptor,
    val resourceFingerprint: String?,
)
```

The exact type is implementation-defined.

For bundled and user-provided font files, a content hash or other stable resource fingerprint SHOULD be recorded in fixtures or preparation metadata where practical.

The repository SHOULD maintain a font manifest containing at least:

```text
logical font ID
upstream family
face/style
upstream version/revision
repository asset path
content hash
license identifier
required notice/license path
claimed script role
```

Font files MUST NOT be fetched from the network during normal offline preparation.

The project MUST comply with font licensing requirements and its Apache-2.0/FOSS/F-Droid distribution goals.

---

## 40. Resource Limits

Text rendering is subject to the project's bounded-resource requirements.

Implementations MUST defend against pathological text input such as:

- extremely long paragraphs;
- extremely long unbroken tokens;
- pathological combining sequences;
- enormous code blocks;
- very large glyph raster dimensions;
- excessive fallback runs.

Concrete thresholds belong in `docs/RESOURCE_LIMITS.md`.

When a text resource limit is exceeded, Core MUST return a structured diagnostic rather than allocate unbounded memory.

---

## 41. Diagnostics

Text-related failures and fallbacks should use the project's structured diagnostic system.

Relevant conditions include:

```text
complex text requires raster fallback
native code page cannot represent content
native font geometry insufficient
missing raster glyph
unsupported bidi/RTL behavior
oversized grapheme cluster
text raster resource limit exceeded
fallback caused layout re-resolution
renderer/shaper unavailable
prepared native text inconsistent with selected encoding
```

Diagnostics MUST avoid embedding printable user content in logs by default.

A UI-facing local diagnostic MAY identify a source location or limited context when needed, but logging policy remains privacy-preserving.

---

## 42. Error Handling Invariants

The following behavior is forbidden:

- silently dropping unsupported text;
- silently replacing unsupported native characters;
- splitting a combining sequence because a line is full;
- printing native bytes under a code page different from the one selected by preparation;
- reflowing raster fallback after preview was produced;
- letting preview and encoding choose different text strategies;
- using screen measurement to repair Core geometry;
- retrying layout in the encoder;
- treating an unsupported script as fixed-width one-code-point-per-cell text.

When faithful output cannot be prepared, Rastrio must surface a diagnostic rather than claim success.

---

## 43. Suggested Portable Model Shape

The following is illustrative only. It documents intended responsibilities, not frozen class names.

```kotlin
data class LayoutConstraints(
    val canvasWidth: Length,
    val orientation: Orientation,
    val typography: TypographyContext,
)

data class TypographyMetrics(
    val ascent: Length,
    val descent: Length,
    val lineGap: Length,
    val lineHeight: Length,
    val baselineOffset: Length,
)

sealed interface PreparedTextOperation

data class PreparedNativeText(
    val text: String,
    val xDots: Int,
    val yDots: Int,
    val widthDots: Int,
    val heightDots: Int,
    val nativeFontId: String,
    val codePageId: String,
    val style: PreparedNativeTextStyle,
    val widthScale: Int,
    val heightScale: Int,
    val alignment: Alignment,
) : PreparedTextOperation

data class PreparedRasterText(
    val xDots: Int,
    val yDots: Int,
    val widthDots: Int,
    val heightDots: Int,
    val raster: MonochromeRasterRef,
    val alignment: Alignment,
) : PreparedTextOperation
```

A real implementation MAY use segment-relative coordinates, logical-to-physical transforms, band references, compact operation records, or different names.

The normative rule is that the resulting values are complete enough that preview and ESC/POS encoding make no new physical decisions.

---

## 44. Preparation Sequence for Text

A conforming physical preparation path is conceptually:

```text
1. Read semantic text/style from ThermalDocument.

2. Load EffectivePrinterProfile.

3. Resolve candidate native typography:
      native font geometry
      styles
      scale
      character/code-page capabilities

4. Resolve raster typography resources/services.

5. Build explicit LayoutConstraints.

6. Run core-layout using the selected typography context.

7. Inspect each candidate physical line for:
      native glyph coverage
      shaping requirements
      style support
      code-page support
      profile reliability/quirks

8. Select NativeText or RasterText per line.

9. If strategy changes metrics:
      re-resolve affected layout deterministically.

10. Repeat only as required to reach stable geometry.

11. Rasterize every raster-selected line.

12. Validate every native-selected line against its final code page/font.

13. Convert to final physical coordinates and scaling.

14. Freeze PreparedPrint.

15. Derive:
      PreparedPrint → PrintPreview
      PreparedPrint → ESC/POS bytes
```

No step after `PreparedPrint` creation may return to step 5 and change geometry.

---

## 45. ASCII and Simple Native Text

ASCII is the baseline native-text case.

For supported native ASCII:

- measurement MAY use fixed printer-cell metrics from the profile;
- a supported code page MUST be selected;
- line breaking MUST use those same metrics;
- preview MUST use the prepared line/cell geometry;
- encoding MUST use the selected code page and font;
- no rasterization is required unless style/profile constraints force it.

This path is expected to support the first basic Android Bluetooth milestone.

---

## 46. CP437-Style Native Content

A CP437-style or equivalent profile-declared code page is a valid native capability only when the profile identifies:

- the code-page strategy;
- the repertoire/mapping used by Rastrio;
- compatible native font(s);
- any known unreliable characters or quirks.

Tests MUST verify both:

- successful native selection for supported characters;
- fallback for characters outside the selected repertoire.

A host JVM/Android charset implementation alone is not sufficient proof that a physical printer supports the mapping.

---

## 47. Unsupported Latin Content

A Latin character that is outside the selected printer-native repertoire MUST trigger native coverage failure.

If raster rendering can represent it, the containing physical line MUST become raster text under the v1 fallback rule.

If raster rendering also lacks the glyph, preparation MUST report a missing-glyph diagnostic.

No lossy accent stripping or transliteration is permitted by default.

---

## 48. Mixed Native/Raster Content

The v1 unit of physical fallback is the line.

Example:

```text
Line 1: "Plain ASCII"         → NativeText, if supported
Line 2: "Value: Ā"            → RasterText, if Ā is not native
Line 3: "Back to ASCII"       → NativeText, if supported
```

The presence of one raster line does not require the entire paragraph or document to become raster unless layout re-resolution makes that necessary.

Within `Line 2`, the entire line is rasterized.

---

## 49. Styles and Fallback

A line may require raster fallback even when every character is natively encodable.

Examples include:

- requested style unsupported by the selected printer font;
- profile-declared unreliable bold behavior;
- unsupported scale;
- style combination that changes geometry unpredictably.

Strategy selection MUST consider style capability as well as glyph coverage.

---

## 50. Tables and Text

Tables are laid out by `core-layout`.

Text inside a table cell follows the same measurement, shaping, grapheme, and fallback rules as other text, but its available width is the resolved cell content width.

Physical fallback MUST NOT independently resize table columns.

If raster fallback changes cell text metrics enough to affect row height or wrapping, the affected logical/table layout must be re-resolved before `PreparedPrint` is finalized.

---

## 51. Landscape and Wide Documents

Landscape logical layout may use a canvas wider than the selected printer.

Text shaping and line breaking occur on the assembled logical canvas before physical segmentation.

Physical segmentation MUST NOT independently re-wrap text in each strip.

When wide-document tiling is implemented:

- text geometry belongs to the wide `LogicalDocument`;
- segmentation maps that geometry into physical strips;
- overlap must not cause a second text layout;
- raster text spanning a segmentation boundary must be handled as prepared physical raster/segment geometry, not re-shaped per strip.

---

## 52. Preview Rendering Guidance

### 52.1 Raster preview

Render the final raster itself.

### 52.2 Native preview

Render a representation into the prepared geometry.

A native preview renderer may use:

- an exact profile font model;
- a project representative font;
- a simple cell visualization for diagnostics.

Whatever is used, it MUST preserve:

```text
prepared origin
prepared advance/cell widths
prepared line height
prepared scale
prepared alignment
```

### 52.3 Debug overlays

Development preview MAY expose optional diagnostic overlays such as:

- line boxes;
- baselines;
- cell boundaries;
- native/raster strategy labels;
- font/code-page identifiers.

These overlays are UI/debug aids, not printed content.

---

## 53. Testing Requirements

Testing is mandatory for text behavior because text geometry is an architectural contract.

### 53.1 Test categories

The project MUST include:

- unit tests;
- deterministic golden tests;
- preparation tests;
- preview consistency tests;
- encoder boundary tests;
- hardware tests where physical printer behavior is involved.

### 53.2 ASCII tests

Cover:

```text
single line
multiple lines
spaces
punctuation
numeric text
wrapping at exact width
wrapping one unit beyond exact width
left/center/right alignment
native style combinations that are supported
```

Expected result:

- native selection where profile supports it;
- stable line geometry;
- stable code-page/font selection.

### 53.3 CP437-style supported-content tests

Using a synthetic or known profile:

- verify representative non-ASCII characters that are explicitly mapped by the declared code page;
- verify selected code-page identity;
- verify exact native/raster decision;
- verify encoding does not substitute characters.

### 53.4 Unsupported Latin tests

Use at least one Latin character not present in the synthetic native repertoire, for example a controlled fixture containing `Ā`.

Verify:

- native coverage fails;
- complete physical line becomes raster;
- wrapping remains stable after any required re-layout;
- no `?` substitution occurs.

### 53.5 Combining-mark tests

Include:

```text
precomposed form
decomposed base + combining mark
multiple combining marks where supported by the test font
combining sequence near a wrap boundary
```

Verify:

- no grapheme split;
- raster shaping positions marks correctly according to the controlled golden;
- native path is rejected unless the synthetic profile explicitly supports the sequence.

### 53.6 Indic-script tests

Include at least one controlled sample requiring shaping, such as a Devanagari sample in a project-controlled test font.

Verify:

- shaping occurs;
- glyph order/placement matches the controlled golden;
- no code-point-per-cell rendering occurs;
- native path is rejected for a profile without explicit complex-script support;
- line-level raster fallback is selected;
- fallback-induced geometry is stable.

### 53.7 Mixed native/raster line tests

Create a paragraph with:

- native-only line;
- line containing one unsupported grapheme among otherwise native text;
- subsequent native-only line.

Verify:

- only the affected physical line is raster under the v1 rule, unless re-layout legitimately moves content;
- the affected line is fully raster, not mixed native/raster;
- neighboring lines remain native when eligible.

### 53.8 Emoji and symbol tests

At minimum test:

```text
simple monochrome symbol covered by bundled font
emoji + text
ZWJ emoji sequence outside v1 bundled coverage
emoji near wrap boundary
variation-selector emoji request
missing emoji glyph
```

Verify:

- cluster-safe wrapping;
- deterministic monochrome rasterization only where controlled coverage exists;
- structured missing-glyph/unsupported-emoji diagnostic otherwise;
- no platform emoji substitution;
- no silent replacement.

### 53.9 Checklist-marker tests

Test unchecked and checked Markdown checklist items.

Verify:

- parser/compiler semantics produce the correct checklist state;
- marker geometry is independent of emoji/icon/font coverage;
- unchecked marker is the canonical outlined square;
- checked marker contains the canonical check stroke;
- marker/content gap and hanging indent are deterministic;
- physical preparation rasterizes the required line/block where portable composition requires it;
- preview uses the exact prepared marker raster/geometry;
- printer/native font changes do not alter marker shape.

### 53.10 Tab tests

Test tabs at logical columns 0, 1, 3, 4, and 7, repeated tabs, tabs near a wrap boundary, tabs in code, and tabs in proportional text.

Verify advancement to the next four-column stop using the correct resolved metric.

### 53.11 Wrapping tests

Test:

```text
breakable sentence
exact-fit line
one-grapheme overflow
multiple spaces
explicit line break
consecutive line breaks
different canvas widths
different native font metrics
different raster font metrics
```

### 53.12 Long-word tests

Test an unbroken token wider than the available line width.

Verify:

- emergency wrap at grapheme boundaries;
- no automatic hyphen insertion;
- deterministic boundaries.

Also test a single grapheme wider than the available width and verify explicit overflow diagnostic behavior.

### 53.13 Alignment tests

For left, center, and right:

- verify logical x geometry;
- verify physical x geometry after conversion;
- verify preview uses those coordinates;
- verify encoder does not recalculate alignment.

Include odd-width cases that exercise deterministic rounding.

### 53.14 Bundled-font and explicit-font tests

Tests MUST verify:

- default proportional typography resolves to the pinned Noto Sans resources;
- requested Light, Regular, Bold, Italic, and Bold Italic faces resolve without synthetic substitution;
- code typography resolves to the pinned JetBrains Mono resource;
- required Devanagari/Indic fixture resolves through the pinned script fallback;
- default fallback does not consult platform-installed fonts;
- explicitly selected font files are fingerprinted and used;
- explicitly selected platform fonts are never silently substituted when unavailable;
- font changes that alter metrics trigger layout differences before `PreparedPrint` finalization.

### 53.15 Deterministic raster-output tests

With controlled font files and renderer version/settings, golden-test:

```text
ASCII raster line
unsupported Latin fallback line
combining-mark line
Indic line
emoji line where supported
mixed-font raster fallback line
odd raster widths
line with style
```

The golden MUST compare the final monochrome raster or another exact stable representation.

### 53.16 Preview/`PreparedPrint` geometry consistency

For every prepared text operation, tests MUST verify that preview:

- uses the same x/y;
- uses the same width/height;
- uses the same line box;
- uses the same scale;
- identifies the same native/raster operation type;
- does not introduce different wrapping.

For raster text, the preview test SHOULD verify the exact raster data identity or hash.

### 53.17 Encoder boundary tests

Tests MUST prove that the encoder:

- does not re-wrap;
- does not call the text shaper;
- does not call the text rasterizer;
- does not choose another code page;
- does not change native/raster strategy;
- fails on an invalid prepared native encoding rather than substituting.

### 53.18 Cross-profile tests

Prepare the same document against at least:

```text
synthetic narrow profile
synthetic wider profile
profile with limited native code page
profile with raster support
```

Verify that differences occur during preparation, not in the encoder.

### 53.19 Regression tests

Every bug affecting:

```text
wrapping
glyph coverage
fallback
alignment
baseline
line height
raster output
native encoding
preview mismatch
```

SHOULD add a regression test before the fix is considered complete.

---

## 54. Golden Fixture Requirements

Text golden fixtures SHOULD live under `test-fixtures/` in stable, reviewable form.

A fixture SHOULD record enough context to reproduce its result, including as applicable:

```text
input text/document
LayoutConstraints
effective PrinterProfile identifier/version
PrintOptions
font resource identity/hash
renderer/shaper version and pinned configuration
Unicode data version
line-break/segmentation policy version
expected LogicalDocument geometry
expected PreparedPrint operation summary
expected raster hash or raster data
expected diagnostics
```

Golden updates MUST be intentional.

Normal test execution MUST NOT silently rewrite expected text fixtures.

---

## 55. Hardware Validation

Automated tests prove Core consistency but cannot prove undocumented printer ROM behavior.

Printer Lab and hardware tests should validate:

- native font cell width;
- line height;
- scale behavior;
- supported styles;
- code-page mappings;
- ambiguous characters;
- combining/Unicode failures;
- raster line baseline and placement relative to native lines.

Discovered behavior that affects reliable output should update:

```text
PrinterProfile / profile overrides
docs/ESC_POS_NOTES.md
docs/HARDWARE_TESTS.md
relevant golden fixtures
```

Generic Core code MUST NOT gain model-specific hard-coded fixes.

---

## 56. Security and Privacy

Text rendering processes user content that may be private.

Implementations MUST NOT log printable text by default.

Debug logging SHOULD prefer:

```text
diagnostic code
text length
script/category metadata where non-sensitive
font/profile identifier
line count
dimensions
strategy type
raster dimensions
```

rather than raw content.

Shaping and raster services used by Core MUST operate offline for normal preparation.

A font fallback implementation MUST NOT fetch remote fonts implicitly.

Any future network font/resource retrieval would require an explicit higher-level policy and is outside this v1 specification.

---

## 57. Compatibility and Evolution

Changes that can alter physical text output are compatibility-sensitive.

Examples include changing:

- default raster font;
- font version;
- shaping backend/version;
- grapheme segmentation implementation;
- line-break algorithm;
- tab policy;
- emergency-wrap policy;
- native code-page mapping;
- native font metrics;
- alignment rounding;
- raster thresholding;
- line-level fallback rule.

Such changes require:

1. specification review;
2. golden-fixture review;
3. preview/print consistency tests;
4. migration or compatibility consideration where persisted prepared artifacts ever become relevant.

`.td` itself remains semantic and does not freeze a physical rendering engine version unless a future `.td` specification explicitly introduces such a field.

---

## 58. Conformance Checklist

An implementation conforms to this specification only if all of the following are true:

- [ ] `.td` remains semantic and printer-independent.
- [ ] `LayoutConstraints` are explicit.
- [ ] `core-layout` owns line breaking and logical geometry.
- [ ] `core-text` owns portable measurement/shaping/raster contracts.
- [ ] Compose does not determine authoritative printer geometry.
- [ ] platform graphics objects do not cross portable Core APIs.
- [ ] native font metrics come from validated printer capability data.
- [ ] code-page selection occurs before ESC/POS encoding.
- [ ] native coverage is validated without lossy substitution.
- [ ] Unicode 18.0 UAX #29 extended grapheme boundaries are pinned and tested.
- [ ] Unicode 18.0 UAX #14 line-breaking behavior is pinned and tested.
- [ ] four-column tab stops are implemented in Core.
- [ ] grapheme clusters are not intentionally split by emergency wrapping.
- [ ] combining/complex text is shaped where required.
- [ ] Indic text has a real HarfBuzz-based raster shaping path for the claimed stable support.
- [ ] bundled project-controlled FOSS fonts are the default raster typography source.
- [ ] explicit user/platform fonts do not silently enter the default fallback chain.
- [ ] checklist markers are semantic graphics independent of emoji/icon fonts.
- [ ] full bidi/RTL is not claimed unless a verified path exists.
- [ ] unsupported significant text produces a diagnostic.
- [ ] raster fallback is whole-line in v1.
- [ ] fallback-induced metric changes trigger re-layout before finalization.
- [ ] `PreparedPrint` resolves all output-affecting text decisions.
- [ ] raster text is finalized before preview/encoding.
- [ ] physical preview consumes `PreparedPrint`.
- [ ] raster preview uses exact prepared raster data.
- [ ] native preview preserves authoritative geometry even when glyph appearance is representative.
- [ ] `core-escpos` does not re-layout, re-shape, or choose text strategy.
- [ ] text tests and raster goldens cover the required cases.
- [ ] raw printable content is not logged by default.

---

## 59. V1 Resolved Typography Baseline

The following policies are resolved for the v1 implementation baseline and MUST NOT be inherited from platform defaults.

### 59.1 Tabs

Canonical tab stops are four columns, as defined in Section 16.

### 59.2 Bidirectional/RTL

Full Unicode bidirectional paragraph layout is not a first-stable requirement.

Unsupported significant RTL/bidirectional content produces a structured diagnostic. Naive reversal is forbidden.

### 59.3 Bundled fonts

The default authoritative raster path uses pinned project-controlled FOSS fonts:

```text
Noto Sans
    Light
    Regular
    Bold
    Italic
    Bold Italic

JetBrains Mono
    code/monospace role

pinned Noto Sans script-specific fallbacks
    only as required for claimed script coverage
```

The project does not require a Nerd Font.

Exact font binaries, upstream revisions, content hashes, and licenses are controlled by repository font-manifest/build metadata.

### 59.4 Emoji

A full emoji font is not bundled for v1.

Only deterministic monochrome symbol/emoji-like coverage present in the controlled font set is supported. Unsupported emoji clusters diagnose rather than silently substitute.

Checklist markers are semantic graphics and do not depend on emoji support.

### 59.5 Unicode segmentation and line breaking

V1 pins:

```text
Unicode version:             18.0
grapheme segmentation:       UAX #29
normal line-break algorithm: UAX #14
```

Repository-controlled tables/data or an exactly controlled equivalent implementation provide the authoritative behavior.

### 59.6 Layout spacing

`TextLayoutPolicyV1` defines the numeric paragraph/list/code defaults in Section 17.3.

Those values are centrally controlled, output-affecting, and golden-tested.

### 59.7 Shaping/raster backend

The reference stable raster path uses pinned:

```text
HarfBuzz
FreeType
bundled font bytes
deterministic monochrome conversion settings
```

Exact dependency versions live in repository dependency/toolchain configuration and are recorded in golden metadata.

Android and Desktop/JVM should use this same reference configuration for stable deterministic raster goldens.

Web may use a target-specific adapter until the project establishes an equivalent reference backend there; it must not be claimed pixel-identical without proof.

### 59.8 Explicit font customization

Users may explicitly select/import fonts and, where supported, explicitly opt into platform-installed fonts.

This does not change the default reproducible bundled-font path.

An explicit external font choice is output-affecting and may reduce cross-device re-preparation reproducibility, but the finalized `PreparedPrint` for that preparation remains immutable and authoritative.

---

## 60. Final Contract

Rastrio's text system is governed by one principle:

> Text may be semantic in `.td`, target-constrained in logical layout, and printer-specific in `PreparedPrint`, but no downstream stage may independently reinterpret the resolved physical text plan.

Accordingly:

```text
ThermalDocument
    expresses text intent

core-text
    supplies portable metrics, shaping, coverage, and raster contracts

core-layout
    resolves logical geometry under explicit LayoutConstraints

core-printer
    resolves native/raster strategy, code page, fonts, scaling,
    physical geometry, and any fallback-induced re-layout

PreparedPrint
    freezes the physical decision

core-preview
    displays that decision

core-escpos
    serializes that decision for ESC/POS v1

future protocol encoders
    must obey the same no-reinterpretation boundary
```

For raster text, preview accuracy means the exact prepared raster is shown geometrically.

For native printer text, preview accuracy means wrapping, placement, operation selection, dimensions, alignment, style intent, and native-font geometry are authoritative; glyph ink shape may remain representative when the printer's exact ROM font data is unavailable.

This distinction is mandatory. It preserves Rastrio's central promise that physical preview and printed geometry derive from one shared, immutable physical plan rather than from separate layout engines.


## Phase 3A Implementation Foundation

The Phase 3A implementation is deliberately narrower than the complete v1 contract above.
It introduces portable contracts in `core-text` and resolved logical geometry in `core-layout`;
it does not claim complete paragraph layout, Unicode shaping, native coverage or raster rendering.

- Text metric and geometry fields ending in `Mm` use portable millimetres. This keeps `core-text`
  independent of `core-document`. `LayoutConstraints.canvasWidth` uses the existing document
  `Length` with `MM`; there is no conversion to screen pixels or printer dots in this phase.
- `LogicalGeometry` centralizes Phase 3 logical precision at 0.000001 mm (one nanometre).
  Finite, nonnegative inputs are quantized to nearest integer ticks with ties to even. Each
  operand is quantized before arithmetic; sums/subtractions use checked integer ticks, scaling
  rounds back to the same grid, and equality/fits/overflow decisions compare ticks. For example,
  three 0.1 mm cells total 0.3 mm, 0.3000004 mm fits 0.3 mm, and 0.300001 mm does not. Exact
  half-tick boundaries follow ties-to-even rather than a caller-specific tolerance. Measurements
  emitted by the ASCII backend and geometry emitted by layout are canonical millimetres.
  Supplied constraints/metrics remain immutable inputs; their logical interpretation uses this
  policy. `LogicalDocument.widthMm` reports the canonical canvas width.
  The representability ceiling is 10^15 ticks (10^9 mm), keeping checked arithmetic and tick/mm
  round trips safe across common targets. Negative/non-finite values and raw resource-limit
  violations are rejected before rounding. A positive canvas width must remain positive after
  quantization; zero-progress heights/spacing fail with `LAY122`. This logical precision is
  independent of subsequent printer-dot rounding and does not change portable `Length` or `.td`.
- Coordinates increase rightward/downward from the logical canvas top-left. Ascent and descent
  are nonnegative distances above/below the baseline. Line height includes line gap, baseline
  placement is explicit, and no platform padding is added. Final physical rounding remains the
  responsibility of printer preparation.
- `ResolvedTypography` retains a controlled metric/font/backend identity and immutable style,
  metrics and optional supplied `FixedCellGeometry`. The fixed geometry is not a physical print
  strategy. `TypographyContext` resolves body, six headings, code and list-marker typography.
- `TextMeasurer` is injected and receives text plus resolved typography. Cluster ranges and break
  offsets use UTF-16 positions with exclusive ends. Clusters partition the text and carry advances
  including backend shaping effects; allowed/mandatory breaks must occur at cluster boundaries.
  Total advance equals the checked sum of individually quantized cluster advances under
  `LogicalGeometry`; equality is evaluated on that grid, not by exact binary floating-point
  summation. No platform object or resource handle crosses the boundary. The service must fail
  when required shaping/coverage cannot be supplied. Portable glyph-painting data can be added with the later shaping backend.
- `AsciiFixedCellMeasurer` accepts only printable ASCII U+0020 through U+007E under supplied fixed
  geometry. Each character is one cluster; SPACE supplies an allowed break after itself. Empty
  text has zero advance. Tabs, controls, line breaks and non-ASCII text return a diagnostic without
  invented geometry. This restricted backend does not substitute for Unicode 18 UAX #29/#14.
- `FoundationLayoutEngine` supports empty documents and fitting plain single-line paragraphs
  (including consecutive `Text` nodes and empty paragraphs). It resolves left/center/right x,
  line bounds and baseline, absolute run coordinates and block source indices. Paragraph after
  spacing is 0.5 × body line height per Section 17.3 and contributes to total document height,
  including after the final paragraph. Width is explicit in both orientations; landscape does
  not swap axes or infer a width. Results retain the complete constraints and metric context.
- `LogicalTextLine` permits a finite nonnegative advance larger than its available width, so
  later engines can preserve an intact oversized cluster with an overflow diagnostic per §14.6.
  The immutable model does not choose an overflow policy. The foundation engine continues to
  reject paragraphs requiring wrapping; it does not implement emergency breaks yet.
- Unsupported blocks/inlines, explicit breaks and paragraphs needing wrapping fail atomically
  (`LAY100` / `LAY101`), rather than clipping or dropping content. Unsupported schemas (`LAY102`)
  and inconsistent service metrics/input lengths (`LAY103`) also fail. Text service diagnostics
  retain their `TXT` code with the semantic source block index. No diagnostic embeds printable
  content. Geometry-only image/QR placeholder types are present for later layout; this engine
  does not yet produce them.
- `SnapshotList` defensively copies immutable elements into a collection with no mutation API.
  Finalized logical/text collections and heading selections cannot be changed through caller lists.

Runtime budgets use the provisional document/layout limits from RESOURCE_LIMITS Sections 17.1,
17.2, 17.5 and 17.7: 100,000 blocks, 500,000 inline nodes, 1,000 mm maximum width and 1,000,000
items (blocks, lines, runs and measured clusters). Phase 3A additionally limits aggregate input text
to 8 Mi UTF-16 code units and each ASCII measurement run to 64 Ki code units. These are trusted,
explicit operational bounds, not portable-format fields or permanent Unicode text policy.
Counts are checked before concatenation; generated items and finite coordinate arithmetic are
checked during layout. Resource failures return `LAY120` or `TXT120`, and coordinate-range failures return `LAY122`,
without partial output. Internal mandatory breaks from an injected backend also return `LAY100`.

Phase 3B must implement full logical flow and wrapping using these cluster/measurement contracts,
explicit line breaks, styles, heading/list/code/table/placeholder policies, and the specified
Unicode/tab rules when supported. Phase 3A makes no new permanent decision about font selection,
physical native/raster strategy, physical rounding, or shaping-backend versions.


## Phase 3B Paragraph and Heading Flow

Phase 3B extends the committed Phase 3A `FoundationLayoutEngine` without changing the
portable measurement or logical scene contracts. Only `Paragraph` and `Heading` blocks
are supported; structured blocks remain subsequent Phase 3 work and fail atomically with
`LAY100`. The broader Phase 3A roadmap above is not the scope of this subphase.

### Break selection and whitespace

Within each line, select the furthest fitting supplied legal break opportunity when the
next cluster would overflow. If none exists, use the furthest fitting measured cluster
boundary. No hyphens are inserted. Supplied mandatory breaks always terminate a line;
only semantic `LineBreak` nodes set `endsWithExplicitBreak`. A trailing semantic break
leaves a final empty line, and consecutive breaks preserve empty lines. Empty lines use
the block's base typography and contain an empty resolved run.

Phase 3B preserves all semantic whitespace, including leading/trailing/repeated ASCII
spaces and soft-wrap separators. A separator stays at the end of the preceding line if
it fits there; if it cannot fit, its cluster follows the same emergency/overflow rules as
other content. No space is trimmed, collapsed, or replaced. Per-line alignment includes
these measured spaces. This is the deterministic choice permitted by Section 15.3.

An indivisible oversized cluster occupies its own overflowing line with `LAY101` in the
successful document diagnostics. Repeated overflow is represented by one budgeted,
content-free diagnostic per source block; each overflowing line retains its actual geometry. Its x origin is zero for all alignments; negative
coordinates, scaling, clipping, substitution and dropping content are not introduced.
Block bounds describe the nominal canvas content area; overflowing line/run bounds retain
the actual measured advance.

### Typography and vertical geometry

Paragraph base typography is `TypographyContext.body`; heading base typography is
`TypographyContext.heading(level)`. Strong, emphasis and strike combine monotonically
with the resolved base style. Inline code selects the code typography and applies the
surrounding semantic bold/emphasis/strike flags. Base typography identity, metrics, role
and any existing underline are retained. Links lay out only visible children, with no
resource resolution or speculative destination metadata.

Adjacent text leaves with equal resolved typography are measured together. Each resolved
span is measured once, and wrapped runs slice its finalized measurement at supplied
cluster boundaries, rebasing UTF-16 offsets and break opportunities without remeasurement.
This does not add full Unicode segmentation or shaping. The ASCII backend continues to
return `TXT100` for tabs, controls and unsupported Unicode. The existing four-column tab
contract in Section 16 remains unchanged and awaits a supporting backend.

All output geometry uses `LogicalGeometry`. Mixed-typography runs share a line baseline:
its offset is the greatest quantized run baseline offset, and its below-baseline extent
is the greatest quantized `(lineHeight - baselineOffset)` among the runs. Line height is
the checked sum of those extents; each run has its own resolved height and y coordinate.
No extra inter-line spacing or host font padding is added.

Paragraph after spacing remains `0.5 × body line height`, including after the final block.
The provisional Phase 3B heading policy uses no before spacing and the same
`0.5 × body line height` after spacing, centrally named in `TextLayoutPolicyV1`.
Heading sizes/line metrics come solely from the supplied context, without an invented
heading scale. Block bounds exclude after spacing; document height includes it.

### Bounded traversal and failures

The engine preflights the entire supported tree before measurement, counting nested
inline nodes and text/code leaves under the existing aggregate budgets. Iterative iterator
frames bound traversal stack/sibling storage. `LayoutResourcePolicy.maxInlineDepth`
defaults to 64 and can only be lowered; root inline nodes occupy depth one. Excess nesting,
node/text/item counts fail with `LAY120`. Measured clusters and generated blocks, lines and
runs, measured spans, explicit break markers and retained diagnostics are charged to the
shared item budget before their Core retention/allocation. Source cluster atoms are reserved
before construction; service-internal measurement allocations retain their backend budget.
The provisional Phase 3B default is 100,000 cost items as justified in RESOURCE_LIMITS;
explicit break markers are also bounded by the inline-node budget. Intermediate span text, measurements and
cluster references are bounded by aggregate text/node budgets and the injected backend's
per-request policy; they are retained only for the current block. Final measurements may
copy cluster slices, so transient source-plus-result storage is bounded rather than
claimed to be zero-copy. Break slicing uses indexed lower-bound lookup rather than
rescanning a paragraph's complete break list for every narrow line.

`LAY101` now reports preserved cluster overflow rather than rejecting a paragraph needing
wrapping. `LAY104` rejects heading levels outside 1–6, including direct in-memory inputs.
Existing schema (`LAY102`), inconsistent measurement (`LAY103`), coordinate/progress
(`LAY122`) and backend (`TXTxxx`) diagnostics remain structured and content-free. Fatal
failures return no partial logical document. No `.td`/`.tcfg` schema or printer/platform
contract changes are introduced.


## Phase 3C1 Structured Text and Tabs

This section extends the committed Phase 3A/3B operational subset above.
Those earlier subsets describe their implementation milestones; their tab and
structured-block exclusions no longer apply to the Phase 3C1 layout engine.
The low-level ASCII text measurer still rejects tabs and line controls: layout
owns resolving their geometry, using portable metric inputs from core-text.

### Authoritative tab and code flow

`core-layout` selects the next strictly greater four-column stop from the
current line advance relative to its content origin. Stop arithmetic is on
`LogicalGeometry`'s normalized integer grid. Code uses resolved fixed-cell
advance when supplied; proportional text uses a measured U+0020 SPACE advance
under the resolved span typography. Stops do not reset at style/run boundaries.
When wrapping moves a tab, resolve it again from the new line's content origin.
Zero-progress or out-of-range tab metrics fail with a structured diagnostic.

Semantic tabs remain `\t` in finalized logical run text. Final run measurements
include the actual selected tab advances. Consumers MUST use this geometry,
not expand tabs through host text defaults. Tabs are break opportunities after
their advance; no whitespace is discarded.

Code uses the same cluster-safe flow as paragraphs/headings, with code
typography, no syntax highlighting and no horizontal scrolling. CRLF is one
explicit boundary; CR and LF independently delimit explicit lines. Empty code
text creates one empty logical code line. A trailing boundary preserves its
final empty line: `""` has one line, `"a\n"` and `"\n"` have two, and
`"\n\n"` has three. Semantic document text is not rewritten. All remaining
spaces and tabs are preserved. Before/after code spacing is 0.5 body line
advance, with zero additional inter-line spacing.

### Centrally versioned structured geometry

`TextLayoutPolicyV1` defines the following output-affecting v1 policies:

- Body em is resolved body ascent plus descent, excluding line gap.
- Outer list indentation is one body em; each nested list uses two body em
  from its enclosing item content origin. Quotes retain list nesting context.
- Marker/content gap is one measured body SPACE advance.
- Ordered markers are decimal non-negative `start + itemIndex`, followed by
  a period, measured under resolved list-marker typography. One common column
  per list reserves the maximum measured marker width. Each marker is
  right-aligned within it. Continuations and later child blocks share the
  common content origin, including across digit-width changes. Checked Long
  arithmetic rejects numbering overflow before measurement.
- Unordered markers are font-independent filled squares of side 0.25 body em.
- Checklist markers are outlined squares of side one body em, with stroke
  width 0.08 em. Outline centerlines are inset by half their stroke width.
  Checked markers additionally contain explicit points at square-local
  fractions (0.2, 0.5), (0.4, 0.7), (0.8, 0.25), with butt caps and bevel joins.
  No font glyph is queried for either checklist state.
- Graphic markers are vertically centered in a body line box at item flow
  start, even if the first child is another container. Item height contains
  both marker extent and complete child flow. Ordered markers carry their
  resolved baseline and line height; those extents participate in item height.
- Adjacent items add zero extra spacing. Child paragraph/code spacing remains
  explicit; list containers add no additional gap.
- Quotes preserve nested semantics and indent content by one body em per
  level. They add no decorative rule or container spacing.
- Separators are semantic solid rules spanning the current content width.
  Thickness is 0.05 body em; before/after spacing is 0.5 body line advance.

Every dimension is normalized through LogicalGeometry. Indentation/markers
that exhaust content width fail with LAY107. Positive remaining width that
cannot fit an indivisible cluster retains it and reports LAY101 as before.
No negative, artificial minimum or clamped content width is invented.

### Logical output and runtime validity

LogicalTextBlock.kind distinguishes CODE from ordinary TEXT without changing
existing paragraph/heading geometry. LogicalListBlock and LogicalListItem
retain common marker/content geometry and finalized child blocks.
LogicalUnorderedMarker, LogicalOrderedMarker and LogicalChecklistMarker carry
explicit marker geometry/semantics. LogicalQuoteBlock retains quote nesting and
child origins; LogicalSeparator retains rule bounds and spacing. All nested
coordinates are absolute millimetres; all child collections are snapshots.
There is no rasterization, printer strategy, protocol or platform state here.

ListItem.blocks, ChecklistItem.blocks and Quote.blocks must be non-empty.
Runtime layout enforces the same rule as the existing `.td` validator, now
explicit in TD_SPEC Section 44. A checklist item containing a valid empty
paragraph remains valid and retains normal marker and paragraph geometry.
This reconciliation does not change the validator or persisted format.
Empty list containers are permitted.

New diagnostics: LAY105 rejects empty item/quote block arrays, including checklist items; LAY106 rejects
negative or overflowing ordered numbering; LAY107 rejects exhausted content
width. Existing LAY100 still rejects excluded block types, LAY120 covers all
runtime resource bounds, and LAY122 covers coordinate/zero-progress failures.
Diagnostics contain no printable content. Failures return no partial document.


## Phase 3C2 Tables and Semantic Placeholders

Phase 3C2 completes the feature-bearing logical Phase 3 subset. It extends the
Phase 3A/B/C1 contracts without changing text measurement, shaping or portable schemas.
The following output-affecting choices were approved on 2026-10-06, with empty-cell,
requested-size and fixed-image-box clarifications approved on 2026-10-07. They are
centralized in `TextLayoutPolicyV1`.

### Table geometry and text

A table spans its enclosing available logical content width. Divide that width's
integer LogicalGeometry ticks equally by the column count, giving one remaining tick
per column from left to right. This requires no intrinsic text sizing or sampling.
Column widths and origins are finalized before cell text flow. There are no column
gaps, borders, decorative rules, automatic header bold or header typography changes.
Header identity remains explicit in the logical rows. Author-supplied inline styles apply.

All cell sides have 0.25 body em padding, where body em is body ascent plus descent.
Padding is normalized on the logical grid; zero canonical padding fails with LAY122.
Padding exhausting the column width fails with LAY107. A positive remaining content
width still uses existing cluster-safe emergency wrapping/overflow with LAY101.
The existing shared text flow determines cell typography, line breaks, tabs, alignment,
styles, run coordinates and baselines. There is no cell-specific measurement/wrapping
algorithm and no paragraph after-spacing inside a cell. Empty content reserves one
canonical empty body-line height plus top/bottom padding. The shared Phase 3B empty-line
contract currently retains one empty resolved run; tables neither add another synthetic
run nor introduce a different empty-text representation. A row whose cells produce no
text geometry still reserves one canonical body-line height plus top/bottom padding.
Zero/unrepresentable canonical line height fails with LAY122. Actual retained text objects
and explicitly reserved structural/preflight costs count against the shared budget.
Cell alignment overrides the column alignment; absent an override the column
alignment applies. Content is top-aligned. Each row height is its tallest finalized
cell text height plus padding on both vertical sides. All row cells share that y/height.
Content bounds describe the padded row area; each text block retains its actual height.

`LogicalTableBlock` retains table bounds, column bounds/alignment, row bounds/header
identity, cell bounds/column index/alignment, content bounds and finalized text blocks.
Collections are immutable snapshots. Column bounds cover the complete table height.
Wide tables resolve once on the assembled explicit canvas. Landscape neither swaps
axes nor substitutes width. There is no semantic column splitting or physical segmentation.

### Image and QR geometry

`LogicalImagePlaceholder` preserves asset identity, alignment, sizing and optional alt text.
Intrinsic size is explicitly unresolved. Its square bounds are authoritative placeholder
geometry, never an assertion about the image's true aspect. AutoSizing and FitWidthSizing
both use enclosing available width, retaining distinct semantic sizing intent.
RequestedWidthSizing uses its exact normalized positive logical width. No intrinsic-size
service, asset access, URI interpretation, metadata parsing or image decoding occurs.
The square is FINALIZED logical outer geometry, including its height and contribution
to document flow. Later explicit asset resolution/preparation MUST fit the actual image
inside that already-final box while preserving its aspect ratio; it MUST NOT change the
box, neighboring positions, document height or logical layout after decoding. AutoSizing
and FitWidthSizing reserve a full-width square outer box; RequestedWidthSizing reserves
a square of the requested width. Unused interior space does not shorten the logical box.
This policy makes no claim that the image pixels themselves are square. No intrinsic
image dimensions are needed to establish these authoritative logical outer bounds.

`LogicalQrPlaceholder` preserves opaque payload, alignment, error-correction preference
and nullable requested size. Requested sizes become exact square bounds. The versioned
default is min(30 mm, enclosing available content width). No modules, quiet zones,
QR library, encoding, URL interpretation or physical/native strategy is introduced.

Requested lengths must be finite, positive millimetres. Invalid lengths fail with
LAY105; raw lengths above runtime maxWidthMm fail with LAY120 before quantization;
positive lengths quantizing to zero fail with LAY122. Normalized sizes above enclosing
available width fail atomically with LAY107; no clamping, scaling down or overflow
geometry occurs. A valid portable .td can therefore fail under narrower explicit
LayoutConstraints and succeed under wider constraints. For Phase 3C2, LAY107 is
deliberately generalized from exhausted marker/indent content width to insufficient
available logical width for required geometry, including cell padding and requested
image/QR dimensions. Paragraph cluster overflow remains governed separately by LAY101.
Left/center/right placement
uses zero/half/all spare content width from its explicit enclosing origin, using the
existing LogicalGeometry normalization. Tiny available widths retain positive progress.

### Vertical flow

Tables, images and QR each add 0.5 body line advance before and after the block,
including after the final block. Gaps are centrally versioned, normalized and retained
explicitly; gaps quantizing to zero fail with LAY122. Block bounds exclude spacing.
All emitted geometry and semantic spacing contribute to LogicalDocument.heightMm.
The same rules apply within list/quote content regions. There is no Phase 4 UI,
PreparedPrint, printer geometry, rasterization, QR encoding or protocol work.
