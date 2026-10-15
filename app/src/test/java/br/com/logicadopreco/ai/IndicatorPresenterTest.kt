package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class IndicatorPresenterTest {
    private fun sample() = AnalysisSnapshot(
        phase = "OBSERVAÇÃO", candles = 22, quality = 93,
        pattern = "Comando nível 2", direction = "CALL",
        lastUpdateMs = 1000, asset = "EUR/JPY", timeframe = "M1"
    )

    @Test fun visualCallNeverBecomesOrder() {
        val ui = IndicatorPresenter.show(
            sample(), CaptureSnapshot(phase = "CAPTURANDO"), 500
        )
        assertEquals("AGUARDAR", ui.headline)
        assertTrue(ui.context.contains("sem entrada"))
        assertEquals("SEM ENTRADA", ui.badge)
    }
    @Test fun insufficientCandlesBlockTrade() {
        val ui = IndicatorPresenter.show(
            sample().copy(candles = 1),
            CaptureSnapshot(phase = "CAPTURANDO"), 500
        )
        assertEquals("CALIBRAR", ui.headline)
    }
    @Test fun staleCaptureBlocksReading() {
        val ui = IndicatorPresenter.show(
            sample(), CaptureSnapshot(phase = "CAPTURANDO"), 6000
        )
        assertEquals("CAPTURA PAUSADA", ui.headline)
    }
    @Test fun zeroCaptureIsNotLive() {
        val ui = IndicatorPresenter.show(
            sample(), CaptureSnapshot(phase = "PARADO"), 500
        )
        assertEquals("CAPTURA PAUSADA", ui.headline)
    }
}
