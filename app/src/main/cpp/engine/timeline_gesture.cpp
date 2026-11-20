// Build 233 — the native timeline's gesture state machine. See timeline_gesture.h.
// Ported 1:1 from build 232's Kotlin TlGestureMachine (NativeTimelineSurface.kt), including its
// thresholds (touch slop, 1.2x vertical bias, 100 px/s fling minimum) and its rounding rules.
#include "timeline_gesture.h"

#include <algorithm>
#include <cmath>

namespace am {
namespace timeline {

namespace {

// Kotlin's Float.roundToLong / roundToInt: nearest, ties toward +infinity.
inline int64_t roundHalfUp(double v) { return (int64_t)std::floor(v + 0.5); }

inline bool isTrimOrFade(Zone z) {
    return z == kZoneTrimL || z == kZoneTrimR || z == kZoneFadeL || z == kZoneFadeR;
}

constexpr double kFriction = -4.2;          // Compose exponentialDecay(frictionMultiplier = 1)
constexpr double kFlingStopSpeed = 20.0;    // absVelocityThreshold
constexpr float kFlingMinSpeedPx = 100.f;   // below this (px/s) no fling starts
constexpr int64_t kVelocityHorizonMs = 100; // Android VelocityTracker: samples used
constexpr int64_t kVelocityStoppedMs = 40;  // a gap this long = the finger had stopped

inline void emit(std::vector<Event>* out, int32_t type, double a = 0, double b = 0) {
    Event e;
    e.type = type;
    e.a = a;
    e.b = b;
    out->push_back(e);
}

}  // namespace

// ---- scroll ----

float Gesture::maxScrollY(const Renderer& r) const {
    if (viewH_ <= 0) return 0.f;
    const Snapshot& s = r.snapshot();
    return std::max(0.f, contentHeightPx(s) - ((float)viewH_ - s.rulerHPx));
}

float Gesture::scrollY(const Renderer& r) const {
    return std::min(std::max(scrollY_, 0.f), maxScrollY(r));
}

// ---- helpers ----

void Gesture::cancelLongPress(std::vector<Event>* out) {
    if (!longPressArmed_) return;
    longPressArmed_ = false;
    emit(out, kEvCancelLongPress);
}

void Gesture::armLongPress(std::vector<Event>* out) {
    cancelLongPress(out);
    // Build 235: the eye is part of the rail head — a press starting on it must arm
    // the long-press too, or a long-press on the eye can never enter multi-select.
    if (downZone_ != kZoneBar && downZone_ != kZoneRail && downZone_ != kZoneEye) return;
    longPressArmed_ = true;
    emit(out, kEvArmLongPress, (double)longPressMs_);
}

void Gesture::clearDrag(std::vector<Event>* out) {
    dragTrack_ = -1;
    dragClip_ = -1;
    dragDx_ = 0;
    dragDy_ = 0;
    emit(out, kEvRedraw);
}

float Gesture::laneStepPx(const Renderer& r, int32_t track) const {
    const Snapshot& s = r.snapshot();
    if (track < 0 || track >= (int32_t)s.tracks.size()) return 27.f * (s.density > 0 ? s.density : 1.f);
    // Kotlin: (tallest clip's lane height in dp + 3) * density = lane height px + the 3 dp gap.
    return s.tracks[(size_t)track].heightPx + s.gapPx;
}

// ---- velocity (Android VelocityTracker, LSQ2 flavour) ----

bool Gesture::computeVelocity(float* vx, float* vy) const {
    *vx = 0;
    *vy = 0;
    if (samples_.size() < 2) return false;
    const int64_t newest = samples_.back().t;
    // Newest -> oldest while inside the horizon and without a "finger stopped" gap.
    std::vector<const Sample*> pts;
    int64_t prevT = newest;
    for (size_t i = samples_.size(); i-- > 0;) {
        const Sample& sm = samples_[i];
        if (newest - sm.t > kVelocityHorizonMs) break;
        if (prevT - sm.t > kVelocityStoppedMs) break;
        pts.push_back(&sm);
        prevT = sm.t;
    }
    if (pts.size() < 2) return false;

    auto fit = [&](bool useY) -> double {
        const size_t n = pts.size();
        // Linear fallback: slope over the two ends.
        auto linear = [&]() -> double {
            const Sample* a = pts.back();   // oldest
            const Sample* b = pts.front();  // newest
            const double dt = (double)(b->t - a->t) / 1000.0;
            if (dt <= 0) return 0.0;
            return ((useY ? b->y : b->x) - (useY ? a->y : a->x)) / dt;
        };
        if (n < 3) return linear();
        // x(t) = c0 + c1 t + c2 t^2, t in seconds relative to the newest sample.
        double s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, y0 = 0, y1 = 0, y2 = 0;
        for (const Sample* p : pts) {
            const double t = (double)(p->t - newest) / 1000.0;
            const double v = useY ? p->y : p->x;
            const double t2 = t * t;
            s0 += 1; s1 += t; s2 += t2; s3 += t2 * t; s4 += t2 * t2;
            y0 += v; y1 += v * t; y2 += v * t2;
        }
        // Solve [s0 s1 s2; s1 s2 s3; s2 s3 s4] c = [y0 y1 y2] (Cramer's rule).
        auto det3 = [](double a, double b, double c, double d, double e, double f, double g,
                       double h, double i) {
            return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        };
        const double d = det3(s0, s1, s2, s1, s2, s3, s2, s3, s4);
        if (std::fabs(d) < 1e-18) return linear();
        const double d1 = det3(s0, y0, s2, s1, y1, s3, s2, y2, s4);  // c1 = slope at t = 0
        return d1 / d;
    };
    *vx = (float)fit(false);
    *vy = (float)fit(true);
    return true;
}

// ---- fling ----

void Gesture::startFlingScrub(const Renderer& r, float vx) {
    const Snapshot& s = r.snapshot();
    if (std::fabs(vx) <= kFlingMinSpeedPx || s.pxPerMs <= 0) return;
    const double density = s.density > 0 ? s.density : 1.0;
    Fling f;
    f.start = scrubPosMs_;
    f.v0 = -((double)vx / density) / (double)s.pxPerMs;
    f.lo = 0;
    f.hi = (double)s.totalMs;
    if (std::fabs(f.v0) <= kFlingStopSpeed) return;
    f.durationSec = std::log(kFlingStopSpeed / std::fabs(f.v0)) / kFriction;
    f.active = true;
    scrubFling_ = f;
}

void Gesture::startFlingScroll(const Renderer& r, float vy) {
    if (std::fabs(vy) <= kFlingMinSpeedPx) return;
    Fling f;
    f.start = scrollY(r);
    f.v0 = -(double)vy;
    f.lo = 0;
    f.hi = maxScrollY(r);
    if (std::fabs(f.v0) <= kFlingStopSpeed) return;
    f.durationSec = std::log(kFlingStopSpeed / std::fabs(f.v0)) / kFriction;
    f.active = true;
    scrollFling_ = f;
}

// Returns true while the animation continues. value(t) = x0 - v0/k + (v0/k) e^(k t), k = -4.2.
bool Gesture::stepFling(Fling* f, int64_t nowNanos, double* value) {
    if (!f->started) {
        f->started = true;
        f->t0Nanos = nowNanos;
    }
    double t = (double)(nowNanos - f->t0Nanos) * 1e-9;
    bool more = true;
    if (t >= f->durationSec) {
        t = f->durationSec;
        more = false;
    }
    *value = f->start - f->v0 / kFriction + (f->v0 / kFriction) * std::exp(kFriction * t);
    return more;
}

void Gesture::tick(const Renderer& r, int64_t nowNanos, std::vector<Event>* out) {
    if (scrubFling_.active) {
        double v = 0;
        bool more = stepFling(&scrubFling_, nowNanos, &v);
        const double ms = std::min(std::max(v, scrubFling_.lo), scrubFling_.hi);
        emit(out, kEvSeek, (double)roundHalfUp(ms));
        if (ms <= scrubFling_.lo || ms >= scrubFling_.hi) more = false;
        scrubFling_.active = more;
    }
    if (scrollFling_.active) {
        double v = 0;
        bool more = stepFling(&scrollFling_, nowNanos, &v);
        const double sy = std::min(std::max(v, scrollFling_.lo), scrollFling_.hi);
        scrollY_ = (float)sy;
        emit(out, kEvRedraw);
        if (sy <= scrollFling_.lo || sy >= scrollFling_.hi) more = false;
        scrollFling_.active = more;
    }
    (void)r;
    if (animating()) emit(out, kEvAnimate);
}

// ---- gesture actions ----

void Gesture::applyScrub(const Renderer& r, float dx, std::vector<Event>* out) {
    const Snapshot& s = r.snapshot();
    if (s.pxPerMs <= 0) return;
    const double density = s.density > 0 ? s.density : 1.0;
    const double deltaMs = -((double)dx / density) / (double)s.pxPerMs;
    scrubPosMs_ = std::min(std::max(scrubPosMs_ + deltaMs, 0.0), (double)s.totalMs);
    emit(out, kEvSeek, (double)roundHalfUp(scrubPosMs_));
}

void Gesture::applyScroll(const Renderer& r, float dy, std::vector<Event>* out) {
    scrollY_ = std::min(std::max(scrollY_ - dy, 0.f), maxScrollY(r));
    emit(out, kEvRedraw);
}

void Gesture::applyTrimFade(const Renderer& r, float dx, std::vector<Event>* out) {
    const Snapshot& s = r.snapshot();
    if (!hasClip() || s.pxPerMs <= 0) return;
    const float density = s.density > 0 ? s.density : 1.f;
    msAcc_ += dx / density / s.pxPerMs;
    const int64_t whole = (int64_t)msAcc_;  // Kotlin Float.toLong(): truncates toward zero
    if (whole == 0) return;
    msAcc_ -= (float)whole;
    switch (downZone_) {
        case kZoneTrimL: emit(out, kEvTrim, 1, (double)whole); break;
        case kZoneTrimR: emit(out, kEvTrim, 0, (double)whole); break;
        case kZoneFadeL: emit(out, kEvFade, 1, (double)whole); break;
        case kZoneFadeR: emit(out, kEvFade, 0, (double)whole); break;
        default: break;
    }
}

void Gesture::dispatchTap(const Renderer& r, std::vector<Event>* out) {
    const bool multi = r.snapshot().multiMode;
    switch (downZone_) {
        case kZonePill:
            emit(out, kEvPill);
            break;
        case kZoneEye:
            if (!hasClip()) return;
            emit(out, multi ? kEvToggleMulti : kEvToggleVisibility);
            break;
        case kZoneRail:
            if (!hasClip()) return;
            emit(out, multi ? kEvToggleMulti : kEvSelect);
            break;
        case kZoneBar:
            if (hasClip()) emit(out, kEvSelect);
            break;
        case kZoneChainX:
        case kZoneChildX:
            if (hasClip()) emit(out, kEvDelete);
            break;
        default:
            break;  // ruler / empty / trim / fade: a tap does nothing
    }
}

void Gesture::longPressFired(const Renderer& r, std::vector<Event>* out) {
    if (!longPressArmed_) return;
    longPressArmed_ = false;  // the timer is spent: no cancel event needed
    if (mode_ != kPending || !hasClip()) return;
    // Build 235: long-press anywhere on the rail head — the eye included — is a
    // multi-select press, exactly like the plain rail. (A short TAP on the eye
    // still toggles visibility; see dispatchTap.)
    if (downZone_ == kZoneRail || downZone_ == kZoneEye) {
        emit(out, r.snapshot().multiMode ? kEvToggleMulti : kEvEnterMulti);
        mode_ = kDead;
    } else {
        // detectDragGesturesAfterLongPress: select + open the undo step, then drag.
        emit(out, kEvSelect);
        emit(out, kEvBeginAdjust);
        dragAccX_ = 0;
        dragAccY_ = 0;
        mode_ = kDragBar;
        dragTrack_ = downTrack_;
        dragClip_ = downClip_;
        dragDx_ = 0;
        dragDy_ = 0;
        emit(out, kEvRedraw);
    }
}

// ---- the touch entry point ----

void Gesture::touch(const Renderer& r, const TouchInput& in, std::vector<Event>* out) {
    setViewSize(in.viewW, in.viewH);
    const Snapshot& s = r.snapshot();
    const float density = s.density > 0 ? s.density : 1.f;

    switch (in.action) {
        case kActionDown: {
            scrubFling_.active = false;
            scrollFling_.active = false;
            samples_.clear();
            samples_.push_back({in.timeMs, in.x, in.y});
            downX_ = in.x; downY_ = in.y;
            lastX_ = in.x; lastY_ = in.y;
            scrubPosMs_ = (double)in.playheadMs;
            View v;
            v.w = in.viewW;
            v.h = in.viewH;
            v.playheadMs = in.playheadMs;
            v.scrollYPx = scrollY(r);
            int32_t tr = -1, cl = -1;
            downZone_ = r.hitTest(in.x, in.y, v, &tr, &cl);
            downTrack_ = tr;
            downClip_ = cl;
            emit(out, kEvBegin, (double)tr, (double)cl);
            dragAccX_ = 0; dragAccY_ = 0; msAcc_ = 0;
            mode_ = kPending;
            armLongPress(out);
            break;
        }

        case kActionPointerDown: {
            if (in.pointerCount >= 2) {
                cancelLongPress(out);
                if (mode_ == kDragBar) clearDrag(out);
                mode_ = kPinch;
                lastSpan_ = std::hypot(in.x - in.x1, in.y - in.y1);
                pinchPpm_ = s.pxPerMs;
            }
            break;
        }

        case kActionMove: {
            samples_.push_back({in.timeMs, in.x, in.y});
            if (samples_.size() > 32) samples_.erase(samples_.begin(), samples_.begin() + 16);
            if (mode_ == kPinch) {
                if (in.pointerCount >= 2) {
                    const float span = std::hypot(in.x - in.x1, in.y - in.y1);
                    if (lastSpan_ > 1.f && span > 1.f) {
                        const float zoomChange = span / lastSpan_;
                        if (std::fabs(zoomChange - 1.f) > 0.001f) {
                            pinchPpm_ = std::min(std::max(pinchPpm_ * zoomChange, kMinZoom), kMaxZoom);
                            emit(out, kEvZoom, (double)pinchPpm_);
                        }
                    }
                    lastSpan_ = span;
                }
                return;
            }
            const float x = in.x, y = in.y;
            const float dx = x - lastX_;
            const float dy = y - lastY_;
            switch (mode_) {
                case kPending: {
                    const float totalDx = x - downX_;
                    const float totalDy = y - downY_;
                    if (isTrimOrFade(downZone_)) {
                        // detectHorizontalDragGestures: starts on horizontal slop.
                        if (std::fabs(totalDx) > touchSlop_ && std::fabs(totalDx) >= std::fabs(totalDy)) {
                            cancelLongPress(out);
                            emit(out, kEvBeginAdjust);
                            msAcc_ = 0;
                            mode_ = (downZone_ == kZoneTrimL || downZone_ == kZoneTrimR) ? kTrim : kFade;
                            lastX_ = x; lastY_ = y;
                            applyTrimFade(r, dx, out);
                        }
                    } else if (std::fabs(totalDy) > touchSlop_ &&
                               std::fabs(totalDy) > std::fabs(totalDx) * 1.2f) {
                        cancelLongPress(out);
                        mode_ = kScrollY;
                        scrollY_ = scrollY(r);
                        applyScroll(r, dy, out);
                    } else if (std::fabs(totalDx) > touchSlop_ || std::fabs(totalDy) > touchSlop_) {
                        cancelLongPress(out);
                        mode_ = kScrub;
                        emit(out, kEvScrubStart);
                        lastX_ = downX_;  // the slop distance counts, like the panel
                        applyScrub(r, x - lastX_, out);
                        lastX_ = x;
                    }
                    break;
                }
                case kScrub:
                    applyScrub(r, dx, out);
                    break;
                case kScrollY:
                    applyScroll(r, dy, out);
                    break;
                case kDragBar: {
                    dragAccX_ += dx;
                    dragAccY_ += dy;
                    if (dragTrack_ >= 0 && dragClip_ >= 0 && s.pxPerMs > 0) {
                        bool isChain = false;
                        if (dragTrack_ < (int32_t)s.tracks.size()) {
                            const Track& t = s.tracks[(size_t)dragTrack_];
                            if (dragClip_ < (int32_t)t.clips.size())
                                isChain = (t.clips[(size_t)dragClip_].flags & kFlagChain) != 0;
                        }
                        if (!isChain && std::fabs(dx) >= std::fabs(dy)) {
                            const int64_t deltaMs = roundHalfUp((double)dx / density / s.pxPerMs);
                            if (deltaMs != 0) {
                                emit(out, kEvMoveTime, (double)deltaMs);
                                dragAccX_ -= (float)((double)deltaMs * s.pxPerMs * density);
                            }
                        }
                        dragDx_ = dragAccX_;
                        dragDy_ = dragAccY_;
                        emit(out, kEvRedraw);
                    }
                    break;
                }
                case kTrim:
                case kFade:
                    applyTrimFade(r, dx, out);
                    break;
                default:
                    break;
            }
            lastX_ = x;
            lastY_ = y;
            break;
        }

        case kActionPointerUp: {
            if (mode_ == kPinch && in.pointerCount <= 2) mode_ = kDead;
            break;
        }

        case kActionUp: {
            cancelLongPress(out);
            switch (mode_) {
                case kPending:
                    dispatchTap(r, out);
                    break;
                case kScrub: {
                    float vx = 0, vy = 0;
                    if (computeVelocity(&vx, &vy)) startFlingScrub(r, vx);
                    break;
                }
                case kScrollY: {
                    float vx = 0, vy = 0;
                    if (computeVelocity(&vx, &vy)) startFlingScroll(r, vy);
                    break;
                }
                case kDragBar: {
                    if (downTrack_ >= 0) {
                        const float step = laneStepPx(r, downTrack_);
                        const int32_t steps = (step > 0) ? (int32_t)roundHalfUp((double)dragAccY_ / step) : 0;
                        if (steps != 0) {
                            const int32_t maxLane = std::max((int32_t)s.tracks.size() - 1, 0);
                            const int32_t to = std::min(std::max(downTrack_ + steps, 0), maxLane);
                            if (to != downTrack_) emit(out, kEvReorder, (double)downTrack_, (double)to);
                        }
                    }
                    clearDrag(out);
                    break;
                }
                default:
                    break;
            }
            mode_ = kIdle;
            samples_.clear();
            if (animating()) emit(out, kEvAnimate);
            break;
        }

        case kActionCancel: {
            cancelLongPress(out);
            if (mode_ == kDragBar) clearDrag(out);
            mode_ = kIdle;
            samples_.clear();
            break;
        }

        default:
            break;
    }
}

}  // namespace timeline
}  // namespace am
