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

/** Trusted parsing/output policy, never serialized profile data. Recursion is capped at 64. */
data class TcfgResourcePolicy(
    val maxInputBytes: Int = 1024 * 1024,
    val maxJsonDepth: Int = 64,
    val maxStringBytes: Int = 64 * 1024,
    val maxJsonTokens: Int = 100_000,
    val maxObjectProperties: Int = 64,
    val maxNumberChars: Int = 64,
) {
    init {
        require(maxInputBytes > 0 && maxJsonDepth in 1..64 && maxStringBytes > 0)
        require(maxJsonTokens > 0 && maxObjectProperties > 0 && maxNumberChars > 0)
    }
}

internal class TcfgFailure(val diagnostic: ProfileDiagnostic) : Exception(diagnostic.message)
internal fun tcfgFail(code: String, path: String, message: String): Nothing =
    throw TcfgFailure(ProfileDiagnostic(code, path, message))

internal sealed interface TcfgJson {
    data class Obj(val fields: Map<String, TcfgJson>) : TcfgJson
    data class Arr(val values: List<TcfgJson>) : TcfgJson
    data class Str(val value: String) : TcfgJson
    data class Num(val raw: String) : TcfgJson
    data class Bool(val value: Boolean) : TcfgJson
    data object Null : TcfgJson
}

/** Profile-local RFC 8259 parser. Keys are checked before parsing their values or inserting them. */
internal class TcfgJsonParser(
    private val source: String,
    private val policy: TcfgResourcePolicy,
    private val maxCollectionEntries: Int,
    private val maxStringBytes: Int,
) {
    private var index = 0
    private var tokens = 0L
    fun parse(): TcfgJson {
        whitespace()
        val result = value(0)
        whitespace()
        if (index != source.length) syntax()
        return result
    }
    private fun value(depth: Int): TcfgJson {
        tick()
        if (index >= source.length) syntax()
        return when (source[index]) {
            '{' -> { nesting(depth); obj(depth + 1) }
            '[' -> { nesting(depth); array(depth + 1) }
            '"' -> TcfgJson.Str(string())
            't' -> { literal("true"); TcfgJson.Bool(true) }
            'f' -> { literal("false"); TcfgJson.Bool(false) }
            'n' -> { literal("null"); TcfgJson.Null }
            '-', in '0'..'9' -> TcfgJson.Num(number())
            else -> syntax()
        }
    }
    private fun nesting(depth: Int) { if (depth >= policy.maxJsonDepth) limit() }
    private fun obj(depth: Int): TcfgJson.Obj {
        index++
        whitespace()
        val fields = linkedMapOf<String, TcfgJson>()
        if (consume('}')) return TcfgJson.Obj(fields)
        while (true) {
            if (fields.size >= policy.maxObjectProperties) limit()
            tick() // Property names also consume the node/token budget.
            if (index >= source.length || source[index] != '"') syntax()
            val key = string()
            if (fields.containsKey(key)) tcfgFail("PRF132", "$", "Duplicate JSON property")
            whitespace()
            requireChar(':')
            whitespace()
            fields[key] = value(depth)
            whitespace()
            if (consume('}')) return TcfgJson.Obj(fields)
            requireChar(',')
            whitespace()
        }
    }
    private fun array(depth: Int): TcfgJson.Arr {
        index++
        whitespace()
        val values = mutableListOf<TcfgJson>()
        if (consume(']')) return TcfgJson.Arr(values)
        while (true) {
            if (values.size >= maxCollectionEntries) limit()
            values += value(depth)
            whitespace()
            if (consume(']')) return TcfgJson.Arr(values)
            requireChar(',')
            whitespace()
        }
    }
    private fun string(): String {
        requireChar('"')
        val result = StringBuilder()
        var bytes = 0L
        while (index < source.length) {
            if (consume('"')) return result.toString()
            val c = stringChar()
            val cost = when {
                c.isHighSurrogate() -> {
                    val low = stringChar()
                    if (!low.isLowSurrogate()) syntax()
                    if (bytes + 4 > maxStringBytes) limit()
                    result.append(c).append(low)
                    4
                }
                c.isLowSurrogate() -> syntax()
                c.code < 128 -> 1
                c.code < 2048 -> 2
                else -> 3
            }
            if (bytes + cost > maxStringBytes) limit()
            if (!c.isHighSurrogate()) result.append(c)
            bytes += cost
        }
        syntax()
    }
    private fun stringChar(): Char {
        if (index >= source.length) syntax()
        val c = source[index++]
        if (c == '"' || c.code < 32) syntax()
        if (c != '\\') return c
        if (index >= source.length) syntax()
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                var value = 0
                repeat(4) {
                    if (index >= source.length) syntax()
                    val hex = source[index++]
                    val digit = when (hex) {
                        in '0'..'9' -> hex - '0'
                        in 'a'..'f' -> hex - 'a' + 10
                        in 'A'..'F' -> hex - 'A' + 10
                        else -> syntax()
                    }
                    value = (value shl 4) or digit
                }
                value.toChar()
            }
            else -> syntax()
        }
    }
    private fun number(): String {
        val start = index
        fun advance() { index++; if (index - start > policy.maxNumberChars) limit() }
        fun take(c: Char): Boolean {
            if (index >= source.length || source[index] != c) return false
            advance()
            return true
        }
        fun digits() {
            val before = index
            while (index < source.length && source[index] in '0'..'9') advance()
            if (before == index) syntax()
        }
        take('-')
        if (take('0')) {
            if (index < source.length && source[index] in '0'..'9') syntax()
        } else digits()
        if (take('.')) digits()
        if (index < source.length && source[index] in "eE") {
            advance()
            if (index < source.length && source[index] in "+-") advance()
            digits()
        }
        return source.substring(start, index)
    }
    private fun literal(text: String) {
        if (!source.startsWith(text, index)) syntax()
        index += text.length
    }
    private fun whitespace() { while (index < source.length && source[index] in " \t\r\n") index++ }
    private fun consume(c: Char): Boolean = if (index < source.length && source[index] == c) { index++; true } else false
    private fun requireChar(c: Char) { if (!consume(c)) syntax() }
    private fun tick() { if (++tokens > policy.maxJsonTokens) limit() }
    private fun syntax(): Nothing = tcfgFail("PRF131", "$", "Invalid standard JSON")
    private fun limit(): Nothing = tcfgFail("PRF138", "$", "Trusted JSON resource limit exceeded")
}

/** Bounded compact writer. All strings come from immutable validated profile values. */
internal class TcfgJsonWriter(private val maxChars: Int) {
    private val out = StringBuilder()
    private fun append(value: String) {
        if (value.length.toLong() + out.length > maxChars) tcfgFail("PRF138", "$", "Trusted JSON output limit exceeded")
        out.append(value)
    }
    fun obj(block: TcfgJsonWriter.() -> Unit) { append("{"); block(); trimComma(); append("}") }
    fun <T> arr(values: Iterable<T>, write: TcfgJsonWriter.(T) -> Unit) {
        append("[")
        for (value in values) { write(value); append(",") }
        trimComma(); append("]")
    }
    fun field(name: String, value: String) { field(name) { string(value) } }
    fun field(name: String, value: Int) { field(name) { append(value.toString()) } }
    fun field(name: String, value: Boolean) { field(name) { append(value.toString()) } }
    fun field(name: String, block: TcfgJsonWriter.() -> Unit) { string(name); append(":"); block(); append(",") }
    fun integer(value: Int) { append(value.toString()) }
    fun string(value: String) {
        append("\"")
        for (c in value) append(when (c) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\b' -> "\\b"
            '\u000c' -> "\\f"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (c.code < 32) "\\u" + c.code.toString(16).padStart(4, '0') else c.toString()
        })
        append("\"")
    }
    private fun trimComma() { if (out.lastOrNull() == ',') out.setLength(out.length - 1) }
    override fun toString(): String = out.toString()
}
