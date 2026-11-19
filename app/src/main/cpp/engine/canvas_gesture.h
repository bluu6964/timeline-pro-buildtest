// After Motion native engine - Canvas touch hit-testing & drag/tap gesture FSM.
// Mirrors ui/components/CanvasDragLayer.kt + SelectionFrame.kt (FrameGeo) 1:1.
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace canvas {

constexpr int32_t kKindInvalid = -1;
constexpr int32_t kKindText = 0;
constexpr int32_t kKindShapeFamily = 1;
constexpr int32_t kKindBlocker = 2;

constexpr int32_t kEvSelect = 1;
constexpr int32_t kEvDoubleTap = 2;
constexpr int32_t kEvMove = 3;
constexpr int32_t kEvCommit = 4;
constexpr int32_t kEvArmSingleTap = 5;
constexpr int32_t kEvCancelSingleTap = 6;
constexpr int32_t kEvConsume = 7;

struct Item {
    int32_t kind = kKindInvalid;
    float boxW = 0.0f;
    float boxH = 0.0f;
    float visW = 0.0f;
    float visH = 0.0f;
    float visOffX = 0.0f;
    float visOffY = 0.0f;
    float effScale = 1.0f;
    float rotDeg = 0.0f;
    float pivotX = 0.5f;
    float pivotY = 0.5f;
    bool flipH = false;
    bool flipV = false;
    float depthX = 0.0f;
    float depthY = 0.0f;
    float startX = 0.0f;
    float startY = 0.0f;
    float depthFactor = 1.0f;
};

struct Event {
    int32_t type = 0;
    int32_t index = -1;
    float x = 0.0f;
    float y = 0.0f;
    int64_t token = 0;
};

bool itemContainsPoint(const Item& item, float frameW, float frameH, float px, float py);

class Gesture {
public:
    void setConfig(float touchSlopPx, float doubleTapSlopPx,
                   int64_t doubleTapTimeoutMs, int64_t longPressTimeoutMs);

    // Evaluates double-tap arming and topmost hit-test (reverse order, blocker stops).
    // pendingCurrentIndex is the index in [items] of the currently pending tap clip, or -1.
    // Returns true if a draggable/tappable target was captured.
    bool onDown(float px, float py, int64_t uptimeMs,
                float frameW, float frameH,
                int32_t pendingCurrentIndex,
                const Item* items, int32_t itemCount,
                std::vector<Event>& outEvents);

    void onMove(float dx, float dy, std::vector<Event>& outEvents);
    void onUp(int64_t uptimeMs, bool isRelease, std::vector<Event>& outEvents);
    void onCancel(std::vector<Event>& outEvents);
    void onSingleTapTimeout(int64_t token, std::vector<Event>& outEvents);

    int64_t pendingToken() const { return hasPending_ ? pendingToken_ : 0; }

private:
    void resetActive();

    float touchSlopPx_ = 24.0f;
    float doubleTapSlopPx_ = 300.0f;
    int64_t doubleTapTimeoutMs_ = 300;
    int64_t longPressTimeoutMs_ = 400;

    bool hasPending_ = false;
    int64_t pendingToken_ = 0;
    int64_t nextToken_ = 0;
    float pendingDownX_ = 0.0f;
    float pendingDownY_ = 0.0f;
    int64_t pendingUpTimeMs_ = 0;

    bool active_ = false;
    int32_t targetIndex_ = -1;
    bool targetIsText_ = false;
    int32_t armedIndex_ = -1;
    int64_t armedToken_ = 0;
    float downX_ = 0.0f;
    float downY_ = 0.0f;
    int64_t downTimeMs_ = 0;
    float totalDx_ = 0.0f;
    float totalDy_ = 0.0f;
    bool dragging_ = false;
    float startX_ = 0.0f;
    float startY_ = 0.0f;
    float depthFactor_ = 1.0f;
    float lastPosX_ = 0.0f;
    float lastPosY_ = 0.0f;
};

}  // namespace canvas
}  // namespace am
