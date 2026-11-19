// JNI bridge between com.example.engine.CanvasGestureNative (Kotlin) and am::canvas::Gesture.
#include <jni.h>

#include <cstdint>
#include <vector>

#include "canvas_gesture.h"

using namespace am::canvas;

namespace {

constexpr int kItemFloatStride = 17;
constexpr int kEventStride = 5;

// Packs events into double[] with 5 doubles per event: [type, index, x, y, token].
jdoubleArray packEvents(JNIEnv* env, const std::vector<Event>& evs) {
    const jsize n = static_cast<jsize>(evs.size() * kEventStride);
    jdoubleArray out = env->NewDoubleArray(n);
    if (out && n > 0) {
        std::vector<jdouble> buf(static_cast<size_t>(n));
        for (size_t i = 0; i < evs.size(); ++i) {
            buf[i * kEventStride + 0] = static_cast<jdouble>(evs[i].type);
            buf[i * kEventStride + 1] = static_cast<jdouble>(evs[i].index);
            buf[i * kEventStride + 2] = static_cast<jdouble>(evs[i].x);
            buf[i * kEventStride + 3] = static_cast<jdouble>(evs[i].y);
            buf[i * kEventStride + 4] = static_cast<jdouble>(evs[i].token);
        }
        env->SetDoubleArrayRegion(out, 0, n, buf.data());
    }
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_engine_CanvasGestureNative_nativeCreate(
    JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new Gesture());
}

JNIEXPORT void JNICALL Java_com_example_engine_CanvasGestureNative_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Gesture*>(handle);
}

JNIEXPORT void JNICALL Java_com_example_engine_CanvasGestureNative_nativeSetConfig(
    JNIEnv*, jclass, jlong handle,
    jfloat touchSlopPx, jfloat doubleTapSlopPx,
    jlong doubleTapTimeoutMs, jlong longPressTimeoutMs) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    if (!g) return;
    g->setConfig(touchSlopPx, doubleTapSlopPx,
                 static_cast<int64_t>(doubleTapTimeoutMs),
                 static_cast<int64_t>(longPressTimeoutMs));
}

JNIEXPORT jboolean JNICALL Java_com_example_engine_CanvasGestureNative_nativeOnDown(
    JNIEnv* env, jclass, jlong handle,
    jfloat px, jfloat py, jlong uptimeMs,
    jfloat frameW, jfloat frameH,
    jint pendingCurrentIndex,
    jintArray kinds, jfloatArray geomFlat) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    if (!g || !kinds || !geomFlat) return JNI_FALSE;
    const jsize count = env->GetArrayLength(kinds);
    if (count <= 0 || env->GetArrayLength(geomFlat) < count * kItemFloatStride) {
        return JNI_FALSE;
    }
    std::vector<jint> vKinds(static_cast<size_t>(count));
    std::vector<jfloat> vGeom(static_cast<size_t>(count) * kItemFloatStride);
    env->GetIntArrayRegion(kinds, 0, count, vKinds.data());
    env->GetFloatArrayRegion(geomFlat, 0, count * kItemFloatStride, vGeom.data());

    std::vector<Item> items(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        const float* p = vGeom.data() + static_cast<size_t>(i) * kItemFloatStride;
        Item& it = items[static_cast<size_t>(i)];
        it.kind = vKinds[static_cast<size_t>(i)];
        it.boxW = p[0];
        it.boxH = p[1];
        it.visW = p[2];
        it.visH = p[3];
        it.visOffX = p[4];
        it.visOffY = p[5];
        it.effScale = p[6];
        it.rotDeg = p[7];
        it.pivotX = p[8];
        it.pivotY = p[9];
        it.flipH = p[10] != 0.0f;
        it.flipV = p[11] != 0.0f;
        it.depthX = p[12];
        it.depthY = p[13];
        it.startX = p[14];
        it.startY = p[15];
        it.depthFactor = p[16];
    }
    std::vector<Event> events;
    const bool captured = g->onDown(px, py, static_cast<int64_t>(uptimeMs),
                                    frameW, frameH, pendingCurrentIndex,
                                    items.data(), static_cast<int32_t>(count), events);
    return captured ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_CanvasGestureNative_nativeOnMove(
    JNIEnv* env, jclass, jlong handle, jfloat dx, jfloat dy) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    std::vector<Event> events;
    if (g) g->onMove(dx, dy, events);
    return packEvents(env, events);
}

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_CanvasGestureNative_nativeOnUp(
    JNIEnv* env, jclass, jlong handle, jlong uptimeMs, jboolean isRelease) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    std::vector<Event> events;
    if (g) g->onUp(static_cast<int64_t>(uptimeMs), isRelease == JNI_TRUE, events);
    return packEvents(env, events);
}

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_CanvasGestureNative_nativeOnCancel(
    JNIEnv* env, jclass, jlong handle) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    std::vector<Event> events;
    if (g) g->onCancel(events);
    return packEvents(env, events);
}

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_CanvasGestureNative_nativeOnSingleTapTimeout(
    JNIEnv* env, jclass, jlong handle, jlong token) {
    auto* g = reinterpret_cast<Gesture*>(handle);
    std::vector<Event> events;
    if (g) g->onSingleTapTimeout(static_cast<int64_t>(token), events);
    return packEvents(env, events);
}

}  // extern "C"
