// Migration stage 3 (build 246): the template builders' laws — see
// template_plans.h. Every function here is an expression-by-expression port
// of the Kotlin original named in its comment; the golden dump
// (~/workspace/after-motion-dev/stage3-migration-golden/) proves the built
// graphs byte-identical before/after.
//
// Plan record layout (per clip, in lanesTopFirst order):
//   i32 flags, str id, str name, i32 type ordinal,
//   i64 startMs, durationMs, color, textColor, gradColor1, gradColor2,
//       strokeColor,
//   i32 fillMode, fillImageMode, gradType, fontWeight,
//   f32 gradX1, gradY1, gradX2, gradY2,
//   f32 shapeScaleX, shapeScaleY, shapeCornerRadius,
//   f32 strokeWidth, strokeTrimStart, strokeTrimEnd,
//   f32 transformX, transformY, rotationDeg, scalePercent, opacity,
//       pieRadius,
//   [HAS_TEXT] str text, [HAS_FILL_URI] str fillImageUri,
//   [HAS_SHAPE_PATTERN] str shapePattern, [HAS_GROUP] str groupId,
//   [HAS_CONTROLLER] str controllerId,
//   [HAS_PARENT] str parentId, f32 refX, refY, refScale, refRot
//       (the lock flags ride in `flags`),
//   i32 keyframe track count; per track: i32 AnimProp ordinal, i32 key
//       count, per key: i64 timeMs, f32 a, f32 b, f32 easing x1 y1 x2 y2,
//       i32 tag,
//   pie start keys, pie end keys (same key encoding, count-prefixed),
//   [HAS_PIE_SEGMENTS] f64 storedStart, f64 storedEnd — the wrapper calls
//       the existing PieOutline.normalisedSegments on them, exactly as the
//       originals did,
//   i32 effect count; per effect: str type, i32 value count, f32 values
//       (the wrapper constructs ClipFx, whose default UUID mints there —
//       exactly where the originals minted it),
//   the text animators (writeAnimators layout).
// Flags: HAS_TEXT 1, HAS_FILL_URI 2, HAS_SHAPE_PATTERN 4, HAS_PARENT 8,
// PARENT_LOCK_SCALE 16, PARENT_LOCK_ROTATE 32, HAS_GROUP 64,
// HAS_CONTROLLER 128, IS_ITALIC 256, HAS_PIE_SEGMENTS 512.

#include "template_plans.h"

#include <cmath>
#include <cstring>

#include "frame_eval.h"   // easingApply (PieCounterSpec.percentAt)
#include "model_logic.h"  // packColor (Genie's FILL_COLOR keys)

namespace am {

namespace {

// ---- easing presets (Easing.PRESETS values, copied by the Specs) ----
struct Ease { float x1, y1, x2, y2; };
constexpr Ease kLinear{1.0f / 3.0f, 1.0f / 3.0f, 2.0f / 3.0f, 2.0f / 3.0f};
constexpr Ease kCubicOut{0.215f, 0.61f, 0.355f, 1.0f};
constexpr Ease kCubicIn{0.55f, 0.055f, 0.675f, 0.19f};
constexpr Ease kCubicInOut{0.645f, 0.045f, 0.355f, 1.0f};
constexpr Ease kBackOut{0.175f, 0.885f, 0.32f, 1.2f};

// LayerType ordinals (model/EditorModels.kt).
constexpr int32_t kShape = 2;
constexpr int32_t kText = 5;
constexpr int32_t kNull = 6;
constexpr int32_t kGroup = 9;
constexpr int32_t kController = 10;

// AnimProp ordinals (model/EditorModels.kt).
constexpr int32_t kPos = 0;
constexpr int32_t kScale = 1;
constexpr int32_t kRotation = 2;
constexpr int32_t kRotX = 4;
constexpr int32_t kRotY = 5;
constexpr int32_t kFillColor = 6;
constexpr int32_t kOpacity = 11;
constexpr int32_t kStrokeTrim = 13;

// Fill / gradient constants (model/EditorModels.kt).
constexpr int32_t kFillNone = 0;
constexpr int32_t kFillSolid = 1;
constexpr int32_t kFillGradient = 2;
constexpr int32_t kFillImage = 3;
// kGradLinear / kGradRadial come from frame_eval.h (same values).
constexpr int32_t kImgFill = 0;

PlanKey key(int64_t t, float a, float b, Ease e = kLinear) {
    return PlanKey{t, a, b, e.x1, e.y1, e.x2, e.y2, 0};
}

// One clip under construction: TimelineClip's field set with the data
// class's own defaults, so a field a builder leaves unset emits exactly
// what the original's constructor/copy chain produced.
struct ClipRec {
    std::string id, name;
    int32_t type = 0;
    int64_t startMs = 0, durationMs = 0, color = 0;
    int64_t textColor = 0xFFFFFFFFLL, gradColor1 = 0xFF1FD91FLL,
            gradColor2 = 0xFFFFFFFFLL, strokeColor = 0xFFFFFFFFLL;
    int32_t fillMode = kFillSolid, fillImageMode = kImgFill,
            gradType = kGradLinear, fontWeight = 600;
    float gradX1 = 0.05f, gradY1 = 0.5f, gradX2 = 0.95f, gradY2 = 0.5f;
    float shapeScaleX = 1.0f, shapeScaleY = 1.0f, shapeCornerRadius = -1.0f;
    float strokeWidth = 0.0f, strokeTrimStart = 0.0f, strokeTrimEnd = 1.0f;
    float transformX = 0.0f, transformY = 0.0f, rotationDeg = 0.0f,
          scalePercent = 100.0f, opacity = 100.0f, pieRadius = 0.0f;
    bool hasText = false; std::string text;
    bool hasFillUri = false; std::string fillUri;
    bool hasShapePattern = false; std::string shapePattern;
    bool hasGroup = false; std::string groupId;
    bool hasController = false; std::string controllerId;
    bool hasParent = false; std::string parentId;
    float pRefX = 0.0f, pRefY = 0.0f, pRefScale = 100.0f, pRefRot = 0.0f;
    bool pLockScale = false, pLockRotate = false;
    bool italic = false;
    std::vector<std::pair<int32_t, std::vector<PlanKey>>> tracks;
    std::vector<PlanKey> pieStart, pieEnd;
    bool hasPieSegs = false; double storedStart = 0.0, storedEnd = 0.0;
    std::vector<std::pair<std::string, std::vector<float>>> fx;
    std::vector<PlanAnimator> animators;

    ClipRec& track(int32_t prop, std::vector<PlanKey> keys) {
        tracks.emplace_back(prop, std::move(keys));
        return *this;
    }
    // The GroupOps.linkTo(groupId, Pose.REST) parent every template child
    // carries: ParentLink(groupId, 0, 0, 100, 0), no locks.
    ClipRec& restLink(const std::string& gid) {
        hasGroup = true; groupId = gid;
        hasParent = true; parentId = gid;
        pRefX = 0.0f; pRefY = 0.0f; pRefScale = 100.0f; pRefRot = 0.0f;
        return *this;
    }
};

void writeKeys(PlanWriter& w, const std::vector<PlanKey>& keys) {
    w.i32(static_cast<int32_t>(keys.size()));
    for (const PlanKey& k : keys) {
        w.i64(k.timeMs);
        w.f32(k.a); w.f32(k.b);
        w.f32(k.e1); w.f32(k.e2); w.f32(k.e3); w.f32(k.e4);
        w.i32(k.tag);
    }
}

void writeClip(PlanWriter& w, const ClipRec& c) {
    int32_t flags = 0;
    if (c.hasText) flags |= 1;
    if (c.hasFillUri) flags |= 2;
    if (c.hasShapePattern) flags |= 4;
    if (c.hasParent) flags |= 8;
    if (c.pLockScale) flags |= 16;
    if (c.pLockRotate) flags |= 32;
    if (c.hasGroup) flags |= 64;
    if (c.hasController) flags |= 128;
    if (c.italic) flags |= 256;
    if (c.hasPieSegs) flags |= 512;
    w.i32(flags);
    w.str(c.id);
    w.str(c.name);
    w.i32(c.type);
    w.i64(c.startMs); w.i64(c.durationMs); w.i64(c.color);
    w.i64(c.textColor); w.i64(c.gradColor1); w.i64(c.gradColor2);
    w.i64(c.strokeColor);
    w.i32(c.fillMode); w.i32(c.fillImageMode); w.i32(c.gradType);
    w.i32(c.fontWeight);
    w.f32(c.gradX1); w.f32(c.gradY1); w.f32(c.gradX2); w.f32(c.gradY2);
    w.f32(c.shapeScaleX); w.f32(c.shapeScaleY); w.f32(c.shapeCornerRadius);
    w.f32(c.strokeWidth); w.f32(c.strokeTrimStart); w.f32(c.strokeTrimEnd);
    w.f32(c.transformX); w.f32(c.transformY); w.f32(c.rotationDeg);
    w.f32(c.scalePercent); w.f32(c.opacity); w.f32(c.pieRadius);
    if (c.hasText) w.str(c.text);
    if (c.hasFillUri) w.str(c.fillUri);
    if (c.hasShapePattern) w.str(c.shapePattern);
    if (c.hasGroup) w.str(c.groupId);
    if (c.hasController) w.str(c.controllerId);
    if (c.hasParent) {
        w.str(c.parentId);
        w.f32(c.pRefX); w.f32(c.pRefY); w.f32(c.pRefScale); w.f32(c.pRefRot);
    }
    w.i32(static_cast<int32_t>(c.tracks.size()));
    for (const auto& t : c.tracks) {
        w.i32(t.first);
        writeKeys(w, t.second);
    }
    writeKeys(w, c.pieStart);
    writeKeys(w, c.pieEnd);
    if (c.hasPieSegs) {
        w.f64(c.storedStart);
        w.f64(c.storedEnd);
    }
    w.i32(static_cast<int32_t>(c.fx.size()));
    for (const auto& f : c.fx) {
        w.str(f.first);
        w.i32(static_cast<int32_t>(f.second.size()));
        for (float v : f.second) w.f32(v);
    }
    writeAnimators(w, c.animators);
}

std::vector<uint8_t> finishPlan(const std::vector<ClipRec>& clips) {
    PlanWriter w;
    w.i32(1);  // plan version
    w.i32(0);  // meta count (stage 3 needs none: roles slice by position)
    w.i32(static_cast<int32_t>(clips.size()));
    for (const ClipRec& c : clips) writeClip(w, c);
    return w.buf;
}

// The base(...) helper of PieCounter / DonutChart / SocialGrowth / Genie:
// a child of the group on the REST link with the shared timing.
ClipRec baseChild(const std::string& id, const std::string& name, int32_t type, int64_t color,
                  int64_t insertAtMs, int64_t dur, const std::string& groupId) {
    ClipRec c;
    c.id = id; c.name = name; c.type = type; c.color = color;
    c.startMs = insertAtMs; c.durationMs = dur;
    c.restLink(groupId);
    return c;
}

// The frame-covering background the four base() builders share.
ClipRec frameBackground(const std::string& id, int64_t bgTextColor, float pw, float ph, float box,
                        int64_t insertAtMs, int64_t dur, const std::string& groupId) {
    ClipRec c = baseChild(id, "Background", kShape, 0xFF455A64LL, insertAtMs, dur, groupId);
    c.fillMode = kFillSolid;
    c.textColor = bgTextColor;
    c.hasShapePattern = true; c.shapePattern = "RECT";
    c.shapeScaleX = pw / box;
    c.shapeScaleY = ph / box;
    c.scalePercent = pw / box * 100.0f;
    return c;
}

}  // namespace

// ---- PlanWriter ----

void PlanWriter::u8(uint8_t v) { buf.push_back(v); }

void PlanWriter::i32(int32_t v) {
    uint32_t u = static_cast<uint32_t>(v);
    buf.push_back(static_cast<uint8_t>(u));
    buf.push_back(static_cast<uint8_t>(u >> 8));
    buf.push_back(static_cast<uint8_t>(u >> 16));
    buf.push_back(static_cast<uint8_t>(u >> 24));
}

void PlanWriter::i64(int64_t v) {
    uint64_t u = static_cast<uint64_t>(v);
    for (int i = 0; i < 8; i++) buf.push_back(static_cast<uint8_t>(u >> (8 * i)));
}

void PlanWriter::f32(float v) {
    uint32_t u;
    std::memcpy(&u, &v, 4);
    i32(static_cast<int32_t>(u));
}

void PlanWriter::f64(double v) {
    uint64_t u;
    std::memcpy(&u, &v, 8);
    i64(static_cast<int64_t>(u));
}

void PlanWriter::str(const std::string& s) {
    i32(static_cast<int32_t>(s.size()));
    buf.insert(buf.end(), s.begin(), s.end());
}

void writeAnimators(PlanWriter& w, const std::vector<PlanAnimator>& animators) {
    w.i32(static_cast<int32_t>(animators.size()));
    for (const PlanAnimator& a : animators) {
        w.str(a.effectId);
        w.f32(a.refSize);
        w.i32(static_cast<int32_t>(a.props.size()));
        for (const PlanAnimator::Prop& p : a.props) {
            w.str(p.name);
            w.str(p.type);
            w.i32(static_cast<int32_t>(p.value.size()));
            for (float v : p.value) w.f32(v);
            w.i32(static_cast<int32_t>(p.keys.size()));
            for (const PlanAnimKey& k : p.keys) {
                w.f32(k.t);
                w.i32(static_cast<int32_t>(k.v.size()));
                for (float v : k.v) w.f32(v);
                w.u8(k.hasEasing ? 1 : 0);
                if (k.hasEasing) {
                    w.f32(k.e1); w.f32(k.e2); w.f32(k.e3); w.f32(k.e4);
                }
            }
        }
    }
}

namespace {

// The counter animator both PieCounterSpec.counterAnimator and
// SocialGrowthSpec.counterAnimator build (Alight's counter effect id).
PlanAnimator counterAnimator(const std::vector<PlanAnimKey>& offsetKeys) {
    PlanAnimator a;
    a.effectId = "com.alightcreative.effects.counter";
    a.refSize = 0.0f;
    PlanAnimator::Prop offset;
    offset.name = "offset";
    offset.type = "float";
    offset.keys = offsetKeys;
    PlanAnimator::Prop scale;
    scale.name = "scale";
    scale.type = "float";
    scale.value = {1.0f};
    a.props = {offset, scale};
    return a;
}

PlanAnimKey animKey(float t, float v) {
    PlanAnimKey k;
    k.t = t;
    k.v = {v};
    k.hasEasing = false;
    k.e1 = k.e2 = k.e3 = k.e4 = 0.0f;
    return k;
}

PlanAnimKey animKey(float t, float v, Ease e) {
    PlanAnimKey k = animKey(t, v);
    k.hasEasing = true;
    k.e1 = e.x1; k.e2 = e.y1; k.e3 = e.x2; k.e4 = e.y2;
    return k;
}

}  // namespace

// ======================================================================
// Carousel (model/CarouselTemplate.kt)
// ======================================================================

namespace {

// CarouselSpec's measured angular easing, cubic-bezier(0.78, 0.013,
// 0.194, 1.0), in Double like the original.
double carouselBx(double s) {
    const double u = 1.0 - s;
    return 3.0 * u * u * s * 0.78 + 3.0 * u * s * s * 0.194 + s * s * s;
}

double carouselBy(double s) {
    const double u = 1.0 - s;
    return 3.0 * u * u * s * 0.013 + 3.0 * u * s * s * 1.0 + s * s * s;
}

/** Bezier parameter whose x is [x] (bisection; bx is monotonic). */
double carouselParamAtX(double x) {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    double lo = 0.0, hi = 1.0, s = x;
    for (int i = 0; i < 60; i++) {
        s = (lo + hi) / 2.0;
        if (carouselBx(s) < x) lo = s; else hi = s;
    }
    return s;
}

/** de Casteljau split of a cubic bezier at [t]: (left, right) polygons. */
struct BezPt { double x, y; };
void carouselSubdivide(const BezPt p[4], double t, BezPt left[4], BezPt right[4]) {
    auto lerp = [t](BezPt a, BezPt b) {
        return BezPt{a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t};
    };
    const BezPt q0 = lerp(p[0], p[1]), q1 = lerp(p[1], p[2]), q2 = lerp(p[2], p[3]);
    const BezPt r0 = lerp(q0, q1), r1 = lerp(q1, q2);
    const BezPt s = lerp(r0, r1);
    left[0] = p[0]; left[1] = q0; left[2] = r0; left[3] = s;
    right[0] = s; right[1] = r1; right[2] = q2; right[3] = p[3];
}

/** The master curve's easing restricted to loop fractions [xa]..[xb]. */
Ease carouselSliceEasing(double xa, double xb) {
    const BezPt ctrl[4] = {{0.0, 0.0}, {0.78, 0.013}, {0.194, 1.0}, {1.0, 1.0}};
    const double sa = carouselParamAtX(xa);
    const double sb = carouselParamAtX(xb);
    BezPt leftA[4], rightA[4], leftB[4], rightB[4];
    carouselSubdivide(ctrl, sa, leftA, rightA);
    carouselSubdivide(rightA, (sb - sa) / (1.0 - sa), leftB, rightB);
    const BezPt* seg = leftB;
    const double dx = seg[3].x - seg[0].x;
    const double dy = seg[3].y - seg[0].y;
    if (dx <= 0.0 || dy <= 0.0) return kLinear;
    return Ease{static_cast<float>((seg[1].x - seg[0].x) / dx),
                static_cast<float>((seg[1].y - seg[0].y) / dy),
                static_cast<float>((seg[2].x - seg[0].x) / dx),
                static_cast<float>((seg[2].y - seg[0].y) / dy)};
}

/** CarouselTemplate.rotationKeys. */
std::vector<PlanKey> carouselRotationKeys(int64_t durationMs, bool clockwise) {
    const int64_t steps = durationMs / 50;
    const double turn = clockwise ? 360.0 : -360.0;
    std::vector<PlanKey> out;
    for (int64_t j = 0; j <= steps; j++) {
        const int64_t tMs = j * 50;
        const double x = static_cast<double>(tMs) / static_cast<double>(durationMs);
        Ease e = kLinear;
        if (j < steps) {
            e = carouselSliceEasing(
                x, static_cast<double>(tMs + 50) / static_cast<double>(durationMs));
        }
        out.push_back(key(tMs, static_cast<float>(turn * carouselProgress(x)), 0.0f, e));
    }
    return out;
}

/** CarouselTemplate.ringStored. */
void carouselRingStored(double phaseDeg, float k, float dyScene, float radius, float out[2]) {
    const double a = phaseDeg * (M_PI / 180.0);  // Math.toRadians
    const double sx = 364.0f + radius * std::cos(a);
    const double sy = 224.0f + radius * std::sin(a) + dyScene;
    out[0] = static_cast<float>((sx - 720.0f / 2.0) * k);
    out[1] = static_cast<float>((sy - 450.0f / 2.0) * k);
}

}  // namespace

double carouselProgress(double x) {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    return carouselBy(carouselParamAtX(x));
}

float carouselRingRadius(int32_t cardCount) {
    const float needed = (95.0f * 1.04f / 2.0f) /
        static_cast<float>(std::sin(M_PI / static_cast<double>(cardCount)));
    return needed > 133.2f ? needed : 133.2f;
}

void carouselFit(float pwIn, float phIn, float radius, float out[3]) {
    const float pw = pwIn > 1.0f ? pwIn : 720.0f;
    const float ph = phIn > 1.0f ? phIn : pw * 450.0f / 720.0f;
    const float k = pw / 720.0f;
    const float ringReach = radius + 95.0f / 2.0f;
    const float contentW = 2.0f * ringReach > 720.0f ? 2.0f * ringReach : 720.0f;
    const float contentH = 2.0f * ringReach > 450.0f ? 2.0f * ringReach : 450.0f;
    const float fit = 0.96f * (pw / contentW < ph / contentH ? pw / contentW : ph / contentH) / k;
    const float cx = (364.0f - 720.0f / 2.0f) * k;
    const float cy = (224.0f - 450.0f / 2.0f) * k;
    out[0] = -cx * fit;
    out[1] = -cy * fit;
    out[2] = fit;
}

double carouselAngleDegAt(int64_t tMs, int32_t cardIndex, int32_t cardCount, int64_t durationMs,
                          bool clockwise) {
    const double turn = clockwise ? 360.0 : -360.0;
    return cardIndex * (360.0 / cardCount) +
        turn * carouselProgress(static_cast<double>(tMs) / static_cast<double>(durationMs));
}

void carouselCenterSceneAt(int64_t tMs, int32_t cardIndex, int32_t cardCount, int64_t durationMs,
                           bool clockwise, float out[2]) {
    const double a = carouselAngleDegAt(tMs, cardIndex, cardCount, durationMs, clockwise) *
        (M_PI / 180.0);
    const float r = carouselRingRadius(cardCount);
    out[0] = static_cast<float>(364.0f + r * std::cos(a));
    out[1] = static_cast<float>(224.0f + r * std::sin(a));
}

std::vector<uint8_t> carouselPlan(float pwIn, float phIn, const std::string& backgroundUri,
                                  const std::vector<std::string>& cardUris, int64_t insertAtMs,
                                  const std::string& idBase, int32_t cardCount,
                                  int64_t durationMs, bool clockwise) {
    const float pw = pwIn > 1.0f ? pwIn : 720.0f;
    const float k = pw / 720.0f;
    const float boxPx = 720.0f * 130.0f / 360.0f;
    const float cardScale = 95.0f / boxPx;
    const float cornerStored = 65.0f * 14.0f / (95.0f / 2.0f);
    const float radius = carouselRingRadius(cardCount);

    const std::string groupId = idBase + "-group";
    float fit3[3];
    carouselFit(pwIn, phIn, radius, fit3);

    std::vector<ClipRec> clips;
    ClipRec group;
    group.id = groupId; group.name = "Carousel"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = durationMs;
    group.color = 0xFF5BB85DLL;
    group.transformX = fit3[0]; group.transformY = fit3[1];
    group.scalePercent = fit3[2] * 100.0f;
    clips.push_back(group);

    const float centreX = (364.0f - 720.0f / 2.0f) * k;
    const float centreY = (224.0f - 450.0f / 2.0f) * k;

    ClipRec nullClip;
    nullClip.id = idBase + "-null"; nullClip.name = "Carousel Null";
    nullClip.type = kNull; nullClip.startMs = insertAtMs;
    nullClip.durationMs = durationMs; nullClip.color = 0xFFE8A33DLL;
    nullClip.transformX = centreX; nullClip.transformY = centreY;
    nullClip.track(kRotation, carouselRotationKeys(durationMs, clockwise));
    nullClip.restLink(groupId);
    clips.push_back(nullClip);

    ClipRec controller;
    controller.id = idBase + "-controller"; controller.name = "Carousel Controller";
    controller.type = kController; controller.startMs = insertAtMs;
    controller.durationMs = durationMs; controller.color = 0xFF00B8A9LL;
    controller.transformX = centreX; controller.transformY = centreY;
    controller.restLink(groupId);
    clips.push_back(controller);

    const std::string nullId = idBase + "-null";
    const std::string controllerId = idBase + "-controller";
    static const int64_t kCardTints[8] = {
        0xFFEF5350LL, 0xFFFFA726LL, 0xFFFFEE58LL, 0xFF66BB6ALL,
        0xFF42A5F5LL, 0xFF7E57C2LL, 0xFFEC407ALL, 0xFF26A69ALL};

    std::vector<ClipRec> pairs;  // (shadow, card) per card, reordered below
    for (int32_t i = 0; i < cardCount; i++) {
        const double phase = i * (360.0 / cardCount);
        float sh[2], cd[2];
        carouselRingStored(phase, k, 2.5f, radius, sh);
        carouselRingStored(phase, k, 0.0f, radius, cd);
        ClipRec shadow;
        shadow.id = idBase + "-shadow-" + std::to_string(i + 1);
        shadow.name = "Card " + std::to_string(i + 1) + " shadow";
        shadow.type = kShape; shadow.startMs = insertAtMs;
        shadow.durationMs = durationMs; shadow.color = 0xFF9E9E9ELL;
        shadow.fillMode = kFillSolid;
        shadow.textColor = 0xFF000000LL;
        shadow.opacity = 16.0f;
        shadow.hasShapePattern = true; shadow.shapePattern = "ROUNDED_RECT";
        shadow.shapeScaleX = cardScale; shadow.shapeScaleY = cardScale;
        shadow.scalePercent = cardScale * 100.0f;
        shadow.shapeCornerRadius = cornerStored;
        shadow.transformX = sh[0]; shadow.transformY = sh[1];
        shadow.hasGroup = true; shadow.groupId = groupId;
        shadow.hasParent = true; shadow.parentId = nullId;
        shadow.pRefX = centreX; shadow.pRefY = centreY;
        shadow.pRefScale = 100.0f; shadow.pRefRot = 0.0f;
        shadow.pLockRotate = true;
        shadow.hasController = true; shadow.controllerId = controllerId;
        pairs.push_back(shadow);
        ClipRec card;
        card.id = idBase + "-card-" + std::to_string(i + 1);
        card.name = "Photo " + std::to_string(i + 1);
        card.type = kShape; card.startMs = insertAtMs;
        card.durationMs = durationMs; card.color = 0xFF90A4AELL;
        card.fillMode = kFillImage;
        card.hasFillUri = true; card.fillUri = cardUris[static_cast<size_t>(i)];
        card.fillImageMode = kImgFill;
        card.textColor = kCardTints[i % 8];
        card.hasShapePattern = true; card.shapePattern = "ROUNDED_RECT";
        card.shapeScaleX = cardScale; card.shapeScaleY = cardScale;
        card.scalePercent = cardScale * 100.0f;
        card.shapeCornerRadius = cornerStored;
        card.transformX = cd[0]; card.transformY = cd[1];
        card.hasGroup = true; card.groupId = groupId;
        card.hasParent = true; card.parentId = nullId;
        card.pRefX = centreX; card.pRefY = centreY;
        card.pRefScale = 100.0f; card.pRefRot = 0.0f;
        card.pLockRotate = true;
        card.hasController = true; card.controllerId = controllerId;
        pairs.push_back(card);
    }
    // Children in lane order: null, controller, per card (card, shadow), bg.
    std::vector<ClipRec> ordered;
    ordered.push_back(clips[0]);
    ordered.push_back(clips[1]);
    ordered.push_back(clips[2]);
    for (int32_t i = 0; i < cardCount; i++) {
        ordered.push_back(pairs[static_cast<size_t>(2 * i + 1)]);  // card
        ordered.push_back(pairs[static_cast<size_t>(2 * i)]);      // shadow
    }
    const float bgScaleX = (720.0f + 20.0f) / boxPx;
    const float bgScaleY = (450.0f + 20.0f) / boxPx;
    ClipRec bg;
    bg.id = idBase + "-bg"; bg.name = "Background"; bg.type = kShape;
    bg.startMs = insertAtMs; bg.durationMs = durationMs;
    bg.color = 0xFFB2B2B4LL;
    bg.fillMode = kFillImage;
    bg.hasFillUri = true; bg.fillUri = backgroundUri;
    bg.fillImageMode = kImgFill;
    bg.hasShapePattern = true; bg.shapePattern = "RECT";
    bg.shapeScaleX = bgScaleX; bg.shapeScaleY = bgScaleY;
    bg.scalePercent = bgScaleX * 100.0f;
    bg.restLink(groupId);
    ordered.push_back(bg);
    return finishPlan(ordered);
}

// ======================================================================
// PopTitle (model/PopTitleTemplate.kt)
// ======================================================================

std::vector<uint8_t> popTitlePlan(float pwIn, int64_t insertAtMs, const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float box = pw * 130.0f / 360.0f;
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Pop Title"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = 3000;
    group.color = 0xFF5BB85DLL;

    const float rise = 0.035f * pw;
    const float exitRise = 0.025f * pw;
    ClipRec text;
    text.id = idBase + "-text"; text.name = "Title Text"; text.type = kText;
    text.startMs = insertAtMs; text.durationMs = 3000;
    text.color = 0xFF3992CELL;
    text.hasText = true; text.text = "আপনার লেখা";
    text.textColor = 0xFFFFFFFFLL;
    text.fontWeight = 700;
    text.track(kPos, {key(0, 0.0f, rise, kCubicOut), key(600, 0.0f, 0.0f),
                      key(2600, 0.0f, 0.0f, kCubicIn), key(3000, 0.0f, -exitRise)});
    text.track(kScale, {key(0, 80.0f, 0.0f, kBackOut), key(600, 100.0f, 0.0f)});
    text.track(kOpacity, {key(0, 0.0f, 0.0f, kCubicOut), key(600, 100.0f, 0.0f),
                          key(2600, 100.0f, 0.0f, kCubicIn), key(3000, 0.0f, 0.0f)});
    text.restLink(groupId);

    const float barScaleX = 0.30f * pw / box * 100.0f;
    const float barScaleY = 0.011f * pw / box * 100.0f;
    ClipRec bar;
    bar.id = idBase + "-bar"; bar.name = "Accent Bar"; bar.type = kShape;
    bar.startMs = insertAtMs; bar.durationMs = 3000;
    bar.color = 0xFFFFB300LL;
    bar.fillMode = kFillSolid;
    bar.textColor = 0xFFFFB300LL;
    bar.hasShapePattern = true; bar.shapePattern = "ROUNDED_RECT";
    bar.shapeCornerRadius = 65.0f;
    bar.shapeScaleX = barScaleX / 100.0f;
    bar.shapeScaleY = barScaleY / 100.0f;
    bar.scalePercent = barScaleX;
    bar.transformY = 0.105f * pw;
    bar.track(kScale, {key(0, 0.0f, barScaleY, kCubicOut), key(600, barScaleX, barScaleY)});
    bar.track(kOpacity, {key(0, 100.0f, 0.0f), key(2600, 100.0f, 0.0f, kCubicIn),
                         key(3000, 0.0f, 0.0f)});
    bar.restLink(groupId);

    return finishPlan({group, text, bar});
}

// ======================================================================
// LogoReveal (model/LogoRevealTemplate.kt)
// ======================================================================

std::vector<uint8_t> logoRevealPlan(float pwIn, const std::string& logoUri, int64_t insertAtMs,
                                    const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float box = pw * 130.0f / 360.0f;
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Logo Reveal"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = 3000;
    group.color = 0xFF7E57C2LL;

    const float logoScale = 0.28f * pw / box;
    const float restPct = logoScale * 100.0f;
    ClipRec logo;
    logo.id = idBase + "-logo"; logo.name = "Logo"; logo.type = kShape;
    logo.startMs = insertAtMs; logo.durationMs = 3000;
    logo.color = 0xFF5E35B1LL;
    logo.fillMode = kFillImage;
    logo.hasFillUri = true; logo.fillUri = logoUri;
    logo.fillImageMode = kImgFill;
    logo.hasShapePattern = true; logo.shapePattern = "ROUNDED_RECT";
    logo.shapeCornerRadius = 26.0f;
    logo.shapeScaleX = logoScale; logo.shapeScaleY = logoScale;
    logo.scalePercent = restPct;
    logo.transformY = -0.10f * pw;
    logo.track(kScale, {key(0, 60.0f / 100.0f * restPct, 0.0f, kBackOut),
                        key(500, restPct, 0.0f)});
    logo.track(kOpacity, {key(0, 0.0f, 0.0f, kCubicOut), key(500, 100.0f, 0.0f)});
    logo.restLink(groupId);

    const float textY = 0.13f * pw;
    const float textRise = 0.03f * pw;
    ClipRec text;
    text.id = idBase + "-text"; text.name = "Brand Name"; text.type = kText;
    text.startMs = insertAtMs; text.durationMs = 3000;
    text.color = 0xFF3992CELL;
    text.hasText = true; text.text = "আপনার ব্র্যান্ড";
    text.textColor = 0xFFFFFFFFLL;
    text.fontWeight = 700;
    text.transformY = textY;
    text.track(kPos, {key(350, 0.0f, textY + textRise, kCubicOut), key(800, 0.0f, textY)});
    text.track(kOpacity, {key(350, 0.0f, 0.0f, kCubicOut), key(800, 100.0f, 0.0f)});
    text.restLink(groupId);

    return finishPlan({group, logo, text});
}

// ======================================================================
// WordPop (model/WordPopTemplate.kt)
// ======================================================================

namespace {

const char* const kWordPopWords[3] = {"আপনার", "নতুন", "লেখা"};
constexpr float kWordPopAdvEm[3] = {3.596f, 2.000f, 2.228f};

float wordPopTotalEm() {
    // WORD_ADV_EM.sum() + (WORDS.size - 1) * SPACE_EM, sequential Float adds.
    float sum = 0.0f;
    for (float v : kWordPopAdvEm) sum += v;
    return sum + 2 * 0.260f;
}

}  // namespace

int64_t wordPopStartMs(int32_t index) { return static_cast<int64_t>(index) * 180LL; }

float wordPopAdvancePx(int32_t index, float pw) {
    return kWordPopAdvEm[index] * 0.11f * pw;
}

float wordPopTotalWidthPx(float pw) { return wordPopTotalEm() * 0.11f * pw; }

float wordPopCentreXPx(int32_t index, float pw) {
    const float totalEm = wordPopTotalEm();
    float beforeEm = index * 0.260f;
    for (int32_t j = 0; j < index; j++) beforeEm += kWordPopAdvEm[j];
    const float centreEm = -totalEm / 2.0f + beforeEm + kWordPopAdvEm[index] / 2.0f;
    return centreEm * 0.11f * pw;
}

std::vector<uint8_t> wordPopPlan(float pwIn, int64_t insertAtMs, const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float rise = 0.02f * pw;
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Word Pop"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = 3000;
    group.color = 0xFFEF6C00LL;

    std::vector<ClipRec> clips;
    clips.push_back(group);
    for (int32_t i = 0; i < 3; i++) {
        const int64_t start = wordPopStartMs(i);
        const int64_t landed = start + 350;
        const float x = wordPopCentreXPx(i, pw);
        ClipRec w;
        w.id = idBase + "-word" + std::to_string(i + 1);
        w.name = "Word " + std::to_string(i + 1);
        w.type = kText; w.startMs = insertAtMs; w.durationMs = 3000;
        w.color = 0xFF3992CELL;
        w.hasText = true; w.text = kWordPopWords[i];
        w.textColor = 0xFFFFFFFFLL;
        w.fontWeight = 700;
        w.transformX = x;
        w.track(kPos, {key(start, x, rise, kCubicOut), key(landed, x, 0.0f)});
        w.track(kScale, {key(start, 60.0f, 0.0f, kBackOut), key(landed, 100.0f, 0.0f)});
        w.track(kOpacity, {key(start, 0.0f, 0.0f, kCubicOut), key(landed, 100.0f, 0.0f),
                           key(2600, 100.0f, 0.0f, kCubicIn), key(3000, 0.0f, 0.0f)});
        w.restLink(groupId);
        clips.push_back(w);
    }
    return finishPlan(clips);
}

// ======================================================================
// PieCounter (model/PieCounterTemplate.kt)
// ======================================================================

float piePercentAt(int64_t relMs) {
    if (relMs <= 0) return 0.0f;
    if (relMs >= 3200) return 100.0f;
    return easingApply(kCubicOut.x1, kCubicOut.y1, kCubicOut.x2, kCubicOut.y2,
                       static_cast<float>(relMs) / 3200.0f) * 100.0f;
}

std::vector<uint8_t> pieCounterAnimatorBlob() {
    PlanWriter w;
    writeAnimators(w, {counterAnimator({animKey(0.0f, 0.0f),
                                        animKey(3200.0f / 4000.0f, 100.0f, kCubicOut)})});
    return w.buf;
}

std::vector<uint8_t> pieCounterPlan(float pwIn, float phIn, int64_t insertAtMs,
                                    const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float ph = phIn > 1.0f ? phIn : 1800.0f;
    const float box = pw * 130.0f / 360.0f;
    const int64_t dur = 4000;
    const float rPx = 0.30f * pw;
    const float pieCY = 0.07f * ph;
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Pie Counter"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = dur;
    group.color = 0xFF1E88E5LL;

    ClipRec background = frameBackground(idBase + "-bg", 0xFF000000LL, pw, ph, box,
                                         insertAtMs, dur, groupId);

    auto pieLayer = [&](const std::string& id, const std::string& name,
                        std::vector<PlanKey> startKeys, std::vector<PlanKey> endKeys,
                        float storedStart, float storedEnd) {
        ClipRec c = baseChild(id, name, kShape, 0xFF42A5F5LL, insertAtMs, dur, groupId);
        c.hasShapePattern = true; c.shapePattern = "PATH";
        c.hasPieSegs = true;
        c.storedStart = static_cast<double>(storedStart);
        c.storedEnd = static_cast<double>(storedEnd);
        c.pieStart = std::move(startKeys);
        c.pieEnd = std::move(endKeys);
        c.pieRadius = 1.0f;
        c.shapeScaleX = 2.0f * rPx / box;
        c.shapeScaleY = 2.0f * rPx / box;
        c.scalePercent = 2.0f * rPx / box * 100.0f;
        c.transformY = pieCY;
        return c;
    };

    ClipRec fill = pieLayer(idBase + "-fill", "Pie Fill",
                            {key(0, 90.0f, 0.0f)},
                            {key(0, 449.5f, 0.0f, kCubicOut), key(3200, 89.5f, 0.0f)},
                            90.0f, 89.5f);
    fill.fillMode = kFillGradient;
    fill.gradType = kGradLinear;
    fill.gradColor1 = 0xFF1E8CFFLL;
    fill.gradColor2 = 0xFFF4F8FFLL;
    fill.gradX1 = 0.10f; fill.gradY1 = 0.05f; fill.gradX2 = 0.95f; fill.gradY2 = 0.95f;

    ClipRec outline = pieLayer(idBase + "-outline", "Pie Outline",
                               {key(0, 89.5f, 0.0f, kCubicOut), key(3200, -269.5f, 0.0f)},
                               {key(0, 90.0f, 0.0f)},
                               89.5f, 90.0f);
    outline.fillMode = kFillNone;
    outline.strokeColor = 0xFFB9C4CELL;
    outline.strokeWidth = 360.0f * 0.0035f;

    ClipRec percent = baseChild(idBase + "-percent", "Percent", kText, 0xFF3992CELL,
                                insertAtMs, dur, groupId);
    percent.hasText = true; percent.text = "0%";
    percent.fillMode = kFillGradient;
    percent.gradType = kGradLinear;
    percent.gradColor1 = 0xFF8A8F98LL;
    percent.gradColor2 = 0xFFFFFFFFLL;
    percent.gradX1 = 0.5f; percent.gradY1 = 0.0f; percent.gradX2 = 0.5f; percent.gradY2 = 1.0f;
    percent.fontWeight = 800;
    percent.scalePercent = 0.17f / 0.11f * 100.0f;
    percent.transformY = -0.13f * ph;
    percent.animators = {counterAnimator({animKey(0.0f, 0.0f),
                                          animKey(3200.0f / 4000.0f, 100.0f, kCubicOut)})};
    percent.track(kOpacity, {key(0, 0.0f, 0.0f, kCubicOut), key(300, 100.0f, 0.0f)});

    // Lane order: percent, outline, fill, background.
    return finishPlan({group, percent, outline, fill, background});
}

// ======================================================================
// DonutChart (model/DonutChartTemplate.kt)
// ======================================================================

namespace {

constexpr float kDonutSpans[5][2] = {
    {90.0f, 270.0f}, {18.0f, 90.0f}, {-36.0f, 18.0f}, {-72.0f, -36.0f}, {-90.0f, -72.0f}};
constexpr int64_t kDonutGradients[5][2] = {
    {0xFF8F0E1ELL, 0xFFE8112DLL}, {0xFFB33A12LL, 0xFFF4652ALL}, {0xFFC25E0ALL, 0xFFF59A23LL},
    {0xFFA8B51BLL, 0xFFD7DF23LL}, {0xFF12B76ALL, 0xFF2EE66BLL}};
constexpr int32_t kDonutPercents[5] = {50, 20, 15, 10, 5};
const char* const kDonutLegend[5] = {
    "50% : Opportunistic Scams", "20% : Overly Ambitious", "15% : \"Too early\" solutions",
    "10% : Good Investments", "5% : The Next \"Amazons\""};
constexpr int64_t kDonutWindows[5][2] = {
    {300, 2000}, {2000, 3300}, {3300, 4300}, {4300, 5300}, {5300, 6300}};

}  // namespace

void donutLabelPos(int32_t index, float pw, float ph, float out[2]) {
    const float a = kDonutSpans[index][0], b = kDonutSpans[index][1];
    const double mid = ((a + b) / 2.0f) * (M_PI / 180.0);  // Math.toRadians of the Float
    const float r = 0.40f * pw;
    out[0] = static_cast<float>(r * std::cos(mid));
    out[1] = static_cast<float>(-0.20f * ph - r * std::sin(mid));
}

float donutLegendY(int32_t index, float ph) {
    return (0.115f + index * 0.036f) * ph;
}

std::vector<uint8_t> donutPlan(float pwIn, float phIn, int64_t insertAtMs,
                               const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float ph = phIn > 1.0f ? phIn : 1920.0f;
    const float box = pw * 130.0f / 360.0f;
    const int64_t dur = 10000;
    const float cy = -0.20f * ph;
    const float rPx = 0.315f * pw;
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Donut Chart"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = dur;
    group.color = 0xFFE53935LL;

    ClipRec background = frameBackground(idBase + "-bg", 0xFF050506LL, pw, ph, box,
                                         insertAtMs, dur, groupId);

    ClipRec title = baseChild(idBase + "-title", "Title", kText, 0xFF3992CELL,
                              insertAtMs, dur, groupId);
    title.hasText = true; title.text = "0x";
    title.textColor = 0xFF9A9A9ALL;
    title.fontWeight = 600;
    title.scalePercent = 0.030f / 0.11f * 100.0f;
    title.transformY = -0.435f * ph;
    title.track(kOpacity, {key(0, 0.0f, 0.0f, kCubicOut), key(600, 100.0f, 0.0f)});

    struct Seg { ClipRec wedge, label, dot, legend; };
    std::vector<Seg> segs;
    for (int32_t i = 0; i < 5; i++) {
        const float a = kDonutSpans[i][0], b = kDonutSpans[i][1];
        const int64_t t0 = kDonutWindows[i][0], t1 = kDonutWindows[i][1];
        Seg s;
        ClipRec wedge = baseChild(idBase + "-seg" + std::to_string(i + 1),
                                  "Segment " + std::to_string(kDonutPercents[i]) + "%",
                                  kShape, 0xFFEF5350LL, insertAtMs, dur, groupId);
        wedge.fillMode = kFillGradient;
        wedge.gradType = kGradLinear;
        wedge.gradColor1 = kDonutGradients[i][0];
        wedge.gradColor2 = kDonutGradients[i][1];
        wedge.gradX1 = 0.15f; wedge.gradY1 = 0.1f; wedge.gradX2 = 0.9f; wedge.gradY2 = 0.95f;
        wedge.hasShapePattern = true; wedge.shapePattern = "PATH";
        wedge.hasPieSegs = true;
        if (i == 0) {
            wedge.pieStart = {key(t0, a - 360.0f, 0.0f, kCubicInOut), key(t1, b - 360.0f, 0.0f)};
            wedge.pieEnd = {key(0, a, 0.0f)};
            wedge.storedStart = static_cast<double>(b - 360.0f);
            wedge.storedEnd = static_cast<double>(a);
        } else {
            wedge.pieStart = {key(t0, b, 0.0f, kCubicInOut), key(t1, a, 0.0f)};
            wedge.pieEnd = {key(0, b - 360.0f, 0.0f)};
            wedge.storedStart = static_cast<double>(a);
            wedge.storedEnd = static_cast<double>(b - 360.0f);
        }
        wedge.pieRadius = 1.0f;
        wedge.shapeScaleX = 2.0f * rPx / box;
        wedge.shapeScaleY = 2.0f * rPx / box;
        wedge.scalePercent = 2.0f * rPx / box * 100.0f;
        wedge.transformY = cy;
        s.wedge = wedge;

        float lp[2];
        donutLabelPos(i, pw, ph, lp);
        const float labelScale = 0.034f / 0.11f * 100.0f;
        ClipRec label = baseChild(idBase + "-pct" + std::to_string(i + 1),
                                  "Label " + std::to_string(kDonutPercents[i]) + "%",
                                  kText, 0xFF3992CELL, insertAtMs, dur, groupId);
        label.hasText = true;
        label.text = std::to_string(kDonutPercents[i]) + "%";
        label.textColor = 0xFFFFFFFFLL;
        label.fontWeight = 700;
        label.scalePercent = labelScale;
        label.transformX = lp[0];
        label.transformY = lp[1];
        label.opacity = 0.0f;
        label.track(kOpacity, {key(t1 - 150, 0.0f, 0.0f, kCubicOut),
                               key(t1 + 200, 100.0f, 0.0f)});
        label.track(kScale, {key(t1 - 150, labelScale * 0.5f, 0.0f, kBackOut),
                             key(t1 + 200, labelScale, 0.0f)});
        s.label = label;

        const int64_t legendStart = kDonutWindows[i][0] + 400;
        const float ly = donutLegendY(i, ph);
        const float dotD = 0.020f * pw;
        ClipRec dot = baseChild(idBase + "-dot" + std::to_string(i + 1),
                                "Legend Dot " + std::to_string(i + 1),
                                kShape, 0xFFEF5350LL, insertAtMs, dur, groupId);
        dot.fillMode = kFillSolid;
        dot.textColor = kDonutGradients[i][1];
        dot.hasShapePattern = true; dot.shapePattern = "CIRCLE";
        dot.shapeScaleX = dotD / box;
        dot.shapeScaleY = dotD / box;
        dot.scalePercent = dotD / box * 100.0f;
        dot.transformX = -0.235f * pw;
        dot.transformY = ly;
        dot.opacity = 0.0f;
        dot.track(kOpacity, {key(legendStart, 0.0f, 0.0f, kCubicOut),
                             key(legendStart + 400, 100.0f, 0.0f)});
        s.dot = dot;

        ClipRec legend = baseChild(idBase + "-legend" + std::to_string(i + 1),
                                   "Legend " + std::to_string(i + 1),
                                   kText, 0xFF3992CELL, insertAtMs, dur, groupId);
        legend.hasText = true; legend.text = kDonutLegend[i];
        legend.textColor = 0xFFFFFFFFLL;
        legend.fontWeight = 600;
        legend.scalePercent = 0.030f / 0.11f * 100.0f;
        legend.transformX = -0.01f * pw;
        legend.transformY = ly;
        legend.opacity = 0.0f;
        legend.track(kOpacity, {key(legendStart, 0.0f, 0.0f, kCubicOut),
                                key(legendStart + 400, 100.0f, 0.0f)});
        legend.track(kPos, {key(legendStart, -0.04f * pw, ly, kCubicOut),
                            key(legendStart + 400, -0.01f * pw, ly)});
        s.legend = legend;
        segs.push_back(s);
    }

    const float holeD = 0.375f * pw;
    ClipRec hole = baseChild(idBase + "-hole", "Donut Hole", kShape, 0xFF455A64LL,
                             insertAtMs, dur, groupId);
    hole.fillMode = kFillSolid;
    hole.textColor = 0xFF050506LL;
    hole.hasShapePattern = true; hole.shapePattern = "CIRCLE";
    hole.shapeScaleX = holeD / box;
    hole.shapeScaleY = holeD / box;
    hole.scalePercent = holeD / box * 100.0f;
    hole.transformY = cy;

    auto arc = [&](const std::string& id, const std::string& name, float dFrac, float rot,
                   float trimS, float trimE, int64_t t0, int64_t t1) {
        const float d = dFrac * pw;
        ClipRec c = baseChild(id, name, kShape, 0xFFB0BEC5LL, insertAtMs, dur, groupId);
        c.fillMode = kFillNone;
        c.hasShapePattern = true; c.shapePattern = "CIRCLE";
        c.shapeScaleX = d / box;
        c.shapeScaleY = d / box;
        c.scalePercent = d / box * 100.0f;
        c.strokeColor = 0xFFFFFFFFLL;
        c.strokeWidth = 360.0f * 0.004f;
        c.transformY = cy;
        c.rotationDeg = rot;
        c.opacity = 70.0f;
        c.strokeTrimStart = trimS;
        c.strokeTrimEnd = trimE;
        c.track(kStrokeTrim, {key(t0, trimS, trimS, kCubicInOut), key(t1, trimS, trimE)});
        return c;
    };
    ClipRec arcOuter = arc(idBase + "-arcouter", "Arc Accent Outer", 0.345f * 2.0f, -30.0f,
                           0.02f, 0.30f, 800, 3000);
    ClipRec arcInner = arc(idBase + "-arcinner", "Arc Accent Inner", 0.175f * 2.0f, 140.0f,
                           0.55f, 0.85f, 1500, 4000);

    std::vector<ClipRec> clips;
    clips.push_back(group);
    clips.push_back(title);
    for (const Seg& s : segs) {
        clips.push_back(s.label);
        clips.push_back(s.legend);
        clips.push_back(s.dot);
    }
    clips.push_back(arcOuter);
    clips.push_back(arcInner);
    clips.push_back(hole);
    for (const Seg& s : segs) clips.push_back(s.wedge);
    clips.push_back(background);
    return finishPlan(clips);
}

// ======================================================================
// SocialGrowth (model/SocialGrowthTemplate.kt)
// ======================================================================

namespace {

constexpr int64_t kGreen = 0xFF2EE66BLL;

// SocialGrowthTemplate.opacityKeys: the segment's easing follows the
// value's direction (up = Cubic out, down = Cubic in, flat/last = Linear).
std::vector<PlanKey> sgOpacityKeys(const std::vector<std::pair<int64_t, float>>& ks) {
    std::vector<PlanKey> out;
    for (size_t i = 0; i < ks.size(); i++) {
        Ease e = kLinear;
        if (i + 1 < ks.size()) {
            if (ks[i + 1].second > ks[i].second) e = kCubicOut;
            else if (ks[i + 1].second < ks[i].second) e = kCubicIn;
        }
        out.push_back(key(ks[i].first, ks[i].second, 0.0f, e));
    }
    return out;
}

constexpr float kPostsKeys[7][2] = {
    {0.52f, 0.0f}, {0.62f, 2.0f}, {0.70f, 6.0f}, {0.78f, 12.0f},
    {0.85f, 24.0f}, {0.92f, 37.0f}, {1.0f, 59.0f}};
constexpr float kFollowersKeys[7][2] = {
    {0.52f, 0.0f}, {0.62f, 3.0f}, {0.70f, 13.0f}, {0.78f, 77.0f},
    {0.85f, 896.0f}, {0.92f, 3938.0f}, {1.0f, 9716.0f}};

PlanAnimator sgCounterAnimator(const float keys[][2], size_t n) {
    std::vector<PlanAnimKey> ks;
    for (size_t i = 0; i < n; i++) ks.push_back(animKey(keys[i][0], keys[i][1]));
    return counterAnimator(ks);
}

}  // namespace

std::vector<uint8_t> counterAnimatorBlob(const std::vector<float>& tvPairs) {
    std::vector<PlanAnimKey> ks;
    for (size_t i = 0; i + 1 < tvPairs.size(); i += 2) {
        ks.push_back(animKey(tvPairs[i], tvPairs[i + 1]));
    }
    PlanWriter w;
    writeAnimators(w, {counterAnimator(ks)});
    return w.buf;
}

void socialTilePos(int32_t index, float pw, float ph, float out[2]) {
    const int32_t col = index % 3;
    const int32_t row = index / 3;
    out[0] = (col - (3 - 1) / 2.0f) * 0.196f * pw;
    out[1] = (-0.155f + row * 0.1105f) * ph;
}

std::vector<uint8_t> socialGrowthPlan(float pwIn, float phIn, const std::string& avatarUri,
                                      int64_t insertAtMs, const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float ph = phIn > 1.0f ? phIn : 1920.0f;
    const float box = pw * 130.0f / 360.0f;
    const int64_t dur = 10000;
    const std::string groupId = idBase + "-group";
    auto pctW = [&](float frac) { return frac * pw / box * 100.0f; };

    ClipRec group;
    group.id = groupId; group.name = "Social Growth"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = dur;
    group.color = kGreen;

    ClipRec background = frameBackground(idBase + "-bg", 0xFF000000LL, pw, ph, box,
                                         insertAtMs, dur, groupId);

    static const char* const kTitles[3] = {"CREATE", "POST...", "ELEVATE"};
    static const std::vector<std::pair<int64_t, float>> kTitleWindows[3] = {
        {{600, 0.0f}, {1000, 100.0f}, {4900, 100.0f}, {5300, 0.0f}},
        {{5300, 0.0f}, {5700, 100.0f}, {7300, 100.0f}, {7700, 0.0f}},
        {{7700, 0.0f}, {8100, 100.0f}}};
    std::vector<ClipRec> titles;
    for (int32_t i = 0; i < 3; i++) {
        ClipRec t = baseChild(idBase + "-title" + std::to_string(i + 1),
                              std::string("Title ") + kTitles[i], kText, 0xFF3992CELL,
                              insertAtMs, dur, groupId);
        t.hasText = true; t.text = kTitles[i];
        t.textColor = i == 2 ? 0xFF3DFF6ELL : 0xFFFFFFFFLL;
        t.fontWeight = 800;
        t.scalePercent = 0.052f / 0.11f * 100.0f;
        t.transformY = -0.345f * ph;
        t.opacity = 0.0f;
        t.track(kOpacity, sgOpacityKeys(kTitleWindows[i]));
        titles.push_back(t);
    }

    const float pillW = 0.235f * pw;
    const float pillH = 0.042f * ph;
    const float pillY = -0.06f * ph;
    const std::vector<PlanKey> uploadKeys =
        sgOpacityKeys({{800, 0.0f}, {1100, 100.0f}, {2400, 100.0f}, {2700, 0.0f}});
    ClipRec uploadPill = baseChild(idBase + "-upload", "Upload Button", kShape, 0xFF78909CLL,
                                   insertAtMs, dur, groupId);
    uploadPill.fillMode = kFillSolid;
    uploadPill.textColor = 0xFF15151ALL;
    uploadPill.hasShapePattern = true; uploadPill.shapePattern = "ROUNDED_RECT";
    uploadPill.shapeCornerRadius = 65.0f;
    uploadPill.shapeScaleX = pillW / box;
    uploadPill.shapeScaleY = pillH / box;
    uploadPill.scalePercent = pillW / box * 100.0f;
    uploadPill.transformY = pillY;
    uploadPill.strokeColor = 0xFF3A3A42LL;
    uploadPill.strokeWidth = 1.0f;
    uploadPill.opacity = 0.0f;
    uploadPill.track(kOpacity, uploadKeys);

    ClipRec uploadText = baseChild(idBase + "-uploadtext", "Upload Label", kText, 0xFF3992CELL,
                                   insertAtMs, dur, groupId);
    uploadText.hasText = true; uploadText.text = "UPLOAD";
    uploadText.textColor = 0xFFFFFFFFLL;
    uploadText.fontWeight = 600;
    uploadText.scalePercent = 0.026f / 0.11f * 100.0f;
    uploadText.transformY = pillY;
    uploadText.opacity = 0.0f;
    uploadText.track(kOpacity, uploadKeys);

    const float ringD = 0.185f * pw;
    const float ringY = 0.04f * ph;
    ClipRec ring = baseChild(idBase + "-ring", "Progress Ring", kShape, 0xFF66BB6ALL,
                             insertAtMs, dur, groupId);
    ring.fillMode = kFillNone;
    ring.hasShapePattern = true; ring.shapePattern = "CIRCLE";
    ring.shapeScaleX = ringD / box;
    ring.shapeScaleY = ringD / box;
    ring.scalePercent = ringD / box * 100.0f;
    ring.strokeColor = kGreen;
    ring.strokeWidth = 360.0f * 0.008f;
    ring.transformY = ringY;
    ring.opacity = 0.0f;
    ring.strokeTrimStart = 0.0f;
    ring.strokeTrimEnd = 1.0f;
    ring.track(kOpacity, sgOpacityKeys({{2600, 0.0f}, {2750, 100.0f}, {4600, 100.0f}, {4900, 0.0f}}));
    ring.track(kStrokeTrim, {key(2600, 0.0f, 0.0f, kCubicInOut), key(4200, 0.0f, 1.0f)});

    auto checkMark = [&](const std::string& idPrefix, const std::string& namePrefix,
                         float cx, float cy, std::vector<PlanKey> keys) {
        std::vector<ClipRec> out;
        ClipRec a = baseChild(idPrefix + "-a", namePrefix + " A", kShape, 0xFF66BB6ALL,
                              insertAtMs, dur, groupId);
        a.fillMode = kFillSolid;
        a.textColor = 0xFF4DFF7ALL;
        a.hasShapePattern = true; a.shapePattern = "ROUNDED_RECT";
        a.shapeCornerRadius = 65.0f;
        a.shapeScaleX = 0.037f * pw / box;
        a.shapeScaleY = 0.0095f * pw / box;
        a.scalePercent = pctW(0.037f);
        a.transformX = cx - 0.017f * pw;
        a.transformY = cy + 0.001f * pw;
        a.rotationDeg = 48.0f;
        a.opacity = 0.0f;
        a.track(kOpacity, keys);
        out.push_back(a);
        ClipRec b = baseChild(idPrefix + "-b", namePrefix + " B", kShape, 0xFF66BB6ALL,
                              insertAtMs, dur, groupId);
        b.fillMode = kFillSolid;
        b.textColor = 0xFF4DFF7ALL;
        b.hasShapePattern = true; b.shapePattern = "ROUNDED_RECT";
        b.shapeCornerRadius = 65.0f;
        b.shapeScaleX = 0.051f * pw / box;
        b.shapeScaleY = 0.0095f * pw / box;
        b.scalePercent = pctW(0.051f);
        b.transformX = cx + 0.015f * pw;
        b.transformY = cy - 0.003f * pw;
        b.rotationDeg = -52.0f;
        b.opacity = 0.0f;
        b.track(kOpacity, keys);
        out.push_back(b);
        return out;
    };
    std::vector<ClipRec> ringCheck = checkMark(
        idBase + "-ringcheck", "Ring Check", 0.0f, ringY,
        sgOpacityKeys({{4350, 0.0f}, {4600, 100.0f}, {4900, 100.0f}, {5150, 0.0f}}));

    const std::vector<PlanKey> headerKeys = sgOpacityKeys({{5100, 0.0f}, {5600, 100.0f}});
    const float avatarD = 0.088f * pw;
    const float headerY = -0.278f * ph;
    ClipRec avatar = baseChild(idBase + "-avatar", "Profile Photo", kShape, 0xFF66BB6ALL,
                               insertAtMs, dur, groupId);
    avatar.fillMode = kFillImage;
    avatar.hasFillUri = true; avatar.fillUri = avatarUri;
    avatar.fillImageMode = kImgFill;
    avatar.hasShapePattern = true; avatar.shapePattern = "CIRCLE";
    avatar.shapeScaleX = avatarD / box;
    avatar.shapeScaleY = avatarD / box;
    avatar.scalePercent = avatarD / box * 100.0f;
    avatar.transformX = -0.235f * pw;
    avatar.transformY = headerY;
    avatar.opacity = 0.0f;
    avatar.track(kOpacity, headerKeys);

    auto avatarRingLayer = [&](const std::string& id, const std::string& name, int64_t stroke,
                               std::vector<PlanKey> keys) {
        const float d = avatarD * 1.09f;
        ClipRec c = baseChild(id, name, kShape, 0xFFB0BEC5LL, insertAtMs, dur, groupId);
        c.fillMode = kFillNone;
        c.hasShapePattern = true; c.shapePattern = "CIRCLE";
        c.shapeScaleX = d / box;
        c.shapeScaleY = d / box;
        c.scalePercent = d / box * 100.0f;
        c.strokeColor = stroke;
        c.strokeWidth = 360.0f * 0.004f;
        c.transformX = -0.235f * pw;
        c.transformY = headerY;
        c.opacity = 0.0f;
        c.track(kOpacity, std::move(keys));
        return c;
    };
    ClipRec avatarRing = avatarRingLayer(
        idBase + "-avring", "Avatar Ring", 0xFF565660LL,
        sgOpacityKeys({{5100, 0.0f}, {5600, 100.0f}, {7700, 100.0f}, {8100, 0.0f}}));
    ClipRec avatarRingGreen = avatarRingLayer(
        idBase + "-avringgreen", "Avatar Ring Green", kGreen,
        sgOpacityKeys({{7700, 0.0f}, {8100, 100.0f}}));

    auto counterText = [&](const std::string& id, const std::string& name, float x,
                           PlanAnimator animator) {
        ClipRec c = baseChild(id, name, kText, 0xFF3992CELL, insertAtMs, dur, groupId);
        c.hasText = true; c.text = "0";
        c.textColor = 0xFFFFFFFFLL;
        c.fontWeight = 800;
        c.scalePercent = 0.040f / 0.11f * 100.0f;
        c.transformX = x;
        c.transformY = headerY - 0.006f * ph;
        c.opacity = 0.0f;
        c.animators = {std::move(animator)};
        c.track(kOpacity, headerKeys);
        return c;
    };
    auto counterLabel = [&](const std::string& id, const std::string& name, float x,
                            const std::string& text) {
        ClipRec c = baseChild(id, name, kText, 0xFF3992CELL, insertAtMs, dur, groupId);
        c.hasText = true; c.text = text;
        c.textColor = 0xFFB9B9C0LL;
        c.fontWeight = 600;
        c.scalePercent = 0.020f / 0.11f * 100.0f;
        c.transformX = x;
        c.transformY = headerY + 0.036f * ph;
        c.opacity = 0.0f;
        c.track(kOpacity, headerKeys);
        return c;
    };
    ClipRec postsCount = counterText(idBase + "-posts", "Posts Count", -0.075f * pw,
                                     sgCounterAnimator(kPostsKeys, 7));
    ClipRec postsLabel = counterLabel(idBase + "-postslabel", "Posts Label", -0.075f * pw, "POSTS");
    ClipRec followersCount = counterText(idBase + "-followers", "Followers Count", 0.115f * pw,
                                         sgCounterAnimator(kFollowersKeys, 7));
    ClipRec followersLabel = counterLabel(idBase + "-followerslabel", "Followers Label",
                                          0.115f * pw, "FOLLOWERS");

    const float tilePx = 0.168f * pw;
    const float tileRestA = tilePx / box * 100.0f;
    std::vector<ClipRec> tiles;
    std::vector<std::vector<ClipRec>> tileChecks;
    for (int32_t i = 0; i < 12; i++) {
        float tp[2];
        socialTilePos(i, pw, ph, tp);
        const int64_t tin = 5200 + static_cast<int64_t>(i) * 130;
        const int64_t wave = 8600 + static_cast<int64_t>(i) * 60;
        ClipRec tile = baseChild(idBase + "-tile" + std::to_string(i + 1),
                                 "Tile " + std::to_string(i + 1), kShape, 0xFF66BB6ALL,
                                 insertAtMs, dur, groupId);
        tile.fillMode = kFillGradient;
        tile.gradType = kGradLinear;
        tile.gradColor1 = 0xFF081C0DLL;
        tile.gradColor2 = 0xFF23C75CLL;
        tile.gradX1 = 0.5f; tile.gradY1 = 0.0f; tile.gradX2 = 0.5f; tile.gradY2 = 1.0f;
        tile.hasShapePattern = true; tile.shapePattern = "ROUNDED_RECT";
        tile.shapeCornerRadius = 65.0f * (0.012f / (0.168f / 2.0f));
        tile.shapeScaleX = tilePx / box;
        tile.shapeScaleY = tilePx / box;
        tile.scalePercent = tileRestA;
        tile.transformX = tp[0];
        tile.transformY = tp[1];
        tile.opacity = 0.0f;
        tile.track(kScale, {key(tin, tileRestA * 0.3f, tileRestA * 0.3f, kBackOut),
                            key(tin + 380, tileRestA, tileRestA)});
        tile.track(kOpacity, {key(tin, 0.0f, 0.0f, kCubicOut),
                              key(tin + 380, 100.0f, 0.0f),
                              key(wave, 100.0f, 0.0f, kCubicIn),
                              key(wave + 150, 55.0f, 0.0f, kCubicOut),
                              key(wave + 320, 100.0f, 0.0f)});
        tiles.push_back(tile);
        tileChecks.push_back(checkMark(
            idBase + "-tile" + std::to_string(i + 1) + "check",
            "Tile " + std::to_string(i + 1) + " Check", tp[0], tp[1],
            {key(tin + 120, 0.0f, 0.0f, kCubicOut), key(tin + 440, 100.0f, 0.0f),
             key(wave, 100.0f, 0.0f, kCubicIn), key(wave + 150, 55.0f, 0.0f, kCubicOut),
             key(wave + 320, 100.0f, 0.0f)}));
    }

    std::vector<ClipRec> clips;
    clips.push_back(group);
    for (const ClipRec& t : titles) clips.push_back(t);
    clips.push_back(followersLabel);
    clips.push_back(followersCount);
    clips.push_back(postsLabel);
    clips.push_back(postsCount);
    clips.push_back(avatarRingGreen);
    clips.push_back(avatarRing);
    clips.push_back(avatar);
    for (int32_t i = 11; i >= 0; i--) {
        clips.push_back(tileChecks[static_cast<size_t>(i)][0]);
        clips.push_back(tileChecks[static_cast<size_t>(i)][1]);
        clips.push_back(tiles[static_cast<size_t>(i)]);
    }
    clips.push_back(ringCheck[0]);
    clips.push_back(ringCheck[1]);
    clips.push_back(ring);
    clips.push_back(uploadText);
    clips.push_back(uploadPill);
    clips.push_back(background);
    return finishPlan(clips);
}

// ======================================================================
// LowerThird (model/LowerThirdTemplate.kt)
// ======================================================================

void lowerThirdLayout(float pw, float ph, float out[14]) {
    const float fontPx = 0.11f * pw;
    const float nameLineH = fontPx * 100.0f / 100.0f;
    const float subLineH = fontPx * 58.0f / 100.0f;
    const float barH = nameLineH + 0.016f * pw + subLineH;
    const float blockBottom = ph / 2.0f - 0.065f * ph;
    const float leftEdge = -pw / 2.0f + 0.055f * pw;
    const float textLeft = leftEdge + 0.013f * pw + 0.018f * pw;
    out[0] = fontPx;
    out[1] = nameLineH;
    out[2] = subLineH;
    out[3] = barH;
    out[4] = blockBottom;
    out[5] = leftEdge;
    out[6] = textLeft;
    out[7] = leftEdge + 0.013f * pw / 2.0f;
    out[8] = blockBottom - barH / 2.0f;
    out[9] = textLeft + 5.439f * fontPx * 100.0f / 100.0f / 2.0f;
    out[10] = blockBottom - subLineH - 0.016f * pw - nameLineH / 2.0f;
    out[11] = textLeft + 7.487f * fontPx * 58.0f / 100.0f / 2.0f;
    out[12] = blockBottom - subLineH / 2.0f;
    out[13] = 0.045f * pw;
}

std::vector<uint8_t> lowerThirdPlan(float pwIn, float phIn, int64_t insertAtMs,
                                    const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float ph = phIn > 1.0f ? phIn : 1080.0f;
    const float box = pw * 130.0f / 360.0f;
    float L[14];
    lowerThirdLayout(pw, ph, L);
    const float slide = L[13];
    const std::string groupId = idBase + "-group";

    ClipRec group;
    group.id = groupId; group.name = "Lower Third"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = 4000;
    group.color = 0xFF00695CLL;

    auto stagedText = [&](const std::string& id, const std::string& name,
                          const std::string& text, float scale, float cx, float cy,
                          int64_t startMs, int64_t endMs) {
        ClipRec c;
        c.id = id; c.name = name; c.type = kText;
        c.startMs = insertAtMs; c.durationMs = 4000;
        c.color = 0xFF3992CELL;
        c.hasText = true; c.text = text;
        c.textColor = 0xFFFFFFFFLL;
        c.fontWeight = 700;
        c.scalePercent = scale;
        c.transformX = cx; c.transformY = cy;
        c.track(kPos, {key(startMs, cx - slide, cy, kCubicOut), key(endMs, cx, cy),
                       key(3500, cx, cy, kCubicIn), key(4000, cx - slide, cy)});
        c.track(kOpacity, {key(startMs, 0.0f, 0.0f, kCubicOut), key(endMs, 100.0f, 0.0f),
                           key(3500, 100.0f, 0.0f, kCubicIn), key(4000, 0.0f, 0.0f)});
        c.restLink(groupId);
        return c;
    };
    ClipRec name = stagedText(idBase + "-name", "Name", "আপনার নাম", 100.0f,
                              L[9], L[10], 150, 550);
    ClipRec subtitle = stagedText(idBase + "-subtitle", "Subtitle", "আপনার পরিচিতি", 58.0f,
                                  L[11], L[12], 300, 700);

    const float barScaleX = 0.013f * pw / box * 100.0f;
    const float barScaleY = L[3] / box * 100.0f;
    const float barX = L[7], barY = L[8];
    ClipRec bar;
    bar.id = idBase + "-bar"; bar.name = "Accent Bar"; bar.type = kShape;
    bar.startMs = insertAtMs; bar.durationMs = 4000;
    bar.color = 0xFFFFB300LL;
    bar.fillMode = kFillSolid;
    bar.textColor = 0xFFFFB300LL;
    bar.hasShapePattern = true; bar.shapePattern = "ROUNDED_RECT";
    bar.shapeCornerRadius = 65.0f;
    bar.shapeScaleX = barScaleX / 100.0f;
    bar.shapeScaleY = barScaleY / 100.0f;
    bar.scalePercent = barScaleX;
    bar.transformX = barX; bar.transformY = barY;
    bar.track(kScale, {key(0, barScaleX, 0.1f, kCubicOut), key(250, barScaleX, barScaleY)});
    bar.track(kPos, {key(0, barX, barY), key(3500, barX, barY, kCubicIn),
                     key(4000, barX - slide, barY)});
    bar.track(kOpacity, {key(0, 100.0f, 0.0f), key(3500, 100.0f, 0.0f, kCubicIn),
                         key(4000, 0.0f, 0.0f)});
    bar.restLink(groupId);

    return finishPlan({group, name, subtitle, bar});
}

// ======================================================================
// Genie (model/GenieTemplate.kt)
// ======================================================================

namespace {

const char* const kGenieNames[6] = {"Me", "Liam", "Noah", "Emma", "Chloe", "Steph"};
const char* const kGenieCardTitles[4] = {"LIAM", "NOAH", "EMMA", "CHLOE"};
const char* const kGenieCardNames[4] = {"Liam", "Noah", "Emma", "Chloe"};
const char* const kGenieActivities[4] = {
    "Scenic walk together", "Birdwatching with Noah", "Picnic by the lake",
    "Eating tapas at The Ranch"};
constexpr int64_t kGenieRingColors[4] = {0xFFE53935LL, 0xFFFFFFFFLL, 0xFFFFFFFFLL, 0xFFFFFFFFLL};

void genieBarPointImpl(float u, float v, float /*pw*/, float ph, float out[2]) {
    const double rad = 20.0 * (M_PI / 180.0);  // Math.toRadians(BAR_TILT_DEG)
    const float c = static_cast<float>(std::cos(rad));
    const float s = static_cast<float>(std::sin(rad));
    out[0] = u * c - v * s;
    out[1] = 0.03f * ph + u * s + v * c;
}

}  // namespace

int64_t genieCardStartMs(int32_t index) { return 3500LL + static_cast<int64_t>(index) * 2500LL; }

float genieAvatarU(int32_t index, float pw) { return (index - 2.5f) * 0.138f * pw; }

float genieScrollAt(int64_t relMs, float pw) {
    const float half = 0.138f * pw / 2.0f;
    if (relMs <= 2600) return -half;
    if (relMs >= 13500) return half;
    const float f = static_cast<float>(relMs - 2600) / static_cast<float>(13500 - 2600);
    return -half + 0.138f * pw * f;
}

void genieBarPoint(float u, float v, float pw, float ph, float out[2]) {
    genieBarPointImpl(u, v, pw, ph, out);
}

void genieAvatarPosAt(int32_t index, int64_t relMs, float pw, float ph, float out[2]) {
    genieBarPointImpl(genieAvatarU(index, pw) - genieScrollAt(relMs, pw), 0.0f, pw, ph, out);
}

void genieAvatarNamePosAt(int32_t index, int64_t relMs, float pw, float ph, float out[2]) {
    genieBarPointImpl(genieAvatarU(index, pw) - genieScrollAt(relMs, pw), 0.088f * pw, pw, ph, out);
}

void geniePetalOffset(int32_t k, float pw, float out[2]) {
    const double a = (k * 45) * (M_PI / 180.0);  // Math.toRadians((k * 45).toDouble())
    out[0] = 0.0145f * pw * static_cast<float>(std::sin(a));
    out[1] = -0.0145f * pw * static_cast<float>(std::cos(a));
}

void genieBarEndFlowerCentre(float pw, float ph, float out[2]) {
    genieBarPointImpl(0.428f * pw, 0.0f, pw, ph, out);
}

void genieCardPoint(float lx, float ly, float pw, float ph, float out[2]) {
    const double rad = -16.0 * (M_PI / 180.0);  // Math.toRadians(CARD_TILT_DEG)
    const float c = static_cast<float>(std::cos(rad));
    const float s = static_cast<float>(std::sin(rad));
    out[0] = 0.02f * pw + lx * c - ly * s;
    out[1] = -0.05f * ph + lx * s + ly * c;
}

std::vector<uint8_t> geniePlan(float pwIn, float phIn, const std::vector<std::string>& avatarUris,
                               const std::vector<std::string>& photoUris, int64_t insertAtMs,
                               const std::string& idBase) {
    const float pw = pwIn > 1.0f ? pwIn : 1080.0f;
    const float ph = phIn > 1.0f ? phIn : 1920.0f;
    const float box = pw * 130.0f / 360.0f;
    const int64_t dur = 16000;
    const float barCY = 0.03f * ph;
    const std::string groupId = idBase + "-group";
    auto pctW = [&](float frac) { return frac * pw / box * 100.0f; };
    auto pctPx = [&](float px) { return px / box * 100.0f; };

    ClipRec group;
    group.id = groupId; group.name = "Genie"; group.type = kGroup;
    group.startMs = insertAtMs; group.durationMs = dur;
    group.color = 0xFF3949ABLL;

    // ---- background: FILL_COLOR keys black -> grey -> black (packColor) ----
    float pcBlack[2], pcGrey[2];
    packColor(0xFF000000LL, pcBlack);
    packColor(0xFFE9E9ECLL, pcGrey);
    ClipRec background = frameBackground(idBase + "-bg", 0xFF000000LL, pw, ph, box,
                                         insertAtMs, dur, groupId);
    background.track(kFillColor, {
        key(0, pcBlack[0], pcBlack[1]),
        key(1800, pcBlack[0], pcBlack[1], kCubicInOut),
        key(2800, pcGrey[0], pcGrey[1]),
        key(13500, pcGrey[0], pcGrey[1], kCubicInOut),
        key(14800, pcBlack[0], pcBlack[1]),
        key(dur, pcBlack[0], pcBlack[1])});

    // ---- the pill / bar morph tracks (barKeys) ----
    const float barWPct = pctW(0.92f), barHPct = pctW(0.13f);
    const float pillWPct = pctW(0.203f), pillHPct = pctW(0.098f);
    auto barKeys = [&](float offX, float offY, float mul) {
        std::vector<std::pair<int32_t, std::vector<PlanKey>>> tracks;
        tracks.emplace_back(kPos, std::vector<PlanKey>{
            key(0, offX, offY), key(1000, offX, offY, kCubicOut),
            key(2100, offX, barCY + offY), key(13800, offX, barCY + offY, kCubicInOut),
            key(15250, offX, offY)});
        tracks.emplace_back(kRotation, std::vector<PlanKey>{
            key(0, 0.0f, 0.0f), key(1000, 0.0f, 0.0f, kCubicOut),
            key(2100, 20.0f, 0.0f), key(13800, 20.0f, 0.0f, kCubicInOut),
            key(15250, 0.0f, 0.0f)});
        tracks.emplace_back(kScale, std::vector<PlanKey>{
            key(0, pillWPct * mul, pillHPct * mul),
            key(1000, pillWPct * mul, pillHPct * mul, kCubicOut),
            key(2100, barWPct * mul, barHPct * mul),
            key(13800, barWPct * mul, barHPct * mul, kCubicInOut),
            key(15250, pillWPct * mul, pillHPct * mul)});
        return tracks;
    };
    ClipRec bar = baseChild(idBase + "-bar", "Pill / Bar", kShape, 0xFF90A4AELL,
                            insertAtMs, dur, groupId);
    bar.fillMode = kFillSolid;
    bar.textColor = 0xFFFFFFFFLL;
    bar.hasShapePattern = true; bar.shapePattern = "ROUNDED_RECT";
    bar.shapeCornerRadius = 65.0f;
    bar.shapeScaleX = 0.92f * pw / box;
    bar.shapeScaleY = 0.13f * pw / box;
    bar.scalePercent = barWPct;
    bar.transformY = barCY;
    bar.rotationDeg = 20.0f;
    bar.tracks = barKeys(0.0f, 0.0f, 1.0f);

    const float shadowOffX = 0.004f * pw;
    const float shadowOffY = 0.008f * pw;
    ClipRec barShadow = baseChild(idBase + "-barshadow", "Bar Shadow", kShape, 0xFF90A4AELL,
                                  insertAtMs, dur, groupId);
    barShadow.fillMode = kFillSolid;
    barShadow.textColor = 0xFF8E8E96LL;
    barShadow.hasShapePattern = true; barShadow.shapePattern = "ROUNDED_RECT";
    barShadow.shapeCornerRadius = 65.0f;
    barShadow.shapeScaleX = 0.92f * pw / box * 1.02f;
    barShadow.shapeScaleY = 0.13f * pw / box * 1.02f;
    barShadow.scalePercent = barWPct * 1.02f;
    barShadow.transformX = shadowOffX;
    barShadow.transformY = barCY + shadowOffY;
    barShadow.rotationDeg = 20.0f;
    barShadow.opacity = 45.0f;
    barShadow.tracks = barKeys(shadowOffX, shadowOffY, 1.02f);
    barShadow.track(kOpacity, {key(2100, 0.0f, 0.0f, kCubicOut), key(2600, 45.0f, 0.0f),
                               key(13500, 45.0f, 0.0f, kCubicIn), key(14200, 0.0f, 0.0f)});

    // ---- the light streak ----
    const float streakWPct = pctW(0.98f);
    const float streakHPct = pctW(0.055f);
    ClipRec streak = baseChild(idBase + "-streak", "Light Streak", kShape, 0xFF90A4AELL,
                               insertAtMs, dur, groupId);
    streak.fillMode = kFillGradient;
    streak.gradType = kGradRadial;
    streak.gradColor1 = 0xFFFFFFFFLL;
    streak.gradColor2 = 0x00A9C6FFLL;
    streak.hasShapePattern = true; streak.shapePattern = "ROUNDED_RECT";
    streak.shapeCornerRadius = 65.0f;
    streak.shapeScaleX = 0.98f * pw / box;
    streak.shapeScaleY = 0.055f * pw / box;
    streak.scalePercent = streakWPct;
    streak.transformY = barCY * 0.5f;
    streak.opacity = 0.0f;
    streak.fx = {{"glow", {30.0f, 60.0f, 160.0f}}, {"directional_blur", {0.0f, 45.0f, 20.0f}}};
    streak.track(kOpacity, {key(1000, 0.0f, 0.0f, kCubicOut), key(1250, 100.0f, 0.0f),
                            key(1900, 100.0f, 0.0f, kCubicIn), key(2250, 0.0f, 0.0f)});
    streak.track(kScale, {key(1000, streakWPct * 0.30f, streakHPct * 0.55f, kCubicOut),
                          key(1600, streakWPct, streakHPct)});

    // ---- the flower petals (pill centre -> bar end -> pill centre) ----
    float endCentre[2];
    genieBarEndFlowerCentre(pw, ph, endCentre);
    const double tiltRad = 20.0 * (M_PI / 180.0);
    std::vector<ClipRec> petals;
    for (int32_t k = 0; k < 8; k++) {
        const float ang = static_cast<float>(k * 45);
        float off[2];
        geniePetalOffset(k, pw, off);
        // The original rotates the offset in Double and narrows after.
        const float endOffX = static_cast<float>(off[0] * std::cos(tiltRad) - off[1] * std::sin(tiltRad));
        const float endOffY = static_cast<float>(off[0] * std::sin(tiltRad) + off[1] * std::cos(tiltRad));
        const float endX = endCentre[0] + endOffX;
        const float endY = endCentre[1] + endOffY;
        ClipRec p = baseChild(idBase + "-petal" + std::to_string(k + 1),
                              "Flower Petal " + std::to_string(k + 1), kShape, 0xFF8D6E63LL,
                              insertAtMs, dur, groupId);
        p.fillMode = kFillSolid;
        p.textColor = 0xFF202024LL;
        p.hasShapePattern = true; p.shapePattern = "OVAL";
        p.shapeScaleX = 0.013f * pw / box;
        p.shapeScaleY = 0.026f * pw / box;
        p.scalePercent = pctW(0.013f);
        p.transformX = off[0];
        p.transformY = off[1];
        p.rotationDeg = ang;
        p.track(kPos, {key(0, off[0], off[1]), key(1200, off[0], off[1]),
                       key(2350, endX, endY), key(14100, endX, endY), key(14700, off[0], off[1])});
        p.track(kRotation, {key(0, ang, 0.0f), key(1200, ang, 0.0f),
                            key(2350, ang + 20.0f, 0.0f), key(14100, ang + 20.0f, 0.0f),
                            key(14700, ang, 0.0f)});
        p.track(kOpacity, {key(0, 100.0f, 0.0f), key(1000, 100.0f, 0.0f, kCubicIn),
                           key(1200, 0.0f, 0.0f), key(2350, 0.0f, 0.0f, kCubicOut),
                           key(2650, 100.0f, 0.0f), key(13800, 100.0f, 0.0f, kCubicIn),
                           key(14100, 0.0f, 0.0f), key(14700, 0.0f, 0.0f, kCubicOut),
                           key(15250, 100.0f, 0.0f)});
        petals.push_back(p);
    }

    // ---- avatars + name labels ----
    auto scrollPosKeys = [&](int32_t index, float v) {
        float p0[2], p1[2];
        genieBarPointImpl(genieAvatarU(index, pw), v, pw, ph, p0);
        genieBarPointImpl(genieAvatarU(index, pw) - 0.138f * pw, v, pw, ph, p1);
        return std::vector<PlanKey>{key(2600, p0[0], p0[1]), key(13500, p1[0], p1[1])};
    };
    auto avatarOpacityKeys = [&](int32_t index) {
        const int64_t fi = 1900 + static_cast<int64_t>(index) * 90;
        return std::vector<PlanKey>{
            key(fi, 0.0f, 0.0f, kCubicOut), key(fi + 350, 100.0f, 0.0f),
            key(13800, 100.0f, 0.0f, kCubicIn), key(14400, 0.0f, 0.0f)};
    };
    const float avatarRestPct = pctW(0.115f);
    std::vector<ClipRec> avatars, avatarNames;
    for (int32_t i = 0; i < 6; i++) {
        const int64_t fi = 1900 + static_cast<int64_t>(i) * 90;
        float p0[2];
        genieBarPointImpl(genieAvatarU(i, pw), 0.0f, pw, ph, p0);
        ClipRec a = baseChild(idBase + "-avatar" + std::to_string(i + 1),
                              std::string("Avatar ") + kGenieNames[i], kShape, 0xFF66BB6ALL,
                              insertAtMs, dur, groupId);
        a.fillMode = kFillImage;
        a.hasFillUri = true; a.fillUri = avatarUris[static_cast<size_t>(i)];
        a.fillImageMode = kImgFill;
        a.hasShapePattern = true; a.shapePattern = "CIRCLE";
        a.shapeScaleX = 0.115f * pw / box;
        a.shapeScaleY = 0.115f * pw / box;
        a.scalePercent = avatarRestPct;
        a.transformX = p0[0];
        a.transformY = p0[1];
        a.track(kPos, scrollPosKeys(i, 0.0f));
        a.track(kScale, {key(fi, avatarRestPct * 0.4f, 0.0f, kBackOut),
                         key(fi + 350, avatarRestPct, 0.0f)});
        a.track(kOpacity, avatarOpacityKeys(i));
        avatars.push_back(a);

        const float v = 0.088f * pw;
        float np[2];
        genieBarPointImpl(genieAvatarU(i, pw), v, pw, ph, np);
        ClipRec n = baseChild(idBase + "-avname" + std::to_string(i + 1),
                              std::string("Name ") + kGenieNames[i], kText, 0xFF3992CELL,
                              insertAtMs, dur, groupId);
        n.hasText = true; n.text = kGenieNames[i];
        n.textColor = 0xFF2B2B30LL;
        n.fontWeight = 600;
        n.scalePercent = 0.030f / 0.11f * 100.0f;
        n.transformX = np[0];
        n.transformY = np[1];
        n.rotationDeg = 20.0f;
        n.track(kPos, scrollPosKeys(i, v));
        n.track(kOpacity, avatarOpacityKeys(i));
        avatarNames.push_back(n);
    }

    // ---- selection rings ----
    std::vector<ClipRec> rings;
    for (int32_t i = 0; i < 4; i++) {
        const int32_t avatarIdx = i + 1;
        const int64_t s = genieCardStartMs(i);
        float p0[2];
        genieBarPointImpl(genieAvatarU(avatarIdx, pw), 0.0f, pw, ph, p0);
        ClipRec r = baseChild(idBase + "-ring" + std::to_string(i + 1),
                              std::string("Ring ") + kGenieCardNames[i], kShape, 0xFFFFCA28LL,
                              insertAtMs, dur, groupId);
        r.fillMode = kFillNone;
        r.hasShapePattern = true; r.shapePattern = "CIRCLE";
        r.shapeScaleX = 0.13f * pw / box;
        r.shapeScaleY = 0.13f * pw / box;
        r.scalePercent = pctW(0.13f);
        r.strokeColor = kGenieRingColors[i];
        r.strokeWidth = 360.0f * 0.0075f;
        r.transformX = p0[0];
        r.transformY = p0[1];
        r.opacity = 0.0f;
        r.track(kPos, scrollPosKeys(avatarIdx, 0.0f));
        r.track(kOpacity, {key(s - 50, 0.0f, 0.0f, kCubicOut), key(s + 250, 100.0f, 0.0f),
                           key(s + 2000 - 50, 100.0f, 0.0f, kCubicIn),
                           key(s + 2500 - 50, 0.0f, 0.0f)});
        rings.push_back(r);
    }

    // ---- the four profile cards ----
    const float baseRot = -16.0f;
    auto cardKeys = [&](float posX, float posY, float restA, float restB, float rotBase,
                        int64_t s) {
        std::vector<std::pair<int32_t, std::vector<PlanKey>>> tracks;
        tracks.emplace_back(kPos, std::vector<PlanKey>{
            key(s, posX, posY + 0.035f * ph, kCubicOut), key(s + 550, posX, posY),
            key(s + 2000, posX, posY, kCubicIn), key(s + 2500, posX, posY + 0.05f * ph)});
        tracks.emplace_back(kScale, std::vector<PlanKey>{
            key(s, restA * 0.55f, restB * 0.55f, kBackOut), key(s + 550, restA, restB),
            key(s + 2000, restA, restB, kCubicIn), key(s + 2500, restA * 0.72f, restB * 0.72f)});
        tracks.emplace_back(kRotation, std::vector<PlanKey>{
            key(s, rotBase + 26.0f, 0.0f, kCubicOut), key(s + 550, rotBase, 0.0f),
            key(s + 2000, rotBase, 0.0f, kCubicIn), key(s + 2500, rotBase - 11.0f, 0.0f)});
        tracks.emplace_back(kRotX, std::vector<PlanKey>{
            key(s, 12.0f, 0.0f, kCubicOut), key(s + 550, 0.0f, 0.0f),
            key(s + 2000, 0.0f, 0.0f, kCubicIn), key(s + 2500, -8.0f, 0.0f)});
        tracks.emplace_back(kRotY, std::vector<PlanKey>{
            key(s, -38.0f, 0.0f, kCubicOut), key(s + 550, 0.0f, 0.0f),
            key(s + 2000, 0.0f, 0.0f, kCubicIn), key(s + 2500, 30.0f, 0.0f)});
        tracks.emplace_back(kOpacity, std::vector<PlanKey>{
            key(s, 0.0f, 0.0f, kCubicOut), key(s + 300, 100.0f, 0.0f),
            key(s + 2000, 100.0f, 0.0f, kCubicIn), key(s + 2500, 0.0f, 0.0f)});
        return tracks;
    };
    struct CardRec { std::vector<ClipRec> all; };
    std::vector<CardRec> cards;
    for (int32_t i = 0; i < 4; i++) {
        const int64_t s = genieCardStartMs(i);
        const std::string cp = idBase + "-card" + std::to_string(i + 1);
        const std::string nm = kGenieCardNames[i];
        auto shapeChild = [&](const std::string& id, const std::string& name, float lx, float ly,
                              float wPx, float hPx, float restB) {
            float pos[2];
            genieCardPoint(lx, ly, pw, ph, pos);
            const float restA = pctPx(wPx);
            ClipRec c = baseChild(id, name, kShape, 0xFFAB47BCLL, insertAtMs, dur, groupId);
            c.shapeScaleX = wPx / box;
            c.shapeScaleY = hPx / box;
            c.scalePercent = restA;
            c.transformX = pos[0];
            c.transformY = pos[1];
            c.rotationDeg = baseRot;
            c.tracks = cardKeys(pos[0], pos[1], restA, restB, baseRot, s);
            return c;
        };
        auto textChild = [&](const std::string& id, const std::string& name, float lx, float ly,
                             const std::string& text, float fontFrac, int64_t color, int32_t weight,
                             bool italic) {
            float pos[2];
            genieCardPoint(lx, ly, pw, ph, pos);
            const float f = fontFrac / 0.11f * 100.0f;
            ClipRec c = baseChild(id, name, kText, 0xFF3992CELL, insertAtMs, dur, groupId);
            c.hasText = true; c.text = text;
            c.textColor = color;
            c.fontWeight = weight;
            c.italic = italic;
            c.scalePercent = f;
            c.transformX = pos[0];
            c.transformY = pos[1];
            c.rotationDeg = baseRot;
            c.tracks = cardKeys(pos[0], pos[1], f, 0.0f, baseRot, s);
            return c;
        };

        const float photoW = 0.60f * pw;
        const float photoH = 0.42f * ph;
        float photoPos[2];
        genieCardPoint(0.0f, 0.0f, pw, ph, photoPos);
        const float photoRestA = pctPx(photoW);
        const float photoRestB = pctPx(photoH);
        const float cornerRho = (0.030f * pw) / ((photoW < photoH ? photoW : photoH) / 2.0f);
        const float cornerCoerced = cornerRho < 0.0f ? 0.0f : (cornerRho > 1.0f ? 1.0f : cornerRho);
        ClipRec photo = baseChild(cp + "-photo", "Photo " + nm, kShape, 0xFFAB47BCLL,
                                  insertAtMs, dur, groupId);
        photo.fillMode = kFillImage;
        photo.hasFillUri = true; photo.fillUri = photoUris[static_cast<size_t>(i)];
        photo.fillImageMode = kImgFill;
        photo.hasShapePattern = true; photo.shapePattern = "ROUNDED_RECT";
        photo.shapeCornerRadius = 65.0f * cornerCoerced;
        photo.shapeScaleX = photoW / box;
        photo.shapeScaleY = photoH / box;
        photo.scalePercent = photoRestA;
        photo.transformX = photoPos[0];
        photo.transformY = photoPos[1];
        photo.rotationDeg = baseRot;
        photo.tracks = cardKeys(photoPos[0], photoPos[1], photoRestA, photoRestB, baseRot, s);

        ClipRec title = textChild(cp + "-title", "Name " + nm, -0.03f * pw, -0.115f * ph,
                                  kGenieCardTitles[i], 0.150f, 0xFFFFFFFFLL, 800, true);
        const float pillH = 0.050f * ph;
        ClipRec suggestPill = shapeChild(cp + "-sugpill", "Suggests Pill " + nm,
                                         -0.055f * pw, 0.150f * ph, 0.44f * pw, pillH, pctPx(pillH));
        suggestPill.fillMode = kFillSolid;
        suggestPill.textColor = 0xB31E1E24LL;
        suggestPill.hasShapePattern = true; suggestPill.shapePattern = "ROUNDED_RECT";
        suggestPill.shapeCornerRadius = 65.0f;

        std::vector<ClipRec> cardPetals;
        for (int32_t k = 0; k < 8; k++) {
            const float ang = static_cast<float>(k * 45);
            const double a = (k * 45) * (M_PI / 180.0);
            // The card petals' offset is computed in Double (the original
            // multiplies Float x Double) and narrowed once, unlike
            // GenieSpec.petalOffset's Float form — ported as written.
            const float offX = static_cast<float>(0.0095f * pw * std::sin(a));
            const float offY = static_cast<float>(-0.0095f * pw * std::cos(a));
            const float lx = -0.245f * pw + offX;
            const float ly = 0.150f * ph + offY;
            float pos[2];
            genieCardPoint(lx, ly, pw, ph, pos);
            const float wPx = 0.009f * pw;
            const float hPx = 0.018f * pw;
            const float restA = pctPx(wPx);
            ClipRec p = baseChild(cp + "-petal" + std::to_string(k + 1),
                                  "Card Flower " + nm + " " + std::to_string(k + 1),
                                  kShape, 0xFF8D6E63LL, insertAtMs, dur, groupId);
            p.fillMode = kFillSolid;
            p.textColor = 0xFFFFFFFFLL;
            p.hasShapePattern = true; p.shapePattern = "OVAL";
            p.shapeScaleX = wPx / box;
            p.shapeScaleY = hPx / box;
            p.scalePercent = restA;
            p.transformX = pos[0];
            p.transformY = pos[1];
            p.rotationDeg = baseRot + ang;
            p.tracks = cardKeys(pos[0], pos[1], restA, pctPx(hPx), baseRot + ang, s);
            cardPetals.push_back(p);
        }

        ClipRec suggestsText = textChild(cp + "-sug", "Suggests " + nm, -0.115f * pw,
                                         0.1375f * ph, "Genie suggests", 0.0255f,
                                         0xFFFFFFFFLL, 600, false);
        ClipRec activityText = textChild(cp + "-act", "Activity " + nm, -0.115f * pw,
                                         0.1625f * ph, kGenieActivities[i], 0.022f,
                                         0xFFD8D8DELL, 500, false);
        ClipRec invitePill = shapeChild(cp + "-invitepill", "Invite Pill " + nm,
                                        0.212f * pw, 0.150f * ph, 0.125f * pw, pillH, pctPx(pillH));
        invitePill.fillMode = kFillSolid;
        invitePill.textColor = 0x40FFFFFFLL;
        invitePill.hasShapePattern = true; invitePill.shapePattern = "ROUNDED_RECT";
        invitePill.shapeCornerRadius = 65.0f;
        ClipRec inviteText = textChild(cp + "-invite", "Invite " + nm, 0.212f * pw,
                                       0.150f * ph, "Invite", 0.026f, 0xFFFFFFFFLL, 700, false);

        CardRec card;
        card.all.push_back(inviteText);
        card.all.push_back(invitePill);
        card.all.push_back(activityText);
        card.all.push_back(suggestsText);
        for (const ClipRec& p : cardPetals) card.all.push_back(p);
        card.all.push_back(suggestPill);
        card.all.push_back(title);
        card.all.push_back(photo);
        cards.push_back(card);
    }

    std::vector<ClipRec> clips;
    clips.push_back(group);
    for (const CardRec& c : cards) {
        for (const ClipRec& cl : c.all) clips.push_back(cl);
    }
    for (const ClipRec& r : rings) clips.push_back(r);
    for (const ClipRec& n : avatarNames) clips.push_back(n);
    for (const ClipRec& a : avatars) clips.push_back(a);
    clips.push_back(streak);
    for (const ClipRec& p : petals) clips.push_back(p);
    clips.push_back(bar);
    clips.push_back(barShadow);
    clips.push_back(background);
    return finishPlan(clips);
}

// ======================================================================
// UserTemplateOps (model/UserTemplate.kt)
// ======================================================================

namespace {

// Kotlin's Char.isWhitespace over a UTF-8 string: the union of
// Character.isWhitespace and Character.isSpaceChar (the Unicode White_Space
// set plus the FS/GS/RS/US controls Java counts as whitespace).
bool utf8IsBlank(const std::string& s) {
    size_t i = 0;
    bool any = false;
    while (i < s.size()) {
        const auto b = static_cast<uint8_t>(s[i]);
        uint32_t cp;
        size_t n;
        if (b < 0x80) { cp = b; n = 1; }
        else if ((b & 0xE0) == 0xC0) { cp = b & 0x1F; n = 2; }
        else if ((b & 0xF0) == 0xE0) { cp = b & 0x0F; n = 3; }
        else if ((b & 0xF8) == 0xF0) { cp = b & 0x07; n = 4; }
        else { cp = 0xFFFD; n = 1; }  // malformed byte: not whitespace
        for (size_t j = 1; j < n && i + j < s.size(); j++) {
            cp = (cp << 6) | (static_cast<uint8_t>(s[i + j]) & 0x3F);
        }
        i += n;
        any = true;
        const bool ws = (cp >= 0x09 && cp <= 0x0D) || (cp >= 0x1C && cp <= 0x1F) ||
            cp == 0x20 || cp == 0x85 || cp == 0xA0 || cp == 0x1680 ||
            (cp >= 0x2000 && cp <= 0x200A) || cp == 0x2028 || cp == 0x2029 ||
            cp == 0x202F || cp == 0x205F || cp == 0x3000;
        if (!ws) return false;
    }
    return any || s.empty();  // isNotBlank("") is false: empty counts as blank
}

bool keyInside(const std::vector<int32_t>& keyToClip, int32_t key) {
    return key >= 0 && static_cast<size_t>(key) < keyToClip.size() && keyToClip[key] >= 0;
}

}  // namespace

std::vector<uint8_t> userMediaRefs(const std::vector<std::string>& refs, int32_t clipCount) {
    std::vector<std::string> out;
    for (int32_t i = 0; i < clipCount; i++) {
        for (int32_t slot = 0; slot < 6; slot++) {
            const std::string& r = refs[static_cast<size_t>(i * 6 + slot)];
            if (r.empty() || utf8IsBlank(r)) continue;
            bool seen = false;
            for (const std::string& o : out) {
                if (o == r) { seen = true; break; }
            }
            if (!seen) out.push_back(r);
        }
    }
    PlanWriter w;
    w.i32(static_cast<int32_t>(out.size()));
    for (const std::string& s : out) w.str(s);
    return w.buf;
}

std::vector<int32_t> userCapturePlan(
    const std::vector<int32_t>& idKey, const std::vector<int32_t>& groupKey,
    const std::vector<int32_t>& parentKey, const std::vector<int32_t>& controllerKey,
    const std::vector<int32_t>& maskKey, const std::vector<int32_t>& tfKey,
    const std::vector<int32_t>& cpKey, const std::vector<int32_t>& prKey,
    const std::vector<int32_t>& keyToClip, const std::vector<uint8_t>& has,
    int32_t groupKeyOf, const std::vector<uint8_t>& prSelf) {
    const size_t n = idKey.size();
    std::vector<int32_t> out;
    out.reserve(n * 7);
    for (size_t i = 0; i < n; i++) {
        const bool isRoot = idKey[i] == groupKeyOf;
        const bool hasParent = has[i * 7 + 0] != 0;
        const bool hasGroup = has[i * 7 + 1] != 0;
        const bool hasController = has[i * 7 + 2] != 0;
        const bool hasMask = has[i * 7 + 3] != 0;
        const bool hasTf = has[i * 7 + 4] != 0;
        const bool hasCp = has[i * 7 + 5] != 0;
        const bool hasPr = has[i * 7 + 6] != 0;
        int32_t parentMode;
        if (isRoot || !hasParent) parentMode = 0;
        else if (keyInside(keyToClip, parentKey[i])) parentMode = 1;
        else if (hasGroup && keyInside(keyToClip, groupKey[i])) parentMode = 2;
        else parentMode = 0;
        out.push_back(parentMode);
        out.push_back(!isRoot && hasGroup && keyInside(keyToClip, groupKey[i]) ? 1 : 0);
        out.push_back(hasController && keyInside(keyToClip, controllerKey[i]) ? 1 : 0);
        out.push_back(hasMask && keyInside(keyToClip, maskKey[i]) ? 1 : 0);
        out.push_back(hasTf && keyInside(keyToClip, tfKey[i]) ? 1 : 0);
        out.push_back(hasCp && keyInside(keyToClip, cpKey[i]) ? 1 : 0);
        out.push_back(hasPr && (prSelf[i] != 0 || keyInside(keyToClip, prKey[i])) ? 1 : 0);
    }
    return out;
}

std::vector<int64_t> userInstantiatePlan(
    const std::vector<int32_t>& groupKey, const std::vector<int32_t>& parentKey,
    const std::vector<int32_t>& controllerKey, const std::vector<int32_t>& maskKey,
    const std::vector<int32_t>& tfKey, const std::vector<int32_t>& cpKey,
    const std::vector<int32_t>& prKey, const std::vector<int32_t>& keyToClip,
    const std::vector<uint8_t>& has, const std::vector<int64_t>& startMs, int64_t insertAtMs) {
    const size_t n = groupKey.size();
    std::vector<int64_t> out;
    out.reserve(1 + n * 7);
    // shift = insertAtMs - root.startMs (the root is clip 0 by the
    // SavedTemplate contract); wrapping Long arithmetic like the JVM's.
    out.push_back(static_cast<int64_t>(static_cast<uint64_t>(insertAtMs) -
                                       static_cast<uint64_t>(startMs[0])));
    auto target = [&](int32_t key, bool present) -> int64_t {
        if (!present || key < 0 || static_cast<size_t>(key) >= keyToClip.size()) return -1;
        return keyToClip[key];
    };
    for (size_t i = 0; i < n; i++) {
        out.push_back(target(groupKey[i], has[i * 7 + 1] != 0));
        out.push_back(target(parentKey[i], has[i * 7 + 0] != 0));
        out.push_back(target(controllerKey[i], has[i * 7 + 2] != 0));
        out.push_back(target(maskKey[i], has[i * 7 + 3] != 0));
        out.push_back(target(tfKey[i], has[i * 7 + 4] != 0));
        out.push_back(target(cpKey[i], has[i * 7 + 5] != 0));
        out.push_back(target(prKey[i], has[i * 7 + 6] != 0));
    }
    return out;
}

}  // namespace am
