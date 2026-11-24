package com.example.model

/**
 * Build 273 — the decision the preview makes for one video layer on one frame:
 * which decoder-pool calls put the frame that belongs to the playhead on the
 * layer's surface. Pure (no Android), so the harness pins it; the engine's
 * synchronous path and its parallel prefetch path both follow this one law,
 * so the two can never disagree about which frame a layer shows.
 *
 * The law is exactly the rule the preview always used (extracted, not
 * changed): a layer that has shown nothing yet, or any layer while paused /
 * scrubbing, seeks (patiently while it has shown nothing — a fresh decoder
 * may have to wait for a free slot); while playing, a layer far behind or
 * ahead of its surface frame seeks, and a layer close behind steps forward
 * with next() calls, at most [MAX_STEPS] per frame.
 */
object PlaybackAdvanceLaw {

    enum class Action { SEEK_PATIENT, SEEK, NEXT }

    /** Playing and farther than this from the surface frame = seek, don't step. */
    const val MAX_CATCHUP_US = 750_000L

    /** Most next() steps one frame may take for one layer. */
    const val MAX_STEPS = 3

    fun action(shown: Boolean, playing: Boolean, targetUs: Long, ptsUs: Long, frameUs: Long): Action {
        if (!shown) return Action.SEEK_PATIENT
        if (!playing) return Action.SEEK
        val diff = targetUs - ptsUs
        return if (diff < -2 * frameUs || diff > MAX_CATCHUP_US) Action.SEEK else Action.NEXT
    }

    /** One more next() step towards [targetUs]? ([steps] = steps already taken this frame.) */
    fun shouldStep(targetUs: Long, ptsUs: Long, frameUs: Long, steps: Int): Boolean =
        targetUs - ptsUs >= frameUs && steps < MAX_STEPS
}
