// Audio Analysis & DSP Suite (Gradual Native Migration, Step 1A):
// Pure C++17 implementations of:
//   - Spectral-flux onset & beat detection (model/BeatDetect.kt)
//   - Sidechain audio ducking envelope & curve builder (model/Ducking.kt)
//   - Spectral noise-reduction profile accumulator & mono reference processor (model/NoiseReduction.kt)
//   - Waveform peak normalization & chunked PCM16 WAV reversal (engine/AudioWaveform.kt, AudioReverse.kt)
//
// Plain C++17, no Android headers: host-testable.
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace audio_analysis {

// ---- Beat Detection (model/BeatDetect.kt) ------------------------------------------------------

struct BeatResult {
    std::vector<int64_t> beatsMs;
    double bpm = 0.0;  // <= 0 means no BPM estimate
};

BeatResult detectBeats(const float* samples, int count, int sampleRate);
double estimateBpm(const int64_t* beatsMs, int count);

// ---- Sidechain Audio Ducking (model/Ducking.kt) ------------------------------------------------

constexpr int64_t kDuckStepMs = 10;
constexpr float kDuckThreshold = 0.02f;
constexpr float kDuckMaxAmountDb = 36.0f;
constexpr float kDuckGainEps = 0.001f;

struct DuckPoint {
    int64_t relMs = 0;
    float gain = 1.0f;
};

std::vector<float> duckPeakEnvelope(const float* samples, int count, int sampleRate,
                                    int64_t stepMs = kDuckStepMs);

std::vector<float> duckGainsForLevels(const float* levels, int count, float amountDb,
                                      float smoothness, int64_t stepMs = kDuckStepMs);

// Returns empty vector when nothing in the ducked span actually ducks (matches Kotlin null).
std::vector<DuckPoint> duckBuildCurve(int64_t triggerStartMs, const float* triggerLevels,
                                      int levelCount, int64_t duckedStartMs,
                                      int64_t duckedDurationMs, float amountDb,
                                      float smoothness);

// ---- Spectral Noise Reduction Profile & Processor (model/NoiseReduction.kt) --------------------

constexpr int kNoiseSampleRate = 48000;
constexpr int kNoiseFftSize = 2048;
constexpr int kNoiseHop = 512;
constexpr int kNoiseBins = kNoiseFftSize / 2 + 1;
constexpr double kNoiseProfilePercentile = 0.10;
constexpr float kNoiseFloorGain = 0.05f;
constexpr float kNoiseMaxOver = 4.0f;
constexpr double kNoiseAttackMs = 10.0;
constexpr double kNoiseReleaseMs = 80.0;

// Computes kNoiseBins magnitude bins for one Hann-windowed mono frame of kNoiseFftSize samples.
void noiseFrameMagnitudes(const float* frame2048, float* outBins1025);

// Streaming log-histogram percentile estimator matching NoiseReduction.ProfileAccumulator.
class NoiseProfileAccumulator {
public:
    static constexpr int kBuckets = 256;
    static constexpr double kMinMag = 1e-5;
    static constexpr double kMaxMag = 2.0;

    NoiseProfileAccumulator();

    void addMagnitudes(const float* bins1025);
    void pushMonoSamples(const float* samples, int count);
    void finishMonoStream();

    int frameCount() const { return frames_; }
    void buildProfile(float* outBins1025) const;

    static int bucketOf(float magnitude);
    static float bucketValue(int bucket);

private:
    void drainFrames(bool finalFlush);

    std::vector<int32_t> counts_;  // kNoiseBins * kBuckets
    int frames_ = 0;

    // Streaming STFT framing buffer (for pushMonoSamples / finishMonoStream).
    std::vector<float> streamBuf_;
    size_t readPos_ = 0;
};

// Reference mono noise-reduction processor matching NoiseReduction.Processor.process().
std::vector<float> noiseProcessMono(const float* input, int count,
                                    const float* profile1025, float strength);

// ---- Waveform Peak Normalization & PCM16 Reversal ----------------------------------------------

void normalizeWaveformPeaks(float* peaks, int count);

// Reverses a raw PCM16 little-endian file frame-by-frame into a standard 16-bit WAV file.
bool reversePcm16FileToWav(const char* pcmPath, const char* wavPath,
                           int channels, int sampleRate, int64_t frames);

}  // namespace audio_analysis
}  // namespace am
