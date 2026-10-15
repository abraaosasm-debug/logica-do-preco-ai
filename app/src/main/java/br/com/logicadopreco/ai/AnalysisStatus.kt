package br.com.logicadopreco.ai

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AnalysisSnapshot(
    val phase: String = "AGUARDANDO",
    val candles: Int = 0,
    val quality: Int = 0,
    val pattern: String = "—",
    val direction: String = "NEUTRO",
    val detail: String = "Inicie a captura e calibre a área do gráfico.",
    val marker: String = "—",
    val analyzedFrames: Long = 0,
    val lastUpdateMs: Long = 0,
    val asset: String = "Não identificado",
    val timeframe: String = "M1",
    val events: List<String> = emptyList()
)

object AnalysisStatus {
    private val mutable = MutableStateFlow(AnalysisSnapshot())
    val status: StateFlow<AnalysisSnapshot> = mutable
    private var lastEventSignature = ""
    private var lastEventAt = 0L

    @Synchronized
    fun update(vision: MarketVision.Result, reading: PatternReading, asset: String, timeframe: String) {
        val now = SystemClock.elapsedRealtime()
        val quality = if (vision.candles.isEmpty()) 0
            else (vision.candles.map { it.quality }.average() * 100).toInt()
        val valid = vision.candles.size >= 8 && quality >= 68
        val phase = if (!valid) "CALIBRAR" else reading.status
        val explanation = if (!valid) vision.reason else reading.rationale
        val previous = mutable.value
        val signature = phase + "|" + reading.label + "|" + reading.direction + "|" + reading.sourceX
        var events = previous.events
        if (phase == "OBSERVAÇÃO" && signature != lastEventSignature && now - lastEventAt >= 45000L) {
            val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            events = (listOf(date + " • " + reading.direction + " em observação • " + reading.label) +
                events).take(25)
            lastEventAt = now
            lastEventSignature = signature
        } else if (phase != "OBSERVAÇÃO") {
            lastEventSignature = ""
        }
        mutable.value = AnalysisSnapshot(
            phase = phase,
            candles = vision.candles.size,
            quality = quality,
            pattern = if (valid) reading.label else "—",
            direction = if (valid) reading.direction else "NEUTRO",
            detail = explanation,
            marker = if (valid && reading.markerY != null)
                "Linha 50%: y=" + reading.markerY + " / " + vision.sampledHeight
                else "—",
            analyzedFrames = previous.analyzedFrames + 1,
            lastUpdateMs = now,
            asset = asset, timeframe = timeframe,
            events = events
        )
    }

    @Synchronized
    fun error(reason: String) {
        mutable.value = mutable.value.copy(
            phase = "INDETERMINADO", direction = "NEUTRO",
            detail = reason
        )
    }

    @Synchronized
    fun reset() {
        mutable.value = AnalysisSnapshot()
        lastEventSignature = ""
        lastEventAt = 0L
    }

    fun ageMs(): Long =
        (SystemClock.elapsedRealtime() - mutable.value.lastUpdateMs).coerceAtLeast(0L)
}
