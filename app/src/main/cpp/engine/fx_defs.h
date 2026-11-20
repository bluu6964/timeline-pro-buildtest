// The effect definition table: the 167 effects the Effects panel offers, their
// slider definitions, their EXT-group defaults and native group types.
//
// This table used to live as a Kotlin literal list (ClipFxLibrary.all in
// model/ClipFx.kt, 167 FxDef entries). It was migrated here so the C++ side is
// the single source of truth — the FxType enum in fx_plan.h already mirrored
// the same numbers by hand. The data rows in fx_defs_data.h were GENERATED
// from the Kotlin table by fx_defs_gen.py and verified field-by-field (floats
// as raw bits) against a dump of the Kotlin original before the Kotlin table
// was deleted. The @Serializable ClipFx instance class stays in Kotlin (it is
// stored in project files); only this read-only definition data moved.
//
// No Android / GL dependencies: plain data + pure maths, host-testable.
#pragma once

#include <cstdint>

namespace am {

/** One adjustable value of an effect (a slider in the Effects panel). */
struct FxParamDefData {
    const char* name;
    float min;
    float max;
    float def;
    /** One tick of the ruler slider = one step (values are whole steps from min). */
    float step;
    const char* unit;
};

/**
 * An effect the engine can run (nativeType = FxType in fx_plan.h). The order
 * and meaning of params are the p0, p1, p2 of that effect there. extras are
 * the effect's values past the declared params (from index 7 on: EXT-group
 * defaults, packed colours, anchor points); extType..ext4Type are the native
 * types of its EXT..EXT4 groups (values [7..13], [14..20], [21..27],
 * [28..34]), -1 when the effect has no such group.
 */
struct FxDefData {
    const char* id;
    int nativeType;
    const char* name;
    const char* category;
    const char* blurb;
    const FxParamDefData* params;
    int paramCount;
    const float* extras;
    int extraCount;
    int extType;
    int ext2Type;
    int ext3Type;
    int ext4Type;
};

int fxDefCount();
const FxDefData& fxDefAt(int index);
const FxDefData* fxDefById(const char* id);

/**
 * An Alight point (x, y) packed into one float: two 12-bit signed fields,
 * (x + 2048) * 4096 + (y + 2048) — exact in a float for |x|, |y| <= 2047 (the
 * components are rounded to whole units). Ported from ClipFxLibrary.packPoint;
 * fxUnpackPoint in fx_plan.cpp is the inverse.
 */
float fxPackPoint(float x, float y);

/** A swatch colour (0xAARRGGBB) for the key hue / strength sliders of Chroma Key. Ported from ClipFxLibrary.keySwatchArgb. */
uint32_t fxKeySwatchArgb(float hue, float strength, float brightness);

// ClipFxLibrary's other two scalar helpers did not need porting: keyFromRgb
// and grainFrame already exist in fx_plan.cpp (fxKeyFromRgb / fxGrainFrame)
// with identical maths — the JNI bridge calls those, so each helper now has
// exactly one implementation.

}  // namespace am
