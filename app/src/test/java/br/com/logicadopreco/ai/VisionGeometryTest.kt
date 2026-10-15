package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test

class VisionGeometryTest {
    @Test fun roiMapsToCorrectPixels() {
        val crop = VisionCrop(left = 10, top = 20, right = 90, bottom = 80)
        val bounds = VisionGeometry.roi(1000, 2000, crop)
        assertEquals(100f, bounds.left, .001f)
        assertEquals(400f, bounds.top, .001f)
        assertEquals(900f, bounds.right, .001f)
        assertEquals(1600f, bounds.bottom, .001f)
    }
    @Test fun sampledCoordinatesMapInsideRoi() {
        val crop = VisionCrop(left = 10, top = 20, right = 90, bottom = 80)
        val point = VisionGeometry.toImagePoint(250, 150, 500, 300, 1000, 2000, crop)
        assertEquals(500f, point.x, .001f)
        assertEquals(1000f, point.y, .001f)
    }
}
