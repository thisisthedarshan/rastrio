# Printer profiles and strict `.tcfg` codec (Phase 5)

`PrinterProfile` is an immutable **candidate**, not an accepted capability set.
`PrinterProfileValidator.validate` returns either `Success<ValidatedPrinterProfile>`
or `Failure` with fatal structural diagnostics. Downstream APIs should accept the
validated wrapper, not a candidate. The wrappers have no public constructors or
copy methods. `ProfileList` snapshots caller-owned collections and exposes a
read-only list; nested capability values are immutable as well.

The complete `.tcfg` v1 domain includes identity/display metadata, independent
horizontal/vertical DPI, printable width, native fonts/styles/code pages, integer
registered selectors, raster bands, native QR/barcodes, cutter, printer-side
buffer guidance, status queries, the explicit ESC/POS protocol variant and quirks.
The closed v1 `ProtocolProfile` cannot represent an unknown family; the byte
reader rejects unknown families before constructing the ESC/POS variant.

## Trusted validation inputs

Applications supply `TrustedProfileRegistry` and `PrinterProfileValidationLimits`.
Neither is loaded from profile data. Registry methods must be deterministic,
side-effect-free lookups into trusted metadata. Strategy metadata distinguishes
purpose, compatible dialects, integer selector ranges and documented cell-height
line-advance defaults. Dialect metadata identifies unambiguous font/repertoire
defaults and optional tighter scale constraints. Unknown identifiers never cause
reflection, dynamic loading, I/O or command generation.

`BaselineTrustedProfileRegistry` supplies the v1 generic ESC/POS dialect and
eight strategy definitions from `TCFG_SPEC.md` §20. It recognizes `cp437`,
`model2`, `code128`, and `ean13`, with no registered quirks or inferred native
defaults. Font selectors use `0..1`; code-page selectors use `0..255`. These
are vocabulary/parameter constraints, not hardware compatibility evidence.
The registry contains metadata only; trusted command implementations belong
to a later phase.

`SyntheticReferenceProfiles` supplies immutable narrow, 80 mm, and raster-only
candidates from the specification examples. Callers must validate them using
their own resource policy. Reviewable canonical fixtures live in
[`test-fixtures/tcfg`](../../test-fixtures/tcfg/README.md); JVM tests compare
their bytes with production codec output, while common tests cover the same
capability values portably. These values make no commercial hardware claims.
The [H50i worksheet](../../docs/H50I_DEVELOPMENT_PROFILE.md) records missing
mandatory geometry/protocol evidence; no loadable H50i profile is supplied.

Numerical and collection ceilings are **required policy inputs**, not new schema
rules or universal hardware constants. These include DPI, dimensions, scales,
raster band bytes, QR module size, printer buffer bytes/pause and per-list counts.
Only the documented provisional 64 KiB UTF-8 string baseline has a default.
Policy construction mistakes throw `IllegalArgumentException`; expected invalid
candidate data returns diagnostics. Counts are checked before collection walks,
identifiers before registry resolution, and strings without allocating UTF-8
copies. Raster byte counts and native scale products widen before arithmetic.
The byte codec additionally enforces parsing limits from `RESOURCE_LIMITS.md`
before and during parsing; typed semantic validation does not replace parsing.

## Overrides

`resolve(base, override)` takes a validated base and replaces the explicitly
supplied geometry, capability blocks, protocol or quirk list. Omitted fields
inherit. Blocks and their arrays replace completely; there is no generic merge.
Buffer burst/pause recommendations are independently optional.
`PrinterBufferOverride.Inherit` differs from `Replace(null)`, which clears guidance.
Identity, display metadata and schema version are not calibration overrides.
The **entire** resulting candidate is validated against the current registry and
policy, even when the base was accepted under a different policy. Success yields
`EffectivePrinterProfile`; failure never falls back to the base. Input/base values
remain unchanged. Capability order is retained; the writer sorts only native text scale arrays.

## Diagnostic contract

All diagnostics are fatal and contain constant messages without imported content.
Traversal follows schema field order, then list order; cross-field output-path
validation is last. Paths contain only fixed property names and numeric indexes.

| Code | Meaning |
|---|---|
| PRF100 | Unsupported format or schema version |
| PRF101 | Invalid bounded ASCII identifier |
| PRF102 | Unsafe display control or invalid Unicode scalar sequence |
| PRF103 | Numerical semantic minimum violation |
| PRF104 | Missing or inconsistent capability dependency |
| PRF105 | Duplicate capability/identifier |
| PRF106 | Unregistered identifier or unsupported v1 enum |
| PRF107 | Strategy purpose/dialect or quirk/dialect incompatibility |
| PRF108 | Invalid selector parameter, unavailable documented default or dialect scale constraint |
| PRF120 | Trusted resource/sanity policy exceeded |

Native-text disablement requires empty fonts/code pages. Style declarations are
still validated but cannot activate native text when `supported` is false.
Omitted line advance is accepted only when trusted metadata documents derivation
from cell height; no generic font geometry or selection is guessed.

## Tests and phase boundary

Portable tests cover typed synthetic candidates with both test and production registries.
They cover valid output paths, semantic failures, registry/default constraints,
resource/arithmetic boundaries, deterministic diagnostics, immutable snapshots and
fully revalidated overrides. Display/model names have no effect on validation.

This module retains zero Core dependencies. Phase 6 physical preparation,
ESC/POS encoding, transport, physical preview, UI, `PrinterInstance` persistence
and `PrintOptions` are deliberately absent.

## Phase 5B byte codec

`TcfgCodec(registry, validationLimits, resourcePolicy)` exposes:

- `decode(ByteArray): ProfileValidationResult<ValidatedPrinterProfile>`
- `encode(ValidatedPrinterProfile): ProfileValidationResult<ByteArray>`

It operates entirely on portable values with no filesystem, networking or
platform API. Every successfully decoded candidate passes the existing Phase 5A
validator with the supplied trusted registry and semantic limits. Structural
failures return one fatal diagnostic; semantic failures retain the validator's
codes, paths and ordering. Expected hostile input never exposes parser exceptions.

The local strict JSON machinery deliberately follows `core-document`'s approach
without a dependency on that module or a new shared serialization framework.
Duplicate decoded keys fail before their values are parsed or inserted; escaped
aliases such as `name` and `na\u006de` also collide. Exact property sets are
checked at every object level. Integer fields accept only integer JSON tokens
(no decimal/exponent representation), using checked `Int` conversion without
floating point. Capability consistency and registered enums/strategies remain
owned by the existing semantic validator. Unknown protocol families fail during
schema decoding.

Malformed UTF-8 and a leading BOM are rejected. BOMs outside strings fail JSON
syntax; U+FEFF inside a string is ordinary Unicode data. Unpaired JSON surrogate
escapes are rejected. UTF-8 string bytes are counted by scalar before appending.
Object keys share the same string budget as values. No diagnostic includes
attacker-controlled key names or values: syntax/resource paths use `$`, and
schema paths contain fixed property names and numeric indexes.

`TcfgResourcePolicy` defaults to 1 MiB input/output bytes, 64 container nesting
levels, 64 KiB per decoded string, and 100,000 nodes/tokens (including keys).
Additional operational ceilings are 64 properties per object and 64 characters
per numeric token. Depth policy may be lowered but cannot exceed the portable
64-level recursive implementation ceiling. Arrays use
`PrinterProfileValidationLimits.maxCollectionEntries` during parsing, and the
string budget is the smaller of parsing and semantic policy. Limits apply even
to unknown fields before schema rejection. These are trusted operational policy,
not wire fields or new schema-version semantics. Canonical scale sorting checks the existing semantic collection ceiling and
minimum encoded size before allocating a sorted copy, including for profiles
validated under a different trusted policy. Output construction is bounded
in UTF-16 characters, then checked against the UTF-8 byte ceiling; its temporary
buffers are bounded multiples of that ceiling.

The writer emits compact standard JSON, no BOM, stable specification property
order, absent optional values omitted, and ascending native text scales. Other
arrays retain domain order. Decode/re-encode of canonical output is byte-stable;
unsorted input scales normalize without changing capability meaning. The writer
accepts only the validated wrapper, with no override/effective-profile overload.
It exports capability data alone.

| Code | Wire-stage meaning |
|---|---|
| PRF130 | Invalid UTF-8 or rejected leading BOM |
| PRF131 | Invalid standard JSON or Unicode string syntax |
| PRF132 | Duplicate decoded JSON property |
| PRF133 | Unknown v1 property (path identifies containing object) |
| PRF134 | Missing required property |
| PRF135 | Wrong token type, including illegal null |
| PRF136 | Non-integer representation or integer overflow |
| PRF137 | Unsupported wire format, schema version or protocol family |
| PRF138 | Trusted parsing or output resource limit exceeded |

The common codec tests exercise the production byte path with an in-memory test
registry. Test profiles are not maintained/reference printer profiles. No
production catalog, preparation, protocol encoding, hardware or transport was
introduced by Phase 5B. Phase 5C adds only the metadata and reference data above;
Phase 5D verifies profile selection/overrides, leaving physical plans to Phase 6.
