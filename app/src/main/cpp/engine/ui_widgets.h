#pragma once

#include <cstdint>

namespace am {
namespace ui {

// ---- RulerDial math ----

float quantizeRulerValue(float value, float minVal, float maxVal, float step);

float rulerDragDelta(float pos, float dxPx, float tickPx, float minVal, float maxVal, float step);

// Closed-form Compose exponentialDecay step at elapsed time dtSec (fractionMultiplier=1.2).
// Returns quantized clamped value at `tSec`, sets `*done = true` once velocity drops below threshold or bounds hit.
float rulerFlingEval(
    float startVal,
    float velocityUnitsPerSec,
    float elapsedSec,
    float frictionMultiplier,
    float step,
    float minVal,
    float maxVal,
    bool* outDone);

// ---- CropBox math ----

// presetKind: 0 = Free, 1 = Original, 2 = Project, 3 = Custom ratio (customNum / customDen).
// Returns <= 0 when unconstrained (Free / invalid).
float cropLockK(int presetKind, float customNum, float customDen, float srcAspect, float projAspect);

void cropRectFromProps(float wPct, float hPct, float hPosPct, float vPosPct, float outLtrb[4]);

void cropPropsFromRect(float l, float t, float r, float b, float minSize, float outProps[4]);

void cropResizeAroundCenter(
    float curW, float curH, float curL, float curT, float curR, float curB,
    float newW, float newH, float lockK, float minSize, float outProps[4]);

// Returns bitmask: -1 = none, 0 = move inside box, bitflags 1=L, 2=R, 4=T, 8=B.
int cropHitTest(float px, float py, float l, float t, float r, float b, float tol);

void cropDrag(
    int mode, float dxNorm, float dyNorm,
    float l, float t, float r, float b,
    float lockK, float minSize, float outProps[4]);

// ---- Pen / Node path math ----

void pathFrameTransform(
    float cx, float cy,
    float boxW, float boxH,
    float frameW, float frameH,
    float scalePct, float rotDeg,
    float depthX, float depthY,
    bool flipH, bool flipV,
    float pivotX, float pivotY,
    float inX, float inY,
    bool inverse,
    float outXy[2]);

// hitKind constants
constexpr int NODE_HIT_NONE = 0;
constexpr int NODE_HIT_ANCHOR = 1;
constexpr int NODE_HIT_IN_HANDLE = 2;
constexpr int NODE_HIT_OUT_HANDLE = 3;
constexpr int NODE_HIT_EDGE = 4;

// Tests handles (for `selectedIdx`), anchors, and flattened cubic/line segments in canvas pixel space.
// `anchorCanvasXy`: 2 * count floats
// `inCanvasXy` / `hasIn`: per-anchor handle in canvas px
// `outCanvasXy` / `hasOut`: per-anchor handle in canvas px
// `segTypes`: 0 = move, 1 = line (2 floats: x,y), 2 = cubic (6 floats: c1x,c1y,c2x,c2y,x,y), 3 = close
// `outResult`: [hitKind, anchorOrSubpathIdx, segIdx, edgeT]
void nodeHitTest(
    float px, float py,
    const float* anchorCanvasXy,
    const float* inCanvasXy, const uint8_t* hasIn,
    const float* outCanvasXy, const uint8_t* hasOut,
    int anchorCount,
    int selectedIdx,
    float radiusPx,
    const int32_t* segTypes,
    const float* segCanvasPts,
    int segCount,
    float outResult[4]);

// ---- PlanarQuad math ----

// Returns 0..3 corner index, 4 = inside quad body, -1 = none.
int planarHitTest(float fx, float fy, const float quad8[8], float radiusNorm);

void planarMove(const float quad8[8], float dx, float dy, float outQuad8[8]);

// ---- FrameGeo hit test (MediaGestureLayer / Selection) ----

bool frameGeoContains(
    float px, float py,
    float boxW, float boxH,
    float frameW, float frameH,
    float minScale,
    float pivotRefW, float pivotRefH,
    float visW, float visH, float visOffX, float visOffY,
    float scalePct, float rotDeg,
    float depthX, float depthY,
    bool flipH, bool flipV,
    float pivotX, float pivotY);

// ---- Color space helpers ----

void hsvToRgb(float hDeg, float s, float v, float outRgb[3]);
void rgbToHsv(float r, float g, float b, float outHsv[3]);

}  // namespace ui
}  // namespace am
