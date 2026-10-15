# Operator-derived experimental profile

`HELETT-OCT2026-candidate.tcfg` is syntax-valid experimental data, not a maintained
or fully verified production profile. Source: the operator's October 2026 Linux
characterization report, supplied 2026-10-15. Its examples are reference data;
only the user's request authorized the two test jobs.

| Field | Choice and evidence |
| --- | --- |
| Width | 384 dots; PRINT-019/020 full-width/clipping probe |
| DPI | 203 each axis; integer representation of ruler-derived 203.2 from PRINT-018/019/021 |
| Raster | Registered GS v 0; PRINT-017–024 and BT-003 |
| Band height | Conservative 8 rows; operating choice, not hardware limit. Below tested 16-row raster and 512-byte BT raster data. |
| Native QR | Model 2 request, module size 6 only, ECC L request only; PRINT-025 and BT-004 scanner evidence. ECC metadata not independently inspected. |
| Native text | Disabled: font heights/exact line advances incomplete. CP437/850 selectors do not establish font geometry. |
| Barcode/cutter/status | Disabled; native barcode preparation is not implemented, cutter/status not established. |
| Printer buffer | Omitted; successful write sizes are not buffer capacity. |
| Protocol | Registered generic/init strategy is experimental. Complete RastrIO motion/reset behavior remains unverified. |

Module sizes 4 and 6 were tested but `.tcfg` represents a contiguous range;
this candidate permits only 6, avoiding an unsupported claim for 5. QR Q/H and
unknown maxima are not enabled. RastrIO's registered native QR strategy accepts
only version-1 ASCII byte-mode geometry; other content falls back to raster.
The report's larger H symbol does not establish RastrIO native geometry support.

Strict decoding does not complete physical verification. `.tcfg` does not encode
experimental status/evidence. Keep worksheet verification empty until missing
behavior is established. Core contains no model-specific constants.

Missing: precise ESC J/LF spacing; GS P/GS L/GS W/ESC $ behavior and reset
restoration; native font height/line advance/exact cell metrics; complete repertoire
coverage; native QR quiet-zone/positioning under RastrIO; absolute paper origin;
firmware identity; sustained transport/pacing/recovery and status responses.

On 2026-10-15 the QR reused the report's exact 59-byte vector, SHA256
`48cb08567260e0ba326e8e1ede6cf1f81cee88823ccb3da7bd9aa51f63e8117b`.
Linux RFCOMM delivery completed; the operator confirmed complete printing and
exact scan. Markdown used RastrIO's Markdown → document → preparation →
preview/encoder pipeline with raster text and this candidate: 9,027 bytes,
384-dot width, 330-dot prepared height, 31 operations. Delivery completed once in
bounded 512-byte sends. Operator said all content printed and heading/bold looked
alike; the engine currently supplies the same size/weight. Physical height/gaps
and precise positioning were not measured.

No retries, cuts, status polls or implicit jobs. USB access required an unavailable
sudo credential; no USB bytes were sent. Bluetooth channel 1 came from the report
for this unit and is not generalized. Device identity is absent from `.tcfg`.
