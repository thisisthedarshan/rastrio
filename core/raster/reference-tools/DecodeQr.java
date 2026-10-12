/**
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.file.*;
import java.util.*;

/** Offline independent ZXing 3.5.3 decode of the actual finalized raster fixtures. */
public final class DecodeQr {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Fixture directory required");
        int count = 0;
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".pgm")).sorted().toList()) {
                byte[] pgm = Files.readAllBytes(file);
                int offset = 0;
                String[] lines = new String[3];
                for (int i = 0; i < 3; i++) {
                    int start = offset;
                    while (pgm[offset] != '\n') offset++;
                    lines[i] = new String(pgm, start, offset++ - start, java.nio.charset.StandardCharsets.US_ASCII);
                }
                if (!lines[0].equals("P5") || !lines[2].equals("255")) throw new AssertionError("Unexpected PGM");
                String[] dimensions = lines[1].split(" ");
                int width = Integer.parseInt(dimensions[0]), height = Integer.parseInt(dimensions[1]);
                byte[] pixels = Arrays.copyOfRange(pgm, offset, pgm.length);
                if (pixels.length != width * height) throw new AssertionError("Invalid raster length");
                LuminanceSource source = new LuminanceSource(width, height) {
                    public byte[] getRow(int y, byte[] row) {
                        if (row == null || row.length < width) row = new byte[width];
                        System.arraycopy(pixels, y * width, row, 0, width);
                        return row;
                    }
                    public byte[] getMatrix() { return pixels; }
                };
                Result result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), Map.of(DecodeHintType.PURE_BARCODE, true));
                @SuppressWarnings("unchecked")
                List<byte[]> segments = (List<byte[]>) result.getResultMetadata().get(ResultMetadataType.BYTE_SEGMENTS);
                byte[] expected = Files.readAllBytes(Path.of(file.toString().replace(".pgm", ".payload")));
                if (segments == null || segments.size() != 1 || !Arrays.equals(expected, segments.get(0)))
                    throw new AssertionError("Decoded bytes differ: " + file.getFileName());
                count++;
            }
        }
        if (count != 16) throw new AssertionError("Expected 16 fixtures, got " + count);
        System.out.println("ZXing 3.5.3 offline QR decode: " + count + " finalized raster fixtures PASS");
    }
}
