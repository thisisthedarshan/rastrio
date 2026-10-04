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

package com.circuitnext.rastrio.core.document

internal sealed interface JsonValue {
    data class Obj(val fields: LinkedHashMap<String, JsonValue>) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/** Strict RFC 8259 tokenizer. Duplicate keys are rejected while source evidence still exists. */
internal class StrictJson(private val source: String, private val policy: TdResourcePolicy) {
    private var index = 0
    private var tokens = 0

    fun parse(): JsonValue {
        whitespace()
        val value = value(0)
        whitespace()
        if (index != source.length) invalid("Trailing data after JSON value")
        return value
    }

    private fun value(depth: Int): JsonValue {
        if (depth > policy.maxJsonDepth) limit("JSON nesting exceeds policy")
        tick()
        if (index >= source.length) invalid("Unexpected end of JSON")
        return when (source[index]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> JsonValue.Str(string())
            't' -> { literal("true"); JsonValue.Bool(true) }
            'f' -> { literal("false"); JsonValue.Bool(false) }
            'n' -> { literal("null"); JsonValue.Null }
            '-', in '0'..'9' -> JsonValue.Num(number())
            else -> invalid("Invalid JSON token")
        }
    }

    private fun objectValue(depth: Int): JsonValue.Obj {
        index++
        whitespace()
        val fields = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonValue.Obj(fields)
        while (true) {
            if (index >= source.length || source[index] != '"') invalid("Object key must be a string")
            val key = string()
            if (fields.containsKey(key)) invalid("Duplicate JSON object key")
            whitespace()
            requireChar(':')
            whitespace()
            fields[key] = value(depth)
            whitespace()
            if (consume('}')) break
            requireChar(',')
            whitespace()
        }
        return JsonValue.Obj(fields)
    }

    private fun arrayValue(depth: Int): JsonValue.Arr {
        index++
        whitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonValue.Arr(values)
        while (true) {
            values += value(depth)
            whitespace()
            if (consume(']')) break
            requireChar(',')
            whitespace()
        }
        return JsonValue.Arr(values)
    }

    private fun string(): String {
        requireChar('"')
        val result = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            when {
                c == '"' -> {
                    val decoded = result.toString()
                    if (!decoded.hasValidUnicode()) invalid("Unpaired surrogate in JSON string")
                    if (decoded.encodeToByteArray().size > policy.maxStringBytes) limit("JSON string exceeds policy")
                    return decoded
                }
                c == '\\' -> {
                    if (index >= source.length) invalid("Incomplete JSON escape")
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(unicodeEscape())
                        else -> invalid("Invalid JSON escape")
                    }
                }
                c.code < 0x20 -> invalid("Unescaped control character in JSON string")
                else -> result.append(c)
            }
            if (result.length > policy.maxStringBytes) limit("JSON string exceeds policy")
        }
        invalid("Unterminated JSON string")
    }

    private fun unicodeEscape(): Char {
        if (index + 4 > source.length) invalid("Incomplete Unicode escape")
        var value = 0
        repeat(4) {
            val digit = source[index++].digitToIntOrNull(16) ?: invalid("Invalid Unicode escape")
            value = (value shl 4) or digit
        }
        return value.toChar()
    }

    private fun number(): String {
        val start = index
        consume('-')
        if (consume('0')) {
            if (index < source.length && source[index] in '0'..'9') invalid("Leading zero in JSON number")
        } else {
            digits(required = true)
        }
        if (consume('.')) digits(required = true)
        if (index < source.length && source[index] in "eE") {
            index++
            if (index < source.length && source[index] in "+-") index++
            digits(required = true)
        }
        return source.substring(start, index)
    }

    private fun digits(required: Boolean) {
        val start = index
        while (index < source.length && source[index] in '0'..'9') index++
        if (required && start == index) invalid("Invalid JSON number")
    }

    private fun literal(text: String) {
        if (!source.startsWith(text, index)) invalid("Invalid JSON literal")
        index += text.length
    }

    private fun whitespace() {
        while (index < source.length && source[index] in " \t\r\n") index++
    }

    private fun consume(c: Char): Boolean = if (index < source.length && source[index] == c) { index++; true } else false
    private fun requireChar(c: Char) { if (!consume(c)) invalid("Expected '$c'") }
    private fun tick() { tokens++; if (tokens > policy.maxJsonTokens) limit("JSON token count exceeds policy") }
    private fun invalid(message: String): Nothing = throw TdException.Invalid(message)
    private fun limit(message: String): Nothing = throw TdException.Limit(message, "TD124")
}

internal class JsonWriter(private val maxChars: Int) {
    private val out = StringBuilder()
    fun obj(block: JsonWriter.() -> Unit) { out.append('{'); block(); trimComma(); out.append('}'); guard() }
    fun arr(values: Iterable<Any?>, write: (Any?) -> Unit) { out.append('['); values.forEach { write(it); out.append(','); guard() }; trimComma(); out.append(']'); guard() }
    fun field(name: String, value: String) { string(name); out.append(':'); string(value); out.append(','); guard() }
    fun field(name: String, value: Int) { string(name); out.append(':').append(value).append(','); guard() }
    fun field(name: String, value: Long) { string(name); out.append(':').append(value).append(','); guard() }
    fun field(name: String, value: Double) { require(value.isFinite()); string(name); out.append(':').append(value.toString()).append(','); guard() }
    fun field(name: String, value: Boolean) { string(name); out.append(':').append(value).append(','); guard() }
    fun field(name: String, block: JsonWriter.() -> Unit) { string(name); out.append(':'); block(); out.append(','); guard() }
    fun string(value: String) {
        out.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000c' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) {
                    out.append("\\u")
                    out.append(c.code.toString(16).padStart(4, '0'))
                } else out.append(c)
            }
        }
        out.append('"')
        guard()
    }
    private fun trimComma() { if (out.lastOrNull() == ',') out.setLength(out.length - 1) }
    private fun guard() { if (out.length > maxChars) throw TdException.Limit("JSON output exceeds policy", "TD124") }
    override fun toString(): String = out.toString()
}

internal fun JsonValue.asObject(label: String): LinkedHashMap<String, JsonValue> = (this as? JsonValue.Obj)?.fields ?: throw TdException.Invalid("$label must be an object")
internal fun JsonValue.asArray(label: String): List<JsonValue> = (this as? JsonValue.Arr)?.values ?: throw TdException.Invalid("$label must be an array")
internal fun JsonValue.asString(label: String): String = (this as? JsonValue.Str)?.value ?: throw TdException.Invalid("$label must be a string")
internal fun JsonValue.asBoolean(label: String): Boolean = (this as? JsonValue.Bool)?.value ?: throw TdException.Invalid("$label must be a boolean")
internal fun JsonValue.asLong(label: String): Long {
    val raw = (this as? JsonValue.Num)?.raw ?: throw TdException.Invalid("$label must be an integer")
    if (raw.contains('.') || raw.contains('e', true)) throw TdException.Invalid("$label must be an integer")
    return raw.toLongOrNull() ?: throw TdException.Invalid("$label is outside supported integer range")
}
internal fun JsonValue.asDouble(label: String): Double {
    val raw = (this as? JsonValue.Num)?.raw ?: throw TdException.Invalid("$label must be a number")
    return raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw TdException.Invalid("$label must be finite")
}
internal fun objectOf(value: JsonValue, label: String, required: Set<String>, optional: Set<String> = emptySet()): Map<String, JsonValue> {
    val fields = value.asObject(label)
    val unknown = fields.keys - required - optional
    if (unknown.isNotEmpty()) throw TdException.Invalid("Unknown $label property")
    val missing = required - fields.keys
    if (missing.isNotEmpty()) throw TdException.Invalid("Missing $label property")
    return fields
}

internal fun String.hasValidUnicode(): Boolean {
    var i = 0
    while (i < length) {
        val char = this[i]
        if (char.isHighSurrogate()) {
            if (i + 1 >= length || !this[i + 1].isLowSurrogate()) return false
            i++
        } else if (char.isLowSurrogate()) return false
        i++
    }
    return true
}
