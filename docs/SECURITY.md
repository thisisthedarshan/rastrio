# RastrIO Security Specification and Threat Model

**Status:** Normative engineering specification  
**Specification revision:** 1.1  
**Applies to:** RastrIO repository
**Primary target:** Android  
**Secondary targets:** Desktop/JVM and Web/Wasm where applicable  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related specifications:** `docs/TD_SPEC.md`, `docs/TCFG_SPEC.md`, `docs/TEXT_RENDERING_SPEC.md`, `docs/PREVIEW_SPEC.md`, `docs/TESTING.md`, `docs/RESOURCE_LIMITS.md`, `docs/HARDWARE_TESTS.md`

---

## 1. Purpose

This document defines RastrIO's application-security architecture, trust model, threat model, mandatory controls, privacy rules, network policy, platform security boundaries, supply-chain policy, and security-testing requirements.

It is intentionally specific to RastrIO's architecture and data flows. It is not a generic secure-coding checklist.

The guiding rules are:

1. All imported or externally supplied data is untrusted until validated.
2. Core parsing and document processing MUST be deterministic, bounded, and free of hidden network I/O.
3. Portable formats MUST describe data and validated capabilities, not executable behavior.
4. Physical printer output MUST be derived from validated `PreparedPrint` operations; imported content MUST NOT be able to inject arbitrary protocol programs. For v1, the active protocol family is ESC/POS.
5. Resource limits are part of the security boundary.
6. Printable user content is private and MUST NOT be logged by default.
7. A transport failure after bytes may have reached a printer is an ambiguous physical side effect and MUST NOT trigger automatic retry.
8. Security controls MUST be implemented with the feature they protect, not deferred to release cleanup.

Concrete numeric thresholds are defined in `docs/RESOURCE_LIMITS.md`. This document defines **what must be bounded and how violations are handled**, but MUST NOT duplicate conflicting numeric values.

---

## 2. Normative Language

The terms **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

A security-sensitive feature is not complete until its relevant validation, failure behavior, and regression tests exist.

---

## 3. Security Goals

RastrIO MUST protect the following security properties.

### 3.1 Local integrity

Untrusted files, Markdown, assets, profiles, device metadata, or printer responses MUST NOT:

- write outside an intended storage location;
- execute application code;
- cause arbitrary command execution by a printer;
- modify application configuration outside the operation explicitly requested by the user;
- bypass format validation or printer-profile safety rules.

### 3.2 Availability

A malformed or adversarial input MUST NOT be allowed to consume unbounded:

- memory;
- CPU time;
- disk space;
- archive entries;
- decoded pixels;
- raster buffers;
- font parser/shaping work and caches;
- prepared-artifact/spool storage;
- preview objects;
- print-job bytes;
- transport queues.

Inputs exceeding policy MUST fail with a structured diagnostic rather than relying on eventual out-of-memory, stack-overflow, disk-full, or watchdog failure.

### 3.3 Privacy

RastrIO is offline-first. Printable content MUST remain local unless the user explicitly invokes a feature that requires external access or sharing.

Normal logging MUST NOT contain printable content, credentials, authentication tokens, private asset bodies, or unnecessary physical-device identifiers.

### 3.4 Predictable physical side effects

Printing is a physical side effect. RastrIO MUST avoid silently causing duplicate output, unintended cutter operations, or profile-controlled arbitrary printer commands.

The application MUST distinguish:

- failure before any output could have occurred; and
- failure after output may have partially occurred.

### 3.5 Portable-format safety

`.td` and `.tcfg` inputs MUST have explicit schemas, explicit version handling, strict validation, and bounded parsing.

Unknown future versions MUST NOT be silently interpreted as current versions.

---

## 4. Security Non-Goals and Assumptions

RastrIO cannot provide guarantees outside the trust boundaries it controls.

The following are not assumed to be trustworthy:

- thermal-printer firmware;
- Bluetooth device names;
- USB descriptors;
- externally supplied printer-profile metadata;
- remote servers referenced by Markdown;
- filenames;
- archive metadata;
- image metadata;
- printer status responses.

RastrIO does not claim to provide cryptographic attestation of inexpensive ESC/POS printers. A paired or selected device may still be mislabeled, cloned, replaced, or running malicious or defective firmware.

RastrIO also cannot guarantee secure deletion from flash storage or operating-system caches after content has been decoded or printed. The application SHOULD minimize sensitive temporary data and clean it up promptly, but MUST NOT claim forensic erasure.

Compromise of the host operating system, a rooted device with hostile privileged software, a malicious JVM/runtime, or a malicious build toolchain is outside this application's primary threat model. Supply-chain controls in this document reduce but do not eliminate those risks.

---

## 5. Trust Model

The following inputs MUST be treated as untrusted regardless of where they originated:

- `.td` files;
- `.tcfg` files;
- Markdown;
- image files;
- explicitly imported/user-selected font files;
- QR payloads;
- external asset references;
- imported printer profiles;
- profile overrides imported from external sources;
- Bluetooth device metadata;
- USB device metadata;
- browser hardware metadata;
- filenames;
- archive paths;
- serialized JSON fields;
- source files embedded in `.td`;
- prepared-artifact/spool data when read back from storage across a process/runtime boundary or after persistence;
- printer status or response bytes where status-query features exist;
- persisted data created by older application versions until migration and validation complete.

"Created by RastrIO" is not sufficient reason to skip validation when data crosses a persistent or import boundary. Files may have been modified after creation, produced by an older buggy version, or copied from another device.

---

## 6. Assets to Protect

RastrIO's security design protects:

- printable document content;
- Markdown source;
- notes and template state;
- QR payloads;
- images and embedded assets;
- imported/user-selected font contents;
- finalized prepared raster/graphic artifacts and spool files;
- authentication tokens used by any explicit external-asset resolver;
- local file contents;
- printer identities associated with a user's `PrinterInstance`;
- application availability;
- persistent profile/configuration integrity;
- physical printer behavior;
- paper and consumables;
- predictable cutter behavior;
- user trust in preview-to-print consistency.

---

## 7. Major Trust Boundaries

RastrIO has the following security-relevant boundaries.

### 7.1 File/import boundary

```text
External file / share / picker / archive
        ↓
bounded byte acquisition
        ↓
format-specific parser
        ↓
schema + semantic validation
        ↓
trusted Core model
```

No Core model produced from an imported file is trusted until both syntactic parsing and semantic validation succeed.

### 7.2 External-asset boundary

```text
Markdown / ThermalDocument
        ↓
external AssetReference
        ↓
explicit higher-level policy decision
        ↓
ExternalAssetResolver
        ↓
bounded response acquisition
        ↓
media validation + decode limits
        ↓
embedded/local asset
```

The first two stages MUST NOT perform network I/O.

### 7.3 Printer-profile boundary

```text
.tcfg
  ↓
bounded strict-standard-JSON validation/tokenization
  ↓
duplicate-key / unknown-property / lenient-syntax rejection
  ↓
schema validation
  ↓
validated protocol-family + strategy registry checks
  ↓
PrinterProfile
```

A valid `PrinterProfile` describes capabilities and selects known strategies. It is not an executable printer program.

### 7.4 Physical-output boundary

```text
ThermalDocument + validated PrinterProfile + PrintOptions
        ↓
PreparationEngine
        ↓
PreparedPrint
   + immutable prepared artifacts
        ↓
trusted protocol encoder
        ↓
encoded byte chunks
        ↓
platform PrinterTransport
        ↓
printer

v1 trusted protocol encoder = `core-escpos`
```

The encoder serializes validated prepared operations. It MUST NOT execute arbitrary profile templates, dynamically loaded protocol code, or reinterpret user data as raw commands.

A future protocol family MUST introduce a trusted family-specific validator/encoder boundary. It MUST NOT be represented by importing arbitrary byte-generating logic.

### 7.5 Font boundary

The default bundled font set is a reviewed build input.

Explicitly imported/user-selected fonts are untrusted binary files:

```text
font bytes / platform font selection
        ↓
bounded font acquisition
        ↓
trusted font adapter / parser backend
        ↓
bounded shaping/raster contracts
        ↓
portable Core text result / finalized raster
```

Font files MUST NOT gain code-execution, filesystem, or network authority merely because a native text backend parses them.

### 7.6 Prepared-artifact boundary

Large finalized raster/graphic output may be backed by application-controlled spool storage:

```text
PreparationEngine
        ↓
finalized immutable prepared artifact
        ↓
portable read-only artifact contract
       /       ↓   ↓
 preview  protocol encoder
```

The backing-store path/handle is platform-owned implementation detail.

Portable Core consumers MUST NOT accept attacker-controlled filesystem paths as prepared-artifact identity.

If finalized artifact content is missing, corrupted, or unreadable, consumers MUST fail rather than silently regenerate output-affecting content.

### 7.7 Platform boundary

Bluetooth, USB, file pickers, image decoders, font/file adapters, prepared-artifact backing stores, browser APIs, permissions, and network access are platform concerns. Portable Core APIs MUST receive platform-neutral validated data and MUST NOT import platform object types.

---

## 8. Security Invariants

The following invariants are mandatory.

### SEC-INV-01 — No hidden network I/O

Core document parsing, Markdown parsing, layout, text processing, font fallback, raster processing, printer preparation, preview generation, and protocol encoding MUST NOT perform network requests.

### SEC-INV-02 — Imported profiles are data, not code

`.tcfg` MUST NOT contain executable scripts, expressions, unrestricted byte sequences, raw command programs, or templates that are emitted directly to a printer.

### SEC-INV-03 — Imported `.td` content cannot escape its container

Archive entries MUST NOT resolve outside the logical `.td` container.

### SEC-INV-04 — Resource consumption is bounded

All externally controlled sizes and counts MUST be subject to the applicable limits in `docs/RESOURCE_LIMITS.md`.

### SEC-INV-05 — No print-job-wide giant allocation

Large raster output and encoded printer output MUST support bounded prepared artifacts and streaming/chunked processing. The system MUST NOT require one giant receipt bitmap or one giant print-job `ByteArray`.

### SEC-INV-06 — Printable content is not routine log data

Printable content MUST NOT be logged by default.

### SEC-INV-07 — Ambiguous physical output is not idempotent

If bytes may have reached the printer, RastrIO MUST NOT automatically retry the job.

### SEC-INV-08 — Preview does not create a second execution path

Physical preview consumes the same immutable `PreparedPrint` and finalized prepared-artifact content used by the active protocol encoder. Security-sensitive output operations such as cut boundaries, checklist graphics, QR/image rasters, and raster text MUST NOT be independently reconstructed by UI code.

### SEC-INV-09 — Validation precedes Core consumption

Imported profiles and persistent models MUST be fully validated before they are accepted as trusted inputs to printer preparation or other Core algorithms.

### SEC-INV-10 — Untrusted text is data

Markdown, document text, filenames, QR payloads, profile labels, device names, diagnostics, and asset metadata MUST NOT be evaluated as code, templates, shell commands, format strings, HTML scripts, or protocol programs.

### SEC-INV-11 — Parser libraries do not define portable-format validity

`.td` and `.tcfg` validation MUST enforce RastrIO's strict JSON rules even when the selected parser is more permissive.

Duplicate keys, unknown v1 properties, prohibited lenient syntax, invalid numerics, and unsupported discriminators/identifiers MUST be detected before parser behavior can erase or normalize the evidence.

### SEC-INV-12 — Imported fonts are untrusted binary input

User-selected/imported font files MUST be bounded and parsed through reviewed font-backend boundaries.

They MUST NOT enter the default fallback chain implicitly, trigger network retrieval, or cause unbounded cache/parser work.

### SEC-INV-13 — Finalized prepared artifacts are immutable sensitive output

Prepared-artifact backing storage MUST be private, bounded, lifecycle-managed, and stable for the valid `PreparedPrint` lifetime.

Preview and encoding MUST read the same finalized content.

Missing/corrupt prepared artifact content MUST NOT trigger silent regeneration.

### SEC-INV-14 — Protocol-family extensibility is trusted code, not imported code

Schema v1 supports the trusted ESC/POS protocol family.

A future protocol family requires an explicit schema/specification extension and trusted encoder implementation. Imported profiles may select only registered family/strategy identifiers and validated parameters.

---

## 9. Threat Model Summary

| Threat | Example attack | Primary impact | Required control |
|---|---|---|---|
| Path traversal | `../../outside/file` in `.td` | overwrite/read unintended files | normalized-path validation; reject absolute or escaping paths; avoid extraction where possible |
| Archive path ambiguity | alternate separators or duplicate normalized names | parser confusion, overwrite | canonical archive-path normalization and duplicate rejection |
| ZIP bomb | tiny compressed archive expands massively | memory/disk/CPU exhaustion | bounded compressed and expanded sizes; streaming limits; stop on violation |
| Excessive archive entries | huge number of tiny ZIP entries | CPU/memory exhaustion | bounded entry count |
| Oversized JSON | giant `document.json` or `.tcfg` | memory/CPU exhaustion | byte limit before parse; bounded depth/structure; fail closed |
| Lenient JSON ambiguity | duplicate keys, comments, trailing commas, ignored unknown fields | validation bypass / parser disagreement | RastrIO-defined strict prevalidation/token inspection before lossy decode |
| Malformed serialization | invalid discriminators, types, references | crashes or inconsistent models | strict decoding plus semantic validation |
| Image decompression bomb | small image declares enormous dimensions | OOM/CPU exhaustion | pixel and decode-memory limits; checked arithmetic; bounded decode |
| Malicious font | crafted font causes parser recursion/allocation/native crash | availability / possible native-memory risk | bounded input/work; reviewed HarfBuzz/FreeType boundary; fuzz/regression testing; explicit import only |
| Hostile Markdown | deep nesting, giant tokens, crafted HTML | CPU exhaustion or injection | input limits; parser robustness; no HTML execution |
| Unsafe raw HTML | `<script>` or active elements | script/code execution | never execute; literalize or diagnose according to Markdown spec |
| Malicious URL | tracking URL, local-network target, huge response | privacy leak, network abuse, exhaustion | explicit resolver; no automatic fetch; scheme/policy validation; bounded response |
| Unexpected network request | opening/printing implicitly fetches assets | privacy and availability | offline-by-default; explicit resolution state |
| `.tcfg` command injection | profile embeds raw ESC/POS bytes | arbitrary printer behavior | allowlisted strategy IDs and validated parameters only |
| User text control-byte injection | text includes ESC/GS control characters | command injection through native text | reject/rasterize/represent semantically; never concatenate uncontrolled control bytes |
| Arbitrary byte-program injection | custom template emits protocol stream | cutter/feed/device abuse | no raw byte templates in normal profile format |
| Untrusted protocol-family injection | profile names unknown family/module to load | code execution or arbitrary printer behavior | registered family identifiers; no dynamic modules; trusted encoder implementations only |
| Sensitive logging | Markdown/QR/image/token appears in logs | privacy breach | content-redacted logging policy |
| Unsafe temporary files | predictable shared temp path | disclosure/tampering | private temp area, safe creation, cleanup, no path trust |
| Prepared-artifact tampering/loss | spool file changed, confused, deleted early | preview/print mismatch or silent output change | private bounded store; stable artifact identity/integrity; lifetime management; fail instead of regenerate |
| Device spoofing | malicious printer copies device name | wrong-device printing/data disclosure | explicit user selection; treat metadata as hints, not proof |
| Enormous print job | extremely long content | memory/CPU/paper denial of service | preparation limits, streaming, warnings/rejection, cancellation |
| Duplicate printing | reconnect/retry after partial write | duplicate/partial physical output | partial-transmission state; no automatic retry |
| Malicious printer response | oversized or malformed status bytes | parser/resource abuse | bounded reads, strict status parsing, timeouts |
| Supply-chain compromise | malicious/transitively vulnerable dependency | arbitrary app/build compromise | dependency review, pinning, verification, minimal dependency surface |

---

## 10. Common Untrusted-Input Processing Rules

All untrusted-input pipelines MUST follow these principles.

### 10.1 Bound before expensive work

Where practical, RastrIO MUST check inexpensive metadata and byte-count limits before:

- allocating large buffers;
- decompressing;
- decoding images;
- parsing fonts;
- parsing large JSON structures;
- creating prepared-artifact/spool storage;
- rendering;
- building preview scenes;
- creating raster bands;
- preparing an enormous print job.

Metadata alone is not trusted. Runtime counters MUST enforce limits even when declared sizes are missing or false.

### 10.2 Checked arithmetic

Size calculations derived from untrusted dimensions or counts MUST use checked arithmetic.

Code MUST NOT allow integer overflow in calculations such as:

```text
width × height
rows × bytesPerRow
entryCount × metadataSize
segmentCount × overlap
decodedPixels × bytesPerPixel
```

An overflow or impossible dimension MUST produce a structured failure.

### 10.3 Fail closed

When security validation is ambiguous, RastrIO MUST reject the affected import or operation rather than guessing a permissive interpretation.

Security failures MUST NOT silently downgrade to an unsafe code path.

### 10.4 Strict parsing precedes semantic validation

Successful JSON/ZIP/Markdown/font parsing does not imply safe domain data.

For `.td` and `.tcfg`, strict JSON conformance MUST be established before a lossy object representation can discard information required for validation.

A parser configuration that ignores unknown fields or collapses duplicate keys is not sufficient by itself.

Where necessary, RastrIO MUST use bounded lexical pre-validation, token inspection, parser wrapping, or equivalent checks before schema/model decode.

After syntactic conformance, the result MUST still pass format-specific semantic validation before it becomes a trusted Core model.

### 10.5 Diagnostics are data-minimized

A security diagnostic SHOULD identify:

- diagnostic code;
- format/stage;
- violated rule;
- bounded operational metadata needed to understand the problem.

It SHOULD NOT echo full untrusted input, document text, payloads, URLs with credentials, or raw binary data.


### 10.6 Safe failure behavior

Security-sensitive failures MUST leave the system in a conservative, internally consistent state.

In particular:

- a failed import MUST NOT activate a partially validated document or profile;
- a resource-exhaustion limit violation MUST abort the expensive operation promptly;
- a failed external-asset fetch MUST NOT silently fall back to an unbounded or different network path;
- an encoder safety failure MUST NOT emit guessed or partially substituted raw commands;
- a transport failure MUST preserve whether physical output may already have occurred;
- cleanup failure MUST be reported where it can leave meaningful persistent data or locked hardware state.

A safe failure is not necessarily silent. The application SHOULD surface an actionable structured diagnostic while still refusing the unsafe operation.


---

## 11. `.td` Archive Security

`.td` v1 is ZIP-compatible and therefore has an archive attack surface.

The `.td` reader MUST implement archive safety before ordinary document decoding.

### 11.1 Validate before extraction

The preferred implementation SHOULD read the archive as a logical container and avoid filesystem extraction where practical.

If extraction is required by a platform adapter, validation MUST occur before writing an entry to disk.

At minimum, the reader MUST validate:

- archive structure;
- supported container version;
- total entry count;
- each entry path;
- duplicate normalized paths;
- declared compressed size where available;
- actual compressed bytes consumed where measurable;
- declared uncompressed size where available;
- actual uncompressed bytes produced;
- total expanded bytes;
- individual-entry size;
- required files;
- unsupported special entry types where detectable.

All resource thresholds come from `docs/RESOURCE_LIMITS.md`.

### 11.2 Archive path normalization

Archive entry names MUST be treated as logical archive paths, not trusted host filesystem paths.

Normalization MUST reject:

- absolute paths;
- root-prefixed paths;
- parent traversal components such as `..`;
- paths that normalize outside the container;
- empty or invalid path components where prohibited by `docs/TD_SPEC.md`;
- NUL-containing names;
- platform drive-qualified paths;
- UNC/network paths;
- alternate-separator forms that would become traversal on a supported platform.

Backslash and slash ambiguity MUST be handled explicitly so that an entry cannot be safe on one platform and escaping on another.

After normalization, duplicate logical paths MUST be rejected. The reader MUST NOT use "last entry wins" or "first entry wins" semantics for duplicates.

If data is ever materialized onto a case-insensitive filesystem, the extraction layer MUST additionally prevent case-collision overwrites.

### 11.3 Symlinks and special files

`.td` does not require symlinks, hard links, device nodes, FIFOs, or executable entries.

Such archive entry types SHOULD be rejected where they can be identified. An implementation MUST NOT intentionally create links or special files from imported `.td` content.

### 11.4 ZIP-bomb protection

Protection MUST use runtime expansion accounting, not only ZIP metadata.

The reader MUST stop processing once any applicable compressed, uncompressed, per-entry, total-expansion, or entry-count limit is exceeded.

A limit violation MUST:

- abort loading;
- release archive streams/resources;
- avoid retaining partially trusted models;
- avoid persisting partially extracted data as a valid document;
- return a structured diagnostic.

### 11.5 Required entries

The reader MUST require the v1 files defined by `docs/TD_SPEC.md`, including a valid manifest and authoritative document payload.

Missing, duplicated, malformed, or unsupported-version required entries MUST fail safely.

### 11.6 Source and assets

Optional `source/` content and `assets/` entries are as untrusted as `document.json`.

Source retention does not grant source content permission to execute, resolve files, or access the network.

Asset metadata MUST be validated against the actual archive contents. A declared byte size or hash MAY be used for integrity checks but MUST NOT replace runtime size enforcement.

---

## 12. `.td` JSON and Document-Model Security

### 12.1 Bounded, strict JSON

`manifest.json` and `document.json` MUST be size-bounded before or while parsing.

The reader MUST enforce the complete strict JSON contract from `docs/TD_SPEC.md`, including rejection of duplicate object keys, unknown v1 properties, comments, trailing commas, prohibited lenient tokens/forms, illegal nulls, unsupported discriminators, and resource-limit violations.

Duplicate-key and unknown-property detection MUST occur before a parser representation can silently erase that information.

The decoder MUST also enforce applicable structure/depth/count limits from `docs/RESOURCE_LIMITS.md`.

### 12.2 Stable discriminators only

Polymorphic document nodes MUST use stable, explicitly supported discriminators.

Untrusted serialized input MUST NOT select arbitrary Kotlin/JVM classes, reflection targets, serializers, class names, or dynamically loaded implementations.

### 12.3 Unknown versions and nodes

Unknown future container versions or document schema versions MUST fail according to the compatibility policy in `docs/TD_SPEC.md`.

Unknown node types MUST NOT be silently mapped to an existing type merely to continue loading.

### 12.4 Semantic validation

After decoding, the document validator MUST reject or diagnose invalid state including, where applicable:

- invalid dimensions;
- invalid enum/discriminator values;
- impossible layout values;
- invalid asset references;
- missing assets;
- duplicate IDs where uniqueness is required;
- reference cycles where the model requires an acyclic structure;
- excessive nesting;
- invalid text/control characters according to the document/text specification;
- counts exceeding resource policy.

A decoded object graph MUST NOT enter layout or printer preparation until validation succeeds.

---

## 13. `.tcfg` Security

`.tcfg` is a portable printer-capability description. It is not a scripting language and not a raw protocol container.

### 13.1 Bounded strict parsing

`.tcfg` input MUST be subject to:

- raw byte limits;
- strict standard-JSON syntax validation;
- duplicate-key rejection before collapse/overwrite;
- unknown v1 property rejection;
- JSON structure/depth limits;
- string/count limits;
- explicit schema-version checks;
- semantic validation.

Comments, trailing commas, single-quoted strings, unquoted keys, non-standard numeric tokens, and other prohibited lenient forms MUST NOT be accepted.

Thresholds are defined by `docs/RESOURCE_LIMITS.md`.

A permissive JSON-library setting such as ignoring unknown keys MUST NOT weaken the `.tcfg` contract.

### 13.2 Validation before use

An imported profile MUST be validated before it can be:

- saved as an active profile;
- assigned to a `PrinterInstance`;
- used to prepare a print;
- used by Printer Lab;
- used to select a protocol family/strategy.

Invalid profiles MUST NOT become partially active.

### 13.3 Protocol-strategy allowlist

Profiles MAY select only protocol-family and strategy identifiers implemented and recognized by RastrIO.

For schema v1, `protocol.family` MUST be `escpos`.

An unknown or future protocol family MUST fail validation. It MUST NOT fall back to ESC/POS or cause dynamic code/module loading.

Examples include validated identifiers equivalent to:

- known raster strategies;
- known QR strategies;
- known cutter strategies;
- known native-font/code-page capabilities;
- known quirk identifiers.

Strategy parameters MUST be range- and consistency-validated before use.

### 13.4 Prohibited executable/profile features

Normal `.tcfg` MUST NOT contain or enable:

- arbitrary raw byte arrays that are sent directly to a printer;
- arbitrary hexadecimal command strings;
- arbitrary escape-sequence templates;
- templated command programs;
- JavaScript or other scripts;
- expressions evaluated at runtime;
- shell commands;
- reflection/class names used to instantiate behavior;
- downloadable executable modules;
- URLs that are automatically fetched to obtain protocol code;
- unvalidated format strings that generate printer commands;
- class/module/library names used to dynamically load a protocol encoder;
- native library paths supplied by a profile.

A future raw-command escape hatch, if ever designed, MUST be outside the normal trusted `.tcfg` format, explicitly dangerous, isolated, and subject to a separate architecture/security review.

### 13.5 Capability consistency

Validation MUST reject capability combinations that are impossible, internally inconsistent, or unsafe according to `docs/TCFG_SPEC.md`.

User overrides MUST pass the same safety rules as base profile data. Overrides MUST NOT bypass protocol strategy validation.

---

## 14. Protocol Command-Injection Controls

RastrIO's printer output streams contain control bytes by design. Therefore the boundary between **document data** and **printer commands** must be explicit.

The concrete v1 encoder is ESC/POS, but the security rules in this section apply to any future trusted protocol encoder.

### 14.1 No raw protocol bytes from document content

`ThermalDocument`, Markdown, template state, QR payloads, filenames, labels, and imported profile strings MUST NOT be copied into a printer protocol stream as unvalidated raw command bytes.

### 14.2 Native text and control characters

Native printer text requires special care because ESC/POS and other printer protocols use control-byte values/framing that can conflict with arbitrary byte sequences.

Printer preparation and/or text encoding MUST ensure that user-controlled text cannot introduce protocol control sequences.

Control characters that would be interpreted as printer commands MUST be:

- represented through explicit semantic operations where appropriate; or
- rejected with a diagnostic; or
- rendered through a safe raster path when that preserves intended visible content.

The encoder MUST NOT concatenate arbitrary user-controlled byte strings into the command stream.

Line breaks, feeds, cuts, code-page changes, alignment, and style changes MUST be represented as explicit prepared operations, not smuggled through text.

### 14.3 QR and barcode payloads

QR and future barcode payloads are data.

For native printer features, the encoder MUST use the selected protocol's structured length/framing rules and MUST NOT interpret payload substrings as command templates.

Payload size and encoding constraints MUST be validated before `PreparedPrint` is finalized.

### 14.4 Encoder responsibility

The active trusted protocol encoder MUST serialize already validated `PreparedPrint` operations.

For v1, this encoder is `core-escpos`.

If a prepared operation contains a value that cannot be serialized safely, the encoder MUST fail with a structured diagnostic. It MUST NOT substitute a raw escape hatch, reinterpret source document content, or regenerate missing prepared raster data.

---

## 15. Markdown Security

Markdown is untrusted text input.

### 15.1 No execution

The Markdown compiler MUST NOT:

- execute code blocks;
- execute inline code;
- interpret text as shell commands;
- execute raw HTML;
- execute JavaScript;
- invoke browser DOM APIs;
- load files referenced by Markdown;
- fetch network resources.

### 15.2 Raw HTML

Raw HTML MUST never execute.

Unsupported raw HTML follows the v1 Markdown/`.td` policy:

- significant raw/unsupported HTML SHOULD be preserved as ordinary literal document text where the parser exposes it safely;
- compilation MUST emit a structured warning such as `MD101 Unsupported raw HTML`;
- if significant source cannot be preserved safely, compilation MUST return a structured diagnostic rather than silently dropping it.

Raw HTML tags, attributes, scripts, styles, event handlers, and embedded markup MUST NOT be interpreted as active HTML semantics.

Raw HTML MUST NOT be rendered through an execution-capable browser/WebView path merely to obtain appearance.

### 15.3 Parser resource limits

Markdown length, structural depth, token counts, or other parser-sensitive quantities MUST be bounded according to `docs/RESOURCE_LIMITS.md`.

Hostile Markdown MUST not be able to force unbounded recursion or catastrophic resource use.

Long unbroken tokens, deeply nested lists/quotes, giant tables, and pathological emphasis/link constructs MUST be included in robustness tests.

### 15.4 Links

Links remain semantic document content.

Displaying a link MUST NOT cause navigation or network I/O during compilation, layout, preview generation, or printing.

Any UI action that opens a link is a separate explicit user action and is outside Core parsing.

---

## 16. External Asset and Network Policy

### 16.1 Representation

An external Markdown asset such as:

```markdown
![logo](https://example.com/logo.png)
```

MUST compile to an external `AssetReference` or a diagnostic state. The Markdown compiler MUST NOT retrieve it.

### 16.2 Explicit resolver

Network retrieval, where supported, MUST occur only through a higher-level `ExternalAssetResolver` or equivalent platform service.

The resolver MUST be invoked by an explicit application policy such as:

- a direct user action; or
- a clearly documented user setting that authorizes such resolution.

Opening, previewing, preparing, or printing a document MUST NOT silently create a new network dependency.

### 16.3 Resolver controls

An external resolver MUST apply, as relevant:

- allowed-scheme validation;
- URL parsing before request;
- redirect validation on every hop;
- request timeout;
- response byte limits;
- media-type validation;
- image/resource limits before or during decode;
- cancellation;
- cleanup of partial downloads;
- safe handling of authentication data;
- explicit handling of local/private-network targets.

Credentials, cookies, authentication headers, or application tokens MUST NOT be attached to arbitrary external asset URLs by default.

A redirect MUST NOT bypass the resolver's scheme or destination policy.

### 16.4 Offline-by-default behavior

Without explicit resolution, external assets remain unresolved and MUST be represented as such in diagnostics/preview.

A print operation MUST NOT unexpectedly fetch an unresolved asset.

Where a user chooses to save a self-contained `.td`, the application MAY embed a successfully resolved asset according to the save policy and applicable resource limits.

---

## 17. Malicious URL Considerations

External URLs can be harmful even when no code execution occurs.

Potential risks include:

- user tracking;
- disclosure of IP/network metadata;
- access to local services;
- requests to unexpectedly large resources;
- redirect loops;
- credential leakage through URLs;
- misleading file extensions or media types.

Therefore:

1. Core MUST treat URLs as inert data.
2. Higher-level resolvers MUST default to explicit, policy-controlled access.
3. URL strings SHOULD be redacted in routine logs.
4. Embedded credentials in URLs MUST NOT be logged.
5. Content type and actual decoded format MUST be validated rather than trusting a filename suffix.
6. A resolver SHOULD treat access to loopback, link-local, or private-network destinations as security-sensitive and require an explicit policy rather than accidentally inheriting ordinary public-HTTP behavior.

---

## 18. Image Security

Images are untrusted compressed inputs and can be decompression bombs.

### 18.1 Decode boundary

Image decoding belongs behind `ImageDecodeService` or another platform adapter.

Before or during decode, the implementation MUST enforce:

- source byte limits;
- supported-format policy;
- maximum decoded dimensions;
- maximum total pixel count;
- decode-memory budget;
- metadata limits where relevant.

Exact values belong in `docs/RESOURCE_LIMITS.md`.

### 18.2 Do not trust image headers

Declared width, height, frame count, orientation, or compressed size may be malformed or deceptive.

All allocation calculations MUST use checked arithmetic and be validated against actual decoder behavior.

### 18.3 Multi-frame or animated images

If an image format can contain multiple frames, the application MUST have an explicit policy for which frame(s) are supported.

It MUST NOT decode an unbounded frame sequence merely because the container permits it.

### 18.4 Metadata

EXIF or equivalent metadata is untrusted.

Only metadata needed for supported behavior, such as orientation where applicable, SHOULD be consumed. Metadata MUST NOT be executed or used as a path without validation.

### 18.5 Raster processing

After decode, `core-raster` MUST preserve bounded-memory behavior.

Large images MUST be resized, rejected, tiled, or otherwise processed within resource policy. Raster processing MUST NOT require a job-wide bitmap or job-wide packed buffer.

---

## 19. QR Payload Security

QR payloads are arbitrary user data.

RastrIO MUST treat QR payloads as opaque content except for validations explicitly required by the QR implementation.

A QR payload MUST NOT:

- be interpreted as a URL merely because it looks like one;
- trigger a network request;
- be executed;
- become a template;
- be logged by default;
- be inserted into ESC/POS command streams without safe native-QR framing or rasterization.

Length and encoding limits MUST be enforced before preparation completes.

If a QR payload cannot be represented under the selected native QR strategy, preparation MUST choose a validated fallback or return a diagnostic according to the printer contract.

---

## 19A. Font Security

RastrIO's default bundled fonts are reviewed repository/build inputs.

Explicit user-selected/imported font files are untrusted binary input.

### 19A.1 Bounded acquisition and parsing

Before invoking FreeType/HarfBuzz or another approved font backend, RastrIO MUST enforce applicable input/resource limits from `docs/RESOURCE_LIMITS.md`.

Font parsing/shaping MUST remain bounded with respect to:

```text
file bytes
tables
glyph count
composite recursion
variation data
cmap/name records
shaping-table work
glyph/shaping caches
```

Malformed-font failures from native libraries MUST be converted into controlled application failures where the platform permits recovery.

### 19A.2 No implicit platform fallback

The default deterministic fallback chain MUST use project-controlled bundled font resources.

RastrIO MUST NOT scan or silently select arbitrary platform fonts merely because a glyph is missing.

Platform-installed fonts are allowed only through explicit product/user choice as defined by `TEXT_RENDERING_SPEC.md`.

### 19A.3 No network font resolution

Missing fonts MUST NOT trigger network downloads during normal preparation, preview, or printing.

A missing selected font is a local resolution failure.

### 19A.4 Font identity and integrity

Bundled font resources SHOULD be identified by pinned repository path/version/hash.

Imported font resources SHOULD receive a stable content fingerprint where practical.

If a selected font changes between measurement and rasterization in a way that can affect output, preparation MUST fail or restart deliberately; it MUST NOT mix inconsistent font bytes within one finalized physical plan.

---

## 20. Resource-Exhaustion and Denial-of-Service Controls

RastrIO accepts potentially very long documents. Long-document support does not mean unbounded-resource support.

### 20.1 Central limits

`docs/RESOURCE_LIMITS.md` MUST define concrete limits for at least:

- archive compressed size;
- archive expanded size;
- archive entry count;
- individual archive entry size;
- JSON size;
- JSON depth/structural limits;
- source/Markdown size;
- asset size;
- image dimensions/pixel count;
- imported font file/parser/cache work;
- prepared-artifact/spool bytes and metadata;
- preview tile or scene budgets;
- raster band size;
- large-document warning/rejection thresholds;
- any print-job planning limits required for safe operation.

### 20.2 Early estimation

Where practical, preparation SHOULD estimate expensive output before allocating it.

For example, the system may estimate:

- expected logical length;
- raster rows;
- segment count;
- approximate encoded bytes;
- preview tile count.

An estimate is advisory and MUST NOT replace runtime counters.

### 20.3 Bounded intermediate structures

Algorithms MUST avoid building unbounded collections directly from attacker-controlled counts.

Where streaming or incremental processing is possible, it SHOULD be preferred.

### 20.4 Long previews

Preview rendering MUST support virtualization, tiling, incremental composition, or another bounded-memory strategy.

A single enormous bitmap MUST NOT be required to preview a long receipt.

### 20.5 Long print jobs

Large jobs MUST be cancellable.

Cancellation MUST stop further preparation/encoding/transmission as soon as safely practical, release resources, and preserve the truth that already transmitted printer output cannot be undone.

---

## 21. Prepared-Artifact Streaming and Byte-Stream Safety

### 21.1 Raster bands and prepared artifacts

Prepared raster/graphic output MUST support bounded bands, tiles, or immutable prepared-artifact backing stores.

Printer-side raster banding is a physical preparation concern. Prepared-artifact storage is a `core-printer`/platform backing-store concern. Transport write chunking is a separate concern.

Preview and the active protocol encoder MUST read the same finalized artifact content.

### 21.2 No giant encoded buffer

The active protocol encoder (`core-escpos` for v1) MUST support encoded chunks or streaming output.

It MUST NOT require concatenating an entire long job into one `ByteArray`.

### 21.3 Prepared-artifact integrity and lifetime

Prepared-artifact storage MUST:

- be application-controlled/private where the platform supports it;
- be bounded by trusted resource policy;
- use application-generated identity rather than attacker-controlled paths;
- remain immutable/readable for the valid `PreparedPrint` lifetime;
- be cleaned after disposal/cancellation according to lifecycle policy.

Where corruption or cross-preparation confusion is plausible, stable identity/integrity checks SHOULD be used.

If finalized artifact data becomes unreadable or corrupt, preview/encoding MUST fail the preparation. They MUST NOT re-run shaping, image processing, dithering, QR generation, checklist-marker generation, or another output-affecting transformation to recreate it.

### 21.4 Backpressure

Transport/session orchestration SHOULD avoid accumulating an unbounded queue of encoded chunks when the physical connection is slow.

### 21.5 Partial failure

If encoding or transport fails partway through a job, buffers MUST be released and the job state MUST preserve whether output may already have occurred.

---

## 22. Print Lifecycle and Duplicate-Print Safety

Printing is not reliably idempotent.

### 22.1 Required state distinction

The job lifecycle MUST distinguish sufficient state to report:

- failure before transmission;
- failure during transmission;
- cancellation;
- successful transmission according to the transport contract.

Failure metadata SHOULD support the PRD concepts:

- `bytesAttempted`;
- `bytesTransmitted`;
- `outputMayHaveOccurred`;
- `failureStage`.

Exact field names may differ.

### 22.2 No unsafe automatic retry

If `outputMayHaveOccurred` is true or cannot be ruled out, RastrIO MUST NOT automatically retry the job.

The UI MAY offer a user-controlled retry only after communicating that duplicate or partial output is possible.

### 22.3 Reconnection is not proof of non-output

A dropped Bluetooth/USB connection followed by successful reconnection MUST NOT be treated as evidence that the previous bytes were not printed.

### 22.4 Completion semantics

`COMPLETED` means the intended bytes were transmitted according to the transport contract.

RastrIO MUST NOT claim physical paper output succeeded unless a supported printer protocol provides reliable confirmation.

---

## 23. Cutter and Other Physical-Operation Safety

Automatic cutter commands and other hardware-affecting operations MUST originate from validated `PreparedPrint` operations.

A profile may advertise cutter support and select a known cutter strategy, but it MUST NOT supply arbitrary cutter byte sequences.

The preview model MUST distinguish:

- printed manual cut guides; and
- non-printing annotations for automatic cut boundaries.

UI code MUST NOT convert an automatic-cut annotation into printable content or vice versa.

If cutter capability or strategy validation fails, the system MUST fail or select a documented safe fallback. It MUST NOT emit guessed command bytes.

---

## 24. Temporary, Spool, and Local Storage

### 24.1 Temporary/spool files

If temporary or prepared-artifact spool files are necessary, platform code MUST:

- create them in application-private storage where available;
- avoid predictable shared/public paths;
- use safe create semantics;
- avoid following untrusted symlinks;
- derive names/identities independently of untrusted filenames and archive paths;
- enforce byte, file-count, and metadata/index limits while writing;
- close handles on success, failure, cancellation, and disposal;
- remove abandoned/stale temporary data according to an application cleanup policy;
- tolerate abnormal-process cleanup where supported;
- avoid exposing printable content or imported font bytes to unrelated applications.

Untrusted archive paths, filenames, document fields, or profile fields MUST NOT be used directly as prepared-artifact host paths.

### 24.2 Live prepared-artifact lifetime

A cleanup mechanism MUST distinguish disposable scratch data from finalized prepared-artifact content still required by a valid `PreparedPrint`.

Live prepared artifacts MUST NOT be deleted merely to evict a preview cache or satisfy unrelated cache pressure.

If storage pressure makes the finalized artifact unavailable, the preparation MUST be invalidated/fail explicitly rather than silently regenerate different physical output.

### 24.3 Partial files and atomic persistence

A partially downloaded, decoded, extracted, font-imported, spooled, or generated file MUST NOT be mistaken for a complete valid document/profile/font/prepared artifact.

Where persistence is required, implementations SHOULD use atomic replacement patterns appropriate to the platform.

Finalized prepared artifacts MUST be published to consumers only after their expected content/metadata are complete.

### 24.4 Sensitive persistence

Printer identities and recent-document metadata SHOULD be stored only to the extent required by the feature.

Document bodies, QR payloads, resolved external assets, imported fonts, and prepared raster/graphic artifacts MUST NOT be copied into unrelated caches merely for diagnostics.

Persistent preview or spool caches, if ever introduced beyond the active preparation lifecycle, require explicit security/privacy review.

---

## 25. Privacy and Logging Policy

### 25.1 Content that MUST NOT be logged by default

Normal application logs, CI logs, crash logs, transport logs, and diagnostics exported for bug reports MUST NOT contain the following by default:

- Markdown bodies;
- Markdown source;
- notes;
- template text;
- QR payloads;
- images;
- imported font files;
- prepared raster/graphic artifact bodies;
- document bodies;
- embedded source files;
- authentication tokens;
- cookies;
- authorization headers;
- sensitive external-asset contents;
- arbitrary local file contents;
- raw printer-protocol streams if they may contain printable content;
- native text payload bytes;
- complete URLs containing credentials or sensitive query parameters;
- Bluetooth names or addresses when unnecessary;
- USB serial numbers when unnecessary.

### 25.2 Operational metadata that MAY be logged

Subject to ordinary data minimization, logs MAY include non-content operational metadata such as:

- diagnostic codes;
- format/schema versions;
- profile IDs that are not private device identities;
- operation types;
- resolved dimensions;
- image dimensions after validation;
- raster band dimensions;
- segment counts;
- byte counts;
- timing measurements;
- connection state;
- transport type;
- retry prohibition reason;
- resource-limit category exceeded;
- feature/capability identifiers;
- application/build version.

Even permitted metadata SHOULD be omitted when it is not useful for diagnosis.

### 25.3 Filenames and paths

Filenames and local paths can themselves be sensitive.

Routine logs SHOULD avoid full paths and SHOULD avoid filenames unless needed to diagnose a user-visible local error.

If a path must be shown to the user, it SHOULD appear in local UI diagnostics rather than automatically entering exported logs.

### 25.4 Bug-report export

If RastrIO provides a diagnostic export:

- the export MUST be explicit;
- content fields MUST remain redacted by default;
- included fields SHOULD be previewable before sharing;
- the user SHOULD be able to exclude device-identifying metadata;
- no report MUST be uploaded automatically merely because it was generated.

---

## 26. Diagnostics and Error Handling

Security errors SHOULD use structured diagnostics consistent with RastrIO's diagnostic model.

Examples of security-relevant diagnostic categories include:

```text
TDxxx   archive/document validation
MDxxx   Markdown/raw HTML/external asset
IMGxxx  image decode/resource validation
TXTxxx  font/text shaping/raster security/resource validation
PRFxxx  printer profile validation
PRNxxx  preparation/prepared-artifact safety
PRVxxx  preview-owned safety/resource failures
ESCxxx  v1 ESC/POS encoder safety/inconsistent operation
TRNxxx  transport/partial-output state
```

A fatal validation failure MUST prevent the affected operation.

Diagnostics MUST avoid echoing private printable content.

Parser exceptions, malformed input, unsupported versions, and resource-limit violations MUST be converted into controlled failures rather than process crashes where the platform permits recovery.

---

## 27. Cancellation and Resource Cleanup

Any operation that can be materially expensive SHOULD support cooperative cancellation where the surrounding architecture permits it.

Cancellation paths MUST be tested for:

- archive reads;
- external asset retrieval;
- image decode/processing where supported;
- font parsing/shaping/rasterization;
- raster generation;
- prepared-artifact/spool creation and reads;
- preview generation;
- protocol encoding/streaming;
- transport writes.

On cancellation, RastrIO MUST:

- stop scheduling further work;
- close streams and device handles;
- release temporary buffers;
- release disposable temporary/spool resources according to policy;
- preserve finalized prepared artifacts still required by any deliberately retained valid preparation, or explicitly invalidate that preparation;
- leave persistent state internally consistent;
- accurately preserve partial-transmission state.

Cancellation MUST NOT be reported as "nothing printed" if bytes may already have reached the printer.

---

## 28. Transport and Device Security

### 28.1 Device metadata is not authentication

Bluetooth names, advertised metadata, USB product strings, VID/PID values, and similar descriptors can be incorrect or spoofed.

RastrIO MUST treat them as discovery/selection metadata, not cryptographic identity.

### 28.2 User-selected `PrinterInstance`

The portable application-level `PrinterInstance` model belongs to `shared`.

It may persist an opaque platform-neutral transport locator/identity for convenience, but live Bluetooth/USB/browser/desktop device objects remain platform-owned.

That identity MUST remain separate from portable `PrinterProfile` data.

Exporting `.tcfg` MUST NOT export personal paired-device identity.

### 28.3 Bluetooth

Android Bluetooth transport SHOULD:

- request only permissions needed by the active workflow;
- rely on explicit user selection/pairing where required by the OS;
- avoid logging device names/addresses unless needed;
- use timeouts and cancellation;
- report partial transmission distinctly;
- close sockets on failure/cancellation;
- avoid interpreting arbitrary incoming data unless a defined status protocol requires it.

Bluetooth Classic/SPP does not provide application-level proof that a selected physical printer is the intended trustworthy printer. The UI and documentation MUST NOT imply stronger identity assurance than the platform/protocol provides.

### 28.4 USB

USB transport SHOULD:

- require the platform's explicit permission flow;
- validate selected interfaces/endpoints against the transport implementation's expectations;
- treat descriptors as untrusted;
- bound any inbound status data;
- close claimed interfaces/connections on failure or cancellation.

A matching VID/PID alone MUST NOT be treated as proof of trusted firmware.

### 28.5 Printer responses

Where status-query support is added, response parsers MUST be:

- bounded;
- timeout-controlled;
- strategy/profile-specific;
- robust against malformed bytes.

Printer response data MUST NOT be evaluated as code or used to bypass profile validation.

---

## 29. Android Platform Considerations

Android is the primary release target.

At an architectural level:

1. Runtime permissions belong in `androidApp`, not Core.
2. Permission requests MUST be scoped to the user action that needs them.
3. Denied permissions MUST fail the operation cleanly without weakening validation.
4. Bluetooth and USB discovery metadata is untrusted.
5. File import SHOULD use Android's supported document/file access mechanisms rather than constructing arbitrary paths from external strings.
6. Imported content MUST still pass Core validation after the platform grants access.
7. Temporary decoded/imported content, imported font copies where needed, and prepared-artifact spool data SHOULD remain in application-private storage.
8. `PrinterInstance` transport identity MUST remain separate from `.tcfg`.
9. External asset retrieval, if implemented, belongs in a policy-controlled platform service and MUST not become an implicit Core behavior.
10. Background/lifecycle interruption MUST preserve cancellation, prepared-artifact lifecycle, and partial-transmission semantics.

This document intentionally does not prescribe Android API call sequences.

---

## 30. Desktop/JVM Considerations

Desktop initially serves primarily as a development and shared-UI target.

Desktop introduces filesystem differences that are relevant to security:

- path separators differ by operating system;
- case sensitivity may differ;
- symlink behavior differs;
- user-selected paths may expose broad filesystem access.

Therefore `.td` archive paths MUST remain logical portable paths and MUST NOT inherit host filesystem semantics.

If Desktop printer transports are later added, they MUST follow the same `PreparedPrint` → trusted protocol encoder → transport separation and the same partial-output rules as Android.

Desktop development conveniences MUST NOT introduce a production-only bypass such as "load raw profile commands" into shared Core.

---

## 31. Web/Wasm Considerations

Web is a later target and MUST not force Core to weaken its security boundaries.

Where browser APIs are used:

- hardware access MUST use explicit browser capability/permission flows;
- feature detection is required;
- external assets MUST still use explicit resolver policy;
- browser-origin/network restrictions do not replace RastrIO's own resource limits;
- imported files MUST be validated exactly as on other platforms;
- unavailable WebUSB/WebSerial support MUST fail as an unsupported capability, not cause a Core fork.

Raw HTML from Markdown MUST NOT be injected into an execution-capable DOM path.

---

## 32. Dependency and Supply-Chain Policy

RastrIO is Apache-2.0 licensed, FOSS-oriented, and intended to be F-Droid compatible.

### 32.1 Dependency review

New dependencies used by official builds MUST be reviewed for:

- license compatibility;
- maintenance status;
- security history where relevant;
- Kotlin Multiplatform target support where required;
- F-Droid compatibility;
- transitive dependency impact;
- package/build size;
- whether the dependency is actually necessary.

### 32.2 Minimize dependency surface

A dependency SHOULD NOT be added when a small, well-tested implementation using existing platform/library capabilities is sufficient.

This is especially important for parsers, archive utilities, image libraries, font/shaping libraries, networking stacks, and protocol libraries because each can materially expand the attack surface.

Native-code dependencies such as the selected FreeType/HarfBuzz integration require explicit review of version pinning, update process, platform packaging, and malformed-input handling.

### 32.3 No mandatory proprietary services

Basic document creation, preview, profile handling, and printing MUST NOT require:

- Google Play Services;
- proprietary cloud accounts;
- proprietary analytics;
- proprietary crash-reporting services;
- remote license servers;
- vendor-specific proprietary printer SDKs.

Optional future integrations MUST NOT make such services mandatory for the core offline workflow without an explicit project-level architecture decision.

### 32.4 Pinned and reproducible configuration

Release builds SHOULD be reproducible and dependency resolution SHOULD be pinned as far as practical.

The repository SHOULD:

- use the Gradle Wrapper;
- keep dependency/plugin versions in the version catalog or other approved central configuration;
- avoid dynamic versions in release configurations;
- use dependency verification/checksums or equivalent Gradle mechanisms where practical;
- keep build-tool versions explicit;
- document any repository required for official builds.

### 32.5 Dependency updates

Dependency updates MUST be reviewed like code changes.

Automated update tooling MAY propose changes, but security-sensitive or major-version upgrades MUST NOT be merged solely because a bot generated them.

### 32.6 Release audit

Before a stable release, the project MUST perform:

- dependency review;
- license compatibility audit;
- FOSS/F-Droid compatibility review;
- review of mandatory repositories/services;
- security review of dependencies that parse untrusted files or interact with hardware/network boundaries.

---

## 33. Security Testing Strategy

Security testing is part of feature implementation.

The detailed repository-wide test taxonomy lives in `docs/TESTING.md`; the requirements below are specifically security-relevant.

### 33.1 Security regression tests

Every practical security bug fix MUST add an automated regression test where feasible.

Regression tests MUST preserve the smallest stable malicious or malformed fixture necessary to reproduce the issue without embedding real user secrets.

### 33.2 Archive parser tests

`.td` archive tests MUST include at least:

- parent traversal;
- absolute path;
- alternate-separator traversal;
- normalized duplicate path;
- duplicate required entry;
- malformed ZIP;
- truncated entry;
- false size metadata where the library permits simulation;
- excessive entry count;
- excessive compressed size;
- excessive expanded size;
- oversized individual entry;
- oversized JSON;
- missing manifest;
- missing `document.json`;
- unsupported versions;
- special/link entries where the archive implementation exposes them;
- cancellation during read;
- cleanup after failure.

### 33.3 Malformed serialization tests

`.td` and `.tcfg` tests MUST include:

- invalid JSON;
- truncated JSON;
- duplicate object keys before parser collapse/overwrite;
- comments and trailing commas;
- single-quoted strings / unquoted property names;
- `NaN`, `Infinity`, `-Infinity`, and other prohibited numeric forms;
- unknown top-level and nested v1 properties even when a parser can ignore them;
- wrong field types;
- unknown discriminators;
- unsupported schema versions;
- excessive nesting;
- excessive collection counts;
- invalid dimensions;
- invalid enum values;
- duplicate IDs where prohibited;
- invalid references;
- semantically inconsistent capability combinations.

### 33.4 Profile-injection tests

`.tcfg` security tests MUST verify rejection of attempts to introduce:

- raw ESC/POS bytes;
- hexadecimal command programs;
- command templates;
- script/expression fields;
- unknown protocol-family IDs;
- attempts to make an unknown family fall back to ESC/POS;
- dynamic module/class/native-library identifiers;
- unknown strategy IDs;
- out-of-range strategy parameters;
- unsafe override values.

A test MUST prove that an imported profile cannot cause the encoder to emit arbitrary attacker-selected byte programs through the normal profile path.

### 33.5 Native-text command-injection tests

Tests MUST cover document text containing protocol control characters or equivalent unsafe byte sequences.

The expected result MUST be a safe semantic handling path, diagnostic, or raster fallback—not direct protocol-command execution.

### 33.6 Markdown tests

Security-focused Markdown fixtures SHOULD include:

- raw HTML;
- script-like HTML;
- giant/deep nesting;
- long unbroken tokens;
- malformed links;
- external images;
- `file:` references;
- `data:` references where applicable;
- suspicious or malformed URLs;
- content that resembles ESC/POS control sequences.

Compilation MUST remain network-free.

### 33.7 Image tests

Image security tests MUST include, subject to available decoders:

- oversized declared dimensions;
- truncated data;
- malformed metadata;
- extreme aspect ratios;
- dimensions causing multiplication overflow if unchecked;
- images above the configured pixel budget;
- cancellation and cleanup;
- supported-format confusion/mislabeled extensions.

### 33.8 Font security tests

Security-focused font tests MUST cover the explicitly imported/user-selected font boundary where that feature is enabled.

At minimum, include:

- over-limit font files;
- malformed/truncated fonts;
- pathological table/record structures;
- composite-glyph recursion/pathology where supported by the backend;
- shaping-table stress fixtures;
- repeated font/size/style combinations that pressure caches;
- cancellation during font parsing/shaping;
- unavailable explicit platform-font selection;
- proof that the default fallback path does not trigger network access or unbounded system-font scanning.

Where practical, native-backend failures MUST remain contained as controlled operation failures rather than process-wide corruption.

### 33.9 Prepared-artifact/spool tests

Tests MUST cover:

- application-generated/private artifact identity;
- rejection of attacker-controlled paths as artifact backing locations;
- payload/store resource-limit enforcement;
- bounded index/chunk metadata;
- cancellation cleanup;
- normal disposal cleanup;
- prevention of premature cleanup while a preparation remains valid;
- preview and encoder reading identical finalized content;
- tampered/corrupt/unreadable artifact behavior;
- failure rather than silent rerasterization/regeneration;
- stale artifact identity from another preparation not being reused.

### 33.10 Transport tests

Fake transport tests MUST cover:

- failure before first byte;
- failure after partial transmission;
- timeout;
- cancellation;
- disconnect;
- reconnect;
- short/partial writes where applicable;
- bounded chunking/backpressure;
- correct `outputMayHaveOccurred` semantics;
- prohibition of automatic retry after ambiguous output.

### 33.11 Logging tests

Where practical, automated tests SHOULD assert that common error paths do not include:

- Markdown bodies;
- QR payloads;
- image bytes;
- tokens;
- raw document bodies;
- raw encoded print data.

Redaction behavior SHOULD itself have regression coverage.

---

## 34. Fuzzing Targets

Fuzzing SHOULD be introduced as soon as the relevant parser/validator exists.

High-value fuzz targets include:

1. `.td` ZIP/archive reader;
2. `.td` manifest JSON parser;
3. `.td` document JSON parser and validator;
4. `.tcfg` JSON parser and validator;
5. Markdown compiler front end;
6. archive-path normalization;
7. external-URL parser/policy layer;
8. profile override validation;
9. prepared-operation validation;
10. prepared-artifact metadata/index/read adapters;
11. imported font boundary/parser integration where practical;
12. status-response parsers when printer queries are implemented.

For image formats primarily decoded by platform libraries, RastrIO SHOULD fuzz or property-test its boundary logic—dimension checks, size accounting, metadata interpretation, and decoder result validation—even when the underlying codec itself is outside the project.

For FreeType/HarfBuzz or equivalent native font backends, RastrIO SHOULD reuse upstream fuzz-tested stable releases and additionally fuzz/property-test its own resource/accounting/adapter boundary where practical.

Fuzz targets MUST use bounded execution and MUST treat crashes, hangs, stack overflows, unbounded growth, and invariant violations as failures.

A fuzz-discovered issue that is fixed MUST receive a stable regression fixture/test where practical.

---

## 35. Property-Based Security Tests

Property-based tests SHOULD cover invariants that are difficult to exhaust with examples.

Examples:

- normalized archive paths never escape the logical root;
- a path rejected on one supported platform cannot become an extraction escape on another;
- normalized duplicate paths are always detected;
- duplicate JSON object keys cannot be lost before conformance validation;
- unknown `.td`/`.tcfg` v1 fields cannot be silently ignored;
- size accounting never decreases as bytes are consumed;
- packed raster row sizes never overflow and equal the defined geometry;
- validated profile overrides cannot produce invalid physical dimensions;
- unknown protocol family/strategy identifiers never reach an encoder;
- prepared-artifact identities cannot resolve to attacker-selected filesystem paths;
- prepared artifact corruption cannot trigger output regeneration;
- segmented coverage cannot create negative dimensions;
- cancellation never transitions a partially transmitted job to a state implying no output occurred.

---

## 36. Security Test Fixtures

Security fixtures SHOULD live under `test-fixtures/` in clearly named subdirectories.

Malicious fixtures MUST:

- contain no real secrets;
- contain no real user documents;
- be as small as practical;
- document what security property they exercise;
- be deterministic;
- not depend on external network availability.

Golden updates MUST remain intentional. Normal CI MUST NOT silently rewrite security fixtures.

---

## 37. CI and Release Gates

Repository verification SHOULD include security-sensitive tests in the normal mandatory path for the current development phase.

Before stable Android release, mandatory gates MUST include:

- `.td` archive security regression suite;
- `.tcfg` validation/security regression suite;
- resource-limit tests;
- path traversal tests;
- ZIP-bomb/expansion-limit tests;
- malformed-input and strict-JSON conformance tests;
- imported-font security/resource tests if user-font import is enabled;
- prepared-artifact/spool lifecycle/integrity tests;
- transport partial-failure/retry tests;
- privacy/logging review;
- permission review;
- dependency/license audit;
- mandatory hardware regression suite.

A security test MUST NOT be made non-blocking merely because the malicious input is uncommon.

---

## 38. Hardware Security and Manual Tests

`docs/HARDWARE_TESTS.md` contains numbered physical tests.

Security-relevant hardware tests SHOULD include:

- disconnect during printing;
- cancellation during printing;
- reconnect after ambiguous partial transmission;
- cutter behavior where supported;
- oversized/long-job cancellation;
- status-query malformed/unexpected response behavior where testable;
- profile mismatch handling;
- explicit user retry after ambiguous output.

Hardware tests MUST record the printer profile/version, app commit/release, transport, input fixture, expected physical behavior, observed behavior, and quirks.

Manual hardware testing does not replace automated fake-transport tests.

---

## 39. Security Response and Vulnerability Handling

### 39.1 Reporting channel

Before a stable release, the repository SHOULD publish a private security-reporting channel suitable for pre-disclosure vulnerability reports.

The exact reporting endpoint is a repository-maintenance decision and MUST NOT be invented in this specification.

Public issue reports MAY be used for non-sensitive hardening requests, but reporters SHOULD have a private option for vulnerabilities whose details would materially aid exploitation.

### 39.2 Triage

A reported issue SHOULD be triaged based on concrete impact such as:

- arbitrary local file write/read;
- arbitrary printer command execution;
- unexpected network access;
- content/privacy disclosure;
- denial of service;
- duplicate or unsafe physical output;
- supply-chain compromise;
- bypass of format/profile validation.

Severity labels and disclosure timelines MAY follow the project's chosen security process, but the technical facts and affected versions MUST be documented.

### 39.3 Fix requirements

A security fix SHOULD include:

- root-cause description;
- affected versions or commits where determinable;
- regression test;
- compatibility/migration implications;
- release note or advisory when user action is required;
- documentation updates if an invariant, format rule, or resource limit changes.

### 39.4 Security-sensitive format changes

If a fix requires tightening `.td` or `.tcfg` validation, maintainers MUST assess compatibility impact.

The project MUST NOT silently accept unsafe legacy behavior solely for backward compatibility.

Where compatibility must change, the relevant format specification and migration policy MUST be updated.

---

## 40. Security Review Requirements for New Features

A feature requires explicit security review when it introduces or materially changes any of the following:

- file import/export;
- archive contents;
- serialization schema;
- external network access;
- image decoding;
- imported/user-selected font parsing or platform-font access;
- HTML rendering;
- URL handling;
- printer-profile capabilities;
- new protocol families or protocol operations;
- raw byte handling;
- Bluetooth/USB/Web hardware access;
- printer status queries;
- temporary files or prepared-artifact spool storage;
- persistent device identities;
- logging or diagnostic export;
- background execution;
- new dependencies that parse untrusted data or access the network/hardware.

The review MUST identify:

1. new untrusted inputs;
2. resource limits;
3. failure behavior;
4. privacy/logging impact;
5. network behavior;
6. physical-output implications;
7. regression/fuzz tests;
8. documentation changes.

---

## 41. Contributor Security Checklist

For a change touching untrusted input or physical output, contributors SHOULD verify all applicable items:

- [ ] Input is treated as untrusted.
- [ ] Parsing is bounded by `docs/RESOURCE_LIMITS.md`.
- [ ] Integer size calculations are overflow-safe.
- [ ] Validation occurs before trusted Core use.
- [ ] Strict JSON rules are enforced before lossy parser behavior can hide duplicate/unknown fields.
- [ ] Unknown versions/protocol families/strategies fail safely.
- [ ] No hidden network access was introduced.
- [ ] Raw HTML cannot execute.
- [ ] External assets remain explicit.
- [ ] Imported profiles cannot inject raw commands or dynamically load protocol code.
- [ ] User text cannot inject printer-protocol control sequences.
- [ ] Imported fonts are bounded/untrusted and never trigger implicit network fallback.
- [ ] Long data is streamed/tiled/banded/spooled where required.
- [ ] Prepared artifacts are immutable/private/bounded and never silently regenerated.
- [ ] Cancellation releases resources.
- [ ] Temporary/spool files are private, bounded, lifecycle-managed, and cleaned without deleting live prepared artifacts.
- [ ] Logs contain no printable content or secrets by default.
- [ ] Partial transmission does not trigger automatic retry.
- [ ] Platform permissions are scoped to the feature.
- [ ] New dependencies were reviewed.
- [ ] Automated regression tests cover the new security boundary.
- [ ] Relevant fuzz/property tests were considered.
- [ ] Format/security/resource documentation is updated.

---

## 42. Security Definition of Done

A security-sensitive feature is complete only when:

1. the trust boundary is explicit;
2. untrusted inputs are validated;
3. applicable resource limits are enforced;
4. failure behavior is structured and safe;
5. cancellation/cleanup behavior is defined;
6. logging/privacy impact is addressed;
7. network behavior is explicit;
8. protocol output cannot be attacker-programmed through normal portable formats;
9. unknown protocol families cannot trigger fallback or dynamic code loading;
10. imported font and prepared-artifact boundaries are secured where applicable;
11. transport partial-output semantics are preserved;
12. automated tests cover realistic malformed/adversarial cases;
13. hardware tests are added where physical behavior is relevant;
14. dependency implications are reviewed;
15. related specifications are updated.

Compilation alone is not evidence of security completeness.

---

## 43. Resolved Security Baseline and Maintenance Decisions

The security architecture for v1 is sufficiently defined for implementation.

The following items remain maintenance or future-feature decisions rather than unresolved v1 security architecture.

### 43.1 Concrete resource values

Concrete resource thresholds live in `docs/RESOURCE_LIMITS.md`.

Security relies on their enforcement but does not duplicate the numeric policy here.

### 43.2 Vulnerability-reporting endpoint

Before stable release, the repository SHOULD publish a private vulnerability-reporting channel.

The exact service/address is a repository-maintenance decision and is intentionally not invented in this specification.

### 43.3 Raw-command tooling

No raw-command escape hatch is defined or permitted by the normal v1 `.tcfg` format or ordinary document pipeline.

If such a developer tool is proposed in the future, it requires a separate explicit architecture/security design.

It MUST NOT be introduced as an undocumented profile field, normal import option, or fallback path.

### 43.4 Printer attestation

RastrIO v1 has no cross-printer cryptographic attestation mechanism.

Bluetooth/USB names, descriptors, addresses, VID/PID values, product strings, and similar metadata remain discovery/selection hints rather than proof of firmware or device identity.

### 43.5 Future protocol families

`.tcfg` schema v1 supports only the trusted ESC/POS family.

A future protocol family requires deliberate schema/specification work plus a trusted family-specific encoder/security review.

It MUST NOT introduce dynamically downloaded/imported protocol code.

### 43.6 Imported-font backend hardening

The architecture now defines imported fonts as untrusted and bounded.

Concrete parser/cache tuning limits still require profiling/fuzzing of the pinned HarfBuzz/FreeType integration before user-font import is considered stable.

This is a measurement/hardening task governed by `RESOURCE_LIMITS.md`, not a reason to leave the trust boundary undefined.

---

## 44. Summary of Mandatory Security Contracts

RastrIO's minimum security contract is:

```text
Untrusted input
    ↓
bounded acquisition
    ↓
strict parsing
    ↓
semantic validation
    ↓
trusted portable model
    ↓
deterministic bounded Core processing
    ↓
validated PreparedPrint
+ immutable prepared artifacts
    ↓
trusted protocol-family serialization
    ↓
bounded transport
    ↓
physical printer
```

At every boundary:

- sizes are bounded;
- paths are normalized;
- executable interpretation is prohibited unless explicitly part of trusted application code;
- network access is explicit;
- private printable content is not routine log data;
- printer profiles select registered protocol families/strategies rather than inject programs;
- imported fonts remain bounded data rather than executable authority;
- finalized prepared artifacts remain private, immutable, bounded, and shared by preview/encoding;
- long work is streamable/cancellable;
- ambiguous partial physical output is never automatically retried.

These contracts apply from the first implementation of each feature and are not deferred release-hardening tasks.
