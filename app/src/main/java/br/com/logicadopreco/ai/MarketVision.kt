package br.com.logicadopreco.ai

import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Posições relativas em pixels amostrados, Y cresce para baixo. */
data class VisionCandle(
    val x: Int,
    val up: Boolean,
    val openY: Int,
    val closeY: Int,
    val highY: Int,
    val lowY: Int,
    val bodyWidth: Int,
    val quality: Double
) {
    val upperWick: Int get() = (min(openY, closeY) - highY).coerceAtLeast(0)
    val lowerWick: Int get() = (lowY - max(openY, closeY)).coerceAtLeast(0)
    val bodyHeight: Int get() = abs(closeY - openY)
}

/**
 * Reconhecimento determinístico offline de candles VERDES/VERMELHOS.
 * Não acessa a corretora nem altera o gráfico.
 * Pode falhar com outras paletas, grade, indicadores ou compressão da imagem.
 */
class MarketVision {
    data class Result(val candles: List<VisionCandle>, val sampledWidth: Int, val sampledHeight: Int,
                      val coloredPixels: Int, val reason: String)

    private fun classify(r: Int, g: Int, b: Int): Int {
        // Exige predominância cromática. Evita branco, preto e cinza.
        if (g >= 85 && g >= r * 1.25 && g >= b * 1.12 && g - r >= 20) return 1
        if (r >= 90 && r >= g * 1.28 && r >= b * 1.18 && r - g >= 30) return -1
        return 0
    }

    fun analyze(image: Image, crop: VisionCrop): Result {
        if (!crop.valid) return Result(emptyList(), 0, 0, 0, "Recorte inválido. Ajuste os limites.")
        if (image.planes.isEmpty()) return Result(emptyList(), 0, 0, 0, "Imagem sem pixels.")
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride < 3) return Result(emptyList(), 0, 0, 0, "Formato de pixels incompatível.")

        val x0 = image.width * crop.left / 100
        val y0 = image.height * crop.top / 100
        val x1 = image.width * crop.right / 100
        val y1 = image.height * crop.bottom / 100
        val width = (x1 - x0).coerceAtLeast(1)
        val height = (y1 - y0).coerceAtLeast(1)
        val w = min(600, width)
        val h = min(360, height)
        if (w < 80 || h < 80) return Result(emptyList(), w, h, 0, "Área de análise pequena.")

        val classes = ByteArray(w * h)
        val rowCount = IntArray(h)
        var colored = 0
        for (y in 0 until h) {
            val realY = y0 + y * height / h
            for (x in 0 until w) {
                val realX = x0 + x * width / w
                val offset = realY * stride + realX * pixelStride
                if (offset < 0 || offset + 2 >= buffer.limit()) continue
                // RGBA_8888 disponibiliza canais R, G, B nesta ordem.
                val r = buffer.get(offset).toInt() and 255
                val g = buffer.get(offset + 1).toInt() and 255
                val b = buffer.get(offset + 2).toInt() and 255
                val value = classify(r, g, b)
                if (value != 0) {
                    classes[y * w + x] = value.toByte()
                    rowCount[y]++
                    colored++
                }
            }
        }

        // Remove linhas horizontais coloridas (e.g. preço atual, áreas pintadas).
        for (y in 0 until h) {
            if (rowCount[y] > w * 0.35) {
                for (x in 0 until w) classes[y * w + x] = 0
            }
        }
        val colCount = IntArray(w)
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) if (classes[base + x] != 0.toByte()) colCount[x]++
        }

        val groups = ArrayList<IntRange>()
        var start = -1
        var last = -1
        for (x in 0 until w) {
            if (colCount[x] >= 2) {
                if (start < 0) start = x
                last = x
            } else if (start >= 0 && x - last > 2) {
                groups.add(start..last)
                start = -1
            }
        }
        if (start >= 0) groups.add(start..last)

        val candles = ArrayList<VisionCandle>()
        for (range in groups) {
            val gx0 = range.first
            val gx1 = range.last
            val groupWidth = gx1 - gx0 + 1
            // Retira trechos largos que costumam ser textos, botões ou faixas.
            if (groupWidth < 2 || groupWidth > w * 0.09) continue
            var greens = 0
            var reds = 0
            for (x in gx0..gx1) for (y in 0 until h) {
                when (classes[y*w+x].toInt()) {
                    1 -> greens++
                    -1 -> reds++
                }
            }
            val count = greens + reds
            if (count < 10) continue
            val isUp = greens > reds
            val majority = max(greens, reds).toDouble() / count
            if (majority < 0.83) continue
            val sign = if (isUp) 1 else -1

            var highY = h
            var lowY = -1
            var openBody = h
            var closeBody = -1
            var bodyPixels = 0
            val minBodyPixels = max(2, (groupWidth * 0.52).toInt())
            for (y in 0 until h) {
                var pixelsInRow = 0
                for (x in gx0..gx1) if (classes[y*w+x].toInt() == sign) pixelsInRow++
                if (pixelsInRow > 0) {
                    highY = min(highY, y)
                    lowY = max(lowY, y)
                }
                if (pixelsInRow >= minBodyPixels) {
                    openBody = min(openBody, y)
                    closeBody = max(closeBody, y)
                    bodyPixels++
                }
            }
            if (highY >= h || lowY <= highY || openBody >= h ||
                closeBody < openBody || bodyPixels < 2) continue
            val bodyTop = openBody
            val bodyBottom = closeBody
            val bodyHeight = bodyBottom - bodyTop
            if (bodyHeight < 2) continue
            val up = isUp
            val quality = (0.5 * majority + 0.25 * min(1.0, groupWidth / 5.0)
                + 0.25 * min(1.0, bodyHeight / 9.0)).coerceIn(0.0, 1.0)
            candles.add(VisionCandle(
                x = (gx0 + gx1) / 2, up = up,
                openY = if (up) bodyBottom else bodyTop,
                closeY = if (up) bodyTop else bodyBottom,
                highY = highY, lowY = lowY,
                bodyWidth = groupWidth, quality = quality
            ))
        }

        // Descarta padrões pouco densos e cenários não calibrados.
        val reason = when {
            candles.size < 6 -> "Poucos candles identificados. Calibre o recorte e a paleta verde/vermelha."
            candles.size > 90 -> "Muitos objetos detectados: verifique o recorte."
            else -> "Candles coloridos detectados; confira a calibração visual."
        }
        return Result(candles.sortedBy { it.x }, w, h, colored, reason)
    }
}
