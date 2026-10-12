/*
 * Adapted from Project Nayuki QR Code generator v1.8.0 (Python).
 * https://github.com/nayuki/QR-Code-generator/tree/v1.8.0
 *
 * QR Code generator library (Python)
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 * implied, including but not limited to the warranties of merchantability,
 * fitness for a particular purpose and noninfringement. In no event shall the
 * authors or copyright holders be liable for any claim, damages or other
 * liability, whether in an action of contract, tort or otherwise, arising from,
 * out of or in connection with the Software or the use or other dealings in the
 * Software.
 *
 */

package com.circuitnext.rastrio.core.raster

import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException

/** Opaque byte-mode Model 2 encoding, exact correction, smallest version, lowest-penalty mask. */
object QrMatrixEncoder {
    fun encode(
        payload: ByteArray,
        errorCorrection: QrCorrection,
        isCancelled: () -> Boolean = { false },
        utf8Eci: Boolean = false,
    ): QrMatrix {
        require(payload.isNotEmpty() && payload.size <= 2953) { "QR payload is empty or exceeds Model 2 byte capacity" }
        fun cancelled() { if (isCancelled()) throw CancellationException("QR generation cancelled") }
        cancelled()
        val version = (1..40).firstOrNull {
            val countBits = if (it <= 9) 8 else 16
            payload.size < (1 shl countBits) &&
                4 + countBits + payload.size * 8 + (if (utf8Eci) 12 else 0) <= dataCodewords(it, errorCorrection) * 8
        } ?: throw IllegalArgumentException("QR payload exceeds selected error correction capacity")
        val capacity = dataCodewords(version, errorCorrection)
        val words = IntArray(capacity)
        var bitCount = 0
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                words[bitCount / 8] = words[bitCount / 8] or (((value ushr i) and 1) shl (7 - bitCount % 8))
                bitCount++
            }
        }
        if (utf8Eci) { append(7, 4); append(26, 8) }
        append(4, 4)
        append(payload.size, if (version <= 9) 8 else 16)
        payload.forEach { append(it.toInt() and 255, 8) }
        append(0, minOf(4, capacity * 8 - bitCount))
        append(0, (8 - bitCount % 8) % 8)
        var pad = 0xEC
        while (bitCount < capacity * 8) { append(pad, 8); pad = pad xor 0xFD }
        return MatrixBuilder(version, errorCorrection, words, ::cancelled).build()
    }
}

private class MatrixBuilder(
    private val version: Int,
    private val correction: QrCorrection,
    private val words: IntArray,
    private val cancelled: () -> Unit,
) {
    private val size = version * 4 + 17
    private val modules = BooleanArray(size * size)
    private val function = BooleanArray(size * size)
    private fun at(x: Int, y: Int) = modules[y * size + x]
    private fun set(x: Int, y: Int, dark: Boolean) {
        modules[y * size + x] = dark
        function[y * size + x] = true
    }
    fun build(): QrMatrix {
        for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
        if (version > 1) {
            val count = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val positions = IntArray(count) { if (it == 0) 6 else size - 7 - (count - 1 - it) * step }
            for (i in positions.indices) for (j in positions.indices) {
                if ((i == 0 && (j == 0 || j == count - 1)) || (i == count - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2)
                    set(positions[i] + dx, positions[j] + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
        }
        format(0)
        if (version >= 7) {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val a = size - 11 + i % 3; val b = i / 3
                set(a, b, bit(bits, i)); set(b, a, bit(bits, i))
            }
        }
        val data = interleave()
        var i = 0
        for (initialRight in size - 1 downTo 1 step 2) {
            val right = if (initialRight <= 6) initialRight - 1 else initialRight
            for (vertical in 0 until size) for (j in 0..1) {
                val x = right - j
                val y = if (((right + 1) and 2) == 0) size - 1 - vertical else vertical
                if (!function[y * size + x] && i < data.size * 8) {
                    modules[y * size + x] = bit(data[i / 8], 7 - i % 8); i++
                }
            }
        }
        check(i == data.size * 8)
        var bestMask = 0; var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            cancelled()
            applyMask(mask); format(mask)
            val score = penalty()
            if (score < bestPenalty) { bestPenalty = score; bestMask = mask }
            applyMask(mask)
        }
        applyMask(bestMask); format(bestMask)
        cancelled()
        return QrMatrix(version, bestMask, correction, modules)
    }
    private fun finder(x: Int, y: Int) {
        for (dy in -4..4) for (dx in -4..4) {
            if (x + dx in 0 until size && y + dy in 0 until size) {
                val distance = maxOf(abs(dx), abs(dy))
                set(x + dx, y + dy, distance != 2 && distance != 4)
            }
        }
    }
    private fun format(mask: Int) {
        val formatBits = intArrayOf(1, 0, 3, 2)[correction.ordinal]
        val data = (formatBits shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412
        for (i in 0 until 6) set(8, i, bit(bits, i))
        set(8, 7, bit(bits, 6)); set(8, 8, bit(bits, 7)); set(7, 8, bit(bits, 8))
        for (i in 9 until 15) set(14 - i, 8, bit(bits, i))
        for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
        set(8, size - 8, true)
    }
    private fun interleave(): IntArray {
        val count = BLOCKS[correction.ordinal][version]
        val eccLength = ECC[correction.ordinal][version]
        val raw = rawModules(version) / 8
        val shortCount = count - raw % count
        val shortLength = raw / count
        val divisor = IntArray(eccLength).also { it[eccLength - 1] = 1 }
        var root = 1
        repeat(eccLength) {
            for (j in divisor.indices) {
                divisor[j] = multiply(divisor[j], root)
                if (j + 1 < divisor.size) divisor[j] = divisor[j] xor divisor[j + 1]
            }
            root = multiply(root, 2)
        }
        var offset = 0
        val blocks = Array(count) { index ->
            cancelled()
            val dataLength = shortLength - eccLength + if (index < shortCount) 0 else 1
            val data = words.copyOfRange(offset, offset + dataLength)
            offset += dataLength
            val remainder = IntArray(eccLength)
            for (value in data) {
                val factor = value xor remainder[0]
                for (j in 0 until eccLength - 1) remainder[j] = remainder[j + 1]
                remainder[eccLength - 1] = 0
                for (j in divisor.indices) remainder[j] = remainder[j] xor multiply(divisor[j], factor)
            }
            IntArray(shortLength + 1).also { block ->
                data.copyInto(block)
                remainder.copyInto(block, shortLength + 1 - eccLength)
            }
        }
        check(offset == words.size)
        val result = IntArray(raw)
        var index = 0
        for (i in blocks[0].indices) for (j in blocks.indices)
            if (i != shortLength - eccLength || j >= shortCount) result[index++] = blocks[j][i]
        check(index == result.size)
        return result
    }
    private fun applyMask(mask: Int) {
        for (y in 0 until size) for (x in 0 until size) {
            val invert = when (mask) {
                0 -> (x + y) % 2
                1 -> y % 2
                2 -> x % 3
                3 -> (x + y) % 3
                4 -> (x / 3 + y / 2) % 2
                5 -> x * y % 2 + x * y % 3
                6 -> (x * y % 2 + x * y % 3) % 2
                else -> ((x + y) % 2 + x * y % 3) % 2
            } == 0
            val index = y * size + x
            if (invert && !function[index]) modules[index] = !modules[index]
        }
    }
    private fun penalty(): Int {
        var result = 0
        for (axis in 0..1) for (line in 0 until size) {
            var color = false; var run = 0
            val history = IntArray(7)
            fun add(length: Int) {
                val value = length + if (history[0] == 0) size else 0
                for (i in 6 downTo 1) history[i] = history[i - 1]
                history[0] = value
            }
            fun patterns(): Int {
                val n = history[1]
                val core = n > 0 && history[2] == n && history[4] == n && history[5] == n && history[3] == n * 3
                return (if (core && history[0] >= n * 4 && history[6] >= n) 1 else 0) +
                    (if (core && history[6] >= n * 4 && history[0] >= n) 1 else 0)
            }
            for (position in 0 until size) {
                val dark = if (axis == 0) at(position, line) else at(line, position)
                if (dark == color) {
                    run++
                    if (run == 5) result += 3 else if (run > 5) result++
                } else {
                    add(run)
                    if (!color) result += patterns() * 40
                    color = dark; run = 1
                }
            }
            if (color) { add(run); run = 0 }
            add(run + size)
            result += patterns() * 40
        }
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val color = at(x, y)
            if (at(x + 1, y) == color && at(x, y + 1) == color && at(x + 1, y + 1) == color) result += 3
        }
        val dark = modules.count { it }
        val total = size * size
        result += ((abs(dark * 20 - total * 10) + total - 1) / total - 1) * 10
        return result
    }
}

private fun bit(value: Int, index: Int) = ((value ushr index) and 1) != 0
private fun multiply(x: Int, y: Int): Int {
    var result = 0
    for (i in 7 downTo 0) result = ((result shl 1) xor ((result ushr 7) * 0x11D)) xor (((y ushr i) and 1) * x)
    return result
}
private fun rawModules(version: Int): Int {
    var result = (16 * version + 128) * version + 64
    if (version >= 2) {
        val count = version / 7 + 2
        result -= (25 * count - 10) * count - 55
        if (version >= 7) result -= 36
    }
    return result
}
private fun dataCodewords(version: Int, correction: QrCorrection) =
    rawModules(version) / 8 - ECC[correction.ordinal][version] * BLOCKS[correction.ordinal][version]

private val ECC = arrayOf(
    intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
    intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
)

private val BLOCKS = arrayOf(
    intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
    intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
    intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
    intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
)
