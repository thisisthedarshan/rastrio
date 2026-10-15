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

package com.circuitnext.rastrio.printing

import android.content.Context
import android.content.SharedPreferences
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.printer.CutIntent
import com.circuitnext.rastrio.core.printer.CutMode

internal data class PrinterConfiguration(val printers: List<ConfiguredPrinter>, val selectedId: String?, val importedProfile: PrinterProfile? = null)

/** Versioned, bounded private snapshot. Portable profile bytes never contain device identity. */
internal class PrinterInstanceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("printer-instance", Context.MODE_PRIVATE))

    @Synchronized fun load(): PrinterInstance? = try {
        if (preferences.getInt("version", 0) != 1) null else PrinterInstance(
            preferences.getString("id", null) ?: "", preferences.getString("name", null) ?: "",
            PrinterTransportKind.BLUETOOTH_CLASSIC, preferences.getString("locator", null) ?: "",
            preferences.getString("profile", null) ?: "", preferences.getInt("revision", 0))
    } catch (_: RuntimeException) { null }

    @Synchronized fun save(instance: PrinterInstance): Boolean {
        require(instance.transportKind == PrinterTransportKind.BLUETOOTH_CLASSIC)
        return replace(mapOf("version" to 1, "id" to instance.id, "name" to instance.displayName,
            "locator" to instance.transportLocator, "profile" to instance.profileId, "revision" to instance.profileRevision))
    }
    @Synchronized fun clear(): Boolean = replace(emptyMap())

    @Synchronized fun loadConfiguration(): PrinterConfiguration? = try {
        require(preferences.getInt("version", 0) == 2)
        val count = preferences.getInt("count", -1)
        require(count in 0..32)
        var bytes = 0
        fun text(key: String, limit: Int): String {
            val value = preferences.getString(key, null) ?: error("Missing field")
            require(value.length <= limit)
            bytes += value.encodeToByteArray().size
            require(bytes <= 4 * 1024 * 1024)
            return value
        }
        val printers = (0 until count).map { index ->
            val key = "p$index."
            val base = decode(text(key + "base", 1024 * 1024))
            val mask = preferences.getInt(key + "mask", -1)
            require(mask in 0..2047)
            val effective = if (mask == 0) base else decode(text(key + "effective", 1024 * 1024))
            val overrides = if (mask == 0) null else PrinterProfileOverride(
                geometry = effective.geometry.takeIf { mask and 1 != 0 },
                nativeText = effective.nativeText.takeIf { mask and 2 != 0 },
                raster = effective.raster.takeIf { mask and 4 != 0 },
                nativeQr = effective.nativeQr.takeIf { mask and 8 != 0 },
                nativeBarcode = effective.nativeBarcode.takeIf { mask and 16 != 0 },
                cutter = effective.cutter.takeIf { mask and 32 != 0 },
                printerBuffer = if (mask and 64 != 0) PrinterBufferOverride.Replace(effective.printerBuffer) else PrinterBufferOverride.Inherit,
                statusQuery = effective.statusQuery.takeIf { mask and 128 != 0 },
                protocol = effective.protocol.takeIf { mask and 256 != 0 },
                quirks = effective.quirks.takeIf { mask and 512 != 0 })
            val instance = PrinterInstance(text(key + "id", 128), text(key + "name", 256),
                PrinterTransportKind.BLUETOOTH_CLASSIC, text(key + "locator", 1024),
                text(key + "profile", 128), preferences.getInt(key + "revision", 0),
                PrinterPrintDefaults(CutIntent.valueOf(text(key + "cut", 32)),
                    CutMode.valueOf(text(key + "mode", 32)), preferences.getInt(key + "feed", -1)), overrides)
            ConfiguredPrinter(instance, base).also { require(it.effectiveProfile() == effective) }
        }
        require(printers.distinctBy { it.instance.id }.size == printers.size)
        require(printers.distinctBy { it.instance.transportLocator }.size == printers.size)
        val selected = preferences.getString("selected", null)
        require(selected == null || printers.any { it.instance.id == selected })
        val imported = preferences.getString("imported", null)?.let { decode(text("imported", 1024 * 1024)) }
        PrinterConfiguration(printers, selected, imported)
    } catch (_: RuntimeException) { null }

    @Synchronized fun saveConfiguration(printers: List<ConfiguredPrinter>, selectedId: String?): Boolean {
        val version = try { preferences.getInt("version", 0) } catch (_: RuntimeException) { return false }
        // Only exact migration may replace an existing legacy association.
        if (version == 1) return false
        return writeConfiguration(printers, selectedId, loadConfiguration()?.importedProfile)
    }

    @Synchronized fun saveImportedProfile(profile: PrinterProfile?, accept: () -> Boolean = { true }): Boolean {
        if (!accept()) return false
        val version = try { preferences.getInt("version", 0) } catch (_: RuntimeException) { return false }
        val current = loadConfiguration() ?: if (version == 0) PrinterConfiguration(emptyList(), null) else return false
        return writeConfiguration(current.printers, current.selectedId, profile)
    }

    @Synchronized fun deleteImportedProfile(accept: () -> Boolean = { true }): PrinterConfiguration? {
        if (!accept()) return null
        val current = loadConfiguration() ?: return null
        val imported = current.importedProfile ?: return current
        val kept = current.printers.filterNot { it.instance.profileId == imported.profileId && it.instance.profileRevision == imported.profileRevision }
        val selected = current.selectedId.takeIf { id -> kept.any { it.instance.id == id } }
        return if (writeConfiguration(kept, selected, null)) PrinterConfiguration(kept, selected) else null
    }

    @Synchronized fun hasStoredState(): Boolean = preferences.all.isNotEmpty()

    private fun writeConfiguration(printers: List<ConfiguredPrinter>, selectedId: String?, imported: PrinterProfile?): Boolean = try {
        val version = preferences.getInt("version", 0)
        require(version in 0..2 && (version != 2 || loadConfiguration() != null) && (version != 1 || load() != null))
        require(printers.size <= 32 && printers.distinctBy { it.instance.id }.size == printers.size)
        require(printers.distinctBy { it.instance.transportLocator }.size == printers.size)
        require(selectedId == null || printers.any { it.instance.id == selectedId })
        val values = mutableMapOf<String, Any>("version" to 2, "count" to printers.size)
        if (selectedId != null) values["selected"] = selectedId
        var bytes = 0
        fun put(key: String, value: Any) {
            if (value is String) { bytes += value.encodeToByteArray().size; require(bytes <= 4 * 1024 * 1024) }
            values[key] = value
        }
        if (imported != null) put("imported", encode(imported))
        printers.forEachIndexed { index, configured ->
            val instance = configured.instance
            require(instance.transportKind == PrinterTransportKind.BLUETOOTH_CLASSIC)
            val effective = configured.effectiveProfile() ?: error("Invalid effective profile")
            val key = "p$index."
            val o = instance.overrides
            val mask = if (o == null) 0 else listOf(o.geometry != null, o.nativeText != null, o.raster != null,
                o.nativeQr != null, o.nativeBarcode != null, o.cutter != null,
                o.printerBuffer is PrinterBufferOverride.Replace, o.statusQuery != null, o.protocol != null,
                o.quirks != null).foldIndexed(1024) { bit, acc, set -> if (set) acc or (1 shl bit) else acc }
            put(key + "base", encode(configured.baseProfile))
            put(key + "mask", mask)
            if (mask != 0) put(key + "effective", encode(effective))
            put(key + "id", instance.id); put(key + "name", instance.displayName)
            put(key + "locator", instance.transportLocator); put(key + "profile", instance.profileId)
            put(key + "revision", instance.profileRevision); put(key + "cut", instance.defaults.cutIntent.name)
            put(key + "mode", instance.defaults.cutMode.name); put(key + "feed", instance.defaults.finalFeedDots)
        }
        replace(values)
    } catch (_: RuntimeException) { false }

    @Synchronized fun migrate(profile: PrinterProfile): PrinterConfiguration? {
        loadConfiguration()?.let { return it }
        val version = try { preferences.getInt("version", 0) } catch (_: RuntimeException) { return null }
        if (version != 0 && version != 1) return null
        val legacy = load()
        if (version == 1 && legacy == null) return null
        val printers = if (legacy == null) emptyList() else listOf(ConfiguredPrinter(legacy, profile))
        if (printers.any { it.effectiveProfile() == null } || !writeConfiguration(printers, legacy?.id, profile)) return null
        return PrinterConfiguration(printers, legacy?.id, profile)
    }

    private fun decode(value: String): PrinterProfile = when (val result = PrinterProfileFiles.decode(value.encodeToByteArray())) {
        is ProfileValidationResult.Success -> result.value.profile
        is ProfileValidationResult.Failure -> error("Invalid profile snapshot")
    }
    private fun encode(value: PrinterProfile): String = when (val result = PrinterProfileFiles.encode(value)) {
        is ProfileValidationResult.Success -> result.value.also { require(it.size <= 1024 * 1024) }.decodeToString()
        is ProfileValidationResult.Failure -> error("Invalid profile snapshot")
    }
    private fun replace(values: Map<String, Any?>): Boolean {
        val previous = preferences.all.toMap()
        fun editor(data: Map<String, Any?>): SharedPreferences.Editor = preferences.edit().clear().also { edit ->
            data.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                else -> Unit
            } }
        }
        if (editor(values).commit()) return true
        // Android publishes memory before disk success; restore the last confirmed snapshot.
        editor(previous).commit()
        return false
    }
}
