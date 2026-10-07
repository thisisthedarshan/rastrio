# Rastrio Resource Limits Specification

**Status:** Implementation specification  
**Specification revision:** 1.1  
**Applies to:** Rastrio Core, shared application, platform applications, preview, printing, import/export, and untrusted-input processing  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related specifications:** `docs/TD_SPEC.md`, `docs/TCFG_SPEC.md`, `docs/TEXT_RENDERING_SPEC.md`, `docs/PREVIEW_SPEC.md`, `docs/SECURITY.md`, `docs/TESTING.md`  
**Primary release target:** Android  
**Secondary targets:** Desktop/JVM and Web/Wasm

---

## 1. Purpose

Rastrio processes inputs whose logical or decoded size may be dramatically larger than their apparent source size.

Examples include:

- extremely long receipts;
- multi-megabyte Markdown files;
- `.td` ZIP-compatible archives with high expansion ratios;
- images whose compressed representation is small but whose decoded bitmap is enormous;
- documents containing very large tables;
- wide landscape documents that produce many physical segments;
- long raster operations;
- malformed or malicious JSON structures;
- printer jobs whose encoded output is substantially larger than the originating document.

Rastrio MUST therefore treat resource consumption as an explicit part of input validation, document processing, preview generation, printer preparation, encoding, and transmission.

The objective of this specification is to ensure that an input cannot cause Rastrio to require unbounded:

- memory;
- archive expansion;
- object allocation;
- parser depth;
- image decode size;
- layout work;
- preview bitmap allocation;
- raster buffering;
- prepared-artifact/spool storage;
- font parsing and glyph-cache work;
- encoded print buffering;
- transport buffering;
- geometry;
- collection sizes.

This specification defines:

1. which resources MUST be bounded;
2. which component owns each bound;
3. how limits interact;
4. how exceeding a limit is reported;
5. when rejection, warning, downscaling, confirmation, or another bounded fallback is appropriate;
6. provisional implementation values where useful;
7. how limits are tested and subsequently tuned.

---

## 2. Normative Language

The terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

A numeric value explicitly labelled **Provisional Baseline** or **Provisional Default** is not a permanent Rastrio format constraint.

Such values:

- are initial implementation parameters;
- MUST be represented through a centralized resource-policy mechanism rather than scattered constants;
- SHOULD be revised using profiling, fuzzing, hardware tests, and minimum-device testing;
- MUST NOT become part of `.td` or `.tcfg` compatibility semantics merely because an initial implementation uses them;
- MAY be reduced on a constrained platform;
- MAY be increased in a later release after validation without requiring a `.td` or `.tcfg` schema change.

Numeric limits that form part of a printer's actual hardware capability are different. Those belong in `PrinterProfile` where appropriate.

---

## 3. Scope

This specification covers resource controls for:

- `.td` archives;
- `.tcfg` files;
- JSON decoding;
- Markdown parsing and compilation;
- images and image decoding;
- raster processing;
- bundled and explicitly selected font resources;
- logical layout;
- physical preparation;
- immutable prepared-artifact backing stores;
- preview;
- protocol encoding (`core-escpos` for v1);
- print sessions;
- platform transports.

This specification does not establish:

- arbitrary product-content limits unrelated to safety or feasibility;
- printer-specific widths;
- fixed Bluetooth chunk sizes shared by every printer;
- fixed raster-band heights shared by every printer;
- permanent maximum receipt lengths based solely on an initial implementation;
- limits embedded in portable documents that allow an untrusted document to enlarge its own processing budget.

---

# 4. Core Resource-Limit Principles

## 4.1 Everything untrusted is bounded

Every variable-size structure originating from untrusted input MUST ultimately be subject to a finite bound.

This includes indirect structures.

For example, bounding `.td` file size alone is insufficient because a small ZIP file may expand into a much larger archive.

Similarly, bounding image file size alone is insufficient because a small compressed image may decode into hundreds of millions of pixels.

---

## 4.2 Limits apply to actual consumption

Metadata supplied by an input MUST NOT be trusted as the sole enforcement mechanism.

For example, a ZIP entry's declared uncompressed size MAY be used for early rejection, but Rastrio MUST also count the bytes actually produced during decompression.

An input that claims:

```text
uncompressedSize = 1 MiB
```

but emits:

```text
200 MiB
```

during decompression MUST be stopped according to the actual-byte budget.

---

## 4.3 Fail before expensive work where possible

Rastrio SHOULD reject invalid or clearly over-limit input before allocating large buffers or performing expensive transforms.

Preferred sequence:

```text
cheap metadata validation
        ↓
checked size arithmetic
        ↓
resource-budget validation
        ↓
bounded decode / parse / transform
```

This is particularly important for:

- archive expansion;
- image decoding;
- JSON parsing;
- raster allocation;
- preview surfaces.

---

## 4.4 Streaming is not permission for infinite work

Streaming allows memory use to remain bounded, but it does not eliminate the need for total-work limits.

A streaming parser or raster encoder MUST still have finite controls over relevant quantities such as:

- total input bytes;
- expanded bytes;
- node count;
- logical operations;
- raster bytes;
- physical segments.

---

## 4.5 Memory limits and content limits are different

The implementation SHOULD distinguish:

```text
content complexity
```

from:

```text
simultaneously resident memory
```

For example, a 30 MiB raster job MAY be acceptable if generated and consumed incrementally while retaining only a small number of bands.

It is not acceptable to conclude that the same job is safe merely because it is theoretically streamable while accidentally retaining every band in memory.

---

## 4.6 Checked arithmetic is mandatory

All resource-size calculations involving externally influenced values MUST use checked arithmetic.

Calculations such as:

```text
width × height
width × height × bytesPerPixel
rowBytes × bandHeight
rows × columns
entryCount + 1
expandedBytes + nextEntryBytes
segmentCount × segmentWidth
```

MUST NOT silently overflow `Int`, `Long`, or platform-specific integer representations.

Intermediate resource arithmetic SHOULD use `Long` or an equivalent sufficiently wide integer type even where final dimensions fit into `Int`.

Overflow MUST be treated as an invalid or over-limit input.

---

## 4.7 Untrusted input cannot raise its own budget

A `.td`, `.tcfg`, Markdown file, image, asset, external URL response, printer name, or archive metadata MUST NOT be able to modify the safety ceilings under which it is processed.

Resource policy originates from trusted application configuration.

`PrinterProfile` is permitted to contribute genuine printer-side constraints, but an imported profile still undergoes validation and cannot disable application safety ceilings.

---

# 5. Resource Policy Architecture

Resource limits SHOULD be represented centrally rather than as unrelated constants throughout Core.

A conceptual model is:

```kotlin
data class ResourcePolicy(
    val td: TdResourceLimits,
    val tcfg: TcfgResourceLimits,
    val markdown: MarkdownResourceLimits,
    val image: ImageResourceLimits,
    val font: FontResourceLimits,
    val raster: RasterResourceLimits,
    val preparedArtifacts: PreparedArtifactResourceLimits,
    val preview: PreviewResourceLimits,
    val document: DocumentResourceLimits,
    val printing: PrintingResourceLimits,
)
```

Exact names are not normative.

The policy MAY be divided between modules, but there MUST be one clearly traceable source for each limit.

Modules MUST NOT independently invent conflicting limits.

For example:

```text
core-document
    owns/enforces .td structural limits

core-profile
    owns/enforces .tcfg structural limits

core-markdown
    owns Markdown parser/compiler limits

core-text / platform font adapter
    own font parsing/shaping/cache limits

core-raster
    owns raster working-set limits

core-layout
    owns layout-complexity limits

core-preview
    owns preview tile/cache limits

core-printer
    combines document and PrinterProfile constraints
    and owns prepared-artifact lifecycle/budgets

platform transport
    owns transport buffering/chunking limits
```

---

# 6. Limit Classes

Rastrio SHOULD distinguish at least four classes of resource control.

## 6.1 Hard safety ceiling

Crossing a hard safety ceiling prevents the operation.

Examples:

- archive expansion above allowed maximum;
- arithmetic overflow;
- absurd image dimensions;
- JSON nesting beyond parser policy.

Hard safety ceilings MUST NOT be bypassed merely by clicking through a warning dialog.

---

## 6.2 Operational limit

An operational limit exists because the current implementation cannot process additional data safely or predictably.

It MAY later be raised without changing the portable format.

Example:

```text
maximum Markdown AST nodes supported by the current implementation
```

---

## 6.3 Warning or confirmation threshold

A document can remain technically processable while still being unexpectedly expensive.

Examples include:

- an unusually long physical print;
- hundreds of landscape strips;
- a document close to resource ceilings.

Such cases MAY require explicit user confirmation rather than rejection.

---

## 6.4 Hardware/session constraint

A printer or transport can impose a limit independent of application input safety.

Examples:

```text
PrinterProfile:
    preferred raster band height
    printer-side buffer guidance

PrinterTransport/session:
    write chunk size
    queued-byte budget
    pacing
    timeout
```

These MUST remain architecturally distinct.

---

# 7. Effective-Limit Precedence

Where more than one limit applies, Rastrio MUST use the safe effective bound.

Conceptually:

```text
effective limit =
    minimum(
        application safety ceiling,
        platform/runtime ceiling,
        operation-specific ceiling,
        validated hardware/session ceiling where applicable
    )
```

A more permissive lower-level component MUST NOT override a stricter safety ceiling.

A platform MAY use stricter limits than another platform.

For example, Web/Wasm may initially use a smaller preview cache or image working-set budget than Desktop/JVM.

Portable `.td` compatibility MUST NOT imply that every supported device is required to process every theoretically valid document regardless of available resources.

---

# 8. Provisional Baseline Values

The PRD does not define permanent numeric resource ceilings.

The values in this section are therefore **provisional engineering starting points**.

They exist so that Phase 1 and subsequent implementations can be safe before extensive production measurements are available.

All values MUST remain configurable in trusted application policy.

| Resource | Provisional Baseline | Classification |
|---|---:|---|
| `.td` compressed file size | 64 MiB | hard operational/safety ceiling |
| `.td` total expanded bytes | 256 MiB | hard safety ceiling |
| `.td` archive reader resident input, output, metadata, and inflater scratch | 128 MiB | hard memory ceiling |
| `.td` archive entries | 1,024 | hard complexity ceiling |
| `.td` individual expanded entry | 64 MiB | hard ceiling |
| `.td` `manifest.json` | 64 KiB | hard ceiling |
| `.td` `document.json` | 16 MiB | hard ceiling |
| `.td` embedded assets | 512 | hard complexity ceiling |
| `.td` individual embedded asset | 32 MiB | hard ceiling |
| normalized archive path length | 1,024 UTF-8 bytes | hard ceiling |
| archive path segments | 64 | hard ceiling |
| `.tcfg` file size | 1 MiB | hard ceiling |
| `.tcfg` JSON nesting | 64 levels | hard ceiling |
| `.tcfg` individual string value | 64 KiB UTF-8 | hard ceiling |
| `.tcfg` total decoded JSON nodes/tokens | 100,000 | complexity ceiling |
| Markdown UTF-8 source size | 8 MiB | operational ceiling |
| Markdown source lines | 100,000 | pre-parser allocation ceiling |
| Markdown AST nodes | 250,000 | complexity ceiling |
| Markdown structural nesting | 128 levels | complexity ceiling |
| image compressed input | 32 MiB | hard input ceiling |
| image encoded dimension | 32,768 pixels per axis | hard sanity ceiling |
| encoded image pixels | 100 megapixels | hard preflight ceiling |
| decoded image working pixel target | 24 megapixels | operational memory ceiling |
| image decode working set | 128 MiB | memory ceiling |
| explicitly imported font file | 32 MiB | provisional hard input ceiling |
| raster scratch working set | 16 MiB | memory ceiling |
| preferred initial raster band height | 256 rows | tuning default only |
| raster band payload | 2 MiB | operational ceiling |
| simultaneously retained raster bands | 2 | tuning default |
| cumulative finalized prepared raster/graphic payload | 128 MiB | provisional total-work/storage ceiling |
| preview tile area | 2 megapixels | memory ceiling |
| preferred preview tile height | 1,024 pixels | tuning default |
| preview rendered-tile cache | 32 MiB | memory ceiling |
| document blocks | 100,000 | complexity ceiling |
| inline nodes | 500,000 | complexity ceiling |
| table rows per table | 20,000 | complexity ceiling |
| table columns per table | 256 | complexity ceiling |
| total cells per table | 250,000 | complexity ceiling |
| generated layout cost items | 100,000 | provisional Phase 3B resident-cost ceiling |
| provisional logical landscape width | 1,000 mm | operational ceiling |
| physical landscape segments | 256 | operational ceiling |
| encoded bytes queued ahead of transport | 256 KiB | memory/backpressure ceiling |
| encoder-produced chunk | 64 KiB | provisional encoder ceiling |

`KiB` and `MiB` in this document use binary units:

```text
1 KiB = 1,024 bytes
1 MiB = 1,048,576 bytes
```

These values MUST be revisited after measurement on the project's supported Android device baseline.

---

# 9. `.td` Resource Limits

## 9.1 Compressed archive size

The application MUST bound the total compressed `.td` input size.

This prevents:

- excessive file reads;
- large-memory imports;
- storage abuse;
- expensive archive scanning;
- trivially oversized hostile input.

### Provisional Baseline

```text
64 MiB
```

Where the source exposes its total byte length before opening, the limit SHOULD be checked before archive parsing begins.

Unknown-length streams MUST be counted while being consumed.

---

## 9.2 Expanded archive size

The total bytes produced by expanding all archive entries MUST be bounded independently of compressed size.

This is the primary protection against high archive expansion.

### Provisional Baseline

```text
256 MiB total expanded content
```

The reader MUST maintain a cumulative counter:

```text
totalExpandedBytes
```

and stop decompression before accepting data that would exceed the limit.

Archive metadata MUST NOT be trusted in place of the actual expansion counter.

The Phase 1 in-memory reader also applies a trusted 128 MiB resident archive budget before allocating or retaining each entry. It counts the caller-provided compressed input, retained expanded payloads, a conservative allowance for central-directory paths and entry objects, and transient inflater scratch during deflation. This is a separate runtime memory ceiling from the 256 MiB cumulative expansion ceiling: an archive can be below the expansion ceiling yet fail the resident ceiling. The inflater writes into one preallocated output array per entry; the reader checks actual produced bytes against the entry and cumulative limits. Applications may lower the resident budget for smaller devices. The budget is not a `.td` format field or a guarantee covering later JSON object materialization.

---

## 9.3 Archive entry count

The number of ZIP entries MUST be bounded.

An attacker can cause excessive CPU, memory, path-normalization work, metadata allocation, and lookup overhead through huge numbers of tiny entries even when total archive bytes remain small.

### Provisional Baseline

```text
1,024 entries
```

Directories count toward archive structural complexity if represented as actual ZIP entries.

---

## 9.4 Per-entry expanded size

Every archive entry MUST have an independent expanded-size limit.

### Provisional Baseline

```text
64 MiB
```

More restrictive semantic limits then apply to known entries such as:

```text
manifest.json
document.json
assets/*
source/*
```

---

## 9.5 `manifest.json`

The manifest is expected to be very small.

### Provisional Baseline

```text
64 KiB UTF-8
```

A manifest exceeding this value SHOULD be rejected rather than partially parsed.

The manifest MUST NOT be streamed into an unbounded JSON object merely because the total `.td` archive remains under its limit.

---

## 9.6 `document.json`

`document.json` is authoritative for `.td` document semantics and therefore requires an explicit bound.

### Provisional Baseline

```text
16 MiB UTF-8
```

This is a serialized-size bound, not the only document-complexity bound.

A 10 MiB `document.json` containing pathologically nested or enormous collections may still violate structural limits elsewhere in this specification.

---

## 9.7 Maximum asset count

The number of embedded assets MUST be bounded.

### Provisional Baseline

```text
512 assets
```

The following SHOULD also be bounded independently:

- asset metadata records;
- asset references in the document;
- asset archive entries.

The same physical asset MAY be referenced more than once without each reference counting as a new embedded asset, provided the asset model permits this.

---

## 9.8 Maximum individual asset size

Embedded assets MUST have a per-asset expanded-size ceiling.

### Provisional Baseline

```text
32 MiB
```

Image assets remain subject to image decode limits even when their compressed asset bytes are below this value.

A 2 MiB PNG can still be rejected or downsampled if its decoded geometry is unsafe.

---

## 9.9 Archive paths

Archive path processing itself consumes resources and MUST be bounded.

The reader MUST:

- normalize paths before use;
- reject absolute paths;
- reject paths that normalize outside the logical archive root;
- reject normalized duplicates;
- bound normalized path length;
- bound path segment count.

### Provisional Baseline

```text
normalized UTF-8 path length: 1,024 bytes
segments: 64
```

Rastrio SHOULD process `.td` entries directly from the archive abstraction rather than extracting arbitrary paths into a filesystem.

---

## 9.10 Compression ratio

Rastrio MUST NOT rely solely on a maximum compression ratio as ZIP-bomb protection.

Compression ratios vary legitimately between content types.

The authoritative protections are:

```text
compressed input bound
+
actual expanded byte bound
+
per-entry expanded bound
+
entry-count bound
```

An implementation MAY additionally detect extreme expansion ratios for diagnostics or earlier rejection, but such a heuristic MUST NOT replace actual expanded-byte counting.

---

## 9.11 Nested archives

`.td` processing MUST NOT recursively unpack embedded assets merely because they happen to contain another archive format.

Only the `.td` container itself is interpreted as the Rastrio archive.

An embedded ZIP, APK, JAR, or similar binary is data unless another explicitly invoked feature supports that format.

---

# 10. `.tcfg` Resource Limits

`.tcfg` v1 is UTF-8 JSON and is expected to remain substantially smaller and structurally simpler than normal `.td` documents.

An imported profile is untrusted.

---

## 10.1 File size

`.tcfg` input MUST have a total byte-size ceiling.

### Provisional Baseline

```text
1 MiB
```

A printer capability profile requiring more than this should trigger schema/design review rather than an automatic limit increase.

---

## 10.2 JSON nesting

JSON nesting MUST be bounded.

Deeply nested objects or arrays can cause:

- parser recursion;
- stack exhaustion;
- disproportionate allocation;
- expensive validation walks.

### Provisional Baseline

```text
64 nesting levels
```

If the chosen JSON decoder does not provide a reliable depth limit, Rastrio MUST introduce bounded token scanning, a parser wrapper, or another enforcement mechanism.

---

## 10.3 String values

Individual strings MUST be bounded.

This applies to fields such as:

- display names;
- manufacturer names;
- model names;
- IDs;
- descriptions;
- quirk identifiers;
- protocol-strategy identifiers.

### Provisional general ceiling

```text
64 KiB UTF-8 per string
```

Schema-specific fields SHOULD use substantially smaller semantic limits where appropriate.

For example, a profile ID or protocol-strategy identifier should not legitimately require tens of kilobytes.

---

## 10.4 Property and collection complexity

The decoder MUST prevent a small serialized profile from constructing an unreasonable object graph.

### Provisional Baseline

```text
100,000 decoded JSON nodes/tokens
```

Arrays that are naturally constrained by the schema SHOULD use tighter semantic bounds.

For example:

- code pages;
- native fonts;
- cutter modes;
- known quirks;
- protocol strategies.

A profile containing thousands of duplicate or irrelevant capability declarations SHOULD fail validation rather than consuming unbounded validation time.

---

## 10.5 Unknown properties and strict conformance

`.tcfg` schema v1 rejects unknown properties.

Resource enforcement MUST occur early enough that hostile unknown data cannot bypass byte, nesting, string, or token/node budgets merely because schema validation will later reject it.

Duplicate object keys, comments, trailing commas, non-standard numeric tokens, and other prohibited lenient JSON forms are likewise invalid under `docs/TCFG_SPEC.md`.

The parser path MUST preserve enough information to detect those conditions before a permissive library can discard, normalize, or overwrite them.

---

# 11. JSON Parsing Requirements

All JSON originating in `.td`, `.tcfg`, migration data, or other untrusted portable content MUST be bounded before and during model construction.

At minimum Rastrio MUST control:

```text
input bytes
nesting depth
string length where feasible
array/object complexity
decoded collection sizes
domain-model collection sizes
```

A successful JSON parse is not equivalent to successful validation.

Processing MUST follow the conceptual sequence:

```text
bounded bytes
    ↓
bounded strict-standard-JSON tokenization / pre-validation
    ↓
duplicate-key / unknown-property / prohibited-leniency checks
    ↓
schema/model decode
    ↓
domain validation
    ↓
cross-reference validation
```

A parser configuration that discards unknown fields or collapses duplicate keys before Rastrio can validate them is insufficient by itself.

Where the chosen parser does not expose the required controls, Rastrio MUST add bounded lexical scanning, parser wrapping, token inspection, or equivalent validation as required by `TD_SPEC.md` and `TCFG_SPEC.md`.

The application MUST NOT allocate a collection based solely on an untrusted declared count without first validating the count and arithmetic.

---

# 12. Markdown Resource Limits

Markdown is untrusted text input.

The GFM parser and Rastrio compiler MUST remain bounded even where the document is syntactically valid.

---

## 12.1 Source size

Markdown source size MUST be bounded before full compilation.

### Provisional Baseline

```text
8 MiB UTF-8
```

A platform MAY use a smaller limit when its memory constraints require it.

Source byte size SHOULD be checked before conversion into representations that consume more memory, such as UTF-16 strings or parser objects.

---

## 12.1A Source lines and parser work

Before normalization or parser construction, bound source lines independently of
UTF-8 bytes. The provisional runtime baseline is **100,000 lines**, including
blank lines and the final line (an empty source has one line). CRLF counts as one
terminator; standalone CR and LF each count as one. Check the bound before
incrementing a `Long` counter. This limits the dependency's eager per-line views,
which AST validation cannot protect after allocation. Platforms may lower this
trusted policy ceiling. It is not a Markdown format restriction.

Parser work has a provisional runtime ceiling of **50,000,000 units**. Account
for cancellation checkpoints, source reads, slice creation/materialization, and
cached and filtered token-list reads in every sequential inline parser. Repeated
cached-token scans must consume this budget even when they never read source or
call cancellation checkpoints. Preserve upstream syntax and parser order; do
not substitute image/bracket-count quotas for actual work accounting. Reject
line/work excess with a content-free `MD121` diagnostic and no partial document.

The line ceiling bounds a separate allocation driver; it does not promise that
all inputs below it fit every heap. Existing source, AST, nesting and canonical
limits still apply. Neither line nor work ceilings are controlled by input.

---

## 12.2 AST/node count

Serialized source size alone does not adequately bound parser complexity.

The parser/compiler MUST limit the number of generated AST or equivalent structural nodes.

### Provisional Baseline

```text
250,000 Markdown AST nodes
```

If the third-party Markdown parser does not expose node-count enforcement during parsing, Rastrio SHOULD perform a bounded traversal immediately after parse and before expensive compilation/layout work.

Where possible, parsing itself SHOULD also be protected against pathological construction.

---

## 12.3 Structural nesting

Nested structures MUST be bounded.

Examples include:

- nested lists;
- block quotes;
- emphasis nesting;
- links;
- inline structures.

### Provisional Baseline

```text
128 structural levels
```

The effective parser limit MAY be lower if the chosen GFM implementation has a smaller safe recursion depth.

---

## 12.4 Long unbroken text

Very long unbroken sequences can trigger poor behavior in line-breaking algorithms if implementations repeatedly rescan the same text.

Rastrio MUST ensure that line-breaking work for long tokens is bounded and approximately linear or otherwise explicitly complexity-limited.

A long unbroken token MUST NOT automatically be truncated.

A provisional threshold of:

```text
64 KiB of unbroken text
```

SHOULD trigger the long-token processing path or a warning during early implementation.

Acceptable behavior includes:

- chunked measurement;
- safe break opportunities according to the text-layout policy;
- raster fallback where appropriate;
- rejection when the implementation cannot guarantee bounded processing.

Silent content loss is forbidden.

---

## 12.5 Parser complexity attacks

Test inputs MUST include structures intended to trigger worst-case parser behavior.

Examples include:

```text
deeply nested emphasis markers
large repeated tables
huge link/reference sets
long delimiter runs
extreme nested lists
very long code fences
large unbroken text
```

The goal is not merely avoiding crashes; processing must remain within explicit work and memory budgets.

---

# 13. Image Resource Limits

Images require multiple independent limits because compressed byte size does not predict decoded memory usage.

---

## 13.1 Compressed input size

Every image source MUST have a compressed-input byte limit.

### Provisional Baseline

```text
32 MiB
```

This applies whether the image originates from:

- `.td`;
- a user-selected local file;
- explicit external asset resolution;
- clipboard/import;
- another supported image source.

Network-resolved assets MUST be bounded while downloading and MUST NOT first download an unlimited response before checking its size.

---

## 13.2 Dimension preflight

Where the image format and platform decoder permit it, Rastrio SHOULD inspect image dimensions before allocating the full decoded bitmap.

The following calculation MUST use checked arithmetic:

```text
pixelCount = width × height
```

Negative, zero where invalid, overflowing, or nonsensical dimensions MUST be rejected.

### Provisional encoded dimension ceiling

```text
32,768 pixels per axis
```

This is a sanity ceiling, not a desired decode target.

---

## 13.3 Encoded pixel count

Even when dimensions individually appear acceptable, their product MUST be bounded.

### Provisional preflight ceiling

```text
100 megapixels
```

Images above the normal decoded working budget MAY still be accepted only when the platform decode service can safely downsample without allocating the complete source-resolution bitmap.

Otherwise they MUST be rejected before full decode.

---

## 13.4 Decoded memory estimation

Before full-resolution allocation where possible, Rastrio MUST estimate decoded memory.

Conceptually:

```text
minimumPixelBytes =
    width × height × bytesPerDecodedPixel
```

For an RGBA representation:

```text
bytesPerDecodedPixel ≈ 4
```

This is only the minimum pixel-storage estimate.

The implementation MUST also allow for:

- row buffers;
- decoder state;
- orientation transforms;
- temporary scaling buffers;
- raster conversion;
- runtime/object overhead.

The estimate MUST therefore not assume that `width × height × 4` equals the entire operation's memory cost.

---

## 13.5 Decode working-set budget

### Provisional Baseline

```text
24 megapixels decoded target
128 MiB total image-decode working-set budget
```

The memory budget is authoritative where it conflicts with the pixel-count target.

A platform with significantly higher bitmap overhead MAY select a smaller effective pixel limit.

---

## 13.6 Downscale behavior

Images intended for thermal printing will normally require far fewer pixels than modern camera images.

Rastrio SHOULD therefore downsample as early as practical toward the resolution actually required for:

- crop/edit operations;
- preview;
- selected printer output.

Where the decoder supports sampled decode, Rastrio SHOULD prefer:

```text
decode near required working resolution
```

over:

```text
decode enormous source at full resolution
→ allocate
→ resize down
```

Downscaling MAY be automatic when it does not remove image content and the result remains sufficient for the requested output resolution.

Cropping or other semantic content removal MUST follow the user-selected image workflow; it MUST NOT be performed silently merely to satisfy a resource limit.

---

## 13.7 Decompression bombs

Image formats can act as decompression bombs independently of ZIP containers.

Safe image handling therefore requires all of:

```text
compressed-byte limit
dimension validation
pixel-count validation
checked memory estimate
bounded decoder allocation
bounded post-decode processing
```

A decoder crash or out-of-memory condition is not an acceptable substitute for these checks.

---

# 13A. Font Resource Limits

Rastrio's default authoritative raster typography uses project-controlled bundled fonts.

Bundled font files are trusted build inputs only after their exact bytes, hashes, versions, and licenses have been reviewed and pinned by the repository.

Explicitly imported/user-selected font files are untrusted binary input.

---

## 13A.1 Imported font file size

An explicitly imported font file MUST have a byte-size ceiling before the font parser is invoked.

### Provisional Baseline

```text
32 MiB per imported font file
```

This is a trusted application-policy starting point, not a portable document-format limit.

A future measured value MAY be lower or higher without changing `.td`, `.tcfg`, or text semantics.

---

## 13A.2 Font parser work

File size alone is not sufficient to bound font-parser work.

The font adapter MUST apply finite limits or bounded work accounting to applicable structures such as:

```text
font tables
glyph count
variation axes/instances
cmap entries/subtables
name records
color/bitmap tables where encountered
outline complexity
composite-glyph recursion
shaping-table lookup complexity
```

This specification intentionally does not invent permanent numeric maxima for each font-format structure before the selected FreeType/HarfBuzz integration is measured.

Before user-font import is enabled in production, the implementation MUST establish concrete trusted limits or equivalent bounded-work guards for the selected parser/backend.

Malformed or over-complex fonts MUST fail with a structured diagnostic rather than causing unbounded CPU, recursion, allocation, or native-library failure propagation.

---

## 13A.3 Glyph raster and shaping caches

Glyph, face, shaping-plan, and related font caches MUST be bounded.

They MUST NOT grow monotonically with every unique code point, font, size, style, or document processed.

Cache eviction MUST NOT change text correctness. Evicted glyphs/plans MAY be deterministically recomputed from the still-valid selected font resource.

Project-wide resource accounting SHOULD distinguish:

```text
font parser working memory
glyph/shaping cache memory
final prepared raster memory/storage
```

because those are independent costs.

---

## 13A.4 User-selected platform fonts

An explicitly selected platform-installed font does not require Rastrio to copy the entire system font inventory into memory.

Enumeration, metadata reads, and font opening MUST remain bounded by platform-adapter policy.

If the selected platform font is unavailable or cannot be opened safely within policy, preparation MUST fail with a diagnostic rather than scanning an unbounded fallback set.

The default font path MUST NOT enumerate arbitrary system fonts for implicit fallback.

---

## 13A.5 No network font fetching

Normal preparation MUST NOT fetch missing fonts from the network.

A missing bundled, imported, or explicitly selected platform font is a local resource-resolution failure.

This preserves offline-first behavior and prevents an unavailable font from turning text preparation into unbounded or attacker-directed network work.

---

# 14. Raster Engine Resource Limits

`core-raster` MUST support bounded-memory operation.

The engine MUST NOT require an entire long print job to exist as one raster allocation.

---

## 14.1 Raster rows

The canonical packed row size for a one-bit raster is:

```text
rowBytes = ceil(widthDots / 8)
```

Equivalent integer arithmetic MUST be overflow-safe.

Padding bits MUST remain deterministic as required by the raster specification.

---

## 14.2 Band height

Raster band height is a physical/preparation characteristic, not a global transport write size.

The selected band height SHOULD consider:

```text
PrinterProfile preferred band guidance
application raster working-set budget
raster strategy requirements
operation dimensions
```

### Provisional initial tuning value

```text
256 raster rows
```

This is not a portable-format requirement and MUST NOT override a stricter validated printer constraint.

---

## 14.3 Raster band byte size

A single prepared raster band MUST be bounded.

### Provisional Baseline

```text
2 MiB packed raster payload per band
```

If the chosen width and preferred band height would exceed this value, preparation MUST reduce the band height.

Conceptually:

```text
bandHeight =
    min(
        profilePreferredHeight,
        rowsThatFitApplicationBandBudget,
        otherValidatedPrinterConstraints
    )
```

---

## 14.4 Working buffers

Raster transforms MUST have a known working-set bound.

### Provisional Baseline

```text
16 MiB raster scratch working set
```

This budget excludes a separately accounted source image that is already owned by the image pipeline, but includes temporary working data created by the raster operation.

Algorithms SHOULD be implemented row-wise or band-wise where practical.

Floyd–Steinberg and similar error-diffusion algorithms SHOULD retain only the state required for continuity rather than the full output image.

---

## 14.5 Simultaneously retained bands

The raster pipeline SHOULD operate with minimal look-ahead.

### Provisional Default

```text
at most 2 complete raster bands retained simultaneously
```

A different number MAY be used where algorithmic continuity requires it, but the resulting bytes MUST remain inside the raster working-set budget.

---

## 14.6 Total finalized prepared raster/graphic output

Streaming or spooling prevents resident-memory exhaustion but does not permit unbounded total work or temporary-storage use.

The preparation process MUST therefore track cumulative finalized prepared raster/graphic payload bytes across the complete job, including as applicable:

```text
raster text
raster images
raster QR/barcodes
semantic checklist-marker raster/graphics
registration/cut-guide raster where represented as prepared raster
other finalized monochrome prepared graphic payloads
```

### Provisional Baseline

```text
128 MiB cumulative finalized prepared raster/graphic payload per prepared job
```

This is a total-work/storage ceiling, not a requirement to keep 128 MiB resident in memory.

Approaching a large-job threshold SHOULD produce a structured warning before physical printing.

The exact user-confirmation threshold SHOULD be selected through measurements of:

- real printer speed;
- paper consumption;
- Bluetooth/USB throughput;
- typical use cases.

It MUST NOT be derived only from the maximum memory budget.

---

# 15. Immutable `PreparedPrint` and Bounded Prepared-Artifact Storage

`PreparedPrint` is the authoritative immutable physical plan owned by `core-printer`.

Immutability does not require all finalized raster/graphic bytes for the complete job to be held in one memory collection.

A prepared operation MAY reference immutable finalized content through the portable prepared-artifact contract backed by:

```text
bounded in-memory bands
application-controlled temporary/spool storage
another deterministic immutable chunk store
```

The following requirements are normative:

1. physical geometry is final before the artifact is published;
2. shaping, scaling, dithering, rasterization, segmentation, and other output-affecting preparation are final;
3. artifact identity, dimensions, ordering, and content remain stable for the required `PreparedPrint` lifetime;
4. preview and the active protocol encoder observe the same finalized content or equivalent immutable snapshots;
5. reading artifact content cannot invoke a new output-affecting rendering decision;
6. portable Core APIs do not expose platform file handles or attacker-controlled filesystem paths;
7. all backing storage and index metadata are resource-bounded;
8. cancellation and preparation disposal release owned backing resources;
9. if finalized content becomes unavailable or corrupt, the preparation is invalid and consumers MUST fail rather than silently regenerate it.

---

## 15.1 Resident-memory budget

Prepared-artifact storage MUST NOT defeat raster working-set limits by retaining every band in memory.

Resident prepared-raster memory remains governed by:

```text
raster scratch working set
simultaneously retained band policy
preview tile/cache policy
encoder/transport queue policy
```

The cumulative 128 MiB job ceiling is not a 128 MiB resident-memory allowance.

---

## 15.2 Backing-store byte accounting

A spool-backed implementation MUST count bytes actually written to the backing store.

Declared/planned raster sizes MAY be used for early rejection, but actual stored bytes and metadata must remain bounded.

The safe effective store limit is conceptually:

```text
min(
    trusted job prepared-payload ceiling,
    trusted platform/application temporary-storage budget
)
```

If an implementation uses a representation with material storage overhead beyond raw prepared payload, that overhead MUST be included in platform/application temporary-storage accounting.

Untrusted content MUST NOT select or enlarge the spool budget.

---

## 15.3 Artifact indexing

Artifact indexes/chunk tables MUST also be bounded.

A tiny prepared payload MUST NOT be able to create millions of zero-length or microscopic artifact records.

Artifact/chunk count SHOULD be related to existing operation/band complexity limits and MUST have a finite implementation ceiling.

---

## 15.4 Lifecycle and cleanup

A prepared-artifact owner MUST define:

```text
creation
publication/finalization
read lifetime
cancellation behavior
normal disposal
abnormal-process cleanup strategy where platform support permits
```

Deletion MUST NOT occur while a still-valid `PreparedPrint` is expected to be previewed or encoded.

Conversely, completed/abandoned preparations MUST NOT leave unbounded orphaned spool data.

---

## 15.5 Integrity

Where spool-backed artifacts could be corrupted or confused across preparations, implementations SHOULD use stable artifact identity and integrity checks appropriate to the backing mechanism.

A content hash MAY be used.

Integrity metadata is an implementation/runtime concern unless another specification explicitly persists it.

---

# 16. Preview Resource Limits

Long-document preview MUST be virtualized.

Rastrio MUST NOT require one bitmap or canvas covering the complete physical height of a long receipt.

---

## 16.1 Preview tiles

Preview rendering SHOULD be divided into tiles or equivalent bounded render units.

### Provisional Baseline

```text
maximum tile area: 2 megapixels
preferred vertical tile height: 1,024 pixels
```

Tile height MAY vary according to:

- current zoom;
- preview width;
- platform renderer;
- device memory;
- wide-document mode.

The byte budget is more important than preserving an exact tile height.

---

## 16.2 Preview cache

Rendered preview caching MUST be byte-budgeted.

It MUST NOT merely cache an unlimited number of tiles.

### Provisional Baseline

```text
32 MiB rendered preview tile cache
```

The number of retained tiles SHOULD be derived from actual tile memory cost.

For example:

```text
maxCachedTiles =
    cacheByteBudget / actualTileByteCost
```

with appropriate minimum/maximum handling.

---

## 16.3 Lazy rendering

Only preview regions that are visible or reasonably near the visible viewport SHOULD normally be rendered.

A long receipt MUST NOT cause every preview tile to be rasterized before the user can view the first screen.

The renderer SHOULD support:

```text
visible tile
+
bounded prefetch window
+
bounded LRU or equivalent cache
```

---

## 16.4 Long-document virtualization

UI containers MUST use virtualization or an equivalent incremental representation for extremely long documents.

The implementation MUST avoid creating an enormous Compose hierarchy containing one heavyweight preview node per physical row or similarly granular unit.

Preview model structure SHOULD be coarse enough to remain manageable while retaining authoritative geometry.

---

## 16.5 Wide landscape documents

A wide landscape preview MUST NOT allocate one enormous horizontal bitmap.

Wide documents SHOULD support:

- vector/scene-style rendering where practical;
- horizontal tiling;
- vertical tiling;
- two-dimensional tiling where both dimensions are large;
- assembled logical inspection independently of physical strip preview.

Preview tile budgets apply in both axes.

---

## 16.6 Zoom

Zooming MUST NOT multiply retained raster memory without bound.

Higher zoom SHOULD cause visible tiles to be rerendered at an appropriate resolution while obsolete high-resolution tiles become evictable.

A preview cache MUST remain governed by its byte budget independent of zoom level.

---

# 17. Document and Layout Resource Limits

Thermal documents can be intentionally long, so Rastrio MUST not assume that document height is comparable to a sheet of paper.

Document complexity still requires finite limits.

---

## 17.1 Block count

### Provisional Baseline

```text
100,000 document blocks
```

The limit applies to the resulting canonical document model, not merely Markdown AST nodes.

---

## 17.2 Inline node count

### Provisional Baseline

```text
500,000 inline nodes
```

Text stored inside a node remains subject to source and string-resource controls.

---

## 17.3 Tables

Tables require additional limits because several algorithms can become proportional to:

```text
rows × columns
```

or worse if implemented poorly.

### Provisional Baseline

```text
rows per table:       20,000
columns per table:       256
cells per table:      250,000
```

The cell-count limit is independent of the row and column maxima.

For example, passing both the row limit and column limit does not permit:

```text
20,000 × 256
```

cells if the resulting total exceeds the configured cell budget.

---

## 17.4 Table layout complexity

Column sizing MUST avoid algorithms whose practical cost becomes uncontrolled for large tables.

Where an exact algorithm has high complexity, the implementation SHOULD:

- use bounded sampling;
- use incremental width calculation;
- impose an explicit work budget;
- or reject documents exceeding the supported complexity.

Such approximations MUST preserve content; they MUST NOT silently remove cells.

---

## 17.5 Logical width

Logical landscape width MUST be finite.

### Provisional Baseline

```text
1,000 mm maximum author/resolved logical width
```

This value exists to prevent absurd geometry and pathological segment generation.

It is not a printer paper-width limit.

It MAY be revised when wide-document workflows are measured.

---

## 17.6 Logical height

Rastrio SHOULD NOT impose a small page-like logical-height limit because long receipts are a first-class use case.

Instead, logical height MUST be constrained indirectly through bounded:

```text
block count
inline-node count
table complexity
layout-operation count
coordinate arithmetic
prepared-operation count
raster output
```

Coordinates MUST remain representable without overflow in the chosen internal unit model.

An accumulated logical height that exceeds the engine's checked coordinate range MUST fail with a structured diagnostic.

---

## 17.7 Layout work/items

The layout engine SHOULD track the number of generated geometry items or equivalent work units.

### Provisional Baseline

```text
100,000 generated layout cost items (Phase 3B default)
```

This protects against inputs where a relatively small semantic model expands into pathological layout complexity.
The normative requirement is early bounded allocation, not a permanent numeric format limit.
The current Phase 3B default and its retained-cost accounting/rationale are defined below.

---

## 17.8 Pathological geometry

Rastrio MUST reject:

- NaN dimensions where floating-point representations are used;
- infinite values;
- negative dimensions where not explicitly meaningful;
- integer overflow;
- coordinates that cannot be represented safely;
- absurd multiplication during unit conversion;
- zero-progress layout loops.

Layout loops MUST guarantee forward progress.

For example, a block that cannot fit on an empty line MUST NOT repeatedly request another identical line forever.

---

# 18. Landscape Segmentation Limits

Wide logical layout occurs before physical segmentation.

Physical segment generation MUST nevertheless be bounded.

### Provisional Baseline

```text
256 physical segments per prepared print
```

The segment count MUST be calculated using checked arithmetic before allocating a complete segment collection.

If segmentation would exceed the configured limit, preparation MUST stop before creating the full output plan.

The UI MAY allow the user to revise:

- logical width;
- overlap;
- print options;
- selected printer;

before attempting preparation again.

Content MUST NOT simply be dropped after the maximum segment number.

---

# 19. Printer Constraints vs Transport Constraints

This distinction is architectural and MUST remain explicit.

## 19.1 Printer constraints

Printer-side constraints originate from the validated effective `PrinterProfile`.

Examples include:

```text
printable width
recommended raster band height
known printer buffer limitations
printer-side pacing guidance
supported raster methods
```

These constraints influence physical preparation.

---

## 19.2 Transport/session constraints

Transport constraints belong to the active connection/session implementation.

Examples include:

```text
maximum write chunk
preferred write chunk
queued/in-flight byte budget
write timeout
connection pacing
flush behavior
platform API limitations
```

These do not belong in `.td`.

They also do not become permanent characteristics of a portable `.tcfg` profile merely because one Bluetooth stack happens to require them.

A `PrinterInstance` or platform session MAY persist transport-specific user/session preferences where appropriate.

---

## 19.3 Effective transmission policy

The active print session combines both classes.

Conceptually:

```text
PreparedPrint
    ↓
ESC/POS encoded chunks
    ↓
effective transmission policy
    =
printer guidance
    ∩
transport/session constraints
    ↓
physical writes
```

For example, a printer may tolerate large raster bands while a Bluetooth implementation requires small writes.

The transport MAY split encoded data further.

It MUST NOT cause the raster image to be re-dithered or the document to be re-laid out.

---

# 20. ESC/POS Encoding Limits

`core-escpos` MUST operate incrementally.

It MUST NOT build one giant print-job `ByteArray`.

---

## 20.1 Encoder chunks

The encoder SHOULD emit bounded chunks or an equivalent streaming sequence.

### Provisional encoder ceiling

```text
64 KiB per emitted encoder chunk
```

This is not the physical transport write size.

The active transport is permitted and expected to split an encoder chunk further.

---

## 20.2 Encoder state

Protocol encoder state MUST remain proportional to the current operation or current band, not total document length.

The encoder MUST NOT accumulate prior bytes merely for convenience.

---

## 20.3 Total output accounting

The print pipeline SHOULD maintain cumulative encoded-byte accounting.

This supports:

- progress;
- diagnostics;
- large-job warnings;
- partial-transmission metadata;
- regression testing.

The accounting mechanism MUST use overflow-safe arithmetic.

---

# 21. Transport Buffering

Every platform transport MUST use bounded buffering.

Unbounded producer/consumer queues are forbidden.

---

## 21.1 Queued data

### Provisional Baseline

```text
256 KiB encoded bytes queued ahead of transport
```

Backpressure MUST occur when this budget is reached.

The encoder or print-session producer MUST wait, suspend, or otherwise stop producing additional buffered output until space is available.

Dropping print data is not an acceptable backpressure strategy.

---

## 21.2 Write chunks

There MUST NOT be a single universal printer write-chunk constant in generic Core.

Each transport/session MUST expose or internally use a validated bounded write policy appropriate to that transport.

For example:

```text
Android Bluetooth RFCOMM
Android USB
Desktop serial
WebUSB
WebSerial
```

may legitimately use different write sizes.

Initial values MUST be chosen through:

- platform testing;
- H50i testing where relevant;
- Printer Lab chunk stress tests;
- failure/recovery tests.

---

## 21.3 Printer buffering

Printer-side buffer limitations MUST NOT be conflated with transport buffers.

A printer profile might recommend:

```text
small raster bands
```

while Bluetooth might independently use:

```text
even smaller write chunks
```

Both constraints apply without changing the underlying physical raster geometry.

---

# 22. Printing and Long Jobs

A technically valid print job can still consume excessive:

- time;
- paper;
- battery;
- transport bandwidth.

Rastrio SHOULD therefore distinguish:

```text
unsafe to process
```

from:

```text
safe but unusually large to print
```

The latter SHOULD use warning or confirmation behavior rather than automatic rejection where practical.

The confirmation threshold SHOULD be selected empirically using:

- estimated physical paper length;
- encoded byte count;
- segment count;
- expected printer throughput.

No arbitrary permanent paper-length number is established by this specification.

---

# 23. Cancellation

Every long-running stage MUST support cancellation at bounded work intervals where its calling environment supports cancellation.

This includes:

- archive loading;
- Markdown compilation;
- image processing;
- raster generation;
- layout;
- preparation;
- preview rendering;
- ESC/POS encoding;
- transport writes.

At minimum, cancellation SHOULD be checked between natural bounded units such as:

```text
archive entries
parser/compiler batches
image rows or tiles
raster bands
preview tiles
prepared operations
encoded chunks
transport writes
```

Cancellation MUST release temporary buffers and close owned resources.

---

## 23.1 Cancellation during physical printing

Once bytes may have reached the printer, cancellation MUST follow the same conservative semantics as other partial-transmission failures.

A cancelled job MAY already have produced paper output.

Cancellation MUST NOT automatically restart the job.

---

# 24. Temporary and Spool Storage

Temporary storage includes general processing scratch as well as the bounded immutable prepared-artifact backing stores defined in Section 15.

Any such storage MUST:

- use an application-controlled location or equivalent platform-private storage abstraction;
- use non-conflicting application-generated identities;
- prevent path injection and traversal;
- enforce byte and metadata/index budgets;
- close handles/resources deterministically;
- participate in cancellation cleanup;
- delete abandoned data when no longer required where practical;
- tolerate/recover from orphaned temporary data after abnormal process termination where the platform allows;
- avoid exposing printable content or imported fonts to unrelated applications;
- never accept an attacker-supplied path as the authoritative backing-store location.

Prepared-artifact cleanup has an additional correctness constraint:

> backing data MUST remain readable for the lifetime during which its finalized `PreparedPrint` is valid.

A cleanup routine MUST NOT delete live prepared artifacts merely to satisfy cache pressure.

If storage pressure prevents maintaining the artifact under policy, preparation MUST fail or be invalidated explicitly.

Temporary/spool storage is not permission for unlimited resource use.

Disk bytes, file/object count, index metadata, and cleanup work MUST remain bounded.

---

# 25. Failure Behavior

Limit failures MUST be deterministic and explicit.

Rastrio MUST NOT silently lose significant printable content.

The default response depends on the resource involved.

| Condition | Required behavior |
|---|---|
| malformed or unsafe archive | reject |
| archive expansion exceeds hard ceiling | reject |
| archive entry count exceeds ceiling | reject |
| JSON exceeds safety/complexity ceiling | reject |
| unsupported unsafe nesting | reject |
| Markdown exceeds parser safety capability | reject compilation |
| extremely large but safely processable document | warn or require confirmation where appropriate |
| image source too large to decode safely | downsample safely when possible; otherwise reject |
| image pixels exceed safe decoded working set | sampled decode/downscale where possible |
| imported font exceeds file/parser-work policy | reject font/import or affected preparation |
| font/shaping cache budget reached | evict recomputable cache entries |
| prepared-artifact payload/store ceiling exceeded | reject preparation; do not truncate printable output |
| finalized prepared artifact becomes unreadable/corrupt | invalidate/fail affected preparation; do not regenerate silently |
| preview cache budget reached | evict cached tiles |
| preview document extremely long | continue through virtualization |
| raster band would exceed working budget | reduce band height |
| total raster job exceeds configured hard job ceiling | reject preparation |
| printer has smaller safe raster limit | obey profile-derived constraint |
| transport requires smaller writes | split encoded data |
| queued transport bytes reach budget | apply backpressure |
| landscape segment count exceeds supported ceiling | reject preparation; do not omit segments |
| user cancellation before physical output | cancel cleanly |
| cancellation/failure after possible transmission | report partial/ambiguous output |

---

# 26. Downscaling Rules

Downscaling is permitted only where it is semantically appropriate.

Images MAY be downscaled to a resolution sufficient for their intended physical output.

Preview render targets MAY be rendered at viewport-appropriate resolution.

The following MUST NOT be silently downscaled or discarded as a generic resource-limit workaround:

- text content;
- table rows;
- Markdown sections;
- QR payload content;
- document blocks;
- physical landscape strips.

If content cannot be processed within a hard limit, Rastrio MUST report that condition rather than silently omitting content.

---

# 27. Truncation Rules

Truncation is allowed only when loss is explicitly non-semantic or the affected data is itself diagnostic/display metadata where truncation is documented.

Potentially acceptable examples include:

- shortening an excessively long filename in a diagnostic display while retaining the underlying validation result;
- limiting log-message formatting;
- limiting preview diagnostic detail lists while separately reporting total count.

Rastrio MUST NOT silently truncate:

```text
Markdown source
document body
table rows
table columns
QR payload
image content
print operations
raster rows
landscape segments
```

merely to fit a processing limit.

---

# 28. Structured Diagnostics

Resource-limit failures MUST use structured diagnostics.

A resource diagnostic SHOULD include fields conceptually equivalent to:

```kotlin
data class ResourceLimitDiagnostic(
    val code: String,
    val severity: Severity,
    val resource: String,
    val observed: Long?,
    val limit: Long?,
    val unit: String?,
    val stage: String,
    val message: String,
)
```

Exact types are not normative.

Diagnostic text MUST avoid embedding printable user content unless required for local user-facing explanation.

Useful metadata includes:

```text
limit type
configured limit
observed size/count
processing stage
archive entry index/path where safe
image dimensions
node count
table dimensions
segment count
raster byte count
```

---

# 29. Diagnostic Code Guidance

Existing PRD diagnostic families SHOULD remain authoritative.

Recommended resource-related codes include:

```text
TD120  .td archive resource limit exceeded
TD121  .td expanded-size limit exceeded
TD122  .td entry-count limit exceeded
TD123  .td entry-size limit exceeded
TD124  .td JSON resource limit exceeded

PRF120 .tcfg resource limit exceeded

MD120  Markdown source resource limit exceeded
MD121  Markdown structural complexity exceeded

IMG120 Image input size exceeded
IMG121 Image pixel/dimension limit exceeded
IMG122 Image decode working-set limit exceeded

TXT120 Font input/resource limit exceeded
TXT121 Text shaping/raster work limit exceeded

LAY120 Document/layout complexity exceeded
LAY121 Table complexity exceeded
LAY122 Geometry range exceeded

PRN120 Prepared raster/graphic payload limit exceeded
PRN121 Physical segment limit exceeded
PRN122 Prepared-artifact backing-store limit exceeded
PRN123 Prepared-artifact integrity/lifetime failure

PRV120 Preview tile/cache resource limit exceeded

ESC120 Encoder resource limit exceeded

TRN120 Transport buffering limit exceeded
```

Exact codes MAY be refined when subsystem diagnostic registries are implemented.

Existing assigned diagnostic codes MUST NOT later be silently repurposed for unrelated errors.

---

# 30. Warnings vs Fatal Diagnostics

A diagnostic severity SHOULD correspond to recoverability.

## Warning

Use a warning when the operation remains correct and safe.

Examples:

- unusually large but supported print job;
- long image downsampled to safe target resolution;
- many physical segments;
- resource use approaching a configured operational threshold.

---

## Error/Fatal

Use a fatal diagnostic when continuing would be unsafe, ambiguous, or unsupported.

Examples:

- archive expansion limit exceeded;
- arithmetic overflow;
- impossible dimensions;
- parser depth exceeded;
- prepared segment limit exceeded;
- no safe image decode strategy.

Fatal diagnostics prevent the affected operation.

---

# 31. Resource Budgets Across Pipeline Stages

Resource ownership MUST remain stage-local where possible.

A single input can encounter several independent budgets:

```text
.td input
  ↓
archive budgets
  ↓
JSON budgets
  ↓
ThermalDocument complexity budgets
  ↓
layout budgets
  ↓
PreparedPrint budgets
  ↓
preview budgets
  ↓
protocol-encoder budgets
  ↓
transport/session budgets
```

Passing an earlier limit does not imply permission to ignore a later limit.

For example:

```text
valid .td
```

does not imply:

```text
safe to render one giant preview bitmap
```

and:

```text
valid PreparedPrint
```

does not imply:

```text
safe to buffer all encoded bytes in Bluetooth memory
```

---

# 32. Memory Accounting

Where practical, resource policy SHOULD use explicit byte budgets rather than assumptions based solely on object counts.

Important memory categories include:

```text
input byte buffers
decoded strings
JSON parser objects
document model
Markdown AST
decoded images
image-processing scratch
font parser/shaping working memory
glyph/shaping caches
raster scratch
prepared raster bands
prepared-artifact/spool bytes and index metadata
preview tiles
encoded output queues
transport buffers
```

An implementation SHOULD avoid simultaneously retaining large representations from multiple stages when one can safely be released.

Example:

```text
raw image bytes
+
full-resolution decoded RGBA
+
second resized RGBA
+
grayscale copy
+
full 1-bit raster
+
encoded raster ByteArray
```

SHOULD NOT all remain resident if a bounded pipeline can release or stream intermediate representations.

---

# 33. Platform-Specific Tightening

The shared Core policy defines safe application ceilings, but a target MAY require stricter effective budgets.

Examples:

```text
Android low-memory device
Web/Wasm browser heap
Desktop/JVM with larger available heap
```

Platform-specific limits MUST only tighten or appropriately adapt shared policy unless a larger value has been validated as safe.

Platform-specific adjustments MUST NOT change document semantics.

For example, lowering the preview tile cache on Web must not change `PreparedPrint`.

---

# 34. Runtime Memory Pressure

Where a platform exposes reliable memory-pressure notifications, preview caches and other disposable caches SHOULD respond by releasing reclaimable data.

Correctness MUST NOT depend on such notifications.

Hard safety must be established by explicit limits before memory pressure occurs.

A runtime out-of-memory exception MUST be treated as a failure condition, not as the primary resource-control mechanism.

---

# 35. Configuration and Persistence

Resource limits are trusted application configuration.

They MUST NOT be serialized inside normal `.td` content.

Imported `.tcfg` files MUST NOT be able to enlarge application parsing or memory ceilings.

A development build MAY expose resource-policy overrides for:

- profiling;
- fuzzing;
- boundary testing;
- device characterization.

Production UI SHOULD NOT expose arbitrary hard-safety-ceiling increases as casual user settings.

Where configurable values are persisted, they MUST be validated before use.

---

# 36. Limit-Version Independence

Resource-policy changes do not inherently change:

```text
.td containerVersion
documentSchemaVersion
.tcfg schema version
```

For example:

```text
Rastrio 1.0:
    max expanded archive = X

Rastrio 1.2:
    max expanded archive = Y
```

does not by itself require a `.td` migration.

Portable schema compatibility and implementation resource capability are separate concerns.

---

# 37. Measuring and Revising Provisional Limits

Before promoting provisional values to stable defaults, the project SHOULD measure:

- peak heap use;
- allocation rates;
- parsing time;
- layout time;
- raster-generation time;
- preview responsiveness;
- transport throughput;
- cancellation responsiveness;
- behavior on minimum-supported Android hardware;
- Desktop/JVM behavior;
- Web/Wasm constraints when the Web phase begins.

Limits SHOULD include sufficient safety margin rather than matching the largest successful test exactly.

Measurements SHOULD include adversarial as well as normal input.

A value MUST NOT be raised merely because one high-memory developer machine can process a larger fixture.

---

# 38. Boundary Tests

Every hard numeric resource limit MUST have automated boundary coverage.

For a limit `N`, tests SHOULD exercise at least:

```text
N - 1
N
N + 1
```

or equivalent meaningful values where byte encoding makes exact construction impractical.

Tests MUST verify:

- accepted boundary behavior;
- rejected over-limit behavior;
- correct diagnostic;
- absence of partial semantic output;
- cleanup of temporary resources.

---

# 39. `.td` Resource Tests

Required tests include:

```text
compressed archive exactly at limit
compressed archive over limit
expanded archive exactly at limit
expanded archive over limit
many tiny entries
entry count at limit
entry count over limit
single oversized entry
oversized manifest
oversized document.json
asset count at limit
asset count over limit
oversized asset
false ZIP size metadata
actual expansion exceeding declared size
long archive path
too many path segments
normalized duplicate entries
path traversal combined with resource attack
high-compression ZIP bomb
corrupt archive during bounded expansion
```

Tests MUST verify that actual expanded bytes are counted independently of ZIP metadata.

---

# 40. `.tcfg` Resource Tests

Required tests include:

```text
file size boundary
nesting boundary
excessive nesting
very large strings
many properties
large arrays
token/node limit
unknown-property resource consumption before rejection
duplicate-key attack before map collapse
comments/trailing-comma/lenient-JSON attack under resource limits
repeated capability entries
unsafe/raw-command field combined with large payload
```

Validation MUST terminate within controlled resource use.

---

# 41. Markdown Resource Tests

Required tests include:

```text
source size boundary
normalized source-line exact/over boundary and many short lines
cached-token work exact/over boundary, unmatched images and legitimate images
multi-million-line pre-parser rejection
AST node boundary
deeply nested lists
deep block quotes
pathological emphasis
very long code blocks
long delimiter sequences
huge tables
very long unbroken token
many links
many image references
mixed pathological constructs
```

Tests SHOULD assert that processing time does not exhibit unexpected super-linear growth for representative adversarial families.

---

# 42. Image Resource Tests

Required tests include:

```text
compressed size boundary
huge declared width
huge declared height
pixel-count overflow attempt
100+ megapixel source
tiny compressed / huge decoded image
malformed dimensions
truncated image stream
orientation transform expansion
sampled decode path
decoder without safe sampled decode
working-set rejection
automatic safe downscale
```

Tests MUST prove that unsafe geometry is detected before the application intentionally allocates the corresponding full bitmap where preflight is supported.

---

# 42A. Font Resource Tests

Required tests for explicitly imported fonts include:

```text
file size exactly at provisional limit
file size over limit
truncated/malformed font
pathological table count/structure
composite-glyph recursion/pathology
very large glyph repertoire
complex shaping tables
many font sizes/styles causing cache churn
font cache eviction
cancellation during font parsing/shaping
explicit platform font unavailable
default path proving no unbounded system-font scan
```

Where the selected FreeType/HarfBuzz integration exposes format-specific limits or failure modes, regression tests SHOULD exercise them.

Tests MUST demonstrate that malformed or adversarial fonts cannot force unbounded native memory, recursion, CPU work, or cache growth.

---

# 43. Raster Resource Tests

Required tests include:

```text
band size exactly at limit
band requiring smaller height
full-width raster
very long raster
odd widths
width not divisible by 8
many consecutive bands
scratch-buffer accounting
error-diffusion continuity across bands
cancellation between bands
cumulative finalized raster/graphic payload ceiling
spool-backed prepared artifact at payload boundary
prepared artifact actual stored-byte accounting
bounded artifact index/chunk count
prepared artifact cancellation cleanup
prepared artifact lifetime through preview + encoding
prepared artifact corruption/unreadable failure
```

A long-raster test MUST verify that resident memory remains approximately bounded as total raster length increases.

A spool-backed test MUST also verify that preview and encoding consume identical finalized content and that missing/corrupt finalized content is never silently regenerated.

---

# 44. Preview Resource Tests

Required tests include:

```text
very long receipt
very wide landscape document
long + wide document
2-megapixel tile-area boundary
preferred 1,024-pixel tile-height behavior
32 MiB cache boundary
cache eviction
rapid scrolling
zoom changes
cache under memory pressure
off-screen tile disposal
revisiting evicted content
```

Tests MUST demonstrate that increasing document length does not require a proportionally large monolithic preview bitmap.

Where practical, memory tests SHOULD compare:

```text
10× document length
```

against resident preview memory and confirm that memory remains governed primarily by the viewport/cache policy rather than total document height.

---

# 45. Document/Layout Resource Tests

Required tests include:

```text
block-count boundary
inline-node boundary
table row boundary
table column boundary
table cell boundary
wide table
nested structures
huge logical width
coordinate overflow
unit conversion overflow
zero-progress layout case
operation-count boundary
segment-count boundary
```

Property-based tests SHOULD generate combinations of:

```text
dimensions
nesting
table geometry
segment widths
overlap
logical widths
```

and verify that all generated geometry remains finite, bounded, and forward-progressing.

---

# 46. Printing and Transport Resource Tests

Transport fakes MUST support resource-related scenarios including:

```text
small transport write limit
small queued-byte limit
slow consumer
temporary stall
partial write
timeout
cancellation
disconnect
backpressure
very long encoded stream
printer profile requesting small raster bands
transport requiring smaller write chunks
```

Tests MUST prove:

```text
encoder output can stream
queue memory remains bounded
transport chunking does not alter PreparedPrint
transport changes do not trigger re-layout
transport changes do not trigger re-dithering
```

---

# 47. Adversarial Combination Tests

Limits MUST also be tested in combination.

Examples include:

```text
large .td
+
many assets
+
large document.json

small ZIP
+
high expansion
+
many entries

large Markdown
+
deep nesting
+
huge tables

large image
+
rotation
+
resize
+
dither

wide document
+
many segments
+
large raster operations

slow Bluetooth transport
+
large raster print
+
cancellation
```

Independent unit tests alone are insufficient because multiple individually acceptable allocations can exceed the intended combined working set.

---

# 48. Memory Regression Tests

Performance/resource regression tests SHOULD record peak memory for representative fixtures.

Recommended fixture categories:

```text
small receipt
large text receipt
extreme text receipt
normal image
large camera image
full-width raster
very long raster
large Markdown document
large table
wide landscape document
many-strip landscape document
```

CI environments may not produce perfectly stable absolute heap measurements.

Where absolute heap assertions are unreliable, regression tests SHOULD still detect major multiplicative regressions or unexpected retention.

---

# 49. Performance Regression Tests

Resource limits are not a substitute for algorithmic performance testing.

Large fixtures SHOULD record or assert reasonable behavior for:

```text
parse duration
compile duration
layout duration
preparation duration
raster duration
preview first-visible-render latency
incremental scroll rendering
encoding throughput
```

Exact time budgets SHOULD be added only after repeatable benchmark infrastructure exists.

Performance thresholds must not be based on one developer workstation.

---

# 50. Fuzzing

Fuzzing is strongly recommended for:

- `.td` archive readers;
- `.td` JSON;
- `.tcfg` JSON;
- Markdown;
- image metadata adapters where practical;
- layout geometry;
- segmentation arithmetic.

Fuzzers MUST run under finite resource budgets themselves.

A fuzz result that merely reaches an expected resource-limit diagnostic is not a crash.

The following are failures:

- out-of-memory caused before enforcement;
- stack overflow;
- integer overflow;
- infinite loop;
- unbounded allocation;
- uncaught decoder exception escaping the intended boundary;
- silent content truncation.

---

# 51. Regression Policy

Every bug involving:

- excessive allocation;
- parser blowup;
- unbounded loops;
- resource-limit bypass;
- archive expansion;
- image bomb handling;
- malicious/pathological font handling;
- prepared-artifact/spool leakage or unbounded growth;
- enormous preview allocation;
- giant `ByteArray` creation;
- transport queue growth;

MUST receive a regression test where practical.

The regression fixture SHOULD be reduced to the smallest input that reliably demonstrates the failure.

---

# 52. Observability Without Sensitive Logging

Resource behavior SHOULD be observable without logging printable content.

Acceptable diagnostic/telemetry-style metadata includes:

```text
input byte count
archive entry count
expanded byte count
JSON depth
node count
image width/height
pixel count
estimated working bytes
raster band dimensions
raster byte count
preview tile count
preview cache bytes
segment count
encoded byte count
queued transport bytes
```

Logs MUST NOT include raw:

```text
Markdown
document bodies
QR payloads
images
user notes
```

by default.

---

# 53. Implementation Guidance

A reusable budget abstraction MAY be useful.

For example:

```kotlin
class ByteBudget(
    val maximum: Long,
) {
    private var used: Long = 0

    fun consume(bytes: Long) {
        require(bytes >= 0)
        val next = Math.addExact(used, bytes)

        if (next > maximum) {
            throw ResourceLimitExceeded(...)
        }

        used = next
    }
}
```

The exact implementation is not normative.

Equivalent abstractions may exist for:

```text
bytes
nodes
entries
pixels
cells
operations
segments
```

Resource-limit exceptions SHOULD normally be converted at subsystem boundaries into structured diagnostics rather than escaping directly to UI code.

---

# 54. Required Ownership Summary

| Resource | Primary Owner |
|---|---|
| `.td` compressed/expanded/archive limits | `core-document` |
| `.td` JSON/domain limits | `core-document` |
| `.tcfg` parsing/model limits | `core-profile` |
| Markdown source/AST limits | `core-markdown` |
| logical document complexity | `core-document` / `core-layout` |
| text-layout pathological work | `core-text` / `core-layout` |
| font input/parser/shaping/cache limits | `core-text` + platform/backend font adapter |
| image input/decode limits | platform decode adapter + `core-raster` policy |
| raster scratch/band limits | `core-raster` / `core-printer` |
| prepared-artifact payload/spool lifecycle limits | `core-printer` + platform backing-store adapter |
| printer buffer guidance | `PrinterProfile` |
| physical segment limits | `core-printer` |
| preview tile/cache limits | `core-preview` + shared UI renderer |
| protocol encoder buffering | active trusted protocol encoder (`core-escpos` for v1) |
| transport chunking/buffering | active platform transport/session |
| large-job user confirmation | shared Presentation/UI |

Ownership MUST remain consistent with the architectural dependency rules.

---

# 55. Required Architecture Invariants

The following resource-related invariants are mandatory.

1. `.td` and `.tcfg` processing is bounded from the first implementation.
2. Rastrio MUST NOT trust declared ZIP sizes without counting actual expanded bytes.
3. Strict JSON validation MUST occur without losing duplicate-key or unknown-property information.
4. Long documents MUST NOT require one giant bitmap.
5. Long print jobs MUST NOT require one giant `ByteArray`.
6. Image decoding MUST account for decoded dimensions, not only compressed file size.
7. Imported/user-selected fonts MUST be treated as bounded untrusted binary input.
8. Font and shaping caches MUST be evictable and bounded.
9. Resource arithmetic MUST be overflow-safe.
10. Preview memory MUST be governed by tiles/cache budgets rather than total receipt length.
11. Raster processing MUST support bands.
12. `PreparedPrint` MAY reference bounded immutable prepared artifacts; this MUST NOT cause all prepared raster bytes to become resident simultaneously.
13. Prepared-artifact disk/storage bytes and index metadata MUST be bounded.
14. Preview and protocol encoding MUST consume identical finalized prepared artifact content.
15. Missing/corrupt finalized prepared artifacts MUST fail; consumers MUST NOT silently regenerate output-affecting content.
16. Printer raster/buffer constraints come from the effective `PrinterProfile`.
17. Transport write/chunk/pacing constraints belong to the active transport/session.
18. Printer limits and transport limits MUST NOT be conflated.
19. Changing transport MUST NOT cause document re-layout or re-dithering.
20. Significant printable content MUST NOT silently disappear because a limit was reached.
21. Hard-limit violations MUST result in structured diagnostics.
22. Untrusted content MUST NOT be able to enlarge its own processing limits.
23. Cancellation MUST remain possible during long-running bounded operations.
24. Cancellation/disposal MUST clean up owned temporary/spool resources.
25. A partial or cancelled physical transmission MUST NOT be automatically retried when output may already have occurred.

---

# 56. Stable-Release Review

Before the first stable Android release, the project MUST review the provisional defaults in this document using actual measurements.

At minimum, review must cover:

```text
.td archive limits
.tcfg limits
Markdown limits
image pixel and memory limits
font import/parser/shaping/cache limits
raster working set
prepared-artifact/spool storage
preview cache
long-document behavior
large-table behavior
large raster printing
Bluetooth/USB and other active transport buffering
protocol encoder buffering
cancellation
minimum-supported Android device behavior
```

The review SHOULD classify each provisional value as one of:

```text
retain
raise
lower
replace with derived policy
replace with platform-specific policy
```

The reason for materially changing a limit SHOULD be documented in the commit, issue, benchmark notes, or relevant specification.

---

# 57. Future Platform Review

Desktop/JVM and Web/Wasm MUST receive their own resource-policy validation as they become product targets.

Desktop MUST NOT simply disable bounds because more memory is commonly available.

Web MUST NOT blindly inherit Android memory assumptions where browser/Wasm allocation behavior differs.

Portable formats remain common even where effective runtime processing limits differ.

---

# 58. Measurement Questions Before Stable Release

The following matters require empirical measurement rather than architectural guesswork.

They are tuning questions, not permission for unbounded interim implementations.

1. What image decode working-set ceiling is appropriate for the minimum-supported Android device after the image pipeline is implemented?
2. Whether the provisional **32 MiB imported-font file ceiling** should be retained, lowered, or replaced by a tighter parser-work policy after the selected FreeType/HarfBuzz integration is profiled and fuzzed.
3. What concrete glyph/shaping-cache byte budgets provide good performance on the Android baseline while remaining bounded?
4. Whether the current **32 MiB preview cache** provides smooth scrolling without unacceptable memory pressure on the Android baseline.
5. Which raster-band heights and printer-side buffering constraints are reliable across representative supported ESC/POS printer profiles. The H50i may contribute evidence, but MUST NOT define the generic default.
6. What Bluetooth/USB transport write sizes and pacing are reliable across supported Android versions and representative hardware.
7. At what estimated paper length or encoded job size should the UI require explicit large-print confirmation.
8. Whether the initial **128 MiB cumulative finalized prepared raster/graphic payload ceiling** should remain fixed, become platform-dependent, or be replaced by a combined operation/time/paper/storage policy.
9. What platform-private prepared-artifact spool budget is appropriate on the minimum-supported Android device, including orphan-cleanup behavior under storage pressure.
10. Whether Web/Wasm requires materially lower Markdown, image, font, preview, or document-complexity defaults.
11. Whether wide-document measurements justify raising or lowering the provisional **1,000 mm** logical-width and **256 physical-segment** ceilings.
12. Whether the selected GFM and strict JSON implementations provide sufficient native depth/work controls or require additional Rastrio-side bounded wrappers.
13. Whether Android/Desktop can use identical pinned HarfBuzz/FreeType settings with acceptably stable memory/performance, and what differences Web requires.

The provisional policy in this document applies until measured replacements are deliberately adopted.

---

# 59. Implementation Completion Criteria

A resource-sensitive feature is not complete until:

- all externally controlled sizes are identified;
- checked arithmetic is used;
- hard limits are enforced before unsafe allocation where practical;
- actual consumption is counted where metadata can lie;
- normal inputs remain usable;
- over-limit behavior is deterministic;
- structured diagnostics exist;
- cancellation is handled where applicable;
- temporary/spool resources are cleaned up without invalidating still-live preparations;
- imported-font parser/cache work is bounded where that feature is enabled;
- significant printable content is never silently truncated;
- boundary tests exist;
- adversarial tests exist;
- regression coverage exists for discovered failures;
- provisional values are centralized and replaceable;
- printer constraints remain separate from transport constraints.

---

# 60. Final Resource Model

Rastrio's resource model can be summarized as:

```text
             UNTRUSTED INPUT
                    │
                    ▼
        bounded bytes / structure
                    │
                    ▼
          bounded semantic model
                    │
                    ▼
       bounded text/font/layout work
                    │
                    ▼
          bounded physical plan
      + immutable prepared artifacts
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
  bounded preview       bounded protocol encoding
 tiles/cache/ranged          chunks
 artifact reads                │
                               ▼
                      bounded transport queue
                               │
                               ▼
                            Printer
```

For printer output:

```text
PrinterProfile
    │
    ├── raster-band guidance
    ├── printer buffer guidance
    └── validated printer-side pacing guidance where applicable
                 │
                 ▼
        effective transmission
                 ▲
                 │
Transport / Session
    ├── write chunk size
    ├── transport buffering
    ├── timeout
    └── connection pacing
```

These constraints combine into a safe transmission policy while remaining distinct architectural responsibilities.

The governing rule is:

> **No untrusted or arbitrarily large input may force Rastrio to allocate, parse, shape, rasterize, spool, render, prepare, encode, preview, or buffer an unbounded amount of data.**

At the same time:

> **Resource enforcement must preserve document semantics. When significant printable content cannot be processed safely, Rastrio reports the problem rather than silently discarding that content.**

The provisional numeric baselines in this document provide safe initial implementation targets. They are deliberately replaceable by measured values without changing Rastrio's document model, printer model, preview contract, or transport architecture.


## Phase 3A Operational Text/Layout Budgets

The initial `TextResourcePolicy` caps each ASCII measurement request at 64 Ki UTF-16 code units.
For its printable-ASCII repertoire this is also 64 KiB of UTF-8 text. `LayoutResourcePolicy` caps
aggregate plain-paragraph input at 8 Mi UTF-16 code units, with 100,000 blocks, 500,000 inline nodes,
1,000 mm resolved width and originally 1,000,000 generated items (including measured clusters).
Phase 3B tightens that item default and expands accounting as described below. Limits are
trusted runtime inputs; documents cannot raise them. The existing archive/JSON/string/source-byte
limits continue to apply at their own input boundaries.

These code-unit limits bound intermediate concatenation and measurement collections in the Phase
3A engine. They are provisional operational budgets, not a permanent Unicode segmentation policy.
A future backend must additionally bound its own shaping/glyph work and memory. Failure returns
`TXT120` / `LAY120` and no partial logical document. Coordinate arithmetic rejects non-finite values
and loss of forward progress (`LAY122`). Phase 3 logical arithmetic additionally uses the
`LogicalGeometry` 0.000001 mm grid with a 10^15-tick representability ceiling (10^9 mm), chosen
for exact checked sums and stable tick/mm round trips on all common targets. Raw count/width
resource limits are checked before quantization; rounding cannot admit an over-limit input.
A positive canvas width that quantizes to zero is invalid; zero-progress line heights/spacing
fail with `LAY122`. The ceiling bounds coordinate representation, not page-like receipt length.
ASCII advance range failures return `TXT120` before allocating measured clusters.


## Phase 3B Operational Flow Budgets

**Normative requirement:** generated/intermediate layout must be bounded and rejected with
`LAY120` before memory pressure is the effective control. `OutOfMemoryError` is not an
acceptable limit mechanism, and a finite but unaffordable count is not sufficient.

**Provisional default:** `LayoutResourcePolicy.maxItems = 100,000`, reduced from the
Phase 3A value of 1,000,000. This is trusted application tuning, not a `.td` field or a
portable-format ceiling. Callers may lower it for smaller Android/browser memory budgets;
raising it requires a corresponding measured memory envelope. Aggregate input, 64 Ki ASCII
request, block, inline-node, depth and width controls remain independently applicable.

Cost items are reserved cumulatively for each:

- measured span, before making its immutable text/request/measurement;
- measured source cluster and its associated `FlowAtom`, before retaining cluster atoms;
- explicit-break marker, before constructing/appending its `FlowAtom`;
- generated block and line, before retained geometry/per-line collections;
- generated run, before slice lists, sliced measurements, bounds and substrings;
- retained diagnostic, before construction/appending.

One cluster cost item covers its source cluster, associated atom and final slice cluster;
these bounded copies can coexist. The units are counts of cost families, not exact JVM or
browser heap bytes. The backend still bounds its own per-request allocations: layout checks
returned cluster counts before retaining their atoms, while the ASCII service limits each
request to 65,536 code units before allocating its measurement collections. The 8 Mi code-unit
aggregate bound continues to constrain span text/builders and the canonical input's text.
No document-wide flattened copy is built.

### Rationale and regression envelope

A one-cluster-per-line result retains much more than a text character: line/run/bounds
objects, list snapshots and backing arrays, sliced measurement/cluster/break collections,
substrings, plus the current block's source measurements and atoms. The old million-item
allowance admitted hundreds of thousands of these object families and exhausted a 128 MiB
JVM heap on supported maximum-size paragraphs. The tenfold reduction is deliberately
conservative for Android/browser-class runtimes rather than tuned to a large desktop heap.

Under the current accounting, a 65,536-cluster paragraph first reserves a block, a measured
span and its clusters. At one-tick width it has room for at most 17,230 line/run pairs and
one coalesced overflow diagnostic before `LAY120`; it cannot retain all 65,536 output lines
or proceed to the next paragraph's measurement. A maximum-size paragraph at 1,000 mm width
and a 1 mm cell still produces 66 lines within policy. Explicit-break and alternating-style
zero-advance cases consume the same shared budget, so a line-only cap cannot be bypassed.

The runnable `:core:layout:resourceHeapProbe` uses a dedicated `-Xmx128m` JVM for eight
65,536-character paragraphs at a one-tick canvas width, 300,000 semantic breaks, and
100,000 alternating zero-advance styled leaves. All three must return `LAY120`; a normal
maximum-size paragraph must succeed. This is supporting empirical evidence for the current
ceiling, not a guarantee for arbitrary heap occupancy, platform object sizes or future
measurement backends. Common exact-limit/one-over tests are the primary deterministic gate;
ordinary unit tests do not intentionally provoke an OOM. Reprofile this tunable default when
representation costs/backends change, retaining adequate application/runtime headroom.

### Diagnostic growth and atomicity

Successful output carries at most one content-free `LAY101` overflow diagnostic per source
block, charged against the shared item budget. It means one or more intact clusters exceed
available width; line/run geometry retains each actual overflow. Repeated identical conditions
are coalesced in source-block order without hiding that overflow occurred. Fatal resource
failure returns only its structured diagnostic and no partially generated document.

Inline traversal remains iterative, with maximum depth 64 (root inline nodes at depth one).
Entire-tree preflight rejects depth/node/text violations before measurement. Coordinate
range and quantized forward-progress failures remain `LAY122`. No persistent-format,
platform or printer contract changes accompany this operational resource tightening.


## Phase 3C1 Operational Structured-Flow Budgets

Phase 3C1 retains the Phase 3B 100,000 cumulative cost-item ceiling and all
aggregate text, block, inline, inline-depth, width and backend bounds.
`LayoutResourcePolicy.maxBlockDepth` has default/maximum 64. Top-level blocks
have depth one; contained blocks add one. List-item wrappers count as resource
work but do not add block depth. Preflight independently counts nested blocks,
all items including checklist items, inline nodes and code/text code
units before any service measurement. Empty ordinary/checklist item or quote block arrays fail
with LAY105; numbered marker overflow is checked before allocating strings.

Traversal retains bounded iterator frames rather than recursive calls or
sibling-wide flattened work queues. No document-wide flattened copy exists.
Code line boundaries are scanned incrementally without `split` collections or
intermediate line substrings. Existing paragraph/heading cost boundaries do
not change when no new structured content or tabs are present.

Additional cumulative costs are reserved for:

- two items per list/quote container: finalized container and iterator frame;
- three items per list item: finalized item, marker, and its child iterator;
- three additional point items for a checked checklist stroke;
- each measured ordered-marker slot, before marker string/service allocation;
- each ordered marker/SPACE measurement span and returned cluster, using the
  existing span/cluster cost families before retaining the returned result;
- each tab span and cluster/atom before construction, plus any measured SPACE
  metric span/clusters; selected line slices remain covered by cluster/run costs;
- each code explicit boundary atom before retention, followed by ordinary
  line/run costs; each separator before construction.

Marker slots cover their final run/bounds/string/measurement copies. Each
container/item cost covers its bounded child builder/snapshot backing copies.
Tab-column caches are scoped to a text block, with entries covered by their
reserved tab/metric families. Returned backend measurements are still subject
to the backend's independent allocation bounds. Large maximum marker columns
are measured under budget rather than inferred from numeral length or endpoint
width; a proportional backend cannot assume all same-length numbers have equal
width. Empty containers create no marker column or child frame amplification.

LAY101 is coalesced per actual affected text block, including nested children;
multiple affected children of one top-level semantic block may therefore share
its sourceBlockIndex. Every retained diagnostic is charged, including bounded copies of backend
failure diagnostics. All backend diagnostics are preserved when they fit;
otherwise the conversion fails through LAY120 before allocating its copy.
LAY120 aborts the
whole operation before further amplification and returns no partial document.

The existing dedicated 128 MiB `:core:layout:resourceHeapProbe` additionally
checks 40,000 checklist items containing valid empty paragraphs, 65,536 tabs in code, 65,536 explicit code
boundaries, one-cluster-per-line code, and 30,000 measured ordered markers.
Each must fail through LAY120 rather than heap or stack exhaustion. Common
exact/one-over tests independently pin depth, checklist empty-paragraph flow, checked marker,
code-boundary and narrow-list diagnostic budgets. These remain conservative
operational controls, not a universal heap-occupancy guarantee.


## Phase 3C2 Operational Table and Placeholder Budgets

The Phase 3C1 shared cumulative maxItems ceiling and text/inline/depth/coordinate
controls remain applicable. LayoutResourcePolicy adds trusted table ceilings: 20,000
body rows, 256 columns and 250,000 total cells including header. Callers may lower
these limits, never raise them beyond these structural ceilings. Header-only tables
are valid. Zero columns or mismatched header/body row widths fail atomically with
LAY105. Counts above structural/runtime policies fail with LAY120.

Preflight checks dimensions and checked total-row/cell arithmetic before visiting
cell content. It bounds rows * columns using division before multiplication, then
walks cells without flattening or allocating a rectangular matrix. Aggregate input
inline-node/text/depth limits apply to all cells. Image asset identities and alt text,
and opaque QR payloads, count against the same aggregate UTF-16 text-code-unit budget.
The absence of decode/encode work does not permit unbounded semantic strings.

Each table reserves cumulative structural costs before retaining column scratch slots
or output: one table, two items per column (sizing slot and final column/bounds), two
per row (builder and final row/bounds), and two per cell (cell/content bounds and
row-local finalized-text slot). Empty cells use the existing canonical body-line
representation, without a table-only synthetic run. Actual retained objects and
explicit structural/preflight reservations are accounted; no empty cell is free.
Cells additionally consume all ordinary TextBlockFlow
costs: text block, measured spans/clusters, breaks/tabs, lines/runs and diagnostics.
Preflight includes the three-item minimum text block/line/run cost for every cell,
along with structural table costs and placeholder/list-item lower bounds, to reject
unaffordable declared geometry before any document measurement. Preflight validates
a lower bound; actual charges still share one cumulative output/work budget.
Each image or QR placeholder costs one item covering its immutable model/bounds;
semantic strings/intent are retained by immutable references, not copied or decoded.
Builders and snapshot copies are bounded by these reserved families.

A one-column header-only empty table costs 10 items; adding one ASCII character costs
12. Two empty one-column rows cost 17, as do two empty cells in a header-only table.
One placeholder costs one item, independent of payload interpretation. Exact/one-over
common tests pin these boundaries and shared table/placeholder accumulation. These
are cost families, not byte guarantees. Backend allocation bounds remain independent.

The existing dedicated 128 MiB resourceHeapProbe adds many empty table cells, narrow
cell wrapping, alternating styled cell text, wide-table geometry amplification, and
100,000 image/QR placeholders at the exact shared ceiling plus one-over rejection.
Representative amplification must fail through LAY120 rather than OOM; normal bounded
placeholders succeed. Coordinate/progress failures remain LAY122; padding exhaustion
or requests above available content width use LAY107, deliberately generalized to
insufficient available logical width for required geometry. Requests are rejected
atomically after normalization, without clamping, scaling or overflow output. Finalized
square image boxes stay fixed after later decoding, so intrinsic dimensions do not
amplify or change document flow. Diagnostics remain content-private.
