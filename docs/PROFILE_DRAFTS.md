# Local printer profile drafts

The profile editor stores an incomplete local worksheet in shared application state. It is not `.tcfg`, a second portable printer profile format, or an activated `PrinterProfile`. A draft retains unknown/invalid numeric input for correction, but rejects unknown form property names on persistence. There are no arbitrary command fields. Private evidence records may retain an opaque device locator; it never enters `.tcfg` or portable Core models.

The form descriptors cover every v1 capability represented by the existing Core models. Native font/code-page rows are individually editable (up to 16 each); other capability lists use comma-separated registered values. Model conversion and output validation use `PrinterProfileFiles` and the existing Core validator and strict codec. Import/export use the existing `.tcfg` representation without adding evidence or draft fields.

A fresh draft records only its entered profile identity and revision 1. DPI, printable width, selectors and output strategies remain unknown. All native capabilities start disabled. Self-test millimetre measurements, CP437 labels and internally printed QR/barcodes do not establish host command compatibility.

Evidence is separate local state: unknown, user-entered, inferred, hardware-observed or verified. Ordinary activation requires verified geometry, protocol and each enabled output-affecting capability, plus a bounded procedure, observation, printer identity and exact field snapshot for each required area. A VERIFIED label and freeform notes alone never authorize activation. Editing an output field clears the verification for its capability block. Duplicated/imported profiles retain their exact Core capability values but begin with user-entered evidence. A copied synthetic fixture remains ineligible for physical activation even after renaming.

`validateCandidate()` validates the same Core syntax/capabilities without asserting evidence, solely for an explicitly experimental Printer Lab flow. It does not activate/export an ordinary profile or print by itself. Geometry still must be explicit and resource-valid; the Lab must obtain separate user authorization before sending a bounded diagnostic plan.

Local persistence uses the versioned `RastrIO-local-draft-2:` prefix followed by length-framed ID, notes, synthetic-source flag, field count/key/value pairs, evidence count/key/value pairs, inspection proposals and verification records. Each proposal retains its value, evidence classification, source description and applicable device identity. Each verification record retains its area, procedure, observation, identity and field snapshot. Version 1 records remain readable and migrate on save; old VERIFIED labels have no verification records and cannot activate a physical profile. There is no permissive JSON parser. Readers reject unsupported versions, truncated/excess framing, duplicate/unknown keys and evidence values, and trailing content. There is no migration from arbitrary unsupported versions; an unreadable record is not trusted as a completed profile.

Limits: 32 distinct drafts, 128 characters per local identity, 1,024 characters per form value, 4,096 characters of evidence notes, 49,152 combined value characters, and 65,536 characters per encoded record. Fonts/code-pages and other Core collections remain subject to the application-owned Core policy (16 entries). Private platform adapters persist these records; the memory adapter is session-local. Failed storage validation preserves prior stored state. Portable export separately applies strict UTF-8/JSON and profile resource policy.

Profile draft creation, editing, save/load, validation and export do not acquire a transport or send bytes. The physical `PrinterInstance` and its private device locator stay separate; assigning a validated profile and saving the printer is an explicit platform/application operation.


The Android adapter persists drafts under fixed app-private SharedPreferences keys;
validated active `.tcfg` content uses the existing fixed AtomicFile, and the selected
PrinterInstance uses its separate private store. Draft save failure keeps the editor
open and blocks activation. The current editor worksheet is also length-framed in
Compose saved state to survive activity recreation; corrupt/unavailable restored
state recovers to printer management instead of leaving an empty screen. Desktop/Web
fallback worksheets are session-local until a platform adapter is supplied.

The Add Printer sequence exposes paired devices before any profile import. It keeps
device choice pending while the user creates/selects and validates/calibrates a
profile, then explicitly saves the private association. Selection, saving, socket
connection, local writes and confirmed paper output are distinct states. BLE-only
metadata disables Classic assignment and does not authorize arbitrary GATT writes.

## Profile deletion

Stored drafts expose confirmed Delete in their contextual menu. The bounded
remaining list must persist before UI state changes. Android restores previously
confirmed preference values when commit fails, retaining drafts on subsequent load.

Android's saved physical profile has a separate confirmed deletion action. It
cancels active output/preparation, invalidates pending profile loads/imports and
serializes selection clearing after older writes. Failed selection clearing retains
the profile. Failed profile-file deletion reports failure with selection cleared.
Success clears the active profile and prepared-plan eligibility. Bluetooth pairing,
exported files and unrelated drafts are preserved. Portable schemas are unchanged.

## Guided editor and inspection proposals

Basic configuration shows name/manufacturer/model, the selected device, reported paper/printable millimetres, reported charset/font, and explicit dot geometry. Millimetres, font names and charset reports are worksheet-only information; they never derive DPI, printable dots, protocol selectors or capability strategies. All previously available Core fields remain accessible under one Advanced configuration accordion, with one capability area open at a time. Profile identity/revision, protocol identifiers, mapping tables, native font scales, raster bands, QR/barcode/status strategies, pacing and quirks use progressive disclosure.

Inspection suggestions are visible proposals. `withSuggestions` stores at most 32 source-preserving proposals without modifying fields; `acceptSuggestion` requires an explicit choice. Existing and proposed values are shown together when they conflict. Device-reported, manufacturer-documented, operator-reported, inferred and physically-verified source classifications remain distinct. A physically-verified suggestion does not by itself create a host-print verification record. An editor bound to a selected printer rejects proposals and records for another device.

`recordVerification` requires a nonblank procedure (256 characters), observed result (1,024 characters) and opaque printer identity (1,024 characters). It captures the current area's exact values with a bounded snapshot (16,384 characters); ordinary validation rejects missing or stale snapshots and mixed printer identities. Editing an output field clears that area's record; editing quirks clears all records. The editor only enables evidence recording when a physical device is selected. This records operator evidence; software does not assert that it witnessed paper output.

Saving an empty/incomplete draft is normal. Numeric blanks remain blanks; worksheet conversion reports one actionable unknown-value message per affected field before invoking Core, rather than constructing zero-valued geometry and appending cascading Core errors. The strict Core profile validator is unchanged and validates populated candidates. Ordinary validation summarizes remaining work and displays field guidance; complete diagnostic codes and paths are available through Technical Details. Save Draft, Validate Profile, activation and Printer Lab remain separate actions. Activation stays disabled until validation succeeds and draft edits invalidate that result.

### Experimental ruler measurements

The generic 2-inch starter offers inferred worksheet proposals only: 58 mm paper,
48 mm printable millimetres, CP437 and a reported 12×24 font. It leaves DPI,
printable dots and protocol mappings unknown. Explicit acceptance preserves the
proposal's source; it does not establish hardware compatibility.

Printer Lab may create an isolated, transient experimental candidate after the
operator selects the starter and acknowledges its registered command-path
hypothesis. Its trial 203 DPI/384-dot geometry never fills the original worksheet
or activates a profile. The bounded 64×64 raster sample uses the production
preparation/encoding path and separate print consent. Native text, native QR,
barcodes, cutting and status commands remain disabled in this candidate.

Ruler measurements use known, positive multiples-of-eight dot intervals within
the captured raster. Finite physical spans must be positive and at most 1,024 mm;
each computed axis must be within 1–600 DPI. Review shows nearest-integer worksheet
rounding. Applying a reviewed result requires the exact prepared plan, unchanged
original draft and same selected device. It changes only the original draft's
DPI and bounded operator provenance, clears geometry verification and preserves
printable dot width and capability mappings. It never promotes the trial command
path or supplies VERIFIED evidence. Changing draft, device or plan invalidates the
result. Head width, clipping, command compatibility and repeatability still require
the separate physical procedure in HW-037.
