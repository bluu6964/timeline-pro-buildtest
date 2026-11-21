// JNI for value_laws (migration stage 4, build 247). Bridge:
// engine/ValueLawsNative.kt. Plain values and flat arrays cross; the string
// laws (markers trim, FxLists, FxTileArt) run over the string's UTF-16 code
// units — exactly Kotlin's Char sequence — so trim / hashing semantics are
// the JVM's own.

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "value_laws.h"

namespace {

using namespace am::laws;

jfloatArray toFloats(JNIEnv* env, const std::vector<float>& v) {
    jfloatArray a = env->NewFloatArray((jsize)v.size());
    if (!v.empty()) env->SetFloatArrayRegion(a, 0, (jsize)v.size(), v.data());
    return a;
}
jintArray toInts(JNIEnv* env, const std::vector<int32_t>& v) {
    jintArray a = env->NewIntArray((jsize)v.size());
    if (!v.empty()) env->SetIntArrayRegion(a, 0, (jsize)v.size(), v.data());
    return a;
}
std::vector<float> getFloats(JNIEnv* env, jfloatArray a) {
    jsize n = env->GetArrayLength(a);
    std::vector<float> v((size_t)n);
    if (n > 0) env->GetFloatArrayRegion(a, 0, n, v.data());
    return v;
}
std::vector<int32_t> getInts(JNIEnv* env, jintArray a) {
    jsize n = env->GetArrayLength(a);
    std::vector<int32_t> v((size_t)n);
    if (n > 0) env->GetIntArrayRegion(a, 0, n, v.data());
    return v;
}
std::vector<int64_t> getLongs(JNIEnv* env, jlongArray a) {
    jsize n = env->GetArrayLength(a);
    std::vector<int64_t> v((size_t)n);
    if (n > 0) env->GetLongArrayRegion(a, 0, n, (jlong*)v.data());
    return v;
}
std::u16string getU16(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    jsize n = env->GetStringLength(s);
    std::u16string out((size_t)n, 0);
    if (n > 0) env->GetStringRegion(s, 0, n, (jchar*)out.data());
    return out;
}
jstring toU16(JNIEnv* env, const std::u16string& s) {
    return env->NewString((const jchar*)s.data(), (jsize)s.size());
}
// Kotlin Char.isWhitespace == Character.isWhitespace || Character.isSpaceChar.
bool isWhitespace16(char16_t c) {
    if (c >= 0x0009 && c <= 0x000D) return true;
    if (c >= 0x001C && c <= 0x001F) return true;
    if (c == 0x0020 || c == 0x00A0 || c == 0x1680) return true;
    if (c >= 0x2000 && c <= 0x200A) return true;
    if (c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000) return true;
    return false;
}
void trimRange16(const std::u16string& s, size_t begin, size_t end, size_t* outBegin, size_t* outEnd) {
    size_t b = begin, e = end;
    while (b < e && isWhitespace16(s[b])) b++;
    while (e > b && isWhitespace16(s[e - 1])) e--;
    *outBegin = b;
    *outEnd = e;
}
std::vector<std::u16string> splitComma(const std::u16string& s) {
    std::vector<std::u16string> out;
    if (s.empty()) return out;
    size_t start = 0;
    while (true) {
        size_t comma = s.find(u',', start);
        if (comma == std::u16string::npos) {
            out.push_back(s.substr(start));
            break;
        }
        out.push_back(s.substr(start, comma - start));
        start = comma + 1;
    }
    return out;
}
std::u16string joinComma(const std::vector<std::u16string>& parts) {
    std::u16string out;
    for (size_t i = 0; i < parts.size(); i++) {
        if (i > 0) out += u',';
        out += parts[i];
    }
    return out;
}

// Anchor blob codec: path = [nSubs, (nAnchors, closed, 9 floats per anchor)*];
// pen list = [nAnchors, 9 floats per anchor]. Per anchor:
// [x, y, inX, inY, outX, outY, hasIn, hasOut, smooth].
void putAnchor(std::vector<float>& blob, const AnchorRec& a) {
    blob.push_back(a.x);
    blob.push_back(a.y);
    blob.push_back(a.inX);
    blob.push_back(a.inY);
    blob.push_back(a.outX);
    blob.push_back(a.outY);
    blob.push_back(a.hasIn ? 1.0f : 0.0f);
    blob.push_back(a.hasOut ? 1.0f : 0.0f);
    blob.push_back(a.smooth ? 1.0f : 0.0f);
}
AnchorRec getAnchor(const float* p) {
    return AnchorRec{p[0], p[1], p[2], p[3], p[4], p[5], p[6] != 0.0f, p[7] != 0.0f, p[8] != 0.0f};
}
std::vector<float> pathToBlob(const PathRec& path) {
    std::vector<float> blob;
    blob.push_back((float)path.size());
    for (const SubpathRec& sp : path) {
        blob.push_back((float)sp.anchors.size());
        blob.push_back(sp.closed ? 1.0f : 0.0f);
        for (const AnchorRec& a : sp.anchors) putAnchor(blob, a);
    }
    return blob;
}
PathRec blobToPath(const std::vector<float>& blob) {
    PathRec path;
    size_t pos = 0;
    if (blob.empty()) return path;
    int nSubs = (int)blob[pos++];
    for (int s = 0; s < nSubs && pos < blob.size(); s++) {
        SubpathRec sp;
        int nA = (int)blob[pos++];
        sp.closed = blob[pos++] != 0.0f;
        for (int i = 0; i < nA && pos + 9 <= blob.size(); i++) {
            sp.anchors.push_back(getAnchor(blob.data() + pos));
            pos += 9;
        }
        path.push_back(std::move(sp));
    }
    return path;
}
std::vector<float> penToBlob(const std::vector<AnchorRec>& anchors) {
    std::vector<float> blob;
    blob.push_back((float)anchors.size());
    for (const AnchorRec& a : anchors) putAnchor(blob, a);
    return blob;
}
std::vector<AnchorRec> blobToPen(const std::vector<float>& blob) {
    std::vector<AnchorRec> out;
    if (blob.empty()) return out;
    int n = (int)blob[0];
    size_t pos = 1;
    for (int i = 0; i < n && pos + 9 <= blob.size(); i++) {
        out.push_back(getAnchor(blob.data() + pos));
        pos += 9;
    }
    return out;
}
MaskFrameP frameFrom(JNIEnv* env, jfloatArray a) {
    std::vector<float> v = getFloats(env, a);
    MaskFrameP f{v[0], v[1], v[2], v[3], v[4], v[5], v[6]};
    return f;
}
MaskShapeP shapeFrom(jint shape, jfloat corner, jint sides, jfloat inner,
                     const std::vector<float>& points, jboolean smooth) {
    MaskShapeP m;
    m.shape = shape;
    m.corner = corner;
    m.sides = sides;
    m.inner = inner;
    m.points = points.data();
    m.nPoints = (int)(points.size() / 2);
    m.smooth = smooth == JNI_TRUE;
    return m;
}
jfloatArray normalizeBlob(JNIEnv* env, const NormalizeResult& r) {
    std::vector<float> blob;
    blob.push_back(r.ok ? 1.0f : 0.0f);
    blob.push_back(r.offsetX);
    blob.push_back(r.offsetY);
    blob.push_back(r.shapeScale);
    blob.push_back((float)r.segStream.size());
    for (float v : r.segStream) blob.push_back(v);
    blob.push_back((float)(r.flatFrame.size() / 2));
    for (float v : r.flatFrame) blob.push_back(v);
    return toFloats(env, blob);
}

}  // namespace

extern "C" {

// ---- Ducking ----
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckFloorGain(JNIEnv*, jclass, jfloat a) {
    return duckFloorGain(a);
}
JNIEXPORT jdouble JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckAttackMs(JNIEnv*, jclass, jfloat s) {
    return duckAttackMs(s);
}
JNIEXPORT jdouble JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckReleaseMs(JNIEnv*, jclass, jfloat s) {
    return duckReleaseMs(s);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckTargetGain(JNIEnv*, jclass, jfloat l, jfloat f) {
    return duckTargetGain(l, f);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckTimelineLevels(
    JNIEnv* env, jclass, jfloatArray peaks, jlong durationMs, jfloat speed, jfloatArray heard) {
    std::vector<float> p = getFloats(env, peaks);
    std::vector<float> h = getFloats(env, heard);
    return toFloats(env, duckTimelineLevels(p.data(), (int)p.size(), durationMs, speed, h.data(), (int)h.size()));
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeDuckGainAt(
    JNIEnv* env, jclass, jlongArray times, jfloatArray gains, jlong relMs) {
    std::vector<int64_t> t = getLongs(env, times);
    std::vector<float> g = getFloats(env, gains);
    return duckGainAt(t.data(), g.data(), (int)t.size(), relMs);
}

// ---- NoiseReduction ----
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseOverSubtraction(JNIEnv*, jclass, jfloat s) {
    return noiseOverSubtraction(s);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseRawGain(JNIEnv*, jclass, jfloat m, jfloat n, jfloat s) {
    return noiseRawGain(m, n, s);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseSmoothFreq(JNIEnv* env, jclass, jfloatArray raw) {
    std::vector<float> v = getFloats(env, raw);
    return toFloats(env, noiseSmoothFreq(v.data(), (int)v.size()));
}
JNIEXPORT jdouble JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseStepCoef(JNIEnv*, jclass, jdouble tau) {
    return noiseStepCoef(tau);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeNoisePercentile(JNIEnv* env, jclass, jfloatArray values, jdouble p) {
    std::vector<float> v = getFloats(env, values);
    return noisePercentile(v.data(), (int)v.size(), p);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseProfile(
    JNIEnv* env, jclass, jfloatArray frames, jint nFrames, jint bins) {
    std::vector<float> v = getFloats(env, frames);
    return toFloats(env, noiseProfile(v.data(), nFrames, bins));
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseBucketOf(JNIEnv*, jclass, jfloat m) {
    return noiseBucketOf(m);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseBucketValue(JNIEnv*, jclass, jint b) {
    return noiseBucketValue(b);
}
JNIEXPORT void JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseAccumAdd(
    JNIEnv* env, jclass, jintArray counts, jfloatArray mags) {
    std::vector<float> m = getFloats(env, mags);
    jsize n = env->GetArrayLength(counts);
    std::vector<int32_t> c((size_t)n);
    env->GetIntArrayRegion(counts, 0, n, c.data());
    noiseAccumAdd(c.data(), m.data(), (int)m.size());
    env->SetIntArrayRegion(counts, 0, n, c.data());
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseAccumProfile(
    JNIEnv* env, jclass, jintArray counts, jint frames, jint bins) {
    std::vector<int32_t> c = getInts(env, counts);
    return toFloats(env, noiseAccumProfile(c.data(), frames, bins));
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseHann(JNIEnv*, jclass, jint i) {
    return noiseHann(i);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeNoiseWindow(JNIEnv* env, jclass) {
    return toFloats(env, noiseWindow());
}

// ---- SpatialAlign ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeSpatialAlignDeltas(
    JNIEnv* env, jclass, jfloatArray rects, jint action) {
    std::vector<float> r = getFloats(env, rects);
    return toFloats(env, spatialAlignDeltas(r.data(), (int)(r.size() / 4), action));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeSpatialDistributeDeltas(
    JNIEnv* env, jclass, jfloatArray rects, jboolean horizontal) {
    std::vector<float> r = getFloats(env, rects);
    return toFloats(env, spatialDistributeDeltas(r.data(), (int)(r.size() / 4), horizontal == JNI_TRUE));
}

// ---- ProjectMarkers ----
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMarkerNormalize(JNIEnv* env, jclass, jlongArray times) {
    std::vector<int64_t> t = getLongs(env, times);
    return toInts(env, markerNormalize(t.data(), (int)t.size()).perm);
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeMarkerNearest(
    JNIEnv* env, jclass, jlongArray times, jlong timeMs, jlong tolMs) {
    std::vector<int64_t> t = getLongs(env, times);
    return markerNearest(t.data(), (int)t.size(), timeMs, tolMs);
}
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeTrimRange(JNIEnv* env, jclass, jstring s) {
    std::u16string u = getU16(env, s);
    size_t b, e;
    trimRange16(u, 0, u.size(), &b, &e);
    std::vector<int32_t> out{(int32_t)b, (int32_t)e};
    return toInts(env, out);
}

// ---- OnionSkin ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeOnionNormalize(
    JNIEnv* env, jclass, jint count, jfloat opacity) {
    int32_t c;
    float o;
    onionNormalize(count, opacity, &c, &o);
    std::vector<float> out{(float)c, o};
    return toFloats(env, out);
}
JNIEXPORT jlong JNICALL Java_com_example_engine_ValueLawsNative_nativeOnionFrameStepMs(JNIEnv*, jclass, jint fps, jint step) {
    return onionFrameStepMs(fps, step);
}
JNIEXPORT jbyteArray JNICALL Java_com_example_engine_ValueLawsNative_nativeOnionPlan(
    JNIEnv* env, jclass, jlong head, jint fps, jlong dur, jint count, jfloat op) {
    std::vector<OnionGhost> plan = onionPlan(head, fps, dur, count, op);
    // Blob: [count i32] then per ghost [timeMs i64, side i32, step i32, alpha f32, hue f32].
    jbyteArray a = env->NewByteArray((jsize)(4 + plan.size() * 24));
    std::vector<uint8_t> buf(4 + plan.size() * 24);
    auto put32 = [&](size_t off, uint32_t v) { std::memcpy(buf.data() + off, &v, 4); };
    auto put64 = [&](size_t off, uint64_t v) { std::memcpy(buf.data() + off, &v, 8); };
    put32(0, (uint32_t)plan.size());
    for (size_t i = 0; i < plan.size(); i++) {
        size_t o = 4 + i * 24;
        put64(o, (uint64_t)plan[i].timeMs);
        put32(o + 8, (uint32_t)plan[i].side);
        put32(o + 12, (uint32_t)plan[i].step);
        uint32_t ab, hb;
        std::memcpy(&ab, &plan[i].alpha, 4);
        std::memcpy(&hb, &plan[i].hueDeg, 4);
        put32(o + 16, ab);
        put32(o + 20, hb);
    }
    env->SetByteArrayRegion(a, 0, (jsize)buf.size(), (const jbyte*)buf.data());
    return a;
}

// ---- CropBounds ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeCropVisibleBox(
    JNIEnv* env, jclass, jfloat cw, jfloat ch, jfloat chp, jfloat cvp, jfloat bw, jfloat bh) {
    float out[4];
    if (!cropVisibleBox(cw, ch, chp, cvp, bw, bh, out)) return toFloats(env, {});
    return toFloats(env, std::vector<float>(out, out + 4));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeCropVisibleRect(
    JNIEnv* env, jclass, jfloat cw, jfloat ch, jfloat chp, jfloat cvp, jfloat bw, jfloat bh) {
    float out[4];
    if (!cropVisibleRect(cw, ch, chp, cvp, bw, bh, out)) return toFloats(env, {});
    return toFloats(env, std::vector<float>(out, out + 4));
}

// ---- GifExport ----
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeGifDelayCs(JNIEnv*, jclass, jint fps) {
    return gifDelayCs(fps);
}
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeGifSize(JNIEnv* env, jclass, jint w, jint h) {
    int32_t out[2];
    gifSize(w, h, out);
    return toInts(env, std::vector<int32_t>(out, out + 2));
}

// ---- VectorDraw / PathEdit / PenPath ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeVectorBuild(
    JNIEnv* env, jclass, jfloatArray rawXy, jfloat aspect, jboolean smooth, jboolean close) {
    std::vector<float> v = getFloats(env, rawXy);
    return toFloats(env, vectorBuildStream(v.data(), (int)(v.size() / 2), aspect, smooth == JNI_TRUE, close == JNI_TRUE));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeVectorNormalize(
    JNIEnv* env, jclass, jintArray ops, jintArray counts, jfloatArray pts, jfloat cw, jfloat ch, jfloat density) {
    std::vector<int32_t> o = getInts(env, ops);
    std::vector<int32_t> c = getInts(env, counts);
    std::vector<float> p = getFloats(env, pts);
    SegPack pack{o.data(), c.data(), p.data(), (int)o.size()};
    return normalizeBlob(env, vectorNormalize(pack, cw, ch, density));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePenBuild(
    JNIEnv* env, jclass, jfloatArray blob, jboolean closed, jfloat cw, jfloat ch, jfloat density) {
    std::vector<AnchorRec> anchors = blobToPen(getFloats(env, blob));
    return normalizeBlob(env, penBuild(anchors, closed == JNI_TRUE, cw, ch, density));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathFromSegments(
    JNIEnv* env, jclass, jintArray ops, jintArray counts, jfloatArray pts) {
    std::vector<int32_t> o = getInts(env, ops);
    std::vector<int32_t> c = getInts(env, counts);
    std::vector<float> p = getFloats(env, pts);
    SegPack pack{o.data(), c.data(), p.data(), (int)o.size()};
    return toFloats(env, pathToBlob(pathFromSegments(pack)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathToSegments(JNIEnv* env, jclass, jfloatArray blob) {
    return toFloats(env, pathToSegments(blobToPath(getFloats(env, blob))));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathMoveAnchor(
    JNIEnv* env, jclass, jfloatArray blob, jint sub, jint idx, jfloat x, jfloat y) {
    return toFloats(env, pathToBlob(pathMoveAnchor(blobToPath(getFloats(env, blob)), sub, idx, x, y)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathMoveHandle(
    JNIEnv* env, jclass, jfloatArray blob, jint sub, jint idx, jboolean out, jfloat x, jfloat y) {
    return toFloats(env, pathToBlob(pathMoveHandle(blobToPath(getFloats(env, blob)), sub, idx, out == JNI_TRUE, x, y)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathSplitSegment(
    JNIEnv* env, jclass, jfloatArray blob, jint sub, jint seg, jfloat t) {
    int32_t inserted;
    PathRec r = pathSplitSegment(blobToPath(getFloats(env, blob)), sub, seg, t, &inserted);
    std::vector<float> outBlob;
    outBlob.push_back((float)inserted);
    std::vector<float> body = pathToBlob(r);
    for (float v : body) outBlob.push_back(v);
    return toFloats(env, outBlob);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePathDeleteAnchor(
    JNIEnv* env, jclass, jfloatArray blob, jint sub, jint idx) {
    return toFloats(env, pathToBlob(pathDeleteAnchor(blobToPath(getFloats(env, blob)), sub, idx)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePenTap(
    JNIEnv* env, jclass, jfloatArray blob, jfloat x, jfloat y) {
    return toFloats(env, penToBlob(penTap(blobToPen(getFloats(env, blob)), x, y)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePenPull(
    JNIEnv* env, jclass, jfloatArray blob, jfloat x, jfloat y) {
    return toFloats(env, penToBlob(penPull(blobToPen(getFloats(env, blob)), x, y)));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePenReleaseTap(JNIEnv* env, jclass, jfloatArray blob) {
    return toFloats(env, penToBlob(penReleaseTap(blobToPen(getFloats(env, blob)))));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativePenUndoPoint(JNIEnv* env, jclass, jfloatArray blob) {
    return toFloats(env, penToBlob(penUndoPoint(blobToPen(getFloats(env, blob)))));
}

// ---- MaskGeometry ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskFrameInit(
    JNIEnv* env, jclass, jfloat sCx, jfloat sCy, jfloat sW, jfloat sH, jfloat sRot, jfloat aspect) {
    MaskFrameP f = maskFrameInit(sCx, sCy, sW, sH, sRot, aspect);
    return toFloats(env, {f.cx, f.cy, f.hw, f.hh, f.cosR, f.sinR, f.aspect});
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskToPicture(
    JNIEnv* env, jclass, jfloatArray frame, jfloat lx, jfloat ly) {
    float out[2];
    maskToPicture(frameFrom(env, frame), lx, ly, out);
    return toFloats(env, std::vector<float>(out, out + 2));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskToLocal(
    JNIEnv* env, jclass, jfloatArray frame, jfloat px, jfloat py) {
    float out[2];
    maskToLocal(frameFrom(env, frame), px, py, out);
    return toFloats(env, std::vector<float>(out, out + 2));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskHandles(
    JNIEnv* env, jclass, jfloatArray frame, jint shape, jfloat corner, jint sides, jfloat inner, jfloatArray points) {
    std::vector<float> pts = getFloats(env, points);
    MaskShapeP m = shapeFrom(shape, corner, sides, inner, pts, JNI_FALSE);
    return toFloats(env, maskHandles(frameFrom(env, frame), m));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskOutline(
    JNIEnv* env, jclass, jfloatArray frame, jint shape, jfloat corner, jint sides, jfloat inner,
    jfloatArray points, jboolean smooth) {
    std::vector<float> pts = getFloats(env, points);
    MaskShapeP m = shapeFrom(shape, corner, sides, inner, pts, smooth);
    return toFloats(env, maskOutline(frameFrom(env, frame), m));
}
JNIEXPORT jboolean JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskContains(
    JNIEnv* env, jclass, jfloatArray frame, jint shape, jfloat corner, jint sides, jfloat inner,
    jfloatArray points, jboolean smooth, jfloat px, jfloat py, jfloat lineTol) {
    std::vector<float> pts = getFloats(env, points);
    MaskShapeP m = shapeFrom(shape, corner, sides, inner, pts, smooth);
    return maskContains(frameFrom(env, frame), m, px, py, lineTol) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskDragCorner(
    JNIEnv* env, jclass, jfloatArray frame, jint i, jfloat px, jfloat py) {
    float out[4];
    maskDragCorner(frameFrom(env, frame), i, px, py, out);
    return toFloats(env, std::vector<float>(out, out + 4));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskDragEdge(
    JNIEnv* env, jclass, jfloatArray frame, jint shape, jfloatArray state, jint i, jfloat px, jfloat py) {
    std::vector<float> st = getFloats(env, state);
    MaskShapeP m = shapeFrom(shape, 0.0f, 0, 0.0f, {}, JNI_FALSE);
    float out[4];
    maskDragEdge(frameFrom(env, frame), m, st.data(), i, px, py, out);
    return toFloats(env, std::vector<float>(out, out + 4));
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskRotateTo(
    JNIEnv* env, jclass, jfloatArray frame, jfloat px, jfloat py) {
    return maskRotateTo(frameFrom(env, frame), px, py);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskMovedBy(
    JNIEnv* env, jclass, jfloat startCx, jfloat startCy, jfloat fromX, jfloat fromY,
    jfloat toX, jfloat toY, jfloat aspect) {
    float out[2];
    maskMovedBy(startCx, startCy, fromX, fromY, toX, toY, aspect, out);
    return toFloats(env, std::vector<float>(out, out + 2));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskDragPoint(
    JNIEnv* env, jclass, jfloatArray frame, jfloatArray points, jint i, jfloat px, jfloat py) {
    std::vector<float> pts = getFloats(env, points);
    std::vector<float> outPts;
    if (!maskDragPoint(frameFrom(env, frame), pts.data(), (int)(pts.size() / 2), i, px, py, &outPts)) {
        return toFloats(env, {});
    }
    return toFloats(env, outPts);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskInsertPointNear(
    JNIEnv* env, jclass, jfloatArray frame, jfloatArray points, jfloat px, jfloat py, jfloat tol) {
    std::vector<float> pts = getFloats(env, points);
    std::vector<float> outPts;
    int32_t idx;
    if (!maskInsertPointNear(frameFrom(env, frame), pts.data(), (int)(pts.size() / 2), px, py, tol, &outPts, &idx)) {
        return toFloats(env, {});
    }
    std::vector<float> blob{(float)idx};
    for (float v : outPts) blob.push_back(v);
    return toFloats(env, blob);
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskHitHandle(
    JNIEnv* env, jclass, jfloatArray frame, jint shape, jfloat corner, jint sides, jfloat inner,
    jfloatArray points, jfloat px, jfloat py, jfloat radius) {
    std::vector<float> pts = getFloats(env, points);
    MaskShapeP m = shapeFrom(shape, corner, sides, inner, pts, JNI_FALSE);
    return maskHitHandle(frameFrom(env, frame), m, px, py, radius);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeDistToSegment(
    JNIEnv*, jclass, jfloat px, jfloat py, jfloat ax, jfloat ay, jfloat bx, jfloat by) {
    return distToSegment(px, py, ax, ay, bx, by);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeLayerMapOf(
    JNIEnv* env, jclass, jfloat cw, jfloat ch, jfloat chp, jfloat cvp) {
    float out[4];
    layerMapOf(cw, ch, chp, cvp, out);
    return toFloats(env, std::vector<float>(out, out + 4));
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeLayerMapApply(
    JNIEnv* env, jclass, jfloatArray map, jboolean toBox, jfloat a, jfloat b) {
    std::vector<float> m = getFloats(env, map);
    float out[2];
    layerMapApply(m.data(), toBox == JNI_TRUE, a, b, out);
    return toFloats(env, std::vector<float>(out, out + 2));
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeMaskNeighbourKey(
    JNIEnv* env, jclass, jlongArray times, jlong relMs, jboolean forward, jlong tolMs) {
    std::vector<int64_t> t = getLongs(env, times);
    return maskNeighbourKey(t.data(), (int)t.size(), relMs, forward == JNI_TRUE, tolMs);
}

// ---- TextOnPath ----
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeArcTableBuild(JNIEnv* env, jclass, jfloatArray xy) {
    std::vector<float> v = getFloats(env, xy);
    ArcTable t = arcTableBuild(v.data(), (int)(v.size() / 2));
    std::vector<float> blob;
    blob.push_back(t.total);
    blob.push_back((float)(t.cum.size()));
    for (float f : t.xy) blob.push_back(f);
    for (float f : t.cum) blob.push_back(f);
    return toFloats(env, blob);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeArcPointAt(
    JNIEnv* env, jclass, jfloatArray xy, jfloatArray cum, jfloat total, jfloat d) {
    std::vector<float> x = getFloats(env, xy);
    std::vector<float> c = getFloats(env, cum);
    float out[3];
    arcPointAt(x.data(), c.data(), (int)c.size(), total, d, out);
    return toFloats(env, std::vector<float>(out, out + 3));
}

// ---- ClipClipboard ----
JNIEXPORT jlong JNICALL Java_com_example_engine_ValueLawsNative_nativeClipboardEarliest(JNIEnv* env, jclass, jlongArray times) {
    std::vector<int64_t> t = getLongs(env, times);
    return clipboardEarliest(t.data(), (int)t.size());
}
JNIEXPORT jlong JNICALL Java_com_example_engine_ValueLawsNative_nativeClipboardPasteShift(
    JNIEnv*, jclass, jlong earliest, jlong anchor, jlong duration) {
    return clipboardPasteShift(earliest, anchor, duration);
}
JNIEXPORT jbyteArray JNICALL Java_com_example_engine_ValueLawsNative_nativeClipboardMerge(
    JNIEnv* env, jclass, jlongArray tTimes, jfloatArray tVals, jintArray tTags,
    jlongArray cTimes, jfloatArray cVals, jintArray cTags, jlong shift) {
    KeysPack target{getLongs(env, tTimes), getFloats(env, tVals), getInts(env, tTags)};
    KeysPack copied{getLongs(env, cTimes), getFloats(env, cVals), getInts(env, cTags)};
    KeysPack merged = clipboardMergeProp(target, copied, shift);
    // Blob: [n i32][times i64*n][vals f32*6n][tags i32*n], little-endian (host + arm are LE).
    size_t n = merged.times.size();
    jbyteArray a = env->NewByteArray((jsize)(4 + n * 8 + n * 24 + n * 4));
    std::vector<uint8_t> buf(4 + n * 8 + n * 24 + n * 4);
    size_t o = 0;
    auto put = [&](const void* src, size_t len) { std::memcpy(buf.data() + o, src, len); o += len; };
    uint32_t nu = (uint32_t)n;
    put(&nu, 4);
    for (size_t i = 0; i < n; i++) put(&merged.times[i], 8);
    for (size_t i = 0; i < n * 6; i++) put(&merged.vals[i], 4);
    for (size_t i = 0; i < n; i++) put(&merged.tags[i], 4);
    env->SetByteArrayRegion(a, 0, (jsize)buf.size(), (const jbyte*)buf.data());
    return a;
}

// ---- BlendModes table ----
JNIEXPORT jobjectArray JNICALL Java_com_example_engine_ValueLawsNative_nativeBlendNames(JNIEnv* env, jclass) {
    jclass strCls = env->FindClass("java/lang/String");
    int n = blendSpecCount();
    jobjectArray a = env->NewObjectArray(n, strCls, nullptr);
    for (int i = 0; i < n; i++) env->SetObjectArrayElement(a, i, env->NewStringUTF(blendSpecName(i)));
    return a;
}
JNIEXPORT jobjectArray JNICALL Java_com_example_engine_ValueLawsNative_nativeBlendCategories(JNIEnv* env, jclass) {
    jclass strCls = env->FindClass("java/lang/String");
    int n = blendSpecCount();
    jobjectArray a = env->NewObjectArray(n, strCls, nullptr);
    for (int i = 0; i < n; i++) env->SetObjectArrayElement(a, i, env->NewStringUTF(blendSpecCategory(i)));
    return a;
}
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeBlendShaderIds(JNIEnv* env, jclass) {
    int n = blendSpecCount();
    std::vector<int32_t> v((size_t)n);
    for (int i = 0; i < n; i++) v[(size_t)i] = blendSpecShaderId(i);
    return toInts(env, v);
}
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeBlendKinds(JNIEnv* env, jclass) {
    int n = blendSpecCount();
    std::vector<int32_t> v((size_t)n);
    for (int i = 0; i < n; i++) v[(size_t)i] = blendSpecKind(i);
    return toInts(env, v);
}
JNIEXPORT jobjectArray JNICALL Java_com_example_engine_ValueLawsNative_nativeBlendCategoryList(JNIEnv* env, jclass) {
    jclass strCls = env->FindClass("java/lang/String");
    int n = blendCategoryCount();
    jobjectArray a = env->NewObjectArray(n, strCls, nullptr);
    for (int i = 0; i < n; i++) env->SetObjectArrayElement(a, i, env->NewStringUTF(blendCategoryName(i)));
    return a;
}

// ---- FxLists / FxTileArt (UTF-16 string laws) ----
JNIEXPORT jstring JNICALL Java_com_example_engine_ValueLawsNative_nativeFxParse(JNIEnv* env, jclass, jstring raw) {
    std::u16string u = getU16(env, raw);
    std::vector<std::u16string> parts = splitComma(u);
    std::vector<std::u16string> out;
    for (const std::u16string& part : parts) {
        size_t b, e;
        trimRange16(part, 0, part.size(), &b, &e);
        if (e <= b) continue;
        std::u16string id = part.substr(b, e - b);
        bool dup = false;
        for (const std::u16string& kept : out) {
            if (kept == id) { dup = true; break; }
        }
        if (!dup) out.push_back(id);
    }
    return toU16(env, joinComma(out));
}
JNIEXPORT jstring JNICALL Java_com_example_engine_ValueLawsNative_nativeFxToggle(JNIEnv* env, jclass, jstring joined, jstring id) {
    std::vector<std::u16string> parts = splitComma(getU16(env, joined));
    std::u16string target = getU16(env, id);
    bool present = false;
    for (const std::u16string& p : parts) {
        if (p == target) { present = true; break; }
    }
    std::vector<std::u16string> out;
    if (present) {
        // Kotlin List.minus(element) removes only the FIRST occurrence.
        bool dropped = false;
        for (const std::u16string& p : parts) {
            if (!dropped && p == target) {
                dropped = true;
                continue;
            }
            out.push_back(p);
        }
    } else {
        out = parts;
        out.push_back(target);
    }
    return toU16(env, joinComma(out));
}
JNIEXPORT jstring JNICALL Java_com_example_engine_ValueLawsNative_nativeFxPushRecent(
    JNIEnv* env, jclass, jstring joined, jstring id, jint cap) {
    std::vector<std::u16string> parts = splitComma(getU16(env, joined));
    std::u16string target = getU16(env, id);
    std::vector<std::u16string> out;
    out.push_back(target);
    for (const std::u16string& p : parts) {
        if (p != target) out.push_back(p);
    }
    int32_t c = cap < 1 ? 1 : cap;
    if ((int32_t)out.size() > c) out.resize((size_t)c);
    return toU16(env, joinComma(out));
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeFxSeed(JNIEnv* env, jclass, jstring id) {
    std::u16string u = getU16(env, id);
    uint32_t h = 0x811C9DC5u;
    for (char16_t ch : u) {
        h = h ^ (uint32_t)(uint16_t)ch;
        h = h * 0x01000193u;
    }
    return (jint)(int32_t)h;
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeFxUnit(JNIEnv*, jclass, jint seed, jint salt) {
    uint32_t x = (uint32_t)seed ^ ((uint32_t)salt * 0x9E3779B9u);  // -1640531527 as u32
    x = x ^ (x << 13);
    x = x ^ (x >> 17);
    x = x ^ (x << 5);
    return (jfloat)((float)(x & 0xFFFFFFu) / 16777216.0f);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeFxHueShift(JNIEnv* env, jclass, jstring id) {
    std::u16string u = getU16(env, id);
    uint32_t h = 0x811C9DC5u;
    for (char16_t ch : u) {
        h = h ^ (uint32_t)(uint16_t)ch;
        h = h * 0x01000193u;
    }
    uint32_t x = h ^ (77u * 0x9E3779B9u);
    x = x ^ (x << 13);
    x = x ^ (x >> 17);
    x = x ^ (x << 5);
    float unit = (float)(x & 0xFFFFFFu) / 16777216.0f;
    return unit * 48.0f - 24.0f;
}

// ---- ToolRowSplit ----
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeToolTileWidth(JNIEnv* env, jclass, jstring label) {
    return toolTileWidth(env->GetStringLength(label));
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeToolAvailableDp(JNIEnv*, jclass, jint w, jint back, jint pad) {
    return toolAvailableDp(w, back, pad);
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeToolVisibleCount(
    JNIEnv* env, jclass, jintArray widths, jint avail, jint gap) {
    std::vector<int32_t> v = getInts(env, widths);
    return toolVisibleCount(v.data(), (int)v.size(), avail, gap);
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeToolGridHeight(
    JNIEnv*, jclass, jint count, jint tileH, jint gap, jint vPad, jint maxRows, jint cols) {
    return toolGridHeight(count, tileH, gap, vPad, maxRows, cols);
}
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeToolSharedPanelHeight(JNIEnv*, jclass, jint overflow, jint utility) {
    return toolSharedPanelHeight(overflow, utility);
}

// ---- PagerGrid ----
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativePagerRowCounts(JNIEnv* env, jclass, jint count, jint columns) {
    return toInts(env, pagerRowCounts(count, columns));
}

// ---- UsedTools ----
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeUsedToolsMask(
    JNIEnv*, jclass, jint effectsCount, jboolean hasAnim, jboolean fillNotSolid, jlong textColor,
    jboolean hasKeys, jfloat tx, jfloat ty, jfloat rot, jfloat scale, jfloat r3x, jfloat r3y, jfloat tz,
    jfloat cropW, jfloat cropH, jboolean blendNotNormal, jfloat opacity,
    jint masksCount, jfloat volume, jboolean muted, jboolean hasCutout, jboolean hasParent) {
    return usedToolsMask(effectsCount, hasAnim == JNI_TRUE, fillNotSolid == JNI_TRUE, textColor,
                         hasKeys == JNI_TRUE, tx, ty, rot, scale, r3x, r3y, tz,
                         cropW, cropH, blendNotNormal == JNI_TRUE, opacity,
                         masksCount, volume, muted == JNI_TRUE, hasCutout == JNI_TRUE, hasParent == JNI_TRUE);
}

// ---- ShapeEditValues ----
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeShapeCoerceCorner(JNIEnv*, jclass, jfloat dp) {
    return shapeCoerceCorner(dp);
}
JNIEXPORT jfloatArray JNICALL Java_com_example_engine_ValueLawsNative_nativeShapeCoerceTrim(JNIEnv* env, jclass, jfloat s, jfloat e) {
    float out[2];
    shapeCoerceTrim(s, e, out);
    return toFloats(env, std::vector<float>(out, out + 2));
}

// ---- TextPaintRules ----
JNIEXPORT jintArray JNICALL Java_com_example_engine_ValueLawsNative_nativeTextBgArgb(JNIEnv* env, jclass, jlong color, jfloat opacity) {
    int32_t argb;
    if (!textBgArgb(color, opacity, &argb)) return toInts(env, {});
    return toInts(env, {argb});
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeTextLetterSpacingEm(JNIEnv*, jclass, jfloat p) {
    return textLetterSpacingEm(p);
}
JNIEXPORT jfloat JNICALL Java_com_example_engine_ValueLawsNative_nativeTextLetterSpacingPx(JNIEnv*, jclass, jfloat fontPx, jfloat p) {
    return textLetterSpacingPx(fontPx, p);
}

// ---- CanvasMark ----
JNIEXPORT jint JNICALL Java_com_example_engine_ValueLawsNative_nativeCanvasMark(JNIEnv*, jclass, jint type, jboolean selected) {
    return canvasMark(type, selected == JNI_TRUE);
}

}  // extern "C"
