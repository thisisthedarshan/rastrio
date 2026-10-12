# Helett H50i BillQuick Go development draft

Status: **provisional evidence inventory; not an executable printer profile**.
This draft is deliberately not `.tcfg`: mandatory physical geometry and reliable
host-command facts are missing. It cannot authorize preparation for this physical
printer or transmission. The portable schema and strict validator are unchanged.

The user supplied the following physical self-test transcription on 2026-10-13.
It corroborates the earlier `H50I-SELFTEST-2026-10-02` inventory in
[ESC_POS_NOTES](ESC_POS_NOTES.md#311-current-observed-device-facts). No new paper
test was performed by the agent, and no scan/photo was supplied in this request.

| Field | Reported evidence | Scope |
| --- | --- | --- |
| Manufacturer/model | Helett H50i BillQuick Go | User identification of the reference unit |
| Paper | 2 inches / 58 mm | Reported paper class; not printable dot width |
| Print width | 48 mm | Printer-reported physical width |
| Interfaces | USB and Bluetooth | Reported presence; endpoints and host behavior unverified |
| Default font | 12×24 | Reported cell geometry; selector and line advance unknown |
| Default/current charset | CP437 | Reported repertoire; `ESC t` selector and complete byte map unknown |
| QR / EAN13 | Reported supported | Firmware capability only; native host-command strategies unverified |
| Maximum print speed | 60 mm/s | Reported maximum; not transport pacing guidance |
| Density / autofeed | 5 / 0 mm | Current configuration; no DPI or capability inference |

Temperature 33 is transient telemetry and is not part of this draft configuration.
No cutter capability is enabled. Existing observations describe the reference
unit as lacking an automatic cutter; no cut test or new observation is claimed.

Required before a usable profile can be created:

- independently established horizontal DPI, vertical DPI, and reliable printable
  dot width; 384 dots and 203 DPI are not assumed;
- captured initialization/dialect behavior and at least one reliable output path;
- raster strategy, maximum reliable width and band limits before raster QR;
- font selector/advance/style and CP437 selector/repertoire before native text;
- native QR model/size/correction/payload command evidence before enabling native
  QR; EAN13 command evidence before enabling native barcode;
- transport-specific chunk/pacing measurements, recorded separately from raster
  bands; unknown printer buffering remains absent.

Use HW-035 for inventory, HW-012 for raster width, HW-001/002 for native geometry,
the character-set procedure in the Printer Lab checklist, HW-007 for raster
compatibility, HW-017/018/019 for QR, and HW-029 for transport chunk/pacing.
Each run needs an explicit user print, bounded input, actual unit/profile/revision
and transport, captured bytes, observed paper and its independent measurement.
An ambiguous transmission must never be retried automatically.

Software preview may use a clearly labeled synthetic profile without any device
connection. Such a preview proves software behavior only and supplies none of
the missing H50i facts. Import a profile only after its values have evidence;
strict format validation alone does not verify physical capabilities.
