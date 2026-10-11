# H50i first-print evidence runbook

No hardware result is recorded by this implementation. Follow the permanent
procedures in [HARDWARE_TESTS](HARDWARE_TESTS.md); preserve their IDs.

## Before connecting RastrIO

1. Capture HW-035 self-test and record unit/firmware, paper, software revision and
   measurement tools in a private evidence record. Do not publish Bluetooth
   addresses or pairing information.
2. Establish printable dot width and independently measured horizontal and
   vertical DPI. A reported 48 mm width cannot supply these three values.
3. Capture and verify the registered dialect and initialization command path.
   Validate native font selectors, cell/advance metrics and CP437 selector/repertoire
   before enabling those capabilities. Generic Epson syntax and byte goldens do
   not establish H50i compatibility.
4. Record absence of the automatic cutter. Keep cutter supported=false; no full
   or partial cut command applies to this observed unit.
5. Populate an evidence-scoped development .tcfg through the ordinary strict
   validator. Do not rename a synthetic fixture to imply hardware verification.
   Keep unknown buffer/pacing data absent. No raw-command terminal is supplied.

## Controlled software workflow

Pair the device in Android settings. Import the validated .tcfg, request
Bluetooth connection permission, refresh paired devices and select the target.
Inspect the associated imported profile and prepare the document explicitly.
Print consumes that displayed immutable plan. Changing source/profile/options
requires a fresh preparation. Begin with a short, bounded ASCII fixture.

Local transmission completion means bytes were accepted by the socket API; it
never confirms paper output. After timeout/disconnect/cancellation, inspect the
paper and acknowledge possible partial/duplicate output before requesting a
fresh attempt. Never reconnect and replay automatically.

## Semantic alignment and style fixtures

HW-003/HW-004 use public semantic preparation rather than Markdown directives.
Use the complete Kotlin fixture in [hardware fixture instructions](../test-fixtures/hardware/README.md).
The essential inputs are:

```kotlin
val aligned = ThermalDocument(blocks = listOf(
    Paragraph(Alignment.LEFT, listOf(Text("LEFT"))),
    Paragraph(Alignment.CENTER, listOf(Text("CENTER"))),
    Paragraph(Alignment.RIGHT, listOf(Text("RIGHT"))),
))
val styled = ThermalDocument(blocks = listOf(
    Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text("BOLD"))), Text(" PLAIN"))),
))
val alignmentResult = BasicPreparationEngine().prepare(aligned, verifiedProfile)
val boldResult = BasicPreparationEngine().prepare(styled, verifiedProfile)
val underlineResult = BasicPreparationEngine().prepare(styled, verifiedProfile,
    PrintOptions(nativeUnderline = true))
```

Import the ordinary `core.document` and `core.printer` types. `verifiedProfile`
is the actual evidence-validated target profile. Bold and underline jobs require
verified native style support; failed preparation is a diagnostic, not permission
to claim support. Underline is a transient job option and is never stored in
`.td`/`.tcfg`. Preview the successful immutable plan before transmission. The
fixtures retain no automatic cut; no raw command or invented H50i metric is used.

## Permanent acceptance sequence

| ID | Evidence required | Current result |
| --- | --- | --- |
| HW-001 | Short ASCII paper and captured exact stream/profile | NOT RUN |
| HW-002 | Narrow/boundary wrapping measured against prepared geometry | NOT RUN |
| HW-003 | Left/center/right and measured prepared positions | NOT RUN |
| HW-004 | Bold/underline and state transitions with capability evidence | NOT RUN |
| HW-005 | Long text: continuity, timing and truncation check | NOT RUN |
| HW-006 | Interrupted job, conservative outcome and confirmed new attempt | NOT RUN |
| HW-007 | Checkerboard packing/polarity/orientation | NOT RUN |
| HW-008–011 | Threshold/Bayer/Atkinson/Floyd–Steinberg gradients | NOT RUN |
| HW-012 | Actual full-width raster, measured printed dots | NOT RUN |
| HW-013 | Long raster continuity and bands | NOT RUN |
| HW-035 | Self-test capability inventory plus missing calibration | NOT RUN |
| HW-031–032 | Automatic cutter | N/A: cutter absent on observed H50i |

All NOT RUN entries lack an accessible evidence-validated target configuration
and captured physical run. Raster tests additionally require a verified raster
strategy, width and band limits. Follow HW-024–029 for transmission accounting,
cancellation, recovery and measured chunk/pacing stress when equipment is ready.

## Record each run

Record profile hash/revision, effective options, code revision, device/firmware
scope, transport policy, prepared geometry/artifact hash, exact stream or private
export, observed paper/photos, measurements, diagnostic codes and PASS/FAIL/NOT
RUN/N/A rationale. Distinguish DOC, BYTE, hardware-observed and hardware-verified
claims per [ESC_POS_NOTES](ESC_POS_NOTES.md). Store private identifiers outside
version-controlled documentation. Update the H50i worksheet only with sufficient
actual evidence.
