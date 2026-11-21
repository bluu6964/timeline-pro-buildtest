// JNI for the template builders' laws (engine/template_plans.h, migration
// stage 3). The builder entry points take the template's inputs with every
// string as a UTF-8 byte array (the templates carry Bengali sample texts)
// and return the flat construction plan TemplatePlansNative documents. The
// Spec entry points are thin scalar/array wrappers over the same laws. The
// UserTemplateOps entries take the identity-key packs the model side builds
// (see TemplatePlansNative / UserTemplateOps).

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "template_plans.h"

namespace {

std::string readBytes(JNIEnv* env, jbyteArray arr) {
    if (arr == nullptr) return {};
    const jsize len = env->GetArrayLength(arr);
    if (len == 0) return {};
    std::string out(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

std::vector<std::string> readBytesArray(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (arr == nullptr) return out;
    const jsize n = env->GetArrayLength(arr);
    out.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; i++) {
        auto* el = static_cast<jbyteArray>(env->GetObjectArrayElement(arr, i));
        out.push_back(readBytes(env, el));
        if (el != nullptr) env->DeleteLocalRef(el);
    }
    return out;
}

template <typename T>
std::vector<T> readArray(JNIEnv* env, jobject arr) {
    if (arr == nullptr) return {};
    auto* a = reinterpret_cast<jarray>(arr);
    const jsize len = env->GetArrayLength(a);
    std::vector<T> out(static_cast<size_t>(len));
    if (len == 0) return out;
    if constexpr (std::is_same_v<T, int32_t>) {
        env->GetIntArrayRegion(reinterpret_cast<jintArray>(a), 0, len,
                               reinterpret_cast<jint*>(out.data()));
    } else if constexpr (std::is_same_v<T, int64_t>) {
        env->GetLongArrayRegion(reinterpret_cast<jlongArray>(a), 0, len,
                                reinterpret_cast<jlong*>(out.data()));
    } else if constexpr (std::is_same_v<T, float>) {
        env->GetFloatArrayRegion(reinterpret_cast<jfloatArray>(a), 0, len,
                                 reinterpret_cast<jfloat*>(out.data()));
    } else if constexpr (std::is_same_v<T, uint8_t>) {
        env->GetBooleanArrayRegion(reinterpret_cast<jbooleanArray>(a), 0, len,
                                   reinterpret_cast<jboolean*>(out.data()));
    }
    return out;
}

jbyteArray makeBytes(JNIEnv* env, const std::vector<uint8_t>& v) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(v.size()),
                                reinterpret_cast<const jbyte*>(v.data()));
    }
    return out;
}

jfloatArray makeFloats(JNIEnv* env, const float* v, size_t n) {
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(n));
    if (n > 0) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(n), v);
    return out;
}

jintArray makeInts(JNIEnv* env, const std::vector<int32_t>& v) {
    jintArray out = env->NewIntArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(v.size()),
                               reinterpret_cast<const jint*>(v.data()));
    }
    return out;
}

jlongArray makeLongs(JNIEnv* env, const std::vector<int64_t>& v) {
    jlongArray out = env->NewLongArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetLongArrayRegion(out, 0, static_cast<jsize>(v.size()),
                                reinterpret_cast<const jlong*>(v.data()));
    }
    return out;
}

}  // namespace

extern "C" {

// ---- builders ----

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselPlan(
    JNIEnv* env, jclass, jfloat pw, jfloat ph, jbyteArray backgroundUri, jobjectArray cardUris,
    jlong insertAtMs, jbyteArray idBase, jint cardCount, jlong durationMs, jboolean clockwise) {
    return makeBytes(env, am::carouselPlan(pw, ph, readBytes(env, backgroundUri),
                                           readBytesArray(env, cardUris), insertAtMs,
                                           readBytes(env, idBase), cardCount, durationMs,
                                           clockwise != JNI_FALSE));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativePopTitlePlan(JNIEnv* env, jclass, jfloat pw,
                                                               jlong insertAtMs,
                                                               jbyteArray idBase) {
    return makeBytes(env, am::popTitlePlan(pw, insertAtMs, readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeLogoRevealPlan(JNIEnv* env, jclass, jfloat pw,
                                                                 jbyteArray logoUri,
                                                                 jlong insertAtMs,
                                                                 jbyteArray idBase) {
    return makeBytes(env, am::logoRevealPlan(pw, readBytes(env, logoUri), insertAtMs,
                                             readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeWordPopPlan(JNIEnv* env, jclass, jfloat pw,
                                                              jlong insertAtMs,
                                                              jbyteArray idBase) {
    return makeBytes(env, am::wordPopPlan(pw, insertAtMs, readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGeniePlan(JNIEnv* env, jclass, jfloat pw,
                                                            jfloat ph, jobjectArray avatarUris,
                                                            jobjectArray photoUris,
                                                            jlong insertAtMs, jbyteArray idBase) {
    return makeBytes(env, am::geniePlan(pw, ph, readBytesArray(env, avatarUris),
                                        readBytesArray(env, photoUris), insertAtMs,
                                        readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativePieCounterPlan(JNIEnv* env, jclass, jfloat pw,
                                                                 jfloat ph, jlong insertAtMs,
                                                                 jbyteArray idBase) {
    return makeBytes(env, am::pieCounterPlan(pw, ph, insertAtMs, readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeDonutPlan(JNIEnv* env, jclass, jfloat pw,
                                                            jfloat ph, jlong insertAtMs,
                                                            jbyteArray idBase) {
    return makeBytes(env, am::donutPlan(pw, ph, insertAtMs, readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeSocialGrowthPlan(JNIEnv* env, jclass, jfloat pw,
                                                                   jfloat ph, jbyteArray avatarUri,
                                                                   jlong insertAtMs,
                                                                   jbyteArray idBase) {
    return makeBytes(env, am::socialGrowthPlan(pw, ph, readBytes(env, avatarUri), insertAtMs,
                                               readBytes(env, idBase)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeLowerThirdPlan(JNIEnv* env, jclass, jfloat pw,
                                                                 jfloat ph, jlong insertAtMs,
                                                                 jbyteArray idBase) {
    return makeBytes(env, am::lowerThirdPlan(pw, ph, insertAtMs, readBytes(env, idBase)));
}

// ---- Spec laws ----

JNIEXPORT jdouble JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselProgress(JNIEnv*, jclass, jdouble x) {
    return am::carouselProgress(x);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselRingRadius(JNIEnv*, jclass,
                                                                     jint cardCount) {
    return am::carouselRingRadius(cardCount);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselFit(JNIEnv* env, jclass, jfloat pw,
                                                              jfloat ph, jfloat radius) {
    float out[3];
    am::carouselFit(pw, ph, radius, out);
    return makeFloats(env, out, 3);
}

JNIEXPORT jdouble JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselAngleDegAt(
    JNIEnv*, jclass, jlong tMs, jint cardIndex, jint cardCount, jlong durationMs,
    jboolean clockwise) {
    return am::carouselAngleDegAt(tMs, cardIndex, cardCount, durationMs, clockwise != JNI_FALSE);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCarouselCenterSceneAt(
    JNIEnv* env, jclass, jlong tMs, jint cardIndex, jint cardCount, jlong durationMs,
    jboolean clockwise) {
    float out[2];
    am::carouselCenterSceneAt(tMs, cardIndex, cardCount, durationMs, clockwise != JNI_FALSE, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_TemplatePlansNative_nativeWordPopStartMs(JNIEnv*, jclass, jint index) {
    return am::wordPopStartMs(index);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeWordPopAdvancePx(JNIEnv*, jclass, jint index,
                                                                   jfloat pw) {
    return am::wordPopAdvancePx(index, pw);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeWordPopTotalWidthPx(JNIEnv*, jclass, jfloat pw) {
    return am::wordPopTotalWidthPx(pw);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeWordPopCentreXPx(JNIEnv*, jclass, jint index,
                                                                   jfloat pw) {
    return am::wordPopCentreXPx(index, pw);
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieCardStartMs(JNIEnv*, jclass, jint index) {
    return am::genieCardStartMs(index);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieAvatarU(JNIEnv*, jclass, jint index,
                                                               jfloat pw) {
    return am::genieAvatarU(index, pw);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieScrollAt(JNIEnv*, jclass, jlong relMs,
                                                                jfloat pw) {
    return am::genieScrollAt(relMs, pw);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieBarPoint(JNIEnv* env, jclass, jfloat u,
                                                                jfloat v, jfloat pw, jfloat ph) {
    float out[2];
    am::genieBarPoint(u, v, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieAvatarPosAt(JNIEnv* env, jclass,
                                                                   jint index, jlong relMs,
                                                                   jfloat pw, jfloat ph) {
    float out[2];
    am::genieAvatarPosAt(index, relMs, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieAvatarNamePosAt(JNIEnv* env, jclass,
                                                                       jint index, jlong relMs,
                                                                       jfloat pw, jfloat ph) {
    float out[2];
    am::genieAvatarNamePosAt(index, relMs, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGeniePetalOffset(JNIEnv* env, jclass, jint k,
                                                                   jfloat pw) {
    float out[2];
    am::geniePetalOffset(k, pw, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieBarEndFlowerCentre(JNIEnv* env, jclass,
                                                                          jfloat pw, jfloat ph) {
    float out[2];
    am::genieBarEndFlowerCentre(pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeGenieCardPoint(JNIEnv* env, jclass, jfloat lx,
                                                                 jfloat ly, jfloat pw, jfloat ph) {
    float out[2];
    am::genieCardPoint(lx, ly, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativePiePercentAt(JNIEnv*, jclass, jlong relMs) {
    return am::piePercentAt(relMs);
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativePieCounterAnimator(JNIEnv* env, jclass) {
    return makeBytes(env, am::pieCounterAnimatorBlob());
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeCounterAnimator(JNIEnv* env, jclass,
                                                                  jfloatArray tvPairs) {
    return makeBytes(env, am::counterAnimatorBlob(readArray<float>(env, tvPairs)));
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeSocialTilePos(JNIEnv* env, jclass, jint index,
                                                                jfloat pw, jfloat ph) {
    float out[2];
    am::socialTilePos(index, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeDonutLabelPos(JNIEnv* env, jclass, jint index,
                                                                jfloat pw, jfloat ph) {
    float out[2];
    am::donutLabelPos(index, pw, ph, out);
    return makeFloats(env, out, 2);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_TemplatePlansNative_nativeDonutLegendY(JNIEnv*, jclass, jint index,
                                                               jfloat ph) {
    return am::donutLegendY(index, ph);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeLowerThirdLayout(JNIEnv* env, jclass, jfloat pw,
                                                                    jfloat ph) {
    float out[14];
    am::lowerThirdLayout(pw, ph, out);
    return makeFloats(env, out, 14);
}

// ---- UserTemplateOps ----

JNIEXPORT jbyteArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeUserMediaRefs(JNIEnv* env, jclass,
                                                                jobjectArray flatRefs,
                                                                jint clipCount) {
    return makeBytes(env, am::userMediaRefs(readBytesArray(env, flatRefs), clipCount));
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeUserCapturePlan(
    JNIEnv* env, jclass, jintArray idKey, jintArray groupKey, jintArray parentKey,
    jintArray controllerKey, jintArray maskKey, jintArray tfKey, jintArray cpKey, jintArray prKey,
    jintArray keyToClip, jbooleanArray has, jint groupKeyOf, jbooleanArray prSelf) {
    return makeInts(env, am::userCapturePlan(
                             readArray<int32_t>(env, idKey), readArray<int32_t>(env, groupKey),
                             readArray<int32_t>(env, parentKey),
                             readArray<int32_t>(env, controllerKey),
                             readArray<int32_t>(env, maskKey), readArray<int32_t>(env, tfKey),
                             readArray<int32_t>(env, cpKey), readArray<int32_t>(env, prKey),
                             readArray<int32_t>(env, keyToClip), readArray<uint8_t>(env, has),
                             groupKeyOf, readArray<uint8_t>(env, prSelf)));
}

JNIEXPORT jlongArray JNICALL
Java_com_example_engine_TemplatePlansNative_nativeUserInstantiatePlan(
    JNIEnv* env, jclass, jintArray groupKey, jintArray parentKey, jintArray controllerKey,
    jintArray maskKey, jintArray tfKey, jintArray cpKey, jintArray prKey, jintArray keyToClip,
    jbooleanArray has, jlongArray startMs, jlong insertAtMs) {
    return makeLongs(env, am::userInstantiatePlan(
                              readArray<int32_t>(env, groupKey), readArray<int32_t>(env, parentKey),
                              readArray<int32_t>(env, controllerKey),
                              readArray<int32_t>(env, maskKey), readArray<int32_t>(env, tfKey),
                              readArray<int32_t>(env, cpKey), readArray<int32_t>(env, prKey),
                              readArray<int32_t>(env, keyToClip), readArray<uint8_t>(env, has),
                              readArray<int64_t>(env, startMs), insertAtMs));
}

}  // extern "C"
