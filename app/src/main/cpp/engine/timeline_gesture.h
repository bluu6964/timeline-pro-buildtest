// Build 233 — GRADUAL NATIVE MIGRATION, STEP 1b: THE TIMELINE'S TOUCH LOGIC.
//
// The gesture state machine of the native timeline (it used to be Kotlin: TlGestureMachine in
// NativeTimelineSurface.kt) now lives here. It is pure C++ (no Android headers), so the whole
// thing is host-testable (tests/timeline_gesture_selftest.cpp).
//
// What it does (same rules as build 232):
//   tap bar / rail / eye / pill / chain x           -> select / toggle / open / delete events
//   horizontal swipe on ruler / lanes / free bar    -> scrub (seek events) + fling
//   mostly vertical swipe                           -> lane scroll (owned HERE) + fling
//   long-press a bar, then drag                     -> move in time (live) + lane reorder on release
//   long-press a rail cell                          -> enter / toggle multi-select
//   drag a trim handle / fade knob                  -> whole-ms trim / fade events
//   two fingers                                     -> pinch zoom event
//
// The machine never touches the project. It reads the renderer's snapshot (layout, zones, lane
// heights, chain flags) and returns EVENTS; the Kotlin glue maps them onto the ViewModel (clip
// indices -> ids, undo steps). The vertical scroll position and the long-press drag offset are
// owned here and handed to the renderer for drawing, so a gesture frame needs no Kotlin at all.
#pragma once
#include <cstdint>
#include <vector>

#include "timeline_render.h"

namespace am {
namespace timeline {

/** Event types. Values are part of the JNI contract (mirrored in NativeTimeline.kt, TL_EV_*). */
enum EventType : int32_t {
    kEvRedraw = 0,        // scroll / drag offset changed: draw a frame
    kEvBegin,             // a = track, b = clip (-1 = none): a gesture started on this clip
    kEvScrubStart,        // pause playback (first scrub movement)
    kEvSeek,              // a = project ms
    kEvSelect,            // select the gesture's clip
    kEvBeginAdjust,       // open the undo step (before a drag / trim / fade)
    kEvMoveTime,          // a = delta ms (move the gesture's clip in time)
    kEvReorder,           // a = from lane, b = to lane
    kEvTrim,              // a = 1 left edge / 0 right edge, b = delta ms (whole ms)
    kEvFade,              // a = 1 fade-in / 0 fade-out, b = raw whole-ms delta (the caller applies
                          //     fadeIn + b / fadeOut - b to its CURRENT values)
    kEvZoom,              // a = new px-per-ms (dp per ms, already clamped)
    kEvPill,              // the layer-count pill was tapped
    kEvToggleVisibility,  // the gesture's clip: eye
    kEvToggleMulti,       // the gesture's clip: toggle in multi-select
    kEvEnterMulti,        // the gesture's clip: long-press on the rail -> start multi-select
    kEvDelete,            // the gesture's clip: chain / child x
    kEvArmLongPress,      // a = delay ms: the host calls longPressFired() after that unless cancelled
    kEvCancelLongPress,   // the host drops that timer
    kEvAnimate            // a fling is running: the host calls tick() on the next frame
};

struct Event {
    int32_t type = kEvRedraw;
    double a = 0;
    double b = 0;
};

/** Android MotionEvent action numbers (the JNI layer passes actionMasked unchanged). */
enum Action : int32_t {
    kActionDown = 0,
    kActionUp = 1,
    kActionMove = 2,
    kActionCancel = 3,
    kActionPointerDown = 5,
    kActionPointerUp = 6
};

struct TouchInput {
    int32_t action = kActionDown;
    float x = 0, y = 0;          // pointer 0
    float x1 = 0, y1 = 0;        // pointer 1 (only when pointerCount >= 2)
    int32_t pointerCount = 1;
    int64_t timeMs = 0;          // event time (for the velocity tracker)
    int64_t playheadMs = 0;      // the editor playhead right now
    int32_t viewW = 0, viewH = 0;
};

/** The timeline zoom range, as in Kotlin: TIMELINE_PX_PER_MS * [0.25, 8]. */
constexpr float kBasePxPerMs = 0.045f;
constexpr float kMinZoom = kBasePxPerMs * 0.25f;
constexpr float kMaxZoom = kBasePxPerMs * 8.f;

class Gesture {
public:
    void setConfig(float touchSlopPx, int64_t longPressMs) {
        touchSlop_ = touchSlopPx > 0 ? touchSlopPx : 8.f;
        longPressMs_ = longPressMs > 0 ? longPressMs : 400;
    }

    /** One touch event. [r] supplies the snapshot + hit testing. Events are appended to [out]. */
    void touch(const Renderer& r, const TouchInput& in, std::vector<Event>* out);

    /** The host's long-press timer fired. */
    void longPressFired(const Renderer& r, std::vector<Event>* out);

    /** One animation frame of a running fling ([nowNanos] = the frame time). */
    void tick(const Renderer& r, int64_t nowNanos, std::vector<Event>* out);

    /** The surface size the scroll limit is computed for (the draw call keeps it current). */
    void setViewSize(int32_t w, int32_t h) { viewW_ = w; viewH_ = h; }

    /** Vertical scroll, clamped to the CURRENT snapshot (it can shrink under the finger). */
    float scrollY(const Renderer& r) const;
    float maxScrollY(const Renderer& r) const;

    /** The long-press drag offset the renderer draws (track / clip -1 = none). */
    void dragView(int32_t* track, int32_t* clip, float* dx, float* dy) const {
        *track = dragTrack_; *clip = dragClip_; *dx = dragDx_; *dy = dragDy_;
    }

    bool animating() const { return scrubFling_.active || scrollFling_.active; }

private:
    enum Mode : int32_t {
        kIdle = 0, kPending, kScrub, kScrollY, kDragBar, kTrim, kFade, kPinch,
        kDead  // pinch finished / long-press consumed: ignore until all fingers lift
    };

    struct Sample { int64_t t; float x, y; };

    // Exponential decay, the same curve Compose's exponentialDecay(frictionMultiplier = 1) draws.
    struct Fling {
        bool active = false;
        bool started = false;       // t0 taken on the first tick
        int64_t t0Nanos = 0;
        double start = 0, v0 = 0;   // value, velocity (units / s)
        double lo = 0, hi = 0;      // clamp range
        double durationSec = 0;
    };

    void cancelLongPress(std::vector<Event>* out);
    void armLongPress(std::vector<Event>* out);
    void clearDrag(std::vector<Event>* out);
    void applyScrub(const Renderer& r, float dx, std::vector<Event>* out);
    void applyScroll(const Renderer& r, float dy, std::vector<Event>* out);
    void applyTrimFade(const Renderer& r, float dx, std::vector<Event>* out);
    void dispatchTap(const Renderer& r, std::vector<Event>* out);
    float laneStepPx(const Renderer& r, int32_t track) const;
    bool computeVelocity(float* vx, float* vy) const;
    void startFlingScrub(const Renderer& r, float vx);
    void startFlingScroll(const Renderer& r, float vy);
    bool stepFling(Fling* f, int64_t nowNanos, double* value);
    float pxPerMs(const Renderer& r) const { return r.snapshot().pxPerMs; }
    bool hasClip() const { return downTrack_ >= 0 && downClip_ >= 0; }

    float touchSlop_ = 8.f;
    int64_t longPressMs_ = 400;
    int32_t viewW_ = 0, viewH_ = 0;

    Mode mode_ = kIdle;
    float downX_ = 0, downY_ = 0, lastX_ = 0, lastY_ = 0;
    Zone downZone_ = kZoneNone;
    int32_t downTrack_ = -1, downClip_ = -1;
    double scrubPosMs_ = 0;
    float dragAccX_ = 0, dragAccY_ = 0;
    float msAcc_ = 0;
    float lastSpan_ = 0;
    float pinchPpm_ = kBasePxPerMs;
    bool longPressArmed_ = false;

    float scrollY_ = 0;
    int32_t dragTrack_ = -1, dragClip_ = -1;
    float dragDx_ = 0, dragDy_ = 0;

    std::vector<Sample> samples_;
    Fling scrubFling_, scrollFling_;
};

}  // namespace timeline
}  // namespace am
