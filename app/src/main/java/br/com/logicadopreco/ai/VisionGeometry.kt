package br.com.logicadopreco.ai

import kotlin.math.roundToInt

/** Converte coordenadas amostradas para a imagem visualizada pelo usuário. */
data class PixelPoint(val x: Float, val y: Float)
data class PixelRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

object VisionGeometry {
    fun roi(width: Int, height: Int, crop: VisionCrop): PixelRect {
        require(width > 0 && height > 0 && crop.valid)
        return PixelRect(
            width * crop.left / 100f,
            height * crop.top / 100f,
            width * crop.right / 100f,
            height * crop.bottom / 100f
        )
    }

    fun toImagePoint(
        x: Int,
        y: Int,
        sampleW: Int,
        sampleH: Int,
        width: Int,
        height: Int,
        crop: VisionCrop
    ): PixelPoint {
        require(sampleW > 0 && sampleH > 0)
        val rect = roi(width, height, crop)
        return PixelPoint(
            rect.left + x.toFloat() / sampleW * (rect.right - rect.left),
            rect.top + y.toFloat() / sampleH * (rect.bottom - rect.top)
        )
    }
}
