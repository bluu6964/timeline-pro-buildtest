// After Motion native engine - Canvas touch hit-testing & drag/tap gesture FSM.
#include "canvas_gesture.h"

#include <algorithm>
#include <cmath>

#include "scene_math.h"

namespace am {
namespace canvas {

namespace {

constexpr double kDegToRad = 3.14159265358979323846 / 180.0;
constexpr float kMinScale = 0.01f;

struct MappedFrame {
    float frameW;
    float frameH;
    float sx;
    float sy;
    float cs;
    float sn;
    float pvx;
    float pvy;
    float depthX;
    float depthY;

    void map(float qx, float qy, float& ox, float& oy) const {
        const float x = (qx - pvx) * sx;
        const float y = (qy - pvy) * sy;
        ox = frameW * 0.5f + pvx + x * cs - y * sn + depthX;
        oy = frameH * 0.5f + pvy + x * sn + y * cs + depthY;
    }
};

MappedFrame makeFrame(const Item& it, float frameW, float frameH) {
    const float sc = std::max(it.effScale, kMinScale);
    const double rad = static_cast<double>(it.rotDeg) * kDegToRad;
    return MappedFrame{
        frameW,
        frameH,
        sc * (it.flipH ? -1.0f : 1.0f),
        sc * (it.flipV ? -1.0f : 1.0f),
        static_cast<float>(std::cos(rad)),
        static_cast<float>(std::sin(rad)),
        (it.pivotX - 0.5f) * it.boxW,
        (it.pivotY - 0.5f) * it.boxH,
        it.depthX,
        it.depthY
    };
}

}  // namespace

bool itemContainsPoint(const Item& item, float frameW, float frameH, float px, float py) {
    if (item.kind == kKindInvalid) return false;
    const MappedFrame f = makeFrame(item, frameW, frameH);
    const float hw = item.visW * 0.5f;
    const float hh = item.visH * 0.5f;
    float cx[4];
    float cy[4];
    f.map(item.visOffX - hw, item.visOffY - hh, cx[0], cy[0]);
    f.map(item.visOffX + hw, item.visOffY - hh, cx[1], cy[1]);
    f.map(item.visOffX + hw, item.visOffY + hh, cx[2], cy[2]);
    f.map(item.visOffX - hw, item.visOffY + hh, cx[3], cy[3]);
    bool hasPos = false;
    bool hasNeg = false;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) & 3;
        const float cr = (cx[j] - cx[i]) * (py - cy[i]) - (cy[j] - cy[i]) * (px - cx[i]);
        if (cr > 0.0f) {
            hasPos = true;
        } else if (cr < 0.0f) {
            hasNeg = true;
        }
    }
    return !(hasPos && hasNeg);
}

void Gesture::setConfig(float touchSlopPx, float doubleTapSlopPx,
                        int64_t doubleTapTimeoutMs, int64_t longPressTimeoutMs) {
    if (touchSlopPx > 0.0f) touchSlopPx_ = touchSlopPx;
    if (doubleTapSlopPx >= 0.0f) doubleTapSlopPx_ = doubleTapSlopPx;
    if (doubleTapTimeoutMs >= 0) doubleTapTimeoutMs_ = doubleTapTimeoutMs;
    if (longPressTimeoutMs >= 0) longPressTimeoutMs_ = longPressTimeoutMs;
}

void Gesture::resetActive() {
    active_ = false;
    targetIndex_ = -1;
    targetIsText_ = false;
    armedIndex_ = -1;
    armedToken_ = 0;
    downX_ = 0.0f;
    downY_ = 0.0f;
    downTimeMs_ = 0;
    totalDx_ = 0.0f;
    totalDy_ = 0.0f;
    dragging_ = false;
    startX_ = 0.0f;
    startY_ = 0.0f;
    depthFactor_ = 1.0f;
    lastPosX_ = 0.0f;
    lastPosY_ = 0.0f;
}

bool Gesture::onDown(float px, float py, int64_t uptimeMs,
                     float frameW, float frameH,
                     int32_t pendingCurrentIndex,
                     const Item* items, int32_t itemCount,
                     std::vector<Event>& outEvents) {
    outEvents.clear();
    resetActive();
    if (!items || itemCount <= 0) return false;

    int32_t armedIdx = -1;
    int64_t armedTok = 0;
    if (hasPending_ && pendingCurrentIndex >= 0 && pendingCurrentIndex < itemCount) {
        const Item& ptItem = items[pendingCurrentIndex];
        if (itemContainsPoint(ptItem, frameW, frameH, px, py) &&
            (uptimeMs - pendingUpTimeMs_) <= doubleTapTimeoutMs_ &&
            std::hypot(px - pendingDownX_, py - pendingDownY_) <= doubleTapSlopPx_) {
            armedIdx = pendingCurrentIndex;
            armedTok = pendingToken_;
        }
    }

    int32_t hitIdx = -1;
    bool hitIsText = false;
    bool blocked = false;
    for (int32_t i = itemCount - 1; i >= 0; --i) {
        const Item& it = items[i];
        if (it.kind == kKindInvalid) continue;
        if (!itemContainsPoint(it, frameW, frameH, px, py)) continue;
        if (it.kind == kKindBlocker) {
            blocked = true;
        } else if (it.kind == kKindText) {
            hitIdx = i;
            hitIsText = true;
        } else if (it.kind == kKindShapeFamily) {
            hitIdx = i;
            hitIsText = false;
        }
        break;
    }

    if (blocked || hitIdx < 0) return false;

    const Item& target = items[hitIdx];
    active_ = true;
    targetIndex_ = hitIdx;
    targetIsText_ = hitIsText;
    armedIndex_ = armedIdx;
    armedToken_ = armedTok;
    downX_ = px;
    downY_ = py;
    downTimeMs_ = uptimeMs;
    totalDx_ = 0.0f;
    totalDy_ = 0.0f;
    dragging_ = false;
    startX_ = target.startX;
    startY_ = target.startY;
    depthFactor_ = target.depthFactor;
    lastPosX_ = target.startX;
    lastPosY_ = target.startY;
    return true;
}

void Gesture::onMove(float dx, float dy, std::vector<Event>& outEvents) {
    outEvents.clear();
    if (!active_) return;
    totalDx_ += dx;
    totalDy_ += dy;
    if (!dragging_) {
        if (std::hypot(totalDx_, totalDy_) <= touchSlopPx_) return;
        dragging_ = true;
        if (armedToken_ != 0 && hasPending_ && pendingToken_ == armedToken_) {
            const int64_t oldTok = pendingToken_;
            hasPending_ = false;
            pendingToken_ = 0;
            outEvents.push_back(Event{kEvCancelSingleTap, -1, 0.0f, 0.0f, oldTok});
        }
        if (targetIsText_) {
            outEvents.push_back(Event{kEvSelect, targetIndex_, 0.0f, 0.0f, 0});
        }
    }
    am::scene::draggedPosition(startX_, startY_, totalDx_, totalDy_, depthFactor_,
                               lastPosX_, lastPosY_);
    outEvents.push_back(Event{kEvMove, targetIndex_, lastPosX_, lastPosY_, 0});
    outEvents.push_back(Event{kEvConsume, targetIndex_, 0.0f, 0.0f, 0});
}

void Gesture::onUp(int64_t uptimeMs, bool isRelease, std::vector<Event>& outEvents) {
    outEvents.clear();
    if (!active_) return;
    if (dragging_) {
        outEvents.push_back(Event{kEvCommit, targetIndex_, lastPosX_, lastPosY_, 0});
        resetActive();
        return;
    }
    if (isRelease) {
        const bool quickTap = (uptimeMs - downTimeMs_) <= longPressTimeoutMs_;
        const bool armedValid = (armedToken_ != 0 && hasPending_ && pendingToken_ == armedToken_);
        if (quickTap && armedValid) {
            const int64_t oldTok = pendingToken_;
            const int32_t armedIdx = armedIndex_;
            hasPending_ = false;
            pendingToken_ = 0;
            outEvents.push_back(Event{kEvCancelSingleTap, -1, 0.0f, 0.0f, oldTok});
            outEvents.push_back(Event{kEvDoubleTap, armedIdx, 0.0f, 0.0f, oldTok});
            if (targetIsText_ && targetIndex_ != armedIdx) {
                hasPending_ = true;
                pendingToken_ = ++nextToken_;
                pendingDownX_ = downX_;
                pendingDownY_ = downY_;
                pendingUpTimeMs_ = uptimeMs;
                outEvents.push_back(Event{kEvArmSingleTap, targetIndex_, downX_, downY_, pendingToken_});
            }
        } else if (quickTap && targetIsText_) {
            if (hasPending_) {
                const int64_t oldTok = pendingToken_;
                outEvents.push_back(Event{kEvCancelSingleTap, -1, 0.0f, 0.0f, oldTok});
            }
            hasPending_ = true;
            pendingToken_ = ++nextToken_;
            pendingDownX_ = downX_;
            pendingDownY_ = downY_;
            pendingUpTimeMs_ = uptimeMs;
            outEvents.push_back(Event{kEvArmSingleTap, targetIndex_, downX_, downY_, pendingToken_});
        } else if (!quickTap && armedValid) {
            const int64_t oldTok = pendingToken_;
            hasPending_ = false;
            pendingToken_ = 0;
            outEvents.push_back(Event{kEvCancelSingleTap, -1, 0.0f, 0.0f, oldTok});
        }
    }
    resetActive();
}

void Gesture::onCancel(std::vector<Event>& outEvents) {
    outEvents.clear();
    if (!active_) return;
    if (dragging_) {
        outEvents.push_back(Event{kEvCommit, targetIndex_, lastPosX_, lastPosY_, 0});
    }
    resetActive();
}

void Gesture::onSingleTapTimeout(int64_t token, std::vector<Event>& outEvents) {
    outEvents.clear();
    if (hasPending_ && pendingToken_ == token) {
        hasPending_ = false;
        pendingToken_ = 0;
        outEvents.push_back(Event{kEvSelect, -1, 0.0f, 0.0f, token});
    }
}

}  // namespace canvas
}  // namespace am
