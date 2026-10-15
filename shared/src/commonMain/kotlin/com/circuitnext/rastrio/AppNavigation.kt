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

package com.circuitnext.rastrio

internal enum class AuthoringRoute { HOME, TEMPLATE_GALLERY, TEMPLATE_SETTINGS, MARKDOWN, PREVIEW, PRINTER_SETUP, PHYSICAL_PREVIEW, IMAGE, IMAGE_PREVIEW, TEMPLATES, TEMPLATE_PREVIEW, TEMPLATE_CONFIRMATION, TEMPLATE_LOGICAL_PREVIEW, PRINTER_MANAGEMENT, PRINTER_LAB, PROFILE_EDITOR, ADD_PRINTER }

/** A bounded shared page stack; platform Back uses the same destinations as visible navigation. */
internal data class AppNavigation(val pages: List<AuthoringRoute> = listOf(AuthoringRoute.HOME)) {
    val route get() = pages.last()
    val canGoBack get() = pages.size > 1
    fun open(destination: AuthoringRoute): AppNavigation {
        val previous = pages.indexOf(destination)
        return if (previous >= 0) copy(pages = pages.take(previous + 1))
        else copy(pages = (pages + destination).takeLast(32).let {
            if (it.first() == AuthoringRoute.HOME) it else listOf(AuthoringRoute.HOME) + it.drop(1)
        })
    }
    fun back() = if (canGoBack) copy(pages = pages.dropLast(1)) else this
    companion object {
        fun restore(values: List<String>): AppNavigation? = runCatching {
            require(values.size in 1..32)
            val routes = values.map(AuthoringRoute::valueOf)
            require(routes.first() == AuthoringRoute.HOME && routes.distinct().size == routes.size)
            AppNavigation(routes)
        }.getOrNull()
    }
}
