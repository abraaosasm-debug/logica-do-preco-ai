package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class CandleTimelineTest {
    private fun candles(last: Int): List<VisionCandle> = (0..10).map { n ->
        val x = last - (10-n)*8
        VisionCandle(x,true,65,35,32,67,5,.94)
    }

    @Test fun doNotInventStartTimestampFromStaticChart() {
        val tracker=CandleTimeline()
        val initial=tracker.observe(candles(200),1_000,"M1")
        assertEquals("APRENDENDO", initial.phase)
        for (n in 1..100) tracker.observe(candles(200),1_000+n*650L,"M1")
        val result=tracker.observe(candles(200),70_000,"M1")
        assertEquals(0,result.rollovers)
        assertFalse(result.synchronized)
    }

    @Test fun confirmNewColumnOnlyAfterTwoFrames() {
        val tracker=CandleTimeline()
        tracker.observe(candles(200),1000,"M1")
        val first=tracker.observe(candles(208),60_500,"M1")
        assertEquals(0,first.rollovers)
        val second=tracker.observe(candles(208),61_200,"M1")
        assertEquals(1,second.rollovers)
        assertFalse(second.synchronized)
    }

    @Test fun observingTwoRegularPeriodsStillNotBrokerClock() {
        val tracker=CandleTimeline()
        tracker.observe(candles(200),1000,"M1")
        for (n in 1..3) {
            tracker.observe(candles(200+n*8),60_000L*n,"M1")
            tracker.observe(candles(200+n*8),60_000L*n+800,"M1")
        }
        val result=tracker.observe(candles(224),181_000,"M1")
        assertTrue(result.rollovers >= 3)
        assertTrue(result.validIntervals >= 2)
        assertTrue(result.detail.contains("relógio") || result.phase == "OBSERVADO")
    }

    @Test fun changingTimeframeInvalidatesClock() {
        val tracker=CandleTimeline()
        tracker.observe(candles(200),1000,"M1")
        tracker.observe(candles(208),60_000,"M1")
        tracker.observe(candles(208),60_800,"M1")
        val changed=tracker.observe(candles(208),61_200,"M5")
        assertEquals(0,changed.rollovers)
    }
}
