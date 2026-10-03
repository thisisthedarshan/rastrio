# Rastrio Hardware Regression Tests

**Status:** Implementation specification  
**Specification revision:** 1.1  
**Project:** Rastrio  
**Purpose:** Permanent manual hardware regression-test specification  
**Current available reference device:** Helett H50i BillQuick Go  
**Primary initial transport:** Android Bluetooth Classic (RFCOMM/SPP)  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related documents:** `docs/TESTING.md`, `docs/PREVIEW_SPEC.md`, `docs/TEXT_RENDERING_SPEC.md`, `docs/TCFG_SPEC.md`, `docs/ESC_POS_NOTES.md`, `docs/RESOURCE_LIMITS.md`, `docs/SECURITY.md`

---

## 1. Purpose

This document defines Rastrio's permanent, numbered manual hardware regression tests.

Hardware tests exist to verify behavior that cannot be established sufficiently by pure unit tests, golden tests, transport fakes, serialization tests, or preview tests alone. They supplement automated testing; they do not replace it.

The **Helett H50i BillQuick Go** (H50i) is one physical ESC/POS-compatible device currently available for Rastrio development and validation.

It is not the generic printer model for Rastrio.

Permanent test procedures SHOULD be reusable across maintained `PrinterProfile` implementations wherever the tested capability is portable. A result on one printer proves behavior only for the recorded device/profile/firmware/transport context.

This specification has four goals:

1. keep physical-printer regression coverage stable and reviewable;
2. make physical expectations traceable to `PreparedPrint`, preview, encoded behavior, and profile capability data;
3. prevent unverified printer behavior from becoming generic Core assumptions; and
4. turn hardware discoveries into durable automated tests, profile corrections, and `ESC_POS_NOTES.md` observations.

A successful automated byte-level test proves what Rastrio emitted. A successful hardware test proves what a particular tested printer physically did under a recorded configuration. Neither result may be silently generalized to every ESC/POS-compatible printer.

---

## 2. Normative Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, **MAY**, and **RECOMMENDED** are normative in this document.

A requirement marked **provisional** is guidance that MAY be revised when measured hardware evidence becomes available. Provisional guidance MUST NOT be promoted to a universal printer capability or tolerance without evidence.

---

## 3. Core Principles

1. Hardware tests MUST have permanent IDs.
2. Established IDs MUST NOT be casually renumbered, reused, or reassigned to a different purpose.
3. If a test becomes obsolete, its ID remains retired and its historical meaning remains documented.
4. Hardware tests MUST focus on physical or hardware-coupled behavior that normal automated tests cannot establish adequately.
5. Automated unit, golden, property, security, and transport tests remain mandatory where applicable.
6. A physical-output change MUST have a corresponding physical-preview expectation where the feature is represented in preview.
7. Physical preview and physical printing MUST be derived from the same immutable `PreparedPrint` value or equivalent immutable snapshot, including the same finalized prepared-artifact content.
8. A hardware test MUST NOT rely on preview or protocol-encoder code independently re-laying out, re-wrapping, re-dithering, re-rasterizing, re-shaping, re-segmenting, regenerating missing prepared artifacts, or otherwise reinterpreting the print plan.
9. Tests MUST distinguish application/transport observations from actual printer feedback. If the hardware provides no reliable status confirmation, the test MUST NOT claim that it does.
10. Printer-specific behavior belongs in `PrinterProfile`, validated overrides, registered protocol-strategy selection, or a documented quirk. Generic Core code MUST NOT gain one-off model checks merely to make a single hardware test pass.
11. A failed hardware test SHOULD produce an automated regression test wherever the failure can be reproduced below the hardware boundary.
12. A newly discovered ESC/POS behavior or printer quirk SHOULD produce or update a corresponding observation in `docs/ESC_POS_NOTES.md`.
13. If the root cause is inaccurate capability data, the profile or validated override SHOULD be corrected rather than adding model-specific Core behavior.
14. A test that is not applicable because the selected profile does not advertise the required capability MUST be recorded as **N/A**, not forced through an unsafe or undocumented command path.
15. Hardware tests MUST use normal Rastrio preparation and encoding paths wherever practical. Printer Lab MAY generate test documents, but it MUST NOT become a separate hidden printer engine.

---

## 4. Test-ID Policy

### 4.1 ID format

Permanent hardware tests use:

```text
HW-NNN
```

where `NNN` is a zero-padded monotonically assigned number.

The PRD establishes `HW-001` through `HW-023`. Those IDs and names are preserved exactly in this specification.

This revision also preserves all previously assigned `HW-024` through `HW-034` IDs and adds `HW-035` for printer self-test/configuration evidence.

### 4.2 Stability rules

- An existing ID MUST NOT be renumbered because tests are reorganized into different sections.
- An existing ID MUST NOT be reused for a new behavior after removal of the old behavior.
- Substantial semantic changes to a test SHOULD receive a new ID. The old test MAY be marked retired with a pointer to its replacement.
- Small clarifications, improved fixtures, stronger evidence requirements, and added measurement instructions MAY be made without changing the ID, provided the original behavior under test remains the same.
- A test MAY be parameterized across multiple printer profiles, transports, or print options without creating new IDs when the underlying behavior is identical.
- Capability-specific variants MUST be recorded separately in the run record so one successful variant does not imply all variants passed.

### 4.3 Reserved and future tests

Tests `HW-024` and later in this document are permanent additional IDs, but many have future-phase prerequisites. Defining a test here does **not** claim that the corresponding feature is implemented.

---

## 5. Relationship to Automated Testing

Hardware regression is the final layer of a larger test stack.

Before a hardware test is considered meaningful, applicable lower layers SHOULD already pass:

```text
source/input
    ↓
ThermalDocument golden or builder test
    ↓
LogicalDocument layout test
    ↓
PreparedPrint golden / invariant test
+ prepared-artifact identity/content test where applicable
    ↓
PrintPreview contract test
    ↓
protocol byte-level golden test
(`core-escpos` for v1)
    ↓
transport fake/integration test
    ↓
physical hardware test
```

Hardware tests MUST NOT be used to compensate for missing deterministic tests. Examples:

- wrapping belongs in automated layout tests before `HW-002` is run;
- exact dither matrices belong in raster goldens before `HW-008` through `HW-011` are run;
- native/raster text-strategy selection belongs in printer-preparation tests before `HW-014` and `HW-015` are run;
- exact ESC/POS command serialization belongs in `core-escpos` goldens before v1 hardware validation;
- partial-transmission state transitions belong in transport tests before `HW-006` or `HW-024` is run.

A hardware test SHOULD therefore answer questions such as:

- Did the target printer interpret the emitted bytes as expected?
- Does the physical geometry correspond to the authoritative preview within the measured hardware tolerance?
- Does a long or banded stream remain physically continuous?
- Does a declared printer capability actually work on the tested hardware?
- Does a transport failure produce the conservative user-visible behavior required by Rastrio?

---

## 6. Applicability and Phase Gating

Each test declares a **required phase/feature**. A test is mandatory for a release or development gate only when its prerequisites are implemented and the corresponding feature is claimed by that build/profile.

This document does not promote roadmap features to release requirements by itself.

In particular:

- `HW-001` through `HW-006` belong to the first Android Bluetooth text-printing milestone.
- `HW-007` through `HW-013` apply when raster/image printing is implemented.
- `HW-014` through `HW-016` apply when Unicode raster-text fallback is implemented.
- `HW-017` through `HW-019` apply when QR capability resolution is implemented.
- `HW-020` through `HW-023` apply when wide-document landscape tiling is implemented or explicitly promoted to release-critical scope.
- `HW-024` through `HW-034` apply only when their stated feature prerequisites are implemented.
- `HW-035` applies whenever a physical printer exposes a self-test/configuration report worth capturing; it records evidence and does not itself prove host-command support.

If a build does not implement a future feature, its future test is **NOT RUN / prerequisite absent**, not a product failure.

---

## 7. Standard Hardware Test-Case Template

Every permanent test entry MUST contain, either directly or by explicit inheritance from a group precondition, the following fields.

| Field | Required content |
|---|---|
| **ID** | Permanent `HW-NNN` identifier. |
| **Title** | Stable descriptive name. |
| **Purpose** | Physical or hardware-coupled behavior the test proves. |
| **Required phase/feature** | Implementation phase or feature gate required before execution. |
| **Printer/profile** | Physical printer model and effective `PrinterProfile` identity/version. |
| **Transport** | Bluetooth, USB, other implemented transport, or a specified transport matrix. |
| **Prerequisites** | Capabilities, fixtures, app state, calibration, or equipment required. |
| **Input/document fixture** | Version-controlled fixture or deterministic Printer Lab generator. |
| **Print options** | Output-affecting `PrintOptions`; defaults MUST be recorded rather than assumed when they matter. |
| **Procedure** | Ordered manual actions and observations. |
| **Expected preview** | Expected physical-preview representation derived from the same `PreparedPrint`. |
| **Expected encoded/behavioral characteristics** | Relevant already-resolved operations, strategy, banding, command family, state transition, or transport behavior. Exact bytes SHOULD remain covered by automated goldens. |
| **Expected physical output** | What the operator should observe on paper or hardware. |
| **Pass criteria** | Explicit criteria for PASS. |
| **Failure observations** | Measurements and facts to capture when behavior differs. |
| **Evidence/artifact recording** | Required photos, scans, self-test pages, prepared-plan/artifact hashes, logs, or run metadata. |
| **Related automated tests** | Required lower-layer coverage. Exact source paths MAY be added once the repository implementation exists. |
| **Related ESC/POS observation IDs** | Observation IDs from `ESC_POS_NOTES.md`, or `None/TBD` until a relevant observation exists. |
| **Notes** | Capability caveats, known model-specific behavior, or follow-up requirements. |

A test entry MAY add fields such as measurement method, expected diagnostics, retry policy, or variant matrix when useful.

---

## 8. Test Fixtures

### 8.1 Fixture location

Hardware fixtures SHOULD live under a stable repository path such as:

```text
test-fixtures/hardware/
```

The exact internal serialization MAY vary by test. Source Markdown, `.td` documents, deterministic raster assets, or deterministic Printer Lab test generators are acceptable when they exercise the intended production pipeline.

### 8.2 Fixture rules

Fixtures MUST be:

- deterministic;
- version controlled where practical;
- small enough to review unless the test intentionally targets large-job behavior;
- free of private user data;
- explicit about required fonts or raster assets;
- independent of hidden network access;
- designed so the expected physical feature is easy to inspect.

If a test relies on an automatically generated fixture, the generator parameters MUST be recorded in the test run.

### 8.3 Stable content versus stable purpose

The test ID fixes the test's purpose, not every byte of its fixture forever. A fixture MAY be corrected or strengthened if the change is reviewed and does not change the behavior under test. Significant fixture changes SHOULD be noted in version control and, where appropriate, in historical test results.

---

## 9. Printer and Profile Recording

Every run MUST record the **effective** printer configuration, not merely the model name.

At minimum record:

```text
printer model
printer serial/local label if useful and non-sensitive
firmware version if known
base PrinterProfile ID
base PrinterProfile schema/version or content revision
validated user overrides, if any
transport type
transport/session settings that can affect delivery
```

Bluetooth MAC addresses, USB serial numbers, or other unique device identities SHOULD NOT be placed in public artifacts unless needed and deliberately redacted or pseudonymized.

If any development/maintained profile changes, prior results remain evidence only for the profile revision under which they were recorded unless compatibility with the new revision is explicitly established.

---

## 10. Physical Measurement Protocol

### 10.1 General rule

Rastrio MUST NOT define one universal physical tolerance for all thermal printers. Mechanical feed error, paper stock, thermal head behavior, printer firmware, dot pitch, cutting mechanism, and measurement technique vary by device.

A dimension-sensitive test MUST therefore record a tolerance source using the following precedence:

1. manufacturer-published tolerance, if reliable and applicable;
2. a profile-specific calibration/tolerance established by repeatable hardware measurement;
3. a test-specific empirical repeatability envelope established during calibration;
4. if none exists, **measurement-only / provisional** status, where the dimension is recorded but not treated as a universal pass/fail threshold.

A provisional tolerance MUST be labelled as such and MUST NOT be copied into generic Core logic.

### 10.2 Measurement tools

Use the least ambiguous tool appropriate to the dimension:

- a rigid ruler with millimetre markings for overall paper/print widths and long distances;
- a vernier/digital caliper for short distances where paper deformation can be controlled;
- a flatbed scan or perpendicular high-resolution photograph with a scale reference for geometry comparison;
- a magnifier or macro photograph for individual raster rows, registration marks, and fine alignment when necessary.

Do not tension, stretch, or curl the paper while measuring. Thermal paper SHOULD be allowed to lie flat and measurements SHOULD be taken soon enough that severe curling or environmental deformation does not dominate the result.

### 10.3 Printable width

To measure effective printable width:

1. use a fixture that places known marks or raster content at the prepared left and right printable boundaries;
2. record the prepared width in dots and the profile's physical conversion metadata;
3. measure the physical distance between the corresponding printed boundaries;
4. record clipping, unprinted edge bands, or asymmetric margins separately;
5. if repeated samples vary, record the observed range rather than hiding it behind a single rounded number.

A profile correction MAY be justified if repeated measurements show that the declared printable width is not reliably usable.

### 10.4 Margins and alignment

For left/center/right alignment tests, measure from a consistent paper edge or calibrated printable-origin mark. Centering SHOULD be evaluated relative to the effective printable region, not blindly relative to total paper width when hardware has asymmetric non-printable margins.

### 10.5 Line spacing

For repeated text lines, measure across multiple baseline or row intervals and divide by the number of intervals rather than relying on a single adjacent-line measurement. This reduces ruler-placement error.

The test record SHOULD identify whether the measurement corresponds to:

- baseline-to-baseline spacing;
- top-of-glyph to top-of-glyph spacing;
- raster-row distance; or
- feed distance between prepared operations.

### 10.6 Raster width

For raster output, compare:

```text
PreparedPrint raster width in dots
vs
profile dot-density/physical-width model
vs
measured physical raster width
```

The physical test verifies printer interpretation. Bit packing, row byte counts, and padding bits remain automated-test responsibilities.

### 10.7 Landscape overlap

For tiled output:

1. identify the logical overlap interval represented in adjacent `PrintSegment`s;
2. align the strips using registration marks or duplicated overlap content;
3. measure the physical duplicated region at multiple vertical positions if practical;
4. record average/visible drift and any feed-dependent skew;
5. distinguish preparation geometry error from manual assembly error.

### 10.8 Registration alignment

Registration marks SHOULD be measured using reconstructed strips placed flat on a common surface.

Record:

- horizontal mark-to-mark offset;
- vertical mark-to-mark offset;
- rotation/skew if visible;
- whether the error is consistent or grows with feed length.

No universal pass tolerance is defined here. The applicable profile/test calibration MUST define the acceptance envelope before a strict geometry gate is used.

### 10.9 Provisional repeatability guidance

When establishing a new profile-specific tolerance, print multiple identical samples under the same configuration. A small repeated set—**three samples is a practical provisional starting point, not a universal requirement**—can reveal whether the device is repeatable enough to support a numerical tolerance. If results vary materially, record the distribution and investigate paper feed, thermal settings, mechanical skew, or transport/printer buffering before choosing a tolerance.

---

## 11. Preview Comparison Rules

### 11.1 Authoritative aspects

For a prepared physical plan, hardware tests SHOULD compare the printed result against preview for aspects the preview contract makes authoritative, including as applicable:

- line wrapping;
- operation placement;
- alignment geometry;
- raster pixels/data;
- raster dimensions;
- segment boundaries;
- registration-mark placement;
- QR/image placement;
- manual cut-guide content;
- automatic-cut boundary annotation.

### 11.2 Native printer glyphs

Unless a `PrinterProfile` contains an exact printer-font model, exact native glyph shapes are not required to match the screen rendering. Tests MUST distinguish:

- **authoritative geometry and style intent**, from
- **representative native glyph appearance**.

A hardware result MUST NOT fail merely because the printer's built-in glyph shape differs from a representative preview font when geometry and semantic styling are otherwise correct.

### 11.3 Raster content

Raster preview is expected to represent the exact finalized prepared 1-bit raster/graphic data, including content read from immutable prepared-artifact storage. Therefore any disagreement in pixel structure between physical-preview data and encoder-visible prepared data is an automated contract failure even before hardware is considered.

On paper, thermal physics may alter darkness, edge sharpness, and apparent dot growth. Hardware comparison SHOULD focus on preserved structure, dimensions, orientation, row continuity, and absence of corruption while separately recording density/thermal behavior.

---

## 12. Encoded-Byte and Protocol Evidence

Hardware tests MAY record protocol bytes, but exact protocol serialization belongs primarily in automated encoder golden tests and the relevant protocol engineering notebook.

For `.tcfg` schema v1, the active family is ESC/POS and the notebook is `ESC_POS_NOTES.md`.

When a hardware test reveals a command-dependent behavior, record:

```text
PreparedPrint operation
selected protocol strategy/dialect
relevant command bytes in hexadecimal, if verified
printer/profile
observed result
run/date/context
related hardware test ID
```

Do not add an exact hexadecimal command to this document merely because it is commonly associated with ESC/POS. It SHOULD be linked from a verified `ESC_POS_NOTES.md` observation or byte-level golden test.

---

## 13. Test Run Result Convention

### 13.1 Result statuses

Each execution of a permanent test MUST use one of:

| Result | Meaning |
|---|---|
| **PASS** | All applicable pass criteria were observed. |
| **FAIL** | One or more applicable pass criteria were violated. |
| **BLOCKED** | The test could not be completed because of an environmental, tooling, or setup issue unrelated to the behavior under test. |
| **N/A** | The selected hardware/profile intentionally lacks a required capability. |
| **NOT RUN** | The test was not executed, normally because the feature is not implemented or the run scope excluded it. |

`COMPLETED` is a print-job lifecycle state, not a hardware-test result.

### 13.2 Required run record

Each executed test MUST record:

```text
Test ID:
Result:
Date/time:
Application version:
Git commit:
Build variant:
Printer model:
Firmware (if known):
Base profile ID/version:
Effective profile overrides:
Transport:
Transport/session settings relevant to the test:
Input fixture + fixture revision/hash where practical:
PrintOptions:
PreparedPrint identity/hash where available:
Expected physical result:
Observed physical result:
Measurements + tolerance source:
Diagnostics/state transitions:
Notes/quirks:
Photo/scan/artifact reference:
Related automated test result/reference:
Related ESC/POS observation ID(s):
Operator:
```

### 13.3 Suggested artifact layout

If the repository stores run artifacts, a convention such as the following is RECOMMENDED:

```text
hardware-results/
  YYYY-MM-DD/
    <printer-profile-id>/
      HW-001/
        result.md
        photo-01.jpg
        prepared-summary.txt
        encoded-summary.txt
```

Large photos/videos MAY be stored externally if repository policy avoids large binary history. In that case `result.md` SHOULD contain a stable reference to the external artifact.

### 13.4 Evidence handling

Evidence SHOULD capture enough context to review the failure later without exposing printable private content. Test fixtures MUST use non-sensitive data.

Logs SHOULD record diagnostic codes, dimensions, operation types, band sizes, byte counts, and state transitions rather than raw document bodies or QR payloads unless the payload is a designated non-sensitive test fixture.

---

## 14. Failure Triage and Documentation Flow

A failed hardware test MUST first be classified rather than patched ad hoc.

Use the following sequence:

```text
hardware failure
    ↓
is the prepared plan wrong?
    ├─ yes → core-layout/core-printer regression + automated test
    └─ no
        ↓
is preview inconsistent with PreparedPrint?
        ├─ yes → core-preview regression + automated test
        └─ no
            ↓
are encoded bytes/artifact reads inconsistent with PreparedPrint?
            ├─ yes → active protocol encoder / prepared-artifact regression + golden test
            └─ no
                ↓
is transport delivery incorrect?
                ├─ yes → platform transport regression + fake/integration test
                └─ no
                    ↓
printer capability / dialect / quirk mismatch
                    ↓
profile correction and/or ESC_POS_NOTES observation
```

A hardware failure SHOULD result in one or more of:

- an automated regression test reproducing the software-side defect;
- an `ESC_POS_NOTES.md` observation describing verified protocol or hardware behavior;
- a `PrinterProfile` or validated override correction;
- a transport-policy correction;
- a preview-contract correction if preview did not represent the prepared result;
- a resource-limit/pacing adjustment supported by evidence.

A failure MUST NOT result in a generic Core conditional such as `if (printerModel == H50i)`—or an equivalent model branch for any printer—merely because the test was performed on one device.

---

# Part I — PRD-Established Permanent Tests

## 15. Phase 9 — Basic Android Bluetooth Text Printing

### HW-001 — Simple ASCII

**Purpose**  
Verify the first end-to-end native-text path from `ThermalDocument` through `PreparedPrint`, physical preview, ESC/POS encoding, Bluetooth transport, and the printer.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing; Phase 6 basic printer preparation; Phase 7 physical preview; Phase 8 ESC/POS encoder.

**Printer/profile**  
Any physical printer/profile declaring reliable native ASCII text. The H50i may be used as the currently available execution device once its development profile has the required validated facts.

**Transport**  
Android Bluetooth Classic for the current primary Android milestone.

**Prerequisites**

- printer is paired/selected;
- effective profile validates successfully;
- ASCII text is representable by the selected native-text strategy;
- byte-level plain-text encoder golden tests pass;
- transport connect/write/disconnect fake tests pass.

**Input/document fixture**  
`test-fixtures/hardware/HW-001-simple-ascii.md` or an equivalent deterministic fixture containing several lines of printable ASCII letters, digits, spaces, and common punctuation. The fixture SHOULD include explicit line breaks and a final line marker.

**Print options**

- text strategy: `Auto` or the production default that resolves to native text;
- profile default native font unless the test matrix states otherwise;
- default alignment: left;
- cut/feed policy recorded explicitly.

**Procedure**

1. Open/compile the fixture.
2. Select the target printer and effective profile.
3. Generate `PreparedPrint` and physical preview.
4. Record the prepared native-text operation count, font, code page, geometry, and final feed/cut operations.
5. Print once through the production Bluetooth transport.
6. Inspect the entire physical output against the fixture and preview.
7. Record the print-job terminal state separately from the physical observation.

**Expected preview**  
All lines appear in order with resolved wrapping, line placement, and alignment. Native glyph appearance MAY be representative, but geometry MUST reflect `PreparedPrint`.

**Expected encoded/behavioral characteristics**

- encoder serializes the already-selected initialization/native-text/style/feed/cut operations;
- no raster-text fallback is introduced by the encoder;
- no text re-wrapping or code-page reselection occurs during encoding;
- transport sends encoded chunks without understanding document semantics.

**Expected physical output**  
All intended ASCII characters appear in the correct order with the expected explicit line breaks. No unexpected characters, duplicated lines, missing lines, or protocol-control garbage are printed.

**Pass criteria**

- physical text content matches the non-sensitive fixture;
- line sequence and explicit breaks match `PreparedPrint`/preview;
- no corruption or unexpected raw-command artifacts are visible;
- final feed/cut behavior matches the prepared operations that are applicable to the profile;
- application state does not claim stronger physical confirmation than the transport/protocol provides.

**Failure observations**  
Record the first mismatching character/line, whether corruption begins after a particular operation, any unexpected feed/cut behavior, terminal state, byte count metadata, and a photo/scan.

**Evidence/artifact recording**  
Required: run record and clear output photo/scan. Recommended: `PreparedPrint` summary/hash and encoded-byte golden reference.

**Related automated tests**  
Plain text Markdown/document golden; basic layout; native-text preparation; code-page selection; plain-text ESC/POS golden; Bluetooth connect/write fake tests.

**Related ESC/POS observation IDs**  
TBD until verified observations are recorded.

**Notes**  
Passing this test on any one printer does not imply that all ESC/POS-compatible printers use the same initialization, font, code-page, raster, QR, barcode, or cut strategy.

---

### HW-002 — Line wrapping

**Purpose**  
Verify that target-constrained wrapping represented in `PreparedPrint` and physical preview is reproduced on paper without the printer or encoder introducing a conflicting wrap interpretation.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing.

**Printer/profile**  
Any profile with sufficiently validated native text metrics. The H50i may be used when its required metrics are established.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- `HW-001` passes on the same setup;
- automated layout goldens for the selected printable width/font metrics pass;
- profile font geometry used by preparation is known well enough to predict line boundaries.

**Input/document fixture**  
`test-fixtures/hardware/HW-002-line-wrapping.md`, containing a paragraph deliberately designed to wrap across several lines under the selected profile. Words near wrap boundaries SHOULD be uniquely identifiable so dropped or duplicated tokens are obvious.

**Print options**  
Native text strategy where reliably supported; default font and left alignment unless the fixture states otherwise.

**Procedure**

1. Prepare the fixture for the selected profile.
2. Record expected physical lines from `PreparedPrint`.
3. Inspect physical preview and confirm the same line boundaries.
4. Print.
5. Compare word/token boundaries on every printed line against the prepared lines.
6. Measure right-edge extent if the profile has a calibrated printable width/tolerance.

**Expected preview**  
Line count and line boundaries are exactly those in the prepared plan.

**Expected encoded/behavioral characteristics**  
Each physical line is already resolved before encoding. The encoder MUST NOT pass an unwrapped paragraph to the printer and rely on firmware wrapping as the authoritative layout algorithm.

**Expected physical output**  
The printed line breaks correspond to the prepared line sequence. Text is neither clipped because of an overestimated line capacity nor prematurely wrapped because of an underestimated geometry model.

**Pass criteria**

- line count matches the prepared plan;
- every token appears exactly once and on the expected line;
- left/right placement is consistent with the resolved geometry within the applicable profile-specific measurement tolerance;
- no extra firmware-generated line break changes the intended layout.

**Failure observations**  
Record the first line boundary disagreement, measured printed width of the affected line, selected font, scaling, code page, profile metrics, and photo/scan.

**Evidence/artifact recording**  
Run record, preview capture, output photo/scan, and measured width for any discrepancy.

**Related automated tests**  
Layout wrapping goldens; profile font-metric tests; `PreparedPrint` text-operation golden; ESC/POS native-text golden.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
If physical wrapping differs while encoded line boundaries are correct, investigate whether the printer adds implicit wrapping, margins, scaling, or font geometry not represented by the profile.

---

### HW-003 — Alignment

**Purpose**  
Verify left, center, and right alignment geometry for native text on physical paper.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing.

**Printer/profile**  
Any profile satisfying the stated capability prerequisites. The H50i may be used where applicable.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- printable width or effective printable region is calibrated sufficiently for alignment comparison;
- automated alignment calculations pass.

**Input/document fixture**  
`test-fixtures/hardware/HW-003-alignment.md` containing three clearly labelled short lines or blocks with left, center, and right alignment using identical or comparable text widths.

**Print options**  
Native text where reliable; same font/scale for all alignment samples.

**Procedure**

1. Prepare and preview the fixture.
2. Record the resolved horizontal geometry for each aligned line/block.
3. Print.
4. Measure left and right offsets relative to the calibrated printable region, not merely the physical paper edge.
5. Compare measured placement with the prepared geometry.

**Expected preview**  
Three distinct placements reflecting left, center, and right alignment, all resolved from `PreparedPrint`.

**Expected encoded/behavioral characteristics**  
The selected alignment behavior is already determined before encoding. Whether serialization uses printer alignment state or explicit geometry is a validated strategy detail; the encoder MUST NOT choose a new alignment policy.

**Expected physical output**  
Left-aligned content begins at the expected printable origin; centered content is visually centered within the effective printable region; right-aligned content terminates at the expected right boundary, subject to profile-specific mechanical tolerance.

**Pass criteria**

- ordering and alignment classes are correct;
- measured placement is inside the calibrated acceptance envelope;
- style/alignment state does not leak from one sample into the next.

**Failure observations**  
Record measured offsets, effective printable width, whether the error is constant or alignment-specific, and output evidence.

**Evidence/artifact recording**  
Run record, annotated photo/scan with ruler/scale when measurement is required, preview capture.

**Related automated tests**  
Alignment unit tests; logical-layout goldens; `PreparedPrint` geometry tests; ESC/POS alignment serialization golden.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
An asymmetric hardware non-printable region SHOULD be captured in calibration/profile data rather than compensated by a model-specific Core conditional.

---

### HW-004 — Bold/underline

**Purpose**  
Verify physical interpretation and state scoping of supported native bold and underline styles.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing; basic style resolution and ESC/POS style serialization.

**Printer/profile**  
Any profile declaring the corresponding native style reliable. For the H50i, record N/A until that style is actually verified rather than assuming support.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- profile explicitly declares tested style capabilities;
- encoder goldens exist for style on/off transitions;
- fixture uses native-representable characters.

**Input/document fixture**  
`test-fixtures/hardware/HW-004-bold-underline.md` containing labelled samples for plain, bold, underline, and bold+underline, followed by a plain sample to detect leaked state.

**Print options**  
Native text. Record font, scale, and any style fallback policy.

**Procedure**

1. Prepare/preview the fixture.
2. Confirm each line's style intent in `PreparedPrint`.
3. Print.
4. Visually compare each labelled sample.
5. Confirm that the final plain sample is not unintentionally bold or underlined.

**Expected preview**  
Style intent is visible while geometry remains authoritative. Native glyph weight/underline shape MAY differ from the representative preview.

**Expected encoded/behavioral characteristics**  
Style state transitions match prepared operations; the encoder resets state where required by the selected protocol strategy.

**Expected physical output**  
Supported bold/underline samples are visibly distinct from plain text, combinations are applied only where intended, and no style leaks into subsequent plain text.

**Pass criteria**

- each profile-declared supported style is physically observed;
- no unexpected style leakage occurs;
- geometry/wrapping remains consistent with the prepared plan.

**Failure observations**  
Record which style failed, whether the command had no effect or the wrong effect, whether state persisted unexpectedly, and a close photo/scan.

**Evidence/artifact recording**  
Run record and close output image showing all style transitions.

**Related automated tests**  
Style-resolution tests; `PreparedPrint` style goldens; ESC/POS bold/underline byte goldens.

**Related ESC/POS observation IDs**  
TBD; create/update an observation for any model-specific style behavior.

**Notes**  
If a style command is unreliable on the tested printer, that profile SHOULD stop advertising it as reliable or preparation SHOULD choose a documented fallback. Do not preserve a false capability simply to satisfy this test.

---

### HW-005 — Long text

**Purpose**  
Verify physical continuity, ordering, and transport/printer behavior for a long native-text job.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing.

**Printer/profile**  
Any profile satisfying the stated capability prerequisites. The H50i may be used as the currently available device where applicable.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- `HW-001` and `HW-002` pass;
- transport chunking and state-transition tests pass;
- long preview can be inspected without requiring a giant bitmap.

**Input/document fixture**  
`test-fixtures/hardware/HW-005-long-text.md`, generated or stored with many uniquely numbered lines/records and periodic section markers. The fixture MUST be long enough to exercise repeated transport writes and printer feeding, but no universal line count is mandated.

**Print options**  
Production defaults for native text; record transport chunk/pacing settings.

**Procedure**

1. Prepare the job and record total prepared operations/lines.
2. Review beginning, middle, and end of the physical preview.
3. Print without intentional interruption.
4. Inspect section markers and line numbering across the entire output.
5. Check for duplicated, missing, reordered, truncated, or corrupted ranges.
6. Record any pauses, buffer stalls, or thermal-density changes as observations.

**Expected preview**  
Complete long output represented lazily/incrementally as needed, with no geometry discontinuity caused by preview virtualization.

**Expected encoded/behavioral characteristics**

- output is streamed/chunked rather than requiring one giant print-job `ByteArray`;
- transport write chunks do not alter document semantics;
- `PreparedPrint` remains unchanged throughout delivery.

**Expected physical output**  
All numbered ranges appear in order through the final marker. Normal hardware pauses are acceptable if no content is lost or duplicated.

**Pass criteria**

- first and final markers are present;
- sequence inspection finds no missing/duplicate/reordered numbered records;
- print job reaches the transport-defined terminal state expected for successful transmission;
- no silent automatic retry occurs.

**Failure observations**  
Record the first and last known-good markers, approximate point of corruption/stall, transport byte counters, diagnostics, connection state, and physical output photo(s).

**Evidence/artifact recording**  
Run record, representative photos of start/middle/end, and complete output scan/photo sequence when diagnosing a failure.

**Related automated tests**  
Long-document layout; bounded preview; streaming encoder; transport chunking; state-machine tests.

**Related ESC/POS observation IDs**  
TBD if printer buffer/pacing behavior is discovered.

**Notes**  
This test observes physical continuity; it does not replace resource-limit or memory-performance tests.

---

### HW-006 — Disconnect during job / recovery procedure

**Purpose**  
Verify conservative application behavior when Bluetooth connectivity is interrupted after a print job has begun and physical output may already have occurred.

**Required phase/feature**  
Phase 9 — Android Bluetooth Printing; print-job lifecycle; partial-transmission reporting; safe retry UX.

**Printer/profile**  
Any supported Bluetooth printer/profile suitable for this transport test. The H50i may serve as the currently available device.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**

- a long deterministic fixture that visibly identifies progress;
- a reliable way to interrupt the Bluetooth connection during active transmission;
- transport fake tests already verify partial-write/error state semantics.

**Input/document fixture**  
Reuse `HW-005-long-text.md` or a dedicated `HW-006-disconnect.md` with numbered progress markers.

**Print options**  
Production defaults; record chunk/pacing policy.

**Procedure**

1. Prepare and preview the long job.
2. Start printing.
3. After visible output has begun, intentionally interrupt the Bluetooth connection using a repeatable method appropriate to the test setup.
4. Observe application state and diagnostics.
5. Confirm whether the printer continues output already buffered data, stops immediately, or behaves otherwise; record this as an observation, not an assumption.
6. Restore connectivity.
7. Confirm that Rastrio does **not** automatically resume or retry the ambiguous job.
8. If validating recovery, explicitly start a new short test print only after the UI communicates the partial/duplicate-output risk.

**Expected preview**  
The preview remains the original complete `PreparedPrint`; connection loss MUST NOT mutate or patch the preview to imply a known physical completion point.

**Expected encoded/behavioral characteristics**

- failure is surfaced with a transport/print-job failure state;
- metadata can indicate partial transmission or `outputMayHaveOccurred` when appropriate;
- no automatic retry of the ambiguous job occurs;
- reconnecting the transport does not silently replay the previous job.

**Expected physical output**  
The sheet MAY contain an arbitrary prefix or buffered continuation of the intended output. The exact physical stopping point is not a pass criterion unless reliable device feedback exists. Partial paper output is expected to be possible.

**Pass criteria**

- interruption is detected and surfaced;
- Rastrio does not claim verified physical success;
- no automatic retry/resume creates duplicate output;
- recovery requires an explicit user-controlled action for a subsequent print;
- observed buffered-printer behavior is recorded.

**Failure observations**  
Record bytes attempted/transmitted where available, failure stage, visible last marker, any post-disconnect buffered continuation, application state transitions, and whether duplicate output occurred.

**Evidence/artifact recording**  
Run record, screenshot of failure/retry UX, physical output photo, transport diagnostic excerpt with sensitive identifiers redacted.

**Related automated tests**  
Partial transmission; connection loss; no-auto-retry invariant; lifecycle transitions; reconnect tests with fakes.

**Related ESC/POS observation IDs**  
Only if printer buffering or command-state behavior is discovered; otherwise transport behavior belongs outside ESC/POS notes.

**Notes**  
This is the canonical PRD-established Bluetooth interruption test. `HW-024` later focuses more narrowly on partial-transmission accounting/evidence.

---

## 16. Phase 10 — Raster Engine and Image Printing

### HW-007 — Checkerboard

**Purpose**  
Verify raster orientation, black/white polarity, horizontal/vertical alignment, row packing interpretation, and gross scaling using an unambiguous checkerboard pattern.

**Required phase/feature**  
Phase 10 — Raster Engine and Image Printing.

**Printer/profile**  
Any profile with a validated raster strategy. The H50i may be used once its raster strategy is independently verified.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- deterministic checkerboard raster golden passes;
- raster bit-packing, padding-bit, and band-splitting tests pass;
- profile raster strategy validates.

**Input/document fixture**  
`test-fixtures/hardware/HW-007-checkerboard.png` or deterministic generated equivalent with cells large enough to inspect physically and dimensions that exercise both axes.

**Print options**  
No unintended resampling after preparation; record scaling, density, and raster strategy.

**Procedure**

1. Prepare the image and inspect exact 1-bit preview.
2. Record prepared raster width/height and band layout.
3. Print.
4. Inspect polarity, orientation, cell sequence, edges, and band boundaries.

**Expected preview**  
Exact checkerboard pixels from the prepared 1-bit raster.

**Expected encoded/behavioral characteristics**  
Prepared raster bands are serialized without re-dithering, inversion, or resizing.

**Expected physical output**  
Alternating black/white cells appear in the correct orientation without systematic horizontal bit shifts, inverted polarity, missing rows, repeated rows, or band seams.

**Pass criteria**

- pattern orientation and polarity match preview;
- no row/column corruption is visible;
- dimensions are within the applicable profile-specific measurement envelope;
- band boundaries are not visibly displaced or duplicated.

**Failure observations**  
Record whether failure suggests bit order, polarity, row-byte width, padding, band header, scaling, or printer feed behavior.

**Evidence/artifact recording**  
Full output photo/scan plus close-up of any corruption.

**Related automated tests**  
Checkerboard raster golden; bit packing; padding bits; raster band serialization.

**Related ESC/POS observation IDs**  
TBD for verified raster-command behavior.

**Notes**  
Thermal dot spread may soften cell edges and is not by itself evidence of a raster-data mismatch.

---

### HW-008 — Threshold gradient

**Purpose**  
Verify physical output of deterministic threshold conversion over a grayscale gradient and confirm that preview uses the exact prepared raster.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a validated raster capability.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**  
Threshold algorithm golden for the same fixture/options passes.

**Input/document fixture**  
`test-fixtures/hardware/HW-008-gradient.png`, a controlled grayscale gradient with reference bands/labels outside the processed region if useful.

**Print options**  
Threshold algorithm; record threshold level, brightness, contrast, gamma, density, and scaling.

**Procedure**

1. Prepare with threshold dithering.
2. Capture exact raster preview.
3. Print.
4. Compare the location and shape of the black/white transition with preview.
5. Record thermal-density effects separately.

**Expected preview**  
Exact deterministic threshold raster.

**Expected encoded/behavioral characteristics**  
No re-thresholding during encoding. Raster bands preserve prepared rows exactly.

**Expected physical output**  
A monotonic gradient transition corresponding structurally to the prepared raster, without unrelated repeating artifacts, row loss, or band discontinuities.

**Pass criteria**  
Structural black/white pattern and transition direction match preview; no encoding/banding corruption is visible.

**Failure observations**  
Record transition displacement, density setting, any band boundary artifact, and whether the encoded raster golden still matches `PreparedPrint`.

**Evidence/artifact recording**  
Photo/scan and preview capture.

**Related automated tests**  
Threshold gradient golden; scaling; band split; ESC/POS raster golden.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
The exact optical darkness of black areas is printer/media dependent and is not expected to equal a screen preview.

---

### HW-009 — Bayer gradient

**Purpose**  
Verify ordered Bayer dithering survives physical encoding/printing without matrix corruption or raster-band discontinuity.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a validated raster capability.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**  
Bayer deterministic golden passes.

**Input/document fixture**  
Reuse the controlled gradient fixture used for raster algorithm comparison.

**Print options**  
Bayer ordered dithering with all tonal options recorded.

**Procedure**  
Prepare, inspect exact raster preview, print, and examine ordered pattern consistency across the full width and across raster bands.

**Expected preview**  
Exact prepared Bayer matrix pattern.

**Expected encoded/behavioral characteristics**  
No re-dithering or row phase reset during encoding/banding.

**Expected physical output**  
Ordered-dot texture changes progressively with tone and remains spatially consistent; no band boundary resets, unexpected stripes, or missing rows appear.

**Pass criteria**  
Pattern structure and continuity match preview at inspection scale; physical darkness differences are documented but do not mask structural correctness.

**Failure observations**  
Record repeated patterns, phase shifts, band seams, orientation errors, or scaling changes.

**Evidence/artifact recording**  
Full photo/scan and close-up of ordered pattern.

**Related automated tests**  
Bayer gradient golden; row continuity; band splitting; encoder raster golden.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
Printer dot gain can change apparent tone without changing the underlying ordered pattern.

---

### HW-010 — Atkinson gradient

**Purpose**  
Verify deterministic Atkinson error-diffusion output on physical hardware and continuity across prepared raster bands.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a validated raster capability.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**  
Atkinson golden and band-continuity tests pass.

**Input/document fixture**  
Controlled gradient fixture shared with raster algorithm comparisons.

**Print options**  
Atkinson dithering; all tonal settings recorded.

**Procedure**  
Prepare, inspect exact raster preview, print, then inspect tone progression and band boundaries.

**Expected preview**  
Exact prepared Atkinson 1-bit raster.

**Expected encoded/behavioral characteristics**  
Error-diffusion result is finalized before encoding; encoder never reruns the algorithm per band.

**Expected physical output**  
The expected error-diffusion texture is continuous across the image. Band transitions do not reset or visibly alter the dither state because the prepared raster is already final.

**Pass criteria**  
No band seam, missing/repeated rows, inversion, or unexpected resampling is visible; broad tone ordering follows the preview.

**Failure observations**  
Record seam locations, row corruption, density effects, and prepared band boundaries.

**Evidence/artifact recording**  
Photo/scan and preview capture.

**Related automated tests**  
Atkinson gradient golden; band splitting; row continuity; raster encoding.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
Algorithm correctness is established by golden tests; this test verifies the printer receives/interprets the prepared raster coherently.

---

### HW-011 — Floyd–Steinberg gradient

**Purpose**  
Verify deterministic Floyd–Steinberg error-diffusion output on physical hardware and continuity across prepared raster bands.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a validated raster capability.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**  
Floyd–Steinberg golden and band-continuity tests pass.

**Input/document fixture**  
Controlled gradient fixture shared with raster algorithm comparisons.

**Print options**  
Floyd–Steinberg dithering; tonal settings recorded.

**Procedure**  
Prepare, preview, print, and inspect the entire gradient including every prepared band boundary.

**Expected preview**  
Exact prepared Floyd–Steinberg raster.

**Expected encoded/behavioral characteristics**  
No per-band re-dithering; row data is serialized from the finalized prepared raster.

**Expected physical output**  
Continuous error-diffusion texture with no row loss, repeat, inversion, or visible algorithm reset at raster-band boundaries.

**Pass criteria**  
Structural output matches preview and band continuity is preserved.

**Failure observations**  
Record boundary positions, corruption type, raster strategy, and thermal-density settings.

**Evidence/artifact recording**  
Photo/scan and preview capture.

**Related automated tests**  
Floyd–Steinberg gradient golden; row continuity; band splitting; raster encoder golden.

**Related ESC/POS observation IDs**  
TBD.

**Notes**  
Optical tone is hardware/media dependent; structural continuity is the primary criterion.

---

### HW-012 — Full-width image

**Purpose**  
Verify use of the profile's effective printable raster width, edge handling, alignment, and absence of unexpected clipping or horizontal offset.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a calibrated/validated printable width.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**

- effective printable width is known from profile/calibration;
- full-width raster golden passes;
- fixture contains boundary markers that make clipping visible.

**Input/document fixture**  
`test-fixtures/hardware/HW-012-full-width.png` or deterministic generated image containing visible left/right border markers, center marker, and interior reference geometry.

**Print options**  
Scale to effective printable width using the production scaling strategy; record density and margins/alignment.

**Procedure**

1. Prepare the image and record raster width in dots.
2. Confirm preview shows the exact final raster positioned at the expected printable region.
3. Print.
4. Inspect both horizontal edges for clipping or unused bands.
5. Measure physical raster width using the protocol in Section 10.

**Expected preview**  
Raster spans the resolved printable width exactly as represented by `PreparedPrint`.

**Expected encoded/behavioral characteristics**  
Encoder uses the prepared raster dimensions without resizing. Row-byte width matches automated bit-packing tests.

**Expected physical output**  
Both boundary markers are present as expected, the image is not horizontally shifted, and measured width agrees with the profile-specific calibrated envelope.

**Pass criteria**  
No unexpected clipping; no unintended extra margin introduced by software/firmware; measured width falls within the applicable calibrated tolerance.

**Failure observations**  
Record which edge clips, measured width, prepared dot width, alignment offset, and any suspected hardware margin.

**Evidence/artifact recording**  
Annotated photo/scan with a scale reference.

**Related automated tests**  
Full-width raster golden; scaling; dot conversion; row packing; printer-preparation geometry.

**Related ESC/POS observation IDs**  
TBD if the raster command has model-specific width limitations.

**Notes**  
Repeated edge clipping SHOULD lead to profile calibration/capability correction, not hard-coded H50i width in generic Core.

---

### HW-013 — Long image

**Purpose**  
Verify long raster output, prepared band continuity, printer-buffer behavior, and absence of missing/duplicated rows across many raster bands.

**Required phase/feature**  
Phase 10.

**Printer/profile**  
Any profile with a validated raster capability.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**

- bounded-memory raster preparation exists;
- band-splitting and row-continuity automated tests pass;
- fixture contains periodic row/section markers.

**Input/document fixture**  
`test-fixtures/hardware/HW-013-long-image.png`, intentionally tall with repeated uniquely labelled horizontal markers/patterns.

**Print options**  
Production raster settings and profile-recommended band size; transport pacing recorded.

**Procedure**

1. Prepare and record total raster height and band boundaries.
2. Inspect preview at beginning, selected middle boundaries, and end.
3. Print.
4. Inspect every visible marker sequence and each band boundary region.
5. Record stalls or density changes separately from row-data correctness.

**Expected preview**  
Continuous exact raster data independent of preview virtualization.

**Expected encoded/behavioral characteristics**

- raster is serialized in prepared bands;
- no giant print-job-wide raster byte array is required by the encoder;
- transport MAY further chunk encoded bytes without changing the band plan.

**Expected physical output**  
All marker rows appear once, in order, with no gaps/repeats at band boundaries and no unexpected truncation before the final marker.

**Pass criteria**  
Complete marker sequence, correct orientation, no visible row discontinuity attributable to band handling, and successful application-level transmission state.

**Failure observations**  
Record failing marker/band index, transport settings, band height, byte counters, any printer pause, and output photo.

**Evidence/artifact recording**  
Photos of start/end and every suspected seam; full scan if practical.

**Related automated tests**  
Long-raster resource tests; band split; row continuity; encoded streaming; transport chunking.

**Related ESC/POS observation IDs**  
TBD for verified buffer/pacing/raster limitations.

**Notes**  
If output fails only above a certain band or write pattern, investigate printer buffer guidance separately from transport chunk-size limits.

---

## 17. Phase 11 — Unicode and Raster Text Fallback

### HW-014 — Unicode raster text

**Purpose**  
Verify that text not reliably representable by the selected printer's native code pages is rendered as prepared raster text and physically matches the exact raster preview.

**Required phase/feature**  
Phase 11 — Unicode and Raster Text Fallback.

**Printer/profile**  
Any profile with documented native text coverage sufficient for the fallback case.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**

- controlled text rasterizer/font resources are configured;
- glyph coverage detection tests pass;
- the chosen fixture contains code points explicitly outside the effective profile's reliable native coverage;
- raster-text golden passes.

**Input/document fixture**  
`test-fixtures/hardware/HW-014-unicode-raster-text.md`, containing a controlled Unicode sample selected to force raster fallback under the tested profile. The fixture MUST document why the sample is outside native coverage.

**Print options**  
`Auto` text strategy unless the test intentionally forces raster; font/rasterizer revision recorded.

**Procedure**

1. Prepare the fixture.
2. Confirm the intended line resolves to `RasterText` rather than native text.
3. Inspect exact physical preview.
4. Print.
5. Compare glyph sequence, raster dimensions, and line placement with preview.

**Expected preview**  
Exact final raster text for the fallback line.

**Expected encoded/behavioral characteristics**  
The prepared raster text is encoded as raster data. The encoder does not perform shaping, font fallback, or code-page selection.

**Expected physical output**  
Unicode glyphs are present, in order, and visibly correspond to the prepared raster. No replacement characters, dropped glyphs, or raw byte mojibake appear.

**Pass criteria**

- fallback strategy matches `PreparedPrint`;
- all intended glyphs are physically present;
- line geometry matches the prepared raster placement within applicable measurement tolerance;
- no silent native-text corruption occurs.

**Failure observations**  
Record whether failure originates in shaping/rasterization, prepared geometry, encoding, or physical raster interpretation.

**Evidence/artifact recording**  
Preview capture and close physical photo/scan.

**Related automated tests**  
Coverage detection; raster-text shaping/rendering golden; fallback strategy; exact raster preview; raster encoder golden.

**Related ESC/POS observation IDs**  
Usually none unless printer raster behavior is involved.

**Notes**  
Changing the fixture because a future profile gains native coverage is acceptable if required to preserve the purpose of testing fallback; record the fixture revision.

---

### HW-015 — Mixed supported/unsupported text

**Purpose**  
Verify the initial whole-line fallback rule when a physical line contains both natively supported and unsupported text.

**Required phase/feature**  
Phase 11.

**Printer/profile**  
Any profile whose native coverage is known well enough to exercise the mixed native/raster fallback rule. The H50i may be used once that coverage is validated.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**  
A fixture line containing both known-native and known-unsupported content under the selected profile; whole-line fallback automated test passes.

**Input/document fixture**  
`test-fixtures/hardware/HW-015-mixed-text.md` with one or more lines combining profile-native ASCII/content and code points outside reliable native coverage.

**Print options**  
`Auto` text strategy.

**Procedure**

1. Prepare the fixture.
2. Confirm each affected physical line is represented as one raster-text line rather than per-glyph native/raster mixing.
3. Inspect preview.
4. Print.
5. Check consistent baseline/spacing within the mixed line and correct wrap behavior.

**Expected preview**  
The complete affected line is represented by its final prepared raster.

**Expected encoded/behavioral characteristics**  
No mixed native/raster glyph emission within the same fallback line for the initial implementation. Encoding simply emits the prepared raster operation.

**Expected physical output**  
Native-capable and unsupported characters on the line share the same raster-rendered appearance, with no dropped unsupported characters and no mid-line printer-font switch artifact.

**Pass criteria**

- whole-line fallback occurs as planned;
- all text is present and correctly ordered;
- wrapping does not change after `PreparedPrint` is finalized;
- physical line placement matches preview.

**Failure observations**  
Record unexpected native/raster mixing, wrap changes, missing glyphs, baseline jumps, or duplicate text.

**Evidence/artifact recording**  
Prepared operation summary, preview, close output photo/scan.

**Related automated tests**  
Mixed-coverage strategy; fallback-induced re-layout; line geometry; raster-text golden.

**Related ESC/POS observation IDs**  
None unless physical raster behavior reveals a printer quirk.

**Notes**  
Per-glyph native/raster mixing is deferred and MUST NOT be inferred from this test.

---

### HW-016 — Complex-script sample

**Purpose**  
Verify that a shaped complex-script sample can be prepared as raster text and printed without code-point-by-code-point corruption.

**Required phase/feature**  
Phase 11 — complex-script shaping/rasterization support sufficient for the selected fixture.

**Printer/profile**  
Any profile able to accept the prepared raster-text output; native text capability is not required for the raster portion.

**Transport**  
Android Bluetooth Classic.

**Prerequisites**

- controlled font resource and shaping/rasterizer version;
- deterministic shaped/raster golden for the exact fixture;
- selected script sample is reviewed and stable.

**Input/document fixture**  
`test-fixtures/hardware/HW-016-complex-script.md`, containing at least one controlled complex-script sample that requires shaping. The repository fixture SHOULD identify the script/language and the expected logical text independently of the bitmap golden.

**Print options**  
Raster fallback/`Auto`; font/rasterizer revision recorded.

**Procedure**

1. Prepare the sample.
2. Confirm shaping occurred before final `PreparedPrint` creation.
3. Inspect exact raster preview.
4. Print.
5. Compare physical glyph clusters and ordering with the raster preview/golden.

**Expected preview**  
Exact shaped raster text generated during preparation.

**Expected encoded/behavioral characteristics**  
No code-point-to-printer-cell assumption and no shaping during ESC/POS encoding.

**Expected physical output**  
Printed clusters visually match the prepared raster structure; no isolated combining marks, reordered components, replacement glyphs, or native-printer mojibake appear.

**Pass criteria**  
Physical raster preserves the shaped glyph/cluster structure shown in preview and all intended content is present.

**Failure observations**  
Record whether the preview itself is wrong (text/shaping defect) or only the paper differs (raster/encoding/hardware defect).

**Evidence/artifact recording**  
Preview and close physical scan/photo sufficient to inspect shaping.

**Related automated tests**  
Complex-script shaping; font resource determinism; raster-text golden; preview exactness; raster encoder golden.

**Related ESC/POS observation IDs**  
Normally none unless raster transmission behavior is implicated.

**Notes**  
Hardware PASS only establishes faithful printing of the prepared raster. Linguistic/shaping correctness is primarily validated by the controlled text-rendering tests and fixture review.

---

## 18. Phase 13 — QR Capability Resolution

### HW-017 — Native QR

**Purpose**  
Verify a profile-declared reliable native QR strategy on physical hardware, including payload fidelity, placement, size, and scanability.

**Required phase/feature**  
Phase 13 — QR Capability Resolution.

**Printer/profile**  
Any physical printer/profile that explicitly declares the selected native QR configuration reliable. The H50i is N/A until its host-accessible native QR command path is independently verified.

**Transport**  
Any implemented transport for the selected qualifying printer; Android Bluetooth Classic preferred for the initial Android implementation.

**Prerequisites**

- profile validates native QR strategy;
- native QR preparation and encoder goldens pass;
- offline QR decoder/scanner available for verification.

**Input/document fixture**  
`test-fixtures/hardware/HW-017-native-qr.td` or deterministic QR document using a non-sensitive payload such as `https://example.invalid/rastrio/hw-017`.

**Print options**  
QR strategy forced to `Native`; error correction and requested logical size recorded.

**Procedure**

1. Prepare with forced native QR.
2. Confirm `PreparedPrint` contains `NativeQr` with resolved dimensions/parameters.
3. Inspect physical preview placement and size annotation/representation.
4. Print.
5. Scan the printed code using an offline decoder.
6. Compare decoded payload exactly with the fixture payload.
7. Measure physical QR bounding box if the profile/test defines a calibrated size tolerance.

**Expected preview**  
Placement and resolved QR dimensions match `PreparedPrint`. Exact printer-native module rendering MAY be representative unless an exact model is available.

**Expected encoded/behavioral characteristics**  
Native QR command strategy is already selected before encoding; encoder does not substitute raster QR or change error correction/size.

**Expected physical output**  
One QR code at the prepared location, with the intended size class/geometry and exact decoded payload.

**Pass criteria**

- native strategy actually produces a QR code;
- offline scan returns the exact payload;
- placement matches preview;
- physical size is within the applicable profile-specific tolerance if one is established;
- no duplicate QR or stray payload text is emitted unless explicitly part of the fixture.

**Failure observations**  
Record scan result/error, measured size, selected native parameters, command strategy, and whether the printer ignored or transformed any setting.

**Evidence/artifact recording**  
Output photo/scan, decoder result, prepared native-QR summary.

**Related automated tests**  
Native-capable profile selection; forced-native strategy; payload validation; preview dimensions; ESC/POS native QR golden.

**Related ESC/POS observation IDs**  
Required once native QR command behavior is verified on a model.

**Notes**  
A native QR failure SHOULD normally cause profile capability/strategy correction unless encoder bytes are wrong.

---

### HW-018 — Raster QR

**Purpose**  
Verify QR raster fallback uses the exact prepared bitmap and remains physically scanable.

**Required phase/feature**  
Phase 13 plus raster engine.

**Printer/profile**  
Any raster-capable profile, including the H50i only after its raster strategy is validated.

**Transport**  
Android Bluetooth Classic for the current primary Android execution path.

**Prerequisites**

- raster QR generation golden passes;
- raster transmission hardware tests pass;
- offline QR decoder available.

**Input/document fixture**  
`test-fixtures/hardware/HW-018-raster-qr.td` using a stable non-sensitive payload such as `https://example.invalid/rastrio/hw-018`.

**Print options**  
QR strategy forced to `Raster`; error correction and size recorded.

**Procedure**

1. Prepare with forced raster QR.
2. Confirm `PreparedPrint` contains `RasterQr` or equivalent final raster operation.
3. Inspect exact raster preview.
4. Print.
5. Scan offline and compare payload.
6. Inspect quiet zone and gross module integrity.

**Expected preview**  
Exact final QR raster.

**Expected encoded/behavioral characteristics**  
Encoder serializes prepared raster bytes and does not regenerate the QR symbol.

**Expected physical output**  
QR structure corresponds to preview and decodes to the exact payload.

**Pass criteria**

- exact payload decodes successfully;
- placement and raster dimensions match prepared geometry within applicable tolerance;
- no clipping of required quiet zone occurs;
- no row/band corruption makes the symbol structurally inconsistent.

**Failure observations**  
Record decoder result, raster size, clipping, density setting, and whether preview/encoded data were correct.

**Evidence/artifact recording**  
Close scan/photo and decoder result.

**Related automated tests**  
Raster QR generation; forced-raster strategy; preview exactness; raster encoder golden.

**Related ESC/POS observation IDs**  
Only for raster transport/command quirks.

**Notes**  
Scanability can depend on media, density, scaling, and camera. A structural mismatch from preview is a stronger failure signal than one scanner's difficulty alone.

---

### HW-019 — QR size variations

**Purpose**  
Verify that multiple valid QR sizes resolved by Rastrio produce the intended physical size ordering/geometry and remain usable on the tested printer.

**Required phase/feature**  
Phase 13.

**Printer/profile**  
Any profile supporting the QR strategy under test. Native and raster variants MAY both be executed when available.

**Transport**  
Implemented transport for the selected printer.

**Prerequisites**

- size validation/range tests pass;
- selected sizes are within the validated range for the strategy/profile.

**Input/document fixture**  
`test-fixtures/hardware/HW-019-qr-sizes.td` or deterministic generator producing at least three distinct valid requested/resolved sizes (for example smaller, default/middle, and larger values selected from the profile/strategy's valid range rather than universal hard-coded printer limits).

**Print options**  
Record strategy (`Native`, `Raster`, or both variants), error correction, and each requested/resolved size.

**Procedure**

1. Prepare all size variants.
2. Confirm preview shows distinct resolved sizes and correct placement.
3. Print all variants with clear labels outside QR quiet zones.
4. Measure bounding boxes according to Section 10.
5. Scan each code offline.

**Expected preview**  
Resolved QR geometry for each variant is represented without the UI inventing alternative sizing.

**Expected encoded/behavioral characteristics**  
Each QR operation carries finalized dimensions/parameters; the encoder cannot choose a new size.

**Expected physical output**  
QR codes increase/decrease in physical size according to prepared geometry, remain unclipped, and decode to their expected payloads when the chosen size is viable for the tested printer/media.

**Pass criteria**

- physical size ordering matches `PreparedPrint`;
- measured geometry falls within profile/test-specific tolerance if defined;
- each expected viable sample decodes correctly;
- no sample is silently coerced to an undocumented size.

**Failure observations**  
Record requested vs resolved vs measured size, scanability, strategy, and any apparent printer-side clamping.

**Evidence/artifact recording**  
One image showing all variants with scale reference plus decoder results.

**Related automated tests**  
QR size validation; native/raster strategy; preview dimensions; command/raster goldens.

**Related ESC/POS observation IDs**  
TBD; particularly useful if native firmware clamps module size.

**Notes**  
Do not infer a universal minimum/maximum QR size from one printer. Profile capability data MUST remain model/strategy specific.

---

## 19. Phase 17 — Wide-Document Landscape Tiling

The tests in this section apply only when wide-document landscape tiling is implemented or promoted to a release gate.

### HW-020 — Two-strip table

**Purpose**  
Verify that one wide logical table is segmented into exactly two physical strips while preserving row geometry, column alignment, and intended overlap.

**Required phase/feature**  
Phase 17 — Wide-Document Landscape Tiling.

**Printer/profile**  
Any profile for which wide-document tiling is implemented and validated.

**Transport**  
Any implemented transport.

**Prerequisites**

- segmentation property/golden tests pass;
- fixture/print options are chosen so the selected profile resolves exactly two `PrintSegment`s;
- overlap and registration options are recorded.

**Input/document fixture**  
`test-fixtures/hardware/HW-020-two-strip-table.md` or deterministic wide-table generator with unique cell labels crossing the segment boundary.

**Print options**  
Landscape/wide logical layout; overlap recorded; registration marks/segment numbers recorded; cut policy recorded.

**Procedure**

1. Prepare and confirm exactly two segments.
2. Inspect assembled logical preview and physical-strip preview.
3. Print both strips.
4. Verify segment numbering if enabled.
5. Align strips using the intended overlap/registration method.
6. Compare row and column continuation across the join.

**Expected preview**  
Assembled view shows one continuous logical table; physical view shows two strips with the exact resolved overlap and marks.

**Expected encoded/behavioral characteristics**  
Segments are already resolved in `PreparedPrint`; encoder does not re-slice the table or change overlap.

**Expected physical output**  
Two strips contain complete intended coverage with only deliberate overlap duplication. Table rows remain horizontally consistent after assembly.

**Pass criteria**

- exactly two strips are printed;
- no logical content is lost;
- no content is duplicated outside the intended overlap;
- row/column geometry can be reconstructed within the applicable assembly/alignment tolerance.

**Failure observations**  
Record segment dimensions, overlap, first lost/duplicated coordinate/cell, registration offset, and photos before/after assembly.

**Evidence/artifact recording**  
Photos of separate strips and assembled result, with ruler/scale if measuring overlap.

**Related automated tests**  
Two-segment planning; coverage invariant; overlap; table row alignment; segment numbering; preview/segment contract.

**Related ESC/POS observation IDs**  
Usually none unless printer feed/cut behavior affects segmentation.

**Notes**  
The table is laid out once before physical segmentation. Independently laying out each strip would invalidate this test.

---

### HW-021 — Three-strip table

**Purpose**  
Verify wide logical geometry across two internal joins by printing one table as exactly three physical strips.

**Required phase/feature**  
Phase 17.

**Printer/profile**  
Any profile satisfying the test's capability prerequisites.

**Transport**  
Any implemented transport.

**Prerequisites**  
Same as `HW-020`, with fixture/options resolving exactly three segments.

**Input/document fixture**  
`test-fixtures/hardware/HW-021-three-strip-table.md`, with unique labels spanning both segmentation boundaries.

**Print options**  
Landscape segmentation; overlap, marks, segment numbers, and cut policy recorded.

**Procedure**

1. Confirm exactly three prepared segments.
2. Inspect assembled and physical previews.
3. Print all segments.
4. Reconstruct in order.
5. Inspect both joins and full-row continuity.

**Expected preview**  
One continuous logical table and three exact physical segment representations.

**Expected encoded/behavioral characteristics**  
No semantic table re-layout after segmentation decisions are finalized.

**Expected physical output**  
Three ordered strips reconstruct the intended table with only the configured overlap duplication.

**Pass criteria**  
All content is present once outside overlap, both joins align within the applicable profile/assembly envelope, and segment ordering is unambiguous.

**Failure observations**  
Record which join failed, measured overlap/alignment, segment IDs, and affected cells/coordinates.

**Evidence/artifact recording**  
Separate and assembled photographs/scans.

**Related automated tests**  
Three-segment planning; complete coverage property; overlap and no-unintended-duplication invariants.

**Related ESC/POS observation IDs**  
TBD if relevant.

**Notes**  
A failure that grows from strip 1→2→3 may indicate cumulative feed/skew or assembly error rather than horizontal segmentation math; record both possibilities.

---

### HW-022 — Registration marks

**Purpose**  
Verify that prepared registration marks are physically printed at useful, consistent positions for multi-strip assembly.

**Required phase/feature**  
Phase 17 registration marks.

**Printer/profile**  
Any profile used for wide-document tiling.

**Transport**  
Any implemented transport.

**Prerequisites**

- registration marks enabled;
- mark geometry tested automatically;
- at least two physical segments.

**Input/document fixture**  
A deterministic tiling fixture with simple background/content around mark regions so marks are easy to inspect.

**Print options**  
Registration marks enabled; overlap and segment numbering recorded.

**Procedure**

1. Prepare and inspect mark positions on each physical preview strip.
2. Print.
3. Measure mark locations relative to strip edges/content anchors.
4. Overlay/align adjacent strips using marks.
5. Record horizontal/vertical offset and skew.

**Expected preview**  
Marks appear as prepared printable content/operations at defined segment positions.

**Expected encoded/behavioral characteristics**  
Mark placement is finalized in `PreparedPrint`; encoder serializes it without repositioning.

**Expected physical output**  
Marks are visible, not clipped, and sufficiently consistent to serve as assembly references for the tested printer/profile.

**Pass criteria**  
All required marks are present, correspond to preview positions, and align within the test/profile's established registration tolerance.

**Failure observations**  
Record missing/clipped marks, x/y offsets, skew, and whether error is constant or increases down-feed.

**Evidence/artifact recording**  
Annotated photo/scan with measured mark offsets.

**Related automated tests**  
Registration-mark geometry; segmentation coverage; physical-preview exact placement.

**Related ESC/POS observation IDs**  
Normally none.

**Notes**  
Do not define a universal registration tolerance here. Establish it through printer/profile calibration and repeated hardware evidence.

---

### HW-023 — Physical assembly

**Purpose**  
Verify that the complete multi-strip workflow produces paper strips that a user can physically assemble into the intended wide document with consistent geometry.

**Required phase/feature**  
Phase 17.

**Printer/profile**  
Any profile supporting the wide-document workflow under test.

**Transport**  
Any implemented transport.

**Prerequisites**

- `HW-020` or `HW-021` passes;
- registration/join guidance implemented as claimed;
- safe cut procedure available.

**Input/document fixture**  
A wide document containing horizontal and vertical continuity cues, table borders, text, and/or images that make assembly errors visible.

**Print options**  
Record logical width, segment count, overlap, registration marks, segment numbering, trim/join guides, and cut behavior.

**Procedure**

1. Prepare and inspect assembled + physical-strip previews.
2. Print every segment.
3. Perform any required manual cuts using the intended guidance or allow supported automatic cuts.
4. Assemble the strips on a flat surface using documented overlap/registration guidance.
5. Inspect the reconstructed document end-to-end.
6. Measure representative joins and registration offsets.

**Expected preview**  
Assembled preview shows continuous logical geometry; physical-strip preview shows the exact material to be cut/aligned.

**Expected encoded/behavioral characteristics**  
Segment content, overlap, marks, numbering, and cut operations are all present in `PreparedPrint` before encoding.

**Expected physical output**  
The assembled paper reconstructs the intended logical document without missing regions, accidental duplicate regions outside overlap, or major row/column discontinuity.

**Pass criteria**

- every segment is present and correctly ordered;
- assembly can be performed using the provided guidance;
- reconstructed content is complete;
- join alignment is within the established profile/test tolerance;
- manual/automatic cut behavior matches the prepared plan.

**Failure observations**  
Record assembly method, per-join offsets, cut errors, feed skew, lost/duplicated content, and whether the source is physical variability or preparation geometry.

**Evidence/artifact recording**  
Photos of individual strips, assembly process if useful, and final assembled document with scale reference.

**Related automated tests**  
Segmentation reconstruction property; overlap/no-loss invariants; cut decisions; registration marks; assembled/physical preview consistency.

**Related ESC/POS observation IDs**  
TBD for any cut/feed quirks.

**Notes**  
This is a user-workflow hardware test, not proof that adhesive, paper aging, or long-term assembly durability meets any external standard.

---

# Part II — Additional Permanent / Future-Gated Tests

## 20. Transport Resilience and Long-Job Tests

### HW-024 — Partial print transmission accounting

**Purpose**  
Verify that a real partial hardware transmission is surfaced conservatively and that reported transport metadata is consistent enough to prevent unsafe automatic retry.

**Required phase/feature**  
Print-job partial-transmission metadata implemented; suitable diagnostics available. Future-gated if current transport cannot expose meaningful counters.

**Printer/profile**  
Any supported profile suitable for this resilience test. The H50i may be used as the currently available device.

**Transport**  
Android Bluetooth Classic initially.

**Prerequisites**  
A repeatable failure-injection method that can interrupt after some writes have succeeded; automated partial-write tests pass.

**Input/document fixture**  
Long numbered text or raster fixture with visible progress markers.

**Print options**  
Production defaults; record chunk/pacing configuration.

**Procedure**

1. Start the job.
2. Interrupt after transmission has begun.
3. Record `bytesAttempted`, `bytesTransmitted`, failure stage, and `outputMayHaveOccurred` or equivalent fields where implemented.
4. Inspect physical output.
5. Verify no automatic retry.

**Expected preview**  
Unchanged complete plan; preview does not pretend to know the final physical stop point.

**Expected encoded/behavioral characteristics**  
Partial write is distinguishable from a failure before any possible output; ambiguous physical output is conservatively represented.

**Expected physical output**  
An arbitrary partial result MAY exist. Its exact length is observational unless the printer provides reliable acknowledged progress.

**Pass criteria**  
State/metadata are conservative, internally consistent, and sufficient to block unsafe automatic retry.

**Failure observations**  
Record counters, visible last marker, timing of interruption, and any mismatch between transport reporting and actual evidence.

**Evidence/artifact recording**  
Run record, failure UI screenshot, partial output photo.

**Related automated tests**  
Partial write accounting; failure stages; retry safety invariant.

**Related ESC/POS observation IDs**  
Usually none.

**Notes**  
This complements `HW-006`; it does not replace it.

---

### HW-025 — Cancellation during active job

**Purpose**  
Verify user cancellation semantics when a physical job has already started.

**Required phase/feature**  
User-visible print cancellation implemented.

**Printer/profile**  
Any supported profile suitable for this resilience test. The H50i may be used as the currently available device.

**Transport**  
Android Bluetooth Classic initially; later repeat by transport.

**Prerequisites**  
Cancellation automated tests pass; long fixture available.

**Input/document fixture**  
Long numbered fixture with clear progress markers.

**Print options**  
Production defaults.

**Procedure**

1. Start printing.
2. After visible output begins, invoke cancellation.
3. Observe when new writes stop.
4. Observe any printer output that continues from already-buffered data.
5. Confirm terminal application state and retry UX.

**Expected preview**  
Original full prepared preview remains valid as the intended plan; it does not claim the physically printed prefix is known exactly.

**Expected encoded/behavioral characteristics**  
Cancellation stops further transport work as soon as the transport contract permits; state becomes `CANCELLED` or equivalent; possible partial output is communicated.

**Expected physical output**  
A prefix and/or buffered tail MAY print after cancellation. Exact stop position is not guaranteed without reliable device acknowledgements.

**Pass criteria**  
No new application-driven automatic retry/resume occurs; user sees cancellation with partial-output risk where appropriate.

**Failure observations**  
Record cancellation time, writes after cancellation request, visible output continuation, state transition, and diagnostics.

**Evidence/artifact recording**  
UI screenshot/log metadata and physical output photo.

**Related automated tests**  
Transport cancellation; lifecycle transitions; no-auto-retry.

**Related ESC/POS observation IDs**  
Only if buffered printer behavior reveals a protocol-relevant quirk.

**Notes**  
Printer-side buffering after cancellation is a hardware observation and MUST NOT be described as application-controlled unless proven.

---

### HW-026 — Reconnect behavior after interrupted session

**Purpose**  
Verify that reconnecting after a failed/cancelled session does not silently replay or resume the prior ambiguous job and that a new explicit job can be sent cleanly.

**Required phase/feature**  
Reconnect/session recovery implemented.

**Printer/profile**  
Any supported profile suitable for this resilience test. The H50i may be used as the currently available device.

**Transport**  
Android Bluetooth Classic initially.

**Prerequisites**  
`HW-006` or `HW-025` can induce an interrupted session; reconnect automated tests pass.

**Input/document fixture**  
Interrupted long job plus a short, uniquely labelled recovery fixture.

**Print options**  
Production defaults.

**Procedure**

1. Interrupt or cancel the first job after output begins.
2. Re-establish the connection.
3. Do not press Print immediately; observe whether Rastrio sends anything autonomously.
4. Explicitly submit the short recovery fixture.
5. Inspect output for any unexpected continuation/replay before the recovery fixture.

**Expected preview**  
A new recovery job receives its own new `PreparedPrint`; the old preview/plan is not silently reused as a resumed transmission.

**Expected encoded/behavioral characteristics**  
No automatic replay of prior encoded chunks. New session sends only the explicitly requested new job, aside from protocol initialization required by the strategy.

**Expected physical output**  
No application-caused duplicate continuation appears after reconnect. The short recovery fixture prints normally. Any printer-internal buffered residue observed before the new job is documented separately.

**Pass criteria**  
Reconnect is inert until user action; new job is cleanly distinguishable and no app-level resume occurs.

**Failure observations**  
Record unexpected bytes/writes, stale job IDs, duplicate content, and printer-buffer residue.

**Evidence/artifact recording**  
Transport log metadata and output photo.

**Related automated tests**  
Session reset; reconnect; stale-job isolation.

**Related ESC/POS observation IDs**  
TBD if device reset/init behavior matters.

**Notes**  
Do not confuse printer-internal continuation of already-buffered bytes with Rastrio replay; transport logs should distinguish them where possible.

---

### HW-027 — Very long job

**Purpose**  
Soak-test a production print path for long-duration output and detect cumulative transport, printer-buffer, feed, or thermal issues that shorter fixtures do not reveal.

**Required phase/feature**  
Long-job support and resource limits implemented. Future-gated until a suitable soak fixture and release process exist.

**Printer/profile**  
Any supported profile suitable for this soak test; repeat across additional maintained profiles as evidence grows.

**Transport**  
Android Bluetooth Classic initially.

**Prerequisites**  
Long text/raster automated resource tests pass; operator has sufficient paper and safe test setup.

**Input/document fixture**  
Deterministically generated long job with unique periodic markers and an end checksum/identifier. Exact length SHOULD be chosen empirically for the target printer and release risk; this document does not impose a universal length.

**Print options**  
Production defaults and documented transport policy.

**Procedure**

1. Record printer state and available paper.
2. Prepare without exceeding resource limits.
3. Print uninterrupted.
4. Inspect periodic markers and final marker.
5. Record stalls, connection drops, density fade, feed skew, or buffering pauses.

**Expected preview**  
Lazy/incremental preview remains usable and represents the complete prepared job.

**Expected encoded/behavioral characteristics**  
Bounded streaming; no giant allocation; stable chunk/pacing policy.

**Expected physical output**  
Complete ordered content through the final marker with no dropped/duplicated ranges.

**Pass criteria**  
Job transmits successfully under the transport contract and physical inspection shows complete ordered output.

**Failure observations**  
Record marker interval of first defect, runtime diagnostics, printer pauses, temperature/density changes if visible, and transport counters.

**Evidence/artifact recording**  
Representative start/middle/end evidence and full marker audit notes.

**Related automated tests**  
Resource limits; streaming; transport soak/fake tests where possible.

**Related ESC/POS observation IDs**  
TBD for buffer/pacing quirks.

**Notes**  
This test is intentionally not assigned a universal page/line/byte count.

---

### HW-028 — Printer-buffer stress

**Purpose**  
Empirically validate printer-side buffering guidance and detect data loss/corruption under sustained dense output.

**Required phase/feature**  
Printer Lab chunk/buffer diagnostics and configurable safe transmission policy. Future-gated.

**Printer/profile**  
Any profile under calibration. The H50i may be used first because it is currently available, but its result does not define generic defaults.

**Transport**  
An implemented transport with controllable session write policy.

**Prerequisites**  
Automated tests distinguish printer raster-band planning from transport write chunking; stress fixture available.

**Input/document fixture**  
Dense raster/text sequence designed to produce sustained output and obvious missing-band markers.

**Print options**  
Fixed physical plan. Only printer/transport pacing variables under test may change between variants.

**Procedure**

1. Generate one immutable `PreparedPrint`.
2. Send it using the current recommended policy.
3. Where safe, repeat with controlled stress variants that alter transport chunk/pacing within supported diagnostic ranges.
4. Compare output continuity and transport errors.
5. Stop increasing stress once corruption, instability, unsafe heating, or obvious buffer saturation occurs.

**Expected preview**  
Identical for every transmission-policy variant because transport settings do not change physical preparation.

**Expected encoded/behavioral characteristics**  
Prepared raster-band structure is fixed. Transport MAY subdivide bytes differently. Profile buffer guidance and transport chunk size remain separate configuration dimensions.

**Expected physical output**  
At safe settings, content is identical across variants. Unsafe/stress variants MAY reveal truncation or corruption and must be recorded, not normalized into a universal assumption.

**Pass criteria**  
The profile's recommended safe policy completes without corruption; any discovered safe/unsafe boundary is recorded with enough evidence to justify profile/session guidance.

**Failure observations**  
Record band index, chunk size, pacing, write errors, printer pauses, and exact corruption pattern.

**Evidence/artifact recording**  
Variant matrix with photos and transport metadata.

**Related automated tests**  
Policy intersection; fixed-`PreparedPrint` transport variations; chunking.

**Related ESC/POS observation IDs**  
Use when behavior appears tied to a protocol/raster command rather than transport alone.

**Notes**  
Do not encode Bluetooth-specific chunk values into `PreparedPrint` or generic profile geometry.

---

### HW-029 — Chunk/pacing matrix

**Purpose**  
Verify that changing transport-level chunk size or pacing does not alter `PreparedPrint`, preview, or intended physical content, while identifying stable transport settings for a device/session.

**Required phase/feature**  
Printer Lab transport diagnostics with configurable chunk/pacing. Future-gated.

**Printer/profile**  
Any profile under transport calibration. The H50i may be used first because it is currently available, but transport settings remain device/session evidence.

**Transport**  
Transport under test, initially Android Bluetooth Classic.

**Prerequisites**  
A supported diagnostic range for transport settings and an immutable fixture/plan.

**Input/document fixture**  
A mixed text+raster or long raster fixture that exposes dropped/repeated chunks visibly.

**Print options**  
Fixed across variants.

**Procedure**

1. Prepare once and record `PreparedPrint` identity/hash.
2. Print the same plan using each selected chunk/pacing variant.
3. Verify the prepared identity and preview remain unchanged.
4. Compare physical outputs.

**Expected preview**  
Identical across all variants.

**Expected encoded/behavioral characteristics**  
Encoded logical byte stream is equivalent; only transport write boundaries/timing differ.

**Expected physical output**  
Every stable variant produces semantically identical output. Unstable variants may fail and are documented.

**Pass criteria**  
Changing transport policy never causes re-layout/re-dithering; at least the recommended policy produces correct output.

**Failure observations**  
Record variant parameters, first corruption point, connection error, and whether encoded stream content changed unexpectedly.

**Evidence/artifact recording**  
Matrix of settings/results plus representative output photos.

**Related automated tests**  
Transport chunking; policy intersection; invariant that transport changes do not reprepare the document.

**Related ESC/POS observation IDs**  
Usually none unless command-boundary sensitivity is verified.

**Notes**  
Do not infer that the largest apparently successful write is universally safe.

---

## 21. Cut-Guidance and Cutter Tests

### HW-030 — Manual cutter guidance

**Purpose**  
Verify that printers without an automatic cutter, or jobs intentionally using manual cutting, print a visible manual cut guide as prepared content and show the same guide in physical preview.

**Required phase/feature**  
Manual cut-guide operation implemented.

**Printer/profile**  
Prefer a profile that declares no automatic cutter support. The observed H50i unit is appropriate once its no-cutter profile state is captured as maintained evidence. This test MUST follow profile truth rather than assumptions about a model family.

**Transport**  
Any implemented transport.

**Prerequisites**  
Manual-cut guide preview and encoder tests pass.

**Input/document fixture**  
Short document ending with a manual cut guide.

**Print options**  
Manual cut guide enabled; automatic cut disabled/not available.

**Procedure**

1. Prepare and inspect guide geometry in preview.
2. Print.
3. Verify the guide is visible as printed content at the expected position.
4. Confirm the printer does not perform an automatic cut because of Rastrio's plan.

**Expected preview**  
Manual guide appears as printable content.

**Expected encoded/behavioral characteristics**  
A printable guide operation/raster/text is serialized; no automatic cutter command is introduced.

**Expected physical output**  
Guide is present and usable for manual cutting; paper remains uncut by Rastrio's intended operation.

**Pass criteria**  
Guide placement matches preview and no auto-cut command/behavior is triggered by the prepared plan.

**Failure observations**  
Record guide position, unexpected cut, or missing guide.

**Evidence/artifact recording**  
Photo including document end and guide.

**Related automated tests**  
Manual guide preparation; preview; encoder golden proving no cut command.

**Related ESC/POS observation IDs**  
TBD only if unexpected cutter behavior occurs.

**Notes**  
A manual cut guide is printed content, unlike an automatic cut boundary annotation.

---

### HW-031 — Automatic full cut

**Purpose**  
Verify a profile-declared automatic full-cut operation and ensure preview represents it as an annotation rather than printable content.

**Required phase/feature**  
Automatic cutter support; qualifying cutter-equipped hardware. Future-gated if no supported hardware is available.

**Printer/profile**  
A profile that explicitly declares reliable full-cut support. The observed H50i unit is N/A because it has no automatic cutter. A materially different H50i hardware revision may be tested only if its own profile independently validates cutter support.

**Transport**  
Any implemented compatible transport.

**Prerequisites**  
Cut strategy validated; encoder golden for the exact selected strategy; safe cutter test setup.

**Input/document fixture**  
Short labelled document with a final automatic full-cut operation.

**Print options**  
Automatic cut mode: full.

**Procedure**

1. Prepare and confirm one `AutomaticCut(full)` or equivalent operation.
2. Inspect preview and confirm cut is shown as an annotation/boundary, not a printed line/text.
3. Print.
4. Observe the cutter action and resulting paper separation.

**Expected preview**  
Cut boundary annotation at the prepared position; no printable cut mark unless separately requested.

**Expected encoded/behavioral characteristics**  
Encoder emits only the already-selected validated full-cut strategy at the prepared boundary.

**Expected physical output**  
Paper is fully separated at approximately the intended feed position, subject to cutter mechanics. No literal annotation text/line is printed solely to represent the cut.

**Pass criteria**  
Full separation occurs once at the intended boundary and output before the cut is complete.

**Failure observations**  
Record no-op, partial cut, repeated cut, wrong feed position, jam, or printer reset.

**Evidence/artifact recording**  
Photo of separated output and run record; video MAY be useful for intermittent cutter behavior.

**Related automated tests**  
Cutter capability resolution; full-cut preparation; preview annotation; ESC/POS cut golden.

**Related ESC/POS observation IDs**  
Required for verified cutter command/dialect behavior.

**Notes**  
Do not issue undocumented cutter commands to a profile that does not declare support.

---

### HW-032 — Automatic partial cut

**Purpose**  
Verify a profile-declared automatic partial-cut operation and its distinction from full cut.

**Required phase/feature**  
Partial-cut support implemented and qualifying hardware available.

**Printer/profile**  
A profile that explicitly declares reliable partial-cut support. The observed H50i unit is N/A because it has no automatic cutter.

**Transport**  
Any compatible implemented transport.

**Prerequisites**  
Partial-cut encoder golden and validated protocol strategy.

**Input/document fixture**  
Short labelled document with a final partial-cut operation.

**Print options**  
Automatic cut mode: partial.

**Procedure**

1. Prepare and inspect preview annotation.
2. Print.
3. Observe whether paper remains attached by the expected uncut tab/region.
4. Compare with full-cut behavior only if both are supported and safe.

**Expected preview**  
Partial-cut boundary annotation, not printable cut content.

**Expected encoded/behavioral characteristics**  
Selected partial-cut strategy is serialized exactly as prepared.

**Expected physical output**  
Cutter activates once and leaves paper partially attached in the manner supported by the printer mechanism.

**Pass criteria**  
Observed action is distinctly partial rather than full/no cut, and occurs at the intended boundary.

**Failure observations**  
Record actual cutter action, feed offset, jams, or firmware coercion from partial to full cut.

**Evidence/artifact recording**  
Photo showing remaining attachment/tab and run record.

**Related automated tests**  
Partial-cut capability validation; preparation; preview annotation; encoder golden.

**Related ESC/POS observation IDs**  
Required for verified partial-cut command behavior.

**Notes**  
Some printers may advertise or document cut behavior differently from observed behavior. Record the observation and correct the profile rather than assuming Epson-compatible semantics universally.

---

## 22. Future Transport/Profile Expansion

### HW-033 — USB parity

**Purpose**  
Verify that Android USB transport can deliver the same already-prepared physical plan as Bluetooth without requiring document re-layout, re-dithering, or semantic changes.

**Required phase/feature**  
Phase 16 — Android USB; a printer accessible through both tested transports or an equivalent controlled comparison setup.

**Printer/profile**  
Same physical printer/profile for Bluetooth and USB where practical.

**Transport**  
Android Bluetooth Classic and Android USB Host.

**Prerequisites**

- both transports implemented and individually tested;
- same `PrinterProfile` applicable;
- transport-specific buffering policies recorded.

**Input/document fixture**  
A mixed fixture exercising native text plus raster image/QR if those features are supported by the profile.

**Print options**  
Identical output-affecting options for both runs.

**Procedure**

1. Create one immutable `PreparedPrint` or two provably equivalent prepared snapshots from identical inputs.
2. Record prepared identity/hash and preview.
3. Print through Bluetooth.
4. Print the equivalent byte stream/plan through USB.
5. Compare physical output and final transport diagnostics.

**Expected preview**  
Identical for both transports.

**Expected encoded/behavioral characteristics**  
No re-layout, re-dithering, or strategy reselection solely because transport changes. Transport write boundaries MAY differ.

**Expected physical output**  
Equivalent intended physical content and geometry through both transports, subject to normal printer repeatability.

**Pass criteria**  
No semantic/geometry difference attributable to transport selection and both transports complete under their contracts.

**Failure observations**  
Record whether difference originates in encoded stream, transport framing/chunking, connection lifecycle, or actual printer state.

**Evidence/artifact recording**  
Side-by-side output images, prepared hash, and transport metadata.

**Related automated tests**  
Transport-independence invariant; Bluetooth/USB fake tests; policy intersection.

**Related ESC/POS observation IDs**  
Normally none unless the hardware uses transport-dependent protocol behavior.

**Notes**  
USB parity MUST NOT require USB-specific fields in `ThermalDocument`, `LogicalDocument`, or `PreparedPrint` merely for delivery mechanics.

---

### HW-034 — Alternate printer-profile regression

**Purpose**  
Verify that the same generic Core pipeline works against an additional physical printer/profile without special-casing whichever printer happened to be available during bootstrap development.

**Required phase/feature**  
Printer management/profile import or a second maintained development profile plus access to qualifying hardware.

**Printer/profile**  
Any non-H50i physical printer with a validated `PrinterProfile`.

**Transport**  
Any implemented transport supported by that printer.

**Prerequisites**

- profile validation passes;
- no generic Core source change is made solely to branch on this printer model;
- at least the capability-appropriate subset of earlier hardware tests is executable.

**Input/document fixture**  
A capability-appropriate smoke suite drawn from the permanent tests, normally including simple text, wrapping/alignment, one raster fixture, and QR/cutter tests only when the profile declares those capabilities.

**Print options**  
Use defaults appropriate to the selected profile; record all profile-specific deviations from the comparison/baseline runs.

**Procedure**

1. Select the alternate physical printer and profile.
2. Run the defined smoke subset of earlier permanent tests.
3. Record any profile-specific differences.
4. Review failures for profile/strategy/quirk fixes before considering Core changes.

**Expected preview**  
Reflects the alternate profile's printable width, metrics, capabilities, and selected strategies without UI/layout special-casing.

**Expected encoded/behavioral characteristics**  
Generic preparation resolves behavior from profile data/known validated strategies; transport remains platform-owned.

**Expected physical output**  
Each capability-appropriate smoke test produces output consistent with that profile's prepared plan.

**Pass criteria**  
The selected smoke subset passes without introducing printer-model conditionals into generic Core. Legitimate profile-specific strategies/quirks are represented as validated data.

**Failure observations**  
Record whether failure indicates inaccurate profile capabilities, missing known protocol strategy, real generic bug, or unsupported hardware behavior.

**Evidence/artifact recording**  
Run matrix referencing each executed permanent test plus representative photos and profile revision.

**Related automated tests**  
Multi-profile preparation goldens; profile validation; architecture checks forbidding printer-model constants/branches in generic Core.

**Related ESC/POS observation IDs**  
Add observations for newly verified dialect differences or quirks.

**Notes**  
This is a regression umbrella for profile portability. It does not replace the individual permanent test IDs executed as part of the smoke subset.

---

### HW-035 — Printer self-test capability inventory

**Purpose**  
Capture a printer's own self-test/configuration report as reproducible physical evidence without overstating what that report proves about host-accessible protocol commands.

**Required phase/feature**  
Any development phase in which a physical printer exposes a vendor/firmware self-test or configuration report.

This test may be run before a production `PrinterProfile` exists.

**Printer/profile**  
Any physical printer with a built-in self-test/configuration output. Record the current profile ID/revision if one exists; otherwise record that the profile is not yet promoted.

**Transport**  
Not inherently transport-dependent. Use the printer's normal physical self-test procedure. If invoking self-test requires a host transport, record it.

**Prerequisites**

- physical access to the printer;
- known method for producing its self-test/configuration report;
- no requirement to infer undocumented units or meanings;
- camera/scan capability if evidence is to be retained.

**Input/document fixture**  
None. The printer's own self-test/configuration mechanism is the source.

**Print options**  
N/A unless the printer requires a configurable mode to generate the self-test; record such state explicitly.

**Procedure**

1. Record printer model and any visible hardware/firmware revision identifiers.
2. Generate the printer's own self-test/configuration report.
3. Photograph or scan the complete report where practical.
4. Transcribe only relevant non-sensitive facts exactly as printed.
5. Classify each fact separately rather than treating the whole page as one capability claim.
6. For every reported capability, state what the report **does not** prove about host-accessible protocol behavior.
7. Create/update corresponding `ESC_POS_NOTES.md` observations.
8. Do not copy pairing secrets, unique device identifiers, or unnecessary personal/local device data into public repository artifacts.

**Expected preview**  
N/A. This test inspects printer-generated diagnostic/configuration output rather than a Rastrio `PreparedPrint`.

**Expected encoded/behavioral characteristics**  
No Rastrio protocol bytes are required unless the printer's documented self-test mechanism itself requires host commands.

A self-test report MAY establish facts such as:

```text
reported physical print width
reported resident/default font dimensions
reported current/default charset
reported USB/Bluetooth firmware/interface information
presence of firmware-generated QR/barcode samples
current density/autofeed/configuration values
runtime telemetry
physical cutter presence/absence when directly observable
```

It MUST NOT by itself establish:

```text
printableWidthDots without validated dot geometry
ESC/POS selector values
complete code-page repertoire
host-accessible QR/barcode command strategy
safe raster command/band limits
status-query behavior
transport chunk/pacing policy
```

**Expected physical output**  
A complete printer self-test/configuration page or equivalent firmware report.

**Pass criteria**

- the report is captured legibly enough to support the transcribed observations;
- observations are recorded at the correct evidence strength;
- no unsupported host-command claim is promoted from a self-test-only fact;
- sensitive pairing/device-identifying values are omitted/redacted from public evidence.

**Failure observations**  
Record inability to generate the self-test, illegible output, ambiguous labels/units, or discrepancies between repeated reports.

Ambiguous fields remain unclassified rather than guessed.

**Evidence/artifact recording**  
A photo/scan MAY be stored according to repository evidence-retention policy. At minimum retain a textual observation record containing:

```text
date
printer model
firmware/version fields where shown
profile ID/revision if any
transcribed relevant fields
redactions performed
ESC_POS_NOTES observation IDs
```

**Related automated tests**  
None required for the printer-generated page itself. Any profile claim promoted from the evidence requires the normal schema, preparation, encoder, and resource/security tests.

**Related ESC/POS observation IDs**  
Required for any conclusion added to the ESC/POS notebook.

**Notes**  
This test distinguishes **printer-reported capability/configuration evidence** from **Rastrio-controlled command-path verification**.

For the currently available H50i, the 2026-10-02 observations already reported during development include:

```text
FontVer: 1.00.00
USB: Printing Port
BT_ver: 3.16.1
Temperature: 33
Voltage: 4.20
Print Density: 5
Print Width: 48mm
Print Speed: 60mm/sec (Max)
Autofeed: 0mm
Font: 12x24
Charset: CP437
QR symbol present in self-test
EAN-13 symbol present in self-test
automatic cutter absent on observed unit
```

These existing observations remain `Hardware Observed` until captured through this permanent test/run-record process where applicable.

---

## 23. Release and Milestone Run Sets

### 23.1 First major Android milestone

The first end-to-end Bluetooth text milestone SHOULD execute:

```text
HW-001 Simple ASCII
HW-002 Line wrapping
HW-003 Alignment
HW-004 Bold/underline
HW-005 Long text
HW-006 Disconnect during job / recovery procedure
```

A capability-specific test such as `HW-004` MUST still respect the effective profile. If the reference profile cannot truthfully support a style, record that limitation rather than fabricating capability data.

### 23.2 Raster/image feature gate

When raster/image printing is included:

```text
HW-007 Checkerboard
HW-008 Threshold gradient
HW-009 Bayer gradient
HW-010 Atkinson gradient
HW-011 Floyd–Steinberg gradient
HW-012 Full-width image
HW-013 Long image
```

### 23.3 Unicode fallback feature gate

When raster text fallback is included:

```text
HW-014 Unicode raster text
HW-015 Mixed supported/unsupported text
HW-016 Complex-script sample
```

### 23.4 QR feature gate

When QR printing is included:

```text
HW-017 Native QR        (only on a profile declaring reliable native QR)
HW-018 Raster QR
HW-019 QR size variations
```

### 23.5 Wide-document tiling feature gate

When landscape tiling is implemented/promoted:

```text
HW-020 Two-strip table
HW-021 Three-strip table
HW-022 Registration marks
HW-023 Physical assembly
```

### 23.6 Future capability gates

Additional tests become mandatory when their corresponding capability is shipped or claimed:

```text
HW-024 Partial print transmission accounting
HW-025 Cancellation during active job
HW-026 Reconnect behavior after interrupted session
HW-027 Very long job
HW-028 Printer-buffer stress
HW-029 Chunk/pacing matrix
HW-030 Manual cutter guidance
HW-031 Automatic full cut
HW-032 Automatic partial cut
HW-033 USB parity
HW-034 Alternate printer-profile regression
```

`HW-035 Printer self-test capability inventory` is an evidence-capture test rather than a universal release gate. It SHOULD be run when onboarding or materially revising a printer profile whose firmware exposes useful self-test data.

A release checklist SHOULD identify the exact applicable hardware-test set rather than treating every future-gated ID as automatically mandatory.

---

## 24. Capability Matrix Guidance

For each maintained physical printer/profile, the project SHOULD keep a simple applicability matrix. Example structure:

| Test | H50i current status | Other profile | Notes |
|---|---|---|---|
| HW-001 | Required when Rastrio-controlled Bluetooth path is ready | Normally required for native-text-capable profile | RawBT observation does not replace Rastrio-controlled run |
| HW-004 | Capability-dependent / not yet verified | Capability-dependent | Native bold/underline |
| HW-017 | Capability-dependent / host command not yet verified | Capability-dependent | Self-test QR does not establish native QR command |
| HW-031 | **N/A** for observed H50i unit | Capability-dependent | H50i automatic cutter observed absent |
| HW-032 | **N/A** for observed H50i unit | Capability-dependent | H50i automatic cutter observed absent |
| HW-033 | Future; USB interface presence reported, Rastrio USB behavior unverified | Capability-dependent | USB implementation required |
| HW-035 | Applicable; current self-test observations should be formalized | Applicable where self-test exists | Evidence inventory only |

The repository SHOULD fill this matrix from verified/observed profile capabilities and actual implementation status.

`Hardware Observed` evidence may justify conservative disabling of a capability, but positive output-affecting protocol claims SHOULD receive the command-specific verification required by the relevant permanent test.

---

## 25. Recording Printer Quirks

A physical test result becomes a **quirk** only after evidence shows behavior that is specific enough to require profile/strategy treatment.

Examples of evidence-worthy findings include:

- a documented native style command is ignored by one model;
- a raster command accepts a narrower width than nominal profile data suggests;
- native QR size parameters are clamped or interpreted differently;
- a cutter command behaves as full cut when documented/configured as partial;
- sustained raster writes require printer-side pacing guidance;
- initialization resets a setting that the current encoder assumed persisted.

When such behavior is found:

1. create or update an `ESC_POS_NOTES.md` observation with evidence status;
2. link the observation to the hardware test ID and run artifact;
3. correct `PrinterProfile` capability/quirk/strategy data where appropriate;
4. add a byte-level or preparation regression test where possible;
5. avoid printer-model branching in generic Core.

A one-off failed print without reproducible evidence MUST NOT immediately become a permanent quirk.

---

## 26. Hardware Feedback and Status Claims

Rastrio MUST distinguish:

```text
bytes accepted/transmitted according to transport contract
```

from:

```text
paper physically printed successfully
```

Unless a tested printer/status protocol provides reliable confirmation and the profile/transport supports it, hardware tests MUST treat physical success as a manual operator observation.

Therefore:

- an application `COMPLETED` state may establish successful transmission according to the transport contract;
- the hardware-test operator records whether the expected paper output was actually observed;
- connection loss after partial transmission is ambiguous physical output;
- automatic retry is forbidden when duplicate/partial output may already exist;
- printer-side buffered continuation after disconnect/cancel must be recorded as observed behavior, not inferred application progress.

---

## 27. Safety and Operational Notes

Hardware testing SHOULD avoid unnecessary printer wear, overheating, cutter cycling, and paper waste.

- Dense black raster stress tests SHOULD be bounded by a reviewed test fixture and stopped if the printer exhibits unsafe heat, odor, repeated jams, or abnormal mechanical behavior.
- Cutter tests SHOULD use short non-sensitive fixtures and keep hands clear of the cutter mechanism. They MUST NOT be run against a profile/device known to lack an automatic cutter.
- Printer-buffer stress SHOULD increase load cautiously and stop after the first reproducible instability; discovering an exact destructive limit is not a goal.
- Tests SHOULD use known-good paper appropriate for the printer when comparing geometry or density across runs.
- Battery-powered printers SHOULD record meaningful power context when low battery is suspected to affect speed, density, or connectivity.

These are operational precautions, not substitutes for manufacturer safety guidance.

---

## 28. Adding a New Hardware Test

A contributor adding a new hardware-facing feature SHOULD add a new permanent hardware test when physical behavior cannot be adequately proven below the hardware boundary.

Before allocating a new ID:

1. confirm no existing permanent test already covers the same physical behavior;
2. add/identify the automated unit/golden/integration tests first;
3. identify the capability/profile prerequisite;
4. define preview expectations from `PreparedPrint` and finalized prepared artifacts where applicable;
5. define what is actually observable on hardware;
6. avoid requiring nonexistent printer status feedback;
7. allocate the next unused permanent `HW-NNN` ID;
8. update the release/feature run set if the feature is release-critical;
9. add links to relevant `ESC_POS_NOTES.md` observations after verification.

Do not reserve large undocumented ID ranges. Add IDs as concrete test purposes emerge.

---

## 29. Retiring or Replacing a Test

If a test is no longer valid:

- keep its ID and historical title visible;
- mark it **Retired** with the reason and date/commit;
- state the replacement test ID if one exists;
- do not reuse the number;
- keep historical run artifacts interpretable.

If a feature is removed, the test MAY remain retired rather than deleted so prior release evidence retains meaning.

---

## 30. Minimum Review Checklist for a Hardware Run

Before declaring a scoped hardware regression run complete, verify:

- [ ] Every applicable permanent test has a recorded status.
- [ ] Every PASS includes printer model, effective profile revision, app version/commit, transport, fixture, and physical observation.
- [ ] Every dimension-sensitive PASS references a defined tolerance source rather than an invented universal tolerance.
- [ ] Every FAIL has sufficient evidence to triage the responsible layer.
- [ ] No FAIL was "fixed" solely by generic printer-model special-casing.
- [ ] Output-affecting preview expectations were checked where applicable.
- [ ] Exact raster tests used the same finalized prepared raster/artifact content represented in preview and supplied to the encoder.
- [ ] Partial-output tests did not assume the printer confirmed physical completion.
- [ ] Relevant automated regression coverage was added or linked.
- [ ] Relevant `ESC_POS_NOTES.md` observations were created/updated when a protocol or hardware quirk was discovered.
- [ ] Profile corrections were made where the evidence showed inaccurate capability data.
- [ ] Hardware evidence was not generalized beyond the specific device/profile/firmware/capability actually tested.
- [ ] Evidence contains no unintended private printable content or device identifiers.

---

## 31. Baseline Test Index

| ID | Title | Primary feature group | Status in this specification |
|---|---|---|---|
| HW-001 | Simple ASCII | Android Bluetooth text | PRD-established |
| HW-002 | Line wrapping | Android Bluetooth text | PRD-established |
| HW-003 | Alignment | Android Bluetooth text | PRD-established |
| HW-004 | Bold/underline | Android Bluetooth text | PRD-established |
| HW-005 | Long text | Android Bluetooth text | PRD-established |
| HW-006 | Disconnect during job / recovery procedure | Bluetooth resilience | PRD-established |
| HW-007 | Checkerboard | Raster/image | PRD-established |
| HW-008 | Threshold gradient | Raster/image | PRD-established |
| HW-009 | Bayer gradient | Raster/image | PRD-established |
| HW-010 | Atkinson gradient | Raster/image | PRD-established |
| HW-011 | Floyd–Steinberg gradient | Raster/image | PRD-established |
| HW-012 | Full-width image | Raster/image | PRD-established |
| HW-013 | Long image | Raster/image | PRD-established |
| HW-014 | Unicode raster text | Text fallback | PRD-established |
| HW-015 | Mixed supported/unsupported text | Text fallback | PRD-established |
| HW-016 | Complex-script sample | Text fallback | PRD-established |
| HW-017 | Native QR | QR | PRD-established |
| HW-018 | Raster QR | QR | PRD-established |
| HW-019 | QR size variations | QR | PRD-established |
| HW-020 | Two-strip table | Wide tiling | PRD-established |
| HW-021 | Three-strip table | Wide tiling | PRD-established |
| HW-022 | Registration marks | Wide tiling | PRD-established |
| HW-023 | Physical assembly | Wide tiling | PRD-established |
| HW-024 | Partial print transmission accounting | Transport resilience | Additional, future-gated |
| HW-025 | Cancellation during active job | Transport resilience | Additional, future-gated |
| HW-026 | Reconnect behavior after interrupted session | Transport resilience | Additional, future-gated |
| HW-027 | Very long job | Stress/reliability | Additional, future-gated |
| HW-028 | Printer-buffer stress | Stress/reliability | Additional, future-gated |
| HW-029 | Chunk/pacing matrix | Transport calibration | Additional, future-gated |
| HW-030 | Manual cutter guidance | Cut behavior | Additional, feature-gated |
| HW-031 | Automatic full cut | Cut behavior | Additional, feature-gated |
| HW-032 | Automatic partial cut | Cut behavior | Additional, feature-gated |
| HW-033 | USB parity | Android USB | Additional, future-gated |
| HW-034 | Alternate printer-profile regression | Profile portability | Additional, future-gated |
| HW-035 | Printer self-test capability inventory | Evidence capture/profile onboarding | Additional, evidence-gated |

---

## 32. Hardware Evidence and Measurement Backlog

The hardware architecture is sufficiently defined. The remaining unknowns are empirical facts that must be measured or documented per printer/profile.

### 32.1 H50i facts already observed

Current development evidence includes:

```text
ESC/POS-compatible printing observed through RawBT
self-test reports print width 48 mm
self-test reports font 12x24
self-test reports charset CP437
self-test prints QR and EAN-13 samples
self-test reports USB printing interface
observed unit has no automatic cutter
```

These facts are recorded in `ESC_POS_NOTES.md` at `Hardware Observed` strength.

They do not remove the need for Rastrio-controlled command tests.

### 32.2 H50i measurements/command paths still required

The remaining high-value questions include:

1. authoritative printable width in dots/effective horizontal dot geometry;
2. exact native font selector/advance/line metrics and style interactions;
3. actual `ESC t` mapping for CP437 and any additional reliable code pages;
4. supported raster command strategy, bit orientation, full-width behavior, and safe band guidance;
5. host-accessible native QR/barcode strategies, if Rastrio chooses to use them;
6. printer-side buffering/pacing behavior versus Android Bluetooth transport chunking;
7. status-query behavior, only if a product feature needs it;
8. firmware/hardware revision differences if multiple units/revisions become available;
9. profile-specific physical tolerances for width, alignment, spacing, raster dimensions, and registration.

### 32.3 Additional printer coverage

The project SHOULD eventually run `HW-034` and the capability-appropriate permanent suite against additional ESC/POS-compatible printers.

No specific second model is mandated by this specification.

Compatibility claims remain scoped to evidence actually collected.

### 32.4 Hardware evidence storage

The repository still needs a practical policy for photos/scans and other large hardware-test evidence.

That policy SHOULD define:

```text
what is committed directly
what may be stored externally
retention expectations
privacy/redaction
stable linkage from run records
content hashes where useful
```

Until such a policy exists, textual run records and small reviewable evidence may be committed while large artifacts are referenced conservatively.

These are measurement/maintenance tasks, not unresolved architecture.

---

## 33. Final Invariant

The hardware regression suite validates the boundary between Rastrio's deterministic print plan and real devices.

The intended flow remains:

```text
ThermalDocument
    ↓
LayoutConstraints
    ↓
LogicalDocument
    ↓
PrinterProfile + PrintOptions
    ↓
PreparedPrint
+ immutable prepared artifacts
   /         \
  ↓           ↓
Preview   protocol encoder
          (`core-escpos` v1)
               ↓
           Transport
               ↓
            Printer
               ↓
       manual physical observation
```

Hardware evidence may refine profiles, protocol strategies, transport policy, and bug fixes. It MUST NOT weaken the architectural rule that `PreparedPrint` is the authoritative physical plan shared by preview and encoding.
