// Host self-test for engine/camera.cpp: with no camera it must give the same corners as the renderer's own layer model
// (flat, with the Z depth factor, and turned in 3D at the screen centre); the camera must pan / zoom / dolly / roll the way
// a viewer expects.
//   g++ -std=c++17 -O1 -g -fsanitize=address,undefined -Wall -Wextra -I.. ../engine/camera.cpp camera_selftest.cpp -o camera_selftest && ./camera_selftest
#include <cmath>
#include <cstdio>

#include "engine/camera.h"
#include "engine/gl_math.h"

using namespace am;

static int fails = 0, checks = 0;
static void near(double v, double want, double tol, const char* what) {
    ++checks;
    if (std::fabs(v - want) > tol) { ++fails; std::printf("FAIL %s: got %.4f want %.4f\n", what, v, want); }
}

static void base(float* in) {
    for (int i = 0; i < kCamIn; ++i) in[i] = 0.f;
    in[0] = 1080; in[1] = 1920; in[2] = 1080; in[3] = 1920; in[4] = 1; in[5] = 1;
    in[9] = 1; in[10] = 1; in[16] = 1000;
    in[21] = 1;
}

// Corners of the old model (gl_math.h) in canvas px from the top-left.
static void oldCorners(const Mat4& model, bool flat, float W, float H, float* out) {
    static const float c[4][2] = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
    Mat4 m = Mat4::canvas(W, H) * (flat ? Mat4::flattenZ() : Mat4::identity()) * model;
    for (int i = 0; i < 4; ++i) {
        const float x = m.m[0] * c[i][0] + m.m[4] * c[i][1] + m.m[12];
        const float y = m.m[1] * c[i][0] + m.m[5] * c[i][1] + m.m[13];
        const float w = m.m[3] * c[i][0] + m.m[7] * c[i][1] + m.m[15];
        out[i * 2] = (x / w * 0.5f + 0.5f) * W;
        out[i * 2 + 1] = (0.5f - y / w * 0.5f) * H;
    }
}

int main() {
    float in[kCamIn], q[8], o[8];
    // 1) flat layer: moved, scaled, rotated, picture smaller than the canvas, cropped
    base(in);
    in[2] = 1600; in[3] = 900; in[4] = 0.5f; in[5] = 0.8f;
    in[6] = 120; in[7] = -80; in[9] = 0.7f; in[10] = 0.7f; in[11] = 25;
    if (!cameraQuad(in, q)) { std::puts("FAIL behind"); return 1; }
    {
        const float cw = 1600 * 0.5f, ch = 900 * 0.8f;
        // rotation about the centre: pivot 0 -> offset = base
        oldCorners(layerModel(1080, 1920, cw, ch, 120, -80, 0.7f, 0.7f, 25), false, 1080, 1920, o);
        for (int i = 0; i < 8; ++i) near(q[i], o[i], 0.05, "flat corners");
    }
    // 2) Z depth: the old layer has scale and position times D / (D - Z)
    base(in);
    in[6] = 100; in[7] = 50; in[8] = 400; in[9] = in[10] = 1.f;
    if (!cameraQuad(in, q)) return 1;
    {
        const float f = 1000.f / (1000.f - 400.f);
        oldCorners(layerModel(1080, 1920, 1080, 1920, 100 * f, 50 * f, f, f, 0), false, 1080, 1920, o);
        for (int i = 0; i < 8; ++i) near(q[i], o[i], 0.05, "depth corners");
    }
    // 3) turned in 3D, layer in the middle of the canvas: same as layerModel3d (its own perspective is at the centre too)
    base(in);
    in[12] = 30; in[13] = -40; in[11] = 10;
    if (!cameraQuad(in, q)) return 1;
    {
        oldCorners(layerModel3d(1080, 1920, 1080, 1920, 0, 0, 0, 0, 1, 1, 10, 30, -40, 1000), true, 1080, 1920, o);
        for (int i = 0; i < 8; ++i) near(q[i], o[i], 0.05, "3d corners");
    }
    // 4) camera: pan right by 100 -> the scene moves LEFT by 100
    base(in);
    in[17] = 1; in[18] = 100;
    cameraQuad(in, q);
    near(q[0], 0 - 100, 0.01, "pan x"); near(q[1], 0, 0.01, "pan y");
    // 5) zoom 2: corners go twice as far from the centre
    base(in);
    in[17] = 1; in[21] = 2;
    cameraQuad(in, q);
    near(q[0], 540 - 1080, 0.01, "zoom x"); near(q[1], 960 - 1920, 0.01, "zoom y");
    // 6) dolly into the scene by 500 = a layer at the scene plane looks 1000 / (1000 - 500) = 2x as big
    base(in);
    in[17] = 1; in[20] = 500;
    cameraQuad(in, q);
    near(q[0], 540 - 1080, 0.01, "dolly x");
    // 7) roll of 90 deg: the camera turns clockwise, the scene counter-clockwise: TL corner (-540, -960) goes to (-960, 540)
    base(in);
    in[17] = 1; in[24] = 90;
    cameraQuad(in, q);
    near(q[0], 540 - 960, 0.05, "roll x"); near(q[1], 960 + 540, 0.05, "roll y");
    // 8) parallax: camera pans right; a layer that is nearer (Z = 500) moves more than one at Z = 0
    base(in);
    in[17] = 1; in[18] = 100;
    cameraQuad(in, q);
    const float far0 = q[0];
    base(in);
    in[17] = 1; in[18] = 100; in[8] = 500;
    cameraQuad(in, q);
    ++checks;
    if (!(q[0] - 540 * (1000.f / 500.f) < far0 - 540 * 1.0f + 0.01f && q[0] < far0 - 0.5f)) { ++fails; std::printf("FAIL parallax %.2f vs %.2f\n", q[0], far0); }
    // 9) behind the camera: dolly past the layer
    base(in);
    in[17] = 1; in[20] = 1100;
    ++checks;
    if (cameraQuad(in, q)) { ++fails; std::puts("FAIL behind camera should return false"); }

    // 10) depth (stacking order): no camera = -Z; dolly moves everything the same way; orbit puts the layer that was at the
    //     side behind / in front; a layer the camera passed is "behind the camera".
    {
        float d = 0.f;
        base(in);
        in[8] = 300;
        ++checks; if (!cameraDepth(in, &d)) { ++fails; std::puts("FAIL depth none"); }
        near(d, -300.0, 0.01, "depth no camera = -Z");
        base(in);
        in[8] = 300; in[17] = 1; in[20] = 200;
        cameraDepth(in, &d);
        near(d, -500.0, 0.01, "depth with dolly");
        // Two layers at Z 0 and Z 400 (400 nearer): nearer has the smaller depth
        float a = 0.f, b = 0.f;
        base(in); in[17] = 1; cameraDepth(in, &a);
        base(in); in[8] = 400; in[17] = 1; cameraDepth(in, &b);
        ++checks; if (!(b < a)) { ++fails; std::puts("FAIL nearer layer must have smaller depth"); }
        // Orbit 90 deg about Y: a layer at x = +300 (flat, Z 0) swings toward / away from the camera: its depth changes by ~300
        base(in); in[6] = 300; in[17] = 1; in[23] = 90;
        ++checks; if (!cameraDepth(in, &d)) { ++fails; std::puts("FAIL orbit depth"); }
        ++checks; if (std::fabs(std::fabs(d) - 300.0) > 1.0) { ++fails; std::printf("FAIL orbit depth %.2f\n", d); }
        // Dolly past the layer
        base(in); in[17] = 1; in[20] = 1100;
        ++checks; if (cameraDepth(in, &d)) { ++fails; std::puts("FAIL depth behind camera should be false"); }
    }

    // 11) Build 262: cameraPoints at the four corners == cameraQuad, across placements
    //     (flat / Z / 3D turn / camera pan+dolly+orbit+roll). The grid floor and the
    //     extrusion walls are built from cameraPoints, so they must meet the faces
    //     (cameraQuad pins) exactly.
    {
        const float bw = 800.f, bh = 600.f;  // fitted picture of the placement below
        for (int trial = 0; trial < 3; ++trial) {
            base(in);
            in[2] = 800; in[3] = 600;  // content == fitted (canvas is bigger, fit = min(1080/800, 1920/600) -> bw != 800!)
            // Use a canvas exactly the content size so fit = 1 and the corners are (+-400, +-300).
            in[0] = 800; in[1] = 600;
            if (trial == 1) { in[6] = 130; in[7] = -60; in[8] = 250; in[11] = 20; in[12] = 25; in[13] = -35; in[9] = 1.4f; in[10] = 0.8f; }
            if (trial == 2) {
                in[6] = -80; in[7] = 40; in[8] = 100; in[11] = -15; in[12] = -20; in[13] = 30;
                in[17] = 1; in[18] = 60; in[19] = -45; in[20] = 150; in[21] = 1.3f; in[22] = 18; in[23] = -22; in[24] = 12;
            }
            float quad[8];
            ++checks;
            if (!cameraQuad(in, quad)) { ++fails; std::printf("FAIL points trial %d: quad behind\n", trial); continue; }
            const float pts[12] = {-bw / 2, -bh / 2, 0, bw / 2, -bh / 2, 0, bw / 2, bh / 2, 0, -bw / 2, bh / 2, 0};
            float out[8];
            uint8_t ok[4] = {0, 0, 0, 0};
            cameraPoints(in, pts, 4, out, ok);
            for (int i = 0; i < 4; ++i) {
                ++checks;
                if (!ok[i]) { ++fails; std::printf("FAIL points trial %d corner %d not ok\n", trial, i); }
                near(out[i * 2], quad[i * 2], 0.05, "points==quad x");
                near(out[i * 2 + 1], quad[i * 2 + 1], 0.05, "points==quad y");
            }
        }
    }
    // 12) Build 262: a point pushed along the plane normal (qz = +depth — local +z points
    //     INTO the screen in this math, p.z = v.z - zToward; the extrusion's back face sits
    //     at qz = +depth) lands CLOSER to the centre than the same point at qz = 0, by the
    //     perspective factor: offset scales by 1 / (1 + depth / D) with no camera.
    {
        base(in);
        const float pts[6] = {200, 100, 0, 200, 100, 250};
        float out[4];
        uint8_t ok[2] = {0, 0};
        cameraPoints(in, pts, 2, out, ok);
        ++checks; if (!ok[0] || !ok[1]) { ++fails; std::puts("FAIL qz points not ok"); }
        const double f = 1.0 / (1.0 + 250.0 / 1000.0);
        near(out[2], 540 + 200 * f, 0.05, "back point x");
        near(out[3], 960 + 100 * f, 0.05, "back point y");
        near(out[0], 540 + 200, 0.05, "front point x");
    }
    // 13) Build 262: a point behind the camera reports ok = 0 (dolly past it).
    {
        base(in);
        in[17] = 1; in[20] = 1100;
        const float pts[3] = {0, 0, 0};
        float out[2];
        uint8_t ok[1] = {1};
        cameraPoints(in, pts, 1, out, ok);
        ++checks; if (ok[0]) { ++fails; std::puts("FAIL behind-camera point must not be ok"); }
    }
    // 14) Build 262: the 3D Space's DEFAULT view (pitch = the space clip's rotation3dX
    //     default, tested at +22 deg here and pinned in SpaceRig's default): the grid
    //     floor must read as a floor — all four corners in front of the camera, the FAR
    //     edge nearer the canvas centre (the horizon) than the NEAR edge, and the near
    //     edge BELOW the centre. If this fails with +22, the default pitch sign is wrong.
    {
        base(in);
        in[17] = 1; in[22] = 22;  // camRotX = the space's pitch
        const float S = 2400.f, gy = 0.30f * 1920.f;
        // identity layer placement: content = canvas, base 0, scale 1 (base() already)
        const float pts[12] = {
            -S / 2, gy, -S / 2,  S / 2, gy, -S / 2,   // near edge (z toward the viewer)
            -S / 2, gy, S / 2,   S / 2, gy, S / 2     // far edge
        };
        float out[8];
        uint8_t ok[4] = {0, 0, 0, 0};
        cameraPoints(in, pts, 4, out, ok);
        for (int i = 0; i < 4; ++i) { ++checks; if (!ok[i]) { ++fails; std::printf("FAIL floor corner %d behind\n", i); } }
        const float nearY = (out[1] + out[3]) / 2.f, farY = (out[5] + out[7]) / 2.f;
        ++checks; if (!(nearY > 960.f)) { ++fails; std::printf("FAIL floor near edge must be below centre: %.1f\n", nearY); }
        ++checks; if (!(std::fabs(farY - 960.f) < std::fabs(nearY - 960.f))) {
            ++fails; std::printf("FAIL floor far edge must sit nearer the horizon: far %.1f near %.1f\n", farY, nearY);
        }
    }

    std::printf("camera_selftest: %d checks, %d failed\n", checks, fails);
    return fails ? 1 : 0;
}
