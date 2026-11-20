#include "ui_widgets.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace am {
namespace ui {

namespace {

inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

inline void cubicPoint(
    float x0, float y0, float x1, float y1,
    float x2, float y2, float x3, float y3,
    float t, float& ox, float& oy) {
    const float u = 1.0f - t;
    const float u2 = u * u;
    const float t2 = t * t;
    const float w0 = u2 * u;
    const float w1 = 3.0f * u2 * t;
    const float w2 = 3.0f * u * t2;
    const float w3 = t2 * t;
    ox = w0 * x0 + w1 * x1 + w2 * x2 + w3 * x3;
    oy = w0 * y0 + w1 * y1 + w2 * y2 + w3 * y3;
}

inline void closestPointOnSegment(
    float px, float py,
    float ax, float ay, float bx, float by,
    float& outT, float& outDistSq) {
    const float dx = bx - ax;
    const float dy = by - ay;
    const float len2 = dx * dx + dy * dy;
    float t = 0.0f;
    if (len2 > 1e-8f) {
        t = clampf(((px - ax) * dx + (py - ay) * dy) / len2, 0.0f, 1.0f);
    }
    const float qx = ax + dx * t - px;
    const float qy = ay + dy * t - py;
    outT = t;
    outDistSq = qx * qx + qy * qy;
}

}  // namespace

float quantizeRulerValue(float value, float minVal, float maxVal, float step) {
    if (minVal > maxVal) std::swap(minVal, maxVal);
    if (step <= 1e-6f) return clampf(value, minVal, maxVal);
    const float q = std::round((value - minVal) / step) * step + minVal;
    return clampf(q, minVal, maxVal);
}

float rulerDragDelta(float pos, float dxPx, float tickPx, float minVal, float maxVal, float step) {
    const float spacing = std::max(1.0f, tickPx);
    const float next = clampf(pos + (dxPx / spacing) * step, minVal, maxVal);
    return next;
}

float rulerFlingEval(
    float startVal,
    float velocityUnitsPerSec,
    float elapsedSec,
    float frictionMultiplier,
    float step,
    float minVal,
    float maxVal,
    bool* outDone) {
    // Compose FloatExponentialDecaySpec uses k = -4.2f * frictionMultiplier (sec^-1).
    const float k = -4.2f * std::max(0.05f, frictionMultiplier);
    const float t = std::max(0.0f, elapsedSec);
    const float expKt = std::exp(k * t);
    const float vNow = velocityUnitsPerSec * expKt;
    const float raw = startVal + (velocityUnitsPerSec / k) * (expKt - 1.0f);
    const float clamped = clampf(raw, minVal, maxVal);
    const float absThresh = std::max(0.01f, step * 2.0f);
    if (outDone) {
        *outDone = (std::fabs(vNow) <= absThresh) || (clamped <= minVal) || (clamped >= maxVal);
    }
    return quantizeRulerValue(clamped, minVal, maxVal, step);
}

float cropLockK(int presetKind, float customNum, float customDen, float srcAspect, float projAspect) {
    const float safeSrc = srcAspect > 1e-5f ? srcAspect : 1.0f;
    float pixelRatio = 0.0f;
    switch (presetKind) {
        case 1:  // Original
            pixelRatio = safeSrc;
            break;
        case 2:  // Project
            pixelRatio = projAspect > 1e-5f ? projAspect : 1.0f;
            break;
        case 3:  // Custom ratio
            if (customNum <= 0.0f || customDen <= 0.0f) return 0.0f;
            pixelRatio = customNum / customDen;
            break;
        default: // Free
            return 0.0f;
    }
    return pixelRatio / safeSrc;
}

void cropRectFromProps(float wPct, float hPct, float hPosPct, float vPosPct, float outLtrb[4]) {
    const float w = wPct / 100.0f;
    const float h = hPct / 100.0f;
    const float l = (1.0f - w) * hPosPct / 100.0f;
    const float t = (1.0f - h) * vPosPct / 100.0f;
    outLtrb[0] = l;
    outLtrb[1] = t;
    outLtrb[2] = l + w;
    outLtrb[3] = t + h;
}

void cropPropsFromRect(float l, float t, float r, float b, float minSize, float outProps[4]) {
    const float ms = clampf(minSize, 0.01f, 0.99f);
    const float w = clampf(r - l, ms, 1.0f);
    const float h = clampf(b - t, ms, 1.0f);
    const float nl = clampf(l, 0.0f, 1.0f - w);
    const float nt = clampf(t, 0.0f, 1.0f - h);
    outProps[0] = w * 100.0f;
    outProps[1] = h * 100.0f;
    outProps[2] = (1.0f - w > 1e-4f) ? clampf(nl / (1.0f - w) * 100.0f, 0.0f, 100.0f) : 50.0f;
    outProps[3] = (1.0f - h > 1e-4f) ? clampf(nt / (1.0f - h) * 100.0f, 0.0f, 100.0f) : 50.0f;
}

void cropResizeAroundCenter(
    float curW, float curH, float curL, float curT, float curR, float curB,
    float newW, float newH, float lockK, float minSize, float outProps[4]) {
    (void)curW;
    (void)curH;
    float w = (newW >= 0.0f) ? newW : (curR - curL);
    float h = (newH >= 0.0f) ? newH : (curB - curT);
    if (lockK > 1e-5f) {
        if (newW >= 0.0f) h = w / lockK;
        else w = h * lockK;
        if (h > 1.0f) { h = 1.0f; w = h * lockK; }
        if (w > 1.0f) { w = 1.0f; h = w / lockK; }
    }
    w = clampf(w, minSize, 1.0f);
    h = clampf(h, minSize, 1.0f);
    const float cx = (curL + curR) * 0.5f;
    const float cy = (curT + curB) * 0.5f;
    const float nl = clampf(cx - w * 0.5f, 0.0f, 1.0f - w);
    const float nt = clampf(cy - h * 0.5f, 0.0f, 1.0f - h);
    cropPropsFromRect(nl, nt, nl + w, nt + h, minSize, outProps);
}

int cropHitTest(float px, float py, float l, float t, float r, float b, float tol) {
    const bool nearL = std::fabs(px - l) <= tol && py >= t - tol && py <= b + tol;
    const bool nearR = std::fabs(px - r) <= tol && py >= t - tol && py <= b + tol;
    const bool nearT = std::fabs(py - t) <= tol && px >= l - tol && px <= r + tol;
    const bool nearB = std::fabs(py - b) <= tol && px >= l - tol && px <= r + tol;
    int m = (nearL ? 1 : 0) | (nearR ? 2 : 0) | (nearT ? 4 : 0) | (nearB ? 8 : 0);
    if (m != 0) return m;
    if (px >= l && px <= r && py >= t && py <= b) return 0;
    return -1;
}

void cropDrag(
    int mode, float dxNorm, float dyNorm,
    float l, float t, float r, float b,
    float lockK, float minSize, float outProps[4]) {
    if (mode == 0) {
        const float w = r - l;
        const float h = b - t;
        const float nl = clampf(l + dxNorm, 0.0f, 1.0f - w);
        const float nt = clampf(t + dyNorm, 0.0f, 1.0f - h);
        cropPropsFromRect(nl, nt, nl + w, nt + h, minSize, outProps);
        return;
    }
    const bool mL = (mode & 1) != 0;
    const bool mR = (mode & 2) != 0;
    const bool mT = (mode & 4) != 0;
    const bool mB = (mode & 8) != 0;
    if (lockK <= 1e-5f) {
        if (mL) l = clampf(l + dxNorm, 0.0f, r - minSize);
        if (mR) r = clampf(r + dxNorm, l + minSize, 1.0f);
        if (mT) t = clampf(t + dyNorm, 0.0f, b - minSize);
        if (mB) b = clampf(b + dyNorm, t + minSize, 1.0f);
        cropPropsFromRect(l, t, r, b, minSize, outProps);
        return;
    }
    const bool isCorner = (mL || mR) && (mT || mB);
    if (isCorner) {
        const float ax = mL ? r : l;
        const float ay = mT ? b : t;
        const float mx = clampf(mL ? l + dxNorm : r + dxNorm, 0.0f, 1.0f);
        const float my = clampf(mT ? t + dyNorm : b + dyNorm, 0.0f, 1.0f);
        float w = std::max(std::fabs(mx - ax), std::fabs(my - ay) * lockK);
        const float maxW = std::min(mL ? ax : 1.0f - ax, (mT ? ay : 1.0f - ay) * lockK);
        w = std::max(std::min(w, maxW), std::min(minSize, maxW));
        const float h = w / lockK;
        l = mL ? ax - w : ax;
        r = l + w;
        t = mT ? ay - h : ay;
        b = t + h;
    } else if (mL || mR) {
        const float cy = (t + b) * 0.5f;
        const float rawW = mL
            ? (r - clampf(l + dxNorm, 0.0f, r - minSize))
            : (clampf(r + dxNorm, l + minSize, 1.0f) - l);
        const float maxH = 2.0f * std::min(cy, 1.0f - cy);
        const float h = std::max(std::min(rawW / lockK, maxH), std::min(minSize, maxH));
        const float w = h * lockK;
        if (mL) l = r - w;
        else r = l + w;
        t = cy - h * 0.5f;
        b = t + h;
    } else {
        const float cx = (l + r) * 0.5f;
        const float rawH = mT
            ? (b - clampf(t + dyNorm, 0.0f, b - minSize))
            : (clampf(b + dyNorm, t + minSize, 1.0f) - t);
        const float maxW = 2.0f * std::min(cx, 1.0f - cx);
        const float w = std::max(std::min(rawH * lockK, maxW), std::min(minSize, maxW));
        const float h = w / lockK;
        if (mT) t = b - h;
        else b = t + h;
        l = cx - w * 0.5f;
        r = l + w;
    }
    cropPropsFromRect(l, t, r, b, minSize, outProps);
}

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
    float outXy[2]) {
    const float s = std::max(0.01f, scalePct / 100.0f);
    const float sx = s * (flipH ? -1.0f : 1.0f);
    const float sy = s * (flipV ? -1.0f : 1.0f);
    const float rad = rotDeg * 0.017453292519943295f;
    const float cs = std::cos(rad);
    const float sn = std::sin(rad);
    const float pvx = (pivotX - 0.5f) * boxW;
    const float pvy = (pivotY - 0.5f) * boxH;
    const float ox = frameW * 0.5f + depthX + pvx;
    const float oy = frameH * 0.5f + depthY + pvy;

    if (!inverse) {
        const float lx = ((inX - cx) - pvx) * sx;
        const float ly = ((inY - cy) - pvy) * sy;
        outXy[0] = ox + lx * cs - ly * sn;
        outXy[1] = oy + lx * sn + ly * cs;
    } else {
        const float dx = inX - ox;
        const float dy = inY - oy;
        const float rx = dx * cs + dy * sn;
        const float ry = -dx * sn + dy * cs;
        outXy[0] = rx / sx + pvx + cx;
        outXy[1] = ry / sy + pvy + cy;
    }
}

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
    float outResult[4]) {
    outResult[0] = static_cast<float>(NODE_HIT_NONE);
    outResult[1] = -1.0f;
    outResult[2] = -1.0f;
    outResult[3] = 0.0f;
    const float r2 = radiusPx * radiusPx;

    // 1. Selected anchor's handles take top priority
    if (selectedIdx >= 0 && selectedIdx < anchorCount) {
        if (hasIn && hasIn[selectedIdx] && inCanvasXy) {
            const float dx = px - inCanvasXy[selectedIdx * 2];
            const float dy = py - inCanvasXy[selectedIdx * 2 + 1];
            if (dx * dx + dy * dy <= r2) {
                outResult[0] = static_cast<float>(NODE_HIT_IN_HANDLE);
                outResult[1] = static_cast<float>(selectedIdx);
                return;
            }
        }
        if (hasOut && hasOut[selectedIdx] && outCanvasXy) {
            const float dx = px - outCanvasXy[selectedIdx * 2];
            const float dy = py - outCanvasXy[selectedIdx * 2 + 1];
            if (dx * dx + dy * dy <= r2) {
                outResult[0] = static_cast<float>(NODE_HIT_OUT_HANDLE);
                outResult[1] = static_cast<float>(selectedIdx);
                return;
            }
        }
    }

    // 2. Anchor points
    if (anchorCanvasXy && anchorCount > 0) {
        float bestD2 = r2;
        int bestIdx = -1;
        for (int i = 0; i < anchorCount; ++i) {
            const float dx = px - anchorCanvasXy[i * 2];
            const float dy = py - anchorCanvasXy[i * 2 + 1];
            const float d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) {
                bestD2 = d2;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            outResult[0] = static_cast<float>(NODE_HIT_ANCHOR);
            outResult[1] = static_cast<float>(bestIdx);
            return;
        }
    }

    // 3. Flattened edge / cubic segments (nearest point within radiusPx)
    if (!segTypes || !segCanvasPts || segCount <= 0) return;
    const float edgeR2 = (radiusPx * 0.75f) * (radiusPx * 0.75f);
    float bestD2 = edgeR2;
    int bestSegIdx = -1;
    float bestT = 0.0f;

    int ptIdx = 0;
    float curX = 0.0f, curY = 0.0f;
    float moveX = 0.0f, moveY = 0.0f;
    int edgeCounter = 0;

    for (int s = 0; s < segCount; ++s) {
        const int op = segTypes[s];
        if (op == 0) {  // MOVE
            curX = segCanvasPts[ptIdx++];
            curY = segCanvasPts[ptIdx++];
            moveX = curX;
            moveY = curY;
        } else if (op == 1) {  // LINE
            const float nx = segCanvasPts[ptIdx++];
            const float ny = segCanvasPts[ptIdx++];
            float t = 0.0f, d2 = 0.0f;
            closestPointOnSegment(px, py, curX, curY, nx, ny, t, d2);
            if (d2 <= bestD2) {
                bestD2 = d2;
                bestSegIdx = edgeCounter;
                bestT = t;
            }
            curX = nx;
            curY = ny;
            ++edgeCounter;
        } else if (op == 2) {  // CUBIC
            const float c1x = segCanvasPts[ptIdx++];
            const float c1y = segCanvasPts[ptIdx++];
            const float c2x = segCanvasPts[ptIdx++];
            const float c2y = segCanvasPts[ptIdx++];
            const float nx = segCanvasPts[ptIdx++];
            const float ny = segCanvasPts[ptIdx++];
            constexpr int kSub = 14;
            float ax = curX, ay = curY;
            for (int k = 1; k <= kSub; ++k) {
                const float tEnd = static_cast<float>(k) / static_cast<float>(kSub);
                float bx = 0.0f, by = 0.0f;
                cubicPoint(curX, curY, c1x, c1y, c2x, c2y, nx, ny, tEnd, bx, by);
                float localT = 0.0f, d2 = 0.0f;
                closestPointOnSegment(px, py, ax, ay, bx, by, localT, d2);
                if (d2 <= bestD2) {
                    bestD2 = d2;
                    bestSegIdx = edgeCounter;
                    bestT = ((k - 1) + localT) / static_cast<float>(kSub);
                }
                ax = bx;
                ay = by;
            }
            curX = nx;
            curY = ny;
            ++edgeCounter;
        } else if (op == 3) {  // CLOSE
            float t = 0.0f, d2 = 0.0f;
            closestPointOnSegment(px, py, curX, curY, moveX, moveY, t, d2);
            if (d2 <= bestD2) {
                bestD2 = d2;
                bestSegIdx = edgeCounter;
                bestT = t;
            }
            curX = moveX;
            curY = moveY;
            ++edgeCounter;
        }
    }

    if (bestSegIdx >= 0) {
        outResult[0] = static_cast<float>(NODE_HIT_EDGE);
        outResult[2] = static_cast<float>(bestSegIdx);
        outResult[3] = bestT;
    }
}

int planarHitTest(float fx, float fy, const float quad8[8], float radiusNorm) {
    if (!quad8) return -1;
    const float r2 = radiusNorm * radiusNorm;
    int best = -1;
    float bestD2 = r2;
    for (int i = 0; i < 4; ++i) {
        const float dx = fx - quad8[i * 2];
        const float dy = fy - quad8[i * 2 + 1];
        const float d2 = dx * dx + dy * dy;
        if (d2 <= bestD2) {
            bestD2 = d2;
            best = i;
        }
    }
    if (best >= 0) return best;

    int sign = 0;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) % 4;
        const float ax = quad8[i * 2], ay = quad8[i * 2 + 1];
        const float bx = quad8[j * 2], by = quad8[j * 2 + 1];
        const float cr = (bx - ax) * (fy - ay) - (by - ay) * (fx - ax);
        const int sg = (cr > 0.0f) ? 1 : (cr < 0.0f ? -1 : 0);
        if (sg == 0) continue;
        if (sign == 0) sign = sg;
        else if (sg != sign) return -1;
    }
    return 4;
}

void planarMove(const float quad8[8], float dx, float dy, float outQuad8[8]) {
    if (!quad8 || !outQuad8) return;
    float minX = quad8[0], maxX = quad8[0];
    float minY = quad8[1], maxY = quad8[1];
    for (int i = 1; i < 4; ++i) {
        minX = std::min(minX, quad8[i * 2]);
        maxX = std::max(maxX, quad8[i * 2]);
        minY = std::min(minY, quad8[i * 2 + 1]);
        maxY = std::max(maxY, quad8[i * 2 + 1]);
    }
    const float cx = clampf(dx, -minX, 1.0f - maxX);
    const float cy = clampf(dy, -minY, 1.0f - maxY);
    for (int i = 0; i < 4; ++i) {
        outQuad8[i * 2] = quad8[i * 2] + cx;
        outQuad8[i * 2 + 1] = quad8[i * 2 + 1] + cy;
    }
}

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
    float pivotX, float pivotY) {
    const float s = std::max(minScale, scalePct / 100.0f);
    const float sx = s * (flipH ? -1.0f : 1.0f);
    const float sy = s * (flipV ? -1.0f : 1.0f);
    const float rad = rotDeg * 0.017453292519943295f;
    const float cs = std::cos(rad);
    const float sn = std::sin(rad);
    const float refW = (pivotRefW > 0.0f) ? pivotRefW : boxW;
    const float refH = (pivotRefH > 0.0f) ? pivotRefH : boxH;
    const float pvx = (pivotX - 0.5f) * refW;
    const float pvy = (pivotY - 0.5f) * refH;
    const float ox = frameW * 0.5f + pvx + depthX;
    const float oy = frameH * 0.5f + pvy + depthY;

    const float hw = visW * 0.5f;
    const float hh = visH * 0.5f;
    const float localCorners[4][2] = {
        {visOffX - hw, visOffY - hh},
        {visOffX + hw, visOffY - hh},
        {visOffX + hw, visOffY + hh},
        {visOffX - hw, visOffY + hh}
    };
    float cxy[4][2];
    for (int i = 0; i < 4; ++i) {
        const float x = (localCorners[i][0] - pvx) * sx;
        const float y = (localCorners[i][1] - pvy) * sy;
        cxy[i][0] = ox + x * cs - y * sn;
        cxy[i][1] = oy + x * sn + y * cs;
    }
    bool pos = false, neg = false;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) % 4;
        const float cr = (cxy[j][0] - cxy[i][0]) * (py - cxy[i][1]) -
                         (cxy[j][1] - cxy[i][1]) * (px - cxy[i][0]);
        if (cr > 0.0f) pos = true;
        else if (cr < 0.0f) neg = true;
    }
    return !(pos && neg);
}

void hsvToRgb(float hDeg, float s, float v, float outRgb[3]) {
    float h = std::fmod(hDeg, 360.0f);
    if (h < 0.0f) h += 360.0f;
    const float sat = clampf(s, 0.0f, 1.0f);
    const float val = clampf(v, 0.0f, 1.0f);
    const float c = val * sat;
    const float x = c * (1.0f - std::fabs(std::fmod(h / 60.0f, 2.0f) - 1.0f));
    const float m = val - c;
    float r = 0.0f, g = 0.0f, b = 0.0f;
    if (h < 60.0f)      { r = c; g = x; }
    else if (h < 120.0f) { r = x; g = c; }
    else if (h < 180.0f) { g = c; b = x; }
    else if (h < 240.0f) { g = x; b = c; }
    else if (h < 300.0f) { r = x; b = c; }
    else                 { r = c; b = x; }
    outRgb[0] = clampf(r + m, 0.0f, 1.0f);
    outRgb[1] = clampf(g + m, 0.0f, 1.0f);
    outRgb[2] = clampf(b + m, 0.0f, 1.0f);
}

void rgbToHsv(float r, float g, float b, float outHsv[3]) {
    const float rr = clampf(r, 0.0f, 1.0f);
    const float gg = clampf(g, 0.0f, 1.0f);
    const float bb = clampf(b, 0.0f, 1.0f);
    const float mx = std::max(rr, std::max(gg, bb));
    const float mn = std::min(rr, std::min(gg, bb));
    const float d = mx - mn;
    float h = 0.0f;
    if (d > 1e-6f) {
        if (mx == rr) h = 60.0f * std::fmod((gg - bb) / d + 6.0f, 6.0f);
        else if (mx == gg) h = 60.0f * ((bb - rr) / d + 2.0f);
        else h = 60.0f * ((rr - gg) / d + 4.0f);
    }
    if (h >= 360.0f) h -= 360.0f;
    outHsv[0] = h;
    outHsv[1] = (mx > 1e-6f) ? (d / mx) : 0.0f;
    outHsv[2] = mx;
}

}  // namespace ui
}  // namespace am
