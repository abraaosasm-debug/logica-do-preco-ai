package br.com.logicadopreco.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Separa corpos de candles por cor e sequências verticais.
 *
 * Na v0.3 o agrupamento tolerava falhas horizontais de até 2px entre todas
 * as cores, fundindo muitos candles em um único bloco. Aqui as sementes são
 * colunas com COR DOMINANTE e corpo vertical contínuo; nunca se mesclam
 * cores opostas, e candles adjacentes exigem sua própria região corporal.
 *
 * Retorna geometria estimada (sem OHLC certificado ou temporalidade da Quotex).
 */
object CandleSegmenter {
    fun detect(classes: ByteArray, width: Int, height: Int): List<VisionCandle> {
        if (width < 80 || height < 80 || classes.size != width * height) return emptyList()
        val greenLongest = IntArray(width)
        val redLongest = IntArray(width)
        val seed = IntArray(width)

        // Uma varredura por coluna para encontrar trechos verticais contínuos.
        for (x in 0 until width) {
            var currentSign = 0
            var length = 0
            for (y in 0 until height) {
                val c = classes[y * width + x].toInt()
                if (c != 0 && c == currentSign) {
                    length++
                } else {
                    if (currentSign == 1) greenLongest[x] = max(greenLongest[x], length)
                    if (currentSign == -1) redLongest[x] = max(redLongest[x], length)
                    currentSign = c
                    length = if (c == 0) 0 else 1
                }
            }
            if (currentSign == 1) greenLongest[x] = max(greenLongest[x], length)
            if (currentSign == -1) redLongest[x] = max(redLongest[x], length)

            val g = greenLongest[x]
            val r = redLongest[x]
            seed[x] = when {
                g >= 3 && g >= r * 1.4 -> 1
                r >= 3 && r >= g * 1.4 -> -1
                else -> 0
            }
        }

        val found = ArrayList<VisionCandle>()
        var x = 0
        while (x < width) {
            val sign = seed[x]
            if (sign == 0) { x++; continue }
            val left = x
            while (x + 1 < width && seed[x + 1] == sign) x++
            val right = x
            x++

            val bodyWidth = right - left + 1
            if (bodyWidth < 2 || bodyWidth > width * 0.07) continue
            var bestY0 = -1
            var bestY1 = -1
            var bestX = -1
            var bestLen = 0

            // Corpo = maior faixa vertical contínua e densa encontrada em uma
            // coluna do bloco. Evita misturar máximas/mínimas de velas vizinhas.
            for (col in left..right) {
                var startedAt = -1
                for (row in 0..height) {
                    val colored = row < height && classes[row * width + col].toInt() == sign
                    if (colored && startedAt == -1) startedAt = row
                    if (!colored && startedAt != -1) {
                        val len = row - startedAt
                        if (len > bestLen) {
                            bestLen = len
                            bestY0 = startedAt
                            bestY1 = row - 1
                            bestX = col
                        }
                        startedAt = -1
                    }
                }
            }

            if (bestLen < 3 || bestX < 0) continue

            var hi = height
            var lo = -1
            // Limita a busca por pavios às colunas centrais do corpo;
            // não inclui todo o agrupamento, que pode tocar outra vela.
            val inspectLeft = max(left, bestX - 1)
            val inspectRight = min(right, bestX + 1)
            for (col in inspectLeft..inspectRight) {
                for (row in 0 until height) {
                    if (classes[row * width + col].toInt() == sign) {
                        hi = min(hi, row)
                        lo = max(lo, row)
                    }
                }
            }
            if (hi == height || lo == -1 || lo - hi < 3) continue

            val expected = max(2, bodyWidth)
            val shapeScore = (bestLen.toDouble() / 10.0).coerceIn(0.0, 1.0)
            val widthScore = (expected.toDouble() / 5.0).coerceIn(0.0, 1.0)
            val score = 0.4 + 0.4 * shapeScore + 0.2 * widthScore

            found.add(
                VisionCandle(
                    x = (left + right) / 2,
                    up = sign == 1,
                    openY = if (sign == 1) bestY1 else bestY0,
                    closeY = if (sign == 1) bestY0 else bestY1,
                    highY = min(hi, bestY0),
                    lowY = max(lo, bestY1),
                    bodyWidth = bodyWidth,
                    quality = score.coerceIn(0.0, 1.0)
                )
            )
        }
        return found.sortedBy { it.x }
    }
}
