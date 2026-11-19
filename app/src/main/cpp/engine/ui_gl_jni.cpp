#include <android/native_window_jni.h>
#include <jni.h>

#include <cstdint>

#include "ui_gl_renderer.h"

namespace {

inline am::UiGlRenderer* asUiGl(jlong handle) {
    return reinterpret_cast<am::UiGlRenderer*>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_engine_UiGlNative_nativeCreate(JNIEnv*, jclass) {
    auto* r = new am::UiGlRenderer();
    if (!r->init()) {
        delete r;
        return 0L;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(r));
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiGlNative_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete asUiGl(handle);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_UiGlNative_nativeAttachSurface(
    JNIEnv* env, jclass, jlong handle, jobject surface, jint w, jint h) {
    auto* r = asUiGl(handle);
    if (!r || !surface) return 0;
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (!win) return 0;
    return r->attachSurface(win, static_cast<int>(w), static_cast<int>(h));
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiGlNative_nativeResizeSurface(
    JNIEnv*, jclass, jlong handle, jint surfaceId, jint w, jint h) {
    auto* r = asUiGl(handle);
    if (!r) return;
    r->resizeSurface(static_cast<int>(surfaceId), static_cast<int>(w), static_cast<int>(h));
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiGlNative_nativeDetachSurface(
    JNIEnv*, jclass, jlong handle, jint surfaceId) {
    auto* r = asUiGl(handle);
    if (!r) return;
    r->detachSurface(static_cast<int>(surfaceId));
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeDrawScopeHistogram(
    JNIEnv* env, jclass, jlong handle, jint surfaceId,
    jintArray jHistR, jintArray jHistG, jintArray jHistB, jintArray jHistY) {
    auto* r = asUiGl(handle);
    if (!r || !jHistR || !jHistG || !jHistB || !jHistY) return JNI_FALSE;
    const jsize nR = env->GetArrayLength(jHistR);
    const jsize nG = env->GetArrayLength(jHistG);
    const jsize nB = env->GetArrayLength(jHistB);
    const jsize nY = env->GetArrayLength(jHistY);
    const jsize bins = (nR < nG ? nR : nG);
    const jsize bins2 = (nB < nY ? nB : nY);
    const int n = static_cast<int>(bins < bins2 ? bins : bins2);
    if (n <= 0) return JNI_FALSE;

    jint* pR = env->GetIntArrayElements(jHistR, nullptr);
    jint* pG = env->GetIntArrayElements(jHistG, nullptr);
    jint* pB = env->GetIntArrayElements(jHistB, nullptr);
    jint* pY = env->GetIntArrayElements(jHistY, nullptr);
    bool ok = false;
    if (pR && pG && pB && pY) {
        ok = r->drawScopeHistogram(
            static_cast<int>(surfaceId),
            reinterpret_cast<const int32_t*>(pR),
            reinterpret_cast<const int32_t*>(pG),
            reinterpret_cast<const int32_t*>(pB),
            reinterpret_cast<const int32_t*>(pY),
            n);
    }
    if (pR) env->ReleaseIntArrayElements(jHistR, pR, JNI_ABORT);
    if (pG) env->ReleaseIntArrayElements(jHistG, pG, JNI_ABORT);
    if (pB) env->ReleaseIntArrayElements(jHistB, pB, JNI_ABORT);
    if (pY) env->ReleaseIntArrayElements(jHistY, pY, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeDrawScopeWaveform(
    JNIEnv* env, jclass, jlong handle, jint surfaceId,
    jintArray jWave, jint waveW, jint waveH, jint bins) {
    auto* r = asUiGl(handle);
    if (!r || !jWave || waveW <= 0 || waveH <= 0 || bins <= 0) return JNI_FALSE;
    const jsize len = env->GetArrayLength(jWave);
    if (static_cast<int64_t>(len) < static_cast<int64_t>(waveW) * static_cast<int64_t>(bins)) {
        return JNI_FALSE;
    }
    jint* pWave = env->GetIntArrayElements(jWave, nullptr);
    bool ok = false;
    if (pWave) {
        ok = r->drawScopeWaveform(
            static_cast<int>(surfaceId),
            reinterpret_cast<const int32_t*>(pWave),
            static_cast<int>(waveW),
            static_cast<int>(waveH),
            static_cast<int>(bins));
        env->ReleaseIntArrayElements(jWave, pWave, JNI_ABORT);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeDrawScopeVectorscope(
    JNIEnv* env, jclass, jlong handle, jint surfaceId,
    jintArray jVec, jint grid) {
    auto* r = asUiGl(handle);
    if (!r || !jVec || grid <= 0) return JNI_FALSE;
    const jsize len = env->GetArrayLength(jVec);
    if (static_cast<int64_t>(len) < static_cast<int64_t>(grid) * static_cast<int64_t>(grid)) {
        return JNI_FALSE;
    }
    jint* pVec = env->GetIntArrayElements(jVec, nullptr);
    bool ok = false;
    if (pVec) {
        ok = r->drawScopeVectorscope(
            static_cast<int>(surfaceId),
            reinterpret_cast<const int32_t*>(pVec),
            static_cast<int>(grid));
        env->ReleaseIntArrayElements(jVec, pVec, JNI_ABORT);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeDrawEasingGraph(
    JNIEnv*, jclass, jlong handle, jint surfaceId,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2,
    jfloat progress, jfloat density,
    jfloat padL, jfloat padT, jfloat padR, jfloat padB) {
    auto* r = asUiGl(handle);
    if (!r) return JNI_FALSE;
    return r->drawEasingGraph(
        static_cast<int>(surfaceId),
        x1, y1, x2, y2,
        progress, density,
        padL, padT, padR, padB) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeDrawVectorCmds(
    JNIEnv* env, jclass, jlong handle, jint surfaceId,
    jint clearArgb,
    jfloatArray jCmds, jint cmdFloatCount,
    jintArray jColors, jint colorCount) {
    auto* r = asUiGl(handle);
    if (!r) return JNI_FALSE;
    const jsize maxF = jCmds ? env->GetArrayLength(jCmds) : 0;
    const jsize maxC = jColors ? env->GetArrayLength(jColors) : 0;
    const int nF = (cmdFloatCount > 0 && cmdFloatCount <= maxF) ? static_cast<int>(cmdFloatCount) : 0;
    const int nC = (colorCount > 0 && colorCount <= maxC) ? static_cast<int>(colorCount) : 0;

    jfloat* pF = (nF > 0 && jCmds) ? env->GetFloatArrayElements(jCmds, nullptr) : nullptr;
    jint* pC = (nC > 0 && jColors) ? env->GetIntArrayElements(jColors, nullptr) : nullptr;
    const bool ok = r->drawVectorCommands(
        static_cast<int>(surfaceId),
        static_cast<int32_t>(clearArgb),
        pF, nF,
        reinterpret_cast<const int32_t*>(pC), nC);
    if (pF) env->ReleaseFloatArrayElements(jCmds, pF, JNI_ABORT);
    if (pC) env->ReleaseIntArrayElements(jColors, pC, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiGlNative_nativeSelfTestOffscreen(
    JNIEnv* env, jclass, jlong handle, jint w, jint h, jintArray jOutCounts) {
    auto* r = asUiGl(handle);
    if (!r || !jOutCounts || env->GetArrayLength(jOutCounts) < 4) return JNI_FALSE;
    int32_t counts[4] = {0, 0, 0, 0};
    const bool ok = r->runOffscreenSelfTest(static_cast<int>(w), static_cast<int>(h), counts);
    jint out[4] = {counts[0], counts[1], counts[2], counts[3]};
    env->SetIntArrayRegion(jOutCounts, 0, 4, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
