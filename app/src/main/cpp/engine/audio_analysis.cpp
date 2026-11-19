#include "audio_analysis.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <sys/types.h>

namespace am {
namespace audio_analysis {

namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr int kMinBeatFrameSamples = 512;
constexpr int64_t kMinBeatSpacingMs = 240;
constexpr int64_t kMinTempoIntervalMs = 240;
constexpr int64_t kMaxTempoIntervalMs = 2000;
constexpr float kBeatMedianFactor = 1.5f;
constexpr float kBeatAbsoluteFloor = 0.04f;

// Iterative in-place radix-2 FFT (unscaled, power-of-two n).
void radix2Fft(float* re, float* im, int n) {
    int j = 0;
    for (int i = 1; i < n; ++i) {
        int bit = n >> 1;
        while ((j & bit) != 0) {
            j ^= bit;
            bit >>= 1;
        }
        j |= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        const double ang = -2.0 * kPi / static_cast<double>(len);
        const float wr = static_cast<float>(std::cos(ang));
        const float wi = static_cast<float>(std::sin(ang));
        const int half = len >> 1;
        for (int i = 0; i < n; i += len) {
            float cwr = 1.0f;
            float cwi = 0.0f;
            for (int k = 0; k < half; ++k) {
                const float ur = re[i + k];
                const float ui = im[i + k];
                const float vr = re[i + k + half] * cwr - im[i + k + half] * cwi;
                const float vi = re[i + k + half] * cwi + im[i + k + half] * cwr;
                re[i + k] = ur + vr;
                im[i + k] = ui + vi;
                re[i + k + half] = ur - vr;
                im[i + k + half] = ui - vi;
                const float nwr = cwr * wr - cwi * wi;
                cwi = cwr * wi + cwi * wr;
                cwr = nwr;
            }
        }
    }
}

void radix2Ifft(float* re, float* im, int n) {
    for (int i = 0; i < n; ++i) im[i] = -im[i];
    radix2Fft(re, im, n);
    const float invN = 1.0f / static_cast<float>(n);
    for (int i = 0; i < n; ++i) {
        re[i] *= invN;
        im[i] = -im[i] * invN;
    }
}

int beatFrameSizeFor(int sampleRate) {
    int size = 512;
    while (size < 4096 && size * 2 <= sampleRate / 16) size *= 2;
    return size;
}

float sliceMedian(const float* values, int count, std::vector<float>& scratch) {
    if (count <= 0) return 0.0f;
    scratch.assign(values, values + count);
    std::sort(scratch.begin(), scratch.end());
    return scratch[static_cast<size_t>(count / 2)];
}

inline double lerpD(double a, double b, double t) { return a + (b - a) * t; }

inline float duckFloorGain(float amountDb) {
    if (!(amountDb > 0.0f)) return 1.0f;
    const float clamped = amountDb > kDuckMaxAmountDb ? kDuckMaxAmountDb : amountDb;
    return static_cast<float>(std::pow(10.0, -static_cast<double>(clamped) / 20.0));
}

inline double duckSmoothFactor(float smoothness) {
    if (std::isnan(smoothness)) return 0.5;
    const float s = std::clamp(smoothness, 0.0f, 1.0f);
    return static_cast<double>(s);
}

inline double duckAttackMs(float smoothness) {
    return std::exp(lerpD(std::log(2.0), std::log(50.0), duckSmoothFactor(smoothness)));
}

inline double duckReleaseMs(float smoothness) {
    return std::exp(lerpD(std::log(60.0), std::log(1000.0), duckSmoothFactor(smoothness)));
}

inline float duckTargetGain(float level, float floorVal) {
    if (floorVal >= 1.0f) return 1.0f;
    if (std::isnan(level)) return std::numeric_limits<float>::quiet_NaN();
    const float l = std::fabs(level);
    if (l <= kDuckThreshold) return 1.0f;
    const float over = std::clamp((l - kDuckThreshold) / (1.0f - kDuckThreshold), 0.0f, 1.0f);
    return 1.0f - over * (1.0f - floorVal);
}

inline int64_t floorDiv64(int64_t a, int64_t b) {
    int64_t q = a / b;
    int64_t r = a % b;
    if (r != 0 && ((r < 0) != (b < 0))) --q;
    return q;
}

const std::vector<float>& noiseHannWindow() {
    static const std::vector<float> win = [] {
        std::vector<float> w(kNoiseFftSize);
        for (int i = 0; i < kNoiseFftSize; ++i) {
            w[static_cast<size_t>(i)] =
                0.5f - 0.5f * static_cast<float>(std::cos(2.0 * kPi * i / kNoiseFftSize));
        }
        return w;
    }();
    return win;
}

inline float noiseRawGain(float magnitude, float noise, float strength) {
    if (!(strength > 0.0f)) return 1.0f;
    const float s = std::clamp(strength, 0.0f, 1.0f);
    if (!(s > 0.0f)) return 1.0f;
    if (magnitude <= 0.0f) return kNoiseFloorGain;
    const double over = static_cast<double>(kNoiseMaxOver * s);
    const double r = over * static_cast<double>(noise) / static_cast<double>(magnitude);
    const double g2 = 1.0 - r * r;
    const double floor2 = static_cast<double>(kNoiseFloorGain) * static_cast<double>(kNoiseFloorGain);
    return g2 <= floor2 ? kNoiseFloorGain : static_cast<float>(std::sqrt(g2));
}

inline double noiseStepCoef(double tauMs) {
    return 1.0 - std::exp(-static_cast<double>(kNoiseHop) * 1000.0 /
                          (tauMs * static_cast<double>(kNoiseSampleRate)));
}

void writeLe16(uint8_t* p, uint16_t v) {
    p[0] = static_cast<uint8_t>(v & 0xFFu);
    p[1] = static_cast<uint8_t>((v >> 8) & 0xFFu);
}

void writeLe32(uint8_t* p, uint32_t v) {
    p[0] = static_cast<uint8_t>(v & 0xFFu);
    p[1] = static_cast<uint8_t>((v >> 8) & 0xFFu);
    p[2] = static_cast<uint8_t>((v >> 16) & 0xFFu);
    p[3] = static_cast<uint8_t>((v >> 24) & 0xFFu);
}

}  // namespace

// ---- Beat Detection ----------------------------------------------------------------------------

double estimateBpm(const int64_t* beatsMs, int count) {
    if (!beatsMs || count < 2) return 0.0;
    std::vector<int64_t> intervals;
    intervals.reserve(static_cast<size_t>(count - 1));
    for (int i = 1; i < count; ++i) {
        const int64_t d = beatsMs[i] - beatsMs[i - 1];
        if (d >= kMinTempoIntervalMs && d <= kMaxTempoIntervalMs) {
            intervals.push_back(d);
        }
    }
    if (intervals.empty()) return 0.0;
    std::sort(intervals.begin(), intervals.end());
    const double med = static_cast<double>(intervals[intervals.size() / 2]);
    if (med <= 0.0) return 0.0;
    return std::round(60000.0 / med * 10.0) / 10.0;
}

BeatResult detectBeats(const float* samples, int count, int sampleRate) {
    BeatResult result;
    if (!samples || sampleRate <= 0 || count < kMinBeatFrameSamples) return result;
    const int frameSize = beatFrameSizeFor(sampleRate);
    if (count < frameSize) return result;
    const int hop = frameSize / 4;
    const int frameCount = (count - frameSize) / hop + 1;

    std::vector<float> flux(static_cast<size_t>(frameCount), 0.0f);
    std::vector<float> re(static_cast<size_t>(frameSize), 0.0f);
    std::vector<float> im(static_cast<size_t>(frameSize), 0.0f);
    std::vector<float> prev(static_cast<size_t>(frameSize / 2 + 1), 0.0f);
    std::vector<float> window(static_cast<size_t>(frameSize));
    for (int i = 0; i < frameSize; ++i) {
        window[static_cast<size_t>(i)] = static_cast<float>(
            0.5 - 0.5 * std::cos(2.0 * kPi * static_cast<double>(i) /
                                 static_cast<double>(frameSize - 1)));
    }

    float maxFlux = 0.0f;
    for (int k = 0; k < frameCount; ++k) {
        const int start = k * hop;
        for (int i = 0; i < frameSize; ++i) {
            re[static_cast<size_t>(i)] = samples[start + i] * window[static_cast<size_t>(i)];
            im[static_cast<size_t>(i)] = 0.0f;
        }
        radix2Fft(re.data(), im.data(), frameSize);
        float f = 0.0f;
        const int halfBins = frameSize / 2;
        for (int b = 0; b <= halfBins; ++b) {
            const float m = std::sqrt(re[b] * re[b] + im[b] * im[b]);
            const float growth = m - prev[b];
            if (growth > 0.0f) f += growth;
            prev[b] = m;
        }
        flux[static_cast<size_t>(k)] = f;
        if (f > maxFlux) maxFlux = f;
    }
    if (maxFlux <= 0.0f) return result;
    for (float& v : flux) v /= maxFlux;

    const int halfWin = std::max(1, (sampleRate / 2) / hop);
    const int spacingFrames = std::max(
        1, static_cast<int>((kMinBeatSpacingMs * static_cast<int64_t>(sampleRate)) /
                            (1000LL * static_cast<int64_t>(hop))));

    std::vector<int> candidates;
    std::vector<float> slice(static_cast<size_t>(2 * halfWin + 1));
    std::vector<float> medScratch;
    medScratch.reserve(slice.size());
    for (int k = 0; k < frameCount; ++k) {
        const float v = flux[static_cast<size_t>(k)];
        if (v <= kBeatAbsoluteFloor) continue;
        if (k > 0 && v < flux[static_cast<size_t>(k - 1)]) continue;
        if (k < frameCount - 1 && v < flux[static_cast<size_t>(k + 1)]) continue;
        int n = 0;
        const int j0 = std::max(0, k - halfWin);
        const int j1 = std::min(frameCount - 1, k + halfWin);
        for (int j = j0; j <= j1; ++j) slice[static_cast<size_t>(n++)] = flux[static_cast<size_t>(j)];
        const float med = sliceMedian(slice.data(), n, medScratch);
        if (v > med * kBeatMedianFactor + kBeatAbsoluteFloor) {
            candidates.push_back(k);
        }
    }

    std::stable_sort(candidates.begin(), candidates.end(), [&](int a, int b) {
        return flux[static_cast<size_t>(a)] > flux[static_cast<size_t>(b)];
    });
    std::vector<int> accepted;
    for (int k : candidates) {
        bool farEnough = true;
        for (int a : accepted) {
            if (std::abs(a - k) < spacingFrames) {
                farEnough = false;
                break;
            }
        }
        if (farEnough) accepted.push_back(k);
    }
    std::sort(accepted.begin(), accepted.end());

    result.beatsMs.reserve(accepted.size());
    for (int k : accepted) {
        const int64_t ms = (static_cast<int64_t>(k) * hop + frameSize / 2) * 1000LL / sampleRate;
        result.beatsMs.push_back(ms);
    }
    result.bpm = estimateBpm(result.beatsMs.data(), static_cast<int>(result.beatsMs.size()));
    return result;
}

// ---- Sidechain Ducking -------------------------------------------------------------------------

std::vector<float> duckPeakEnvelope(const float* samples, int count, int sampleRate,
                                    int64_t stepMs) {
    if (!samples || count <= 0 || sampleRate <= 0 || stepMs <= 0) return {};
    const int stepSamples = std::max(
        1, static_cast<int>(static_cast<int64_t>(sampleRate) * stepMs / 1000LL));
    const int steps = (count + stepSamples - 1) / stepSamples;
    std::vector<float> out(static_cast<size_t>(steps), 0.0f);
    for (int k = 0; k < steps; ++k) {
        const int from = k * stepSamples;
        const int to = std::min(count, from + stepSamples);
        float peak = 0.0f;
        for (int i = from; i < to; ++i) {
            const float a = std::fabs(samples[i]);
            if (a > peak) peak = a;
        }
        out[static_cast<size_t>(k)] = peak;
    }
    return out;
}

std::vector<float> duckGainsForLevels(const float* levels, int count, float amountDb,
                                      float smoothness, int64_t stepMs) {
    std::vector<float> out(static_cast<size_t>(std::max(0, count)), 1.0f);
    if (!levels || count <= 0) return out;
    const float floorVal = duckFloorGain(amountDb);
    const double aAttack = 1.0 - std::exp(-static_cast<double>(stepMs) / duckAttackMs(smoothness));
    const double aRelease = 1.0 - std::exp(-static_cast<double>(stepMs) / duckReleaseMs(smoothness));
    double g = 1.0;
    for (int k = 0; k < count; ++k) {
        const double raw = static_cast<double>(duckTargetGain(levels[k], floorVal));
        const double t = std::isnan(raw) ? 1.0 : raw;
        const double a = (t < g) ? aAttack : aRelease;
        g += (t - g) * a;
        out[static_cast<size_t>(k)] = static_cast<float>(g);
    }
    return out;
}

std::vector<DuckPoint> duckBuildCurve(int64_t triggerStartMs, const float* triggerLevels,
                                      int levelCount, int64_t duckedStartMs,
                                      int64_t duckedDurationMs, float amountDb,
                                      float smoothness) {
    if (!(amountDb > 0.0f) || duckedDurationMs <= 0) return {};
    const int steps = std::max(
        1, static_cast<int>((duckedDurationMs + kDuckStepMs - 1) / kDuckStepMs));
    std::vector<float> spanLevels(static_cast<size_t>(steps), 0.0f);
    for (int k = 0; k < steps; ++k) {
        const int64_t winStart = duckedStartMs + static_cast<int64_t>(k) * kDuckStepMs;
        const int64_t winEnd = winStart + kDuckStepMs;
        const int64_t first = floorDiv64(winStart - triggerStartMs, kDuckStepMs);
        const int64_t last = floorDiv64(winEnd - 1 - triggerStartMs, kDuckStepMs);
        float peak = 0.0f;
        for (int64_t idx = first; idx <= last; ++idx) {
            if (triggerLevels && idx >= 0 && idx < levelCount) {
                const float v = triggerLevels[idx];
                if (v > peak) peak = v;
            }
        }
        spanLevels[static_cast<size_t>(k)] = peak;
    }

    const std::vector<float> gains = duckGainsForLevels(
        spanLevels.data(), steps, amountDb, smoothness, kDuckStepMs);
    bool anyDuck = false;
    for (float g : gains) {
        if (g < 1.0f - kDuckGainEps) {
            anyDuck = true;
            break;
        }
    }
    if (!anyDuck) return {};

    std::vector<DuckPoint> out;
    out.push_back({0, gains[0]});
    float lastGain = gains[0];
    for (int k = 1; k < steps; ++k) {
        if (std::fabs(gains[static_cast<size_t>(k)] - lastGain) > kDuckGainEps) {
            out.push_back({static_cast<int64_t>(k) * kDuckStepMs, gains[static_cast<size_t>(k)]});
            lastGain = gains[static_cast<size_t>(k)];
        }
    }
    if (out.back().relMs < duckedDurationMs) {
        out.push_back({duckedDurationMs, gains[static_cast<size_t>(steps - 1)]});
    }
    return out;
}

// ---- Spectral Noise Reduction ------------------------------------------------------------------

void noiseFrameMagnitudes(const float* frame2048, float* outBins1025) {
    if (!frame2048 || !outBins1025) return;
    const auto& win = noiseHannWindow();
    float re[kNoiseFftSize];
    float im[kNoiseFftSize];
    for (int i = 0; i < kNoiseFftSize; ++i) {
        re[i] = frame2048[i] * win[static_cast<size_t>(i)];
        im[i] = 0.0f;
    }
    radix2Fft(re, im, kNoiseFftSize);
    for (int k = 0; k < kNoiseBins; ++k) {
        outBins1025[k] = std::sqrt(re[k] * re[k] + im[k] * im[k]);
    }
}

NoiseProfileAccumulator::NoiseProfileAccumulator()
    : counts_(static_cast<size_t>(kNoiseBins) * static_cast<size_t>(kBuckets), 0) {}

int NoiseProfileAccumulator::bucketOf(float magnitude) {
    static const double kLogMin = std::log10(kMinMag);
    static const double kLogStep = (std::log10(kMaxMag) - kLogMin) / static_cast<double>(kBuckets);
    if (!(magnitude > static_cast<float>(kMinMag))) return 0;
    const int b = static_cast<int>((std::log10(static_cast<double>(magnitude)) - kLogMin) / kLogStep);
    return std::clamp(b, 0, kBuckets - 1);
}

float NoiseProfileAccumulator::bucketValue(int bucket) {
    static const double kLogMin = std::log10(kMinMag);
    static const double kLogStep = (std::log10(kMaxMag) - kLogMin) / static_cast<double>(kBuckets);
    return static_cast<float>(std::pow(10.0, kLogMin + (static_cast<double>(bucket) + 0.5) * kLogStep));
}

void NoiseProfileAccumulator::addMagnitudes(const float* bins1025) {
    if (!bins1025) return;
    for (int k = 0; k < kNoiseBins; ++k) {
        const int b = bucketOf(bins1025[k]);
        ++counts_[static_cast<size_t>(k) * kBuckets + static_cast<size_t>(b)];
    }
    ++frames_;
}

void NoiseProfileAccumulator::pushMonoSamples(const float* samples, int count) {
    if (!samples || count <= 0) return;
    streamBuf_.insert(streamBuf_.end(), samples, samples + count);
    drainFrames(false);
}

void NoiseProfileAccumulator::finishMonoStream() {
    drainFrames(true);
    streamBuf_.clear();
    readPos_ = 0;
}

void NoiseProfileAccumulator::drainFrames(bool finalFlush) {
    float frame[kNoiseFftSize];
    float mags[kNoiseBins];
    while (readPos_ < streamBuf_.size()) {
        const size_t avail = streamBuf_.size() - readPos_;
        if (!finalFlush && avail < static_cast<size_t>(kNoiseFftSize)) break;
        const size_t take = std::min(avail, static_cast<size_t>(kNoiseFftSize));
        std::memcpy(frame, streamBuf_.data() + readPos_, take * sizeof(float));
        if (take < static_cast<size_t>(kNoiseFftSize)) {
            std::memset(frame + take, 0, (static_cast<size_t>(kNoiseFftSize) - take) * sizeof(float));
        }
        noiseFrameMagnitudes(frame, mags);
        addMagnitudes(mags);
        readPos_ += static_cast<size_t>(kNoiseHop);
    }
    if (readPos_ > 65536) {
        if (readPos_ >= streamBuf_.size()) {
            streamBuf_.clear();
        } else {
            streamBuf_.erase(streamBuf_.begin(),
                             streamBuf_.begin() + static_cast<ptrdiff_t>(readPos_));
        }
        readPos_ = 0;
    }
}

void NoiseProfileAccumulator::buildProfile(float* outBins1025) const {
    if (!outBins1025) return;
    if (frames_ == 0) {
        std::memset(outBins1025, 0, static_cast<size_t>(kNoiseBins) * sizeof(float));
        return;
    }
    const int rank = std::clamp(
        static_cast<int>(kNoiseProfilePercentile * static_cast<double>(frames_)), 1, frames_);
    for (int k = 0; k < kNoiseBins; ++k) {
        const int32_t* row = counts_.data() + static_cast<size_t>(k) * kBuckets;
        int cum = 0;
        int b = 0;
        while (b < kBuckets) {
            cum += row[b];
            if (cum >= rank) break;
            ++b;
        }
        outBins1025[k] = bucketValue(std::min(b, kBuckets - 1));
    }
}

std::vector<float> noiseProcessMono(const float* input, int count,
                                    const float* profile1025, float strength) {
    if (!input || count <= 0) return {};
    if (!(strength > 0.0f) || !profile1025) {
        return std::vector<float>(input, input + count);
    }
    const auto& win = noiseHannWindow();
    const double attackCoef = noiseStepCoef(kNoiseAttackMs);
    const double releaseCoef = noiseStepCoef(kNoiseReleaseMs);

    std::vector<float> acc(static_cast<size_t>(count + kNoiseFftSize), 0.0f);
    std::vector<float> norm(static_cast<size_t>(count + kNoiseFftSize), 0.0f);
    float re[kNoiseFftSize];
    float im[kNoiseFftSize];
    float raw[kNoiseBins];
    float sm[kNoiseBins];
    float gains[kNoiseBins];
    for (int k = 0; k < kNoiseBins; ++k) gains[k] = 1.0f;
    bool started = false;

    for (int start = 0; start < count; start += kNoiseHop) {
        for (int i = 0; i < kNoiseFftSize; ++i) {
            const float s = (start + i < count) ? input[start + i] : 0.0f;
            re[i] = s * win[static_cast<size_t>(i)];
            im[i] = 0.0f;
        }
        radix2Fft(re, im, kNoiseFftSize);
        for (int k = 0; k < kNoiseBins; ++k) {
            const float mag = std::sqrt(re[k] * re[k] + im[k] * im[k]);
            raw[k] = noiseRawGain(mag, profile1025[k], strength);
        }
        for (int k = 0; k < kNoiseBins; ++k) {
            const float a = raw[k > 0 ? k - 1 : 0];
            const float b = raw[k];
            const float c = raw[k + 1 < kNoiseBins ? k + 1 : kNoiseBins - 1];
            sm[k] = 0.25f * a + 0.5f * b + 0.25f * c;
        }
        if (!started) {
            std::memcpy(gains, sm, sizeof(gains));
            started = true;
        } else {
            for (int k = 0; k < kNoiseBins; ++k) {
                const double a = (sm[k] < gains[k]) ? attackCoef : releaseCoef;
                gains[k] = static_cast<float>(gains[k] + (sm[k] - gains[k]) * a);
            }
        }
        for (int k = 0; k < kNoiseFftSize; ++k) {
            const int idx = std::min(k, kNoiseFftSize - k);
            const float g = gains[idx];
            re[k] *= g;
            im[k] *= g;
        }
        radix2Ifft(re, im, kNoiseFftSize);
        for (int i = 0; i < kNoiseFftSize; ++i) {
            const float w = win[static_cast<size_t>(i)];
            acc[static_cast<size_t>(start + i)] += re[i] * w;
            norm[static_cast<size_t>(start + i)] += w * w;
        }
    }

    std::vector<float> out(static_cast<size_t>(count), 0.0f);
    for (int i = 0; i < count; ++i) {
        const float n = norm[static_cast<size_t>(i)];
        out[static_cast<size_t>(i)] = (n > 1e-6f) ? (acc[static_cast<size_t>(i)] / n) : 0.0f;
    }
    return out;
}

// ---- Waveform Peaks & PCM16 Reverse ------------------------------------------------------------

void normalizeWaveformPeaks(float* peaks, int count) {
    if (!peaks || count <= 0) return;
    float maxVal = 0.0f;
    for (int i = 0; i < count; ++i) {
        if (peaks[i] > maxVal) maxVal = peaks[i];
    }
    if (maxVal > 0.01f) {
        const float inv = 1.0f / maxVal;
        for (int i = 0; i < count; ++i) {
            peaks[i] = std::clamp(peaks[i] * inv, 0.0f, 1.0f);
        }
    }
}

bool reversePcm16FileToWav(const char* pcmPath, const char* wavPath,
                           int channels, int sampleRate, int64_t frames) {
    if (!pcmPath || !wavPath || channels <= 0 || channels > 8 || sampleRate <= 0 || frames <= 0) {
        return false;
    }
    const int64_t frameBytes = static_cast<int64_t>(channels) * 2;
    const int64_t dataBytes = frames * frameBytes;
    if (dataBytes > static_cast<int64_t>(0xFFFFFFFFu - 44u)) return false;

    FILE* in = std::fopen(pcmPath, "rb");
    if (!in) return false;
    FILE* out = std::fopen(wavPath, "wb");
    if (!out) {
        std::fclose(in);
        return false;
    }

    uint8_t hdr[44];
    std::memcpy(hdr + 0, "RIFF", 4);
    writeLe32(hdr + 4, static_cast<uint32_t>(36 + dataBytes));
    std::memcpy(hdr + 8, "WAVE", 4);
    std::memcpy(hdr + 12, "fmt ", 4);
    writeLe32(hdr + 16, 16u);
    writeLe16(hdr + 20, 1u);
    writeLe16(hdr + 22, static_cast<uint16_t>(channels));
    writeLe32(hdr + 24, static_cast<uint32_t>(sampleRate));
    writeLe32(hdr + 28, static_cast<uint32_t>(sampleRate * frameBytes));
    writeLe16(hdr + 32, static_cast<uint16_t>(frameBytes));
    writeLe16(hdr + 34, 16u);
    std::memcpy(hdr + 36, "data", 4);
    writeLe32(hdr + 40, static_cast<uint32_t>(dataBytes));

    if (std::fwrite(hdr, 1, 44, out) != 44) {
        std::fclose(in);
        std::fclose(out);
        return false;
    }

    constexpr int64_t kChunkFrames = 8192;
    std::vector<uint8_t> buf(static_cast<size_t>(kChunkFrames * frameBytes));
    std::vector<uint8_t> rev(static_cast<size_t>(kChunkFrames * frameBytes));

    int64_t remaining = frames;
    bool ok = true;
    while (remaining > 0) {
        const int64_t n = std::min(remaining, kChunkFrames);
        const int64_t startFrame = remaining - n;
        const size_t bytes = static_cast<size_t>(n * frameBytes);
        if (fseeko(in, static_cast<off_t>(startFrame * frameBytes), SEEK_SET) != 0 ||
            std::fread(buf.data(), 1, bytes, in) != bytes) {
            ok = false;
            break;
        }
        const size_t fb = static_cast<size_t>(frameBytes);
        for (int64_t i = 0; i < n; ++i) {
            std::memcpy(rev.data() + static_cast<size_t>(i) * fb,
                        buf.data() + static_cast<size_t>(n - 1 - i) * fb, fb);
        }
        if (std::fwrite(rev.data(), 1, bytes, out) != bytes) {
            ok = false;
            break;
        }
        remaining -= n;
    }

    std::fclose(in);
    if (std::fclose(out) != 0) ok = false;
    return ok;
}

}  // namespace audio_analysis
}  // namespace am
