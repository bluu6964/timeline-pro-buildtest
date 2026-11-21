// JNI for model_logic (the model/ pure value logic, migration #3).
// Bridge: com.example.engine.ModelLogicNative. Arrays are copied in/out via
// region calls; the identity-key space size is derived from the arrays
// themselves (max key + 1), so no separate keyCount parameter crosses JNI.

#include <jni.h>

#include <limits>
#include <memory>
#include <vector>

#include "model_logic.h"

namespace {

std::vector<int64_t> readLongs(JNIEnv* env, jlongArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::vector<int64_t> v(static_cast<size_t>(n));
    if (n > 0) env->GetLongArrayRegion(arr, 0, n, reinterpret_cast<jlong*>(v.data()));
    return v;
}

std::vector<int> readInts(JNIEnv* env, jintArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::vector<int> v(static_cast<size_t>(n));
    if (n > 0) env->GetIntArrayRegion(arr, 0, n, reinterpret_cast<jint*>(v.data()));
    return v;
}

std::unique_ptr<bool[]> readBools(JNIEnv* env, jbooleanArray arr, size_t n) {
    auto v = std::make_unique<bool[]>(n == 0 ? 1 : n);
    if (arr == nullptr || n == 0) return v;
    std::vector<jboolean> tmp(n);
    env->GetBooleanArrayRegion(arr, 0, static_cast<jsize>(n), tmp.data());
    for (size_t i = 0; i < n; i++) v[i] = tmp[i] != 0;
    return v;
}

jlongArray writeLongs(JNIEnv* env, const std::vector<int64_t>& v) {
    jlongArray arr = env->NewLongArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetLongArrayRegion(arr, 0, static_cast<jsize>(v.size()),
                                reinterpret_cast<const jlong*>(v.data()));
    }
    return arr;
}

jintArray writeInts(JNIEnv* env, const std::vector<int>& v) {
    jintArray arr = env->NewIntArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetIntArrayRegion(arr, 0, static_cast<jsize>(v.size()),
                               reinterpret_cast<const jint*>(v.data()));
    }
    return arr;
}

jfloatArray writeFloats2(JNIEnv* env, const float pair[2]) {
    jfloatArray arr = env->NewFloatArray(2);
    env->SetFloatArrayRegion(arr, 0, 2, pair);
    return arr;
}

int keyCountOf(const std::vector<int>& a, const std::vector<int>& b,
               const std::vector<int>& c, int extra) {
    int kc = extra + 1;
    for (const std::vector<int>* p : {&a, &b, &c}) {
        for (const int v : *p) if (v + 1 > kc) kc = v + 1;
    }
    return kc;
}

}  // namespace

extern "C" {

// ---- Keyframes.kt: colour packing + Easing + keyframe searches ---------------

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ModelLogicNative_nativePackColor(JNIEnv* env, jclass, jlong argb) {
    float out[2];
    am::packColor(argb, out);
    return writeFloats2(env, out);
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ModelLogicNative_nativeUnpackColor(JNIEnv*, jclass, jfloat pa, jfloat pb) {
    return am::unpackColor(pa, pb);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_ModelLogicNative_nativeEasingIsLinear(
        JNIEnv*, jclass, jfloat x1, jfloat y1, jfloat x2, jfloat y2) {
    return am::easingIsLinear(x1, y1, x2, y2) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeEasingPointAt(
        JNIEnv* env, jclass, jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat s) {
    float out[2];
    am::easingPointAt(x1, y1, x2, y2, s, out);
    return writeFloats2(env, out);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_ModelLogicNative_nativeEasingSameAs(
        JNIEnv*, jclass, jfloat x1, jfloat y1, jfloat x2, jfloat y2,
        jfloat ox1, jfloat oy1, jfloat ox2, jfloat oy2, jfloat eps) {
    return am::easingSameAs(x1, y1, x2, y2, ox1, oy1, ox2, oy2, eps) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ModelLogicNative_nativeSegmentIndexAt(
        JNIEnv* env, jclass, jlongArray times, jlong relMs) {
    const std::vector<int64_t> t = readLongs(env, times);
    return am::segmentIndexAt(t.data(), static_cast<int>(t.size()), relMs);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ModelLogicNative_nativeIndexOnTime(
        JNIEnv* env, jclass, jlongArray times, jlong relMs, jlong tolMs) {
    const std::vector<int64_t> t = readLongs(env, times);
    return am::indexOnTime(t.data(), static_cast<int>(t.size()), relMs, tolMs);
}

// ---- BeatDetect.kt -------------------------------------------------------------

JNIEXPORT jdouble JNICALL
Java_com_example_engine_ModelLogicNative_nativeEstimateBpm(
        JNIEnv* env, jclass, jlongArray beats, jlong minIntervalMs, jlong maxIntervalMs) {
    const std::vector<int64_t> b = readLongs(env, beats);
    double bpm = 0.0;
    if (!am::estimateBpm(b.data(), static_cast<int>(b.size()), minIntervalMs, maxIntervalMs, &bpm)) {
        return std::numeric_limits<double>::quiet_NaN();
    }
    return bpm;
}

JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeMergeBeats(
        JNIEnv* env, jclass, jlongArray existing, jlongArray detected, jlong tolMs) {
    const std::vector<int64_t> e = readLongs(env, existing);
    const std::vector<int64_t> d = readLongs(env, detected);
    return writeLongs(env, am::mergeBeats(e.data(), static_cast<int>(e.size()),
                                          d.data(), static_cast<int>(d.size()), tolMs));
}

JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeTimelineMsForOffsets(
        JNIEnv* env, jclass, jlong durationMs, jdouble speed, jlong spanMs, jlongArray offsets) {
    const std::vector<int64_t> o = readLongs(env, offsets);
    return writeLongs(env, am::timelineMsForOffsets(durationMs, speed, spanMs,
                                                    o.data(), static_cast<int>(o.size())));
}

// ---- ExportRange.kt ---------------------------------------------------------------

JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeExportRangeOf(
        JNIEnv* env, jclass, jlong inMs, jboolean inSet, jlong outMs, jboolean outSet,
        jlong totalMs, jlong minLenMs) {
    int64_t pair[2];
    if (!am::exportRangeOf(inSet != 0, inMs, outSet != 0, outMs, totalMs, minLenMs, pair)) {
        return nullptr;
    }
    return writeLongs(env, std::vector<int64_t>{pair[0], pair[1]});
}

// ---- MultiSelectOps.kt -----------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_example_engine_ModelLogicNative_nativeBarMode(
        JNIEnv* env, jclass, jlongArray starts, jlongArray ends, jlong playheadMs) {
    const std::vector<int64_t> s = readLongs(env, starts);
    const std::vector<int64_t> e = readLongs(env, ends);
    return am::barMode(s.data(), e.data(), static_cast<int>(s.size()), playheadMs);
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeDeleteSet(
        JNIEnv* env, jclass, jintArray clipKeys, jintArray chainKeys, jintArray groupKeys,
        jintArray typeCodes, jbooleanArray locked, jbooleanArray checked, jintArray sparedOut) {
    const std::vector<int> ck = readInts(env, clipKeys);
    const std::vector<int> hk = readInts(env, chainKeys);
    const std::vector<int> gk = readInts(env, groupKeys);
    const std::vector<int> tc = readInts(env, typeCodes);
    const size_t n = ck.size();
    const std::unique_ptr<bool[]> lk = readBools(env, locked, n);
    const std::unique_ptr<bool[]> ch = readBools(env, checked, n);
    const int kc = keyCountOf(ck, hk, gk, -1);
    int spared = 0;
    const std::vector<int> gone = am::deleteSet(ck.data(), hk.data(), gk.data(), tc.data(),
                                                lk.get(), ch.get(), static_cast<int>(n), kc,
                                                &spared);
    if (sparedOut != nullptr && env->GetArrayLength(sparedOut) >= 1) {
        const jint v = spared;
        env->SetIntArrayRegion(sparedOut, 0, 1, &v);
    }
    return writeInts(env, gone);
}

// ---- GroupOps.kt ---------------------------------------------------------------------------

JNIEXPORT jintArray JNICALL
Java_com_example_engine_ModelLogicNative_nativeDescendants(
        JNIEnv* env, jclass, jintArray clipKeys, jintArray groupKeys,
        jbooleanArray isGroup, jint rootKey) {
    const std::vector<int> ck = readInts(env, clipKeys);
    const std::vector<int> gk = readInts(env, groupKeys);
    const size_t n = ck.size();
    const std::unique_ptr<bool[]> ig = readBools(env, isGroup, n);
    const int kc = keyCountOf(ck, gk, gk, rootKey);
    return writeInts(env, am::descendantsOf(ck.data(), gk.data(), ig.get(),
                                            static_cast<int>(n), kc, rootKey));
}

// ---- LaneLayout.kt ----------------------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_example_engine_ModelLogicNative_nativeIsNormalized(
        JNIEnv* env, jclass, jintArray laneOffsets, jlongArray startMs,
        jbooleanArray isChain, jbooleanArray chainIdNull, jintArray chainIdTarget) {
    const std::vector<int> offs = readInts(env, laneOffsets);
    const std::vector<int64_t> starts = readLongs(env, startMs);
    const std::vector<int> targets = readInts(env, chainIdTarget);
    const size_t total = starts.size();
    const std::unique_ptr<bool[]> chain = readBools(env, isChain, total);
    const std::unique_ptr<bool[]> idNull = readBools(env, chainIdNull, total);
    const int laneCount = offs.empty() ? 0 : static_cast<int>(offs.size()) - 1;
    return am::isNormalized(offs.data(), laneCount, starts.data(), chain.get(), idNull.get(),
                            targets.data()) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
