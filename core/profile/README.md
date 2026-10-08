# Printer profile domain (Phase 5A)

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
The closed v1 `ProtocolProfile` cannot represent an unknown family; the future
reader must reject unknown families rather than map them to ESC/POS.

## Trusted validation inputs

Applications supply `TrustedProfileRegistry` and `PrinterProfileValidationLimits`.
Neither is loaded from profile data. Registry methods must be deterministic,
side-effect-free lookups into trusted metadata. Strategy metadata distinguishes
purpose, compatible dialects, integer selector ranges and documented cell-height
line-advance defaults. Dialect metadata identifies unambiguous font/repertoire
defaults and optional tighter scale constraints. Unknown identifiers never cause
reflection, dynamic loading, I/O or command generation. There is no production
strategy catalog or device profile in this phase.

Numerical and collection ceilings are **required policy inputs**, not new schema
rules or universal hardware constants. These include DPI, dimensions, scales,
raster band bytes, QR module size, printer buffer bytes/pause and per-list counts.
Only the documented provisional 64 KiB UTF-8 string baseline has a default.
Policy construction mistakes throw `IllegalArgumentException`; expected invalid
candidate data returns diagnostics. Counts are checked before collection walks,
identifiers before registry resolution, and strings without allocating UTF-8
copies. Raster byte counts and native scale products widen before arithmetic.
The future byte reader must also enforce pre-allocation parsing limits from
`RESOURCE_LIMITS.md`; typed semantic validation does not replace that reader.

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
remain unchanged. Capability order is retained; canonical serialization is later.

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

Portable tests use only typed synthetic candidates and an in-memory test registry.
They cover valid output paths, semantic failures, registry/default constraints,
resource/arithmetic boundaries, deterministic diagnostics, immutable snapshots and
fully revalidated overrides. Display/model names have no effect on validation.

This module retains zero Core dependencies. Phase 5B JSON/file handling, Phase 5C
production strategy catalogs, Phase 5D device data, Phase 6 physical preparation,
ESC/POS encoding, transport, physical preview, UI, `PrinterInstance` persistence
and `PrintOptions` are deliberately absent.
