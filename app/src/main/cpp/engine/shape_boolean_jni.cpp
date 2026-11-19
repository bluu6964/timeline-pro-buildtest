// JNI bridge between com.example.engine.ShapeBooleanNative (Kotlin) and am::shape_bool.
#include <jni.h>

#include <vector>

#include "shape_boolean.h"

using namespace am::shape_bool;

namespace {

Region unpackRegion(JNIEnv* env, jintArray ringSizes, jdoubleArray coords) {
    Region reg;
    if (!ringSizes || !coords) return reg;
    const jsize rc = env->GetArrayLength(ringSizes);
    const jsize cl = env->GetArrayLength(coords);
    if (rc <= 0 || cl <= 0) return reg;

    std::vector<jint> sizes(static_cast<size_t>(rc));
    std::vector<jdouble> xy(static_cast<size_t>(cl));
    env->GetIntArrayRegion(ringSizes, 0, rc, sizes.data());
    env->GetDoubleArrayRegion(coords, 0, cl, xy.data());

    reg.reserve(static_cast<size_t>(rc));
    size_t pos = 0;
    for (jint sz : sizes) {
        if (sz <= 0) {
            reg.emplace_back();
            continue;
        }
        const size_t need = static_cast<size_t>(sz) * 2;
        if (pos + need > xy.size()) break;
        Ring ring;
        ring.reserve(static_cast<size_t>(sz));
        for (int i = 0; i < sz; ++i) {
            ring.push_back({xy[pos + 2 * i], xy[pos + 2 * i + 1]});
        }
        pos += need;
        reg.push_back(std::move(ring));
    }
    return reg;
}

// Encodes Region as double[]: [ringCount, size_0, ..., size_{k-1}, x0, y0, ...]
jdoubleArray packRegion(JNIEnv* env, const Region& reg) {
    size_t totalPts = 0;
    for (const auto& r : reg) totalPts += r.size();
    const size_t totalLen = 1 + reg.size() + totalPts * 2;
    std::vector<jdouble> packed;
    packed.reserve(totalLen);
    packed.push_back(static_cast<jdouble>(reg.size()));
    for (const auto& r : reg) packed.push_back(static_cast<jdouble>(r.size()));
    for (const auto& r : reg) {
        for (const auto& p : r) {
            packed.push_back(p.x);
            packed.push_back(p.y);
        }
    }
    jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(packed.size()));
    if (out && !packed.empty()) {
        env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    }
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_ShapeBooleanNative_nativeCombine(
    JNIEnv* env, jclass,
    jintArray sizesA, jdoubleArray coordsA,
    jintArray sizesB, jdoubleArray coordsB,
    jint op) {
    const Region ra = unpackRegion(env, sizesA, coordsA);
    const Region rb = unpackRegion(env, sizesB, coordsB);
    const BoolOp bop = (op >= 0 && op <= 3) ? static_cast<BoolOp>(op) : kUnion;
    const Region res = combine(ra, rb, bop);
    return packRegion(env, res);
}

JNIEXPORT jint JNICALL Java_com_example_engine_ShapeBooleanNative_nativeWindingNumber(
    JNIEnv* env, jclass, jdouble px, jdouble py, jintArray sizes, jdoubleArray coords) {
    const Region reg = unpackRegion(env, sizes, coords);
    return windingNumber({px, py}, reg);
}

JNIEXPORT jdoubleArray JNICALL Java_com_example_engine_ShapeBooleanNative_nativeFlattenPathRings(
    JNIEnv* env, jclass, jintArray ops, jintArray offsets, jintArray counts,
    jfloatArray pts, jint stepsPerCurve) {
    if (!ops || !offsets || !counts) return packRegion(env, {});
    const jsize segCount = env->GetArrayLength(ops);
    if (segCount <= 0 || env->GetArrayLength(offsets) < segCount ||
        env->GetArrayLength(counts) < segCount) {
        return packRegion(env, {});
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
    const Region reg = flattenPathRings(
        vOps.data(), vOff.data(), vCnt.data(), static_cast<int>(segCount),
        vPts.empty() ? nullptr : vPts.data(), static_cast<int>(vPts.size()), stepsPerCurve);
    return packRegion(env, reg);
}

}  // extern "C"
