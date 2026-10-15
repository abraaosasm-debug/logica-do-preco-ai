package br.com.logicadopreco.ai

import android.content.Context
import android.content.SharedPreferences

/** Valores percentuais da área EXCLUSIVA dos candles. */
data class VisionCrop(val left: Int = 2, val top: Int = 30, val right: Int = 73, val bottom: Int = 73) {
    val valid: Boolean get() = right - left >= 20 && bottom - top >= 20
}

class VisionSettings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("vision_config", Context.MODE_PRIVATE)
    fun getCrop() = VisionCrop(
        prefs.getInt("left", 2), prefs.getInt("top", 30),
        prefs.getInt("right", 73), prefs.getInt("bottom", 73)
    )
    fun setCrop(value: VisionCrop) {
        prefs.edit().putInt("left", value.left).putInt("top", value.top)
            .putInt("right", value.right).putInt("bottom", value.bottom).apply()
    }
    fun getTimeframe() = prefs.getString("timeframe", "M1") ?: "M1"
    fun setTimeframe(timeframe: String) = prefs.edit().putString("timeframe", timeframe).apply()
    fun getAsset() = prefs.getString("asset", "Ativo não identificado") ?: "Ativo não identificado"
    fun setAsset(asset: String) = prefs.edit().putString("asset", asset.take(40)).apply()
}
