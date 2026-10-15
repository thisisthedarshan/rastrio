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

/** Local incomplete drafts use fixed private keys, never portable .tcfg exports. */
internal class AndroidProfileDraftStorage(private val preferences: SharedPreferences) : ProfileDraftStorage {
    constructor(context: Context) : this(context.getSharedPreferences("printer-profile-drafts", Context.MODE_PRIVATE))

    override fun loadDrafts(): List<ProfileDraft> {
        return try {
        val count = preferences.getInt("count", 0)
        if (count !in 0..32 || preferences.getInt("version", 1) != 1) emptyList() else {
            val drafts = (0 until count).map { index ->
                val source = preferences.getString("draft$index", null) ?: return emptyList()
                if (source.length > 65536) return emptyList()
                ProfileDraft.decodeLocal(source) ?: return emptyList()
            }
            if (drafts.map { it.draftId }.distinct().size != count) emptyList() else drafts
        }
    } catch (_: IllegalArgumentException) { emptyList() } catch (_: ClassCastException) { emptyList() }
    }

    override fun saveDrafts(drafts: List<ProfileDraft>): Boolean = try {
        require(drafts.size <= 32 && drafts.map { it.draftId }.distinct().size == drafts.size)
        val encoded = drafts.map { it.encodeLocal().also { source -> require(source.length <= 65536) } }
        val previous = loadDrafts().map { it.encodeLocal() }
        if (write(encoded)) true else {
            // A failed commit still changes the preference cache; retain the confirmed worksheet list.
            write(previous)
            false
        }
    } catch (_: IllegalArgumentException) { false }

    private fun write(encoded: List<String>): Boolean {
        val editor = preferences.edit().clear().putInt("version", 1).putInt("count", encoded.size)
        encoded.forEachIndexed { index, source -> editor.putString("draft$index", source) }
        return editor.commit()
    }
}
