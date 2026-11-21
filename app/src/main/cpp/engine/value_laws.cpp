// Migration stage 4 (build 247): the model/ value laws, ported line-by-line
// from the Kotlin originals. See value_laws.h for the semantics contract.
// Internal segment representation for the path family:

#include "value_laws.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "scene_math.h"

namespace am {
namespace laws {

// ---- helpers -------------------------------------------------------------------------------

namespace {

float kCoerceF(float v, float lo, float hi) {  // Kotlin Float.coerceIn (IEEE: NaN passes)
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}
double kCoerceD(double v, double lo, double hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}
float kMaxF(float a, float b) {  // kotlin.math.max / Math.max (NaN propagates, +0 > -0)
    if (std::isnan(a) || std::isnan(b)) return NAN;
    if (a > b) return a;
    if (b > a) return b;
    return (std::signbit(a) && !std::signbit(b)) ? b : a;
}
float kMinF(float a, float b) {  // kotlin.math.min / Math.min (NaN propagates, -0 < +0)
    if (std::isnan(a) || std::isnan(b)) return NAN;
    if (a < b) return a;
    if (b < a) return b;
    return (!std::signbit(a) && std::signbit(b)) ? b : a;
}
float hypotF(float x, float y) {  // kotlin.math.hypot(Float, Float)
    return (float)std::hypot((double)x, (double)y);
}
int64_t wrapAdd(int64_t a, int64_t b) { return (int64_t)((uint64_t)a + (uint64_t)b); }
int64_t wrapSub(int64_t a, int64_t b) { return (int64_t)((uint64_t)a - (uint64_t)b); }
int64_t absWrap(int64_t v) { return v == INT64_MIN ? INT64_MIN : (v < 0 ? -v : v); }

}  // namespace

int64_t f2lSat(float v) {
    if (std::isnan(v)) return 0;
    double d = (double)v;
    if (d >= 9223372036854775808.0) return INT64_MAX;
    if (d <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)d;
}
int64_t d2lSat(double v) {
    if (std::isnan(v)) return 0;
    if (v >= 9223372036854775808.0) return INT64_MAX;
    if (v <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)v;
}
int32_t f2iSat(float v) {
    if (std::isnan(v)) return 0;
    double d = (double)v;
    if (d >= 2147483648.0) return INT32_MAX;
    if (d <= -2147483649.0 + 1.0) return INT32_MIN;
    return (int32_t)d;
}
int32_t d2iSat(double v) {
    if (std::isnan(v)) return 0;
    if (v >= 2147483648.0) return INT32_MAX;
    if (v <= -2147483648.0) return INT32_MIN;
    return (int32_t)v;
}
int32_t roundF(float v) {  // Math.round(float)
    if (std::isnan(v)) return 0;
    float s = v + 0.5f;
    double f = std::floor((double)s);
    if (f >= 2147483648.0) return INT32_MAX;
    if (f <= -2147483649.0 + 1.0) return INT32_MIN;
    return (int32_t)f;
}
int64_t roundD(double v) {  // Math.round(double) == Double.roundToLong
    if (std::isnan(v)) return 0;
    double f = std::floor(v + 0.5);
    if (f >= 9223372036854775808.0) return INT64_MAX;
    if (f <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)f;
}
bool totalLessF(float a, float b) {
    if (std::isnan(a)) return false;
    if (std::isnan(b)) return true;
    if (a < b) return true;
    if (a > b) return false;
    return std::signbit(a) && !std::signbit(b);  // -0.0 < +0.0
}
void sortFloatsTotal(float* v, int n) {
    std::sort(v, v + n, [](float a, float b) { return totalLessF(a, b); });
}

// ---- Ducking.kt ------------------------------------------------------------------------------

namespace {
double duckSmoothFactor(float smoothness) {
    float s = kCoerceF(smoothness, 0.0f, 1.0f);
    return std::isnan(s) ? 0.5 : (double)s;
}
double lerpD(double a, double b, double t) { return a + (b - a) * t; }
}  // namespace

float duckFloorGain(float amountDb) {
    if (!(amountDb > 0.0f)) return 1.0f;
    float capped = amountDb > 36.0f ? 36.0f : amountDb;  // coerceAtMost (IEEE)
    return (float)std::pow(10.0, -(double)capped / 20.0);
}
double duckAttackMs(float smoothness) {
    return std::exp(lerpD(std::log(2.0), std::log(50.0), duckSmoothFactor(smoothness)));
}
double duckReleaseMs(float smoothness) {
    return std::exp(lerpD(std::log(60.0), std::log(1000.0), duckSmoothFactor(smoothness)));
}
float duckTargetGain(float level, float floor) {
    if (floor >= 1.0f) return 1.0f;
    float l = std::fabs(level);
    if (l <= 0.02f) return 1.0f;
    float over = kCoerceF((l - 0.02f) / (1.0f - 0.02f), 0.0f, 1.0f);
    return 1.0f - over * (1.0f - floor);
}
std::vector<float> duckTimelineLevels(const float* peaks, int nPeaks, int64_t durationMs,
                                      float speed, const float* heard, int nHeard) {
    std::vector<float> out;
    if (durationMs <= 0) return out;
    int64_t stepsL = (durationMs + 10 - 1) / 10;
    int steps = (int32_t)stepsL;
    if (steps < 0) steps = 0;
    out.assign((size_t)steps, 0.0f);
    for (int k = 0; k < steps; k++) {
        int64_t relMs = (int64_t)k * 10;
        int64_t centre = relMs + 5 < durationMs ? relMs + 5 : durationMs;
        // TimelineClip.sourceOffsetMs(centre) = (centre * speed).toLong(): Long.times(Float)
        // is a Float product, then a saturating float->long.
        int64_t src = f2lSat((float)centre * speed);
        int32_t srcStep = (int32_t)(src / 10);
        float peak = (srcStep >= 0 && srcStep < nPeaks) ? peaks[srcStep] : 0.0f;
        float hg = k < nHeard ? heard[k] : 0.0f;
        if (hg < 0.0f) hg = 0.0f;  // coerceAtLeast(0.0f) (IEEE: NaN passes)
        out[(size_t)k] = peak * hg;
    }
    return out;
}
float duckGainAt(const int64_t* times, const float* gains, int n, int64_t relMs) {
    if (n == 0) return 1.0f;
    if (relMs <= times[0]) return gains[0];
    if (relMs >= times[n - 1]) return gains[n - 1];
    int lo = 0, hi = n - 1;
    while (hi - lo > 1) {
        int mid = (lo + hi) / 2;
        if (times[mid] <= relMs) lo = mid; else hi = mid;
    }
    double span = (double)(times[hi] - times[lo]);
    if (span <= 0.0) return gains[hi];
    double f = (double)(relMs - times[lo]) / span;
    double diff = (double)gains[hi] - (double)gains[lo];  // Kotlin promotes to Double BEFORE subtracting
    return (float)((double)gains[lo] + diff * f);
}

// ---- NoiseReduction.kt -------------------------------------------------------------------------

namespace {
double logMin() { return std::log10(1e-5); }
double logStep() { return (std::log10(2.0) - logMin()) / 256.0; }
}  // namespace

float noiseOverSubtraction(float strength) {
    float s = kCoerceF(strength, 0.0f, 1.0f);
    return std::isnan(s) ? 0.0f : 4.0f * s;
}
float noiseRawGain(float magnitude, float noise, float strength) {
    float s = kCoerceF(strength, 0.0f, 1.0f);
    if (!(s > 0.0f)) return 1.0f;
    if (magnitude <= 0.0f) return 0.05f;
    double r = (double)(noiseOverSubtraction(s) * noise / magnitude);
    double g2 = 1.0 - r * r;
    double floor2 = (double)(0.05f * 0.05f);
    return g2 <= floor2 ? 0.05f : (float)std::sqrt(g2);
}
std::vector<float> noiseSmoothFreq(const float* raw, int n) {
    std::vector<float> out((size_t)(n > 0 ? n : 0));
    for (int k = 0; k < n; k++) {
        float a = raw[k - 1 < 0 ? 0 : k - 1];
        float b = raw[k];
        float c = raw[k + 1 > n - 1 ? n - 1 : k + 1];
        out[(size_t)k] = 0.25f * a + 0.5f * b + 0.25f * c;
    }
    return out;
}
double noiseStepCoef(double tauMs) {
    return 1.0 - std::exp(-512 * 1000.0 / (tauMs * 48000));
}
float noisePercentile(const float* values, int n, double p) {
    if (n == 0) return 0.0f;
    std::vector<float> sorted(values, values + n);
    sortFloatsTotal(sorted.data(), n);
    double pc = kCoerceD(p, 0.0, 1.0);
    int32_t idx = d2iSat(pc * (double)(n - 1));
    return sorted[(size_t)idx];
}
std::vector<float> noiseProfile(const float* frames, int nFrames, int bins) {
    std::vector<float> out((size_t)bins, 0.0f);
    if (nFrames == 0) return out;
    std::vector<float> column((size_t)nFrames);
    for (int k = 0; k < bins; k++) {
        for (int f = 0; f < nFrames; f++) column[(size_t)f] = frames[(size_t)f * bins + k];
        out[(size_t)k] = noisePercentile(column.data(), nFrames, 0.10);
    }
    return out;
}
int noiseBucketOf(float magnitude) {
    if ((double)magnitude <= 1e-5) return 0;
    int32_t b = d2iSat((std::log10((double)magnitude) - logMin()) / logStep());
    if (b < 0) b = 0;
    if (b > 255) b = 255;
    return b;
}
float noiseBucketValue(int b) {
    return (float)std::pow(10.0, logMin() + (b + 0.5) * logStep());
}
void noiseAccumAdd(int32_t* counts, const float* mags, int bins) {
    for (int k = 0; k < bins; k++) counts[(size_t)k * 256 + noiseBucketOf(mags[k])]++;
}
std::vector<float> noiseAccumProfile(const int32_t* counts, int64_t frames, int bins) {
    std::vector<float> out((size_t)bins, 0.0f);
    if (frames == 0) return out;
    int64_t rank = d2lSat(0.10 * (double)frames);
    if (rank < 1) rank = 1;
    if (rank > frames) rank = frames;
    for (int k = 0; k < bins; k++) {
        int64_t cum = 0;
        int b = 0;
        while (b < 256) {
            cum += counts[(size_t)k * 256 + b];
            if (cum >= rank) break;
            b++;
        }
        out[(size_t)k] = noiseBucketValue(b < 255 ? b : 255);
    }
    return out;
}
float noiseHann(int i) {
    return 0.5f - 0.5f * (float)std::cos(2.0 * 3.141592653589793 * i / 2048);
}
std::vector<float> noiseWindow() {
    std::vector<float> w(2048);
    for (int i = 0; i < 2048; i++) w[(size_t)i] = noiseHann(i);
    return w;
}

// ---- SpatialAlign.kt -----------------------------------------------------------------------------

std::vector<float> spatialAlignDeltas(const float* rects, int n, int32_t action) {
    std::vector<float> out;
    if (n < 2) return out;
    auto L = [&](int i) { return rects[i * 4]; };
    auto T = [&](int i) { return rects[i * 4 + 1]; };
    auto R = [&](int i) { return rects[i * 4 + 2]; };
    auto B = [&](int i) { return rects[i * 4 + 3]; };
    float minL = L(0), maxR = R(0), minT = T(0), maxB = B(0);
    for (int i = 1; i < n; i++) {
        minL = kMinF(minL, L(i));  // Kotlin minOf/maxOf(Float): Math.min/max — NaN PROPAGATES
        maxR = kMaxF(maxR, R(i));
        minT = kMinF(minT, T(i));
        maxB = kMaxF(maxB, B(i));
    }
    float midX = (minL + maxR) / 2.0f;
    float midY = (minT + maxB) / 2.0f;
    const int32_t ALIGN_LEFT = 0, ALIGN_CENTER_H = 1, ALIGN_RIGHT = 2;
    const int32_t ALIGN_TOP = 3, ALIGN_CENTER_V = 4, ALIGN_BOTTOM = 5;
    for (int i = 0; i < n; i++) {
        float cx = (L(i) + R(i)) / 2.0f;
        float cy = (T(i) + B(i)) / 2.0f;
        float dx = 0.0f, dy = 0.0f;
        if (action == ALIGN_LEFT) dx = minL - L(i);
        else if (action == ALIGN_RIGHT) dx = maxR - R(i);
        else if (action == ALIGN_CENTER_H) dx = midX - cx;
        if (action == ALIGN_TOP) dy = minT - T(i);
        else if (action == ALIGN_BOTTOM) dy = maxB - B(i);
        else if (action == ALIGN_CENTER_V) dy = midY - cy;
        if (std::fabs(dx) > 0.01f || std::fabs(dy) > 0.01f) {
            out.push_back((float)i);
            out.push_back(dx);
            out.push_back(dy);
        }
    }
    return out;
}
std::vector<float> spatialDistributeDeltas(const float* rects, int n, bool horizontal) {
    std::vector<float> out;
    if (n < 3) return out;
    auto centre = [&](int i) {
        return horizontal ? (rects[i * 4] + rects[i * 4 + 2]) / 2.0f
                          : (rects[i * 4 + 1] + rects[i * 4 + 3]) / 2.0f;
    };
    std::vector<int> order((size_t)n);
    for (int i = 0; i < n; i++) order[(size_t)i] = i;
    std::stable_sort(order.begin(), order.end(), [&](int a, int b) {
        return totalLessF(centre(a), centre(b));
    });
    float first = centre(order[0]);
    float last = centre(order[(size_t)n - 1]);
    float span = last - first;
    if (span <= 0.01f) return out;
    float step = span / (float)(n - 1);
    for (int i = 0; i < n; i++) {
        int r = order[(size_t)i];
        float d = first + step * (float)i - centre(r);
        if (std::fabs(d) > 0.01f) {
            out.push_back((float)r);
            out.push_back(horizontal ? d : 0.0f);
            out.push_back(horizontal ? 0.0f : d);
        }
    }
    return out;
}

// ---- ProjectMarkers.kt -----------------------------------------------------------------------------

MarkerNormalize markerNormalize(const int64_t* times, int n) {
    MarkerNormalize r;
    for (int i = 0; i < n; i++) {
        if (times[i] >= 0) r.perm.push_back(i);
    }
    std::stable_sort(r.perm.begin(), r.perm.end(), [&](int32_t a, int32_t b) {
        return times[a] < times[b];
    });
    return r;
}
int32_t markerNearest(const int64_t* times, int n, int64_t timeMs, int64_t tolMs) {
    if (n == 0) return -1;
    int best = 0;
    int64_t bestAbs = absWrap(wrapSub(times[0], timeMs));
    for (int i = 1; i < n; i++) {
        int64_t d = absWrap(wrapSub(times[i], timeMs));
        if (d < bestAbs) {
            bestAbs = d;
            best = i;
        }
    }
    return absWrap(wrapSub(times[best], timeMs)) <= tolMs ? best : -1;
}

// ---- OnionSkin.kt -------------------------------------------------------------------------------------

void onionNormalize(int32_t countPerSide, float opacity, int32_t* outCount, float* outOpacity) {
    int32_t c = countPerSide < 1 ? 1 : (countPerSide > 3 ? 3 : countPerSide);
    *outCount = c;
    *outOpacity = std::isnan(opacity) ? 0.35f : kCoerceF(opacity, 0.05f, 0.8f);
}
int64_t onionFrameStepMs(int32_t fps, int32_t step) {
    if (fps <= 0 || step <= 0) return 0;
    return roundD(step * 1000.0 / fps);
}
std::vector<OnionGhost> onionPlan(int64_t playheadMs, int32_t fps, int64_t durationMs,
                                  int32_t countPerSide, float baseOpacity) {
    std::vector<OnionGhost> out;
    int32_t count = countPerSide < 0 ? 0 : (countPerSide > 3 ? 3 : countPerSide);
    if (count == 0 || fps <= 0 || durationMs <= 0) return out;
    float base = kCoerceF(baseOpacity, 0.0f, 1.0f);
    if (!(base > 0.0f)) return out;
    int64_t head = playheadMs < 0 ? 0 : (playheadMs > durationMs ? durationMs : playheadMs);
    for (int32_t step = 1; step <= count; step++) {
        int64_t off = onionFrameStepMs(fps, step);
        if (off <= 0) continue;
        float alpha = base * (float)(count - step + 1) / (float)count;
        int64_t prev = head - off;
        if (prev >= 0) out.push_back({prev, -1, step, alpha, 0.0f});
        int64_t next = head + off;
        if (next <= durationMs) out.push_back({next, 1, step, alpha, 120.0f});
    }
    std::stable_sort(out.begin(), out.end(), [](const OnionGhost& a, const OnionGhost& b) {
        if (a.step != b.step) return a.step > b.step;
        return a.side < b.side;
    });
    return out;
}

// ---- CropBounds.kt ---------------------------------------------------------------------------------------

bool cropVisibleBox(float cwPct, float chPct, float cHPct, float cVPct,
                    float boxW, float boxH, float out[4]) {
    if (boxW <= 0.0f || boxH <= 0.0f) return false;
    float cW = kCoerceF(cwPct / 100.0f, 0.05f, 1.0f);
    float cH = kCoerceF(chPct / 100.0f, 0.05f, 1.0f);
    if (cW >= 0.999f && cH >= 0.999f) {
        out[0] = boxW; out[1] = boxH; out[2] = 0.0f; out[3] = 0.0f;
        return true;
    }
    float l = kCoerceF((1.0f - cW) * (cHPct / 100.0f), 0.0f, 1.0f - cW);
    float t = kCoerceF((1.0f - cH) * (cVPct / 100.0f), 0.0f, 1.0f - cH);
    float w = cW * boxW;
    float h = cH * boxH;
    if (w <= 0.0f || h <= 0.0f) return false;
    out[0] = w;
    out[1] = h;
    out[2] = (l + cW / 2.0f - 0.5f) * boxW;
    out[3] = (t + cH / 2.0f - 0.5f) * boxH;
    return true;
}
bool cropVisibleRect(float cwPct, float chPct, float cHPct, float cVPct,
                     float boxW, float boxH, float out[4]) {
    float vb[4];
    if (!cropVisibleBox(cwPct, chPct, cHPct, cVPct, boxW, boxH, vb)) return false;
    out[0] = kCoerceF((boxW - vb[0]) / 2.0f + vb[2], 0.0f, boxW - vb[0]);
    out[1] = kCoerceF((boxH - vb[1]) / 2.0f + vb[3], 0.0f, boxH - vb[1]);
    out[2] = vb[0];
    out[3] = vb[1];
    return true;
}

// ---- GifExport --------------------------------------------------------------------------------------------

int32_t gifDelayCs(int32_t fps) {
    int32_t f = fps < 1 ? 1 : (fps > 100 ? 100 : fps);
    int64_t r = roundD(100.0 / f);
    int32_t cs = r > INT32_MAX ? INT32_MAX : (r < INT32_MIN ? INT32_MIN : (int32_t)r);
    return cs < 2 ? 2 : cs;
}
void gifSize(int32_t srcW, int32_t srcH, int32_t out[2]) {
    int32_t m = srcW > srcH ? srcW : srcH;
    if (m <= 720) {
        out[0] = srcW;
        out[1] = srcH;
        return;
    }
    float k = 720.0f / (float)m;
    auto even = [](float x) {
        int32_t e = (roundF(x) / 2) * 2;
        return e < 2 ? 2 : e;
    };
    out[0] = even(srcW * k);
    out[1] = even(srcH * k);
}

// ---- Path segments / VectorDraw / PathEdit ---------------------------------------------------------------
// Internal segment form; the bridge converts the pack/stream formats at the edge.

namespace {

struct Seg {
    int32_t op;
    std::vector<float> pts;
};

std::vector<Seg> segsFromPack(const SegPack& pack) {
    std::vector<Seg> out;
    int off = 0;
    for (int i = 0; i < pack.n; i++) {
        Seg s;
        s.op = pack.ops[i];
        int c = pack.counts[i];
        for (int j = 0; j < c; j++) s.pts.push_back(pack.pts[off + j]);
        off += c;
        out.push_back(std::move(s));
    }
    return out;
}

std::vector<float> segsToStream(const std::vector<Seg>& segs) {
    std::vector<float> out;
    for (const Seg& s : segs) {
        out.push_back((float)s.op);
        out.push_back((float)s.pts.size());
        for (float v : s.pts) out.push_back(v);
    }
    return out;
}

NormalizeResult normalizeSegs(const std::vector<Seg>& segs, float canvasW, float canvasH, float density) {
    NormalizeResult r;
    if (canvasW <= 0.0f || canvasH <= 0.0f) return r;
    float aspect = canvasW / canvasH;
    // Flatten via scene_math (the same native flattenPathSegments the Kotlin original calls).
    int n = (int)segs.size();
    std::vector<int32_t> ops((size_t)n), offsets((size_t)n), counts((size_t)n);
    std::vector<float> pts;
    for (int i = 0; i < n; i++) {
        ops[(size_t)i] = segs[(size_t)i].op;
        offsets[(size_t)i] = (int32_t)pts.size();
        counts[(size_t)i] = (int32_t)segs[(size_t)i].pts.size();
        for (float v : segs[(size_t)i].pts) pts.push_back(v);
    }
    std::vector<float> flat = scene::flattenPathSegments(
        ops.data(), offsets.data(), counts.data(), n, pts.data(), (int)pts.size(), 24);
    if (flat.empty()) return r;
    float minX = INFINITY, minY = INFINITY, maxX = -INFINITY, maxY = -INFINITY;
    for (size_t i = 0; i + 1 < flat.size(); i += 2) {
        minX = kMinF(minX, flat[i]);  // Kotlin minOf/maxOf(Float): Math.min/max — NaN PROPAGATES
        minY = kMinF(minY, flat[i + 1]);
        maxX = kMaxF(maxX, flat[i]);
        maxY = kMaxF(maxY, flat[i + 1]);
    }
    float cx = (minX + maxX) / 2.0f;
    float cy = (minY + maxY) / 2.0f;
    float half = kMaxF((maxX - minX) / 2.0f, (maxY - minY) / 2.0f);
    if (half < 1e-6f) return r;
    std::vector<Seg> outSegs;
    for (const Seg& s : segs) {
        Seg o;
        o.op = s.op;
        for (size_t i = 0; i < s.pts.size(); i++) {
            o.pts.push_back(i % 2 == 0 ? (s.pts[i] - cx) / half : (s.pts[i] - cy) / half);
        }
        outSegs.push_back(std::move(o));
    }
    r.ok = true;
    r.offsetX = (cx / aspect - 0.5f) * canvasW;
    r.offsetY = (cy - 0.5f) * canvasH;
    float dens = density < 0.1f ? 0.1f : density;  // coerceAtLeast (IEEE: NaN passes)
    r.shapeScale = half * canvasH / (65.0f * dens);
    r.segStream = segsToStream(outSegs);
    for (size_t i = 0; i + 1 < flat.size(); i += 2) {
        r.flatFrame.push_back(flat[i] / aspect);
        r.flatFrame.push_back(flat[i + 1]);
    }
    return r;
}

bool isSmoothAnchor(const AnchorRec& a) {
    float ax = a.inX - a.x, ay = a.inY - a.y;
    float bx = a.outX - a.x, by = a.outY - a.y;
    float la = hypotF(ax, ay), lb = hypotF(bx, by);
    if (la < 1e-9f || lb < 1e-9f) return false;
    float cross = std::fabs(ax * by - ay * bx);
    float dot = ax * bx + ay * by;
    return dot < 0.0f && cross <= 1e-3f * la * lb + 1e-9f;
}

Seg segBetween(const AnchorRec& a, const AnchorRec& b) {
    Seg s;
    if (!a.hasOut && !b.hasIn) {
        s.op = 1;  // PATH_OP_LINE
        s.pts = {b.x, b.y};
    } else {
        s.op = 3;  // PATH_OP_CUBIC
        s.pts = {a.hasOut ? a.outX : a.x, a.hasOut ? a.outY : a.y,
                 b.hasIn ? b.inX : b.x, b.hasIn ? b.inY : b.y, b.x, b.y};
    }
    return s;
}

std::vector<Seg> pathToSegs(const PathRec& path) {
    std::vector<Seg> out;
    for (const SubpathRec& sp : path) {
        size_t n = sp.anchors.size();
        if (n == 0) continue;
        const AnchorRec& first = sp.anchors[0];
        out.push_back(Seg{0, {first.x, first.y}});  // PATH_OP_MOVE
        if (n == 1) continue;
        for (size_t i = 0; i + 1 < n; i++) out.push_back(segBetween(sp.anchors[i], sp.anchors[i + 1]));
        if (sp.closed) {
            Seg wrap = segBetween(sp.anchors[n - 1], first);
            if (wrap.op == 3) out.push_back(wrap);
            out.push_back(Seg{4, {}});  // PATH_OP_CLOSE
        }
    }
    return out;
}

}  // namespace

std::vector<float> vectorBuildStream(const float* rawXy, int nPts, float aspect,
                                     bool smooth, bool close) {
    std::vector<Seg> segs;
    std::vector<std::pair<float, float>> pts;
    for (int i = 0; i < nPts; i++) {
        float u = kCoerceF(rawXy[i * 2], 0.0f, 1.0f) * aspect;
        float v = kCoerceF(rawXy[i * 2 + 1], 0.0f, 1.0f);
        if (!pts.empty()) {
            float du = u - pts.back().first, dv = v - pts.back().second;
            if (hypotF(du, dv) < 0.0035f) continue;  // MIN_STEP
        }
        pts.push_back({u, v});
    }
    if (pts.size() < 2) return {};  // MIN_POINTS: the wrapper maps an empty stream to null
    segs.push_back(Seg{0, {pts[0].first, pts[0].second}});
    auto mid = [](std::pair<float, float> a, std::pair<float, float> b) {
        return std::pair<float, float>((a.first + b.first) / 2.0f, (a.second + b.second) / 2.0f);
    };
    if (smooth && pts.size() >= 3) {
        auto m0 = mid(pts[0], pts[1]);
        segs.push_back(Seg{1, {m0.first, m0.second}});
        for (size_t i = 1; i + 1 < pts.size(); i++) {
            auto m = mid(pts[i], pts[i + 1]);
            segs.push_back(Seg{2, {pts[i].first, pts[i].second, m.first, m.second}});
        }
        segs.push_back(Seg{1, {pts.back().first, pts.back().second}});
    } else {
        for (size_t i = 1; i < pts.size(); i++) {
            segs.push_back(Seg{1, {pts[i].first, pts[i].second}});
        }
    }
    if (close) segs.push_back(Seg{4, {}});
    return segsToStream(segs);
}

NormalizeResult vectorNormalize(const SegPack& pack, float canvasW, float canvasH, float density) {
    return normalizeSegs(segsFromPack(pack), canvasW, canvasH, density);
}

PathRec pathFromSegments(const SegPack& pack) {
    std::vector<Seg> segs = segsFromPack(pack);
    PathRec subs;
    std::vector<AnchorRec> anchors;
    bool closed = false;
    auto flush = [&]() {
        if (!anchors.empty()) {
            if (closed && anchors.size() >= 3) {
                AnchorRec f = anchors.front();
                AnchorRec l = anchors.back();
                if (f.x == l.x && f.y == l.y) {
                    if (l.hasIn && !f.hasIn) {
                        anchors[0].inX = l.inX;
                        anchors[0].inY = l.inY;
                        anchors[0].hasIn = true;
                    }
                    anchors.pop_back();
                }
            }
            subs.push_back(SubpathRec{anchors, closed});
        }
        anchors.clear();
        closed = false;
    };
    auto lastOrStart = [&]() -> AnchorRec& {
        if (anchors.empty()) anchors.push_back(AnchorRec{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false, false});
        return anchors.back();
    };
    for (const Seg& seg : segs) {
        const std::vector<float>& q = seg.pts;
        switch (seg.op) {
            case 0:  // MOVE
                if (q.size() >= 2) {
                    flush();
                    anchors.push_back(AnchorRec{q[0], q[1], 0.0f, 0.0f, 0.0f, 0.0f, false, false, false});
                }
                break;
            case 1:  // LINE
                if (q.size() >= 2) {
                    lastOrStart();
                    anchors.push_back(AnchorRec{q[0], q[1], 0.0f, 0.0f, 0.0f, 0.0f, false, false, false});
                }
                break;
            case 2:  // QUAD: exact degree elevation to cubic
                if (q.size() >= 4) {
                    AnchorRec prev = lastOrStart();
                    float cx = q[0], cy = q[1], ex = q[2], ey = q[3];
                    float c1x = prev.x + 2.0f / 3.0f * (cx - prev.x);
                    float c1y = prev.y + 2.0f / 3.0f * (cy - prev.y);
                    float c2x = ex + 2.0f / 3.0f * (cx - ex);
                    float c2y = ey + 2.0f / 3.0f * (cy - ey);
                    anchors.back().outX = c1x;
                    anchors.back().outY = c1y;
                    anchors.back().hasOut = true;
                    anchors.push_back(AnchorRec{ex, ey, c2x, c2y, 0.0f, 0.0f, true, false, false});
                }
                break;
            case 3:  // CUBIC
                if (q.size() >= 6) {
                    lastOrStart();
                    anchors.back().outX = q[0];
                    anchors.back().outY = q[1];
                    anchors.back().hasOut = true;
                    anchors.push_back(AnchorRec{q[4], q[5], q[2], q[3], 0.0f, 0.0f, true, false, false});
                }
                break;
            case 4:  // CLOSE
                closed = true;
                flush();
                break;
            default:
                break;
        }
    }
    flush();
    for (SubpathRec& sp : subs) {
        for (AnchorRec& a : sp.anchors) {
            if (a.hasIn && a.hasOut) a.smooth = isSmoothAnchor(a);
        }
    }
    return subs;
}

std::vector<float> pathToSegments(const PathRec& path) {
    return segsToStream(pathToSegs(path));
}

PathRec pathMoveAnchor(const PathRec& path, int32_t sub, int32_t idx, float x, float y) {
    if (sub < 0 || sub >= (int32_t)path.size()) return path;
    if (idx < 0 || idx >= (int32_t)path[(size_t)sub].anchors.size()) return path;
    PathRec out = path;
    AnchorRec& a = out[(size_t)sub].anchors[(size_t)idx];
    float dx = x - a.x, dy = y - a.y;
    a.x = x;
    a.y = y;
    if (a.hasIn) { a.inX += dx; a.inY += dy; }
    if (a.hasOut) { a.outX += dx; a.outY += dy; }
    return out;
}

PathRec pathMoveHandle(const PathRec& path, int32_t sub, int32_t idx, bool out, float x, float y) {
    if (sub < 0 || sub >= (int32_t)path.size()) return path;
    if (idx < 0 || idx >= (int32_t)path[(size_t)sub].anchors.size()) return path;
    PathRec res = path;
    AnchorRec& a = res[(size_t)sub].anchors[(size_t)idx];
    AnchorRec orig = a;
    if (out) { a.outX = x; a.outY = y; a.hasOut = true; }
    else { a.inX = x; a.inY = y; a.hasIn = true; }
    if (orig.smooth) {
        float dx = orig.x - x, dy = orig.y - y;
        float len = hypotF(dx, dy);
        if (len > 1e-9f) {
            if (out && a.hasIn) {
                float ol = hypotF(a.inX - orig.x, a.inY - orig.y);
                a.inX = orig.x + dx / len * ol;
                a.inY = orig.y + dy / len * ol;
            } else if (!out && a.hasOut) {
                float ol = hypotF(a.outX - orig.x, a.outY - orig.y);
                a.outX = orig.x + dx / len * ol;
                a.outY = orig.y + dy / len * ol;
            }
        }
    }
    return res;
}

PathRec pathSplitSegment(const PathRec& path, int32_t sub, int32_t seg, float t, int32_t* outInserted) {
    *outInserted = -1;
    if (sub < 0 || sub >= (int32_t)path.size()) return path;
    const SubpathRec& sp = path[(size_t)sub];
    int32_t n = (int32_t)sp.anchors.size();
    int32_t segCount = sp.closed ? n : n - 1;
    if (n < 2 || seg < 0 || seg >= segCount) return path;
    const AnchorRec& a = sp.anchors[(size_t)seg];
    const AnchorRec& b = sp.anchors[(size_t)((seg + 1) % n)];
    float tt = kCoerceF(t, 0.02f, 0.98f);
    auto lerp = [&](float p, float q) { return p + (q - p) * tt; };
    float p0x = a.x, p0y = a.y;
    float p1x = a.hasOut ? a.outX : a.x, p1y = a.hasOut ? a.outY : a.y;
    float p2x = b.hasIn ? b.inX : b.x, p2y = b.hasIn ? b.inY : b.y;
    float p3x = b.x, p3y = b.y;
    float q0x = lerp(p0x, p1x), q0y = lerp(p0y, p1y);
    float q1x = lerp(p1x, p2x), q1y = lerp(p1y, p2y);
    float q2x = lerp(p2x, p3x), q2y = lerp(p2y, p3y);
    float r0x = lerp(q0x, q1x), r0y = lerp(q0y, q1y);
    float r1x = lerp(q1x, q2x), r1y = lerp(q1y, q2y);
    float sx = lerp(r0x, r1x), sy = lerp(r0y, r1y);
    bool wasLine = !a.hasOut && !b.hasIn;
    PathRec out = path;
    std::vector<AnchorRec>& list = out[(size_t)sub].anchors;
    if (!wasLine) {
        list[(size_t)seg].outX = q0x;
        list[(size_t)seg].outY = q0y;
        list[(size_t)seg].hasOut = true;
        int32_t bi = (seg + 1) % n;
        list[(size_t)bi].inX = q2x;
        list[(size_t)bi].inY = q2y;
        list[(size_t)bi].hasIn = true;
    }
    AnchorRec inserted{sx, sy,
                       wasLine ? 0.0f : r0x, wasLine ? 0.0f : r0y,
                       wasLine ? 0.0f : r1x, wasLine ? 0.0f : r1y,
                       !wasLine, !wasLine, !wasLine};
    list.insert(list.begin() + (seg + 1), inserted);
    *outInserted = seg + 1;
    return out;
}

PathRec pathDeleteAnchor(const PathRec& path, int32_t sub, int32_t idx) {
    if (sub < 0 || sub >= (int32_t)path.size()) return path;
    const SubpathRec& sp = path[(size_t)sub];
    if (idx < 0 || idx >= (int32_t)sp.anchors.size()) return path;
    if (sp.closed && sp.anchors.size() <= 3) return path;
    if (!sp.closed && sp.anchors.size() <= 2) return path;
    PathRec out = path;
    auto& list = out[(size_t)sub].anchors;
    list.erase(list.begin() + idx);
    return out;
}

std::vector<AnchorRec> penTap(const std::vector<AnchorRec>& anchors, float x, float y) {
    std::vector<AnchorRec> out = anchors;
    out.push_back(AnchorRec{x, y, 0.0f, 0.0f, 0.0f, 0.0f, false, false, false});
    return out;
}
std::vector<AnchorRec> penPull(const std::vector<AnchorRec>& anchors, float x, float y) {
    if (anchors.empty()) return anchors;
    std::vector<AnchorRec> out = anchors;
    AnchorRec& a = out.back();
    a.outX = x;
    a.outY = y;
    a.hasOut = true;
    a.inX = 2.0f * a.x - x;
    a.inY = 2.0f * a.y - y;
    a.hasIn = true;
    a.smooth = true;
    return out;
}
std::vector<AnchorRec> penReleaseTap(const std::vector<AnchorRec>& anchors) {
    if (anchors.empty()) return anchors;
    const AnchorRec& a = anchors.back();
    if (!a.hasIn && !a.hasOut) return anchors;
    std::vector<AnchorRec> out = anchors;
    AnchorRec& b = out.back();
    b.hasIn = false;
    b.hasOut = false;
    b.inX = b.inY = b.outX = b.outY = 0.0f;
    b.smooth = false;
    return out;
}
std::vector<AnchorRec> penUndoPoint(const std::vector<AnchorRec>& anchors) {
    if (anchors.empty()) return anchors;
    return std::vector<AnchorRec>(anchors.begin(), anchors.end() - 1);
}
NormalizeResult penBuild(const std::vector<AnchorRec>& anchors, bool closed,
                         float canvasW, float canvasH, float density) {
    NormalizeResult r;
    if (anchors.size() < 2 || canvasW <= 0.0f || canvasH <= 0.0f) return r;
    float aspect = canvasW / canvasH;
    std::vector<AnchorRec> uni = anchors;
    for (AnchorRec& a : uni) {
        a.x = a.x * aspect;
        if (a.hasIn) a.inX = a.inX * aspect;
        if (a.hasOut) a.outX = a.outX * aspect;
    }
    PathRec path{SubpathRec{uni, closed}};
    return normalizeSegs(pathToSegs(path), canvasW, canvasH, density);
}

// ---- TextOnPath.kt (ArcTable) ---------------------------------------------------------------------------------

ArcTable arcTableBuild(const float* xy, int nPts) {
    ArcTable t;
    std::vector<std::pair<float, float>> kept;
    for (int i = 0; i < nPts; i++) {
        float x = xy[i * 2], y = xy[i * 2 + 1];
        if (!kept.empty() && kept.back().first == x && kept.back().second == y) continue;
        kept.push_back({x, y});
    }
    t.xy.resize(kept.size() * 2);
    t.cum.assign(kept.size(), 0.0f);
    float acc = 0.0f;
    for (size_t i = 0; i < kept.size(); i++) {
        t.xy[i * 2] = kept[i].first;
        t.xy[i * 2 + 1] = kept[i].second;
        if (i > 0) {
            acc += hypotF(kept[i].first - kept[i - 1].first, kept[i].second - kept[i - 1].second);
            t.cum[i] = acc;
        }
    }
    t.total = acc;
    return t;
}
void arcPointAt(const float* xy, const float* cum, int n, float total, float d, float out[3]) {
    if (n == 0) {
        out[0] = 0.0f; out[1] = 0.0f; out[2] = 0.0f;
        return;
    }
    if (n == 1 || total <= 0.0f) {
        out[0] = xy[0]; out[1] = xy[1]; out[2] = 0.0f;
        return;
    }
    float dd = kCoerceF(d, 0.0f, total);
    int lo = 0, hi = n - 2, seg = n - 2;
    while (lo <= hi) {
        int mid = (lo + hi) >> 1;  // (lo + hi) ushr 1 for non-negative ints
        if (cum[mid + 1] > dd) {
            seg = mid;
            hi = mid - 1;
        } else {
            lo = mid + 1;
        }
    }
    float ax = xy[seg * 2], ay = xy[seg * 2 + 1];
    float bx = xy[seg * 2 + 2], by = xy[seg * 2 + 3];
    float segLen = cum[seg + 1] - cum[seg];
    float t = segLen > 0.0f ? (dd - cum[seg]) / segLen : 0.0f;
    double ang = std::atan2((double)(by - ay), (double)(bx - ax));
    float angleDeg = (float)(ang * 180.0 / 3.141592653589793);
    out[0] = ax + (bx - ax) * t;
    out[1] = ay + (by - ay) * t;
    out[2] = angleDeg;
}

// ---- MaskGeometry.kt ------------------------------------------------------------------------------------------------

namespace {

const float kMinHalf = 0.004f;
const float kRotateOffset = 0.09f;
const float kLineLen = 20.0f;
const int kArcSteps = 6;
const int kEllipseSteps = 64;
const int kMaskMaxPoints = 16;
const int kMaskMinPoints = 3;
// MaskShape ints.
const int kShapeRect = 0, kShapeEllipse = 1, kShapePolygon = 2, kShapeStar = 3,
          kShapeLine = 4, kShapeMirror = 5, kShapePath = 6;

double toRadiansD(double deg) { return deg / 180.0 * 3.141592653589793; }  // Math.toRadians: /180 first

void toPic(const MaskFrameP& f, float lx, float ly, float out[2]) {
    out[0] = f.cx + lx * f.cosR - ly * f.sinR;
    out[1] = f.cy + lx * f.sinR + ly * f.cosR;
}
void cornerPt(const MaskFrameP& f, int i, float out[2]) {
    toPic(f, (i == 0 || i == 3) ? -f.hw : f.hw, (i == 0 || i == 1) ? -f.hh : f.hh, out);
}
void edgePt(const MaskFrameP& f, int i, float out[2]) {
    switch (i) {
        case 0: toPic(f, 0.0f, -f.hh, out); break;
        case 1: toPic(f, f.hw, 0.0f, out); break;
        case 2: toPic(f, 0.0f, f.hh, out); break;
        default: toPic(f, -f.hw, 0.0f, out); break;
    }
}
void rotateHandlePt(const MaskFrameP& f, const MaskShapeP& m, float out[2]) {
    if (m.shape == kShapeLine) toPic(f, 0.0f, -kRotateOffset, out);
    else toPic(f, 0.0f, -f.hh - kRotateOffset, out);
}
void controlPt(const MaskFrameP& f, const MaskShapeP& m, int i, float out[2]) {
    toPic(f, m.points[i * 2] * f.hw * 2.0f, m.points[i * 2 + 1] * f.hh * 2.0f, out);
}

bool nearEdgePts(float px, float py, const std::vector<float>& poly, float tol) {
    int n = (int)(poly.size() / 2);
    if (n < 2) return false;
    int j = n - 1;
    for (int i = 0; i < n; i++) {
        if (distToSegment(px, py, poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1]) <= tol) return true;
        j = i;
    }
    return false;
}

void resizedState(const MaskFrameP& f, float cx, float cy, float fullW, float fullH, float out[4]) {
    float wp[2];
    toPic(f, cx, cy, wp);
    out[0] = wp[0] / f.aspect;
    out[1] = wp[1];
    out[2] = kCoerceF(fullW / f.aspect, 0.001f, 8.0f);
    out[3] = kCoerceF(fullH, 0.001f, 8.0f);
}

}  // namespace

MaskFrameP maskFrameInit(float sCx, float sCy, float sW, float sH, float sRot, float aspect) {
    MaskFrameP f;
    f.cx = sCx * aspect;
    f.cy = sCy;
    f.hw = kMaxF(kMinHalf, sW * aspect / 2.0f);
    f.hh = kMaxF(kMinHalf, sH / 2.0f);
    double rad = toRadiansD((double)sRot);
    f.cosR = (float)std::cos(rad);
    f.sinR = (float)std::sin(rad);
    f.aspect = aspect;
    return f;
}
void maskToPicture(const MaskFrameP& f, float lx, float ly, float out[2]) { toPic(f, lx, ly, out); }
void maskToLocal(const MaskFrameP& f, float px, float py, float out[2]) {
    float dx = px - f.cx, dy = py - f.cy;
    out[0] = dx * f.cosR + dy * f.sinR;
    out[1] = -dx * f.sinR + dy * f.cosR;
}
std::vector<float> maskHandles(const MaskFrameP& f, const MaskShapeP& m) {
    std::vector<float> out;
    float p[2];
    auto add = [&](int kind, float x, float y) {
        out.push_back((float)kind);
        out.push_back(x);
        out.push_back(y);
    };
    if (m.shape == kShapeLine) {
        rotateHandlePt(f, m, p);
        add(30, p[0], p[1]);
    } else if (m.shape == kShapeMirror) {
        edgePt(f, 0, p); add(20, p[0], p[1]);
        edgePt(f, 2, p); add(22, p[0], p[1]);
        rotateHandlePt(f, m, p); add(30, p[0], p[1]);
    } else {
        for (int i = 0; i < 4; i++) { cornerPt(f, i, p); add(10 + i, p[0], p[1]); }
        for (int i = 0; i < 4; i++) { edgePt(f, i, p); add(20 + i, p[0], p[1]); }
        rotateHandlePt(f, m, p); add(30, p[0], p[1]);
        if (m.shape == kShapePath) {
            for (int i = 0; i < m.nPoints; i++) { controlPt(f, m, i, p); add(100 + i, p[0], p[1]); }
        }
    }
    return out;
}
std::vector<float> maskOutline(const MaskFrameP& f, const MaskShapeP& m) {
    std::vector<float> pts;
    float p[2];
    auto addPic = [&](float lx, float ly) {
        toPic(f, lx, ly, p);
        pts.push_back(p[0]);
        pts.push_back(p[1]);
    };
    switch (m.shape) {
        case kShapeRect: {
            float r = (m.corner / 100.0f * kMinF(f.hw, f.hh));
            if (r < 0.0f) r = 0.0f;  // coerceAtLeast(0.0f) (IEEE: NaN passes — the compare below then fails)
            if (r < 1e-4f) {
                for (int i = 0; i < 4; i++) { cornerPt(f, i, p); pts.push_back(p[0]); pts.push_back(p[1]); }
            } else {
                const float cs[4][2] = {{-1.0f, -1.0f}, {1.0f, -1.0f}, {1.0f, 1.0f}, {-1.0f, 1.0f}};
                const double start[4] = {180.0, 270.0, 0.0, 90.0};
                for (int k = 0; k < 4; k++) {
                    float ccx = cs[k][0] * (f.hw - r);
                    float ccy = cs[k][1] * (f.hh - r);
                    for (int j = 0; j <= kArcSteps; j++) {
                        double a = toRadiansD(start[k] + 90.0 * j / kArcSteps);
                        addPic(ccx + (float)(r * std::cos(a)), ccy + (float)(r * std::sin(a)));
                    }
                }
            }
            break;
        }
        case kShapeEllipse:
            for (int i = 0; i < kEllipseSteps; i++) {
                double a = 2.0 * 3.141592653589793 * i / kEllipseSteps;
                addPic((float)(f.hw * std::cos(a)), (float)(f.hh * std::sin(a)));
            }
            break;
        case kShapePolygon:
        case kShapeStar: {
            int n = m.sides < 3 ? 3 : (m.sides > 12 ? 12 : m.sides);
            double an = 3.141592653589793 / n;
            bool poly = m.shape == kShapePolygon;
            double rho = poly ? std::cos(an)
                              : std::cos(an) * (double)kCoerceF(m.inner, 5.0f, 100.0f) / 100.0;
            int count = poly ? n : 2 * n;
            for (int k = 0; k < count; k++) {
                bool outer = poly || k % 2 == 0;
                double a = k * an * (poly ? 2.0 : 1.0);
                double r = outer ? 1.0 : rho;
                addPic((float)(f.hw * r * std::sin(a)), (float)(-f.hh * r * std::cos(a)));
            }
            break;
        }
        case kShapeLine:
            addPic(-kLineLen, 0.0f);
            addPic(kLineLen, 0.0f);
            break;
        case kShapeMirror:
            addPic(-kLineLen, -f.hh);
            addPic(kLineLen, -f.hh);
            addPic(-kLineLen, f.hh);
            addPic(kLineLen, f.hh);
            break;
        default: {  // PATH (and any other shape, matching the Kotlin else branch)
            int n = m.nPoints < kMaskMaxPoints ? m.nPoints : kMaskMaxPoints;
            if (n >= 3) {
                std::vector<float> flat = scene::flattenMaskControlPoints(m.points, n, m.smooth, kMaskMaxPoints);
                for (size_t i = 0; i + 1 < flat.size(); i += 2) {
                    addPic(flat[i] * f.hw * 2.0f, flat[i + 1] * f.hh * 2.0f);
                }
            }
            break;
        }
    }
    return pts;
}
bool maskContains(const MaskFrameP& f, const MaskShapeP& m, float px, float py, float lineTol) {
    float l[2];
    maskToLocal(f, px, py, l);
    if (m.shape == kShapeLine) return std::fabs(l[1]) <= lineTol;
    if (m.shape == kShapeMirror) return std::fabs(l[1]) <= f.hh + lineTol;
    std::vector<float> poly = maskOutline(f, m);
    int n = (int)(poly.size() / 2);
    bool inside = n >= 3 && scene::pointInPolygon(px, py, poly.data(), n);
    if (m.shape == kShapePath) return inside || nearEdgePts(px, py, poly, lineTol);
    return inside;
}
void maskDragCorner(const MaskFrameP& f, int32_t i, float px, float py, float out[4]) {
    float sx = (i == 0 || i == 3) ? -1.0f : 1.0f;
    float sy = (i == 0 || i == 1) ? -1.0f : 1.0f;
    float fx = -sx * f.hw, fy = -sy * f.hh;
    float q[2];
    maskToLocal(f, px, py, q);
    float nx = fx + sx * kMaxF((q[0] - fx) * sx, kMinHalf * 2.0f);
    float ny = fy + sy * kMaxF((q[1] - fy) * sy, kMinHalf * 2.0f);
    resizedState(f, (fx + nx) / 2.0f, (fy + ny) / 2.0f, std::fabs(nx - fx), std::fabs(ny - fy), out);
}
void maskDragEdge(const MaskFrameP& f, const MaskShapeP& m, const float state[4],
                  int32_t i, float px, float py, float out[4]) {
    float q[2];
    maskToLocal(f, px, py, q);
    if (m.shape == kShapeMirror) {
        float h = kMaxF(std::fabs(q[1]), kMinHalf) * 2.0f;
        if (h > 4.0f) h = 4.0f;  // coerceAtMost(4.0f) (IEEE)
        out[0] = state[0];
        out[1] = state[1];
        out[2] = state[2];
        out[3] = h;
        return;
    }
    if (i == 0 || i == 2) {
        float sy = i == 0 ? -1.0f : 1.0f;
        float fy = -sy * f.hh;
        float ny = fy + sy * kMaxF((q[1] - fy) * sy, kMinHalf * 2.0f);
        resizedState(f, 0.0f, (fy + ny) / 2.0f, f.hw * 2.0f, std::fabs(ny - fy), out);
    } else {
        float sx = i == 3 ? -1.0f : 1.0f;
        float fx = -sx * f.hw;
        float nx = fx + sx * kMaxF((q[0] - fx) * sx, kMinHalf * 2.0f);
        resizedState(f, (fx + nx) / 2.0f, 0.0f, std::fabs(nx - fx), f.hh * 2.0f, out);
    }
}
float maskRotateTo(const MaskFrameP& f, float px, float py) {
    double degD = std::atan2((double)(py - f.cy), (double)(px - f.cx)) * 180.0 / 3.141592653589793;
    float deg = (float)degD + 90.0f;
    while (deg > 180.0f) deg -= 360.0f;
    while (deg < -180.0f) deg += 360.0f;
    float snapped = (float)roundF(deg / 45.0f) * 45.0f;
    if (std::fabs(deg - snapped) < 2.5f) deg = snapped > 180.0f ? snapped - 360.0f : snapped;
    return deg;
}
void maskMovedBy(float startCx, float startCy, float fromX, float fromY,
                 float toX, float toY, float aspect, float out[2]) {
    out[0] = startCx + (toX - fromX) / aspect;
    out[1] = startCy + (toY - fromY);
}
bool maskDragPoint(const MaskFrameP& f, const float* pts, int nPts, int32_t i,
                   float px, float py, std::vector<float>* outPts) {
    if (i < 0 || i >= nPts) return false;
    float q[2];
    maskToLocal(f, px, py, q);
    outPts->assign(pts, pts + nPts * 2);
    (*outPts)[(size_t)i * 2] = kCoerceF(q[0] / (f.hw * 2.0f), -1.5f, 1.5f);
    (*outPts)[(size_t)i * 2 + 1] = kCoerceF(q[1] / (f.hh * 2.0f), -1.5f, 1.5f);
    return true;
}
bool maskInsertPointNear(const MaskFrameP& f, const float* pts, int nPts,
                         float px, float py, float tol,
                         std::vector<float>* outPts, int32_t* outIndex) {
    if (nPts < kMaskMinPoints || nPts >= kMaskMaxPoints) return false;
    MaskShapeP m{kShapePath, 0.0f, 0, 0.0f, pts, nPts, false};
    int best = -1;
    float bestD = 3.4028235e38f;  // Float.MAX_VALUE
    float a[2], b[2];
    for (int i = 0; i < nPts; i++) {
        controlPt(f, m, i, a);
        controlPt(f, m, (i + 1) % nPts, b);
        float d = distToSegment(px, py, a[0], a[1], b[0], b[1]);
        if (d < bestD) {
            bestD = d;
            best = i;
        }
    }
    if (best < 0 || bestD > tol) return false;
    float q[2];
    maskToLocal(f, px, py, q);
    outPts->assign(pts, pts + nPts * 2);
    outPts->insert(outPts->begin() + (best + 1) * 2, {q[0] / (f.hw * 2.0f), q[1] / (f.hh * 2.0f)});
    *outIndex = best + 1;
    return true;
}
int32_t maskHitHandle(const MaskFrameP& f, const MaskShapeP& m, float px, float py, float radius) {
    std::vector<float> hs = maskHandles(f, m);
    int32_t best = -1;  // MaskHandle.NONE
    float bestD = radius;
    for (size_t i = 0; i + 2 < hs.size(); i += 3) {
        float d = hypotF(px - hs[i + 1], py - hs[i + 2]);
        if (d <= bestD) {
            bestD = d;
            best = (int32_t)hs[i];
        }
    }
    return best;
}
float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
    float ex = bx - ax, ey = by - ay;
    float ee = ex * ex + ey * ey;
    float t = ee > 1e-12f ? kCoerceF(((px - ax) * ex + (py - ay) * ey) / ee, 0.0f, 1.0f) : 0.0f;
    return hypotF(px - (ax + ex * t), py - (ay + ey * t));
}
void layerMapOf(float cwPct, float chPct, float cHPct, float cVPct, float out[4]) {
    float cw = kCoerceF(cwPct / 100.0f, 0.05f, 1.0f);
    float ch = kCoerceF(chPct / 100.0f, 0.05f, 1.0f);
    out[0] = (1.0f - cw) * cHPct / 100.0f;
    out[1] = (1.0f - ch) * cVPct / 100.0f;
    out[2] = cw;
    out[3] = ch;
}
void layerMapApply(const float map[8], bool toBox, float a, float b, float out[2]) {
    float cropL = map[0], cropT = map[1], cropW = map[2], cropH = map[3];
    float boxW = map[4], boxH = map[5];
    bool flipH = map[6] != 0.0f, flipV = map[7] != 0.0f;
    if (toBox) {
        float qx = (a - (cropL + cropW / 2.0f)) / cropW * boxW;
        float qy = (b - (cropT + cropH / 2.0f)) / cropH * boxH;
        if (flipH) qx = -qx;
        if (flipV) qy = -qy;
        out[0] = qx;
        out[1] = qy;
    } else {
        float x = flipH ? -a : a;
        float y = flipV ? -b : b;
        out[0] = cropL + cropW / 2.0f + x / boxW * cropW;
        out[1] = cropT + cropH / 2.0f + y / boxH * cropH;
    }
}
int32_t maskNeighbourKey(const int64_t* times, int n, int64_t relMs, bool forward, int64_t tolMs) {
    if (forward) {
        int64_t limit = wrapAdd(relMs, tolMs);
        for (int i = 0; i < n; i++) {
            if (times[i] > limit) return i;
        }
        return -1;
    }
    int64_t limit = wrapSub(relMs, tolMs);
    for (int i = n - 1; i >= 0; i--) {
        if (times[i] < limit) return i;
    }
    return -1;
}

// ---- TextOnPath arc table: see above (arcTableBuild / arcPointAt) ---------------------------------------------

// ---- ClipClipboard.kt ------------------------------------------------------------------------------------------------

int64_t clipboardEarliest(const int64_t* times, int n) {
    int64_t best = times[0];
    for (int i = 1; i < n; i++) best = times[i] < best ? times[i] : best;
    return best;
}
int64_t clipboardPasteShift(int64_t earliest, int64_t anchorRelMs, int64_t durationMs) {
    int64_t anchor = anchorRelMs < 0 ? 0 : (anchorRelMs > durationMs ? durationMs : anchorRelMs);
    return wrapSub(anchor, earliest);
}
KeysPack clipboardMergeProp(const KeysPack& target, const KeysPack& copied, int64_t shift) {
    size_t cn = copied.times.size();
    std::vector<size_t> ord(cn);
    for (size_t i = 0; i < cn; i++) ord[i] = i;
    std::vector<int64_t> shifted(cn);
    for (size_t i = 0; i < cn; i++) shifted[i] = wrapAdd(copied.times[i], shift);
    std::stable_sort(ord.begin(), ord.end(), [&](size_t a, size_t b) { return shifted[a] < shifted[b]; });
    KeysPack pasted;
    for (size_t idx : ord) {
        pasted.times.push_back(shifted[idx]);
        for (int j = 0; j < 6; j++) pasted.vals.push_back(copied.vals[idx * 6 + j]);
        pasted.tags.push_back(copied.tags[idx]);
    }
    KeysPack merged;
    if (cn > 0) {
        int64_t spanStart = pasted.times.front();
        int64_t spanEnd = pasted.times.back();
        for (size_t i = 0; i < target.times.size(); i++) {
            int64_t t = target.times[i];
            if (t < spanStart || t > spanEnd) {
                merged.times.push_back(t);
                for (int j = 0; j < 6; j++) merged.vals.push_back(target.vals[i * 6 + j]);
                merged.tags.push_back(target.tags[i]);
            }
        }
    }
    for (size_t i = 0; i < pasted.times.size(); i++) {
        merged.times.push_back(pasted.times[i]);
        for (int j = 0; j < 6; j++) merged.vals.push_back(pasted.vals[i * 6 + j]);
        merged.tags.push_back(pasted.tags[i]);
    }
    // Stable sort by time (Kotlin (kept + pasted).sortedBy { it.timeMs }).
    std::vector<size_t> mord(merged.times.size());
    for (size_t i = 0; i < mord.size(); i++) mord[i] = i;
    std::stable_sort(mord.begin(), mord.end(), [&](size_t a, size_t b) { return merged.times[a] < merged.times[b]; });
    KeysPack out;
    for (size_t idx : mord) {
        out.times.push_back(merged.times[idx]);
        for (int j = 0; j < 6; j++) out.vals.push_back(merged.vals[idx * 6 + j]);
        out.tags.push_back(merged.tags[idx]);
    }
    return out;
}

// ---- BlendModes.kt table ----------------------------------------------------------------------------------------------

namespace {
struct BlendRow {
    const char* name;
    const char* category;
    int32_t shaderId;
    int32_t kind;  // NORMAL 0, COLOR 1, MATTE 2
};
const BlendRow kBlendRows[] = {
    {"Normal", "Matte", 0, 0},
    {"Alpha Matte", "Matte", 1, 2},
    {"Mask Invert", "Matte", 2, 2},
    {"Luma", "Matte", 3, 2},
    {"Luma Invert", "Matte", 4, 2},
    {"Darken", "Darken", 10, 1},
    {"Multiply", "Darken", 11, 1},
    {"Color Burn", "Darken", 12, 1},
    {"Linear Burn", "Darken", 13, 1},
    {"Darker Color", "Darken", 14, 1},
    {"Lighten", "Brighten", 20, 1},
    {"Screen", "Brighten", 21, 1},
    {"Color Dodge", "Brighten", 22, 1},
    {"Add", "Brighten", 23, 1},
    {"Lighter Color", "Brighten", 24, 1},
    {"Overlay", "Contrast", 30, 1},
    {"Soft Light", "Contrast", 31, 1},
    {"Hard Light", "Contrast", 32, 1},
    {"Soft Overlay", "Contrast", 33, 1},
    {"Difference", "Difference", 40, 1},
    {"Exclusion", "Difference", 41, 1},
    {"Subtract", "Difference", 42, 1},
    {"Diff", "Difference", 43, 1},
    {"Hue", "Color", 50, 1},
    {"Saturation", "Color", 51, 1},
    {"Color", "Color", 52, 1},
    {"Luminosity", "Color", 53, 1},
};
const char* kBlendCategories[] = {"Matte", "Darken", "Brighten", "Contrast", "Difference", "Color"};
}  // namespace

int blendSpecCount() { return (int)(sizeof(kBlendRows) / sizeof(kBlendRows[0])); }
const char* blendSpecName(int i) { return kBlendRows[i].name; }
const char* blendSpecCategory(int i) { return kBlendRows[i].category; }
int32_t blendSpecShaderId(int i) { return kBlendRows[i].shaderId; }
int32_t blendSpecKind(int i) { return kBlendRows[i].kind; }
int blendCategoryCount() { return (int)(sizeof(kBlendCategories) / sizeof(kBlendCategories[0])); }
const char* blendCategoryName(int i) { return kBlendCategories[i]; }

// ---- ToolRowSplit.kt -----------------------------------------------------------------------------------------------------

int32_t toolTileWidth(int32_t labelLen) {
    int32_t text = d2iSat(std::ceil(labelLen * 6.2));
    int32_t inner = text > 23 ? text : 23;
    int32_t w = 8 + inner + 2;
    return w > 40 ? w : 40;
}
int32_t toolAvailableDp(int32_t screenWidthDp, int32_t backZoneDp, int32_t contentPaddingDp) {
    return screenWidthDp - backZoneDp - 2 * contentPaddingDp;
}
int32_t toolVisibleCount(const int32_t* widths, int n, int32_t availableDp, int32_t gapDp) {
    int32_t used = 0, count = 0;
    while (count < n) {
        int32_t add = widths[count] + (count == 0 ? 0 : gapDp);
        if (used + add > availableDp) break;
        used += add;
        count++;
    }
    return count;
}
int32_t toolGridHeight(int32_t itemCount, int32_t tileHeightDp, int32_t gapDp,
                       int32_t vPaddingDp, int32_t maxRows, int32_t columns) {
    if (itemCount <= 0) return 0;
    int32_t rows = (itemCount + columns - 1) / columns;
    if (rows > maxRows) rows = maxRows;
    return rows * tileHeightDp + (rows - 1) * gapDp + 2 * vPaddingDp;
}
int32_t toolSharedPanelHeight(int32_t clipOverflowCount, int32_t utilityCount) {
    int32_t h = 2 * 6 + toolGridHeight(utilityCount, 48, 4, 0, 3, 5);
    if (clipOverflowCount > 0) {
        h += 13 + toolGridHeight(clipOverflowCount, 48, 4, 0, 2, 5);
    }
    return h;
}

// ---- PagerGrid.kt -----------------------------------------------------------------------------------------------------------

std::vector<int32_t> pagerRowCounts(int32_t count, int32_t columns) {
    std::vector<int32_t> rows;
    if (count <= 0 || columns <= 0) return rows;
    int32_t left = count;
    while (left > 0) {
        int32_t r = left < columns ? left : columns;
        rows.push_back(r);
        left -= r;
    }
    return rows;
}

// ---- UsedTools.kt --------------------------------------------------------------------------------------------------------------

int32_t usedToolsMask(int32_t effectsCount, bool hasAnim, bool fillNotSolid, int64_t textColor,
                      bool hasKeys, float tx, float ty, float rot, float scale,
                      float rot3dX, float rot3dY, float tz,
                      float cropW, float cropH, bool blendNotNormal, float opacity,
                      int32_t masksCount, float volume, bool muted, bool hasCutout, bool hasParent) {
    int32_t mask = 0;
    if (effectsCount > 0) mask |= 1;
    if (hasAnim) mask |= 2;
    if (fillNotSolid || textColor != 0xFFFFFFFFLL) mask |= 4;
    if (hasKeys || tx != 0.0f || ty != 0.0f || rot != 0.0f || scale != 100.0f ||
        rot3dX != 0.0f || rot3dY != 0.0f || tz != 0.0f) mask |= 8;
    if (cropW < 99.9f || cropH < 99.9f) mask |= 16;
    if (blendNotNormal || opacity != 100.0f) mask |= 32;
    if (masksCount > 0) mask |= 64;
    if (volume != 1.0f || muted) mask |= 128;
    if (hasCutout) mask |= 256;
    if (hasParent) mask |= 512;
    return mask;
}

// ---- ShapeEditValues.kt -----------------------------------------------------------------------------------------------------------

float shapeCoerceCorner(float dp) { return kCoerceF(dp, 0.0f, 65.0f); }
void shapeCoerceTrim(float start, float end, float out[2]) {
    float s = kCoerceF(start, 0.0f, 1.0f);
    out[0] = s;
    out[1] = kCoerceF(end, s, 1.0f);
}

// ---- TextPaintRules.kt ---------------------------------------------------------------------------------------------------------------

bool textBgArgb(int64_t bgColor, float bgOpacity, int32_t* outArgb) {
    if (bgOpacity <= 0.0f) return false;
    int32_t stored = (int32_t)(((uint64_t)bgColor >> 24) & 0xFF);
    int32_t baseAlpha = stored == 0 ? 0xFF : stored;
    int32_t a = roundF((float)baseAlpha * kCoerceF(bgOpacity, 0.0f, 100.0f) / 100.0f);
    if (a < 0) a = 0;
    if (a > 255) a = 255;
    if (a <= 0) return false;
    *outArgb = (a << 24) | ((int32_t)bgColor & 0x00FFFFFF);
    return true;
}
float textLetterSpacingEm(float percent) { return percent / 100.0f; }
float textLetterSpacingPx(float fontPx, float percent) { return fontPx * percent / 100.0f; }

// ---- CanvasMark.kt ----------------------------------------------------------------------------------------------------------------------

int32_t canvasMark(int32_t typeOrdinal, bool selected) {
    if (typeOrdinal == 6 || typeOrdinal == 10) return selected ? 2 : 0;  // NULL, CONTROLLER
    if (typeOrdinal == 9) return 1;                                      // GROUP
    return selected ? 2 : 0;
}

}  // namespace laws
}  // namespace am
