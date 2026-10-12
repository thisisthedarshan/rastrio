# Phase 11 and 13 hardware fixtures

These are non-sensitive reproducible inputs for HW-014 through HW-019. No procedure has been run on paper. Use the numbered acceptance procedures in `docs/HARDWARE_TESTS.md` and the actual printer's validated associated profile. Synthetic profiles exercise software only and must not authorize real printing.

| ID | Input | Required preparation and expectation |
| --- | --- | --- |
| HW-014 | `HW-014-unicode-raster-text.md` | `ő` (U+0151) and decomposed `e` + U+0301 are outside CP437's reliable native repertoire. Whole-line raster, genuine Noto Sans, visible combining accent, exact prepared-dot preview. |
| HW-015 | `HW-015-mixed-text.md` | First paragraph remains native on a verified CP437 profile; mixed ASCII/Devanagari paragraph becomes whole-line raster. No interleaved printer-native and raster glyph runs within its line. |
| HW-016 | `HW-016-complex-script.md` | Devanagari/Hindi: `हिन्दी`, `क्षि`, `नमस्ते`. Expected logical text is precisely the fixture text. `क्षि` is U+0915 U+094D U+0937 U+093F; preserve the conjunct and position the vowel sign using genuine shaping. Noto Sans Devanagari regular, not isolated code-point painting. Independent human script inspection remains part of acceptance. |
| HW-017 | `HW-017-native-qr.td` | Payload `HW-017`, Medium EC, centered, requested20mm. Force Native only on a verified Model2 profile. Version1 byte capacity and four-module quiet margins. The maintained H50i profile has no verified native QR; this procedure is capability-blocked there, never enable it from its self-test QR. |
| HW-018 | `HW-018-raster-qr.td` | Opaque payload `https://example.invalid/rastrio/hw-018`, High EC, centered, requested30mm. Force Raster; decode the printed result to exactly these bytes. The URL is test data and must not be opened or fetched. |
| HW-019 | `HW-019-qr-sizes.td` | Three Medium-EC `HW-019` symbols, centered, requested12/20/30mm. Force Raster. At synthetic203dpi this resolves to integer scales3/5/8 and87/145/232 dots including quiet zones. Actual profiles may resolve differently or reject a size; never distort/clip modules. |

All text uses checked-in fonts and the backend/version/hash configuration in `core/text/src/jvmAndAndroidMain/resources/rastrio-fonts/manifest.json` and `docs/TEXT_RENDERING_SPEC.md`. The exact source input is separate from rationale so acceptance comments do not accidentally become printable content.

Regenerate QR archives with `./gradlew :core:printer:generatePhase111213HardwareFixtures --offline`. The producer uses the public strict `TdArchive.save/load`, checks deterministic archive bytes and semantic equality, and validates synthetic preparation. `.td` contains no profile or strategy; strategy is an explicit per-job option. No hardware API is invoked by the producer.

For the current app, paste the Markdown sample into Markdown authoring, or enter the QR payload, requested size and EC in QR Message; select the actual associated profile and the specified QR preference, prepare, inspect physical preview, then explicitly print once. Portable archive consumers can load the `.td` fixture through `TdArchive.load`; this does not claim an application archive-import screen exists. Cancel/reconnect is never permission to automatically retry an ambiguous transmission.

Record profile revision/override, phone/app build, backend/font identity, actual module dimensions, diagnostic outcome, photographed paper, decoded payload, and observations under each numbered hardware record. Byte transfer is not proof of paper completion. All HW-014–019 acceptance remains pending; HW-017 additionally requires reliable native capability evidence.

## Android reference-backend runtime gate before paper acceptance

The built app and dedicated test APK can prove the primary Android JNI gate on a working API24+ device/emulator without opening Bluetooth or printing. This gate remains unexecuted in the implementation report: the available Test emulator crashed in its own host process and no physical Android device was connected.

From the repository root, build both artifacts, identify the intended device serial, and run:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest --offline
adb devices -l
adb -s DEVICE_SERIAL install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s DEVICE_SERIAL install -r -t androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w com.circuitnext.rastrio.test/com.circuitnext.rastrio.BundledTextRuntimeInstrumentation
```

`DEVICE_SERIAL` must be the connected device chosen from the inventory. Retain the complete instrumentation output, app/test APK hashes, Android API/ABI and build revision. Success requires the explicit `RastrIO Android reference backend and artifact gate PASS` result, zero failed status entries and final instrumentation result code-1; exit status alone is insufficient. No assertion establishes cross-ABI pixel identity, Bluetooth behavior, paper completion or QR scannability. An unavailable/failing backend is a software gate failure, not hardware acceptance. The command syntax follows the official [Android command-line test guide](https://developer.android.com/studio/test/command-line).
