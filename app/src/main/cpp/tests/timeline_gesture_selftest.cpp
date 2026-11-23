// Build 233 — host selftests for the native timeline gesture machine (timeline_gesture.cpp).
// Build & run (no Android needed):
//   g++ -std=c++17 -O2 -Wall -Wextra -I../engine timeline_gesture_selftest.cpp ../engine/timeline_gesture.cpp ../engine/timeline_render.cpp -o /tmp/tg && /tmp/tg
// Same geometry as timeline_selftest.cpp: density 2, 0.045 dp/ms (0.09 px/ms), ruler 88 px,
// lane gap 8 px, playhead 2000 ms in a 720 px wide surface (the playhead is at the centre).
#include <cmath>
#include <cstdio>
#include <vector>

#include "timeline_gesture.h"

using namespace am::timeline;

static int g_fail = 0;
static int g_pass = 0;
#define CHECK(cond, msg) do { if (cond) { ++g_pass; } else { ++g_fail; \
    std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, msg); } } while (0)

static Snapshot makeScene(bool multi = false) {
    Snapshot s;
    s.density = 2.f; s.pxPerMs = 0.045f; s.rulerHPx = 88; s.railWPx = 84;
    s.topPadPx = 8; s.gapPx = 8; s.bottomPadPx = 8; s.totalMs = 8000;
    s.multiMode = multi;
    Track t0; t0.heightPx = 64;
    Clip v; v.startMs = 1000; v.durMs = 4000; v.kind = kGlyphVideo;
    v.flags = kFlagVideo | kFlagHasUri | kFlagSelected;
    t0.clips.push_back(v);
    Track t1; t1.heightPx = 64;
    Clip a; a.startMs = 1500; a.durMs = 3000; a.kind = kGlyphMusic; a.flags = kFlagAudio;
    t1.clips.push_back(a);
    Track t2; t2.heightPx = 64;
    Clip tx; tx.startMs = 2000; tx.durMs = 2000; tx.kind = kGlyphText;
    t2.clips.push_back(tx);
    Track t3; t3.heightPx = 88; t3.chainClip = 0;
    Clip ch; ch.startMs = 1000; ch.durMs = 3500; ch.kind = kGlyphChain; ch.flags = kFlagChain;
    Clip cA; cA.startMs = 1000; cA.durMs = 1500; cA.kind = kGlyphMusic; cA.flags = kFlagAudio | kFlagInChain;
    t3.clips = {ch, cA};
    Track t4; t4.heightPx = 64;
    Clip sh; sh.startMs = 2500; sh.durMs = 1000; sh.kind = kGlyphShape;
    t4.clips.push_back(sh);
    s.tracks = {t0, t1, t2, t3, t4};
    return s;
}

struct Rig {
    Renderer r;
    Gesture g;
    int viewH = 488;
    int64_t t = 1000;
    int64_t playhead = 2000;

    explicit Rig(const Snapshot& s) { r.setSnapshot(s); g.setConfig(8.f, 400); }

    std::vector<Event> send(int action, float x, float y, int64_t dtMs = 16, int pc = 1,
                            float x1 = 0, float y1 = 0) {
        t += dtMs;
        TouchInput in;
        in.action = action; in.x = x; in.y = y; in.x1 = x1; in.y1 = y1; in.pointerCount = pc;
        in.timeMs = t; in.playheadMs = playhead; in.viewW = 720; in.viewH = viewH;
        std::vector<Event> ev;
        g.touch(r, in, &ev);
        return ev;
    }
};

static int count(const std::vector<Event>& ev, int type) {
    int n = 0;
    for (const Event& e : ev) if (e.type == type) ++n;
    return n;
}
static const Event* first(const std::vector<Event>& ev, int type) {
    for (const Event& e : ev) if (e.type == type) return &e;
    return nullptr;
}

int main() {
    // ---- taps ----
    {
        Rig rig(makeScene());
        auto d = rig.send(kActionDown, 300, 128);
        CHECK(first(d, kEvBegin) && first(d, kEvBegin)->a == 0 && first(d, kEvBegin)->b == 0, "begin carries track/clip");
        CHECK(count(d, kEvArmLongPress) == 1, "long-press armed on a bar");
        auto u = rig.send(kActionUp, 300, 128);
        CHECK(count(u, kEvCancelLongPress) == 1, "tap cancels the timer");
        CHECK(count(u, kEvSelect) == 1, "tap on a bar selects it");
        CHECK(count(u, kEvSeek) == 0, "a tap does not seek");
    }
    {
        Rig rig(makeScene());
        // Build 263: the layer-count pill is gone — its old spot is plain ruler.
        rig.send(kActionDown, 50, 44);
        auto u = rig.send(kActionUp, 50, 44);
        CHECK(u.empty() || count(u, kEvSelect) == 0, "former pill spot: ruler tap does nothing");
        auto d = rig.send(kActionDown, 400, 44);
        CHECK(count(d, kEvArmLongPress) == 0, "no long-press on the ruler");
        u = rig.send(kActionUp, 400, 44);
        CHECK(u.empty() || count(u, kEvSelect) == 0, "ruler tap does nothing");
    }
    {
        Rig rig(makeScene());
        rig.send(kActionDown, 42, 128);
        auto u = rig.send(kActionUp, 42, 128);
        CHECK(count(u, kEvToggleVisibility) == 1, "eye tap toggles visibility");
        Rig multi(makeScene(true));
        multi.send(kActionDown, 42, 128);
        u = multi.send(kActionUp, 42, 128);
        CHECK(count(u, kEvToggleMulti) == 1 && count(u, kEvToggleVisibility) == 0, "eye tap in multi-select toggles the check");
        multi.send(kActionDown, 10, 128);
        u = multi.send(kActionUp, 10, 128);
        CHECK(count(u, kEvToggleMulti) == 1, "rail tap in multi-select toggles the check");
    }

    // ---- scrub ----
    {
        Rig rig(makeScene());
        rig.send(kActionDown, 700, 460);
        auto m = rig.send(kActionMove, 690, 460);
        CHECK(count(m, kEvScrubStart) == 1, "scrub starts past the slop");
        CHECK(first(m, kEvSeek) && (int64_t)first(m, kEvSeek)->a == 2111, "slop distance counts: 10 px = 111 ms");
        CHECK(count(m, kEvCancelLongPress) == 0, "empty area never armed a timer");
        m = rig.send(kActionMove, 680, 460);
        CHECK(count(m, kEvScrubStart) == 0, "scrub start only once");
        CHECK(first(m, kEvSeek) && (int64_t)first(m, kEvSeek)->a == 2222, "next 10 px = +111 ms");
        // far past the start: clamped to 0
        for (int i = 0; i < 80; ++i) m = rig.send(kActionMove, 680 + 10 * (i + 1), 460);
        CHECK(first(m, kEvSeek) && first(m, kEvSeek)->a == 0, "scrub clamps at 0");
    }

    // ---- scrub fling ----
    {
        Rig rig(makeScene());
        rig.send(kActionDown, 700, 460);
        float x = 700;
        for (int i = 0; i < 12; ++i) { x -= 20; rig.send(kActionMove, x, 460, 16); }  // 1250 px/s to the left
        auto u = rig.send(kActionUp, x, 460, 16);
        CHECK(count(u, kEvAnimate) == 1, "fast release starts a fling");
        CHECK(rig.g.animating(), "animating after fling start");
        double last = -1;
        int ticks = 0;
        bool mono = true, inRange = true;
        int64_t now = 5'000'000'000LL;
        for (; ticks < 400; ++ticks) {
            std::vector<Event> ev;
            rig.g.tick(rig.r, now, &ev);
            now += 16'000'000LL;
            const Event* sk = first(ev, kEvSeek);
            if (sk) {
                if (sk->a < last) mono = false;
                if (sk->a < 0 || sk->a > 8000) inRange = false;
                last = sk->a;
            }
            if (count(ev, kEvAnimate) == 0) break;
        }
        CHECK(ticks > 10 && ticks < 200, "fling runs for a plausible time");
        CHECK(mono, "fling to the left moves the playhead forward only");
        CHECK(inRange, "fling stays in 0..total");
        CHECK(!rig.g.animating(), "fling ends");
        // a new touch kills a running fling
        rig.send(kActionDown, 700, 460);
        rig.send(kActionMove, 690, 460);
        rig.send(kActionMove, 660, 460, 16);
        for (int i = 0; i < 8; ++i) rig.send(kActionMove, 660, 460, 16);  // held still (jitter-free)
        u = rig.send(kActionUp, 660, 460, 16);
        CHECK(count(u, kEvAnimate) == 0, "a finger held still before lifting does not fling");
    }

    // ---- vertical scroll (surface only 300 px high: the 5 lanes do not fit) ----
    {
        Rig rig(makeScene());
        rig.viewH = 300;
        rig.send(kActionDown, 700, 250);
        auto m = rig.send(kActionMove, 700, 230);
        CHECK(count(m, kEvScrubStart) == 0 && count(m, kEvSeek) == 0, "vertical swipe does not scrub");
        CHECK(std::fabs(rig.g.scrollY(rig.r) - 20.f) < 0.01f, "scroll follows the finger (20 px)");
        CHECK(count(m, kEvRedraw) >= 1, "scroll asks for a frame");
        for (int i = 0; i < 40; ++i) rig.send(kActionMove, 700, 230 - 10 * (i + 1));
        float mx = rig.g.maxScrollY(rig.r);
        CHECK(mx > 100.f, "content is taller than the lanes area");
        CHECK(std::fabs(rig.g.scrollY(rig.r) - mx) < 0.01f, "scroll clamps at the end");
        rig.send(kActionUp, 700, 0, 400);
        // scrolling back up clamps at 0
        rig.send(kActionDown, 700, 100);
        for (int i = 0; i < 60; ++i) rig.send(kActionMove, 700, 100 + 10 * (i + 1));
        CHECK(rig.g.scrollY(rig.r) == 0.f, "scroll clamps at 0");
    }
    {
        // scroll fling (vertical)
        Rig rig(makeScene());
        rig.viewH = 300;
        rig.send(kActionDown, 700, 280);
        float y = 280;
        rig.send(kActionMove, 700, y - 12);
        for (int i = 0; i < 8; ++i) { y -= 12; rig.send(kActionMove, 700, y - 12 + 0, 16); }
        auto u = rig.send(kActionUp, 700, y, 16);
        CHECK(count(u, kEvAnimate) == 1, "vertical fling starts");
        int64_t now = 1'000'000'000LL;
        float prev = rig.g.scrollY(rig.r);
        bool ok = true;
        for (int i = 0; i < 300; ++i) {
            std::vector<Event> ev;
            rig.g.tick(rig.r, now, &ev);
            now += 16'000'000LL;
            float cur = rig.g.scrollY(rig.r);
            if (cur < prev - 0.001f) ok = false;
            prev = cur;
            if (count(ev, kEvAnimate) == 0) break;
        }
        CHECK(ok, "vertical fling moves one way");
        CHECK(!rig.g.animating(), "vertical fling ends");
        CHECK(rig.g.scrollY(rig.r) <= rig.g.maxScrollY(rig.r) + 0.001f, "fling clamps at the end");
    }

    // ---- long-press drag: move in time + lane reorder ----
    {
        Rig rig(makeScene());
        auto d = rig.send(kActionDown, 300, 128);
        CHECK(count(d, kEvArmLongPress) == 1, "armed");
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        CHECK(count(lp, kEvSelect) == 1 && count(lp, kEvBeginAdjust) == 1, "long-press selects + opens the undo step");
        int32_t dt, dc; float dx, dy;
        rig.g.dragView(&dt, &dc, &dx, &dy);
        CHECK(dt == 0 && dc == 0, "drag offset is shown for the clip");
        auto m = rig.send(kActionMove, 318, 128);  // +18 px = 200 ms
        CHECK(first(m, kEvMoveTime) && (int64_t)first(m, kEvMoveTime)->a == 200, "18 px = 200 ms move");
        rig.g.dragView(&dt, &dc, &dx, &dy);
        CHECK(std::fabs(dx) < 0.01f, "the applied ms is taken off the visual offset");
        m = rig.send(kActionMove, 318, 208);  // down 80 px, no x
        CHECK(count(m, kEvMoveTime) == 0, "vertical drag does not move in time");
        auto u = rig.send(kActionUp, 318, 208);
        CHECK(first(u, kEvReorder) && first(u, kEvReorder)->a == 0 && first(u, kEvReorder)->b == 1,
              "80 px down = one lane (72 px step)");
        rig.g.dragView(&dt, &dc, &dx, &dy);
        CHECK(dt == -1 && dc == -1, "drag cleared on release");
        // timer fires after the gesture ended: ignored
        std::vector<Event> late;
        rig.g.longPressFired(rig.r, &late);
        CHECK(late.empty(), "a late timer does nothing");
    }
    {
        // dragging far past the last lane clamps to it
        Rig rig(makeScene());
        rig.send(kActionDown, 300, 128);
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        rig.send(kActionMove, 300, 600);
        auto u = rig.send(kActionUp, 300, 600);
        CHECK(first(u, kEvReorder) && first(u, kEvReorder)->b == 4, "reorder clamps to the last lane");
    }
    {
        // a rail long-press starts multi-select; in multi-select it toggles instead
        Rig rig(makeScene());
        rig.send(kActionDown, 10, 128);
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        CHECK(count(lp, kEvEnterMulti) == 1, "rail long-press enters multi-select");
        auto u = rig.send(kActionUp, 10, 128);
        CHECK(count(u, kEvSelect) == 0 && count(u, kEvToggleMulti) == 0, "the lifted finger does nothing more");
        Rig m2(makeScene(true));
        m2.send(kActionDown, 10, 128);
        lp.clear();
        m2.g.longPressFired(m2.r, &lp);
        CHECK(count(lp, kEvToggleMulti) == 1, "rail long-press in multi-select toggles");
    }
    {
        // Build 235: a long-press starting on the EYE is a rail-head press too —
        // it arms the timer and enters multi-select; the release does nothing more.
        Rig rig(makeScene());
        auto d = rig.send(kActionDown, 42, 128);
        CHECK(count(d, kEvArmLongPress) == 1, "long-press armed on the eye");
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        CHECK(count(lp, kEvEnterMulti) == 1, "eye long-press enters multi-select");
        CHECK(count(lp, kEvToggleVisibility) == 0 && count(lp, kEvSelect) == 0,
              "eye long-press does not toggle visibility or select");
        auto u = rig.send(kActionUp, 42, 128);
        CHECK(count(u, kEvToggleVisibility) == 0 && count(u, kEvToggleMulti) == 0,
              "release after an eye long-press does nothing more");
        // in multi-select an eye long-press toggles the check instead
        Rig m2(makeScene(true));
        m2.send(kActionDown, 42, 128);
        lp.clear();
        m2.g.longPressFired(m2.r, &lp);
        CHECK(count(lp, kEvToggleMulti) == 1 && count(lp, kEvToggleVisibility) == 0,
              "eye long-press in multi-select toggles the check");
        // a plain tap on the eye still ONLY toggles visibility
        Rig t2(makeScene());
        t2.send(kActionDown, 42, 128);
        auto tu = t2.send(kActionUp, 42, 128);
        CHECK(count(tu, kEvToggleVisibility) == 1 && count(tu, kEvSelect) == 0 &&
              count(tu, kEvEnterMulti) == 0, "eye tap toggles visibility only");
    }
    {
        // moving before the timer cancels it
        Rig rig(makeScene());
        rig.send(kActionDown, 300, 128);
        auto m = rig.send(kActionMove, 330, 128);
        CHECK(count(m, kEvCancelLongPress) == 1 && count(m, kEvScrubStart) == 1, "movement cancels the long-press and scrubs");
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        CHECK(lp.empty(), "cancelled timer is a no-op");
    }

    // ---- trim ----
    {
        Rig rig(makeScene());
        auto d = rig.send(kActionDown, 650, 128);
        CHECK(count(d, kEvArmLongPress) == 0, "no long-press on a trim handle");
        auto m = rig.send(kActionMove, 670, 128);  // +20 px = 222.2 ms
        CHECK(count(m, kEvBeginAdjust) == 1, "trim opens the undo step");
        CHECK(first(m, kEvTrim) && first(m, kEvTrim)->a == 0 && (int64_t)first(m, kEvTrim)->b == 222, "right trim +222 ms");
        m = rig.send(kActionMove, 679, 128);       // +9 px = 100 ms (+0.2 carried)
        CHECK(first(m, kEvTrim) && (int64_t)first(m, kEvTrim)->b == 100, "carried fraction: next 100 ms");
        m = rig.send(kActionMove, 660, 128);       // -19 px = -211.1 ms
        CHECK(first(m, kEvTrim) && (int64_t)first(m, kEvTrim)->b == -210, "negative whole ms truncate toward zero (-210.9 -> -210)");
        auto u = rig.send(kActionUp, 660, 128);
        CHECK(count(u, kEvSelect) == 0, "no select after a trim");

        Rig rl(makeScene());
        rl.send(kActionDown, 250, 128);
        m = rl.send(kActionMove, 240, 128);
        CHECK(first(m, kEvTrim) && first(m, kEvTrim)->a == 1, "left trim handle");
        // a mostly vertical move on a trim handle does not start a trim
        Rig rv(makeScene());
        rv.send(kActionDown, 650, 128);
        m = rv.send(kActionMove, 652, 160);
        CHECK(count(m, kEvTrim) == 0 && count(m, kEvScrubStart) == 0, "vertical move on a handle: nothing");
    }

    // ---- fade knob ----
    {
        Snapshot s2;
        s2.density = 2.f; s2.pxPerMs = 0.045f; s2.rulerHPx = 88; s2.railWPx = 84;
        s2.topPadPx = 8; s2.gapPx = 8; s2.bottomPadPx = 8; s2.totalMs = 8000;
        Track u0; u0.heightPx = 64;
        Clip tc; tc.startMs = 2000; tc.durMs = 2000; tc.kind = kGlyphText;
        u0.clips.push_back(tc);
        Track u1; u1.heightPx = 64;
        Clip ac; ac.startMs = 2000; ac.durMs = 3000; ac.kind = kGlyphMusic;
        ac.flags = kFlagAudio | kFlagSelected; ac.fadeInMs = 500; ac.fadeOutMs = 500;
        u1.clips.push_back(ac);
        s2.tracks = {u0, u1};
        Rig rig(s2);
        rig.send(kActionDown, 405, 168);
        auto m = rig.send(kActionMove, 423, 168);  // +18 px = 200 ms
        CHECK(first(m, kEvFade) && first(m, kEvFade)->a == 1 && ((int64_t)first(m, kEvFade)->b == 200 || (int64_t)first(m, kEvFade)->b == 199), "fade-in knob +200 ms (float: 199 or 200)");
    }

    // ---- pinch ----
    {
        Rig rig(makeScene());
        rig.send(kActionDown, 300, 460);
        auto p = rig.send(kActionPointerDown, 300, 460, 16, 2, 400, 460);   // span 100
        CHECK(count(p, kEvCancelLongPress) == 0, "no timer on empty area");
        auto m = rig.send(kActionMove, 300, 460, 16, 2, 500, 460);          // span 200 -> x2
        CHECK(first(m, kEvZoom) && std::fabs(first(m, kEvZoom)->a - 0.09) < 1e-4, "spread = zoom in x2");
        m = rig.send(kActionMove, 300, 460, 16, 2, 300 + 400, 460);          // span 400 -> x2 again
        CHECK(first(m, kEvZoom) && std::fabs(first(m, kEvZoom)->a - 0.18) < 1e-4, "zoom accumulates without waiting for the snapshot");
        for (int i = 0; i < 6; ++i) m = rig.send(kActionMove, 0, 460, 16, 2, 100000.f * (i + 1), 460);
        CHECK(first(m, kEvZoom) == nullptr || first(m, kEvZoom)->a <= kMaxZoom + 1e-5, "zoom is clamped to 8x");
        auto up = rig.send(kActionPointerUp, 300, 460, 16, 2, 400, 460);
        up = rig.send(kActionMove, 320, 460);
        CHECK(count(up, kEvSeek) == 0, "after a pinch the finger that stays does not scrub");
        rig.send(kActionUp, 320, 460);
    }
    {
        // a second finger during a long-press drag ends the drag without a reorder
        Rig rig(makeScene());
        rig.send(kActionDown, 300, 128);
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        rig.send(kActionMove, 300, 208);
        rig.send(kActionPointerDown, 300, 208, 16, 2, 400, 208);
        int32_t dt, dc; float dx, dy;
        rig.g.dragView(&dt, &dc, &dx, &dy);
        CHECK(dt == -1, "second finger clears the drag");
        auto u = rig.send(kActionUp, 300, 208);
        CHECK(count(u, kEvReorder) == 0, "no reorder after the pinch");
    }

    // ---- cancel ----
    {
        Rig rig(makeScene());
        rig.send(kActionDown, 300, 128);
        std::vector<Event> lp;
        rig.g.longPressFired(rig.r, &lp);
        rig.send(kActionMove, 330, 128);
        auto c = rig.send(kActionCancel, 330, 128);
        int32_t dt, dc; float dx, dy;
        rig.g.dragView(&dt, &dc, &dx, &dy);
        CHECK(dt == -1 && count(c, kEvRedraw) == 1, "cancel clears the drag and redraws");
    }

    // ---- scroll limit follows the snapshot ----
    {
        Rig rig(makeScene());
        rig.viewH = 300;
        rig.send(kActionDown, 700, 250);
        rig.send(kActionMove, 700, 100);
        float big = rig.g.scrollY(rig.r);
        CHECK(big > 100.f, "scrolled down");
        Snapshot small = makeScene();
        small.tracks.resize(1);
        rig.r.setSnapshot(small);
        CHECK(rig.g.scrollY(rig.r) == 0.f, "a smaller snapshot pulls the scroll back in range");
    }

    std::printf("timeline_gesture_selftest: %d passed, %d failed\n", g_pass, g_fail);
    return g_fail ? 1 : 0;
}
