#include "lk_tracker.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <numeric>

namespace am {

namespace {
constexpr double kRadToDeg = 180.0 / 3.14159265358979323846;
}  // namespace

LkCircleTracker::LkCircleTracker(int maxPoints)
    : maxPoints_(std::max(kMinPoints, maxPoints)),
      pts_(static_cast<size_t>(std::max(kMinPoints, maxPoints)) * 2, 0.0f) {}

int LkCircleTracker::copyPoints(float* out, int maxCount) const {
    if (!out || maxCount <= 0 || n_ <= 0) return 0;
    const int k = std::min(n_, maxCount);
    std::copy(pts_.begin(), pts_.begin() + static_cast<ptrdiff_t>(2 * k), out);
    return k;
}

bool LkCircleTracker::start(const float* gray, int w, int h, float cx, float cy, float radius) {
    if (!gray || w <= 0 || h <= 0) return false;
    cx_ = cx;
    cy_ = cy;
    radius_ = std::max(4.0f, radius);
    rotationDeg_ = 0.0f;
    scaleTotal_ = 1.0f;
    n_ = 0;
    sinceReseed_ = 0;
    misses_ = 0;
    prev_ = buildPyramid(gray, w, h, kMaxLevels);
    if (prev_.empty()) return false;
    detect(prev_[0]);
    startCount_ = n_;
    return n_ >= kMinPoints;
}

bool LkCircleTracker::step(const float* gray, int w, int h) {
    if (!gray || w <= 0 || h <= 0 || prev_.empty()) return false;
    if (prev_[0].w != w || prev_[0].h != h) return false;
    std::vector<Level> b = buildPyramid(gray, w, h, kMaxLevels);

    std::vector<float> dxs(static_cast<size_t>(n_), 0.0f);
    std::vector<float> dys(static_cast<size_t>(n_), 0.0f);
    std::vector<uint8_t> ok(static_cast<size_t>(n_), 0);
    int good = 0;
    const float maxJump = 0.4f * static_cast<float>(std::max(w, h));

    for (int i = 0; i < n_; ++i) {
        const float x = pts_[2 * i];
        const float y = pts_[2 * i + 1];
        float fx = 0.0f, fy = 0.0f;
        if (!trackPoint(prev_, b, x, y, fx, fy)) continue;
        float rx = 0.0f, ry = 0.0f;
        if (!trackPoint(b, prev_, fx, fy, rx, ry)) continue;
        const float ex = rx - x;
        const float ey = ry - y;
        if (ex * ex + ey * ey > kFbTol * kFbTol) continue;
        const float dx = fx - x;
        const float dy = fy - y;
        if (std::fabs(dx) > maxJump || std::fabs(dy) > maxJump) continue;
        dxs[i] = dx;
        dys[i] = dy;
        ok[i] = 1;
        ++good;
    }
    if (good < kMinPoints) return miss();

    Similarity motion;
    if (!estimateSimilarity(dxs.data(), dys.data(), ok.data(), good, motion) ||
        motion.inliers < kMinPoints) {
        return miss();
    }

    const float aMotion = motion.a;
    const float bMotion = motion.b;
    const float tx = motion.tx;
    const float ty = motion.ty;
    const float modelScale = std::sqrt(aMotion * aMotion + bMotion * bMotion);
    if (!std::isfinite(modelScale) || modelScale < kMinScale || modelScale > kMaxScale) {
        return miss();
    }

    int kept = 0;
    std::vector<float> newPts(static_cast<size_t>(maxPoints_) * 2, 0.0f);
    const float tol2 = motion.tolerance * motion.tolerance;
    for (int i = 0; i < n_; ++i) {
        if (!ok[i]) continue;
        const float x = pts_[2 * i];
        const float y = pts_[2 * i + 1];
        const float qx = aMotion * x - bMotion * y + tx;
        const float qy = bMotion * x + aMotion * y + ty;
        const float ex = x + dxs[i] - qx;
        const float ey = y + dys[i] - qy;
        if (ex * ex + ey * ey > tol2) continue;
        newPts[2 * kept] = x + dxs[i];
        newPts[2 * kept + 1] = y + dys[i];
        ++kept;
    }
    if (kept < kMinPoints) return miss();
    misses_ = 0;

    const float ncx = aMotion * cx_ - bMotion * cy_ + tx;
    const float ncy = bMotion * cx_ + aMotion * cy_ + ty;
    if (ncx < -radius_ || ncy < -radius_ ||
        ncx > static_cast<float>(w) + radius_ || ncy > static_cast<float>(h) + radius_) {
        return false;
    }

    cx_ = ncx;
    cy_ = ncy;
    rotationDeg_ += static_cast<float>(std::atan2(static_cast<double>(bMotion),
                                                  static_cast<double>(aMotion)) * kRadToDeg);
    scaleTotal_ *= modelScale;
    radius_ = std::clamp(radius_ * modelScale, 4.0f, static_cast<float>(std::max(w, h)));

    int m = 0;
    const float lim = (radius_ * 1.25f) * (radius_ * 1.25f);
    for (int i = 0; i < kept; ++i) {
        const float x = newPts[2 * i];
        const float y = newPts[2 * i + 1];
        const float ddx = x - cx_;
        const float ddy = y - cy_;
        if (ddx * ddx + ddy * ddy > lim) continue;
        pts_[2 * m] = x;
        pts_[2 * m + 1] = y;
        ++m;
    }
    n_ = m;
    prev_ = std::move(b);

    ++sinceReseed_;
    const int reseedBelow = std::max(kMinPoints + 4, static_cast<int>(startCount_ * 0.85f));
    if (n_ < reseedBelow && sinceReseed_ >= kReseedEvery) {
        sinceReseed_ = 0;
        detect(prev_[0]);
    }
    return n_ >= kMinPoints;
}

bool LkCircleTracker::estimateSimilarity(const float* dxs, const float* dys, const uint8_t* ok,
                                         int good, Similarity& out) const {
    if (good < kMinPoints) return false;
    const float mdx = median(dxs, ok, n_, good);
    const float mdy = median(dys, ok, n_, good);
    const float tolerance = 2.75f + 0.025f * std::sqrt(mdx * mdx + mdy * mdy);
    const float tol2 = tolerance * tolerance;

    int bestCount = 0;
    float bestError = std::numeric_limits<float>::infinity();
    float ba = 1.0f, bb = 0.0f, btx = mdx, bty = mdy;

    for (int i = 0; i < n_; ++i) {
        if (!ok[i]) continue;
        const float x1 = pts_[2 * i];
        const float y1 = pts_[2 * i + 1];
        const float qx1 = x1 + dxs[i];
        const float qy1 = y1 + dys[i];
        for (int j = i + 1; j < n_; ++j) {
            if (!ok[j]) continue;
            const float ox = pts_[2 * j] - x1;
            const float oy = pts_[2 * j + 1] - y1;
            const float denom = ox * ox + oy * oy;
            if (denom < 16.0f) continue;
            const float nx = (pts_[2 * j] + dxs[j]) - qx1;
            const float ny = (pts_[2 * j + 1] + dys[j]) - qy1;
            const float a = (ox * nx + oy * ny) / denom;
            const float b = (ox * ny - oy * nx) / denom;
            const float scale2 = a * a + b * b;
            if (scale2 < kMinScale * kMinScale || scale2 > kMaxScale * kMaxScale) continue;
            const float tx = qx1 - (a * x1 - b * y1);
            const float ty = qy1 - (b * x1 + a * y1);
            int count = 0;
            float error = 0.0f;
            for (int k = 0; k < n_; ++k) {
                if (!ok[k]) continue;
                const float x = pts_[2 * k];
                const float y = pts_[2 * k + 1];
                const float ex = x + dxs[k] - (a * x - b * y + tx);
                const float ey = y + dys[k] - (b * x + a * y + ty);
                const float e2 = ex * ex + ey * ey;
                if (e2 <= tol2) {
                    ++count;
                    error += e2;
                }
            }
            if (count > bestCount || (count == bestCount && error < bestError)) {
                bestCount = count;
                bestError = error;
                ba = a;
                bb = b;
                btx = tx;
                bty = ty;
            }
        }
    }
    if (bestCount < kMinPoints) return false;

    float sx = 0.0f, sy = 0.0f, sqx = 0.0f, sqy = 0.0f;
    int count = 0;
    for (int i = 0; i < n_; ++i) {
        if (!ok[i]) continue;
        const float x = pts_[2 * i];
        const float y = pts_[2 * i + 1];
        const float qx = x + dxs[i];
        const float qy = y + dys[i];
        const float ex = qx - (ba * x - bb * y + btx);
        const float ey = qy - (bb * x + ba * y + bty);
        if (ex * ex + ey * ey > tol2) continue;
        sx += x;
        sy += y;
        sqx += qx;
        sqy += qy;
        ++count;
    }
    if (count < kMinPoints) return false;

    const float mx = sx / static_cast<float>(count);
    const float my = sy / static_cast<float>(count);
    const float mqx = sqx / static_cast<float>(count);
    const float mqy = sqy / static_cast<float>(count);
    float den = 0.0f, numA = 0.0f, numB = 0.0f;
    for (int i = 0; i < n_; ++i) {
        if (!ok[i]) continue;
        const float x = pts_[2 * i];
        const float y = pts_[2 * i + 1];
        const float qx = x + dxs[i];
        const float qy = y + dys[i];
        const float ex = qx - (ba * x - bb * y + btx);
        const float ey = qy - (bb * x + ba * y + bty);
        if (ex * ex + ey * ey > tol2) continue;
        const float px = x - mx;
        const float py = y - my;
        const float ux = qx - mqx;
        const float uy = qy - mqy;
        den += px * px + py * py;
        numA += px * ux + py * uy;
        numB += px * uy - py * ux;
    }
    if (den < 1e-4f) return false;

    out.a = numA / den;
    out.b = numB / den;
    out.tx = mqx - (out.a * mx - out.b * my);
    out.ty = mqy - (out.b * mx + out.a * my);
    out.inliers = count;
    out.tolerance = tolerance;
    return true;
}

void LkCircleTracker::detect(const Level& l) {
    const int margin = kWin / 2 + 2;
    const int x0 = std::max(margin, static_cast<int>(cx_ - radius_));
    const int y0 = std::max(margin, static_cast<int>(cy_ - radius_));
    const int x1 = std::min(l.w - 1 - margin, static_cast<int>(cx_ + radius_));
    const int y1 = std::min(l.h - 1 - margin, static_cast<int>(cy_ + radius_));
    if (x1 <= x0 || y1 <= y0) return;
    const int bw = x1 - x0 + 1;
    const int bh = y1 - y0 + 1;

    constexpr int win = 2;
    constexpr float invWinArea = 1.0f / static_cast<float>((2 * win + 1) * (2 * win + 1));
    std::vector<float> eig(static_cast<size_t>(bw) * static_cast<size_t>(bh), 0.0f);
    const float r2 = radius_ * radius_;
    for (int y = y0; y <= y1; ++y) {
        for (int x = x0; x <= x1; ++x) {
            const float ddx = static_cast<float>(x) - cx_;
            const float ddy = static_cast<float>(y) - cy_;
            if (ddx * ddx + ddy * ddy > r2) continue;
            float gxx = 0.0f, gxy = 0.0f, gyy = 0.0f;
            for (int yy = y - win; yy <= y + win; ++yy) {
                const int row = yy * l.w;
                for (int xx = x - win; xx <= x + win; ++xx) {
                    const float gx = l.ix[row + xx];
                    const float gy = l.iy[row + xx];
                    gxx += gx * gx;
                    gxy += gx * gy;
                    gyy += gy * gy;
                }
            }
            const float tr = gxx + gyy;
            const float det = gxx * gyy - gxy * gxy;
            const float disc = std::sqrt(std::max(0.0f, tr * tr * 0.25f - det));
            eig[static_cast<size_t>(y - y0) * bw + (x - x0)] = (tr * 0.5f - disc) * invWinArea;
        }
    }

    const float cell = std::max(3.0f, radius_ * 0.18f);
    const int cols = static_cast<int>(static_cast<float>(bw) / cell) + 1;
    const int rows = static_cast<int>(static_cast<float>(bh) / cell) + 1;
    const size_t totalCells = static_cast<size_t>(cols) * static_cast<size_t>(rows);
    std::vector<float> bestEig(totalCells, 0.0f);
    std::vector<int> bestX(totalCells, 0);
    std::vector<int> bestY(totalCells, 0);
    float maxEig = 0.0f;
    for (int y = 0; y < bh; ++y) {
        for (int x = 0; x < bw; ++x) {
            const float e = eig[static_cast<size_t>(y) * bw + x];
            if (e <= 0.0f) continue;
            const int ci = static_cast<int>(static_cast<float>(y) / cell) * cols +
                           static_cast<int>(static_cast<float>(x) / cell);
            if (e > bestEig[ci]) {
                bestEig[ci] = e;
                bestX[ci] = x + x0;
                bestY[ci] = y + y0;
            }
            if (e > maxEig) maxEig = e;
        }
    }
    if (maxEig < kMinEig) return;
    const float floorVal = std::max(kMinEig, maxEig * 0.05f);
    std::vector<int> order;
    order.reserve(totalCells);
    for (size_t ci = 0; ci < totalCells; ++ci) {
        if (bestEig[ci] >= floorVal) order.push_back(static_cast<int>(ci));
    }
    std::stable_sort(order.begin(), order.end(), [&](int a, int b) {
        return bestEig[a] > bestEig[b];
    });

    const float minD2 = cell * cell * 0.5f;
    for (int ci : order) {
        if (n_ >= maxPoints_) break;
        const float px = static_cast<float>(bestX[ci]);
        const float py = static_cast<float>(bestY[ci]);
        bool close = false;
        for (int j = 0; j < n_; ++j) {
            const float ddx = pts_[2 * j] - px;
            const float ddy = pts_[2 * j + 1] - py;
            if (ddx * ddx + ddy * ddy < minD2) {
                close = true;
                break;
            }
        }
        if (close) continue;
        pts_[2 * n_] = px;
        pts_[2 * n_ + 1] = py;
        ++n_;
    }
}

bool LkCircleTracker::trackPoint(const std::vector<Level>& a, const std::vector<Level>& b,
                                 float x, float y, float& outX, float& outY) {
    float gx = 0.0f;
    float gy = 0.0f;
    constexpr int half = kWin / 2;
    const int levels = static_cast<int>(std::min(a.size(), b.size()));

    for (int lv = levels - 1; lv >= 0; --lv) {
        const float scale = 1.0f / static_cast<float>(1 << lv);
        const float px = x * scale;
        const float py = y * scale;
        const Level& la = a[lv];
        const Level& lb = b[lv];

        float gxx = 0.0f, gxy = 0.0f, gyy = 0.0f;
        int k = 0;
        for (int dy = -half; dy <= half; ++dy) {
            for (int dx = -half; dx <= half; ++dx) {
                const float t = sample(la.img.data(), la.w, la.h, px + dx, py + dy);
                const float ax = sample(la.ix.data(), la.w, la.h, px + dx, py + dy);
                const float ay = sample(la.iy.data(), la.w, la.h, px + dx, py + dy);
                tpl_[k] = t;
                tgx_[k] = ax;
                tgy_[k] = ay;
                gxx += ax * ax;
                gxy += ax * ay;
                gyy += ay * ay;
                ++k;
            }
        }
        const float det = gxx * gyy - gxy * gxy;
        if (lv == 0) {
            const float tr = gxx + gyy;
            const float disc = std::sqrt(std::max(0.0f, tr * tr * 0.25f - det));
            const float minEig = (tr * 0.5f - disc) / static_cast<float>(kWin * kWin);
            if (minEig < kMinEig * 0.25f) return false;
        }
        if (det < 1e-6f) {
            if (lv > 0) {
                gx *= 2.0f;
                gy *= 2.0f;
            }
            continue;
        }

        float vx = 0.0f, vy = 0.0f;
        for (int iter = 0; iter < kMaxIter; ++iter) {
            const float qx = px + gx + vx;
            const float qy = py + gy + vy;
            if (qx < 0.0f || qy < 0.0f ||
                qx > static_cast<float>(lb.w - 1) || qy > static_cast<float>(lb.h - 1)) {
                return false;
            }
            float bx = 0.0f, by = 0.0f;
            k = 0;
            for (int dy = -half; dy <= half; ++dy) {
                for (int dx = -half; dx <= half; ++dx) {
                    const float d = tpl_[k] - sample(lb.img.data(), lb.w, lb.h, qx + dx, qy + dy);
                    bx += d * tgx_[k];
                    by += d * tgy_[k];
                    ++k;
                }
            }
            const float ddx = (gyy * bx - gxy * by) / det;
            const float ddy = (-gxy * bx + gxx * by) / det;
            vx += ddx;
            vy += ddy;
            if (ddx * ddx + ddy * ddy < kEps * kEps) break;
        }
        gx += vx;
        gy += vy;
        if (lv > 0) {
            gx *= 2.0f;
            gy *= 2.0f;
        }
    }

    const float rx = x + gx;
    const float ry = y + gy;
    const int w0 = a[0].w;
    const int h0 = a[0].h;
    if (rx < 0.0f || ry < 0.0f ||
        rx > static_cast<float>(w0 - 1) || ry > static_cast<float>(h0 - 1)) {
        return false;
    }

    const Level& l0 = b[0];
    float sa = 0.0f, sb = 0.0f, saa = 0.0f, sbb = 0.0f, sab = 0.0f;
    int k = 0;
    for (int dy = -half; dy <= half; ++dy) {
        for (int dx = -half; dx <= half; ++dx) {
            const float t = tpl_[k];
            const float q = sample(l0.img.data(), l0.w, l0.h, rx + dx, ry + dy);
            sa += t;
            sb += q;
            saa += t * t;
            sbb += q * q;
            sab += t * q;
            ++k;
        }
    }
    constexpr float cnt = static_cast<float>(kWin * kWin);
    const float va = saa - sa * sa / cnt;
    const float vb = sbb - sb * sb / cnt;
    if (va <= 1e-3f || vb <= 1e-3f) return false;
    const float ncc = (sab - sa * sb / cnt) / std::sqrt(va * vb);
    if (ncc < kMinNcc) return false;

    outX = rx;
    outY = ry;
    return true;
}

std::vector<LkCircleTracker::Level> LkCircleTracker::buildPyramid(const float* gray, int w, int h,
                                                                  int maxLevels) {
    std::vector<Level> out;
    out.reserve(static_cast<size_t>(maxLevels));
    std::vector<float> cur(gray, gray + static_cast<size_t>(w) * static_cast<size_t>(h));
    int cw = w;
    int ch = h;
    while (true) {
        std::vector<float> nextCopy;
        const bool more = (static_cast<int>(out.size()) + 1 < maxLevels && std::min(cw, ch) >= 64);
        if (more) {
            const int w2 = cw / 2;
            const int h2 = ch / 2;
            nextCopy.resize(static_cast<size_t>(w2) * static_cast<size_t>(h2));
            for (int y = 0; y < h2; ++y) {
                const int r0 = 2 * y * cw;
                const int r1 = r0 + cw;
                for (int x = 0; x < w2; ++x) {
                    const int i = 2 * x;
                    nextCopy[static_cast<size_t>(y) * w2 + x] =
                        (cur[r0 + i] + cur[r0 + i + 1] + cur[r1 + i] + cur[r1 + i + 1]) * 0.25f;
                }
            }
        }
        out.push_back(makeLevel(cw, ch, std::move(cur)));
        if (!more) break;
        cw /= 2;
        ch /= 2;
        cur = std::move(nextCopy);
    }
    return out;
}

LkCircleTracker::Level LkCircleTracker::makeLevel(int w, int h, std::vector<float> img) {
    Level l;
    l.w = w;
    l.h = h;
    l.img = std::move(img);
    l.ix.assign(static_cast<size_t>(w) * static_cast<size_t>(h), 0.0f);
    l.iy.assign(static_cast<size_t>(w) * static_cast<size_t>(h), 0.0f);
    for (int y = 1; y < h - 1; ++y) {
        const int row = y * w;
        for (int x = 1; x < w - 1; ++x) {
            l.ix[row + x] = (l.img[row + x + 1] - l.img[row + x - 1]) * 0.5f;
            l.iy[row + x] = (l.img[row + w + x] - l.img[row - w + x]) * 0.5f;
        }
    }
    return l;
}

float LkCircleTracker::sample(const float* a, int w, int h, float x0, float y0) {
    if (w <= 1 || h <= 1) return a[0];
    const float maxX = static_cast<float>(w) - 1.001f;
    const float maxY = static_cast<float>(h) - 1.001f;
    const float x = x0 < 0.0f ? 0.0f : (x0 > maxX ? maxX : x0);
    const float y = y0 < 0.0f ? 0.0f : (y0 > maxY ? maxY : y0);
    const int xi = static_cast<int>(x);
    const int yi = static_cast<int>(y);
    const float fx = x - static_cast<float>(xi);
    const float fy = y - static_cast<float>(yi);
    const int i = yi * w + xi;
    return a[i] * (1.0f - fx) * (1.0f - fy) +
           a[i + 1] * fx * (1.0f - fy) +
           a[i + w] * (1.0f - fx) * fy +
           a[i + w + 1] * fx * fy;
}

float LkCircleTracker::median(const float* v, const uint8_t* use, int len, int count) {
    std::vector<float> t;
    t.reserve(static_cast<size_t>(count));
    for (int i = 0; i < len; ++i) {
        if (use[i]) t.push_back(v[i]);
    }
    if (t.empty()) return 0.0f;
    std::sort(t.begin(), t.end());
    const size_t n = t.size();
    return (n % 2 == 1) ? t[n / 2] : (t[n / 2 - 1] + t[n / 2]) * 0.5f;
}

}  // namespace am
