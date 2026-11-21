// JNI for clip_algebra (the TimelineClip keyframe algebra, migration
// stage 1). Bridge: com.example.engine.ClipAlgebraNative. Arrays are
// copied in/out via region calls; list ops write into caller-sized out
// arrays and return the new key count.

#include <jni.h>

#include <vector>

#include "clip_algebra.h"

namespace {

std::vector<float> readFloats(JNIEnv* env, jfloatArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::vector<float> v(static_cast<size_t>(n));
    if (n > 0) env->GetFloatArrayRegion(arr, 0, n, v.data());
    return v;
}

std::vector<int64_t> readLongs(JNIEnv* env, jlongArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::vector<int64_t> v(static_cast<size_t>(n));
    if (n > 0) env->GetLongArrayRegion(arr, 0, n, reinterpret_cast<jlong*>(v.data()));
    return v;
}

std::vector<int32_t> readInts(JNIEnv* env, jintArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::vector<int32_t> v(static_cast<size_t>(n));
    if (n > 0) env->GetIntArrayRegion(arr, 0, n, reinterpret_cast<jint*>(v.data()));
    return v;
}

void writeFloats(JNIEnv* env, jfloatArray arr, const float* data, jsize n) {
    if (n > 0) env->SetFloatArrayRegion(arr, 0, n, data);
}

void writeLongs(JNIEnv* env, jlongArray arr, const int64_t* data, jsize n) {
    if (n > 0) env->SetLongArrayRegion(arr, 0, n, reinterpret_cast<const jlong*>(data));
}

void writeInts(JNIEnv* env, jintArray arr, const int32_t* data, jsize n) {
    if (n > 0) env->SetIntArrayRegion(arr, 0, n, reinterpret_cast<const jint*>(data));
}

void writeList(JNIEnv* env, const am::KfList& l, jlongArray outTimes, jfloatArray outVals,
               jintArray outTags) {
    const jsize n = static_cast<jsize>(l.times.size());
    writeLongs(env, outTimes, l.times.data(), n);
    writeFloats(env, outVals, l.vals.data(), n * 6);
    writeInts(env, outTags, l.tags.data(), n);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_engine_ClipAlgebraNative_nativePropValues(
    JNIEnv* env, jclass, jfloatArray scalars, jlongArray colorLongs, jint gradType,
    jint prop, jfloatArray out2) {
    auto sc = readFloats(env, scalars);
    auto cl = readLongs(env, colorLongs);
    float out[2] = {0.0f, 0.0f};
    if (sc.size() >= 24 && cl.size() >= 3) {
        am::algebraPropValues(sc.data(), cl.data(), gradType, prop, out);
    }
    writeFloats(env, out2, out, 2);
}

JNIEXPORT void JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeWithProp(
    JNIEnv* env, jclass, jfloatArray scalars, jlongArray colorLongs, jint gradType,
    jint prop, jfloat a, jfloat b) {
    auto sc = readFloats(env, scalars);
    auto cl = readLongs(env, colorLongs);
    if (sc.size() < 24 || cl.size() < 3) return;
    am::algebraWithProp(sc.data(), cl.data(), gradType, prop, a, b);
    writeFloats(env, scalars, sc.data(), 24);
    writeLongs(env, colorLongs, cl.data(), 3);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeClipAt(
    JNIEnv* env, jclass, jlong startMs, jfloatArray scalars, jlongArray colorLongs,
    jint gradType, jintArray propCounts, jlongArray kfTimes, jfloatArray kfVals,
    jlongArray pieStartTimes, jfloatArray pieStartVals,
    jlongArray pieEndTimes, jfloatArray pieEndVals,
    jlongArray arcStartTimes, jfloatArray arcStartVals,
    jlongArray arcEndTimes, jfloatArray arcEndVals,
    jfloat pieRadius, jfloat arcRadius, jboolean arcClosed,
    jlong timeMs, jbooleanArray changedOut) {
    auto sc = readFloats(env, scalars);
    auto cl = readLongs(env, colorLongs);
    auto counts = readInts(env, propCounts);
    auto times = readLongs(env, kfTimes);
    auto vals = readFloats(env, kfVals);
    auto psT = readLongs(env, pieStartTimes);
    auto psV = readFloats(env, pieStartVals);
    auto peT = readLongs(env, pieEndTimes);
    auto peV = readFloats(env, pieEndVals);
    auto asT = readLongs(env, arcStartTimes);
    auto asV = readFloats(env, arcStartVals);
    auto aeT = readLongs(env, arcEndTimes);
    auto aeV = readFloats(env, arcEndVals);
    if (sc.size() < 24 || cl.size() < 3 || counts.size() < am::kAlgebraPropCount) return nullptr;
    std::vector<float> segments;
    bool hasSegments = false;
    bool changed = false;
    am::algebraClipAt(startMs, sc.data(), cl.data(), gradType, counts.data(),
                      times.data(), vals.data(),
                      psT.data(), psV.data(), static_cast<int>(psT.size()),
                      peT.data(), peV.data(), static_cast<int>(peT.size()),
                      asT.data(), asV.data(), static_cast<int>(asT.size()),
                      aeT.data(), aeV.data(), static_cast<int>(aeT.size()),
                      pieRadius, arcRadius, arcClosed != 0, timeMs,
                      segments, hasSegments, changed);
    writeFloats(env, scalars, sc.data(), 24);
    writeLongs(env, colorLongs, cl.data(), 3);
    jboolean ch = changed ? JNI_TRUE : JNI_FALSE;
    env->SetBooleanArrayRegion(changedOut, 0, 1, &ch);
    if (!hasSegments) return nullptr;
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(segments.size()));
    writeFloats(env, out, segments.data(), static_cast<jsize>(segments.size()));
    return out;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeWithKeyframe(
    JNIEnv* env, jclass, jlongArray times, jfloatArray vals, jintArray tags,
    jlong relMs, jfloat a, jfloat b,
    jlongArray outTimes, jfloatArray outVals, jintArray outTags) {
    auto t = readLongs(env, times);
    auto v = readFloats(env, vals);
    auto g = readInts(env, tags);
    am::KfList out;
    am::algebraWithKeyframe(t.data(), v.data(), g.data(), static_cast<int>(t.size()),
                            relMs, a, b, out);
    writeList(env, out, outTimes, outVals, outTags);
    return static_cast<jint>(out.times.size());
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeWithoutKeyframe(
    JNIEnv* env, jclass, jlongArray times, jfloatArray vals, jintArray tags, jint index,
    jlongArray outTimes, jfloatArray outVals, jintArray outTags, jfloatArray removedOut) {
    auto t = readLongs(env, times);
    auto v = readFloats(env, vals);
    auto g = readInts(env, tags);
    am::KfList out;
    float removed[2] = {0.0f, 0.0f};
    am::algebraWithoutKeyframe(t.data(), v.data(), g.data(), static_cast<int>(t.size()),
                               index, out, removed[0], removed[1]);
    writeList(env, out, outTimes, outVals, outTags);
    writeFloats(env, removedOut, removed, 2);
    return static_cast<jint>(out.times.size());
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeWithEasing(
    JNIEnv* env, jclass, jlongArray times, jfloatArray vals, jintArray tags, jint index,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2,
    jlongArray outTimes, jfloatArray outVals, jintArray outTags) {
    auto t = readLongs(env, times);
    auto v = readFloats(env, vals);
    auto g = readInts(env, tags);
    am::KfList out;
    am::algebraWithEasing(t.data(), v.data(), g.data(), static_cast<int>(t.size()),
                          index, x1, y1, x2, y2, out);
    writeList(env, out, outTimes, outVals, outTags);
    return static_cast<jint>(out.times.size());
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeFilterKeysNotTag(
    JNIEnv* env, jclass, jlongArray times, jfloatArray vals, jintArray tags, jint dropTag,
    jlongArray outTimes, jfloatArray outVals, jintArray outTags) {
    auto t = readLongs(env, times);
    auto v = readFloats(env, vals);
    auto g = readInts(env, tags);
    am::KfList out;
    am::algebraFilterNotTag(t.data(), v.data(), g.data(), static_cast<int>(t.size()),
                            dropTag, out);
    writeList(env, out, outTimes, outVals, outTags);
    return static_cast<jint>(out.times.size());
}

JNIEXPORT void JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeShiftKeyTimesByTag(
    JNIEnv* env, jclass, jlongArray times, jintArray tags, jboolean shiftTagged,
    jint tag, jlong delta, jlongArray outTimes) {
    auto t = readLongs(env, times);
    auto g = readInts(env, tags);
    std::vector<int64_t> out(t.size());
    am::algebraShiftKeyTimesByTag(t.data(), g.data(), static_cast<int>(t.size()),
                                  shiftTagged != 0, tag, delta, out.data());
    writeLongs(env, outTimes, out.data(), static_cast<jsize>(out.size()));
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeAnimSpan(
    JNIEnv* env, jclass, jlongArray times, jintArray tags, jint tag) {
    auto t = readLongs(env, times);
    auto g = readInts(env, tags);
    return am::algebraAnimSpan(t.data(), g.data(), static_cast<int>(t.size()), tag);
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeMaxAnimMs(
    JNIEnv*, jclass, jlong otherSpan, jlong durationMs) {
    return am::algebraMaxAnimMs(otherSpan, durationMs);
}

JNIEXPORT void JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeShiftTimes(
    JNIEnv* env, jclass, jlongArray times, jlong delta, jlongArray outTimes) {
    auto t = readLongs(env, times);
    std::vector<int64_t> out(t.size());
    am::algebraShiftTimes(t.data(), static_cast<int>(t.size()), delta, out.data());
    writeLongs(env, outTimes, out.data(), static_cast<jsize>(out.size()));
}

JNIEXPORT void JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeScaleTimes(
    JNIEnv* env, jclass, jlongArray times, jfloat factor, jlongArray outTimes) {
    auto t = readLongs(env, times);
    std::vector<int64_t> out(t.size());
    am::algebraScaleTimes(t.data(), static_cast<int>(t.size()), factor, out.data());
    writeLongs(env, outTimes, out.data(), static_cast<jsize>(out.size()));
}

JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeKeyframeTimes(
    JNIEnv* env, jclass, jlongArray times) {
    auto t = readLongs(env, times);
    std::vector<int64_t> out;
    am::algebraKeyframeTimes(t.data(), static_cast<int>(t.size()), out);
    jlongArray arr = env->NewLongArray(static_cast<jsize>(out.size()));
    writeLongs(env, arr, out.data(), static_cast<jsize>(out.size()));
    return arr;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeClipAnimPlan(
    JNIEnv* env, jclass, jint presetIndex, jint slotTag, jint dir, jfloatArray base,
    jfloat canvasW, jfloat canvasH, jfloatArray out) {
    auto b = readFloats(env, base);
    if (b.size() < 8) return 0;
    std::vector<float> buf(16, 0.0f);
    const int written = am::algebraClipAnimPlan(presetIndex, slotTag, dir, b.data(),
                                                canvasW, canvasH, buf.data());
    writeFloats(env, out, buf.data(), written);
    return written;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipAlgebraNative_nativeAnimWindowSplice(
    JNIEnv* env, jclass, jlongArray times, jfloatArray vals, jintArray tags,
    jlong winStart, jlong winEnd, jlongArray mineTimes, jfloatArray mineVals, jint mineTag,
    jlongArray outTimes, jfloatArray outVals, jintArray outTags) {
    auto t = readLongs(env, times);
    auto v = readFloats(env, vals);
    auto g = readInts(env, tags);
    auto mt = readLongs(env, mineTimes);
    auto mv = readFloats(env, mineVals);
    if (mt.size() < 2 || mv.size() < 12) return 0;
    am::KfList out;
    am::algebraAnimWindowSplice(t.data(), v.data(), g.data(), static_cast<int>(t.size()),
                                winStart, winEnd, mt.data(), mv.data(), mineTag, out);
    writeList(env, out, outTimes, outVals, outTags);
    return static_cast<jint>(out.times.size());
}

}  // extern "C"
