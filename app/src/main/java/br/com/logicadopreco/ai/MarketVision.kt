package br.com.logicadopreco.ai

import android.media.Image
import android.graphics.Bitmap
import android.graphics.Color
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
        if (image.planes.isEmpty()) return Result(emptyList(), 0, 0, 0, "Imagem sem pixels.")
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride < 3) return Result(emptyList(), 0, 0, 0, "Formato de pixels incompatível.")
        return analyzeCore(image.width, image.height, crop) { x, y ->
            val offset = y * stride + x * pixelStride
            if (offset < 0 || offset + 2 >= buffer.limit()) return@analyzeCore 0
            val r = buffer.get(offset).toInt() and 255
            val g = buffer.get(offset + 1).toInt() and 255
            val b = buffer.get(offset + 2).toInt() and 255
            classify(r, g, b)
        }
    }

    /** Análise manual de um print selecionado; usa a mesma lógica do monitoramento. */
    fun analyzeBitmap(bitmap: Bitmap, crop: VisionCrop): Result =
        analyzeCore(bitmap.width, bitmap.height, crop) { x, y ->
            val pixel = bitmap.getPixel(x, y)
            classify(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        }

    private inline fun analyzeCore(imgW: Int, imgH: Int, crop: VisionCrop,
                                   pixelClass: (Int, Int) -> Int): Result {
        if (!crop.valid) return Result(emptyList(), 0, 0, 0, "Recorte inválido. Ajuste os limites.")
        val x0 = imgW * crop.left / 100
        val y0 = imgH * crop.top / 100
        val x1 = imgW * crop.right / 100
        val y1 = imgH * crop.bottom / 100
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
                val value = pixelClass(realX, realY)
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
        // Cada corpo é segmentado independentemente. A versão antiga juntava
        // muitas velas próximas em um único grupo colorido.
        val candles = CandleSegmenter.detect(classes, w, h)

        // Descarta padrões pouco densos e cenários não calibrados.
        val reason = when {
            candles.size < 6 -> "Poucos candles identificados. Calibre o recorte e a paleta verde/vermelha."
            candles.size > 90 -> "Muitos objetos detectados: verifique o recorte."
            else -> "Corpos visuais separados: ${candles.size}. Confirmar com o gráfico real."
        }
        return Result(candles.sortedBy { it.x }, w, h, colored, reason)
    }
}
