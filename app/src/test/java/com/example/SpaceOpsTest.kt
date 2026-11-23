package com.example

import com.example.model.LaneLayout
import com.example.model.LayerType
import com.example.model.SpaceOps
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 262 — the 3D Space membership law ([SpaceOps]): which layers may live
 * in a space, the engines' scoping (space children leave every other level
 * and appear in exactly the space's level), and the extrusion resolution.
 */
class SpaceOpsTest {
    private fun clip(id: String, type: LayerType, spaceId: String? = null, groupId: String? = null) =
        TimelineClip(id = id, name = id, type = type, startMs = 0, durationMs = 1000, color = 0, spaceId = spaceId, groupId = groupId)

    private fun lanes(vararg c: TimelineClip): List<TimelineTrack> = c.map { LaneLayout.newLane(listOf(it)) }

    private fun ids(t: List<TimelineTrack>): List<String> = t.flatMap { it.clips }.map { it.id }

    @Test
    fun onlyVisualLayersCanBeSpaceChildren() {
        assertTrue(SpaceOps.canBeChild(clip("v", LayerType.VIDEO)))
        assertTrue(SpaceOps.canBeChild(clip("s", LayerType.SHAPE)))
        assertTrue(SpaceOps.canBeChild(clip("t", LayerType.TEXT)))
        assertTrue(SpaceOps.canBeChild(clip("o", LayerType.OVERLAY)))
        assertTrue(SpaceOps.canBeChild(clip("k", LayerType.STICKER)))
        assertFalse(SpaceOps.canBeChild(clip("a", LayerType.AUDIO)))
        assertFalse(SpaceOps.canBeChild(clip("n", LayerType.NULL)))
        assertFalse(SpaceOps.canBeChild(clip("c", LayerType.CAMERA)))
        assertFalse(SpaceOps.canBeChild(clip("h", LayerType.CHAIN)))
        assertFalse(SpaceOps.canBeChild(clip("g", LayerType.GROUP)))
        assertFalse(SpaceOps.canBeChild(clip("x", LayerType.CONTROLLER)))
        assertFalse(SpaceOps.canBeChild(clip("3", LayerType.SPACE3D)))
    }

    @Test
    fun topLevelScopeHidesSpaceChildren() {
        val space = clip("sp", LayerType.SPACE3D)
        val inside = clip("in", LayerType.TEXT, spaceId = "sp")
        val outside = clip("out", LayerType.VIDEO)
        val t = lanes(space, inside, outside)
        assertEquals(listOf("sp", "out"), ids(SpaceOps.scopeTracks(t, null)))
    }

    @Test
    fun spaceScopeHoldsExactlyItsChildren() {
        val space = clip("sp", LayerType.SPACE3D)
        val a = clip("a", LayerType.TEXT, spaceId = "sp")
        val b = clip("b", LayerType.VIDEO, spaceId = "sp")
        val other = clip("o", LayerType.SHAPE)
        val otherSpaceChild = clip("x", LayerType.SHAPE, spaceId = "gone")
        val t = lanes(space, a, b, other, otherSpaceChild)
        assertEquals(listOf("a", "b"), ids(SpaceOps.scopeTracks(t, "sp")))
        // A child pointing at a space that does not exist is an orphan: it is
        // NOT hidden from the top level (it renders as a normal layer).
        assertEquals(listOf("sp", "o", "x"), ids(SpaceOps.scopeTracks(t, null)))
    }

    @Test
    fun groupScopeAlsoHidesSpaceChildren() {
        val space = clip("sp", LayerType.SPACE3D)
        val inside = clip("in", LayerType.TEXT, spaceId = "sp")
        val t = lanes(space, inside)
        // An unknown scope id resolves to the top level in GroupOps terms;
        // the space child must still be hidden there.
        assertEquals(listOf("sp"), ids(SpaceOps.scopeTracks(t, "no-such-group")))
    }

    @Test
    fun childrenAndScopeResolution() {
        val space = clip("sp", LayerType.SPACE3D)
        val a = clip("a", LayerType.TEXT, spaceId = "sp")
        val b = clip("b", LayerType.SHAPE)
        val t = lanes(space, a, b)
        assertEquals(listOf("a"), SpaceOps.childrenOf(t, "sp").map { it.id })
        assertEquals("sp", SpaceOps.scopeOf(a, SpaceOps.spaceIdsOf(t)))
        assertNull(SpaceOps.scopeOf(b, SpaceOps.spaceIdsOf(t)))
        assertEquals(setOf("sp"), SpaceOps.spaceIdsOf(t))
    }

    @Test
    fun extrusionResolution() {
        val space = clip("sp", LayerType.SPACE3D).copy(spaceExtrude = 120f)
        val text = clip("t", LayerType.TEXT, spaceId = "sp")
        assertEquals(120f, SpaceOps.extrudeFor(space, text), 0.001f)
        // The child's own override wins; 0 turns its extrusion off.
        assertEquals(30f, SpaceOps.extrudeFor(space, text.copy(spaceExtrudeSelf = 30f)), 0.001f)
        assertEquals(0f, SpaceOps.extrudeFor(space, text.copy(spaceExtrudeSelf = 0f)), 0.001f)
        // Media children never extrude in v1, override or not.
        val video = clip("v", LayerType.VIDEO, spaceId = "sp").copy(spaceExtrudeSelf = 50f)
        assertEquals(0f, SpaceOps.extrudeFor(space, video), 0.001f)
        // A space with extrusion off gives its children none.
        assertEquals(0f, SpaceOps.extrudeFor(space.copy(spaceExtrude = 0f), text), 0.001f)
    }

    @Test
    fun wallColourIsTheFaceColourDarkened() {
        val wall = SpaceOps.wallColorArgb(0xFFCC8844)
        assertEquals(0xFFL, (wall shr 24) and 0xFF)  // alpha kept
        assertEquals((0xCC * 0.55f).toLong(), (wall shr 16) and 0xFF)
        assertEquals((0x88 * 0.55f).toLong(), (wall shr 8) and 0xFF)
        assertEquals((0x44 * 0.55f).toLong(), wall and 0xFF)
    }

    @Test
    fun faceColourSources() {
        val text = clip("t", LayerType.TEXT).copy(textColor = 0xFF112233)
        assertEquals(0xFF112233L, SpaceOps.faceColorOf(text))
        val solid = clip("s", LayerType.SHAPE).copy(fillMode = com.example.model.FILL_SOLID, textColor = 0xFF445566)
        assertEquals(0xFF445566L, SpaceOps.faceColorOf(solid))
        val grad = clip("g", LayerType.SHAPE).copy(fillMode = com.example.model.FILL_GRADIENT, gradColor1 = 0xFF778899)
        assertEquals(0xFF778899L, SpaceOps.faceColorOf(grad))
    }
}
