package com.example

import com.example.engine.fillSourceMs
import com.example.engine.fillVideoPose
import com.example.engine.mediaFillClipOf
import com.example.engine.silhouetteCopy
import com.example.engine.strokeOnlyCopy
import com.example.model.FILL_IMAGE
import com.example.model.FILL_NONE
import com.example.model.FILL_SOLID
import com.example.model.FILL_VIDEO
import com.example.model.IMG_FILL
import com.example.model.IMG_FIT
import com.example.model.IMG_STRETCH
import com.example.model.LayerType
import com.example.model.TimelineClip
import com.example.model.depthX
import com.example.model.depthY
import com.example.model.shapeDrawScaleX
import com.example.model.shapeDrawScaleY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * Video fills inside shapes (build 104): a shape filled with a playing video. These tests pin
 * the native half of the feature: the routing decision the render engines make (live composite
 * vs poster raster), the silhouette / outline split, the fill's source-time mapping, and the
 * geometry that maps the video frame into the shape's box (cover / fit / stretch, flips,
 * rotation, offset). Until build 236 this file also pinned the Alight XML importer's fill
 * mapping and the import-time media copy on verbatim corpus excerpts; the importer is gone,
 * and with it those tests — the fill model they produced is authored natively here.
 */
class FillVideoFillTest {

    // ---- routing + the silhouette / outline split ------------------------------------------------

    private fun shape(fillMode: Int, uri: String? = "content://v/1") = TimelineClip(
        id = "s1", name = "s", type = LayerType.SHAPE, startMs = 0L, durationMs = 1000L, color = 0L,
        fillMode = fillMode, fillVideoUri = if (fillMode == FILL_VIDEO) uri else null,
        fillImageUri = if (fillMode == FILL_IMAGE) uri else null,
        shapePattern = "RECT", strokeWidth = 3f, textColor = 0xFF112233L
    )

    @Test
    fun routingSendsOnlySingleVideoFilledShapesToTheLivePath() {
        val v = shape(FILL_VIDEO)
        assertEquals(v, mediaFillClipOf(listOf(v)))
        assertNull(mediaFillClipOf(listOf(shape(FILL_IMAGE))))
        assertNull(mediaFillClipOf(listOf(v, shape(FILL_SOLID))))
        assertNull(mediaFillClipOf(listOf(shape(FILL_VIDEO, uri = null))))
        assertNull(mediaFillClipOf(emptyList()))
    }

    @Test
    fun silhouetteAndOutlineCopiesSplitTheShape() {
        val v = shape(FILL_VIDEO)
        val sil = v.silhouetteCopy()
        assertEquals(FILL_SOLID, sil.fillMode)
        assertEquals(0xFFFFFFFFL, sil.textColor)
        assertEquals(0f, sil.strokeWidth, 0f)
        assertEquals(v.fillVideoUri, sil.fillVideoUri)
        assertEquals(v.shapePattern, sil.shapePattern)
        val out = v.strokeOnlyCopy()
        assertEquals(FILL_NONE, out.fillMode)
        assertEquals(3f, out.strokeWidth, 0f)          // the outline keeps its stroke
        assertEquals(v.textColor, out.textColor)
    }

    // ---- source time ------------------------------------------------------------------------------

    @Test
    fun fillSourceTimeFollowsTheWindowAndClampsToTheFile() {
        val c = shape(FILL_VIDEO).copy(startMs = 1000L, trimStartMs = 1768L)
        assertEquals(1768L, fillSourceMs(c, 1000L, 0L))            // layer start = window start
        assertEquals(2634L, fillSourceMs(c, 1866L, 0L))            // advances 1:1 with the timeline
        assertEquals(4999L, fillSourceMs(c, 100000L, 5000L))       // clamped into the file
        assertEquals(0L, fillSourceMs(c.copy(trimStartMs = 0L), 0L, 5000L))
        val stretched = c.copy(speed = 2f)
        assertEquals(2768L, fillSourceMs(stretched, 1500L, 0L))    // a stretch fill runs at its speed
    }

    // ---- placement geometry -------------------------------------------------------------------------

    /** A shape whose box is 260 x 130 px on a 1920 x 1080 frame (density 1, k 1, scale 200% x 100%). */
    private fun wideShape(mode: Int) = TimelineClip(
        id = "g", name = "g", type = LayerType.SHAPE, startMs = 0L, durationMs = 1000L, color = 0L,
        fillMode = FILL_VIDEO, fillVideoUri = "content://v/1", fillImageMode = mode,
        shapePattern = "RECT", scalePercent = 200f, shapeScaleX = 1f, shapeScaleY = 0.5f
    )

    private fun drawnSize(clip: TimelineClip, frameW: Int, frameH: Int, cw: Int, ch: Int): Pair<Float, Float> {
        val pose = fillVideoPose(clip, frameW, frameH, 1f, 1f, cw, ch)!!
        val fit = min(frameW.toFloat() / cw, frameH.toFloat() / ch)
        return (cw * fit * pose.scaleX) to (ch * fit * pose.scaleY)
    }

    @Test
    fun coverFillCropsTheOverflow() {
        val c = wideShape(IMG_FILL)
        // The shape's own box, from the model's draw scales (the painters' box).
        assertEquals(260f, 2f * 65f * c.shapeDrawScaleX, 0.01f)
        assertEquals(130f, 2f * 65f * c.shapeDrawScaleY, 0.01f)
        val (dw, dh) = drawnSize(c, 1920, 1080, 640, 360)
        assertEquals(260f, dw, 0.01f)          // the box width is covered exactly
        assertTrue(dh > 130f)                  // the height overflows and is centre-cropped
        assertEquals(146.25f, dh, 0.01f)
    }

    @Test
    fun fitFillLetterboxesInsideTheBox() {
        val (dw, dh) = drawnSize(wideShape(IMG_FIT), 1920, 1080, 640, 360)
        assertEquals(130f, dh, 0.01f)          // the box height is met exactly
        assertTrue(dw < 260f)                  // the width leaves bars at the sides
        assertEquals(231.11f, dw, 0.01f)
    }

    @Test
    fun stretchFillDistortsToTheBoxExactly() {
        val (dw, dh) = drawnSize(wideShape(IMG_STRETCH), 1920, 1080, 640, 360)
        assertEquals(260f, dw, 0.01f)
        assertEquals(130f, dh, 0.01f)
    }

    @Test
    fun poseCarriesOffsetRotationAndFlips() {
        val c = wideShape(IMG_FILL).copy(transformX = 40f, transformY = -20f, rotationDeg = 30f, isFlippedH = true)
        val pose = fillVideoPose(c, 1920, 1080, 2f, 1f, 640, 360)!!
        assertEquals(c.depthX * 2f, pose.offsetX, 0.01f)
        assertEquals(c.depthY * 2f, pose.offsetY, 0.01f)
        assertEquals(30f, pose.rotationDeg, 0.001f)
        assertTrue(pose.scaleX < 0f)           // the flip folds into the placement sign
        assertTrue(pose.scaleY > 0f)
        // No frame size yet = no pose (the caller falls back to the poster).
        assertNull(fillVideoPose(c, 1920, 1080, 1f, 1f, 0, 360))
    }
}
