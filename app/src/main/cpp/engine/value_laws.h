#pragma once

// Migration stage 4 (build 247): the remaining model/ value laws — the pure
// decision / geometry rules of Ducking, NoiseReduction, SpatialAlign,
// ProjectMarkers, OnionSkin, CropBounds, GifExport, ClipClipboard's paste
// law, the BlendModes table, FxLists / FxTileArt, PagerGrid, ToolRowSplit,
// UsedTools, ShapeEditValues, TextPaintRules, CanvasMarkRule, VectorDraw,
// PathEdit / PenPath and MaskGeometry — ported from the Kotlin originals in
// model/. Every function here is a line-by-line port: JVM/Kotlin number
// semantics are replicated exactly (IEEE float comparisons in
// coerceIn/coerceAtLeast — NaN passes through; total-order compareTo in
// minOf/maxOf/sortedBy — NaN is greatest, -0.0 < +0.0; saturating
// float->long/int casts; wrapping long arithmetic). Compiled with
// -ffp-contract=off like the earlier migration modules.

#include <cstdint>
#include <vector>

namespace am {
namespace laws {

// ---- JVM number-semantics helpers ---------------------------------------------------------

// Saturating casts (Kotlin Float/Double -> Long/Int): NaN -> 0, out-of-range clamps.
int64_t f2lSat(float v);
int64_t d2lSat(double v);
int32_t f2iSat(float v);
int32_t d2iSat(double v);
// Math.round(float) -> int / Math.round(double) -> long (floor(x + 0.5), saturating, NaN -> 0).
int32_t roundF(float v);
int64_t roundD(double v);
// Total order (Kotlin compareTo for Float): NaN greatest, -0.0 < +0.0.
bool totalLessF(float a, float b);
void sortFloatsTotal(float* v, int n);

// ---- Ducking.kt ---------------------------------------------------------------------------

float duckFloorGain(float amountDb);
double duckAttackMs(float smoothness);
double duckReleaseMs(float smoothness);
float duckTargetGain(float level, float floor);
// levels[k] = peaks[sourceStep(centre(k))] * max(heard[k], 0); heard has one entry per step.
std::vector<float> duckTimelineLevels(const float* peaks, int nPeaks, int64_t durationMs,
                                      float speed, const float* heard, int nHeard);
float duckGainAt(const int64_t* times, const float* gains, int n, int64_t relMs);

// ---- NoiseReduction.kt --------------------------------------------------------------------

float noiseOverSubtraction(float strength);
float noiseRawGain(float magnitude, float noise, float strength);
std::vector<float> noiseSmoothFreq(const float* raw, int n);
double noiseStepCoef(double tauMs);
float noisePercentile(const float* values, int n, double p);
// frames: nFrames consecutive frames of `bins` magnitudes each.
std::vector<float> noiseProfile(const float* frames, int nFrames, int bins);
int noiseBucketOf(float magnitude);
float noiseBucketValue(int b);
// counts: bins * 256 histogram, mutated in place (ProfileAccumulator.add).
void noiseAccumAdd(int32_t* counts, const float* mags, int bins);
std::vector<float> noiseAccumProfile(const int32_t* counts, int64_t frames, int bins);
float noiseHann(int i);
std::vector<float> noiseWindow();

// ---- SpatialAlign.kt (the alignDeltas / distributeDeltas laws; deltas() is scene_math) ----
// rects: 4 floats per rect (left, top, right, bottom). Output: [idx, dx, dy] triplets in the
// original's map-insertion order.

std::vector<float> spatialAlignDeltas(const float* rects, int n, int32_t action);
std::vector<float> spatialDistributeDeltas(const float* rects, int n, bool horizontal);

// ---- ProjectMarkers.kt --------------------------------------------------------------------

struct MarkerNormalize {
    std::vector<int32_t> perm;  // indices into the input, kept + stably sorted by time
};
MarkerNormalize markerNormalize(const int64_t* times, int n);
// Index of the nearest marker within tolerance, or -1 (minByOrNull first-minimal semantics).
int32_t markerNearest(const int64_t* times, int n, int64_t timeMs, int64_t tolMs);

// ---- OnionSkin.kt -------------------------------------------------------------------------

void onionNormalize(int32_t countPerSide, float opacity, int32_t* outCount, float* outOpacity);
int64_t onionFrameStepMs(int32_t fps, int32_t step);
struct OnionGhost {
    int64_t timeMs;
    int32_t side;
    int32_t step;
    float alpha;
    float hueDeg;
};
std::vector<OnionGhost> onionPlan(int64_t playheadMs, int32_t fps, int64_t durationMs,
                                  int32_t countPerSide, float baseOpacity);

// ---- CropBounds.kt ------------------------------------------------------------------------
// Inputs are the clip's four crop percents + the box. Returns false for "null" (no bounds).

bool cropVisibleBox(float cwPct, float chPct, float cHPct, float cVPct,
                    float boxW, float boxH, float out[4]);  // [width, height, offX, offY]
bool cropVisibleRect(float cwPct, float chPct, float cHPct, float cVPct,
                     float boxW, float boxH, float out[4]);  // [left, top, width, height]

// ---- GifEncoder.kt (GifExport) --------------------------------------------------------------

int32_t gifDelayCs(int32_t fps);
void gifSize(int32_t srcW, int32_t srcH, int32_t out[2]);

// ---- Path segments / VectorDraw.kt / PathEdit.kt -------------------------------------------
// Segment input pack mirrors ShapeBooleanNative.packSegments: ops[i], counts[i] floats of
// pts per segment, pts concatenated (offsets are prefix sums of counts).
// Segment output is frame_eval's packed stream: [op, ptCount, pts...] per segment.

struct SegPack {
    const int32_t* ops;
    const int32_t* counts;
    const float* pts;
    int n;
};

std::vector<float> vectorBuildStream(const float* rawXy, int nPts, float aspect,
                                     bool smooth, bool close);

struct NormalizeResult {
    bool ok = false;
    float offsetX = 0, offsetY = 0, shapeScale = 0;
    std::vector<float> segStream;
    std::vector<float> flatFrame;  // (u / aspect, v) pairs
};
NormalizeResult vectorNormalize(const SegPack& pack, float canvasW, float canvasH, float density);

// Anchors: one float record of 9 per anchor in the blob form used by the bridge:
// [x, y, inX, inY, outX, outY, hasIn, hasOut, smooth] (handle coords 0 when absent).
struct AnchorRec {
    float x, y, inX, inY, outX, outY;
    bool hasIn, hasOut, smooth;
};
struct SubpathRec {
    std::vector<AnchorRec> anchors;
    bool closed = false;
};
using PathRec = std::vector<SubpathRec>;

PathRec pathFromSegments(const SegPack& pack);
std::vector<float> pathToSegments(const PathRec& path);
PathRec pathMoveAnchor(const PathRec& path, int32_t sub, int32_t idx, float x, float y);
PathRec pathMoveHandle(const PathRec& path, int32_t sub, int32_t idx, bool out, float x, float y);
// Returns the new path; inserted index via outInserted (-1 when refused).
PathRec pathSplitSegment(const PathRec& path, int32_t sub, int32_t seg, float t, int32_t* outInserted);
PathRec pathDeleteAnchor(const PathRec& path, int32_t sub, int32_t idx);
// PenPath list ops (a single subpath's anchor list).
std::vector<AnchorRec> penTap(const std::vector<AnchorRec>& anchors, float x, float y);
std::vector<AnchorRec> penPull(const std::vector<AnchorRec>& anchors, float x, float y);
std::vector<AnchorRec> penReleaseTap(const std::vector<AnchorRec>& anchors);
std::vector<AnchorRec> penUndoPoint(const std::vector<AnchorRec>& anchors);
NormalizeResult penBuild(const std::vector<AnchorRec>& anchors, bool closed,
                         float canvasW, float canvasH, float density);

// ---- MaskGeometry.kt ------------------------------------------------------------------------
// Frame params derived from a MaskState + aspect by maskFrameInit.

struct MaskFrameP {
    float cx, cy, hw, hh, cosR, sinR, aspect;
};
MaskFrameP maskFrameInit(float sCx, float sCy, float sW, float sH, float sRot, float aspect);
void maskToPicture(const MaskFrameP& f, float lx, float ly, float out[2]);
void maskToLocal(const MaskFrameP& f, float px, float py, float out[2]);
// Mask shape inputs shared by the outline/handle ops.
struct MaskShapeP {
    int32_t shape;
    float corner;
    int32_t sides;
    float inner;
    const float* points;  // control-point xy pairs (picture-box units)
    int nPoints;
    bool smooth;
};
std::vector<float> maskHandles(const MaskFrameP& f, const MaskShapeP& m);   // [kind, x, y]*
std::vector<float> maskOutline(const MaskFrameP& f, const MaskShapeP& m);   // xy pairs
bool maskContains(const MaskFrameP& f, const MaskShapeP& m, float px, float py, float lineTol);
// Drags: out = the new MaskState's [cx, cy, w, h]. The original state rides along for the
// fields a drag leaves untouched (the MIRROR edge drag keeps cx/cy/w from the state).
void maskDragCorner(const MaskFrameP& f, int32_t i, float px, float py, float out[4]);
void maskDragEdge(const MaskFrameP& f, const MaskShapeP& m, const float state[4],
                  int32_t i, float px, float py, float out[4]);
float maskRotateTo(const MaskFrameP& f, float px, float py);
void maskMovedBy(float startCx, float startCy, float fromX, float fromY,
                 float toX, float toY, float aspect, float out[2]);
// dragPoint: writes the new control-point xy array into outPts; false when i is out of range.
bool maskDragPoint(const MaskFrameP& f, const float* pts, int nPts, int32_t i,
                   float px, float py, std::vector<float>* outPts);
// insertPointNear: false when refused; otherwise outPts + outIndex.
bool maskInsertPointNear(const MaskFrameP& f, const float* pts, int nPts,
                         float px, float py, float tol,
                         std::vector<float>* outPts, int32_t* outIndex);
int32_t maskHitHandle(const MaskFrameP& f, const MaskShapeP& m, float px, float py, float radius);
float distToSegment(float px, float py, float ax, float ay, float bx, float by);
// MaskLayerMap.
void layerMapOf(float cwPct, float chPct, float cHPct, float cVPct, float out[4]);  // [cropL, cropT, cropW, cropH]
void layerMapApply(const float map[8], bool toBox, float a, float b, float out[2]);
// ClipMask.neighbourKey over key times.
int32_t maskNeighbourKey(const int64_t* times, int n, int64_t relMs, bool forward, int64_t tolMs);

// ---- TextOnPath.kt (the ArcTable build + pointAt; layout() is scene_math) ---------------------

struct ArcTable {
    std::vector<float> xy;   // kept points
    std::vector<float> cum;  // cumulative arc length per kept point
    float total = 0;
};
ArcTable arcTableBuild(const float* xy, int nPts);
// out = [x, y, angleDeg]; n = kept point count (xy/cum length).
void arcPointAt(const float* xy, const float* cum, int n, float total, float d, float out[3]);

// ---- ClipClipboard.kt -----------------------------------------------------------------------

int64_t clipboardEarliest(const int64_t* times, int n);
int64_t clipboardPasteShift(int64_t earliest, int64_t anchorRelMs, int64_t durationMs);
struct KeysPack {
    std::vector<int64_t> times;
    std::vector<float> vals;  // stride 6 per key (frame_eval pack layout)
    std::vector<int32_t> tags;
};
KeysPack clipboardMergeProp(const KeysPack& target, const KeysPack& copied, int64_t shift);

// ---- BlendModes.kt (the table; find/inCategory stay thin Kotlin readers) ---------------------

int blendSpecCount();
const char* blendSpecName(int i);
const char* blendSpecCategory(int i);
int32_t blendSpecShaderId(int i);
int32_t blendSpecKind(int i);  // BlendKind ordinal: NORMAL 0, COLOR 1, MATTE 2
int blendCategoryCount();
const char* blendCategoryName(int i);

// ---- ToolRowSplit.kt ------------------------------------------------------------------------

int32_t toolTileWidth(int32_t labelLen);
int32_t toolAvailableDp(int32_t screenWidthDp, int32_t backZoneDp, int32_t contentPaddingDp);
int32_t toolVisibleCount(const int32_t* widths, int n, int32_t availableDp, int32_t gapDp);
int32_t toolGridHeight(int32_t itemCount, int32_t tileHeightDp, int32_t gapDp,
                       int32_t vPaddingDp, int32_t maxRows, int32_t columns);
int32_t toolSharedPanelHeight(int32_t clipOverflowCount, int32_t utilityCount);

// ---- PagerGrid.kt -----------------------------------------------------------------------------

std::vector<int32_t> pagerRowCounts(int32_t count, int32_t columns);

// ---- UsedTools.kt -----------------------------------------------------------------------------
// The raw clip fields + the two constant comparisons the wrapper cannot do natively
// (fillMode is an Int constant compare — it rides as an input boolean). Returns the
// tool bitmask in UsedTools' insertion order: effects 1, animations 2, color 4,
// transform 8, crop 16, blend 32, masks 64, volume 128, cutout 256, parent 512.

int32_t usedToolsMask(int32_t effectsCount, bool hasAnim, bool fillNotSolid, int64_t textColor,
                      bool hasKeys, float tx, float ty, float rot, float scale,
                      float rot3dX, float rot3dY, float tz,
                      float cropW, float cropH, bool blendNotNormal, float opacity,
                      int32_t masksCount, float volume, bool muted, bool hasCutout, bool hasParent);

// ---- ShapeEditValues.kt -----------------------------------------------------------------------

float shapeCoerceCorner(float dp);
void shapeCoerceTrim(float start, float end, float out[2]);

// ---- TextPaintRules.kt ------------------------------------------------------------------------
// Returns false for "null" (nothing painted).

bool textBgArgb(int64_t bgColor, float bgOpacity, int32_t* outArgb);
float textLetterSpacingEm(float percent);
float textLetterSpacingPx(float fontPx, float percent);

// ---- CanvasMark.kt ------------------------------------------------------------------------------
// LayerType ordinals NULL 6, GROUP 9, CONTROLLER 10; CanvasMark ordinals NOTHING 0,
// MARKER 1, SELECTION_BOX 2.

int32_t canvasMark(int32_t typeOrdinal, bool selected);

}  // namespace laws
}  // namespace am
