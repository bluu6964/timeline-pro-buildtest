#include <jni.h>

#include "fx_defs.h"
#include "fx_plan.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_engine_FxDefsNative_nativeCount(JNIEnv*, jclass) {
    return am::fxDefCount();
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeIdAt(JNIEnv* env, jclass, jint index) {
    return env->NewStringUTF(am::fxDefAt(index).id);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_FxDefsNative_nativeTypeAt(JNIEnv*, jclass, jint index) {
    return am::fxDefAt(index).nativeType;
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeNameAt(JNIEnv* env, jclass, jint index) {
    return env->NewStringUTF(am::fxDefAt(index).name);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeCategoryAt(JNIEnv* env, jclass, jint index) {
    return env->NewStringUTF(am::fxDefAt(index).category);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeBlurbAt(JNIEnv* env, jclass, jint index) {
    return env->NewStringUTF(am::fxDefAt(index).blurb);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_FxDefsNative_nativeParamCount(JNIEnv*, jclass, jint index) {
    return am::fxDefAt(index).paramCount;
}

static const am::FxParamDefData& paramAt(jint index, jint param) {
    static const am::FxParamDefData kEmpty = {"", 0.f, 0.f, 0.f, 1.f, ""};
    const am::FxDefData& d = am::fxDefAt(index);
    if (param < 0 || param >= d.paramCount) return kEmpty;
    return d.params[param];
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeParamName(JNIEnv* env, jclass, jint index, jint param) {
    return env->NewStringUTF(paramAt(index, param).name);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativeParamMin(JNIEnv*, jclass, jint index, jint param) {
    return paramAt(index, param).min;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativeParamMax(JNIEnv*, jclass, jint index, jint param) {
    return paramAt(index, param).max;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativeParamDefault(JNIEnv*, jclass, jint index, jint param) {
    return paramAt(index, param).def;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativeParamStep(JNIEnv*, jclass, jint index, jint param) {
    return paramAt(index, param).step;
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_FxDefsNative_nativeParamUnit(JNIEnv* env, jclass, jint index, jint param) {
    return env->NewStringUTF(paramAt(index, param).unit);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_FxDefsNative_nativeExtraCount(JNIEnv*, jclass, jint index) {
    return am::fxDefAt(index).extraCount;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativeExtraAt(JNIEnv*, jclass, jint index, jint k) {
    const am::FxDefData& d = am::fxDefAt(index);
    if (k < 0 || k >= d.extraCount) return 0.f;
    return d.extras[k];
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_FxDefsNative_nativeExtTypesAt(JNIEnv* env, jclass, jint index) {
    const am::FxDefData& d = am::fxDefAt(index);
    jintArray out = env->NewIntArray(4);
    if (!out) return nullptr;
    const jint v[4] = {d.extType, d.ext2Type, d.ext3Type, d.ext4Type};
    env->SetIntArrayRegion(out, 0, 4, v);
    return out;
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_FxDefsNative_nativePackPoint(JNIEnv*, jclass, jfloat x, jfloat y) {
    return am::fxPackPoint(x, y);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_FxDefsNative_nativeKeyFromRgb(JNIEnv* env, jclass, jlong argb) {
    // The unpacking is the Kotlin original's; the maths is am::fxKeyFromRgb in
    // fx_plan.cpp (the copy the Kotlin comment always pointed at — now the only one).
    const float r = (float)((argb >> 16) & 0xFF) / 255.f;
    const float g = (float)((argb >> 8) & 0xFF) / 255.f;
    const float b = (float)(argb & 0xFF) / 255.f;
    float hue, strength, keyV, match;
    am::fxKeyFromRgb(r, g, b, &hue, &strength, &keyV, &match);
    jfloatArray out = env->NewFloatArray(4);
    if (!out) return nullptr;
    const jfloat v[4] = {hue, strength, keyV, match};
    env->SetFloatArrayRegion(out, 0, 4, v);
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_FxDefsNative_nativeKeySwatchArgb(
    JNIEnv*, jclass, jfloat hue, jfloat strength, jfloat brightness) {
    return (jlong)(uint64_t)am::fxKeySwatchArgb(hue, strength, brightness);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_FxDefsNative_nativeGrainFrame(JNIEnv*, jclass, jlong timeMs) {
    // fx_plan.cpp's fxGrainFrame — identical maths to the deleted Kotlin copy.
    return am::fxGrainFrame((int64_t)timeMs);
}

}  // extern "C"
