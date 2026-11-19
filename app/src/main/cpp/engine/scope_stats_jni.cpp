// JNI bridge between com.example.engine.ScopeStatsNative (Kotlin) and am::scopes.
#include <jni.h>

#include <cstdint>
#include <vector>

#include "scope_stats.h"

extern "C" {

JNIEXPORT jintArray JNICALL Java_com_example_engine_ScopeStatsNative_nativeDownscale(
    JNIEnv* env, jclass, jintArray argb, jint width, jint height, jintArray outSize) {
    if (!argb || !outSize || env->GetArrayLength(outSize) < 2) return nullptr;
    const jsize len = env->GetArrayLength(argb);
    const int64_t total = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (width <= 0 || height <= 0 || total <= 0 || static_cast<int64_t>(len) < total) {
        return nullptr;
    }

    jint* src = env->GetIntArrayElements(argb, nullptr);
    if (!src) return nullptr;

    std::vector<int32_t> scaled;
    int outW = 0;
    int outH = 0;
    const bool ok = am::scopes::downscale(
        src, static_cast<int>(len), width, height, scaled, outW, outH);
    env->ReleaseIntArrayElements(argb, src, JNI_ABORT);
    if (!ok) return nullptr;

    jint sz[2] = {outW, outH};
    env->SetIntArrayRegion(outSize, 0, 2, sz);

    jintArray out = env->NewIntArray(static_cast<jsize>(scaled.size()));
    if (out && !scaled.empty()) {
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(scaled.size()), scaled.data());
    }
    return out;
}

// Writes metaOut = {w, h}, histR/G/B/Y (256), vec (96*96), and returns wave (w * 256).
JNIEXPORT jintArray JNICALL Java_com_example_engine_ScopeStatsNative_nativeAnalyze(
    JNIEnv* env, jclass, jintArray argb, jint width, jint height,
    jintArray metaOut, jintArray histR, jintArray histG, jintArray histB,
    jintArray histY, jintArray vec) {
    if (!argb || !metaOut || !histR || !histG || !histB || !histY || !vec) return nullptr;
    if (env->GetArrayLength(metaOut) < 2 ||
        env->GetArrayLength(histR) < am::scopes::kBins ||
        env->GetArrayLength(histG) < am::scopes::kBins ||
        env->GetArrayLength(histB) < am::scopes::kBins ||
        env->GetArrayLength(histY) < am::scopes::kBins ||
        env->GetArrayLength(vec) < am::scopes::kVecGrid * am::scopes::kVecGrid) {
        return nullptr;
    }
    const jsize len = env->GetArrayLength(argb);
    const int64_t total = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (width <= 0 || height <= 0 || total <= 0 || static_cast<int64_t>(len) < total) {
        return nullptr;
    }

    jint* src = env->GetIntArrayElements(argb, nullptr);
    if (!src) return nullptr;

    am::scopes::ScopeResult res;
    const bool ok = am::scopes::analyze(src, static_cast<int>(len), width, height, res);
    env->ReleaseIntArrayElements(argb, src, JNI_ABORT);
    if (!ok) return nullptr;

    jint meta[2] = {res.width, res.height};
    env->SetIntArrayRegion(metaOut, 0, 2, meta);
    env->SetIntArrayRegion(histR, 0, am::scopes::kBins, res.histR);
    env->SetIntArrayRegion(histG, 0, am::scopes::kBins, res.histG);
    env->SetIntArrayRegion(histB, 0, am::scopes::kBins, res.histB);
    env->SetIntArrayRegion(histY, 0, am::scopes::kBins, res.histY);
    env->SetIntArrayRegion(vec, 0, am::scopes::kVecGrid * am::scopes::kVecGrid, res.vec);

    jintArray waveOut = env->NewIntArray(static_cast<jsize>(res.wave.size()));
    if (waveOut && !res.wave.empty()) {
        env->SetIntArrayRegion(waveOut, 0, static_cast<jsize>(res.wave.size()), res.wave.data());
    }
    return waveOut;
}

}  // extern "C"
