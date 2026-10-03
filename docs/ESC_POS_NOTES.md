# Rastrio ESC/POS Engineering Notes

**Status:** Living engineering notebook  
**Notebook revision:** 1.1  
**Project:** Rastrio  
**Protocol family covered by this notebook:** ESC/POS-compatible thermal printers  
**Current available reference device:** Helett H50i BillQuick Go  
**Last baseline review:** 2026-10-02  
**Governing baseline:** `PRD.md` v2.1, `docs/ARCHITECTURE.md`, and `docs/TCFG_SPEC.md`

> This file records evidence about ESC/POS protocol behavior, compatibility, dialects, printer quirks, transport interactions, and physical hardware observations. It is deliberately not a claim that every ESC/POS-compatible printer behaves like an Epson reference printer, the H50i, or any other single model.

---

## 1. Purpose

`docs/ESC_POS_NOTES.md` is Rastrio's living protocol notebook.

It exists to capture the engineering evidence needed to implement, test, and maintain ESC/POS-compatible output without turning unverified assumptions into permanent application behavior.

This notebook applies to `.tcfg` profiles whose validated protocol family is:

```text
protocol.family = escpos
```

A future non-ESC/POS printer language requires its own protocol-specific engineering evidence and trusted encoder work. It MUST NOT be documented here as though it were merely another ESC/POS dialect.

This document SHOULD grow as Rastrio gains:

- automated byte-level encoder tests;
- new `PrinterProfile` capabilities and protocol strategies;
- physical printer test results;
- Bluetooth/SPP and USB transport observations;
- confirmed clone-printer quirks;
- QR, barcode, status, raster, and cutter support;
- failures that require profile quirks, diagnostics, or safer defaults.

This document is intentionally less formal than `ARCHITECTURE.md`, `TCFG_SPEC.md`, or `PREVIEW_SPEC.md`. It may contain hypotheses, experiments, rejected approaches, and model-specific notes, provided the evidence level is explicit.

It MUST NOT silently promote an inference into a verified printer capability.

---

## 2. Relationship to the Rastrio Architecture

For `.tcfg` schema v1, the ESC/POS protocol boundary is:

```text
PreparedPrint
+ finalized immutable prepared artifacts
    ↓
core-escpos
    ↓
encoded byte chunks / stream
    ↓
platform PrinterTransport
    ↓
printer
```

`core-escpos` is the trusted encoder for the `escpos` protocol-family variant. A future protocol family would use a separate trusted encoder while preserving the same `PreparedPrint` authority boundary.

The corresponding preview path is:

```text
PreparedPrint
    ↓
core-preview
    ↓
PrintPreview
```

The same immutable `PreparedPrint` MUST be authoritative for both paths.

### 2.1 What `core-escpos` may do

`core-escpos` MAY:

- emit initialization commands;
- serialize already-selected native text state;
- serialize an already-selected printer font;
- serialize an already-selected code page;
- serialize already-prepared raster bands or read finalized raster/graphic content through the prepared-artifact contract;
- serialize an already-selected QR strategy;
- serialize feed operations;
- serialize automatic-cut operations;
- serialize other protocol operations explicitly represented by `PreparedPrint`;
- split the logical encoded stream into encoder-produced chunks where doing so does not change physical semantics.

### 2.2 What `core-escpos` must not do

`core-escpos` MUST NOT:

- lay out or re-layout text;
- wrap or re-wrap lines;
- shape text;
- select a code page;
- decide whether text is native or raster;
- resize or dither images;
- decide raster resolution;
- choose native versus raster QR;
- choose native versus raster barcode;
- change QR dimensions or error correction;
- segment wide documents;
- change segment overlap;
- create cut decisions that are not already in `PreparedPrint`;
- infer printer geometry from a device name;
- special-case the H50i or any other model inside generic encoder logic;
- re-rasterize or regenerate finalized prepared artifacts because backing storage is banded, chunked, or spool-backed;
- interpret a prepared-artifact identifier as an attacker-controlled filesystem path.

If a decision can change physical output, `core-printer` MUST resolve it before encoding begins.

### 2.3 Protocol knowledge must not leak upward

Raw ESC/POS command details recorded in this file MUST NOT leak into:

- `.td`;
- `ThermalDocument`;
- Markdown compilation;
- template state;
- logical layout semantics;
- `LogicalDocument`;
- Compose preview/layout code.

`.td` stores document semantics, not printer commands.

---

## 3. Evidence Model

Every durable observation SHOULD carry an explicit evidence classification.

### 3.1 Evidence classes

| Code | Evidence class | Meaning |
|---|---|---|
| `DOC` | Specification/documentation-derived | Supported by a protocol or vendor reference. This proves what the referenced documentation says, not that a particular clone printer implements it correctly. |
| `BYTE` | Automated byte-level test | Confirmed by deterministic encoder/golden tests. This proves Rastrio emitted the intended bytes, not that hardware accepted them. |
| `HW` | Physical hardware test | Confirmed on physical hardware with a recorded test procedure and result. |
| `INF` | Inferred/unverified | Plausible engineering hypothesis, reverse-engineered expectation, or untested assumption. MUST NOT be used as established capability evidence. |
| `MODEL` | Printer-specific | Observation applies to an identified printer model, firmware, hardware revision, or profile. Normally combined with `HW`, `DOC`, or both. |

Multiple evidence codes MAY apply to one observation, for example `DOC + BYTE + HW + MODEL`.

### 3.2 Verification status

Use one of these statuses:

- `TBD` — not yet investigated;
- `Candidate` — documented or hypothesized but not adopted as a reliable Rastrio behavior;
- `Byte Verified` — encoder output covered by automated exact-byte tests;
- `Hardware Observed` — directly observed on named physical hardware, but not yet captured by the repository's permanent numbered hardware-test procedure and evidence record;
- `Hardware Verified` — behavior reproduced through a recorded/repeatable physical hardware test with sufficient context;
- `Adopted` — Rastrio architecture/project rule intentionally accepted as the current engineering baseline; this status is for project-owned rules, not a substitute for hardware evidence;
- `Rejected` — tested and found unsuitable or incorrect for the stated scope;
- `Superseded` — retained for history but replaced by a newer observation or strategy.

`Hardware Observed` SHOULD identify the device/context and what was actually observed.

`Hardware Verified` SHOULD identify the printer profile/version and hardware test reference.

A `Hardware Observed` fact MAY guide the next experiment, but a maintained production-profile claim SHOULD be promoted to `Hardware Verified` when that claim materially affects physical output and a numbered hardware test is practical.

### 3.3 Observation ID convention

Stable observations use:

```text
ESC-OBS-0001
ESC-OBS-0002
...
```

Rules:

1. IDs MUST NOT be renumbered after publication.
2. Deleted or invalidated observations SHOULD remain as `Rejected` or `Superseded` when they have historical value.
3. New observations receive a new monotonically increasing ID.
4. Hardware test IDs such as `HW-001` are separate identifiers and SHOULD be cross-referenced rather than reused as observation IDs.

### 3.4 Observation template

Use this template for substantial additions:

```markdown
### ESC-OBS-XXXX — Short title

- **Feature:** ...
- **Command/behavior:** `AA BB CC ...`
- **Scope:** Generic ESC/POS / H50i / specific model or dialect
- **Evidence:** DOC / BYTE / HW / INF / MODEL
- **Status:** TBD / Candidate / Byte Verified / Hardware Observed / Hardware Verified / Adopted / Rejected / Superseded
- **Source/test:** vendor document, golden test, `HW-xxx`, commit, profile version
- **Observed result:** ...
- **Date:** YYYY-MM-DD
- **Notes:** ...
```

---

## 4. Source Registry

Sources are evidence inputs, not universal compatibility guarantees.

| Source key | Source | Use in this notebook | Limitation |
|---|---|---|---|
| `PRD-2.1` | Rastrio Product Requirements Document v2.1 | Governing architecture, roadmap, test IDs, required protocol capabilities | Does not establish exact command behavior for a particular printer |
| `EPSON-CMDREF` | Epson ESC/POS Command Reference for TM Printers, consulted 2026-10-02 | Generic command names, byte forms, parameter semantics, and model-dependent warnings | Epson TM behavior is not proof of third-party clone behavior |
| `H50I-VENDOR` | Helett H50i vendor protocol/manual material | `TBD — locate and record authoritative material if available` | No authoritative protocol manual is currently recorded here |
| `H50I-SELFTEST-2026-10-02` | H50i physical self-test/configuration page reported during Rastrio engineering review | Printer-reported model/configuration/capability observations | Self-test output does not prove host-accessible command selectors or every supported capability |
| `H50I-RAWBT-2026-10-02` | Physical RawBT-based ESC/POS compatibility test reported during Rastrio engineering review | Confirms that the observed H50i accepts ESC/POS-compatible printing through the tested Android/RawBT path | Exact transmitted bytes/individual command families were not captured here; not a substitute for Rastrio byte-level + numbered hardware tests |
| `RASTRIO-BYTE-TESTS` | Rastrio `core-escpos` golden/byte-level tests | Exact encoder byte behavior | Does not prove physical printer compatibility |
| `RASTRIO-HW-TESTS` | `docs/HARDWARE_TESTS.md` | Physical verification and regression history | Must identify device/profile/context |

When a source has a revision, firmware version, document date, or stable document identifier, record it. A generic web page title without a revision is weaker evidence than a versioned technical manual.

---

## 5. Baseline Observation Index

The following entries establish the initial notebook. Generic command entries are reference candidates for ESC/POS-compatible dialects; they are **not H50i capability claims** until the H50i is physically verified.

| ID | Feature | Command/Behavior | Scope | Evidence | Status | Notes |
|---|---|---|---|---|---|---|
| `ESC-OBS-0001` | Initialization | `1B 40` (`ESC @`) | Epson ESC/POS reference family | `DOC` | Candidate | Initialize/reset printer modes. H50i: `TBD — verify on H50i`. |
| `ESC-OBS-0002` | Line feed | `0A` (`LF`) | Epson ESC/POS reference family | `DOC` | Candidate | Print buffered line and feed according to current line spacing. H50i: TBD. |
| `ESC-OBS-0003` | Bold/emphasis | `1B 45 n` (`ESC E n`) | Epson ESC/POS reference family | `DOC` | Candidate | Emphasized mode on/off. Exact clone behavior may vary. |
| `ESC-OBS-0004` | Underline | `1B 2D n` (`ESC - n`) | Epson ESC/POS reference family | `DOC` | Candidate | Underline off/one-dot/two-dot in Epson semantics. H50i support/widths: TBD. |
| `ESC-OBS-0005` | Alignment | `1B 61 n` (`ESC a n`) | Epson ESC/POS reference family | `DOC` | Candidate | Left/center/right in documented standard mode; command timing/state rules matter. |
| `ESC-OBS-0006` | Font selection | `1B 4D n` (`ESC M n`) | Epson ESC/POS reference family | `DOC` | Candidate | Font set and accepted values are model-dependent. Never assume Font A/B geometry from command name alone. |
| `ESC-OBS-0007` | Character size | `1D 21 n` (`GS ! n`) | Epson ESC/POS reference family | `DOC` | Candidate | Width/height multipliers encoded in `n`; accepted range can be model-dependent. |
| `ESC-OBS-0008` | Code page | `1B 74 n` (`ESC t n`) | Epson ESC/POS reference family | `DOC` | Candidate | Mapping of `n` to code table is model/dialect dependent. Profile must carry validated mapping. |
| `ESC-OBS-0009` | Feed lines | `1B 64 n` (`ESC d n`) | Epson ESC/POS reference family | `DOC` | Candidate | Prints buffer and feeds `n` lines; physical distance depends on line spacing and printer behavior. |
| `ESC-OBS-0010` | Raster image | `1D 76 30 m xL xH yL yH ...` (`GS v 0`) | ESC/POS strategy candidate | `DOC` | Candidate | PRD names `GS v 0` as an example raster strategy; current Epson documentation labels it obsolete. Clone support may nevertheless be common. H50i: TBD. |
| `ESC-OBS-0011` | Native QR | `1D 28 6B ...` (`GS ( k`) family | Epson ESC/POS reference family | `DOC` | Candidate | Multi-function command family; profile must select a validated QR strategy. H50i: TBD. |
| `ESC-OBS-0012` | Native barcode | `1D 6B ...` (`GS k`) family | Epson ESC/POS reference family | `DOC` | Candidate | Symbology and data rules depend on function/dialect. Barcode support is not required merely because parser accepts a barcode operation. |
| `ESC-OBS-0013` | Cut | `1D 56 ...` (`GS V`) family | Epson ESC/POS reference family | `DOC` | Candidate | Full/partial semantics and supported parameter values are model-dependent. The observed H50i unit has no automatic cutter, so this strategy is not applicable to that profile; see `ESC-OBS-0110`. |
| `ESC-OBS-0014` | Real-time status | `10 04 n` (`DLE EOT n`) | Epson ESC/POS reference family | `DOC` | Candidate | Requires bidirectional handling and careful sequencing. H50i support: TBD. |
| `ESC-OBS-0015` | Architecture | Code-page selection occurs before encoding | Rastrio | `PRD-2.1` | Adopted | `core-printer` selects code page; `core-escpos` serializes it. |
| `ESC-OBS-0016` | Architecture | Raster/native strategy occurs before encoding | Rastrio | `PRD-2.1` | Adopted | Encoder may not re-decide native vs raster. |
| `ESC-OBS-0017` | Raster streaming | Raster output is banded/spoolable; no giant job-wide byte array | Rastrio | `PRD-2.1` | Adopted | Raster bands/prepared artifacts are physical-preparation concerns; transport chunks are separate. |
| `ESC-OBS-0018` | Transport | Printer buffering and transport buffering are separate | Rastrio | `PRD-2.1` | Adopted | Effective send policy is safe intersection of printer guidance and transport constraints. |
| `ESC-OBS-0019` | Retry behavior | No automatic retry after ambiguous partial transmission | Rastrio | `PRD-2.1` | Adopted | Duplicate/partial paper output may already have occurred. |
| `ESC-OBS-0020` | Protocol family | `.tcfg` schema v1 accepts `protocol.family = escpos`; future families require separate trusted encoders | Rastrio | `PRD-2.1` | Adopted | An unknown family must not fall back to ESC/POS or load profile-supplied code. |
| `ESC-OBS-0021` | Prepared artifacts | ESC/POS encoding consumes finalized prepared raster/graphic content without regenerating it | Rastrio | `PRD-2.1` | Adopted | Preview and encoder must observe identical finalized content. |

---

## 6. Initialization

### 6.1 Generic documented behavior

`ESC @` is represented as:

```text
Hex: 1B 40
ASCII: ESC @
Semantic intent: initialize printer / reset relevant runtime modes
Evidence: DOC (`EPSON-CMDREF`)
Rastrio status: Candidate protocol operation
H50i: TBD — verify on H50i
```

Rastrio SHOULD use initialization only when the selected protocol strategy/profile says it is appropriate.

The presence of `Initialize` in an encoded stream MUST NOT be used as a substitute for explicit prepared state. The encoder should still serialize the prepared font, code page, styles, alignment, and other state needed for deterministic output rather than relying on undocumented device defaults.

### 6.2 Questions to verify

- Does H50i accept `1B 40` reliably over Bluetooth SPP?
- Does initialization clear buffered-but-unprinted content on H50i?
- Which state survives initialization?
- Does initialization alter density, code page, or other persistent/nonvolatile settings?
- Is an initialization command required at the start of every Rastrio print job, merely recommended, or actively harmful in any workflow?

### 6.3 Planned test linkage

- Encoder golden: exact initialization bytes.
- Hardware: include initialization as part of `HW-001 Simple ASCII` once the protocol sequence is finalized.

---

## 7. Native Text

Native text means bytes interpreted by the printer's resident character generator rather than text rasterized by Rastrio.

### 7.1 Rastrio ownership rules

Before `core-escpos` receives a native-text operation, `PreparedPrint` must already contain the decisions needed to serialize it, conceptually including:

```text
text or encoded text source
resolved x/y or line geometry
selected native printer font
selected code page
bold state
underline state
width/height scale
resolved alignment/state requirements
```

The encoder MUST NOT test glyph coverage and switch strategy during serialization.

### 7.2 Native text evidence required per printer profile

For any profile that advertises native text, verify or document:

- supported font identifiers;
- cell width and height for each font at normal size;
- line height and baseline behavior where relevant;
- width/height scale support;
- code-page mapping;
- bold/emphasized behavior;
- underline behavior;
- alignment behavior;
- interaction between style commands;
- whether style changes apply immediately or only at line boundaries;
- whether command bytes can appear safely between text runs on one physical line;
- whether unsupported byte values print replacement glyphs, blanks, or corrupt state.

### 7.3 Initial hardware references

The PRD reserves the following physical tests:

```text
HW-001 Simple ASCII
HW-002 Line wrapping
HW-003 Alignment
HW-004 Bold/underline
HW-005 Long text
```

The detailed procedures and results belong in `docs/HARDWARE_TESTS.md`; this notebook records the protocol interpretation derived from those results.

---

## 8. Font Selection

### 8.1 Generic command family

Candidate documented command:

```text
Hex: 1B 4D n
ASCII: ESC M n
Semantic intent: select character font
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

Epson documentation uses values such as Font A and Font B, but accepted values and defaults differ across models.

### 8.2 Rastrio rule

A profile MUST NOT merely say “supports `ESC M`.” It needs enough validated font geometry for `core-text` and `core-printer` to make layout decisions.

A valid native-font capability therefore needs, directly or through a referenced strategy, information equivalent to:

```text
profile font identifier
protocol font selector
cell width / advance model
line metrics
supported scaling
style interactions
code-page compatibility if relevant
confidence / quirk information
```

### 8.3 H50i

The printer self-test page reports:

```text
Font: 12x24
FontVer: 1.00.00
```

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This supports the narrow statement that the tested unit reports a `12x24` resident/default font geometry and a font-version string of `1.00.00`.

It does **not** yet establish:

```text
the ESC M selector value for that font
whether 12x24 is the only resident font
whether the dimensions are exact physical cell advance under every mode
default state after ESC @
line advance/baseline behavior
bold/underline/scaling interactions
firmware-to-firmware stability
```

Those remain to be verified through controlled Rastrio byte/hardware tests before they become maintained production-profile claims.

---

## 9. Bold / Emphasized Text

### 9.1 Generic command family

Candidate documented command:

```text
Hex: 1B 45 n
ASCII: ESC E n
Semantic intent: emphasized mode on/off
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 9.2 Compatibility questions

A clone may:

- implement emphasis as a darker overstrike-like effect;
- change effective glyph width or darkness unexpectedly;
- ignore the command;
- support only `0`/`1` rather than all documented equivalent values;
- retain style state across lines or jobs differently from reference behavior.

These are hypotheses until tested on a concrete device.

### 9.3 Rastrio requirement

If bold changes native text geometry for a profile, that fact MUST be represented in the profile/text metrics used during preparation. The encoder must not discover a geometry change after layout.

### 9.4 H50i

`TBD — verify on H50i` under `HW-004 Bold/underline`.

Record:

- command bytes sent;
- selected font and size;
- text fixture;
- whether visual emphasis is obvious;
- whether measured width changes;
- whether subsequent plain text returns to normal after explicit off command;
- whether `ESC @` resets the state.

---

## 10. Underline

### 10.1 Generic command family

Candidate documented command:

```text
Hex: 1B 2D n
ASCII: ESC - n
Semantic intent: underline off / enabled modes
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 10.2 Compatibility risks

Do not assume every clone distinguishes one-dot and two-dot underline modes even if it accepts the command.

Also verify:

- underline through spaces;
- underline of scaled text;
- interaction with Font A/B equivalents;
- whether underline affects line height or clipping;
- state reset behavior.

### 10.3 H50i

`TBD — verify on H50i` under `HW-004 Bold/underline`.

---

## 11. Alignment

### 11.1 Generic command family

Candidate documented command:

```text
Hex: 1B 61 n
ASCII: ESC a n
Typical documented intent:
  n=0 left
  n=1 center
  n=2 right
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 11.2 Important state note

In Epson documentation, `ESC a` has state/position constraints, including line-boundary behavior in standard mode. Rastrio SHOULD therefore serialize alignment as a state transition at a safe operation boundary rather than blindly inserting it in the middle of arbitrary native text bytes.

### 11.3 Rastrio geometry rule

Alignment geometry is already resolved in `PreparedPrint`.

If a printer-native alignment command is used, it is a serialization strategy for the prepared result, not permission for the encoder to recompute where content belongs.

A profile whose alignment command is unreliable MAY require an alternative prepared/serialization strategy, but that strategy decision belongs in `core-printer` and profile capability data.

### 11.4 H50i

`TBD — verify on H50i` with `HW-003 Alignment`.

Test at minimum:

- left;
- center;
- right;
- short and near-full-width lines;
- alignment transitions between consecutive lines;
- interaction with scaled text;
- alignment after raster output if mixed jobs are supported.

---

## 12. Feeds and Line Spacing

### 12.1 Line feed

Candidate command:

```text
Hex: 0A
ASCII: LF
Semantic intent: print buffered line and feed according to current line spacing
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 12.2 Feed by lines

Candidate command:

```text
Hex: 1B 64 n
ASCII: ESC d n
Semantic intent: print buffered data and feed n lines
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 12.3 Rastrio requirements

Feed is a physical operation when it affects paper output. Therefore:

- significant feed decisions SHOULD be represented in `PreparedPrint`;
- the encoder serializes the already-requested feed;
- preview SHOULD represent feeds where they materially affect geometry;
- transport chunking MUST NOT add or remove feed commands.

### 12.4 To verify per profile

- default line spacing;
- supported explicit line-spacing commands if Rastrio adopts them;
- maximum reliable feed count per command;
- whether repeated `LF` and `ESC d n` are physically equivalent for intended use;
- feed behavior before cut;
- feed behavior after raster bands.

### 12.5 H50i

`TBD — verify on H50i`.

---

## 13. Character Size / Native Scaling

### 13.1 Generic command family

Candidate documented command:

```text
Hex: 1D 21 n
ASCII: GS ! n
Semantic intent: select native character width/height magnification
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 13.2 Rastrio rule

`PreparedPrint` may contain resolved width/height scale for native text. The profile must declare which scales are reliable and what geometry they produce.

Do not infer a physical size from the nominal multiplier alone. The base font metrics are profile data.

### 13.3 H50i

```text
TBD — verify on H50i:
- accepted scale values
- maximum reliable width scale
- maximum reliable height scale
- actual cell geometry for each supported scale
- clipping/wrapping near printable edge
- style interaction
```

---

## 14. Code Pages

### 14.1 Generic command family

Candidate documented command:

```text
Hex: 1B 74 n
ASCII: ESC t n
Semantic intent: select character code table/page
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 14.2 Critical compatibility rule

The numeric value `n` is not globally trustworthy across all ESC/POS-compatible devices.

A `PrinterProfile` MUST carry a validated mapping between Rastrio's code-page capability and the protocol selector used by that printer/dialect.

Do not encode assumptions such as:

```text
"CP437 always equals n=0 on every ESC/POS-compatible printer"
```

as generic Core truth.

### 14.3 Preparation ownership

Code-page selection belongs to `core-printer`.

The flow is:

```text
text content
    ↓
glyph/code-page capability analysis
    ↓
core-printer selects native page or raster fallback
    ↓
PreparedPrint records the resolved choice
    ↓
core-escpos emits ESC t / equivalent bytes for that chosen strategy
```

### 14.4 H50i code-page evidence and discovery plan

The printer self-test page reports:

```text
Charset: CP437
```

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This supports the narrow conclusion that the tested firmware identifies CP437 as the current/default charset in the observed self-test state.

It does **not** establish:

```text
which ESC t selector value chooses CP437
whether CP437 is available after every reset/state transition
the complete set of supported code pages
exact byte-to-glyph behavior for every CP437 position
whether the label is fully standards-conformant
```

The selector mapping therefore remains `TBD — verify on H50i` using a controlled character-set test sheet.

For each candidate selector:

1. initialize to a known state;
2. select exactly one candidate code page;
3. print a fixed byte matrix, not Unicode text transformed implicitly by the test harness;
4. photograph/record the printed glyph grid;
5. compare against expected code-page mappings;
6. record undefined/replacement glyph behavior;
7. repeat after power cycle and initialization where useful.

The test harness MUST distinguish source Unicode from actual transmitted bytes.

### 14.5 Printer Lab linkage

The PRD explicitly expects character-set tests in Printer Lab. Those tests SHOULD generate reproducible fixtures and reference observation IDs in this document.

---

## 15. Unicode Limitations

ESC/POS-compatible native text is generally not equivalent to accepting arbitrary Unicode strings.

Rastrio's initial architecture explicitly requires raster fallback for unsupported Unicode, complex scripts, combining characters, emoji policy, and other content that cannot be represented reliably by a validated native strategy.

### 15.1 Rastrio initial fallback rule

If a resolved physical line contains content that cannot be represented reliably as native printer text, the initial implementation may rasterize the complete physical line.

Per-glyph native/raster mixing is deferred.

### 15.2 Encoder rule

The encoder MUST NOT receive a Unicode line and decide, during serialization, whether it is printable.

It receives either:

- a prepared native-text operation with an already-selected code page/encoding strategy; or
- prepared raster text.

### 15.3 H50i

Native Unicode capability beyond validated code pages is `TBD — verify on H50i`.

Planned PRD hardware references:

```text
HW-014 Unicode raster text
HW-015 Mixed supported/unsupported text
HW-016 Complex-script sample
```

These tests validate Rastrio's fallback path even if the H50i has no useful native Unicode support.

---

## 16. Raster Commands

### 16.1 Candidate `GS v 0` strategy

The PRD gives `GS v 0` as an example raster strategy that a profile may select.

Generic documented form:

```text
Hex:
1D 76 30 m xL xH yL yH d1...dk

Semantic intent:
print raster bit image

Horizontal byte count:
xBytes = xL + 256*xH

Raster rows:
yDots = yL + 256*yH

Payload byte count:
k = xBytes * yDots
```

Evidence: `DOC` (`EPSON-CMDREF`) plus architecture mention in `PRD-2.1`.

Status: `Candidate`.

Important: current Epson documentation labels `GS v 0` obsolete. That does **not** mean Rastrio must reject it. Many ESC/POS-compatible devices may implement older command families. It means support MUST be profile/dialect-driven and verified for each target rather than treated as universal.

### 16.2 Rastrio raster ownership

`core-raster` and `core-printer` own:

- image sizing;
- grayscale/tonal adjustment;
- threshold/dithering;
- one-bit packing contract;
- physical raster dimensions;
- raster/native strategy selection;
- raster band planning where band geometry is part of safe printer preparation.

`core-escpos` owns serialization of already-prepared raster data into the selected command family.

That raster data MAY be supplied through the bounded immutable prepared-artifact contract. The encoder may stream/read finalized chunks, but it MUST NOT re-run image scaling, text shaping, dithering, QR generation, checklist-marker generation, or packing decisions that belong upstream.

### 16.3 Required raster invariants

- width not divisible by 8 must be packed correctly;
- bit order must be explicit;
- padding bits must be deterministic;
- long raster output must not require one giant job-wide allocation;
- finalized raster/graphic data may be spool-backed but must remain immutable;
- preview and encoding must use the exact same finalized prepared raster/graphic content;
- missing/corrupt finalized artifacts must fail rather than trigger regeneration;
- re-dithering or re-rasterization inside the encoder is forbidden.

### 16.4 H50i

```text
TBD — verify on H50i:
- whether GS v 0 is supported
- accepted m values
- maximum reliable xBytes/yDots per command
- whether full printable width is accepted
- whether large bands stall, truncate, or corrupt output
- bit order
- row order
- density/thermal limitations
- required pacing between raster bands
```

Planned hardware tests:

```text
HW-007 Checkerboard
HW-008 Threshold gradient
HW-009 Bayer gradient
HW-010 Atkinson gradient
HW-011 Floyd–Steinberg gradient
HW-012 Full-width image
HW-013 Long image
```

---

## 17. Raster Banding

Raster banding is not transport chunking.

### 17.1 Definitions

**Raster band**  
A physically meaningful portion of prepared raster output, generally with complete raster rows and dimensions suitable for the selected printer strategy.

**Transport write chunk**  
A byte slice used by Bluetooth, USB, or another transport when transmitting the already-encoded stream.

### 17.2 Architectural rule

```text
Prepared raster
    ↓
printer-safe raster bands
    ↓
ESC/POS serialization
    ↓
transport-safe byte chunks
```

Changing a Bluetooth write chunk size MUST NOT cause Rastrio to re-dither or re-layout the document.

### 17.3 Profile data to gather

A profile may need:

- preferred raster band height;
- known printer receive/buffer guidance;
- raster strategy identifier;
- quirk such as “requires smaller bands after N rows” if physically confirmed;
- pacing guidance when this is a printer-side requirement rather than transport-only behavior.

### 17.4 H50i

`TBD — empirically determine a reliable banding envelope on H50i.`

The experiment SHOULD vary one dimension at a time and record:

- raster width;
- rows per band;
- payload bytes per band;
- delay/pacing configuration;
- Bluetooth write chunk size;
- total job length;
- whether truncation, reset, garbage, or thermal fading occurs.

Do not infer a permanent numeric limit from a single successful print.

---

## 18. QR Commands

### 18.1 Generic native QR family

A common documented Epson command family is:

```text
Hex prefix: 1D 28 6B ...
ASCII: GS ( k ...
Semantic intent: configure/store/print two-dimensional symbols including QR
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

The family contains multiple functions. Rastrio SHOULD implement native QR as a validated protocol strategy, not as arbitrary raw command templates loaded from `.tcfg`.

### 18.2 Preparation ownership

`.td` stores semantic QR intent such as payload, error-correction preference, requested logical size, and alignment.

`core-printer` decides:

```text
NativeQr
or
RasterQr
```

and records the selected dimensions and parameters in `PreparedPrint`.

`core-escpos` only serializes the already-selected `NativeQr` operation.

### 18.3 H50i

The H50i self-test page physically prints a QR symbol.

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This proves that the tested firmware can internally generate a QR symbol in its self-test path.

It does **not** prove:

```text
host-accessible GS ( k support
the exact QR command family/dialect
Model 2 selector behavior
module-size range
error-correction selector behavior
payload-length limits
binary/non-ASCII payload behavior
alignment behavior
buffer sensitivity
scan reliability for Rastrio-generated commands
```

Those remain to be verified through:

```text
HW-017 Native QR
HW-018 Raster QR
HW-019 QR size variations
```

Native QR SHOULD only become the H50i profile's reliable `Auto` choice after Rastrio-controlled command-path verification.

---

## 19. Barcode Commands

Barcode is a supported `PreparedPrint` concept in the architecture, but it is not required as an initial `.td` v1 block.

### 19.1 Generic command family

Candidate documented family:

```text
Hex prefix: 1D 6B ...
ASCII: GS k ...
Semantic intent: print one-dimensional barcode
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate / future use
```

### 19.2 Rules

- Do not add barcode support to a profile merely because `GS k` is common.
- Symbology, payload validation, length limits, HRI behavior, width/height commands, and terminator conventions can vary.
- `core-printer` must select native versus raster strategy before encoding.
- Unsupported native configuration must produce a diagnostic or raster fallback according to the applicable feature design.

### 19.3 H50i

The H50i self-test page physically prints an EAN-13 barcode.

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This establishes an internal firmware EAN-13 generation capability in the self-test path.

It does **not** establish host-accessible `GS k` support, symbology selector values, payload framing, HRI behavior, width/height parameters, or terminator rules.

Do not enable a native EAN-13 profile strategy until the host command path is verified.

---

## 20. Cut Commands

### 20.1 Generic command family

Candidate documented Epson family:

```text
Hex prefix: 1D 56 ...
ASCII: GS V ...
Semantic intent: select cut mode and cut paper
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

Parameter values and supported modes are model-dependent.

### 20.2 Rastrio ownership

A cutter operation must already exist in `PreparedPrint` as an operation such as `AutomaticCut`.

A printer without a cutter may instead receive printed `ManualCutGuide` content.

These are not equivalent:

```text
ManualCutGuide = printed content
AutomaticCut   = physical device operation
```

Physical preview must display an automatic-cut boundary as an annotation, not as rasterized or native text that would accidentally print.

### 20.3 H50i

The observed H50i unit does not have automatic-cut capability.

Evidence:

```text
direct physical observation
HW + MODEL
Status: Hardware Observed
```

The H50i profile SHOULD therefore keep automatic cutter support disabled.

No `GS V` full/partial cut strategy should be emitted for this profile.

A permanent numbered hardware record should capture the no-cutter observation before the profile is treated as production-maintained.

---

## 21. Full Versus Partial Cut

Generic ESC/POS references contain multiple cut parameter forms and historical command variants. Rastrio must avoid assuming identical semantics across clones.

For each cutter-capable profile record:

- command strategy identifier;
- supported mode: full, partial, or both;
- parameter mapping;
- required pre-cut feed;
- minimum distance from last printed content if relevant;
- whether partial cut physically leaves a predictable bridge;
- whether command is ignored when paper is not at cutter position;
- whether cut is safe after raster output;
- hardware test reference.

### H50i

```text
Automatic cutter: absent on observed unit
Full cut:         unsupported for this profile
Partial cut:      unsupported for this profile
```

This is a model/profile conclusion, not a statement about ESC/POS printers generally.

No cut command should be emitted solely because a device is marketed as a thermal receipt printer.

---

## 22. Printer Status Commands

Status querying is explicitly optional/later in the PRD and must only be used where reliable.

### 22.1 Candidate documented command

A common Epson real-time status command is:

```text
Hex: 10 04 n
ASCII: DLE EOT n
Semantic intent: transmit selected real-time printer status
Evidence: DOC (`EPSON-CMDREF`)
Status: Candidate
```

### 22.2 Risks

Status support is one of the most dangerous areas for clone assumptions because:

- some printers may ignore queries;
- some transports or stacks may not expose bidirectional reads cleanly;
- returned bytes may differ in meaning;
- unsolicited bytes can be confused with status replies;
- sending a query while a stream is in progress can interfere with simplistic firmware;
- a successful byte write is not proof of successful paper output.

### 22.3 Completion semantics

Rastrio's baseline `COMPLETED` state means the intended bytes were transmitted according to the transport contract.

Rastrio MUST NOT claim physical print success unless the selected printer protocol/profile/transport provides reliable confirmation.

### 22.4 H50i

`TBD — status query support is not established.`

Do not enable status polling by default until a dedicated test proves reliable request/response semantics over the relevant H50i transport.

---

## 23. Buffer Limitations

Printer buffer limits and transport buffers are separate.

### 23.1 Printer-side observations to record

- approximate payload size at which failures begin;
- whether failure depends on text versus raster;
- whether raster width affects safe band height;
- whether long continuous jobs require pacing;
- whether the printer drops bytes, stalls, disconnects, resets, or prints corrupt output;
- whether a feed/cut boundary flushes behavior;
- whether status/query traffic changes buffer behavior.

### 23.2 Do not overfit

A single failed 8 KiB write, for example, does not establish “the printer buffer is 8 KiB.” The failure may arise from:

- Bluetooth stack buffering;
- RFCOMM implementation;
- application write behavior;
- printer receive buffer;
- raster processing time;
- thermal throttling;
- firmware bugs;
- insufficient pacing.

Record the experimental conditions before creating profile guidance.

### 23.3 H50i

`TBD — determine printer-side buffer guidance empirically.`

Any eventual profile value should be described as safe guidance, not a claim of exact hardware RAM size, unless authoritative documentation exists.

---

## 24. Timing and Pacing Observations

Pacing MAY be needed for some low-cost printers, especially during dense raster output, but no global sleep constant should be invented.

### 24.1 Record separately

- printer-side pacing guidance;
- Bluetooth write chunk size;
- delay between transport writes;
- delay between physical raster bands;
- post-connect settling requirements;
- post-cut or post-status-query timing if verified;
- observed printer busy behavior.

### 24.2 Configuration rule

Where pacing is required:

- prefer validated profile/session parameters;
- distinguish printer guidance from transport mechanics;
- keep defaults conservative but evidence-based;
- avoid UI-exposed magic numbers unless Printer Lab/advanced settings have a concrete need;
- add regression tests for policy intersection where possible.

### 24.3 H50i

The self-test reports:

```text
Print Speed: 60mm/sec (Max)
```

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This is a printer-reported maximum mechanical/output speed. It does **not** establish a Bluetooth write rate, receive-buffer size, safe raster-band rate, or required pacing interval.

No fixed H50i delay is currently established.

---

## 25. Bluetooth Classic / SPP Observations

Android Bluetooth Classic is Rastrio's first production transport. The planned Android implementation includes RFCOMM/SPP connection, paired-device selection, write/chunking behavior, cancellation, and partial-transmission reporting.

### 25.1 Architectural boundary

Bluetooth transport receives already-encoded byte chunks. It MUST NOT know:

- Markdown;
- `ThermalDocument`;
- code pages;
- image dithering;
- QR semantics;
- line wrapping;
- printer layout.

### 25.2 Observations worth recording

- service discovery behavior;
- RFCOMM service UUID actually used;
- whether a standard SPP UUID works;
- connection establishment latency;
- maximum reliable write size per Android/device combination;
- whether writes block or buffer aggressively;
- whether `flush()` has meaningful behavior in the chosen stream implementation;
- disconnect behavior during long writes;
- error surfaced after remote power-off;
- whether bytes already accepted by the OS may still be lost;
- behavior when printer goes out of range;
- reconnection behavior;
- whether application cancellation can stop already-buffered output.

### 25.3 Partial transmission

A Bluetooth failure after some bytes were accepted may have produced partial paper output.

Rastrio MUST surface that ambiguity and MUST NOT automatically retry the print job.

### 25.4 H50i

ESC/POS-compatible printing has been physically observed through RawBT on Android.

Evidence:

```text
H50I-RAWBT-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This is useful protocol-family/transport-path evidence, but RawBT is a third-party application. Without capturing its exact Bluetooth service selection, command bytes, chunk sizes, and pacing behavior, the result does not establish Rastrio's transport implementation details.

The following still require Rastrio-controlled verification:

```text
paired-device identity/locator behavior
RFCOMM/SPP service/UUID
connection reliability
safe write chunk size
need for pacing
disconnect semantics
read/status support
behavior after printer power cycle
```

Relevant hardware test:

```text
HW-006 Disconnect during job / recovery procedure
```

---

## 26. USB Observations

Android USB is a later phase and must not change `ThermalDocument`, `LogicalDocument`, `PreparedPrint`, or preview semantics.

When implemented, record:

- USB VID/PID only in `PrinterInstance` or device-discovery context where appropriate, never in portable document content;
- claimed interface and endpoint types;
- bulk OUT packet/write behavior;
- read endpoint/status behavior if supported;
- permission flow;
- device detach behavior;
- timeout behavior;
- reliable write chunk sizes;
- differences from Bluetooth delivery for the same encoded byte stream.

A change from Bluetooth to USB SHOULD NOT require re-layout or re-dithering.

### H50i

The self-test reports:

```text
USB: Printing Port
```

Evidence:

```text
H50I-SELFTEST-2026-10-02
HW + MODEL
Status: Hardware Observed
```

This supports the presence of a USB printing interface on the observed unit.

It does not establish:

```text
VID/PID
interface/endpoint layout
USB class/protocol details
bulk-transfer behavior
status/read support
safe write size
Rastrio Android USB compatibility
```

Those remain `TBD` until a Rastrio-controlled USB test exists.

---

## 27. Clone-Printer Incompatibilities

“ESC/POS-compatible” MUST be treated as a protocol-family claim, not a guarantee of exact Epson behavior.

Common categories to investigate and record when actually observed include:

- unsupported commands that are silently ignored;
- different parameter values for nominally similar commands;
- misreported or nonstandard code pages;
- Font A/B geometry differences;
- QR commands accepted only for limited payload sizes;
- `GS v 0` width/height limits;
- raster corruption above a certain band size;
- cut command variants;
- missing status responses;
- status bytes with clone-specific meanings;
- state leakage across jobs;
- commands that require a preceding line feed;
- commands that work over USB but fail over Bluetooth due to timing;
- unexpected resets on large raster payloads.

These are investigation categories, not assertions about the H50i or any unnamed clone.

### 27.1 Quirk encoding rule

When a behavior is confirmed:

1. first determine whether it is a capability difference, protocol strategy difference, transport issue, or actual model quirk;
2. represent stable hardware behavior in `PrinterProfile`/`.tcfg` through validated fields or known quirk identifiers;
3. avoid scattered model-name conditionals;
4. add byte-level tests if encoding changes;
5. add/update hardware tests;
6. link the evidence from this notebook.

---

## 28. Known Dialect Variations

This section records protocol families that Rastrio may need to model explicitly.

### 28.1 Raster strategy

Potential strategy identifiers may include concepts equivalent to:

```text
GS_V_0
other validated raster command family — TBD when required
```

The PRD specifically uses `GS v 0` as an example profile strategy. That example MUST NOT become a universal hard-coded raster method.

### 28.2 QR strategy

Potential concept:

```text
NATIVE_MODEL_2
RASTER
```

Exact `.tcfg` identifiers belong in `TCFG_SPEC.md`.

### 28.3 Cut strategy

Potential concept:

```text
EPSON_COMPATIBLE_FULL_PARTIAL
NONE
```

Again, this is a profile-level strategy concept. Actual parameter mapping must be validated before use.

### 28.4 Code-page strategy

Different printers may assign different numeric selectors to nominal code pages. Profile data should represent actual validated mapping rather than a universal enum-to-byte cast.

### 28.5 State-machine differences

Record if a printer requires unusual sequencing such as:

```text
initialize → code page → font → style → text
```

or if certain commands only work at the start of a line.

Only promote sequencing rules after documentation or hardware evidence.

---

## 29. Failure Modes

Failures should be recorded in terms that help determine the owning layer.

| Failure | Likely investigation area | Required Rastrio response |
|---|---|---|
| Encoder cannot represent prepared operation | `core-escpos` / inconsistent strategy | Fail with structured `ESCxxx` diagnostic; do not improvise a new physical strategy |
| Printer prints wrong wrapping | `core-layout`, metrics, profile, or preparation | Fix upstream geometry/profile; encoder must not re-wrap |
| Garbled accented characters | code-page mapping/profile/preparation | Verify transmitted bytes and printer code page; raster fallback if unreliable |
| Unicode silently disappears | preparation bug | Forbidden; produce fallback or explicit diagnostic |
| Raster right edge corrupt | bit packing, width bytes, profile raster limits | Add deterministic golden + hardware regression |
| Raster stops mid-image | printer buffering, band size, pacing, transport | Separate printer-band and transport-chunk experiments |
| Printer disconnects mid-job | transport/session | Report partial-transmission ambiguity; no automatic retry |
| Cut command prints garbage | wrong dialect/unsupported command | Disable strategy for profile; add hardware observation |
| Status query hangs printing | unsupported/broken bidirectional behavior | Disable status strategy unless reliably verified |
| Preview differs from raster output | preparation/preview invariant breach | Critical bug; preview must consume exact prepared raster |
| Preview differs from native wrapping | metrics/preparation bug | Fix profile/text/layout; do not patch Compose preview |
| Same encoded bytes behave differently by transport | device interface/transport timing | Record transport-specific evidence without changing document semantics |

### 29.1 Diagnostic guidance

Relevant PRD diagnostic families include:

```text
PRNxxx  printer preparation
ESCxxx  ESC/POS encoding
TRNxxx  transport/session
```

Printable user content SHOULD NOT be embedded in logs or diagnostics unless required for explicit local display.

---

## 30. Encoder State and Determinism Notes

ESC/POS is stateful. Rastrio should make serialization deterministic even when a printer retains state between commands.

### 30.1 Recommended encoder discipline

For each prepared segment/job, the encoder SHOULD:

1. begin from an explicit strategy-defined initial state;
2. emit only state changes required by the prepared operation sequence;
3. avoid relying on unknown printer power-on defaults where practical;
4. reset or explicitly set style state when transitioning between operations;
5. keep state tracking internal to serialization and never use it to make new layout decisions;
6. produce identical bytes for identical `PreparedPrint` + protocol strategy inputs.

### 30.2 State-tracking example

Internal encoder state may track values such as:

```text
initialized
selected font
selected code page
bold
underline
character scale
alignment
```

This is serialization state, not document layout state.

The encoder MAY omit redundant protocol commands if and only if doing so preserves deterministic, validated semantics.

### 30.3 Golden-test expectation

Golden tests SHOULD cover exact bytes for:

```text
plain text
multiple lines
code-page switching
alignment
bold
underline
feed
full cut
partial cut where supported
```

These are explicit Phase 8 PRD requirements.

---

## 31. Helett H50i BillQuick Go — Available Reference-Device Notebook

The Helett H50i BillQuick Go is one physical ESC/POS-compatible printer currently available for Rastrio development and validation.

It is **not** Rastrio's generic printer model, compatibility baseline for every ESC/POS device, or source of universal defaults.

The same evidence rules in this notebook apply to the H50i and to every future maintained printer profile.

### 31.1 Current observed device facts

The following table separates direct device/self-test observations from capabilities that still require command-path verification.

| Field | Current notebook value | Evidence/status | What it does / does not establish |
|---|---|---|---|
| Role | Available physical reference device | Project context | Useful for implementation validation; not architecturally privileged |
| Protocol family | ESC/POS-compatible behavior physically observed through RawBT | `H50I-RAWBT-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Establishes ESC/POS-family compatibility on the tested path; exact commands/dialect remain to be captured |
| Android transport path | Bluetooth printing observed through RawBT | `H50I-RAWBT-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Does not yet establish Rastrio RFCOMM UUID/chunk/pacing behavior |
| USB | Self-test reports `USB: Printing Port` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Establishes reported USB printing interface presence, not Rastrio USB endpoint/protocol behavior |
| Bluetooth firmware | Self-test reports `BT_ver: 3.16.1` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Informational device/version context only |
| Font firmware | Self-test reports `FontVer: 1.00.00` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Informational; may help scope future reproducibility findings |
| Reported print width | `48 mm` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Does not establish `printableWidthDots` without validated horizontal dot geometry |
| DPI / dot geometry | `To Verify` | none sufficient yet | MUST NOT infer 203 DPI or another common value merely from printer class |
| Reported native font | `12x24` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Supports reported/default cell geometry only; selector, metrics, styles remain to verify |
| Reported charset | `CP437` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Supports current/default charset observation; selector mapping and full page set remain unknown |
| Native styles | `To Verify` | `HW-003`, `HW-004` planned | Alignment/bold/underline command behavior remains unverified |
| Native scaling | `To Verify` | future controlled test | Measure geometry/accepted values |
| Raster strategy | `To Verify`; `GS v 0` remains candidate | no command-path evidence yet | Do not infer from general ESC/POS compatibility |
| Preferred raster band size | `To Verify` | empirical/resource test required | Separate from transport write chunking |
| QR firmware capability | Self-test page physically prints a QR symbol | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Proves firmware can internally produce a QR in self-test; does not prove host `GS ( k` strategy |
| EAN-13 firmware capability | Self-test page physically prints an EAN-13 barcode | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Proves firmware can internally produce EAN-13 in self-test; does not prove host `GS k` strategy |
| Automatic cutter | Not present / no autocut on observed unit | direct physical observation; `HW + MODEL`; **Hardware Observed** | A maintained profile SHOULD model automatic cutter support as false once captured in permanent hardware evidence |
| Full/partial cut commands | Not applicable to observed unit while cutter support is false | derived from physical absence | Generic ESC/POS cut commands remain valid for other cutter-capable profiles |
| Status queries | `To Verify` | later/optional capability | Do not poll until command behavior is verified |
| Printer buffer guidance | `To Verify` | long-job/raster experiments required | Separate from Android transport buffering |
| Bluetooth safe write chunk | `To Verify` | Rastrio transport evidence required | RawBT success does not reveal RawBT's internal chunk/pacing policy |
| Bluetooth pacing | `To Verify` | Rastrio transport evidence required | No arbitrary sleep constant |
| Reported max print speed | `60 mm/sec (Max)` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Printer-reported maximum; not a Rastrio transport throughput or pacing guarantee |
| Current print density | `5` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Current/configuration state; not automatically portable profile capability |
| Current autofeed | `0 mm` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Current/configuration state; semantics/range not established |
| Runtime temperature | `33` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Runtime telemetry; unit/meaning not promoted without documentation |
| Runtime voltage | `4.20` | `H50I-SELFTEST-2026-10-02`; `HW + MODEL`; **Hardware Observed** | Runtime telemetry; not portable profile data |

The self-test also exposes Bluetooth pairing/configuration fields. Their values are local device/session configuration and MUST NOT be promoted into `.tcfg` or generic profile data. This notebook does not need to preserve pairing secrets/identifiers to establish printing capability.

### 31.2 Current H50i observation table

| ID | Feature | Command/Behavior | Scope | Evidence | Status | Hardware test | Notes |
|---|---|---|---|---|---|---|---|
| `ESC-OBS-0100` | Initialization | `1B 40` candidate | H50i | `INF + MODEL` | TBD | `HW-001` | Exact initialization behavior still requires Rastrio-controlled bytes |
| `ESC-OBS-0101` | ASCII/native text | ESC/POS-compatible printing through RawBT | H50i + Android | `HW + MODEL` | Hardware Observed | `HW-001` pending | Confirms ESC/POS-family behavior; exact transmitted sequence not captured in this notebook |
| `ESC-OBS-0102` | Wrapping geometry | prepared line breaks must match physical output | H50i | `MODEL` | TBD | `HW-002` | Measure against validated profile font metrics |
| `ESC-OBS-0103` | Alignment | `1B 61 n` candidate | H50i | `INF + MODEL` | TBD | `HW-003` | Verify left/center/right with captured bytes |
| `ESC-OBS-0104` | Bold | `1B 45 n` candidate | H50i | `INF + MODEL` | TBD | `HW-004` | Verify visual and geometry effects |
| `ESC-OBS-0105` | Underline | `1B 2D n` candidate | H50i | `INF + MODEL` | TBD | `HW-004` | Verify supported modes |
| `ESC-OBS-0106` | Long native text | sustained stream | H50i | `MODEL` | TBD | `HW-005` | Observe truncation/buffer behavior |
| `ESC-OBS-0107` | Disconnect | partial transmission semantics | H50i + Android BT | `MODEL` | TBD | `HW-006` | Verify recovery UX assumptions |
| `ESC-OBS-0108` | Raster | `GS v 0` candidate | H50i | `INF + MODEL` | TBD | `HW-007`–`HW-013` | Establish packing, width, banding, pacing |
| `ESC-OBS-0109` | Native QR host command | `GS ( k` candidate | H50i | `INF + MODEL` | TBD | `HW-017` | Self-test QR is not host-command evidence |
| `ESC-OBS-0110` | Cutter | automatic cutter absent on observed unit | H50i | `HW + MODEL` | Hardware Observed | permanent no-cutter evidence pending | Do not emit cut commands for the H50i profile |
| `ESC-OBS-0111` | Status | `DLE EOT n` candidate | H50i | `INF + MODEL` | TBD | TBD | Do not enable polling before reliable test |
| `ESC-OBS-0112` | Protocol family | ESC/POS-compatible printing confirmed using RawBT | H50i + Android | `HW + MODEL` | Hardware Observed | formal hardware record pending | Sufficient to classify the device under the v1 ESC/POS family; insufficient for individual strategy claims |
| `ESC-OBS-0113` | Reported print width | Self-test reports `48 mm` | H50i | `HW + MODEL` | Hardware Observed | geometry calibration pending | Do not convert to dots without validated DPI/dot geometry |
| `ESC-OBS-0114` | Reported font | Self-test reports `12x24`; `FontVer 1.00.00` | H50i | `HW + MODEL` | Hardware Observed | font geometry test pending | Supports reported/default cell geometry only |
| `ESC-OBS-0115` | Reported charset | Self-test reports `CP437` | H50i | `HW + MODEL` | Hardware Observed | byte-grid test pending | Selector and complete repertoire still unknown |
| `ESC-OBS-0116` | QR self-test | Firmware self-test prints a QR symbol | H50i | `HW + MODEL` | Hardware Observed | `HW-017` pending | Does not prove host-accessible native QR command |
| `ESC-OBS-0117` | EAN-13 self-test | Firmware self-test prints an EAN-13 barcode | H50i | `HW + MODEL` | Hardware Observed | barcode command test pending | Does not prove host-accessible `GS k` command strategy |
| `ESC-OBS-0118` | USB interface | Self-test reports `USB: Printing Port` | H50i | `HW + MODEL` | Hardware Observed | USB test pending | Does not establish endpoint/protocol behavior |
| `ESC-OBS-0119` | Printer configuration | Self-test reports max speed 60 mm/s, density 5, autofeed 0 mm | H50i | `HW + MODEL` | Hardware Observed | N/A | Mostly configuration/telemetry; do not automatically serialize into `.tcfg` |
| `ESC-OBS-0120` | Runtime telemetry | Self-test reports temperature 33 and voltage 4.20 | H50i | `HW + MODEL` | Hardware Observed | N/A | Runtime state, not portable profile capability |

### 31.3 Profile promotion rule

A capability SHOULD be promoted into a maintained production `PrinterProfile` for the H50i—or any other printer—only when all relevant conditions are satisfied:

1. the behavior is supported by trustworthy documentation or reproducible physical evidence;
2. the evidence supports the **specific field/strategy claim**, not merely a nearby capability;
3. required geometry/parameter values are known;
4. `core-printer` can make the decision before encoding;
5. `core-escpos` has byte-level coverage for the selected ESC/POS strategy;
6. a numbered hardware test exists where practical;
7. failure behavior is understood well enough to avoid unsafe defaults;
8. any profile field, strategy, or quirk identifier is documented in `TCFG_SPEC.md`.

A direct hardware observation may be sufficient to disable a capability conservatively—for example, a physically absent automatic cutter—while positive command claims generally require command-path evidence.

### 31.4 Anti-hardcoding rule

Generic Core MUST contain no H50i-specific width, DPI, code-page selector, raster-band constant, cut command, Bluetooth chunk size, timing value, or similar device constant.

The same rule applies to every printer model.

Stable printer characteristics belong in validated profile data. Local pairing/device identity and transport-session behavior belong to `PrinterInstance`/platform transport state, not `.tcfg`.

---

## 32. Hardware-Test Mapping

The repository hardware-test specification defines the following permanent ranges relevant to this notebook.

| Hardware test | Protocol evidence expected here |
|---|---|
| `HW-001 Simple ASCII` | initialization, native text baseline, default/selected font, code page |
| `HW-002 Line wrapping` | measured native geometry versus `PreparedPrint` |
| `HW-003 Alignment` | alignment command/strategy and physical result |
| `HW-004 Bold/underline` | style command support and geometry effects |
| `HW-005 Long text` | sustained native stream, buffering, pacing |
| `HW-006 Disconnect during job / recovery procedure` | partial transmission and retry safety |
| `HW-007`–`HW-013` | raster command, packing, banding, full-width and long-image behavior |
| `HW-014`–`HW-016` | Unicode raster fallback and complex-script physical output |
| `HW-017`–`HW-019` | native/raster QR behavior and size variations |
| `HW-020`–`HW-023` | segmented wide-document output, registration and assembly |

`docs/HARDWARE_TESTS.md` owns the physical procedures and result logs. This file owns the protocol conclusions drawn from those results.

---

## 33. How Observations Feed Back into Rastrio

A verified observation is useful only if it is reflected in the correct owning artifact.

### 33.1 `PrinterProfile`

Feed an observation into `PrinterProfile` when it establishes portable model/capability data such as:

- DPI;
- printable width;
- native font geometry;
- native style support;
- validated code-page mapping;
- raster strategy;
- native QR capability;
- barcode capability;
- cutter modes;
- printer-side buffer guidance;
- preferred raster band size;
- status-query capability;
- known quirk identifiers.

Do not put Bluetooth device identity or USB instance identity into `PrinterProfile`.

### 33.2 `TCFG_SPEC.md`

Update `TCFG_SPEC.md` when an observation requires:

- a new portable profile field;
- a new known protocol strategy identifier;
- a new validated parameter;
- a new quirk identifier;
- changed validation rules;
- compatibility/versioning behavior.

A `.tcfg` profile MUST NOT become a vehicle for arbitrary raw ESC/POS byte-program injection.

### 33.3 Encoder tests

Add or update byte-level tests when an observation affects serialization.

At minimum verify:

- exact command prefix;
- parameter byte encoding;
- little-endian width/height fields where applicable;
- payload boundaries;
- state transitions;
- omission of redundant state only when intentional;
- failure on unsupported/inconsistent prepared operations.

Hardware success without byte-level regression coverage is insufficient for a stable encoder feature.

### 33.4 `HARDWARE_TESTS.md`

Add or update a numbered physical test when the behavior can only be trusted after real-device verification.

Record:

```text
test ID
printer profile/version
app commit or release
transport
input fixture
exact relevant command strategy
expected physical result
observed result
notes/quirks
```

### 33.5 `PREVIEW_SPEC.md` / text/layout specifications

Update formal preview or text specifications only when the observation changes an architectural contract or measured geometry model.

Do not patch preview rendering to mimic a discovered printer quirk while leaving `PreparedPrint` unchanged. The physical decision must be represented upstream first.

---

## 34. Adding a New Printer or Dialect

Use this sequence:

1. Establish that the device belongs to the intended ESC/POS-compatible protocol family using controlled evidence.
2. Create or select a conservative `PrinterProfile` with unsupported/unverified capabilities disabled by default.
3. Establish basic native ASCII output with captured/known Rastrio bytes.
4. Verify printable width in dots/effective dot geometry and native font geometry.
5. Verify alignment and styles.
6. Map code pages using transmitted-byte fixtures.
7. Verify raster strategy and bit packing.
8. Determine safe raster banding and printer-side pacing guidance.
9. Verify QR only if required.
10. Verify cutter only if hardware has one.
11. Verify status querying only if there is a real product use and bidirectional behavior is reliable.
12. Run long-job and disconnect tests.
13. Record every promoted capability in this notebook and `PrinterProfile` evidence.
14. Add byte-level tests and numbered hardware regressions.

A new printer is not “supported” merely because one ESC/POS-compatible sequence produced paper once.

Support claims SHOULD be scoped to the profile capabilities actually validated.

---

## 35. Byte Capture and Reproduction Guidance

When investigating a protocol issue, record the exact bytes sent whenever practical.

Preferred representation:

```text
1B 40
1B 61 01
48 65 6C 6C 6F 0A
```

For large payloads, do not paste megabytes of raster data into this file. Instead record:

- fixture name/hash;
- dimensions;
- command header;
- payload byte count;
- first/last small byte window when useful;
- golden-test fixture path;
- encoded-stream hash.

A reproduction record SHOULD identify:

```text
Rastrio commit
profile ID/version
PreparedPrint fixture
encoder strategy
transport
printer model
firmware/revision if known
result
```

---

## 36. Security Notes for Protocol Work

Protocol experimentation must preserve Rastrio's security model.

### 36.1 Imported profiles

Imported `.tcfg` files MUST NOT inject arbitrary byte templates or executable command programs into normal printing.

For schema v1, profiles may select only the registered `escpos` family plus known, validated ESC/POS strategies and validated parameters.

Unknown/future protocol families MUST fail validation rather than falling back to ESC/POS or loading profile-supplied code.

### 36.2 Printer-supplied data

Device names, Bluetooth metadata, USB descriptors, status bytes, and other printer-originated data are untrusted inputs.

Do not use them as code, paths, format strings, or unsanitized log content.

### 36.3 Logging

Do not log printable user content by default, including:

- document text;
- QR payloads;
- image contents;
- Markdown source;
- arbitrary encoded native text bytes when they reveal user content.

Useful non-content logging may include:

```text
observation/test ID
profile ID
strategy ID
operation type
raster dimensions
band size
byte count
transport state
diagnostic code
```

### 36.4 Raw-command experimentation

If a future Printer Lab feature allows raw command experiments, it must be explicitly designed as a dangerous, isolated developer/advanced feature and remain outside normal trusted `.tcfg` import semantics.

---

## 37. Open Verification Backlog

The backlog distinguishes generic ESC/POS engineering from work on the currently available H50i reference device.

### Generic ESC/POS encoder/profile work

- [ ] Exact-byte golden for initialization.
- [ ] Exact-byte golden for plain native text.
- [ ] Exact-byte golden for font selection.
- [ ] Exact-byte golden for code-page switch.
- [ ] Exact-byte golden for alignment.
- [ ] Exact-byte golden for bold.
- [ ] Exact-byte golden for underline.
- [ ] Exact-byte golden for native scaling.
- [ ] Exact-byte golden for feeds.
- [ ] Exact-byte golden for raster header/payload dimensions.
- [ ] Exact-byte golden for raster band/artifact sequence.
- [ ] Exact-byte golden for native QR strategy when implemented.
- [ ] Exact-byte golden for native barcode strategy when implemented.
- [ ] Exact-byte golden for cuts when a cutter-capable profile exists.
- [ ] Structured failure test for unsupported prepared operation.
- [ ] Protocol-family dispatch test proving unknown families never reach `core-escpos`.
- [ ] Prepared-artifact test proving encoding reads finalized content without regeneration.
- [ ] Maintain synthetic ESC/POS profiles covering different widths, font metrics, code pages, cutter/no-cutter behavior, and raster constraints.
- [ ] Add physical evidence from additional printer models as available; do not use the H50i as a universal default.

### H50i — observations already available

- [x] Physical ESC/POS-family compatibility observed through RawBT.
- [x] Self-test reports print width `48 mm`.
- [x] Self-test reports native/default font `12x24`.
- [x] Self-test reports charset `CP437`.
- [x] Self-test prints a QR symbol.
- [x] Self-test prints an EAN-13 barcode.
- [x] Physical unit observed without automatic cutter.
- [x] Self-test reports USB printing interface presence.
- [x] Self-test configuration/version/telemetry values recorded at the appropriate evidence strength.

These are `Hardware Observed`, not replacements for command-specific Rastrio hardware tests.

### H50i — command/profile verification still required

- [ ] Obtain or identify trustworthy H50i protocol documentation, if available.
- [ ] Record additional hardware/firmware revision information where useful.
- [ ] Capture Rastrio-controlled Bluetooth RFCOMM/SPP service/connection behavior.
- [ ] Verify `ESC @` initialization behavior with exact captured bytes.
- [ ] Verify simple ASCII native text through Rastrio's encoder/transport path.
- [ ] Determine authoritative printable width in dots/effective horizontal dot geometry.
- [ ] Verify the reported `12x24` font's selector, exact advance/line metrics, and reset behavior.
- [ ] Verify native scaling.
- [ ] Verify left/center/right alignment.
- [ ] Verify bold/emphasized mode.
- [ ] Verify underline modes.
- [ ] Map reliable code pages and selector values; do not assume the CP437 selector.
- [ ] Verify long native-text jobs.
- [ ] Verify `GS v 0` or choose another validated raster strategy.
- [ ] Verify bit order and raster row orientation.
- [ ] Determine safe raster band guidance.
- [ ] Determine Bluetooth transport chunk guidance separately.
- [ ] Determine whether pacing is required and at which layer.
- [ ] Verify host-accessible native QR commands or keep raster QR as the reliable path.
- [ ] Verify host-accessible EAN-13/native barcode commands only if the feature is needed.
- [ ] Keep automatic cutter capability disabled for the H50i profile; capture permanent hardware evidence for the no-cutter claim.
- [ ] Verify status-query support only if needed.
- [ ] Record disconnect/partial-transmission behavior.

---

## 38. Decision / Observation Log Template

Append new entries below rather than rewriting history when the historical sequence matters.

```markdown
### ESC-OBS-XXXX — <title>

- **Date:** YYYY-MM-DD
- **Feature:** <feature>
- **Scope:** <generic dialect / printer model / transport>
- **Evidence:** <DOC/BYTE/HW/INF/MODEL>
- **Status:** <TBD / Candidate / Byte Verified / Hardware Observed / Hardware Verified / Adopted / Rejected / Superseded>
- **Profile:** <profile ID/version if applicable>
- **Command/behavior:** `<hex>` or concise behavior
- **PreparedPrint fixture:** <fixture or N/A>
- **Encoder test:** <test ID/path or TBD>
- **Hardware test:** <HW-xxx or N/A>
- **Transport:** <Bluetooth SPP / USB / fake / file sink / N/A>
- **Expected:** <expected result>
- **Observed:** <observed result>
- **Conclusion:** <what is now known>
- **Follow-up:** <profile/spec/test changes>
```

---

## 39. Current Conclusions

At this revision, the reliable conclusions are:

1. Rastrio `.tcfg` schema v1 targets ESC/POS-compatible printers and MUST model dialect/model differences explicitly.
2. The H50i is one available reference device, not the generic compatibility baseline.
3. A physical RawBT test provides **Hardware Observed** evidence that the tested H50i accepts ESC/POS-compatible printing, but exact command strategies still require Rastrio-controlled byte/hardware tests.
4. The H50i self-test provides **printer-reported** evidence for `48 mm` print width, `12x24` font, `CP437` charset, QR/EAN-13 self-test generation, USB printing-interface presence, and several configuration/version/telemetry values.
5. Those self-test facts must not be promoted beyond what they prove: `48 mm` is not `printableWidthDots`; `CP437` is not an `ESC t` selector mapping; a self-test QR/EAN-13 print is not proof of the host native command strategy.
6. The observed H50i has no automatic cutter; the H50i profile should therefore remain no-cutter unless contradictory model/revision evidence appears.
7. `PreparedPrint` is the complete physical plan; `core-escpos` is the v1 ESC/POS serializer, not a second printer-layout/rendering engine.
8. Finalized raster/graphic data may be banded or spool-backed, but preview and encoder must consume identical immutable prepared content without regeneration.
9. Code-page, native/raster, QR, barcode, segmentation, raster preparation, and cut decisions occur before encoding.
10. `PrinterProfile` is the correct place for validated portable hardware capabilities and ESC/POS strategies; local Bluetooth/USB pairing/device identity is not.
11. Printer-side buffering/raster banding and transport write chunking are distinct.
12. Physical hardware observations require evidence scoped to the specific claim and should be promoted to permanent numbered hardware tests where practical.
13. Uncertainty is preferable to fabricated compatibility data. A conservative `TBD` or disabled capability is valid engineering state until evidence exists.

