# Android QR preview and H50i integration report

Completed software work: **2026-10-13**. Required runnable software checks pass.
**Android native instrumentation is NOT PASSED; physical H50i acceptance is NOT
PASSED.** This establishes the minimum Phase 14 foundation, not full phase exit
or a verified H50i profile. Changes remain unstaged and uncommitted.

## QR diagnosis and resolution

The original emulator symptom could not be directly reproduced: the available
AVD crashes before the instrumentation harness can execute. Source inspection
and failing-then-passing UI regressions establish three integration faults:

1. Imported-profile preparation was exposed only after selecting a paired
   printer. A valid imported target can now preview offline without a device.
2. Removing/replacing an imported profile left template/Markdown preparation
   stale. Target changes now cancel/invalidate preparation and reconfigure image
   preparation too. Removal explicitly selects the labeled synthetic test target
   with APP140; stale physical output cannot remain printable.
3. Logical preview intentionally shows QR intent as a placeholder. The old
   two-step UI obscured physical preview. **Preview QR symbol** now builds and
   prepares the current form and opens physical preview on success, with the
   logical/physical distinction explained.

The committed app already had a suitable synthetic narrow profile, so absent
physical-printer configuration alone did not explain every symptom. The traced
pipeline is TemplateForms → TemplateDocumentBuilder → PhysicalPreviewSession →
BasicPreparationEngine → QR strategy/matrix/finalized bands → PreparedPrint →
toPrintPreview → PhysicalPreviewCanvas. No Core QR algorithm change was needed.
Physical preview and ESC/POS consume the same finalized prepared artifacts;
neither regenerates a QR matrix. Native QR appearance remains representative.

Coverage distinguishes missing/provisional targets, valid offline targets,
unsupported native strategy, invalid/non-square geometry, preparation errors,
missing text runtime (TXT132), painted preview pixels and stale transitions.
Twenty-four raster cases cover ASCII/Unicode, 12/20/30 mm and all four correction
levels, comparing preview artifacts with encoded raster bytes. These tests do
not establish that the Android native backend loads on a device.

## Actual changes

- Shared App, TemplateScreen and PrintingControls: direct QR preview, offline
  imported-profile action, invalidation, structured diagnostics and
  configuration-only management assignment. Independent review caught management
  selection preparing retained Markdown that was not displayed; a failing
  regression confirmed it, and selection now only configures targets.
- New shared PrinterManagementScreen: Home access, effective profile identity,
  revision/geometry, synthetic versus physically unverified status, existing
  printer discovery/selection and assignment, and Printer Lab access. Existing
  private persistent PrinterInstance identity is reused. Identity/transport stay
  outside portable profiles/documents; no duplicate persistence or Android-only
  screen was introduced.
- Printer Lab: blocked provisional H50i inventory/calibration guidance and a
  bounded HW-018 draft (30 mm, High correction, forced Raster, no cut). Creating
  the draft does not connect or print. Its example.invalid URL is QR content and
  is never fetched.
- Shared PrinterProfileFiles: strict Core validation/codec for import/export;
  original PRF diagnostics preserved; synthetic IDs rejected for physical
  assignment (PRF150). Schema, strategy registry, Core boundaries and dependencies
  remain unchanged.
- Android MainActivity/AndroidPrintingPlatform: user-selected SAF export,
  validated canonical snapshot, 1 MiB bound checked before opening, chunks at
  most 8192 bytes, serialized exports, cancellation/close and no retries. APP143
  reports failed/possibly partial export. Export contains no private printer
  identity. Timeout is cooperative: an unresponsive provider open/close can
  outlast it; provider I/O interruption is not guaranteed.
- Tests: strict-reader/round-trip profiles; QR artifact/byte matrix and structured
  failures; seven shared JVM UI cases including painted pixels; three Android
  export-stream cases. Existing malformed-image UI test now scrolls to lazy-list
  controls after added controls changed composition visibility; image production
  behavior is unchanged.
- Evidence: [H50i draft](H50I_DEVELOPMENT_DRAFT.md), corroboration in
  [ESC/POS notes](ESC_POS_NOTES.md), bounded HW-036/037 in
  [hardware procedures](HARDWARE_TESTS.md).

## H50i evidence and unknowns

The user-supplied self-test transcription corroborates the existing inventory;
no new physical test or photograph was obtained by the agent.

| Field | Evidence and qualification |
| --- | --- |
| Manufacturer/model | User identifies Helett H50i BillQuick Go reference unit |
| Paper / width | Reported 2 inches / 58 mm paper, 48 mm physical print width; dot width unknown |
| Font / charset | Reported 12×24 and CP437; selectors, line advance and host byte behavior unverified |
| Interfaces | Reported Bluetooth/USB; USB endpoints and reliable host behavior unverified |
| QR / EAN13 | Reported firmware support; host-command strategies unverified |
| Speed / density / autofeed | Reported maximum 60 mm/s, current density 5, autofeed 0 mm; not DPI/pacing evidence |
| Temperature | Reported 33, transient telemetry excluded from capabilities |

Horizontal/vertical DPI, reliable dot width, initialization/dialect, raster band
limits, CP437 ESC t parameter, native QR/barcode parameters and transport pacing
remain unknown. Neither 384 dots nor 203 DPI is assigned. No cutter capability is
enabled. Synthetic previews are never labeled as verified H50i output.

Mandatory schema facts are missing, so the baseline is a **non-executable
development draft**, not a selectable .tcfg. No validator was relaxed or schema
field added. Geometry must be independently established before creating a valid
profile; entering guessed values to bootstrap calibration would defeat this
gate. Strict file validation alone does not verify hardware.

## Verification: exact final commands

Commands ran at the repository root with independent Gradle runs serialized.
CHROME_BIN identifies the installed browser used by JS/Wasm tests.

```sh
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest --offline --console=plain --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process
```

**PASS**, 2m 2s; `/tmp/rastrio-qr-jvm-final.log`. Includes final management
regression and painted-pixel assertion.

```sh
env CHROME_BIN=/opt/brave.com/brave/brave GRADLE_OPTS=-Dorg.gradle.workers.max=2 ./gradlew check :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :desktopApp:compileKotlin :core:layout:resourceHeapProbe :core:raster:resourceHeapProbe :core:printer:resourceHeapProbe :core:escpos:resourceHeapProbe :core:text:resourceHeapProbe :core:raster:qrResourceHeapProbe --continue --offline --console=plain --max-workers=2
```

**PASS**, 4m 39s, 770 tasks (90 executed, 20 cached, 660 up-to-date);
`/tmp/rastrio-qr-cumulative-final.log`. Covers Core/profile/preparation/encoding/
preview, template/UI navigation, JVM/JS/Wasm, Android-host/unit tests, both APKs,
Desktop compilation, architecture verification and six resource probes.

```sh
env CHROME_BIN=/opt/brave.com/brave/brave GRADLE_OPTS=-Dorg.gradle.workers.max=2 ./scripts/verify.sh
```

**PASS**, 52s, 734 tasks (48 executed, 5 cached, 681 up-to-date), with
RastrIO verification passed; `/tmp/rastrio-qr-verify.log`.

Final direct-target XML inventory: **2048 tests, zero failures/errors, zero
skips**, excluding duplicate aggregate directories. Results include cached and
up-to-date tasks; not all 2048 executed afresh in the last command.
Compiler/Gradle warnings remain, without failed required checks.

Final audit: `git diff --check` passed; `git diff --cached --stat` was empty;
`git rev-parse HEAD` matched the base below. All six new Kotlin source files
retain the repository's full Apache-2.0 header and 2026 Darshan copyright.
The APK hashes and direct-target XML counts above were rechecked after the final
verification run.

Probe evidence includes bounded long-document layout; 100,000 raster rows with
4608-byte maximum bands; prepared-artifact admission failure under 128 MiB; text
run limits/cancellation; 32 QR symbols with 2960-byte maximum bands; and 2048
ESC/POS encodings/221,231,104 streamed bytes under 64 MiB.

Regression evidence: `/tmp/rastrio-qr-red-confirm.log` recorded four intended
integration failures before fixes; `/tmp/rastrio-management-red.log` recorded the
review-discovered hidden-preparation failure before its fix. Early parallel
builds collided in Kotlin generated-output/cache state and were not accepted as
baseline evidence. Serial recovery resolved that. First cumulative run failed
only the existing image UI test's uncomposed field; the scrolling fix passed full
final reruns. No failed check is represented as a pass.

## Android instrumentation: NOT PASSED

Available AVD: **Test**, configured x86_64 with 16 KiB Play Store image path
system-images/android-37.2/google_apis_playstore_ps16k/x86_64/. This describes
configuration, not an observed running API level or ABI.

| Attempt | Exact outcome |
| --- | --- |
| `android emulator start Test --headless` | Brief ADB device listing, then host qemu process 38487 SIGSEGV; `coredumpctl info 38487 --no-pager` confirms host crash; shell queries ended error: closed |
| `/home/darshan/Android/Sdk/emulator/emulator -avd Test -no-window -no-snapshot -no-audio -gpu swiftshader` | Process 40218 exited 139; shell queries error: closed; /tmp/rastrio-emulator-swiftshader.log |
| `/home/darshan/Android/Sdk/emulator/emulator -avd Test -no-snapshot -no-audio -gpu swiftshader` | Windowed process 41525 exited 139; ADB offline then absent; /tmp/rastrio-emulator-windowed.log |

Existing runner attempt:

```sh
adb -s emulator-5554 shell am instrument -w com.circuitnext.rastrio.test/com.circuitnext.rastrio.BundledTextRuntimeInstrumentation
```

Exit 1: adb: device 'emulator-5554' not found. Stored AVD configuration was not
changed or wiped. No APK could be installed/observed on a stable device.

| Required observation | Verdict |
| --- | --- |
| Running API/ABI and installed APK identity | Unavailable |
| Native renderer loading/runtime fonts | Not executed |
| Device shaping/raster generation | Not executed |
| Pixel/artifact device assertions | Not executed |
| Instrumentation gate | **NOT PASSED**, blocked by emulator host crash |

Built app SHA-256:
`d4625f1fc2c05dffa4e8cf7b689871361d6bd06ddb252929f5309b202e915523`.
Built test APK SHA-256:
`f7e027e1723d9fe080d9352cb1325daea7ddb2fa4afde489ed045781c2a2398b`.
Static app ZIP inspection verified 11 fonts (4,362,164 bytes), matching controlled
sources and manifest hashes, and native libraries for arm64-v8a, armeabi-v7a, x86
and x86_64. Packaging evidence is not device loading or Phase 11 acceptance.
The app launch reported by the user remains insufficient.

## Independent reviews

Architecture/minimalism review found the management retained-Markdown issue.
Its regression failed before the configuration-only fix; final read-only
recheck confirmed resolution and no further actionable finding.

Targeted Codex Security review completed with **no reportable vulnerabilities**,
covering profile import/export, identity separation, stream bounds/cancellation,
QR preparation and UI transitions. Preflight was ready (three passing checks);
the same scan was sealed once. The final management guard received a separate
read-only recheck without altering sealed artifacts. Cooperative provider I/O
cancellation remains the documented limitation.

Scan ID: 6d98f2b2-9b45-4f74-b583-31ec1b78f2ce.
Managed report:
`/home/darshan/.codex/state/plugins/codex-security/scans/RastrIO/0fb2e971efa9e8bd2e42611e26f1d45d9f9eab56_20261013T092100Z_ro2ub5cz/report.md`.
Available host aggregate accounting reported 5,192,892 tokens including 4,863,872
cached across four threads; it does not isolate this scan's worker cost.

Graphify was attempted before source discovery but its map predates Phases
11–13. Serena Kotlin discovery failed on language-server archive extraction.
AGENTS.md fallback used targeted authoritative source/test reads. Tool maps did
not replace normative specifications or source evidence.

## Human hardware gates and continuation

No physical print, scan or capability promotion was performed. Execute permanent
procedures with the actual unit, profile/revision and transport:

- HW-035 inventory; HW-037 independent geometry/profile gate; HW-012 width.
- HW-001/002/004 native font geometry; HW-036 CP437 selector/repertoire.
- HW-007 raster compatibility; HW-018/019 raster QR and external scanning.
- HW-017 native QR only after command prerequisites are established.
- HW-029 Bluetooth chunk/pacing, separate from printer raster-band limits.

Procedures require bounded input, explicit user printing, recorded transmitted
bytes and observed paper, no automatic retry after ambiguous output and no silent
promotion. EAN13 host execution remains outside this change because the semantic/
prepared barcode operation is absent; firmware support alone does not establish
a host strategy.

Before full Phase 14: repair the emulator or use a functioning API 24+ Android
device and run the existing harness; establish physical H50i geometry and one
reliable output path; then finish remaining profile/catalog/validated-override
and calibration scope under the PRD. Minimum UI/import/export foundation here
does not imply full Phase 14 completion.

Phase 15 Android UX/release work should follow runtime and hardware acceptance.
Phase 16 is **Android USB**: discovery, permissions, endpoints, PrinterInstance
integration and session/write safety remain platform-owned and transmit the same
PreparedPrint bytes. Reported USB presence verifies none of that behavior and
must not lead to Core semantic/preparation duplication.

## Recommended commit (manual review only)

```text
fix(preview): resolve QR and printer-profile integration

Expose offline imported-profile previews and direct QR physical preview,
invalidate plans when profiles change, and keep management assignment from
preparing undisplayed content. Add shared printer management and Lab access
with validated Android profile export and a non-executable H50i evidence draft.

Verify JVM/JS/Wasm/Android-host checks, APK assembly, Desktop compilation,
architecture, six resource probes and scripts/verify.sh. Android instrumentation
remains blocked by emulator SIGSEGV; physical H50i acceptance is pending.
```

Base HEAD remains 0fb2e971efa9e8bd2e42611e26f1d45d9f9eab56. Pre-existing
untracked phase reports are preserved. No Git index/history mutation was made.
