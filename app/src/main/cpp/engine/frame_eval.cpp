#include "frame_eval.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <limits>

namespace am {
namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr double kTwoPi = 2.0 * kPi;
constexpr float kPiF = 3.14159265358979323846f;
constexpr float kDegToRadF = kPiF / 180.0f;
constexpr double kDegToRad = kPi / 180.0;
constexpr double kRadToDeg = 180.0 / kPi;

inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

inline int clampi(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

inline int ktRoundToInt(float x) {
    if (!std::isfinite(x)) return 0;
    double r = std::floor(static_cast<double>(x) + 0.5);
    if (r >= static_cast<double>(std::numeric_limits<int>::max())) {
        return std::numeric_limits<int>::max();
    }
    if (r <= static_cast<double>(std::numeric_limits<int>::min())) {
        return std::numeric_limits<int>::min();
    }
    return static_cast<int>(r);
}

inline float fxAt(const float* vals, int len, int idx, float defVal) {
    return (vals != nullptr && idx >= 0 && idx < len) ? vals[idx] : defVal;
}

inline uint32_t unpackColorArgb(float pa, float pb) {
    int x = clampi(static_cast<int>(pa), 0, 65535);
    int y = clampi(static_cast<int>(pb), 0, 65535);
    uint32_t a = static_cast<uint32_t>((x >> 8) & 0xFF);
    uint32_t r = static_cast<uint32_t>(x & 0xFF);
    uint32_t g = static_cast<uint32_t>((y >> 8) & 0xFF);
    uint32_t b = static_cast<uint32_t>(y & 0xFF);
    return (a << 24) | (r << 16) | (g << 8) | b;
}

inline void packColorArgb(uint32_t argb, float& outA, float& outB) {
    uint32_t a = (argb >> 24) & 0xFFu;
    uint32_t r = (argb >> 16) & 0xFFu;
    uint32_t g = (argb >> 8) & 0xFFu;
    uint32_t b = argb & 0xFFu;
    outA = static_cast<float>(a * 256u + r);
    outB = static_cast<float>(g * 256u + b);
}

inline float bezierEval(float c1, float c2, float s) {
    float u = 1.0f - s;
    return 3.0f * u * u * s * c1 + 3.0f * u * s * s * c2 + s * s * s;
}

inline float bezierDeriv(float c1, float c2, float s) {
    float u = 1.0f - s;
    return 3.0f * u * u * c1 + 6.0f * u * s * (c2 - c1) + 3.0f * s * s * (1.0f - c2);
}

inline float repeatHash01(int32_t seed, int32_t index, int32_t salt) {
    constexpr uint64_t kC1 = 0x9E3779B97F4A7C15ULL;
    constexpr uint64_t kC2 = 0xBF58476D1CE4E5B9ULL;
    constexpr uint64_t kC3 = 0x94D049BB133111EBULL;
    uint64_t z = static_cast<uint64_t>(static_cast<int64_t>(seed)) * kC1 +
                 static_cast<uint64_t>(static_cast<int64_t>(index)) * kC2 +
                 static_cast<uint64_t>(static_cast<int64_t>(salt)) * kC3;
    z = (z ^ (z >> 30)) * kC2;
    z = (z ^ (z >> 27)) * kC3;
    z = z ^ (z >> 31);
    return static_cast<float>((z >> 40) & 0xFFFFFFULL) / 16777216.0f;
}

inline void readAltColor(const float* fxVals, int fxLen, int at, bool& hasAlt, uint32_t& argb) {
    if (fxAt(fxVals, fxLen, at + 2, 0.0f) == 0.0f) {
        hasAlt = false;
        argb = 0;
        return;
    }
    hasAlt = true;
    argb = unpackColorArgb(fxAt(fxVals, fxLen, at, 0.0f), fxAt(fxVals, fxLen, at + 1, 0.0f));
}

inline float scatterEvolutionAt(
    const float* fxVals, int fxLen,
    int64_t clipStartMs, int64_t clipEndMs, int64_t timeMs) {
    float v0 = fxAt(fxVals, fxLen, 9, 0.0f);
    if (timeMs == std::numeric_limits<int64_t>::min() || clipEndMs <= clipStartMs) {
        return v0;
    }
    float t0 = fxAt(fxVals, fxLen, 8, 0.0f);
    float t1 = fxAt(fxVals, fxLen, 10, 1.0f);
    float v1 = fxAt(fxVals, fxLen, 11, v0);
    float u = clampf(static_cast<float>(timeMs - clipStartMs) /
                     static_cast<float>(clipEndMs - clipStartMs), 0.0f, 1.0f);
    float span = t1 - t0;
    float f = (span > 0.0001f) ? clampf((u - t0) / span, 0.0f, 1.0f) : (u < t0 ? 0.0f : 1.0f);
    return v0 + (v1 - v0) * f;
}

inline bool isAsciiDigit(uint16_t ch) {
    return ch >= static_cast<uint16_t>('0') && ch <= static_cast<uint16_t>('9');
}

struct CounterToken {
    int end = 0;
    double value = 0.0;
    int decimals = 0;
    bool grouped = false;
};

bool scanCounterNumber(const uint16_t* text, int n, int from, CounterToken& out) {
    int i = from;
    bool neg = false;
    uint16_t c = text[i];
    if (c == static_cast<uint16_t>('-')) {
        uint16_t prev = (from > 0) ? text[from - 1] : static_cast<uint16_t>(' ');
        if (isAsciiDigit(prev) || prev == static_cast<uint16_t>('.') ||
            prev == static_cast<uint16_t>(',') || prev == static_cast<uint16_t>('-')) {
            return false;
        }
        bool ok = (from + 1 < n) &&
                  (isAsciiDigit(text[from + 1]) ||
                   (text[from + 1] == static_cast<uint16_t>('.') &&
                    from + 2 < n && isAsciiDigit(text[from + 2])));
        if (!ok) return false;
        neg = true;
        i++;
    } else if (c == static_cast<uint16_t>('.')) {
        uint16_t prev = (from > 0) ? text[from - 1] : static_cast<uint16_t>(' ');
        if (isAsciiDigit(prev) || prev == static_cast<uint16_t>('.')) return false;
        if (from + 1 >= n || !isAsciiDigit(text[from + 1])) return false;
    } else if (!isAsciiDigit(c)) {
        return false;
    }

    std::string intDigits;
    bool grouped = false;
    while (i < n) {
        uint16_t ch = text[i];
        if (isAsciiDigit(ch)) {
            intDigits.push_back(static_cast<char>(ch));
            i++;
        } else if (ch == static_cast<uint16_t>(',') && !intDigits.empty() &&
                   i + 1 < n && isAsciiDigit(text[i + 1])) {
            grouped = true;
            i++;
        } else {
            break;
        }
    }

    std::string fracDigits;
    if (i < n && text[i] == static_cast<uint16_t>('.') &&
        i + 1 < n && isAsciiDigit(text[i + 1])) {
        int j = i + 1;
        while (j < n && isAsciiDigit(text[j])) {
            fracDigits.push_back(static_cast<char>(text[j]));
            j++;
        }
        i = j;
    }

    if (intDigits.empty() && fracDigits.empty()) return false;

    std::string numStr = intDigits;
    if (!fracDigits.empty()) {
        numStr.push_back('.');
        numStr.append(fracDigits);
    }
    char* endPtr = nullptr;
    double mag = std::strtod(numStr.c_str(), &endPtr);
    out.end = i;
    out.value = neg ? -mag : mag;
    out.decimals = static_cast<int>(fracDigits.size());
    out.grouped = grouped;
    return true;
}

std::string groupThousandsStr(const std::string& s) {
    if (s.size() <= 3) return s;
    std::string out;
    out.reserve(s.size() + s.size() / 3);
    size_t i = s.size() % 3;
    if (i > 0) out.append(s, 0, i);
    while (i < s.size()) {
        if (!out.empty()) out.push_back(',');
        out.append(s, i, 3);
        i += 3;
    }
    return out;
}

std::string formatCounterValue(double v, int decimals, bool grouped) {
    double pow10 = std::pow(10.0, static_cast<double>(decimals));
    double scaled = v * pow10;
    if (!std::isfinite(scaled) || std::fabs(scaled) > 9.0e15) {
        char buf[128];
        int dec = clampi(decimals, 0, 20);
        std::snprintf(buf, sizeof(buf), "%.*f", dec, v);
        return std::string(buf);
    }
    int64_t units = (scaled >= 0.0)
        ? static_cast<int64_t>(std::floor(scaled + 0.5))
        : static_cast<int64_t>(std::ceil(scaled - 0.5));
    bool neg = units < 0;
    uint64_t a = neg ? static_cast<uint64_t>(-units) : static_cast<uint64_t>(units);
    uint64_t powL = static_cast<uint64_t>(pow10);
    if (powL == 0) powL = 1;
    std::string intStr = std::to_string(a / powL);
    if (grouped) intStr = groupThousandsStr(intStr);
    std::string out;
    out.reserve(intStr.size() + static_cast<size_t>(decimals) + 3);
    if (neg) out.push_back('-');
    out.append(intStr);
    if (decimals > 0) {
        out.push_back('.');
        std::string frac = std::to_string(a % powL);
        if (static_cast<int>(frac.size()) < decimals) {
            out.append(static_cast<size_t>(decimals - static_cast<int>(frac.size())), '0');
        }
        out.append(frac);
    }
    return out;
}

void preparedPieArcAngles(double startDeg, double endDeg, double& outLow, double& outHigh) {
    double startAngle = -startDeg * kDegToRad + 2000.0 * kPi;
    double endAngle = -endDeg * kDegToRad + 2000.0 * kPi;
    double lowAngle = std::min(startAngle, endAngle);
    double highAngle = std::max(startAngle, endAngle);
    double diff = std::fmod((highAngle - lowAngle) / kTwoPi, 2.0);
    lowAngle = std::fmod(lowAngle, kTwoPi);
    highAngle = std::fmod(highAngle, kTwoPi);
    if (diff < 1.0) {
        lowAngle += kTwoPi;
    } else {
        highAngle += kTwoPi;
    }
    outLow = lowAngle;
    outHigh = highAngle;
}

std::array<double, 8> createSmallArc(double r, double a1, double a2) {
    double a = (a2 - a1) / 2.0;
    double x4 = r * std::cos(a);
    double y4 = r * std::sin(a);
    double x1 = x4;
    double y1 = -y4;
    double q1 = x1 * x1 + y1 * y1;
    double q2 = q1 + x1 * x4 + y1 * y4;
    double k2 = 4.0 / 3.0 * (std::sqrt(2.0 * q1 * q2) - q2) / (x1 * y4 - y1 * x4);
    double x2 = x1 - k2 * y1;
    double y2 = y1 + k2 * x1;
    double x3 = x2;
    double y3 = -y2;
    double ar = a + a1;
    double cosAr = std::cos(ar);
    double sinAr = std::sin(ar);
    return {
        r * std::cos(a1), r * std::sin(a1),
        x2 * cosAr - y2 * sinAr, x2 * sinAr + y2 * cosAr,
        x3 * cosAr - y3 * sinAr, x3 * sinAr + y3 * cosAr,
        r * std::cos(a2), r * std::sin(a2)
    };
}

std::vector<std::array<double, 8>> createArcCurves(double radius, double startAngle, double endAngle) {
    double angleDiff = std::fmod(endAngle - startAngle, kTwoPi);
    double startAngleN = (std::fabs(startAngle) > kTwoPi) ? std::fmod(startAngle, kTwoPi) : startAngle;
    double endAngleN = startAngleN + angleDiff;
    std::vector<std::array<double, 8>> curves;
    curves.reserve(4);
    constexpr double kPiOverTwo = kPi / 2.0;
    double sgn = (startAngle < endAngle) ? 1.0 : -1.0;
    double a1 = startAngle;
    double totalAngle = std::min(kTwoPi, std::fabs(endAngleN - startAngleN));
    while (totalAngle > 0.00001) {
        double a2 = a1 + sgn * std::min(totalAngle, kPiOverTwo);
        curves.push_back(createSmallArc(radius, a1, a2));
        totalAngle -= std::fabs(a2 - a1);
        a1 = a2;
    }
    return curves;
}

inline void pushSegMove(std::vector<float>& out, float x, float y) {
    out.push_back(static_cast<float>(kPathOpMove));
    out.push_back(2.0f);
    out.push_back(x);
    out.push_back(y);
}

inline void pushSegLine(std::vector<float>& out, float x, float y) {
    out.push_back(static_cast<float>(kPathOpLine));
    out.push_back(2.0f);
    out.push_back(x);
    out.push_back(y);
}

inline void pushSegCubic(
    std::vector<float>& out,
    float x1, float y1, float x2, float y2, float x3, float y3) {
    out.push_back(static_cast<float>(kPathOpCubic));
    out.push_back(6.0f);
    out.push_back(x1);
    out.push_back(y1);
    out.push_back(x2);
    out.push_back(y2);
    out.push_back(x3);
    out.push_back(y3);
}

inline void pushSegClose(std::vector<float>& out) {
    out.push_back(static_cast<float>(kPathOpClose));
    out.push_back(0.0f);
}

struct CalloutNode {
    double x = 0.0;
    double y = 0.0;
    bool hasI = false;
    double ix = 0.0;
    double iy = 0.0;
    bool hasO = false;
    double ox = 0.0;
    double oy = 0.0;
    bool plain = false;
};

inline CalloutNode makeAnchor(
    double x, double y,
    bool hasI, double ix, double iy,
    bool hasO, double ox, double oy) {
    CalloutNode n;
    n.x = x; n.y = y;
    n.hasI = hasI; n.ix = ix; n.iy = iy;
    n.hasO = hasO; n.ox = ox; n.oy = oy;
    n.plain = false;
    return n;
}

inline CalloutNode makePlain(double x, double y) {
    CalloutNode n;
    n.x = x; n.y = y;
    n.plain = true;
    return n;
}

inline void pushCalloutStep(std::vector<float>& out, const CalloutNode& prev, const CalloutNode& cur) {
    if (prev.plain || cur.plain) {
        pushSegLine(out, static_cast<float>(cur.x), static_cast<float>(cur.y));
    } else {
        pushSegCubic(
            out,
            static_cast<float>(prev.hasO ? prev.ox : prev.x),
            static_cast<float>(prev.hasO ? prev.oy : prev.y),
            static_cast<float>(cur.hasI ? cur.ix : cur.x),
            static_cast<float>(cur.hasI ? cur.iy : cur.y),
            static_cast<float>(cur.x),
            static_cast<float>(cur.y));
    }
}

} // namespace

bool isLinearEasing(float x1, float y1, float x2, float y2) {
    return std::fabs(x1 - y1) < 1e-4f &&
           std::fabs(x2 - y2) < 1e-4f &&
           std::fabs(x1 - (1.0f / 3.0f)) < 1e-3f &&
           std::fabs(x2 - (2.0f / 3.0f)) < 1e-3f;
}

float easingApply(float x1, float y1, float x2, float y2, float t) {
    if (t <= 0.0f) return 0.0f;
    if (t >= 1.0f) return 1.0f;
    float s = t;
    for (int i = 0; i < 6; ++i) {
        float err = bezierEval(x1, x2, s) - t;
        if (std::fabs(err) < 1e-5f) return bezierEval(y1, y2, s);
        float d = bezierDeriv(x1, x2, s);
        if (std::fabs(d) < 1e-6f) break;
        s = clampf(s - err / d, 0.0f, 1.0f);
    }
    float lo = 0.0f;
    float hi = 1.0f;
    s = t;
    for (int i = 0; i < 24; ++i) {
        float x = bezierEval(x1, x2, s);
        if (std::fabs(x - t) < 1e-5f) break;
        if (x < t) lo = s; else hi = s;
        s = (lo + hi) * 0.5f;
    }
    return bezierEval(y1, y2, s);
}

void keyframeValueAt(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t relMs,
    bool isColor,
    float& outA,
    float& outB) {
    if (count <= 0 || timesMs == nullptr || vals == nullptr) {
        outA = 0.0f;
        outB = 0.0f;
        return;
    }
    if (relMs <= timesMs[0]) {
        outA = vals[0];
        outB = vals[1];
        return;
    }
    int last = count - 1;
    if (relMs >= timesMs[last]) {
        outA = vals[last * 6 + 0];
        outB = vals[last * 6 + 1];
        return;
    }
    int i = 0;
    while (i < last && timesMs[i + 1] <= relMs) {
        ++i;
    }
    int64_t span = std::max<int64_t>(1, timesMs[i + 1] - timesMs[i]);
    float t = static_cast<float>(relMs - timesMs[i]) / static_cast<float>(span);
    const float* k0 = vals + i * 6;
    const float* k1 = vals + (i + 1) * 6;
    float e = isLinearEasing(k0[2], k0[3], k0[4], k0[5])
        ? t
        : easingApply(k0[2], k0[3], k0[4], k0[5], t);
    if (!isColor) {
        outA = k0[0] + (k1[0] - k0[0]) * e;
        outB = k0[1] + (k1[1] - k0[1]) * e;
        return;
    }
    uint32_t c0 = unpackColorArgb(k0[0], k0[1]);
    uint32_t c1 = unpackColorArgb(k1[0], k1[1]);
    auto lerpCh = [&](int shift) -> uint32_t {
        float v0 = static_cast<float>((c0 >> shift) & 0xFFu);
        float v1 = static_cast<float>((c1 >> shift) & 0xFFu);
        return static_cast<uint32_t>(clampi(static_cast<int>(v0 + (v1 - v0) * e), 0, 255));
    };
    uint32_t blended = (lerpCh(24) << 24) | (lerpCh(16) << 16) | (lerpCh(8) << 8) | lerpCh(0);
    packColorArgb(blended, outA, outB);
}

int64_t speedSourceOffsetMs(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t relMs) {
    if (relMs <= 0 || count <= 0 || timesMs == nullptr || vals == nullptr) {
        return 0;
    }
    constexpr int64_t kStep = 20;
    double acc = 0.0;
    int64_t t = 0;
    while (t < relMs) {
        int64_t dt = std::min(kStep, relMs - t);
        float a = 0.0f;
        float b = 0.0f;
        keyframeValueAt(timesMs, vals, count, t + dt / 2, false, a, b);
        acc += static_cast<double>(a) * static_cast<double>(dt);
        t += dt;
    }
    return static_cast<int64_t>(acc);
}

std::vector<float> speedRampTable(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t durationMs) {
    if (count <= 0 || timesMs == nullptr || vals == nullptr) {
        return {};
    }
    constexpr double kBlockMs = 512.0 * 1000.0 / 48000.0;
    int blocks = std::max(1, static_cast<int>(std::ceil(static_cast<double>(durationMs) / kBlockMs)));
    std::vector<float> out(static_cast<size_t>(blocks));
    for (int i = 0; i < blocks; ++i) {
        int64_t midMs = static_cast<int64_t>((static_cast<double>(i) + 0.5) * kBlockMs);
        float a = 0.0f;
        float b = 0.0f;
        keyframeValueAt(timesMs, vals, count, midMs, false, a, b);
        out[static_cast<size_t>(i)] = clampf(a, kMinSpeed, kMaxSpeed);
    }
    return out;
}

bool amFxTrackValueAt(
    const float* values,
    int length,
    float rel,
    int start,
    float& outValue) {
    if (values == nullptr || start < 0 || length <= start) return false;
    int n = static_cast<int>(values[start]);
    constexpr int kKeyStride = 6;
    if (n < 2 || length < start + 1 + n * kKeyStride) return false;

    auto tAt = [&](int idx) -> float { return values[start + 1 + idx * kKeyStride]; };
    auto vAt = [&](int idx) -> float { return values[start + 1 + idx * kKeyStride + 1]; };

    if (rel <= tAt(0)) {
        outValue = vAt(0);
        return true;
    }
    if (rel >= tAt(n - 1)) {
        outValue = vAt(n - 1);
        return true;
    }
    int i = 0;
    while (i < n - 2 && tAt(i + 1) <= rel) {
        ++i;
    }
    float span = std::max(1e-6f, tAt(i + 1) - tAt(i));
    float f = clampf((rel - tAt(i)) / span, 0.0f, 1.0f);
    int base = start + 1 + (i + 1) * kKeyStride;
    float eased = easingApply(values[base + 2], values[base + 3], values[base + 4], values[base + 5], f);
    outValue = vAt(i) + (vAt(i + 1) - vAt(i)) * eased;
    return true;
}

bool motionBlurPos(
    float p0x, float p0y,
    float p1x, float p1y, float p1Rot,
    float canvasHeightPx, float tune,
    float& outAngleDeg, float& outLength) {
    if (canvasHeightPx <= 0.0f) return false;
    float vx = p1x - p0x;
    float vy = p1y - p0y;
    float speedPx = std::hypot(vx, vy);
    if (speedPx < 0.5f) return false;
    float length = clampf((speedPx / canvasHeightPx) * 675.0f * clampf(tune, 0.0f, 1.0f), 0.0f, 100.0f);
    if (length < 0.5f) return false;
    float rawDeg = static_cast<float>(std::atan2(static_cast<double>(-vy), static_cast<double>(vx)) * kRadToDeg) + p1Rot;
    float angle = std::fmod(std::fmod(rawDeg, 360.0f) + 360.0f, 360.0f);
    outAngleDeg = angle;
    outLength = length;
    return true;
}

bool motionBlurRadial(
    float p0Rot, float p0Scale,
    float p1Rot, float p1Scale,
    float tune, bool useScale, bool useAngle,
    float& outAmount, float& outZoomPct) {
    if (!useScale && !useAngle) return false;
    float dDeg = std::fmod(std::fmod(p1Rot - p0Rot, 360.0f) + 540.0f, 360.0f) - 180.0f;
    float spinPart = useAngle ? 2.0f * std::fabs(static_cast<float>(static_cast<double>(dDeg) * kDegToRad)) : 0.0f;
    float sMid = (p0Scale + p1Scale) * 0.5f;
    if (sMid <= 0.0f) return false;
    float zoomPart = useScale ? 2.0f * std::fabs((p1Scale - p0Scale) / sMid) / 0.6f : 0.0f;
    float amt = spinPart + zoomPart;
    if (amt < 0.005f) return false;
    float amount = clampf(amt * clampf(tune, 0.0f, 1.0f) * 100.0f, 0.0f, 100.0f);
    if (amount < 0.5f) return false;
    outAmount = amount;
    outZoomPct = (amt > 0.0f) ? (zoomPart / amt * 100.0f) : 0.0f;
    return true;
}

Mb4Resolved motionBlur4(
    float p0x, float p0y, float p0Rot, float p0Scale,
    float p1x, float p1y, float p1Rot, float p1Scale,
    float canvasHeightPx,
    bool usePos, bool useScale, bool useAngle) {
    Mb4Resolved res;
    if (usePos) {
        float vx = p1x - p0x;
        float vy = p1y - p0y;
        if (std::fabs(vx) > 0.0001f || std::fabs(vy) > 0.0001f) {
            float sMid = (p0Scale + p1Scale) * 0.5f / 100.0f;
            if (sMid > 0.001f && canvasHeightPx > 0.0f) {
                double th = static_cast<double>(p1Rot) * kDegToRad;
                float c = static_cast<float>(std::cos(th));
                float s = static_cast<float>(std::sin(th));
                res.hasPos = true;
                res.posPx = (vx * c + vy * s) / sMid / canvasHeightPx;
                res.posPy = (-vx * s + vy * c) / sMid / canvasHeightPx;
            }
        }
    }
    if (useScale) {
        float sMid = (p0Scale + p1Scale) * 0.5f;
        if (sMid > 0.0f) {
            float svel = (p1Scale - p0Scale) / sMid;
            if (std::fabs(svel) > 0.0001f) {
                res.hasScale = true;
                res.scaleVel = svel;
            }
        }
    }
    if (useAngle) {
        float dDeg = std::fmod(std::fmod(p1Rot - p0Rot, 360.0f) + 540.0f, 360.0f) - 180.0f;
        if (std::fabs(dDeg) > 0.0001f) {
            res.hasAngle = true;
            res.angleDeg = dDeg;
        }
    }
    return res;
}

RepeatEvalOutput repeatEval(
    int mode,
    const float* fxVals, int fxLen,
    float clipX, float clipY, float clipRot, float clipScale, float clipOp,
    float undoRotDeg, float outerScale, float outerRotDeg,
    int64_t clipStartMs, int64_t clipEndMs, int64_t timeMs) {
    RepeatEvalOutput out;
    int n = clampi(ktRoundToInt(fxAt(fxVals, fxLen, 0, 1.0f)), 1, kRepeatMaxCount);

    float undoRad = undoRotDeg * kDegToRadF;
    float cosU = std::cos(undoRad);
    float sinU = std::sin(undoRad);

    if (mode == kRepeatModeRadial) {
        float radius = fxAt(fxVals, fxLen, 1, 0.0f) * 2.0f;
        float startAngle = fxAt(fxVals, fxLen, 2, 0.0f);
        float sweep = fxAt(fxVals, fxLen, 3, 360.0f);
        float orientation = fxAt(fxVals, fxLen, 4, 0.0f);
        float baseScale = fxAt(fxVals, fxLen, 5, 100.0f) / 100.0f;
        float alpha = fxAt(fxVals, fxLen, 6, 100.0f) / 100.0f;
        float angle = fxAt(fxVals, fxLen, 7, 0.0f);
        float scaleStep = fxAt(fxVals, fxLen, 8, 100.0f) / 100.0f;
        readAltColor(fxVals, fxLen, 9, out.hasAltColor, out.altColorArgb);

        out.copies.resize(static_cast<size_t>(n));
        for (int i = 0; i < n; ++i) {
            float aDeg = startAngle + static_cast<float>(i) * (sweep / static_cast<float>(n));
            float a = aDeg * kDegToRadF;
            float ax = radius * std::sin(a);
            float ay = -radius * std::cos(a);
            float ux = ax * cosU + ay * sinU;
            float uy = -ax * sinU + ay * cosU;
            RepeatCopyPose& cp = out.copies[static_cast<size_t>(i)];
            cp.x = clipX + ux;
            cp.y = clipY + uy;
            cp.rotDeg = clipRot + aDeg + orientation + static_cast<float>(i) * angle;
            cp.scalePercent = std::max(0.01f, clipScale * baseScale * std::pow(scaleStep, static_cast<float>(i)));
            cp.opacity = clampf(clipOp * alpha, 0.0f, 100.0f);
        }
    } else if (mode == kRepeatModeScatter) {
        float radius = fxAt(fxVals, fxLen, 1, 0.0f);
        float ox = fxAt(fxVals, fxLen, 2, 0.0f);
        float oy = fxAt(fxVals, fxLen, 3, 0.0f);
        float seed = fxAt(fxVals, fxLen, 4, 0.0f);
        float scaleFloor = clampf(fxAt(fxVals, fxLen, 5, 0.0f) / 100.0f, 0.0f, 1.0f);
        float alpha = fxAt(fxVals, fxLen, 6, 100.0f) / 100.0f;
        float fieldAngle = fxAt(fxVals, fxLen, 7, 0.0f) * kDegToRadF;
        float e = scatterEvolutionAt(fxVals, fxLen, clipStartMs, clipEndMs, timeMs);
        readAltColor(fxVals, fxLen, 12, out.hasAltColor, out.altColorArgb);
        int32_t seedBits = ktRoundToInt(seed * 1000.0f) + ktRoundToInt(ox * 131.0f + oy * 57.0f);
        float twoPi = 2.0f * kPiF;

        out.copies.resize(static_cast<size_t>(n));
        for (int i = 0; i < n; ++i) {
            float u1 = repeatHash01(seedBits, i, 1);
            float u2 = repeatHash01(seedBits, i, 2);
            float u3 = repeatHash01(seedBits, i, 3);
            float u4 = repeatHash01(seedBits, i, 4);
            float u5 = repeatHash01(seedBits, i, 5);
            float theta = twoPi * u1 + fieldAngle - twoPi * u4 * 0.375f * e;
            float rho = radius * std::sqrt(u2) * (0.55f + 0.45f * std::sin(twoPi * (0.8f * e + u5)));
            float ax = rho * std::cos(theta);
            float ay = rho * std::sin(theta);
            float ux = ax * cosU + ay * sinU;
            float uy = -ax * sinU + ay * cosU;
            RepeatCopyPose& cp = out.copies[static_cast<size_t>(i)];
            cp.x = clipX + ux;
            cp.y = clipY + uy;
            cp.rotDeg = clipRot;
            cp.scalePercent = std::max(0.01f, clipScale * (scaleFloor + (1.0f - scaleFloor) * u3));
            cp.opacity = clampf(clipOp * alpha, 0.0f, 100.0f);
        }
    } else if (mode == kRepeatModeLine) {
        float px = fxAt(fxVals, fxLen, 1, 0.0f);
        float py = fxAt(fxVals, fxLen, 2, 0.0f);
        float ox = fxAt(fxVals, fxLen, 3, 0.0f);
        float oy = fxAt(fxVals, fxLen, 4, 0.0f);
        float angle = fxAt(fxVals, fxLen, 5, 0.0f);
        float scale = fxAt(fxVals, fxLen, 6, 100.0f) / 100.0f;
        float alpha = fxAt(fxVals, fxLen, 7, 100.0f) / 100.0f;
        readAltColor(fxVals, fxLen, 9, out.hasAltColor, out.altColorArgb);
        float a = angle * kDegToRadF;
        float ca = std::cos(a);
        float sa = std::sin(a);

        out.copies.resize(static_cast<size_t>(n));
        for (int i = 0; i < n; ++i) {
            float lx = px + static_cast<float>(i) * ox;
            float ly = py + static_cast<float>(i) * oy;
            float rx = lx * ca - ly * sa;
            float ry = lx * sa + ly * ca;
            float ux = rx * cosU + ry * sinU;
            float uy = -rx * sinU + ry * cosU;
            RepeatCopyPose& cp = out.copies[static_cast<size_t>(i)];
            cp.x = clipX + ux;
            cp.y = clipY + uy;
            cp.rotDeg = clipRot;
            cp.scalePercent = std::max(0.01f, clipScale * std::pow(scale, static_cast<float>(i)));
            cp.opacity = clampf(clipOp * std::pow(alpha, static_cast<float>(i)), 0.0f, 100.0f);
        }
    } else {
        // Plain repeat
        if (n <= 1) {
            out.copies.push_back({clipX, clipY, clipRot, clipScale, clipOp});
        } else {
            float ox = fxAt(fxVals, fxLen, 1, 0.0f);
            float oy = fxAt(fxVals, fxLen, 2, 0.0f);
            float angle = fxAt(fxVals, fxLen, 3, 0.0f);
            float scale = fxAt(fxVals, fxLen, 4, 100.0f) / 100.0f;
            float alpha = fxAt(fxVals, fxLen, 5, 100.0f) / 100.0f;
            float ux = ox * cosU + oy * sinU;
            float uy = -ox * sinU + oy * cosU;
            out.copies.resize(static_cast<size_t>(n));
            for (int i = 0; i < n; ++i) {
                RepeatCopyPose& cp = out.copies[static_cast<size_t>(i)];
                if (i == 0) {
                    cp = {clipX, clipY, clipRot, clipScale, clipOp};
                } else {
                    cp.x = clipX + static_cast<float>(i) * ux;
                    cp.y = clipY + static_cast<float>(i) * uy;
                    cp.rotDeg = clipRot + static_cast<float>(i) * angle;
                    cp.scalePercent = std::max(0.01f, clipScale * std::pow(scale, static_cast<float>(i)));
                    cp.opacity = clampf(clipOp * std::pow(alpha, static_cast<float>(i)), 0.0f, 100.0f);
                }
            }
        }
    }

    if (!(outerScale == 1.0f && outerRotDeg == 0.0f) && out.copies.size() > 1) {
        float rad = outerRotDeg * kDegToRadF;
        float c = std::cos(rad);
        float s = std::sin(rad);
        for (auto& cp : out.copies) {
            float dx = cp.x - clipX;
            float dy = cp.y - clipY;
            if (dx != 0.0f || dy != 0.0f) {
                cp.x = clipX + (dx * c - dy * s) * outerScale;
                cp.y = clipY + (dx * s + dy * c) * outerScale;
            }
        }
    }

    return out;
}

void groupCopyOffset(
    float anchorX, float anchorY,
    float dx, float dy, float rotDeg, float scaleMul, float k,
    float& outX, float& outY) {
    float rad = rotDeg * kDegToRadF;
    float cosr = std::cos(rad);
    float sinr = std::sin(rad);
    float rx = scaleMul * (cosr * anchorX - sinr * anchorY);
    float ry = scaleMul * (sinr * anchorX + cosr * anchorY);
    outX = anchorX + dx * k - rx;
    outY = anchorY + dy * k - ry;
}

void textAnimValueAt(
    const float* keyTimes,
    const float* keyVals,
    int keyCount,
    int dims,
    const uint8_t* keyHasEasing,
    const float* keyEasings,
    bool stepOnly,
    float p,
    float* outVals) {
    if (keyCount <= 0 || dims <= 0 || keyTimes == nullptr || keyVals == nullptr || outVals == nullptr) {
        return;
    }
    if (p <= keyTimes[0]) {
        for (int j = 0; j < dims; ++j) outVals[j] = keyVals[j];
        return;
    }
    int last = keyCount - 1;
    if (p >= keyTimes[last]) {
        const float* kv = keyVals + last * dims;
        for (int j = 0; j < dims; ++j) outVals[j] = kv[j];
        return;
    }
    int i = 0;
    for (int k = last; k >= 0; --k) {
        if (keyTimes[k] <= p) {
            i = k;
            break;
        }
    }
    if (i >= last) i = last - 1;
    const float* v0 = keyVals + i * dims;
    const float* v1 = keyVals + (i + 1) * dims;
    if (stepOnly) {
        for (int j = 0; j < dims; ++j) outVals[j] = v0[j];
        return;
    }
    float span = std::max(1e-6f, keyTimes[i + 1] - keyTimes[i]);
    float raw = clampf((p - keyTimes[i]) / span, 0.0f, 1.0f);
    float f = raw;
    if (keyHasEasing != nullptr && keyEasings != nullptr && keyHasEasing[i + 1] != 0) {
        const float* ez = keyEasings + (i + 1) * 4;
        f = easingApply(ez[0], ez[1], ez[2], ez[3], raw);
    }
    for (int j = 0; j < dims; ++j) {
        outVals[j] = v0[j] + (v1[j] - v0[j]) * f;
    }
}

void textProgressVisibleRange(
    int count, float start, float end,
    int& outFirst, int& outLast) {
    outFirst = -1;
    outLast = -1;
    if (count <= 0) return;
    float lo = clampf(std::min(start, end), 0.0f, 1.0f);
    float hi = clampf(std::max(start, end), 0.0f, 1.0f);
    float invCount = 1.0f / static_cast<float>(count);
    for (int i = 0; i < count; ++i) {
        float a = std::max(static_cast<float>(i) * invCount, lo);
        float b = std::min(static_cast<float>(i + 1) * invCount, hi);
        if ((b - a) > 0.0001f) {
            if (outFirst < 0) outFirst = i;
            outLast = i;
        }
    }
}

std::u16string counterSubstituteUtf16(
    const uint16_t* chars,
    int length,
    double offset,
    double scale) {
    if (chars == nullptr || length <= 0) return {};
    bool hasDigit = false;
    for (int i = 0; i < length; ++i) {
        if (isAsciiDigit(chars[i])) {
            hasDigit = true;
            break;
        }
    }
    if (!hasDigit) {
        return std::u16string(reinterpret_cast<const char16_t*>(chars), static_cast<size_t>(length));
    }

    std::u16string out;
    out.reserve(static_cast<size_t>(length) + 8);
    int i = 0;
    while (i < length) {
        CounterToken tok;
        if (!scanCounterNumber(chars, length, i, tok)) {
            out.push_back(static_cast<char16_t>(chars[i]));
            ++i;
        } else {
            std::string formatted = formatCounterValue(tok.value * scale + offset, tok.decimals, tok.grouped);
            for (char ch : formatted) {
                out.push_back(static_cast<char16_t>(static_cast<unsigned char>(ch)));
            }
            i = tok.end;
        }
    }
    return out;
}

double pieArcSweepDegrees(double startDeg, double endDeg) {
    double low = 0.0;
    double high = 0.0;
    preparedPieArcAngles(startDeg, endDeg, low, high);
    double angleDiff = std::fmod(high - low, kTwoPi);
    double startN = (std::fabs(low) > kTwoPi) ? std::fmod(low, kTwoPi) : low;
    double endN = startN + angleDiff;
    return std::min(kTwoPi, std::fabs(endN - startN)) * kRadToDeg;
}

std::vector<float> pieOutlineSegments(double startDeg, double endDeg, double radius) {
    double low = 0.0;
    double high = 0.0;
    preparedPieArcAngles(startDeg, endDeg, low, high);
    auto curves = createArcCurves(radius, low, high);
    if (curves.empty()) return {};
    std::vector<float> out;
    out.reserve(4 + curves.size() * 8 + 4 + 2);
    pushSegMove(out, static_cast<float>(curves[0][0]), static_cast<float>(curves[0][1]));
    for (const auto& c : curves) {
        pushSegCubic(
            out,
            static_cast<float>(c[2]), static_cast<float>(c[3]),
            static_cast<float>(c[4]), static_cast<float>(c[5]),
            static_cast<float>(c[6]), static_cast<float>(c[7]));
    }
    pushSegLine(out, 0.0f, 0.0f);
    pushSegClose(out);
    return out;
}

std::vector<float> arcOutlineSegments(double startDeg, double endDeg, double radius, bool closed) {
    double low = 0.0;
    double high = 0.0;
    preparedPieArcAngles(startDeg, endDeg, low, high);
    auto curves = createArcCurves(radius, low, high);
    if (curves.empty()) return {};
    std::vector<float> out;
    out.reserve(4 + curves.size() * 8 + (closed ? 2 : 0));
    pushSegMove(out, static_cast<float>(curves[0][0]), static_cast<float>(curves[0][1]));
    for (const auto& c : curves) {
        pushSegCubic(
            out,
            static_cast<float>(c[2]), static_cast<float>(c[3]),
            static_cast<float>(c[4]), static_cast<float>(c[5]),
            static_cast<float>(c[6]), static_cast<float>(c[7]));
    }
    if (closed) pushSegClose(out);
    return out;
}

std::vector<float> calloutOutlineSegments(
    double width, double height,
    double tailX, double tailY,
    double cornerRadius, double tailWidth) {
    double w = width;
    double h = height;
    double r = std::min(std::max(0.0, cornerRadius), std::min(w, h));
    double pointOffs = r * (1.0 - 0.552228474);

    std::vector<CalloutNode> nodes;
    nodes.reserve(11);
    nodes.push_back(makeAnchor(-w + r, -h, true, -w + pointOffs, -h, false, 0.0, 0.0));
    nodes.push_back(makeAnchor(w - r, -h, false, 0.0, 0.0, true, w - pointOffs, -h));
    nodes.push_back(makeAnchor(w, -h + r, true, w, -h + pointOffs, false, 0.0, 0.0));
    nodes.push_back(makeAnchor(w, h - r, false, 0.0, 0.0, true, w, h - pointOffs));
    nodes.push_back(makeAnchor(w - r, h, true, w - pointOffs, h, false, 0.0, 0.0));
    nodes.push_back(makeAnchor(-w + r, h, false, 0.0, 0.0, true, -w + pointOffs, h));
    nodes.push_back(makeAnchor(-w, h - r, true, -w, h - pointOffs, false, 0.0, 0.0));
    nodes.push_back(makeAnchor(-w, -h + r, false, 0.0, 0.0, true, -w, -h + pointOffs));

    constexpr double kHandleAttraction = 3.5;
    if (std::fabs(tailY) <= std::fabs(tailX)) {
        double minPos = -h + r;
        double maxPos = h - r;
        double tailHalfWidth = std::min(tailWidth, maxPos - minPos) / 2.0;
        double tailPos = tailY * kHandleAttraction;
        tailPos = std::max(tailPos, minPos + tailHalfWidth);
        tailPos = std::min(tailPos, maxPos - tailHalfWidth);
        if (tailX < 0.0) {
            CalloutNode ins[3] = {
                makePlain(-w, tailPos + tailHalfWidth),
                makePlain(tailX, tailY),
                makePlain(-w, tailPos - tailHalfWidth)
            };
            nodes.insert(nodes.begin() + 7, ins, ins + 3);
        } else {
            CalloutNode ins[3] = {
                makePlain(w, tailPos - tailHalfWidth),
                makePlain(tailX, tailY),
                makePlain(w, tailPos + tailHalfWidth)
            };
            nodes.insert(nodes.begin() + 3, ins, ins + 3);
        }
    } else {
        double minPos = -w + r;
        double maxPos = w - r;
        double tailHalfWidth = std::min(tailWidth, maxPos - minPos) / 2.0;
        double tailPos = tailX * kHandleAttraction;
        tailPos = std::max(tailPos, minPos + tailHalfWidth);
        tailPos = std::min(tailPos, maxPos - tailHalfWidth);
        if (tailY < 0.0) {
            CalloutNode ins[3] = {
                makePlain(tailPos - tailHalfWidth, -h),
                makePlain(tailX, tailY),
                makePlain(tailPos + tailHalfWidth, -h)
            };
            nodes.insert(nodes.begin() + 1, ins, ins + 3);
        } else {
            CalloutNode ins[3] = {
                makePlain(tailPos + tailHalfWidth, h),
                makePlain(tailX, tailY),
                makePlain(tailPos - tailHalfWidth, h)
            };
            nodes.insert(nodes.begin() + 5, ins, ins + 3);
        }
    }

    std::vector<float> out;
    out.reserve(nodes.size() * 8 + 6);
    pushSegMove(out, static_cast<float>(nodes[0].x), static_cast<float>(nodes[0].y));
    for (size_t i = 1; i < nodes.size(); ++i) {
        pushCalloutStep(out, nodes[i - 1], nodes[i]);
    }
    pushCalloutStep(out, nodes.back(), nodes[0]);
    pushSegClose(out);
    return out;
}

void gradEndpoints(
    int gradType, float cx, float cy, float angleDeg, float scale,
    float out4[4]) {
    double rad = static_cast<double>(angleDeg) * kDegToRad;
    float half = 0.5f * scale;
    if (gradType == kGradRadial) {
        out4[0] = cx;
        out4[1] = cy;
        out4[2] = cx + half;
        out4[3] = cy;
    } else if (gradType == kGradSweep) {
        out4[0] = cx;
        out4[1] = cy;
        out4[2] = cx - static_cast<float>(std::cos(rad)) * half;
        out4[3] = cy - static_cast<float>(std::sin(rad)) * half;
    } else {
        float dx = static_cast<float>(std::sin(rad));
        float dy = -static_cast<float>(std::cos(rad));
        out4[0] = cx - dx * half;
        out4[1] = cy - dy * half;
        out4[2] = cx + dx * half;
        out4[3] = cy + dy * half;
    }
}

void gradAngleScale(
    int gradType, float x1, float y1, float x2, float y2,
    float out2[2]) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    if (gradType == kGradRadial) {
        out2[0] = 0.0f;
        out2[1] = 2.0f * dx;
    } else if (gradType == kGradSweep) {
        float half = std::hypot(dx, dy);
        if (half < 1e-6f) {
            out2[0] = 0.0f;
            out2[1] = 0.0f;
        } else {
            float a = static_cast<float>(std::atan2(static_cast<double>(-dy), static_cast<double>(-dx)) * kRadToDeg);
            out2[0] = (a < 0.0f) ? (a + 360.0f) : a;
            out2[1] = 2.0f * half;
        }
    } else {
        float len = std::hypot(dx, dy);
        if (len < 1e-6f) {
            out2[0] = 0.0f;
            out2[1] = 0.0f;
        } else {
            float a = static_cast<float>(std::atan2(static_cast<double>(dx), static_cast<double>(-dy)) * kRadToDeg);
            out2[0] = (a < 0.0f) ? (a + 360.0f) : a;
            out2[1] = len;
        }
    }
}

} // namespace am
