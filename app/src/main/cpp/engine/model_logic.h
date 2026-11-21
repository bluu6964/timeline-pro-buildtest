// model_logic: the pure value logic of the model/ package, migrated from
// Kotlin (migration #3 — LaneLayout / GroupOps / MultiSelectOps / BeatDetect /
// ExportRange / Keyframes value functions).
//
// Everything here works on plain values only: numbers and flat arrays of
// numbers / identity keys. The Kotlin wrappers keep their public signatures
// and do the object <-> array marshalling (a TimelineClip is never sent
// across JNI — the ClipFx precedent); each function below is the whole
// computation of the Kotlin original it replaces, replicated line for line,
// including the JVM's number semantics where they differ from C++'s
// (Float.toInt / Double.toLong saturation, wrapping Long arithmetic,
// abs(Long.MIN_VALUE)). Verified by golden input->output diffs against the
// Kotlin originals (floats/doubles compared as raw bits) in
// ~/workspace/after-motion-dev/model-migration-golden/.
//
// This TU is compiled with -ffp-contract=off (see CMakeLists): the easing
// bezier is strict-IEEE Kotlin float math, and FMA contraction on arm64
// would change its last bits.
#pragma once

#include <cstdint>
#include <vector>

namespace am {

// ---- Keyframes.kt: colour packing -----------------------------------------

/** ARGB colour -> (A,R) and (G,B) packed as two exact floats (each < 65536). */
void packColor(int64_t argb, float out[2]);

/** Inverse of packColor, with the original's coerceIn(0, 65535) per half. */
int64_t unpackColor(float pa, float pb);

// ---- Keyframes.kt: Easing curve maths --------------------------------------

/** The Easing.isLinear test (control points on the diagonal, at the thirds). */
bool easingIsLinear(float x1, float y1, float x2, float y2);

/** Point of the cubic bezier for parameter s: (bx(s), by(s)). */
void easingPointAt(float x1, float y1, float x2, float y2, float s, float out[2]);

/** The Easing.sameAs test: all four control points within eps. */
bool easingSameAs(float x1, float y1, float x2, float y2,
                  float ox1, float oy1, float ox2, float oy2, float eps);

// ---- Keyframes.kt: keyframe list searches (times only) ----------------------

/**
 * Index of the keyframe that starts the segment relMs is in
 * (times[i] <= relMs < times[i+1]), or -1 outside every segment / n < 2.
 */
int segmentIndexAt(const int64_t* times, int n, int64_t relMs);

/** Index of the keyframe relMs sits on (within tolMs), nearest wins, or -1. */
int indexOnTime(const int64_t* times, int n, int64_t relMs, int64_t tolMs);

// ---- BeatDetect.kt -----------------------------------------------------------

/**
 * Tempo of a beat list: 60000 / the median inter-beat interval, intervals
 * outside [minIntervalMs, maxIntervalMs] not voting, rounded to 0.1 BPM.
 * Returns false (no estimate) when fewer than 2 beats or no interval votes.
 */
bool estimateBpm(const int64_t* beats, int n, int64_t minIntervalMs,
                 int64_t maxIntervalMs, double* bpmOut);

/**
 * Merges detected beat marks into existing ones: a detected mark within
 * tolMs of an existing (or already-taken) one drops out. Sorted result.
 */
std::vector<int64_t> mergeBeats(const int64_t* existing, int nExisting,
                                const int64_t* detected, int nDetected,
                                int64_t tolMs);

/**
 * The inverse of TimelineClip.sourceOffsetMs under constant speed: maps
 * source offsets to layer-relative timeline times. spanMs is the clip's
 * sourceOffsetMs(durationMs), computed by the Kotlin wrapper through the
 * original law; speed is coerced to at least 0.01 here, as in the original.
 */
std::vector<int64_t> timelineMsForOffsets(int64_t durationMs, double speed,
                                          int64_t spanMs,
                                          const int64_t* offsets, int n);

// ---- ExportRange.kt -----------------------------------------------------------

/**
 * The export-range law: clamps both points into [0, totalMs] and accepts
 * them when they end up at least minLenMs apart. Returns false for a
 * missing point, a non-positive total, or a too-short range.
 */
bool exportRangeOf(bool inSet, int64_t inMs, bool outSet, int64_t outMs,
                   int64_t totalMs, int64_t minLenMs, int64_t pairOut[2]);

// ---- MultiSelectOps.kt ---------------------------------------------------------

/** Multi-bar mode codes: 0 = SPLIT, 1 = START, 2 = END (MultiBarMode order in the wrapper). */
int barMode(const int64_t* startsMs, const int64_t* endsMs, int n, int64_t playheadMs);

/**
 * The deletion SET of MultiSelectOps.deleteChecked over the flat clip list:
 * the checked clips, plus a checked Chain's members, plus a checked Group's
 * descendants (the descendantsInto traversal below), minus the LOCKED clips
 * inside the set (counted into sparedOut). Returns the surviving set as
 * ascending flat indices. Clips are addressed by identity keys: clipKeys[i]
 * is the key of clip i's id, chainKeys/groupKeys[i] the key of its
 * chainId/groupId string (or -1 when null or not any clip's id — such a key
 * can never be a chain parent or enter a group frontier). typeCodes:
 * 1 = CHAIN, 2 = GROUP, 0 = anything else.
 */
std::vector<int> deleteSet(const int* clipKeys, const int* chainKeys,
                           const int* groupKeys, const int* typeCodes,
                           const bool* locked, const bool* checked,
                           int n, int keyCount, int* sparedOut);

// ---- GroupOps.kt -----------------------------------------------------------------

/**
 * GroupOps.descendantsOf as an index traversal: every clip inside the group
 * whose id key is rootKey, also groups inside it, in the original's
 * discovery order (level by level, flat order inside a level, at most 16
 * levels). groupKeys[i] is the key of clip i's groupId string (or -1);
 * isGroup[i] marks Group layers (their own keys join the next frontier).
 */
std::vector<int> descendantsOf(const int* clipKeys, const int* groupKeys,
                               const bool* isGroup, int n, int keyCount,
                               int rootKey);

// ---- LaneLayout.kt -----------------------------------------------------------------

/**
 * LaneLayout.isNormalized over the flat clip list: laneOffsets has
 * laneCount+1 entries (lane l covers [laneOffsets[l], laneOffsets[l+1])).
 * Per clip: startMs, isChain (type == CHAIN), chainIdNull, and
 * chainIdTarget — the FLAT index of the same-lane clip whose id equals this
 * clip's chainId, or -1. The check mirrors the Kotlin original condition
 * for condition (see the audit): non-empty lanes, start order, a plain lane
 * holding exactly one unchained clip, a chain lane holding exactly the
 * chain followed by its members in start order.
 */
bool isNormalized(const int* laneOffsets, int laneCount, const int64_t* startMs,
                  const bool* isChain, const bool* chainIdNull,
                  const int* chainIdTarget);

}  // namespace am
