#include "scope_stats.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace am {
namespace scopes {

namespace {

// Matches Kotlin Float.roundToInt() / java.lang.Math.round(float) for non-negative finite floats.
inline int kotlinRoundToInt(float x) {
    return static_cast<int>(std::floor(x + 0.5f));
}

}  // namespace

bool downscale(const int32_t* argb, int argbLen, int width, int height,
               std::vector<int32_t>& outPixels, int& outW, int& outH) {
    if (!argb || width <= 0 || height <= 0) return false;
    const int64_t total = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (total <= 0 || static_cast<int64_t>(argbLen) < total) return false;

    const int longest = std::max(width, height);
    if (longest <= kMaxSide) {
        outW = width;
        outH = height;
        outPixels.assign(argb, argb + total);
        return true;
    }

    const float scale = static_cast<float>(kMaxSide) / static_cast<float>(longest);
    const int tw = std::max(1, kotlinRoundToInt(static_cast<float>(width) * scale));
    const int th = std::max(1, kotlinRoundToInt(static_cast<float>(height) * scale));
    outW = tw;
    outH = th;
    outPixels.resize(static_cast<size_t>(tw) * static_cast<size_t>(th));

    for (int ty = 0; ty < th; ++ty) {
        const int y0 = static_cast<int>(static_cast<int64_t>(ty) * height / th);
        const int y1 = std::max(
            y0 + 1, static_cast<int>(static_cast<int64_t>(ty + 1) * height / th));
        for (int tx = 0; tx < tw; ++tx) {
            const int x0 = static_cast<int>(static_cast<int64_t>(tx) * width / tw);
            const int x1 = std::max(
                x0 + 1, static_cast<int>(static_cast<int64_t>(tx + 1) * width / tw));
            int64_t sr = 0;
            int64_t sg = 0;
            int64_t sb = 0;
            int64_t n = 0;
            for (int y = y0; y < y1; ++y) {
                const int32_t* row =
                    argb + static_cast<size_t>(y) * static_cast<size_t>(width) + x0;
                for (int x = x0; x < x1; ++x) {
                    const uint32_t p = static_cast<uint32_t>(*row++);
                    sr += (p >> 16) & 0xFFu;
                    sg += (p >> 8) & 0xFFu;
                    sb += p & 0xFFu;
                    ++n;
                }
            }
            const uint32_t r = static_cast<uint32_t>((sr + n / 2) / n);
            const uint32_t g = static_cast<uint32_t>((sg + n / 2) / n);
            const uint32_t b = static_cast<uint32_t>((sb + n / 2) / n);
            outPixels[static_cast<size_t>(ty) * static_cast<size_t>(tw) + tx] =
                static_cast<int32_t>(0xFF000000u | (r << 16) | (g << 8) | b);
        }
    }
    return true;
}

bool analyze(const int32_t* argb, int argbLen, int width, int height, ScopeResult& out) {
    if (!argb || width <= 0 || height <= 0) return false;
    const int64_t total = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (total <= 0 || static_cast<int64_t>(argbLen) < total) return false;

    std::vector<int32_t> scaled;
    const int32_t* px = argb;
    int w = width;
    int h = height;
    if (std::max(width, height) > kMaxSide) {
        if (!downscale(argb, argbLen, width, height, scaled, w, h)) return false;
        px = scaled.data();
    }

    out.width = w;
    out.height = h;
    std::memset(out.histR, 0, sizeof(out.histR));
    std::memset(out.histG, 0, sizeof(out.histG));
    std::memset(out.histB, 0, sizeof(out.histB));
    std::memset(out.histY, 0, sizeof(out.histY));
    std::memset(out.vec, 0, sizeof(out.vec));
    out.wave.assign(static_cast<size_t>(w) * static_cast<size_t>(kBins), 0);

    for (int y = 0; y < h; ++y) {
        const int32_t* row = px + static_cast<size_t>(y) * static_cast<size_t>(w);
        for (int x = 0; x < w; ++x) {
            const uint32_t p = static_cast<uint32_t>(row[x]);
            const int r = static_cast<int>((p >> 16) & 0xFFu);
            const int g = static_cast<int>((p >> 8) & 0xFFu);
            const int b = static_cast<int>(p & 0xFFu);
            ++out.histR[r];
            ++out.histG[g];
            ++out.histB[b];

            const float yVal = 0.299f * static_cast<float>(r) +
                               0.587f * static_cast<float>(g) +
                               0.114f * static_cast<float>(b);
            const int level = std::clamp(kotlinRoundToInt(yVal), 0, kBins - 1);
            ++out.histY[level];
            ++out.wave[static_cast<size_t>(x) * static_cast<size_t>(kBins) + level];

            const float cbVal = (-0.14713f * static_cast<float>(r) -
                                 0.28886f * static_cast<float>(g) +
                                 0.436f * static_cast<float>(b)) / 255.0f;
            const float crVal = (0.615f * static_cast<float>(r) -
                                 0.51499f * static_cast<float>(g) -
                                 0.10001f * static_cast<float>(b)) / 255.0f;
            const int vx = std::clamp(
                static_cast<int>(((cbVal / kVecFullScale) * 0.5f + 0.5f) * kVecGrid),
                0, kVecGrid - 1);
            const int vy = std::clamp(
                static_cast<int>((0.5f - (crVal / kVecFullScale) * 0.5f) * kVecGrid),
                0, kVecGrid - 1);
            ++out.vec[vy * kVecGrid + vx];
        }
    }
    return true;
}

}  // namespace scopes
}  // namespace am
