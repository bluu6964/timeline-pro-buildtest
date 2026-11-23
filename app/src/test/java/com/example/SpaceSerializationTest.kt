package com.example

import com.example.engine.ProjectStore
import com.example.model.LaneLayout
import com.example.model.LayerType
import com.example.model.ProjectSnapshot
import com.example.model.TimelineClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 262 — serialization of the 3D Space: a project with a Space and its
 * children saves and loads IDENTICALLY (spaceId, grid settings, extrusion,
 * the space camera fields), and a project file written before build 262
 * (no space keys at all) still loads, with the space fields at their
 * defaults. CI-only: the kotlinc harness stubs out kotlinx.serialization.
 */
class SpaceSerializationTest {

    private fun clip(id: String, type: LayerType) =
        TimelineClip(id = id, name = id, type = type, startMs = 0, durationMs = 5000, color = 0)

    @Test
    fun spaceProjectRoundTripsIdentically() {
        val space = clip("sp", LayerType.SPACE3D).copy(
            spaceGridOn = false, spaceGridSize = 1800f, spaceExtrude = 140f,
            rotation3dX = 24f, rotation3dY = -18f, transformZ = 120f, scalePercent = 130f
        )
        val text = clip("t1", LayerType.TEXT).copy(spaceId = "sp", spaceExtrudeSelf = 45f, transformZ = -60f)
        val video = clip("v1", LayerType.VIDEO).copy(spaceId = "sp", uri = "file:///tmp/a.mp4")
        val plain = clip("p1", LayerType.SHAPE)
        val snapshot = ProjectSnapshot(
            tracks = listOf(space, text, video, plain).map { LaneLayout.newLane(listOf(it)) }
        )
        val back = ProjectStore.decode(ProjectStore.encode(snapshot))
        assertEquals(snapshot, back)
        val backSpace = back.tracks.flatMap { it.clips }.first { it.id == "sp" }
        assertEquals(LayerType.SPACE3D, backSpace.type)
        assertEquals(140f, backSpace.spaceExtrude, 0.001f)
        val backText = back.tracks.flatMap { it.clips }.first { it.id == "t1" }
        assertEquals("sp", backText.spaceId)
        assertEquals(45f, backText.spaceExtrudeSelf, 0.001f)
    }

    @Test
    fun preSpaceProjectFileStillLoads() {
        // A clip JSON exactly as a pre-262 build wrote it: no spaceId /
        // spaceGridOn / spaceGridSize / spaceExtrude / spaceExtrudeSelf keys.
        val oldJson = """
            {"tracks":[{"id":"lane1","name":"Lane 1","clips":[
              {"id":"c1","name":"Title","type":"TEXT","startMs":0,"durationMs":4000,"color":123}
            ]}]}
        """.trimIndent()
        val back = ProjectStore.decode(oldJson)
        val c = back.tracks.single().clips.single()
        assertNull(c.spaceId)
        assertTrue(c.spaceGridOn)
        assertEquals(2400f, c.spaceGridSize, 0.001f)
        assertEquals(80f, c.spaceExtrude, 0.001f)
        assertEquals(-1f, c.spaceExtrudeSelf, 0.001f)
    }
}
