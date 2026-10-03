# Rastrio `.td` Thermal Document Specification

**Status:** Normative specification  
**Specification version:** 1.1  
**Target format:** `.td` container version 1 / document schema version 1  
**Project:** Rastrio  
**License context:** Apache-2.0  
**Governing baseline:** `PRD.md` v2.1 and `docs/ARCHITECTURE.md`  
**Related specifications:** `docs/SECURITY.md`, `docs/RESOURCE_LIMITS.md`, `docs/TEXT_RENDERING_SPEC.md`, `docs/PREVIEW_SPEC.md`, `docs/TESTING.md`

---

## 1. Purpose

A Rastrio `.td` file is the portable serialized representation of a `ThermalDocument` together with any embedded document assets and optional retained source material.

The format exists so a Rastrio document can be saved, copied, versioned, reopened, shared, tested, and processed without being tied to:

- a particular printer;
- ESC/POS;
- a particular printer width;
- a code page;
- Bluetooth or USB identity;
- transport configuration;
- a platform UI toolkit;
- Android, Desktop, or Web.

`.td` is the persistent semantic document boundary.

It stores:

- document content;
- semantic structure;
- limited author-controlled layout intent;
- embedded-asset metadata and content;
- external asset references when unresolved/unembedded;
- optional retained source material.

It does not store a prepared print job.

Conceptually:

```text
Markdown / template state / other semantic input
                    ↓
              ThermalDocument
                    ↓
               .td archive

              .td archive
                    ↓
              ThermalDocument
                    ↓
       layout / printer preparation later
```

A `.td` reader MUST produce validated portable document state before that state enters layout, printer preparation, preview, or protocol encoding.

---

## 2. Normative Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

Where conceptual Kotlin types appear, exact class/package names MAY differ. Serialized field names, discriminator values, version semantics, archive rules, validation rules, and compatibility behavior defined by this specification are normative for schema v1.

`PRD.md` remains the project-level source of truth. This specification adds format detail beneath that baseline and MUST NOT weaken its architectural invariants.

---

## 3. Fundamental Invariants

A conforming `.td` v1 implementation MUST preserve all of the following.

1. `.td` is printer-independent.
2. `.td` is transport-independent.
3. `.td` contains semantic document intent, not printer commands.
4. `.td` does not contain `PreparedPrint` or printer-specific physical segmentation.
5. `.td` parsing performs no hidden network I/O.
6. Imported `.td` data is untrusted until archive, JSON, schema, semantic, and resource validation complete.
7. `document.json` is authoritative for portable document semantics.
8. Optional retained source is non-authoritative and MUST NOT override `document.json`.
9. Unknown future container or document schema versions MUST NOT be silently interpreted as v1.
10. Parser-library leniency MUST NOT weaken this specification.
11. Every externally controlled count, size, nesting depth, path, and decoded structure is subject to the applicable trusted `ResourcePolicy`.
12. No archive entry may escape the logical `.td` container namespace.
13. External asset references are data only; Core parsing MUST NOT fetch them.
14. Raw/unsupported Markdown HTML is not represented as executable/renderable HTML in `.td` v1.
15. Diagnostics produced while compiling source input are not persisted in `.td` v1 unless a future schema explicitly adds persisted diagnostics.

---

## 4. Non-Goals

`.td` v1 is not:

- a print spool format;
- an ESC/POS program;
- a printer profile;
- a printer-instance record;
- a Bluetooth pairing record;
- a USB device record;
- a transport/session configuration;
- a persistence format for `PrintOptions`;
- a persistence format for `PreparedPrint`;
- a physical-preview cache;
- a raw HTML document format;
- a web bundle;
- a general-purpose ZIP package format;
- a CSS-like page-layout language;
- a general binary object graph;
- a plugin/script container;
- a mechanism for executing code or commands.

The following MUST NOT appear as `.td` semantics:

- ESC/POS bytes or command templates;
- printer code-page selections;
- native printer-font command identifiers;
- printer DPI selected for a physical target;
- printer printable width in dots;
- transport chunk sizes;
- Bluetooth addresses;
- USB endpoint selections;
- cutter command byte sequences;
- target-printer raster-band decisions;
- target-specific wide-document strip segmentation;
- protocol strategy identifiers.

---

## 5. File Extension and Media Type

The normative file extension is:

```text
.td
```

If a media type is useful for local integration, Rastrio SHOULD use the provisional vendor-tree form:

```text
application/vnd.rastrio.td+zip
```

This recommendation does not imply IANA registration.

---

# Part I — Container Format

## 6. Physical Representation

`.td` container version 1 is a ZIP-compatible archive.

A typical archive is:

```text
example.td
├── manifest.json
├── document.json
├── assets/
│   ├── image-001.png
│   └── logo.webp
└── source/
    └── original.md
```

Required file entries:

```text
manifest.json
document.json
```

Optional namespaces:

```text
assets/
source/
```

`assets/` contains embedded document assets declared by the manifest.

`source/` contains optional retained source material declared by the manifest.

A writer MUST NOT place arbitrary implementation caches, thumbnails, prepared raster output, printer state, preview caches, temporary files, or transport state in the archive.

---

## 7. ZIP Compatibility Rules

A v1 writer MUST create a valid ZIP-compatible archive readable by the project's supported ZIP implementation.

Writers SHOULD use ordinary stored or deflated entries and SHOULD NOT require uncommon ZIP features merely to save a normal document.

Because v1 resource ceilings are far below classic ZIP32 limits, a v1 writer SHOULD NOT require ZIP64 for ordinary output. A reader MAY accept ZIP64 containers when supported by its ZIP implementation, but all normal Rastrio resource limits still apply.

Archive encryption is not part of `.td` v1.

A writer MUST NOT emit encrypted entries.

A reader MUST reject encrypted entries it cannot safely and explicitly support. Rastrio v1 does not define a password/encryption workflow.

---

## 8. Archive Namespace

Archive entry names are logical portable paths.

They are not host filesystem paths.

All writer-created entry paths MUST:

- be relative;
- use `/` as the separator;
- be valid UTF-8;
- contain no NUL characters;
- contain no empty path segment;
- contain no `.` or `..` segment;
- not begin with `/`;
- not end with `/` for normal file entries;
- not contain `\`;
- not be drive-qualified or UNC/network paths;
- remain within the logical archive root after validation.

A reader MUST reject path forms that could become absolute, traversing, or ambiguous on any supported platform.

In particular, the reader MUST reject:

```text
../x
foo/../../x
/x
C:/x
C:\x
\\server\share\x
foo\..\x
foo//bar
foo/./bar
```

Directory entries MAY exist in a ZIP container, but they do not satisfy required file entries and do not grant permission for arbitrary child paths.

---

## 9. Duplicate Paths

After portable path validation/normalization, two archive entries MUST NOT represent the same logical path.

Duplicate normalized paths are invalid.

A reader MUST NOT use:

- first-entry-wins;
- last-entry-wins;
- overwrite-on-extraction

semantics to resolve duplicates.

If an implementation materializes entries onto a case-insensitive filesystem, the materialization layer MUST additionally prevent case-collision overwrites even though archive logical paths are otherwise case-sensitive.

---

## 10. Special ZIP Entries

`.td` does not require:

- symlinks;
- hard links;
- device nodes;
- FIFOs;
- executable filesystem semantics.

Where a ZIP implementation exposes such metadata, a reader SHOULD reject special/link entries.

A conforming reader MUST NOT intentionally create a host symlink, hard link, device node, FIFO, or executable artifact from imported `.td` content.

---

## 11. Allowed v1 File Locations

Container version 1 recognizes file entries only at:

```text
manifest.json
document.json
assets/<validated relative asset path>
source/<validated relative source path>
```

Unknown file entries outside these locations MUST be rejected by a strict v1 reader.

This prevents an old v1 reader from silently accepting future container semantics it does not understand.

A future extension requiring new top-level entries requires a new container version or an explicit backward-compatible change to this specification.

---

## 12. Required-Entry Rules

A valid v1 archive MUST contain exactly one logical `manifest.json` and exactly one logical `document.json`.

The archive is invalid if either is:

- absent;
- duplicated;
- unreadable;
- encrypted;
- over the applicable limit;
- invalid UTF-8;
- invalid JSON;
- inconsistent with this specification.

`manifest.json` MUST be validated before `document.json` is trusted.

---

# Part II — Manifest

## 13. Manifest Purpose

`manifest.json` describes container-level versioning and archive-contained resources.

It does not duplicate the document block tree.

The manifest is the authority for:

- `.td` format identity;
- container version;
- document schema version expected in `document.json`;
- document encoding;
- embedded-asset inventory;
- optional retained-source inventory.

---

## 14. Manifest v1 Shape

A valid v1 manifest has this structure:

```json
{
  "format": "rastrio-td",
  "containerVersion": 1,
  "documentSchemaVersion": 1,
  "encoding": "json",
  "assets": [
    {
      "id": "image-001",
      "path": "assets/image-001.png",
      "mediaType": "image/png",
      "byteSize": 12345,
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ],
  "source": {
    "path": "source/original.md",
    "mediaType": "text/markdown",
    "byteSize": 2048,
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

`assets` is REQUIRED and MAY be an empty array.

`source` is OPTIONAL.

No other top-level properties are valid in manifest schema v1.

---

## 15. Manifest Format Identifier

`format` MUST be exactly:

```json
"rastrio-td"
```

A reader MUST reject a missing or different value.

---

## 16. Container Version

`containerVersion` MUST be a JSON integer.

For this specification it MUST equal:

```json
1
```

`containerVersion` governs ZIP/container organization and manifest/container semantics.

A reader that supports only container version 1 MUST reject any other value.

---

## 17. Document Schema Version

`documentSchemaVersion` MUST be a JSON integer.

For this specification it MUST equal:

```json
1
```

It states which schema the archive expects in `document.json`.

`document.json.schemaVersion` MUST equal this manifest value.

A mismatch is invalid.

---

## 18. Encoding

`encoding` MUST be exactly:

```json
"json"
```

Container version 1 does not define CBOR, protobuf, MessagePack, YAML, or another document encoding.

---

## 19. Embedded Asset Descriptor

Each member of `assets` MUST be an object with exactly these properties:

| Property | Required | Meaning |
|---|---|---|
| `id` | yes | stable identifier used by embedded `AssetReference` values |
| `path` | yes | archive path to the embedded bytes |
| `mediaType` | yes | declared media type |
| `byteSize` | yes | expected expanded byte count |
| `sha256` | no | lowercase SHA-256 digest of the exact embedded bytes |

No additional properties are allowed in v1.

### 19.1 Asset ID

`id` MUST:

- be a non-empty ASCII string;
- match `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`;
- be unique within the manifest;
- not be interpreted as a path, URL, class name, or executable reference.

Asset IDs are case-sensitive.

### 19.2 Asset path

`path` MUST:

- begin with `assets/`;
- identify a file entry, not a directory;
- satisfy all archive-path rules;
- be unique among asset descriptors;
- identify exactly one archive file entry.

The filename extension is advisory. `mediaType` and validated content determine decode behavior.

### 19.3 Media type

`mediaType` MUST be a syntactically valid non-empty media-type string acceptable to the current document/image pipeline.

For v1 image content, normal expected values include:

```text
image/png
image/jpeg
image/webp
```

A reader MUST NOT trust a declared media type in place of actual bounded content validation/decoding.

### 19.4 Byte size

`byteSize` MUST be a non-negative integer within resource policy.

It MUST equal the actual expanded number of bytes produced for that asset entry.

A mismatch is an integrity/validation failure.

The declared value does not replace runtime expanded-byte accounting.

### 19.5 SHA-256

If present, `sha256` MUST be exactly 64 lowercase hexadecimal characters.

It MUST equal the SHA-256 hash of the exact expanded asset bytes.

Writers SHOULD include SHA-256 for embedded assets because it improves deterministic fixture verification and integrity checking.

The hash is not a substitute for archive/resource validation.

---

## 20. Retained Source Descriptor

`source` is optional and describes one retained source file in container version 1.

If present, it MUST contain exactly:

| Property | Required | Meaning |
|---|---|---|
| `path` | yes | archive path of retained source |
| `mediaType` | yes | source media type |
| `byteSize` | yes | actual expanded byte count |
| `sha256` | no | lowercase SHA-256 digest |

`path` MUST begin with `source/` and satisfy all archive-path rules.

`byteSize` and `sha256`, when present, have the same validation semantics as asset descriptors.

Retained source:

- is non-authoritative;
- MUST NOT replace or override `document.json`;
- MUST NOT execute;
- MUST NOT trigger filesystem access;
- MUST NOT trigger network access;
- MAY be offered to the user for editing/export/recompilation by a higher-level workflow.

Container version 1 defines at most one retained source descriptor.

---

## 21. Source-Retention Policy

Source material MUST NOT be silently embedded merely because a `ThermalDocument` originated from Markdown.

The save workflow MUST make source retention an explicit policy or a clearly documented application default.

At the Core API boundary, saving SHOULD make the policy explicit, conceptually:

```text
SourceRetention.None
SourceRetention.Include(source bytes + metadata)
```

The exact Kotlin API is not normative.

Changing whether source is retained MUST NOT change the semantic contents of `document.json`.

---

# Part III — JSON Contract

## 22. UTF-8 and Standard JSON

`manifest.json` and `document.json` MUST be UTF-8 encoded standard JSON.

Writers MUST NOT emit a byte-order mark.

Readers MAY accept a UTF-8 BOM only if the implementation does so deterministically and before strict JSON validation; writers still MUST NOT emit one.

A conforming v1 reader MUST reject:

- malformed UTF-8;
- comments;
- trailing commas;
- single-quoted strings;
- unquoted property names;
- duplicate object-property names;
- invalid escapes;
- invalid standard-JSON number grammar;
- `NaN`;
- `Infinity`;
- `-Infinity`;
- unknown properties at schema-defined object levels;
- unsupported discriminators/enums;
- illegal `null` values;
- excessive nesting, token counts, strings, arrays, or total bytes under `ResourcePolicy`.

---

## 23. Parser-Library Independence

The `.td` specification is authoritative over the JSON library.

A reader MUST reject invalid input even if the selected parser would otherwise:

- ignore an unknown property;
- collapse duplicate keys;
- coerce a value;
- accept comments;
- accept trailing commas;
- accept non-standard numerics;
- deserialize an unknown subtype into a fallback type.

Duplicate-key and unknown-property detection MUST occur before a lossy object representation can erase the evidence required for validation.

If the chosen JSON library cannot enforce a rule directly, Rastrio MUST use bounded lexical/token pre-validation, a parser wrapper, structural inspection, or another deterministic enforcement layer.

---

## 24. Optional Properties and `null`

Schema v1 uses omission rather than `null` for optional properties unless this specification explicitly states otherwise.

No v1 property defined below accepts JSON `null`.

A reader MUST reject `null` where an optional field should instead have been omitted.

---

## 25. JSON Object Member Order

JSON object member order is not semantically significant.

A writer SHOULD emit a stable field order for readability and deterministic tests.

Readers MUST NOT depend on property order.

---

# Part IV — `document.json`

## 26. Document Purpose

`document.json` is the authoritative semantic document payload.

It serializes the printer-independent `ThermalDocument`.

A v1 document has this top-level shape:

```json
{
  "schemaVersion": 1,
  "metadata": {},
  "layout": {
    "orientation": "portrait"
  },
  "blocks": []
}
```

Required top-level properties:

```text
schemaVersion
metadata
layout
blocks
```

No additional top-level properties are valid in v1.

---

## 27. `schemaVersion`

`schemaVersion` MUST be the integer:

```json
1
```

It MUST equal `manifest.json.documentSchemaVersion`.

---

## 28. Document Metadata

`metadata` MUST be an object.

Schema v1 defines one optional property:

```json
{
  "title": "Shopping list"
}
```

`title`:

- is OPTIONAL;
- MUST be a string when present;
- MUST satisfy string/resource limits;
- has no printer-command semantics.

An empty object is valid:

```json
{}
```

Additional metadata requires a future schema/specification update rather than ad-hoc unknown properties.

---

## 29. Document Layout

`layout` represents limited author-controlled document layout intent.

It is not printer geometry.

Schema v1 properties:

| Property | Required | Meaning |
|---|---|---|
| `orientation` | yes | `portrait` or `landscape` |
| `maxWidth` | no | optional logical maximum width |

Example:

```json
{
  "orientation": "landscape",
  "maxWidth": {
    "value": 180,
    "unit": "mm"
  }
}
```

### 29.1 Orientation

Valid values are exactly:

```text
portrait
landscape
```

Orientation is author intent.

It does not select a printer command or rotate printer hardware directly.

### 29.2 Maximum logical width

`maxWidth` is an optional logical upper bound.

In v1 its shape is:

```json
{
  "value": 180,
  "unit": "mm"
}
```

Rules:

- `value` MUST be a finite positive JSON number;
- `unit` MUST be exactly `mm` in schema v1;
- the resulting dimension MUST satisfy document/resource policy;
- `maxWidth` MUST NOT be interpreted as printer paper width;
- printer preparation remains free to derive a narrower target-constrained layout when required by the selected printer and workflow.

A future unit requires an explicit schema/specification change.

---

## 30. Document Blocks

`blocks` MUST be an array of `DocumentBlock` objects.

Version 1 supports exactly these block discriminators:

```text
paragraph
heading
unorderedList
orderedList
checklist
quote
codeBlock
separator
image
table
qrCode
```

The discriminator property is named:

```json
"type"
```

Unknown block discriminators are invalid in schema v1.

Block count and recursive structure are bounded by `docs/RESOURCE_LIMITS.md`.

---

## 31. Alignment

Where a block/cell supports alignment, valid values are exactly:

```text
left
center
right
```

Alignment is semantic/author layout intent.

It does not mean a particular ESC/POS alignment command will be used.

---

# Part V — Inline Content

## 32. Inline Content Model

Version 1 supports exactly these inline discriminators:

```text
text
strong
emphasis
strike
inlineCode
link
lineBreak
```

Every inline object uses the property:

```json
"type"
```

Unknown inline discriminators are invalid.

---

## 33. Text Inline

Shape:

```json
{
  "type": "text",
  "text": "Hello"
}
```

Properties:

- `type` REQUIRED;
- `text` REQUIRED string.

Text is Unicode semantic content.

It MUST NOT be interpreted as HTML, a template program, a shell command, or ESC/POS bytes.

---

## 34. Strong Inline

Shape:

```json
{
  "type": "strong",
  "children": [
    { "type": "text", "text": "important" }
  ]
}
```

`children` MUST be an array of inline content.

`strong` expresses semantic strong emphasis. It does not require native printer bold.

---

## 35. Emphasis Inline

Shape:

```json
{
  "type": "emphasis",
  "children": [
    { "type": "text", "text": "emphasized" }
  ]
}
```

`children` MUST be an array of inline content.

---

## 36. Strike Inline

Shape:

```json
{
  "type": "strike",
  "children": [
    { "type": "text", "text": "removed" }
  ]
}
```

`children` MUST be an array of inline content.

---

## 37. Inline Code

Shape:

```json
{
  "type": "inlineCode",
  "text": "./gradlew check"
}
```

`text` is literal code text.

It is data only and MUST NOT execute.

---

## 38. Link Inline

Shape:

```json
{
  "type": "link",
  "destination": "https://example.com",
  "children": [
    { "type": "text", "text": "Example" }
  ]
}
```

`destination` MUST be a bounded string.

Core `.td` loading MUST NOT dereference or fetch the destination.

A platform UI MAY offer an explicit user-initiated open action according to application security policy.

---

## 39. Line Break Inline

Shape:

```json
{
  "type": "lineBreak"
}
```

No additional properties are valid.

It represents an explicit author line break.

---

# Part VI — Block Types

## 40. Paragraph

Shape:

```json
{
  "type": "paragraph",
  "alignment": "left",
  "content": [
    { "type": "text", "text": "Hello world" }
  ]
}
```

Required properties:

```text
type
alignment
content
```

`content` MUST be an array of inline content.

---

## 41. Heading

Shape:

```json
{
  "type": "heading",
  "level": 2,
  "alignment": "left",
  "content": [
    { "type": "text", "text": "Details" }
  ]
}
```

`level` MUST be an integer from 1 through 6 inclusive.

`content` MUST be an array of inline content.

---

## 42. Unordered List

Shape:

```json
{
  "type": "unorderedList",
  "items": [
    {
      "blocks": [
        {
          "type": "paragraph",
          "alignment": "left",
          "content": [
            { "type": "text", "text": "Item" }
          ]
        }
      ]
    }
  ]
}
```

Each item object MUST contain exactly:

```text
blocks
```

`blocks` MUST be a non-empty array of document blocks.

Nested lists are represented by list blocks inside an item's `blocks` array.

Recursive nesting is subject to resource limits.

---

## 43. Ordered List

Shape:

```json
{
  "type": "orderedList",
  "start": 1,
  "items": [
    {
      "blocks": [
        {
          "type": "paragraph",
          "alignment": "left",
          "content": [
            { "type": "text", "text": "First" }
          ]
        }
      ]
    }
  ]
}
```

`start` MUST be a non-negative integer within the document's numeric/resource policy.

`items` follows the same item structure as `unorderedList`.

---

## 44. Checklist

Shape:

```json
{
  "type": "checklist",
  "items": [
    {
      "checked": false,
      "blocks": [
        {
          "type": "paragraph",
          "alignment": "left",
          "content": [
            { "type": "text", "text": "Buy paper" }
          ]
        }
      ]
    }
  ]
}
```

Each checklist item MUST contain exactly:

```text
checked
blocks
```

`checked` MUST be Boolean.

Checklist state is semantic content. It does not prescribe an emoji or a printer-native glyph. Physical checklist-marker rendering is resolved later by the text/printer preparation pipeline.

---

## 45. Quote

Shape:

```json
{
  "type": "quote",
  "blocks": [
    {
      "type": "paragraph",
      "alignment": "left",
      "content": [
        { "type": "text", "text": "Quoted text" }
      ]
    }
  ]
}
```

`blocks` MUST be a non-empty array of document blocks.

Recursive nesting is subject to resource limits.

---

## 46. Code Block

Shape:

```json
{
  "type": "codeBlock",
  "text": "fun main() {}",
  "language": "kotlin"
}
```

`text` is REQUIRED.

`language` is OPTIONAL and is a bounded informational string.

Code-block contents are data only and MUST NOT execute.

---

## 47. Separator

Shape:

```json
{
  "type": "separator"
}
```

No additional properties are valid.

The separator expresses a semantic/thematic rule. Its exact physical geometry is resolved by layout/preparation.

---

## 48. Asset References

Image blocks refer to assets through one of two explicit reference variants.

### 48.1 Embedded asset reference

```json
{
  "kind": "embedded",
  "assetId": "image-001"
}
```

`assetId` MUST identify exactly one descriptor in `manifest.json.assets`.

A missing asset is invalid.

### 48.2 External asset reference

```json
{
  "kind": "external",
  "uri": "https://example.com/image.png"
}
```

`uri` is a bounded string representing unresolved external identity.

Loading `.td` MUST NOT fetch it.

External resolution belongs to an explicit higher-level `ExternalAssetResolver`/policy workflow.

Unknown `kind` values are invalid.

---

## 49. Image Sizing Intent

An image block contains a `sizing` object using exactly one v1 mode.

### 49.1 Automatic

```json
{
  "mode": "auto"
}
```

### 49.2 Fit available logical width

```json
{
  "mode": "fitWidth"
}
```

### 49.3 Requested logical width

```json
{
  "mode": "width",
  "width": {
    "value": 40,
    "unit": "mm"
  }
}
```

For `width`, the length rules from document layout apply.

No image sizing mode stores printer dots or target DPI.

---

## 50. Image Block

Shape:

```json
{
  "type": "image",
  "asset": {
    "kind": "embedded",
    "assetId": "image-001"
  },
  "alignment": "center",
  "sizing": {
    "mode": "fitWidth"
  },
  "altText": "Rastrio logo"
}
```

Required properties:

```text
type
asset
alignment
sizing
```

`altText` is OPTIONAL.

An image block with an external asset reference is valid portable semantic content, but it remains unresolved until a higher-level workflow explicitly resolves it. Printing/preparation MUST NOT perform implicit network retrieval.

---

## 51. Table

Rastrio v1 tables represent a rectangular semantic table suitable for GFM-style content.

Shape:

```json
{
  "type": "table",
  "columns": [
    { "alignment": "left" },
    { "alignment": "right" }
  ],
  "header": [
    { "content": [{ "type": "text", "text": "Item" }] },
    { "content": [{ "type": "text", "text": "Qty" }] }
  ],
  "rows": [
    [
      { "content": [{ "type": "text", "text": "Paper" }] },
      { "content": [{ "type": "text", "text": "2" }] }
    ]
  ]
}
```

Required properties:

```text
type
columns
header
rows
```

Each `columns` entry contains exactly:

```json
{ "alignment": "left" }
```

Each table cell contains `content` and MAY contain an alignment override:

```json
{
  "content": [ ... inline content ... ],
  "alignment": "center"
}
```

If `alignment` is omitted, the cell uses its column alignment. If present, it MUST be one of the normal v1 alignment values. No additional cell properties are valid.

Validation rules:

- `columns` MUST be non-empty;
- `header` cell count MUST equal `columns` count;
- every row cell count MUST equal `columns` count;
- table/row/column/cell counts MUST obey resource policy;
- cell content is inline semantic content;
- table geometry is not stored in `.td`.

Column widths, wrapping, row heights, strip segmentation, and target physical geometry are resolved later by layout/printer preparation.

---

## 52. QR Code

`.td` stores semantic QR intent, not printer commands.

Shape:

```json
{
  "type": "qrCode",
  "payload": "https://example.com",
  "alignment": "center",
  "errorCorrection": "auto"
}
```

Optional requested logical size:

```json
{
  "type": "qrCode",
  "payload": "hello",
  "alignment": "center",
  "errorCorrection": "medium",
  "requestedSize": {
    "value": 30,
    "unit": "mm"
  }
}
```

Required properties:

```text
type
payload
alignment
errorCorrection
```

`requestedSize` is OPTIONAL.

Valid `errorCorrection` values are:

```text
auto
low
medium
quartile
high
```

These correspond to author-level preference only.

The printer engine later chooses native versus raster QR and resolves final supported parameters into `PreparedPrint`.

`payload` is data only. Rastrio MUST NOT automatically open, fetch, execute, or interpret it as a URL merely because it looks like one.

---

# Part VII — Semantic Validation

## 53. Validation Stages

A `.td` reader SHOULD conceptually validate in this order:

```text
bounded input acquisition
        ↓
ZIP/container structural validation
        ↓
archive path + entry/resource validation
        ↓
strict manifest JSON validation
        ↓
manifest schema/version validation
        ↓
strict document JSON validation
        ↓
document schema decoding
        ↓
asset/source cross-checking
        ↓
semantic model validation
        ↓
trusted ThermalDocument
```

A partially decoded object graph MUST NOT be treated as trusted merely because JSON parsing succeeded.

---

## 54. Document Semantic Validation

After schema decoding, validation MUST reject invalid state including as applicable:

- unsupported orientation;
- invalid length values;
- invalid heading levels;
- malformed or unknown node discriminators;
- invalid alignment values;
- invalid list structure;
- excessive recursive nesting;
- invalid table shape;
- invalid asset reference variants;
- missing embedded assets;
- duplicate asset IDs;
- duplicate asset paths;
- asset metadata/content mismatch;
- invalid QR error-correction values;
- counts exceeding resource policy;
- strings/nodes exceeding resource policy;
- malformed model state that cannot be produced by a conforming writer.

A validated document MUST remain printer-independent.

Validation MUST NOT add printer-specific values to make an otherwise invalid document usable.

---

## 55. Asset Validation

For every embedded manifest asset, the reader MUST validate:

1. descriptor schema;
2. unique ID;
3. valid `assets/` path;
4. exactly one matching archive entry;
5. actual expanded byte count;
6. total/per-entry resource limits;
7. SHA-256 when declared;
8. media/content suitability when the asset is actually decoded for use.

Image decoding remains independently subject to `docs/SECURITY.md` and `docs/RESOURCE_LIMITS.md`.

A small embedded file may still be rejected at decode time if it expands into unsafe pixel dimensions or memory use.

---

## 56. External Assets

External asset references are valid semantic references but unresolved external content.

Core `.td` reading MUST NOT:

- make HTTP requests;
- open arbitrary local paths;
- attach ambient credentials;
- redirect through another network resolver;
- convert an external reference into an embedded asset silently.

A higher-level explicit resolution workflow MAY fetch/import an external asset subject to security/resource policy.

If a user saves a self-contained document after successful resolution, the application MAY convert the reference to an embedded asset in a newly serialized `.td`.

---

## 57. Raw HTML and Unsupported Markdown

`.td` v1 defines no `RawHtml` block or inline type.

When Markdown input contains raw/unsupported HTML, the Markdown compiler follows the project v1 policy:

- it MUST NOT produce executable/renderable HTML nodes;
- significant source text is preserved as ordinary literal document text where safely available;
- a structured diagnostic is emitted by compilation;
- scripts, styles, event handlers, embedded markup, and resource URLs are not executed/interpreted;
- unsupported significant content MUST NOT silently disappear.

Compilation diagnostics are not persisted in `.td` v1.

---

# Part VIII — Security and Resource Contract

## 58. Resource Policy

Every `.td` reader MUST enforce the active trusted `ResourcePolicy` from `docs/RESOURCE_LIMITS.md`.

The current specification intentionally does not make provisional engineering limits permanent portable-format constants.

The reader MUST bound at least:

- compressed archive bytes;
- total expanded bytes;
- archive entry count;
- per-entry expanded bytes;
- normalized path length;
- path segment count;
- `manifest.json` size;
- `document.json` size;
- JSON nesting/structure/token complexity;
- embedded asset count;
- individual asset size;
- source size;
- document block count;
- inline node count;
- table dimensions/cells;
- recursive nesting;
- image decode resources when an image is decoded.

An imported document MUST NOT be able to increase its own resource budget.

---

## 59. Runtime Expansion Accounting

ZIP metadata is advisory for early rejection, not authoritative for safety.

A reader MUST count actual bytes produced while decompressing entries and cumulatively across the archive.

If a declared size says one value but decompression produces more bytes, actual produced bytes control limit enforcement.

Processing MUST stop before accepting bytes that would exceed an applicable hard limit.

---

## 60. Checked Arithmetic

All calculations affected by untrusted counts, lengths, sizes, indices, table dimensions, or asset metadata MUST use checked arithmetic.

Overflow is invalid/over-limit input.

A reader MUST NOT allow integer overflow to wrap a resource check into a smaller value.

---

## 61. Avoid Filesystem Extraction

A reader SHOULD operate on `.td` as a logical archive and avoid extracting arbitrary entries to a host filesystem.

If platform constraints require extraction or temporary materialization:

- paths MUST already have passed archive-path validation;
- the destination root MUST be application controlled;
- host path creation MUST remain inside that root;
- symlinks/special files MUST not be created;
- case-collision risk MUST be handled;
- partial files MUST not be promoted as valid document state;
- cleanup/cancellation behavior MUST be explicit.

---

## 62. Failure Behavior

Malformed, unsupported, corrupt, or over-limit input MUST fail closed with structured diagnostics.

A load failure MUST NOT:

- return a partially trusted `ThermalDocument` as successful;
- persist partially extracted content as a valid `.td`;
- retry through a less strict parser;
- silently drop unknown significant nodes;
- ignore missing embedded assets;
- fetch missing content from the network;
- reinterpret an unknown future version as v1.

---

# Part IX — Serialization

## 63. Writer Requirements

A conforming v1 writer MUST:

1. validate the in-memory `ThermalDocument` before serialization;
2. emit `manifest.json` and `document.json` as UTF-8 standard JSON;
3. emit container/document version 1;
4. use the stable v1 discriminator strings from this specification;
5. emit only schema-defined properties;
6. avoid JSON `null` for optional fields;
7. emit embedded assets under validated `assets/` paths;
8. record correct asset byte sizes;
9. compute declared SHA-256 values from exact output bytes;
10. include retained source only according to the explicit save policy;
11. avoid printer/transport/prepared state;
12. avoid hidden network I/O while serializing.

---

## 64. Deterministic Serialization

For equivalent validated semantic input and equivalent explicitly supplied embedded/source bytes, the writer SHOULD produce deterministic logical content.

At minimum, writers SHOULD use stable ordering for:

- manifest fields;
- document fields;
- serialized arrays where domain order is meaningful;
- asset descriptors;
- archive entries.

Recommended archive entry order:

```text
manifest.json
document.json
assets/* sorted by path
source/*
```

However, byte-for-byte ZIP identity is NOT a v1 compatibility requirement.

ZIP metadata, compression-library behavior, compression level, timestamps, or other non-semantic container details MAY cause binary differences.

Tests MUST compare semantic/normalized representations unless a particular test explicitly controls those archive details.

---

## 65. Round-Trip Contract

For any valid schema-v1 in-memory document supported by the current implementation:

```text
serialize
→ load
→ validate
```

MUST preserve semantic equality, subject only to explicitly documented normalization.

Round-tripping MUST NOT introduce:

- printer state;
- transport state;
- new external fetches;
- lost significant semantic content;
- changed block order;
- changed inline order;
- changed asset identity.

---

# Part X — Versioning and Compatibility

## 66. Independent Versions

The following are independent concepts:

```text
Rastrio application version
.td containerVersion
documentSchemaVersion
```

An application release does not automatically imply a new `.td` version.

A `.td` schema/container version changes only when the persistent format contract requires it.

---

## 67. Unknown Versions

A v1-only reader MUST reject:

```text
containerVersion != 1
documentSchemaVersion != 1
document.json.schemaVersion != 1
```

It MUST NOT attempt a best-effort v1 interpretation.

The diagnostic SHOULD distinguish unsupported version from corrupt input where practical.

---

## 68. Compatibility Within v1

Schema v1 is strict.

Unknown properties and unknown discriminators are rejected rather than ignored.

Therefore adding a new serialized field or node discriminator is a public format change and requires deliberate compatibility/version analysis.

Internal Kotlin API refactoring does not require a schema change when serialized semantics remain unchanged.

---

## 69. Migration

When a future supported `.td` version requires migration:

- migration MUST be explicit and version-aware;
- input MUST first be parsed/validated under the source version's rules;
- migration MUST produce a new validated semantic representation;
- migration MUST NOT execute retained source;
- migration MUST NOT perform implicit network access;
- migration MUST preserve significant content or emit a diagnostic/fail where preservation is impossible;
- migration/resource processing remains bounded;
- opening an older file SHOULD NOT silently overwrite it on disk merely because an in-memory migration occurred.

Representative released-version fixtures SHOULD remain in the repository as compatibility tests while that version remains supported.

---

# Part XI — Relationship to Other Rastrio Models

## 70. `.td` vs `LogicalDocument`

`.td` stores `ThermalDocument` semantics and author layout intent.

It does not store authoritative resolved logical geometry.

`LogicalDocument` is derived later from:

```text
ThermalDocument
+
LayoutConstraints
```

A logical layout cache, if ever introduced, is not part of `.td` v1 semantics.

---

## 71. `.td` vs `PrinterProfile`

`.td` does not contain `PrinterProfile` data.

A document may be prepared against many different validated printer profiles without modifying the saved semantic document.

---

## 72. `.td` vs `PrinterInstance`

`.td` does not contain:

- Bluetooth identity;
- USB identity;
- user-local printer labels;
- transport locators;
- pairing information;
- last-used printer state.

Those belong to application/platform persistence around `PrinterInstance`, not the portable document.

---

## 73. `.td` vs `PrintOptions`

Per-job choices such as:

- dithering algorithm;
- brightness/contrast/gamma;
- density;
- native/raster preference;
- QR strategy preference;
- physical segmentation overlap;
- registration marks;
- manual cut guides

are not `.td` v1 document semantics unless a specific field is explicitly defined above as author intent.

`PrintOptions` remain inputs to printer preparation.

---

## 74. `.td` vs `PreparedPrint`

`PreparedPrint` is target-specific and immutable.

It may contain/reference:

- native text decisions;
- selected code pages;
- final raster output;
- final QR strategy;
- cuts;
- feeds;
- physical segmentation;
- printer units;
- finalized prepared artifacts.

None of those physical decisions are serialized into `.td` v1.

---

# Part XII — Diagnostics

## 75. Diagnostic Family

`.td` parsing/loading/validation uses the `TDxxx` diagnostic family.

Existing project examples include:

```text
TD101 Invalid asset reference
TD120 Archive resource limit exceeded
TD121 Expanded-size limit exceeded
TD122 Entry-count limit exceeded
TD123 Entry-size limit exceeded
TD124 JSON resource limit exceeded
```

Exact additional codes MAY be assigned as implementation proceeds, but codes used publicly/tests SHOULD remain stable once established.

Diagnostics MUST avoid leaking printable content by default.

Useful structured context MAY include:

- failing logical archive path when safe;
- validation category;
- version value;
- resource name and configured limit;
- node/path location in `document.json`;
- asset ID.

Avoid copying private document bodies, QR payloads, retained source text, or image bytes into routine logs.

---

# Part XIII — Required Test Contract

## 76. Serialization Tests

The implementation MUST cover at least:

```text
minimal document
all block types
all inline types
metadata
portrait
landscape
maxWidth
embedded asset
external asset reference
multiple assets
optional retained source
round-trip semantic equality
```

---

## 77. Invalid JSON Tests

The actual production reader path MUST reject at least:

```text
comments
trailing commas
single-quoted strings
unquoted property names
duplicate keys in manifest.json
duplicate keys in document.json
unknown top-level properties
unknown nested properties
NaN
Infinity
-Infinity
invalid JSON number grammar
illegal nulls
unknown block discriminators
unknown inline discriminators
unknown enum values
malformed UTF-8
```

Testing only a helper validator is insufficient if the production parser can erase invalid syntax/evidence first.

---

## 78. Archive-Security Tests

Tests MUST cover at least:

```text
invalid ZIP
corrupt archive
missing manifest.json
missing document.json
duplicate required entry
../ traversal
nested traversal
absolute paths
Windows-style absolute/drive paths
backslash traversal
normalized duplicate paths
unsupported special entries where detectable
compressed-size limit
expanded-size limit
entry-count limit
per-entry limit
oversized manifest.json
oversized document.json
asset-count limit
oversized asset
source-size limit
```

No accepted test archive path may escape the logical archive namespace.

---

## 79. Asset Tests

Tests MUST cover:

- valid embedded asset reference;
- missing manifest asset descriptor;
- duplicate asset ID;
- duplicate asset path;
- asset path outside `assets/`;
- missing asset entry;
- byte-size mismatch;
- hash mismatch;
- invalid hash syntax;
- external reference remains unresolved without network access;
- image decode limits are still applied after archive validation.

---

## 80. Version Tests

Tests MUST cover:

- valid container v1/schema v1;
- unsupported container version;
- unsupported document schema version;
- mismatch between manifest document version and `document.json.schemaVersion`;
- unsupported future node discriminator;
- migration fixtures when future versions exist.

---

## 81. Property/Fuzz Testing

Property-based and fuzz testing SHOULD target:

- archive path normalization;
- size arithmetic;
- strict JSON tokenization;
- recursive node validation;
- table shape invariants;
- asset reference cross-checking;
- round-trip semantics;
- malformed ZIP metadata;
- decompression limit enforcement.

Crashes, hangs, uncontrolled allocation, path escape, silent invalid-input acceptance, or hidden network access are failures.

---

# Part XIV — Complete Minimal Example

## 82. Minimal Archive

```text
minimal.td
├── manifest.json
└── document.json
```

`manifest.json`:

```json
{
  "format": "rastrio-td",
  "containerVersion": 1,
  "documentSchemaVersion": 1,
  "encoding": "json",
  "assets": []
}
```

`document.json`:

```json
{
  "schemaVersion": 1,
  "metadata": {},
  "layout": {
    "orientation": "portrait"
  },
  "blocks": []
}
```

This is a valid empty semantic document.

---

# Part XV — Example with Content and Asset

## 83. Example Archive

```text
note.td
├── manifest.json
├── document.json
├── assets/
│   └── image-001.png
└── source/
    └── original.md
```

Example `manifest.json`:

```json
{
  "format": "rastrio-td",
  "containerVersion": 1,
  "documentSchemaVersion": 1,
  "encoding": "json",
  "assets": [
    {
      "id": "image-001",
      "path": "assets/image-001.png",
      "mediaType": "image/png",
      "byteSize": 12345,
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ],
  "source": {
    "path": "source/original.md",
    "mediaType": "text/markdown",
    "byteSize": 321,
    "sha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
  }
}
```

Example `document.json`:

```json
{
  "schemaVersion": 1,
  "metadata": {
    "title": "Printer notes"
  },
  "layout": {
    "orientation": "portrait"
  },
  "blocks": [
    {
      "type": "heading",
      "level": 1,
      "alignment": "left",
      "content": [
        { "type": "text", "text": "Printer notes" }
      ]
    },
    {
      "type": "paragraph",
      "alignment": "left",
      "content": [
        { "type": "text", "text": "Preview and printing share one " },
        {
          "type": "strong",
          "children": [
            { "type": "text", "text": "PreparedPrint" }
          ]
        },
        { "type": "text", "text": "." }
      ]
    },
    {
      "type": "image",
      "asset": {
        "kind": "embedded",
        "assetId": "image-001"
      },
      "alignment": "center",
      "sizing": {
        "mode": "fitWidth"
      },
      "altText": "Reference image"
    },
    {
      "type": "qrCode",
      "payload": "https://example.com",
      "alignment": "center",
      "errorCorrection": "auto"
    }
  ]
}
```

The example hashes/sizes above are illustrative placeholders and are valid only if they match the actual archive bytes in a real fixture.

---

# Part XVI — Implementation Guidance

## 84. Suggested Module Ownership

`core-document` owns:

- domain types;
- v1 serializers/deserializers;
- strict schema validation;
- archive namespace validation;
- manifest/document cross-validation;
- portable `.td` diagnostics.

Platform/application code MAY own:

- file pickers;
- opening input streams;
- saving output streams;
- durable/local storage locations;
- user-facing source-retention choices;
- explicit external asset resolution services.

Platform types MUST NOT leak into the portable `.td` model.

---

## 85. Implementation Sequence

A safe Phase 1 implementation order is:

```text
portable domain types
        ↓
semantic validators
        ↓
strict JSON token/pre-validation layer
        ↓
manifest/document serializers
        ↓
archive path validator
        ↓
bounded ZIP reader/writer
        ↓
asset/source cross-validation
        ↓
round-trip + invalid-input tests
        ↓
fuzz/property/resource/security tests
```

Do not postpone archive security or strict JSON handling until after basic loading works.

They are part of the first conforming reader.

---

## 86. Open Questions

There are no unresolved format questions that block implementation of `.td` container version 1 / document schema version 1 as defined here.

The following are deliberately outside this schema and require a future specification/version decision if introduced:

- additional metadata fields beyond `title`;
- additional physical container namespaces;
- multiple retained source files;
- additional length units;
- richer image sizing/cropping intent;
- barcode blocks;
- persisted compiler diagnostics;
- raw HTML nodes;
- additional table-cell block content beyond inline content;
- compact binary document encoding;
- archive encryption/signing;
- printer-specific/prepared output caching inside `.td`.

These items MUST NOT be added ad hoc to schema v1.

---

## 87. Conformance Summary

A `.td` v1 implementation is conforming only if it preserves all of the following:

```text
portable semantic ThermalDocument
printer independence
transport independence
strict UTF-8 JSON
stable explicit discriminators
strict unknown-property rejection
strict duplicate-key rejection
explicit container/document versioning
bounded ZIP processing
portable safe archive paths
embedded-asset cross-validation
no hidden external-asset fetching
optional non-authoritative retained source
round-trip semantic equality
explicit compatibility/migration behavior
no printer/prepared state in the document
```

If an implementation choice makes the file easier to parse but violates one of those properties, the implementation must change rather than weakening the `.td` contract.
