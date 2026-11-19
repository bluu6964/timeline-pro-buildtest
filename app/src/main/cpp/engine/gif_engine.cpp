#include "gif_engine.h"

#include <algorithm>
#include <cstring>

namespace am {
namespace gif {

namespace {

constexpr int kHistBins = 32768;

struct Box {
    std::vector<int> bins;
    int64_t population = 0;
    int rMin = 31, rMax = 0;
    int gMin = 31, gMax = 0;
    int bMin = 31, bMax = 0;

    bool splittable() const {
        return bins.size() > 1 && (rMax > rMin || gMax > gMin || bMax > bMin);
    }
};

Box makeBox(std::vector<int> bins, const int32_t* hist) {
    Box box;
    box.bins = std::move(bins);
    for (int bin : box.bins) {
        box.population += hist[bin];
        const int r = (bin >> 10) & 31;
        const int g = (bin >> 5) & 31;
        const int b = bin & 31;
        if (r < box.rMin) box.rMin = r;
        if (r > box.rMax) box.rMax = r;
        if (g < box.gMin) box.gMin = g;
        if (g > box.gMax) box.gMax = g;
        if (b < box.bMin) box.bMin = b;
        if (b > box.bMax) box.bMax = b;
    }
    return box;
}

}  // namespace

bool quantize(const uint8_t* rgba, int rgbaLen, int width, int height, int maxColors,
              bool bottomUp, std::vector<int32_t>& paletteOut,
              std::vector<uint8_t>& indicesOut) {
    if (!rgba || width < 1 || height < 1) return false;
    const int64_t count64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (count64 <= 0 || static_cast<int64_t>(rgbaLen) < count64 * 4) return false;
    const int count = static_cast<int>(count64);
    const int limit = std::clamp(maxColors, 2, 256);

    std::vector<int32_t> hist(kHistBins, 0);
    std::vector<int64_t> rSum(kHistBins, 0);
    std::vector<int64_t> gSum(kHistBins, 0);
    std::vector<int64_t> bSum(kHistBins, 0);

    const uint8_t* p = rgba;
    for (int i = 0; i < count; ++i) {
        const int r = p[0];
        const int g = p[1];
        const int b = p[2];
        const int bin = ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
        ++hist[bin];
        rSum[bin] += r;
        gSum[bin] += g;
        bSum[bin] += b;
        p += 4;
    }

    std::vector<int> used;
    used.reserve(2048);
    for (int bin = 0; bin < kHistBins; ++bin) {
        if (hist[bin] > 0) used.push_back(bin);
    }

    std::vector<Box> boxes;
    boxes.reserve(static_cast<size_t>(limit));
    boxes.push_back(makeBox(std::move(used), hist.data()));

    while (static_cast<int>(boxes.size()) < limit) {
        int targetIdx = -1;
        int64_t bestPop = -1;
        for (int i = 0; i < static_cast<int>(boxes.size()); ++i) {
            if (boxes[static_cast<size_t>(i)].splittable() &&
                (targetIdx < 0 || boxes[static_cast<size_t>(i)].population > bestPop)) {
                targetIdx = i;
                bestPop = boxes[static_cast<size_t>(i)].population;
            }
        }
        if (targetIdx < 0) break;

        Box box = std::move(boxes[static_cast<size_t>(targetIdx)]);
        boxes.erase(boxes.begin() + targetIdx);

        const int dr = box.rMax - box.rMin;
        const int dg = box.gMax - box.gMin;
        const int db = box.bMax - box.bMin;
        const int shift = (dr >= dg && dr >= db) ? 10 : (dg >= db ? 5 : 0);

        std::sort(box.bins.begin(), box.bins.end(), [shift](int a, int b) {
            const int ca = (a >> shift) & 31;
            const int cb = (b >> shift) & 31;
            return ca != cb ? ca < cb : a < b;
        });

        const int nBins = static_cast<int>(box.bins.size());
        int64_t acc = 0;
        const int64_t halfPop = box.population / 2;
        int cut = nBins - 2;
        for (int k = 0; k < nBins; ++k) {
            acc += hist[box.bins[static_cast<size_t>(k)]];
            if (acc >= halfPop) {
                cut = std::min(k, nBins - 2);
                break;
            }
        }

        std::vector<int> left(box.bins.begin(), box.bins.begin() + cut + 1);
        std::vector<int> right(box.bins.begin() + cut + 1, box.bins.end());
        boxes.push_back(makeBox(std::move(left), hist.data()));
        boxes.push_back(makeBox(std::move(right), hist.data()));
    }

    int tableSize = 2;
    while (tableSize < static_cast<int>(boxes.size())) tableSize <<= 1;
    paletteOut.assign(static_cast<size_t>(tableSize), 0);

    std::vector<uint8_t> binIndex(kHistBins, 0);
    for (int bi = 0; bi < static_cast<int>(boxes.size()); ++bi) {
        const Box& box = boxes[static_cast<size_t>(bi)];
        int64_t r = 0, g = 0, b = 0;
        for (int bin : box.bins) {
            r += rSum[bin];
            g += gSum[bin];
            b += bSum[bin];
            binIndex[bin] = static_cast<uint8_t>(bi);
        }
        const int64_t n = std::max<int64_t>(1, box.population);
        paletteOut[static_cast<size_t>(bi)] = static_cast<int32_t>(
            ((r / n) << 16) | ((g / n) << 8) | (b / n));
    }

    indicesOut.resize(static_cast<size_t>(count));
    for (int y = 0; y < height; ++y) {
        const int srcY = bottomUp ? (height - 1 - y) : y;
        const uint8_t* src =
            rgba + static_cast<size_t>(srcY) * static_cast<size_t>(width) * 4;
        uint8_t* dst = indicesOut.data() + static_cast<size_t>(y) * static_cast<size_t>(width);
        for (int x = 0; x < width; ++x) {
            const int bin = ((src[0] >> 3) << 10) | ((src[1] >> 3) << 5) | (src[2] >> 3);
            dst[x] = binIndex[bin];
            src += 4;
        }
    }
    return true;
}

std::vector<uint8_t> lzwCompress(const uint8_t* indices, int count, int minCodeSize) {
    const int minCode = std::clamp(minCodeSize, 2, 8);
    const int clearCode = 1 << minCode;
    const int eoiCode = clearCode + 1;

    std::vector<uint8_t> out;
    out.reserve(static_cast<size_t>(std::max(0, count) / 2 + 16));

    uint32_t bitBuf = 0;
    int bitCnt = 0;
    auto emit = [&](int code, int width) {
        bitBuf |= static_cast<uint32_t>(code) << bitCnt;
        bitCnt += width;
        while (bitCnt >= 8) {
            out.push_back(static_cast<uint8_t>(bitBuf & 0xFFu));
            bitBuf >>= 8;
            bitCnt -= 8;
        }
    };

    int nextCode = eoiCode + 1;
    int width = minCode + 1;
    emit(clearCode, width);

    if (!indices || count <= 0) {
        emit(eoiCode, width);
        if (bitCnt > 0) out.push_back(static_cast<uint8_t>(bitBuf & 0xFFu));
        return out;
    }

    // Flat open-addressed hash table (16384 slots for <= 4096 codes) with O(1) generation clear.
    constexpr uint32_t kSlots = 16384;
    constexpr uint32_t kMask = kSlots - 1;
    std::vector<int32_t> keys(kSlots, 0);
    std::vector<uint16_t> vals(kSlots, 0);
    std::vector<uint32_t> stamps(kSlots, 0);
    uint32_t gen = 1;

    auto resetDict = [&]() {
        ++gen;
        if (gen == 0) {
            std::fill(stamps.begin(), stamps.end(), 0u);
            gen = 1;
        }
    };

    int prefix = indices[0];
    for (int i = 1; i < count; ++i) {
        const int k = indices[i];
        const int32_t key = (prefix << 8) | k;
        uint32_t slot = (static_cast<uint32_t>(key) * 2654435761u) & kMask;
        bool hit = false;
        while (stamps[slot] == gen) {
            if (keys[slot] == key) {
                prefix = vals[slot];
                hit = true;
                break;
            }
            slot = (slot + 1u) & kMask;
        }
        if (!hit) {
            emit(prefix, width);
            if (nextCode < 4096) {
                stamps[slot] = gen;
                keys[slot] = key;
                vals[slot] = static_cast<uint16_t>(nextCode++);
                if (nextCode == (1 << width) && width < 12) ++width;
            } else {
                emit(clearCode, width);
                resetDict();
                nextCode = eoiCode + 1;
                width = minCode + 1;
            }
            prefix = k;
        }
    }
    emit(prefix, width);
    emit(eoiCode, width);
    if (bitCnt > 0) out.push_back(static_cast<uint8_t>(bitBuf & 0xFFu));
    return out;
}

bool rgbaBottomUpToArgb(const uint8_t* rgba, int rgbaLen, int width, int height,
                        int32_t* argbOut) {
    if (!rgba || !argbOut || width < 1 || height < 1) return false;
    const int64_t count64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (count64 <= 0 || static_cast<int64_t>(rgbaLen) < count64 * 4) return false;
    for (int y = 0; y < height; ++y) {
        const uint8_t* src =
            rgba + static_cast<size_t>(height - 1 - y) * static_cast<size_t>(width) * 4;
        int32_t* dst = argbOut + static_cast<size_t>(y) * static_cast<size_t>(width);
        for (int x = 0; x < width; ++x) {
            const uint32_t r = src[0];
            const uint32_t g = src[1];
            const uint32_t b = src[2];
            dst[x] = static_cast<int32_t>(0xFF000000u | (r << 16) | (g << 8) | b);
            src += 4;
        }
    }
    return true;
}

}  // namespace gif
}  // namespace am
