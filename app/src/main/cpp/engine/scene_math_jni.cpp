// JNI bridge between com.example.engine.SceneMathNative (Kotlin) and am::scene.
#include <jni.h>

#include <cmath>
#include <vector>

#include "scene_math.h"

using namespace am::scene;

extern "C" {

// out6: [dx, dy, hasGuideX (0/1), guideX, hasGuideY (0/1), guideY]
JNIEXPORT void JNICALL Java_com_example_engine_SceneMathNative_nativeCanvasSnap(
    JNIEnv* env, jclass,
    jfloat cx, jfloat cy, jfloat left, jfloat top, jfloat right, jfloat bottom,
    jfloat canvasW, jfloat canvasH, jfloatArray othersFlat, jfloat thresholdPx,
    jfloatArray out6) {
    if (!out6 || env->GetArrayLength(out6) < 6) return;
    std::vector<SnapRect> others;
    if (othersFlat) {
        const jsize len = env->GetArrayLength(othersFlat);
        const int count = static_cast<int>(len / 6);
        if (count > 0) {
            std::vector<jfloat> raw(static_cast<size_t>(count) * 6);
            env->GetFloatArrayRegion(othersFlat, 0, count * 6, raw.data());
            others.resize(static_cast<size_t>(count));
            for (int i = 0; i < count; ++i) {
                others[static_cast<size_t>(i)] = {
                    raw[6 * i + 0], raw[6 * i + 1], raw[6 * i + 2],
                    raw[6 * i + 3], raw[6 * i + 4], raw[6 * i + 5]
                };
            }
        }
    }
    const SnapRect r{cx, cy, left, top, right, bottom};
    const SnapResult res = snapRect(r, canvasW, canvasH,
                                    others.empty() ? nullptr : others.data(),
                                    static_cast<int>(others.size()), thresholdPx);
    jfloat out[6] = {
        res.dx, res.dy,
        res.hasGuideX ? 1.0f : 0.0f, res.guideX,
        res.hasGuideY ? 1.0f : 0.0f, res.guideY
    };
    env->SetFloatArrayRegion(out6, 0, 6, out);
}

// rectsFlat: [left, top, right, bottom] * count
// Returns packed float array: 3 floats per delta [index, dx, dy]
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_SceneMathNative_nativeSpatialDeltas(
    JNIEnv* env, jclass, jfloatArray rectsFlat, jint action) {
    if (!rectsFlat) return env->NewFloatArray(0);
    const jsize len = env->GetArrayLength(rectsFlat);
    const int count = static_cast<int>(len / 4);
    if (count <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> raw(static_cast<size_t>(count) * 4);
    env->GetFloatArrayRegion(rectsFlat, 0, count * 4, raw.data());
    std::vector<AlignRect> rects(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        rects[static_cast<size_t>(i)] = {
            raw[4 * i + 0], raw[4 * i + 1], raw[4 * i + 2], raw[4 * i + 3]
        };
    }
    const std::vector<AlignDelta> ds = spatialDeltas(rects.data(), count, action);
    const jsize outLen = static_cast<jsize>(ds.size() * 3);
    jfloatArray out = env->NewFloatArray(outLen);
    if (out && outLen > 0) {
        std::vector<jfloat> packed(static_cast<size_t>(outLen));
        for (size_t i = 0; i < ds.size(); ++i) {
            packed[3 * i + 0] = static_cast<jfloat>(ds[i].index);
            packed[3 * i + 1] = ds[i].dx;
            packed[3 * i + 2] = ds[i].dy;
        }
        env->SetFloatArrayRegion(out, 0, outLen, packed.data());
    }
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_SceneMathNative_nativeSpatialCanvasDeltas(
    JNIEnv* env, jclass, jfloatArray rectsFlat, jfloat frameW, jfloat frameH, jint action) {
    if (!rectsFlat) return env->NewFloatArray(0);
    const jsize len = env->GetArrayLength(rectsFlat);
    const int count = static_cast<int>(len / 4);
    if (count <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> raw(static_cast<size_t>(count) * 4);
    env->GetFloatArrayRegion(rectsFlat, 0, count * 4, raw.data());
    std::vector<AlignRect> rects(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        rects[static_cast<size_t>(i)] = {
            raw[4 * i + 0], raw[4 * i + 1], raw[4 * i + 2], raw[4 * i + 3]
        };
    }
    const std::vector<AlignDelta> ds =
        spatialCanvasDeltas(rects.data(), count, frameW, frameH, action);
    const jsize outLen = static_cast<jsize>(ds.size() * 3);
    jfloatArray out = env->NewFloatArray(outLen);
    if (out && outLen > 0) {
        std::vector<jfloat> packed(static_cast<size_t>(outLen));
        for (size_t i = 0; i < ds.size(); ++i) {
            packed[3 * i + 0] = static_cast<jfloat>(ds[i].index);
            packed[3 * i + 1] = ds[i].dx;
            packed[3 * i + 2] = ds[i].dy;
        }
        env->SetFloatArrayRegion(out, 0, outLen, packed.data());
    }
    return out;
}

JNIEXPORT void JNICALL Java_com_example_engine_SceneMathNative_nativeApplyParentToFp(
    JNIEnv* env, jclass, jfloatArray fp,
    jfloat refX, jfloat refY, jfloat refScale, jfloat refRot,
    jfloat nx, jfloat ny, jfloat nowScale, jfloat nowRot, jfloat k,
    jboolean lockScale, jboolean lockRotate, jboolean hiddenByGroup) {
    if (!fp || env->GetArrayLength(fp) < 16) return;
    jfloat buf[16];
    env->GetFloatArrayRegion(fp, 0, 16, buf);
    applyParentToFp(buf, refX, refY, refScale, refRot, nx, ny, nowScale, nowRot, k,
                    lockScale == JNI_TRUE, lockRotate == JNI_TRUE, hiddenByGroup == JNI_TRUE);
    env->SetFloatArrayRegion(fp, 0, 16, buf);
}

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_SceneMathNative_nativeFlattenPathSegments(
    JNIEnv* env, jclass, jintArray ops, jintArray offsets, jintArray counts,
    jfloatArray pts, jint stepsPerCurve) {
    if (!ops || !offsets || !counts) return env->NewFloatArray(0);
    const jsize segCount = env->GetArrayLength(ops);
    if (segCount <= 0 || env->GetArrayLength(offsets) < segCount ||
        env->GetArrayLength(counts) < segCount) {
        return env->NewFloatArray(0);
    }
    std::vector<jint> vOps(static_cast<size_t>(segCount));
    std::vector<jint> vOff(static_cast<size_t>(segCount));
    std::vector<jint> vCnt(static_cast<size_t>(segCount));
    env->GetIntArrayRegion(ops, 0, segCount, vOps.data());
    env->GetIntArrayRegion(offsets, 0, segCount, vOff.data());
    env->GetIntArrayRegion(counts, 0, segCount, vCnt.data());

    std::vector<jfloat> vPts;
    if (pts) {
        const jsize plen = env->GetArrayLength(pts);
        if (plen > 0) {
            vPts.resize(static_cast<size_t>(plen));
            env->GetFloatArrayRegion(pts, 0, plen, vPts.data());
        }
    }
    const std::vector<float> flat = flattenPathSegments(
        vOps.data(), vOff.data(), vCnt.data(), static_cast<int>(segCount),
        vPts.empty() ? nullptr : vPts.data(), static_cast<int>(vPts.size()), stepsPerCurve);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(flat.size()));
    if (out && !flat.empty()) {
        env->SetFloatArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    }
    return out;
}

// Returns packed float array: 5 floats per advance [startDist, x, y, angleDeg, drawn(0/1)]
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_SceneMathNative_nativeLayoutTextOnPath(
    JNIEnv* env, jclass, jfloatArray xy, jfloatArray cum, jfloat total,
    jfloatArray advances, jfloat startFrac) {
    if (!advances) return env->NewFloatArray(0);
    const jsize advCount = env->GetArrayLength(advances);
    if (advCount <= 0) return env->NewFloatArray(0);

    std::vector<jfloat> vAdv(static_cast<size_t>(advCount));
    env->GetFloatArrayRegion(advances, 0, advCount, vAdv.data());

    std::vector<jfloat> vXy;
    std::vector<jfloat> vCum;
    int ptCount = 0;
    if (xy && cum) {
        const int nXy = static_cast<int>(env->GetArrayLength(xy) / 2);
        const int nCum = static_cast<int>(env->GetArrayLength(cum));
        ptCount = std::min(nXy, nCum);
        if (ptCount > 0) {
            vXy.resize(static_cast<size_t>(ptCount) * 2);
            vCum.resize(static_cast<size_t>(ptCount));
            env->GetFloatArrayRegion(xy, 0, ptCount * 2, vXy.data());
            env->GetFloatArrayRegion(cum, 0, ptCount, vCum.data());
        }
    }

    const std::vector<PathPlacement> pl = layoutTextOnPath(
        vXy.empty() ? nullptr : vXy.data(),
        vCum.empty() ? nullptr : vCum.data(),
        ptCount, total, vAdv.data(), static_cast<int>(advCount), startFrac);
    const jsize outLen = static_cast<jsize>(pl.size() * 5);
    jfloatArray out = env->NewFloatArray(outLen);
    if (out && outLen > 0) {
        std::vector<jfloat> packed(static_cast<size_t>(outLen));
        for (size_t i = 0; i < pl.size(); ++i) {
            packed[5 * i + 0] = pl[i].startDist;
            packed[5 * i + 1] = pl[i].x;
            packed[5 * i + 2] = pl[i].y;
            packed[5 * i + 3] = pl[i].angleDeg;
            packed[5 * i + 4] = pl[i].drawn ? 1.0f : 0.0f;
        }
        env->SetFloatArrayRegion(out, 0, outLen, packed.data());
    }
    return out;
}

JNIEXPORT jfloatArray JNICALL Java_com_example_engine_SceneMathNative_nativeFlattenMaskControlPoints(
    JNIEnv* env, jclass, jfloatArray ctrlXy, jboolean smooth, jint maxPoints) {
    if (!ctrlXy) return env->NewFloatArray(0);
    const int count = static_cast<int>(env->GetArrayLength(ctrlXy) / 2);
    if (count <= 0) return env->NewFloatArray(0);
    std::vector<jfloat> vCtrl(static_cast<size_t>(count) * 2);
    env->GetFloatArrayRegion(ctrlXy, 0, count * 2, vCtrl.data());
    const std::vector<float> flat = flattenMaskControlPoints(
        vCtrl.data(), count, smooth == JNI_TRUE, maxPoints);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(flat.size()));
    if (out && !flat.empty()) {
        env->SetFloatArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    }
    return out;
}

JNIEXPORT jboolean JNICALL Java_com_example_engine_SceneMathNative_nativePointInPolygon(
    JNIEnv* env, jclass, jfloat px, jfloat py, jfloatArray polyXy) {
    if (!polyXy) return JNI_FALSE;
    const int count = static_cast<int>(env->GetArrayLength(polyXy) / 2);
    if (count < 3) return JNI_FALSE;
    jfloat* ptr = env->GetFloatArrayElements(polyXy, nullptr);
    if (!ptr) return JNI_FALSE;
    const bool inside = pointInPolygon(px, py, ptr, count);
    env->ReleaseFloatArrayElements(polyXy, ptr, JNI_ABORT);
    return inside ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
