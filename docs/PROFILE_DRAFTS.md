# Local printer profile drafts

The profile editor stores an incomplete local worksheet in shared application state. It is not `.tcfg`, a second portable printer profile format, or an activated `PrinterProfile`. A draft retains unknown/invalid numeric input for correction, but rejects unknown form property names on persistence. There are no arbitrary command fields or transport identities.

The form descriptors cover every v1 capability represented by the existing Core models. Native font/code-page rows are individually editable (up to 16 each); other capability lists use comma-separated registered values. Model conversion and output validation use `PrinterProfileFiles` and the existing Core validator and strict codec. Import/export use the existing `.tcfg` representation without adding evidence or draft fields.

A fresh draft records only its entered profile identity and revision 1. DPI, printable width, selectors and output strategies remain unknown. All native capabilities start disabled. Self-test millimetre measurements, CP437 labels and internally printed QR/barcodes do not establish host command compatibility.

Evidence is separate local state: unknown, user-entered, inferred, hardware-observed or verified. Ordinary activation requires verified geometry, protocol and each enabled output-affecting capability, plus recorded procedure/results. Editing an output field clears the verification for its capability block. Duplicated/imported profiles retain their exact Core capability values but begin with user-entered evidence. A copied synthetic fixture remains ineligible for physical activation even after renaming.

`validateCandidate()` validates the same Core syntax/capabilities without asserting evidence, solely for an explicitly experimental Printer Lab flow. It does not activate/export an ordinary profile or print by itself. Geometry still must be explicit and resource-valid; the Lab must obtain separate user authorization before sending a bounded diagnostic plan.

Local persistence uses the versioned `RastrIO-local-draft-1:` prefix followed by length-framed ID, notes, synthetic-source flag, field count/key/value pairs and evidence count/key/value pairs. There is no permissive JSON parser. Readers reject unsupported versions, truncated/excess framing, duplicate/unknown keys and evidence values, and trailing content. There is no migration to/from arbitrary earlier versions; an unreadable record is not trusted as a completed profile.

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
