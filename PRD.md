# Rastrio — Product Requirements Document

**Status:** Implementation Baseline  
**Document version:** 2.1  
**Project:** Rastrio  
**License:** Apache-2.0  
**Primary release target:** Android  
**Secondary targets:** Desktop/JVM (Windows and Linux), Web/Wasm  
**Possible future desktop target:** macOS  
**Explicit non-target:** iOS  
**Language:** Kotlin  
**Build system:** Gradle with Kotlin DSL  
**UI:** Compose Multiplatform shared by default  
**Primary printer family:** ESC/POS-compatible thermal printers  
**Current available physical reference device:** Helett H50i BillQuick Go  
**Portable document format:** `.td`  
**Printer profile format:** `.tcfg`

---

## 1. Purpose of This Document

This PRD is the authoritative product and engineering baseline for Rastrio.

It defines:

- product scope and priorities;
- architectural boundaries;
- canonical data flows;
- module ownership;
- portable file formats;
- printer preparation and preview contracts;
- platform responsibilities;
- privacy, security, and performance requirements;
- test strategy;
- implementation phases and exit criteria;
- engineering rules for human contributors and coding agents.

Implementation may refine internal APIs, class names, package names, and algorithms, but changes that affect the invariants or public formats in this document require an explicit architecture or specification update.

Where a dedicated technical specification exists, that specification may contain greater detail but must remain consistent with this PRD.

---

## 2. Executive Summary

Rastrio is an open-source, offline-first, cross-platform thermal-document authoring, preview, and printing application.

Its primary purpose is to let users take normal content—especially Markdown, notes, lists, QR payloads, and images—and produce reliable output on inexpensive thermal printers without requiring the user to understand ESC/POS commands, printer code pages, raster packing, Bluetooth sockets, USB endpoints, or device-specific firmware behavior.

The first production target is Android. Desktop/JVM is also a first-class development target because it provides a fast environment for shared UI, parser, layout, preview, and profile-development work. Web is a later product target and must not block Android delivery.

The central architectural rule is:

```text
User content
    ↓
ThermalDocument
    ↓
resolved layout
    ↓
PreparedPrint
   /          \
  ↓            ↓
Preview     trusted protocol encoder
            (`core-escpos` for v1)
                   ↓
               Transport
                   ↓
                Printer
```

`ThermalDocument` is portable and printer-independent.

`PreparedPrint` is immutable, printer-specific, and authoritative. Physical preview and the active trusted protocol encoder must consume the same `PreparedPrint` and finalized prepared-artifact content. For `.tcfg` schema v1, that encoder is `core-escpos`. No downstream stage is allowed to independently re-layout, re-wrap, re-shape, re-dither, re-rasterize, re-segment, or otherwise reinterpret the physical print plan.

---

## 3. Product Vision

Rastrio should make inexpensive thermal printers behave more like useful general-purpose document printers.

A user should be able to print content such as:

```text
README files
meeting notes
shopping lists
todo lists
code snippets
receipts
QR messages
images
wide Markdown tables
```

without dealing directly with:

```text
ESC/POS byte commands
printer code pages
raster bit packing
native font quirks
Bluetooth RFCOMM sockets
USB endpoints
printer widths in dots
cutter commands
firmware-specific behavior
```

The product should expose advanced printer controls when they are genuinely useful, while keeping common workflows approachable.

Rastrio should feel like a polished document utility with a printer-engineering layer underneath it—not like an ESC/POS diagnostic program with a document editor attached.

---

## 4. Product Principles

### 4.1 Portable documents, target-specific preparation

`.td` stores user content, semantics, assets, and limited author layout intent. It does not store ESC/POS bytes, Bluetooth identities, USB endpoints, printer code pages, or transport-specific configuration.

### 4.2 One physical plan

The exact same `PreparedPrint` object (or equivalent immutable value snapshot) must drive physical preview and trusted protocol serialization. For v1, protocol serialization is performed by `core-escpos`.

### 4.3 Shared UI by default

Compose Multiplatform is the default UI implementation for Android, Desktop, and Web. Platform-specific UI should only exist where a concrete platform limitation or native integration requires it.

### 4.4 Platform code owns real platform concerns

Bluetooth, USB, file pickers, clipboard APIs, browser hardware APIs, lifecycle, permissions, and equivalent OS integrations stay in the relevant platform application.

### 4.5 Core remains framework-independent

Printer intelligence, document models, parsing, layout, image processing, profile logic, and protocol preparation must not depend on Android or Compose.

### 4.6 Determinism where practical

Pure transformations in Core should produce the same result for the same inputs. Golden tests are required for transformations where exact output matters.

### 4.7 Data-driven hardware behavior

Printer capability and quirk handling belongs in `PrinterProfile` and validated strategy selection, not in scattered model-specific `if` statements.

### 4.8 Android first

Desktop and Web should support development and portability, but they must not delay the first reliable Android print workflow.

### 4.9 Offline and private by default

Authoring, document conversion, preview, and printing must work without an account and without network access. Printable user content must not be logged by default.

### 4.10 FOSS-friendly by construction

Official builds must not require proprietary services or dependencies that prevent Apache-2.0 distribution or F-Droid-compatible packaging.

---

## 5. Scope

### 5.1 In scope

Rastrio v1 architecture must support:

- GitHub Flavored Markdown authoring and import;
- portable `.td` documents;
- shared Compose Multiplatform UI;
- accurate printer-specific physical preview;
- Android Bluetooth Classic printing;
- thermal image processing and printing;
- deterministic dithering;
- raster fallback for text that cannot be represented natively;
- structured non-Markdown templates;
- QR printing;
- printer profiles and printer instances;
- import/export of printer profiles;
- printer diagnostics and Printer Lab workflows;
- manual cut guides and automatic cutter operations where supported;
- later Android USB printing;
- later Desktop printing/export capabilities;
- later Web file and hardware integration where browser APIs permit it.

### 5.2 Explicitly not required for the first Android stable release

The following are valid roadmap items but must not block the first stable Android release:

- Web hardware printing;
- Desktop hardware printing;
- macOS-specific packaging;
- iOS support;
- cloud accounts or synchronization;
- server-side printing;
- arbitrary CSS-like page layout;
- per-glyph native/raster mixing;
- advanced color image processing;
- full PDF import;
- proprietary printer SDK integration;
- wide-document multi-strip physical assembly if it is not release-ready.

### 5.3 Explicit non-goals

Rastrio is not intended to be:

- a general-purpose word processor;
- a desktop publishing system;
- a browser-based cloud printing service;
- a printer firmware updater;
- a raw ESC/POS command terminal for normal users;
- a replacement for vendor-specific POS software;
- an iOS application.

---

## 6. Target Platforms and Development Priority

Priority order:

```text
1. Android production workflow
2. Shared Core
3. Shared Compose UI
4. Desktop/JVM development convenience
5. Android product polish and reliability
6. Android USB
7. Advanced physical features such as wide-document tiling
8. Desktop product expansion
9. Web product expansion
10. Optional macOS packaging
```

During early development:

```text
androidApp
    must become functional

shared
    must remain portable and tested

desktopApp
    should remain useful for development and should compile

webApp
    should remain structurally healthy, but must not block Android delivery
```

Before the dedicated Web phase, a transient limitation in a Web-only dependency or Beta Web UI tooling must not automatically block an otherwise valid Android release. Common/Core portability must still be preserved.

---

## 7. Primary User Workflows

### 7.1 Markdown printing

```text
Write/Open Markdown
      ↓
Compile
      ↓
Logical Preview
      ↓
Select Printer
      ↓
Physical Preview
      ↓
Adjust Print Options
      ↓
Print
```

### 7.2 Quick templates

Initial visual workflows:

- Todo / Checklist
- Shopping List
- Quick Note
- Memo
- QR Message
- Image Print

Template users must not need to understand Markdown.

All template states compile directly to `ThermalDocument`.

### 7.3 Image printing

```text
Select image
    ↓
Decode
    ↓
Crop / resize / tonal adjustments
    ↓
Dither / threshold
    ↓
Physical preview
    ↓
Print
```

### 7.4 Printer management

```text
Discover or select physical printer
    ↓
Associate PrinterProfile
    ↓
Apply optional user overrides
    ↓
Calibrate / test in Printer Lab
    ↓
Persist PrinterInstance
```

---

## 8. Canonical Terminology

### `ThermalDocument`

Portable semantic document model. Printer-independent.

### `.td`

Portable archive representation of `ThermalDocument` and its embedded assets.

### `LayoutIntent`

Author-controlled layout information stored in the document, such as orientation, alignment, explicit line breaks, and an optional logical maximum width.

### `LayoutConstraints`

Resolved constraints supplied to `core-layout`, such as logical canvas width and typography metrics. These are execution inputs, not permanent document content.

### `LogicalDocument`

A protocol-independent document laid out against explicit `LayoutConstraints`. It may therefore be target-constrained even though it contains no ESC/POS commands.

### `PrinterProfile`

Portable description of a printer model or capability set.

### `.tcfg`

Portable serialized form of `PrinterProfile`.

### `PrinterInstance`

A physical printer known to the user, including transport identity and associated profile.

### `PrintOptions`

User-selected options for the current preparation operation.

### `PreparedPrint`

Immutable printer-specific physical output plan. This is the authoritative input to both physical preview and protocol encoding.

### `PrintPreview`

Platform-neutral preview representation derived from `PreparedPrint`.

### `PrinterTransport`

Platform-specific mechanism that delivers already encoded byte chunks to a physical printer.

### `ProtocolProfile`

Portable protocol-family-specific configuration owned by `PrinterProfile`.

For `.tcfg` schema v1, the only variant is conceptually `EscPosProtocolProfile`, which contains the registered ESC/POS `dialectId`, initialization strategy, and compatibility boundary for validated ESC/POS strategies.

A future printer protocol family requires a future `.tcfg` schema/specification update and its own trusted `ProtocolProfile` variant and encoder.

---

## 9. High-Level Architecture

Rastrio has four implementation domains:

```text
SHARED CORE
    ↓
SHARED PRESENTATION + COMPOSE UI
    ↓
PLATFORM APPLICATION
    ↓
PLATFORM SERVICES / TRANSPORTS
```

More precisely:

```text
┌───────────────────────────────────────────────┐
│            SHARED COMPOSE UI                  │
│                                               │
│ Screens, forms, navigation, preview widgets  │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│          SHARED PRESENTATION                  │
│                                               │
│ App state, workflows, orchestration           │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│                SHARED CORE                    │
│                                               │
│ Document / Markdown / Text / Layout / Raster │
│ Profiles / Printer / Preview / ESC-POS        │
└──────────────────────┬────────────────────────┘
                       │ contracts
                       ▼
┌───────────────────────────────────────────────┐
│              PLATFORM APPS                    │
│                                               │
│ Android / Desktop / Web                       │
│ OS APIs, decoding adapters, printer transport │
└───────────────────────────────────────────────┘
```

Dependency direction must never be reversed merely for convenience.

---

## 10. Repository Structure

The generated application modules should be preserved and the repository should evolve toward:

```text
ProjectRepo/
│
├── androidApp/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── platform/
│           └── printer/
│               ├── bluetooth/
│               └── usb/
│
├── desktopApp/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── platform/
│           └── printer/
│
├── webApp/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── platform/
│           └── printer/
│
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── app/
│       │   ├── navigation/
│       │   ├── presentation/
│       │   ├── ui/
│       │   └── platform/
│       └── commonTest/
│
├── core/
│   ├── document/
│   ├── markdown/
│   ├── templates/
│   ├── text/
│   ├── layout/
│   ├── raster/
│   ├── profile/
│   ├── printer/
│   ├── preview/
│   └── escpos/
│
├── build-logic/
│   └── convention/
│
├── test-fixtures/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── TD_SPEC.md
│   ├── TCFG_SPEC.md
│   ├── TEXT_RENDERING_SPEC.md
│   ├── PREVIEW_SPEC.md
│   ├── TESTING.md
│   ├── SECURITY.md
│   ├── RESOURCE_LIMITS.md
│   ├── ESC_POS_NOTES.md
│   └── HARDWARE_TESTS.md
│
├── gradle/
│   └── libs.versions.toml
│
├── AGENTS.md
├── PRD.md
├── README.md
├── LICENSE
├── settings.gradle.kts
└── build.gradle.kts
```

Exact Kotlin package directories may follow normal package conventions.

Gradle convention plugins should be introduced early to avoid repeating KMP, Compose, compiler, test, and Android-library configuration across Core modules.

`gradle/libs.versions.toml` is the authoritative location for current dependency and plugin versions.

---

## 11. Module Dependency Rules

For dependency diagrams in this PRD:

```text
A → B
```

means **module A depends on module B**.

The Phase 0 baseline dependency graph is:

```text
core-markdown  ─────→ core-document
core-templates ─────→ core-document

core-layout ─────────→ core-document
core-layout ─────────→ core-text

core-printer ────────→ core-layout
core-printer ────────→ core-profile
core-printer ────────→ core-raster
core-printer ────────→ core-text

core-preview ────────→ core-layout
core-preview ────────→ core-printer

core-escpos ─────────→ core-printer
core-escpos ─────────→ core-profile where encoder-visible strategy identifiers
                        or protocol value types are owned by `core-profile`

shared ──────────────→ required core-* modules

androidApp / desktopApp / webApp
   └─────────────────→ shared
   └─────────────────→ portable Core where a platform integration genuinely
                        requires a direct Core contract
```

`core-printer` owns the `PreparedPrint` model, prepared-operation model, and preparation APIs.

`core-preview` and `core-escpos` are downstream consumers of `PreparedPrint`. They MUST depend toward `core-printer`; `core-printer` MUST NOT depend on either of them.

The exact Gradle project paths and `api`/`implementation` exposure may vary where required by Kotlin/Gradle mechanics, but the ownership and direction above are normative.

These rules are mandatory:

1. `core-*` must not depend on `shared`.
2. `core-*` must not depend on application modules.
3. Core must not import Android APIs.
4. Core must not import Compose APIs.
5. Platform applications may depend on `shared` and portable Core as needed.
6. Platform types must not leak into portable Core APIs.
7. Circular module dependencies are forbidden.
8. `core-preview` and `core-escpos` must remain downstream consumers of `PreparedPrint`.
9. No generic catch-all module such as `core-common` or `core-model` should be introduced merely to avoid correct dependency design. A new shared Core module requires a concrete cohesive responsibility.

---

## 12. Shared Core Requirements

Core modules contain Rastrio's portable document and printer intelligence.

They must remain independent of Compose and Android.

Portable Core APIs must not expose:

```kotlin
android.*
androidx.activity.*
android.graphics.Bitmap
android.bluetooth.*
android.hardware.usb.*
androidx.compose.*
```

Core should remain usable from:

```text
Android
Desktop/JVM
Web/Wasm where supported
unit tests
CLI tools
future server utilities
```

A Core API may accept an injected portable interface when a deterministic service cannot reasonably be implemented identically on every target. Such interfaces must use platform-neutral data types.

---

## 13. `core-document`

### Purpose

Defines Rastrio's canonical portable document representation.

Owns:

```text
ThermalDocument
DocumentMetadata
DocumentLayout / LayoutIntent
DocumentBlock
InlineContent
AssetReference
Alignment
Orientation
Dimension / Length
.td serialization model
.td validation
```

This module should be extremely stable.

It knows nothing about:

```text
printers
DPI
ESC/POS
Bluetooth
USB
code pages
transport chunking
Compose
```

### Required design properties

- immutable data models by default;
- explicit schema versioning;
- stable node discriminators;
- deterministic validation;
- no I/O hidden inside domain constructors;
- no network access;
- no platform file APIs.

---

## 14. `core-markdown`

### Purpose

Compile GitHub Flavored Markdown into `ThermalDocument`.

Pipeline:

```text
Markdown String
      ↓
GFM Parser
      ↓
Markdown AST
      ↓
Rastrio Markdown Compiler
      ↓
ThermalDocument + Diagnostics
```

Initial required GFM constructs:

- paragraphs;
- headings;
- strong/bold;
- emphasis;
- strikethrough;
- inline code;
- fenced code blocks;
- ordered lists;
- unordered lists;
- task lists;
- block quotes;
- thematic separators;
- links;
- images;
- tables.

### Mandatory boundaries

The Markdown compiler must not know:

```text
printer width
printer DPI
ESC/POS
Bluetooth
USB
physical segmentation
cutter behavior
```

The Markdown compiler must not perform network I/O or open files referenced by Markdown.

For example:

```markdown
![logo](https://example.com/logo.png)
```

must compile to an external `AssetReference` or diagnostic state. A higher-level asset resolver decides whether the asset is fetched, imported, embedded, rejected, or left unresolved.

Raw HTML must never execute code.

For v1, raw or unsupported HTML is handled as follows:

1. the Markdown compiler MUST NOT create an executable or renderable HTML node;
2. the original significant HTML source text MUST be preserved as literal document text where the parser exposes it safely;
3. the compiler MUST emit a structured warning such as `MD101 Unsupported raw HTML`;
4. HTML tags, attributes, URLs, scripts, styles, event handlers, and embedded markup MUST NOT be interpreted or executed by Core or preview;
5. unsupported HTML MUST NOT silently disappear;
6. if the selected Markdown parser cannot safely preserve significant source text for a construct, compilation MUST return a structured diagnostic rather than silently dropping that construct.

`.td` v1 therefore requires no `RawHtml` block or inline node. The compiled `ThermalDocument` contains ordinary semantic text; compilation diagnostics are returned alongside the document and are not persisted in `.td` v1 unless a future schema explicitly defines persisted diagnostics.

`docs/TD_SPEC.md` and the Markdown/compiler tests must preserve this policy.

---

## 15. `core-templates`

### Purpose

Convert structured template state directly into `ThermalDocument`.

Examples:

```text
ShoppingListState
      ↓
ShoppingListDocumentBuilder
      ↓
ThermalDocument
```

```text
MemoState
      ↓
MemoDocumentBuilder
      ↓
ThermalDocument
```

Initial templates:

- Todo / Checklist
- Shopping List
- Quick Note
- Memo
- QR Message

Image Print is a specialized image workflow rather than a normal text template.

Template implementations must not generate Markdown as an unnecessary intermediary.

---

## 16. `core-text`

### Purpose

Own portable text measurement, shaping contracts, native-font geometry models, raster-text preparation contracts, and text-related deterministic behavior required by layout and printer preparation.

This module exists to prevent text measurement and raster fallback logic from being duplicated across layout, preview, Compose UI, and printer encoding.

### Owns conceptual types such as

```text
TextStyle
TextMetrics
TypographyContext
FontMetrics
GlyphCoverage
TextMeasurement
TextLine
TextRun
ShapedText
RasterTextRequest
TextRenderResult
```

Exact names may change.

### Text-rendering contract

Rastrio must explicitly distinguish:

1. **Native printer text metrics** — geometry supplied or derived from `PrinterProfile`.
2. **Raster text metrics** — geometry produced by the Rastrio text renderer and its selected font resources.
3. **Preview representation** — user-visible rendering of already-resolved text operations.

For complex scripts, combining characters, emoji, and similar cases, text rendering may require shaping rather than one-character-per-cell assumptions.

The implementation must not silently treat Unicode code points as independent printer cells where that would produce invalid output.

### Platform-neutral service boundary

If the actual glyph rasterizer or shaping implementation requires platform-specific backing code, Core may define a narrow injected interface using portable inputs and outputs.

For example:

```kotlin
interface TextRasterizer {
    fun rasterize(request: RasterTextRequest): RasterTextResult
}
```

No Android `Paint`, Compose `TextLayoutResult`, Skia handle, browser canvas object, or equivalent platform type may cross the Core boundary.

### Reproducibility requirement

Raster text preparation must record enough information to make the `PreparedPrint` immutable after preparation. Preview and the active trusted protocol encoder must never invoke text shaping or raster generation again; for v1 this applies to `core-escpos`.

---

## 17. `core-layout`

### Purpose

Convert `ThermalDocument` into deterministic protocol-independent logical geometry under explicit `LayoutConstraints`.

Correct pipeline:

```text
ThermalDocument
      +
LayoutConstraints
      ↓
LogicalLayoutEngine
      ↓
LogicalDocument
```

This replaces the incorrect assumption that all line wrapping can be both printer-independent and independent of resolved canvas width or text metrics.

### `LayoutConstraints`

Conceptually:

```kotlin
data class LayoutConstraints(
    val canvasWidth: Length,
    val orientation: Orientation,
    val typography: TypographyContext,
)
```

The real model may include additional constraints as implementation requires.

`LayoutConstraints` are runtime inputs. They are not persisted as physical printer state inside `.td`.

### Responsibilities

`core-layout` owns:

```text
block flow
paragraph flow
semantic vertical spacing
line breaking
list indentation
alignment
tables
code layout
image placement
QR placeholder placement
logical portrait layout
logical landscape layout
```

### Mandatory boundary

`core-layout` may know:

```text
canvas width
logical units
typography metrics
orientation
layout intent
```

It must not know:

```text
ESC/POS commands
Bluetooth
USB
cutter command bytes
RFCOMM
USB endpoints
printer protocol serialization
```

A `LogicalDocument` is therefore **protocol-independent**, but it is not necessarily independent of the selected target's resolved layout constraints.

### Logical-preview mode

A logical preview may be generated without a physical printer by using an explicit preview preset or document-defined logical width.

The caller must choose the preset; `core-layout` must not hide an arbitrary printer width constant.

---

## 18. `core-raster`

### Purpose

Provide deterministic portable image-processing and monochrome raster utilities.

Pipeline:

```text
DecodedImage
     ↓
Orientation normalization
     ↓
Crop
     ↓
Resize
     ↓
Grayscale
     ↓
Brightness / Contrast / Gamma
     ↓
Threshold / Dither
     ↓
1-bit Raster
```

Initial algorithms:

```text
Threshold
Bayer ordered dithering
Atkinson dithering
Floyd–Steinberg dithering
```

### Portable image representation

Core must operate on a platform-neutral decoded representation, for example conceptually:

```kotlin
data class RgbaImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)
```

The exact memory representation may change for performance reasons.

### Decode boundary

Image decoding is not assumed to be implemented by `core-raster`.

The canonical boundary is:

```text
PNG / JPEG / WebP / platform image source
        ↓
ImageDecodeService
        ↓
portable DecodedImage
        ↓
core-raster
```

Platform image types must not leak into Core.

### Raster requirements

- deterministic output for deterministic inputs;
- width not divisible by 8 must be handled correctly;
- bit order must be explicitly specified;
- padding bits must be deterministic;
- large output must support bands/tiles;
- no giant print-job-wide raster allocation requirement.

---

## 19. `core-profile`

### Purpose

Represent printer capabilities, protocol strategies, known constraints, and validated user-overridable printer characteristics.

Owns:

```text
PrinterProfile
.tcfg parsing
.tcfg validation
profile overrides
capability definitions
protocol dialect definitions
known quirk identifiers
```

Potential capabilities include:

```text
DPI
printable width
native fonts
font geometry
native styles
character/code pages
raster methods
native QR
native barcode
cutter support
printer buffer guidance
preferred raster band size
status-query support
known quirks
```

### Protocol strategy requirement

Printer behavior must be data-driven through validated known strategies.

For example, a profile may declare concepts equivalent to:

```text
protocol family: ESC/POS
raster strategy: GS v 0
QR strategy: native model 2
cut strategy: Epson-compatible full/partial
```

The exact schema belongs in `docs/TCFG_SPEC.md`.

For `.tcfg` schema v1, the only normative protocol family is `escpos`.

The profile's top-level `protocol` block is the protocol-family boundary. A future non-ESC/POS printer language requires an explicit profile-schema/specification extension and a trusted protocol encoder. It MUST NOT be represented by pretending to be ESC/POS or by importing arbitrary byte-generating logic.

The architecture SHOULD preserve this evolution path:

```text
PreparedPrint
    ├──→ core-escpos
    └──→ future protocol encoder
```

without prematurely introducing a generic protocol module before a real second protocol family exists.

### Security rule

Imported `.tcfg` profiles must not contain arbitrary executable code or unrestricted arbitrary raw byte templates that are emitted directly to the printer.

Profiles may select from known validated protocol strategies and validated parameters.

Any future raw-command escape hatch must be explicitly designed, isolated, visibly dangerous, and outside the normal trusted profile format.

---

## 20. `core-printer`

### Purpose

Resolve document intent, layout constraints, printer capabilities, and print options into an authoritative physical print plan.

This module owns physical printer decision making.

It also owns the portable `PreparedPrint` domain model and prepared-operation types consumed by `core-preview` and `core-escpos`.

`core-printer` MUST NOT depend on `core-preview` or `core-escpos`.

### Canonical preparation pipeline

```text
ThermalDocument
      +
PrinterProfile
      +
PrintOptions
      +
required render services
      ↓
PreparationEngine
      ↓
resolve LayoutConstraints
      ↓
core-layout
      ↓
LogicalDocument
      ↓
physical strategy resolution
      ↓
PreparedPrint
```

This orchestration allows portrait wrapping and native-font geometry to depend on the selected target without making `.td` printer-specific or allowing the ESC/POS encoder to perform layout.

### Responsibilities

`core-printer` owns decisions including:

```text
physical dimensions
dot conversion
layout-constraint resolution
native vs raster text strategy
code-page selection
Unicode fallback strategy
image sizing
image raster resolution
QR native/raster selection
barcode native/raster selection
landscape physical segmentation
segment overlap
registration marks
segment numbering
manual cut guides
automatic cut operations
printer-specific diagnostics
protocol strategy selection
```

### Non-responsibilities

`core-printer` does not:

```text
open Bluetooth sockets
claim USB interfaces
request Android permissions
show UI
write bytes to hardware
```

---

## 21. `core-preview`

### Purpose

Generate portable preview representations from Core output.

Two preview modes exist.

### 21.1 Logical Preview

```text
LogicalDocument
      ↓
LogicalPreview
```

Shows the assembled logical document under the selected logical layout constraints.

This is especially useful for authoring, desktop development, and landscape documents.

### 21.2 Physical Preview

```text
PreparedPrint
      ↓
PrintPreview
```

Shows what Rastrio expects the selected printer to produce geometrically and operationally.

### Accuracy contract

For raster operations, preview should represent the exact raster data that will be encoded.

For native printer text, Rastrio guarantees authoritative:

- wrapping;
- line placement;
- alignment;
- cell/operation geometry;
- style intent;
- cut/feed/QR/image placement.

Exact native glyph shape is only guaranteed where the `PrinterProfile` provides an exact font model. Otherwise the preview uses a representative rendering while preserving resolved geometry.

This distinction must be visible in `docs/PREVIEW_SPEC.md`.

### Preview representation

`PrintPreview` should be a platform-neutral model or tile/scene representation, not a Compose-specific object.

Shared Compose UI renders the result.

Long documents must support incremental/lazy preview rendering rather than requiring one enormous bitmap.

---

## 22. `core-escpos`

### Purpose

Serialize `PreparedPrint` into ESC/POS-compatible command bytes or chunks.

Pipeline:

```text
PreparedPrint
      ↓
EscPosEncoder
      ↓
Encoded byte chunks / stream
```

### The encoder may

- map already-selected protocol operations to bytes;
- emit initialization commands;
- serialize already-selected fonts/styles/code pages;
- serialize already-prepared raster bands;
- serialize already-selected QR operations;
- serialize feed/cut operations;
- frame chunks for the logical protocol stream.

### The encoder must not

```text
re-layout text
re-wrap lines
re-shape text
choose a code page
re-dither images
resize images
recalculate tables
change QR dimensions
choose native/raster strategy
re-slice landscape output
change overlap
introduce new cut decisions
```

If an encoding decision can alter physical output, that decision belongs in `PreparedPrint` before encoding begins.

`core-escpos` must fail with a structured diagnostic if it encounters an unsupported or internally inconsistent prepared operation.

---

## 23. Shared Application Module

The generated `shared/` module owns shared application behavior and shared Compose Multiplatform UI.

It may depend on `core-*`.

Core modules must never depend on `shared`.

The shared module contains:

```text
application/session state
workflow orchestration
navigation
shared platform-service contracts
Compose screens
Compose components
adaptive UI behavior
```

The same shared UI code is the default implementation for Android, Desktop, and Web.

---

## 24. Shared Presentation

Presentation coordinates application workflows. It does not implement printer algorithms.

Potential state models include:

```text
DocumentSession
MarkdownEditorState
TemplateEditorState
PrinterSelectionState
PrintConfigurationState
PreviewState
PrintJobState
RecentDocumentsState
```

Potential controllers/use cases include:

```text
DocumentController
AssetController
PreviewController
PrinterController
PrintController
```

Presentation is responsible for orchestration such as:

```text
user changes Markdown
    ↓
compile ThermalDocument
    ↓
request logical preview
```

or:

```text
user selects printer
    ↓
load PrinterProfile
    ↓
prepare PreparedPrint
    ↓
request physical preview
```

Presentation must not duplicate:

- layout logic;
- raster algorithms;
- native/raster strategy selection;
- ESC/POS encoding;
- printer capability validation.

---

## 25. Shared Compose Multiplatform UI

Initial shared screens:

```text
RastrioApp
HomeScreen
MarkdownScreen
TemplateScreen
ImagePrintScreen
PreviewScreen
PrinterScreen
PrinterProfileScreen
PrinterLabScreen
SettingsScreen
```

Potential reusable components:

```text
PaperPreview
PhysicalStripPreview
PrinterSelector
DiagnosticBanner
TemplateCard
DitheringSelector
PrintOptionsPanel
PrintProgress
```

### Shared-UI rule

Android-specific, Desktop-specific, or Web-specific copies of a screen are forbidden unless a concrete platform requirement justifies them.

Preferred model:

```text
shared Composable
    +
platform service contract
    +
platform implementation
```

instead of:

```text
AndroidScreen
DesktopScreen
WebScreen
```

### Adaptive UI

Phone:

```text
Editor / Form
      ↓
Preview
      ↓
Print
```

Tablet/Desktop:

```text
┌──────────────────────┬──────────────────────┐
│ Editor / Form        │ Preview              │
│                      │                      │
└──────────────────────┴──────────────────────┘
```

Web should reuse the same responsive concepts while respecting browser-specific limitations.

---

## 26. Platform Service Contracts

Shared Presentation/UI may require operating-system functionality.

Small interfaces should be introduced only when there is a real consumer.

Potential contracts:

```kotlin
interface FileService
interface ClipboardService
interface ShareService
interface PlatformStorage
interface ImageDecodeService
interface ExternalAssetResolver
interface PrinterDiscoveryService
interface PrinterTransport
```

Rules:

1. Do not create abstractions solely for hypothetical future platforms.
2. Interfaces must use platform-neutral types.
3. Permission prompts belong to platform implementations.
4. Core parsing and layout must never directly call these services.
5. Network access must be explicit and policy-controlled.

---

## 27. `androidApp`

Android is the primary production application.

Responsibilities:

```text
Android entry point
Compose host
Android lifecycle
Storage Access Framework / file picker
runtime permissions
clipboard integration
share intents
Android storage adapters
image decode adapter
external asset resolution policy
Bluetooth printer discovery
Bluetooth printer transport
USB printer discovery
USB printer transport
PrinterInstance persistence adapter
```

Suggested structure:

```text
androidApp/src/main/.../
├── platform/
│   ├── AndroidFileService.kt
│   ├── AndroidClipboardService.kt
│   ├── AndroidShareService.kt
│   ├── AndroidImageDecodeService.kt
│   └── AndroidPlatformServices.kt
│
└── printer/
    ├── bluetooth/
    │   ├── BluetoothDiscovery.kt
    │   ├── BluetoothPrinterTransport.kt
    │   └── BluetoothPermissions.kt
    │
    └── usb/
        ├── UsbDiscovery.kt
        ├── UsbPrinterTransport.kt
        └── UsbPermissions.kt
```

Android transport implementations belong in `androidApp`.

---

## 28. `desktopApp`

Desktop/JVM initially exists primarily for:

```text
fast shared UI development
Markdown testing
template development
preview development
printer-profile editing
debugging
fixture generation where appropriate
ESC/POS export testing
```

Windows and Linux are the main intended desktop platforms.

macOS may be added later without changing Core architecture.

Early Desktop hardware transport is not required.

Later Desktop functionality may include:

```text
filesystem integration
clipboard
ESC/POS file export
serial printing
USB printing
Bluetooth printing where practical
```

Desktop hardware support should be introduced only when platform libraries and demand justify it.

---

## 29. `webApp`

Web is a later product target.

Initially it should:

```text
remain structurally buildable where current tooling permits
launch the shared UI during development when practical
avoid forcing Core architecture to fork
not block Android releases due solely to Web/Beta tooling limitations
```

Later responsibilities may include:

```text
browser file APIs
browser storage
clipboard
WebUSB
WebSerial
browser permission handling
```

Web hardware features must use feature detection and explicit user-facing capability messages.

Rastrio must not assume that WebUSB or WebSerial exists in every browser.

---

## 30. Transport Architecture

There is no requirement for a global platform-implementation `transport/` Gradle module.

Each platform owns its transport implementation.

Conceptually:

```text
shared:
    PrinterTransport contract

androidApp:
    BluetoothPrinterTransport
    UsbPrinterTransport

desktopApp:
    DesktopPrinterTransport, when needed

webApp:
    WebUsbPrinterTransport
    WebSerialPrinterTransport
```

### Transport knows

```text
connection state
byte chunks
write operations
flush where meaningful
disconnect
transport errors
bytes accepted/transmitted according to its contract
```

### Transport must not know

```text
Markdown
ThermalDocument
tables
image semantics
QR semantics
layout
dithering
code-page selection
printer capability resolution
```

---

## 31. Printer Buffering vs Transport Buffering

Printer limits and transport limits are separate concerns.

### PrinterProfile may describe

```text
recommended raster band height
known printer buffer limitations
printer-side pacing guidance
```

### Transport/session configuration may describe

```text
safe write chunk size
transport-specific buffering
connection pacing
write timeout
```

The effective transmission policy is the safe intersection of printer constraints and transport constraints.

A `PreparedPrint` should describe physical printer operations without becoming tied to Bluetooth or USB write chunk sizes.

Changing transport must not require re-layout or re-dithering the document.

---

## 32. Canonical End-to-End Pipelines

### 32.1 Markdown authoring

```text
Markdown
    ↓
GFM Parser
    ↓
Markdown AST
    ↓
Rastrio Markdown Compiler
    ↓
ThermalDocument
```

### 32.2 Template authoring

```text
Compose Form
    ↓
Template State
    ↓
core-templates
    ↓
ThermalDocument
```

### 32.3 Logical preview without selected printer

```text
ThermalDocument
      +
Logical Preview Preset / document width intent
      ↓
LayoutConstraints
      ↓
core-layout
      ↓
LogicalDocument
      ↓
core-preview
      ↓
LogicalPreview
```

### 32.4 Authoritative physical preparation

```text
ThermalDocument
      +
PrinterProfile
      +
PrintOptions
      +
render services
      ↓
core-printer
      ↓
resolve LayoutConstraints
      ↓
core-layout
      ↓
LogicalDocument
      ↓
physical operation planning
      ↓
PreparedPrint
```

### 32.5 Physical preview and printing

```text
                    PreparedPrint
                    /           \
                   /             \
                  ▼               ▼
           core-preview      core-escpos
                  │               │
                  ▼               ▼
            PrintPreview       Byte Stream
                  │               │
                  ▼               ▼
          Shared Compose UI   Platform Transport
                                  │
                                  ▼
                               Printer
```

### 32.6 Image pipeline

```text
Image source
    ↓
ImageDecodeService
    ↓
DecodedImage
    ↓
core-raster
    ↓
processed image intent/raster input
    ↓
core-printer
    ↓
PreparedPrint raster operation(s)
```

---

## 33. Preview Ownership

The preview contract must remain unambiguous.

### Who requests preview?

Shared Presentation.

### Who creates logical and physical preview models?

`core-preview`.

### Who creates printer-specific geometry?

`core-printer` and `core-layout` through the preparation pipeline.

### Who stores current preview state?

Shared Presentation.

### Who displays it?

Shared Compose UI.

The platform application must not recreate printer layout.

---

## 34. Preview / Print Consistency Contract

Physical preview and physical print consume exactly the same `PreparedPrint` instance or an equivalent immutable value snapshot.

```text
PreparedPrint
   ├──→ PrintPreview
   └──→ trusted protocol encoder
            (`core-escpos` for v1)
            ↓
         Byte Stream
```

No alternate physical layout implementation is permitted.

Changing an output-affecting option must create a new preparation result.

The UI must never manually patch an existing physical preview.

---

## 35. Print Adjustment Loop

Examples:

```text
Atkinson
→ Floyd–Steinberg

3 mm overlap
→ 5 mm overlap

Auto text mode
→ Prefer raster
```

Any change that can alter physical output triggers:

```text
new PrintOptions
      ↓
PreparationEngine
      ↓
new LogicalDocument when required
      ↓
new PreparedPrint
      ↓
new PrintPreview
```

A purely transport-level change such as Bluetooth write chunk size does not require re-preparing physical geometry.

---

## 36. `.td` Purpose

`.td` is Rastrio's portable semantic document format.

It describes:

```text
document content
document semantics
limited author layout intent
asset references / embedded assets
optional source material
```

It does not describe:

```text
ESC/POS bytes
Bluetooth
USB
printer width in dots
printer code-page selection
transport chunking
cutter command sequences
physical segmentation generated for a specific printer
```

---

## 37. `.td` Physical Representation

Version 1 uses a ZIP-compatible container.

Example:

```text
example.td
│
├── manifest.json
├── document.json
│
├── assets/
│   ├── image-001.png
│   └── logo.webp
│
└── source/
    └── original.md
```

`source/` is optional.

`document.json` is authoritative for the portable document semantics.

Source content must not be silently embedded merely because the document was originally created from Markdown. The save workflow should make source retention an explicit policy or documented default.

---

## 38. `.td` Manifest and Versioning

Version fields must have unambiguous meanings.

Recommended conceptual manifest:

```json
{
  "format": "rastrio-td",
  "containerVersion": 1,
  "documentSchemaVersion": 1,
  "encoding": "json"
}
```

Application version, archive container version, and document schema version are independent.

Migration rules belong in `docs/TD_SPEC.md`.

Unknown future versions must not be silently interpreted as v1.

---

## 39. `.td` Serialization

Version 1 uses:

```text
UTF-8 JSON
kotlinx.serialization-compatible models
explicit stable node discriminators
strict validation
```

The `.td` specification is authoritative over the behavior of any JSON library.

A conforming reader MUST reject input that `docs/TD_SPEC.md` defines as invalid even if the selected parser library accepts it. This includes, where prohibited by the format specification:

```text
duplicate object keys
unknown properties
unsupported polymorphic discriminators
non-standard or lenient JSON token forms
invalid control characters
non-finite or otherwise invalid numeric representations
excessive nesting / size / token counts
```

If the selected serialization library does not enforce a required rule directly, Rastrio MUST add a bounded pre-validation, parser wrapper, or post-parse structural validation layer. Library leniency MUST NOT silently weaken the portable format contract.

Do not create a custom Rastrio text DSL.

A compact binary encoding may be considered later only if measured storage or performance needs justify it.

---

## 40. `.td` Asset Metadata

Embedded assets should have stable metadata sufficient for validation and deterministic handling.

Conceptually:

```text
asset id
archive path
media type
byte size
optional content hash
```

A SHA-256 hash is recommended for integrity checks, deterministic fixture verification, and future deduplication/caching. It is not a substitute for archive safety checks.

External asset references must never be fetched implicitly by Core parsing.

---

## 41. `.td` Archive Security

Archive safety is a Phase 1 requirement, not release-cleanup work.

The first `.td` reader must protect against:

```text
path traversal
absolute archive paths
normalized duplicate paths
ZIP bombs
unbounded expansion
unbounded entry count
oversized JSON
oversized individual assets
invalid/corrupt archives
unsupported versions
malformed node graphs
```

`docs/SECURITY.md` and `docs/RESOURCE_LIMITS.md` must define concrete limits before `.td` loading is considered complete.

No archive entry may escape the logical document container.

---

## 42. `ThermalDocument`

Conceptually:

```kotlin
data class ThermalDocument(
    val schemaVersion: Int,
    val metadata: DocumentMetadata,
    val layout: DocumentLayout,
    val blocks: List<DocumentBlock>,
)
```

Exact representation may change.

The model must remain semantic rather than protocol-oriented.

---

## 43. Initial Block Types

Version 1:

```text
Paragraph
Heading
UnorderedList
OrderedList
Checklist
Quote
CodeBlock
Separator
Image
Table
QrCode
```

Barcode may be added later without redesigning the entire model.

---

## 44. Initial Inline Types

Version 1:

```text
Text
Strong
Emphasis
Strike
InlineCode
Link
LineBreak
```

These describe author intent.

For example:

```text
Strong
```

means semantic emphasis, not a hard-coded ESC/POS bold command.

---

## 45. `.td` Layout Scope

`.td` intentionally provides limited layout control.

Initial author-controlled concepts:

```text
alignment
orientation
optional maximum logical width
semantic formatting
explicit line breaks
separators
image sizing intent
```

Do not implement a general CSS-like layout system.

Avoid until a demonstrated product requirement exists:

```text
arbitrary box-model margins
arbitrary padding
absolute x/y positioning
z-index
floating layout
complex CSS grids
```

---

## 46. Alignment

Supported block-level alignment:

```text
left
center
right
```

Applicable where appropriate to:

```text
paragraphs
headings
images
QR codes
table cells
```

---

## 47. Orientation and Logical Width

Supported orientation:

```text
portrait
landscape
```

### Portrait

Physical preparation normally derives the logical canvas width from the selected printer's printable width and the resolved typography context.

### Landscape

Landscape may declare a wider author-intended logical canvas.

Example:

```json
{
  "layout": {
    "orientation": "landscape",
    "maxWidth": {
      "value": 180,
      "unit": "mm"
    }
  }
}
```

Meaning:

> Lay this document out on a logical canvas up to 180 mm wide.

It does not mean the selected printer uses 180 mm paper.

---

## 48. Landscape Workflow

Wide content must be laid out before physical segmentation.

Correct:

```text
Markdown Table
      ↓
ThermalDocument
      ↓
LayoutConstraints: 180 mm logical canvas
      ↓
LogicalDocument
      ↓
Printer Resolver
      ↓
48 mm physical strips
```

Incorrect:

```text
Table
  ↓
split semantic content first
  ↓
independently lay out each strip
```

The correct approach preserves:

```text
row heights
column geometry
wrapping
image positioning
alignment
```

Wide-document tiling is an advanced feature and is not required to block the first Android stable release.

---

## 49. `.tcfg` Purpose and Format

`.tcfg` describes portable printer capabilities and protocol strategy.

Version 1 normative representation is UTF-8 JSON.

Rationale:

- it uses the same stable serialization approach as `.td`;
- it is fully portable across the intended KMP targets;
- it avoids adding a second parser family to the security surface;
- it is still sufficiently human-readable for community-maintained profiles.

YAML may be added later as an import/export convenience format if there is a demonstrated need. It is not part of the normative v1 profile representation.

As with `.td`, the `.tcfg` specification is authoritative over the behavior of the selected JSON library. Parser leniency MUST NOT cause Rastrio to accept duplicate keys, unknown fields, unsupported identifiers, non-standard JSON forms, or other input rejected by `docs/TCFG_SPEC.md`. Required strictness MUST be enforced with bounded validation even when a dependency is more permissive.

Potential v1 fields include:

```text
profile identity
display name
profile schema version
DPI
printable width
native font capabilities
font geometry
native styles
character/code pages
raster support
raster strategies
QR support
barcode support
cutter support
printer buffer guidance
recommended raster band size
status-query support
protocol dialect / strategy identifiers
known quirks
```

The complete schema lives in `docs/TCFG_SPEC.md`.

---

## 50. `PrinterProfile` vs `PrinterInstance`

These are distinct concepts.

### `PrinterProfile`

Describes a printer model or capability set.

Properties:

```text
portable
shareable
importable/exportable
contains no paired-device identity
contains no user-private transport identity
```

### `PrinterInstance`

Describes a physical user printer.

The portable application-level `PrinterInstance` model belongs to `shared`, not to `core-profile`.

It may contain platform-neutral persisted values such as:

```text
application-local instance id
display name
transport kind
opaque persisted transport locator / device identity
associated PrinterProfile id/revision
validated user overrides
transport/session preferences
last-used information
```

The `PrinterInstance` model MUST NOT contain Android Bluetooth objects, USB device objects, browser handles, desktop serial-port objects, open sockets, file descriptors, or other live platform types.

Platform application modules own:

```text
device discovery
permission handling
conversion between persisted transport locator and platform device/session objects
transport connection lifecycle
platform persistence adapter implementation
```

`shared` owns the application-level model and workflows that associate a physical printer with a profile. Platform persistence adapters store and restore that model using platform-appropriate local storage.

A `.tcfg` file must not contain personal paired-device identifiers.

`PrinterInstance` is local application state and MUST NOT be serialized as `.tcfg`.

---

## 51. Printer Profile Overrides

Users may need to correct inaccurate or unusual hardware behavior without modifying the base profile.

Override rules must be explicit and deterministic.

Conceptually:

```text
base PrinterProfile
      ↓
validated user overrides
      ↓
EffectivePrinterProfile
```

Overrides must not bypass schema validation or protocol safety rules.

The UI should expose only relevant and understandable overrides by default. Highly technical settings belong in Printer Lab or advanced settings.

---

## 52. `PrintOptions`

`PrintOptions` represents user-selected settings for the current physical preparation.

Potential options:

```text
dithering algorithm
brightness
contrast
gamma
density
text strategy
image scaling strategy
landscape overlap
registration marks
strip numbers
manual cut guides
native/raster preference
QR strategy preference
```

Only options meaningful for the selected printer and current document should be shown.

Changing a `PrintOptions` value that can affect physical output invalidates the existing `PreparedPrint`.

---

## 53. `PreparedPrint`

`PreparedPrint` is the authoritative immutable printer-specific physical output plan.

Conceptually:

```kotlin
data class PreparedPrint(
    val logicalDocument: LogicalDocument,
    val target: PreparedTarget,
    val segments: List<PrintSegment>,
    val diagnostics: List<Diagnostic>,
    val availableAdjustments: AvailableAdjustments,
)
```

Exact representation may change.

### `PreparedPrint` completeness rule

If changing a value can change what physically comes out of the printer, that decision must already be represented in `PreparedPrint` before `core-escpos` runs.

Potential physical operations:

```text
NativeText
RasterText
RasterImage
NativeQr
RasterQr
NativeBarcode
RasterBarcode
Separator
Feed
RegistrationMark
ManualCutGuide
AutomaticCut
```

### Example native text operation

Conceptually, a resolved native text operation may include:

```text
text
resolved x/y geometry
selected printer font
selected code page
bold/underline state
width/height scale
alignment already resolved
```

The ESC/POS encoder serializes those decisions. It does not make them.

### Example raster text/image operation

A raster operation must already contain or reference the final monochrome raster geometry and band information required for encoding.

The encoder must not invoke image or text rendering.

### Bounded immutable prepared data

`PreparedPrint` immutability does not require all prepared raster bytes to live in one in-memory collection.

For large jobs, a prepared raster operation MAY reference an immutable prepared artifact represented through a portable Core contract and backed by:

```text
bounded in-memory bands
application-controlled temporary/spool storage
another deterministic immutable chunk store
```

The following rules are mandatory:

1. preparation finalizes raster content before `PreparedPrint` becomes `READY`;
2. the prepared artifact has stable identity, dimensions, ordering, and content for the lifetime of that `PreparedPrint`;
3. consumers receive read-only access;
4. `core-preview` and `core-escpos` consume the same finalized prepared content or equivalent immutable snapshot;
5. neither consumer may re-run shaping, image scaling, dithering, segmentation, or any other output-affecting preparation step;
6. artifact storage MUST NOT expose platform file objects through portable Core APIs;
7. temporary/spooled artifacts are application-controlled resources with bounded size, explicit cleanup, cancellation handling, and no attacker-controlled filesystem path semantics;
8. failure to retain/read finalized prepared data invalidates that print preparation; downstream code MUST NOT silently regenerate different output.

The exact storage implementation may vary by platform and resource policy. The semantic result must remain equivalent to an immutable sequence of prepared operations and raster bands.

---

## 54. Native vs Raster Text

Native printer text should be used when the selected profile can reliably represent the content and requested style under the resolved layout contract.

Raster fallback is used where required, including potentially:

```text
unsupported Unicode
unsupported style
complex-script shaping
custom glyph requirements
profile-declared unreliable native rendering
```

### Initial simplification

For the initial Unicode fallback implementation:

> If a resolved physical line requires raster text because native rendering cannot represent it reliably, rasterize the complete physical line.

Per-glyph native/raster mixing is deferred.

### Important geometry rule

Raster fallback must not silently change wrapping after `PreparedPrint` is finalized.

If fallback metrics alter layout, the preparation stage must re-resolve the affected layout before producing the final immutable `PreparedPrint`.

---

## 55. QR Codes

`.td` stores semantic QR content such as:

```text
payload
error-correction preference
requested logical size
alignment
```

The printer engine decides:

```text
native QR
or
raster QR
```

based on validated profile capabilities and current print options.

The chosen strategy, dimensions, and error-correction parameters must be represented in `PreparedPrint`.

---

## 56. Cutter Support

Profiles declare cutter capability.

Example conceptual profile data:

```json
{
  "cutter": {
    "supported": true,
    "modes": ["full", "partial"]
  }
}
```

For cutter-equipped printers:

```text
PreparedPrint
→ AutomaticCut
```

For printers without a cutter, Rastrio may add:

```text
PreparedPrint
→ ManualCutGuide
```

A manual cut guide is printed content.

An automatic cut boundary is a preview annotation representing a physical cutter operation and must not accidentally become printed text or raster content.

---

## 57. Raster Streaming

Large raster output must support bounded-memory preparation and encoding.

Do not construct one giant print-job `ByteArray`.

Conceptually:

```text
large raster
    ↓
Band 1
Band 2
Band 3
...
```

Printer profile may influence safe raster-band height.

Prepared raster bands are finalized during preparation and may be retained through the bounded immutable prepared-artifact mechanism defined for `PreparedPrint`.

Transport may further split encoded bytes according to transport/session limits.

Raster bands and transport write chunks are not the same concept.

---

## 58. Print Job Lifecycle

Use an explicit state machine.

Baseline lifecycle:

```text
CREATED
   ↓
PREPARING
   ↓
READY
   ↓
CONNECTING
   ↓
PRINTING
   ↓
COMPLETED
```

Alternative terminal states:

```text
FAILED
CANCELLED
```

`COMPLETED` means Rastrio successfully transmitted the intended bytes according to the transport contract.

Rastrio must not claim that paper physically printed successfully unless the printer protocol provides reliable confirmation and the profile/transport supports it.

### Partial-transmission semantics

A print failure may occur after some bytes have already reached the printer.

Failure metadata should therefore be able to represent concepts such as:

```text
bytesAttempted
bytesTransmitted
outputMayHaveOccurred
failureStage
```

Exact fields may change.

### Retry safety invariant

Rastrio must not automatically retry a print job when physical output may already have partially occurred.

The UI may offer a user-controlled retry after clearly communicating that duplicate or partial output is possible.

---

## 59. Diagnostics

Major Core stages should return structured diagnostics rather than silently dropping significant content.

Conceptually:

```kotlin
data class Diagnostic(
    val code: String,
    val severity: Severity,
    val message: String,
    val sourceLocation: SourceLocation?,
)
```

Potential code families:

```text
TDxxx   document/archive
MDxxx   Markdown
TXTxxx  text/shaping/rendering
LAYxxx  layout
IMGxxx  image/raster
PRFxxx  profile
PRNxxx  printer preparation
PRVxxx  preview-owned failures
ESCxxx  ESC/POS encoding
TRNxxx  transport/session
```

Examples:

```text
TD101 Invalid asset reference
TD120 Archive resource limit exceeded
MD101 Unsupported raw HTML
MD201 Markdown image unresolved
TXT201 Complex text raster fallback
LAY201 Content exceeds requested logical width
PRN301 Glyph raster fallback
PRN401 QR raster fallback
PRN502 Document split into four segments
IMG201 Image decode failed
ESC101 Unsupported prepared operation
TRN301 Connection lost after partial transmission
```

Warnings appear in UI where relevant.

Fatal diagnostics prevent the affected operation.

Diagnostic messages must avoid embedding private printable content unless explicitly required for local user display.

---

## 60. Offline and Privacy Requirements

Core document creation, preview, and printing must work offline.

No account is required.

Rastrio must not log printable content by default.

Avoid logging:

```text
Markdown source
notes
QR payloads
addresses
images
document bodies
file contents
tokens
Bluetooth names when unnecessary
```

Logging may include non-content metadata such as:

```text
diagnostic codes
profile IDs
resolved dimensions
operation types
band sizes
byte counts
connection state
```

Logs intended for bug reports should provide an explicit redaction/export path.

---

## 61. External Asset and Network Policy

Core parsing never performs network I/O.

External Markdown assets such as HTTP(S) images are resolved only by an explicit higher-level service and policy.

Default behavior should favor privacy and predictability:

1. show that the asset is external or unresolved;
2. require an explicit user action or documented setting before network retrieval;
3. validate media type and resource limits;
4. decode through the platform image service;
5. embed the resulting asset into `.td` when the user chooses to save a self-contained document, subject to save policy.

A print operation must not unexpectedly depend on a remote asset that has not been resolved.

---

## 62. Security Requirements

Security-sensitive inputs include:

```text
.td archives
.tcfg profiles
Markdown with external references
imported images
printer identities
Bluetooth / USB connections
browser hardware APIs
```

Mandatory principles:

- untrusted files are parsed with explicit limits;
- path traversal is rejected;
- archive expansion is bounded;
- imported profiles cannot inject arbitrary command programs;
- unsupported schema versions fail safely;
- network access is explicit;
- raw printable content is not logged by default;
- permission requests are scoped and explained;
- printer transport input is not treated as trusted application code.

`docs/SECURITY.md` must document the threat model for portable file formats and hardware transports.

---

## 63. Resource and Performance Requirements

Thermal documents may be extremely long. Performance design must therefore avoid assumptions suitable only for page-sized documents.

### Required bounded-resource behavior

Rastrio must avoid requiring:

```text
one giant receipt bitmap
one giant print-job ByteArray
unbounded ZIP expansion
unbounded decoded images
unbounded preview scene allocation
```

### Preview

Long previews should support virtualization, tiling, incremental composition, or equivalent bounded-memory rendering.

For example, the implementation must not require a single:

```text
576 × 100000 ARGB bitmap
```

merely to display a long receipt.

### Images

Imported images must be validated against maximum pixel count and memory budget before or during decode.

### Documents

Very large Markdown or `.td` documents may produce warnings or reject operations when explicit resource limits are exceeded.

Concrete limits live in `docs/RESOURCE_LIMITS.md` and must be covered by tests.

---

## 64. Shared UI Requirements

The Home screen should emphasize what users want to print rather than printer engineering.

Conceptually:

```text
Rastrio

Markdown
Write or open Markdown

Quick Print

[ Note ]        [ Todo ]
[ Shopping ]    [ QR ]
[ Image ]       [ Memo ]

Recent
─────────────────────────
README.md
Weekend Shopping

Printer
Helett H50i            ●
```

Advanced engineering controls belong in Printer Lab or advanced settings.

### Markdown screen

Eventually supports:

```text
write Markdown
open .md
save source
compile diagnostics
logical preview
physical preview
print
```

On larger screens:

```text
┌──────────────────────┬──────────────────────┐
│ Markdown Editor      │ Preview              │
│                      │                      │
└──────────────────────┴──────────────────────┘
```

### Accessibility

Shared UI must support reasonable accessibility semantics, scalable text where practical, keyboard navigation on desktop, and touch targets appropriate for Android.

---

## 65. Printer Lab

Printer Lab is an advanced shared UI for testing and calibrating printer behavior.

Potential capabilities:

```text
assign profile
import/export .tcfg
profile overrides
width calibration
font tests
character-set tests
raster tests
QR tests
cutter tests
chunk stress tests
transport diagnostics
```

Printer Lab must use the same Core preparation and protocol layers as normal printing wherever possible. It must not become a parallel hidden printer engine.

---

## 66. Testing Philosophy

Testing is part of feature development, not a cleanup phase.

Critical functionality must not be implemented and left untested for a later milestone.

Every practical bug fix should include a regression test.

Core transformation tests should prefer exact deterministic assertions where possible.

Hardware-facing behavior requires a combination of automated fakes and permanently documented physical tests.

---

## 67. Unit Tests

Unit tests are required for deterministic pure functionality including:

```text
schema validation
dimensions and unit conversion
alignment calculations
layout constraints
capability selection
text coverage decisions
image algorithms
profile overrides
tiling geometry
cut decisions
transport-policy intersection
state transitions
resource-limit validation
```

---

## 68. Golden Tests

Golden tests are required for transformations where exact output is an important contract.

Examples:

```text
Markdown
→ ThermalDocument

ThermalDocument + LayoutConstraints
→ LogicalDocument

ThermalDocument + PrinterProfile + PrintOptions
→ PreparedPrint

PreparedPrint
→ ESC/POS bytes
```

Expected outputs should live under `test-fixtures/` using stable, reviewable representations.

Golden updates must be intentional. A test command must not silently rewrite expected fixtures during normal CI.

---

## 69. Raster Golden Tests

Every raster algorithm requires deterministic golden output.

Fixtures must include:

```text
all black
all white
50% gray
gradient
checkerboard
photograph-like test image
odd dimensions
width not divisible by 8
full printer width
very long narrow content
```

Tests must cover:

```text
threshold
Bayer
Atkinson
Floyd–Steinberg
bit packing
padding bits
band splitting
row continuity
```

---

## 70. Text Tests

Text tests must cover, as capabilities are implemented:

```text
ASCII
native code-page content
unsupported Latin characters
combining characters
mixed native/unsupported content
Indic script
emoji policy
complex-script shaping
long unbroken tokens
explicit line breaks
native/raster fallback geometry
```

When raster text is implemented, representative font resources and renderer versions used by deterministic tests must be controlled.

---

## 71. `.td` Tests

Tests must cover:

```text
encode
decode
round trip
every block type
every inline type
assets
asset metadata
optional source
portrait layout intent
landscape layout intent
invalid ZIP
corrupt document
missing manifest
missing document.json
invalid format identifier
unsupported container version
unsupported document schema version
unknown node
path traversal
absolute path
normalized duplicate path
ZIP bomb/resource limit
oversized entry count
oversized asset
oversized JSON
```

Fuzzing malformed archives is strongly recommended once the reader exists.

---

## 72. `.tcfg` Tests

Tests must cover:

```text
encode/decode
profile validation
invalid printable width
invalid DPI
invalid font geometry
capability combinations
code pages
raster strategies
QR configuration
cutter configuration
protocol-strategy validation
override precedence
unknown top-level and nested properties rejected
attempted unsafe/raw command injection
resource limits
```

---

## 73. Property-Based and Invariant Tests

Property-based tests should be added where geometric invariants are more valuable than a handful of examples.

Examples:

```text
packed row size = ceil(width / 8)
segment coverage never loses logical coordinates
overlap is never negative
no duplicate content outside intentional overlap
unit conversions preserve expected tolerances
validated profile overrides cannot create impossible dimensions
archive paths never normalize outside the container
```

---

## 74. Shared Compose UI Tests

Use Compose Multiplatform testing where practical.

Critical shared flows include:

```text
Home → Markdown
Home → Template
Markdown editing
compile diagnostics
logical preview request
printer selection
physical preview request
print option changes
print confirmation
print-job progress
failure display
partial-output warning
```

Platform-specific integration tests supplement common UI tests where OS APIs are involved.

---

## 75. Transport Tests

Every transport implementation should support automated testing through fakes or adapters for:

```text
connect
write
chunking
timeout
flush where meaningful
failure
disconnect
cancellation
partial transmission
state transitions
```

Transport tests must verify that a failure after partial transmission is surfaced distinctly enough for the application to avoid unsafe automatic retry.

---

## 76. Hardware Tests

Manual hardware tests must be permanently numbered and stored in:

```text
docs/HARDWARE_TESTS.md
```

The H50i is the currently available physical reference device for development and hardware validation. It has no architectural privilege and does not define generic ESC/POS defaults.

Every hardware-facing feature should add or update relevant tests.

Hardware tests should record:

```text
test ID
printer profile/version
app commit or release
transport
input fixture
expected physical result
observed result
notes/quirks
```

---

## 77. Build and CI Policy

Repository validation should provide a clear top-level command, eventually:

```bash
./gradlew verifyRastrio
```

`verifyRastrio` should aggregate the checks considered mandatory for the current development phase.

`./gradlew check` should remain useful, but the project must not assume that the default Gradle lifecycle automatically executes every target-specific browser, device, integration, or hardware test.

### Before the dedicated Web phase

Mandatory CI gates:

```text
Core/common tests
Android compilation and automated tests
shared compilation/tests
Desktop/JVM compilation/tests
format/lint/static checks adopted by the project
```

Web checks should run where practical, but a Web-only tooling or dependency limitation must not automatically block Android delivery if Core portability remains intact.

### During and after the dedicated Web phase

Promote the agreed Web application build/test suite to a mandatory gate.

### Hardware tests

Hardware tests are manual or device-lab release gates unless a reliable automated rig is later introduced.

---

## 78. Build System and Toolchain Policy

Rastrio uses:

```text
Gradle Wrapper
Kotlin DSL
Version Catalog
Kotlin Multiplatform
Compose Multiplatform
Gradle convention plugins
```

Build files should use `.gradle.kts`.

Current tool versions belong in:

```text
gradle/libs.versions.toml
Gradle wrapper properties
```

The PRD should not hard-code rapidly changing plugin versions as architectural requirements.

The repository must document the currently supported:

```text
JDK
Gradle
Kotlin
Android Gradle Plugin
Compose Multiplatform
Android compileSdk
Android minSdk
targetSdk policy
```

in a short developer setup section or dedicated toolchain document.

Shared KMP library modules should use the current officially supported Android/KMP integration for the selected AGP version. Android application entry-point code remains in the separate `androidApp` module.

---

## 79. Dependency and FOSS Policy

Dependencies required for official builds must be reviewed for:

```text
license compatibility
maintenance status
KMP target support
F-Droid compatibility
security history where relevant
size/build impact
```

Official Android functionality must not require Google Play Services or another proprietary runtime service unless the project explicitly revises its FOSS distribution goals.

Optional integrations, if ever added, must not make proprietary services mandatory for basic document creation, preview, or printing.

A dependency license audit is required before stable releases.

---

# Implementation Plan

The phases below define implementation order and exit criteria. They are not a promise that every phase must be completed before an earlier usable release can be published.

The first priority is proving the core architecture with simple text on Android Bluetooth before expanding into image, Unicode, USB, tiling, Desktop hardware, or Web hardware.

---

## 80. Phase 0 — Repository and Architecture Foundation

### Objective

Prepare the generated KMP project for disciplined multi-module development.

### Implement

Preserve application entry modules:

```text
androidApp
desktopApp
webApp
shared
```

Create Core modules:

```text
core/document
core/markdown
core/templates
core/text
core/layout
core/raster
core/profile
core/printer
core/preview
core/escpos
```

Create:

```text
build-logic/
docs/
test-fixtures/
AGENTS.md
PRD.md
README.md
```

Introduce:

```text
version catalog
Kotlin DSL only
convention plugins where useful
basic CI
```

Record the selected supported development toolchain in the repository developer setup documentation, including:

```text
JDK
Gradle Wrapper
Kotlin
Android Gradle Plugin
Compose Multiplatform
Android compileSdk
Android minSdk
targetSdk policy
```

Rapidly changing version numbers belong in Gradle configuration/version catalogs and developer setup documentation, not as permanent architectural requirements in this PRD.

Implement minimal shared:

```text
RastrioApp()
HomeScreen
```

Ensure Android hosts the shared Compose UI.

Desktop should launch the shared shell.

Web should compile/launch if supported by the current selected toolchain without disproportionate work.

### Tests / verification

Verify:

```text
all Core modules compile
shared compiles
androidApp compiles
desktopApp compiles
Core has no Android dependency
Core has no Compose dependency
core-preview depends downstream toward core-printer
core-escpos depends downstream toward core-printer
core-printer does not depend on core-preview or core-escpos
basic architecture-boundary checks exist
```

### Exit criteria

- repository-root mandatory verification passes;
- Android displays the shared Rastrio shell;
- Desktop displays the same shared shell;
- module dependency direction is documented.

---

## 81. Phase 1 — `.td` Document Model and Safe Archive

### Objective

Define Rastrio's most important portable contract.

### Implement

Domain models:

```text
ThermalDocument
DocumentMetadata
DocumentLayout / LayoutIntent
DocumentBlock
InlineContent
Orientation
Alignment
Dimension / Length
AssetReference
AssetMetadata
```

Blocks:

```text
Paragraph
Heading
UnorderedList
OrderedList
Checklist
Quote
CodeBlock
Separator
Image
Table
QrCode
```

Inline nodes:

```text
Text
Strong
Emphasis
Strike
InlineCode
Link
LineBreak
```

Implement safe `.td` archive creation and loading.

Implement:

```text
manifest version semantics
strict JSON conformance independent of parser-library leniency
strict schema validation
asset path normalization
archive resource limits
source retention policy
```

Create/update:

```text
docs/TD_SPEC.md
docs/SECURITY.md
docs/RESOURCE_LIMITS.md
```

### Mandatory tests

```text
minimal document
all blocks
all inline types
metadata
portrait
landscape
max logical width
embedded image metadata
optional source
invalid archive
invalid format
unsupported versions
missing document
unknown block
duplicate JSON key
unknown JSON property
non-standard/lenient JSON form rejected
path traversal
absolute path
normalized duplicate path
ZIP bomb/resource limit
round-trip equality
```

### Exit criteria

A `.td` can be safely created, serialized, reopened, validated, and compared using portable Core code.

---

## 82. Phase 2 — GitHub Flavored Markdown Compiler

### Objective

Implement the original Rastrio use case at semantic level.

### Implement

```text
Markdown
→ GFM AST
→ ThermalDocument
```

Support the initial Markdown feature set.

External assets compile to references/diagnostics without hidden network I/O.

Raw/unsupported HTML compiles to literal text plus a structured warning under the v1 policy defined in this PRD. It never executes and never silently disappears.

### Golden fixtures

```text
paragraph.md
headings.md
formatting.md
unordered-list.md
ordered-list.md
nested-list.md
task-list.md
blockquote.md
code.md
links.md
image.md
external-image.md
table.md
unicode.md
raw-html.md
mixed-document.md
```

Each verifies exact expected `ThermalDocument` output and diagnostics.

### Exit criteria

A realistic GitHub README compiles deterministically without printer knowledge or network access.

---

## 83. Phase 3 — Text and Layout Foundation

### Objective

Establish deterministic text/layout ownership before physical printer adaptation.

### Implement

Define:

```text
TypographyContext
native font metrics model
text measurement contracts
LayoutConstraints
LogicalDocument
LogicalLayoutEngine
```

Implement initial layout support for:

```text
paragraphs
headings
alignment
lists
checklists
quotes
code
tables
image placeholders
QR placeholders
portrait
landscape logical width
```

The first version may use simplified text behavior sufficient for ASCII/native printer metrics, provided the later Unicode/shaping extension point is real and tested.

### Tests

Golden and unit tests for:

```text
wrapping
left/center/right alignment
long paragraphs
nested lists
checklists
code blocks
normal tables
wide tables
explicit line breaks
empty documents
long unbroken text
portrait width changes
same document under different LayoutConstraints
```

### Exit criteria

`ThermalDocument + LayoutConstraints` deterministically produces `LogicalDocument`, with no ESC/POS dependency.

---

## 84. Phase 4 — Shared Logical Preview and Authoring UI

### Objective

Expose document compilation and logical layout through the shared UI.

### Implement

```text
LogicalDocument
→ LogicalPreview
```

Shared screens:

```text
HomeScreen
MarkdownScreen
PreviewScreen
```

Android becomes the primary working application.

Desktop becomes the preferred rapid UI development target where convenient.

### Tests

```text
Home → Markdown
enter Markdown
compile
show diagnostics
open logical preview
navigate back
retain expected state
```

### Exit criteria

Android and Desktop can enter Markdown and display the same shared logical-preview UI.

---

## 85. Phase 5 — Printer Profiles and Protocol Strategies

### Objective

Introduce hardware capabilities without sending data to a printer.

### Implement

`.tcfg` v1 as UTF-8 JSON.

Create:

```text
H50i development profile / evidence worksheet
synthetic narrow profile
synthetic 80 mm profile
```

The H50i development profile MUST be clearly marked unverified until each output-affecting capability is supported by recorded documentation, byte-level evidence where applicable, and/or physical hardware evidence. A manufacturer/model name, nominal paper width, third-party application behavior, or generic Epson command reference is not sufficient evidence for printable dot width, font metrics, code pages, raster strategy, QR/cutter/status behavior, buffering, or pacing.

Implement strict `.tcfg` JSON/schema validation and capability validation for:

```text
printable width
DPI
native fonts
font metrics
code pages
raster capability
QR capability
cutter capability
protocol strategies
buffer guidance
```

### Tests

```text
round trip
invalid width
invalid DPI
invalid font geometry
code pages
raster strategy
QR strategy
cutter
protocol validation
duplicate JSON key rejected
unknown JSON field rejected
non-standard/lenient JSON form rejected
override precedence
unsafe profile fields rejected
```

Add a test ensuring generic Core contains no H50i width or command constants.

### Exit criteria

One document can be prepared against multiple validated synthetic profile capability sets without source-code changes.

The H50i profile may remain development-only at this phase. It becomes a maintained production profile only after the capabilities used by the supported Android printing path are hardware-validated and recorded in `docs/ESC_POS_NOTES.md` / `docs/HARDWARE_TESTS.md`.

---

## 86. Phase 6 — Basic Printer Preparation

### Objective

Produce a complete printer-specific physical plan for basic text.

### Implement

Canonical preparation:

```text
ThermalDocument
+
PrinterProfile
+
PrintOptions
→
LayoutConstraints
→
LogicalDocument
→
PreparedPrint
```

Initial operations:

```text
NativeText
Separator
Feed
ManualCutGuide
AutomaticCut
```

Implement:

```text
printer-width resolution
dot conversion
native font selection
code-page selection for supported text
basic style resolution
cut decisions
```

### Tests

```text
different printable widths change wrapping
native text selection
code-page selection
unsupported glyph detection
cutter supported
cutter absent
manual guide
automatic cut
different profiles produce different PreparedPrint
PreparedPrint is immutable
```

### Exit criteria

Basic physical output can be completely represented by `PreparedPrint` without ESC/POS encoding.

---

## 87. Phase 7 — Authoritative Physical Preview

### Objective

Implement printer-specific preview before hardware output.

### Implement

```text
PreparedPrint
→ core-preview
→ PrintPreview
```

Shared Compose UI displays:

```text
paper width
resolved wrapping
native-text geometry
separator
feeds where visually meaningful
manual cut guide
automatic cut boundary annotation
diagnostics
```

### Contract tests

Verify:

```text
preview consumes PreparedPrint only
preview uses PreparedPrint geometry
manual cut guide matches prepared operation
auto-cut annotation is not printed content
same PreparedPrint produces deterministic preview
PreparedPrint remains immutable
```

### Exit criteria

Android can preview actual printer-specific text geometry before Bluetooth is implemented.

---

## 88. Phase 8 — ESC/POS Encoder and Debug Transport

### Objective

Develop protocol serialization independently from physical Bluetooth.

### Implement

ESC/POS support for prepared operations:

```text
initialize
native text
selected code page
alignment/state required by prepared operation
basic font selection
bold
underline
feed
cut
```

Implement development transports/sinks where appropriate:

```text
FakePrinterTransport
DebugFilePrinterTransport
InMemoryByteSink
```

### Golden tests

Exact byte-level output for:

```text
plain text
multiple lines
code-page switching where required
alignment
bold
underline
feed
full cut
partial cut where supported
```

Encoder tests must prove it does not re-layout or choose a different text strategy.

### Exit criteria

A `PreparedPrint` can be serialized and inspected without physical hardware.

---

## 89. Phase 9 — Android Bluetooth Printing

### Objective

Complete the first end-to-end physical print.

### Implement in `androidApp`

```text
Bluetooth permissions
paired-device selection
RFCOMM/SPP connection
BluetoothPrinterTransport
PrinterInstance persistence
print-job lifecycle
partial-transmission reporting
safe retry UX
```

### Automated tests

```text
connection lifecycle
write
chunking
printer/transport policy intersection
failure
disconnect
cancellation
partial transmission
state transitions
no automatic retry after ambiguous output
```

Use fakes where hardware cannot be automated.

### Hardware tests

```text
HW-001 Simple ASCII
HW-002 Line wrapping
HW-003 Alignment
HW-004 Bold/underline
HW-005 Long text
HW-006 Disconnect during job / recovery procedure
```

### Exit criteria — First Major Milestone

Android can perform:

```text
Markdown
→ ThermalDocument
→ resolved layout
→ PreparedPrint
→ PrintPreview
→ ESC/POS
→ Bluetooth
→ H50i (currently available validation device)
```

and produce physical text output whose wrapping, alignment, primary geometry, and cut behavior match the authoritative preview contract.

This concrete milestone uses the H50i because it is the available physical test device; passing it does not establish generic compatibility with all ESC/POS printers.

---

## 90. Phase 10 — Raster Engine and Image Printing

### Objective

Support deterministic thermal image printing.

### Implement

Full raster pipeline:

```text
Decode
→ normalize orientation
→ crop
→ resize
→ grayscale
→ brightness / contrast / gamma
→ threshold / dither
→ 1-bit raster
→ raster bands
```

Add:

```text
Markdown image resolution after explicit asset resolution
Image Print UI
PreparedPrint raster operations
ESC/POS raster output
physical raster preview
```

### Mandatory golden tests

Every dithering algorithm.

Also test:

```text
odd width
width not divisible by 8
very narrow image
full printer width
long image
band splitting
bit packing
padding bits
row continuity
resource limits
```

### Hardware tests

```text
HW-007 Checkerboard
HW-008 Threshold gradient
HW-009 Bayer gradient
HW-010 Atkinson gradient
HW-011 Floyd–Steinberg gradient
HW-012 Full-width image
HW-013 Long image
```

### Exit criteria

Image preview uses the exact raster plan encoded for the printer and closely reflects physical output within hardware limitations.

---

## 91. Phase 11 — Unicode and Raster Text Fallback

### Objective

Allow reliable printing of text unsupported by printer-native code pages.

### Implement

Before Phase 11 output goldens are treated as stable, the project MUST deliberately select and record in `docs/TEXT_RENDERING_SPEC.md`:

```text
canonical tab expansion policy
Unicode line-break implementation/version
project-controlled raster fallback font resources and versions
emoji support/failure policy
paragraph/list/code spacing defaults
supported bidirectional/RTL scope for the stable release
shaping/raster backend(s) used by supported targets
which backend/platform combinations are expected to be pixel-identical
```

These choices are implementation/specification decisions rather than permanent PRD-level library mandates, but they MUST NOT be inherited accidentally from platform defaults.

Full Unicode bidirectional paragraph layout is NOT required merely to ship the first stable Android release. If a verified raster shaping/layout path supports a particular RTL/bidirectional case, Rastrio MAY support it. Otherwise unsupported significant RTL/bidirectional content MUST produce a structured diagnostic rather than be reversed, corrupted, or silently dropped.

Emoji is supported only where the controlled raster font/backend policy can produce a deterministic printable monochrome result. Unsupported emoji grapheme clusters MUST produce a structured diagnostic; silent substitution is forbidden.

Implement:

```text
glyph/code-page capability detection
text shaping/rasterization service
whole-line raster fallback
PreparedPrint RasterText
physical raster-text preview
```

Initial rule:

> If any content on a resolved physical line cannot be reliably represented natively, the complete physical line may be rasterized.

If fallback changes text metrics, preparation must re-resolve layout before finalizing `PreparedPrint`.

### Tests

```text
ASCII/native content
CP437-compatible content
unsupported Latin
combining marks
Indic script
mixed supported/unsupported text
emoji policy
complex-script layout
line geometry
fallback-induced re-layout
```

### Hardware tests

```text
HW-014 Unicode raster text
HW-015 Mixed supported/unsupported text
HW-016 Complex-script sample
```

### Exit criteria

Unsupported text is either printed through deterministic raster fallback or rejected with an explicit diagnostic; it is never silently corrupted or dropped.

---

## 92. Phase 12 — Templates

### Objective

Provide useful workflows to non-Markdown users.

### Implement shared UI and Core builders for

```text
Todo
Shopping List
Quick Note
Memo
QR Message
```

Flow:

```text
Compose Form
     ↓
Template State
     ↓
core-templates
     ↓
ThermalDocument
```

### Tests

Every template requires deterministic:

```text
State
→ ThermalDocument
```

Shared UI tests include:

```text
add item
remove item
quantity editing
todo checked state
memo entry
QR entry
preview
state restoration where applicable
```

### Exit criteria

All initial templates function without requiring Markdown.

---

## 93. Phase 13 — QR Capability Resolution

### Objective

Support printer-native QR where useful with raster fallback.

### Implement

Strategies:

```text
Auto
Native
Raster
```

Native QR may be selected only when the profile marks the selected configuration reliable.

### Tests

```text
native-capable profile
no native capability
Auto strategy
forced raster
unsupported native size
payload validation
error-correction settings
preview dimensions
encoder receives already-selected strategy
```

### Hardware tests

```text
HW-017 Native QR
HW-018 Raster QR
HW-019 QR size variations
```

---

## 94. Phase 14 — Printer Management and Printer Lab

### Objective

Allow additional ESC/POS-compatible printers to be configured without source changes.

### Implement shared UI

```text
Printers
Printer Profiles
Printer Lab
```

Android integration:

```text
paired Bluetooth device selection
PrinterInstance persistence
```

Features:

```text
assign profile
import .tcfg
export .tcfg
profile overrides
width calibration
font tests
character-set tests
raster tests
QR tests
cutter tests
chunk stress test
```

### Tests

```text
profile import/export
PrinterProfile vs PrinterInstance
override precedence
invalid override
profile duplication
unsafe profile rejected
Printer Lab document generation
persistence
```

---

## 95. Phase 15 — Android UX and Release Polish

### Objective

Turn the working Android stack into a reliable daily-use application.

### Implement

```text
Recent documents
Saved .td files
Open Markdown
Open .td
explicit source-retention choices
Share-to-print
Clipboard printing
Improved image controls
Printer status where reliably available
Adaptive UI
Loading states
Error states
partial-output warnings
Animations where useful
Accessibility
Dark theme
Light theme
```

Shared Compose implementations remain the default.

Android-specific screen copies require a documented platform reason.

### Release-quality requirements

```text
permission audit
privacy review
resource-limit review
migration tests
archive security review
dependency/license audit
F-Droid packaging review
complete mandatory hardware regression suite
```

### Exit criteria — Android Stable Candidate

The application satisfies the Android stable-release requirements defined later in this PRD.

---

## 96. Phase 16 — Android USB

### Objective

Add wired printing without changing document or printer-preparation semantics.

### Implement in `androidApp`

```text
USB Host discovery
permission flow
interface/endpoint selection
UsbPrinterTransport
USB PrinterInstance support
transport-specific buffering/error handling
```

No semantic changes should be required to:

```text
ThermalDocument
LogicalDocument
PreparedPrint
core-preview
```

`core-escpos` should require changes only if actual printer protocol support changes—not merely because the byte stream is delivered over USB.

### Exit criteria

The same physical plan can be sent through Bluetooth or USB by selecting a different compatible transport/session.

---

## 97. Phase 17 — Wide-Document Landscape Tiling

### Objective

Print wide logical content such as large Markdown tables as multiple physical strips.

This is an advanced feature and is not a blocker for the first Android stable release unless the project explicitly promotes it to release-critical status.

### Implement

```text
Wide LogicalDocument
→ physical PrintSegments
```

Support:

```text
logical max width
horizontal segmentation
overlap
registration marks
segment numbers
trim lines
join guides
manual cut
automatic cut
```

Preview modes:

```text
Assembled
Physical strips
```

### Mandatory tests

```text
180 mm logical width on narrow printer
same document on wider printer
segment count
coverage
overlap
coordinate continuity
no lost content
no duplicate content outside intended overlap
table row alignment
registration marks
segment numbering
manual cut behavior
auto-cut behavior
```

Add a reconstruction/property test proving segmented coverage corresponds to the intended logical canvas.

### Hardware tests

```text
HW-020 Two-strip table
HW-021 Three-strip table
HW-022 Registration marks
HW-023 Physical assembly
```

### Exit criteria

A wide table can be printed as multiple strips and manually assembled with consistent geometry.

---

## 98. Phase 18 — Desktop Product Expansion

### Objective

Turn Desktop from primarily a development target into an optional useful application.

Potential functionality:

```text
Markdown editor
templates
.td documents
preview
printer profile editor
ESC/POS file export
desktop printer transport if justified
```

Windows and Linux are the first Desktop product targets.

macOS packaging may be added later.

Desktop hardware printing is not required for the Android stable release.

---

## 99. Phase 19 — Web Product Expansion

### Objective

Enable meaningful browser use while preserving Core architecture.

Reuse:

```text
Core
Presentation
Shared Compose UI
```

Implement inside `webApp`:

```text
browser file APIs
browser storage
clipboard
explicit external-asset retrieval policy
WebUSB where available
WebSerial where available
browser permission handling
feature detection
```

Validate carefully:

```text
Markdown editor behavior
keyboard shortcuts
large-document scrolling
preview virtualization/performance
image loading
clipboard
file handling
hardware API availability
permission failures
```

During this phase, the agreed Web build/test suite becomes a mandatory CI gate.

Web constraints must not cause Core document or printer models to fork.

---

## 100. First Major Milestone

The first major Rastrio milestone is:

> An Android user can open or write a GitHub Flavored Markdown document, select a configured Bluetooth thermal printer, view an authoritative physical preview generated from the same immutable `PreparedPrint` used for encoding, press Print, and receive text output whose wrapping, alignment, dimensions, and primary geometry match the preview contract.

This milestone requires:

```text
safe .td model
Markdown compiler
layout constraints
basic text/layout engine
printer profile
PreparedPrint
physical preview
ESC/POS encoder
Android Bluetooth transport
```

It does not require:

```text
images
Unicode raster fallback
templates
USB
wide landscape tiling
Desktop hardware printing
Web hardware printing
```

---

## 101. First Stable Android Release Requirements

The first stable Android release should provide a reliable daily-use core rather than requiring every advanced roadmap feature.

Required:

```text
GitHub Flavored Markdown authoring/opening
.td save/open with safe archive handling
shared KMP Core
shared Compose Multiplatform UI
printer-specific physical preview
Bluetooth Classic printing
thermal image printing
threshold/Bayer/Atkinson/Floyd–Steinberg
Unicode raster fallback
Todo template
Shopping template
Quick Note
Memo
QR Message
printer profiles
PrinterInstance persistence
manual cut guides
automatic cut where available
Printer Lab basic calibration/tests
offline functionality
no mandatory account
F-Droid-compatible distribution
privacy/resource/security review
hardware regression suite
```

Recommended but not release-blocking unless promoted by project decision:

```text
Android USB
wide-document landscape tiling
advanced status queries
Desktop hardware printing
Web hardware printing
```

This distinction exists to prevent a complex advanced feature from indefinitely delaying a reliable Android release.

---

## 102. Release Hardening

Before a stable Android release:

```text
dependency audit
Apache-2.0 compatibility audit
FOSS/F-Droid compatibility audit
permission audit
privacy review
offline-operation review
release signing process
F-Droid metadata
migration tests
.td security regression suite
.tcfg validation/security regression suite
ZIP-bomb protections verified
path traversal protections verified
resource limits verified
complete mandatory hardware regression suite
crash/error-path review
```

The stable release must not depend on untested manual profile hacks for the currently available reference device or for any maintained production printer profile.

---

## 103. Definition of Done

A feature is complete only when:

1. implementation exists;
2. appropriate automated tests exist;
3. existing mandatory tests pass;
4. diagnostics/error behavior are defined;
5. relevant specifications are updated;
6. architectural boundaries remain intact;
7. output-affecting behavior is represented in `PreparedPrint`;
8. physical-output changes are represented by physical preview;
9. hardware-facing functionality has a numbered hardware test where practical;
10. shared behavior is not unnecessarily duplicated in platform apps;
11. platform implementation details do not leak into portable Core APIs;
12. security/resource implications are addressed for untrusted input;
13. accessibility implications are addressed for significant UI changes;
14. no hidden network requirement is introduced into offline workflows;
15. migration/compatibility impact is handled for persistent formats.

A feature is not complete merely because it compiles.

---

## 104. Engineering Rules for Codex and Human Contributors

`AGENTS.md` must establish and maintain these rules.

1. `core-*` modules must not depend on Android APIs.
2. `core-*` modules must not depend on Compose.
3. Shared UI uses Compose Multiplatform by default.
4. Shared Presentation coordinates Core; it does not duplicate Core algorithms.
5. Platform apps own genuine OS integrations.
6. Android Bluetooth and USB implementations live inside `androidApp`.
7. Desktop transports live inside `desktopApp` when implemented.
8. Web hardware integrations live inside `webApp`.
9. `.td` remains printer-independent.
10. `core-layout` owns logical geometry under explicit `LayoutConstraints`.
11. `LogicalDocument` is protocol-independent but may be target-constrained.
12. `core-text` owns shared text metrics/rendering contracts.
13. `core-printer` owns printer-specific preparation and strategy decisions.
14. `core-preview` derives physical preview from `PreparedPrint`.
15. Shared Compose UI displays Core preview output rather than recreating printer layout.
16. `core-escpos` serializes `PreparedPrint` and must not make layout decisions.
17. Physical preview and the active trusted protocol encoder consume the same `PreparedPrint` value and finalized prepared-artifact content.
18. Code-page selection must be resolved before the active protocol encoder receives the plan; for v1, before `core-escpos` serialization.
19. Raster/native strategy must be resolved before the active protocol encoder receives the plan; for v1, before `core-escpos` serialization.
20. Printer properties come from `PrinterProfile` or validated overrides.
21. H50i characteristics must not be hard-coded into generic Core code.
22. Imported `.tcfg` files may not inject arbitrary raw command programs.
23. Printer buffering and transport write chunking are separate concerns.
24. Changing transport must not require re-layout or re-dithering.
25. `.td` readers enforce security/resource limits from the first implementation.
26. Core Markdown parsing never performs hidden network I/O.
27. External assets require explicit higher-level resolution policy.
28. New `.td` features require `TD_SPEC.md` updates.
29. New `.tcfg` fields require `TCFG_SPEC.md` updates.
30. Text-rendering contract changes require `TEXT_RENDERING_SPEC.md` updates.
31. Preview contract changes require `PREVIEW_SPEC.md` updates.
32. Critical transformations require automated tests.
33. Raster algorithms require deterministic golden tests.
34. ESC/POS features require byte-level golden tests where practical.
35. Every practical bug fix requires a regression test.
36. Unsupported significant content produces diagnostics rather than silently disappearing.
37. Printable user content must not be logged by default.
38. Template UI compiles directly to `ThermalDocument`.
39. Do not introduce speculative abstractions solely for future platforms.
40. Android is the implementation and release priority.
41. Desktop is encouraged as the rapid shared-UI development target.
42. Web must not block Android before its dedicated implementation phase solely because of Web-specific tooling limitations.
43. Platform-specific types must not leak across portable Core APIs.
44. Prefer immutable domain models.
45. Prefer deterministic pure transformations in Core.
46. Preserve `.td` and `.tcfg` compatibility or provide an explicit migration path.
47. Do not automatically retry an ambiguously partially transmitted print job.
48. Long documents and images must respect bounded-memory/resource design.
49. Shared screens must not be duplicated per platform without a concrete requirement.
50. Official builds must remain compatible with the project's FOSS/F-Droid goals.
51. `PreparedPrint` and prepared-operation types are owned by `core-printer`; `core-preview` and `core-escpos` are downstream consumers.
52. `PrinterInstance` is shared application state, not a `.tcfg`/Core profile model; live platform device/session objects stay in platform applications.
53. Portable JSON format rules are authoritative over parser-library leniency.
54. Raw/unsupported Markdown HTML is literal text plus a diagnostic in v1 and never executes.
55. H50i output-affecting capabilities must not be promoted from unverified assumptions into the maintained production profile.
56. Finalized prepared raster content may be stored in bounded immutable/spooled artifacts, but preview and encoding must consume that finalized content without re-rendering.

---

## 105. Required Technical Documentation

### `docs/ARCHITECTURE.md`

Defines:

```text
module responsibilities
dependency direction
Core/shared/platform boundaries
canonical data flow
preparation orchestration
service injection boundaries
```

### `docs/TD_SPEC.md`

Defines:

```text
.td package format
manifest
JSON schema
document blocks
inline nodes
assets
versioning
migration
layout intent
source retention
security rules
```

### `docs/TCFG_SPEC.md`

Defines:

```text
.tcfg JSON schema
PrinterProfile
capabilities
protocol strategies
quirks
validation
overrides
security restrictions
```

### `docs/TEXT_RENDERING_SPEC.md`

Defines:

```text
text measurement ownership
native font metrics
code-page coverage
complex-script policy
raster fallback
font resources
shaping/rasterization service contracts
determinism expectations
preview limitations for native printer fonts
```

### `docs/PREVIEW_SPEC.md`

Defines:

```text
LogicalDocument
PreparedPrint
LogicalPreview
PrintPreview
physical segments
native-text preview accuracy contract
raster preview exactness
cut annotations
preview/print consistency contract
```

### `docs/TESTING.md`

Defines:

```text
unit tests
golden tests
property-based tests
fuzz tests
raster fixtures
text fixtures
UI tests
integration tests
transport tests
hardware tests
regression policy
fixture-update policy
```

### `docs/SECURITY.md`

Defines:

```text
threat model
.td handling
.tcfg handling
archive safety
profile safety
external assets
hardware permissions
privacy/logging rules
```

### `docs/RESOURCE_LIMITS.md`

Defines concrete limits for:

```text
archive size
expanded archive size
entry count
JSON size/depth
asset size
image pixels
preview tiles
raster bands
large-document warnings
```

### `docs/HARDWARE_TESTS.md`

Contains numbered physical test procedures and results.

### `docs/ESC_POS_NOTES.md`

Contains tested protocol observations, command compatibility notes, printer quirks, and evidence supporting profile decisions.

---

## 106. Architectural Invariants

These invariants are non-negotiable unless the architecture is intentionally revised and the PRD/specifications are updated.

### Invariant A — `.td` describes documents, not printers

```text
.td
≠
ESC/POS
```

### Invariant B — Layout inputs are explicit

```text
ThermalDocument + LayoutConstraints
→ LogicalDocument
```

No hidden global printer width or UI text measurement may determine Core layout.

### Invariant C — `LogicalDocument` is protocol-independent

It may be laid out for target-specific constraints, but it contains no Bluetooth, USB, or ESC/POS command semantics.

### Invariant D — One authoritative physical plan

```text
PreparedPrint
   ├──→ PrintPreview
   └──→ trusted protocol encoder
            (`core-escpos` for v1)
```

### Invariant E — Encoder does not reinterpret output

`core-escpos` is the v1 trusted protocol encoder and serializes decisions already represented in `PreparedPrint`. Any future trusted protocol encoder must obey the same no-reinterpretation rule.

### Invariant F — Hardware capabilities are data-driven

```text
PrinterProfile
```

not printer-specific constants scattered through generic code.

### Invariant G — Compose is UI, not domain architecture

Core remains usable without Compose.

### Invariant H — Platform transports stay on their platform

```text
Android Bluetooth → androidApp
Android USB       → androidApp
WebUSB/WebSerial  → webApp
Desktop transport → desktopApp
```

### Invariant I — Printer buffering is not transport buffering

Physical raster-band planning and connection write chunking remain separate responsibilities.

### Invariant J — External resources are explicit

Core parsing never silently accesses files or the network.

### Invariant K — Untrusted portable files are bounded

`.td` and `.tcfg` parsing enforce security and resource limits from their first implementation.

### Invariant L — Partial physical output is treated conservatively

Ambiguous partial transmission must not trigger automatic print retry.

### Invariant M — Shared UI is the default

Platform-specific copies of ordinary screens require a concrete platform justification.

### Invariant N — Android comes first

Future platform work must not delay the first reliable Android printer workflow.

### Invariant O — Prepared-plan dependency direction is downstream

`core-printer` owns `PreparedPrint`.

```text
core-preview → core-printer
core-escpos  → core-printer
```

`core-printer` does not depend on preview or any protocol encoder, including `core-escpos`.

### Invariant P — Local printer identity is application state

`PrinterInstance` belongs to shared application state and may persist platform-neutral transport identity values. Live Bluetooth, USB, browser, serial, socket, endpoint, permission, and device objects remain platform-owned.

### Invariant Q — Parser libraries do not define portable-format validity

`.td` and `.tcfg` accept exactly the syntax and schema their normative specifications permit. A permissive dependency parser must be constrained by Rastrio validation rather than silently widening the accepted format.

### Invariant R — Large prepared output remains immutable without requiring monolithic memory

Final prepared raster content may be backed by bounded chunks or controlled spool storage. Once preparation completes, preview and encoding only read the finalized prepared content; they do not recreate it.

### Invariant S — Reference-printer behavior is evidence-gated

H50i behavior is not a generic ESC/POS assumption. Output-affecting capabilities enter the maintained profile only with documented evidence appropriate to the claim.

---

## 107. Final Architectural Model

Rastrio can be summarized as:

```text
                           USER INPUT

                Markdown / Templates / Images
                           │
                           ▼
                    ThermalDocument
                         (.td)
                           │
             ┌─────────────┴─────────────┐
             │                           │
             │                    PrinterProfile
             │                    PrintOptions
             │                           │
             └─────────────┬─────────────┘
                           ▼
                    PreparationEngine
                           │
                  resolve layout context
                           │
                           ▼
                    LayoutConstraints
                           │
                           ▼
                      core-layout
                           │
                           ▼
                    LogicalDocument
                           │
                           ▼
                 physical strategy plan
                           │
                           ▼
                     PreparedPrint
                      /          \
                     /            \
                    ▼              ▼
              core-preview   trusted protocol encoder
                    │         (`core-escpos` for v1)
                    │              │
                    ▼              ▼
               PrintPreview     Byte Stream
                    │              │
                    ▼              ▼
          Shared Compose UI    Print Session
                                   │
                           Platform Transport
                             /       |       \
                            /        |        \
                           ▼         ▼         ▼
                     Bluetooth     USB      Web/Desktop
                                   │
                                   ▼
                                Printer
```

Around the engine:

```text
Shared Compose UI
        ↓
Shared Presentation
        ↓
Shared Core
        ↓
Platform services only where genuinely required
```

Responsibilities:

```text
Core
    Understands documents, logical layout, printer capabilities,
    physical preparation, preview models, and protocol encoding.

Shared Presentation
    Coordinates application workflows and state.

Shared Compose UI
    Lets users interact with those workflows across platforms.

androidApp / desktopApp / webApp
    Host the shared application and implement OS integrations.

Platform Transport
    Delivers already-encoded bytes without interpreting document semantics.
```

The central document contract is:

> `.td` stores a portable description of the user's document, assets, and limited author layout intent. It does not store printer output instructions.

The central layout contract is:

> `core-layout` produces protocol-independent logical geometry from a `ThermalDocument` and explicit `LayoutConstraints`. Physical preparation derives target-specific constraints without leaking ESC/POS into the layout engine.

The central printer contract is:

> `PrinterProfile` describes what the hardware can reliably do. `PrintOptions` describes what the user requests for the current job. `PreparedPrint` describes exactly what Rastrio decided the target printer should do.

The central preview contract is:

> `core-preview` renders the exact `PreparedPrint` plan that `core-escpos` serializes. Raster content is previewed from the exact prepared raster data; native printer glyph shapes may be representative unless an exact font model is available, but geometry and operation semantics remain authoritative.

The central portability contract is:

> Core and primary UI are shared. Only genuine operating-system, hardware, decoding, and permission integrations are platform-specific.

These contracts take precedence over implementation shortcuts.

---

## 108. Decision Log for This Baseline

This PRD intentionally incorporates the following architecture decisions:

1. **Gradle + Kotlin DSL** is the build configuration baseline.
2. **Compose Multiplatform UI is shared by default** across Android, Desktop, and Web.
3. **Android remains the first production target.**
4. **Desktop/JVM is a first-class development target** and later optional product target.
5. **Web is later and must not block Android before its dedicated phase.**
6. **iOS is not a target.**
7. **`ThermalDocument` remains printer-independent.**
8. **`LogicalDocument` is protocol-independent but may be target-constrained through explicit `LayoutConstraints`.**
9. **Text measurement/rendering ownership is explicit through `core-text`.**
10. **`PreparedPrint` is the immutable physical intermediate representation.**
11. **Preview and encoding consume the same physical plan.**
12. **ESC/POS dialect/strategy belongs in validated profile capability data.**
13. **`.tcfg` v1 uses JSON rather than YAML.**
14. **Printer raster-band constraints and transport write chunking are separate.**
15. **Archive safety is required from Phase 1.**
16. **Core never silently resolves remote Markdown assets.**
17. **Partial transmission is treated as potentially partial physical output and is not automatically retried.**
18. **Long documents are designed for bounded-memory streaming/virtualization.**
19. **Wide landscape tiling remains an advanced feature but is not a mandatory blocker for the first stable Android release.**
20. **Official builds remain compatible with the project's Apache-2.0/FOSS/F-Droid goals.**
21. **`PreparedPrint` and prepared-operation types are owned by `core-printer`; `core-preview` and `core-escpos` depend downstream toward it.**
22. **The portable application-level `PrinterInstance` model belongs to `shared`; platform applications own discovery, live device/session objects, transport resolution, permissions, and persistence adapters.**
23. **Prepared raster output may use bounded immutable chunk/spool backing; immutability does not require one giant in-memory bitmap or byte array.**
24. **Raw/unsupported Markdown HTML is preserved as literal text with a structured warning in v1; it never executes and never silently disappears.**
25. **Portable JSON validity is defined by `TD_SPEC.md` / `TCFG_SPEC.md`, not by the permissiveness of a chosen parser library.**
26. **The H50i maintained production profile is evidence-gated; generic ESC/POS documentation or nominal product marketing data is not enough to assert output-affecting device capabilities.**
27. **Full bidirectional paragraph layout is not a mandatory first-stable-release feature; unsupported significant RTL/bidirectional content must fail visibly rather than be corrupted.**
28. **Emoji support is limited to deterministic printable behavior provided by the controlled raster text policy; unsupported emoji must diagnose rather than silently substitute.**

---

## 109. Baseline Approval Rule

This v2.1 document should be copied to repository-root `PRD.md` and treated as the implementation baseline.

Before changing an architectural invariant, file-format compatibility rule, release-critical behavior, or module responsibility, contributors should update the relevant specification and this PRD in the same change or record a deliberate migration plan.

Normal implementation details that preserve these contracts do not require PRD changes.

