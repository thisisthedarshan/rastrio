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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.*

/** Enumerates semantic identities only; URLs and embedded names never cause resource acquisition. */
fun markdownImageReferences(document: ThermalDocument): List<AssetReference> {
    val references = linkedSetOf<AssetReference>()
    val stack = mutableListOf(document.blocks.iterator())
    while (stack.isNotEmpty()) {
        if (!stack.last().hasNext()) { stack.removeAt(stack.lastIndex); continue }
        when (val block = stack.last().next()) {
            is Image -> references.add(block.asset)
            is Quote -> stack.add(block.blocks.iterator())
            is UnorderedList -> stack.add(block.items.flatMap { it.blocks }.iterator())
            is OrderedList -> stack.add(block.items.flatMap { it.blocks }.iterator())
            is Checklist -> stack.add(block.items.flatMap { it.blocks }.iterator())
            else -> Unit
        }
    }
    return references.toList()
}
