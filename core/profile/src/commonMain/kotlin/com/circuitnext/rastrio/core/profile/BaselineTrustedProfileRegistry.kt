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

package com.circuitnext.rastrio.core.profile

/** Baseline v1 compatibility metadata; device capability evidence remains profile-owned. */
object BaselineTrustedProfileRegistry : TrustedProfileRegistry {
    private val genericDialect = DialectDefinition()
    private val dialectIds = profileListOf("escpos.generic")
    private val strategies = mapOf(
        "escpos.init.standard" to StrategyDefinition(StrategyPurpose.INITIALIZATION, dialectIds),
        "escpos.font.esc-m" to StrategyDefinition(StrategyPurpose.FONT_SELECTION, dialectIds, 0..1),
        "escpos.code-page.esc-t" to StrategyDefinition(StrategyPurpose.CODE_PAGE_SELECTION, dialectIds, 0..255),
        "escpos.raster.gs-v-0" to StrategyDefinition(StrategyPurpose.RASTER, dialectIds),
        "escpos.qr.model2" to StrategyDefinition(StrategyPurpose.QR, dialectIds),
        "escpos.barcode.gs-k" to StrategyDefinition(StrategyPurpose.BARCODE, dialectIds),
        "escpos.cut.gs-v" to StrategyDefinition(StrategyPurpose.CUT, dialectIds),
        "escpos.status.dle-eot" to StrategyDefinition(StrategyPurpose.STATUS_QUERY, dialectIds),
    )

    override fun dialect(id: String): DialectDefinition? = if (id == "escpos.generic") genericDialect else null
    override fun strategy(id: String): StrategyDefinition? = strategies[id]
    override fun knowsRepertoire(id: String): Boolean = id == "cp437"
    override fun knowsQrModel(id: String): Boolean = id == "model2"
    override fun knowsBarcodeSymbology(id: String): Boolean = id == "code128" || id == "ean13"
    override fun quirk(id: String): QuirkDefinition? = null
}
