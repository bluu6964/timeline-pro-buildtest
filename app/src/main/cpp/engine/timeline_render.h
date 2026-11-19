// Build 224 — GRADUAL NATIVE MIGRATION, STEP 1: THE TIMELINE.
//
// The timeline's DRAWING moves off the Compose recomposition path onto a
// native-rendered SurfaceView driven by the existing C++ engine library
// (libam_engine). The Kotlin model / ViewModel stays the single source of
// truth: Kotlin hands this renderer a plain-data snapshot (tracks, clips,
// view geometry) whenever the data changes, plus three per-frame values
// (playhead ms, vertical scroll, drag override), and the renderer draws
// every pixel of the ruler + lanes region in C++ — frame-synced by the
// Kotlin side's Choreographer loop, with NO Compose involvement per frame.
//
// This file is pure C++ (no Android headers) so the geometry, the layout
// math and the rasterizer are host-testable (tests/timeline_selftest.cpp).
// The JNI bridge (timeline_jni.cpp) only moves data and window buffers.
//
// TEXT: glyph rasterization is the one job deliberately NOT done here.
// Labels are rasterized ONCE per (text, style, width) by Android's own
// text stack into a shared atlas bitmap owned by Kotlin (the same fonts
// Compose uses, so labels look identical to the Compose timeline, with
// full Bengali / RTL shaping); the snapshot references atlas entries and
// this renderer only BLITS them. Every frame's drawing is native; no
// Compose overlay is involved. See the build-224 CYCLE-LOG entry.
//
// The geometry mirrors ui/components/TimescaleSection.kt and
// ui/components/TimelineLayerPanel.kt of build 223 exactly (the
// playhead-centred world: screen x = centre + (ms - playhead) * pxPerMs;
// the TrimHandleWidth offset cancels out of the bar position). Colours
// are the build-223 theme values; the per-type bar colour and glyph kind
// are computed by Kotlin (barColorFor / barGlyphFor) and handed over, so
// they cannot drift from the Compose path.
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace timeline {

/** Glyph kinds of the capsule bars (Kotlin: barGlyphFor). */
enum GlyphKind : int32_t {
    kGlyphNone = 0,
    kGlyphText,
    kGlyphShape,
    kGlyphFx,
    kGlyphGroup,
    kGlyphNull,
    kGlyphSticker,
    kGlyphCamera,
    kGlyphVector,
    kGlyphDrawing,
    kGlyphVideo,
    kGlyphMusic,
    kGlyphChain
};

/** Per-clip flags (built by Kotlin from the TimelineClip fields). */
enum ClipFlag : uint32_t {
    kFlagSelected = 1u << 0,
    kFlagMulti = 1u << 1,       // checked in multi-select
    kFlagHidden = 1u << 2,      // !isVisible (bar drawn at 35%)
    kFlagLocked = 1u << 3,
    kFlagFx = 1u << 4,          // effects present (fx badge)
    kFlagChain = 1u << 5,       // the CHAIN clip itself (transparent bar, section lane)
    kFlagInChain = 1u << 6,     // a child inside a chain section
    kFlagHasUri = 1u << 7,
    kFlagImage = 1u << 8,       // photo (VIDEO type with isImage)
    kFlagMuted = 1u << 9,       // video waveform strip is skipped when muted
    kFlagGroupEdge = 1u << 10,  // member of a real Group (teal start edge)
    kFlagController = 1u << 11, // hollow teal bar
    kFlagVideo = 1u << 12,
    kFlagAudio = 1u << 13
};

struct Clip {
    int64_t startMs = 0;
    int64_t durMs = 0;
    int32_t kind = kGlyphNone;  // GlyphKind (capsule + chain-child bars)
    uint32_t color = 0;         // ARGB body colour (unused for chain / controller)
    uint32_t flags = 0;
    int32_t label = -1;         // atlas entry: capsule / chain-child label (ellipsized by Kotlin)
    int32_t chip = -1;          // atlas entry: name chip (video / audio bars)
    int32_t badge = -1;         // atlas entry: audio duration badge ("%.1fs")
    std::vector<int64_t> keysRelMs;  // in-bar keyframe diamonds (selected clip only)
    // Waveform peaks (AudioWaveform buckets of 50 ms of SOURCE time); empty = none yet.
    std::vector<float> peaks;
    int64_t trimStartMs = 0;
    float speed = 1.f;
    float volume = 1.f;
    int64_t fadeInMs = 0;
    int64_t fadeOutMs = 0;
};

struct Track {
    float heightPx = 0;      // lane row height (Kotlin: laneHeightFor, in px)
    int32_t chainClip = -1;  // index into clips of the CHAIN clip, or -1
    std::vector<Clip> clips;
};

struct Snapshot {
    float density = 1.f;    // px per dp
    float pxPerMs = 0.045f; // dp per ms (model TIMELINE_PX_PER_MS units)
    float rulerHPx = 0;     // 44 dp in px
    float railWPx = 0;      // 42 dp in px
    float topPadPx = 0;     // lanes content top padding (4 dp)
    float gapPx = 0;        // lane gap (4 dp)
    float bottomPadPx = 0;  // lanes content bottom padding (4 dp; 106 dp in multi-select, build 235)
    int64_t totalMs = 0;
    std::vector<int64_t> beatMs;
    bool hasRange = false;
    int64_t rangeInMs = 0;
    int64_t rangeOutMs = 0;
    std::vector<int64_t> rulerKeysMs;  // the selected layer's keyframes, project ms
    int32_t layerCount = 0;
    int32_t pillLabel = -1;   // atlas entry: "N Layers"
    float pillTextWPx = 0;    // measured width of the pill label (px)
    int32_t totalLabel = -1;  // atlas entry: total duration text
    int32_t chainLabel = -1;  // atlas entry: "Chain" (the empty-section label)
    bool comfort = false;
    bool multiMode = false;
    std::vector<Track> tracks;
};

/** One glyph-atlas entry: a tight ink box inside the atlas bitmap. */
struct AtlasEntry {
    int32_t x = 0, y = 0, w = 0, h = 0;
    float base = 0;  // baseline distance from the ink box's top (px; digits use it)
};

struct Atlas {
    std::vector<uint8_t> rgba;  // RGBA_8888, w * h * 4 (white glyphs; alpha = coverage)
    int32_t w = 0, h = 0;
    std::vector<AtlasEntry> entries;
    int32_t digit[11];          // entries for '0'..'9' and ':' (timecode), -1 = absent
    float digitAdv[11];         // advance per digit (px)
    float digitBaseY = 0;       // timecode baseline y from the ruler's top (px)
    Atlas() {
        for (int i = 0; i < 11; ++i) { digit[i] = -1; digitAdv[i] = 0; }
    }
};

/** Per-frame view values (the only things that change while playing / scrolling / dragging). */
struct View {
    int32_t w = 0, h = 0;        // surface size (px)
    int64_t playheadMs = 0;
    float scrollYPx = 0;
    int32_t dragTrack = -1, dragClip = -1;  // the long-press-dragged clip, or -1
    float dragDx = 0, dragDy = 0;           // its visual offset (px)
};

/** Hit-test zones (Kotlin's gesture machine consumes these). */
enum Zone : int32_t {
    kZoneNone = 0,
    kZoneRuler,     // ruler: scrub
    kZoneEmpty,     // lanes background: scrub / vertical scroll
    kZoneBar,       // a clip bar (track / clip set)
    kZoneRail,      // a lane's left cell (select / long-press)
    kZoneEye,       // the eye inside a lane cell (visibility toggle)
    kZoneTrimL,     // selected clip's left trim handle
    kZoneTrimR,     // selected clip's right trim handle
    kZoneFadeL,     // selected audio clip's fade-in knob
    kZoneFadeR,     // selected audio clip's fade-out knob
    kZonePill,      // the layer-count pill
    kZoneChainX,    // the selected chain section's delete x
    kZoneChildX     // a chain child's delete x (clip set)
};

// ---- Layout math (single source for drawing AND hit testing; host-tested) ----

/** px per ms at the snapshot's zoom. */
float ppmPx(const Snapshot& s);
/** Screen x of a project time (the playhead sits at the surface's horizontal centre). */
float xForMs(const Snapshot& s, const View& v, int64_t ms);
/** Top y (px) of lane [track] inside the surface (ruler included, scroll applied). */
float laneTopPx(const Snapshot& s, const View& v, int32_t track);
/** Total scrollable content height of the lanes region (px, ruler excluded). */
float contentHeightPx(const Snapshot& s);
/** The bar rect of a clip (px). Chain clips span the full width; chain children are inset. */
void barRectPx(const Snapshot& s, const View& v, int32_t track, int32_t clip,
               float* x, float* y, float* w, float* h);
/** Ruler tick step (ms) for the zoom: the first of the Compose steps whose pitch is >= 8 dp. */
int64_t rulerStepMs(float ppm, float density);
/** The audio fade multiplier at [relMs] into the clip (mirrors AudioClipBody.fadeAt). */
float fadeGainAt(const Clip& c, int64_t relMs);
/** Height (px) of one waveform bar of an audio body / video strip; exposed for tests. */
float wavePeak(const Clip& c, float srcMs, float msPerBar);

class Renderer {
public:
    void setSnapshot(Snapshot s) { snap_ = std::move(s); }
    void setAtlas(Atlas a) { atlas_ = std::move(a); }
    const Snapshot& snapshot() const { return snap_; }

    /** Draws the whole timeline slot into [dst] (RGBA_8888, [stride] bytes per row). */
    void draw(uint8_t* dst, int32_t dstW, int32_t dstH, int32_t stride, const View& v) const;

    /** Hit test at surface px (x, y); returns the Zone and sets [track] / [clip] (-1 when n/a). */
    Zone hitTest(float x, float y, const View& v, int32_t* track, int32_t* clip) const;

private:
    Snapshot snap_;
    Atlas atlas_;
};

}  // namespace timeline
}  // namespace am
