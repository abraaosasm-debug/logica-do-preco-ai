package br.com.logicadopreco.ai

import kotlin.math.abs
import kotlin.math.max

/**
 * Observa mudanças de posição das velas. Detecta transições candidatas, sem
 * pressupor que o horário local equivale ao relógio da corretora.
 *
 * IMPORTANTE: a Quotex pode rolar todo o gráfico a cada candle; nesses casos
 * uma mudança baseada apenas em posição X NÃO sincroniza o tempo.
 */
data class TimelineReading(
    val phase: String,
    val rollovers: Int,
    val validIntervals: Int,
    val lastBoundaryMs: Long?,
    val ageMs: Long?,
    val detail: String
) {
    val synchronized: Boolean get() = validIntervals >= 2 && phase == "OBSERVADO"
}

class CandleTimeline {
    private var anchorX = -1
    private var pendingX = -1
    private var pendingFrames = 0
    private var previousAt = 0L
    private var boundaryAt: Long? = null
    private var rollovers = 0
    private var validIntervals = 0
    private var lastTimeframe = "M1"

    @Synchronized
    fun observe(candles: List<VisionCandle>, nowMs: Long, timeframe: String): TimelineReading {
        if (timeframe != lastTimeframe) {
            clear()
            lastTimeframe = timeframe
        }
        if (candles.size < 8) {
            pendingX = -1
            pendingFrames = 0
            return status("SEM BASE", nowMs, "Menos de oito candles reconhecidos.")
        }
        val sorted = candles.sortedBy { it.x }
        val diffs = sorted.zipWithNext { a, b -> b.x - a.x }
            .filter { it >= 3 && it <= 28 }.sorted()
        if (diffs.size < 5) return status(
            "SEM BASE", nowMs, "Espaçamento dos candles ainda não é regular."
        )
        val step = diffs[diffs.size / 2]
        val last = sorted.last().x

        if (anchorX < 0) {
            anchorX = last
            previousAt = nowMs
            return status("APRENDENDO", nowMs, "Aguardando observar a formação de um novo candle.")
        }
        val delta = last - anchorX
        val minStep = max(3, (step * .65).toInt())
        // Nova coluna mais à direita e candle anterior ainda identificável.
        val prevExists = sorted.dropLast(1).takeLast(3).any { abs(it.x - anchorX) <= max(2,step/3) }
        val newColumn = delta >= minStep && delta <= step * 2 + 2 && prevExists
        if (newColumn) {
            if (abs(last - pendingX) <= 2) pendingFrames++ else {
                pendingX = last
                pendingFrames = 1
            }
            if (pendingFrames >= 2) {
                anchorX = last
                pendingX = -1
                pendingFrames = 0
                rollovers++
                val previous = boundaryAt
                boundaryAt = nowMs
                val expected = if (timeframe == "M5") 300_000L else 60_000L
                if (previous != null) {
                    val elapsed = nowMs - previous
                    if (abs(elapsed - expected) <= 12_000L) {
                        validIntervals++
                    } else {
                        validIntervals = 0
                    }
                }
                return status("OBSERVADO", nowMs,
                    "Nova coluna observada; abertura exata ainda não verificada.")
            }
        } else {
            // Caso o gráfico se mova para a esquerda, não inventar um rollover.
            if (delta < -max(3,step/2)) {
                anchorX = last
                pendingX = -1
                pendingFrames = 0
                validIntervals = 0
                return status("DESLOCAMENTO", nowMs,
                    "O gráfico rolou horizontalmente. Sem sincronização temporal.")
            }
            if (delta > step * 2 + 2) {
                anchorX = last
                pendingX = -1
                pendingFrames = 0
                validIntervals = 0
                return status("RECALIBRAR", nowMs,
                    "Mudança brusca de posição; possível alteração de zoom ou escala.")
            }
            pendingX = -1
            pendingFrames = 0
        }
        previousAt = nowMs
        val age = boundaryAt?.let { nowMs - it }
        if (age != null && age > 420_000L) {
            validIntervals = 0
            return status("SEM RELÓGIO", nowMs,
                "Nenhuma nova vela observada recentemente.")
        }
        return status(if (rollovers > 0) "OBSERVADO" else "APRENDENDO", nowMs,
            if (validIntervals >= 2) "Ciclos visuais repetidos, ainda sem confirmação do relógio da Quotex."
            else "Aguardando observar ciclos completos para estimar abertura.")
    }

    private fun status(phase:String, nowMs:Long, detail:String) = TimelineReading(
        phase = phase, rollovers = rollovers, validIntervals = validIntervals,
        lastBoundaryMs = boundaryAt,
        ageMs = boundaryAt?.let { (nowMs-it).coerceAtLeast(0L) },
        detail = detail
    )

    @Synchronized
    fun clear() {
        anchorX = -1
        pendingX = -1
        pendingFrames = 0
        previousAt = 0L
        boundaryAt = null
        rollovers = 0
        validIntervals = 0
    }
}
