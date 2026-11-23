// 3D camera (v151, Alight Motion style Camera layer). The scene is the layers at their 3D places (x, y, Z depth, turned in
// 3D); the camera pans, dollies (Z), zooms, rolls and orbits, and looks at the scene through ONE pinhole camera (distance
// D, the same D as the Z depth). A layer under a camera is drawn on the 4 projected corners of its picture (the renderer's
// corner pin path, quad_warp.h), so there is no second drawing code.
//
// Layer (before the camera), the same maths as gl_math.h layerModel3d with the layer's own perspective replaced by the
// scene's one:   point = base + pivot + Rx * Ry * Rz * S * (q - pivot),  z (into the screen) = -Z + that point's z
// Camera:        p -= (panX, panY);  p = Rz(-roll) Ry(-rotY) Rx(-rotX) p;  z -= dolly;  (x, y) *= zoom;  screen = (x, y) / (1 + z / D)
// With no camera, Z = 0 and no 3D turn this is exactly the flat layer; with Z != 0 it is the old "depth factor" D / (D - Z).
// Pure C++, host-tested: tests/camera_selftest.cpp.
#pragma once

#include <cstdint>

namespace am {

constexpr int kCamIn = 25;
// in[]: 0 canvasW, 1 canvasH, 2 contentW, 3 contentH (whole picture), 4 cropW, 5 cropH (fractions of it),
//       6 baseX, 7 baseY (canvas px from the centre, position WITHOUT the depth factor), 8 Z (px, toward the camera = +),
//       9 scaleX, 10 scaleY (without the depth factor; negative = flipped), 11 rotationDeg (Z), 12 rotX, 13 rotY (Compose
//       convention), 14 pivotX, 15 pivotY (px from the canvas centre), 16 D (px),
//       17 hasCamera (0 / 1), 18 panX, 19 panY, 20 dolly (px, + = camera moves into the scene), 21 zoom (1 = 100 %),
//       22 camRotX, 23 camRotY, 24 camRoll (degrees)
// out[8]: x, y of TL, TR, BR, BL in canvas px from the TOP-LEFT of the canvas (the corner pin order).
// Returns false when a corner is at / behind the camera (the layer is not drawn then).
bool cameraQuad(const float* in, float* out);

// Distance of the CENTRE of the layer (its pivot-compensated origin) from the camera plane, after pan / orbit / roll /
// dolly: bigger = farther. Same in[] as cameraQuad (content / crop / scale / pivot are not used). Used to order the layers:
// the farthest is drawn first, so a layer the camera has dollied past or orbited behind goes to the back. With no camera
// it is -Z. Returns false when the centre is at / behind the camera (the layer is not drawn then).
bool cameraDepth(const float* in, float* depth);

// Build 262 (3D Space): the same projection as cameraQuad for ARBITRARY points of the layer's plane.
// pts = n triples (qx, qy, qz): qx / qy are the point in the FITTED picture, px from its centre (the
// same space cameraQuad's corners live in: corner = (+-bw/2, +-bh/2, 0)), and qz is the point's offset
// along the plane's own normal in the same px (local +z points INTO the screen in this math —
// p.z = v.z - zToward — so the extrusion's back face sits at qz = +depth, and a 3D scene's floor
// recedes toward qz = +extent). The point goes through the layer's own rotation about its pivot
// exactly like a corner, then the camera. out = n pairs (canvas px from the top-left); ok[i] tells
// whether point i is in front of the camera (out is still written, but meaningless when ok is 0).
// cameraQuad's corners ARE cameraPoints at the four corners with qz = 0 (the selftest pins it).
void cameraPoints(const float* in, const float* pts, int n, float* out, uint8_t* ok);

}  // namespace am
