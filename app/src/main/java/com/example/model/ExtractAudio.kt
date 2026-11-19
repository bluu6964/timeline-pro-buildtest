package com.example.model

/**
 * Extract audio (build 203 — Sarowar's order 2026-11-15 ~10:37 + ~10:42: build
 * 203 carries TWO updates, detach from an existing video layer and Add Audio →
 * "Extract from video"). Both build an ordinary AUDIO layer whose [TimelineClip.uri]
 * points at a VIDEO container: no file is copied or transcoded, because the shared
 * C++ audio path already decodes the audio stream out of a video container — that
 * is how a video layer's own sound has always played. The sound pick
 * (ExportPlanNative.pickAudio, shared by preview and export) is type-based
 * (AUDIO and VIDEO clips alike, keyed on uri / visibility / mute — never on the
 * mime type), so an audio layer carrying a video uri is picked, mixed, ducked,
 * gated and waveform-drawn exactly like an imported audio file (the waveform
 * cache is keyed by uri: the detached layer literally shares its source's
 * soundtrack waveform). Project save/load needs NO new field: the layer is a
 * plain [TimelineClip] in the snapshot's tracks, and every field used here
 * already serializes.
 *
 * The DETACH law is MIX-PRESERVING: after the operation the project sounds
 * exactly as before. The new layer takes the source's sound state wholesale
 * (volume, fades, mute, visibility, speed — constant and the SPEED keyframe
 * ramp alike), and the video layer's own volume is set to 0 by the caller
 * through the normal volume field, so the sound exists exactly once, on the
 * new layer, where it can be edited on its own. Everything picture-shaped
 * (transform, effects, masks, crop, ducking / NR settings, chain / group /
 * parent / controller links) stays behind on the video layer: the audio layer
 * is a fresh standalone layer, placed by the caller in its own lane directly
 * below the source's lane ([LaneLayout.placeBelow]).
 */
object ExtractAudio {

    /** Why an extract request was refused (the screen words each one for the snackbar). */
    enum class Fail { NOT_VIDEO, LOCKED, NO_AUDIO }

    sealed interface Outcome {
        data class Done(val audioId: String) : Outcome
        data class Refused(val fail: Fail) : Outcome
    }

    /**
     * The detached audio layer for the video layer [source]: same file, same
     * timeline window (start, duration, source trim), same sound state. Only
     * the SPEED keyframes ride along in storage (inert since build 237 removed
     * ramps — the layer plays at its constant base speed); every other
     * keyframed property is picture-shaped and meaningless on a sound layer.
     */
    fun detachedLayer(source: TimelineClip, newId: String): TimelineClip = TimelineClip(
        id = newId,
        name = "${source.name} (Audio)",
        type = LayerType.AUDIO,
        startMs = source.startMs,
        durationMs = source.durationMs,
        color = defaultClipColor(LayerType.AUDIO),
        volume = source.volume,
        speed = source.speed,
        isMuted = source.isMuted,
        fadeInMs = source.fadeInMs,
        fadeOutMs = source.fadeOutMs,
        isVisible = source.isVisible,
        uri = source.uri,
        mimeType = source.mimeType,
        isImage = false,
        sourceDurationMs = source.sourceDurationMs,
        trimStartMs = source.trimStartMs,
        keyframes = source.keyframes.filterKeys { it == AnimProp.SPEED }
    )

    /**
     * The audio layer for Add Audio → "Extract from video": the picked video's
     * whole sound, at [startMs] (the playhead), full source length, untrimmed.
     * The name is the file's name without its extension ([displayNameFor]).
     */
    fun importedLayer(item: ImportedMedia, startMs: Long, newId: String): TimelineClip = TimelineClip(
        id = newId,
        name = displayNameFor(item.name),
        type = LayerType.AUDIO,
        startMs = startMs.coerceAtLeast(0L),
        durationMs = item.durationMs,
        color = defaultClipColor(LayerType.AUDIO),
        uri = item.uri,
        mimeType = item.mimeType,
        isImage = false,
        sourceDurationMs = item.durationMs
    )

    /**
     * A picked file's display name without its extension ("my_video.mp4" ->
     * "my_video"). A name with no dot, a leading dot (".mp4"), or a suffix
     * that does not look like an extension (longer than 5 chars or containing
     * non-alphanumerics) is returned unchanged.
     */
    fun displayNameFor(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return fileName
        val ext = fileName.substring(dot + 1)
        if (ext.length !in 1..5 || !ext.all { it.isLetterOrDigit() }) return fileName
        return fileName.substring(0, dot)
    }
}
