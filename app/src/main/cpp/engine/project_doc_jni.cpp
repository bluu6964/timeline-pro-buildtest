#include "project_doc.h"

#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>

namespace {

std::string jstringToStdString(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* utf = env->GetStringUTFChars(s, nullptr);
    if (utf == nullptr) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(s, utf);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_engine_ProjectDocNative_nativeWriteAtomic(
    JNIEnv* env, jclass, jstring jTargetPath, jbyteArray jBytes) {
    if (jTargetPath == nullptr || jBytes == nullptr) return JNI_FALSE;
    std::string path = jstringToStdString(env, jTargetPath);
    jsize len = env->GetArrayLength(jBytes);
    jbyte* ptr = env->GetByteArrayElements(jBytes, nullptr);
    bool ok = false;
    if (ptr != nullptr || len == 0) {
        ok = am::atomicWriteFile(
            path,
            reinterpret_cast<const uint8_t*>(ptr),
            static_cast<size_t>(len > 0 ? len : 0));
    }
    if (ptr != nullptr) env->ReleaseByteArrayElements(jBytes, ptr, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_ProjectDocNative_nativeReadFile(
    JNIEnv* env, jclass, jstring jPath) {
    if (jPath == nullptr) return nullptr;
    std::string path = jstringToStdString(env, jPath);
    std::vector<uint8_t> data;
    if (!am::readWholeFile(path, data)) return nullptr;
    jsize n = static_cast<jsize>(data.size());
    jbyteArray out = env->NewByteArray(n);
    if (out != nullptr && n > 0) {
        env->SetByteArrayRegion(out, 0, n, reinterpret_cast<const jbyte*>(data.data()));
    }
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryCreate(
    JNIEnv*, jclass, jint capacity) {
    auto* buf = new am::NativeHistoryBuffer(static_cast<int>(capacity));
    return reinterpret_cast<jlong>(buf);
}

JNIEXPORT void JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryDestroy(
    JNIEnv*, jclass, jlong handle) {
    if (handle == 0) return;
    delete reinterpret_cast<am::NativeHistoryBuffer*>(handle);
}

JNIEXPORT void JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryPush(
    JNIEnv* env, jclass, jlong handle, jbyteArray jBlob) {
    if (handle == 0 || jBlob == nullptr) return;
    auto* buf = reinterpret_cast<am::NativeHistoryBuffer*>(handle);
    jsize len = env->GetArrayLength(jBlob);
    jbyte* ptr = env->GetByteArrayElements(jBlob, nullptr);
    if (ptr != nullptr || len == 0) {
        buf->push(reinterpret_cast<const uint8_t*>(ptr), static_cast<size_t>(len > 0 ? len : 0));
    }
    if (ptr != nullptr) env->ReleaseByteArrayElements(jBlob, ptr, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryPop(
    JNIEnv* env, jclass, jlong handle) {
    if (handle == 0) return nullptr;
    auto* buf = reinterpret_cast<am::NativeHistoryBuffer*>(handle);
    std::vector<uint8_t> blob;
    if (!buf->pop(blob)) return nullptr;
    jsize n = static_cast<jsize>(blob.size());
    jbyteArray out = env->NewByteArray(n);
    if (out != nullptr && n > 0) {
        env->SetByteArrayRegion(out, 0, n, reinterpret_cast<const jbyte*>(blob.data()));
    }
    return out;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistorySize(
    JNIEnv*, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jint>(reinterpret_cast<am::NativeHistoryBuffer*>(handle)->size());
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryTotalBytes(
    JNIEnv*, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jlong>(reinterpret_cast<am::NativeHistoryBuffer*>(handle)->totalBytes());
}

JNIEXPORT void JNICALL
Java_com_example_engine_ProjectDocNative_nativeHistoryClear(
    JNIEnv*, jclass, jlong handle) {
    if (handle == 0) return;
    reinterpret_cast<am::NativeHistoryBuffer*>(handle)->clear();
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ProjectDocNative_nativeSolveChainStart(
    JNIEnv* env, jclass,
    jlongArray jSiblingStarts, jlongArray jSiblingEnds,
    jlong desiredStartMs, jlong durationMs) {
    if (jSiblingStarts == nullptr || jSiblingEnds == nullptr) {
        return desiredStartMs < 0 ? 0 : desiredStartMs;
    }
    jsize n = env->GetArrayLength(jSiblingStarts);
    jsize m = env->GetArrayLength(jSiblingEnds);
    int count = static_cast<int>(n < m ? n : m);
    if (count <= 0) return desiredStartMs < 0 ? 0 : desiredStartMs;

    jlong* starts = env->GetLongArrayElements(jSiblingStarts, nullptr);
    jlong* ends = env->GetLongArrayElements(jSiblingEnds, nullptr);
    int64_t res = desiredStartMs < 0 ? 0 : desiredStartMs;
    if (starts != nullptr && ends != nullptr) {
        res = am::solveChainPlacement(
            reinterpret_cast<const int64_t*>(starts),
            reinterpret_cast<const int64_t*>(ends),
            count,
            static_cast<int64_t>(desiredStartMs),
            static_cast<int64_t>(durationMs));
    }
    if (starts != nullptr) env->ReleaseLongArrayElements(jSiblingStarts, starts, JNI_ABORT);
    if (ends != nullptr) env->ReleaseLongArrayElements(jSiblingEnds, ends, JNI_ABORT);
    return static_cast<jlong>(res);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_ProjectDocNative_nativeXmlSanitize(
    JNIEnv* env, jclass, jstring jStr) {
    if (jStr == nullptr) return nullptr;
    jsize len = env->GetStringLength(jStr);
    if (len <= 0) return jStr;
    const jchar* chars = env->GetStringChars(jStr, nullptr);
    if (chars == nullptr) return jStr;
    std::u16string res = am::xmlSanitizeUtf16(reinterpret_cast<const uint16_t*>(chars), static_cast<int>(len));
    env->ReleaseStringChars(jStr, chars);
    return env->NewString(reinterpret_cast<const jchar*>(res.data()), static_cast<jsize>(res.size()));
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_ProjectDocNative_nativeXmlEscAttr(
    JNIEnv* env, jclass, jstring jStr) {
    if (jStr == nullptr) return nullptr;
    jsize len = env->GetStringLength(jStr);
    if (len <= 0) return jStr;
    const jchar* chars = env->GetStringChars(jStr, nullptr);
    if (chars == nullptr) return jStr;
    std::u16string res = am::xmlEscapeAttrUtf16(reinterpret_cast<const uint16_t*>(chars), static_cast<int>(len));
    env->ReleaseStringChars(jStr, chars);
    return env->NewString(reinterpret_cast<const jchar*>(res.data()), static_cast<jsize>(res.size()));
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_ProjectDocNative_nativeXmlEscText(
    JNIEnv* env, jclass, jstring jStr) {
    if (jStr == nullptr) return nullptr;
    jsize len = env->GetStringLength(jStr);
    if (len <= 0) return jStr;
    const jchar* chars = env->GetStringChars(jStr, nullptr);
    if (chars == nullptr) return jStr;
    std::u16string res = am::xmlEscapeTextUtf16(reinterpret_cast<const uint16_t*>(chars), static_cast<int>(len));
    env->ReleaseStringChars(jStr, chars);
    return env->NewString(reinterpret_cast<const jchar*>(res.data()), static_cast<jsize>(res.size()));
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_ProjectDocNative_nativeFormatCorpusFloat(
    JNIEnv* env, jclass, jfloat v) {
    std::string res = am::formatCorpusFloat(v);
    return env->NewStringUTF(res.c_str());
}

} // extern "C"
