package com.example

import com.example.model.DragMath
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Build 225 — the canvas drag accumulation rule (model/DragMath.kt): a drag is
 * measured on a fixed full-canvas layer in canvas pixels, and the stored position
 * is start + totalCanvasTravel / depthFactor.
 *
 * The equivalence tests replay the build-224 per-layer handlers' math (the text
 * handler in TextLayerView and the shape handler in CanvasArea): each event's
 * amount arrived in the layer's own scaled / rotated / flipped space and was
 * converted back per event. Feeding the same finger travel through that old
 * conversion must land on the DragMath position — the fix changes WHERE the drag
 * is measured, never where the layer ends up.
 */
class DragMathTest {

    @Test
    fun depthOne_travelIsStoredAsIs() {
        val (x, y) = DragMath.draggedPosition(10f, 20f, 30f, -5f, 1f)
        assertEquals(40f, x, 0f)
        assertEquals(15f, y, 0f)
    }

    @Test
    fun depthTwo_halvesTheTravel() {
        val (x, y) = DragMath.draggedPosition(0f, 0f, 30f, 10f, 2f)
        assertEquals(15f, x, 0f)
        assertEquals(5f, y, 0f)
    }

    @Test
    fun depthHalf_doublesTheTravel() {
        val (x, y) = DragMath.draggedPosition(3f, -2f, 30f, 10f, 0.5f)
        assertEquals(63f, x, 0f)
        assertEquals(18f, y, 0f)
    }

    @Test
    fun zeroTravel_keepsTheStart() {
        val (x, y) = DragMath.draggedPosition(-7.5f, 12.25f, 0f, 0f, 1.8f)
        assertEquals(-7.5f, x, 0f)
        assertEquals(12.25f, y, 0f)
    }

    @Test
    fun zeroDepthFactor_usesTheFloor() {
        // depthFactorOf never returns below 0.001; DragMath guards the same floor.
        val (x, y) = DragMath.draggedPosition(0f, 0f, 1f, -1f, 0f)
        assertEquals(1000f, x, 0.001f)
        assertEquals(-1000f, y, 0.001f)
    }

    /** One finger path, as canvas-space deltas per pointer event. */
    private val events = listOf(
        12.5f to -3.25f, -4f to 8.75f, 20.25f to 14.5f, -9.5f to -6f, 3.75f to 2.25f
    )

    /**
     * A canvas-space delta [f] expressed in the layer's own local space: the
     * inverse of the graphicsLayer transform (rotation about the pivot, then the
     * effective scale x flip). This is the space the old handlers received their
     * drag amounts in.
     */
    private fun toLocal(
        f: Pair<Float, Float>, rotDeg: Float, scalePercent: Float, df: Float, fx: Float, fy: Float
    ): Pair<Float, Float> {
        val rad = Math.toRadians(rotDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // R(-theta) * F
        val rx = f.first * c + f.second * s
        val ry = -f.first * s + f.second * c
        val sc = (scalePercent / 100f) * df
        return (rx / (sc * fx)) to (ry / (sc * fy))
    }

    /** The build-224 TEXT handler's accumulation (TextLayerView), replayed verbatim. */
    private fun oldTextDrag(
        startX: Float, startY: Float, rotDeg: Float, scalePercent: Float, df: Float, fx: Float, fy: Float
    ): Pair<Float, Float> {
        val rad = Math.toRadians(rotDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val scale = scalePercent / 100f // stored moves are before the depth factor
        var dragX = startX
        var dragY = startY
        for (f in events) {
            val amount = toLocal(f, rotDeg, scalePercent, df, fx, fy)
            val ax = amount.first * fx
            val ay = amount.second * fy
            dragX += (ax * c - ay * s) * scale
            dragY += (ax * s + ay * c) * scale
        }
        return dragX to dragY
    }

    /** The build-224 SHAPE handler's accumulation (CanvasArea), replayed verbatim. */
    private fun oldShapeDrag(
        startX: Float, startY: Float, rotDeg: Float, scalePercent: Float, df: Float, fx: Float, fy: Float
    ): Pair<Float, Float> {
        val rad = Math.toRadians(rotDeg.toDouble())
        val c = cos(rad).toFloat()
        val sn = sin(rad).toFloat()
        val sc = scalePercent / 100f // stored moves are before the depth factor
        val sx = sc * fx
        val sy = sc * fy
        var dragX = startX
        var dragY = startY
        for (f in events) {
            val amount = toLocal(f, rotDeg, scalePercent, df, fx, fy)
            dragX += amount.first * sx * c - amount.second * sy * sn
            dragY += amount.first * sx * sn + amount.second * sy * c
        }
        return dragX to dragY
    }

    @Test
    fun equivalence_oldHandlersLandOnDragMath() {
        val combos = listOf(
            // rotationDeg, scalePercent, depthFactor, flipX, flipY
            Combo(0f, 100f, 1f, 1f, 1f),
            Combo(35f, 150f, 1.6f, -1f, 1f),
            Combo(-80f, 40f, 0.7f, 1f, -1f),
            Combo(170f, 220f, 2.5f, -1f, -1f),
            Combo(90f, 75f, 1f, 1f, -1f)
        )
        val totalX = events.sumOf { it.first.toDouble() }.toFloat()
        val totalY = events.sumOf { it.second.toDouble() }.toFloat()
        for (cb in combos) {
            val want = DragMath.draggedPosition(15f, -40f, totalX, totalY, cb.df)
            val viaText = oldTextDrag(15f, -40f, cb.rot, cb.scale, cb.df, cb.fx, cb.fy)
            val viaShape = oldShapeDrag(15f, -40f, cb.rot, cb.scale, cb.df, cb.fx, cb.fy)
            assertEquals("text x $cb", want.first, viaText.first, 0.01f)
            assertEquals("text y $cb", want.second, viaText.second, 0.01f)
            assertEquals("shape x $cb", want.first, viaShape.first, 0.01f)
            assertEquals("shape y $cb", want.second, viaShape.second, 0.01f)
        }
    }

    private data class Combo(val rot: Float, val scale: Float, val df: Float, val fx: Float, val fy: Float)
}
