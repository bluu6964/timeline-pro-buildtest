// model_logic: see model_logic.h. Each function replicates its Kotlin
// original line for line; the helpers below pin the JVM number semantics
// that differ from C++'s defaults.

#include "model_logic.h"

#include <algorithm>
#include <cmath>

namespace am {
namespace {

// Kotlin Float.toInt(): NaN -> 0, saturates to the Int range, else
// truncates toward zero. (A plain C++ cast is UB for NaN / out of range.)
int32_t kFloatToInt(float f) {
    if (std::isnan(f)) return 0;
    if (f >= 2147483648.0f) return INT32_MAX;   // 2^31
    if (f <= -2147483648.0f) return INT32_MIN;  // -2^31
    return static_cast<int32_t>(f);
}

// Kotlin Double.toLong(): NaN -> 0, saturates to the Long range, else
// truncates toward zero.
int64_t kDoubleToLong(double d) {
    if (std::isnan(d)) return 0;
    if (d >= 9223372036854775808.0) return INT64_MAX;   // 2^63
    if (d <= -9223372036854775808.0) return INT64_MIN;  // -2^63
    return static_cast<int64_t>(d);
}

// Kotlin Long subtraction wraps (two's complement); in C++ the unsigned
// detour makes the wrap defined.
int64_t kSub(int64_t a, int64_t b) {
    return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
}

// Kotlin abs(Long): abs(Long.MIN_VALUE) == Long.MIN_VALUE (it stays negative).
int64_t kAbs(int64_t v) {
    if (v == INT64_MIN) return INT64_MIN;
    return v < 0 ? -v : v;
}

int64_t kCoerceIn(int64_t v, int64_t lo, int64_t hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

}  // namespace

// ---- Keyframes.kt: colour packing -------------------------------------------

void packColor(int64_t argb, float out[2]) {
    const int64_t a = (argb >> 24) & 0xFF;
    const int64_t r = (argb >> 16) & 0xFF;
    const int64_t g = (argb >> 8) & 0xFF;
    const int64_t b = argb & 0xFF;
    out[0] = static_cast<float>(a * 256 + r);
    out[1] = static_cast<float>(g * 256 + b);
}

int64_t unpackColor(float pa, float pb) {
    const int32_t x = kCoerceIn(kFloatToInt(pa), 0, 65535);
    const int32_t y = kCoerceIn(kFloatToInt(pb), 0, 65535);
    return (static_cast<int64_t>(x >> 8) << 24) |
           (static_cast<int64_t>(x & 0xFF) << 16) |
           (static_cast<int64_t>(y >> 8) << 8) |
           static_cast<int64_t>(y & 0xFF);
}

// ---- Keyframes.kt: Easing curve maths -----------------------------------------

bool easingIsLinear(float x1, float y1, float x2, float y2) {
    return std::fabs(x1 - y1) < 1e-4f && std::fabs(x2 - y2) < 1e-4f &&
           std::fabs(x1 - 1.0f / 3.0f) < 1e-3f && std::fabs(x2 - 2.0f / 3.0f) < 1e-3f;
}

void easingPointAt(float x1, float y1, float x2, float y2, float s, float out[2]) {
    const float u = 1.0f - s;
    out[0] = 3.0f * u * u * s * x1 + 3.0f * u * s * s * x2 + s * s * s;
    out[1] = 3.0f * u * u * s * y1 + 3.0f * u * s * s * y2 + s * s * s;
}

bool easingSameAs(float x1, float y1, float x2, float y2,
                  float ox1, float oy1, float ox2, float oy2, float eps) {
    return std::fabs(x1 - ox1) < eps && std::fabs(y1 - oy1) < eps &&
           std::fabs(x2 - ox2) < eps && std::fabs(y2 - oy2) < eps;
}

// ---- Keyframes.kt: keyframe list searches ---------------------------------------

int segmentIndexAt(const int64_t* times, int n, int64_t relMs) {
    if (n < 2) return -1;
    for (int i = 0; i + 1 < n; i++) {
        if (relMs >= times[i] && relMs < times[i + 1]) return i;
    }
    return -1;
}

int indexOnTime(const int64_t* times, int n, int64_t relMs, int64_t tolMs) {
    int best = -1;
    int64_t bestD = INT64_MAX;
    for (int i = 0; i < n; i++) {
        const int64_t d = kAbs(kSub(times[i], relMs));
        if (d <= tolMs && d < bestD) {
            best = i;
            bestD = d;
        }
    }
    return best;
}

// ---- BeatDetect.kt -----------------------------------------------------------------

bool estimateBpm(const int64_t* beats, int n, int64_t minIntervalMs,
                 int64_t maxIntervalMs, double* bpmOut) {
    if (n < 2) return false;
    std::vector<int64_t> intervals;
    intervals.reserve(static_cast<size_t>(n - 1));
    for (int i = 1; i < n; i++) {
        const int64_t d = kSub(beats[i], beats[i - 1]);
        if (d >= minIntervalMs && d <= maxIntervalMs) intervals.push_back(d);
    }
    if (intervals.empty()) return false;
    std::sort(intervals.begin(), intervals.end());
    const double med = static_cast<double>(intervals[intervals.size() / 2]);
    if (med <= 0.0) return false;
    // kotlin.math.round(x) == floor(x + 0.5) for the (always positive) x here.
    *bpmOut = std::floor(60000.0 / med * 10.0 + 0.5) / 10.0;
    return true;
}

std::vector<int64_t> mergeBeats(const int64_t* existing, int nExisting,
                                const int64_t* detected, int nDetected,
                                int64_t tolMs) {
    std::vector<int64_t> out(existing, existing + nExisting);
    std::sort(out.begin(), out.end());
    std::vector<int64_t> det(detected, detected + nDetected);
    std::sort(det.begin(), det.end());
    for (const int64_t d : det) {
        bool dup = false;
        for (const int64_t v : out) {
            if (kAbs(kSub(v, d)) <= tolMs) { dup = true; break; }
        }
        if (!dup) out.push_back(d);
    }
    std::sort(out.begin(), out.end());
    return out;
}

std::vector<int64_t> timelineMsForOffsets(int64_t durationMs, double speed,
                                          int64_t spanMs,
                                          const int64_t* offsets, int n) {
    std::vector<int64_t> out(static_cast<size_t>(n));
    if (n == 0) return out;
    // coerceAtLeast(0.01): the >= form also maps a NaN speed to 0.01, as Kotlin does.
    const double spd = (speed >= 0.01) ? speed : 0.01;
    // sortedBy { it.second } is a STABLE sort: equal offsets keep index order.
    std::vector<int> order(static_cast<size_t>(n));
    for (int i = 0; i < n; i++) order[static_cast<size_t>(i)] = i;
    std::stable_sort(order.begin(), order.end(),
                     [&](int a, int b) { return offsets[a] < offsets[b]; });
    for (const int index : order) {
        const int64_t off = offsets[index];
        int64_t v;
        if (off <= 0) {
            v = 0;
        } else if (off >= spanMs) {
            v = durationMs;
        } else {
            v = kCoerceIn(kDoubleToLong(static_cast<double>(off) / spd), 0, durationMs);
        }
        out[static_cast<size_t>(index)] = v;
    }
    return out;
}

// ---- ExportRange.kt ------------------------------------------------------------------

bool exportRangeOf(bool inSet, int64_t inMs, bool outSet, int64_t outMs,
                   int64_t totalMs, int64_t minLenMs, int64_t pairOut[2]) {
    if (!inSet || !outSet || totalMs <= 0) return false;
    const int64_t i = kCoerceIn(inMs, 0, totalMs);
    const int64_t o = kCoerceIn(outMs, 0, totalMs);
    if (kSub(o, i) >= minLenMs) {
        pairOut[0] = i;
        pairOut[1] = o;
        return true;
    }
    return false;
}

// ---- MultiSelectOps.kt -----------------------------------------------------------------

int barMode(const int64_t* startsMs, const int64_t* endsMs, int n, int64_t playheadMs) {
    if (n == 0) return 0;  // SPLIT
    bool anyInside = false;
    bool allEnded = true;
    for (int i = 0; i < n; i++) {
        if (playheadMs > startsMs[i] && playheadMs < endsMs[i]) anyInside = true;
        if (!(endsMs[i] <= playheadMs)) allEnded = false;
    }
    if (anyInside) return 0;  // SPLIT
    if (allEnded) return 2;   // END
    return 1;                 // START
}

namespace {

// The descendantsOf traversal, shared by descendantsOf and deleteSet:
// level-by-level BFS over identity keys, flat order inside a level, at
// most 16 levels (the original's guard), discovery order preserved.
void descendantsInto(const int* clipKeys, const int* groupKeys,
                     const char* isGroup01, int n, int keyCount, int rootKey,
                     std::vector<int>& out) {
    std::vector<char> seen(static_cast<size_t>(n), 0);
    std::vector<char> frontier(static_cast<size_t>(keyCount), 0);
    std::vector<char> next(static_cast<size_t>(keyCount), 0);
    if (rootKey >= 0 && rootKey < keyCount) frontier[static_cast<size_t>(rootKey)] = 1;
    int guard = 0;
    while (guard++ < 16) {
        bool any = false;
        for (const char c : frontier) if (c) { any = true; break; }
        if (!any) break;
        std::fill(next.begin(), next.end(), 0);
        for (int i = 0; i < n; i++) {
            const int g = groupKeys[i];
            if (g >= 0 && g < keyCount && frontier[static_cast<size_t>(g)] &&
                !seen[static_cast<size_t>(i)]) {
                seen[static_cast<size_t>(i)] = 1;
                out.push_back(i);
                if (isGroup01[i]) {
                    const int k = clipKeys[i];
                    if (k >= 0 && k < keyCount) next[static_cast<size_t>(k)] = 1;
                }
            }
        }
        frontier.swap(next);
    }
}

}  // namespace

std::vector<int> deleteSet(const int* clipKeys, const int* chainKeys,
                           const int* groupKeys, const int* typeCodes,
                           const bool* locked, const bool* checked,
                           int n, int keyCount, int* sparedOut) {
    std::vector<char> gone(static_cast<size_t>(n), 0);
    std::vector<char> isGroup(static_cast<size_t>(n), 0);
    for (int i = 0; i < n; i++) isGroup[static_cast<size_t>(i)] = typeCodes[i] == 2 ? 1 : 0;
    std::vector<int> scratch;
    for (int c = 0; c < n; c++) {
        if (!checked[c]) continue;
        gone[static_cast<size_t>(c)] = 1;
        if (typeCodes[c] == 1) {  // CHAIN: its members go with it
            for (int i = 0; i < n; i++) {
                if (chainKeys[i] == clipKeys[c]) gone[static_cast<size_t>(i)] = 1;
            }
        } else if (typeCodes[c] == 2) {  // GROUP: its descendants go with it
            scratch.clear();
            descendantsInto(clipKeys, groupKeys, isGroup.data(),
                            n, keyCount, clipKeys[c], scratch);
            for (const int i : scratch) gone[static_cast<size_t>(i)] = 1;
        }
    }
    int spared = 0;
    for (int i = 0; i < n; i++) {
        if (gone[static_cast<size_t>(i)] && locked[i]) {
            spared++;
            gone[static_cast<size_t>(i)] = 0;
        }
    }
    *sparedOut = spared;
    std::vector<int> out;
    for (int i = 0; i < n; i++) if (gone[static_cast<size_t>(i)]) out.push_back(i);
    return out;
}

// ---- GroupOps.kt -----------------------------------------------------------------------

std::vector<int> descendantsOf(const int* clipKeys, const int* groupKeys,
                               const bool* isGroup, int n, int keyCount,
                               int rootKey) {
    std::vector<char> isGroup01(static_cast<size_t>(n));
    for (int i = 0; i < n; i++) isGroup01[static_cast<size_t>(i)] = isGroup[i] ? 1 : 0;
    std::vector<int> out;
    descendantsInto(clipKeys, groupKeys, isGroup01.data(), n, keyCount, rootKey, out);
    return out;
}

// ---- LaneLayout.kt -----------------------------------------------------------------------

bool isNormalized(const int* laneOffsets, int laneCount, const int64_t* startMs,
                  const bool* isChain, const bool* chainIdNull,
                  const int* chainIdTarget) {
    for (int lane = 0; lane < laneCount; lane++) {
        const int s = laneOffsets[lane];
        const int e = laneOffsets[lane + 1];
        if (s == e) return false;
        // sortedBy { startMs } (stable) must be the identity on this lane.
        for (int i = s + 1; i < e; i++) {
            if (startMs[i] < startMs[i - 1]) return false;
        }
        int ci = -1;
        for (int i = s; i < e; i++) {
            if (isChain[i]) { ci = i; break; }
        }
        if (ci < 0) {
            if (e - s != 1) return false;
            if (!chainIdNull[s]) return false;
        } else {
            // The expected lane is [chain] + its members in start order:
            // the chain must sit first and every other clip must be a member.
            if (ci != s) return false;
            for (int i = s; i < e; i++) {
                if (i == ci) continue;
                if (chainIdTarget[i] != ci) return false;
            }
        }
    }
    return true;
}

}  // namespace am
