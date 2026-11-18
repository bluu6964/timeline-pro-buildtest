// Build 224 — JNI bridge between com.example.engine.TimelineNative (Kotlin) and the native
// timeline renderer (timeline_render.cpp). Data only: Kotlin builds a snapshot through the
// begin/add/end calls, uploads the label atlas bitmap, and asks for frames; this file owns
// the ANativeWindow of the timeline's SurfaceView and the renderer's mutex.
#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <algorithm>
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
    // Build 227: take the handle mutex before touching the window. Build 224's
    // version released the window and freed the handle WITHOUT the lock, while
    // the main thread could be inside nativeDraw / nativeHitTest /
    // nativeEndSnapshot holding only that same mutex — a use-after-free
    // (SIGSEGV) on editor teardown. The Kotlin side now also serializes
    // destroy against every other entry point (NativeTimelineEngine's
    // read/write lock), so this lock is the second, native line of defence.
    // The delete happens after the guard's scope ends (never free an object
    // while holding its own mutex).
    {
        std::lock_guard<std::mutex> lock(h->mu);
        if (h->window) {
            ANativeWindow_release(h->window);
            h->window = nullptr;
        }
    }
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
    // Build 227: refuse absurd bitmap sizes rather than attempting a huge copy.
    if (info.width > 8192 || info.height > 8192) return;
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
    // Build 227 hardening: cap the entry count (a corrupt count must not turn
    // into a giant allocation), null-check the array element pointers, clamp
    // the bases reads to the bases array's OWN length (build 224 indexed it
    // with the rect count, unchecked), and clamp every entry rect into the
    // bitmap's bounds so every renderer blit is in-bounds by construction.
    if (n < 0 || n > (1 << 20)) return;
    jint* r = env->GetIntArrayElements(rects, nullptr);
    if (!r) return;
    jfloat* b = bases ? env->GetFloatArrayElements(bases, nullptr) : nullptr;
    if (bases && !b) {
        env->ReleaseIntArrayElements(rects, r, JNI_ABORT);
        return;
    }
    const jsize nBase = bases ? env->GetArrayLength(bases) : 0;
    at.entries.resize((size_t)n);
    for (jsize i = 0; i < n; ++i) {
        int32_t ex = std::clamp<int32_t>(r[i * 4], 0, at.w);
        int32_t ey = std::clamp<int32_t>(r[i * 4 + 1], 0, at.h);
        at.entries[i].x = ex;
        at.entries[i].y = ey;
        at.entries[i].w = std::clamp<int32_t>(r[i * 4 + 2], 0, at.w - ex);
        at.entries[i].h = std::clamp<int32_t>(r[i * 4 + 3], 0, at.h - ey);
        at.entries[i].base = (b && i < nBase) ? b[i] : 0.f;
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
    // Build 227: sanitize the view geometry at the boundary. A zero / negative
    // / NaN density or pxPerMs would turn the renderer's divisions into
    // inf/NaN and its float->int casts into UB; negative pads/counts are
    // meaningless. (Kotlin passes sane values today — this is the choke-point
    // guarantee, not a behaviour change.) Also take the handle mutex like
    // every other entry point: build 224 mutated h->building with no lock.
    auto posOr = [](jfloat v, jfloat fallback) { return (v > 0.f) ? v : fallback; };
    auto nonNeg = [](jfloat v) { return (v >= 0.f) ? v : 0.f; };
    Snapshot s;
    s.density = posOr(density, 1.f);
    s.pxPerMs = posOr(pxPerMs, 0.045f);
    s.rulerHPx = nonNeg(rulerHPx);
    s.railWPx = nonNeg(railWPx);
    s.topPadPx = nonNeg(topPadPx);
    s.gapPx = nonNeg(gapPx);
    s.bottomPadPx = nonNeg(bottomPadPx);
    s.totalMs = totalMs < 0 ? 0 : totalMs;
    s.layerCount = layerCount < 0 ? 0 : layerCount;
    s.pillLabel = pillLabel;
    s.pillTextWPx = pillTextWPx;
    s.totalLabel = totalLabel;
    s.chainLabel = chainLabel;
    s.comfort = comfort == JNI_TRUE;
    s.multiMode = multiMode == JNI_TRUE;
    s.hasRange = hasRange == JNI_TRUE;
    s.rangeInMs = rangeInMs;
    s.rangeOutMs = rangeOutMs;
    s.pillTextWPx = nonNeg(s.pillTextWPx);
    std::lock_guard<std::mutex> lock(h->mu);
    h->building = std::move(s);
}

JNIEXPORT void JNICALL Java_com_example_engine_TimelineNative_nativeSetBeats(
    JNIEnv* env, jclass, jlong p, jlongArray beatMs, jlongArray rulerKeysMs) {
    TlHandle* h = of(p);
    if (!h) return;
    std::lock_guard<std::mutex> lock(h->mu);
    if (beatMs) {
        const jsize n = env->GetArrayLength(beatMs);
        if (n < 0) return;
        h->building.beatMs.resize((size_t)n);
        if (n > 0) env->GetLongArrayRegion(beatMs, 0, n,
                                           reinterpret_cast<jlong*>(h->building.beatMs.data()));
    }
    if (rulerKeysMs) {
        const jsize n = env->GetArrayLength(rulerKeysMs);
        if (n < 0) return;
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
    std::lock_guard<std::mutex> lock(h->mu);
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
    if (!h) return;
    std::lock_guard<std::mutex> lock(h->mu);
    if (trackIdx < 0 || trackIdx >= (jint)h->building.tracks.size()) return;
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
        if (n > 0) {
            c.keysRelMs.resize((size_t)n);
            env->GetLongArrayRegion(keysRelMs, 0, n,
                                    reinterpret_cast<jlong*>(c.keysRelMs.data()));
        }
    }
    if (peaks) {
        const jsize n = env->GetArrayLength(peaks);
        if (n > 0) {
            c.peaks.resize((size_t)n);
            env->GetFloatArrayRegion(peaks, 0, n, c.peaks.data());
        }
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
    if (!buf.bits) {
        // Lock claimed success without a buffer: release it and skip the frame
        // rather than handing a null framebuffer to the renderer.
        ANativeWindow_unlockAndPost(h->window);
        return JNI_FALSE;
    }
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
