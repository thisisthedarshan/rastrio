# Helett H50i BillQuick Go development draft

Status: **experimental configuration available; production verification incomplete**.
The October Linux report now supplies measured raster geometry and host-command
observations. See the 2026-10-15 update below and its experimental candidate link.
Precise motion/positioning and native font geometry remain incomplete; the
portable schema and strict validator are unchanged.

## Historical self-test inventory — 2026-10-13

The initial inventory below was not executable `.tcfg`: dot geometry and reliable
host-command facts were missing at that time. It did not authorize transmission.

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

Missing at the initial self-test stage (historical; see the later update):

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


## Operator Linux characterization received 2026-10-15

The operator supplied a manual October 2026 study with USB-001–004, PRINT-001–039
and BT-001–004. This is supplied physical/measured/host evidence, not a new agent
execution of those procedures. It supersedes earlier unknown values only within
the tested scope: GS v 0 width384 with392-dot clipping; ruler-derived203.2 DPI
both axes; CP437 ESC t0/CP850 ESC t2 for listed characters; ESC M0/1 with A32/B42
column capacities; native QR GS(k model2 requests with4/6 modules and scanner
checks; native EAN13 GS k67 with scanner checks; USB writes through9228 bytes
and Classic RFCOMM channel1 ASCII/raster/QR writes through524 bytes. These are
tested operating points, not buffer, throughput or maximum-height limits.

Font heights/exact line advances, full repertoire coverage, precise ESC J/LF
spacing, RastrIO GS P/GS L/GS W/ESC $/reset behavior, QR quiet-zone positioning,
absolute paper origin, status and interruption recovery remain incomplete.
PRINT-038/039 produced equal2mm gaps for different ESC J arguments; do not mark
proportional feeding verified. Native EAN13 is not enabled by the application
until its prepared operation/encoder implementation exists.

See [experimental profile and exact scope](../test-fixtures/hardware/HELETT-OCT2026-candidate.md).
The candidate is outside maintained production profiles. On2026-10-15 the agent
sent one report-identical59-byte QR over Linux Bluetooth, and the operator
confirmed complete paper output and exact scan. Then one9027-byte RastrIO
Markdown raster job was sent in512-byte bounded sends; operator confirmed all
content printed, with heading/bold alike. No retry/cut/status commands were sent.
Current heading typography uses body metrics and bold weight, explaining that
observation without attributing it to unknown native printer font behavior.
