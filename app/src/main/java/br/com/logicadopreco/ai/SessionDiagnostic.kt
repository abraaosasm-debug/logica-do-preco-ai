package br.com.logicadopreco.ai

import android.os.SystemClock
import java.util.Locale

/** Apenas geometria: não inclui fotos, cotações, saldo ou credenciais. */
object SessionDiagnostic {
    private val lines = ArrayDeque<String>()
    private const val limit = 600
    private const val header =
        "epoch_ms,uptime_ms,timeframe,roi_width,roi_height,candles,avg_visual_quality,last_x,last_open_y,last_close_y,last_up,previous_x,clock_state,clock_rollovers,clock_cycles"

    @Synchronized
    fun add(candles: List<VisionCandle>, quality:Int, timeframe:String,
            width:Int, height:Int, clock:TimelineReading) {
        val last=candles.lastOrNull()
        val prior=candles.dropLast(1).lastOrNull()
        val row=listOf(
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
            timeframe,
            width,height,candles.size,quality,
            last?.x ?: -1,last?.openY ?: -1,last?.closeY ?: -1,
            if(last==null) "-" else if(last.up) "UP" else "DOWN",
            prior?.x ?: -1,
            clock.phase,clock.rollovers,clock.validIntervals
        ).joinToString(",")
        lines.addLast(row)
        while (lines.size>limit) lines.removeFirst()
    }

    @Synchronized
    fun exportCsv(): String {
        return "# LP Vision - Diagnostico de candles (sem imagens ou dados de conta)\n" +
            header + "\n" + lines.joinToString("\n") + "\n"
    }

    @Synchronized
    fun count(): Int = lines.size

    @Synchronized
    fun clear() = lines.clear()
}
