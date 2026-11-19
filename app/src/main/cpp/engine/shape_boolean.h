// After Motion native engine - double-precision 2D polygon boolean operations
// (Union, Subtract, Intersect, XOR) under non-zero winding rule (1:1 parity with ShapeBoolean.kt).
#pragma once

#include <cstdint>
#include <vector>

namespace am {
namespace shape_bool {

enum BoolOp : int32_t {
    kUnion = 0,
    kSubtract = 1,
    kIntersect = 2,
    kXor = 3
};

struct Pt {
    double x = 0.0;
    double y = 0.0;
};

using Ring = std::vector<Pt>;
using Region = std::vector<Ring>;

double signedArea(const Ring& ring);
int windingNumber(const Pt& p, const Region& region);

// Combines regions a and b under op (Subtract is a - b), returning rings oriented
// positive-outer / negative-hole.
Region combine(const Region& a, const Region& b, BoolOp op);

// Flattens stored PATH segments into boolean rings (splitting at every MOVE).
Region flattenPathRings(const int32_t* ops, const int32_t* offsets, const int32_t* counts,
                        int segCount, const float* pts, int ptsLen, int stepsPerCurve);

}  // namespace shape_bool
}  // namespace am
