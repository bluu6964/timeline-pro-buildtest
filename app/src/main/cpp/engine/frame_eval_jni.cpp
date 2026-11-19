#include "frame_eval.h"

#include <jni.h>
#include <algorithm>
#include <vector>

namespace {

jfloatArray makeFloatArray(JNIEnv* env, const std::vector<float>& vec) {
    jsize n = static_cast<jsize>(vec.size());
    jfloatArray out = env->NewFloatArray(n);
    if (out != nullptr && n > 0) {
        env->SetFloatArrayRegion(out, 0, n, vec.data());
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FrameEvalNative_nativeEasingApply(
    JNIEnv*, jclass,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat t) {
    return am::easingApply(x1, y1, x2, y2, t);
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeKeyframeValueAt(
    JNIEnv* env, jclass,
    jlongArray jTimes, jfloatArray jVals, jlong relMs, jboolean isColor, jfloatArray jOut2) {
    if (jTimes == nullptr || jVals == nullptr || jOut2 == nullptr) return;
    jsize n = env->GetArrayLength(jTimes);
    jsize vLen = env->GetArrayLength(jVals);
    jsize oLen = env->GetArrayLength(jOut2);
    if (n <= 0 || vLen < n * 6 || oLen < 2) return;

    jlong* times = env->GetLongArrayElements(jTimes, nullptr);
    jfloat* vals = env->GetFloatArrayElements(jVals, nullptr);
    if (times != nullptr && vals != nullptr) {
        float out[2] = {0.0f, 0.0f};
        am::keyframeValueAt(
            reinterpret_cast<const int64_t*>(times),
            vals,
            static_cast<int>(n),
            static_cast<int64_t>(relMs),
            isColor != JNI_FALSE,
            out[0], out[1]);
        env->SetFloatArrayRegion(jOut2, 0, 2, out);
    }
    if (times != nullptr) env->ReleaseLongArrayElements(jTimes, times, JNI_ABORT);
    if (vals != nullptr) env->ReleaseFloatArrayElements(jVals, vals, JNI_ABORT);
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_FrameEvalNative_nativeSpeedSourceOffsetMs(
    JNIEnv* env, jclass,
    jlongArray jTimes, jfloatArray jVals, jlong relMs) {
    if (jTimes == nullptr || jVals == nullptr || relMs <= 0) return 0;
    jsize n = env->GetArrayLength(jTimes);
    jsize vLen = env->GetArrayLength(jVals);
    if (n <= 0 || vLen < n * 6) return 0;

    jlong* times = env->GetLongArrayElements(jTimes, nullptr);
    jfloat* vals = env->GetFloatArrayElements(jVals, nullptr);
    int64_t res = 0;
    if (times != nullptr && vals != nullptr) {
        res = am::speedSourceOffsetMs(
            reinterpret_cast<const int64_t*>(times),
            vals,
            static_cast<int>(n),
            static_cast<int64_t>(relMs));
    }
    if (times != nullptr) env->ReleaseLongArrayElements(jTimes, times, JNI_ABORT);
    if (vals != nullptr) env->ReleaseFloatArrayElements(jVals, vals, JNI_ABORT);
    return static_cast<jlong>(res);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FrameEvalNative_nativeSpeedRampTable(
    JNIEnv* env, jclass,
    jlongArray jTimes, jfloatArray jVals, jlong durationMs) {
    if (jTimes == nullptr || jVals == nullptr) return nullptr;
    jsize n = env->GetArrayLength(jTimes);
    jsize vLen = env->GetArrayLength(jVals);
    if (n <= 0 || vLen < n * 6) return nullptr;

    jlong* times = env->GetLongArrayElements(jTimes, nullptr);
    jfloat* vals = env->GetFloatArrayElements(jVals, nullptr);
    std::vector<float> table;
    if (times != nullptr && vals != nullptr) {
        table = am::speedRampTable(
            reinterpret_cast<const int64_t*>(times),
            vals,
            static_cast<int>(n),
            static_cast<int64_t>(durationMs));
    }
    if (times != nullptr) env->ReleaseLongArrayElements(jTimes, times, JNI_ABORT);
    if (vals != nullptr) env->ReleaseFloatArrayElements(jVals, vals, JNI_ABORT);
    return makeFloatArray(env, table);
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_FrameEvalNative_nativeAmFxTrackValueAt(
    JNIEnv* env, jclass,
    jfloatArray jValues, jfloat rel, jint start, jfloatArray jOut1) {
    if (jValues == nullptr || jOut1 == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(jValues);
    if (len <= 0 || env->GetArrayLength(jOut1) < 1) return JNI_FALSE;
    jfloat* vals = env->GetFloatArrayElements(jValues, nullptr);
    bool ok = false;
    float outVal = 0.0f;
    if (vals != nullptr) {
        ok = am::amFxTrackValueAt(vals, static_cast<int>(len), rel, static_cast<int>(start), outVal);
        env->ReleaseFloatArrayElements(jValues, vals, JNI_ABORT);
    }
    if (ok) {
        env->SetFloatArrayRegion(jOut1, 0, 1, &outVal);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_FrameEvalNative_nativeMotionBlurPos(
    JNIEnv* env, jclass,
    jfloat p0x, jfloat p0y, jfloat p1x, jfloat p1y, jfloat p1Rot,
    jfloat canvasHeightPx, jfloat tune, jfloatArray jOut2) {
    if (jOut2 == nullptr || env->GetArrayLength(jOut2) < 2) return JNI_FALSE;
    float out[2] = {0.0f, 0.0f};
    bool ok = am::motionBlurPos(p0x, p0y, p1x, p1y, p1Rot, canvasHeightPx, tune, out[0], out[1]);
    if (ok) env->SetFloatArrayRegion(jOut2, 0, 2, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_FrameEvalNative_nativeMotionBlurRadial(
    JNIEnv* env, jclass,
    jfloat p0Rot, jfloat p0Scale, jfloat p1Rot, jfloat p1Scale,
    jfloat tune, jboolean useScale, jboolean useAngle, jfloatArray jOut2) {
    if (jOut2 == nullptr || env->GetArrayLength(jOut2) < 2) return JNI_FALSE;
    float out[2] = {0.0f, 0.0f};
    bool ok = am::motionBlurRadial(
        p0Rot, p0Scale, p1Rot, p1Scale, tune,
        useScale != JNI_FALSE, useAngle != JNI_FALSE,
        out[0], out[1]);
    if (ok) env->SetFloatArrayRegion(jOut2, 0, 2, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_FrameEvalNative_nativeMotionBlur4(
    JNIEnv* env, jclass,
    jfloat p0x, jfloat p0y, jfloat p0Rot, jfloat p0Scale,
    jfloat p1x, jfloat p1y, jfloat p1Rot, jfloat p1Scale,
    jfloat canvasHeightPx,
    jboolean usePos, jboolean useScale, jboolean useAngle,
    jfloatArray jOut4) {
    if (jOut4 == nullptr || env->GetArrayLength(jOut4) < 4) return 0;
    am::Mb4Resolved r = am::motionBlur4(
        p0x, p0y, p0Rot, p0Scale,
        p1x, p1y, p1Rot, p1Scale,
        canvasHeightPx,
        usePos != JNI_FALSE, useScale != JNI_FALSE, useAngle != JNI_FALSE);
    float vals[4] = {r.posPx, r.posPy, r.scaleVel, r.angleDeg};
    env->SetFloatArrayRegion(jOut4, 0, 4, vals);
    jint mask = 0;
    if (r.hasPos) mask |= 1;
    if (r.hasScale) mask |= 2;
    if (r.hasAngle) mask |= 4;
    return mask;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FrameEvalNative_nativeRepeatEval(
    JNIEnv* env, jclass,
    jint mode, jfloatArray jFxVals,
    jfloat clipX, jfloat clipY, jfloat clipRot, jfloat clipScale, jfloat clipOp,
    jfloat undoRotDeg, jfloat outerScale, jfloat outerRotDeg,
    jlong clipStartMs, jlong clipEndMs, jlong timeMs,
    jlongArray jOutAlt2) {
    jfloat* fxVals = (jFxVals != nullptr) ? env->GetFloatArrayElements(jFxVals, nullptr) : nullptr;
    jsize fxLen = (jFxVals != nullptr) ? env->GetArrayLength(jFxVals) : 0;

    am::RepeatEvalOutput res = am::repeatEval(
        static_cast<int>(mode), fxVals, static_cast<int>(fxLen),
        clipX, clipY, clipRot, clipScale, clipOp,
        undoRotDeg, outerScale, outerRotDeg,
        static_cast<int64_t>(clipStartMs),
        static_cast<int64_t>(clipEndMs),
        static_cast<int64_t>(timeMs));

    if (fxVals != nullptr) env->ReleaseFloatArrayElements(jFxVals, fxVals, JNI_ABORT);

    if (jOutAlt2 != nullptr && env->GetArrayLength(jOutAlt2) >= 2) {
        jlong alt[2] = {
            res.hasAltColor ? 1L : 0L,
            static_cast<jlong>(res.altColorArgb)
        };
        env->SetLongArrayRegion(jOutAlt2, 0, 2, alt);
    }

    std::vector<float> flat;
    flat.reserve(res.copies.size() * 5);
    for (const auto& cp : res.copies) {
        flat.push_back(cp.x);
        flat.push_back(cp.y);
        flat.push_back(cp.rotDeg);
        flat.push_back(cp.scalePercent);
        flat.push_back(cp.opacity);
    }
    return makeFloatArray(env, flat);
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeGroupCopyOffset(
    JNIEnv* env, jclass,
    jfloat anchorX, jfloat anchorY,
    jfloat dx, jfloat dy, jfloat rotDeg, jfloat scaleMul, jfloat k,
    jfloatArray jOut2) {
    if (jOut2 == nullptr || env->GetArrayLength(jOut2) < 2) return;
    float out[2] = {0.0f, 0.0f};
    am::groupCopyOffset(anchorX, anchorY, dx, dy, rotDeg, scaleMul, k, out[0], out[1]);
    env->SetFloatArrayRegion(jOut2, 0, 2, out);
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeTextAnimValueAt(
    JNIEnv* env, jclass,
    jfloatArray jKeyTimes, jfloatArray jKeyVals, jint dims,
    jbyteArray jKeyHasEasing, jfloatArray jKeyEasings,
    jboolean stepOnly, jfloat p, jfloatArray jOutVals) {
    if (jKeyTimes == nullptr || jKeyVals == nullptr || jOutVals == nullptr || dims <= 0) return;
    jsize n = env->GetArrayLength(jKeyTimes);
    if (n <= 0 || env->GetArrayLength(jKeyVals) < n * dims || env->GetArrayLength(jOutVals) < dims) return;

    jfloat* times = env->GetFloatArrayElements(jKeyTimes, nullptr);
    jfloat* vals = env->GetFloatArrayElements(jKeyVals, nullptr);
    jbyte* hasEz = (jKeyHasEasing != nullptr && env->GetArrayLength(jKeyHasEasing) >= n)
        ? env->GetByteArrayElements(jKeyHasEasing, nullptr) : nullptr;
    jfloat* ez = (jKeyEasings != nullptr && env->GetArrayLength(jKeyEasings) >= n * 4)
        ? env->GetFloatArrayElements(jKeyEasings, nullptr) : nullptr;

    if (times != nullptr && vals != nullptr) {
        std::vector<float> out(static_cast<size_t>(dims), 0.0f);
        am::textAnimValueAt(
            times, vals, static_cast<int>(n), static_cast<int>(dims),
            reinterpret_cast<const uint8_t*>(hasEz), ez,
            stepOnly != JNI_FALSE, p, out.data());
        env->SetFloatArrayRegion(jOutVals, 0, dims, out.data());
    }
    if (times != nullptr) env->ReleaseFloatArrayElements(jKeyTimes, times, JNI_ABORT);
    if (vals != nullptr) env->ReleaseFloatArrayElements(jKeyVals, vals, JNI_ABORT);
    if (hasEz != nullptr) env->ReleaseByteArrayElements(jKeyHasEasing, hasEz, JNI_ABORT);
    if (ez != nullptr) env->ReleaseFloatArrayElements(jKeyEasings, ez, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeTextProgressVisibleRange(
    JNIEnv* env, jclass,
    jint count, jfloat start, jfloat end, jintArray jOut2) {
    if (jOut2 == nullptr || env->GetArrayLength(jOut2) < 2) return;
    int first = -1;
    int last = -1;
    am::textProgressVisibleRange(static_cast<int>(count), start, end, first, last);
    jint out[2] = {static_cast<jint>(first), static_cast<jint>(last)};
    env->SetIntArrayRegion(jOut2, 0, 2, out);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FrameEvalNative_nativeCounterSubstitute(
    JNIEnv* env, jclass,
    jstring jText, jdouble offset, jdouble scale) {
    if (jText == nullptr) return nullptr;
    jsize len = env->GetStringLength(jText);
    if (len <= 0) return jText;
    const jchar* chars = env->GetStringChars(jText, nullptr);
    if (chars == nullptr) return jText;
    std::u16string res = am::counterSubstituteUtf16(
        reinterpret_cast<const uint16_t*>(chars), static_cast<int>(len), offset, scale);
    env->ReleaseStringChars(jText, chars);
    return env->NewString(reinterpret_cast<const jchar*>(res.data()), static_cast<jsize>(res.size()));
}

JNIEXPORT jdouble JNICALL
Java_com_example_engine_FrameEvalNative_nativePieArcSweepDegrees(
    JNIEnv*, jclass, jdouble startDeg, jdouble endDeg) {
    return am::pieArcSweepDegrees(startDeg, endDeg);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FrameEvalNative_nativePieSegments(
    JNIEnv* env, jclass, jdouble startDeg, jdouble endDeg, jdouble radius) {
    return makeFloatArray(env, am::pieOutlineSegments(startDeg, endDeg, radius));
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FrameEvalNative_nativeArcSegments(
    JNIEnv* env, jclass, jdouble startDeg, jdouble endDeg, jdouble radius, jboolean closed) {
    return makeFloatArray(env, am::arcOutlineSegments(startDeg, endDeg, radius, closed != JNI_FALSE));
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FrameEvalNative_nativeCalloutSegments(
    JNIEnv* env, jclass,
    jdouble width, jdouble height, jdouble tailX, jdouble tailY,
    jdouble cornerRadius, jdouble tailWidth) {
    return makeFloatArray(env, am::calloutOutlineSegments(width, height, tailX, tailY, cornerRadius, tailWidth));
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeGradEndpoints(
    JNIEnv* env, jclass,
    jint gradType, jfloat cx, jfloat cy, jfloat angleDeg, jfloat scale,
    jfloatArray jOut4) {
    if (jOut4 == nullptr || env->GetArrayLength(jOut4) < 4) return;
    float out[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    am::gradEndpoints(static_cast<int>(gradType), cx, cy, angleDeg, scale, out);
    env->SetFloatArrayRegion(jOut4, 0, 4, out);
}

JNIEXPORT void JNICALL
Java_com_example_engine_FrameEvalNative_nativeGradAngleScale(
    JNIEnv* env, jclass,
    jint gradType, jfloat x1, jfloat y1, jfloat x2, jfloat y2,
    jfloatArray jOut2) {
    if (jOut2 == nullptr || env->GetArrayLength(jOut2) < 2) return;
    float out[2] = {0.0f, 0.0f};
    am::gradAngleScale(static_cast<int>(gradType), x1, y1, x2, y2, out);
    env->SetFloatArrayRegion(jOut2, 0, 2, out);
}

} // extern "C"
