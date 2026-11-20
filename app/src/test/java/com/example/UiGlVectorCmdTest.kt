package com.example

import com.example.engine.UiGlNative
import com.example.engine.UiGlRuntime
import com.example.engine.VectorCmdBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UiGlVectorCmdTest {

    @Test
    fun packsAllPrimitivesWithExactArgbColorsAndResizesSafely() {
        val b = VectorCmdBuilder(initialFloats = 8, initialColors = 2)
        b.addLine(1f, 2f, 30f, 40f, 2.5f, 0xFF3EB1F7.toInt(), dashOn = 6f, dashOff = 4f, capRound = false)
        b.addPolyline(
            floatArrayOf(0f, 10f, 10f, 0f),
            floatArrayOf(0f, 0f, 10f, 10f),
            4,
            closed = true,
            width = 1.5f,
            argb = 0x73000000
        )
        b.addPolyline(floatArrayOf(5f), floatArrayOf(5f), 1, closed = false, width = 2f, argb = 0xFFFFFFFF.toInt())
        b.addCircle(50f, 60f, 8f, 0xFFD2FC32.toInt(), strokeWidth = 1.5f, strokeArgb = 0xFF111114.toInt())
        b.addRoundRect(20f, 30f, 16f, 16f, 4f, 0xFFFFFFFF.toInt(), strokeWidth = 1.5f, strokeArgb = 0xFF1689F5.toInt())
        b.addRect(0f, 0f, 100f, 50f, 0xFF0D0D0D.toInt())

        val snap = b.build()

        // 9 (line) + (7 + 8) (polyline) + 0 (skipped 1-pt polyline) + 5 (circle) + 7 (roundRect) + 5 (rect) = 41
        assertEquals(41, snap.floatCount)
        assertEquals(41, snap.floats.size)
        // 1 (line) + 1 (polyline) + 2 (circle) + 2 (roundRect) + 1 (rect) = 7
        assertEquals(7, snap.colorCount)
        assertEquals(7, snap.colors.size)

        assertEquals(UiGlNative.CMD_LINE.toFloat(), snap.floats[0], 0f)
        assertEquals(6f, snap.floats[6], 0f)
        assertEquals(0f, snap.floats[8], 0f)

        assertEquals(UiGlNative.CMD_POLYLINE.toFloat(), snap.floats[9], 0f)
        assertEquals(1f, snap.floats[10], 0f)
        assertEquals(4f, snap.floats[15], 0f)

        assertEquals(UiGlNative.CMD_CIRCLE.toFloat(), snap.floats[24], 0f)
        assertEquals(UiGlNative.CMD_ROUND_RECT.toFloat(), snap.floats[29], 0f)
        assertEquals(UiGlNative.CMD_RECT.toFloat(), snap.floats[36], 0f)

        assertEquals(0xFF3EB1F7.toInt(), snap.colors[0])
        assertEquals(0x73000000, snap.colors[1])
        assertEquals(0xFFD2FC32.toInt(), snap.colors[2])
        assertEquals(0xFF111114.toInt(), snap.colors[3])
        assertEquals(0xFFFFFFFF.toInt(), snap.colors[4])
        assertEquals(0xFF1689F5.toInt(), snap.colors[5])
        assertEquals(0xFF0D0D0D.toInt(), snap.colors[6])
    }

    @Test
    fun runtimeFallsBackCleanlyOnHostJvmWithoutNativeLib() {
        assertFalse(UiGlRuntime.isSupported)
        assertFalse(com.example.engine.UiMathNative.isAvailable)
    }

    @Test
    fun packsExtendedVectorCommandsAccurately() {
        val b = VectorCmdBuilder(initialFloats = 16, initialColors = 4)
        b.addCubic(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 2f, 0xFF112233.toInt(), capRound = true)
        b.addArc(10f, 20f, 8f, 8f, -90f, 180f, width = 3f, fillArgb = 0, strokeArgb = 0xFF445566.toInt(), capRound = true, closedPie = false)
        b.addFilledPolygon(floatArrayOf(0f, 5f, 10f), floatArrayOf(0f, 10f, 0f), 3, 0xFF778899.toInt())
        b.addDimmerHole(100f, 200f, 10f, 20f, 90f, 180f, 0x80000000.toInt())
        b.addShaderQuad(5f, 6f, 40f, 20f, 4f, UiGlNative.QUAD_SV_BOX, p0 = 0.5f, c0Argb = 0xFFFF0000.toInt(), c1Argb = 0xFF00FF00.toInt())
        b.addGradRect(0f, 0f, 50f, 80f, vertical = true, c0Argb = 0xFF101010.toInt(), c1Argb = 0xFF202020.toInt())

        val snap = b.build()
        // 11 (cubic) + 10 (arc) + (2 + 6) (fillPoly) + 7 (dimmerHole) + 13 (shaderQuad) + 6 (gradRect) = 55
        assertEquals(55, snap.floatCount)
        // 1 (cubic) + 2 (arc) + 1 (fillPoly) + 1 (dimmerHole) + 2 (shaderQuad) + 2 (gradRect) = 9
        assertEquals(9, snap.colorCount)
        assertEquals(UiGlNative.CMD_CUBIC.toFloat(), snap.floats[0], 0f)
        assertEquals(UiGlNative.CMD_ARC.toFloat(), snap.floats[11], 0f)
        assertEquals(UiGlNative.CMD_FILL_POLY.toFloat(), snap.floats[21], 0f)
        assertEquals(UiGlNative.CMD_DIMMER_HOLE.toFloat(), snap.floats[29], 0f)
        assertEquals(UiGlNative.CMD_SHADER_QUAD.toFloat(), snap.floats[36], 0f)
        assertEquals(UiGlNative.CMD_GRAD_RECT.toFloat(), snap.floats[49], 0f)
    }
}
