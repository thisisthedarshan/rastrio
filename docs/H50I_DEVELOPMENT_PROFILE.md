# H50i Development Profile — Evidence Worksheet

**Status:** Incomplete / unverified; no loadable H50i `.tcfg` profile
**Evidence review date:** 2026-10-08
**Device scope:** Helett H50i BillQuick Go unit described in the existing notebook

This worksheet satisfies the Phase 5 development-profile/evidence deliverable in
[PRD §85](../PRD.md#85-phase-5--printer-profiles-and-protocol-strategies).
It records existing evidence and remaining questions; it adds no hardware result.
The capability contract remains [TCFG_SPEC §33](TCFG_SPEC.md#33-hardware-backed-production-profile-requirements).
The observation authority is [ESC_POS_NOTES §31](ESC_POS_NOTES.md#31-helett-h50i-billquick-go--available-reference-device-notebook).

## Evidence classification and sources

Use the [notebook evidence model](ESC_POS_NOTES.md#3-evidence-model):

| Class | Meaning here | Current H50i evidence |
|---|---|---|
| `DOC` | Specification/documentation-derived; documentation alone does not prove clone behavior | Generic Epson command candidates; no authoritative H50i protocol manual recorded |
| `BYTE` | Automated exact-byte verification; does not prove hardware acceptance | No H50i command-strategy byte verification recorded in the reviewed notebook |
| `HW + MODEL`, Hardware Observed | Physical observation reported for the identified unit; permanent repeatable run evidence pending | Existing RawBT, self-test, and cutter observations below |
| `HW + MODEL`, Hardware Verified | Reproduced through a numbered procedure with a sufficiently scoped run record | No promotion to this status made by this review |
| `INF + MODEL`, TBD | Inferred/unverified command candidate; cannot establish capability | Initialization, styles, raster, native QR, and status candidates |
| Missing/unknown | Required fact or parameter without sufficient recorded evidence | Dot geometry, dialect/initialization compatibility, and detailed capability parameters |

Source keys below are the existing [notebook source registry](ESC_POS_NOTES.md#4-source-registry)
keys, not newly collected evidence:

- `H50I-SELFTEST-2026-10-02`: reported physical self-test/configuration page.
- `H50I-RAWBT-2026-10-02`: reported physical Android/RawBT compatibility test;
  exact transmitted commands, chunk sizes, and pacing were not captured.
- `H50I-VENDOR`: placeholder for authoritative vendor material; currently TBD.
- `EPSON-CMDREF`: generic documentation; its command candidates do not establish
  H50i compatibility.

## Recorded observations and their limits

All device facts in this table remain `HW + MODEL`, **Hardware Observed**.
Observation IDs resolve to the existing notebook §31.2.

| Fact | Source / observation | Supported claim and remaining limit |
|---|---|---|
| ESC/POS-compatible printing through RawBT | `H50I-RAWBT-2026-10-02`; `ESC-OBS-0101`, `ESC-OBS-0112` | Supports `escpos` family on that tested path; does not establish a registered dialect or any individual strategy |
| Self-test reports `Print Width: 48mm` | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0113` | Reported physical width only; no reliable `printableWidthDots` or DPI |
| Self-test reports `Font: 12x24`, `FontVer: 1.00.00` | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0114` | Reported/default cell geometry and version context; selector, advance, line metrics, alternate fonts, scaling, and styles remain unknown |
| Self-test reports `Charset: CP437` | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0115` | Current/default charset label; no selector mapping, complete repertoire, or byte-to-glyph verification |
| Self-test prints QR and EAN-13 symbols | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0116`, `ESC-OBS-0117` | Internal firmware symbol generation only; no host QR/barcode strategy or parameter support established |
| Automatic cutter absent on observed unit | Direct physical observation recorded as `ESC-OBS-0110` | Sufficient to keep automatic cutter disabled conservatively; permanent evidence capture still pending; no full/partial cut commands applicable |
| Self-test reports `USB: Printing Port`, `BT_ver: 3.16.1` | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0118`, notebook §31.1 | Interface/version context; no RastrIO USB endpoint, Bluetooth session, or transport policy established |
| Max speed `60mm/sec`, density `5`, autofeed `0mm`, temperature `33`, voltage `4.20` | `H50I-SELFTEST-2026-10-02`; `ESC-OBS-0119`, `ESC-OBS-0120` | Reported configuration/telemetry; no portable buffer/pacing guarantee; ambiguous telemetry units are not inferred |

Pairing secrets, Bluetooth addresses, device/session identifiers, and other local
transport configuration are excluded from this worksheet and from `.tcfg`.

## Missing facts and future evidence work

The first two rows block a truthful loadable v1 profile. Optional capabilities
may remain disabled or omitted as the schema permits; disabling them cannot supply
missing mandatory geometry or validate an untested initialization strategy.

| Profile area | Missing evidence | Existing observation / future procedure |
|---|---|---|
| Required geometry | Reliable printable dot width, effective horizontal DPI, and independently established vertical DPI | `ESC-OBS-0113`; geometry calibration and `HW-035` inventory; millimetres alone are insufficient |
| Required protocol | Registered dialect compatibility and initialization behavior on captured controlled commands | `ESC-OBS-0100`, `ESC-OBS-0112`; `HW-001` when a controlled command path exists |
| Native text, if enabled | Font selector parameters, usable metrics/line advance, scaling/style behavior; code-page selectors and verified repertoire | `ESC-OBS-0102`–`ESC-OBS-0105`, `ESC-OBS-0114`, `ESC-OBS-0115`; `HW-001`–`HW-004` plus controlled font/character-grid measurements |
| Raster, if enabled | Accepted strategy, bit packing/orientation, maximum reliable width, safe band limits and printer-side guidance | `ESC-OBS-0108`; `HW-007`–`HW-013`, `HW-028`; generic `GS v 0` candidate is not evidence |
| Native QR/barcode, if enabled | Host command-path compatibility, models/symbologies, sizes, error correction and payload/parameter limits | `ESC-OBS-0109`, `ESC-OBS-0116`, `ESC-OBS-0117`; `HW-017`, `HW-019`, and a numbered barcode procedure when that feature is implemented |
| Cutter | Permanent capture of observed absence; disable automatic cutter for this unit | `ESC-OBS-0110`; `HW-035`; `HW-031`/`HW-032` are not applicable |
| Optional status | Reliable request/response semantics if a product feature requires them | `ESC-OBS-0111`; dedicated numbered procedure when needed; keep polling disabled meanwhile |
| Optional printer buffer guidance | Reproducible burst/band limits independent of transport write chunking | `ESC-OBS-0106`, `ESC-OBS-0108`; `HW-005`, `HW-013`, `HW-028`, `HW-029`; omit unknown guidance |
| Transport and reproduction context | RastrIO-controlled Bluetooth/USB behavior, revision scope and measurement tolerances | `ESC-OBS-0107`, `ESC-OBS-0118`; `HW-006`, `HW-029`, `HW-033`, `HW-035`; transport identity stays outside `.tcfg` |

These are future evidence tasks, not Phase 5 implementation work. The permanent
procedures and recording requirements are in [HARDWARE_TESTS](HARDWARE_TESTS.md),
especially `HW-035` and §32. No physical test was run for this worksheet.

## Development-profile decision

No loadable H50i `.tcfg` was created. v1 requires positive, authoritative
`geometry.horizontalDpi`, `geometry.verticalDpi`, and
`geometry.printableWidthDots`, plus a compatible registered dialect and
initialization strategy. The current recorded evidence cannot populate these
fields without inference. Common 203 DPI/58 mm conventions and the synthetic
profiles' values are not substitutes for measurements or device-specific evidence.

The worksheet remains outside the maintained production profile set. A future
development profile must trace every output-affecting field to suitable evidence
and pass the ordinary strict codec/registry/semantic validation pipeline.
Schema validity alone does not justify production promotion: the notebook §31.3
promotion rule still requires appropriate documentation, byte coverage, and
hardware validation for capabilities used by the supported printing path.

H50i display identity must never select hidden behavior. No H50i constants,
encoder, preparation, physical preview, or transport implementation are introduced
by this evidence review.
