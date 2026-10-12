# Portable QR reference verification

The common encoder adapts the byte-mode path from [Project Nayuki QR Code generator
1.8.0](https://github.com/nayuki/QR-Code-generator/tree/v1.8.0), preserving the MIT
notice in `QrMatrixEncoder.kt`. No external QR dependency enters production Core.
Exact correction is retained (`boostecl=False`), masks use the upstream penalty
rules and lowest-index tie break, and the smallest fitting Model 2 version is
selected. Opaque bytes use byte mode; UTF-8 ECI 26 is opt-in. Empty payloads are
rejected. `rasterBands` accepts profile-aware `minBandHeight` (default 1) and
preflights a balanced partition under the maximum band/scratch limits before
delivering any band. Numeric/alphanumeric optimization is deliberately absent because the
frozen API consumes opaque bytes.

`QrMatrixTest` contains 160 fixed vectors, one for the smallest payload requiring
each version 1–40 at every correction level. Payload byte `i` is `(i*149+37)%256`.
Each entry records byte length, selected mask and FNV-1a32 over row-major module
values 0/1. The full 21×21 `Hello, world!` matrix is also checked in. Expected data
was generated once with upstream Python 1.8.0, not with the Kotlin implementation:

- Python source SHA-256: `b089855caf16185c61421ea4927c1b213cf9468940d71fa8ab11ef83662dcc84`.
- `QrCode.encode_segments([QrSegment.make_bytes(payload)], correction, boostecl=False)`.

The independent offline decoder is [ZXing 3.5.3](https://github.com/zxing/zxing/tree/zxing-3.5.3),
Apache-2.0. Its test-only jar is supplied externally and is not bundled or fetched
by runtime printing or preview. Verify the Maven `com.google.zxing:core:3.5.3` jar
SHA-256 before use:
`8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.

After centrally compiling `:core:raster:jvmTestClasses`, use the JVM test runtime
classpath to run:

```text
java -cp "$RASTER_TEST_CLASSPATH" com.circuitnext.rastrio.core.raster.QrReferenceDecodeProbe "$FIXTURE_DIRECTORY"
javac -cp "$ZXING_CORE_JAR" -d "$DECODER_CLASSES" core/raster/reference-tools/DecodeQr.java
java -cp "$DECODER_CLASSES:$ZXING_CORE_JAR" DecodeQr "$FIXTURE_DIRECTORY"
java -Xmx16m -cp "$RASTER_TEST_CLASSPATH" com.circuitnext.rastrio.core.raster.QrResourceHeapProbe
```

Create fixture/output directories first. These commands perform no network I/O.
The producer exports actual final band pixels as PGM plus expected payload bytes:
ASCII, UTF-8 ECI (including Devanagari/combining text), all 256 opaque byte values,
and exact version-40 capacity, each at all four correction levels. The decoder
extracts the QR from these pixels and compares its byte segment with the expected
payload. It must decode exactly 16 fixtures. Fixture names and logged counts contain
only synthetic test data. The heap probe streams 32 version-40 symbols and retains
no receipt bitmap; cumulative payload is 8,761,600 bytes, max band 2,960 bytes.

Independent decode verifies software QR validity, not thermal-paper scannability.
Physical acceptance remains HW-018/HW-019.
