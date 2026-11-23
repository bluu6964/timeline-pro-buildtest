package com.example.model

/**
 * Build 264 — the native timeline's snapshot CHANGE KEY: a structural
 * fingerprint of everything the timeline snapshot build consumes, and
 * nothing else. The engine rebuilds its C++ snapshot only when this key
 * changes (or a rebuild is forced, e.g. a filmstrip strip landing).
 *
 * Why: the snapshot build is expensive (per-clip label measurement, one
 * JNI call per clip, a full copy of every waveform peaks array), and it
 * used to run on EVERY recomposition of the timeline surface — during
 * playback the playhead state emits ~60 times a second and the editor
 * screen recomposes with it, so the timeline rebuilt its (playhead-
 * independent) snapshot ~60 times a second. The playhead is NOT an
 * input here: the native draw reads it live per frame.
 *
 * Completeness rule: the key must cover EVERY field the snapshot build
 * reads, or the timeline would go stale. For tracks/clips it mixes the
 * data classes' own structural hashCodes, which cover every constructor
 * property — a superset of what the build reads (labels, colours, flags,
 * keyframe times, chain/group links, lane heights via clip types). A
 * change the build cannot see (e.g. an effect parameter, beyond the
 * effects list's own hash) may still change the key and cost one
 * rebuild; the reverse must never happen.
 *
 * Waveforms: the peaks arrays are write-once analysis results published
 * as new map entries, never mutated in place. The key mixes each entry's
 * uri, array identity, length and a strided content sample, combined
 * order-independently — a replaced array (a finished analysis) changes
 * the key; the same published arrays never do.
 */
object TimelineSnapshotKey {

    fun compute(
        tracks: List<TimelineTrack>,
        selectedClipId: String?,
        multiSelectIds: Set<String>,
        waveforms: Map<String, FloatArray>,
        totalMs: Long,
        beatMs: List<Long>,
        rulerKeysMs: List<Long>,
        exportInMs: Long?,
        exportOutMs: Long?,
        pxPerMs: Float,
        comfort: Boolean,
        bottomPadPx: Float
    ): Long {
        var h = SEED
        h = mix(h, tracks.size.toLong())
        for (track in tracks) {
            h = mix(h, track.id.hashCode().toLong())
            h = mix(h, track.clips.size.toLong())
            for (clip in track.clips) {
                h = mix(h, clip.hashCode().toLong())
            }
        }
        h = mix(h, (selectedClipId?.hashCode() ?: 0).toLong())
        h = mix(h, multiSelectIds.size.toLong())
        h = mix(h, multiSelectIds.hashCode().toLong())
        // Order-independent waveform mix (map iteration order must not matter).
        var w = 0L
        for ((uri, peaks) in waveforms) {
            var e = mix(SEED, uri.hashCode().toLong())
            e = mix(e, System.identityHashCode(peaks).toLong())
            e = mix(e, peaks.size.toLong())
            if (peaks.isNotEmpty()) {
                e = mix(e, peaks[0].toRawBits().toLong())
                e = mix(e, peaks[peaks.size / 2].toRawBits().toLong())
                e = mix(e, peaks[peaks.size - 1].toRawBits().toLong())
            }
            w += e
        }
        h = mix(h, waveforms.size.toLong())
        h = mix(h, w)
        h = mix(h, totalMs)
        h = mix(h, beatMs.hashCode().toLong())
        h = mix(h, rulerKeysMs.hashCode().toLong())
        h = mix(h, exportInMs ?: NONE)
        h = mix(h, exportOutMs ?: NONE)
        h = mix(h, pxPerMs.toRawBits().toLong())
        h = mix(h, if (comfort) 1L else 0L)
        h = mix(h, bottomPadPx.toRawBits().toLong())
        return h
    }

    private fun mix(h: Long, v: Long): Long = (h xor v) * PRIME

    private const val SEED = 0x243F6A8885A308D3L
    private const val PRIME = 0x100000001B3L
    private const val NONE = Long.MIN_VALUE
}
