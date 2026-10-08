# Synthetic printer-profile fixtures

These maintained v1 `.tcfg` files mirror `SyntheticReferenceProfiles` and the capability examples in `docs/TCFG_SPEC.md` §§30–32. All values are synthetic test data; none describe the H50i or any commercial printer.

- `synthetic-narrow.tcfg`: 384 dots, native text and raster, no optional native QR/barcode/cut/status capabilities.
- `synthetic-80mm.tcfg`: 576 dots, native text and raster, plus native QR, barcode, cutter and status capabilities.
- `synthetic-raster-only.tcfg`: 384 dots, raster with no native text and smaller bands.

Files use the production codec's compact canonical JSON representation (no trailing newline). `SyntheticReferenceFixtureTest` decodes their exact committed bytes with `BaselineTrustedProfileRegistry`, checks equality with the portable candidates, and requires exact canonical re-encoding. Update candidate data and fixtures together, then run `./gradlew :core:profile:jvmTest`.

The fixtures are JVM test resources only. Common tests exercise the equivalent immutable candidate data across configured targets without filesystem APIs. Profiles remain untrusted candidates until the production validator accepts them under an application-owned resource policy.
