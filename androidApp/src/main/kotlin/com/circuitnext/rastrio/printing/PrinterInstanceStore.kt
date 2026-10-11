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

/** Fixed keys in app-private storage; no profile export or printable content. */
internal class PrinterInstanceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("printer-instance", Context.MODE_PRIVATE))
    fun load(): PrinterInstance? = try {
        val id = if (preferences.getInt("version", 0) == 1) preferences.getString("id", null) else null
        if (id == null) null else PrinterInstance(id,
            preferences.getString("name", null) ?: "",
            PrinterTransportKind.BLUETOOTH_CLASSIC,
            preferences.getString("locator", null) ?: "",
            preferences.getString("profile", null) ?: "",
            preferences.getInt("revision", 0))
    } catch (_: IllegalArgumentException) { null } catch (_: ClassCastException) { null }

    fun save(instance: PrinterInstance): Boolean {
        require(instance.transportKind == PrinterTransportKind.BLUETOOTH_CLASSIC)
        return preferences.edit().putInt("version", 1).putString("id", instance.id).putString("name", instance.displayName)
            .putString("locator", instance.transportLocator).putString("profile", instance.profileId)
            .putInt("revision", instance.profileRevision).commit()
    }
}
