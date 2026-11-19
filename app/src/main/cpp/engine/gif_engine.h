// After Motion native engine - deterministic GIF89a median-cut quantizer, LZW compressor,
// and RGBA bottom-up to ARGB converter (1:1 parity with GifEncoder.kt).
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace gif {

// Quantizes RGBA8888 pixels into a power-of-two 0xRRGGBB palette (2..256) and top-down indices.
// Returns false on invalid dimensions or undersized input buffer.
bool quantize(const uint8_t* rgba, int rgbaLen, int width, int height, int maxColors,
              bool bottomUp, std::vector<int32_t>& paletteOut,
              std::vector<uint8_t>& indicesOut);

// GIF LZW compressor: unsigned index stream -> compressed bytes (without sub-block framing).
std::vector<uint8_t> lzwCompress(const uint8_t* indices, int count, int minCodeSize);

// Converts GL bottom-up RGBA8888 readback into top-down opaque ARGB8888 ints.
bool rgbaBottomUpToArgb(const uint8_t* rgba, int rgbaLen, int width, int height,
                        int32_t* argbOut);

}  // namespace gif
}  // namespace am
