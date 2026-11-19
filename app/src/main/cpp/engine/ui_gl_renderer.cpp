#include "ui_gl_renderer.h"

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstring>

#define TAG "AmUiGl"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace am {

namespace {

// ---- GLSL ES 3.00 Shaders (all output premultiplied RGBA for GL_ONE, GL_ONE_MINUS_SRC_ALPHA) ----

static const char* kFlatVs = R"(#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec4 aColor;
layout(location = 2) in float aCov;
uniform vec2 uViewport;
out vec4 vColor;
out float vCov;
void main() {
    vec2 ndc = vec2((aPos.x / uViewport.x) * 2.0 - 1.0, 1.0 - (aPos.y / uViewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    vColor = aColor;
    vCov = aCov;
}
)";

static const char* kFlatFs = R"(#version 300 es
precision highp float;
in vec4 vColor;
in float vCov;
out vec4 fragColor;
void main() {
    float a = vColor.a * clamp(vCov, 0.0, 1.0);
    fragColor = vec4(vColor.rgb * a, a);
}
)";

static const char* kSdfVs = R"(#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aLocal;
layout(location = 2) in vec4 aParams;
layout(location = 3) in vec4 aFill;
layout(location = 4) in vec4 aStroke;
layout(location = 5) in vec4 aMeta;
uniform vec2 uViewport;
out vec2 vLocal;
out vec4 vParams;
out vec4 vFill;
out vec4 vStroke;
out vec4 vMeta;
void main() {
    vec2 ndc = vec2((aPos.x / uViewport.x) * 2.0 - 1.0, 1.0 - (aPos.y / uViewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    vLocal = aLocal;
    vParams = aParams;
    vFill = aFill;
    vStroke = aStroke;
    vMeta = aMeta;
}
)";

static const char* kSdfFs = R"(#version 300 es
precision highp float;
in vec2 vLocal;
in vec4 vParams;
in vec4 vFill;
in vec4 vStroke;
in vec4 vMeta;
out vec4 fragColor;
void main() {
    float type = vMeta.x;
    if (type < 0.5) {
        // Circle: params = (radius, strokeWidth, 0, 0)
        float r = vParams.x;
        float sw = vParams.y;
        float d = length(vLocal) - r;
        float fa = (vFill.a > 0.0001) ? smoothstep(0.6, -0.6, d) * vFill.a : 0.0;
        float sa = (sw > 0.0001 && vStroke.a > 0.0001)
            ? smoothstep(0.6, -0.6, abs(d) - sw * 0.5) * vStroke.a
            : 0.0;
        vec4 fillC = vec4(vFill.rgb * fa, fa);
        vec4 stC = vec4(vStroke.rgb * sa, sa);
        vec4 outC = stC + fillC * (1.0 - stC.a);
        if (outC.a < 0.001) discard;
        fragColor = outC;
    } else if (type < 1.5) {
        // Rounded rect: params = (halfW, halfH, cornerRadius, strokeWidth)
        vec2 hs = vParams.xy;
        float cr = min(vParams.z, min(hs.x, hs.y));
        float sw = vParams.w;
        vec2 q = abs(vLocal) - (hs - vec2(cr));
        float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cr;
        float fa = (vFill.a > 0.0001) ? smoothstep(0.6, -0.6, d) * vFill.a : 0.0;
        float sa = (sw > 0.0001 && vStroke.a > 0.0001)
            ? smoothstep(0.6, -0.6, abs(d) - sw * 0.5) * vStroke.a
            : 0.0;
        vec4 fillC = vec4(vFill.rgb * fa, fa);
        vec4 stC = vec4(vStroke.rgb * sa, sa);
        vec4 outC = stC + fillC * (1.0 - stC.a);
        if (outC.a < 0.001) discard;
        fragColor = outC;
    } else {
        // Line segment: params = (segLen, halfWidth, dashOn, dashOff), meta = (2, dashPhase, capRound, 0)
        float len = vParams.x;
        float hw = vParams.y;
        float dashOn = vParams.z;
        float dashOff = vParams.w;
        float phase = vMeta.y;
        float capRound = vMeta.z;
        float d;
        if (capRound > 0.5) {
            float cx = clamp(vLocal.x, 0.0, len);
            d = length(vec2(vLocal.x - cx, vLocal.y)) - hw;
        } else {
            float dx = max(-vLocal.x, vLocal.x - len);
            float dy = abs(vLocal.y) - hw;
            d = max(dx, dy);
        }
        float a = smoothstep(0.6, -0.6, d);
        if (dashOn > 0.001 && dashOff > 0.001) {
            float period = dashOn + dashOff;
            float halfOn = dashOn * 0.5;
            float c = mod(vLocal.x + phase - halfOn + period * 0.5, period) - period * 0.5;
            float dashMask = smoothstep(0.6, -0.6, abs(c) - halfOn);
            a *= dashMask;
        }
        float sa = a * vStroke.a;
        if (sa < 0.001) discard;
        fragColor = vec4(vStroke.rgb * sa, sa);
    }
}
)";

static const char* kPointVs = R"(#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec4 aColor;
layout(location = 2) in float aSize;
uniform vec2 uViewport;
out vec4 vColor;
out float vQuadSize;
out float vRadius;
void main() {
    vec2 ndc = vec2((aPos.x / uViewport.x) * 2.0 - 1.0, 1.0 - (aPos.y / uViewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    float q = max(aSize + 1.2, 2.0);
    gl_PointSize = q;
    vColor = aColor;
    vQuadSize = q;
    vRadius = aSize * 0.5;
}
)";

static const char* kPointFs = R"(#version 300 es
precision highp float;
in vec4 vColor;
in float vQuadSize;
in float vRadius;
out vec4 fragColor;
void main() {
    vec2 dpx = (gl_PointCoord - vec2(0.5)) * vQuadSize;
    float d = length(dpx) - vRadius;
    float a = smoothstep(0.6, -0.6, d) * vColor.a;
    if (a < 0.001) discard;
    fragColor = vec4(vColor.rgb * a, a);
}
)";

static GLuint compileShader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    if (!sh) return 0;
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {0};
        glGetShaderInfoLog(sh, sizeof(log) - 1, nullptr, log);
        LOGE("Shader compile failed (%u): %s", type, log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

static GLuint linkProgram(const char* vsSrc, const char* fsSrc) {
    GLuint vs = compileShader(GL_VERTEX_SHADER, vsSrc);
    if (!vs) return 0;
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fsSrc);
    if (!fs) {
        glDeleteShader(vs);
        return 0;
    }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512] = {0};
        glGetProgramInfoLog(prog, sizeof(log) - 1, nullptr, log);
        LOGE("Program link failed: %s", log);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

// Scope colours — exact match to ScopesPanel.kt
constexpr int32_t kPlotBg      = static_cast<int32_t>(0xFF0D0D0D);
constexpr int32_t kGridLine    = static_cast<int32_t>(0xFF262626);
constexpr int32_t kHistRed     = static_cast<int32_t>(0x80FF4444);
constexpr int32_t kHistGreen   = static_cast<int32_t>(0x8051FF51);
constexpr int32_t kHistBlue    = static_cast<int32_t>(0x805599FF);
constexpr int32_t kHistLuma    = static_cast<int32_t>(0xF2FFFFFF);
constexpr int32_t kWaveFaint   = static_cast<int32_t>(0x4DB7FFB7);  // alpha 0.30
constexpr int32_t kWaveBright  = static_cast<int32_t>(0xF2B7FFB7);  // alpha 0.95
constexpr int32_t kVecFaint    = static_cast<int32_t>(0x4DA8FFF2);  // alpha 0.30
constexpr int32_t kVecBright   = static_cast<int32_t>(0xF2A8FFF2);  // alpha 0.95
constexpr int32_t kVecTarget   = static_cast<int32_t>(0xFF4A4A4A);

// EasingEditor colours — exact match to EasingEditor.kt
constexpr int32_t kEaseGrid    = static_cast<int32_t>(0xFF4A4C50);
constexpr int32_t kEasePlay    = static_cast<int32_t>(0xFFC9CBCF);
constexpr int32_t kEaseArm     = static_cast<int32_t>(0xFF6B6E73);
constexpr int32_t kEaseWhite   = static_cast<int32_t>(0xFFFFFFFF);
constexpr int32_t kEaseHandle  = static_cast<int32_t>(0xFFEA1252);

constexpr float kVecFullScale = 0.65f;
constexpr float kEaseYMin = -0.3f;
constexpr float kEaseYMax = 1.3f;

inline float bt601Cb(int r, int g, int b) {
    return (-0.14713f * r - 0.28886f * g + 0.436f * b) / 255.0f;
}

inline float bt601Cr(int r, int g, int b) {
    return (0.615f * r - 0.51499f * g - 0.10001f * b) / 255.0f;
}

}  // namespace

UiGlRenderer::~UiGlRenderer() {
    release();
}

UiGlRenderer::Rgba UiGlRenderer::argbToRgba(int32_t argb) {
    const uint32_t u = static_cast<uint32_t>(argb);
    return {
        ((u >> 16) & 0xFFu) / 255.0f,
        ((u >> 8) & 0xFFu) / 255.0f,
        (u & 0xFFu) / 255.0f,
        ((u >> 24) & 0xFFu) / 255.0f
    };
}

bool UiGlRenderer::init() {
    if (initialized_) return true;
    if (!egl_.init()) {
        LOGE("EglCore init failed");
        return false;
    }
    currentSurf_ = egl_.bootstrapSurface();
    if (!compileShaders()) {
        release();
        return false;
    }

    // Flat VAO/VBO
    glGenVertexArrays(1, &vaoFlat_);
    glGenBuffers(1, &vboFlat_);
    glBindVertexArray(vaoFlat_);
    glBindBuffer(GL_ARRAY_BUFFER, vboFlat_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(FlatVertex),
                          reinterpret_cast<const void*>(offsetof(FlatVertex, x)));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, sizeof(FlatVertex),
                          reinterpret_cast<const void*>(offsetof(FlatVertex, r)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 1, GL_FLOAT, GL_FALSE, sizeof(FlatVertex),
                          reinterpret_cast<const void*>(offsetof(FlatVertex, cov)));

    // SDF VAO/VBO
    glGenVertexArrays(1, &vaoSdf_);
    glGenBuffers(1, &vboSdf_);
    glBindVertexArray(vaoSdf_);
    glBindBuffer(GL_ARRAY_BUFFER, vboSdf_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, x)));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, lx)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 4, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, p0)));
    glEnableVertexAttribArray(3);
    glVertexAttribPointer(3, 4, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, fr)));
    glEnableVertexAttribArray(4);
    glVertexAttribPointer(4, 4, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, sr)));
    glEnableVertexAttribArray(5);
    glVertexAttribPointer(5, 4, GL_FLOAT, GL_FALSE, sizeof(SdfVertex),
                          reinterpret_cast<const void*>(offsetof(SdfVertex, type)));

    // Point VAO/VBO
    glGenVertexArrays(1, &vaoPoint_);
    glGenBuffers(1, &vboPoint_);
    glBindVertexArray(vaoPoint_);
    glBindBuffer(GL_ARRAY_BUFFER, vboPoint_);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(PointVertex),
                          reinterpret_cast<const void*>(offsetof(PointVertex, x)));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, sizeof(PointVertex),
                          reinterpret_cast<const void*>(offsetof(PointVertex, r)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 1, GL_FLOAT, GL_FALSE, sizeof(PointVertex),
                          reinterpret_cast<const void*>(offsetof(PointVertex, size)));

    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    initialized_ = true;
    LOGI("UiGlRenderer initialized");
    return true;
}

bool UiGlRenderer::compileShaders() {
    progFlat_ = linkProgram(kFlatVs, kFlatFs);
    progSdf_ = linkProgram(kSdfVs, kSdfFs);
    progPoint_ = linkProgram(kPointVs, kPointFs);
    if (!progFlat_ || !progSdf_ || !progPoint_) return false;
    uFlatViewport_ = glGetUniformLocation(progFlat_, "uViewport");
    uSdfViewport_ = glGetUniformLocation(progSdf_, "uViewport");
    uPointViewport_ = glGetUniformLocation(progPoint_, "uViewport");
    return true;
}

void UiGlRenderer::release() {
    if (!initialized_ && surfaces_.empty()) {
        egl_.release();
        return;
    }
    if (initialized_) {
        egl_.makeCurrent(egl_.bootstrapSurface());
        currentSurf_ = egl_.bootstrapSurface();
        for (auto& kv : surfaces_) {
            if (kv.second.surface != EGL_NO_SURFACE) egl_.destroySurface(kv.second.surface);
            if (kv.second.window) ANativeWindow_release(kv.second.window);
        }
        surfaces_.clear();
        if (vboFlat_) glDeleteBuffers(1, &vboFlat_);
        if (vaoFlat_) glDeleteVertexArrays(1, &vaoFlat_);
        if (vboSdf_) glDeleteBuffers(1, &vboSdf_);
        if (vaoSdf_) glDeleteVertexArrays(1, &vaoSdf_);
        if (vboPoint_) glDeleteBuffers(1, &vboPoint_);
        if (vaoPoint_) glDeleteVertexArrays(1, &vaoPoint_);
        if (progFlat_) glDeleteProgram(progFlat_);
        if (progSdf_) glDeleteProgram(progSdf_);
        if (progPoint_) glDeleteProgram(progPoint_);
        vboFlat_ = vaoFlat_ = vboSdf_ = vaoSdf_ = vboPoint_ = vaoPoint_ = 0;
        progFlat_ = progSdf_ = progPoint_ = 0;
        initialized_ = false;
    }
    egl_.release();
    currentSurf_ = EGL_NO_SURFACE;
}

int UiGlRenderer::attachSurface(ANativeWindow* window, int w, int h) {
    if (!initialized_ || !window) {
        if (window) ANativeWindow_release(window);
        return 0;
    }
    EGLSurface surf = egl_.createWindowSurface(window);
    if (surf == EGL_NO_SURFACE) {
        ANativeWindow_release(window);
        return 0;
    }
    int sw = egl_.width(surf);
    int sh = egl_.height(surf);
    if (sw <= 0) sw = w;
    if (sh <= 0) sh = h;
    int id = nextSurfaceId_++;
    surfaces_[id] = SurfaceSlot{window, surf, sw, sh};
    return id;
}

void UiGlRenderer::resizeSurface(int surfaceId, int w, int h) {
    auto it = surfaces_.find(surfaceId);
    if (it == surfaces_.end()) return;
    if (w > 0) it->second.w = w;
    if (h > 0) it->second.h = h;
}

void UiGlRenderer::detachSurface(int surfaceId) {
    auto it = surfaces_.find(surfaceId);
    if (it == surfaces_.end()) return;
    if (currentSurf_ == it->second.surface) {
        egl_.makeCurrent(egl_.bootstrapSurface());
        currentSurf_ = egl_.bootstrapSurface();
    }
    if (it->second.surface != EGL_NO_SURFACE) egl_.destroySurface(it->second.surface);
    if (it->second.window) ANativeWindow_release(it->second.window);
    surfaces_.erase(it);
}

bool UiGlRenderer::beginSurface(int surfaceId, int& outW, int& outH, EGLSurface& outSurf) {
    if (!initialized_) return false;
    auto it = surfaces_.find(surfaceId);
    if (it == surfaces_.end()) return false;
    EGLSurface surf = it->second.surface;
    if (surf == EGL_NO_SURFACE) return false;
    if (currentSurf_ != surf) {
        if (!egl_.makeCurrent(surf)) {
            currentSurf_ = EGL_NO_SURFACE;
            return false;
        }
        currentSurf_ = surf;
    }
    int qw = egl_.width(surf);
    int qh = egl_.height(surf);
    outW = qw > 0 ? qw : it->second.w;
    outH = qh > 0 ? qh : it->second.h;
    if (outW <= 0 || outH <= 0) return false;
    outSurf = surf;
    glViewport(0, 0, outW, outH);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    return true;
}

void UiGlRenderer::clearColor(int32_t argb) {
    Rgba c = argbToRgba(argb);
    glClearColor(c.r * c.a, c.g * c.a, c.b * c.a, c.a);
    glClear(GL_COLOR_BUFFER_BIT);
}

void UiGlRenderer::resetBatches() {
    flatVerts_.clear();
    sdfVerts_.clear();
    pointVerts_.clear();
}

void UiGlRenderer::flushBatches(int w, int h) {
    const float fw = static_cast<float>(w);
    const float fh = static_cast<float>(h);

    if (!flatVerts_.empty()) {
        glUseProgram(progFlat_);
        glUniform2f(uFlatViewport_, fw, fh);
        glBindVertexArray(vaoFlat_);
        glBindBuffer(GL_ARRAY_BUFFER, vboFlat_);
        glBufferData(
            GL_ARRAY_BUFFER,
            static_cast<GLsizeiptr>(flatVerts_.size() * sizeof(FlatVertex)),
            flatVerts_.data(),
            GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, static_cast<GLsizei>(flatVerts_.size()));
    }

    if (!sdfVerts_.empty()) {
        glUseProgram(progSdf_);
        glUniform2f(uSdfViewport_, fw, fh);
        glBindVertexArray(vaoSdf_);
        glBindBuffer(GL_ARRAY_BUFFER, vboSdf_);
        glBufferData(
            GL_ARRAY_BUFFER,
            static_cast<GLsizeiptr>(sdfVerts_.size() * sizeof(SdfVertex)),
            sdfVerts_.data(),
            GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, static_cast<GLsizei>(sdfVerts_.size()));
    }

    if (!pointVerts_.empty()) {
        glUseProgram(progPoint_);
        glUniform2f(uPointViewport_, fw, fh);
        glBindVertexArray(vaoPoint_);
        glBindBuffer(GL_ARRAY_BUFFER, vboPoint_);
        glBufferData(
            GL_ARRAY_BUFFER,
            static_cast<GLsizeiptr>(pointVerts_.size() * sizeof(PointVertex)),
            pointVerts_.data(),
            GL_STREAM_DRAW);
        glDrawArrays(GL_POINTS, 0, static_cast<GLsizei>(pointVerts_.size()));
    }

    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

// ---- Geometry batch builders ----

void UiGlRenderer::addRect(float x, float y, float w, float h, Rgba fill) {
    if (fill.a <= 0.0f || w <= 0.0f || h <= 0.0f) return;
    const float x1 = x + w;
    const float y1 = y + h;
    flatVerts_.push_back({x,  y,  fill.r, fill.g, fill.b, fill.a, 1.0f});
    flatVerts_.push_back({x1, y,  fill.r, fill.g, fill.b, fill.a, 1.0f});
    flatVerts_.push_back({x1, y1, fill.r, fill.g, fill.b, fill.a, 1.0f});
    flatVerts_.push_back({x,  y,  fill.r, fill.g, fill.b, fill.a, 1.0f});
    flatVerts_.push_back({x1, y1, fill.r, fill.g, fill.b, fill.a, 1.0f});
    flatVerts_.push_back({x,  y1, fill.r, fill.g, fill.b, fill.a, 1.0f});
}

void UiGlRenderer::addTri(float x0, float y0, float x1, float y1, float x2, float y2, Rgba c) {
    flatVerts_.push_back({x0, y0, c.r, c.g, c.b, c.a, 1.0f});
    flatVerts_.push_back({x1, y1, c.r, c.g, c.b, c.a, 1.0f});
    flatVerts_.push_back({x2, y2, c.r, c.g, c.b, c.a, 1.0f});
}

void UiGlRenderer::addCircle(float cx, float cy, float radius, float strokeWidth, Rgba fill, Rgba stroke) {
    if (radius <= 0.0f && strokeWidth <= 0.0f) return;
    if (fill.a <= 0.0f && (strokeWidth <= 0.0f || stroke.a <= 0.0f)) return;
    const float ext = radius + strokeWidth * 0.5f + 1.5f;
    auto push = [&](float dx, float dy) {
        sdfVerts_.push_back({
            cx + dx, cy + dy, dx, dy,
            radius, strokeWidth, 0.0f, 0.0f,
            fill.r, fill.g, fill.b, fill.a,
            stroke.r, stroke.g, stroke.b, stroke.a,
            0.0f, 0.0f, 0.0f, 0.0f
        });
    };
    push(-ext, -ext); push( ext, -ext); push( ext,  ext);
    push(-ext, -ext); push( ext,  ext); push(-ext,  ext);
}

void UiGlRenderer::addRoundRect(
    float cx, float cy, float w, float h, float cornerR, float strokeWidth, Rgba fill, Rgba stroke) {
    if (w <= 0.0f || h <= 0.0f) return;
    if (fill.a <= 0.0f && (strokeWidth <= 0.0f || stroke.a <= 0.0f)) return;
    const float hw = w * 0.5f;
    const float hh = h * 0.5f;
    const float pad = strokeWidth * 0.5f + 1.5f;
    const float ex = hw + pad;
    const float ey = hh + pad;
    auto push = [&](float dx, float dy) {
        sdfVerts_.push_back({
            cx + dx, cy + dy, dx, dy,
            hw, hh, cornerR, strokeWidth,
            fill.r, fill.g, fill.b, fill.a,
            stroke.r, stroke.g, stroke.b, stroke.a,
            1.0f, 0.0f, 0.0f, 0.0f
        });
    };
    push(-ex, -ey); push( ex, -ey); push( ex,  ey);
    push(-ex, -ey); push( ex,  ey); push(-ex,  ey);
}

void UiGlRenderer::addSegmentSdf(
    float x0, float y0, float x1, float y1,
    float width, float dashOn, float dashOff, float dashPhase, bool capRound, Rgba stroke) {
    if (width <= 0.0f || stroke.a <= 0.0f) return;
    const float dx = x1 - x0;
    const float dy = y1 - y0;
    const float len = std::hypot(dx, dy);
    const float hw = width * 0.5f;
    if (len < 1e-4f) {
        if (capRound) addCircle(x0, y0, hw, 0.0f, stroke, {0, 0, 0, 0});
        return;
    }
    const float ux = dx / len;
    const float uy = dy / len;
    const float nx = -uy;
    const float ny = ux;
    const float padX = (capRound ? hw : 0.0f) + 1.5f;
    const float padY = hw + 1.5f;
    const float capFlag = capRound ? 1.0f : 0.0f;

    auto push = [&](float s, float t) {
        sdfVerts_.push_back({
            x0 + ux * s + nx * t,
            y0 + uy * s + ny * t,
            s, t,
            len, hw, dashOn, dashOff,
            0.0f, 0.0f, 0.0f, 0.0f,
            stroke.r, stroke.g, stroke.b, stroke.a,
            2.0f, dashPhase, capFlag, 0.0f
        });
    };
    push(-padX,       -padY);
    push(len + padX,  -padY);
    push(len + padX,   padY);
    push(-padX,       -padY);
    push(len + padX,   padY);
    push(-padX,        padY);
}

void UiGlRenderer::addPolylineRibbon(
    const float* xy, int count, bool closed, float width, Rgba stroke) {
    if (count < 2 || width <= 0.0f || stroke.a <= 0.0f) return;

    // Deduplicate near-identical consecutive points so tangents stay finite.
    std::vector<float> px;
    std::vector<float> py;
    px.reserve(static_cast<size_t>(count));
    py.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        const float x = xy[i * 2];
        const float y = xy[i * 2 + 1];
        if (px.empty() || std::hypot(x - px.back(), y - py.back()) > 1e-3f) {
            px.push_back(x);
            py.push_back(y);
        }
    }
    if (closed && px.size() > 2 && std::hypot(px.front() - px.back(), py.front() - py.back()) <= 1e-3f) {
        px.pop_back();
        py.pop_back();
    }
    const int n = static_cast<int>(px.size());
    if (n < 2) return;
    if (n == 2 && !closed) {
        addSegmentSdf(px[0], py[0], px[1], py[1], width, 0.0f, 0.0f, 0.0f, true, stroke);
        return;
    }

    const float hw = width * 0.5f;
    const float core = std::max(0.0f, hw - 0.5f);
    const float outer = hw + 0.6f;
    const float coreCov = std::min(1.0f, width);

    struct Ring {
        float lox, loy, lix, liy, rix, riy, rox, roy;
    };
    std::vector<Ring> rings(static_cast<size_t>(n));

    auto dirBetween = [&](int a, int b, float& ux, float& uy) -> bool {
        float dx = px[static_cast<size_t>(b)] - px[static_cast<size_t>(a)];
        float dy = py[static_cast<size_t>(b)] - py[static_cast<size_t>(a)];
        float d = std::hypot(dx, dy);
        if (d < 1e-5f) {
            ux = 1.0f;
            uy = 0.0f;
            return false;
        }
        ux = dx / d;
        uy = dy / d;
        return true;
    };

    for (int i = 0; i < n; ++i) {
        float u0x = 0.0f, u0y = 0.0f, u1x = 0.0f, u1y = 0.0f;
        bool hasIn = false;
        bool hasOut = false;
        if (closed) {
            hasIn = dirBetween((i + n - 1) % n, i, u0x, u0y);
            hasOut = dirBetween(i, (i + 1) % n, u1x, u1y);
        } else {
            if (i > 0) hasIn = dirBetween(i - 1, i, u0x, u0y);
            if (i < n - 1) hasOut = dirBetween(i, i + 1, u1x, u1y);
        }
        float mx = 0.0f, my = 1.0f, scale = 1.0f;
        if (hasIn && hasOut) {
            const float n0x = -u0y, n0y = u0x;
            const float n1x = -u1y, n1y = u1x;
            const float bx = n0x + n1x;
            const float by = n0y + n1y;
            const float bl = std::hypot(bx, by);
            if (bl > 1e-4f) {
                mx = bx / bl;
                my = by / bl;
                const float denom = std::max(mx * n1x + my * n1y, 0.4f);
                scale = 1.0f / denom;
            } else {
                mx = n1x;
                my = n1y;
                scale = 1.0f;
            }
        } else if (hasOut) {
            mx = -u1y;
            my = u1x;
        } else if (hasIn) {
            mx = -u0y;
            my = u0x;
        }
        const float cx = px[static_cast<size_t>(i)];
        const float cy = py[static_cast<size_t>(i)];
        const float cs = core * scale;
        const float os = outer * scale;
        rings[static_cast<size_t>(i)] = {
            cx + mx * os, cy + my * os,
            cx + mx * cs, cy + my * cs,
            cx - mx * cs, cy - my * cs,
            cx - mx * os, cy - my * os
        };
    }

    auto emitQuad = [&](float ax, float ay, float ac,
                        float bx, float by, float bc,
                        float cx, float cy, float cc,
                        float dx, float dy, float dc) {
        flatVerts_.push_back({ax, ay, stroke.r, stroke.g, stroke.b, stroke.a, ac});
        flatVerts_.push_back({bx, by, stroke.r, stroke.g, stroke.b, stroke.a, bc});
        flatVerts_.push_back({cx, cy, stroke.r, stroke.g, stroke.b, stroke.a, cc});
        flatVerts_.push_back({ax, ay, stroke.r, stroke.g, stroke.b, stroke.a, ac});
        flatVerts_.push_back({cx, cy, stroke.r, stroke.g, stroke.b, stroke.a, cc});
        flatVerts_.push_back({dx, dy, stroke.r, stroke.g, stroke.b, stroke.a, dc});
    };

    const int segs = closed ? n : (n - 1);
    for (int i = 0; i < segs; ++i) {
        const Ring& a = rings[static_cast<size_t>(i)];
        const Ring& b = rings[static_cast<size_t>((i + 1) % n)];
        emitQuad(a.lox, a.loy, 0.0f, a.lix, a.liy, coreCov, b.lix, b.liy, coreCov, b.lox, b.loy, 0.0f);
        if (core > 0.0f) {
            emitQuad(a.lix, a.liy, coreCov, a.rix, a.riy, coreCov, b.rix, b.riy, coreCov, b.lix, b.liy, coreCov);
        }
        emitQuad(a.rix, a.riy, coreCov, a.rox, a.roy, 0.0f, b.rox, b.roy, 0.0f, b.rix, b.riy, coreCov);
    }
}

void UiGlRenderer::addPolyline(
    const float* xy, int count, bool closed,
    float width, float dashOn, float dashOff, bool capRound, Rgba stroke) {
    if (count < 2 || width <= 0.0f || stroke.a <= 0.0f) return;
    // Translucent non-dashed polylines use non-overlapping AA miter ribbons so joins never double-darken.
    if (stroke.a < 0.99f && dashOn <= 0.0f) {
        addPolylineRibbon(xy, count, closed, width, stroke);
        return;
    }
    const int segs = closed ? count : (count - 1);
    float phase = 0.0f;
    const bool roundCaps = capRound && !(dashOn > 0.0f && dashOff > 0.0f);
    for (int i = 0; i < segs; ++i) {
        const int j = (i + 1) % count;
        const float x0 = xy[i * 2];
        const float y0 = xy[i * 2 + 1];
        const float x1 = xy[j * 2];
        const float y1 = xy[j * 2 + 1];
        addSegmentSdf(x0, y0, x1, y1, width, dashOn, dashOff, phase, roundCaps, stroke);
        phase += std::hypot(x1 - x0, y1 - y0);
    }
}

void UiGlRenderer::addPoint(float x, float y, float size, Rgba c) {
    if (c.a <= 0.0f || size <= 0.0f) return;
    pointVerts_.push_back({x, y, c.r, c.g, c.b, c.a, size});
}

// ---- Domain plot builders ----

void UiGlRenderer::buildQuarterGraticule(int w, int h) {
    const Rgba grid = argbToRgba(kGridLine);
    const float fw = static_cast<float>(w);
    const float fh = static_cast<float>(h);
    for (int i = 1; i <= 3; ++i) {
        const float x = fw * static_cast<float>(i) / 4.0f;
        const float y = fh * static_cast<float>(i) / 4.0f;
        addSegmentSdf(x, 0.0f, x, fh, 1.0f, 0.0f, 0.0f, 0.0f, false, grid);
        addSegmentSdf(0.0f, y, fw, y, 1.0f, 0.0f, 0.0f, 0.0f, false, grid);
    }
    // 1 px outer border matching Compose drawRect(GridLine, style = Stroke(1f))
    addRoundRect(fw * 0.5f, fh * 0.5f, fw - 1.0f, fh - 1.0f, 0.0f, 1.0f, {0, 0, 0, 0}, grid);
}

void UiGlRenderer::buildHistogramGeometry(
    int w, int h,
    const int32_t* histR, const int32_t* histG,
    const int32_t* histB, const int32_t* histY, int bins) {
    buildQuarterGraticule(w, h);
    if (!histR || !histG || !histB || !histY || bins <= 0) return;

    int32_t peak = 1;
    for (int i = 0; i < bins; ++i) {
        if (histR[i] > peak) peak = histR[i];
        if (histG[i] > peak) peak = histG[i];
        if (histB[i] > peak) peak = histB[i];
        if (histY[i] > peak) peak = histY[i];
    }

    const int cols = std::max(1, w);
    const float fw = static_cast<float>(w);
    const float fh = static_cast<float>(h);
    const float invPeak = 1.0f / static_cast<float>(peak);
    std::vector<float> ys(static_cast<size_t>(cols));

    auto computeHeights = [&](const int32_t* hist) {
        for (int dx = 0; dx < cols; ++dx) {
            const int b0 = (dx * bins) / cols;
            const int b1 = std::min(bins, std::max(b0 + 1, ((dx + 1) * bins) / cols));
            int32_t m = 0;
            for (int b = b0; b < b1; ++b) {
                if (hist[b] > m) m = hist[b];
            }
            ys[static_cast<size_t>(dx)] = fh - (static_cast<float>(m) * invPeak) * fh;
        }
    };

    auto emitFilledChannel = [&](const int32_t* hist, int32_t colorArgb) {
        computeHeights(hist);
        const Rgba c = argbToRgba(colorArgb);
        // Leading half-pixel from x=0 to x=0.5
        addTri(0.0f, fh, 0.5f, ys[0], 0.5f, fh, c);
        for (int dx = 0; dx + 1 < cols; ++dx) {
            const float x0 = static_cast<float>(dx) + 0.5f;
            const float x1 = static_cast<float>(dx + 1) + 0.5f;
            const float y0 = ys[static_cast<size_t>(dx)];
            const float y1 = ys[static_cast<size_t>(dx + 1)];
            addTri(x0, fh, x0, y0, x1, y1, c);
            addTri(x0, fh, x1, y1, x1, fh, c);
        }
        // Trailing half-pixel from cols-0.5 to fw
        const float lx = static_cast<float>(cols - 1) + 0.5f;
        addTri(lx, fh, lx, ys[static_cast<size_t>(cols - 1)], fw, fh, c);
    };

    emitFilledChannel(histR, kHistRed);
    emitFilledChannel(histG, kHistGreen);
    emitFilledChannel(histB, kHistBlue);

    // Luma trace on top as a crisp white line (alpha 0.95, 1.2 px)
    computeHeights(histY);
    std::vector<float> lumaPts(static_cast<size_t>(cols) * 2);
    for (int dx = 0; dx < cols; ++dx) {
        lumaPts[static_cast<size_t>(dx) * 2] = static_cast<float>(dx) + 0.5f;
        lumaPts[static_cast<size_t>(dx) * 2 + 1] = ys[static_cast<size_t>(dx)];
    }
    addPolyline(lumaPts.data(), cols, false, 1.2f, 0.0f, 0.0f, true, argbToRgba(kHistLuma));
}

void UiGlRenderer::buildWaveformGeometry(
    int w, int h,
    const int32_t* wave, int waveW, int waveH, int bins) {
    buildQuarterGraticule(w, h);
    if (!wave || waveW <= 0 || waveH <= 0 || bins <= 0) return;

    const int cols = std::max(1, w);
    const int perCol = std::max(1, waveH * std::max(1, waveW / cols));
    const float invPerCol = 1.0f / static_cast<float>(perCol);
    const float fh = static_cast<float>(h);
    const float invBins = 1.0f / static_cast<float>(bins);
    const Rgba faintC = argbToRgba(kWaveFaint);
    const Rgba brightC = argbToRgba(kWaveBright);

    std::vector<PointVertex> bright;
    bright.reserve(2048);

    for (int dx = 0; dx < cols; ++dx) {
        const int x0 = (dx * waveW) / cols;
        const int x1 = std::min(waveW, std::max(x0 + 1, ((dx + 1) * waveW) / cols));
        const float px = static_cast<float>(dx) + 0.5f;
        for (int level = 0; level < bins; ++level) {
            int32_t count = 0;
            for (int x = x0; x < x1; ++x) {
                count += wave[x * bins + level];
            }
            if (count == 0) continue;
            const float py = fh - (static_cast<float>(level) + 0.5f) * invBins * fh;
            if (static_cast<float>(count) * invPerCol >= 0.25f) {
                bright.push_back({px, py, brightC.r, brightC.g, brightC.b, brightC.a, 1.6f});
            } else {
                addPoint(px, py, 1.6f, faintC);
            }
        }
    }
    pointVerts_.insert(pointVerts_.end(), bright.begin(), bright.end());
}

void UiGlRenderer::buildVectorscopeGeometry(int w, int h, const int32_t* vec, int grid) {
    const Rgba gridC = argbToRgba(kGridLine);
    const float fw = static_cast<float>(w);
    const float fh = static_cast<float>(h);
    addRoundRect(fw * 0.5f, fh * 0.5f, fw - 1.0f, fh - 1.0f, 0.0f, 1.0f, {0, 0, 0, 0}, gridC);

    const float side = std::min(fw, fh);
    const float cx = fw * 0.5f;
    const float cy = fh * 0.5f;
    const float radius = side * 0.5f - 2.0f;
    if (radius <= 4.0f) return;

    addCircle(cx, cy, radius, 1.0f, {0, 0, 0, 0}, gridC);
    addCircle(cx, cy, radius * 0.5f, 1.0f, {0, 0, 0, 0}, gridC);
    addSegmentSdf(cx - radius, cy, cx + radius, cy, 1.0f, 0.0f, 0.0f, 0.0f, false, gridC);
    addSegmentSdf(cx, cy - radius, cx, cy + radius, 1.0f, 0.0f, 0.0f, 0.0f, false, gridC);

    // Six BT.601 primary / secondary target crosses
    const int primaries[6][3] = {
        {255, 0, 0}, {255, 0, 255}, {0, 0, 255},
        {0, 255, 255}, {0, 255, 0}, {255, 255, 0}
    };
    const Rgba targetC = argbToRgba(kVecTarget);
    for (int i = 0; i < 6; ++i) {
        const float u = bt601Cb(primaries[i][0], primaries[i][1], primaries[i][2]);
        const float v = bt601Cr(primaries[i][0], primaries[i][1], primaries[i][2]);
        const float tx = cx + (u / kVecFullScale) * radius;
        const float ty = cy - (v / kVecFullScale) * radius;
        addSegmentSdf(tx - 3.0f, ty, tx + 3.0f, ty, 1.2f, 0.0f, 0.0f, 0.0f, false, targetC);
        addSegmentSdf(tx, ty - 3.0f, tx, ty + 3.0f, 1.2f, 0.0f, 0.0f, 0.0f, false, targetC);
    }

    if (!vec || grid <= 0) return;
    int32_t peak = 1;
    const int total = grid * grid;
    for (int i = 0; i < total; ++i) {
        if (vec[i] > peak) peak = vec[i];
    }
    const float invPeak = 1.0f / static_cast<float>(peak);
    const float invGrid = 1.0f / static_cast<float>(grid);
    const Rgba faintC = argbToRgba(kVecFaint);
    const Rgba brightC = argbToRgba(kVecBright);

    std::vector<PointVertex> bright;
    bright.reserve(512);
    for (int by = 0; by < grid; ++by) {
        const float fy = (static_cast<float>(by) + 0.5f) * invGrid * 2.0f - 1.0f;
        const float py = cy + fy * radius;
        for (int bx = 0; bx < grid; ++bx) {
            const int32_t count = vec[by * grid + bx];
            if (count == 0) continue;
            const float fx = (static_cast<float>(bx) + 0.5f) * invGrid * 2.0f - 1.0f;
            const float px = cx + fx * radius;
            if (static_cast<float>(count) * invPeak >= 0.25f) {
                bright.push_back({px, py, brightC.r, brightC.g, brightC.b, brightC.a, 2.0f});
            } else {
                addPoint(px, py, 2.0f, faintC);
            }
        }
    }
    pointVerts_.insert(pointVerts_.end(), bright.begin(), bright.end());
}

void UiGlRenderer::buildEasingGeometry(
    int w, int h,
    float x1, float y1, float x2, float y2,
    float progress, float density,
    float padL, float padT, float padR, float padB) {
    const float fw = static_cast<float>(w);
    const float fh = static_cast<float>(h);
    const float pw = fw - padL - padR;
    const float ph = fh - padT - padB;
    if (pw <= 1.0f || ph <= 1.0f) return;
    const float left = padL;
    const float top = padT;
    const float bottom = top + ph;
    const float d = std::max(0.5f, density);

    auto sx = [&](float x) { return left + x * pw; };
    auto sy = [&](float y) { return bottom - (y - kEaseYMin) / (kEaseYMax - kEaseYMin) * ph; };

    // 4x4 dashed grid over 0..1 box
    const Rgba gridC = argbToRgba(kEaseGrid);
    const float dashOn = 6.0f * d;
    const float dashOff = 5.0f * d;
    const float gridW = 2.0f * d;
    for (int i = 0; i <= 4; ++i) {
        const float f = static_cast<float>(i) / 4.0f;
        addSegmentSdf(sx(f), sy(0.0f), sx(f), sy(1.0f), gridW, dashOn, dashOff, 0.0f, false, gridC);
        addSegmentSdf(sx(0.0f), sy(f), sx(1.0f), sy(f), gridW, dashOn, dashOff, 0.0f, false, gridC);
    }

    // Playhead vertical line
    const float clampedP = std::max(0.0f, std::min(1.0f, progress));
    const float px = sx(clampedP);
    addSegmentSdf(px, top, px, bottom, 2.0f * d, 0.0f, 0.0f, 0.0f, false, argbToRgba(kEasePlay));

    // Control arms
    const Rgba armC = argbToRgba(kEaseArm);
    addSegmentSdf(sx(0.0f), sy(0.0f), sx(x1), sy(y1), 1.5f * d, 0.0f, 0.0f, 0.0f, true, armC);
    addSegmentSdf(sx(1.0f), sy(1.0f), sx(x2), sy(y2), 1.5f * d, 0.0f, 0.0f, 0.0f, true, armC);

    // Cubic bezier curve (64 segments)
    constexpr int kSteps = 64;
    float curvePts[(kSteps + 1) * 2];
    for (int i = 0; i <= kSteps; ++i) {
        const float s = static_cast<float>(i) / static_cast<float>(kSteps);
        const float u = 1.0f - s;
        const float bx = 3.0f * u * u * s * x1 + 3.0f * u * s * s * x2 + s * s * s;
        const float by = 3.0f * u * u * s * y1 + 3.0f * u * s * s * y2 + s * s * s;
        curvePts[i * 2] = sx(bx);
        curvePts[i * 2 + 1] = sy(by);
    }
    const Rgba whiteC = argbToRgba(kEaseWhite);
    addPolyline(curvePts, kSteps + 1, false, 3.0f * d, 0.0f, 0.0f, true, whiteC);

    // Two control points (outer 13dp white, inner 10dp pink #EA1252)
    const Rgba handleC = argbToRgba(kEaseHandle);
    const float pts[2][2] = {{sx(x1), sy(y1)}, {sx(x2), sy(y2)}};
    for (int i = 0; i < 2; ++i) {
        addCircle(pts[i][0], pts[i][1], 13.0f * d, 0.0f, whiteC, {0, 0, 0, 0});
        addCircle(pts[i][0], pts[i][1], 10.0f * d, 0.0f, handleC, {0, 0, 0, 0});
    }
}

void UiGlRenderer::buildVectorCmdGeometry(
    const float* cmds, int cmdFloatCount,
    const int32_t* colors, int colorCount) {
    if (!cmds || cmdFloatCount <= 0) return;
    int p = 0;
    int cp = 0;
    while (p < cmdFloatCount) {
        const int op = static_cast<int>(cmds[p++]);
        switch (op) {
            case UI_CMD_LINE: {
                if (p + 8 > cmdFloatCount || cp + 1 > colorCount) return;
                const float x0 = cmds[p++];
                const float y0 = cmds[p++];
                const float x1 = cmds[p++];
                const float y1 = cmds[p++];
                const float width = cmds[p++];
                const float dashOn = cmds[p++];
                const float dashOff = cmds[p++];
                const bool capRound = cmds[p++] > 0.5f;
                const Rgba c = argbToRgba(colors[cp++]);
                addSegmentSdf(x0, y0, x1, y1, width, dashOn, dashOff, 0.0f, capRound, c);
                break;
            }
            case UI_CMD_POLYLINE: {
                if (p + 6 > cmdFloatCount || cp + 1 > colorCount) return;
                const bool closed = cmds[p++] > 0.5f;
                const float width = cmds[p++];
                const float dashOn = cmds[p++];
                const float dashOff = cmds[p++];
                const bool capRound = cmds[p++] > 0.5f;
                const int n = static_cast<int>(cmds[p++]);
                if (n < 0 || p + n * 2 > cmdFloatCount) return;
                const Rgba c = argbToRgba(colors[cp++]);
                if (n >= 2) {
                    addPolyline(cmds + p, n, closed, width, dashOn, dashOff, capRound, c);
                }
                p += n * 2;
                break;
            }
            case UI_CMD_CIRCLE: {
                if (p + 4 > cmdFloatCount || cp + 2 > colorCount) return;
                const float cx = cmds[p++];
                const float cy = cmds[p++];
                const float r = cmds[p++];
                const float sw = cmds[p++];
                const Rgba fill = argbToRgba(colors[cp++]);
                const Rgba stroke = argbToRgba(colors[cp++]);
                addCircle(cx, cy, r, sw, fill, stroke);
                break;
            }
            case UI_CMD_ROUND_RECT: {
                if (p + 6 > cmdFloatCount || cp + 2 > colorCount) return;
                const float cx = cmds[p++];
                const float cy = cmds[p++];
                const float rw = cmds[p++];
                const float rh = cmds[p++];
                const float cr = cmds[p++];
                const float sw = cmds[p++];
                const Rgba fill = argbToRgba(colors[cp++]);
                const Rgba stroke = argbToRgba(colors[cp++]);
                addRoundRect(cx, cy, rw, rh, cr, sw, fill, stroke);
                break;
            }
            case UI_CMD_RECT: {
                if (p + 4 > cmdFloatCount || cp + 1 > colorCount) return;
                const float x = cmds[p++];
                const float y = cmds[p++];
                const float rw = cmds[p++];
                const float rh = cmds[p++];
                const Rgba fill = argbToRgba(colors[cp++]);
                addRect(x, y, rw, rh, fill);
                break;
            }
            default:
                return;
        }
    }
}

// ---- Public draw entry points ----

bool UiGlRenderer::drawScopeHistogram(
    int surfaceId,
    const int32_t* histR, const int32_t* histG,
    const int32_t* histB, const int32_t* histY, int bins) {
    int w = 0, h = 0;
    EGLSurface surf = EGL_NO_SURFACE;
    if (!beginSurface(surfaceId, w, h, surf)) return false;
    clearColor(kPlotBg);
    resetBatches();
    buildHistogramGeometry(w, h, histR, histG, histB, histY, bins);
    flushBatches(w, h);
    return egl_.swap(surf, -1);
}

bool UiGlRenderer::drawScopeWaveform(
    int surfaceId,
    const int32_t* wave, int waveW, int waveH, int bins) {
    int w = 0, h = 0;
    EGLSurface surf = EGL_NO_SURFACE;
    if (!beginSurface(surfaceId, w, h, surf)) return false;
    clearColor(kPlotBg);
    resetBatches();
    buildWaveformGeometry(w, h, wave, waveW, waveH, bins);
    flushBatches(w, h);
    return egl_.swap(surf, -1);
}

bool UiGlRenderer::drawScopeVectorscope(int surfaceId, const int32_t* vec, int grid) {
    int w = 0, h = 0;
    EGLSurface surf = EGL_NO_SURFACE;
    if (!beginSurface(surfaceId, w, h, surf)) return false;
    clearColor(kPlotBg);
    resetBatches();
    buildVectorscopeGeometry(w, h, vec, grid);
    flushBatches(w, h);
    return egl_.swap(surf, -1);
}

bool UiGlRenderer::drawEasingGraph(
    int surfaceId,
    float x1, float y1, float x2, float y2,
    float progress, float density,
    float padL, float padT, float padR, float padB) {
    int w = 0, h = 0;
    EGLSurface surf = EGL_NO_SURFACE;
    if (!beginSurface(surfaceId, w, h, surf)) return false;
    clearColor(0x00000000);
    resetBatches();
    buildEasingGeometry(w, h, x1, y1, x2, y2, progress, density, padL, padT, padR, padB);
    flushBatches(w, h);
    return egl_.swap(surf, -1);
}

bool UiGlRenderer::drawVectorCommands(
    int surfaceId,
    int32_t clearArgb,
    const float* cmds, int cmdFloatCount,
    const int32_t* colors, int colorCount) {
    int w = 0, h = 0;
    EGLSurface surf = EGL_NO_SURFACE;
    if (!beginSurface(surfaceId, w, h, surf)) return false;
    clearColor(clearArgb);
    resetBatches();
    buildVectorCmdGeometry(cmds, cmdFloatCount, colors, colorCount);
    flushBatches(w, h);
    return egl_.swap(surf, -1);
}

bool UiGlRenderer::runOffscreenSelfTest(int w, int h, int32_t outCounts[4]) {
    if (!initialized_ || w <= 0 || h <= 0 || !outCounts) return false;
    outCounts[0] = outCounts[1] = outCounts[2] = outCounts[3] = 0;

    if (!egl_.makeCurrent(egl_.bootstrapSurface())) return false;
    currentSurf_ = egl_.bootstrapSurface();

    GLuint fbo = 0, rbo = 0;
    glGenFramebuffers(1, &fbo);
    glGenRenderbuffers(1, &rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, rbo);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, w, h);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rbo);

    bool ok = (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    if (ok) {
        glViewport(0, 0, w, h);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        std::vector<uint8_t> rgba(static_cast<size_t>(w) * static_cast<size_t>(h) * 4u);

        auto countNonBg = [&]() -> int32_t {
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
            int32_t n = 0;
            for (size_t i = 0; i < rgba.size(); i += 4) {
                if (rgba[i] > 28 || rgba[i + 1] > 28 || rgba[i + 2] > 28) ++n;
            }
            return n;
        };
        auto countNonTransparent = [&]() -> int32_t {
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
            int32_t n = 0;
            for (size_t i = 0; i < rgba.size(); i += 4) {
                if (rgba[i + 3] > 16) ++n;
            }
            return n;
        };

        // 1. Histogram check
        int32_t hR[256] = {0}, hG[256] = {0}, hB[256] = {0}, hY[256] = {0};
        for (int i = 40; i < 220; ++i) {
            hR[i] = 100;
            hG[i] = 80;
            hB[i] = 60;
            hY[i] = 90;
        }
        clearColor(kPlotBg);
        resetBatches();
        buildHistogramGeometry(w, h, hR, hG, hB, hY, 256);
        flushBatches(w, h);
        outCounts[0] = countNonBg();

        // 2. Waveform + Vectorscope phosphor check
        std::vector<int32_t> wave(static_cast<size_t>(32) * 256u, 0);
        for (int x = 0; x < 32; ++x) wave[static_cast<size_t>(x) * 256u + 128u] = 20;
        std::vector<int32_t> vec(96u * 96u, 0);
        vec[48u * 96u + 48u] = 50;
        vec[32u * 96u + 64u] = 30;
        clearColor(kPlotBg);
        resetBatches();
        buildWaveformGeometry(w, h, wave.data(), 32, 32, 256);
        buildVectorscopeGeometry(w, h, vec.data(), 96);
        flushBatches(w, h);
        outCounts[1] = countNonBg();

        // 3. Easing graph check
        clearColor(0x00000000);
        resetBatches();
        buildEasingGeometry(w, h, 0.42f, 0.0f, 0.58f, 1.0f, 0.5f, 2.0f, 24.0f, 8.0f, 8.0f, 20.0f);
        flushBatches(w, h);
        outCounts[2] = countNonTransparent();

        // 4. Vector command stream check (dashed quad + round rect + circle)
        const float cmds[] = {
            static_cast<float>(UI_CMD_POLYLINE), 1.0f, 3.0f, 8.0f, 4.0f, 0.0f, 4.0f,
            16.0f, 16.0f, static_cast<float>(w - 16), 16.0f,
            static_cast<float>(w - 16), static_cast<float>(h - 16), 16.0f, static_cast<float>(h - 16),
            static_cast<float>(UI_CMD_ROUND_RECT),
            static_cast<float>(w) * 0.5f, static_cast<float>(h) * 0.5f, 20.0f, 20.0f, 4.0f, 3.0f,
            static_cast<float>(UI_CMD_CIRCLE),
            static_cast<float>(w) * 0.5f, 10.0f, 8.0f, 2.0f
        };
        const int32_t cols[] = {
            static_cast<int32_t>(0xFF3EB1F7),
            static_cast<int32_t>(0xFFFFFFFF), static_cast<int32_t>(0xFF1689F5),
            static_cast<int32_t>(0xFFD2FC32), static_cast<int32_t>(0xFF111114)
        };
        clearColor(0x00000000);
        resetBatches();
        buildVectorCmdGeometry(
            cmds, static_cast<int>(sizeof(cmds) / sizeof(cmds[0])),
            cols, static_cast<int>(sizeof(cols) / sizeof(cols[0])));
        flushBatches(w, h);
        outCounts[3] = countNonTransparent();
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindRenderbuffer(GL_RENDERBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);
    glDeleteRenderbuffers(1, &rbo);
    return ok;
}

}  // namespace am
