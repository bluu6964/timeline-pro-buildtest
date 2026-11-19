// JNI bridge between com.example.engine.AudioAnalysisNative (Kotlin) and am::audio_analysis
// plus NDK AudioFileSource waveform & noise-profile extraction.
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <vector>

#include "audio_analysis.h"
#include "audio_decoder.h"

using namespace am::audio_analysis;

namespace {
inline NoiseProfileAccumulator* Acc(jlong h) {
    return reinterpret_cast<NoiseProfileAccumulator*>(h);
}
}  // namespace

extern "C" {

// ---- Beat detection ----------------------------------------------------------------------------

JNIEXPORT jlongArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeDetectBeats(
    JNIEnv* env, jclass, jfloatArray samples, jint sampleRate, jdoubleArray bpmOut) {
    if (!samples || sampleRate <= 0) return nullptr;
    const jsize count = env->GetArrayLength(samples);
    if (count <= 0) return env->NewLongArray(0);
    std::vector<jfloat> buf(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, buf.data());

    const BeatResult r = detectBeats(buf.data(), static_cast<int>(count), sampleRate);
    if (bpmOut && env->GetArrayLength(bpmOut) >= 1) {
        jdouble bpm = r.bpm;
        env->SetDoubleArrayRegion(bpmOut, 0, 1, &bpm);
    }
    const jsize n = static_cast<jsize>(r.beatsMs.size());
    jlongArray out = env->NewLongArray(n);
    if (out && n > 0) {
        std::vector<jlong> tmp(r.beatsMs.begin(), r.beatsMs.end());
        env->SetLongArrayRegion(out, 0, n, tmp.data());
    }
    return out;
}

// ---- Sidechain ducking -------------------------------------------------------------------------

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeDuckPeakEnvelope(
    JNIEnv* env, jclass, jfloatArray samples, jint sampleRate, jlong stepMs) {
    if (!samples || sampleRate <= 0 || stepMs <= 0) return env->NewFloatArray(0);
    const jsize count = env->GetArrayLength(samples);
    if (count <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> buf(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, buf.data());

    const std::vector<float> envOut = duckPeakEnvelope(buf.data(), static_cast<int>(count),
                                                       sampleRate, stepMs);
    const jsize n = static_cast<jsize>(envOut.size());
    jfloatArray out = env->NewFloatArray(n);
    if (out && n > 0) env->SetFloatArrayRegion(out, 0, n, envOut.data());
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeDuckGainsForLevels(
    JNIEnv* env, jclass, jfloatArray levels, jfloat amountDb, jfloat smoothness, jlong stepMs) {
    if (!levels || stepMs <= 0) return env->NewFloatArray(0);
    const jsize count = env->GetArrayLength(levels);
    if (count <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> buf(static_cast<size_t>(count));
    env->GetFloatArrayRegion(levels, 0, count, buf.data());

    const std::vector<float> gains = duckGainsForLevels(
        buf.data(), static_cast<int>(count), amountDb, smoothness, stepMs);
    const jsize n = static_cast<jsize>(gains.size());
    jfloatArray out = env->NewFloatArray(n);
    if (out && n > 0) env->SetFloatArrayRegion(out, 0, n, gains.data());
    return out;
}

// Returns packed pairs: timesOut[i] in relMs, gainsOut[i] in gain; returns point count (0 = no duck).
JNIEXPORT jint JNICALL Java_com_example_engine_AudioAnalysisNative_nativeDuckBuildCurve(
    JNIEnv* env, jclass, jlong triggerStartMs, jfloatArray triggerLevels,
    jlong duckedStartMs, jlong duckedDurationMs, jfloat amountDb, jfloat smoothness,
    jlongArray timesOut, jfloatArray gainsOut) {
    if (!timesOut || !gainsOut) return 0;
    std::vector<jfloat> lv;
    if (triggerLevels) {
        const jsize lc = env->GetArrayLength(triggerLevels);
        if (lc > 0) {
            lv.resize(static_cast<size_t>(lc));
            env->GetFloatArrayRegion(triggerLevels, 0, lc, lv.data());
        }
    }
    const std::vector<DuckPoint> pts = duckBuildCurve(
        triggerStartMs, lv.empty() ? nullptr : lv.data(), static_cast<int>(lv.size()),
        duckedStartMs, duckedDurationMs, amountDb, smoothness);
    if (pts.empty()) return 0;
    const int cap = std::min(env->GetArrayLength(timesOut), env->GetArrayLength(gainsOut));
    const int n = std::min(cap, static_cast<int>(pts.size()));
    std::vector<jlong> ts(static_cast<size_t>(n));
    std::vector<jfloat> gs(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        ts[static_cast<size_t>(i)] = pts[static_cast<size_t>(i)].relMs;
        gs[static_cast<size_t>(i)] = pts[static_cast<size_t>(i)].gain;
    }
    env->SetLongArrayRegion(timesOut, 0, n, ts.data());
    env->SetFloatArrayRegion(gainsOut, 0, n, gs.data());
    return n;
}

// ---- Noise reduction profile & reference processor ---------------------------------------------

JNIEXPORT void JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseFrameMagnitudes(
    JNIEnv* env, jclass, jfloatArray frame, jfloatArray outBins) {
    if (!frame || !outBins) return;
    if (env->GetArrayLength(frame) < kNoiseFftSize || env->GetArrayLength(outBins) < kNoiseBins) {
        return;
    }
    jfloat f[kNoiseFftSize];
    jfloat bins[kNoiseBins];
    env->GetFloatArrayRegion(frame, 0, kNoiseFftSize, f);
    noiseFrameMagnitudes(f, bins);
    env->SetFloatArrayRegion(outBins, 0, kNoiseBins, bins);
}

JNIEXPORT jlong JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseAccCreate(
    JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new NoiseProfileAccumulator());
}

JNIEXPORT void JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseAccDestroy(
    JNIEnv*, jclass, jlong h) {
    delete Acc(h);
}

JNIEXPORT void JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseAccPushMono(
    JNIEnv* env, jclass, jlong h, jfloatArray samples) {
    if (!h || !samples) return;
    const jsize n = env->GetArrayLength(samples);
    if (n <= 0) return;
    std::vector<jfloat> buf(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, buf.data());
    Acc(h)->pushMonoSamples(buf.data(), static_cast<int>(n));
}

JNIEXPORT jint JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseAccFinish(
    JNIEnv* env, jclass, jlong h, jfloatArray outProfile) {
    if (!h) return 0;
    Acc(h)->finishMonoStream();
    const int fc = Acc(h)->frameCount();
    if (outProfile && env->GetArrayLength(outProfile) >= kNoiseBins) {
        jfloat p[kNoiseBins];
        Acc(h)->buildProfile(p);
        env->SetFloatArrayRegion(outProfile, 0, kNoiseBins, p);
    }
    return fc;
}

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeNoiseProcessMono(
    JNIEnv* env, jclass, jfloatArray input, jfloatArray profile, jfloat strength) {
    if (!input) return env->NewFloatArray(0);
    const jsize n = env->GetArrayLength(input);
    if (n <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> in(static_cast<size_t>(n));
    env->GetFloatArrayRegion(input, 0, n, in.data());

    jfloat prof[kNoiseBins];
    const float* profPtr = nullptr;
    if (profile && env->GetArrayLength(profile) == kNoiseBins) {
        env->GetFloatArrayRegion(profile, 0, kNoiseBins, prof);
        profPtr = prof;
    }
    const std::vector<float> res = noiseProcessMono(in.data(), static_cast<int>(n),
                                                    profPtr, strength);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(res.size()));
    if (out && !res.empty()) {
        env->SetFloatArrayRegion(out, 0, static_cast<jsize>(res.size()), res.data());
    }
    return out;
}

// Decodes [startMs, startMs + spanMs) directly via NDK AudioFileSource (48 kHz) and computes
// the 1025-bin spectral noise profile in C++ with zero Java MediaCodec / array overhead.
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeComputeNoiseProfileFd(
    JNIEnv* env, jclass, jint fd, jlong length, jlong startMs, jlong spanMs) {
    if (fd < 0 || spanMs <= 0) return nullptr;
    auto src = am::AudioFileSource::open(fd, length, std::max<int64_t>(0, startMs) * 1000LL);
    if (!src) return nullptr;

    NoiseProfileAccumulator acc;
    const int64_t targetFrames = (spanMs * kNoiseSampleRate + 999LL) / 1000LL;
    constexpr int kBlock = 4096;
    std::vector<float> stereo(static_cast<size_t>(kBlock) * 2);
    std::vector<float> mono(static_cast<size_t>(kBlock));

    int64_t readTotal = 0;
    while (readTotal < targetFrames) {
        const int want = static_cast<int>(std::min<int64_t>(kBlock, targetFrames - readTotal));
        const int got = src->read(stereo.data(), want);
        if (got <= 0) break;
        for (int i = 0; i < got; ++i) {
            mono[static_cast<size_t>(i)] =
                0.5f * (stereo[static_cast<size_t>(i) * 2] + stereo[static_cast<size_t>(i) * 2 + 1]);
        }
        acc.pushMonoSamples(mono.data(), got);
        readTotal += got;
    }
    acc.finishMonoStream();
    if (acc.frameCount() <= 0) return nullptr;

    jfloat prof[kNoiseBins];
    acc.buildProfile(prof);
    jfloatArray out = env->NewFloatArray(kNoiseBins);
    if (out) env->SetFloatArrayRegion(out, 0, kNoiseBins, prof);
    return out;
}

// ---- Waveform peak extraction directly from fd via NDK AudioFileSource -------------------------

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_AudioAnalysisNative_nativeComputeWaveformFd(
    JNIEnv* env, jclass, jint fd, jlong length, jlong durationMs, jint bucketMs) {
    if (fd < 0 || bucketMs <= 0) return nullptr;
    auto src = am::AudioFileSource::open(fd, length, 0);
    if (!src) return nullptr;

    const int peakCount = std::max(2, static_cast<int>(durationMs / bucketMs + 2));
    std::vector<float> peaks(static_cast<size_t>(peakCount), 0.0f);

    constexpr int kBlock = 4096;
    std::vector<float> stereo(static_cast<size_t>(kBlock) * 2);
    int64_t framesDone = 0;
    while (true) {
        const int got = src->read(stereo.data(), kBlock);
        if (got <= 0) break;
        for (int f = 0; f < got; ++f) {
            const float l = std::fabs(stereo[static_cast<size_t>(f) * 2]);
            const float r = std::fabs(stereo[static_cast<size_t>(f) * 2 + 1]);
            const float level = l > r ? l : r;
            const int64_t ms = (framesDone + f) * 1000LL / am::kAudioRate;
            const int64_t bucketIdx = ms / bucketMs;
            const int b = bucketIdx <= 0 ? 0
                : (bucketIdx >= peakCount ? peakCount - 1 : static_cast<int>(bucketIdx));
            if (level > peaks[static_cast<size_t>(b)]) peaks[static_cast<size_t>(b)] = level;
        }
        framesDone += got;
    }

    normalizeWaveformPeaks(peaks.data(), peakCount);
    jfloatArray out = env->NewFloatArray(peakCount);
    if (out) env->SetFloatArrayRegion(out, 0, peakCount, peaks.data());
    return out;
}

// ---- PCM16 file reverse to WAV -----------------------------------------------------------------

JNIEXPORT jboolean JNICALL Java_com_example_engine_AudioAnalysisNative_nativeReversePcm16ToWav(
    JNIEnv* env, jclass, jstring pcmPath, jstring wavPath, jint channels,
    jint sampleRate, jlong frames) {
    if (!pcmPath || !wavPath) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(pcmPath, nullptr);
    const char* w = env->GetStringUTFChars(wavPath, nullptr);
    const bool ok = (p && w) ? reversePcm16FileToWav(p, w, channels, sampleRate, frames) : false;
    if (p) env->ReleaseStringUTFChars(pcmPath, p);
    if (w) env->ReleaseStringUTFChars(wavPath, w);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
