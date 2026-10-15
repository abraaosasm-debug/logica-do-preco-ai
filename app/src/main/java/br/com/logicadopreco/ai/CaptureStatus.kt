package br.com.logicadopreco.ai

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Telemetria real da captura. Não produz sinais de mercado. */
data class CaptureSnapshot(
    val phase: String = "PARADO",
    val message: String = "Pronto para iniciar um teste de captura.",
    val frameCount: Long = 0,
    val fps: Double = 0.0,
    val meanBrightness: Int? = null,
    val width: Int = 0,
    val height: Int = 0,
    val lastFrameAtMs: Long = 0L
) {
    fun lastFrameAgeMs(): Long? =
        if (lastFrameAtMs == 0L) null
        else (SystemClock.elapsedRealtime() - lastFrameAtMs).coerceAtLeast(0L)
}

object CaptureStatus {
    private val mutable = MutableStateFlow(CaptureSnapshot())
    val status: StateFlow<CaptureSnapshot> = mutable

    private var windowStartMs = 0L
    private var windowFrames = 0

    @Synchronized
    fun start(width: Int, height: Int) {
        windowStartMs = SystemClock.elapsedRealtime()
        windowFrames = 0
        mutable.value = CaptureSnapshot(
            phase = "INICIANDO",
            message = "Aguardando os primeiros quadros do Android.",
            width = width,
            height = height
        )
    }

    @Synchronized
    fun onFrame(width: Int, height: Int, brightness: Int) {
        val now = SystemClock.elapsedRealtime()
        windowFrames++
        val elapsed = now - windowStartMs
        val old = mutable.value
        val fps = if (elapsed >= 1500L && elapsed > 0L) {
            val calculated = 1000.0 * windowFrames / elapsed
            windowStartMs = now
            windowFrames = 0
            calculated
        } else old.fps

        mutable.value = old.copy(
            phase = "CAPTURANDO",
            message = if (brightness < 5) "Imagem quase preta: verifique a tela compartilhada." else
                "Quadros recebidos. Os candles ainda não são interpretados nesta versão.",
            frameCount = old.frameCount + 1,
            fps = fps,
            meanBrightness = brightness,
            width = width,
            height = height,
            lastFrameAtMs = now
        )
    }

    @Synchronized
    fun setPhase(phase: String, message: String) {
        mutable.value = mutable.value.copy(phase = phase, message = message)
    }

    @Synchronized
    fun stop(message: String = "Captura encerrada pelo usuário.") {
        mutable.value = mutable.value.copy(phase = "PARADO", message = message, fps = 0.0)
    }
}
