// After Motion native engine - scene math suite:
//   * CanvasSnap (axis-aligned magnetic snapping + guides)
//   * SpatialAlign (multi-layer align / distribute / canvas center)
//   * DragMath (depth-compensated fixed-canvas drag accumulation)
//   * ParentRig (2D hierarchical parent move + fp transform)
//   * Path flattening, TextOnPath arc-length table & layout
//   * MaskGeometry Catmull-Rom flattening, point-in-polygon, segment distance
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace scene {

// ---- Canvas Snap -------------------------------------------------------------------------------

struct SnapRect {
    float cx, cy, left, top, right, bottom;
};

struct SnapResult {
    float dx = 0.0f;
    float dy = 0.0f;
    bool hasGuideX = false;
    float guideX = 0.0f;
    bool hasGuideY = false;
    float guideY = 0.0f;
};

SnapResult snapRect(const SnapRect& rect, float canvasW, float canvasH,
                    const SnapRect* others, int otherCount, float thresholdPx);

// ---- Drag Math ---------------------------------------------------------------------------------

void draggedPosition(float startX, float startY, float totalDx, float totalDy,
                     float depthFactor, float& outX, float& outY);

// ---- Spatial Align / Distribute ----------------------------------------------------------------

enum SpatialAction : int32_t {
    kAlignLeft = 0,
    kAlignCenterH = 1,
    kAlignRight = 2,
    kAlignTop = 3,
    kAlignCenterV = 4,
    kAlignBottom = 5,
    kDistributeH = 6,
    kDistributeV = 7,
    kCanvasCenterH = 8,
    kCanvasCenterV = 9
};

struct AlignRect {
    float left, top, right, bottom;
};

struct AlignDelta {
    int32_t index = 0;
    float dx = 0.0f;
    float dy = 0.0f;
};

std::vector<AlignDelta> spatialDeltas(const AlignRect* rects, int count, int32_t action);
std::vector<AlignDelta> spatialCanvasDeltas(const AlignRect* rects, int count,
                                            float frameW, float frameH, int32_t action);

// ---- ParentRig 2D Composition ------------------------------------------------------------------

void parentMove(float x, float y, float refX, float refY, float nx, float ny,
                float ds, float drDeg, float& outX, float& outY);

void applyParentToFp(float* fp16, float refX, float refY, float refScale, float refRot,
                     float nx, float ny, float nowScale, float nowRot, float k,
                     bool lockScale, bool lockRotate, bool hiddenByGroup);

// ---- Path Flattening & TextOnPath --------------------------------------------------------------

constexpr int kPathOpMove = 0;
constexpr int kPathOpLine = 1;
constexpr int kPathOpQuad = 2;
constexpr int kPathOpCubic = 3;
constexpr int kPathOpClose = 4;

// Flattens encoded segments (ops[i], offsets[i], counts[i] into flat pts) into x,y pairs.
std::vector<float> flattenPathSegments(const int32_t* ops, const int32_t* offsets,
                                       const int32_t* counts, int segCount,
                                       const float* pts, int ptsLen, int stepsPerCurve);

struct PathPlacement {
    float startDist;
    float x;
    float y;
    float angleDeg;
    bool drawn;
};

// Deduplicates consecutive polyline points, builds cumulative arc lengths, and lays advances out.
float buildArcTable(const float* inXy, int inPointCount,
                    std::vector<float>& outXy, std::vector<float>& outCum);

void arcPointAt(const float* xy, const float* cum, int pointCount, float total,
                float d, float& outX, float& outY, float& outAngleDeg);

std::vector<PathPlacement> layoutTextOnPath(const float* xy, const float* cum, int pointCount,
                                            float total, const float* advances, int advCount,
                                            float startFrac);

// ---- Mask Geometry -----------------------------------------------------------------------------

// Exact port of MaskGeometry.flattenControlPoints (Catmull-Rom closed loop or plain polygon).
std::vector<float> flattenMaskControlPoints(const float* ctrlXy, int count, bool smooth,
                                            int maxPoints);

bool pointInPolygon(float px, float py, const float* polyXy, int pointCount);
float distToSegment(float px, float py, float ax, float ay, float bx, float by);

}  // namespace scene
}  // namespace am
