#include "scene_math.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace am {
namespace scene {

namespace {

constexpr double kDegToRad = 3.14159265358979323846 / 180.0;
constexpr double kRadToDeg = 180.0 / 3.14159265358979323846;
constexpr float kSpatialEps = 0.01f;

void bestSnapAxis(const float features[3], const std::vector<float>& candidates,
                  float thresholdPx, float& outDelta, bool& outHasGuide, float& outGuide) {
    float bestDelta = 0.0f;
    bool hasGuide = false;
    float bestGuide = 0.0f;
    float bestAbs = std::numeric_limits<float>::max();
    for (int fi = 0; fi < 3; ++fi) {
        const float f = features[fi];
        for (float c : candidates) {
            const float d = c - f;
            const float a = std::fabs(d);
            if (a <= thresholdPx && a < bestAbs) {
                bestAbs = a;
                bestDelta = d;
                hasGuide = true;
                bestGuide = c;
            }
        }
    }
    outDelta = bestDelta;
    outHasGuide = hasGuide;
    outGuide = bestGuide;
}

inline bool isFiniteRect(const AlignRect& r) {
    return std::isfinite(r.left) && std::isfinite(r.top) &&
           std::isfinite(r.right) && std::isfinite(r.bottom);
}

}  // namespace

// ---- Canvas Snap -------------------------------------------------------------------------------

SnapResult snapRect(const SnapRect& rect, float canvasW, float canvasH,
                    const SnapRect* others, int otherCount, float thresholdPx) {
    const int oc = (others && otherCount > 0) ? otherCount : 0;
    std::vector<float> xCand;
    std::vector<float> yCand;
    xCand.reserve(static_cast<size_t>(3 + oc * 3));
    yCand.reserve(static_cast<size_t>(3 + oc * 3));

    xCand.push_back(canvasW * 0.5f);
    xCand.push_back(0.0f);
    xCand.push_back(canvasW);
    yCand.push_back(canvasH * 0.5f);
    yCand.push_back(0.0f);
    yCand.push_back(canvasH);

    for (int i = 0; i < oc; ++i) {
        xCand.push_back(others[i].cx);
        xCand.push_back(others[i].left);
        xCand.push_back(others[i].right);
        yCand.push_back(others[i].cy);
        yCand.push_back(others[i].top);
        yCand.push_back(others[i].bottom);
    }

    SnapResult res;
    const float fx[3] = {rect.cx, rect.left, rect.right};
    const float fy[3] = {rect.cy, rect.top, rect.bottom};
    bestSnapAxis(fx, xCand, thresholdPx, res.dx, res.hasGuideX, res.guideX);
    bestSnapAxis(fy, yCand, thresholdPx, res.dy, res.hasGuideY, res.guideY);
    return res;
}

// ---- Drag Math ---------------------------------------------------------------------------------

void draggedPosition(float startX, float startY, float totalDx, float totalDy,
                     float depthFactor, float& outX, float& outY) {
    const float df = depthFactor < 0.001f ? 0.001f : depthFactor;
    outX = startX + totalDx / df;
    outY = startY + totalDy / df;
}

// ---- Spatial Align / Distribute ----------------------------------------------------------------

std::vector<AlignDelta> spatialDeltas(const AlignRect* rects, int count, int32_t action) {
    std::vector<AlignDelta> out;
    if (!rects || count <= 0) return out;
    if (action == kCanvasCenterH || action == kCanvasCenterV) return out;

    struct Indexed {
        int32_t idx;
        AlignRect r;
        float cx() const { return (r.left + r.right) * 0.5f; }
        float cy() const { return (r.top + r.bottom) * 0.5f; }
    };
    std::vector<Indexed> finite;
    finite.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        if (isFiniteRect(rects[i])) finite.push_back({i, rects[i]});
    }

    if (action == kDistributeH || action == kDistributeV) {
        if (finite.size() < 3) return out;
        const bool horiz = (action == kDistributeH);
        std::stable_sort(finite.begin(), finite.end(), [horiz](const Indexed& a, const Indexed& b) {
            return horiz ? (a.cx() < b.cx()) : (a.cy() < b.cy());
        });
        const float first = horiz ? finite.front().cx() : finite.front().cy();
        const float last = horiz ? finite.back().cx() : finite.back().cy();
        const float span = last - first;
        if (span <= kSpatialEps) return out;
        const float step = span / static_cast<float>(finite.size() - 1);
        out.reserve(finite.size());
        for (size_t i = 0; i < finite.size(); ++i) {
            const float c = horiz ? finite[i].cx() : finite[i].cy();
            const float d = first + step * static_cast<float>(i) - c;
            if (std::fabs(d) > kSpatialEps) {
                out.push_back({finite[i].idx, horiz ? d : 0.0f, horiz ? 0.0f : d});
            }
        }
        return out;
    }

    if (finite.size() < 2) return out;
    float minL = finite[0].r.left;
    float maxR = finite[0].r.right;
    float minT = finite[0].r.top;
    float maxB = finite[0].r.bottom;
    for (size_t i = 1; i < finite.size(); ++i) {
        minL = std::min(minL, finite[i].r.left);
        maxR = std::max(maxR, finite[i].r.right);
        minT = std::min(minT, finite[i].r.top);
        maxB = std::max(maxB, finite[i].r.bottom);
    }
    const float midX = (minL + maxR) * 0.5f;
    const float midY = (minT + maxB) * 0.5f;
    out.reserve(finite.size());
    for (const auto& item : finite) {
        float dx = 0.0f;
        float dy = 0.0f;
        switch (action) {
            case kAlignLeft: dx = minL - item.r.left; break;
            case kAlignRight: dx = maxR - item.r.right; break;
            case kAlignCenterH: dx = midX - item.cx(); break;
            case kAlignTop: dy = minT - item.r.top; break;
            case kAlignBottom: dy = maxB - item.r.bottom; break;
            case kAlignCenterV: dy = midY - item.cy(); break;
            default: break;
        }
        if (std::fabs(dx) > kSpatialEps || std::fabs(dy) > kSpatialEps) {
            out.push_back({item.idx, dx, dy});
        }
    }
    return out;
}

std::vector<AlignDelta> spatialCanvasDeltas(const AlignRect* rects, int count,
                                            float frameW, float frameH, int32_t action) {
    std::vector<AlignDelta> out;
    if (!rects || count <= 0) return out;
    if (action != kCanvasCenterH && action != kCanvasCenterV) return out;
    if (!std::isfinite(frameW) || !std::isfinite(frameH) || frameW <= 0.0f || frameH <= 0.0f) {
        return out;
    }
    const bool horiz = (action == kCanvasCenterH);
    const float target = horiz ? (frameW * 0.5f) : (frameH * 0.5f);
    out.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        const AlignRect& r = rects[i];
        if (!isFiniteRect(r)) continue;
        const float c = horiz ? ((r.left + r.right) * 0.5f) : ((r.top + r.bottom) * 0.5f);
        const float d = target - c;
        if (std::fabs(d) > kSpatialEps) {
            out.push_back({i, horiz ? d : 0.0f, horiz ? 0.0f : d});
        }
    }
    return out;
}

// ---- ParentRig 2D Composition ------------------------------------------------------------------

void parentMove(float x, float y, float refX, float refY, float nx, float ny,
                float ds, float drDeg, float& outX, float& outY) {
    const float vx = x - refX;
    const float vy = y - refY;
    const double rad = static_cast<double>(drDeg) * kDegToRad;
    const float c = static_cast<float>(std::cos(rad));
    const float s = static_cast<float>(std::sin(rad));
    outX = nx + (vx * c - vy * s) * ds;
    outY = ny + (vx * s + vy * c) * ds;
}

void applyParentToFp(float* fp16, float refX, float refY, float refScale, float refRot,
                     float nx, float ny, float nowScale, float nowRot, float k,
                     bool lockScale, bool lockRotate, bool hiddenByGroup) {
    if (!fp16) return;
    const float denom = refScale < 0.01f ? 0.01f : refScale;
    const float ds = nowScale / denom;
    const float dr = nowRot - refRot;
    const float dsOwn = lockScale ? 1.0f : ds;
    const float drOwn = lockRotate ? 0.0f : dr;

    float x = 0.0f, y = 0.0f;
    parentMove(fp16[0], fp16[1], refX * k, refY * k, nx * k, ny * k, ds, dr, x, y);
    fp16[0] = x;
    fp16[1] = y;

    float bx = 0.0f, by = 0.0f;
    parentMove(fp16[14], fp16[15], refX * k, refY * k, nx * k, ny * k, ds, dr, bx, by);
    fp16[14] = bx;
    fp16[15] = by;

    fp16[2] *= dsOwn;
    fp16[3] *= dsOwn;
    fp16[4] += drOwn;
    if (hiddenByGroup) fp16[5] = 0.0f;
}

// ---- Path Flattening & TextOnPath --------------------------------------------------------------

std::vector<float> flattenPathSegments(const int32_t* ops, const int32_t* offsets,
                                       const int32_t* counts, int segCount,
                                       const float* pts, int ptsLen, int stepsPerCurve) {
    std::vector<float> out;
    if (!ops || !offsets || !counts || segCount <= 0) return out;
    const int steps = std::max(1, stepsPerCurve);
    float curX = 0.0f, curY = 0.0f;
    float startX = 0.0f, startY = 0.0f;

    auto push = [&](float x, float y) {
        out.push_back(x);
        out.push_back(y);
    };

    for (int s = 0; s < segCount; ++s) {
        const int op = ops[s];
        const int off = offsets[s];
        const int cnt = counts[s];
        if (off < 0 || cnt < 0 || off + cnt > ptsLen) continue;
        const float* q = pts ? (pts + off) : nullptr;
        switch (op) {
            case kPathOpMove:
                if (q && cnt >= 2) {
                    curX = q[0]; curY = q[1];
                    startX = curX; startY = curY;
                    push(curX, curY);
                }
                break;
            case kPathOpLine:
                if (q && cnt >= 2) {
                    curX = q[0]; curY = q[1];
                    push(curX, curY);
                }
                break;
            case kPathOpQuad:
                if (q && cnt >= 4) {
                    const float cx = q[0], cy = q[1], ex = q[2], ey = q[3];
                    for (int i = 1; i <= steps; ++i) {
                        const float t = static_cast<float>(i) / static_cast<float>(steps);
                        const float u = 1.0f - t;
                        push(u * u * curX + 2.0f * u * t * cx + t * t * ex,
                             u * u * curY + 2.0f * u * t * cy + t * t * ey);
                    }
                    curX = ex; curY = ey;
                }
                break;
            case kPathOpCubic:
                if (q && cnt >= 6) {
                    const float c1x = q[0], c1y = q[1], c2x = q[2], c2y = q[3];
                    const float ex = q[4], ey = q[5];
                    for (int i = 1; i <= steps; ++i) {
                        const float t = static_cast<float>(i) / static_cast<float>(steps);
                        const float u = 1.0f - t;
                        const float u2 = u * u, t2 = t * t;
                        push(u2 * u * curX + 3.0f * u2 * t * c1x + 3.0f * u * t2 * c2x + t2 * t * ex,
                             u2 * u * curY + 3.0f * u2 * t * c1y + 3.0f * u * t2 * c2y + t2 * t * ey);
                    }
                    curX = ex; curY = ey;
                }
                break;
            case kPathOpClose:
                push(startX, startY);
                curX = startX; curY = startY;
                break;
            default:
                break;
        }
    }
    return out;
}

float buildArcTable(const float* inXy, int inPointCount,
                    std::vector<float>& outXy, std::vector<float>& outCum) {
    outXy.clear();
    outCum.clear();
    if (!inXy || inPointCount <= 0) return 0.0f;
    outXy.reserve(static_cast<size_t>(inPointCount) * 2);
    for (int i = 0; i < inPointCount; ++i) {
        const float x = inXy[2 * i];
        const float y = inXy[2 * i + 1];
        if (outXy.empty() || outXy[outXy.size() - 2] != x || outXy[outXy.size() - 1] != y) {
            outXy.push_back(x);
            outXy.push_back(y);
        }
    }
    const int n = static_cast<int>(outXy.size() / 2);
    outCum.assign(static_cast<size_t>(n), 0.0f);
    float acc = 0.0f;
    for (int i = 1; i < n; ++i) {
        acc += std::hypot(outXy[2 * i] - outXy[2 * (i - 1)],
                          outXy[2 * i + 1] - outXy[2 * (i - 1) + 1]);
        outCum[static_cast<size_t>(i)] = acc;
    }
    return acc;
}

void arcPointAt(const float* xy, const float* cum, int pointCount, float total,
                float d, float& outX, float& outY, float& outAngleDeg) {
    if (!xy || !cum || pointCount <= 0) {
        outX = outY = outAngleDeg = 0.0f;
        return;
    }
    if (pointCount == 1 || total <= 0.0f) {
        outX = xy[0];
        outY = xy[1];
        outAngleDeg = 0.0f;
        return;
    }
    const float dd = std::isnan(d) ? 0.0f : std::clamp(d, 0.0f, total);
    int lo = 0;
    int hi = pointCount - 2;
    int seg = pointCount - 2;
    while (lo <= hi) {
        const int mid = (lo + hi) >> 1;
        if (cum[mid + 1] > dd) {
            seg = mid;
            hi = mid - 1;
        } else {
            lo = mid + 1;
        }
    }
    const float ax = xy[2 * seg];
    const float ay = xy[2 * seg + 1];
    const float bx = xy[2 * (seg + 1)];
    const float by = xy[2 * (seg + 1) + 1];
    const float segLen = cum[seg + 1] - cum[seg];
    const float t = segLen > 0.0f ? (dd - cum[seg]) / segLen : 0.0f;
    outX = ax + (bx - ax) * t;
    outY = ay + (by - ay) * t;
    outAngleDeg = static_cast<float>(std::atan2(static_cast<double>(by - ay),
                                                static_cast<double>(bx - ax)) * kRadToDeg);
}

std::vector<PathPlacement> layoutTextOnPath(const float* xy, const float* cum, int pointCount,
                                            float total, const float* advances, int advCount,
                                            float startFrac) {
    std::vector<PathPlacement> out;
    if (!advances || advCount <= 0) return out;
    out.reserve(static_cast<size_t>(advCount));
    float frac = std::isnan(startFrac) ? 0.0f : std::clamp(startFrac, 0.0f, 1.0f);
    float pen = frac * total;
    if (std::isnan(pen)) pen = 0.0f;
    for (int i = 0; i < advCount; ++i) {
        float px = 0.0f, py = 0.0f, ang = 0.0f;
        arcPointAt(xy, cum, pointCount, total, pen, px, py, ang);
        out.push_back({pen, px, py, ang, total > 0.0f && pen < total});
        pen += advances[i];
    }
    return out;
}

// ---- Mask Geometry -----------------------------------------------------------------------------

std::vector<float> flattenMaskControlPoints(const float* ctrlXy, int count, bool smooth,
                                            int maxPoints) {
    std::vector<float> out;
    if (!ctrlXy || count <= 0 || maxPoints <= 0) return out;
    const int n = std::min(count, maxPoints);
    if (n < 3) return out;
    if (!smooth) {
        out.assign(ctrlXy, ctrlXy + static_cast<size_t>(n) * 2);
        return out;
    }
    const int spp = std::max(1, std::min(8, 96 / n));
    out.reserve(static_cast<size_t>(n * spp) * 2);
    auto eval = [](float a, float b, float c, float d, float t, float t2, float t3) {
        return 0.5f * ((2.0f * b) + (-a + c) * t +
                       (2.0f * a - 5.0f * b + 4.0f * c - d) * t2 +
                       (-a + 3.0f * b - 3.0f * c + d) * t3);
    };
    for (int i = 0; i < n; ++i) {
        const int i0 = (i + n - 1) % n;
        const int i1 = i;
        const int i2 = (i + 1) % n;
        const int i3 = (i + 2) % n;
        for (int s = 0; s < spp; ++s) {
            const float t = static_cast<float>(s) / static_cast<float>(spp);
            const float t2 = t * t;
            const float t3 = t2 * t;
            out.push_back(eval(ctrlXy[2 * i0], ctrlXy[2 * i1], ctrlXy[2 * i2], ctrlXy[2 * i3],
                               t, t2, t3));
            out.push_back(eval(ctrlXy[2 * i0 + 1], ctrlXy[2 * i1 + 1],
                               ctrlXy[2 * i2 + 1], ctrlXy[2 * i3 + 1],
                               t, t2, t3));
        }
    }
    return out;
}

bool pointInPolygon(float px, float py, const float* polyXy, int pointCount) {
    if (!polyXy || pointCount < 3) return false;
    bool inside = false;
    int j = pointCount - 1;
    for (int i = 0; i < pointCount; ++i) {
        const float ax = polyXy[2 * i];
        const float ay = polyXy[2 * i + 1];
        const float bx = polyXy[2 * j];
        const float by = polyXy[2 * j + 1];
        if (((ay > py) != (by > py)) &&
            (px < (bx - ax) * (py - ay) / (by - ay) + ax)) {
            inside = !inside;
        }
        j = i;
    }
    return inside;
}

float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
    const float ex = bx - ax;
    const float ey = by - ay;
    const float ee = ex * ex + ey * ey;
    const float t = ee > 1e-12f
        ? std::clamp(((px - ax) * ex + (py - ay) * ey) / ee, 0.0f, 1.0f)
        : 0.0f;
    return std::hypot(px - (ax + ex * t), py - (ay + ey * t));
}

}  // namespace scene
}  // namespace am
