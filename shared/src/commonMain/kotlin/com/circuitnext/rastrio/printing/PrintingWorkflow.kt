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

import com.circuitnext.rastrio.core.printer.PreparedPrint
import com.circuitnext.rastrio.core.profile.PrinterProfile

/** Stable device locator retains ambiguous-output evidence across refreshes and selection changes. */
class PrinterSessionStore(private val transportFactory: () -> PrinterTransport) {
    private val sessions = mutableMapOf<Pair<PrinterTransportKind, String>, PrintSession>()

    fun session(instance: PrinterInstance): PrintSession? {
        val key = instance.transportKind to instance.transportLocator
        return sessions[key] ?: if (sessions.size < 100) {
            PrintSession(transportFactory()).also { sessions[key] = it }
        } else null
    }

    fun cancelAll() { sessions.values.forEach { it.cancel() } }
}

fun isSyntheticProfile(profile: PrinterProfile): Boolean = profile.profileId.startsWith("org.rastrio.test.") ||
    profile.profileId.startsWith("synthetic") || profile.profileId.startsWith("rastrio.synthetic")

/** Both preview and transmission must use the current associated profile, never a stale prepared target. */
fun canPrintPrepared(instance: PrinterInstance?, profile: PrinterProfile?, prepared: PreparedPrint?,
    preparedProfile: PrinterProfile?): Boolean =
    instance != null && profile != null && prepared != null && profile == preparedProfile && !isSyntheticProfile(profile) &&
        instance.profileId == profile.profileId && instance.profileRevision == profile.profileRevision &&
        prepared.target.profileId == profile.profileId && prepared.target.profileRevision == profile.profileRevision &&
        prepared.target.printableWidthDots == profile.geometry.printableWidthDots &&
        prepared.target.horizontalDpi == profile.geometry.horizontalDpi &&
        prepared.target.verticalDpi == profile.geometry.verticalDpi

fun PrinterProfile.writeGuidance(): PrinterWriteGuidance = PrinterWriteGuidance(
    printerBuffer?.recommendedMaxBurstBytes, printerBuffer?.recommendedPauseAfterBurstMs?.toLong(),
)
