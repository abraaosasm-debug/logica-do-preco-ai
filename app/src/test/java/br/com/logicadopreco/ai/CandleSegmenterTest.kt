package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class CandleSegmenterTest {
    private fun draw(width: Int, height: Int, candles: Int, gap: Int = 2): ByteArray {
        val grid = ByteArray(width * height)
        val step = 7 + gap
        for (i in 0 until candles) {
            val x0 = 4 + i * step
            val sign = if (i % 3 == 0) -1 else 1
            val top = 15 + ((i * 13) % 37)
            val bodyHeight = 10 + (i % 9)
            for (x in x0 until x0 + 6) {
                if (x >= width) continue
                for (y in top until top + bodyHeight) {
                    grid[y * width + x] = sign.toByte()
                }
            }
            // Pavios verticais da mesma cor.
            val cx = x0 + 3
            if (cx < width) for (y in (top - 5).coerceAtLeast(0) until top + bodyHeight + 5) {
                grid[y * width + cx] = sign.toByte()
            }
        }
        return grid
    }

    @Test fun detectsDozensRatherThanMergingIntoOne() {
        val grid = draw(570, 180, candles = 56, gap = 2)
        val candles = CandleSegmenter.detect(grid, 570, 180)
        assertTrue("Got " + candles.size, candles.size >= 45)
        assertEquals(-1, if (candles[0].up) 1 else -1)
    }

    @Test fun staysSeparateEvenWithNearZeroGap() {
        val grid = draw(570, 180, candles = 55, gap = 0)
        val candles = CandleSegmenter.detect(grid, 570, 180)
        assertTrue("Got " + candles.size, candles.size >= 45)
    }

    @Test fun rejectUnrecognizableInput() {
        assertTrue(CandleSegmenter.detect(ByteArray(570*180), 570,180).isEmpty())
        assertTrue(CandleSegmenter.detect(ByteArray(10), 570, 180).isEmpty())
    }
}
