package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class ExpandedPatternEngineTest {
    private fun candle(x: Int, up: Boolean, open: Int, close: Int,
                       high: Int, low: Int, quality: Double = .95) =
        VisionCandle(x, up, open, close, high, low, 7, quality)

    private val green = candle(10, true, 90, 50, 45, 90)
    private val red = candle(10, false, 50, 90, 50, 95)

    @Test fun commandLevelsFollowThePdf() {
        val engine = ExpandedPatternEngine()
        assertEquals(1, engine.commandLevel(candle(5, true, 80, 40, 40, 80), 2))
        assertEquals(2, engine.commandLevel(green, 2))
        assertEquals(3, engine.commandLevel(candle(5, true, 80, 40, 40, 90), 2))
        assertEquals(0, engine.commandLevel(candle(5, true, 80, 40, 34, 88), 2))
        assertEquals(3, engine.commandLevel(red, 2)) // candle sem pavio inferior
    }

    @Test fun noPatternWithTooFewCandles() {
        val result = ExpandedPatternEngine().evaluate(List(7) { green.copy(x = it * 10) }, 300)
        assertTrue(result.evidence.isEmpty())
        assertEquals("NEUTRO", result.candidateDirection)
        assertFalse(result.triggerConfirmed)
    }

    @Test fun doesNotGenerateConfirmedTradeEvenWithCandidateData() {
        val candles = mutableListOf<VisionCandle>()
        repeat(10) { n ->
            candles += if (n % 2 == 0)
                candle(n * 12, true, 88, 46, 42, 88)
            else candle(n * 12, false, 45, 85, 44, 90)
        }
        val result = ExpandedPatternEngine().evaluate(candles, 300)
        assertFalse(result.triggerConfirmed)
        assertFalse(result.priceLevelsAvailable)
        assertFalse(result.candleTimesAvailable)
    }

    @Test fun uncertainRecognitionBlocksEvidence() {
        val candles = List(10) { green.copy(x = it * 15, quality = .4) }
        val result = ExpandedPatternEngine().evaluate(candles, 300)
        assertTrue(result.evidence.isEmpty())
        assertEquals("NEUTRO", result.candidateDirection)
    }
}
