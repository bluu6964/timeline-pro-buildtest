package com.example

import com.example.engine.ProjectStore
import com.example.model.LaneLayout
import com.example.model.LayerType
import com.example.model.ProjectSnapshot
import com.example.model.TEXT_BG_NONE
import com.example.model.TEXT_BG_RECTANGLE
import com.example.model.TEXT_BG_ROUNDED
import com.example.model.TEXT_BG_SPEECH
import com.example.model.TEXT_BG_SPEECH_SQ
import com.example.model.TEXT_BG_THOUGHT
import com.example.model.TextTemplates
import com.example.model.TimelineClip
import com.example.model.defaultTextLook
import com.example.model.textBgCornerApplies
import com.example.model.textBgShapeDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 268 — the Bubbles background shape: the additive serialized
 * field (old projects load as Rectangle = the pre-268 box), the
 * shape → catalog-geometry mapping, the Corner applicability rule,
 * and the Templates bundles (data over existing machinery only).
 * CI-only where serialization is involved: the kotlinc harness
 * stubs out kotlinx.serialization.
 */
class TextBgShapeTest {

    private fun textClip(id: String = "t1") =
        TimelineClip(id = id, name = id, type = LayerType.TEXT, startMs = 0, durationMs = 4000, color = 0)

    @Test
    fun bgShapeRoundTripsThroughProjectJson() {
        for (shape in listOf(TEXT_BG_NONE, TEXT_BG_RECTANGLE, TEXT_BG_ROUNDED,
                             TEXT_BG_SPEECH, TEXT_BG_SPEECH_SQ, TEXT_BG_THOUGHT)) {
            val clip = textClip().copy(bgShape = shape, bgOpacity = 90f, bgColor = 0xFF112233)
            val snapshot = ProjectSnapshot(tracks = listOf(LaneLayout.newLane(listOf(clip))))
            val back = ProjectStore.decode(ProjectStore.encode(snapshot))
            assertEquals(snapshot, back)
            assertEquals(shape, back.tracks.single().clips.single().bgShape)
        }
    }

    @Test
    fun preBubblesProjectFileLoadsAsRectangle() {
        // A clip JSON exactly as a pre-268 build wrote it: bg fields
        // present, no bgShape key at all.
        val oldJson = """
            {"tracks":[{"id":"lane1","name":"Lane 1","clips":[
              {"id":"c1","name":"Title","type":"TEXT","startMs":0,"durationMs":4000,"color":123,
               "bgOpacity":80.0,"bgColor":4278190080,"bgCorner":20.0,"bgPadding":30.0}
            ]}]}
        """.trimIndent()
        val back = ProjectStore.decode(oldJson)
        val c = back.tracks.single().clips.single()
        assertEquals(TEXT_BG_RECTANGLE, c.bgShape)
        assertEquals(80f, c.bgOpacity, 0.001f)
    }

    @Test
    fun bubbleShapesResolveToCatalogGeometry() {
        // The three bubble kinds draw real ShapeCatalog outlines.
        assertEquals("SH_SPEECH", textBgShapeDef(TEXT_BG_SPEECH)?.pattern)
        assertEquals("SH_SPEECH_SQ", textBgShapeDef(TEXT_BG_SPEECH_SQ)?.pattern)
        assertEquals("SH_THOUGHT", textBgShapeDef(TEXT_BG_THOUGHT)?.pattern)
        for (shape in listOf(TEXT_BG_SPEECH, TEXT_BG_SPEECH_SQ, TEXT_BG_THOUGHT)) {
            val def = textBgShapeDef(shape)
            assertNotNull(def)
            assertTrue("outline has points", def!!.totalPoints >= 3)
            // Points live in the unit basis the painters map from
            // (a fit offset may overshoot a hair — the painters just
            // map the basis, so a small overshoot is harmless).
            for (i in def.flat.indices) {
                assertTrue("point near the unit basis", def.flat[i] in -1.05f..1.05f)
            }
        }
        // The box shapes and None have no polygon.
        assertNull(textBgShapeDef(TEXT_BG_NONE))
        assertNull(textBgShapeDef(TEXT_BG_RECTANGLE))
        assertNull(textBgShapeDef(TEXT_BG_ROUNDED))
    }

    @Test
    fun cornerAppliesOnlyToTheBoxShapes() {
        assertTrue(textBgCornerApplies(TEXT_BG_RECTANGLE))
        assertTrue(textBgCornerApplies(TEXT_BG_ROUNDED))
        assertFalse(textBgCornerApplies(TEXT_BG_NONE))
        assertFalse(textBgCornerApplies(TEXT_BG_SPEECH))
        assertFalse(textBgCornerApplies(TEXT_BG_SPEECH_SQ))
        assertFalse(textBgCornerApplies(TEXT_BG_THOUGHT))
    }

    @Test
    fun templatesAreBundlesOverExistingFields() {
        assertTrue("starter set has ~12 templates", TextTemplates.size in 10..14)
        assertEquals(
            "template names are unique",
            TextTemplates.size, TextTemplates.map { it.name }.toSet().size
        )
        val base = textClip().copy(text = "Hello", fontFamily = "Roboto")
        for (t in TextTemplates) {
            val out = t.applyTo(base)
            // The bundle touches only look fields: identity and text survive.
            assertEquals(base.id, out.id)
            assertEquals("Hello", out.text)
            assertEquals(t.fontFamily, out.fontFamily)
            assertEquals(t.bgShape, out.bgShape)
            // A shaped template turns its background on; a shapeless
            // one turns it off — never a shape with 0 opacity.
            if (t.bgShape == TEXT_BG_NONE) assertEquals(0f, out.bgOpacity, 0.001f)
            else assertEquals(100f, out.bgOpacity, 0.001f)
            // Every template shape is one the painters can draw.
            assertTrue(t.bgShape in TEXT_BG_NONE..TEXT_BG_THOUGHT)
        }
    }

    @Test
    fun defaultLookRestoresThePlainDefaults() {
        val styled = TextTemplates.first { it.bgShape == TEXT_BG_SPEECH }.applyTo(textClip())
        val back = defaultTextLook(styled)
        val d = textClip()
        assertEquals(d.fontFamily, back.fontFamily)
        assertEquals(d.textColor, back.textColor)
        assertEquals(d.bgShape, back.bgShape)
        assertEquals(d.bgOpacity, back.bgOpacity, 0.001f)
        assertEquals(styled.text, back.text)
    }
}
