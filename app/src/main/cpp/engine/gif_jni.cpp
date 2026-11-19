// JNI bridge between com.example.engine.GifNative (Kotlin) and am::gif.
#include <jni.h>

#include <cstdint>
#include <vector>

#include "gif_engine.h"

extern "C" {

// Writes pixel indices into indicesOut (length >= width*height) and returns palette (0xRRGGBB).
JNIEXPORT jintArray JNICALL Java_com_example_engine_GifNative_nativeQuantize(
    JNIEnv* env, jclass, jbyteArray rgba, jint width, jint height, jint maxColors,
    jboolean bottomUp, jbyteArray indicesOut) {
    if (!rgba || !indicesOut || width < 1 || height < 1) return nullptr;
    const jsize rgbaLen = env->GetArrayLength(rgba);
    const int64_t count64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (count64 <= 0 || static_cast<int64_t>(rgbaLen) < count64 * 4 ||
        static_cast<int64_t>(env->GetArrayLength(indicesOut)) < count64) {
        return nullptr;
    }

    jbyte* src = env->GetByteArrayElements(rgba, nullptr);
    if (!src) return nullptr;

    std::vector<int32_t> palette;
    std::vector<uint8_t> indices;
    const bool ok = am::gif::quantize(
        reinterpret_cast<const uint8_t*>(src), static_cast<int>(rgbaLen),
        width, height, maxColors, bottomUp == JNI_TRUE, palette, indices);
    env->ReleaseByteArrayElements(rgba, src, JNI_ABORT);
    if (!ok) return nullptr;

    env->SetByteArrayRegion(indicesOut, 0, static_cast<jsize>(indices.size()),
                            reinterpret_cast<const jbyte*>(indices.data()));
    jintArray palOut = env->NewIntArray(static_cast<jsize>(palette.size()));
    if (palOut && !palette.empty()) {
        env->SetIntArrayRegion(palOut, 0, static_cast<jsize>(palette.size()), palette.data());
    }
    return palOut;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_engine_GifNative_nativeLzwCompress(
    JNIEnv* env, jclass, jbyteArray indices, jint minCodeSize) {
    if (!indices) return nullptr;
    const jsize count = env->GetArrayLength(indices);
    jbyte* ptr = count > 0 ? env->GetByteArrayElements(indices, nullptr) : nullptr;
    if (count > 0 && !ptr) return nullptr;

    const std::vector<uint8_t> compressed = am::gif::lzwCompress(
        reinterpret_cast<const uint8_t*>(ptr), static_cast<int>(count), minCodeSize);
    if (ptr) env->ReleaseByteArrayElements(indices, ptr, JNI_ABORT);

    jbyteArray out = env->NewByteArray(static_cast<jsize>(compressed.size()));
    if (out && !compressed.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(compressed.size()),
                                reinterpret_cast<const jbyte*>(compressed.data()));
    }
    return out;
}

JNIEXPORT jintArray JNICALL Java_com_example_engine_GifNative_nativeRgbaBottomUpToArgb(
    JNIEnv* env, jclass, jbyteArray rgba, jint width, jint height) {
    if (!rgba || width < 1 || height < 1) return nullptr;
    const jsize rgbaLen = env->GetArrayLength(rgba);
    const int64_t count64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (count64 <= 0 || static_cast<int64_t>(rgbaLen) < count64 * 4) return nullptr;

    jbyte* src = env->GetByteArrayElements(rgba, nullptr);
    if (!src) return nullptr;

    std::vector<int32_t> argb(static_cast<size_t>(count64));
    const bool ok = am::gif::rgbaBottomUpToArgb(
        reinterpret_cast<const uint8_t*>(src), static_cast<int>(rgbaLen),
        width, height, argb.data());
    env->ReleaseByteArrayElements(rgba, src, JNI_ABORT);
    if (!ok) return nullptr;

    jintArray out = env->NewIntArray(static_cast<jsize>(count64));
    if (out) {
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(count64), argb.data());
    }
    return out;
}

}  // extern "C"
