#include <jni.h>

#include <vector>

#include "ui_widgets.h"

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_engine_UiMathNative_nativeRulerQuantize(
    JNIEnv*, jclass, jfloat value, jfloat minVal, jfloat maxVal, jfloat step) {
    return am::ui::quantizeRulerValue(value, minVal, maxVal, step);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_UiMathNative_nativeRulerDragDelta(
    JNIEnv*, jclass, jfloat pos, jfloat dxPx, jfloat tickPx, jfloat minVal, jfloat maxVal, jfloat step) {
    return am::ui::rulerDragDelta(pos, dxPx, tickPx, minVal, maxVal, step);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiMathNative_nativeRulerFlingEval(
    JNIEnv* env, jclass,
    jfloat startVal, jfloat velocityUnitsPerSec, jfloat elapsedSec,
    jfloat frictionMultiplier, jfloat step, jfloat minVal, jfloat maxVal,
    jfloatArray jOutValAndDone) {
    if (!jOutValAndDone || env->GetArrayLength(jOutValAndDone) < 2) return JNI_FALSE;
    bool done = true;
    const float v = am::ui::rulerFlingEval(
        startVal, velocityUnitsPerSec, elapsedSec, frictionMultiplier, step, minVal, maxVal, &done);
    jfloat out[2] = {v, done ? 1.0f : 0.0f};
    env->SetFloatArrayRegion(jOutValAndDone, 0, 2, out);
    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_UiMathNative_nativeCropLockK(
    JNIEnv*, jclass, jint presetKind, jfloat customNum, jfloat customDen, jfloat srcAspect, jfloat projAspect) {
    return am::ui::cropLockK(presetKind, customNum, customDen, srcAspect, projAspect);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_UiMathNative_nativeCropHitTest(
    JNIEnv*, jclass, jfloat px, jfloat py, jfloat l, jfloat t, jfloat r, jfloat b, jfloat tol) {
    return am::ui::cropHitTest(px, py, l, t, r, b, tol);
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativeCropDrag(
    JNIEnv* env, jclass,
    jint mode, jfloat dxNorm, jfloat dyNorm,
    jfloat l, jfloat t, jfloat r, jfloat b,
    jfloat lockK, jfloat minSize, jfloatArray jOutProps4) {
    if (!jOutProps4 || env->GetArrayLength(jOutProps4) < 4) return;
    float out[4] = {100.0f, 100.0f, 50.0f, 50.0f};
    am::ui::cropDrag(mode, dxNorm, dyNorm, l, t, r, b, lockK, minSize, out);
    env->SetFloatArrayRegion(jOutProps4, 0, 4, out);
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativeCropResizeCenter(
    JNIEnv* env, jclass,
    jfloat curW, jfloat curH, jfloat curL, jfloat curT, jfloat curR, jfloat curB,
    jfloat newW, jfloat newH, jfloat lockK, jfloat minSize, jfloatArray jOutProps4) {
    if (!jOutProps4 || env->GetArrayLength(jOutProps4) < 4) return;
    float out[4] = {100.0f, 100.0f, 50.0f, 50.0f};
    am::ui::cropResizeAroundCenter(curW, curH, curL, curT, curR, curB, newW, newH, lockK, minSize, out);
    env->SetFloatArrayRegion(jOutProps4, 0, 4, out);
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativePathFrameTransform(
    JNIEnv* env, jclass,
    jfloat cx, jfloat cy, jfloat boxW, jfloat boxH, jfloat frameW, jfloat frameH,
    jfloat scalePct, jfloat rotDeg, jfloat depthX, jfloat depthY,
    jboolean flipH, jboolean flipV, jfloat pivotX, jfloat pivotY,
    jfloat inX, jfloat inY, jboolean inverse, jfloatArray jOutXy2) {
    if (!jOutXy2 || env->GetArrayLength(jOutXy2) < 2) return;
    float out[2] = {inX, inY};
    am::ui::pathFrameTransform(
        cx, cy, boxW, boxH, frameW, frameH, scalePct, rotDeg, depthX, depthY,
        flipH != JNI_FALSE, flipV != JNI_FALSE, pivotX, pivotY,
        inX, inY, inverse != JNI_FALSE, out);
    env->SetFloatArrayRegion(jOutXy2, 0, 2, out);
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativeNodeHitTest(
    JNIEnv* env, jclass,
    jfloat px, jfloat py,
    jfloatArray jAnchors, jfloatArray jIns, jbooleanArray jHasIn,
    jfloatArray jOuts, jbooleanArray jHasOut,
    jint anchorCount, jint selectedIdx, jfloat radiusPx,
    jintArray jSegTypes, jfloatArray jSegPts, jint segCount,
    jfloatArray jOutResult4) {
    if (!jOutResult4 || env->GetArrayLength(jOutResult4) < 4) return;
    float res[4] = {0.0f, -1.0f, -1.0f, 0.0f};

    jfloat* pA = jAnchors ? env->GetFloatArrayElements(jAnchors, nullptr) : nullptr;
    jfloat* pIn = jIns ? env->GetFloatArrayElements(jIns, nullptr) : nullptr;
    jfloat* pOut = jOuts ? env->GetFloatArrayElements(jOuts, nullptr) : nullptr;
    jboolean* pHasIn = jHasIn ? env->GetBooleanArrayElements(jHasIn, nullptr) : nullptr;
    jboolean* pHasOut = jHasOut ? env->GetBooleanArrayElements(jHasOut, nullptr) : nullptr;
    jint* pSegT = jSegTypes ? env->GetIntArrayElements(jSegTypes, nullptr) : nullptr;
    jfloat* pSegP = jSegPts ? env->GetFloatArrayElements(jSegPts, nullptr) : nullptr;

    std::vector<uint8_t> hIn(static_cast<size_t>(anchorCount > 0 ? anchorCount : 0), 0);
    std::vector<uint8_t> hOut(static_cast<size_t>(anchorCount > 0 ? anchorCount : 0), 0);
    for (int i = 0; i < anchorCount; ++i) {
        if (pHasIn) hIn[static_cast<size_t>(i)] = pHasIn[i] ? 1 : 0;
        if (pHasOut) hOut[static_cast<size_t>(i)] = pHasOut[i] ? 1 : 0;
    }

    am::ui::nodeHitTest(
        px, py,
        pA, pIn, hIn.empty() ? nullptr : hIn.data(),
        pOut, hOut.empty() ? nullptr : hOut.data(),
        anchorCount, selectedIdx, radiusPx,
        reinterpret_cast<const int32_t*>(pSegT), pSegP, segCount,
        res);

    if (pA) env->ReleaseFloatArrayElements(jAnchors, pA, JNI_ABORT);
    if (pIn) env->ReleaseFloatArrayElements(jIns, pIn, JNI_ABORT);
    if (pOut) env->ReleaseFloatArrayElements(jOuts, pOut, JNI_ABORT);
    if (pHasIn) env->ReleaseBooleanArrayElements(jHasIn, pHasIn, JNI_ABORT);
    if (pHasOut) env->ReleaseBooleanArrayElements(jHasOut, pHasOut, JNI_ABORT);
    if (pSegT) env->ReleaseIntArrayElements(jSegTypes, pSegT, JNI_ABORT);
    if (pSegP) env->ReleaseFloatArrayElements(jSegPts, pSegP, JNI_ABORT);

    env->SetFloatArrayRegion(jOutResult4, 0, 4, res);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_UiMathNative_nativePlanarHitTest(
    JNIEnv* env, jclass, jfloat fx, jfloat fy, jfloatArray jQuad8, jfloat radiusNorm) {
    if (!jQuad8 || env->GetArrayLength(jQuad8) < 8) return -1;
    jfloat* q = env->GetFloatArrayElements(jQuad8, nullptr);
    const int hit = q ? am::ui::planarHitTest(fx, fy, q, radiusNorm) : -1;
    if (q) env->ReleaseFloatArrayElements(jQuad8, q, JNI_ABORT);
    return hit;
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativePlanarMove(
    JNIEnv* env, jclass, jfloatArray jQuad8, jfloat dx, jfloat dy, jfloatArray jOutQuad8) {
    if (!jQuad8 || !jOutQuad8 || env->GetArrayLength(jQuad8) < 8 || env->GetArrayLength(jOutQuad8) < 8) return;
    jfloat* q = env->GetFloatArrayElements(jQuad8, nullptr);
    float out[8] = {0};
    if (q) {
        am::ui::planarMove(q, dx, dy, out);
        env->ReleaseFloatArrayElements(jQuad8, q, JNI_ABORT);
    }
    env->SetFloatArrayRegion(jOutQuad8, 0, 8, out);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_UiMathNative_nativeFrameGeoContains(
    JNIEnv*, jclass,
    jfloat px, jfloat py,
    jfloat boxW, jfloat boxH, jfloat frameW, jfloat frameH,
    jfloat minScale, jfloat pivotRefW, jfloat pivotRefH,
    jfloat visW, jfloat visH, jfloat visOffX, jfloat visOffY,
    jfloat scalePct, jfloat rotDeg, jfloat depthX, jfloat depthY,
    jboolean flipH, jboolean flipV, jfloat pivotX, jfloat pivotY) {
    return am::ui::frameGeoContains(
        px, py, boxW, boxH, frameW, frameH, minScale,
        pivotRefW, pivotRefH, visW, visH, visOffX, visOffY,
        scalePct, rotDeg, depthX, depthY,
        flipH != JNI_FALSE, flipV != JNI_FALSE, pivotX, pivotY) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativeHsvToRgb(
    JNIEnv* env, jclass, jfloat hDeg, jfloat s, jfloat v, jfloatArray jOutRgb3) {
    if (!jOutRgb3 || env->GetArrayLength(jOutRgb3) < 3) return;
    float rgb[3] = {0};
    am::ui::hsvToRgb(hDeg, s, v, rgb);
    env->SetFloatArrayRegion(jOutRgb3, 0, 3, rgb);
}

JNIEXPORT void JNICALL
Java_com_example_engine_UiMathNative_nativeRgbToHsv(
    JNIEnv* env, jclass, jfloat r, jfloat g, jfloat b, jfloatArray jOutHsv3) {
    if (!jOutHsv3 || env->GetArrayLength(jOutHsv3) < 3) return;
    float hsv[3] = {0};
    am::ui::rgbToHsv(r, g, b, hsv);
    env->SetFloatArrayRegion(jOutHsv3, 0, 3, hsv);
}

}  // extern "C"
