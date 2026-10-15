package br.com.logicadopreco.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Auditoria visual de um PRINT SELECIONADO pelo usuário (não de frames privados).
 * Guarda somente a última miniatura na RAM. Não faz uploads nem grava capturas.
 */
data class VisionAuditPreview(
    val bitmap: Bitmap? = null,
    val candidates: Int = 0,
    val review: String = "PENDENTE",
    val details: String = "Abra um print do gráfico para conferir a marcação de cada candle.",
    val crop: VisionCrop = VisionCrop()
)

object VisionAudit {
    private val mutable = MutableStateFlow(VisionAuditPreview())
    val state: StateFlow<VisionAuditPreview> = mutable

    @Synchronized
    fun clear() {
        mutable.value = VisionAuditPreview()
    }

    @Synchronized
    fun markResult(valid: Boolean) {
        val old = mutable.value
        if (old.bitmap == null) return
        mutable.value = old.copy(
            review = if (valid) "CONFIRMADO MANUALMENTE" else "PRECISA DE AJUSTES",
            details = if (valid)
                "Marcação visual conferida por você neste print. Não valida sinais ou horários."
            else "Falso positivo, candle ausente ou região imprecisa. Ajuste o recorte."
        )
    }

    @Synchronized
    fun publish(source: Bitmap, crop: VisionCrop, result: MarketVision.Result) {
        val maxDimension = max(source.width, source.height)
        val factor = 1080f / maxDimension
        val scale = factor.coerceAtMost(1f)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val decorated = small.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(decorated)
        val roi = VisionGeometry.roi(width, height, crop)

        fun paint(color: Int, stroke: Float, style: Paint.Style = Paint.Style.STROKE) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = stroke
                this.style = style
            }

        val border = paint(Color.rgb(90, 220, 243), 2.5f)
        canvas.drawRect(roi.left, roi.top, roi.right, roi.bottom, border)

        if (result.sampledWidth > 0 && result.sampledHeight > 0) {
            for ((index, candle) in result.candles.withIndex()) {
                val color = if (candle.up) Color.rgb(94, 238, 157) else Color.rgb(248, 132, 147)
                val wickTop = VisionGeometry.toImagePoint(
                    candle.x, candle.highY, result.sampledWidth, result.sampledHeight,
                    width, height, crop
                )
                val wickBottom = VisionGeometry.toImagePoint(
                    candle.x, candle.lowY, result.sampledWidth, result.sampledHeight,
                    width, height, crop
                )
                val bodyTop = VisionGeometry.toImagePoint(
                    candle.x, minOf(candle.openY, candle.closeY),
                    result.sampledWidth, result.sampledHeight, width, height, crop
                )
                val bodyBottom = VisionGeometry.toImagePoint(
                    candle.x, maxOf(candle.openY, candle.closeY),
                    result.sampledWidth, result.sampledHeight, width, height, crop
                )
                val halfBody = candle.bodyWidth.toFloat() / result.sampledWidth *
                    (roi.right - roi.left) / 2
                val bodyStroke = paint(color, 2f)
                canvas.drawLine(wickTop.x, wickTop.y, wickBottom.x, wickBottom.y, bodyStroke)
                canvas.drawRect(
                    bodyTop.x - halfBody, bodyTop.y,
                    bodyBottom.x + halfBody, bodyBottom.y, bodyStroke
                )

                // Rótulos espaçados para não poluir o gráfico.
                if (index % 4 == 0) {
                    val labelPaint = paint(color, 1f, Paint.Style.FILL).apply { textSize = 11f }
                    canvas.drawText((index + 1).toString(), bodyTop.x - 6, bodyTop.y - 6, labelPaint)
                }
            }
        }
        mutable.value = VisionAuditPreview(
            bitmap = decorated,
            candidates = result.candles.size,
            review = "PENDENTE",
            details = "Verde/vermelho = corpo detectado; linha azul = recorte. " +
                "Confira falsos positivos antes de confiar em padrões.",
            crop = crop
        )
    }
}
