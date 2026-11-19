package com.example

import com.example.engine.MissingMedia
import com.example.model.LayerType
import com.example.model.ProjectSnapshot
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Media substitution (build 111, [MissingMedia]): the pure collection / re-pointing half.
 *
 * The fixture is a snapshot built directly in the project model, carrying the same three
 * foreign references as the real "ST Intro Template 1" case the feature was made for: two
 * media shapes whose pictures lived on the exporting phone (`send-message.png` .../53288 in
 * the pill, `images.png` .../1000033464 in the "Logo Here" group) and the audio layer
 * (.../53292). Until build 236 the fixture was produced by parsing that project's Alight
 * Motion XML; the XML importer is gone, and [MissingMedia] only ever reads the snapshot,
 * so the snapshot is authored natively now — the references, names and slots are unchanged.
 */
class MissingMediaTest {

    private val ownAuthority = "com.example.app.fileprovider"
    private val imgPill = "content://media/external/images/media/53288"
    private val imgLogo = "content://media/external/images/media/1000033464"
    private val audioSrc = "content://media/external/audio/media/53292"

    /** The ST Intro references as a native snapshot: two picture fills + one audio layer file. */
    private fun stIntroSnapshot(): ProjectSnapshot {
        fun clip(id: String, name: String, type: LayerType, uri: String? = null, fillI: String? = null) =
            TimelineClip(
                id = id, name = name, type = type, startMs = 0, durationMs = 1000, color = 0,
                uri = uri, fillImageUri = fillI
            )
        return ProjectSnapshot(
            tracks = listOf(
                TimelineTrack(
                    id = "t", name = "T",
                    clips = listOf(
                        clip("pill", "send-message.png", LayerType.SHAPE, fillI = imgPill),
                        clip("logo", "images.png", LayerType.SHAPE, fillI = imgLogo),
                        clip("sound", "Minimal Intro SOUND.mp3", LayerType.AUDIO, uri = audioSrc)
                    )
                )
            )
        )
    }

    private fun clips(s: ProjectSnapshot) = s.tracks.flatMap { it.clips }

    @Test
    fun foreignRefsAreCollectedForSubstitution() {
        val items = MissingMedia.collect(stIntroSnapshot(), ownAuthority)
        assertEquals(3, items.size)
        // One entry per file, labelled by the layer (AM names media layers after their file).
        val bySource = items.associateBy { it.sourceUri }
        assertEquals("send-message.png", bySource[imgPill]?.label)
        assertEquals("images.png", bySource[imgLogo]?.label)
        assertEquals("Minimal Intro SOUND.mp3", bySource[audioSrc]?.label)
        assertEquals(MissingMedia.Kind.IMAGE, bySource[imgPill]?.kind)
        assertEquals(MissingMedia.Kind.IMAGE, bySource[imgLogo]?.kind)
        assertEquals(MissingMedia.Kind.AUDIO, bySource[audioSrc]?.kind)
        assertEquals("Picture fill", bySource[imgPill]?.where)
        assertEquals("Picture fill", bySource[imgLogo]?.where)
        assertEquals("Layer file", bySource[audioSrc]?.where)
        assertTrue(items.all { it.layerCount == 1 })
    }

    @Test
    fun nothingIsMissingWhenRefsAreOwnedCopies() {
        // The same three slots, but every reference is one of the app's own copied files:
        // nothing is unresolvable, so nothing is offered for substitution.
        fun clip(id: String, type: LayerType, uri: String? = null, fillI: String? = null) =
            TimelineClip(
                id = id, name = id, type = type, startMs = 0, durationMs = 1000, color = 0,
                uri = uri, fillImageUri = fillI
            )
        val snap = ProjectSnapshot(
            tracks = listOf(
                TimelineTrack(
                    id = "t", name = "T",
                    clips = listOf(
                        clip("pill", LayerType.SHAPE, fillI = "content://$ownAuthority/imports/copy_1.bin"),
                        clip("logo", LayerType.SHAPE, fillI = "content://$ownAuthority/imports/copy_2.bin"),
                        clip("sound", LayerType.AUDIO, uri = "content://$ownAuthority/imports/copy_3.bin")
                    )
                )
            )
        )
        assertTrue(MissingMedia.collect(snap, ownAuthority).isEmpty())
    }

    @Test
    fun substitutingTheLogoRepointsItsLayerAndClearsTheItem() {
        val snap = stIntroSnapshot()
        val mine = "content://$ownAuthority/imports/my-logo.png"

        val after = MissingMedia.substitute(snap, imgLogo, mine)
        val logo = clips(after).single { it.fillImageUri == mine }
        assertEquals("images.png", logo.name)
        // The other two missing files are untouched, and only they remain on the list.
        val remaining = MissingMedia.collect(after, ownAuthority)
        assertEquals(setOf(imgPill, audioSrc), remaining.map { it.sourceUri }.toSet())
    }

    @Test
    fun embeddedAndTokenRefsAreCollectedOwnedAndLocalRefsAreNot() {
        fun clip(id: String, type: LayerType, uri: String? = null, fillI: String? = null, fillV: String? = null) =
            TimelineClip(
                id = id, name = id, type = type, startMs = 0, durationMs = 1000, color = 0,
                uri = uri, fillImageUri = fillI, fillVideoUri = fillV
            )
        val snap = ProjectSnapshot(
            tracks = listOf(
                TimelineTrack(
                    id = "t", name = "T",
                    clips = listOf(
                        clip("vidFill", LayerType.SHAPE, fillV = "am:5A2183027F5DE730E692BBF35E5CB75D328D856A.mp4"),
                        clip("imgFill", LayerType.SHAPE, fillI = "am:F15C58BAAC544334BFB9D0B3ABC9790C526200EF.png"),
                        clip("token", LayerType.VIDEO, uri = "am-asset:0/video.mp4"),
                        clip("own", LayerType.VIDEO, uri = "content://$ownAuthority/imports/already.mp4"),
                        clip("file", LayerType.SHAPE, fillI = "file:///data/local/x.png"),
                        clip("foreignImgLayer", LayerType.VIDEO, uri = imgPill).copy(isImage = true)
                    )
                )
            )
        )
        val items = MissingMedia.collect(snap, ownAuthority)
        assertEquals(listOf("vidFill", "imgFill", "token", "foreignImgLayer"), items.map { it.label })
        assertEquals(
            listOf(MissingMedia.Kind.VIDEO, MissingMedia.Kind.IMAGE, MissingMedia.Kind.VIDEO, MissingMedia.Kind.IMAGE),
            items.map { it.kind }
        )
        assertEquals(listOf("Video fill", "Picture fill", "Layer file", "Layer file"), items.map { it.where })
    }

    @Test
    fun aSharedFileIsOneItemNamingEveryLayer() {
        fun shape(id: String) = TimelineClip(
            id = id, name = id, type = LayerType.SHAPE, startMs = 0, durationMs = 1000, color = 0,
            fillImageUri = imgPill
        )
        val video = TimelineClip(
            id = "v", name = "v", type = LayerType.SHAPE, startMs = 0, durationMs = 1000, color = 0,
            fillVideoUri = imgPill
        )
        val snap = ProjectSnapshot(
            tracks = listOf(TimelineTrack(id = "t", name = "T", clips = listOf(shape("a"), shape("b"), video)))
        )
        val items = MissingMedia.collect(snap, ownAuthority)
        assertEquals(1, items.size)
        assertEquals(listOf("a", "b", "v"), items[0].clipIds)
        assertEquals(3, items[0].layerCount)

        // One substitution re-points all three slots at once.
        val mine = "content://$ownAuthority/imports/shared.png"
        val after = MissingMedia.substitute(snap, imgPill, mine)
        assertTrue(clips(after).all { it.fillImageUri == mine || it.fillVideoUri == mine })
        assertTrue(MissingMedia.collect(after, ownAuthority).isEmpty())
    }

    @Test
    fun substituteOfAnUnknownSourceChangesNothing() {
        val snap = stIntroSnapshot()
        val after = MissingMedia.substitute(snap, "content://media/external/images/media/999", "content://x/y.png")
        assertSame(snap, after)
        assertSame(snap, MissingMedia.substitute(snap, imgPill, imgPill))
    }
}
