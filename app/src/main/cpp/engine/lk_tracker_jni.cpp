// JNI bridge between com.example.engine.LkTrackerNative (Kotlin) and am::LkCircleTracker.
#include <jni.h>

#include <vector>

#include "lk_tracker.h"

using am::LkCircleTracker;

namespace {
inline LkCircleTracker* T(jlong p) { return reinterpret_cast<LkCircleTracker*>(p); }

inline void writeState(JNIEnv* env, const LkCircleTracker* t, jfloatArray stateOut) {
    if (!stateOut || env->GetArrayLength(stateOut) < 6) return;
    jfloat s[6] = {
        t->cx(),
        t->cy(),
        t->radius(),
        t->rotationDeg(),
        t->scaleTotal(),
        static_cast<float>(t->pointCount())
    };
    env->SetFloatArrayRegion(stateOut, 0, 6, s);
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_engine_LkTrackerNative_nativeCreate(JNIEnv*, jclass,
                                                                              jint maxPoints) {
    return reinterpret_cast<jlong>(new LkCircleTracker(maxPoints));
}

JNIEXPORT void JNICALL Java_com_example_engine_LkTrackerNative_nativeDestroy(JNIEnv*, jclass,
                                                                              jlong p) {
    delete T(p);
}

JNIEXPORT jboolean JNICALL Java_com_example_engine_LkTrackerNative_nativeStart(
    JNIEnv* env, jclass, jlong p, jfloatArray gray, jint w, jint h,
    jfloat cx, jfloat cy, jfloat radius, jfloatArray stateOut) {
    if (!p || !gray || w <= 0 || h <= 0) return JNI_FALSE;
    const jsize expected = w * h;
    if (env->GetArrayLength(gray) < expected) return JNI_FALSE;
    std::vector<jfloat> g(static_cast<size_t>(expected));
    env->GetFloatArrayRegion(gray, 0, expected, g.data());
    const bool ok = T(p)->start(g.data(), w, h, cx, cy, radius);
    writeState(env, T(p), stateOut);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_example_engine_LkTrackerNative_nativeStep(
    JNIEnv* env, jclass, jlong p, jfloatArray gray, jint w, jint h, jfloatArray stateOut) {
    if (!p || !gray || w <= 0 || h <= 0) return JNI_FALSE;
    const jsize expected = w * h;
    if (env->GetArrayLength(gray) < expected) return JNI_FALSE;
    std::vector<jfloat> g(static_cast<size_t>(expected));
    env->GetFloatArrayRegion(gray, 0, expected, g.data());
    const bool ok = T(p)->step(g.data(), w, h);
    writeState(env, T(p), stateOut);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_example_engine_LkTrackerNative_nativePoints(
    JNIEnv* env, jclass, jlong p, jfloatArray out) {
    if (!p || !out) return 0;
    const int cap = env->GetArrayLength(out) / 2;
    if (cap <= 0) return 0;
    const int count = T(p)->pointCount();
    const int want = count < cap ? count : cap;
    if (want <= 0) return 0;
    std::vector<jfloat> tmp(static_cast<size_t>(want) * 2);
    const int copied = T(p)->copyPoints(tmp.data(), want);
    if (copied > 0) env->SetFloatArrayRegion(out, 0, copied * 2, tmp.data());
    return copied;
}

}  // extern "C"
