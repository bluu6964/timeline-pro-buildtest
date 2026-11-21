// JNI for the clip-graph algebra (engine/clip_graph.h, migration stage 2).
// Every graph entry point takes the packed graph slice in the fixed
// argument order the Kotlin bridge (engine/ClipGraphNative.kt) packs it:
// laneOffsets, laneIndex, idKey, groupKey, chainKey, parentKey,
// controllerKey, keyToClip, type, startMs, durationMs, locked, visible,
// muted, groupFreeLength, fadeInMs, fadeOutMs, trimStartMs, speed,
// hasSource, sourceDurationMs — then the op's own arguments. Results come
// back as flat primitive arrays in the layouts ClipGraphNative documents.

#include <jni.h>

#include <cstdint>
#include <vector>

#include "clip_graph.h"

namespace {

using am::GraphPack;

template <typename T>
std::vector<T> readArray(JNIEnv* env, jobject arr) {
    if (arr == nullptr) return {};
    auto* a = reinterpret_cast<jarray>(arr);
    jsize len = env->GetArrayLength(a);
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

GraphPack readPack(JNIEnv* env, jintArray laneOffsets, jintArray laneIndex, jintArray idKey,
                   jintArray groupKey, jintArray chainKey, jintArray parentKey,
                   jintArray controllerKey, jintArray keyToClip, jintArray type,
                   jlongArray startMs, jlongArray durationMs, jbooleanArray locked,
                   jbooleanArray visible, jbooleanArray muted, jbooleanArray groupFreeLength,
                   jlongArray fadeInMs, jlongArray fadeOutMs, jlongArray trimStartMs,
                   jfloatArray speed, jbooleanArray hasSource, jlongArray sourceDurationMs) {
    GraphPack p;
    p.laneOffsets = readArray<int32_t>(env, laneOffsets);
    p.laneIndex = readArray<int32_t>(env, laneIndex);
    p.idKey = readArray<int32_t>(env, idKey);
    p.groupKey = readArray<int32_t>(env, groupKey);
    p.chainKey = readArray<int32_t>(env, chainKey);
    p.parentKey = readArray<int32_t>(env, parentKey);
    p.controllerKey = readArray<int32_t>(env, controllerKey);
    p.keyToClip = readArray<int32_t>(env, keyToClip);
    p.type = readArray<int32_t>(env, type);
    p.startMs = readArray<int64_t>(env, startMs);
    p.durationMs = readArray<int64_t>(env, durationMs);
    p.locked = readArray<uint8_t>(env, locked);
    p.visible = readArray<uint8_t>(env, visible);
    p.muted = readArray<uint8_t>(env, muted);
    p.groupFreeLength = readArray<uint8_t>(env, groupFreeLength);
    p.fadeInMs = readArray<int64_t>(env, fadeInMs);
    p.fadeOutMs = readArray<int64_t>(env, fadeOutMs);
    p.trimStartMs = readArray<int64_t>(env, trimStartMs);
    p.speed = readArray<float>(env, speed);
    p.hasSource = readArray<uint8_t>(env, hasSource);
    p.sourceDurationMs = readArray<int64_t>(env, sourceDurationMs);
    p.n = static_cast<int32_t>(p.idKey.size());
    p.keyCount = static_cast<int32_t>(p.keyToClip.size());
    return p;
}

jlongArray makeLongs(JNIEnv* env, const std::vector<int64_t>& v) {
    jlongArray out = env->NewLongArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetLongArrayRegion(out, 0, static_cast<jsize>(v.size()),
                                reinterpret_cast<const jlong*>(v.data()));
    return out;
}

jintArray makeInts(JNIEnv* env, const std::vector<int32_t>& v) {
    jintArray out = env->NewIntArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(v.size()),
                               reinterpret_cast<const jint*>(v.data()));
    return out;
}

jfloatArray makeFloats(JNIEnv* env, const std::vector<float>& v) {
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetFloatArrayRegion(out, 0, static_cast<jsize>(v.size()),
                                 reinterpret_cast<const jfloat*>(v.data()));
    return out;
}

#define PACK_ARGS \
    jintArray laneOffsets, jintArray laneIndex, jintArray idKey, jintArray groupKey, \
    jintArray chainKey, jintArray parentKey, jintArray controllerKey, jintArray keyToClip, \
    jintArray type, jlongArray startMs, jlongArray durationMs, jbooleanArray locked, \
    jbooleanArray visible, jbooleanArray muted, jbooleanArray groupFreeLength, \
    jlongArray fadeInMs, jlongArray fadeOutMs, jlongArray trimStartMs, jfloatArray speed, \
    jbooleanArray hasSource, jlongArray sourceDurationMs

#define READ_PACK \
    readPack(env, laneOffsets, laneIndex, idKey, groupKey, chainKey, parentKey, controllerKey, \
             keyToClip, type, startMs, durationMs, locked, visible, muted, groupFreeLength, \
             fadeInMs, fadeOutMs, trimStartMs, speed, hasSource, sourceDurationMs)

}  // namespace

extern "C" {

// [mode, targetClip, startMs, durationMs, outerKey, topLaneIndex, groupCount, movers...]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeGroupPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                        jbooleanArray selected) {
    GraphPack p = READ_PACK;
    am::GroupPlan plan = am::graphGroupPlan(p, readArray<uint8_t>(env, selected));
    std::vector<int64_t> out = {plan.mode, plan.targetClip, plan.startMs, plan.durationMs,
                                plan.outerKey, plan.topLaneIndex, plan.groupCount};
    for (int32_t m : plan.movers) out.push_back(m);
    return makeLongs(env, out);
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeUngroupPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                          jint opGroupKey) {
    GraphPack p = READ_PACK;
    return makeInts(env, am::graphUngroupPlan(p, opGroupKey));
}

// per original: [groupKeyOut, parentKeyOut, controllerKeyOut, nameSuffix]
JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeDuplicatePlan(JNIEnv* env, jobject, PACK_ARGS,
                                                            jintArray originalOrder,
                                                            jint opGroupKey) {
    GraphPack p = READ_PACK;
    auto entries = am::graphDuplicatePlan(p, readArray<int32_t>(env, originalOrder), opGroupKey);
    std::vector<int32_t> out;
    for (const auto& e : entries) {
        out.push_back(e.groupKeyOut);
        out.push_back(e.parentKeyOut);
        out.push_back(e.controllerKeyOut);
        out.push_back(e.nameSuffix);
    }
    return makeInts(env, out);
}

// [ok, groupStartMs, groupFirstDurMs, kidCount, per kid: clip, cls, d1, parentOut, halfKey]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeSplitGroupPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                              jint opGroupKey, jlong playheadMs) {
    GraphPack p = READ_PACK;
    am::SplitGroupPlan plan = am::graphSplitGroupPlan(p, opGroupKey, playheadMs);
    std::vector<int64_t> out = {plan.ok, plan.groupStartMs, plan.groupFirstDurMs,
                                static_cast<int64_t>(plan.kids.size())};
    for (const auto& k : plan.kids) {
        out.push_back(k.clip);
        out.push_back(k.cls);
        out.push_back(k.d1);
        out.push_back(k.parentOut);
        out.push_back(k.halfKey);
    }
    return makeLongs(env, out);
}

// [changed, refitCount, per refit: clip, startMs, durationMs, trimDelta, endDelta,
//  then per clip: clearGroup, clearParent]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeFitGroupsPass(JNIEnv* env, jobject, PACK_ARGS) {
    GraphPack p = READ_PACK;
    am::FitGroupsPass pass = am::graphFitGroupsPass(p);
    std::vector<int64_t> out = {pass.changed, static_cast<int64_t>(pass.refits.size())};
    for (const auto& r : pass.refits) {
        out.push_back(r.clip);
        out.push_back(r.startMs);
        out.push_back(r.durationMs);
        out.push_back(r.trimDelta);
        out.push_back(r.endDelta);
    }
    for (int32_t i = 0; i < p.n; i++) {
        out.push_back(pass.clearGroup[i]);
        out.push_back(pass.clearParent[i]);
    }
    return makeLongs(env, out);
}

// [laneCount, keptCounts..., kept indices..., overflowCount, overflow..., clearChain...]
JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeNormalizePlan(JNIEnv* env, jobject, PACK_ARGS) {
    GraphPack p = READ_PACK;
    am::NormalizePlan plan = am::graphNormalizePlan(p);
    std::vector<int32_t> out;
    out.push_back(static_cast<int32_t>(plan.keptPerLane.size()));
    for (const auto& kept : plan.keptPerLane) out.push_back(static_cast<int32_t>(kept.size()));
    for (const auto& kept : plan.keptPerLane)
        for (int32_t i : kept) out.push_back(i);
    out.push_back(static_cast<int32_t>(plan.overflow.size()));
    for (int32_t i : plan.overflow) out.push_back(i);
    for (int32_t i = 0; i < p.n; i++) out.push_back(plan.clearChain[i]);
    return makeInts(env, out);
}

// [changed, count, per entry: clip, startMs, durationMs]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeFitChains(JNIEnv* env, jobject, PACK_ARGS) {
    GraphPack p = READ_PACK;
    am::FitChainsPlan plan = am::graphFitChains(p);
    std::vector<int64_t> out = {plan.changed, static_cast<int64_t>(plan.entries.size())};
    for (const auto& e : plan.entries) {
        out.push_back(e.clip);
        out.push_back(e.startMs);
        out.push_back(e.durationMs);
    }
    return makeLongs(env, out);
}

// [identity, from, to]
JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeReorder(JNIEnv* env, jobject, jint laneCount,
                                                      jint from, jint to) {
    am::ReorderPlan plan = am::graphReorder(laneCount, from, to);
    return makeInts(env, {plan.identity, plan.from, plan.to});
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipGraphNative_nativePlaceInChainLane(JNIEnv* env, jobject, PACK_ARGS,
                                                               jint opChainKey) {
    GraphPack p = READ_PACK;
    return am::graphPlaceInChainLane(p, opChainKey);
}

// [selCount, insertAt, chainStart, chainDur, selOrder...]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeChainSelectedPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                                jbooleanArray selected) {
    GraphPack p = READ_PACK;
    am::ChainSelectedPlan plan = am::graphChainSelectedPlan(p, readArray<uint8_t>(env, selected));
    std::vector<int64_t> out = {static_cast<int64_t>(plan.selOrder.size()), plan.insertAt,
                                plan.chainStart, plan.chainDur};
    for (int32_t i : plan.selOrder) out.push_back(i);
    return makeLongs(env, out);
}

// [editCount, per edit: clip, code, newStart, newDur, newTrim, fadeMode, kfOp, kfDelta, half2Trim]
JNIEXPORT jlongArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeMultiApplyPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                             jbooleanArray selected, jint action,
                                                             jlong playheadMs) {
    GraphPack p = READ_PACK;
    auto edits = am::graphMultiApplyPlan(p, readArray<uint8_t>(env, selected), action, playheadMs);
    std::vector<int64_t> out = {static_cast<int64_t>(edits.size())};
    for (const auto& e : edits) {
        out.push_back(e.clip);
        out.push_back(e.code);
        out.push_back(e.newStart);
        out.push_back(e.newDur);
        out.push_back(e.newTrim);
        out.push_back(e.fadeMode);
        out.push_back(e.kfOp);
        out.push_back(e.kfDelta);
        out.push_back(e.half2Trim);
    }
    return makeLongs(env, out);
}

// [mainCount, colourCount, colourScalarsChanged, main entries (prop, mode, a, b)...,
//  colour entries...]; rel is returned through relOut[0].
JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeFanPlan(
    JNIEnv* env, jobject,
    jfloatArray beforeScalars, jlongArray beforeColors, jint beforeGradType,
    jintArray beforeCounts, jlongArray beforeTimes, jfloatArray beforeVals,
    jlong beforeStart,
    jfloatArray afterScalars, jlongArray afterColors, jint afterGradType,
    jintArray afterCounts, jlongArray afterTimes, jfloatArray afterVals,
    jlong afterStart,
    jfloatArray layerScalars, jlongArray layerColors, jint layerGradType,
    jintArray layerCounts, jlongArray layerTimes, jfloatArray layerVals,
    jlong layerStart, jlong layerDur, jint layerType, jlong timeMs, jlongArray relOut) {
    auto bs = readArray<float>(env, beforeScalars);
    auto bc = readArray<int64_t>(env, beforeColors);
    auto bct = readArray<int32_t>(env, beforeCounts);
    auto bt = readArray<int64_t>(env, beforeTimes);
    auto bv = readArray<float>(env, beforeVals);
    auto as = readArray<float>(env, afterScalars);
    auto ac = readArray<int64_t>(env, afterColors);
    auto act = readArray<int32_t>(env, afterCounts);
    auto at = readArray<int64_t>(env, afterTimes);
    auto av = readArray<float>(env, afterVals);
    auto ls = readArray<float>(env, layerScalars);
    auto lc = readArray<int64_t>(env, layerColors);
    auto lct = readArray<int32_t>(env, layerCounts);
    auto lt = readArray<int64_t>(env, layerTimes);
    auto lv = readArray<float>(env, layerVals);
    am::FanPlan plan = am::graphFanPlan(
        bs.data(), bc.data(), beforeGradType, bct.data(), bt.data(), bv.data(), beforeStart,
        as.data(), ac.data(), afterGradType, act.data(), at.data(), av.data(),
        afterStart, ls.data(), lc.data(), layerGradType, lct.data(), lt.data(),
        lv.data(), layerStart, layerDur, layerType, timeMs);
    std::vector<float> out = {static_cast<float>(plan.entries.size()),
                              static_cast<float>(plan.colourEntries.size()),
                              static_cast<float>(plan.colourScalarsChanged)};
    for (const auto& e : plan.entries) {
        out.push_back(static_cast<float>(e.prop));
        out.push_back(static_cast<float>(e.mode));
        out.push_back(e.a);
        out.push_back(e.b);
    }
    for (const auto& e : plan.colourEntries) {
        out.push_back(static_cast<float>(e.prop));
        out.push_back(static_cast<float>(e.mode));
        out.push_back(e.a);
        out.push_back(e.b);
    }
    if (relOut != nullptr && env->GetArrayLength(relOut) >= 1) {
        jlong rel = plan.rel;
        env->SetLongArrayRegion(relOut, 0, 1, &rel);
    }
    return makeFloats(env, out);
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeConnected(JNIEnv* env, jobject, PACK_ARGS,
                                                        jint opControllerKey) {
    GraphPack p = READ_PACK;
    return makeInts(env, am::graphConnected(p, opControllerKey));
}

// [soloVisible..., soloMuted..., soloCopy..., restVisible..., restMuted..., restCopy...] (0/1)
JNIEXPORT jintArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeSoloPlan(JNIEnv* env, jobject, PACK_ARGS,
                                                       jint soloKey, jbooleanArray stateHas,
                                                       jbooleanArray stateVisible,
                                                       jbooleanArray stateMuted) {
    GraphPack p = READ_PACK;
    am::SoloPlan plan = am::graphSoloPlan(p, soloKey, readArray<uint8_t>(env, stateHas),
                                          readArray<uint8_t>(env, stateVisible),
                                          readArray<uint8_t>(env, stateMuted));
    std::vector<int32_t> out;
    auto pushAll = [&](const std::vector<uint8_t>& v) {
        for (uint8_t x : v) out.push_back(x);
    };
    pushAll(plan.soloVisible);
    pushAll(plan.soloMuted);
    pushAll(plan.soloCopy);
    pushAll(plan.restVisible);
    pushAll(plan.restMuted);
    pushAll(plan.restCopy);
    return makeInts(env, out);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeMaskStateAt(JNIEnv* env, jobject,
                                                          jfloatArray base, jlongArray times,
                                                          jfloatArray states, jfloatArray easings,
                                                          jlong t) {
    auto b = readArray<float>(env, base);
    auto tm = readArray<int64_t>(env, times);
    auto st = readArray<float>(env, states);
    auto ea = readArray<float>(env, easings);
    am::MaskStateF baseF;
    for (int i = 0; i < 8; i++) baseF.v[i] = b[static_cast<size_t>(i)];
    am::MaskStateF out = am::graphMaskStateAt(baseF, static_cast<int32_t>(tm.size()), tm.data(),
                                              st.data(), ea.data(), t);
    return makeFloats(env, std::vector<float>(out.v, out.v + 8));
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipGraphNative_nativeMaskKeyIndexAt(JNIEnv* env, jobject,
                                                             jlongArray times, jlong t) {
    auto tm = readArray<int64_t>(env, times);
    return am::graphMaskKeyIndexAt(static_cast<int32_t>(tm.size()), tm.data(), t);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeMaskLerp(JNIEnv* env, jobject, jfloatArray a,
                                                       jfloatArray b, jfloat t) {
    auto av = readArray<float>(env, a);
    auto bv = readArray<float>(env, b);
    am::MaskStateF af, bf;
    for (int i = 0; i < 8; i++) {
        af.v[i] = av[static_cast<size_t>(i)];
        bf.v[i] = bv[static_cast<size_t>(i)];
    }
    am::MaskStateF out = am::graphMaskLerp(af, bf, t);
    return makeFloats(env, std::vector<float>(out.v, out.v + 8));
}

JNIEXPORT jint JNICALL
Java_com_example_engine_ClipGraphNative_nativeTrackIndexAt(JNIEnv* env, jobject,
                                                           jlongArray times, jlong t) {
    auto tm = readArray<int64_t>(env, times);
    return am::graphTrackIndexAt(static_cast<int32_t>(tm.size()), tm.data(), t);
}

// [applied, x, y, rot, scalePercent, shapeScaleX, shapeScaleY]
JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeTrackRender(
    JNIEnv* env, jobject, jlongArray times, jfloatArray dx, jfloatArray dy, jfloatArray rot,
    jfloatArray scale, jfloat tx, jfloat ty, jfloat rotDeg, jfloat scalePercent, jfloat ssx,
    jfloat ssy, jlong t) {
    auto tm = readArray<int64_t>(env, times);
    auto dxv = readArray<float>(env, dx);
    auto dyv = readArray<float>(env, dy);
    auto rotv = readArray<float>(env, rot);
    auto scalev = readArray<float>(env, scale);
    am::TrackRenderPlan plan = am::graphTrackRender(
        static_cast<int32_t>(tm.size()), tm.data(), dxv.data(), dyv.data(),
        rot == nullptr ? nullptr : rotv.data(), scale == nullptr ? nullptr : scalev.data(),
        tx, ty, rotDeg, scalePercent, ssx, ssy, t);
    return makeFloats(env, {static_cast<float>(plan.applied), plan.x, plan.y, plan.rot,
                            plan.scalePercent, plan.shapeScaleX, plan.shapeScaleY});
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_engine_ClipGraphNative_nativeTrackOffset(JNIEnv* env, jobject, jlongArray times,
                                                          jfloatArray dx, jfloatArray dy, jlong t) {
    auto tm = readArray<int64_t>(env, times);
    auto dxv = readArray<float>(env, dx);
    auto dyv = readArray<float>(env, dy);
    float out[2];
    am::graphTrackOffset(static_cast<int32_t>(tm.size()), tm.data(), dxv.data(), dyv.data(), t, out);
    return makeFloats(env, {out[0], out[1]});
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_ClipGraphNative_nativeDetachedKeepSpeed(JNIEnv* env, jobject,
                                                                jintArray propCounts) {
    auto counts = readArray<int32_t>(env, propCounts);
    return am::graphDetachedKeepSpeed(counts.data()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_ClipGraphNative_nativeImportedStart(JNIEnv*, jobject, jlong startMs) {
    return am::graphImportedStart(startMs);
}

}  // extern "C"
