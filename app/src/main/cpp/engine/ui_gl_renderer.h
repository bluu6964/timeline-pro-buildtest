#pragma once

#include <android/native_window.h>
#include <GLES3/gl3.h>

#include <cstdint>
#include <unordered_map>
#include <vector>

#include "egl_core.h"

namespace am {

// Command opcodes for UiGlRenderer::drawVectorCommands().
// Colors are passed in a separate ARGB int32 array so 32-bit colors never lose precision in float32.
enum UiVectorCmd : int32_t {
    UI_CMD_LINE        = 1,  // floats: x0, y0, x1, y1, width, dashOn, dashOff, capRound | colors: strokeArgb
    UI_CMD_POLYLINE    = 2,  // floats: closed, width, dashOn, dashOff, capRound, count, x0, y0, ... | colors: strokeArgb
    UI_CMD_CIRCLE      = 3,  // floats: cx, cy, radius, strokeWidth | colors: fillArgb, strokeArgb
    UI_CMD_ROUND_RECT  = 4,  // floats: cx, cy, width, height, cornerRadius, strokeWidth | colors: fillArgb, strokeArgb
    UI_CMD_RECT        = 5,  // floats: x, y, width, height | colors: fillArgb
    UI_CMD_CUBIC       = 6,  // floats: x0, y0, c1x, c1y, c2x, c2y, x1, y1, width, capRound | colors: strokeArgb
    UI_CMD_ARC         = 7,  // floats: cx, cy, rx, ry, startDeg, sweepDeg, width, capRound, closedPie | colors: fillArgb, strokeArgb
    UI_CMD_FILL_POLY   = 8,  // floats: count, x0, y0, ... | colors: fillArgb
    UI_CMD_DIMMER_HOLE = 9,  // floats: w, h, l, t, r, b | colors: dimArgb
    UI_CMD_SHADER_QUAD = 10, // floats: x, y, w, h, cornerR, mode, p0, p1, p2, p3, p4, p5 | colors: c0Argb, c1Argb
    UI_CMD_GRAD_RECT   = 11, // floats: x, y, w, h, vertical | colors: c0Argb, c1Argb
    // Build 249 (UI migration stage 2): one atlas blit. floats: entry, dstX, dstY,
    // srcDX, srcDY, srcW, srcH, dstW, dstH (src* = sub-rect inside the atlas entry,
    // entry-relative px; dstW/dstH <= 0 = natural src size) | colors: tintArgb.
    // The atlas holds white glyphs / icon shapes (alpha = coverage); the tint
    // supplies the colour. Skipped silently when no atlas is set or the entry
    // index is out of range, so command streams stay valid before the first upload.
    UI_CMD_TEXT        = 12
};

// Shared single-context OpenGL ES 3 renderer for high-FPS 2D vector, plot & UI widget surfaces.
// Must be created, invoked, and destroyed on ONE dedicated GL thread (UiGlRuntime's GlThread).
class UiGlRenderer {
public:
    UiGlRenderer() = default;
    ~UiGlRenderer();

    UiGlRenderer(const UiGlRenderer&) = delete;
    UiGlRenderer& operator=(const UiGlRenderer&) = delete;

    bool init();
    void release();

    // Takes ownership of ANativeWindow* (from ANativeWindow_fromSurface). Returns > 0 surfaceId or 0 on failure.
    int attachSurface(ANativeWindow* window, int w, int h);
    void resizeSurface(int surfaceId, int w, int h);
    void detachSurface(int surfaceId);

    bool drawScopeHistogram(
        int surfaceId,
        const int32_t* histR, const int32_t* histG,
        const int32_t* histB, const int32_t* histY, int bins);

    bool drawScopeWaveform(
        int surfaceId,
        const int32_t* wave, int waveW, int waveH, int bins);

    bool drawScopeVectorscope(
        int surfaceId,
        const int32_t* vec, int grid);

    bool drawEasingGraph(
        int surfaceId,
        float x1, float y1, float x2, float y2,
        float progress, float density,
        float padL, float padT, float padR, float padB);

    bool drawRulerDial(
        int surfaceId,
        float value, float minVal, float maxVal, float step,
        float tickSpacingPx, bool accentNeedle, float density);

    bool drawPositionPad(
        int surfaceId,
        float xNorm, float yNorm,
        int32_t dotArgb, int32_t gridArgb, float density);

    bool drawCropOverlay(
        int surfaceId,
        float lNorm, float tNorm, float rNorm, float bNorm,
        float density);

    bool drawWaveformStrip(
        int surfaceId,
        const float* peaks, int peakCount,
        int32_t bgTopArgb, int32_t bgBotArgb, int32_t barArgb, float density);

    bool drawEasingTile(
        int surfaceId,
        float x1, float y1, float x2, float y2,
        int32_t strokeArgb, float density);

    bool drawVectorCommands(
        int surfaceId,
        int32_t clearArgb,
        const float* cmds, int cmdFloatCount,
        const int32_t* colors, int colorCount);

    // Build 249: installs (or replaces) the shared UI label/icon atlas.
    // [rgba] is the atlas bitmap's pixels (RGBA_8888, w * h * 4, row-major,
    // top row first); [rects] holds 4 ints per entry (x, y, w, h inside the
    // bitmap). Entries are clamped into the bitmap's bounds. Must be called
    // on the GL thread (UiGlRuntime posts); the texture lives in the renderer's
    // single EGL context, so every surface can blit from it. Passing
    // entryCount <= 0 clears the entries (blits skip until the next upload).
    void setAtlas(const uint8_t* rgba, int w, int h, const int32_t* rects, int entryCount);

    // Offscreen FBO self-test verifying GLES3 compilation and rasterization for:
    // outCounts[0] = histogram non-bg pixels
    // outCounts[1] = waveform + vectorscope phosphor pixels
    // outCounts[2] = easing graph non-transparent pixels
    // outCounts[3] = vector command stream + shader quad non-transparent pixels
    bool runOffscreenSelfTest(int w, int h, int32_t outCounts[4]);

private:
    struct Rgba {
        float r, g, b, a;
    };

    struct FlatVertex {
        float x, y;
        float r, g, b, a;
        float cov;
    };

    struct SdfVertex {
        float x, y;
        float lx, ly;
        float p0, p1, p2, p3;
        float fr, fg, fb, fa;
        float sr, sg, sb, sa;
        float type, phase, capRound, pad;
    };

    struct PointVertex {
        float x, y;
        float r, g, b, a;
        float size;
    };

    struct QuadVertex {
        float x, y;
        float lx, ly;
        float w, h, cornerR, mode;
        float p0, p1, p2, p3;
        float p4, p5;
        float c0r, c0g, c0b, c0a;
        float c1r, c1g, c1b, c1a;
    };

    // Build 249: one atlas-blit vertex (position + atlas UV + tint).
    struct TexVertex {
        float x, y;
        float u, v;
        float r, g, b, a;
    };

    // Build 249: one atlas entry — a rect inside the atlas bitmap (px).
    struct AtlasEntry {
        int32_t x = 0, y = 0, w = 0, h = 0;
    };

    struct SurfaceSlot {
        ANativeWindow* window = nullptr;
        EGLSurface surface = EGL_NO_SURFACE;
        int w = 0;
        int h = 0;
    };

    static Rgba argbToRgba(int32_t argb);

    bool compileShaders();
    bool beginSurface(int surfaceId, int& outW, int& outH, EGLSurface& outSurf);
    void clearColor(int32_t argb);
    void resetBatches();
    void flushBatches(int w, int h);

    // Primitive batch builders (screen-pixel space, top-left origin)
    void addRect(float x, float y, float w, float h, Rgba fill);
    void addGradRect(float x, float y, float w, float h, bool vertical, Rgba c0, Rgba c1);
    void addTri(float x0, float y0, float x1, float y1, float x2, float y2, Rgba c);
    void addCircle(float cx, float cy, float radius, float strokeWidth, Rgba fill, Rgba stroke);
    void addRoundRect(float cx, float cy, float w, float h, float cornerR, float strokeWidth, Rgba fill, Rgba stroke);
    void addSegmentSdf(
        float x0, float y0, float x1, float y1,
        float width, float dashOn, float dashOff, float dashPhase, bool capRound, Rgba stroke);
    void addPolylineRibbon(
        const float* xy, int count, bool closed, float width, Rgba stroke);
    void addPolyline(
        const float* xy, int count, bool closed,
        float width, float dashOn, float dashOff, bool capRound, Rgba stroke);
    void addCubic(
        float x0, float y0, float c1x, float c1y,
        float c2x, float c2y, float x1, float y1,
        float width, bool capRound, Rgba stroke);
    void addArc(
        float cx, float cy, float rx, float ry,
        float startDeg, float sweepDeg, float width,
        bool capRound, bool closedPie, Rgba fill, Rgba stroke);
    void addFilledPolygon(const float* xy, int count, Rgba fill);
    void addDimmerHole(float w, float h, float l, float t, float r, float b, Rgba dim);
    void addShaderQuad(
        float x, float y, float w, float h, float cornerR, float mode,
        float p0, float p1, float p2, float p3, float p4, float p5,
        Rgba c0, Rgba c1);
    void addPoint(float x, float y, float size, Rgba c);
    // Build 249: queue one atlas blit (see UI_CMD_TEXT). No-op when the entry
    // is out of range, no atlas is installed, or the src rect is empty.
    void addTextBlit(
        int entry, float dstX, float dstY,
        float srcDX, float srcDY, float srcW, float srcH,
        float dstW, float dstH, Rgba tint);

    void buildQuarterGraticule(int w, int h);
    void buildHistogramGeometry(
        int w, int h,
        const int32_t* histR, const int32_t* histG,
        const int32_t* histB, const int32_t* histY, int bins);
    void buildWaveformGeometry(
        int w, int h,
        const int32_t* wave, int waveW, int waveH, int bins);
    void buildVectorscopeGeometry(
        int w, int h,
        const int32_t* vec, int grid);
    void buildEasingGeometry(
        int w, int h,
        float x1, float y1, float x2, float y2,
        float progress, float density,
        float padL, float padT, float padR, float padB);
    void buildRulerDialGeometry(
        int w, int h,
        float value, float minVal, float maxVal, float step,
        float tickSpacingPx, bool accentNeedle, float density);
    void buildPositionPadGeometry(
        int w, int h, float xNorm, float yNorm,
        int32_t dotArgb, int32_t gridArgb, float density);
    void buildCropOverlayGeometry(
        int w, int h, float lNorm, float tNorm, float rNorm, float bNorm, float density);
    void buildWaveformStripGeometry(
        int w, int h, const float* peaks, int peakCount,
        int32_t bgTopArgb, int32_t bgBotArgb, int32_t barArgb, float density);
    void buildEasingTileGeometry(
        int w, int h, float x1, float y1, float x2, float y2,
        int32_t strokeArgb, float density);
    void buildVectorCmdGeometry(
        const float* cmds, int cmdFloatCount,
        const int32_t* colors, int colorCount);

    EglCore egl_;
    bool initialized_ = false;
    EGLSurface currentSurf_ = EGL_NO_SURFACE;
    int nextSurfaceId_ = 1;
    std::unordered_map<int, SurfaceSlot> surfaces_;

    GLuint progFlat_ = 0;
    GLint uFlatViewport_ = -1;
    GLuint vaoFlat_ = 0;
    GLuint vboFlat_ = 0;

    GLuint progSdf_ = 0;
    GLint uSdfViewport_ = -1;
    GLuint vaoSdf_ = 0;
    GLuint vboSdf_ = 0;

    GLuint progPoint_ = 0;
    GLint uPointViewport_ = -1;
    GLuint vaoPoint_ = 0;
    GLuint vboPoint_ = 0;

    GLuint progQuad_ = 0;
    GLint uQuadViewport_ = -1;
    GLuint vaoQuad_ = 0;
    GLuint vboQuad_ = 0;

    // Build 249: atlas-text program + the shared atlas texture/entries.
    GLuint progTex_ = 0;
    GLint uTexViewport_ = -1;
    GLint uTexAtlas_ = -1;
    GLuint vaoTex_ = 0;
    GLuint vboTex_ = 0;
    GLuint atlasTex_ = 0;
    int atlasW_ = 0;
    int atlasH_ = 0;
    std::vector<AtlasEntry> atlasEntries_;

    std::vector<FlatVertex> flatVerts_;
    std::vector<SdfVertex> sdfVerts_;
    std::vector<PointVertex> pointVerts_;
    std::vector<QuadVertex> quadVerts_;
    std::vector<TexVertex> texVerts_;
};

}  // namespace am
