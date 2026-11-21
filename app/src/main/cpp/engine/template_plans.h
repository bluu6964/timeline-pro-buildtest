// Migration stage 3 (build 246): the template builders' laws, ported from
// model/*Template.kt + model/UserTemplate.kt. Each builder's computation
// (timings, geometry, keyframe plans, text/graphic parameters) runs here and
// returns ONE flat construction plan (a little-endian byte buffer, see
// template_plans.cpp for the layout); the Kotlin wrapper
// (engine/TemplatePlansNative.kt) decodes the plan and assembles the
// TimelineClip graph, minting the ClipFx UUIDs exactly where the originals
// minted them. Untouched clip fields never cross.
//
// The ports are expression-by-expression: Kotlin Float ops are IEEE
// single-precision (each op rounded), Double ops IEEE double; the TU is
// compiled -ffp-contract=off (see CMakeLists) so C++ float math matches the
// JVM original bit for bit. Strings cross as UTF-8 bytes (the templates
// carry Bengali sample texts) — no lossy conversion anywhere.
//
// The UserTemplateOps rules (capture normalisation / instantiate remap /
// media reference collection) are the same family as stage 2's graph plans:
// clips cross as identity keys, the native side returns per-clip decisions,
// and the Kotlin wrapper applies them with the original copy() calls.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace am {

// ---- shared plan primitives (defined in template_plans.cpp) ----

struct PlanWriter {
    std::vector<uint8_t> buf;
    void u8(uint8_t v);
    void i32(int32_t v);
    void i64(int64_t v);
    void f32(float v);
    void f64(double v);
    void str(const std::string& s);  // u32 byte length + UTF-8 bytes
};

// One keyframe in a plan (Keyframe's full field set).
struct PlanKey {
    int64_t timeMs;
    float a, b;
    float e1, e2, e3, e4;  // easing control points
    int32_t tag;
};

// One text-animator property key (AnimKey).
struct PlanAnimKey {
    float t;
    std::vector<float> v;
    bool hasEasing;
    float e1, e2, e3, e4;
};

// One text animator (TextAnimator).
struct PlanAnimator {
    std::string effectId;
    float refSize;
    struct Prop {
        std::string name;
        std::string type;
        std::vector<float> value;
        std::vector<PlanAnimKey> keys;
    };
    std::vector<Prop> props;
};

// Writes: i32 animator count, then each animator in the plan layout.
void writeAnimators(PlanWriter& w, const std::vector<PlanAnimator>& animators);

// ---- the builders: each returns a complete plan byte buffer ----
// Plan layout: i32 version (=1), i32 meta count + meta ints, i32 clip count,
// then one record per clip in lanesTopFirst order (group first). The record
// layout is documented at writeClip() in template_plans.cpp.

std::vector<uint8_t> carouselPlan(float pw, float ph, const std::string& backgroundUri,
                                  const std::vector<std::string>& cardUris, int64_t insertAtMs,
                                  const std::string& idBase, int32_t cardCount, int64_t durationMs,
                                  bool clockwise);
std::vector<uint8_t> popTitlePlan(float pw, int64_t insertAtMs, const std::string& idBase);
std::vector<uint8_t> logoRevealPlan(float pw, const std::string& logoUri, int64_t insertAtMs,
                                    const std::string& idBase);
std::vector<uint8_t> wordPopPlan(float pw, int64_t insertAtMs, const std::string& idBase);
std::vector<uint8_t> geniePlan(float pw, float ph, const std::vector<std::string>& avatarUris,
                               const std::vector<std::string>& photoUris, int64_t insertAtMs,
                               const std::string& idBase);
std::vector<uint8_t> pieCounterPlan(float pw, float ph, int64_t insertAtMs,
                                    const std::string& idBase);
std::vector<uint8_t> donutPlan(float pw, float ph, int64_t insertAtMs, const std::string& idBase);
std::vector<uint8_t> socialGrowthPlan(float pw, float ph, const std::string& avatarUri,
                                      int64_t insertAtMs, const std::string& idBase);
std::vector<uint8_t> lowerThirdPlan(float pw, float ph, int64_t insertAtMs,
                                    const std::string& idBase);

// ---- Spec law functions (the same code the builders use internally) ----

double carouselProgress(double x);
float carouselRingRadius(int32_t cardCount);
// Writes (gx, gy, fit) into out[3].
void carouselFit(float pw, float ph, float radius, float out[3]);
double carouselAngleDegAt(int64_t tMs, int32_t cardIndex, int32_t cardCount, int64_t durationMs,
                          bool clockwise);
void carouselCenterSceneAt(int64_t tMs, int32_t cardIndex, int32_t cardCount, int64_t durationMs,
                           bool clockwise, float out[2]);

int64_t wordPopStartMs(int32_t index);
float wordPopAdvancePx(int32_t index, float pw);
float wordPopTotalWidthPx(float pw);
float wordPopCentreXPx(int32_t index, float pw);

int64_t genieCardStartMs(int32_t index);
float genieAvatarU(int32_t index, float pw);
float genieScrollAt(int64_t relMs, float pw);
void genieBarPoint(float u, float v, float pw, float ph, float out[2]);
void genieAvatarPosAt(int32_t index, int64_t relMs, float pw, float ph, float out[2]);
void genieAvatarNamePosAt(int32_t index, int64_t relMs, float pw, float ph, float out[2]);
void geniePetalOffset(int32_t k, float pw, float out[2]);
void genieBarEndFlowerCentre(float pw, float ph, float out[2]);
void genieCardPoint(float lx, float ly, float pw, float ph, float out[2]);

float piePercentAt(int64_t relMs);
// The PieCounter counter animator as a standalone animator blob
// (i32 count = 1 + the animator, the writeAnimators layout).
std::vector<uint8_t> pieCounterAnimatorBlob();
// The SocialGrowth counter animator for (t, v) key pairs, same blob layout.
std::vector<uint8_t> counterAnimatorBlob(const std::vector<float>& tvPairs);

void socialTilePos(int32_t index, float pw, float ph, float out[2]);
void donutLabelPos(int32_t index, float pw, float ph, float out[2]);
float donutLegendY(int32_t index, float ph);
// The 14 LowerThird layout values, in LowerThirdSpec's terms:
// [fontPx, nameLineHPx, subtitleLineHPx, barHeightPx, blockBottomPx,
//  leftEdgePx, textLeftPx, barCentreXPx, barCentreYPx, nameCentreXPx,
//  nameCentreYPx, subtitleCentreXPx, subtitleCentreYPx, slidePx]
void lowerThirdLayout(float pw, float ph, float out[14]);

// ---- UserTemplateOps plans ----

// The ordered, de-duplicated media references of a clip list. refs holds
// 6 slots per clip (uri, fillImageUri, fillVideoUri, planarRemove patchPath,
// cutoutDir, faceBlurDir); an empty string stands for a null reference.
// Returns a blob: i32 count, then per reference (u32 length + UTF-8 bytes).
std::vector<uint8_t> userMediaRefs(const std::vector<std::string>& refs, int32_t clipCount);

// The capture normalisation decisions. All key arrays are per clip; a link's
// key indexes keyToClip (the clip index carrying that id, or -1 when the id
// names no clip of the pack = outside / dangling). has* flags mark which
// references exist at all. groupKey is the capture target group's key (the
// root test is idKey == groupKey); prSelf marks pin.videoId == the clip's
// own id. Returns 7 ints per clip: parentMode (0 null, 1 keep, 2 REST link
// to the clip's groupId), keepGroupId, keepController, keepMaskTarget,
// keepTrackFollow, keepCornerPin, keepPlanarRemove.
std::vector<int32_t> userCapturePlan(
    const std::vector<int32_t>& idKey, const std::vector<int32_t>& groupKey,
    const std::vector<int32_t>& parentKey, const std::vector<int32_t>& controllerKey,
    const std::vector<int32_t>& maskKey, const std::vector<int32_t>& tfKey,
    const std::vector<int32_t>& cpKey, const std::vector<int32_t>& prKey,
    const std::vector<int32_t>& keyToClip, const std::vector<uint8_t>& has,
    int32_t groupKeyOf, const std::vector<uint8_t>& prSelf);

// The instantiate remap: returns shift (insertAtMs - the root's startMs, the
// root being clip 0) followed by 7 target clip indices per clip (groupId,
// parent, controllerId, maskTargetId, trackFollow, cornerPin, planarRemove
// pin), each the index of the clip carrying the referenced id or -1 (the
// reference becomes null), exactly the original's idMap lookups.
std::vector<int64_t> userInstantiatePlan(
    const std::vector<int32_t>& groupKey, const std::vector<int32_t>& parentKey,
    const std::vector<int32_t>& controllerKey, const std::vector<int32_t>& maskKey,
    const std::vector<int32_t>& tfKey, const std::vector<int32_t>& cpKey,
    const std::vector<int32_t>& prKey, const std::vector<int32_t>& keyToClip,
    const std::vector<uint8_t>& has, const std::vector<int64_t>& startMs, int64_t insertAtMs);

}  // namespace am
