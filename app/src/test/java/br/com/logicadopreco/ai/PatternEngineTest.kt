package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class PatternEngineTest {
    private fun red(x: Int) = VisionCandle(
        x = x, up = false, openY = 40, closeY = 60,
        highY = 38, lowY = 62, bodyWidth = 7, quality = 0.94
    )
    private fun commandGreen(x: Int, openWick: Int = 0) = VisionCandle(
        x = x, up = true, openY = 80, closeY = 40,
        highY = 36, lowY = 80 + openWick, bodyWidth = 7, quality = 0.97
    )
    private fun liveGreen(x: Int) = VisionCandle(
        x = x, up = true, openY = 40, closeY = 50,
        highY = 39, lowY = 65, bodyWidth = 7, quality = 0.92
    )
    @Test fun requiresEnoughCandles() {
        val result = PatternEngine().evaluate((1..7).map { red(it * 20) }, 360)
        assertEquals("CALIBRAR", result.status)
        assertEquals("NEUTRO", result.direction)
    }
    @Test fun detectsCommandAndReturnToHalfInObservationOnly() {
        val candles = (0..7).map { red(10 + it * 18) } +
            commandGreen(174) + liveGreen(192)
        val result = PatternEngine().evaluate(candles, 360)
        assertEquals("OBSERVAÇÃO", result.status)
        assertEquals("CALL", result.direction)
        assertTrue(result.rationale.contains("NÃO está confirmado"))
    }
    @Test fun rejectsCommandWithLargeOpeningWick() {
        val candles = (0..7).map { red(10 + it * 18) } +
            commandGreen(174, openWick = 20) + liveGreen(192)
        val result = PatternEngine().evaluate(candles, 360)
        assertNotEquals("OBSERVAÇÃO", result.status)
        assertEquals("NEUTRO", result.direction)
    }
    @Test fun rejectsInvalidCrop() {
        assertFalse(VisionCrop(50, 25, 60, 60).valid)
        assertTrue(VisionCrop(5, 18, 88, 78).valid)
    }
}
