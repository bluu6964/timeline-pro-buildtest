// After Motion native engine - video scope statistics (histogram, luma waveform, vectorscope)
// using BT.601 full-range YCbCr, 1:1 parity with ScopeStats.kt.
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace scopes {

constexpr int kBins = 256;
constexpr int kMaxSide = 320;
constexpr int kVecGrid = 96;
constexpr float kVecFullScale = 0.65f;

struct ScopeResult {
    int width = 0;
    int height = 0;
    int32_t histR[kBins]{};
    int32_t histG[kBins]{};
    int32_t histB[kBins]{};
    int32_t histY[kBins]{};
    std::vector<int32_t> wave;   // width * 256
    int32_t vec[kVecGrid * kVecGrid]{};
};

// Box-averages argb down so max(width, height) <= kMaxSide.
// Returns false on invalid dimensions or undersized buffer (64-bit safe).
bool downscale(const int32_t* argb, int argbLen, int width, int height,
               std::vector<int32_t>& outPixels, int& outW, int& outH);

// Runs downscale + single-pass histogram, waveform, and vectorscope statistics.
bool analyze(const int32_t* argb, int argbLen, int width, int height, ScopeResult& out);

}  // namespace scopes
}  // namespace am
