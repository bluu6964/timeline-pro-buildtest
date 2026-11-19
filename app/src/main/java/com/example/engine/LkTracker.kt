package com.example.engine

/** Grey picture, values 0..255, row-major. Pure data: no Android classes, so it is unit-testable. */
class GrayImage(val w: Int, val h: Int, val px: FloatArray) {
    init {
        require(w > 0 && h > 0 && px.size == w * h) { "bad grey picture size" }
    }
}

/**
 * Follows a round region of a video (the circle the user puts on an object).
 *
 * How it works (in C++, cpp/engine/lk_tracker.cpp — build 234 made the native
 * tracker the only implementation; the Kotlin pyramidal-LK port was removed):
 *  1. Corners with the strongest texture inside the circle are picked (Shi-Tomasi).
 *  2. Every new frame, each corner is followed by pyramidal Lucas-Kanade optical flow.
 *  3. A forward-backward check throws away corners that were not followed reliably.
 *  4. Deterministic RANSAC fits translation, rotation and uniform scale, rejecting background/outlier features.
 *  5. When too few corners are left, new ones are picked inside the transformed circle.
 *
 * All coordinates are in picture pixels. Call [start] with the first frame, then [step] with every
 * following frame; [cx] / [cy] are the circle centre after the latest call.
 */
class CircleTracker(private val maxPoints: Int = 60) : AutoCloseable {

    var cx = 0f
        private set
    var cy = 0f
        private set
    var radius = 0f
        private set

    /** Turn of the tracked object since [start], in degrees (clockwise in picture pixels, y down). */
    var rotationDeg = 0f
        private set

    /** Zoom of the tracked object since [start] (1 = first frame). */
    var scaleTotal = 1f
        private set

    /** Number of corners followed right now. */
    val pointCount: Int get() = n

    private var n = 0

    private var nativeHandle: Long = LkTrackerNative.nativeCreate(maxPoints).also {
        check(it != 0L) { "native LK tracker could not be created" }
    }
    private val nativeState = FloatArray(6)

    override fun close() {
        val h = nativeHandle
        if (h != 0L) {
            nativeHandle = 0L
            LkTrackerNative.nativeDestroy(h)
        }
    }

    @Suppress("unused")
    protected fun finalize() {
        close()
    }

    private fun applyNativeState() {
        cx = nativeState[0]
        cy = nativeState[1]
        radius = nativeState[2]
        rotationDeg = nativeState[3]
        scaleTotal = nativeState[4]
        n = nativeState[5].toInt()
    }

    private fun handle(): Long {
        check(nativeHandle != 0L) { "CircleTracker is closed" }
        return nativeHandle
    }

    /** The corners followed right now, as x, y, x, y ... (picture pixels). For the live display. */
    fun copyPoints(): FloatArray {
        val out = FloatArray(maxPoints * 2)
        val count = LkTrackerNative.nativePoints(handle(), out)
        return out.copyOf(count * 2)
    }

    /**
     * Puts the circle on the first frame and picks the corners. Returns false when the circle holds
     * too little texture to follow (plain wall, sky...): the caller should tell the user.
     */
    fun start(frame: GrayImage, cx: Float, cy: Float, radius: Float): Boolean {
        val ok = LkTrackerNative.nativeStart(handle(), frame.px, frame.w, frame.h, cx, cy, radius, nativeState)
        applyNativeState()
        return ok
    }

    /**
     * Follows the circle into [frame] (same size as the first one). Returns false when the object
     * is lost (too few corners left, or the circle left the picture); the centre is then not moved.
     */
    fun step(frame: GrayImage): Boolean {
        val ok = LkTrackerNative.nativeStep(handle(), frame.px, frame.w, frame.h, nativeState)
        applyNativeState()
        return ok
    }

    companion object {
        /** Least corners needed to trust the motion. */
        const val MIN_POINTS = 4

        /** Frames in a row that may fail before the object is declared lost. */
        const val MAX_MISSES = 3
    }
}
