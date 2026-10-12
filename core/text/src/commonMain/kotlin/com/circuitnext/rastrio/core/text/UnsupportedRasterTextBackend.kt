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

package com.circuitnext.rastrio.core.text

/** Application resolution obtains an explicit failure backend when native packaging is unavailable. */
expect fun createPlatformRasterTextBackend(): RasterTextBackend

class TextBackendUnavailableException(val diagnostics: SnapshotList<TextDiagnostic>) : IllegalStateException("Controlled raster text backend unavailable")

class UnsupportedRasterTextBackend : RasterTextBackend {
    override val identity = "unavailable-controlled-raster-text"
    private val diagnostics = SnapshotList(listOf(TextDiagnostic("TXT132", "Controlled raster text backend is unavailable on this target")))
    override fun typography(style: TextStyle, sizeMm: Double): ResolvedTypography = throw TextBackendUnavailableException(diagnostics)
    override fun measure(request: TextMeasureRequest): TextMeasureResult = TextMeasureResult.Failure(diagnostics)
    override fun rasterize(request: RasterTextRequest): RasterTextResult = RasterTextResult.Failure(diagnostics)
}
