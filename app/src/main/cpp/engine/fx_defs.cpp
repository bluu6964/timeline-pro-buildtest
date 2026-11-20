#include "fx_defs.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "fx_defs_data.h"

namespace am {

int fxDefCount() { return kFxDefCount; }

const FxDefData& fxDefAt(int index) {
    static const FxDefData kEmpty = {"", 0, "", "", "", nullptr, 0, nullptr, 0, -1, -1, -1, -1};
    if (index < 0 || index >= kFxDefCount) return kEmpty;
    return kFxDefs[index];
}

const FxDefData* fxDefById(const char* id) {
    if (!id) return nullptr;
    for (int i = 0; i < kFxDefCount; ++i) {
        if (std::strcmp(kFxDefs[i].id, id) == 0) return &kFxDefs[i];
    }
    return nullptr;
}

float fxPackPoint(float x, float y) {
    // kotlin.math.roundToInt (Math.round on the JVM) is floor(v + 0.5f) in
    // float arithmetic, ties toward +infinity; then coerceIn(-2048, 2047).
    auto pack = [](float v) {
        int r = (int)std::floor(v + 0.5f);
        r = std::min(2047, std::max(-2048, r));
        return r + 2048;
    };
    return (float)(pack(x) * 4096 + pack(y));
}

uint32_t fxKeySwatchArgb(float hue, float strength, float brightness) {
    // Ported line-for-line from the Kotlin original (HSV sector maths; the
    // float % operator is fmod in both languages, truncation toward zero in
    // both float->int conversions).
    const float h = std::fmod(std::fmod(hue, 360.f) + 360.f, 360.f) / 60.f;
    const float s = std::min(1.f, std::max(0.f, strength / 100.f));
    const int i = (int)h % 6;
    const float f = h - (int)h;
    const float p = 1.f - s;
    const float q = 1.f - s * f;
    const float t = 1.f - s * (1.f - f);
    float r, g, b;
    switch (i) {
        case 0: r = 1.f; g = t; b = p; break;
        case 1: r = q; g = 1.f; b = p; break;
        case 2: r = p; g = 1.f; b = t; break;
        case 3: r = p; g = q; b = 1.f; break;
        case 4: r = t; g = p; b = 1.f; break;
        default: r = 1.f; g = p; b = q; break;
    }
    const float k = 0.25f + 0.75f * std::min(1.f, std::max(0.f, brightness / 100.f));
    auto c = [k](float x) -> uint32_t {
        int v = (int)(x * k * 255.f + 0.5f);
        return (uint32_t)std::min(255, std::max(0, v));
    };
    return 0xFF000000u | (c(r) << 16) | (c(g) << 8) | c(b);
}

}  // namespace am
