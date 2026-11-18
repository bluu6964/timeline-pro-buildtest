# Build 233 — native timeline gestures (Kotlin -> C++, step 1 of the timeline / layer panel move)

The touch state machine of the native timeline (`TlGestureMachine`, Kotlin) now lives in C++:
`cpp/engine/timeline_gesture.{h,cpp}`. Kotlin keeps only a thin glue (`TlGestureGlue` in
`NativeTimelineSurface.kt`): it passes each MotionEvent to `TimelineNative.nativeTouch` and performs the
returned events through the same ViewModel callbacks as before.

## What is native now
slop / scrub maths, vertical lane scroll (the scroll position and the long-press drag offset are owned by
C++ and drawn by it: `nativeDraw(handle, playheadMs)`), long-press drag (move in time + lane reorder),
trim / fade accumulation, pinch zoom, velocity tracking and both flings (same exponential-decay curve as
Compose's `exponentialDecay`).

## What stays in Kotlin (needs Android)
long-press timer (`Handler`), fling frame clock (`Choreographer`), clip index -> id mapping, the ViewModel calls.

## Tests
`cpp/tests/timeline_gesture_selftest.cpp` (68 checks, ASan + UBSan clean on the host):
`g++ -std=c++17 -O2 -I../engine timeline_gesture_selftest.cpp ../engine/timeline_gesture.cpp ../engine/timeline_render.cpp -o /tmp/tg && /tmp/tg`

## NOT run here
Kotlin (no compiler here) and the real NDK build: build with GitHub Actions, then test on the phone.

## Check on the phone (small differences from build 232 are possible)
1. Fling feel (scrub + vertical): velocity is a native least-squares fit that mimics Android's VelocityTracker,
   not the platform class itself.
2. Pinch: zoom now accumulates inside C++ from the pinch start, so it no longer waits for each recomposition.
3. Everything else should feel identical: tap, scrub, scroll, long-press drag, lane reorder, trim, fade, multi-select.
