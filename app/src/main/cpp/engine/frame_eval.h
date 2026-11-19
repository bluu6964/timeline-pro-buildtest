#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace am {

// Step 2A: Native Frame Evaluation & Parametric Geometry Suite.
// Pure C++17 implementations matching Keyframes.kt, SpeedRamp.kt, AmFxEval.kt,
// MotionBlurEval.kt, RepeatEval.kt, TextAnimEval.kt, TextProgressEval.kt,
// CounterEval.kt, PieOutline.kt, ArcOutline.kt, CalloutOutline.kt, GradGeometry.kt.

constexpr float kMinSpeed = 0.1f;
constexpr float kMaxSpeed = 4.0f;
constexpr int kRepeatMaxCount = 50;

constexpr int kRepeatModePlain = 0;
constexpr int kRepeatModeRadial = 1;
constexpr int kRepeatModeScatter = 2;
constexpr int kRepeatModeLine = 3;

constexpr int kGradLinear = 0;
constexpr int kGradRadial = 1;
constexpr int kGradSweep = 2;

constexpr int kPathOpMove = 0;
constexpr int kPathOpLine = 1;
constexpr int kPathOpQuad = 2;
constexpr int kPathOpCubic = 3;
constexpr int kPathOpClose = 4;

/** CSS / Alight cubic-bezier(x1, y1, x2, y2) progress for t in [0, 1]. */
float easingApply(float x1, float y1, float x2, float y2, float t);

/** True when (x1, y1, x2, y2) is Easing.LINEAR within tolerance. */
bool isLinearEasing(float x1, float y1, float x2, float y2);

/**
 * Evaluates a keyframe track at relMs.
 * vals layout (stride 6 per key): [a, b, x1, y1, x2, y2].
 * When isColor is true, (a, b) is unpacked as ARGB, interpolated per channel, and repacked.
 */
void keyframeValueAt(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t relMs,
    bool isColor,
    float& outA,
    float& outB);

/** Integrates a SPEED keyframe curve using 20 ms midpoint steps. */
int64_t speedSourceOffsetMs(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t relMs);

/** Generates the 512-frame / 48 kHz per-block audio speed ramp table. */
std::vector<float> speedRampTable(
    const int64_t* timesMs,
    const float* vals,
    int count,
    int64_t durationMs);

/** Evaluates one AmFxEval values-tail keyframe track starting at `start`. */
bool amFxTrackValueAt(
    const float* values,
    int length,
    float rel,
    int start,
    float& outValue);

/** MotionBlurEval.blurAt math given pose pair (p0, p1). */
bool motionBlurPos(
    float p0x, float p0y,
    float p1x, float p1y, float p1Rot,
    float canvasHeightPx, float tune,
    float& outAngleDeg, float& outLength);

/** MotionBlurEval.radialAt math given pose pair (p0, p1). */
bool motionBlurRadial(
    float p0Rot, float p0Scale,
    float p1Rot, float p1Scale,
    float tune, bool useScale, bool useAngle,
    float& outAmount, float& outZoomPct);

struct Mb4Resolved {
    bool hasPos = false;
    float posPx = 0.0f;
    float posPy = 0.0f;

    bool hasScale = false;
    float scaleVel = 0.0f;

    bool hasAngle = false;
    float angleDeg = 0.0f;
};

/** MotionBlurEval.mb4 3-stage velocity decomposition. */
Mb4Resolved motionBlur4(
    float p0x, float p0y, float p0Rot, float p0Scale,
    float p1x, float p1y, float p1Rot, float p1Scale,
    float canvasHeightPx,
    bool usePos, bool useScale, bool useAngle);

struct RepeatCopyPose {
    float x = 0.0f;
    float y = 0.0f;
    float rotDeg = 0.0f;
    float scalePercent = 100.0f;
    float opacity = 100.0f;
};

struct RepeatEvalOutput {
    std::vector<RepeatCopyPose> copies;
    bool hasAltColor = false;
    uint32_t altColorArgb = 0;
};

/**
 * Evaluates RepeatEval (plain / radial / scatter / line) including outer similarity transform.
 * timeMs == INT64_MIN behaves like Kotlin Long.MIN_VALUE (static scatter at v0).
 */
RepeatEvalOutput repeatEval(
    int mode,
    const float* fxVals, int fxLen,
    float clipX, float clipY, float clipRot, float clipScale, float clipOp,
    float undoRotDeg, float outerScale, float outerRotDeg,
    int64_t clipStartMs, int64_t clipEndMs, int64_t timeMs);

/** RepeatEval.groupCopyOffset. */
void groupCopyOffset(
    float anchorX, float anchorY,
    float dx, float dy, float rotDeg, float scaleMul, float k,
    float& outX, float& outY);

/**
 * TextAnimEval.valueAt for multi-component keyframes.
 * keyVals stride = dims; keyEasings stride = 4 (only read if keyHasEasing[i] != 0).
 */
void textAnimValueAt(
    const float* keyTimes,
    const float* keyVals,
    int keyCount,
    int dims,
    const uint8_t* keyHasEasing,
    const float* keyEasings,
    bool stepOnly,
    float p,
    float* outVals);

/** TextProgressEval.visibleRange: outputs [outFirst, outLast] or [-1, -1] if empty. */
void textProgressVisibleRange(
    int count, float start, float end,
    int& outFirst, int& outLast);

/**
 * CounterEval.substitute over UTF-16 code units (exact match with Kotlin String).
 * Replaces every ASCII number token with (val * scale + offset) keeping decimals & grouping.
 */
std::u16string counterSubstituteUtf16(
    const uint16_t* chars,
    int length,
    double offset,
    double scale);

/** PieOutline.sweepDegrees / ArcOutline.sweepDegrees (shared wrap law). */
double pieArcSweepDegrees(double startDeg, double endDeg);

/**
 * Packed PathSeg stream: repeated [op, ptCount, p0, p1, ...].
 * PieOutline.segments(startDeg, endDeg, radius).
 */
std::vector<float> pieOutlineSegments(double startDeg, double endDeg, double radius);

/** ArcOutline.segments(startDeg, endDeg, radius, closed). */
std::vector<float> arcOutlineSegments(double startDeg, double endDeg, double radius, bool closed);

/** CalloutOutline.segments(w, h, tailX, tailY, cornerRadius, tailWidth). */
std::vector<float> calloutOutlineSegments(
    double width, double height,
    double tailX, double tailY,
    double cornerRadius, double tailWidth);

/** GradGeometry.endpoints -> out4 = [x1, y1, x2, y2]. */
void gradEndpoints(
    int gradType, float cx, float cy, float angleDeg, float scale,
    float out4[4]);

/** GradGeometry.angleScale -> out2 = [angleDeg, scale]. */
void gradAngleScale(
    int gradType, float x1, float y1, float x2, float y2,
    float out2[2]);

} // namespace am
