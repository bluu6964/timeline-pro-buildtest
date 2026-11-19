// 2D Circle Motion Tracker (Gradual Native Migration, Step 1A):
// Pure C++17 port of engine/LkTracker.kt (CircleTracker).
// Follows a round region across video frames using:
//   1. Shi-Tomasi corner detection inside the circle (5x5 structure tensor + grid suppression)
//   2. Pyramidal Lucas-Kanade optical flow (4 levels, 15x15 window)
//   3. Forward-backward consistency check + Normalized Cross-Correlation (NCC) appearance check
//   4. Deterministic pair-hypothesis RANSAC similarity fit (translation + rotation + uniform scale)
//      with least-squares refinement over inliers
//   5. Consecutive-miss tolerance and periodic corner reseeding
//
// Plain C++17, no Android or GL headers: host-testable.
#pragma once

#include <cstdint>
#include <vector>

namespace am {

class LkCircleTracker {
public:
    static constexpr int kWin = 15;
    static constexpr int kMaxIter = 20;
    static constexpr float kEps = 0.03f;
    static constexpr float kFbTol = 1.5f;
    static constexpr int kMinPoints = 4;
    static constexpr int kMaxMisses = 3;
    static constexpr float kMinEig = 4.0f;
    static constexpr int kReseedEvery = 2;
    static constexpr float kMinScale = 0.65f;
    static constexpr float kMaxScale = 1.55f;
    static constexpr float kMinNcc = 0.5f;
    static constexpr int kMaxLevels = 4;

    explicit LkCircleTracker(int maxPoints = 60);

    // Places the circle on the first frame (w * h float luma, 0..255) and detects corners.
    // Returns false when too little texture is found inside the circle.
    bool start(const float* gray, int w, int h, float cx, float cy, float radius);

    // Follows the circle into the next frame (same size as first frame).
    // Returns false when the object is lost or leaves the frame.
    bool step(const float* gray, int w, int h);

    float cx() const { return cx_; }
    float cy() const { return cy_; }
    float radius() const { return radius_; }
    float rotationDeg() const { return rotationDeg_; }
    float scaleTotal() const { return scaleTotal_; }
    int pointCount() const { return n_; }

    // Copies up to maxCount tracked corners as (x, y) pairs into out. Returns count copied.
    int copyPoints(float* out, int maxCount) const;

private:
    struct Level {
        int w = 0;
        int h = 0;
        std::vector<float> img;
        std::vector<float> ix;
        std::vector<float> iy;
    };

    struct Similarity {
        float a = 1.0f;
        float b = 0.0f;
        float tx = 0.0f;
        float ty = 0.0f;
        int inliers = 0;
        float tolerance = 0.0f;
    };

    bool miss() { return ++misses_ <= kMaxMisses; }
    void detect(const Level& l);
    bool trackPoint(const std::vector<Level>& a, const std::vector<Level>& b,
                    float x, float y, float& outX, float& outY);
    bool estimateSimilarity(const float* dxs, const float* dys, const uint8_t* ok,
                            int good, Similarity& out) const;

    static std::vector<Level> buildPyramid(const float* gray, int w, int h, int maxLevels = kMaxLevels);
    static Level makeLevel(int w, int h, std::vector<float> img);
    static float sample(const float* a, int w, int h, float x0, float y0);
    static float median(const float* v, const uint8_t* use, int len, int count);

    int maxPoints_ = 60;
    float cx_ = 0.0f;
    float cy_ = 0.0f;
    float radius_ = 0.0f;
    float rotationDeg_ = 0.0f;
    float scaleTotal_ = 1.0f;

    int n_ = 0;
    std::vector<float> pts_;
    std::vector<Level> prev_;
    int startCount_ = 0;
    int sinceReseed_ = 0;
    int misses_ = 0;

    // Per-point scratch buffers.
    float tpl_[kWin * kWin];
    float tgx_[kWin * kWin];
    float tgy_[kWin * kWin];
};

}  // namespace am
