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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.core.text.*
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/** Explicitly resolved, immutable decoded pixels. Width/height are selected by physical preparation. */
data class ResolvedRasterImage(
    val image: DecodedImage,
    val options: RasterOptions = RasterOptions(image.width, image.height),
)

/** Native and image preparation; no encoder, host fonts, implicit asset resolution, or device I/O. */
class BasicPreparationEngine(
    private val resources: PreparationResourcePolicy = PreparationResourcePolicy(),
    private val layoutResources: LayoutResourcePolicy = LayoutResourcePolicy(),
    private val profileLimits: PrinterProfileValidationLimits = PrinterProfileValidationLimits(
        600, 1024, 256, 8, 1024, 131072, 16, 65536, 1000, 16,
    ),
) {
    fun prepare(
        document: ThermalDocument,
        profile: PrinterProfile,
        options: PrintOptions = PrintOptions(),
        override: PrinterProfileOverride = PrinterProfileOverride(),
        resolvedImages: Map<AssetReference, ResolvedRasterImage> = emptyMap(),
        isCancelled: () -> Boolean = { false },
    ): PreparationResult = try {
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        if (resolvedImages.size > layoutResources.maxBlocks) fail("PRN120", "Resolved image count exceeds preparation policy")
        val images = resolvedImages.toMap()
        val validator = PrinterProfileValidator(BaselineTrustedProfileRegistry, profileLimits)
        val validated = when (val result = validator.validate(profile)) {
            is ProfileValidationResult.Success -> result.value
            is ProfileValidationResult.Failure -> profileFailure(result.diagnostics)
        }
        val effective = when (val result = validator.resolve(validated, override)) {
            is ProfileValidationResult.Success -> result.value.profile
            is ProfileValidationResult.Failure -> profileFailure(result.diagnostics)
        }
        if (document.schemaVersion != 1) fail("PRN100", "Unsupported document version")
        if (document.layout.orientation != Orientation.PORTRAIT) fail("PRN104", "Landscape preparation is unavailable")
        val capability = effective.nativeText
        preflight(document, images, capability.supported, isCancelled)
        if (!capability.supported && (options.nativeFontId != null || options.codePageId != null ||
            options.widthScale != 1 || options.heightScale != 1 || options.nativeUnderline)) fail("PRN101", "Native options are unavailable")
        if (options.nativeUnderline && !capability.styles.underline)
            fail("PRN102", "Requested native underline is unsupported")
        val font = if (!capability.supported) null else if (options.nativeFontId == null) capability.fonts.first()
            else capability.fonts.firstOrNull { it.id == options.nativeFontId }
                ?: fail("PRN101", "Selected native font is unavailable")
        val page = if (!capability.supported) null else if (options.codePageId == null) capability.codePages.first()
            else capability.codePages.firstOrNull { it.id == options.codePageId }
                ?: fail("PRN101", "Selected code page is unavailable")
        if (capability.supported && (options.widthScale !in capability.styles.widthScales || options.heightScale !in capability.styles.heightScales))
            fail("PRN102", "Selected native scale is unsupported")
        // Image-only spacing uses two vertical dots; these are not invented native font capabilities.
        val cellWidth = font?.let { multiply(it.cellWidthDots, options.widthScale) } ?: 1
        val cellHeight = font?.let { multiply(it.cellHeightDots, options.heightScale) } ?: 2
        val lineAdvance = font?.let { multiply(it.lineAdvanceDots ?: it.cellHeightDots, options.heightScale) } ?: 2
        if (lineAdvance < cellHeight) fail("PRN102", "Overlapping native line advances are unavailable")
        val cutMode = options.cutMode.name.lowercase()
        val automatic = when (options.cutIntent) {
            CutIntent.AUTOMATIC -> {
                if (!effective.cutter.supported || cutMode !in effective.cutter.modes)
                    fail("PRN103", "Requested automatic cut is unsupported")
                true
            }
            CutIntent.AUTOMATIC_OR_MANUAL -> effective.cutter.supported && cutMode in effective.cutter.modes
            else -> false
        }
        val guide = options.cutIntent == CutIntent.MANUAL_GUIDE ||
            options.cutIntent == CutIntent.AUTOMATIC_OR_MANUAL && !automatic
        val g = effective.geometry
        val availableMm = g.printableWidthDots.toDouble() / g.horizontalDpi * 25.4
        val requested = document.layout.maxWidth?.also {
            if (it.unit != LengthUnit.MM || !it.value.isFinite() || it.value <= 0.0 || it.value > layoutResources.maxWidthMm)
                fail("PRN100", "Invalid document width limit")
        }?.value
        val widthMm = min(availableMm, requested ?: availableMm)
        // Floor the logical cell tick so a full exact-dot row is not lost to upward tick rounding.
        val cellMm = floor(cellWidth.toDouble() / g.horizontalDpi * 25.4 * 1_000_000.0) / 1_000_000.0
        val heightMm = cellHeight.toDouble() / g.verticalDpi * 25.4
        val advanceMm = lineAdvance.toDouble() / g.verticalDpi * 25.4
        // Cell-bottom baseline is equivalent top-cell geometry, not a claimed glyph ascent.
        val metrics = TypographyMetrics(heightMm, 0.0, advanceMm - heightMm, advanceMm, heightMm)
        val body = ResolvedTypography(font?.let { "native:${it.id}" } ?: "image-spacing-v1", TextStyle(), metrics,
            FixedCellGeometry(cellMm, cellMm, heightMm))
        val context = TypographyContext(body, body.copy(style = TextStyle(role = TextRole.CODE)),
            body.copy(style = TextStyle(role = TextRole.LIST_MARKER)),
            SnapshotList(List(6) { body.copy(style = TextStyle(weight = TextWeight.BOLD, role = TextRole.HEADING)) }))
        val constraints = LayoutConstraints(Length(widthMm, LengthUnit.MM), Orientation.PORTRAIT,
            context, layoutResources.copy(maxTextCodeUnits = min(layoutResources.maxTextCodeUnits, resources.maxTextCodeUnits)))
        val measurer = if (page != null) RegisteredFixedCellMeasurer(page.repertoire,
            TextResourcePolicy(min(64 * 1024, min(resources.maxTextCodeUnits, layoutResources.maxItems))))
            else TextMeasurer { TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT101", "Image-only layout cannot measure text")))) }
        val logical = when (val result = FoundationLayoutEngine(measurer).layout(document, constraints)) {
            is LayoutResult.Success -> result.document
            is LayoutResult.Failure -> {
                if (result.diagnostics.size > resources.maxDiagnostics) fail("PRN120", "Diagnostic count exceeds preparation policy")
                throw PreparationAbort(SnapshotList(result.diagnostics.map {
                    PreparationDiagnostic(it.code, it.message, it.sourceBlockIndex)
                }))
            }
        }
        if (logical.diagnostics.size > resources.maxDiagnostics) fail("PRN120", "Diagnostic count exceeds preparation policy")
        val target = PreparedTarget(effective.profileId, effective.profileRevision, g.horizontalDpi, g.verticalDpi,
            g.printableWidthDots, (effective.protocol as EscPosProtocolProfile).dialectId,
            (effective.protocol as EscPosProtocolProfile).initializationStrategy)
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        val preparedFont = font?.let { PreparedNativeFont(it.id, it.selector, it.cellWidthDots, it.cellHeightDots,
            it.lineAdvanceDots ?: it.cellHeightDots) }
        val preparedPage = page?.let { PreparedCodePage(it.id, it.repertoire, it.selector) }
        val job = BasicPhysicalPlan(resources, effective, target, preparedFont, preparedPage, options, images, isCancelled)
        job.blocks(logical.blocks)
        job.feedTo(dots(logical.heightMm, g.verticalDpi, resources.maxHeightDots))
        job.feed(options.finalFeedDots)
        if (guide) job.manualGuide()
        if (automatic) job.cut(options.cutMode)
        val diagnostics = logical.diagnostics.map { PreparationDiagnostic(it.code, it.message, it.sourceBlockIndex) }.toMutableList()
        if (guide && options.cutIntent == CutIntent.AUTOMATIC_OR_MANUAL) {
            if (diagnostics.size >= resources.maxDiagnostics) fail("PRN120", "Diagnostic count exceeds preparation policy")
            diagnostics.add(PreparationDiagnostic("PRN201", "Automatic cut unavailable; explicit manual-guide fallback selected"))
        }
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        PreparationResult.Success(PreparedPrint(logical, target, SnapshotList(job.operations),
            SnapshotList(diagnostics),
            job.cursor, resources))
    } catch (failure: PreparationAbort) {
        PreparationResult.Failure(failure.diagnostics)
    } catch (_: IllegalArgumentException) {
        PreparationResult.Failure(SnapshotList(listOf(PreparationDiagnostic("PRN120", "Preparation exceeds safe geometry or resource bounds"))))
    }

    private fun preflight(document: ThermalDocument, images: Map<AssetReference, ResolvedRasterImage>,
        nativeAvailable: Boolean, isCancelled: () -> Boolean) {
        if (document.blocks.size > layoutResources.maxBlocks) fail("PRN120", "Block count exceeds preparation policy")
        var textUnits = 0L
        var inlineNodes = 0L
        fun text(value: String, source: Int) {
            if (!nativeAvailable) fail("PRN101", "Native text is unavailable; raster text is not implemented", source)
            if (value.length.toLong() > resources.maxTextCodeUnits.toLong() - textUnits)
                fail("PRN120", "Source text exceeds preparation policy", source)
            textUnits += value.length
        }
        var blockCount = 0L
        var items = 0L
        fun inlines(content: List<InlineContent>, source: Int) {
            val stack = mutableListOf(content.iterator())
            while (stack.isNotEmpty()) {
                if (!stack.last().hasNext()) { stack.removeAt(stack.lastIndex); continue }
                if (++inlineNodes > layoutResources.maxInlineNodes) fail("PRN120", "Inline count exceeds preparation policy", source)
                val children = when (val node = stack.last().next()) {
                    is Text -> { text(node.text, source); null }
                    is InlineCode -> { text(node.text, source); null }
                    is Strong -> node.children
                    is Emphasis -> node.children
                    is Strike -> node.children
                    is Link -> node.children
                    LineBreak -> null
                }
                if (children != null && children.isNotEmpty()) {
                    if (stack.size >= layoutResources.maxInlineDepth) fail("PRN120", "Inline nesting exceeds preparation policy", source)
                    stack.add(children.iterator())
                }
            }
        }
        data class Frame(val blocks: Iterator<DocumentBlock>, val source: Int?, var position: Int = 0)
        val stack = mutableListOf(Frame(document.blocks.iterator(), null))
        while (stack.isNotEmpty()) {
            if (isCancelled()) fail("PRN130", "Preparation cancelled")
            val frame = stack.last()
            if (!frame.blocks.hasNext()) { stack.removeAt(stack.lastIndex); continue }
            val source = frame.source ?: frame.position
            frame.position++
            if (++blockCount > layoutResources.maxBlocks) fail("PRN120", "Block count exceeds preparation policy", source)
            val children = when (val block = frame.blocks.next()) {
                is Paragraph -> { inlines(block.content, source); null }
                is Heading -> { inlines(block.content, source); null }
                is CodeBlock -> { text(block.text, source); null }
                com.circuitnext.rastrio.core.document.Separator -> null
                is Image -> {
                    if (block.asset !in images) fail("PRN104", "Image asset has not been explicitly resolved", source)
                    null
                }
                is Quote -> block.blocks.iterator()
                is OrderedList -> {
                    if (!nativeAvailable) fail("PRN101", "Native list markers are unavailable", source)
                    items += block.items.size
                    if (items > layoutResources.maxItems) fail("PRN120", "Item count exceeds preparation policy", source)
                    sequence { for (item in block.items) yieldAll(item.blocks) }.iterator()
                }
                is Table -> {
                    if (block.columns.size > layoutResources.maxTableColumns ||
                        block.rows.size > layoutResources.maxTableRows)
                        fail("PRN120", "Table dimensions exceed preparation policy", source)
                    var cells = 0L
                    fun checkRow(row: List<TableCell>) {
                        cells += row.size
                        items += row.size
                        if (cells > layoutResources.maxTableCells || items > layoutResources.maxItems)
                            fail("PRN120", "Table cells exceed preparation policy", source)
                        for (cell in row) inlines(cell.content, source)
                    }
                    checkRow(block.header)
                    for (row in block.rows) checkRow(row)
                    null
                }
                else -> fail("PRN104", "This content requires a later preparation strategy", source)
            }
            if (children != null && children.hasNext()) {
                if (stack.size >= layoutResources.maxBlockDepth) fail("PRN120", "Block nesting exceeds preparation policy", source)
                stack.add(Frame(children, source))
            }
        }
    }

    private fun profileFailure(diagnostics: ProfileList<ProfileDiagnostic>): Nothing {
        if (diagnostics.size > resources.maxDiagnostics) fail("PRN120", "Diagnostic count exceeds preparation policy")
        throw PreparationAbort(SnapshotList(diagnostics.map { PreparationDiagnostic(it.code, it.message) }))
    }
}

private class PreparationAbort(val diagnostics: SnapshotList<PreparationDiagnostic>) : RuntimeException()
private fun fail(code: String, message: String, source: Int? = null): Nothing =
    throw PreparationAbort(SnapshotList(listOf(PreparationDiagnostic(code, message, source))))
private fun multiply(value: Int, scale: Int): Int {
    if (value <= 0 || scale <= 0 || value > Int.MAX_VALUE / scale) fail("PRN120", "Native cell geometry exceeds safe bounds")
    return value * scale
}
private fun dots(mm: Double, dpi: Int, maximum: Int): Int {
    val converted = LogicalGeometry.normalize(mm) / 25.4 * dpi
    if (!converted.isFinite() || converted < 0.0) fail("PRN120", "Invalid physical coordinate")
    val rounded = round(converted)
    if (rounded > maximum.toDouble()) fail("PRN120", "Physical coordinate exceeds preparation policy")
    return rounded.toInt()
}

private class BasicPhysicalPlan(
    private val resources: PreparationResourcePolicy,
    private val profile: PrinterProfile,
    private val target: PreparedTarget,
    private val font: PreparedNativeFont?,
    private val page: PreparedCodePage?,
    private val options: PrintOptions,
    private val images: Map<AssetReference, ResolvedRasterImage>,
    private val isCancelled: () -> Boolean,
) {
    val operations = mutableListOf<PreparedOperation>()
    var cursor = 0
        private set
    private var textUnits = 0L
    private var graphicBytes = 0L
    private val cellWidth = font?.let { multiply(it.cellWidthDots, options.widthScale) } ?: 1
    private val cellHeight = font?.let { multiply(it.cellHeightDots, options.heightScale) } ?: 2
    private var rasterOrdinal = 0
    private fun reserve(count: Int = 1) {
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        if (count > resources.maxOperations - operations.size) fail("PRN120", "Operation count exceeds preparation policy")
    }
    private fun end(advance: Int): Int {
        if (advance < 0 || advance > resources.maxHeightDots - cursor) fail("PRN120", "Prepared length exceeds preparation policy")
        return cursor + advance
    }
    fun feedTo(y: Int) {
        if (y < cursor) fail("PRN120", "Physical operations would require backward paper movement")
        feed(y - cursor)
    }
    fun feed(advance: Int) {
        if (advance == 0) return
        reserve()
        val next = end(advance)
        operations.add(Feed(DotBounds(0, cursor, 0, advance), advance))
        cursor = next
    }
    private data class Element(val bounds: LogicalBounds, val source: Int,
        val runs: List<LogicalTextRun>? = null, val alignment: Alignment = Alignment.LEFT,
        val image: LogicalImagePlaceholder? = null)

    fun blocks(blocks: List<LogicalBlock>) {
        val elements = mutableListOf<Element>()
        fun add(element: Element) {
            if (elements.size >= resources.maxOperations) fail("PRN120", "Operation count exceeds preparation policy", element.source)
            elements.add(element)
        }
        fun text(block: LogicalTextBlock) {
            for (line in block.lines) add(Element(line.bounds, block.sourceBlockIndex, line.runs, line.alignment))
        }
        val stack = mutableListOf(blocks.iterator())
        while (stack.isNotEmpty()) {
            if (!stack.last().hasNext()) { stack.removeAt(stack.lastIndex); continue }
            val children = when (val block = stack.last().next()) {
                is LogicalTextBlock -> { text(block); null }
                is LogicalSeparator -> { add(Element(block.bounds, block.sourceBlockIndex)); null }
                is LogicalImagePlaceholder -> { add(Element(block.bounds, block.sourceBlockIndex, alignment = block.alignment, image = block)); null }
                is LogicalQuoteBlock -> block.blocks.iterator()
                is LogicalListBlock -> {
                    if (block.kind != LogicalListKind.ORDERED)
                        fail("PRN104", "Graphic list markers require a later preparation strategy", block.sourceBlockIndex)
                    for (item in block.items) {
                        val marker = item.marker as? LogicalOrderedMarker
                            ?: fail("PRN104", "List marker requires a later preparation strategy", block.sourceBlockIndex)
                        add(Element(marker.bounds, block.sourceBlockIndex, listOf(marker.run), Alignment.RIGHT))
                    }
                    sequence { for (item in block.items) yieldAll(item.blocks) }.iterator()
                }
                is LogicalTableBlock -> {
                    for (row in block.rows) for (cell in row.cells) text(cell.text)
                    null
                }
                else -> fail("PRN104", "This content requires a later preparation strategy", block.sourceBlockIndex)
            }
            if (children != null) stack.add(children)
        }
        elements.sortWith(compareBy<Element>({ dots(it.bounds.yMm, target.verticalDpi, resources.maxHeightDots) }, { it.bounds.xMm }))
        var position = 0
        while (position < elements.size) {
            if (isCancelled()) fail("PRN130", "Preparation cancelled")
            val first = elements[position]
            val y = dots(first.bounds.yMm, target.verticalDpi, resources.maxHeightDots)
            feedTo(y)
            if (first.image != null) {
                image(first.image)
                position++
                continue
            }
            if (first.runs == null) {
                val x = dots(first.bounds.xMm, target.horizontalDpi, target.printableWidthDots)
                val width = dots(first.bounds.widthMm, target.horizontalDpi, target.printableWidthDots)
                val height = maxOf(1, dots(first.bounds.heightMm, target.verticalDpi, resources.maxHeightDots))
                graphic(x, width, height, first.source, false)
                position++
                continue
            }
            var groupEnd = position
            var height = 0
            var runCount = 0
            while (groupEnd < elements.size) {
                val element = elements[groupEnd]
                if (element.runs == null || dots(element.bounds.yMm, target.verticalDpi, resources.maxHeightDots) != y) break
                height = maxOf(height, dots(element.bounds.heightMm, target.verticalDpi, resources.maxHeightDots))
                if (element.runs.size > resources.maxOperations - runCount) fail("PRN120", "Operation count exceeds preparation policy", element.source)
                runCount += element.runs.size
                groupEnd++
            }
            // Different columns can overlap vertically; advance only as far as the next resolved row.
            val advance = if (groupEnd < elements.size) min(height,
                dots(elements[groupEnd].bounds.yMm, target.verticalDpi, resources.maxHeightDots) - y) else height
            val next = end(advance)
            reserve(runCount)
            if (runCount == 0) feed(advance)
            else {
                var emitted = 0
                for (index in position until groupEnd) {
                    val element = elements[index]
                    for (run in element.runs!!) {
                        native(run, element.alignment, element.source, y, if (++emitted == runCount) advance else 0)
                    }
                }
                cursor = next
            }
            position = groupEnd
        }
    }

    private fun native(run: LogicalTextRun, alignment: Alignment, source: Int, y: Int, advance: Int) {
        val font = font ?: fail("PRN101", "Native text is unavailable", source)
        val page = page ?: fail("PRN101", "Native code page is unavailable", source)
        val semantic = run.typography.style
        val bold = semantic.weight == TextWeight.BOLD
        val underline = semantic.underline || options.nativeUnderline
        if (semantic.emphasis || semantic.strike || bold && !profile.nativeText.styles.bold ||
            underline && !profile.nativeText.styles.underline)
            fail("PRN102", "Requested text style has no supported native strategy", source)
        val x = dots(run.bounds.xMm, target.horizontalDpi, target.printableWidthDots)
        val runWidth = dots(run.bounds.widthMm, target.horizontalDpi, target.printableWidthDots)
        val content = if (run.text == "\t") {
            if (runWidth % cellWidth != 0) fail("PRN120", "Tab geometry cannot be represented by native cells", source)
            val count = runWidth / cellWidth
            if (count.toLong() > resources.maxTextCodeUnits.toLong() - textUnits)
                fail("PRN120", "Native text exceeds preparation policy")
            " ".repeat(count)
        } else run.text
        if (content.length.toLong() > resources.maxTextCodeUnits.toLong() - textUnits)
            fail("PRN120", "Native text exceeds preparation policy", source)
        val bytes = when (val coverage = NativeTextRepertoires.assess(content, page.repertoire,
            TextResourcePolicy(min(64 * 1024, resources.maxTextCodeUnits)))) {
            is NativeCoverageResult.Representable -> coverage.characterBytes
            is NativeCoverageResult.Unsupported -> throw PreparationAbort(SnapshotList(coverage.diagnostics.map {
                PreparationDiagnostic(it.code, it.message, source)
            }))
            is NativeCoverageResult.Unknown -> throw PreparationAbort(SnapshotList(coverage.diagnostics.map {
                PreparationDiagnostic(it.code, it.message, source)
            }))
        }
        val width = multiplyWidth(content.length)
        if (width > target.printableWidthDots - x) fail("PRN120", "Native text exceeds printable strip", source)
        operations.add(NativeText(content, bytes, font, page,
            PreparedNativeStyle(bold, underline, options.widthScale, options.heightScale),
            when (alignment) { Alignment.LEFT -> PreparedAlignment.LEFT; Alignment.CENTER -> PreparedAlignment.CENTER; Alignment.RIGHT -> PreparedAlignment.RIGHT },
            DotBounds(x, y, width, cellHeight), advance,
            source))
        textUnits += content.length
    }
    private fun multiplyWidth(count: Int): Int {
        if (count > Int.MAX_VALUE / cellWidth) fail("PRN120", "Text width exceeds safe bounds")
        return count * cellWidth
    }
    private fun image(block: LogicalImagePlaceholder) {
        val source = block.sourceBlockIndex
        val resolved = images[block.asset] ?: fail("PRN104", "Image asset has not been explicitly resolved", source)
        val raster = profile.raster
        if (!raster.supported) fail("PRN104", "Image requires a supported raster strategy", source)
        val strategy = raster.preferredStrategy ?: fail("PRN104", "Image raster strategy is unavailable", source)
        val band = raster.bandHeightDots ?: fail("PRN104", "Image raster band geometry is unavailable", source)
        val boxWidth = dots(block.bounds.widthMm, target.horizontalDpi, target.printableWidthDots)
        val boxHeight = dots(block.bounds.heightMm, target.verticalDpi, resources.maxHeightDots)
        val boxX = dots(block.bounds.xMm, target.horizontalDpi, target.printableWidthDots)
        val o = resolved.options
        if (o.orientation !in 1..8) fail("PRN100", "Invalid image orientation", source)
        val orientedWidth = if (o.orientation >= 5) resolved.image.height else resolved.image.width
        val orientedHeight = if (o.orientation >= 5) resolved.image.width else resolved.image.height
        val crop = o.crop ?: ImageCrop(0, 0, orientedWidth, orientedHeight)
        if (crop.x < 0 || crop.y < 0 || crop.width <= 0 || crop.height <= 0 ||
            crop.x.toLong() + crop.width > orientedWidth || crop.y.toLong() + crop.height > orientedHeight)
            fail("PRN100", "Invalid image crop", source)
        var width = min(boxWidth, raster.maxWidthDots ?: 0)
        if (width <= 0 || boxHeight <= 0) fail("PRN120", "Image has no representable physical area", source)
        var height = maxOf(1L, (width.toLong() * crop.height * target.verticalDpi +
            crop.width.toLong() * target.horizontalDpi / 2) / (crop.width.toLong() * target.horizontalDpi))
        if (height > boxHeight) {
            height = boxHeight.toLong()
            width = maxOf(1L, height * crop.width * target.horizontalDpi /
                (crop.height.toLong() * target.verticalDpi)).toInt()
        }
        val rowBytes = (width + 7) / 8
        val payload = rowBytes.toLong() * height
        if (payload > resources.maxPreparedGraphicBytes - graphicBytes ||
            payload > resources.maxResidentGraphicBytes.toLong() - graphicBytes)
            fail("PRN120", "Finalized image exceeds prepared graphic budgets", source)
        val rows = minOf(band.preferred, band.max, resources.maxGraphicBandBytes / rowBytes)
        if (rows < band.min || (height + rows - 1) / rows > resources.maxOperations - operations.size)
            fail("PRN120", "Image bands exceed preparation policy", source)
        val x = boxX + when (block.alignment) {
            Alignment.LEFT -> 0
            Alignment.CENTER -> (boxWidth - width) / 2
            Alignment.RIGHT -> boxWidth - width
        }
        val start = cursor
        val bottom = end(boxHeight)
        val result = RasterEngine.process(resolved.image,
            o.copy(width = width, height = height.toInt(), bandHeight = rows, minBandHeight = band.min),
            RasterResourcePolicy(maxPayloadBytes = minOf(resources.maxPreparedGraphicBytes - graphicBytes,
                resources.maxResidentGraphicBytes.toLong() - graphicBytes), maxBandBytes = resources.maxGraphicBandBytes),
            isCancelled,
        ) { finalized ->
            if (isCancelled()) fail("PRN130", "Preparation cancelled", source)
            reserve()
            val content = PreparedMonochrome(finalized.width, finalized.height, finalized.copyBytes())
            operations.add(RasterOperation(DotBounds(x, start + finalized.startRow, finalized.width, finalized.height),
                content, strategy, rasterOrdinal++, source))
            graphicBytes += content.sizeBytes
            cursor = start + finalized.startRow + finalized.height
        }
        when (result) {
            is RasterResult.Invalid -> fail(if (result.code == "RAS120") "PRN120" else "PRN100", result.message, source)
            RasterResult.Cancelled -> fail("PRN130", "Preparation cancelled", source)
            is RasterResult.Completed -> Unit
        }
        feedTo(bottom)
    }
    fun manualGuide() = graphic(0, target.printableWidthDots, 1, null, true)
    private fun graphic(x: Int, width: Int, height: Int, source: Int?, manual: Boolean) {
        val raster = profile.raster
        if (!raster.supported || width <= 0 || width > (raster.maxWidthDots ?: 0))
            fail("PRN104", "Printed rule requires an available raster strategy", source)
        val band = raster.bandHeightDots ?: fail("PRN104", "Printed rule has no raster band geometry", source)
        if (height < band.min) fail("PRN104", "Printed rule is smaller than supported raster bands", source)
        if (width > target.printableWidthDots - x) fail("PRN120", "Printed rule exceeds printable strip", source)
        val next = end(height)
        val rowBytes = width / 8 + if (width % 8 == 0) 0 else 1
        val maxRows = min(band.max, resources.maxGraphicBandBytes / rowBytes)
        if (maxRows < band.min) fail("PRN120", "Raster band exceeds preparation policy", source)
        val bandCount = (height.toLong() + maxRows - 1) / maxRows
        if (bandCount > resources.maxOperations - operations.size) fail("PRN120", "Operation count exceeds preparation policy", source)
        if (rowBytes.toLong() * height > resources.maxPreparedGraphicBytes - graphicBytes)
            fail("PRN120", "Finalized graphics exceed preparation policy", source)
        if (rowBytes.toLong() * height > resources.maxResidentGraphicBytes.toLong() - graphicBytes)
            fail("PRN120", "Resident graphics exceed preparation policy", source)
        var remaining = height
        while (remaining > 0) {
            var rows = min(remaining, maxRows)
            if (remaining - rows in 1 until band.min) rows -= band.min - (remaining - rows)
            if (rows < band.min) fail("PRN104", "Rule cannot be split into supported raster bands", source)
            reserve()
            val bytes = ByteArray(rowBytes * rows)
            for (row in 0 until rows) for (column in 0 until width) {
                if (!manual || column % 16 < 8) {
                    val offset = row * rowBytes + column / 8
                    bytes[offset] = (bytes[offset].toInt() or (0x80 ushr (column % 8))).toByte()
                }
            }
            val content = PreparedMonochrome(width, rows, bytes)
            val bounds = DotBounds(x, cursor, width, rows)
            operations.add(if (manual) ManualCutGuide(bounds, content, raster.preferredStrategy!!, source)
                else Separator(bounds, content, raster.preferredStrategy!!, source))
            cursor += rows
            graphicBytes += bytes.size
            remaining -= rows
        }
        check(cursor == next)
    }
    fun cut(mode: CutMode) {
        reserve()
        operations.add(AutomaticCut(DotBounds(0, cursor, 0, 0), mode, profile.cutter.strategy!!))
    }
}
