# Rastrio `.tcfg` Printer Profile Specification

**Status:** Normative specification  
**Specification version:** 1.1  
**Target format:** `.tcfg` schema version 1  
**Project:** Rastrio  
**License context:** Apache-2.0  
**Normative representation:** UTF-8 JSON  

---

## 1. Purpose

A Rastrio `.tcfg` file is the portable serialized representation of a `PrinterProfile`.

A `PrinterProfile` describes a printer model, firmware family, or reusable capability set that Rastrio can use to prepare printer-specific physical output. It contains printer capabilities, geometry, supported text and raster behavior, validated protocol configuration, physical printer constraints, and known validated quirks.

The `.tcfg` schema is vendor-neutral and model-neutral. No printer model is architecturally privileged.

Schema version 1 supports the project's current primary protocol family:

```text
ESC/POS-compatible thermal printers
```

A printer that fits the v1 capability model and registered ESC/POS strategy vocabulary can be represented without adding model-specific Core logic.

The top-level `protocol` block is the protocol-family boundary. In v1 it has exactly one normative family, `escpos`. Future protocol families may define different validated protocol-block variants and dedicated trusted encoders while preserving the same separation between document semantics, printer capabilities, physical preparation, protocol serialization, and transport.

The Helett H50i is only an available physical reference device used to validate the implementation. It MUST NOT define generic defaults or special cases.

A `.tcfg` file MUST be portable, shareable, importable, exportable, deterministic to validate, and independent of any user's physical connection to a printer.

A `.tcfg` file MUST NOT identify a particular paired or connected device.

The profile is an input to printer preparation. It is not a print job, not a transport session, and not an executable printer program.

Conceptually:

```text
PrinterProfile (.tcfg)
        +
validated local PrinterProfileOverride
        ↓
EffectivePrinterProfile
        +
ThermalDocument
        +
PrintOptions
        ↓
PreparationEngine
        ↓
PreparedPrint
```

The resulting `PreparedPrint`, not the `.tcfg` file, records the final physical decisions for a particular print preparation.

---

## 2. Normative Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are to be interpreted as normative requirements.

Where this specification says that an identifier is **registered**, the identifier MUST be implemented and recognized by the running Rastrio version. A profile cannot create new executable behavior merely by inventing an identifier.

---

## 3. Non-Goals

`.tcfg` v1 is not:

- a serialization of `PrinterInstance`;
- a serialization of `PrintOptions`;
- a transport configuration file;
- a Bluetooth pairing record;
- a USB device record;
- a raw ESC/POS command script;
- a programmable byte-template format;
- a printer firmware update format;
- a general device-driver language;
- a container for arbitrary executable code;
- a general inheritance or templating system;
- a replacement for empirical hardware validation;
- a claim that schema version 1 supports every thermal-printer command language.

In particular, `.tcfg` MUST NOT contain:

- Bluetooth MAC addresses or equivalent Bluetooth identities;
- paired-device identifiers;
- USB vendor/product/serial identity for a user's physical unit;
- USB interface or endpoint selections for a particular connection;
- transport write chunk sizes;
- RFCOMM parameters;
- transport write timeouts;
- transport retry policy;
- connection pacing chosen for a transport;
- application-local instance IDs;
- last-used timestamps;
- arbitrary ESC/POS byte arrays;
- arbitrary hexadecimal command strings;
- script fragments;
- unrestricted command templates.

---

## 4. Fundamental Type Separation

Rastrio MUST preserve the following distinction.

### 4.1 `PrinterProfile`

A `PrinterProfile` describes a printer model or reusable capability set.

It is:

- portable;
- shareable;
- importable/exportable;
- hardware-capability oriented;
- free of user-private physical-printer identity.

`.tcfg` serializes this concept.

### 4.2 `PrinterInstance`

A `PrinterInstance` represents one physical printer known to a user.

The portable application-level model belongs to `shared`, not `core-profile`.

It MAY contain platform-neutral local state such as:

- a user-facing local name;
- transport kind;
- an opaque persisted transport locator or device identity;
- connection/session preferences;
- association with a `PrinterProfile`;
- validated user overrides;
- local calibration results;
- last-used metadata.

Live platform objects such as Android Bluetooth/USB objects, browser hardware handles, desktop serial-port objects, sockets, file descriptors, and permission objects remain platform-owned.

A `PrinterInstance` MUST NOT be serialized as `.tcfg`.

### 4.3 `PrintOptions`

`PrintOptions` represents choices for one print preparation.

Examples include:

- dithering algorithm;
- brightness;
- contrast;
- gamma;
- density;
- text strategy preference;
- native/raster preference;
- image scaling strategy;
- QR strategy preference;
- landscape overlap;
- registration marks;
- strip numbering;
- manual cut guides.

`PrintOptions` MUST NOT be serialized into `.tcfg` as printer capabilities.

A print option MAY select among capabilities declared by the effective printer profile. It MUST NOT manufacture a capability that the effective profile does not declare.

---

## 5. File Representation

### 5.1 Extension

The normative file extension is:

```text
.tcfg
```

### 5.2 Encoding

Schema version 1 MUST use:

```text
UTF-8 JSON
```

A v1 file MUST be valid standard JSON and MUST decode as UTF-8.

A byte-order mark SHOULD NOT be emitted. A parser MAY accept a UTF-8 BOM only if doing so is safe and deterministic.

The `.tcfg` specification is authoritative over the selected JSON library. A conforming reader MUST reject input prohibited by this specification even if its parser would otherwise accept, normalize, ignore, coerce, or overwrite that input.

At minimum, schema v1 readers MUST reject:

- comments;
- trailing commas;
- duplicate object-property names;
- single-quoted strings;
- unquoted property names;
- non-standard numeric tokens such as `NaN`, `Infinity`, and `-Infinity`;
- invalid standard-JSON number grammar;
- unknown properties at every schema-defined object level;
- unsupported enum, repertoire, strategy, quirk, protocol-family, or dialect identifiers;
- illegal `null` values.

Duplicate keys MUST be detected before a deserializer or map representation can silently collapse or overwrite them.

If the selected parser cannot enforce a required rule directly, Rastrio MUST add bounded lexical pre-validation, token inspection, parser wrapping, or structural validation.

YAML is not a normative v1 representation.

### 5.3 Format Identifier

The top-level `format` property MUST be:

```json
"rastrio-tcfg"
```

A reader MUST reject a file whose `format` value is absent or different.

### 5.4 Schema Version

The top-level `schemaVersion` property MUST be an integer.

For this specification it MUST equal:

```json
1
```

A reader that supports only schema version 1 MUST reject an unsupported future version. It MUST NOT silently interpret an unknown schema version as v1.

### 5.5 Media Type Recommendation

If a media type is needed for local integration, Rastrio SHOULD use the provisional vendor-tree form:

```text
application/vnd.rastrio.tcfg+json
```

This recommendation does not imply IANA registration.

---

## 6. Top-Level v1 Structure

A v1 profile has this shape:

```json
{
  "format": "rastrio-tcfg",
  "schemaVersion": 1,
  "profileId": "org.rastrio.example.synthetic-narrow",
  "profileRevision": 1,
  "display": {
    "name": "Synthetic Narrow ESC/POS",
    "manufacturer": "Rastrio Test Fixture",
    "model": "Narrow-203"
  },
  "geometry": {
    "horizontalDpi": 203,
    "verticalDpi": 203,
    "printableWidthDots": 384
  },
  "nativeText": {
    "supported": true,
    "fonts": [],
    "styles": {
      "bold": true,
      "underline": true,
      "widthScales": [1, 2],
      "heightScales": [1, 2]
    },
    "codePages": []
  },
  "raster": {
    "supported": true,
    "strategies": ["escpos.raster.gs-v-0"],
    "preferredStrategy": "escpos.raster.gs-v-0",
    "maxWidthDots": 384,
    "bandHeightDots": {
      "min": 1,
      "preferred": 128,
      "max": 255
    }
  },
  "nativeQr": {
    "supported": false,
    "strategies": []
  },
  "nativeBarcode": {
    "supported": false,
    "strategies": []
  },
  "cutter": {
    "supported": false,
    "modes": []
  },
  "printerBuffer": {
    "recommendedMaxBurstBytes": 4096,
    "recommendedPauseAfterBurstMs": 10
  },
  "statusQuery": {
    "supported": false,
    "strategies": []
  },
  "protocol": {
    "family": "escpos",
    "dialectId": "escpos.generic",
    "initializationStrategy": "escpos.init.standard"
  },
  "quirks": []
}
```

The example above illustrates structure. Complete valid synthetic profiles appear later in this document.

---

## 7. Required and Optional Properties

The following top-level properties are REQUIRED in schema version 1:

| Property | Type | Meaning |
|---|---|---|
| `format` | string | Exact format identifier, `rastrio-tcfg`. |
| `schemaVersion` | integer | Profile schema version. MUST be `1` for v1. |
| `profileId` | string | Stable machine-readable profile identity. |
| `profileRevision` | integer | Revision of the profile's capability data. |
| `display` | object | Human-facing profile metadata. |
| `geometry` | object | DPI and printable-width information. |
| `nativeText` | object | Native printer text capabilities. |
| `raster` | object | Raster capability and strategy information. |
| `nativeQr` | object | Native QR capability information. |
| `nativeBarcode` | object | Native barcode capability information. |
| `cutter` | object | Automatic cutter capability information. |
| `statusQuery` | object | Printer status-query capability information. |
| `protocol` | object | Protocol family, dialect, and baseline strategy. |
| `quirks` | array | Registered validated quirk identifiers. |

The following top-level property is OPTIONAL:

| Property | Type | Meaning |
|---|---|---|
| `printerBuffer` | object | Physical printer-side buffering/pacing guidance, when known and validated. |

A missing optional property means **unknown/not declared**, not an invented default.

---

## 8. Profile Identity

### 8.1 `profileId`

`profileId` is the stable identity of the reusable printer profile.

It MUST:

- be an ASCII string;
- match `^[a-z0-9][a-z0-9._-]{0,127}$`;
- be stable across ordinary corrections to the same intended profile;
- not contain a user's physical-printer identifier;
- not encode secrets;
- not be interpreted as a file path, URL, class name, or executable reference.

Recommended form:

```text
<reverse-domain-or-project-namespace>.<vendor-or-owner>.<model-or-capability-name>
```

Examples:

```text
org.rastrio.example.synthetic-narrow
org.rastrio.example.synthetic-80mm
org.rastrio.vendor.example-model
```

A profile ID is identity only. It does not imply that the named hardware is supported, validated, bundled, or behaviorally compatible with another similarly named printer.

### 8.2 `profileRevision`

`profileRevision` MUST be a positive integer.

It identifies the revision of the capability data for the same `profileId`.

A publisher SHOULD increment `profileRevision` when changing any fact that can alter preparation or encoding, including:

- printable width;
- DPI;
- native font geometry;
- code-page capability;
- raster strategy;
- raster band constraints;
- QR/barcode capability;
- cutter capability;
- printer-side buffering guidance;
- protocol strategy;
- quirk set.

`profileRevision` is independent of `schemaVersion`.

Changing JSON representation because of a schema migration does not by itself require changing `profileId`.

If two hardware or firmware families require materially different behavior and cannot be represented safely by one unconditional v1 profile, they SHOULD use separate `profileId` values rather than conditional logic invented inside the profile.

---

## 9. Display and Manufacturer/Model Metadata

`display` MUST be an object with:

```json
{
  "name": "Synthetic Narrow ESC/POS"
}
```

`name` is REQUIRED.

The following properties are OPTIONAL:

```json
{
  "manufacturer": "Example Printer Co.",
  "model": "TP-58"
}
```

Rules:

- `name`, `manufacturer`, and `model` are display metadata only.
- They MUST NOT control protocol selection.
- They MUST NOT be parsed as commands.
- They MUST NOT be used as implicit hardware matching rules.
- They MUST be bounded by the string limits defined in `docs/RESOURCE_LIMITS.md`.
- Control characters other than ordinary JSON whitespace within strings SHOULD be rejected.
- User interfaces MUST treat these strings as untrusted text.

A profile's behavioral identity comes from validated capability data and registered strategies, not from substring matching on manufacturer or model names.

---

## 10. Geometry

`geometry` MUST contain:

```json
{
  "horizontalDpi": 203,
  "verticalDpi": 203,
  "printableWidthDots": 384
}
```

### 10.1 DPI

`horizontalDpi` and `verticalDpi`:

- MUST be positive integers;
- MUST be within resource/sanity limits defined by `docs/RESOURCE_LIMITS.md`;
- MUST represent the physical printer's effective dot density used for preparation;
- MUST NOT be inferred from a transport type.

Rastrio MUST NOT assume that horizontal and vertical DPI are equal.

### 10.2 Printable width

`printableWidthDots`:

- MUST be a positive integer;
- MUST be within resource/sanity limits;
- MUST represent the reliable printable width of the profile;
- MUST be used as a printer capability, not a document property.

The physical printable width in millimetres is derived as:

```text
printableWidthMm =
    printableWidthDots / horizontalDpi × 25.4
```

v1 intentionally stores the authoritative width in dots plus DPI instead of storing a second redundant width value that could disagree.

A self-test page, datasheet, or physical measurement expressed only in millimetres MAY provide useful hardware evidence, but it is not by itself sufficient to populate `printableWidthDots` unless effective horizontal dot density and reliable printable dot width are also established.

Rastrio MUST NOT infer a common DPI merely because a printer belongs to a familiar paper-width class.

### 10.3 Printable height

`.tcfg` v1 does not define a fixed printable page height for continuous-feed thermal printers.

Document length is determined by prepared content and physical operations. Resource limits still apply to raster bands, previews, and total prepared work.

A future fixed-page printer model would require an explicit specification update rather than overloading v1 fields.

---

## 11. Native Text Capability

`nativeText` MUST be present.

Base shape:

```json
{
  "supported": true,
  "fonts": [],
  "styles": {
    "bold": true,
    "underline": true,
    "widthScales": [1, 2],
    "heightScales": [1, 2]
  },
  "codePages": []
}
```

### 11.1 `supported`

If `supported` is `false`:

- `fonts` MUST be empty;
- `codePages` MUST be empty;
- all native text preparation MUST be disabled;
- text MUST be rasterized where raster text is available, or preparation MUST fail with a structured diagnostic.

If `supported` is `true`:

- `fonts` MUST contain at least one valid font;
- `codePages` MUST contain at least one valid repertoire;
- the profile MUST provide enough geometry and selection information for deterministic preparation.

### 11.2 Fonts

Each entry in `fonts` MUST have a stable profile-local `id`.

Example:

```json
{
  "id": "font-a",
  "cellWidthDots": 12,
  "cellHeightDots": 24,
  "lineAdvanceDots": 24,
  "selector": {
    "strategy": "escpos.font.esc-m",
    "parameter": 0
  }
}
```

Required font properties:

- `id`
- `cellWidthDots`
- `cellHeightDots`

Optional font properties:

- `lineAdvanceDots`
- `selector`

Rules:

- font IDs MUST match the general identifier grammar;
- font IDs MUST be unique within the profile;
- geometry values MUST be positive integers;
- `lineAdvanceDots`, when present, MUST be positive;
- omitted `lineAdvanceDots` means the implementation uses the registered strategy's documented default derived from the font geometry;
- a missing `selector` is permitted only when that font is the unambiguous protocol default for the declared dialect;
- a selector MUST reference a registered strategy;
- selector parameters MUST satisfy that strategy's validator.

Native font geometry is part of layout and preparation. It MUST NOT be guessed by the preview layer or ESC/POS encoder.

Printer-reported text such as `Font: 12x24` MAY support the reported/default native cell-geometry claim. It does not by itself establish selector bytes, alternate fonts, line advance, style support, or behavior across firmware revisions. Those claims require their own evidence.

### 11.3 Native styles

`styles` MUST have this shape:

```json
{
  "bold": true,
  "underline": true,
  "widthScales": [1, 2],
  "heightScales": [1, 2]
}
```

`bold` and `underline` are REQUIRED booleans.

`widthScales` and `heightScales` are REQUIRED arrays of positive integers.

Rules:

- scale arrays MUST be non-empty when native text is supported;
- values MUST be unique and sorted in ascending order when profiles are emitted canonically;
- strategy-specific validators MAY impose tighter limits;
- a requested style unsupported by the effective profile MUST NOT be silently emitted as native text;
- unsupported style may cause raster fallback or a structured diagnostic during preparation.

No style in v1 is implied merely because a generic ESC/POS implementation often supports it. The profile must declare support.

### 11.4 Code pages and character repertoires

Each native code-page entry has the following form:

```json
{
  "id": "cp437",
  "repertoire": "cp437",
  "selector": {
    "strategy": "escpos.code-page.esc-t",
    "parameter": 0
  }
}
```

Required properties:

- `id`
- `repertoire`

`selector` is OPTIONAL only when the repertoire is the unambiguous protocol default.

Rules:

- `id` MUST be unique within the profile;
- `repertoire` MUST identify a character repertoire known to the running Rastrio implementation;
- `.tcfg` v1 MUST NOT embed arbitrary user-defined byte-to-Unicode mapping tables;
- unknown repertoires MUST cause profile validation failure;
- selector strategies and parameters MUST be registered and validated;
- code-page selection MUST be resolved by printer preparation before ESC/POS encoding;
- the encoder MUST serialize the already-selected code page and MUST NOT re-run repertoire selection.

The effective character coverage used by preparation is the intersection of:

```text
declared profile repertoire
∩
known Rastrio repertoire mapping
∩
validated quirk restrictions
```

A printer self-test reporting a current/default charset such as `CP437` is useful evidence that the firmware identifies that repertoire as active or available in that state. It does not by itself establish the selector parameter for switching to that repertoire, prove the complete supported code-page set, or prove exact byte-to-glyph behavior for every code point.

---

## 12. Raster Capability

`raster` MUST be present.

### 12.1 Unsupported raster

A raster-incapable profile is represented as:

```json
{
  "supported": false,
  "strategies": []
}
```

When `supported` is `false`:

- `strategies` MUST be empty;
- `preferredStrategy` MUST be absent;
- `maxWidthDots` MUST be absent;
- `bandHeightDots` MUST be absent.

### 12.2 Supported raster

Example:

```json
{
  "supported": true,
  "strategies": [
    "escpos.raster.gs-v-0"
  ],
  "preferredStrategy": "escpos.raster.gs-v-0",
  "maxWidthDots": 384,
  "bandHeightDots": {
    "min": 1,
    "preferred": 128,
    "max": 255
  }
}
```

When `supported` is `true`:

- `strategies` MUST contain at least one registered raster strategy;
- `preferredStrategy` MUST be present;
- `preferredStrategy` MUST also appear in `strategies`;
- `maxWidthDots` MUST be a positive integer;
- `maxWidthDots` MUST NOT exceed `geometry.printableWidthDots`;
- `bandHeightDots` MUST be present.

### 12.3 Raster strategies

A raster strategy is a registered encoder implementation, not an imported command template.

For v1, the baseline registered strategy identifier is:

```text
escpos.raster.gs-v-0
```

Implementations MAY add further registered strategies without allowing profiles to define their byte programs.

A profile referencing an unknown raster strategy MUST be rejected.

### 12.4 Raster band constraints

`bandHeightDots` contains:

```json
{
  "min": 1,
  "preferred": 128,
  "max": 255
}
```

Rules:

```text
1 <= min <= preferred <= max
```

All values MUST be positive integers and MUST remain within resource limits.

Semantics:

- `min` is the minimum valid raster band height accepted by the profile/strategy combination;
- `preferred` is the profile's recommended preparation band height;
- `max` is the maximum reliable raster band height for one prepared raster operation under the profile.

These values constrain physical raster planning.

They are NOT transport write chunk sizes.

A transport MAY split the encoded bytes for one raster band into many writes without changing `PreparedPrint`.

### 12.5 Image constraints

For v1, profile-level image constraints are intentionally limited to printer raster geometry and registered raster-strategy constraints.

The effective maximum raster image width is:

```text
raster.maxWidthDots
```

Decoded image resource limits, source image file types, image pixel-count limits, dithering algorithms, brightness, contrast, and gamma are not printer-profile capabilities. They belong to image decoding, `core-raster`, `PrintOptions`, and `docs/RESOURCE_LIMITS.md`.

If a printer requires a physical raster restriction beyond width and band height, it MUST be represented by a documented registered strategy or validated quirk. It MUST NOT be encoded as an arbitrary byte template.

---

## 13. Native QR Capability

`nativeQr` MUST be present.

Unsupported form:

```json
{
  "supported": false,
  "strategies": []
}
```

Supported form:

```json
{
  "supported": true,
  "strategies": ["escpos.qr.model2"],
  "preferredStrategy": "escpos.qr.model2",
  "models": ["model2"],
  "moduleSize": {
    "min": 1,
    "max": 8
  },
  "errorCorrection": ["L", "M", "Q", "H"]
}
```

When supported:

- `strategies` MUST be non-empty;
- `preferredStrategy` MUST be one of `strategies`;
- every strategy MUST be registered;
- `models` MUST be non-empty;
- `moduleSize.min` and `moduleSize.max` MUST be positive integers with `min <= max`;
- `errorCorrection` MUST be a non-empty set containing only registered values;
- duplicate values are invalid.

Baseline v1 identifiers:

```text
strategy: escpos.qr.model2
model:    model2
EC:       L, M, Q, H
```

The profile declares what native QR behavior is reliable.

A QR symbol printed by a printer's own self-test or firmware menu proves that the printer can internally produce a QR symbol in that context. It does not by itself prove that a host-accessible native QR command strategy, QR model, module-size range, payload limit, or error-correction set is supported. `nativeQr.supported = true` therefore requires command-path evidence appropriate to the registered strategy.

The preparation engine decides whether a specific QR operation uses native QR or raster QR based on:

```text
effective PrinterProfile
+
QR content
+
PrintOptions
+
strategy constraints
```

The selected strategy and final dimensions MUST be represented in `PreparedPrint`.

The encoder MUST NOT decide native-versus-raster QR.

---

## 14. Native Barcode Capability

The PRD permits native barcode support, therefore v1 defines a capability block.

`nativeBarcode` MUST be present.

Unsupported form:

```json
{
  "supported": false,
  "strategies": []
}
```

Supported form:

```json
{
  "supported": true,
  "strategies": ["escpos.barcode.gs-k"],
  "preferredStrategy": "escpos.barcode.gs-k",
  "symbologies": ["code128", "ean13"]
}
```

When supported:

- `strategies` MUST be non-empty;
- `preferredStrategy` MUST be one of `strategies`;
- every strategy MUST be registered;
- `symbologies` MUST be non-empty;
- each symbology MUST be known to the implementation;
- payload validity remains symbology- and strategy-specific.

Baseline v1 strategy identifier:

```text
escpos.barcode.gs-k
```

The format does not embed arbitrary barcode command bytes.

A barcode printed by a printer's own self-test or firmware menu proves internal symbol-generation capability in that context. It does not by itself prove that the corresponding host-accessible native barcode strategy or all parameters for that symbology are supported. Native barcode profile claims require command-path evidence appropriate to the registered strategy.

If a requested barcode cannot be emitted natively, preparation MAY choose raster barcode where implemented and allowed by `PrintOptions`; otherwise it MUST emit a structured diagnostic.

---

## 15. Cutter Capability

`cutter` MUST be present.

Printer without an automatic cutter:

```json
{
  "supported": false,
  "modes": []
}
```

Printer with a cutter:

```json
{
  "supported": true,
  "modes": ["full", "partial"],
  "strategy": "escpos.cut.gs-v"
}
```

Rules:

- `modes` may contain only `full` and/or `partial` in v1;
- modes MUST be unique;
- when `supported` is `false`, `modes` MUST be empty and `strategy` MUST be absent;
- when `supported` is `true`, `modes` MUST be non-empty and `strategy` MUST reference a registered cut strategy;
- unsupported cut modes MUST NOT be emitted by the encoder.

Baseline v1 cut strategy identifier:

```text
escpos.cut.gs-v
```

A manual cut guide is not cutter capability. It is printed content selected during preparation.

An automatic cut is a physical printer operation represented in `PreparedPrint`.

Physical inspection or repeatable hardware behavior can establish that a device has no automatic cutter. In that case the production profile SHOULD declare `supported = false`; Rastrio MUST NOT infer cutter support merely from generic ESC/POS documentation.

---

## 16. Printer-Side Buffering Constraints

`printerBuffer` is OPTIONAL because many printer profiles may not have validated physical buffer data.

Example:

```json
{
  "recommendedMaxBurstBytes": 4096,
  "recommendedPauseAfterBurstMs": 10
}
```

These properties describe validated printer-side reliability guidance, not a transport write API.

### 16.1 `recommendedMaxBurstBytes`

If present, it MUST be a positive integer.

It means:

> the maximum amount of encoded data Rastrio should preferably send toward this printer before allowing the printer-side pacing policy to yield or pause.

It is not the required size of each Bluetooth, USB, serial, WebUSB, or other transport write.

### 16.2 `recommendedPauseAfterBurstMs`

If present, it MUST be a non-negative integer.

It is printer-side pacing guidance derived from printer behavior.

It MUST NOT be treated as a Bluetooth- or USB-specific timing requirement.

### 16.3 Unknown buffer characteristics

If `printerBuffer` is absent, Rastrio MUST treat printer-side guidance as unknown and use safe application/transport defaults.

It MUST NOT invent a model-specific value from the printer name.

---

## 17. Transport Constraints Are Not Profile Capabilities

The following concerns MUST remain distinct.

### 17.1 Printer protocol/dialect strategy

Describes which known ESC/POS-compatible protocol implementation should serialize a prepared operation.

Examples:

```text
escpos.raster.gs-v-0
escpos.qr.model2
escpos.cut.gs-v
```

This belongs to `PrinterProfile`.

### 17.2 Printer buffer limits

Describe physical printer-side limits or safe pacing guidance.

Examples:

```text
recommended raster band height
recommended maximum burst bytes
recommended pause after a printer-side burst
```

These MAY belong to `PrinterProfile` when empirically validated.

### 17.3 Transport write chunk size

Describes how many encoded bytes a particular transport/session should pass to an OS or connection API in one write.

Examples include:

```text
RFCOMM write chunk
USB bulk-transfer chunk
WebUSB transfer chunk
serial write chunk
```

This MUST NOT be stored in `.tcfg`.

### 17.4 Transport pacing

Describes timing imposed by a transport implementation or connection/session.

Examples include:

```text
delay between RFCOMM writes
USB timeout
serial flow-control behavior
browser API scheduling
```

This MUST NOT be stored in `.tcfg` unless a value is genuinely a physical printer constraint independent of transport, in which case it belongs under printer-side guidance with printer-side semantics.

### 17.5 Effective transmission policy

The print session MAY combine:

```text
printer-side constraints
∩
transport/session constraints
```

to produce a safe transmission policy.

That policy MUST NOT cause document re-layout or re-dithering.

A transport change alone MUST NOT require a new `PreparedPrint`.

---

## 18. Status Query Capability

`statusQuery` MUST be present.

Unsupported form:

```json
{
  "supported": false,
  "strategies": []
}
```

Supported form:

```json
{
  "supported": true,
  "strategies": ["escpos.status.dle-eot"],
  "preferredStrategy": "escpos.status.dle-eot"
}
```

Rules:

- supported status querying is a printer/protocol capability;
- every strategy MUST be registered;
- `preferredStrategy`, when required by support, MUST appear in `strategies`;
- actual use also requires a transport/session capable of receiving the corresponding response;
- a profile MUST NOT imply that a one-way transport can perform bidirectional status queries.

Baseline v1 strategy identifier:

```text
escpos.status.dle-eot
```

Status-query support MUST NOT be interpreted as proof that physical paper output completed successfully unless the specific registered strategy and preparation/session contract provide that guarantee.

---

## 19. Protocol-Family Boundary

`protocol` MUST be present.

The `protocol` object is a tagged protocol-family descriptor. Its `family` property determines which trusted protocol schema, validator, strategy registry, and encoder implementation apply.

Schema v1 defines exactly one variant:

```json
{
  "family": "escpos",
  "dialectId": "escpos.generic",
  "initializationStrategy": "escpos.init.standard"
}
```

### 19.1 Protocol family

For schema version 1:

```text
family = escpos
```

is the only normative protocol-family value.

This supports different vendors, models, media widths, firmware families, transports, and ESC/POS-compatible dialects without making any one device the default.

A future non-ESC/POS protocol family requires an explicit `.tcfg` specification/schema update and a trusted encoder implementation. It MUST NOT be represented by pretending to be `escpos`, by overloading ESC/POS fields, or by importing arbitrary byte-generating logic.

### 19.2 Dialect

`dialectId` is a registered identifier for a validated ESC/POS-compatible dialect family.

Baseline v1 dialect:

```text
escpos.generic
```

A dialect ID:

- selects implementation behavior already present in trusted Rastrio code;
- defines which strategy IDs and parameters are compatible;
- does not contain executable code;
- MUST be rejected if unknown.

### 19.3 Initialization

`initializationStrategy` MUST be a registered strategy compatible with the selected dialect.

Baseline v1 identifier:

```text
escpos.init.standard
```

### 19.4 Known strategies, not programmable commands

Profiles MAY select from registered implementation strategies.

Profiles MUST NOT define:

- literal command byte arrays;
- arbitrary command prefixes/suffixes;
- format strings converted directly to bytes;
- script expressions;
- templated bytecode;
- reflection targets;
- class names to instantiate;
- URLs from which command code is loaded.

A strategy identifier is a lookup key into trusted Rastrio implementation code.

Any parameter attached to a strategy MUST have a schema owned by that strategy and MUST pass strategy-specific validation.

### 19.5 Protocol-family extension contract

The stable architectural boundary is:

```text
PrinterProfile
    └── protocol: ProtocolProfile
            ├── EscPosProtocolProfile   // schema v1
            └── future family variant  // future schema/specification
```

A future protocol-family variant MAY use fields that differ from ESC/POS `dialectId` and `initializationStrategy`.

When a future family is added:

1. the `.tcfg` schema version MUST be evolved explicitly;
2. the new `family` identifier MUST be registered;
3. its family-specific fields MUST have a strict schema;
4. its strategy identifiers MUST resolve only to trusted Rastrio implementation code;
5. a dedicated protocol encoder or equivalent trusted serializer MUST consume `PreparedPrint`;
6. document semantics, layout, `PrinterInstance`, `PrintOptions`, and transport identity MUST remain outside the protocol block;
7. a profile MUST NOT contain executable code or unrestricted raw byte programs;
8. unsupported protocol families MUST fail validation rather than falling back to ESC/POS.

The protocol block is therefore swappable by **validated family variant**, not by arbitrary commands.

---

## 20. Baseline v1 ESC/POS Strategy Registry

The following identifiers define the initial ESC/POS vocabulary used by schema v1 examples.

| Identifier | Purpose |
|---|---|
| `escpos.generic` | Baseline validated ESC/POS-compatible dialect family. |
| `escpos.init.standard` | Standard trusted initialization implementation. |
| `escpos.font.esc-m` | Trusted native-font selector implementation. |
| `escpos.code-page.esc-t` | Trusted code-page selector implementation. |
| `escpos.raster.gs-v-0` | Trusted raster implementation using the registered GS v 0 strategy. |
| `escpos.qr.model2` | Trusted native QR Model 2 strategy. |
| `escpos.barcode.gs-k` | Trusted native barcode strategy. |
| `escpos.cut.gs-v` | Trusted full/partial cutter strategy. |
| `escpos.status.dle-eot` | Trusted real-time status query strategy. |

The registry names are stable serialization identifiers.

The byte-level implementation of these strategies belongs in trusted `core-escpos` code and supporting protocol documentation. It MUST NOT be supplied by an imported profile.

An implementation MAY support additional registered IDs.

A profile referencing an unsupported ID MUST fail validation rather than degrading to a guessed implementation.

---

## 21. Known Quirks

`quirks` MUST be an array of registered quirk identifiers.

Example:

```json
{
  "quirks": [
    "escpos.raster.requires-feed-after-band"
  ]
}
```

Rules:

- each quirk ID MUST match the identifier grammar;
- quirk IDs MUST be unique;
- every quirk ID MUST be known to the running implementation;
- unknown quirks MUST cause validation failure;
- quirks MUST represent validated hardware/firmware behavior;
- quirks MUST NOT contain arbitrary parameters in v1;
- quirks MUST NOT inject raw bytes.

The baseline schema intentionally does not define conditional firmware expressions.

If a quirk applies only to a materially distinct firmware family and cannot safely apply to every device represented by one profile, maintainers SHOULD use a separate profile identity or revision rather than inventing a conditional expression language.

Evidence supporting a quirk SHOULD be recorded in `docs/ESC_POS_NOTES.md` and, where physical behavior is involved, `docs/HARDWARE_TESTS.md`.

---

## 22. Capability Overrides

The PRD permits validated user overrides, but those overrides belong to the user's `PrinterInstance`, not to the portable `.tcfg` document.

Therefore `.tcfg` v1 MUST NOT contain:

```text
overrides
userOverrides
instanceOverrides
transportOverrides
```

as top-level fields.

### 22.1 Override pipeline

The normative precedence is:

```text
base PrinterProfile loaded from .tcfg
        ↓
validated local PrinterProfileOverride
        ↓
EffectivePrinterProfile
        ↓
PrintOptions select among effective capabilities
        ↓
PreparedPrint
```

Transport/session constraints are applied later to transmission policy and do not become `EffectivePrinterProfile` fields unless they describe genuine physical printer behavior.

### 22.2 Override safety

A local `PrinterProfileOverride`:

- MUST use typed fields;
- MUST be validated using the same semantic constraints as a base profile;
- MUST NOT introduce raw command bytes;
- MUST NOT reference unknown strategy IDs;
- MUST NOT bypass capability dependencies;
- MUST NOT create impossible dimensions;
- MUST NOT contain transport identity as if it were profile data.

### 22.3 Override semantics

Overrides use field replacement, not object merge by arbitrary JSON rules.

For every overridable field:

- omitted means inherit the base profile value;
- present means replace that field after validation;
- arrays replace the complete corresponding base array unless a future typed override explicitly defines set operations;
- arbitrary JSON Merge Patch or JSON Patch is not part of v1.

The implementation SHOULD expose only fields that have a real calibration or compatibility use case.

### 22.4 Safe enabling/disabling

An override MAY disable a declared capability.

An override MAY enable or correct a capability only when the resulting effective profile is fully valid and uses registered strategies.

The UI SHOULD require an advanced/Printer Lab workflow for changes that can cause protocol incompatibility or physical output changes.

### 22.5 Exporting corrected profiles

If a user intentionally exports corrected model-level capability data as a reusable `.tcfg`, the exported file becomes a normal standalone `PrinterProfile`.

The export MUST NOT copy private `PrinterInstance` transport identity into the file.

---

## 23. No Profile Inheritance in v1

Schema version 1 does not support profile inheritance.

The following concepts are not valid v1 properties:

```text
extends
inherits
include
import
baseProfile
parentProfile
remoteProfile
```

A v1 `.tcfg` file MUST be self-contained.

This avoids:

- resolution order ambiguity;
- network/file dependencies;
- cyclic inheritance;
- partial trust of external profile fragments;
- unclear override precedence.

If inheritance is later justified, it requires an explicit schema/specification update.

---

## 24. Unknown Properties and Enum Handling

### 24.1 Unknown properties

Schema version 1 uses strict property handling.

A v1 parser MUST reject unknown properties at every schema-defined object level.

Rationale:

- catches misspellings;
- avoids silently ignored security-sensitive input;
- prevents accidental feature negotiation;
- makes compatibility behavior deterministic.

Future fields require an explicit schema evolution decision.

### 24.2 Unknown enum values

Unknown enum values MUST be rejected.

This applies to, for example:

- protocol families;
- cutter modes;
- QR models;
- QR error-correction levels;
- barcode symbologies;
- registered strategy IDs;
- registered repertoire IDs;
- registered quirk IDs.

A parser MUST NOT map an unknown value to a default merely because it appears similar to a known value.

### 24.3 Case

Machine identifiers and enum values are case-sensitive.

Normative identifiers in v1 use lowercase ASCII except QR error-correction values `L`, `M`, `Q`, and `H`.

### 24.4 Parser-library conformance

A successful library deserialization is not proof of `.tcfg` validity.

Validation MUST operate on a representation that still preserves enough information to detect:

- duplicate object keys;
- unknown properties;
- exact token/value types;
- prohibited lenient syntax;
- resource-limit violations.

An implementation MUST NOT configure a parser to ignore unknown properties (for example, an `ignoreUnknownKeys`-equivalent mode) and then claim v1 conformance solely because the resulting object model validates.

If duplicate keys have already been collapsed into a map, duplicate-key validation has occurred too late.

---

## 25. String and Identifier Safety

### 25.1 Machine identifiers

Machine identifiers MUST:

- be bounded in length;
- use the defined ASCII identifier grammar unless an enum defines a narrower grammar;
- not contain whitespace;
- not be treated as paths;
- not be used to construct class names for reflection;
- not be executed;
- not be interpolated into raw command templates.

### 25.2 Human-readable strings

Human-readable strings MAY contain Unicode.

They MUST:

- be valid UTF-8 JSON strings;
- remain within resource limits;
- be treated as untrusted display text;
- not be evaluated as markup or code merely because they contain markup-like syntax.

Implementations SHOULD avoid logging imported display strings unless needed for a local diagnostic.

---

## 26. Numerical Validation

Exact global hard limits belong in `docs/RESOURCE_LIMITS.md`.

Within those limits, v1 imposes these semantic rules:

- DPI values MUST be positive integers.
- Printable width MUST be a positive integer.
- Font cell dimensions MUST be positive integers.
- Font line advance, when present, MUST be positive.
- Text scale values MUST be positive integers.
- Raster widths MUST be positive.
- Raster `maxWidthDots` MUST NOT exceed printable width.
- Raster band heights MUST satisfy `min <= preferred <= max`.
- QR module-size bounds MUST satisfy `min <= max`.
- `profileRevision` MUST be positive.
- Printer buffer byte counts MUST be positive when present.
- Printer-side delay values MUST be non-negative.
- Integer arithmetic used for validation MUST detect overflow.

A parser MUST reject values that cannot be represented safely by the implementation's domain types.

---

## 27. Cross-Field Capability Dependencies

The following dependencies are normative.

### 27.1 Native text

If:

```json
{
  "nativeText": {
    "supported": true
  }
}
```

then:

- at least one font is required;
- at least one code page/repertoire is required;
- font geometry must be valid;
- all referenced selectors must be compatible with the protocol dialect.

If `supported` is false, native text arrays must be empty.

### 27.2 Raster

If raster is supported:

- at least one raster strategy is required;
- a preferred strategy is required;
- raster width and band constraints are required;
- preferred strategy must be contained in the supported strategy set.

If raster is unsupported, raster-only constraint fields must be absent.

### 27.3 Native QR

If native QR is supported:

- at least one native QR strategy is required;
- preferred strategy is required;
- model set is required;
- error-correction set is required;
- module-size range is required.

If unsupported, capability-specific fields must be absent.

### 27.4 Native barcode

If native barcode is supported:

- at least one strategy is required;
- preferred strategy is required;
- at least one symbology is required.

### 27.5 Cutter

If cutter is supported:

- at least one mode is required;
- a registered cutter strategy is required.

### 27.6 Status queries

If status querying is supported:

- at least one strategy is required;
- preferred strategy is required.

### 27.7 At least one output path

A valid profile MUST support at least one of:

```text
native text
raster
```

A profile supporting neither cannot produce ordinary Rastrio document output and MUST be rejected.

### 27.8 Strategy/dialect compatibility

Every strategy referenced anywhere in a profile MUST be compatible with `protocol.dialectId`.

The validator MUST reject a strategy/dialect combination that the trusted implementation does not recognize as compatible.

---

## 28. Invalid Combinations

Examples of invalid v1 profiles include:

- `schemaVersion` other than `1` in a v1-only reader;
- duplicate font IDs;
- duplicate code-page IDs;
- unknown strategy IDs;
- unknown quirk IDs;
- `nativeText.supported = false` with non-empty fonts;
- `raster.supported = false` with a `preferredStrategy`;
- raster maximum width greater than printable width;
- raster preferred band height outside min/max;
- native QR enabled without a QR strategy;
- native barcode enabled without symbologies;
- cutter enabled with no cut mode;
- cutter disabled with a cut strategy;
- status query enabled with no strategy;
- code-page selector parameter outside its strategy-specific valid domain;
- a native-font selector incompatible with the selected dialect;
- a profile supporting neither native text nor raster;
- an unknown property such as `rawCommands`;
- a raw byte field even if its bytes would form valid ESC/POS;
- a transport field such as `bluetoothAddress`;
- a transport field such as `writeChunkSize`;
- an inheritance field such as `extends`.

---

## 29. Canonical Serialization

A Rastrio writer SHOULD emit deterministic JSON suitable for reviewable fixtures.

Recommended canonicalization:

- UTF-8;
- no BOM;
- two-space indentation for repository fixtures;
- stable top-level property order matching this specification;
- stable nested property order;
- profile-local arrays sorted only where order has no semantic meaning;
- no duplicate array members where uniqueness is required;
- no insignificant synthetic fields.

JSON object property order is not semantically significant.

A reader MUST NOT require the canonical output order.

Round-trip equality tests SHOULD compare the decoded domain model, not raw whitespace.

Golden fixtures MAY additionally compare canonical serialized JSON.

---

## 30. Complete Synthetic Test Profile — Narrow

The following profile is intentionally synthetic. Its values are suitable for deterministic tests and MUST NOT be interpreted as H50i specifications.

```json
{
  "format": "rastrio-tcfg",
  "schemaVersion": 1,
  "profileId": "org.rastrio.test.synthetic-narrow",
  "profileRevision": 1,
  "display": {
    "name": "Synthetic Narrow 203 dpi",
    "manufacturer": "Rastrio Test Fixture",
    "model": "Synthetic-Narrow"
  },
  "geometry": {
    "horizontalDpi": 203,
    "verticalDpi": 203,
    "printableWidthDots": 384
  },
  "nativeText": {
    "supported": true,
    "fonts": [
      {
        "id": "font-a",
        "cellWidthDots": 12,
        "cellHeightDots": 24,
        "lineAdvanceDots": 24,
        "selector": {
          "strategy": "escpos.font.esc-m",
          "parameter": 0
        }
      },
      {
        "id": "font-b",
        "cellWidthDots": 9,
        "cellHeightDots": 17,
        "lineAdvanceDots": 17,
        "selector": {
          "strategy": "escpos.font.esc-m",
          "parameter": 1
        }
      }
    ],
    "styles": {
      "bold": true,
      "underline": true,
      "widthScales": [1, 2],
      "heightScales": [1, 2]
    },
    "codePages": [
      {
        "id": "cp437",
        "repertoire": "cp437",
        "selector": {
          "strategy": "escpos.code-page.esc-t",
          "parameter": 0
        }
      }
    ]
  },
  "raster": {
    "supported": true,
    "strategies": [
      "escpos.raster.gs-v-0"
    ],
    "preferredStrategy": "escpos.raster.gs-v-0",
    "maxWidthDots": 384,
    "bandHeightDots": {
      "min": 1,
      "preferred": 128,
      "max": 255
    }
  },
  "nativeQr": {
    "supported": false,
    "strategies": []
  },
  "nativeBarcode": {
    "supported": false,
    "strategies": []
  },
  "cutter": {
    "supported": false,
    "modes": []
  },
  "printerBuffer": {
    "recommendedMaxBurstBytes": 4096,
    "recommendedPauseAfterBurstMs": 10
  },
  "statusQuery": {
    "supported": false,
    "strategies": []
  },
  "protocol": {
    "family": "escpos",
    "dialectId": "escpos.generic",
    "initializationStrategy": "escpos.init.standard"
  },
  "quirks": []
}
```

Recommended fixture name:

```text
test-fixtures/profiles/synthetic-narrow.tcfg
```

---

## 31. Complete Synthetic Test Profile — 80 mm Class

The following profile is also synthetic.

```json
{
  "format": "rastrio-tcfg",
  "schemaVersion": 1,
  "profileId": "org.rastrio.test.synthetic-80mm",
  "profileRevision": 1,
  "display": {
    "name": "Synthetic 80 mm 203 dpi",
    "manufacturer": "Rastrio Test Fixture",
    "model": "Synthetic-80"
  },
  "geometry": {
    "horizontalDpi": 203,
    "verticalDpi": 203,
    "printableWidthDots": 576
  },
  "nativeText": {
    "supported": true,
    "fonts": [
      {
        "id": "font-a",
        "cellWidthDots": 12,
        "cellHeightDots": 24,
        "lineAdvanceDots": 24,
        "selector": {
          "strategy": "escpos.font.esc-m",
          "parameter": 0
        }
      },
      {
        "id": "font-b",
        "cellWidthDots": 9,
        "cellHeightDots": 17,
        "lineAdvanceDots": 17,
        "selector": {
          "strategy": "escpos.font.esc-m",
          "parameter": 1
        }
      }
    ],
    "styles": {
      "bold": true,
      "underline": true,
      "widthScales": [1, 2],
      "heightScales": [1, 2]
    },
    "codePages": [
      {
        "id": "cp437",
        "repertoire": "cp437",
        "selector": {
          "strategy": "escpos.code-page.esc-t",
          "parameter": 0
        }
      }
    ]
  },
  "raster": {
    "supported": true,
    "strategies": [
      "escpos.raster.gs-v-0"
    ],
    "preferredStrategy": "escpos.raster.gs-v-0",
    "maxWidthDots": 576,
    "bandHeightDots": {
      "min": 1,
      "preferred": 192,
      "max": 512
    }
  },
  "nativeQr": {
    "supported": true,
    "strategies": [
      "escpos.qr.model2"
    ],
    "preferredStrategy": "escpos.qr.model2",
    "models": [
      "model2"
    ],
    "moduleSize": {
      "min": 1,
      "max": 8
    },
    "errorCorrection": [
      "L",
      "M",
      "Q",
      "H"
    ]
  },
  "nativeBarcode": {
    "supported": true,
    "strategies": [
      "escpos.barcode.gs-k"
    ],
    "preferredStrategy": "escpos.barcode.gs-k",
    "symbologies": [
      "code128",
      "ean13"
    ]
  },
  "cutter": {
    "supported": true,
    "modes": [
      "full",
      "partial"
    ],
    "strategy": "escpos.cut.gs-v"
  },
  "printerBuffer": {
    "recommendedMaxBurstBytes": 8192,
    "recommendedPauseAfterBurstMs": 5
  },
  "statusQuery": {
    "supported": true,
    "strategies": [
      "escpos.status.dle-eot"
    ],
    "preferredStrategy": "escpos.status.dle-eot"
  },
  "protocol": {
    "family": "escpos",
    "dialectId": "escpos.generic",
    "initializationStrategy": "escpos.init.standard"
  },
  "quirks": []
}
```

Recommended fixture name:

```text
test-fixtures/profiles/synthetic-80mm.tcfg
```

The dimensions, buffers, and feature set above exist to exercise code paths. They are not claims about any commercial printer.

---

## 32. Synthetic Raster-Only Test Profile

A raster-only fixture is useful for testing native-text fallback and capability selection.

```json
{
  "format": "rastrio-tcfg",
  "schemaVersion": 1,
  "profileId": "org.rastrio.test.synthetic-raster-only",
  "profileRevision": 1,
  "display": {
    "name": "Synthetic Raster-Only Printer"
  },
  "geometry": {
    "horizontalDpi": 203,
    "verticalDpi": 203,
    "printableWidthDots": 384
  },
  "nativeText": {
    "supported": false,
    "fonts": [],
    "styles": {
      "bold": false,
      "underline": false,
      "widthScales": [],
      "heightScales": []
    },
    "codePages": []
  },
  "raster": {
    "supported": true,
    "strategies": [
      "escpos.raster.gs-v-0"
    ],
    "preferredStrategy": "escpos.raster.gs-v-0",
    "maxWidthDots": 384,
    "bandHeightDots": {
      "min": 1,
      "preferred": 64,
      "max": 128
    }
  },
  "nativeQr": {
    "supported": false,
    "strategies": []
  },
  "nativeBarcode": {
    "supported": false,
    "strategies": []
  },
  "cutter": {
    "supported": false,
    "modes": []
  },
  "statusQuery": {
    "supported": false,
    "strategies": []
  },
  "protocol": {
    "family": "escpos",
    "dialectId": "escpos.generic",
    "initializationStrategy": "escpos.init.standard"
  },
  "quirks": []
}
```

For this profile, text preparation cannot choose native text. Raster text is required.

---

## 33. Hardware-Backed Production Profile Requirements

Synthetic profiles exist to test Rastrio's capability model. A production profile makes claims about real hardware and therefore requires evidence.

No production profile is privileged by this specification. The same rules apply to every vendor, model, and firmware family.

### 33.1 Evidence categories

Profile maintainers SHOULD distinguish at least:

```text
vendor/specification-derived
printer-reported self-test/configuration output
confirmed by automated byte-level test
confirmed by physical hardware behavior
inferred/unverified
```

Evidence SHOULD be recorded in:

```text
docs/ESC_POS_NOTES.md
docs/HARDWARE_TESTS.md
profile-maintenance fixtures/notes where appropriate
```

Evidence provenance is maintenance metadata and is not serialized into `.tcfg` v1.

### 33.2 Claim granularity

Evidence supports only the claim it actually establishes.

Examples:

- a self-test reporting physical print width in millimetres does not establish `printableWidthDots` without validated dot geometry;
- a self-test reporting a native font size such as `12x24` can support the reported/default cell geometry but does not establish font selector parameters, line advance, alternate fonts, or style behavior;
- a self-test reporting a charset such as `CP437` supports a current/default repertoire observation but does not establish every supported code page or switching command;
- a QR symbol printed by firmware does not establish the host-accessible native QR command strategy;
- an EAN-13 symbol printed by firmware does not establish the host-accessible native barcode command strategy;
- physical absence of an automatic cutter is sufficient to model cutter support as false;
- current temperature, voltage, print-density setting, autofeed setting, pairing PIN, Bluetooth firmware version, USB-port label, and similar runtime/transport/configuration observations are not automatically portable profile capabilities.

A maintainer MUST NOT promote an inference into a stronger capability claim merely because that behavior is common among ESC/POS-compatible printers.

### 33.3 Production-profile acceptance

Before an output-affecting field is committed to a maintained production profile, the evidence SHOULD be sufficient for that field's semantics.

As applicable, profile maintainers SHOULD validate:

```text
effective horizontal/vertical DPI
reliable printable width in dots
native font geometry
code-page repertoire and selector behavior
raster command compatibility
safe raster-band constraints
native QR command behavior
native barcode command behavior
cutter capability
printer-side buffer/pacing guidance
status-query behavior
firmware-specific quirks
```

Unknown facts SHOULD remain unknown rather than being replaced with generic assumptions.

If an optional capability is unverified, the production profile SHOULD disable or omit it as allowed by the schema.

If an unknown fact is required for safe ordinary output—such as authoritative dot geometry—the profile MUST remain development/incomplete outside the maintained production profile set until that fact is established.

### 33.4 Reference devices

A project may use one or more physical reference devices to validate implementation behavior.

The Helett H50i BillQuick Go is currently one such reference device because it is available for testing. It has no special schema rules and MUST pass exactly the same validation and evidence requirements as every other production profile.

No H50i width, DPI, command constant, code page, raster rule, or quirk may be hard-coded into generic Core logic.

---

## 34. Profile vs Instance Example

### 34.1 Portable `.tcfg`

The portable profile may say:

```json
{
  "profileId": "org.rastrio.test.synthetic-narrow",
  "profileRevision": 1,
  "display": {
    "name": "Synthetic Narrow 203 dpi"
  },
  "geometry": {
    "horizontalDpi": 203,
    "verticalDpi": 203,
    "printableWidthDots": 384
  }
}
```

The omitted properties above are omitted only to illustrate the distinction; this snippet is not a complete valid `.tcfg`.

### 34.2 Local `PrinterInstance` — NOT `.tcfg`

Application-local persistence may conceptually contain:

```json
{
  "instanceId": "local-printer-7",
  "displayName": "Kitchen Printer",
  "transport": {
    "type": "bluetooth-classic",
    "deviceIdentity": "AA:BB:CC:DD:EE:FF"
  },
  "profileRef": {
    "profileId": "org.rastrio.test.synthetic-narrow",
    "profileRevision": 1
  },
  "profileOverrides": {
    "printableWidthDots": 376
  },
  "sessionPreferences": {
    "writeChunkSize": 512,
    "writeTimeoutMs": 5000
  }
}
```

This example contains data that MUST NOT appear in `.tcfg`:

```text
deviceIdentity
writeChunkSize
writeTimeoutMs
instanceId
```

The exact `PrinterInstance` persistence schema is outside this specification.

### 34.3 Per-job `PrintOptions` — NOT `.tcfg`

A print preparation may conceptually use:

```json
{
  "textStrategy": "auto",
  "dithering": "atkinson",
  "qrStrategy": "auto",
  "manualCutGuides": true
}
```

These are job choices, not profile capabilities.

---

## 35. Validation Pipeline

A `.tcfg` file MUST be fully validated before it reaches `core-printer`.

Recommended pipeline:

```text
untrusted bytes
    ↓
resource-limit precheck
    ↓
UTF-8 decode
    ↓
bounded strict-standard-JSON validation/tokenization
    ↓
duplicate-key / unknown-property / lenient-syntax rejection
    ↓
model decode
    ↓
top-level format/version validation
    ↓
strict structural schema validation
    ↓
identifier/enum validation
    ↓
numerical validation
    ↓
capability dependency validation
    ↓
strategy/dialect registry validation
    ↓
quirk registry validation
    ↓
construct immutable PrinterProfile
    ↓
core-printer
```

No partially validated imported profile may be used for physical preparation.

### 35.1 Validation result

Validation SHOULD return structured diagnostics.

Profile-related diagnostic codes SHOULD use the `PRFxxx` family.

A fatal profile diagnostic MUST prevent the profile from reaching preparation.

Diagnostics SHOULD identify fields by safe structural path, for example:

```text
$.raster.bandHeightDots.preferred
$.nativeText.fonts[1].cellWidthDots
```

Diagnostics SHOULD NOT echo large or sensitive input strings unnecessarily.

---

## 36. Security

Profiles are untrusted input.

### 36.1 Bounded parsing

Readers MUST apply limits from `docs/RESOURCE_LIMITS.md` before and during parsing.

The current provisional application-policy baselines are:

```text
.tcfg file size                  1 MiB
JSON nesting                     64 levels
individual string value          64 KiB UTF-8
total decoded JSON nodes/tokens  100,000
```

These values are trusted resource-policy defaults, not `.tcfg` schema compatibility semantics. They MAY be revised by trusted application policy without changing schema version 1.

Limits MUST cover at least:

- maximum `.tcfg` file size;
- maximum JSON nesting depth;
- maximum total JSON node/property count or equivalent parser work;
- maximum string length;
- maximum array length;
- maximum number of fonts;
- maximum number of code pages;
- maximum number of strategy IDs;
- maximum number of quirks;
- numerical sanity bounds.

A parser MUST fail cleanly when a limit is exceeded.

### 36.2 No executable/raw command injection

A valid v1 profile MUST NOT contain arbitrary executable commands or unrestricted raw byte templates.

The following must be rejected:

```json
{
  "rawCommands": [27, 64]
}
```

```json
{
  "initializeHex": "1b40"
}
```

```json
{
  "protocol": {
    "family": "escpos",
    "script": "emit(0x1b,0x40)"
  }
}
```

```json
{
  "raster": {
    "strategy": "raw:1d7630..."
  }
}
```

These are invalid both because they are outside the schema and because they violate the trusted-strategy architecture.

### 36.3 Strategy resolution

Strategy identifiers MUST resolve only through a trusted in-process registry compiled or otherwise shipped with Rastrio.

Imported profiles MUST NOT:

- load code;
- load plugins;
- reference executable files;
- reference remote scripts;
- instantiate arbitrary classes;
- supply byte-generating expressions.

### 36.4 Safe strings

Display strings and identifiers MUST never be interpreted as:

- shell commands;
- file paths to open automatically;
- URLs to fetch automatically;
- Markdown/HTML to execute;
- ESC/POS bytes.

### 36.5 Validation before `core-printer`

`core-printer` MAY assume that a `PrinterProfile` domain object has passed schema and semantic validation.

Untrusted JSON objects MUST NOT be passed directly to printer preparation.

### 36.6 Logging

Imported profile content SHOULD be logged minimally.

Safe operational logs may include:

- profile ID;
- profile revision;
- diagnostic code;
- validated dimensions;
- strategy identifiers.

Large raw input or unrelated user-private printer identity MUST NOT be logged by default.

---

## 37. Compatibility and Versioning

### 37.1 Independent versions

The following versions are distinct:

```text
Rastrio application version
.tcfg schemaVersion
profileRevision
registered strategy implementation version/internal code revision
```

They MUST NOT be conflated.

### 37.2 Reading v1

A v1 reader:

- MUST require `format = "rastrio-tcfg"`;
- MUST require `schemaVersion = 1`;
- MUST reject unknown schema properties;
- MUST reject unknown required semantic identifiers;
- MUST validate every capability before constructing `PrinterProfile`.

### 37.3 Future schema versions

A future schema version MUST use a new integer value.

An implementation that does not support that value MUST reject the file with a structured unsupported-version diagnostic.

Future versions MUST NOT rely on v1 readers silently ignoring new fields.

### 37.4 Migration

If a later Rastrio version supports both v1 and a newer internal schema:

1. parse the source according to its declared schema version;
2. fully validate that source version;
3. perform a deterministic explicit migration;
4. validate the migrated representation;
5. construct the current immutable domain model.

Migration MUST NOT reinterpret an invalid v1 profile as valid by guessing intended fields.

Schema migration SHOULD preserve:

```text
profileId
profileRevision
behavioral meaning
```

unless the migration explicitly documents why preservation is impossible.

### 37.5 Profile content revisions

Changing hardware facts under an existing `profileId` SHOULD increment `profileRevision`.

A cached or persisted `PrinterInstance` reference SHOULD retain enough profile identity/revision information for the application to detect that a profile update occurred.

The policy for automatically adopting a newer profile revision is application-level and outside this file format specification.

---

## 38. Override Compatibility

A stored local override created for one profile revision may become invalid against another revision.

Therefore:

- overrides MUST be revalidated after the base profile changes;
- invalid overrides MUST NOT be silently retained in the effective profile;
- the application SHOULD surface a diagnostic and require review where semantics changed;
- protocol safety rules always take precedence over persisted override data.

The override layer MUST NOT weaken raw-command restrictions.

---

## 39. Test Requirements

`.tcfg` support is incomplete without automated tests.

### 39.1 Serialization and JSON-conformance tests

Required tests:

- decode a minimal valid profile;
- encode a valid profile;
- encode/decode round trip;
- deterministic canonical fixture output where used;
- reject invalid UTF-8;
- reject malformed JSON;
- reject JSON comments;
- reject trailing commas;
- reject duplicate object keys before collapse/overwrite;
- reject single-quoted strings;
- reject unquoted property names;
- reject `NaN`, `Infinity`, and `-Infinity`;
- reject invalid standard-JSON number grammar;
- reject illegal `null` values;
- reject missing required fields;
- reject unknown top-level and nested properties even if the selected JSON library could ignore them;
- reject wrong `format`;
- reject unsupported `schemaVersion`.

These tests MUST exercise the production reader path rather than only an isolated validator.

### 39.2 Structural validation tests

Required tests:

- unknown top-level property;
- unknown nested property;
- duplicate JSON property;
- duplicate semantic IDs;
- invalid identifier grammar;
- empty required strings;
- string resource-limit violation;
- excessive nesting;
- excessive array counts.

### 39.3 Geometry tests

Required tests:

- zero DPI;
- negative/invalid DPI representation;
- excessive DPI;
- zero printable width;
- excessive printable width;
- integer-overflow paths;
- raster max width greater than printable width.

### 39.4 Native text tests

Required tests:

- supported native text with no font;
- invalid font geometry;
- duplicate font IDs;
- invalid font selector;
- empty code-page set;
- duplicate code-page IDs;
- unknown repertoire;
- unknown code-page selector;
- selector parameter outside strategy range;
- valid style combinations;
- unsupported style causing preparation fallback/diagnostic.

### 39.5 Raster tests

Required tests:

- supported raster with no strategy;
- unknown raster strategy;
- preferred strategy not in strategy set;
- invalid min/preferred/max band ordering;
- resource-limit band size;
- no raster constraints when unsupported;
- preparation uses declared preferred strategy when policy selects it;
- transport chunk changes do not change prepared raster geometry.

### 39.6 QR tests

Required tests:

- native QR disabled;
- valid native QR;
- no strategy when supported;
- unknown strategy;
- unsupported model;
- invalid module-size range;
- invalid error-correction value;
- preferred strategy missing from supported set;
- preparation chooses raster fallback when native is unavailable.

### 39.7 Barcode tests

Required tests:

- native barcode disabled;
- valid supported symbology;
- unsupported symbology;
- unknown strategy;
- missing symbology list;
- preparation fallback/diagnostic behavior.

### 39.8 Cutter tests

Required tests:

- no cutter;
- full cutter;
- partial cutter;
- both modes;
- unknown cut mode;
- missing cut strategy;
- strategy present while cutter unsupported.

### 39.9 Status-query tests

Required tests:

- unsupported status query;
- valid supported query;
- unknown strategy;
- supported with no preferred strategy;
- transport without receive support does not falsely provide status.

### 39.10 Protocol-family and strategy tests

Required tests:

- `family = escpos` accepted for valid v1 profile;
- unknown/future protocol family rejected by a v1 reader;
- non-ESC/POS family MUST NOT fall back to `escpos`;
- unknown dialect;
- unknown initialization strategy;
- incompatible dialect/strategy combination;
- arbitrary raw byte property rejected;
- arbitrary command template rejected;
- script-like field rejected.

### 39.11 Quirk tests

Required tests:

- known quirk accepted;
- unknown quirk rejected;
- duplicate quirk rejected;
- quirk incompatible with dialect rejected where applicable.

### 39.12 Override tests

Required tests:

- base profile only;
- one validated override;
- multiple independent overrides;
- omitted override field inherits;
- array replacement semantics;
- invalid width override rejected;
- invalid DPI override rejected;
- unknown strategy override rejected;
- raw-command override rejected;
- override revalidation after profile revision change;
- effective profile remains fully schema/semantically valid.

### 39.13 Profile-vs-instance boundary tests

Required tests MUST prove that `.tcfg` rejects fields representing:

- Bluetooth identity;
- USB identity;
- transport type for a user's physical connection;
- write chunk size;
- transport timeout;
- last-used timestamp;
- instance ID;
- pairing data.

### 39.14 Synthetic profile fixtures

At minimum, repository fixtures SHOULD include:

```text
synthetic-narrow.tcfg
synthetic-80mm.tcfg
synthetic-raster-only.tcfg
```

The project SHOULD add invalid fixtures for each major validator branch.

### 39.15 Hardware-backed profile tests

For every maintained production hardware profile:

- profile data SHOULD be linked to documented evidence;
- hardware tests SHOULD record profile ID and revision;
- changes to output-affecting profile fields SHOULD trigger relevant hardware regression tests;
- generic Core tests MUST prove no model-specific constants are required;
- evidence tests/fixtures SHOULD distinguish printer-reported facts from command-path-confirmed behavior.

The H50i is one hardware-backed profile candidate, not a special case.

---

## 40. Kotlin Model Guidance

Exact Kotlin names are implementation details, but a v1 model may conceptually resemble:

```kotlin
data class PrinterProfile(
    val format: String,
    val schemaVersion: Int,
    val profileId: String,
    val profileRevision: Int,
    val display: ProfileDisplay,
    val geometry: PrinterGeometry,
    val nativeText: NativeTextCapability,
    val raster: RasterCapability,
    val nativeQr: NativeQrCapability,
    val nativeBarcode: NativeBarcodeCapability,
    val cutter: CutterCapability,
    val printerBuffer: PrinterBufferGuidance?,
    val statusQuery: StatusQueryCapability,
    val protocol: ProtocolProfile,
    val quirks: List<String>,
)

sealed interface ProtocolProfile

data class EscPosProtocolProfile(
    val dialectId: String,
    val initializationStrategy: String,
) : ProtocolProfile
```

The names above are conceptual. Schema v1 requires only the ESC/POS variant, but the domain boundary SHOULD make the protocol-family discriminator explicit rather than spreading ESC/POS-only fields across `PrinterProfile`.

A future protocol family may add another validated `ProtocolProfile` variant under a future `.tcfg` schema version and a corresponding trusted encoder.

Core domain models SHOULD be immutable.

Parsing, structural validation, semantic validation, and domain construction SHOULD remain explicit steps.

No Android, Compose, Bluetooth, USB, or platform-native type may appear in the portable profile model.

---

## 41. Responsibilities by Module

### `core-profile`

Owns:

- `.tcfg` parsing;
- schema validation;
- semantic validation;
- profile domain models;
- registered capability identifiers;
- override validation and effective-profile construction;
- registered protocol dialect/strategy compatibility metadata;
- known quirk identifiers.

### `core-printer`

Consumes a fully validated effective profile and decides:

- layout constraints derived from printer geometry;
- native versus raster text;
- code-page selection;
- image sizing;
- raster band planning;
- native versus raster QR;
- native versus raster barcode;
- cut operations;
- printer-specific diagnostics;
- selected protocol strategy for each physical operation.

### `core-escpos`

Consumes `PreparedPrint` for profiles whose validated protocol family is `escpos` and serializes already-selected operations.

It MUST NOT:

- reinterpret `.tcfg`;
- reselect code pages;
- choose native/raster strategy;
- invent printer geometry;
- apply a new profile override;
- execute imported command templates.

### Future protocol encoder

A future non-ESC/POS protocol family SHOULD be implemented as its own trusted protocol serializer/encoder boundary rather than by adding model-name branches inside `core-escpos`.

Such an encoder consumes the same authoritative preparation layer and MUST obey the same rule:

> serialize the finalized physical plan; do not independently re-layout or reinterpret document semantics.

### Platform transport

Consumes encoded bytes/chunks.

It owns transport/session behavior such as:

- connect/disconnect;
- write chunking;
- write timeout;
- transport pacing;
- connection errors;
- partial transmission reporting.

It MUST NOT reinterpret printer capabilities.

---

## 42. Validation Checklist

A v1 `.tcfg` is valid only when all of the following are true:

1. The file is within resource limits.
2. The bytes decode as valid UTF-8 standard JSON.
3. No duplicate object keys or prohibited lenient JSON forms are present.
4. `format` is exactly `rastrio-tcfg`.
5. `schemaVersion` is exactly `1`.
6. No unknown properties are present at any schema-defined object level.
7. `profileId` and all machine IDs are syntactically valid.
8. `profileRevision` is valid.
9. Display strings are bounded and safe.
10. DPI and printable width are valid.
11. Native text capability is internally consistent.
12. Font geometry is valid.
13. Code-page repertoires are known.
14. Raster capability is internally consistent.
15. Raster widths and band constraints are valid.
16. Native QR capability is internally consistent.
17. Native barcode capability is internally consistent.
18. Cutter capability is internally consistent.
19. Status-query capability is internally consistent.
20. Printer buffer guidance, if present, is valid.
21. For schema v1, `protocol.family` is exactly `escpos`.
22. Protocol dialect is known.
23. Every strategy identifier is registered and dialect-compatible.
24. Every strategy parameter passes strategy-specific validation.
25. Every quirk is registered and compatible.
26. At least one ordinary output path exists.
27. No transport-instance identity is present.
28. No transport write/session tuning is present.
29. No raw executable command program or unrestricted byte template is present.

Only after all checks pass may the parser produce a `PrinterProfile` accepted by `core-printer`.

---

## 43. Resolved v1 Scope and Future Evolution

### 43.1 Resource-policy status

`docs/RESOURCE_LIMITS.md` already defines provisional application-policy baselines for `.tcfg`, including:

```text
file size                  1 MiB
JSON nesting               64 levels
individual string          64 KiB UTF-8
decoded JSON nodes/tokens  100,000
```

These values remain tuning/safety policy rather than schema compatibility semantics.

### 43.2 Hardware-profile status

Hardware facts are profile-maintenance evidence, not open schema questions.

A production profile may be added for any ESC/POS-compatible printer whose required v1 capabilities and strategies have been sufficiently validated.

The H50i is one available reference device only.

### 43.3 Additional ESC/POS strategy registry entries

The baseline ESC/POS registry in this specification is intentionally small.

Additional known ESC/POS-compatible strategies MAY be added when implementation and hardware evidence justify them. New entries remain trusted implementation identifiers rather than profile-supplied command programs.

If a proposed strategy requires new profile data that cannot be expressed safely by existing typed fields, `TCFG_SPEC.md` MUST be revised before that data is accepted.

### 43.4 Future protocol families

Schema v1 supports only:

```text
protocol.family = escpos
```

A future protocol family is added by explicit specification work, not by profile convention.

Such an extension SHOULD preserve the top-level `protocol` boundary while defining a new family-specific variant, strict validator, registered strategy vocabulary, and trusted encoder.

This preserves the intended architecture:

```text
PrinterProfile
    └── swappable validated protocol-family block

PreparedPrint
    └── authoritative finalized physical plan

protocol encoder selected for validated family
    └── serializes that plan

platform transport
    └── delivers bytes
```

---

## 44. Normative Summary

The central `.tcfg` contract is:

> A `.tcfg` file is a bounded, strictly validated, portable UTF-8 JSON description of printer capabilities and trusted protocol strategy selections. It contains no user-specific physical-printer identity and cannot inject arbitrary executable printer commands.

The central model-separation contract is:

```text
PrinterProfile
    = reusable printer capability data

PrinterInstance
    = one user's physical printer + transport identity + local overrides

PrintOptions
    = choices for one preparation

PreparedPrint
    = final immutable physical output plan
```

The central protocol-safety contract is:

> Profiles select from known implementation strategies and validated parameters. Profiles do not define bytecode, scripts, or unrestricted command templates.

The central transport-separation contract is:

> Printer-side physical constraints may be profile data. Transport write chunk size, transport buffering, transport pacing, and connection timeouts are session/transport concerns and do not belong in portable `.tcfg`.

The central hardware-portability contract is:

> Characteristics of every supported printer are validated profile data, never generic Core assumptions or model-name branches.

The central protocol-extensibility contract is:

> `.tcfg` schema v1 supports ESC/POS through a dedicated validated `protocol` variant. Future printer protocols are added as explicit trusted protocol-family variants and encoders; they are never emulated through arbitrary imported command programs.
