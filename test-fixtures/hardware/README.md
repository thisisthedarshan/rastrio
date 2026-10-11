# Hardware fixtures

Deterministic project-owned non-sensitive inputs, Apache-2.0. These are fixtures,
not hardware evidence. Follow the permanent HW-NNN procedures before reporting
physical acceptance. Keep profile/device identities in private evidence records.

HW-001/002/005/006 Markdown is ready for explicit compile/preparation. HW-003
alignment must be expressed through semantic Alignment values (ordinary Markdown
has no center/right directive). HW-004 uses the transient native underline
choice below, after verifying the selected profile's underline capability. No raw
control bytes or HTML workaround is included. Print each job only once unless retry is acknowledged.

HW-007 includes odd width/padding. The shared gradient source exercises HW-008
threshold, HW-009 Bayer, HW-010 Atkinson and HW-011 Floyd–Steinberg through UI
algorithm selection. HW-012 uses 576 source pixels, not an H50i dot-width claim.
HW-013 tests long source handling and band continuity; semantic image placement
follows the documented finalized square outer box and aspect-preserving fitting.
Record actual effective output dimensions/options; source dimensions alone never
establish printer geometry. Real raster strategy/band capability evidence is
required before any live raster job.

## Semantic HW-003 / HW-004 preparation

This Kotlin fixture uses the normal public preparation path. `verifiedProfile`
must be the evidence-validated profile for the actual target, never a renamed
synthetic profile. Supply this prepared result to the ordinary preview/print
workflow; inspect it before authorizing one transmission.

```kotlin
import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.printer.*

val alignmentDocument = ThermalDocument(blocks = listOf(
    Paragraph(Alignment.LEFT, listOf(Text("LEFT"))),
    Paragraph(Alignment.CENTER, listOf(Text("CENTER"))),
    Paragraph(Alignment.RIGHT, listOf(Text("RIGHT"))),
))
val styleDocument = ThermalDocument(blocks = listOf(
    Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text("BOLD"))), Text(" PLAIN"))),
))
val engine = BasicPreparationEngine()
val alignmentResult = engine.prepare(alignmentDocument, verifiedProfile)
val boldResult = engine.prepare(styleDocument, verifiedProfile)
val underlineResult = engine.prepare(styleDocument, verifiedProfile,
    PrintOptions(nativeUnderline = true))
```

Only `PreparationResult.Success.print` is eligible for preview/encoding. Preserve
and report failure diagnostics instead of changing capability declarations to
force success. Bold and underline require their corresponding verified native
style capabilities; all fixtures retain the default no-cut choice.
