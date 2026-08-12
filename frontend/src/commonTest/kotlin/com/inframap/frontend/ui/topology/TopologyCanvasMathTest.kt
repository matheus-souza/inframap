package com.inframap.frontend.ui.topology

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class TopologyCanvasMathTest {
    @Test
    fun graphToScreenTransformationCorrectlyAppliesScaleAndPan() {
        val graphPoint = Offset(100f, 200f)
        val panOffset = Offset(50f, -30f)
        val zoomScale = 1.5f

        val screenPoint = CanvasMatrixMath.graphToScreen(graphPoint, panOffset, zoomScale)

        // 100 * 1.5 + 50 = 200
        assertEquals(200f, screenPoint.x)
        // 200 * 1.5 - 30 = 270
        assertEquals(270f, screenPoint.y)
    }

    @Test
    fun screenToGraphTransformationCorrectlyInvertsScaleAndPan() {
        val screenPoint = Offset(200f, 270f)
        val panOffset = Offset(50f, -30f)
        val zoomScale = 1.5f

        val graphPoint = CanvasMatrixMath.screenToGraph(screenPoint, panOffset, zoomScale)

        // (200 - 50) / 1.5 = 100
        assertEquals(100f, graphPoint.x)
        // (270 - -30) / 1.5 = 200
        assertEquals(200f, graphPoint.y)
    }

    @Test
    fun roundTripTransformIsIdentity() {
        val originalGraphPoint = Offset(45.5f, 120.25f)
        val panOffset = Offset(12.0f, 34.0f)
        val zoomScale = 2.4f

        val screenPoint = CanvasMatrixMath.graphToScreen(originalGraphPoint, panOffset, zoomScale)
        val resultGraphPoint = CanvasMatrixMath.screenToGraph(screenPoint, panOffset, zoomScale)

        assertEquals(originalGraphPoint.x, resultGraphPoint.x, 0.001f)
        assertEquals(originalGraphPoint.y, resultGraphPoint.y, 0.001f)
    }

    @Test
    fun zoomScaleBoundariesSupportMinAndMaxZoom() {
        val graphPoint = Offset(10f, 10f)
        val panOffset = Offset.Zero

        val minZoomScreen = CanvasMatrixMath.graphToScreen(graphPoint, panOffset, 0.2f)
        assertEquals(2f, minZoomScreen.x)
        assertEquals(2f, minZoomScreen.y)

        val maxZoomScreen = CanvasMatrixMath.graphToScreen(graphPoint, panOffset, 3.0f)
        assertEquals(30f, maxZoomScreen.x)
        assertEquals(30f, maxZoomScreen.y)
    }
}
