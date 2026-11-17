// Build 224 — JNI bridge between com.example.engine.TimelineNative (Kotlin) and the native
// timeline renderer (timeline_render.cpp). Data only: Kotlin builds a snapshot through the
// begin/add/end calls, uploads the label atlas bitmap, and asks for frames; this file owns
// the ANativeWindow of the timeline's SurfaceView and the renderer's mutex.
#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <mutex>

#include "timeline_render.h"

namespace {

using namespace am::timeline;

struct TlHandle {
    std::mutex mu;
    Renderer renderer;
    Snapshot building;  // under construction between nativeBeginSnapshot / nativeEndSnapshot
    ANativeWindow* window = nullptr;
};

TlHandle* of(jlong p) { return reinterpret_cast<TlHandle*>(p); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_engine_TimelineNative_nativeCreate(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new TlHandle());
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeDestroy(JNIEnv*, jclass, jlong p) {
    TlHandle* h = of(p);
    if (!h) return;
    if (h->window) ANativeWindow_release(h->window);
    delete h;
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeSetWindow(JNIEnv* env, jclass,
                                                                               jlong p, jobject surface) {
    TlHandle* h = of(p);
    if (!h) return;
    std::lock_guard<std::mutex> lock(h->mu);
    if (h->window) {
        ANativeWindow_release(h->window);
        h->window = nullptr;
    }
    if (surface) h->window = ANativeWindow_fromSurface(env, surface);
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeClearWindow(JNIEnv*, jclass, jlong p) {
    TlHandle* h = of(p);
    if (!h) return;
    std::lock_guard<std::mutex> lock(h->mu);
    if (h->window) {
        ANativeWindow_release(h->window);
        h->window = nullptr;
    }
}

// The label atlas: the bitmap's pixels are copied (Kotlin keeps owning the bitmap), the entry
// rects / digit table describe where each label's ink sits inside it.
JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeSetAtlas(
    JNIEnv* env, jclass, jlong p, jobject bitmap, jintArray rects, jfloatArray bases,
    jintArray digits, jfloatArray digitAdv, jfloat digitBaseY) {
    TlHandle* h = of(p);
    if (!h || !bitmap || !rects) return;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width == 0 || info.height == 0) return;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels)
        return;
    Atlas at;
    at.w = (int32_t)info.width;
    at.h = (int32_t)info.height;
    at.rgba.resize((size_t)at.w * at.h * 4);
    const uint8_t* src = (const uint8_t*)pixels;
    for (int32_t y = 0; y < at.h; ++y)
        memcpy(at.rgba.data() + (size_t)y * at.w * 4, src + (size_t)y * info.stride, (size_t)at.w * 4);
    AndroidBitmap_unlockPixels(env, bitmap);

    const jsize nRect = env->GetArrayLength(rects);
    const jsize n = nRect / 4;
    at.entries.resize((size_t)n);
    jint* r = env->GetIntArrayElements(rects, nullptr);
    jfloat* b = bases ? env->GetFloatArrayElements(bases, nullptr) : nullptr;
    for (jsize i = 0; i < n; ++i) {
        at.entries[i].x = r[i * 4];
        at.entries[i].y = r[i * 4 + 1];
        at.entries[i].w = r[i * 4 + 2];
        at.entries[i].h = r[i * 4 + 3];
        at.entries[i].base = b ? b[i] : 0.f;
    }
    env->ReleaseIntArrayElements(rects, r, JNI_ABORT);
    if (b) env->ReleaseFloatArrayElements(bases, b, JNI_ABORT);
    if (digits && env->GetArrayLength(digits) >= 11) {
        jint dg[11];
        env->GetIntArrayRegion(digits, 0, 11, dg);
        for (int i = 0; i < 11; ++i) at.digit[i] = dg[i];
    }
    if (digitAdv && env->GetArrayLength(digitAdv) >= 11) {
        jfloat da[11];
        env->GetFloatArrayRegion(digitAdv, 0, 11, da);
        for (int i = 0; i < 11; ++i) at.digitAdv[i] = da[i];
    }
    at.digitBaseY = digitBaseY;
    std::lock_guard<std::mutex> lock(h->mu);
    h->renderer.setAtlas(std::move(at));
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeBeginSnapshot(
    JNIEnv*, jclass, jlong p, jfloat density, jfloat pxPerMs, jfloat rulerHPx, jfloat railWPx,
    jfloat topPadPx, jfloat gapPx, jfloat bottomPadPx, jlong totalMs, jint layerCount,
    jint pillLabel, jfloat pillTextWPx, jint totalLabel, jint chainLabel, jboolean comfort,
    jboolean multiMode, jboolean hasRange, jlong rangeInMs, jlong rangeOutMs) {
    TlHandle* h = of(p);
    if (!h) return;
    Snapshot s;
    s.density = density;
    s.pxPerMs = pxPerMs;
    s.rulerHPx = rulerHPx;
    s.railWPx = railWPx;
    s.topPadPx = topPadPx;
    s.gapPx = gapPx;
    s.bottomPadPx = bottomPadPx;
    s.totalMs = totalMs;
    s.layerCount = layerCount;
    s.pillLabel = pillLabel;
    s.pillTextWPx = pillTextWPx;
    s.totalLabel = totalLabel;
    s.chainLabel = chainLabel;
    s.comfort = comfort == JNI_TRUE;
    s.multiMode = multiMode == JNI_TRUE;
    s.hasRange = hasRange == JNI_TRUE;
    s.rangeInMs = rangeInMs;
    s.rangeOutMs = rangeOutMs;
    h->building = std::move(s);
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeSetBeats(
    JNIEnv* env, jclass, jlong p, jlongArray beatMs, jlongArray rulerKeysMs) {
    TlHandle* h = of(p);
    if (!h) return;
    if (beatMs) {
        const jsize n = env->GetArrayLength(beatMs);
        h->building.beatMs.resize((size_t)n);
        if (n > 0) env->GetLongArrayRegion(beatMs, 0, n,
                                           reinterpret_cast<jlong*>(h->building.beatMs.data()));
    }
    if (rulerKeysMs) {
        const jsize n = env->GetArrayLength(rulerKeysMs);
        h->building.rulerKeysMs.resize((size_t)n);
        if (n > 0)
            env->GetLongArrayRegion(rulerKeysMs, 0, n,
                                    reinterpret_cast<jlong*>(h->building.rulerKeysMs.data()));
    }
}

JNIEXPORT jint JNICALL Java_com_example_engine_TimelineNative_nativeAddTrack(
    JNIEnv*, jclass, jlong p, jfloat heightPx, jint chainClip) {
    TlHandle* h = of(p);
    if (!h) return -1;
    Track t;
    t.heightPx = heightPx;
    t.chainClip = chainClip;
    h->building.tracks.push_back(std::move(t));
    return (jint)h->building.tracks.size() - 1;
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeAddClip(
    JNIEnv* env, jclass, jlong p, jint trackIdx, jlong startMs, jlong durMs, jint kind,
    jint color, jint flags, jint label, jint chip, jint badge, jlong trimStartMs, jfloat speed,
    jfloat volume, jlong fadeInMs, jlong fadeOutMs, jlongArray keysRelMs, jfloatArray peaks) {
    TlHandle* h = of(p);
    if (!h || trackIdx < 0 || trackIdx >= (jint)h->building.tracks.size()) return;
    Clip c;
    c.startMs = startMs;
    c.durMs = durMs;
    c.kind = kind;
    c.color = (uint32_t)color;
    c.flags = (uint32_t)flags;
    c.label = label;
    c.chip = chip;
    c.badge = badge;
    c.trimStartMs = trimStartMs;
    c.speed = speed;
    c.volume = volume;
    c.fadeInMs = fadeInMs;
    c.fadeOutMs = fadeOutMs;
    if (keysRelMs) {
        const jsize n = env->GetArrayLength(keysRelMs);
        c.keysRelMs.resize((size_t)n);
        if (n > 0) env->GetLongArrayRegion(keysRelMs, 0, n,
                                           reinterpret_cast<jlong*>(c.keysRelMs.data()));
    }
    if (peaks) {
        const jsize n = env->GetArrayLength(peaks);
        c.peaks.resize((size_t)n);
        if (n > 0) env->GetFloatArrayRegion(peaks, 0, n, c.peaks.data());
    }
    h->building.tracks[trackIdx].clips.push_back(std::move(c));
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeEndSnapshot(JNIEnv*, jclass, jlong p) {
    TlHandle* h = of(p);
    if (!h) return;
    std::lock_guard<std::mutex> lock(h->mu);
    h->renderer.setSnapshot(std::move(h->building));
    h->building = Snapshot();
}

JNIEXPORT jfloat JNICALL Java_com_example_engine_TimelineNative_nativeContentHeight(JNIEnv*, jclass, jlong p) {
    TlHandle* h = of(p);
    if (!h) return 0;
    std::lock_guard<std::mutex> lock(h->mu);
    return contentHeightPx(h->renderer.snapshot());
}

JNIEXPORT jboolean JNICALL Java_com_example_engine_TimelineNative_nativeDraw(
    JNIEnv*, jclass, jlong p, jlong playheadMs, jfloat scrollYPx, jint dragTrack, jint dragClip,
    jfloat dragDx, jfloat dragDy) {
    TlHandle* h = of(p);
    if (!h) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(h->mu);
    if (!h->window) return JNI_FALSE;
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(h->window, &buf, nullptr) != 0) return JNI_FALSE;
    View v;
    v.w = buf.width;
    v.h = buf.height;
    v.playheadMs = playheadMs;
    v.scrollYPx = scrollYPx;
    v.dragTrack = dragTrack;
    v.dragClip = dragClip;
    v.dragDx = dragDx;
    v.dragDy = dragDy;
    h->renderer.draw((uint8_t*)buf.bits, buf.width, buf.height, buf.stride * 4, v);
    ANativeWindow_unlockAndPost(h->window);
    return JNI_TRUE;
}

// out[0] = track, out[1] = clip; returns the zone (timeline_render.h Zone).
JNIEXPORT jint JNICALL Java_com_example_engine_TimelineNative_nativeHitTest(
    JNIEnv* env, jclass, jlong p, jfloat x, jfloat y, jint viewW, jint viewH, jlong playheadMs,
    jfloat scrollYPx, jintArray out) {
    TlHandle* h = of(p);
    if (!h) return 0;
    std::lock_guard<std::mutex> lock(h->mu);
    View v;
    v.w = viewW;
    v.h = viewH;
    v.playheadMs = playheadMs;
    v.scrollYPx = scrollYPx;
    int32_t track = -1, clip = -1;
    Zone z = h->renderer.hitTest(x, y, v, &track, &clip);
    if (out && env->GetArrayLength(out) >= 2) {
        jint res[2] = {track, clip};
        env->SetIntArrayRegion(out, 0, 2, res);
    }
    return (jint)z;
}

}  // extern "C"
