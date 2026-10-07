# RastrIO Testing Strategy

**Status:** Normative engineering specification  
**Specification revision:** 1.1  
**Project:** RastrIO
**Applies to:** Entire repository  
**Primary release target:** Android  
**Secondary development/product targets:** Desktop/JVM and Web/Wasm  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related specifications:** `docs/TD_SPEC.md`, `docs/TCFG_SPEC.md`, `docs/TEXT_RENDERING_SPEC.md`, `docs/PREVIEW_SPEC.md`, `docs/SECURITY.md`, `docs/RESOURCE_LIMITS.md`, `docs/HARDWARE_TESTS.md`

---

## 1. Purpose

This document defines the engineering test strategy for RastrIO.

Its governing principle is:

> **Testing is part of feature implementation, not a later cleanup phase.**

A feature is not complete merely because it compiles, renders correctly for one example, or works once on a physical printer.

Tests are part of the implementation contract.

RastrIO contains several transformations where small mistakes can produce materially incorrect physical output, corrupt portable files, create misleading previews, or cause duplicate printing. The test strategy therefore emphasizes:

- deterministic Core tests;
- exact golden tests where output is contractual;
- invariant and property-based testing for geometry and binary packing;
- aggressive invalid-input testing for portable formats;
- automated transport/session testing using fakes;
- permanently documented physical printer tests;
- migration and compatibility tests for persisted formats;
- explicit security and resource-limit regression suites;
- Android-first CI gates.

This specification does not prescribe a particular Kotlin testing library unless another repository specification does so. Test frameworks may evolve provided the behavioral obligations in this document remain satisfied.

---

## 2. Normative Language

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

- **MUST / MUST NOT** define mandatory repository requirements.
- **SHOULD / SHOULD NOT** define the expected default; deviation requires a concrete reason.
- **MAY** defines permitted but optional behavior.

Where this document proposes a future Gradle task, directory convention, helper, or test utility that does not yet exist, it is explicitly identified as a **proposed convention** rather than an existing repository capability.

---

## 3. Testing Goals

The test suite exists to establish all of the following.

1. Pure Core transformations behave deterministically for equivalent inputs.
2. Portable formats can be safely read, written, validated, and migrated.
3. Layout and text geometry remain stable under explicit constraints.
4. Raster algorithms produce exact and reproducible monochrome output.
5. Printer-specific decisions are completely resolved before `PreparedPrint` is finalized.
6. Physical preview and the active protocol encoder observe the same immutable physical plan and finalized prepared-artifact content.
7. `core-escpos` is the v1 protocol encoder and serializes decisions rather than reinterpreting them.
8. Future protocol encoders, if added, obey the same no-reinterpretation contract.
9. Platform transports deliver bytes without acquiring document semantics.
10. Partial transmission is represented conservatively and never causes unsafe automatic retry.
11. Untrusted files, fonts, prepared-artifact storage, parser behavior, profiles, and device metadata cannot bypass security/resource protections.
12. Long documents, images, fonts, raster output, and prepared artifacts do not require unbounded resident memory.
13. Persistent data remains compatible or is migrated deliberately.
14. Shared application behavior remains portable.
15. Android remains the mandatory production-quality target while Desktop and Web retain the portability expectations defined by the PRD.

Tests SHOULD verify externally meaningful contracts rather than incidental implementation structure.

Refactoring internal classes without changing behavior SHOULD NOT require widespread fixture changes.

---

## 4. Testing Architecture

Tests SHOULD be placed as close as practical to the implementation they verify.

Portable Core tests SHOULD normally live in the relevant module's common test source set, for example conceptually:

```text
core/document/src/commonTest/
core/markdown/src/commonTest/
core/templates/src/commonTest/
core/text/src/commonTest/
core/layout/src/commonTest/
core/raster/src/commonTest/
core/profile/src/commonTest/
core/printer/src/commonTest/
core/preview/src/commonTest/
core/escpos/src/commonTest/
```

Shared presentation and shared Compose tests belong with `shared`.

Android-specific storage, lifecycle, permissions, Bluetooth, USB, persistence, and integration tests belong under `androidApp`.

Desktop-specific tests belong under `desktopApp`.

Web-specific tests belong under `webApp`.

Exact Kotlin Multiplatform test source-set names MAY follow the supported conventions of the repository's selected Kotlin, Compose Multiplatform, and Android Gradle Plugin versions. This specification intentionally does not require a source-set name that may become obsolete as KMP tooling evolves.

### 4.1 Boundary rule

Tests MUST preserve the same dependency boundaries as production code.

A test MUST NOT justify introducing an Android, Compose, Skia, browser, or transport dependency into a portable Core API.

Test utilities shared between Core modules MUST themselves remain portable when used from common tests.

### 4.2 Testability as an architectural property

Where nondeterministic or platform-specific behavior would otherwise prevent reliable testing, the implementation SHOULD expose a narrow injectable abstraction.

Examples include:

```text
TextRasterizer / TextShaper backend adapter
FontResourceResolver
ImageDecodeService
PreparedArtifactStore
Clock, if timestamps materially affect persisted behavior
PrinterTransport
PlatformStorage
external asset resolver
```

Such abstractions MUST use portable domain types at Core boundaries.

Testing requirements MUST NOT cause Core to depend directly on Android, Compose, platform image handles, Bluetooth classes, or USB classes.

---

## 5. Repository Test Taxonomy

RastrIO uses multiple complementary test classes. No single class is sufficient for the entire stack.

### 5.1 Pure unit tests

Pure unit tests verify isolated deterministic logic.

Primary candidates include:

```text
schema validation
dimensions
unit conversion
alignment calculations
layout-constraint resolution
font/cell calculations
Unicode grapheme/line-break policy
text coverage and fallback decisions
font-resource selection/fingerprinting
code-page capability decisions
profile validation
profile override precedence
image algorithm primitives
raster packing
band calculations
prepared-artifact indexing/lifecycle
tiling geometry
cut decisions
transport-policy intersection
state-machine transitions
resource-limit calculations
diagnostic generation
```

Pure unit tests SHOULD be fast, deterministic, independent of network access, and independent of physical hardware.

Core algorithms SHOULD preferentially be testable through this category.

---

### 5.2 Core integration tests

Core integration tests verify multiple RastrIO Core modules working through an architectural boundary.

Examples include:

```text
Markdown
→ ThermalDocument
→ LogicalDocument

ThermalDocument
+ PrinterProfile
+ PrintOptions
→ PreparedPrint

PreparedPrint
→ PrintPreview

PreparedPrint
→ ESC/POS bytes

PreparedPrint + immutable prepared artifacts
→ identical preview/encoder raster content
```

A Core integration test MAY involve several modules, but it MUST NOT silently substitute a second implementation of an algorithm merely to calculate expected values.

For example, a physical-preview test MUST inspect the actual resolved operations in `PreparedPrint`; it MUST NOT independently recalculate wrapping and then compare the two layout engines.

---

### 5.3 Golden tests

Golden tests compare actual output against a checked-in expected artifact.

They are mandatory where exact output is part of the contract, including:

```text
Markdown → ThermalDocument
ThermalDocument + LayoutConstraints → LogicalDocument
ThermalDocument + PrinterProfile + PrintOptions → PreparedPrint
deterministic raster processing
raster bit packing
PreparedPrint → PrintPreview where a stable serialized preview model is available
PreparedPrint → finalized prepared-artifact content
PreparedPrint → ESC/POS bytes
```

Golden tests are defined in detail later in this document.

---

### 5.4 Serialization tests

Serialization tests verify:

```text
encoding
decoding
validation
round trips
version handling
unknown-version behavior
strict JSON conformance
duplicate-key rejection
unknown-property rejection
required fields
optional fields
stable discriminators
migration
semantic equality
archive structure
```

`.td` and `.tcfg` require dedicated serialization suites.

Binary identity MUST NOT be assumed where the format specification does not require binary-identical archives. For example, ZIP metadata differences MUST NOT accidentally become part of the `.td` semantic contract unless `TD_SPEC.md` explicitly defines them as such.

---

### 5.5 Property-based tests

Property-based tests verify invariants across a generated range of valid inputs.

They SHOULD be used when an invariant is stronger than a collection of hand-picked examples.

Primary candidates include:

```text
dimension conversions
bit packing
band splitting
layout coordinate bounds
wide-document segmentation
segment overlap
archive-path normalization
strict JSON invariants
profile overrides
prepared-artifact coverage/index invariants
serialization round trips
```

Randomized failures MUST report enough information, including a reproducible seed or minimized input where supported, to allow the failure to be reproduced locally.

---

### 5.6 Fuzz and invalid-input tests

Fuzz and invalid-input tests exercise untrusted or malformed inputs.

They are particularly important for:

```text
.td ZIP containers
manifest.json
document.json
.tcfg JSON
Markdown
image metadata
imported font files / font-adapter boundaries
prepared-artifact metadata/index readers
archive paths
version identifiers
node discriminators
profile capability combinations
protocol-family identifiers
protocol strategy identifiers
```

Fuzz tests MUST treat crashes, hangs, uncontrolled allocation, path escape, arbitrary command injection, and silent acceptance of structurally invalid data as failures.

Malformed user input SHOULD produce a structured validation failure or diagnostic rather than an uncaught exception.

---

### 5.7 Compose Multiplatform UI tests

Shared UI tests verify behavior owned by shared presentation and shared Compose UI.

They SHOULD focus on:

```text
navigation
user-visible state
form editing
diagnostic presentation
preview requests
printer selection
print-option changes
print confirmation
progress states
failure states
partial-output warnings
state restoration where applicable
```

Shared UI tests MUST NOT validate printer geometry by reproducing Core calculations inside the UI test.

The UI displays Core output. Core tests establish the geometry.

---

### 5.8 Android integration tests

Android integration tests verify Android-specific responsibilities such as:

```text
application lifecycle integration
permission handling
file picker/storage integration
image decoding adapters
PrinterInstance persistence
Bluetooth discovery adapters
Bluetooth transport integration
USB integration when implemented
Android-specific error mapping
shared UI hosting
```

Android integration tests SHOULD use controlled substitutes for external hardware where possible.

Physical printer behavior belongs in the hardware suite rather than ordinary instrumentation tests.

---

### 5.9 Transport tests using fakes

Every transport implementation MUST be testable independently of a physical printer to the practical extent allowed by its platform API.

Tests MUST cover:

```text
connect
write
chunking
timeout
flush where meaningful
disconnect
cancellation
connection failure
write failure
partial transmission
state transitions
```

Transport fakes are defined in detail later in this specification.

---

### 5.10 Hardware/manual tests

Physical printer testing validates assumptions that software-only tests cannot establish.

The manual hardware suite MUST be permanently documented in:

```text
docs/HARDWARE_TESTS.md
```

The Helett H50i BillQuick Go is one currently available ESC/POS reference device used for implementation validation.

It is not the design center of the test strategy and MUST NOT define generic Core or `.tcfg` expectations.

Hardware tests supplement automated tests. They MUST NOT replace deterministic Core, encoder, transport-fake, serialization, or per-profile evidence tests.

---

### 5.11 Compatibility and migration tests

Persistent formats and user-owned data require compatibility tests.

This category covers:

```text
.td versions
.tcfg versions
PrinterInstance persistence
profile override persistence
application storage migrations
old fixtures opened by newer versions
document/profile migrations where defined
unsupported future versions
```

Once a released persistent-format fixture exists, it SHOULD remain checked into the repository permanently unless a deliberate retention policy says otherwise.

---

### 5.12 Performance and resource-limit tests

Performance and resource tests verify bounded behavior rather than only functional correctness.

Examples include:

```text
large Markdown
very long documents
large but permitted archives
large assets
large images
maximum permitted image dimensions
imported-font parser/cache pressure
long raster output
raster band streaming
spool-backed prepared artifacts
preview virtualization
large ESC/POS output
large transport sessions
```

Tests MUST verify the concrete limits defined by `docs/RESOURCE_LIMITS.md` once those limits exist.

---

### 5.13 Security regression tests

Every security-sensitive parser or boundary requires tests for previously identified attack classes.

At minimum these include:

```text
archive path traversal
absolute paths
normalized duplicate archive paths
ZIP bombs
oversized archives
oversized entry count
oversized JSON
duplicate JSON keys
unknown fields hidden by permissive parser settings
prohibited lenient JSON syntax
oversized assets
malicious/pathological imported fonts
prepared-artifact tampering/lifetime failures
unsupported versions
malformed node graphs
unsafe profile fields
unknown protocol-family injection
raw-command injection attempts
unexpected network resolution
printable-content logging regressions
```

A security fix MUST add a regression test whenever a practical automated reproduction can be constructed safely.

---

## 6. Determinism Requirements

Core transformations SHOULD produce equivalent output for equivalent input.

Tests of deterministic behavior MUST control all inputs that can affect results, including where applicable:

```text
locale
character encoding
timezone
line-ending normalization
font resource/version/hash
HarfBuzz/FreeType version and pinned renderer configuration
Unicode data version
line-break/segmentation policy version
profile version
layout constraints
print options
image processing parameters
random seed
```

A deterministic Core test MUST NOT depend on:

```text
current wall-clock time
machine hostname
user home directory
implicit system font discovery
network connectivity
Android device model
unspecified default locale
unordered filesystem traversal
```

If a dependency cannot produce deterministic output across supported targets, the relevant specification MUST state what semantic tolerance is permitted and tests MUST assert that contract rather than pretending bit-exact portability exists.

Raster output intended to be exact MUST use controlled inputs and controlled renderer resources.

---

## 7. Canonical Transformation Coverage

Every major architectural transformation requires explicit automated coverage.

---

## 8. Markdown → `ThermalDocument`

The Markdown compiler is a deterministic semantic transformation.

Tests MUST cover the required GitHub Flavored Markdown constructs as they are implemented:

```text
paragraphs
headings
strong
emphasis
strikethrough
inline code
fenced code
ordered lists
unordered lists
nested lists
task lists
block quotes
thematic separators
links
images
tables
explicit line behavior where applicable
Unicode
raw HTML policy
task/checklist semantic state
mixed documents
```

Golden tests SHOULD use the fixture cases established by the PRD, including conceptually:

```text
paragraph
headings
formatting
unordered-list
ordered-list
nested-list
task-list
blockquote
code
links
image
external-image
table
unicode
raw-html
mixed-document
```

Each relevant fixture SHOULD verify both:

```text
expected ThermalDocument
expected diagnostics
```

Tests MUST prove that Markdown compilation:

- has no printer-width knowledge;
- has no DPI knowledge;
- performs no ESC/POS decisions;
- does not perform hidden network access;
- does not open referenced external files itself;
- does not silently drop unsupported significant content;
- preserves significant raw/unsupported HTML as ordinary literal text where safely available and emits the defined warning such as `MD101`;
- never creates an executable/renderable raw-HTML node;
- produces semantic checked/unchecked checklist state rather than relying on checkbox Unicode/emoji glyphs.

An external HTTP(S) image reference MUST remain a reference or diagnostic until explicitly resolved by a higher layer.

---

## 9. Template State → `ThermalDocument`

Each structured template MUST have deterministic tests proving that its state compiles directly to `ThermalDocument`.

Initial template coverage includes:

```text
Todo / Checklist
Shopping List
Quick Note
Memo
QR Message
```

Tests SHOULD cover:

```text
minimal state
typical state
empty optional fields
multiple items
item ordering
checked/unchecked state
quantities where applicable
special characters
line breaks
Unicode
QR payload
validation diagnostics
```

Tests MUST prove that templates do not use Markdown as an unnecessary intermediate representation.

Template builders MUST remain printer-independent.

---

## 10. `.td` Serialization and Deserialization

The `.td` suite MUST cover successful, unsuccessful, security-sensitive, and compatibility behavior.

### 10.1 Valid documents

Tests MUST cover:

```text
minimal document
every block type
every inline type
metadata
assets
asset metadata
optional source
portrait layout intent
landscape layout intent
maximum logical width
multiple assets
round-trip semantic equality
```

### 10.2 Invalid containers and documents

Tests MUST cover:

```text
invalid ZIP
corrupt archive
missing manifest.json
missing document.json
invalid format identifier
unsupported container version
unsupported document schema version
unknown nodes according to format policy
invalid asset reference
malformed node graph
invalid UTF-8 where applicable
JSON comments
trailing commas
single-quoted strings
unquoted property names
duplicate object keys in manifest.json
duplicate object keys in document.json
unknown top-level fields
unknown nested fields
`NaN`, `Infinity`, `-Infinity`
invalid standard-JSON number grammar
illegal null values
parser configuration that would otherwise ignore unknown fields
```

### 10.3 Archive security

Tests MUST cover:

```text
../ traversal
nested traversal
absolute paths
platform-style absolute paths where applicable
paths that normalize outside the namespace
normalized duplicate paths
resource-expansion limits
entry-count limits
oversized JSON
oversized individual assets
```

No accepted archive path may escape the logical `.td` container namespace after normalization.

### 10.4 Strict-reader-path contract

Strict JSON conformance tests MUST exercise the actual production `.td` reader path.

It is insufficient to test only a helper validator if the production parser can first:

```text
collapse duplicate keys
ignore unknown fields
coerce invalid values
accept prohibited lenient syntax
```

A test MUST prove that parser-library permissiveness cannot weaken `TD_SPEC.md`.

### 10.5 Round-trip contract

For any valid in-memory `ThermalDocument` supported by the current schema:

```text
encode
→ decode
→ validate
```

MUST preserve semantic equality, subject only to explicitly documented normalization.

Tests SHOULD NOT require binary-identical ZIP archives unless the `.td` specification explicitly declares byte-for-byte reproducibility as part of the format.

---

## 11. `.tcfg` Serialization and Deserialization

Tests MUST cover:

```text
encode
decode
semantic round trip
profile identity
display metadata
printable width
DPI
native font capabilities
font geometry
native styles
code pages
raster strategies
QR configuration
barcode configuration when supported
cutter configuration
buffer guidance
status-query capability
`protocol.family = escpos` for schema v1
protocol strategy identifiers
known quirks
override precedence
strict unknown-property rejection
resource limits
```

Invalid-profile tests MUST include:

```text
invalid printable width
invalid DPI
invalid font geometry
impossible capability combinations
unknown protocol family
attempt to make an unknown family fall back to ESC/POS
dynamic module/class/native-library identifiers
unknown required protocol strategy
invalid raster strategy
invalid QR configuration
invalid cutter mode
invalid override
duplicate JSON keys
unknown top-level or nested fields
comments/trailing commas/other lenient JSON
non-standard numeric tokens
attempted arbitrary command injection
unsafe raw byte/program fields
```

A `.tcfg` parser MUST NOT turn imported profile data into arbitrary executable printer-command programs.

Strict JSON tests MUST run through the production reader and prove that permissive parser modes such as ignoring unknown keys cannot weaken the v1 contract.

Protocol-family tests MUST prove:

```text
schema v1 accepts registered ESC/POS profiles
unknown/future family fails closed
unknown family does not fall back to ESC/POS
profile-supplied module/class/library names never load executable protocol code
```

Tests MUST preserve the distinction between:

```text
PrinterProfile
PrinterInstance
PrintOptions
```

A profile fixture MUST NOT require a personal Bluetooth identity, USB identity, pairing record, or other user-specific printer identity.

---

## 12. `ThermalDocument` + `LayoutConstraints` → `LogicalDocument`

Layout tests MUST establish deterministic protocol-independent geometry.

Required coverage includes:

```text
empty document
single paragraph
multiple blocks
wrapping
long paragraph
long unbroken token
explicit line break
left alignment
center alignment
right alignment
headings
ordered lists
unordered lists
nested lists
checklists
four-column tabs
quotes
code blocks
tables
wide tables
image placeholders
QR placeholders
portrait
landscape
different logical widths
same document under different LayoutConstraints
```

Assertions SHOULD cover geometry rather than only high-level text.

Examples include:

```text
line count
line bounds
block bounds
x/y positions
resolved widths
resolved heights
table column geometry
row geometry
alignment offsets
checklist marker/content geometry
tab-stop advances
paragraph/list/code spacing from `TextLayoutPolicyV1`
logical document dimensions
```

Tests MUST prove that:

```text
ThermalDocument + explicit LayoutConstraints
```

is sufficient to determine logical layout.

No hidden printer-width constant or Compose text measurement may affect authoritative Core layout.

---

## 13. Text Measurement and Rendering

Text testing is architecture-critical because layout, preparation, preview, and printing depend on stable geometry.

The v1 deterministic baseline is defined by `docs/TEXT_RENDERING_SPEC.md`:

```text
Unicode data version: 18.0
grapheme segmentation: UAX #29
line breaking: UAX #14
canonical tab stops: 4 columns
default proportional family: pinned Noto Sans
default monospace family: pinned JetBrains Mono
script fallback: pinned required Noto script families
reference shaping: pinned HarfBuzz
reference glyph raster: pinned FreeType
full emoji font: not required
full bidi/RTL: not required for first stable
```

Tests MUST cover, as applicable:

```text
ASCII
printer-native code-page content
unsupported Latin characters
combining characters
grapheme clusters
four-column tabs
mixed supported and unsupported content
Devanagari/Indic shaping fixtures
simple controlled monochrome symbols
unsupported emoji/ZWJ sequences
long unbroken tokens
explicit line breaks
paragraph/list/code spacing
native/raster fallback geometry
explicit user-selected font files
explicit platform-font selection where implemented
```

### 13.1 Native text metrics

Native text tests SHOULD verify:

```text
font selection
cell geometry
width scaling
height scaling
bold geometry where relevant
code-page coverage
line measurement
alignment
```

Tests MUST use metrics supplied through the relevant typography/profile model rather than hard-coded device values.

Native-text geometry tests MUST NOT use Noto Sans, Compose, Android text layout, or any platform font metric as an oracle for printer ROM geometry.

### 13.2 Unicode segmentation and line breaking

Tests MUST pin Unicode 18.0 behavior.

Golden/property coverage SHOULD include:

```text
extended grapheme clusters
combining sequences
variation selectors
ZWJ clusters
regional-indicator sequences
normal break opportunities
emergency grapheme-boundary breaks
line-break behavior near width limits
```

A platform/OS Unicode-data update MUST NOT silently change authoritative Core test results.

### 13.3 Tabs

Tests MUST verify advancement to the next canonical four-column stop rather than blind replacement with four spaces.

At minimum cover logical positions:

```text
0
1
3
4
7
```

plus:

```text
repeated tabs
tabs near wrap boundaries
proportional text
monospace/code text
```

### 13.4 Bundled default fonts

Deterministic raster tests MUST use the pinned project-controlled font files and verify their stable resource identity/content hash.

Coverage MUST establish:

```text
Noto Sans Light
Noto Sans Regular
Noto Sans Bold
Noto Sans Italic
Noto Sans Bold Italic
JetBrains Mono code role
required Noto script fallback for stable Indic/Devanagari cases
```

A missing face MUST NOT be silently replaced by a synthesized style unless a future explicit policy defines and tests that transform.

The default fallback chain MUST NOT consult arbitrary platform-installed fonts.

### 13.5 Explicit user/platform fonts

Where explicit user font import is implemented, tests MUST cover:

```text
font byte fingerprinting
layout changes caused by the chosen font
malformed/over-limit font rejection
unavailable selected font
no silent substitution
resource/cancellation behavior
```

Where explicit platform-font selection is implemented, tests MUST prove it remains opt-in and outside portable pixel-golden guarantees.

### 13.6 Raster text determinism

Deterministic raster-text tests MUST control:

```text
font bytes/hash
HarfBuzz version/configuration
FreeType version/configuration
Unicode data version
requested size
style
hinting/coverage settings
monochrome conversion policy
```

Raster text tests SHOULD compare final monochrome output where practical.

Platform LCD/subpixel rendering MUST NOT enter authoritative goldens.

### 13.7 Whole-line fallback

When a resolved physical line cannot be represented reliably as native printer text, the complete line may require rasterization.

Tests MUST verify that fallback does not silently mutate finalized `PreparedPrint` geometry after preparation.

If fallback metrics require different wrapping, preparation MUST resolve that before the immutable `PreparedPrint` is returned.

### 13.8 Emoji and symbol policy

Tests MUST distinguish:

```text
simple monochrome symbol covered by controlled fonts
unsupported emoji-presentation request
unsupported ZWJ sequence
unsupported flag/modifier sequence
```

Unsupported emoji MUST produce a structured diagnostic rather than silent platform-emoji substitution, tofu, or `?`.

Checklist markers are not emoji and are tested separately.

### 13.9 RTL/bidi scope

Tests MUST prove that unsupported significant RTL/bidirectional content is diagnosed rather than:

```text
naively reversed
silently dropped
printed in incorrect logical order while claiming fidelity
```

Any specifically supported RTL case requires its own deterministic fixture.

### 13.10 Compose boundary

No test may treat Compose's own text layout result as the authoritative expected geometry for printer output.

Compose is a display layer, not the Core text-measurement oracle.

---

## 14. Raster Algorithms

Every deterministic raster algorithm requires exact automated tests.

Initial algorithms include:

```text
Threshold
Bayer ordered dithering
Atkinson dithering
Floyd–Steinberg dithering
```

Raster tests MUST separately cover processing and binary packing where practical.

Required behaviors include:

```text
grayscale conversion where defined
brightness
contrast
gamma
resize behavior
thresholding
dithering
1-bit conversion
bit order
padding bits
band splitting
row continuity
full-width processing
```

The implementation MUST correctly handle widths not divisible by eight.

No test SHOULD assume that image dimensions are conveniently byte-aligned.

---

## 15. `LogicalDocument` + Profile/Options → `PreparedPrint`

Printer-preparation tests verify the most important physical planning boundary.

Inputs conceptually include:

```text
ThermalDocument / resolved LogicalDocument
PrinterProfile
PrintOptions
required rendering services
```

Tests MUST verify physical decisions such as:

```text
target dimensions
dot conversion
printer-width resolution
native vs raster text
font selection
code-page selection
unsupported-glyph handling
image sizing
image raster resolution
QR native/raster strategy
barcode native/raster strategy when implemented
cut decisions
manual cut guides
automatic cuts
physical segmentation
segment overlap
registration marks
segment numbering
semantic checklist marker preparation
finalized raster/graphic artifact identity
diagnostics
protocol-family/strategy selection
```

Tests MUST prove that different profiles can produce different `PreparedPrint` values from the same document without modifying source code.

Tests MUST also prove that the same valid inputs produce equivalent prepared output.

### 15.1 Completeness invariant

If changing a decision can change physical output, that decision MUST be observable in or referenced immutably by `PreparedPrint` before encoding begins.

Tests SHOULD explicitly fail if an output-affecting value remains deferred to `core-escpos`, a future protocol encoder, preview, or UI code.

### 15.2 Immutability

`PreparedPrint` MUST behave as an immutable physical value after successful preparation.

Preview and encoding tests SHOULD be able to consume the same object or semantically equivalent immutable snapshot without mutation.

Where `PreparedPrint` references bounded immutable prepared artifacts, tests MUST verify:

```text
stable artifact identity
stable dimensions/order/content
bounded reads
same finalized content visible to preview and encoder
no re-shaping/re-dithering/re-rasterization during reads
failure rather than regeneration if the artifact is unavailable/corrupt
cancellation/disposal lifecycle
```

---

## 16. `PreparedPrint` → `PrintPreview`

Physical preview tests MUST begin with `PreparedPrint`.

They MUST NOT call a second physical layout algorithm.

Required coverage includes:

```text
paper width
printable area where represented
resolved text geometry
raster operations
images
QR operations
separators
feeds where visually meaningful
manual cut guides
automatic cut annotations
segments
segment overlap
registration marks
diagnostics
```

### 16.1 Same-plan invariant

A contract test MUST establish:

```text
PreparedPrint
    ├── PrintPreview
    └── protocol encoder

v1 protocol encoder = core-escpos
```

as the authoritative downstream split.

Preview MUST NOT cause:

```text
re-layout
re-wrap
re-shaping
re-dithering
re-rasterizing finalized prepared artifacts
strategy reselection
segment recalculation
new cut decisions
```

### 16.2 Raster exactness

For prepared raster operations, physical preview MUST consume the exact finalized raster data carried or referenced by `PreparedPrint`.

Tests MUST compare preview raster content against the prepared artifact/raster representation rather than rerunning the original raster algorithm.

For spool-backed artifacts, tests SHOULD verify bounded ranged/chunk reads where supported and MUST verify that an unreadable/corrupt artifact produces a `PRVxxx`-family preview-owned failure rather than silent regeneration.

### 16.3 Native text

Native-text preview tests MUST verify authoritative geometry:

```text
wrapping
line placement
alignment
operation bounds
style intent
```

Exact glyph-shape equality is required only where the selected profile provides an exact native font model.

Representative preview glyph rendering MUST NOT alter authoritative physical geometry.

### 16.4 Checklist semantics

Physical-preview tests MUST verify that semantic checklist markers use the finalized prepared marker/raster content.

They MUST NOT substitute:

```text
emoji
Unicode checkbox glyphs
icon-font glyphs
Compose/UI checkbox widgets
```

When preparation rasterizes the complete checklist line/block, preview MUST display those exact pixels.

Logical preview MAY render checklist state from `LogicalDocument`, but its geometry must remain Core-derived.

### 16.5 Cut semantics

Tests MUST distinguish:

```text
ManualCutGuide
AutomaticCut
```

A manual cut guide is physical printed content.

An automatic cut boundary is a preview annotation for a printer operation and MUST NOT accidentally become text or raster output.

---

## 17. `PreparedPrint` → Protocol Bytes

`.tcfg` schema v1 supports ESC/POS, so `core-escpos` requires exact byte-level validation.

A future protocol family MUST receive an equivalent dedicated encoder suite without weakening the `PreparedPrint` authority boundary.

Golden tests SHOULD cover, as implemented:

```text
initialization
plain native text
multiple lines
selected code pages
code-page switching
font selection
alignment/state commands
bold
underline
feed
raster bands
native QR
cut commands
full cut
partial cut
```

Expected output MUST be exact at the byte level for operations whose serialization is defined.

### 17.1 Encoder non-responsibility tests

The encoder MUST NOT:

```text
re-layout text
re-wrap text
shape text
select code pages
select native/raster strategy
re-dither images
resize images
recalculate tables
change QR geometry
re-segment landscape output
change overlap
introduce new cut decisions
```

Where practical, tests SHOULD construct a prepared operation with deliberately distinctive resolved values so that any encoder-side reinterpretation becomes observable.

A dedicated test MUST prove that the encoder reads finalized prepared raster/artifact content rather than invoking a raster/text/image generation service.

### 17.2 Protocol-family dispatch

For schema v1, tests MUST prove that only a validated `escpos` profile reaches `core-escpos`.

An unsupported/unknown protocol family MUST fail before encoder dispatch.

It MUST NOT:

```text
fall back to ESC/POS
load a class/module/native library named by `.tcfg`
execute profile-supplied command code
```

### 17.3 Unsupported prepared operations

If the encoder receives an internally inconsistent or unsupported prepared operation, tests MUST verify a structured failure or diagnostic.

It MUST NOT silently drop the operation.

---

## 18. Transport and Session Lifecycle

Transport testing verifies byte delivery, not document semantics.

A transport test MUST NOT parse Markdown, perform layout, dither images, or select printer capabilities.

### 18.1 Required fake capabilities

The repository SHOULD provide reusable fake or scripted transport components able to model:

```text
successful connection
connection refusal
delayed connection
successful write
partial write
failure before any bytes are accepted
failure after some bytes are accepted
timeout
cancellation
disconnect
unexpected disconnect
flush success/failure where applicable
```

A recording fake SHOULD make available enough information to assert:

```text
connection sequence
write-call sequence
requested chunks
accepted/transmitted bytes
disconnect behavior
terminal result
```

### 18.2 Chunking

Tests MUST preserve the distinction between:

```text
printer raster-band size
transport/session write chunk size
```

Changing a transport write chunk size MUST NOT trigger document re-layout or re-dithering.

Where printer-side guidance and transport-side limits both exist, tests MUST verify that the effective transmission policy is a safe intersection rather than conflation of the two concerns.

---

## 19. Print-Job State Machine

Tests MUST exercise valid and invalid transitions for the print-job lifecycle.

Baseline states are:

```text
CREATED
PREPARING
READY
CONNECTING
PRINTING
COMPLETED
FAILED
CANCELLED
```

Tests SHOULD verify:

```text
normal successful path
preparation failure
connection failure
write failure
cancellation before transmission
cancellation during transmission
disconnect during transmission
terminal-state stability
invalid transition rejection
```

`COMPLETED` means that RastrIO successfully transmitted the intended bytes according to the active transport contract.

Tests MUST NOT treat `COMPLETED` as proof that paper physically emerged unless a supported printer/status protocol explicitly provides that evidence.

---

## 20. Partial Transmission Behavior

Partial transmission is safety-critical.

Tests MUST distinguish at least these cases:

```text
failure before any output bytes could have reached the printer
failure after some bytes may have reached the printer
successful complete transmission
```

Failure metadata SHOULD support assertions corresponding to concepts such as:

```text
bytesAttempted
bytesTransmitted
outputMayHaveOccurred
failureStage
```

Exact production field names may differ.

### 20.1 No automatic retry

An automated test MUST prove that an ambiguous partially transmitted job is not automatically retried.

This invariant applies even when reconnecting would otherwise be technically possible.

The application MAY offer an explicit user-controlled retry after clearly warning that duplicate or partial physical output is possible.

### 20.2 UI integration

Shared UI tests MUST verify that a partial-output condition is distinguishable from an ordinary pre-transmission failure.

The UI MUST NOT misleadingly present an ambiguous partial-transmission condition as if no physical output could have occurred.

---

## 21. `PrinterInstance` Persistence

Printer-instance persistence tests MUST remain distinct from `.tcfg` profile tests.

Tests SHOULD cover:

```text
create instance
reload instance
display name
transport type
transport identity
associated profile reference
validated user overrides
transport/session preferences
last-used metadata where implemented
update
delete
storage corruption
migration
```

Tests MUST prove that user-specific transport identity remains part of `PrinterInstance` persistence and does not leak into exported `.tcfg` files.

Persistence adapters SHOULD be tested through an abstract storage contract where practical, with Android-specific integration tests verifying the actual Android implementation.

---

## 22. Golden Test Fixture Layout

All repository-wide golden and reusable test data SHOULD live beneath:

```text
test-fixtures/
```

Module-private trivial data MAY remain beside a module's tests when reuse and reviewability do not justify repository-level fixtures.

### 22.1 Proposed directory convention

The following is the recommended repository convention:

```text
test-fixtures/
├── markdown/
├── templates/
├── td/
│   ├── valid/
│   ├── invalid/
│   ├── security/
│   └── compatibility/
├── tcfg/
│   ├── valid/
│   ├── invalid/
│   ├── security/
│   └── compatibility/
├── layout/
├── text/
├── fonts/
├── raster/
│   ├── source/
│   ├── threshold/
│   ├── bayer/
│   ├── atkinson/
│   ├── floyd-steinberg/
│   └── packing/
├── printer/
├── prepared-artifacts/
├── preview/
├── escpos/
├── transport/
└── fuzz/
    └── corpus/
```

These directories are conventions established by this specification; they MUST NOT be interpreted as claims that they already exist.

---

## 23. Fixture Naming

Fixture names SHOULD be:

```text
lowercase-kebab-case
stable
descriptive
independent of test execution order
```

Examples:

```text
paragraph-basic
nested-list-three-levels
table-wide
unicode-combining-marks
width-385-dots
checkerboard-17x19
partial-write-after-128-bytes
```

A fixture name SHOULD describe the behavior being exercised rather than the implementation class that happens to implement it.

Avoid:

```text
test1
sample2
new-case
temp
bug-final
```

When a bug receives a permanent regression fixture, a durable behavioral name is preferred. An issue number MAY additionally be recorded in fixture metadata or the test name.

---

## 24. Golden Case Structure

Where a transformation has nontrivial inputs and multiple expected outputs, each case SHOULD use a directory.

Example:

```text
test-fixtures/markdown/table-basic/
├── input.md
├── expected.thermal-document.json
└── expected.diagnostics.json
```

A layout case might use:

```text
test-fixtures/layout/paragraph-wrap-48mm/
├── input.thermal-document.json
├── input.layout-constraints.json
└── expected.logical-document.json
```

A printer-preparation case might use:

```text
test-fixtures/printer/native-text-narrow-profile/
├── input.thermal-document.json
├── input.profile.json
├── input.print-options.json
└── expected.prepared-print.json
```

An ESC/POS case might use:

```text
test-fixtures/escpos/plain-text/
├── input.prepared-print.json
└── expected.escpos.hex
```

Exact serialized helper formats MAY change, but fixtures MUST remain stable and reviewable.

---

## 25. Golden Representation

Golden output SHOULD use the most reviewable representation that still asserts the relevant contract.

Recommended representations include:

```text
canonical JSON for semantic models
hexadecimal text for byte streams
PBM or another explicitly deterministic monochrome representation for raster output
small checked-in image files for controlled source fixtures
plain UTF-8 text for diagnostics where appropriate
```

If a golden comparison targets semantic JSON rather than literal serialization bytes, the test SHOULD normalize through a dedicated canonical test representation.

A canonical golden representation MUST NOT accidentally redefine the actual public `.td` or `.tcfg` wire format.

For ESC/POS output, the golden MUST represent the exact bytes expected from the v1 encoder.

For packed 1-bit raster output, tests MUST verify exact bit placement and padding.

Text/raster goldens MUST record or otherwise bind to the controlled font resource/hash, Unicode data version, HarfBuzz/FreeType version/configuration, and any output-affecting monochrome-conversion policy required by `TEXT_RENDERING_SPEC.md`.

Prepared-artifact goldens SHOULD compare semantic metadata plus stable content bytes/hashes; they MUST NOT depend on temporary filesystem paths.

---

## 26. Required Raster Fixtures

The shared raster fixture corpus MUST include:

```text
all black
all white
50% gray
gradient
checkerboard
photograph-like fixture
odd dimensions
width not divisible by 8
full printer width
```

The PRD also requires coverage for very long narrow content.

Therefore the maintained raster corpus SHOULD additionally contain:

```text
very long narrow fixture
very narrow fixture
```

### 26.1 Photograph-like fixture

The photograph-like fixture MUST be stable and legally redistributable in the repository.

Its origin or generation method SHOULD be documented when it is not obviously repository-created.

Tests MUST NOT depend on downloading the fixture from the network.

### 26.2 Width-not-divisible-by-eight cases

At least one packing fixture MUST use a width that requires padding bits in the final byte of each row.

Tests MUST verify:

```text
packed row width
pixel bit order
padding-bit value
next-row alignment
```

### 26.3 Full-width cases

Full-width fixtures SHOULD use profile-derived or fixture-declared width.

Generic raster code MUST NOT contain a hard-coded H50i printer width merely to satisfy a fixture.

---

## 27. Deterministic Golden Comparison

Golden comparison MUST be deterministic.

Tests MUST normalize only properties explicitly excluded from the behavioral contract.

Tests MUST NOT discard a meaningful difference simply to make a golden test pass.

For example:

```text
JSON formatting whitespace
```

may be canonicalized for a semantic model golden.

However:

```text
resolved x/y position
segment count
raster bit
code page
cut operation
ESC/POS byte
```

must not be normalized away when it is the behavior being tested.

Floating-point values SHOULD be avoided for exact geometry where fixed-point or integer domain units are available.

Where floating-point computation is genuinely required, the specification defining the value MUST establish an acceptable tolerance before tests use approximate equality.

---

## 28. Golden Update Procedure

Normal test execution MUST be read-only with respect to checked-in golden files.

CI MUST NOT silently rewrite expected output.

Golden changes require an intentional developer action.

### 28.1 Update process

The expected procedure is:

1. Make the intentional implementation or specification change.
2. Run the affected tests normally and observe the differences.
3. Use an explicitly opt-in local golden-update mechanism if the repository provides one.
4. Inspect every changed fixture.
5. Run the tests again without update mode.
6. Commit implementation, specification changes where required, and intentional golden changes together.

The exact command for updating goldens is not defined by the PRD.

A future dedicated mechanism such as a Gradle property or task MAY be introduced, but its existence MUST NOT be assumed until implemented.

### 28.2 Review requirements

A pull request that changes a golden MUST explain why the output changed.

Reviewers SHOULD give particular scrutiny to changes involving:

```text
PreparedPrint
layout geometry
text metrics / Unicode policy
font resources/backends
raster output
prepared-artifact content/lifecycle
segmentation
cut behavior
code-page selection
protocol-family/strategy selection
ESC/POS bytes
security validation
```

A large unexplained golden diff is not acceptable evidence that the new behavior is correct.

Golden files MUST never be regenerated merely because a test failed after unrelated refactoring.

---

## 29. Property-Based Testing

Property-based tests SHOULD complement example-based and golden tests.

### 29.1 Raster packing invariants

For every valid nonnegative raster width within supported limits:

```text
packedRowByteCount = ceil(width / 8)
```

Equivalent integer arithmetic may be used:

```text
(width + 7) / 8
```

Properties MUST verify that:

```text
every source pixel maps to exactly its defined packed bit
padding bits are deterministic
rows do not bleed into one another
```

### 29.2 Raster band invariants

For banded raster output:

```text
all source rows are represented exactly once across bands
```

unless the specific algorithm explicitly defines overlap, which ordinary raster banding does not.

Joining all bands in order MUST reconstruct the original prepared raster rows.

### 29.3 Wide-document segmentation

When wide-document tiling is implemented:

```text
segmentation must not lose logical coordinates
```

Properties MUST verify:

```text
complete intended coverage
overlap >= 0
overlap is bounded
duplicate content exists only inside intended overlap
segment ordering is stable
coordinate mapping is continuous
```

A reconstruction property SHOULD establish that the union of segment coverage corresponds to the intended logical canvas, accounting explicitly for overlap.

### 29.4 Archive paths

For arbitrary imported archive paths:

```text
accepted normalized paths
```

MUST remain inside the logical archive namespace.

Generated traversal forms such as:

```text
../
../../
mixed separators
dot segments
redundant segments
```

MUST never normalize to an accepted path outside the container.

### 29.5 Serialization

For generated valid model instances supported by the current schema:

```text
decode(encode(value))
```

MUST preserve semantic equality subject to documented normalization.

### 29.6 Strict JSON

Generated JSON/object-token sequences SHOULD verify that:

```text
duplicate object keys are never accepted
unknown v1 fields are never silently ignored
prohibited lenient JSON forms are rejected
parser-library settings cannot change format validity
```

### 29.7 Profile overrides and protocol families

Generated validated overrides MUST NOT produce impossible dimensions or bypass safety validation.

Invalid override combinations MUST be rejected deterministically.

Generated protocol-family identifiers other than registered schema-v1 `escpos` MUST never reach `core-escpos`.

### 29.8 Prepared-artifact coverage

For generated prepared band/chunk sequences within supported limits:

```text
all finalized bytes are readable in stable order
concatenated ranged reads reproduce the authoritative content
chunk boundaries do not alter content
preview and encoder hashes/content agree
```

Artifact identity/path generation MUST not be controllable through document/profile text.

---

## 30. Fuzz Testing

Fuzzing is especially valuable for parser and archive boundaries.

Priority targets are:

```text
.td archive reader
manifest parser
ThermalDocument JSON parser
.tcfg parser
Markdown compiler
path normalizer
profile validator
strict JSON prevalidation/token boundary
prepared-artifact metadata/index/read adapter
imported-font adapter/backend boundary where practical
```

### 30.1 Safety properties

A fuzz target MUST fail the test if malformed input causes:

```text
process crash
unbounded loop
uncontrolled memory allocation
path traversal
unsafe raw printer program acceptance
unknown protocol-family fallback/dynamic loading
duplicate-key/unknown-field validation bypass
prepared-artifact path confusion or silent regeneration
invalid state escaping validation
silent schema-version downgrade
```

Expected parse rejection is not a fuzz failure.

### 30.2 Corpus management

Interesting minimized failing examples SHOULD be preserved beneath:

```text
test-fixtures/fuzz/corpus/
```

or another documented corpus location.

Security-relevant discovered cases SHOULD be promoted to explicit named regression tests rather than relying solely on continued fuzz discovery.

### 30.3 Resource-aware fuzzing

Fuzz harnesses MUST apply bounded input sizes so that the harness itself does not defeat repository resource policies.

Separate explicit resource-limit tests SHOULD exercise maximum and over-limit cases.

---

## 31. Security Regression Testing

Security tests MUST exist from the first implementation of untrusted portable formats.

### 31.1 `.td`

Required regression classes include:

```text
path traversal
absolute path
normalized duplicate path
ZIP bomb / excessive expansion
entry-count overflow
oversized JSON
duplicate JSON key
unknown v1 property
comments/trailing commas/other lenient JSON
oversized asset
unsupported version
corrupt archive
malformed graph
```

### 31.2 `.tcfg`

Required security regression classes include:

```text
unknown protocol family
unknown family fallback attempt
dynamic module/class/native-library identifier
unsafe strategy identifier
attempted raw command template
arbitrary byte injection
duplicate JSON key
unknown nested property
lenient JSON syntax
invalid capability combination
resource-limit violation
override bypass attempt
```

### 31.3 Imported fonts

Where user font import is enabled, required regression classes include:

```text
over-limit font file
malformed/truncated font
pathological font-table structure
cache-growth stress
cancellation during parse/shape
no implicit network fallback
no arbitrary system-font scan in default path
```

### 31.4 Prepared artifacts

Required regression classes include:

```text
attacker-controlled backing path rejected
artifact store limit exceeded
bounded metadata/index count
premature cleanup prevented
stale artifact from another preparation rejected
corrupt/unreadable artifact fails
missing artifact does not trigger rerasterization/regeneration
preview/encoder finalized content mismatch detected
```

### 31.5 External assets

Tests MUST prove that parsing Markdown or `.td` does not implicitly perform network access.

External asset retrieval tests belong at the explicit resolver/policy layer.

### 31.6 Logging

Where logging components are testable, regression tests SHOULD verify that printable content is not emitted by default.

Sensitive examples include:

```text
Markdown bodies
notes
QR payloads
image contents
imported font bytes
prepared raster/graphic artifact bodies
document contents
tokens
unnecessary Bluetooth identifiers
```

Diagnostics MAY include non-content metadata such as codes, dimensions, operation types, and byte counts where allowed by the privacy specification.

---

## 32. Resource-Limit Tests

Concrete numeric resource limits are defined in `docs/RESOURCE_LIMITS.md`.

This document defines how they are tested.

For each hard limit, tests SHOULD include:

```text
well below limit
exactly at limit
just above limit
grossly above limit where safe to construct
```

Relevant resources include:

```text
archive compressed size
archive expanded size
archive entry count
JSON size/depth
asset size
decoded image pixel count
imported font file/parser/cache work
prepared raster/graphic total payload
prepared-artifact/spool bytes and index metadata
preview tile/scene allocation
raster band size
document size
```

Tests MUST verify rejection or diagnostic behavior without first allocating the entire prohibited resource where the implementation is expected to defend during streaming or decode.

Current provisional policies that deserve explicit boundary tests include, among others:

```text
.tcfg: 1 MiB / 64 depth / 64 KiB string / 100,000 tokens-nodes
imported font file: 32 MiB
cumulative finalized prepared raster/graphic payload: 128 MiB
preview tile area: 2 megapixels
preferred preview tile height: 1,024 px
preview rendered-tile cache: 32 MiB
```

The numbers remain centralized in `RESOURCE_LIMITS.md`; tests SHOULD consume trusted policy values rather than duplicate scattered constants.

---

## 33. Performance Tests

Performance tests SHOULD detect architectural regressions, not merely produce benchmark numbers.

Particularly important cases include:

```text
very long Markdown
very long LogicalDocument
long raster image
full-width raster
large imported font/shaping workload
spool-backed prepared output
large number of blocks
large number of preview tiles
large ESC/POS stream
extended transport session
```

Performance tests SHOULD detect accidental introduction of:

```text
one giant receipt bitmap
one giant print-job ByteArray
unbounded preview scene
whole-archive expansion before validation
unbounded decoded image allocation
unbounded font/shaping cache
unbounded prepared-artifact/spool growth
```

Performance thresholds SHOULD NOT be invented in this file.

When the project establishes numeric latency, throughput, or memory budgets, they belong in the appropriate performance/resource specification and MUST then be enforced by suitable tests.

---

## 34. Preview Resource Tests

Long previews MUST support bounded-memory rendering strategies such as:

```text
virtualization
tiling
incremental rendering
lazy scene construction
```

Tests SHOULD verify that accessing a small visible region of a very long preview does not require materializing a single full-document ARGB bitmap.

Tests SHOULD also verify stable tile/segment boundaries where preview models expose them.

Preview resource tests SHOULD exercise the current policy for:

```text
2-megapixel tile-area ceiling
1,024-pixel preferred tile height
32 MiB rendered-tile cache ceiling
```

Spool-backed preview tests MUST prove that viewport/tile rendering reads finalized prepared artifacts and does not re-run output preparation.

Cache eviction followed by reread MUST reproduce the same finalized content while the preparation remains valid.

UI screenshot testing MAY be added for visual regressions, but screenshot tests MUST NOT become the authoritative printer-geometry test.

The authoritative geometry remains Core data.

---

## 35. Compose Multiplatform UI Tests

Shared Compose tests SHOULD cover the primary flows as features become available:

```text
Home → Markdown
Home → Template
Markdown editing
compile diagnostics
logical preview request
printer selection
physical preview request
print-option changes
print confirmation
print-job progress
normal failure display
partial-output warning
navigation
expected state retention
```

Template UI tests SHOULD additionally cover:

```text
add item
remove item
quantity editing
todo checked state
memo entry
QR entry
preview request
state restoration where applicable
```

### 35.1 UI test responsibilities

UI tests verify that:

```text
the correct Core operation is requested
the resulting state is displayed
errors are surfaced
available actions match application state
```

They do not need to independently prove raster math or layout correctness.

### 35.2 Accessibility

Significant shared UI changes SHOULD include accessibility verification appropriate to the feature.

Examples include:

```text
semantic labels
reasonable touch target exposure
keyboard navigation where applicable
focus behavior
readable diagnostic state
```

Platform-specific accessibility testing MAY supplement common UI tests.

---

## 36. Android Mandatory Integration Coverage

Because Android is the primary production target, Android-specific behavior is release-critical.

As implemented, Android automated tests MUST cover relevant portions of:

```text
shared UI hosting
runtime permission state
Storage Access Framework/file integration
Android storage adapters
image decode adapter
Bluetooth discovery adapter
Bluetooth connection lifecycle
Bluetooth transport
PrinterInstance persistence
print-job lifecycle
partial-transmission reporting
safe retry UX
USB behavior when Android USB becomes implemented
```

Tests SHOULD separate:

```text
pure policy logic
Android API adapter behavior
physical device behavior
```

so that most behavior remains executable without a printer.

---

## 37. Fake Printer Transport

A canonical reusable `FakePrinterTransport`, `InMemoryByteSink`, scripted transport, or equivalent test utility SHOULD exist once transport work begins.

The fake SHOULD support deterministic scripting such as:

```text
connect succeeds
connect fails
accept N bytes then fail
accept all bytes
timeout on write K
disconnect after write K
cancel before next write
```

It SHOULD record the byte sequence accepted according to its contract.

### 37.1 Byte accounting

Tests MUST carefully distinguish:

```text
bytes offered by the application
bytes accepted/transmitted according to the transport contract
```

A fake MUST NOT report bytes as transmitted merely because they were placed into an application buffer unless that matches the real transport contract being modeled.

---

## 38. Hardware Test Strategy

Automated testing cannot prove every printer-specific behavior.

Manual hardware tests therefore form a permanent engineering suite.

All physical procedures and results MUST be maintained in:

```text
docs/HARDWARE_TESTS.md
```

### 38.1 Test identification

Each hardware test MUST have a stable identifier.

The PRD establishes initial examples such as:

```text
HW-001 Simple ASCII
HW-002 Line wrapping
HW-003 Alignment
HW-004 Bold/underline
HW-005 Long text
HW-006 Disconnect during job / recovery procedure
HW-007 Checkerboard
HW-008 Threshold gradient
HW-009 Bayer gradient
HW-010 Atkinson gradient
HW-011 Floyd–Steinberg gradient
HW-012 Full-width image
HW-013 Long image
HW-014 Unicode raster text
HW-015 Mixed supported/unsupported text
HW-016 Complex-script sample
HW-017 Native QR
HW-018 Raster QR
HW-019 QR size variations
HW-020 Two-strip table
HW-021 Three-strip table
HW-022 Registration marks
HW-023 Physical assembly
```

Tests for roadmap features become mandatory only when those features themselves become applicable.

### 38.2 Required hardware result metadata

Each execution record SHOULD contain:

```text
hardware test ID
printer model
printer profile ID/version
relevant profile overrides
application commit or release
transport
input fixture
print options
expected physical result
observed physical result
pass/fail
notes
known quirks
tester/date where appropriate
```

### 38.3 Reference hardware and profile evidence

The Helett H50i BillQuick Go is one currently available ESC/POS reference device.

It provides implementation/hardware evidence, but it does not define the generic printer model.

Generic Core logic MUST NOT be changed merely to make H50i-specific behavior pass without representing that behavior through profile data or validated strategies.

The project SHOULD accumulate additional representative printer profiles/hardware evidence over time.

A production profile's output-affecting claims require evidence appropriate to that printer/model/firmware family; success on the H50i does not validate an unrelated printer profile.

---

## 39. Compatibility and Migration Testing

Persistent compatibility is part of correctness.

### 39.1 Portable formats

For each released `.td` or `.tcfg` schema version that remains supported, the repository SHOULD retain representative files produced by that version.

Compatibility tests MUST verify:

```text
supported old version opens correctly
migration produces the documented semantic result
current version round-trips
future unsupported version fails safely
```

Unknown future versions MUST NOT be silently interpreted as the current version.

### 39.2 Migration fixtures

Migration inputs SHOULD be treated as immutable historical evidence.

A migration test SHOULD compare:

```text
old persisted representation
→ migration
→ current semantic representation
```

When migration is lossy by design, the documented loss MUST be explicit and tested.

### 39.3 `PrinterInstance` storage

Changes to locally persisted printer-instance data MUST receive compatibility or migration tests when existing installations may contain the prior representation.

### 39.4 No fake compatibility

A test MUST NOT rewrite an old fixture into the new format before attempting to test compatibility. The old representation itself must be exercised.

---

## 40. Regression Policy

Every practical bug fix **SHOULD add a regression test**.

For bugs that can be reproduced deterministically, the normal expectation is:

```text
test demonstrating failure
→ implementation fix
→ test passes
```

A regression test SHOULD exercise the smallest stable public or architectural contract capable of reproducing the problem.

A bug fix without an automated regression test requires a concrete reason, for example:

```text
physical printer behavior cannot be automated
OS/vendor defect cannot be reproduced in CI
test infrastructure is not capable of exposing the failure
```

In such cases, the fix SHOULD add or update a numbered manual hardware procedure or another permanent verification artifact where practical.

Regression tests SHOULD retain behavioral names rather than temporary labels such as "bug fix test."

---

## 41. Diagnostic Testing

Major Core stages return structured diagnostics where significant content cannot be processed normally.

Tests SHOULD verify:

```text
diagnostic code
severity
relevant source location where defined
operation success/failure semantics
absence of private printable data where not required
```

Tests SHOULD prefer stable diagnostic codes over exact prose when wording is not part of the contract.

Coverage SHOULD include subsystem ownership where applicable, including:

```text
MDxxx
TDxxx
PRFxxx
TXTxxx
IMGxxx
LAYxxx
PRNxxx
PRVxxx
ESCxxx
TRNxxx
```

An upstream diagnostic surfaced by preview must retain its upstream code; preview-owned failures use `PRVxxx`.

Fatal diagnostics MUST prevent the affected operation.

Warnings MUST NOT silently behave as fatal failures unless the relevant specification defines that policy.

Unsupported significant content MUST NOT disappear without either representation or a diagnostic.

---

## 42. Architecture Boundary Tests

The repository SHOULD automate important dependency constraints where practical.

Examples include verifying that Core production code does not import:

```text
android.*
androidx.activity.*
android.graphics.Bitmap
android.bluetooth.*
android.hardware.usb.*
androidx.compose.*
```

Boundary tests or static checks SHOULD also verify:

```text
core-* does not depend on shared
core-* does not depend on app modules
core-preview depends downstream on core-printer
core-escpos depends downstream on core-printer
core-printer does not depend on core-preview
core-printer does not depend on core-escpos
portable APIs do not expose platform types
PreparedArtifact APIs do not expose platform file handles/paths
platform apps do not become alternate Core implementations
no catch-all core-common/core-model module is introduced solely to bypass ownership rules
```

The exact mechanism may be Gradle configuration validation, dependency analysis, source scanning, architecture-test libraries compatible with the project, or another reliable approach.

The chosen mechanism MUST NOT be described as already existing until implemented.

---

## 43. Cross-Platform Test Expectations

Portable Core behavior SHOULD be verified in common tests wherever possible.

Not every deterministic algorithm needs to run redundantly on every target in every pull request if the selected KMP tooling makes that disproportionately expensive, but portability-sensitive code SHOULD periodically execute on representative supported targets.

Target-specific deviations require explicit tests and must not silently change portable semantics.

Desktop/JVM is especially useful for rapid shared Core and UI development.

Web-specific tooling limitations before the dedicated Web phase are treated according to the CI policy below.

---

## 44. Continuous Integration Principles

CI is staged according to project maturity.

A passing CI run should answer:

```text
Does portable Core still behave correctly?
Does Android remain releasable at the current phase?
Does shared application code compile and pass its tests?
Does Desktop remain a healthy development target?
Has Web portability regressed?
Have persistent/security contracts changed?
```

CI MUST NOT imply that physical printer behavior was tested unless a hardware runner actually performed the relevant hardware suite.

---

## 45. Root Verification Command

The repository should eventually provide one clear aggregate verification entry point, conceptually:

```bash
./gradlew verifyRastrIO
```

`verifyRastrIO` is a **target convention**, not a claim that the task currently exists.

When implemented, it SHOULD aggregate the mandatory automated gates for the repository's current development phase.

`./gradlew check` SHOULD remain useful where supported by the configured modules.

However, contributors MUST NOT assume that Gradle's standard `check` lifecycle automatically includes:

```text
browser tests
device tests
Android instrumentation tests
external integration tests
manual hardware tests
every performance test
```

The CI definition must explicitly invoke whatever is mandatory.

---

## 46. Core/Common CI Gate

The Core/common gate is mandatory.

It SHOULD include, as applicable:

```text
Core compilation
Core unit tests
Core integration tests
golden tests
serialization and strict-format conformance tests
property tests selected for normal CI
security regression tests
font/text deterministic tests
prepared-artifact integrity/lifecycle tests
resource-limit tests suitable for normal CI
shared common tests
architecture-boundary validation
adopted lint/static checks
```

A Core/common failure MUST block merging unless the failing check is explicitly classified as nonblocking because of known infrastructure failure.

Output-affecting Core golden failures are blocking.

---

## 47. Android Mandatory CI Gate

Android is the production priority.

The mandatory Android gate SHOULD include, as applicable:

```text
Android compilation
Android unit tests
shared Android-compatible compilation
Android integration tests runnable in CI
Android instrumentation/device tests designated mandatory by the project
Bluetooth/transport tests using fakes
PrinterInstance persistence tests
Android font-import/platform-font adapter tests where implemented
Android prepared-artifact/spool backing-store tests
adopted Android lint/static checks
```

A feature required by the current Android release phase MUST NOT be merged solely on the basis that it passes Desktop tests.

Physical-printer tests remain a separate hardware gate.

---

## 48. Desktop/JVM CI Gate

Before Desktop becomes a production release target, Desktop/JVM serves primarily as:

```text
a portability gate
a shared UI development gate
a Core development environment
```

Desktop compilation and applicable automated tests are mandatory before the dedicated Web phase unless the repository explicitly records a temporary toolchain incident.

Desktop hardware transport tests become mandatory only when that functionality is implemented and promoted into the relevant release scope.

---

## 49. Web CI Before the Dedicated Web Phase

Before the dedicated Web product phase, Web checks SHOULD run where practical.

Examples include:

```text
Web/Wasm compilation
common-source compatibility
shared UI compilation
available browser-independent tests
```

A Web-only Beta tooling or dependency limitation MUST NOT automatically block an otherwise valid Android release when:

```text
Core portability remains intact
the regression is demonstrably Web-tooling-specific
the issue is documented
Android/common behavior remains valid
```

This exception MUST NOT be used to ignore an actual portable API or common-source regression.

A failure caused by Core code no longer being compatible with Web/Wasm is materially different from an external Web tooling outage.

---

## 50. Web CI During the Dedicated Web Phase

Once Web enters its dedicated implementation phase, the agreed Web build and test suite becomes a mandatory CI gate.

At that point, applicable tests SHOULD include:

```text
Web application compilation
shared UI behavior
browser file handling
browser storage
clipboard behavior
preview performance
image handling
hardware capability detection
WebUSB/WebSerial behavior where testable
permission failure handling
```

The exact browser matrix is a release/toolchain decision and is not defined by this testing specification.

---

## 51. CI Test Tiers

Repositories MAY divide automated tests into execution tiers for speed.

A reasonable conceptual model is:

```text
pull request:
    deterministic unit/integration/golden/security tests
    mandatory compilation gates

main/nightly:
    broader property tests
    fuzz corpus
    extended resource tests
    longer-running compatibility cases
    performance regression tests

release candidate:
    all mandatory automated release gates
    migration/security suites
    required hardware suite
```

These tier names are conventions rather than currently established Gradle tasks.

A slow test MUST NOT simply be dropped from verification. It should be assigned to an appropriate execution tier.

---

## 52. Flaky Test Policy

Deterministic Core tests MUST NOT be accepted as flaky.

Repeatedly retrying a deterministic test until it passes is not a fix.

When a test exposes genuine platform nondeterminism:

1. determine whether production behavior is also nondeterministic;
2. tighten or isolate the relevant boundary;
3. assert the documented semantic contract;
4. record unavoidable environmental limitations.

Physical hardware tests may reveal device variability, but results must be recorded rather than silently discarded.

---

## 53. Release Gates

Before a stable Android release, testing evidence MUST include the automated suites relevant to the release scope plus the release-hardening checks required by the PRD.

Testing-related release gates include:

```text
mandatory Core/common suite passes
mandatory Android suite passes
Desktop build/test gate passes
required Web portability status is acceptable for the current phase
migration tests pass
.td security regression suite passes
.tcfg validation/security suite passes
archive traversal protections pass
archive resource protections pass
resource-limit tests pass
font/import security tests pass where enabled
prepared-artifact/spool integrity/lifecycle tests pass
crash/error-path tests pass
applicable hardware regression suite passes on the project's available reference hardware
```

Dependency licensing, FOSS/F-Droid compatibility, permissions, privacy, and signing have additional release requirements outside the narrow scope of test execution but may have automated checks where practical.

A stable release MUST NOT rely on untested model-specific profile hacks in generic Core.

Passing one reference printer does not establish compatibility with every ESC/POS printer; compatibility claims must remain scoped to profiles/evidence actually tested.

---

## 54. Manual Hardware Release Gate

Hardware tests are not assumed to execute under ordinary `./gradlew check`.

Before a release containing hardware-facing changes, the applicable numbered tests in `docs/HARDWARE_TESTS.md` MUST be completed on the available/required hardware that exercises the affected capability or maintained production profile.

The release record SHOULD identify:

```text
which hardware suite revision was used
which tests were executed
which printer/profile was tested
the application commit/release
any known deviations
```

If a hardware test fails because a profile is wrong, the fix belongs in validated profile data or the appropriate physical preparation strategy—not in an undocumented printer-specific workaround in generic Core code.

---

## 55. Testing New Printer Profiles

A new or materially modified `PrinterProfile` SHOULD be tested at several levels.

Automated tests SHOULD verify:

```text
schema validation
capability combinations
font geometry
code-page declarations
raster strategy
QR strategy
cutter strategy
buffer guidance
override behavior
physical preparation
protocol-family validation
ESC/POS serialization for selected schema-v1 strategies
```

Physical verification SHOULD then validate claims that software cannot prove, such as:

```text
actual printable width/dot geometry
font appearance/geometry assumptions
code-page availability and selector behavior
raster compatibility
cut behavior
printer-side buffer behavior
native QR/barcode host-command reliability
status-query behavior where claimed
```

Evidence supporting device-specific conclusions SHOULD be recorded in `docs/ESC_POS_NOTES.md` or `docs/HARDWARE_TESTS.md` as appropriate.

Evidence granularity MUST match the claim being made.

For example:

```text
self-test says CP437
    ≠ proves selector parameter / every supported code page

self-test prints QR/EAN13
    ≠ proves host-accessible native command strategy

paper says 48 mm
    ≠ proves printableWidthDots without validated dot geometry
```

A profile with required unverified physical facts MUST remain development/incomplete rather than becoming a maintained production profile by assumption.

---

## 56. Testing Wide-Document Tiling

Wide-document tiling is an advanced feature and becomes mandatory only when that feature is implemented or promoted into release scope.

Automated coverage MUST include:

```text
wide logical document on narrow printer
same document on wider printer
segment count
segment width
complete coverage
overlap
coordinate continuity
no lost content
no duplicate content outside overlap
table row alignment
registration marks
segment numbering
manual cut behavior
automatic cut behavior
```

A property/reconstruction test MUST establish that segmentation preserves the intended logical canvas.

Physical tests SHOULD confirm assembly using the numbered hardware cases defined for wide strips.

---

## 57. Testing Transport Changes

Changing Bluetooth, USB, Desktop, or Web transport implementation MUST NOT require changing expected logical layout, raster output, or prepared geometry unless the printer/protocol capability itself also changed.

A transport-only pull request SHOULD therefore leave unrelated:

```text
LogicalDocument goldens
PreparedPrint goldens
raster goldens
physical-preview goldens
```

unchanged.

Unexpected changes to these fixtures during transport work are a warning that responsibilities may have leaked across architectural boundaries.

---

## 58. Testing Output-Affecting Option Changes

Any `PrintOptions` value capable of changing physical output invalidates the prior prepared plan.

Tests for such an option MUST establish:

```text
old PrintOptions
→ PreparedPrint A

new PrintOptions
→ PreparedPrint B
```

when the option materially affects output.

The corresponding physical preview MUST derive from `PreparedPrint B`.

Examples include:

```text
dithering algorithm
brightness
contrast
gamma
density
text strategy
image scaling
landscape overlap
registration marks
strip numbers
manual cut guides
native/raster preference
QR strategy preference
```

A transport-only setting such as Bluetooth write chunk size MUST NOT force a new `PreparedPrint`.

---

## 59. Fixture Versioning

Fixtures that model public persistent formats SHOULD declare or imply their relevant schema version.

Compatibility fixture directories SHOULD make historical version ownership clear.

For example:

```text
test-fixtures/td/compatibility/v1/...
test-fixtures/tcfg/compatibility/v1/...
```

The precise directory structure MAY evolve, but old-version fixtures MUST NOT be silently overwritten with current-format output.

Golden fixtures tied only to current internal intermediate representations MAY change deliberately when the internal representation changes without altering external behavior, subject to normal golden review.

---

## 60. Test Data Privacy

Repository fixtures MUST use synthetic or intentionally public test data.

Fixtures MUST NOT contain:

```text
personal notes
private Markdown
real user addresses
secret QR payloads
authentication tokens
personal Bluetooth identifiers
USB identifiers copied from users without justification
private images
```

Hardware test records SHOULD avoid unnecessary personal device information.

Bug reports that require user-provided data must follow the redaction and privacy rules defined by the repository's security/privacy documentation.

---

## 61. Test Dependencies

Test dependencies must follow the same FOSS and portability policies as production dependencies where they affect official repository builds.

A proposed property-testing, fuzzing, snapshot, or mocking library SHOULD be evaluated for:

```text
license
maintenance
Kotlin Multiplatform support
target compatibility
determinism
CI impact
```

Test convenience is not sufficient justification for introducing a dependency that compromises required targets or repository licensing goals.

---

## 62. Code Coverage

This specification does not define a numeric line or branch coverage threshold.

A high percentage alone does not establish correct printer behavior.

Coverage tools MAY be used to identify untested areas, but the repository's primary correctness requirements are behavioral:

```text
critical transformations have tests
security boundaries have tests
output contracts have exact tests where appropriate
bugs receive regressions
hardware behavior has documented verification
```

If the project later adopts numeric coverage gates, those gates MUST supplement rather than replace the obligations in this document.

---

## 63. Testing Responsibilities by Change Type

A change normally carries the following minimum obligations.

### Portable domain-model change

Requires:

```text
unit tests
serialization tests if persisted
validation tests
compatibility/migration analysis
```

### Markdown feature

Requires:

```text
compiler test
golden ThermalDocument
diagnostic tests
invalid/unsupported-content test where applicable
```

### Template feature

Requires:

```text
state → ThermalDocument test
shared UI test where user-facing
```

### Layout feature

Requires:

```text
unit/invariant tests
LogicalDocument golden
multiple relevant constraints
```

### Text-rendering change

Requires:

```text
measurement tests
Unicode segmentation/line-break tests
bundled font resolution/hash tests
coverage/fallback tests
explicit user/platform-font tests where applicable
determinism tests
raster golden where applicable
```

### Raster change

Requires:

```text
algorithm golden
packing tests
odd dimensions
non-byte-aligned width
resource/banding tests
```

### Printer-preparation change

Requires:

```text
PreparedPrint tests
capability/profile variations
prepared-artifact lifecycle/content tests where applicable
diagnostic tests
preview consistency tests
```

### Physical-preview change

Requires:

```text
PreparedPrint-only input contract test
geometry/operation assertions
prepared-artifact exact-content tests
no secondary physical layout/rasterization
```

### ESC/POS feature

Requires:

```text
byte-level golden
unsupported-operation behavior
proof that strategy was already resolved
proof that finalized prepared artifacts are serialized without regeneration
```

### Future protocol-family feature

Requires:

```text
explicit `.tcfg` schema/specification revision
family validation tests
trusted encoder suite
command-injection/security tests
PreparedPrint no-reinterpretation tests
compatibility analysis
```

### Transport change

Requires:

```text
fake transport tests
connection/session tests
failure/cancellation tests
partial-transmission tests
```

### Font import/platform-font feature

Requires:

```text
bounded untrusted-font tests
malformed/pathological font tests
fingerprint/resource identity tests
fallback isolation tests
cancellation/resource tests
security review
```

### Prepared-artifact/backing-store change

Requires:

```text
bounded byte/index tests
immutability/content identity tests
lifecycle/cancellation/disposal tests
tamper/corruption tests
preview/encoder same-content tests
no-regeneration tests
privacy/security review
```

### Persistent storage change

Requires:

```text
round-trip tests
corruption/error tests
migration/compatibility tests where applicable
```

### Hardware-facing feature

Requires:

```text
automated non-hardware coverage
numbered docs/HARDWARE_TESTS.md case where practical
physical execution before applicable release
```

### Security fix

Requires:

```text
security regression test whenever practical
```

---

## 64. Definition of Done

Testing obligations are part of RastrIO's Definition of Done.

A feature is complete only when all applicable conditions below are satisfied.

### 64.1 Implementation and automated verification

The implementation exists and the appropriate automated tests exist.

Tests MUST cover the architectural layer that owns the behavior rather than relying solely on a UI or end-to-end happy-path test.

All mandatory existing tests MUST pass.

### 64.2 Error behavior

Relevant failure modes and diagnostics are defined and tested.

Unsupported significant input MUST NOT disappear silently.

### 64.3 Architectural boundaries

Tests and implementation preserve module boundaries.

In particular:

```text
Core remains free of Android and Compose dependencies
layout remains owned by core-layout
text contracts remain owned by core-text
physical strategy and PreparedPrint remain owned by core-printer
physical preview consumes PreparedPrint downstream
ESC/POS consumes PreparedPrint downstream
core-printer does not depend on preview/ESC-POS
prepared-artifact contracts expose no platform file types
transport delivers bytes without document semantics
```

### 64.4 Physical output

If the feature changes what can physically come out of a printer, that behavior MUST be represented in `PreparedPrint`.

The physical preview MUST represent the resulting physical plan according to the preview accuracy contract.

An output-affecting feature is not complete if the preview still shows the old behavior.

### 64.5 Goldens

If exact transformation output is a contract, the feature MUST add or update the relevant golden.

Golden changes MUST be intentional and reviewed.

### 64.6 Hardware

Hardware-facing functionality SHOULD have a numbered hardware test where practical.

If the feature is release-critical, the applicable physical test MUST be executed before release.

### 64.7 Portable formats

Changes affecting `.td`, `.tcfg`, `PrinterInstance`, or other persistent state MUST address:

```text
serialization
validation
compatibility
migration
invalid inputs
security/resource implications
```

as applicable.

### 64.8 Security and resource handling

Features consuming untrusted data—including portable JSON, imported fonts, device metadata, or artifact backing stores—MUST have appropriate invalid-input, security, and resource-limit coverage.

No feature is complete if its safe operation depends on adding limits "later."

### 64.9 UI

Significant UI behavior requires appropriate shared Compose tests and accessibility consideration.

Platform-specific tests are additionally required where OS behavior is involved.

### 64.10 Offline behavior

Tests MUST NOT introduce a hidden network requirement into workflows that are required to operate offline.

### 64.11 Regression requirement

A practical bug fix SHOULD include an automated regression test.

Where automation is impractical, a permanent manual verification procedure SHOULD be added or updated.

### 64.12 Completion rule

A feature is **not done** because:

```text
it compiles
a demo works once
a preview looks plausible
one printer happened to print it
a golden was regenerated until CI passed
```

It is done when its implementation, automated verification, relevant specifications, failure handling, architecture constraints, compatibility implications, and required physical verification collectively satisfy the applicable contracts.

---

## 65. Pull Request Review Expectations

Reviewers SHOULD treat tests as part of the design.

For nontrivial changes, review should answer:

```text
What contract changed?
Which layer owns that contract?
What automated test proves the behavior?
Could a property test expose additional edge cases?
Did a golden change, and why?
Does the change affect persisted compatibility?
Does it consume untrusted input?
Does it affect physical output?
Does PreparedPrint represent the change?
Does physical preview expose the change from the same finalized plan/artifacts?
Does the active protocol encoder merely serialize the resolved plan?
Did protocol-family validation change?
Could partial output occur?
Does a hardware test need to be added?
```

A change that modifies printer output without corresponding preparation and preview tests deserves heightened scrutiny.

---

## 66. Required Test Invariants Summary

The repository MUST preserve at least the following invariants through automated tests as the relevant features exist:

```text
.td remains printer-independent

.td/.tcfg strict JSON validity is not weakened by parser-library leniency

duplicate JSON keys and unknown v1 properties cannot be silently lost before validation

ThermalDocument + LayoutConstraints
→ deterministic LogicalDocument

Unicode 18.0 UAX #29 grapheme segmentation is pinned

Unicode 18.0 UAX #14 line-breaking behavior is pinned

tabs advance to deterministic four-column stops

default raster typography uses controlled bundled font bytes

unsupported emoji/RTL content diagnoses rather than silently corrupting output

semantic checklist markers do not depend on emoji/icon/platform fonts

packed raster row bytes
= ceil(width / 8)

padding bits are deterministic

raster band splitting loses no rows

wide-document tiling loses no intended coordinates

wide-document duplication occurs only inside intentional overlap

archive paths never escape the document namespace

malformed portable documents fail safely

unsupported schema versions fail safely

.tcfg v1 accepts only the registered ESC/POS protocol family

unknown protocol families never fall back to ESC/POS or dynamically load code

serialization round trips preserve semantic equality

validated profile overrides cannot bypass profile safety

PrinterProfile remains distinct from PrinterInstance

PreparedPrint contains every output-affecting physical decision

PreparedPrint may reference bounded immutable finalized prepared artifacts

physical preview consumes PreparedPrint/artifacts without alternate layout or raster generation

ESC/POS consumes PreparedPrint/artifacts without re-layout/re-dithering/reselection/regeneration

preview and encoder observe identical finalized prepared raster/graphic content

missing/corrupt finalized artifacts fail rather than silently regenerate

changing transport does not require physical re-preparation

partial transmission never triggers automatic retry

Core parsing/font fallback never silently performs network I/O

long documents do not require one giant preview bitmap

large print jobs do not require one giant encoded ByteArray

font/shaping caches and prepared-artifact storage remain bounded

hardware evidence is scoped to the printer/profile capability actually tested
```

These invariants are more important than incidental implementation details.

---

## 67. Evolution of This Strategy

Testing infrastructure may evolve as Kotlin Multiplatform, Compose Multiplatform, Android tooling, Web tooling, fuzzing support, or repository requirements change.

Changes to tooling are permitted provided that:

```text
the architectural test contracts remain intact
critical transformations remain covered
golden determinism is preserved
security coverage is not weakened
Android mandatory gates remain reliable
persistent compatibility remains tested
hardware verification remains traceable
```

If the architecture introduces a new output-affecting stage, protocol family, persistent format, transport class, font/input parser, prepared-artifact store, or other untrusted-input boundary, this document MUST be updated to describe its testing obligations.

---

## 68. Final Engineering Rule

RastrIO's test suite is not a separate verification layer added after implementation.

For every feature:

```text
design
implementation
tests
diagnostics
specification
```

form one engineering change.

The repository should make it difficult to accidentally alter physical output, portable-file semantics, security behavior, or retry safety without a corresponding test failure.

That is the standard required for a printing stack whose output leaves the screen and becomes a physical artifact.