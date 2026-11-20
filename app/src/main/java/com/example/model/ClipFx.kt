package com.example.model

import com.example.engine.FxDefsNative
import kotlinx.serialization.Serializable
import java.util.UUID

/** One adjustable value of an effect (a slider in the Effects panel). */
data class FxParamDef(
    val name: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val unit: String = "",
    /** One tick of the ruler slider = one [step] (values are whole steps from [min]; [default] must be one of them). */
    val step: Float = 1f
)

/**
 * An effect the C++ engine can run ([nativeType] = am::FxType in cpp/engine/fx_plan.h). The order and meaning of
 * [params] are the p0, p1, p2 of that effect there.
 */
data class FxDef(
    val id: String,
    val nativeType: Int,
    val name: String,
    val category: String,
    val blurb: String,
    val params: List<FxParamDef>
)

/** An effect applied to a layer. [values] follow the order of the effect's [FxDef.params]. */
@Serializable
data class ClipFx(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val enabled: Boolean = true,
    val values: List<Float>
)

object ClipFxLibrary {
    const val BLUR = "gaussian_blur"
    const val GLOW = "glow"
    const val RGB_SPLIT = "rgb_split"
    const val GRAIN = "film_grain"
    const val VIGNETTE = "vignette"
    const val CHROMA_KEY = "chroma_key"
    // v140
    const val DIR_BLUR = "directional_blur"
    const val RADIAL_BLUR = "radial_blur"
    const val SHARPEN = "sharpen"
    const val EXPOSURE = "exposure"
    const val BRIGHT_CONTRAST = "brightness_contrast"
    const val HUE_SAT = "hue_saturation"
    const val LEVELS = "levels"
    const val COLOR_BALANCE = "color_balance"
    const val TINT = "tint"
    const val POSTERIZE = "posterize"
    const val WAVE = "wave_warp"
    const val TWIRL = "twirl"
    const val BULGE = "bulge"
    const val MIRROR = "mirror"
    const val KALEIDOSCOPE = "kaleidoscope"
    const val TURBULENT = "turbulent_displace"
    const val MOSAIC = "mosaic"
    const val HALFTONE = "halftone"
    const val SCANLINES = "scanlines"
    const val GLITCH = "glitch"
    // v149
    const val INVERT = "invert"
    const val BW = "black_white"
    const val THRESHOLD = "threshold"
    const val TEMPERATURE = "temperature"
    const val SHADOW_HIGHLIGHT = "shadow_highlight"
    const val LEAVE_COLOR = "color_pop"
    const val CINEMATIC = "cinematic"
    const val SOLARIZE = "solarize"
    const val LENS_BLUR = "lens_blur"
    const val TILT_SHIFT = "tilt_shift"
    const val SURFACE_BLUR = "surface_blur"
    const val LIGHT_RAYS = "light_rays"
    const val RIPPLE = "ripple"
    const val LENS_DISTORT = "lens_distortion"
    const val POLAR = "polar"
    const val TILE = "motion_tile"
    const val SHAKE = "camera_shake"
    const val EMBOSS = "emboss"
    const val FIND_EDGES = "find_edges"
    const val CARTOON = "cartoon"
    const val SKETCH = "pencil_sketch"
    const val OIL = "oil_paint"
    const val STROBE = "strobe_light"
    const val VHS = "vhs"
    const val OLD_FILM = "old_film"
    const val LIGHT_LEAK = "light_leak"
    const val LENS_FLARE = "lens_flare"
    // v157
    const val COPY_BG = "copy_background"
    // Motion Blur: resolved per frame by MotionBlurEval into a directional blur (see engine/MotionBlurEval.kt);
    // its native type (55) never reaches the shaders (fxParseOps skips it: it is above FX_MAX).
    const val MOTION_BLUR = "motion_blur"
    // Motion Blur 4 (Alight's motionblur4, build 136): resolved per frame by MotionBlurEval
    // into its def's three stages (the MB4_* passes below), never into the approximation
    // the older ids get. Its native type (179) never reaches the shaders either
    // (fxParseOps skips it; toNative skips it like Motion Blur).
    const val MOTION_BLUR4 = "motion_blur4"
    // The three motionblur4 stages, emitted per frame by MotionBlurEval in the def's
    // group order (position, scale, angle); kFxBlur2 modes 10..12 in the engine.
    const val MB4_POS = "mb4_pos"
    const val MB4_SCALE = "mb4_scale"
    const val MB4_ANGLE = "mb4_angle"
    // Repeat: resolved in the draw loops by RepeatEval (see engine/RepeatEval.kt) — the layer is
    // drawn once per copy with a shifted / turned / scaled pose: overlay layers as copy clips,
    // video / photo and group layers as per-copy placements of their one picture (RepeatDraw).
    // Its native type (56) never reaches the shaders either (fxParseOps skips it: it is above
    // FX_MAX), and toNative skips it like Motion Blur.
    const val REPEAT = "repeat"
    // Repeat Radial / Repeat Scatter: the other two arrangements of Alight's repeat family (copies on a
    // ring / scattered over a disc), resolved by RepeatEval in the same draw loops; native types 57 / 58
    // sit above FX_MAX for the same reason.
    const val REPEAT_RADIAL = "repeat_radial"
    const val REPEAT_SCATTER = "repeat_scatter"
    // v160: two more SHADED effects (they DO reach the engine, unlike 55..58): Long Shadow (the layer's
    // silhouette extruded along an angle, kFxBlur2 mode 7) and Color Tune (Alight's Lift / Gain grading,
    // kFxColor mode 15). Their native types 59 / 60 sit above FX_MAX too, but fxParseOps accepts them
    // explicitly — see fx_plan.h.
    const val LONG_SHADOW = "long_shadow"
    const val COLOR_TUNE = "color_tune"
    // v161: Solid Color (Alight Motion's `solidcolor`), also shaded (kFxColor mode 16, native type 61):
    // the picture's colours are replaced by one colour, its alpha silhouette kept. Values [0..2] are
    // the colour 0..255, [3] the strength %. Value [4] is written by the Alight importer only: 1 when
    // the colour is keyframed there — SolidColorEval then resolves [0..2] per frame from the clip's
    // FILL_COLOR track (which the importer stores for exactly that colour animation).
    const val SOLID_COLOR = "solid_color"
    // v162: Alight Motion's own `mirror` and `glowscan`, from AM's recovered effect definitions
    // (after-motion-dev/hunt-staging/def_mirror.xml / def_glowscan.xml). AM_MIRROR is NOT the app's
    // MIRROR above (a centre/angle reflect-half): AM flips the WHOLE layer's sampling (type 0 =
    // Horizontal, 1 = Vertical), blends the flipped picture over the original per blendMode
    // (0 Normal, 1 Multiply, 2 Screen, 3 Over, 4 Under) and mixes by alpha. It is shaded
    // (kFxWarp mode 11, native type 62). Values [0] type, [1] blendMode, [2] alpha %, [3] offset %.
    // A keyframed alpha (15 of the 51 corpus instances: 0<->1 flip-flash pulses) rides the values
    // tail from index 7 as a keyframe track — AmFxEval resolves [2] per frame.
    const val AM_MIRROR = "am_mirror"
    // GLOW_SCAN is a dedicated two-texture pass in the engine (native type 63, like Glow at 2):
    // a gaussian-blurred copy of the layer (strength -> the blur, the applyGaussianBlur
    // calibration) is scanned by luma; inside the [threshold, threshold+radius] band the blurred
    // picture gains a pow() glow tinted toward fillColor. Values [0] threshold %, [1] radius %,
    // [2] power, [3] smoothing (AM strength) %, [4] fill %, [5] alpha %, [6] the fill colour
    // packed 0xRRGGBB (importer only, the Long Shadow pattern). AM's `blend` has no slot: the
    // importer reports it when it is not 0 (it is 0 in all 8 corpus instances). A keyframed
    // threshold (ALL 8 corpus instances — the sweep IS the effect) rides the tail like Mirror's.
    const val GLOW_SCAN = "glow_scan"
    // v163: Alight Motion's next tier, from the same recovered definitions (hunt-staging/
    // defs-batch2). LUMA_KEY is a dedicated pass (it scales alpha; native type 64). Values
    // [0] low threshold %, [1] high threshold %, [2] feather %, [3] invert, [4] weighted luma.
    // A keyframed low threshold (ALL 19 corpus instances — the sweep is the effect) rides the
    // values tail from index 7, resolved per frame by AmFxEval.
    const val LUMA_KEY = "luma_key"
    // FOUR_COLOR_GRADIENT (native 65 + EXT 66): values [0..3] the four colours packed 0xRRGGBB,
    // [4] blend %, [5] alpha %, [6] blend mode; values [7..10] (the EXT group) are the four
    // anchor points, each packed into one float by [packPoint]; [11..13] unused.
    const val FOUR_COLOR_GRADIENT = "four_color_gradient"
    // TURBULENT_DISPLACE_AM (native 67 + EXT 68): NOT the app's own TURBULENT (value-noise fbm);
    // this is Alight's simplex-noise displacement. Values [0] intensity %, [1] scale %,
    // [2] direction (0 X, 1 Y, 2 XY, 3 R), [3] evolution %, [4] octaves, [5] screen space,
    // [6] offset %; EXT [7] stretch %, [8] seed %, [9..10] position, [11..12] parallax (AM
    // units). Intensity AND scale are keyframed in all 39 corpus instances: two AmFxEval tails
    // ride from index 14.
    const val TURBULENT_DISPLACE_AM = "turbulent_displace_am"
    // SWIRL_AM (native 69, kFxWarp mode 13): Alight's swirl4 / swirl3 as one fx — values
    // [0] strength %, [1] radius %, [2..3] the centre point (AM units), [4] the version
    // (4 or 3). A keyframed strength rides the tail from index 7.
    const val SWIRL_AM = "swirl_am"
    // STRETCH_SEGMENT (native 70, kFxWarp mode 12): values [0] angle deg, [1] stretch
    // (AM units), [2] offset (AM units), [3] smooth %.
    const val STRETCH_SEGMENT = "stretch_segment"
    // HALFTONE_DOTS (native 71, kFxWarp mode 14): NOT the app's own HALFTONE style effect.
    // Values [0] amount (AM units), [1] strength %, [2] angle deg, [3] phase %,
    // [4..5] the two colours packed 0xRRGGBB.
    const val HALFTONE_DOTS = "halftone_dots"
    // PIXELATE_AM (native 72 + EXT 73): NOT the app's MOSAIC. Values [0] size (AM units),
    // [1..2] stretch x/y %, [3] angle deg, [4] vignette %, [5] screen space, [6] threshold %;
    // EXT [7] saturation %, [8..9] offset (AM units). A keyframed size rides the tail from
    // index 14 (AmFxEval scale 1: the slot already holds AM units).
    const val PIXELATE_AM = "pixelate_am"
    // REPLACE_COLOR (native 74, kFxColor mode 17): values [0] the old colour packed 0xRRGGBB,
    // [1] the new colour packed, [2] threshold %, [3] feather %, [4] alpha %,
    // [5] lock luminance.
    const val REPLACE_COLOR = "replace_color"
    // v164: Alight Motion's parked tier, hunt round 2 (defs-batch3 — every definition recovered
    // from the same public APK asset dump). CHROMA_KEY_AM (native 75) is NOT the app's own
    // CHROMA_KEY: AM measures the YUV distance (luma x0.25) to the key colour on the
    // unpremultiplied pixel. Dedicated pass. Values [0] the key colour packed 0xRRGGBB,
    // [1] threshold %, [2] feather %, [3] defringe, [4] invert. Keyframed threshold / feather
    // ride the tail from index 7.
    const val CHROMA_KEY_AM = "chroma_key_am"
    // COLOR_BALANCE_AM (native 76 + EXT 77, kFxColor mode 18): NOT the app's own
    // COLOR_BALANCE (hue discs). Values [0..6] crLow, mgLow, ybLow, crMid, mgMid, ybMid,
    // crHigh (AM units -100..100); EXT [7] mgHigh, [8] ybHigh, [9] fix luminance. Every band
    // value may ride a tail from index 14.
    const val COLOR_BALANCE_AM = "color_balance_am"
    // COLOR_TEMP_AM (native 78, kFxColor mode 19): values [0] temperature (AM units:
    // Kelvin / 1000), [1] strength %. Both may ride tails from index 7.
    const val COLOR_TEMP_AM = "color_temp_am"
    // COLOR_TINT_AM (native 79, kFxColor mode 20): values [0] tint hue deg (AM raw),
    // [1] strength %. Both may ride tails from index 7.
    const val COLOR_TINT_AM = "color_tint_am"
    // SPIN_BLUR_AM (native 80, kFxBlur2 mode 8): NOT the app's RADIAL_BLUR (fixed 48 taps) —
    // AM's tap count follows the arc length at each radius. Values [0] angle deg (AM raw),
    // [1..2] the centre point (AM units). A keyframed angle rides the tail from index 7.
    const val SPIN_BLUR_AM = "spin_blur_am"
    // OFFSET_AM (native 81, kFxWarp mode 15): values [0..1] offset x/y (AM units),
    // [2] feather %, [3] mask. A keyframed offset rides two tails (x, then y) from index 7.
    const val OFFSET_AM = "offset_am"
    // RADIAL_WIPE_AM (native 82, kFxWarp mode 16): values [0] progress deg, [1] feather deg,
    // [2] direction, [3] repeat count, [4] the centre point packed, [5] offset deg.
    // A keyframed progress rides the tail from index 7.
    const val RADIAL_WIPE_AM = "radial_wipe_am"
    // FIND_EDGES_AM (native 83, kFxStyle mode 14): NOT the app's own FIND_EDGES style.
    // Values [0] smoothing (AM raw), [1] threshold %, [2] invert.
    const val FIND_EDGES_AM = "find_edges_am"
    // HEX_SHIFT_AM (native 84 + EXT 85, kFxWarp mode 17): values [0] strength (AM raw),
    // [1] size (AM units), [2..3] stretch x/y %, [4] angle deg (AM raw), [5] seed,
    // [6] screen space; EXT [7] mask to layer, [8] the position point packed. Keyframed
    // strength / angle ride tails from index 14.
    const val HEX_SHIFT_AM = "hex_shift_am"
    // SOFT_GLOW_AM (native 86 + EXT 87): dedicated two-texture pass like GLOW_SCAN. Values
    // [0] strength %, [1] brightness %, [2] contrast %, [3] highlights %, [4] the colour
    // packed 0xRRGGBB, [5] blend %, [6] alpha %; EXT [7] outside alpha % (the backdrop
    // branch is not reproduced: the importer warns when it is not 0). A keyframed strength
    // rides the tail from index 14.
    const val SOFT_GLOW_AM = "soft_glow_am"
    // FILL_BEHIND_AM (native 88 + EXT 89): dedicated two-texture pass. Values [0] strength %,
    // [1] feather %, [2] gamma (x100), [3] tint strength %, [4] the tint colour packed
    // 0xRRGGBB, [5] edge strength %, [6] scan lines %; EXT [7] scan line count, [8] bg
    // scale %, [9] the edge colour packed, [10..11] bg offset x/y (AM units), [12] alpha %,
    // [13] saturation %. A keyframed bg offset rides two tails from index 14 (values 10/11);
    // AM's brightness / fgAlpha / fgScale / fgOffset have no slot (importer warns when one
    // is not at its default).
    const val FILL_BEHIND_AM = "fill_behind_am"
    // WAVE_WARP_AM (native 90 + EXT 91, kFxWarp mode 18): Alight's wavewarp2. Values
    // [0] phase (turns), [1] angle 1 deg, [2] spacing m1, [3] magnitude m2, [4] angle 2
    // deg (relative), [5] damping, [6] damping spacing; EXT [7] damping origin,
    // [8] screen space (the pattern is layer-anchored here, like every warp; the flag is
    // carried but does not change the maths). Keyframed phase / magnitude / angles ride
    // tails from index 14.
    const val WAVE_WARP_AM = "wave_warp_am"
    // CIRCULAR_RIPPLE_AM (native 92, kFxWarp mode 19): Alight's circularripple2 (=
    // circularripple, byte-identical shader). Values [0] strength, [1] radius,
    // [2] frequency, [3] feather, [4] phase (turns), [5..6] centre x/y (AM units).
    // Keyframed frequency / strength / centre ride tails from index 7.
    const val CIRCULAR_RIPPLE_AM = "circular_ripple_am"
    // STRETCH_AM (native 93, kFxWarp mode 20): Alight's stretch2 / stretch — the two
    // versions share the map, [3] selects the contentOnly compose (1 = v1 composites the
    // layer under the masked sample, 2 = v2 keeps it). Values [0] scale, [1] angle deg,
    // [2] content only. Keyframed scale / angle ride tails from index 7.
    const val STRETCH_AM = "stretch_am"
    // SQUEEZE_AM (native 94, kFxWarp mode 21): Alight's squeeze. Its def also declares a
    // centre point and a radius, but the shader never reads them — [0] strength is all.
    // A keyframed strength rides the tail from index 7.
    const val SQUEEZE_AM = "squeeze_am"
    // FRACTAL_WARP_AM (native 95 + EXT 96, kFxWarp mode 22): Alight's fractalwarp4.
    // Values [0] magnitude, [1] scale, [2] intensity, [3] octaves, [4..5] the offset
    // point x/y, [6] parallax x; EXT [7] parallax y (components, so keyframed points
    // animate). Keyframed magnitude / offset / parallax / scale ride tails from
    // index 14.
    const val FRACTAL_WARP_AM = "fractal_warp_am"
    // STRIPES_AM (native 97, kFxWarp mode 23): Alight's stripes. Values [0] amount,
    // [1] width, [2] angle deg, [3] phase, [4] the colour packed 0xRRGGBB, [5] smoothing,
    // [6] punch out. (The def's screenSpace branch is not reproduced; the importer warns
    // when an instance sets it.) Keyframed phase / width ride tails from index 7.
    const val STRIPES_AM = "stripes_am"
    // TURBULENT_DISPLACE3_AM (native 98 + EXT 99, kFxWarp mode 24): Alight's
    // turbulentdisplace3. Values [0] intensity, [1] scale, [2] evolution, [3] seed,
    // [4] direction (0 X, 1 Y, 2 XY, 3 R, 4 φr, 5 φ), [5] octaves, [6] offset; EXT
    // [7..8] the parallax point x/y, [9..10] the position point x/y, [11..12] stretch
    // x/y, [13] flags (screen space + 2 * single axis; screen space only swaps the
    // stretch here — the pattern stays layer-anchored). Keyframed intensity / scale
    // ride tails from index 14.
    const val TURBULENT_DISPLACE3_AM = "turbulent_displace3_am"
    // ROUGHEN_EDGES_AM (native 100, kFxWarp mode 25): Alight's roughenedges — its shader
    // is turbulentdisplace3's body with the geometry fixed (direction R, detail x5,
    // offset 0.15); the def's other declared params are never read by the shader. Values
    // [0] strength, [1] detail, [2] evolution, [3] seed, [4] octaves. A keyframed
    // evolution rides the tail from index 7.
    const val ROUGHEN_EDGES_AM = "roughen_edges_am"
    // STREAKS_AM (native 101 + EXT 102, kFxBlur2 mode 9): Alight's streaks (linear).
    // Values [0] strength, [1] angle deg, [2] bias, [3] alpha, [4..6] the R/G/B combine
    // modes; EXT [7] the A combine mode (modes: 0 min, 1 max, 2 off, 3 average). A
    // keyframed strength rides the tail from index 14.
    const val STREAKS_AM = "streaks_am"
    // COLOR_HOT_AM (native 103, kFxColor mode 21): Alight's colorhot. Values are the two
    // hue-discs flattened: [0..2] colour hue deg / saturation / bias, [3..5] tint hue /
    // saturation / bias.
    const val COLOR_HOT_AM = "color_hot_am"
    // COLORIZE_AM (native 104, kFxColor mode 22): Alight's colorize — the Color Tint
    // formula with the hue-disc's saturation as the amount. Values [0] tint hue deg,
    // [1] amount.
    const val COLORIZE_AM = "colorize_am"
    // HIGHLIGHT_SHADOW_AM (native 105, kFxColor mode 23): Alight's highlightshadow
    // (GPUImage's formula). Values [0] shadows, [1] highlights (AM raw 0..2; both 1 =
    // identity).
    const val HIGHLIGHT_SHADOW_AM = "highlight_shadow_am"
    // SPOT_COLOR_AM (native 106, kFxColor mode 24): Alight's spotcolor2 (group 0).
    // Values [0] the colour packed 0xRRGGBB, [1] threshold, [2] feather, [3] saturation,
    // [4] choke.
    const val SPOT_COLOR_AM = "spot_color_am"
    // DISSOLVE_AM (native 107, kFxStyle mode 15): Alight's dissolve. Values [0] amount,
    // [1] seed. A keyframed amount rides the tail from index 7.
    const val DISSOLVE_AM = "dissolve_am"
    // SIMPLE_STARS_AM (native 108 + EXT 109, kFxStyle mode 16): Alight's simplestars2.
    // Values [0] scale, [1] star size, [2] star feather, [3] the colour packed 0xRRGGBB,
    // [4] star count, [5] brightness, [6] blend mode (0 normal, 1 multiply, 2 screen,
    // 3 punch out); EXT [7..9] the parallax x/y/z, [10] alpha. Keyframed parallax
    // (three tails) / star size ride tails from index 14.
    const val SIMPLE_STARS_AM = "simple_stars_am"
    // DOTS_AM (native 110 + EXT 111, kFxStyle mode 17): Alight's dots2. Values [0] scale,
    // [1] phase (turns), [2] radius, [3] feather, [4..5] the two colours packed 0xRRGGBB,
    // [6] density; EXT [7..8] the position point x/y, [9] fill, [10] alpha, [11] blend
    // mode (0 normal, 1 multiply, 2 screen), [12] seed, [13] slack. Keyframed phase /
    // density ride tails from index 14.
    const val DOTS_AM = "dots_am"
    // RIDGES_AM (native 112 + EXT 113, kFxStyle mode 18): Alight's ridges2 / ridges.
    // Values [0] intensity, [1] scale, [2] evolution, [3] seed, [4] octaves, [5..6] the
    // two colours packed 0xRRGGBB (their alpha is dropped, like every packed colour);
    // EXT [7..8] the offset point x/y, [9..10] the parallax point x/y, [11..12] stretch
    // x/y (v1: [11] is the scalar stretch), [13] version (1 or 2). Keyframed offset /
    // parallax / evolution ride tails from index 14.
    const val RIDGES_AM = "ridges_am"
    // BEND_WARP_AM (native 114, kFxWarp mode 26): Alight's bendwarp. Values [0] amount
    // deg, [1] offset, [2] origin (0 left, 1 right, 2 top, 3 bottom), [3] opposite type,
    // [4] high limit, [5] low limit. Keyframed amount / offset ride tails from index 7.
    const val BEND_WARP_AM = "bend_warp_am"
    // DISPLACE_MAP_AM (native 115): dedicated backdrop pass (renderer_effects.cpp).
    // Alight's displacemap3 (= displacemap2): the def's `comp` input is srcType "comp" —
    // the composite of everything BELOW the layer, which is exactly the backdrop the
    // renderer captures for Copy Background. Values [0..1] the offset point x/y,
    // [2] x source, [3] y source (0 luma, 1 R, 2 G, 3 B), [4] edges (0 repeat, 1 mirror,
    // 2 transparent, 3 tile), [5] from centre, [6] invert. A keyframed offset rides two
    // tails from index 7.
    const val DISPLACE_MAP_AM = "displace_map_am"
    // DISPLACE_MAP_RAD_AM (native 116 + EXT 117): dedicated backdrop pass. Alight's
    // displacemaprad. Values [0] offset angle deg, [1] offset radius, [2] x source,
    // [3] y source, [4] edges, [5] from centre, [6] invert; EXT [7..8] the centre point
    // x/y. A keyframed offset angle rides the tail from index 14.
    const val DISPLACE_MAP_RAD_AM = "displace_map_rad_am"
    // SMOOTH_BEVEL_AM (native 118 + EXT 119): dedicated two-pass effect
    // (renderer_effects.cpp). Alight's smoothbevel. Values [0] radius, [1] diffuse angle
    // deg, [2] specular angle deg, [3] diffuse intensity, [4] specular intensity,
    // [5] diffuse elevation, [6] specular elevation; EXT [7] specular coefficient,
    // [8] the diffuse colour packed 0xRRGGBB, [9] the specular colour packed. A keyframed
    // specular angle rides the tail from index 14.
    const val SMOOTH_BEVEL_AM = "smooth_bevel_am"
    // LIGHT_GLOW_AM (native 120): dedicated four-pass effect (renderer_effects.cpp).
    // Alight's lightglow — all four passes share one shader in the def (no host
    // downsample shader after all; its buffers are plain downsample="2" targets).
    // Values [0] strength, [1] threshold, [2] intensity, [3] the colour packed 0xRRGGBB,
    // [4] blend, [5] halo, [6] alpha.
    const val LIGHT_GLOW_AM = "light_glow_am"
    // INNER_GLOW_AM (native 121): dedicated two-pass effect (renderer_effects.cpp).
    // Alight's inner-glow — missed by the id-list censuses until the v165 full sweep
    // (one visible corpus instance). Values [0] radius, [1] alpha, [2] hardness,
    // [3] the colour packed 0xRRGGBB (its alpha is declared but never read by the
    // shader), [4] blend mode (0 normal, 1 multiply, 2 screen).
    const val INNER_GLOW_AM = "inner_glow_am"
    // DARK_GLOW_AM (native 122): dedicated pass chain (renderer_effects.cpp). Alight's
    // darkglow, ported from its def (build 120): the layer's dark parts (luma below the
    // threshold), tinted toward the colour by blend, are blurred and multiplied back over
    // the layer at alpha — a glow of the shadows, not of the highlights. Values
    // [0] strength % (AM raw x100), [1] threshold %, [2] the colour packed 0xRRGGBB,
    // [3] blend %, [4] alpha %. Keyframed strength / threshold ride tails from index 7.
    const val DARK_GLOW_AM = "dark_glow_am"
    // EXTRUDE_AM (native 123 + EXT 124 + EXT2 125): dedicated raymarch pass
    // (renderer_effects.cpp). Alight's s3d-extrude, ported from its def: the layer's
    // silhouette extruded into a 3D box, sphere-traced against a gaussian-blurred copy
    // of itself. Values [0] depth, [1] scale, [2] width ratio, [3] height ratio,
    // [4] interior blend, [5] fade blend, [6] shading type (0 flat, 1 point light,
    // 2 directional, 3 depth map, 4 normal map, 5 normal & depth) — all AM raw units.
    // EXT [7..9] rotate xyz deg, [10..12] position xyz, [13] the interior colour packed
    // 0xRRGGBB. EXT2 [14] the fade colour packed, [15] the light colour packed,
    // [16] the light direction point packed, [17] light intensity + strength packed
    // (x100 each: intensity + strength*256), [18] shine + ambient packed (x100 each:
    // shine*128 + ambient), [19] spec, [20] light depth + MAX_ITER packed
    // ((depth+100)*2 + iter*2048). Keyframed rotate / position components ride six
    // AmFxEval tails from index 21.
    const val EXTRUDE_AM = "extrude_am"
    // EXTRUDE_FLAT_AM (native 126 + EXT 127 + EXT2 128): Alight's extrudeflat — the
    // port shares Extrude's march with flat shading and no light model (its schema
    // carries no light params); its def was recovered in hunt round 3 (defs-batch8)
    // and build 136 verified the port against it. Values [0] depth, [1] scale,
    // [2] stretch x, [3] stretch y, [4] interior blend, [5] fade blend, [6] inton
    // (1 in all 16 corpus instances, with interior blend 1; the def gates the
    // interior / fade colour mixes on it).
    // EXT like Extrude's; EXT2 [14] the fade colour packed, [15] MAX_ITER.
    // Keyframed rotate / position ride tails from index 21, like Extrude's.
    const val EXTRUDE_FLAT_AM = "extrude_flat_am"
    // EDGE_GLOW_AM (native 129 + EXT 130): Alight's edgeglow, ported from its def
    // (recovered identically from two dumps). A dedicated three-pass chain
    // (renderer_effects.cpp): the def's findedges-edgeglow pass, the host gaussianblur
    // of the edge buffer, then the def's composite. Values are AM raw units:
    // [0] smoothing 1..10, [1] threshold 0..1, [2] invert 0/1, [3] strength (the blur)
    // 0..2, [4] spread 0..3, [5] fill color packed 0xRRGGBB, [6] blend 0..2.
    // EXT [7] gamma, [8] brightness, [9] tint color packed 0xRRGGBB, [10] tint.
    const val EDGE_GLOW_AM = "edge_glow_am"
    // ---- v175: Alight's effects tier 4 (defs-batch7; every def recovered from two
    // dumps, shader-identical between them). Values are AM raw units throughout.
    // THRESHOLD_AM (native 131): kFxColor mode 25. [0] threshold 0..1, [1] feather
    // 0..1, [2] invert 0/1, [3] blend 0 mask / 1 multiply / 2 screen. (The app's own
    // Threshold is a different effect.)
    const val THRESHOLD_AM = "threshold_am"
    // SPECTRALMAP_AM (native 132 + EXT 133): kFxColor mode 26. [0] phase -99..99,
    // [1] repeat 0.1..10, [2] alpha 0..1, [3] squeeze 0..1, [4] saturation -1..1,
    // [5] reverse 0/1, [6] scale brightness 0/1. EXT [7..9] the red / green / blue
    // colours packed 0xRRGGBB (their alpha never reaches the maths).
    const val SPECTRALMAP_AM = "spectralmap_am"
    // CHANNELMAP_AM (native 134): dedicated pass (it rewrites the alpha).
    // [0..3] the R / G / B / A sources (0 R, 1 G, 2 B, 3 A, 4 luma, 5 zero, 6 one).
    const val CHANNELMAP_AM = "channelmap_am"
    // HALFTONE_LINES_AM (native 135): kFxWarp mode 27. [0] amount 0..500,
    // [1] strength 0..1, [2] angle deg, [3] phase (AM raw -1000..1000),
    // [4] / [5] the two colours packed 0xRRGGBB. Keyframed angle / phase ride
    // AmFxEval tails from index 7.
    const val HALFTONE_LINES_AM = "halftone_lines_am"
    // TURBULENCE2_AM (native 136 + EXT 137 + EXT2 138): kFxStyle mode 19.
    // [0] intensity, [1] scale, [2] evolution, [3] seed, [4] / [5] colours 1/2
    // packed, [6] blend 0..2. EXT [7] / [8] offs x/y, [9] / [10] parallax x/y,
    // [11] / [12] stretch x/y, [13] bias. EXT2 [14] alpha, [15] octaves. Keyframed
    // offs / parallax components ride four AmFxEval tails from index 21.
    const val TURBULENCE2_AM = "turbulence2_am"
    // CHECKER2_AM (native 139 + EXT 140): kFxStyle mode 20. [0] amount 0.5..200,
    // [1] angle deg, [2] colour packed, [3] punch out 0/1, [4] screen space 0/1
    // (carried; the pattern anchors to the layer's rect), [5] / [6] position x/y.
    // EXT [7] / [8] stretch x/y.
    const val CHECKER2_AM = "checker2_am"
    // MOSAIC_AM (native 141): dedicated two-pass. [0] size 1..500 (layer px),
    // [1] / [2] phase x/y. Keyframed size rides an AmFxEval tail from index 7.
    // (The app's own Mosaic style is a different effect.)
    const val MOSAIC_AM = "mosaic_am"
    // GLOW_AM (native 142): dedicated outer-glow scan. [0] radius 0..400,
    // [1] alpha, [2] hardness, [3] colour packed, [4] colour alpha (it scales the
    // result's alpha in blend mode 0), [5] blend 0..2. (The app's own Glow bloom
    // is a different effect.)
    const val GLOW_AM = "glow_am"
    // INNER_BLUR_AM (native 143): dedicated box-chain pass. [0] strength 0..1,
    // [1] iterations 1..6. Keyframed strength rides an AmFxEval tail from index 7.
    const val INNER_BLUR_AM = "inner_blur_am"
    // BOX_BLUR3_AM (native 144): dedicated half-res box pass. [0] strength 0..2.
    const val BOX_BLUR3_AM = "box_blur3_am"
    // FEATHER_AM (native 145): dedicated quarter-res box chain. [0] strength 0..2,
    // [1] iterations 1..5. Keyframed strength rides an AmFxEval tail from index 7.
    const val FEATHER_AM = "feather_am"
    // ELECTRIC_EDGES_AM (native 146 + EXT 147 + EXT2 148): dedicated pass (blur
    // buffer + the def's main pass). [0] distance, [1] thickness, [2] complexity,
    // [3] disort, [4] / [5] colours 1/2 packed, [6] alpha. EXT [7] glow alpha,
    // [8] seed, [9] evolution, [10] fill, [11] mask strength, [12] mask evolution,
    // [13] mask scale. EXT2 [14] mask seed, [15] mask colour packed, [16] render
    // contours, [17] contour count, [18] contour offset, [19] contour angle,
    // [20] strength (the fixed 0.42 feeding the def's gaussianblur buffer).
    // Keyframed evolution rides an AmFxEval tail at index 21 + 2.
    const val ELECTRIC_EDGES_AM = "electric_edges_am"
    // SMOOTH_EDGES_AM (native 149): dedicated pass (blur copy + streak composites).
    // [0] strength 0..2, [1] mask to layer 0/1.
    const val SMOOTH_EDGES_AM = "smooth_edges_am"
    // ---- v176: Queue 15 top batch (hunt round 3, defs-batch8 — the Ultra dump and
    // the TrueMotion conversions agree; both passed the schema acceptance test
    // against the corpus's serialized instances). Values are AM raw units.
    // GAUSSIAN_BLUR_AM (native 150): Alight's standalone gaussianblur as its own
    // dedicated op. [0] strength 0..2 (AM raw). Until v175 the importer mapped it
    // onto the app's BLUR at strength x 50 — the def's own kernel (fxAmGaussianSigma:
    // sigma = strength x frame diagonal x 0.04427) is resolution-dependent, which
    // the app blur's value cannot express, so the stored value is AM's and the
    // renderer computes the sigma. Keyframed strength rides a tail from index 7.
    // (The app's own Blur panel effect is untouched.)
    const val GAUSSIAN_BLUR_AM = "gaussian_blur_am"
    // DEEP_GLOW2_AM (native 151 + EXT 152 + EXT2 153): Alight's deepglow2 AND
    // deepglow_enc (one def, two ids). A dedicated chain (renderer_effects.cpp):
    // the host gaussianblur of the layer, then the def's composite, the whole pass
    // list repeated `iter` times. [0] strength 0..2 (the blur), [1] oalpha 0..3,
    // [2] lowThreshold, [3] highThreshold, [4] feather 0..10, [5] alpb,
    // [6] bmode (0 screen, 1 color dodge, 2 linear add, 3 layer). EXT [7] calpha,
    // [8] hue (raw degrees, def default 360), [9..10] the satvib point,
    // [11] colblend packed 0xRRGGBB (its alpha is never read), [12] strengthhh,
    // [13] angleee (raw degrees). EXT2 [14] position packed, [15] stretch packed,
    // [16] OFT packed, [17] flags (overd 1 + gamma-compensated 2 + chromatic 4 +
    // colour-blend 8 + colour-blend-replace 16 + centre channel 32/64),
    // [18] iterations. Keyframed strength / oalpha ride tails from index 21.
    const val DEEP_GLOW2_AM = "deep_glow2_am"
    // DROP_SHADOW_AM (native 154): Alight's dropshadow. A dedicated chain: the
    // host gaussianblur of the layer, tinted by its alpha, offset, the layer over
    // it. [0] the shadow colour packed 0xRRGGBB, [1] its alpha 0..1 (it scales the
    // shadow, so it cannot ride the packed colour), [2] position packed,
    // [3] strength 0..5 (the blur), [4] alpha 0..5, [5] alphac.
    const val DROP_SHADOW_AM = "drop_shadow_am"
    // BBMAKER_AM (native 155 + EXT 156): Alight's bbmaker — two wipe bands over
    // the layer in layer norm: the colour band (colorbg x tex.a, gated by the
    // startA / endA / angleA / featherA band) under the picture band
    // (adjStart = (start - 20) / 100, adjEnd = (120 - start) / 100 along angle).
    // [0] start, [1] angle (raw degrees), [2] colorbg packed 0xRRGGBB, [3] its
    // alpha, [4] startA, [5] endA, [6] angleA. EXT [7] featherA, [8] opacity.
    // Keyframed start / angle ride tails from index 14.
    const val BBMAKER_AM = "bbmaker_am"
    // UNSHARPMASK_AM (native 157): Alight's unsharpmask — the host gaussianblur
    // of the layer at strength, then adjColor = base (1 + amount) - blur amount
    // mixed in by the luma-difference gate. [0] strength 0..2, [1] amount,
    // [2] threshold, [3] showMask 0/1.
    const val UNSHARPMASK_AM = "unsharpmask_am"
    // NEWSHARPEN_AM (native 158): Alight's newsharpen ("oldsharpen") — at half
    // res, a horizontal then vertical streak average of the picture, then
    // screen(input, (input - streak) x brig); the pass list repeats iter times.
    // [0] strength, [1] whiteonly 0/1, [2] bluredonly 0/1, [3] brig, [4] iter.
    const val NEWSHARPEN_AM = "newsharpen_am"
    // GRADSHADOW_AM (native 159 + EXT 160): the blend-generator family's
    // gradient shadow — a layer-norm band x tex.a x alphb x color through the
    // FULL 25-mode blend switch. [0] start, [1] end, [2] feather (raw, / 100 in
    // the shader), [3] angle, [4] color packed, [5] its alpha, [6] bright.
    // EXT [7] blendMode, [8] alphb, [9] alphab.
    const val GRADSHADOW_AM = "gradshadow_am"
    // GRADIENTFLOW_AM (native 161 + EXT 162 + EXT2 163): the Perlin-folded
    // 4-colour flow field through the SHORT blend switch. [0] scale, [1] angle,
    // [2] position packed, [3] progress (iTime), [4] speed (consumed by the
    // importer's synthetic clock tail), [5] hardness, [6] blendMode.
    // EXT [7..10] color1..4 packed, [11] FREQ, [12] AMP, [13] stretch packed.
    // EXT2 [14] alpha. Keyframed / autop progress rides a tail from index 21.
    const val GRADIENTFLOW_AM = "gradientflow_am"
    // PARTX_AM (native 164 + EXT 165 + EXT2 166): the closed-form starfield,
    // mix(colorbg, colorfg, white) through the SHORT switch; the output tail
    // has NO trailing x tex.a (the def's own inconsistency, kept).
    // [0] colorfg packed, [1] its alpha, [2] colorbg packed, [3] its alpha,
    // [4] kNumAngles, [5] kStarDensity, [6] kMotionBlur. EXT [7] EVO (local
    // time), [8] scale, [9] angle, [10] position packed, [11] stretch packed,
    // [12] bright, [13] blendMode. EXT2 [14] alpha. Keyframed / autop EVO
    // rides a tail from index 21 on slot 7.
    const val PARTX_AM = "partx_am"
    // EDGELINE_AM (native 167 + EXT 168): the Scharr-like 8-tap gradient mapped
    // through color1 / color2 by its luma, through the MINI 3-mode switch.
    // [0] smoothing, [1] threshold, [2] amp, [3] color1 packed, [4] its alpha,
    // [5] color2 packed, [6] its alpha. EXT [7] alpha, [8] blendMode,
    // [9] fix 0/1, [10] newMode 0/1, [11] invert 0/1.
    const val EDGELINE_AM = "edgeline_am"
    // LIGHTSWEEP_AM (native 169 + EXT 170 + EXT2 171): the travelling sine band
    // mapped through color1 / color2 by its luma, through the SHORT switch.
    // [0] scale, [1] angle, [2] position packed, [3] progress (iTime),
    // [4] speed, [5] amount, [6] sharp. EXT [7] stretch packed, [8] color1
    // packed, [9] its alpha, [10] color2 packed, [11] its alpha, [12] hardness,
    // [13] blendMode. EXT2 [14] alpha. Keyframed progress rides a tail from 21.
    const val LIGHTSWEEP_AM = "lightsweep_am"
    // COLORGRAD3_AM (native 172 + EXT 173 + EXT2 174): Alight's 3colorgrad
    // (id com.alightcreative.3colorgrad) — the 3-colour ramp along the centred
    // x through the FULL switch. [0] color1 packed, [1] its alpha, [2] color2
    // packed, [3] its alpha, [4] color3 packed, [5] its alpha, [6] progressa.
    // EXT [7] progressb, [8] offsetx, [9] scale, [10] angle, [11] bright,
    // [12] blendMode, [13] alpha. EXT2 [14] alphab. Keyframed angle rides a
    // tail from index 21 on slot 10.
    const val COLORGRAD3_AM = "colorgrad3_am"
    // DEEP_GLOW_AM (native 175 + EXT 176 + EXT2 177): Alight's deepglow (v1;
    // the double-sourced def). The host gaussianblur of the layer (vblur,
    // sampled at the position offset) hue-rotated in YUV, scaled by the glow
    // radius, optionally threshold-gated, over the base; a boxblur2 (hblur)
    // feeds only the sshadow silhouette. [0] strength 0..2, [1] rrr
    // (Intensity), [2] hue (raw degrees), [3] oalpha, [4] calpha, [5] alpb,
    // [6] color packed. EXT [7] higsha x, [8] higsha y, [9] satvib x,
    // [10] satvib y, [11] position packed, [12] color alpha, [13] alphaa.
    // EXT2 [14] lowThreshold, [15] highThreshold, [16] feather, [17] flags
    // (sshadow 1 + cblend 2 + thrs 4), [18] iter, [19] power (declared by the
    // def, never read by its shader — stored, inert).
    const val DEEP_GLOW_AM = "deep_glow_am"
    // SPILL_CLEANUP_AM (native 178): Alight's spill-cleanup — the host
    // gaussianblur of the layer, two ordered over-compositing streak passes
    // over it, then the spill composite (the streak colour's luma replaced by
    // the base's, laid under the base). [0] strength (AM raw).
    const val SPILL_CLEANUP_AM = "spill_cleanup_am"
    // OPTIC_COMP_AM (native 183 + EXT 184): Alight's opticcompesation — the
    // chromatic barrel / pincushion average of the hunt-round-4 def (the
    // original-format UltraMotionEffects def, corroborated by TrueMotion's
    // afterz-561 SkSL conversion): per tap i in [-iterations, iterations),
    // R / G / B sample at 0.5 + uv / sqrt(1 + amount * |uv|^2) with the amounts
    // shifted by +-separation. [0] iterations, [1] distortion, [2] strength,
    // [3] separation, [4] blendMode (0 NORMAL / 1 MULTIPLY / 2 SCREEN /
    // 3 LAYER), [5] scale, [6] alpha. EXT [7] position x, [8] position y
    // (AM units, / 500 in the shader), [9] stretch x, [10] stretch y,
    // [11] hardness. Keyframed distortion / strength ride AmFxEval tails
    // from index 14.
    const val OPTIC_COMP_AM = "optic_comp_am"
    // NEGATIVE_AM (native 185): Alight's negative (the Ultra original) —
    // invert, luma, fixed-offset unsharp, brightness / contrast, black / white
    // point remap, hash grain. [0] contrast, [1] brightness, [2] blackPoint,
    // [3] whitePoint, [4] sharpness, [5] grainAmount, [6] frameBlend (the
    // def's, never read by its shader — stored, inert).
    const val NEGATIVE_AM = "negative_am"
    // TRITONE_AM (native 186): Alight's tritone (the Ultra original — the
    // TrueMotion vs.tritone is a DIFFERENT effect and is not this port) — a
    // shadow / midtone / highlight colour ramp over luma, mixed back by
    // blend. [0] highlights packed, [1] its alpha, [2] midtones packed,
    // [3] its alpha, [4] shadows packed, [5] its alpha, [6] blend (the
    // colour alphas are carried, never read by the def).
    const val TRITONE_AM = "tritone_am"
    // UNMULT_AM (native 187 + EXT 188): Alight's unmult (the Ultra original) —
    // a matte from max(rgb) with threshold / softness / gamma shaping, a
    // max-value feather, a choke / spread cleanup, and an optional
    // luma-preserving unmultiply; the def writes straight rgb with the matte
    // alpha. [0] blackLevel (0..255), [1] softness, [2] featherPx,
    // [3] cleanupPx, [4] edgeGamma, [5] preserveLuma, [6] previewMatte.
    // EXT [7] quality (pass count; inert in this port).
    const val UNMULT_AM = "unmult_am"
    // DENOISE_AM (native 189): Alight's denoise (the Ultra original) — the
    // Sir Bird spiral sampler at a hard-coded 512 x 512 virtual canvas,
    // centre-direction / saturation weighted. [0] SAMPLES, [1]
    // PIXEL_MULTIPLIER, [2] alpha, [3] strength (declared by the def, never
    // read by its shader — stored, inert).
    const val DENOISE_AM = "denoise_am"
    // MINIMAX_AM (native 190): Alight's minimaxblur (the Ultra original) —
    // one separable min / max pass per axis, per channel or picked by one
    // channel. [0] operation (0 min / 1 max), [1] radius, [2] channel
    // (4 = per-channel), [3] direction (0 both / 1 vertical / 2 horizontal),
    // [4] keepEdge.
    const val MINIMAX_AM = "minimax_am"
    // RGBGLITCH_AM (native 191): Alight's rgbglitch (the Ultra original) —
    // horizontal slice displacement driven by a floor(progressa * 36) clock
    // and a hash; every slice may replace one channel from an offset sample.
    // [0] progressa, [1] progressb, [2] progressc, [3] progressd.
    const val RGBGLITCH_AM = "rgbglitch_am"
    // FISHEYE_AM (native 192): Alight's fisheye_antifisheye (the Ultra
    // original) — the tan / atan radial remap with its aspect modes.
    // [0] power, [1] centerX, [2] centerY, [3] aspectMode, [4] alpha.
    // Keyframed power / centerX ride AmFxEval tails from index 7.
    const val FISHEYE_AM = "fisheye_am"
    // VORTEX_BLUR_AM (native 193): Alight's chromaticvortexblur (the AM stock
    // def) — per channel, a swirl blur whose angle grows toward the centre,
    // sampled along the radius; centre-point components are the def defaults
    // (0, 0) = neutral. [0] strength, [1] radius, [2] colorAberration,
    // [3] centerChannel, [4] mode, [5] centerPoint packed. Keyframed strength
    // rides AmFxEval tails from index 7.
    const val VORTEX_BLUR_AM = "vortex_blur_am"
    // REFRACTION_AM (native 194 + EXT 195 + EXT2 196): Alight's
    // refractionwarp (the Ultra original, pass 1 only — pass 0 is a dead
    // copy) — a recursive cosine heightfield whose gradient refracts the
    // layer, with the reflection highlight. [0] autop, [1] PROGRESS,
    // [2] SPEEED, [3] scale, [4] position packed, [5] intensity, [6]
    // frequency. EXT [7] emboss, [8] steps, [9] angle, [10] delta, [11] gain,
    // [12] reflectionCutOff, [13] reflectionIntensity. EXT2 [14] speed,
    // [15] speed x, [16] speed y, [17] alpha. Keyframed intensity rides
    // AmFxEval tails from index 21.
    const val REFRACTION_AM = "refraction_am"
    // TWITCH_AM (native 197): Alight's smartMotionBlurShake (the Ultra
    // "Twitch") — a motion-blur average along a per-interval hash jitter
    // vector. [0] amplitude, [1] shakeInterval, [2] samples, [3] angle.
    // Keyframed amplitude rides AmFxEval tails from index 7.
    const val TWITCH_AM = "twitch_am"
    // TURBULENT2_AM (native 198 + EXT 199): Alight's turbulentdisplace2 (the
    // TrueMotion afterz-112 SkSL def) — the octave turbulence pair displaced
    // by direction mode (X / Y / XY / radial / rotate / spiral).
    // [0] intensity, [1] evolution, [2] direction, [3] scale, [4] stretch,
    // [5] seed, [6] singleAxis. EXT [7] screenSpace, [8] octaves, [9] offs,
    // [10] position packed, [11] parallax packed. Keyframed intensity rides
    // AmFxEval tails from index 14.
    const val TURBULENT_DISPLACE2_AM = "turbulent_displace2_am"
    // SHAPEFIELD_AM (native 200 + EXT 201 + EXT2 202): Alight's shapefield
    // (the TrueMotion afterz-147 SkSL def) — parallax layers of the layer's
    // own content tiled on a hashed grid with per-cell size / angle / colour
    // rules. [0] position x, [1] position y, [2] position z, [3] density,
    // [4] evolution, [5] seed, [6] scale. EXT [7] shapeSize, [8] shapeAngle,
    // [9] sizeVariance, [10] angleVariance, [11] layerCount, [12] fade,
    // [13] alpha. EXT2 [14] slack, [15] blendMode, [16] coloring, [17]
    // color1 packed, [18] color2 packed, [19] color3 packed, [20] flags
    // (lockLuminance 1 + lockSaturation 2 + interpHue 4). Keyframed evolution
    // / shapeSize ride AmFxEval tails from index 21.
    const val SHAPEFIELD_AM = "shapefield_am"
    // HALFTONE_DOTS_AM (native 203 + EXT 204): Alight's halftone_dots (the
    // Ultra original; DISTINCT from the native halftonedots 71) — a sine
    // product dot screen over centre luma (or its 3x3 smoothed variant).
    // [0] dotsMode, [1] dotsFrequency, [2] dotsAngle, [3] dotsRelWidth,
    // [4] dotsSharpness, [5] dotsLighten, [6] smoothSource. EXT [7] color1
    // packed, [8] color0 packed, [9] shiftX, [10] shiftY, [11] alpha.
    const val HALFTONE_DOTS_AM = "halftone_dots_am"
    // CC_GLASS_AM (native 205 + EXT 206 + EXT2 207): Alight's cc_glass (the
    // Ultra "Glass Surface") — a bump normal from the layer's own content
    // lighting a displaced sample, distant or point light, Blinn specular.
    // [0] property, [1] softness, [2] height, [3] displacement, [4]
    // lightType, [5] lightIntensity, [6] lightColor packed. EXT [7]
    // lightHeight, [8] lightPosition x, [9] lightPosition y, [10]
    // lightDirection, [11] ambient, [12] diffuse, [13] specular. EXT2 [14]
    // roughness, [15] metal.
    const val CC_GLASS_AM = "cc_glass_am"
    // BOKEH_AM (native 208): Alight's cinematic_bokeh (the Ultra "Bokeh
    // Blur", id com.creative.cinematic_bokeh) — a golden-angle disc average
    // weighted by highlight energy, with the polygonal shape term.
    // [0] blurShape, [1] blurWidth, [2] blurHeight, [3] blurRadius,
    // [4] highlightThreshold, [5] highlightGain, [6] quality. Keyframed
    // blurRadius / blurWidth / blurHeight ride AmFxEval tails from index 7.
    const val BOKEH_AM = "bokeh_am"
    // LENSBLUR_OBS_AM (native 209): Alight's lensblurobs (the Ultra "Bokeh
    // Lens Blur") — a golden-angle bokeh with a ^4 energy weight and the
    // three blend modes. [0] ITERATIONS, [1] progressa, [2] RADIUS,
    // [3] bright, [4] alpha, [5] blendMode. Keyframed progressa rides
    // AmFxEval tails from index 7.
    const val LENSBLUR_OBS_AM = "lensblur_obs_am"
    // SOLID_WIPE_AM (native 210 + EXT 211): Alight's solid_color_wipe (the
    // Ultra original) — a rotated band between start and end fills the layer
    // with a solid colour (normal / multiply / screen). [0] fillColor
    // packed, [1] fillColor alpha (carried, never read), [2] alpha,
    // [3] blendMode, [4] start, [5] end, [6] angle. EXT [7] feather.
    const val SOLID_WIPE_AM = "solid_wipe_am"
    // SOFTBLOOM_AM (native 212): Alight's softblooom (the TrueMotion legacy
    // amz_417 def) — a threshold extract at half res blurred with the def's
    // own incremental-gaussian kernel, composited base * alpha + bloom *
    // intensity * galpha. [0] strength, [1] intensity, [2] threshold,
    // [3] glowStretch x, [4] glowStretch y, [5] galpha, [6] alpha.
    const val SOFTBLOOM_AM = "softbloom_am"
    // LENSD_AM (native 213 + EXT 214): Alight's lensd (the Ultra "Lens
    // Distortion+") — pass 0 delegates to chromabbr (amp / new forwarded,
    // rgbsplit / alphaa at defaults), the main pass averages channel-spread
    // scaled samples of it; the whole chain repeats `iter` times.
    // [0] offs, [1] amp, [2] strength, [3] blurMax, [4] aberrationMax,
    // [5] numIters, [6] bright. EXT [7] alpha, [8] new, [9] iter.
    // Keyframed amp rides AmFxEval tails from index 14.
    const val LENSD_AM = "lensd_am"
    // BCC_FILM_GLOW_AM (native 215 + EXT 216 + EXT2 217 + EXT3 218 + EXT4 219):
    // Alight's bccfilmglow (the Ultra "Film Glow" def) — a five-pass film glow
    // over the whole 33-param set, the reason the fx op model grew EXT3 / EXT4
    // banks (five groups of 7). [0] glowAmount, [1] glowRadius, [2] glowSoftness,
    // [3] glowSpread, [4] threshold, [5] softKnee, [6] threshGamma. EXT [7]
    // hlGlowStr, [8] hlThreshold, [9] hlSaturation, [10] innerWeight,
    // [11] outerWeight, [12] tailWeight, [13] glowTint (packed 0xRRGGBB).
    // EXT2 [14] glowTintStr, [15] glowWarmth, [16] glowSatBoost,
    // [17] colorPreserve, [18] halStr, [19] halColor (packed), [20] diffAmount.
    // EXT3 [21] diffHighlights, [22] filmGrain, [23] filmFade, [24] filmSaturation,
    // [25] filmContrast, [26] filmWarmth, [27] softClipKnee. EXT4 [28] blendMode
    // (1 Add, 2 Screen, 3 Soft Light, 4 Lighten), [29] quality (1..3),
    // [30] glowOnly, [31] ditherAmt, [32] globalMix.
    const val BCC_FILM_GLOW_AM = "bcc_film_glow_am"
    // PAGECURL_AM (native 220 + EXT 221 + EXT2 222): Alight's pagecurl (the Ultra
    // def, casty/2015 curl) — a page corner curls over with a blurred back face,
    // a shine along the curl and a drop shadow. Values [0] progress, [1] radius,
    // [2] angle deg, [3] alpha, [4] strength (the blur), [5] backColour packed
    // 0xRRGGBB, [6] backAlpha; EXT [7] backBlendMode (0 Normal, 1 Multiply,
    // 2 Screen), [8] shineColour packed, [9] shineAlpha, [10] shineSpec,
    // [11] shineBlendMode, [12] dropShadowColour packed, [13] dropShadowAlpha;
    // EXT2 [14] softDropShadow, [15] frontShadowColour packed,
    // [16] frontShadowAlpha, [17] frontShadowBlendMode, [18..20] unused.
    // A keyframed progress rides the tail from index 21.
    const val PAGECURL_AM = "pagecurl_am"
    // TILESHIFT2_AM (native 223 + EXT 224): Alight's tileshift2 — the layer is
    // tiled and each tile is jittered by a hash of its cell (amount cells across,
    // stretch per axis, seed). Values [0] strength, [1] amount, [2] angle deg,
    // [3] seed, [4] screenSpace, [5..6] the position point; EXT [7..8] the
    // stretch point. Keyframed strength / position ride tails from index 14.
    // screenSpace stays layer-anchored here (the importer warns when asked).
    const val TILESHIFT2_AM = "tileshift2_am"
    // FRACTAL_WARP3_AM (native 225 + EXT 226): Alight's fractalwarp3 — the same
    // value-noise fbm displacement as fractalwarp4 (identical fbm, same 3.0 /
    // 25.3 / 12.9 constants), so it shares FRACTAL_WARP_AM's slot layout exactly:
    // values [0] magnitude, [1] scale, [2] intensity, [3] octaves, [4..5] the
    // offset point, [6] parallax x; EXT [7] parallax y. Keyframed magnitude /
    // offset / parallax / scale ride tails from index 14, in fractalwarp4's
    // exact order. screenSpace stays layer-anchored (warned when asked).
    const val FRACTAL_WARP3_AM = "fractal_warp3_am"
    // REPEAT_LINE (native 227): Alight's repeat.line — the fourth arrangement of
    // the repeat family (copies along a line through the layer's position, each
    // stepped by the offset, scaled and faded per copy). Resolved in the draw
    // loops by RepeatEval like the rest of the family, so its native type never
    // reaches the shaders (toNative skips it like Repeat). Values [0] count,
    // [1..2] the position point (preview px), [3..4] the offset (preview px),
    // [5] angle deg, [6] scale %, [7] alpha %, [8] phase; [9..11] the
    // alternating copies' colour (packed + flag, the repeatAltColorPacked
    // pattern), [12..13] unused. The window / ordering props (start / end /
    // phase / ease / overlap / shape / invert / randomOrder / seed / blend) have
    // no counterpart: the importer warns (warnRepeatExtras), and keyframed
    // angle / scale / phase take their first value there — the draw loops run
    // RepeatEval on the stored effect, never through AmFxEval, so no tail can
    // carry them (the scatter evolution-track precedent does not apply: the
    // line's angle / scale / phase are plain scalars, not a stored track).
    const val REPEAT_LINE = "repeat_line"
    // RIPPLELIGHT_AM (native 228 + EXT 229): Alight's ripplelight — the round-5
    // union port (Ultra original "Ripple (Radial Light)" + TrueMotion afterz-438):
    // concentric ripple distortion from an adjustable centre, with distance decay
    // and a directional specular light highlight on the wave crests. Values
    // [0] strength, [1] frequency, [2] speed, [3] radius, [4] decay,
    // [5] centre x, [6] centre y; EXT [7] lightIntensity, [8] lightSharpness,
    // [9] lightColor packed 0xRRGGBB, [10] blend, [11] fixedUV (0/1),
    // [12..13] unused. Keyframed strength / frequency / speed / radius / decay /
    // lightIntensity / lightSharpness / blend ride tails from index 14, in that
    // order (see AmFxEval). The light term is DEF-A's oriented reading
    // (max(cos,0)^sharp × orient^sharp × intensity × lightColor); DEF-B's
    // unoriented |cos| alternative is recorded in fx_plan.h, and the phone
    // comparison on omniframe-hardtrekk-hero arbitrates.
    const val RIPPLELIGHT_AM = "ripplelight_am"
    // GRADIENT_OVERLAY_AM (native 230 + EXT 231): Alight's gradientoverlay2 — the
    // round-5 group port (build 172). The effect composites a two-colour gradient
    // OVER the layer's own pixels (its alpha gates the mix), in the layer's own
    // space; it exists because a GROUP has no fill for the importer's gradient-fill
    // mapping to land on, so gradientoverlay on a group becomes this op instead of
    // a skip. Values [0] colour 1 packed 0xRRGGBB, [1] colour 2 packed, [2] angle
    // deg, [3] scale, [4] offset x, [5] offset y (AM units, 500 = the layer box),
    // [6] alpha; EXT [7] gradType (0 linear / 1 radial / 2 sweep), [8] blendMode
    // (0 normal / 1 multiply / 2 screen), [9] colour 1 alpha, [10] colour 2 alpha,
    // [11..13] unused. Keyframed angle / scale / alpha ride tails from index 14,
    // in that order (see AmFxEval); the packed colours, gradType and blendMode
    // never ride. The def's own defaults (def_gradientoverlay2.xml): colours
    // #FFEE5533 / #FFFFEE88, angle 0, scale 1, alpha 0.75, linear, normal.
    const val GRADIENT_OVERLAY_AM = "gradient_overlay_am"

    /**
     * Most effects on one layer (kFxMaxOps in cpp/engine/fx_plan.h — the pair is ONE bound:
     * the renderer's fixed op array, mirrored here for the importer, [toNative] and the Effects
     * panel). 32 since build 130 (Queue 14h): the largest stack in the 93-file Alight corpus is
     * 29 (blxze-brepizx-pack), and the old 12 silently ate real, implemented effects off such
     * layers. The cost of the bound is the array itself (32 x 144 B per layer draw since the
     * v181 EXT3 / EXT4 banks); project
     * files store the effect list unbounded, so nothing else assumes this number.
     */
    const val MAX_EFFECTS = 32

    val categories = listOf("Blur", "Color", "Light", "Lens", "Distort", "Stylize", "Texture", "Keying", "Backdrop")

    /**
     * The 167 effect definitions. The table itself lives in C++ now
     * (cpp/engine/fx_defs.cpp — generated from this file's old Kotlin list by
     * fx_defs_gen.py, verified field-by-field against it): C++ is the single
     * source of truth, and this list is built from it via [FxDefsNative] on
     * first use. The EXT-group defaults and group types ride the same load
     * (see [extraValues] / [extNativeType]).
     */
    val all: List<FxDef> get() = table.defs

    private class NativeTable(
        val defs: List<FxDef>,
        val extras: Map<String, List<Float>>,
        val extTypes: Map<String, IntArray>
    )

    private val table: NativeTable by lazy {
        val n = FxDefsNative.nativeCount()
        val defs = ArrayList<FxDef>(n)
        val extras = HashMap<String, List<Float>>()
        val extTypes = HashMap<String, IntArray>()
        for (i in 0 until n) {
            val id = FxDefsNative.nativeIdAt(i)
            val pc = FxDefsNative.nativeParamCount(i)
            val params = ArrayList<FxParamDef>(pc)
            for (j in 0 until pc) {
                params += FxParamDef(
                    FxDefsNative.nativeParamName(i, j),
                    FxDefsNative.nativeParamMin(i, j),
                    FxDefsNative.nativeParamMax(i, j),
                    FxDefsNative.nativeParamDefault(i, j),
                    FxDefsNative.nativeParamUnit(i, j),
                    FxDefsNative.nativeParamStep(i, j)
                )
            }
            defs += FxDef(
                id, FxDefsNative.nativeTypeAt(i), FxDefsNative.nativeNameAt(i),
                FxDefsNative.nativeCategoryAt(i), FxDefsNative.nativeBlurbAt(i), params
            )
            val ec = FxDefsNative.nativeExtraCount(i)
            if (ec > 0) {
                val list = ArrayList<Float>(ec)
                for (k in 0 until ec) list += FxDefsNative.nativeExtraAt(i, k)
                extras[id] = list
            }
            val ext = FxDefsNative.nativeExtTypesAt(i)
            if (ext.any { it >= 0 }) extTypes[id] = ext
        }
        NativeTable(defs, extras, extTypes)
    }

    fun def(type: String): FxDef? = all.firstOrNull { it.id == type }

    fun create(type: String): ClipFx? {
        val d = def(type) ?: return null
        return ClipFx(type = type, values = d.params.map { it.default } + extraValues(type))
    }

    /**
     * Values past the declared params ([ClipFx.values] from index 7 on): Glow Scan's glow colour,
     * and the EXT groups of the v163 tier (Four Color Gradient's anchor points, Turbulent
     * Displace's and Pixelate's secondary params — Alight's own defaults). Panel edits keep them
     * (the Long Shadow colour precedent); [toNative] falls back to them when a value is missing.
     */
    fun extraValues(type: String): List<Float> = table.extras[type] ?: emptyList()

    /** The native type of an effect's EXT group (its values [7..13]), or null when it has none. */
    fun extNativeType(type: String): Int? = table.extTypes[type]?.get(0)?.takeIf { it >= 0 }

    /** The native type of an effect's EXT2 group (its values [14..20]), or null when it has none. */
    fun ext2NativeType(type: String): Int? = table.extTypes[type]?.get(1)?.takeIf { it >= 0 }

    /** The native type of an effect's EXT3 group (its values [21..27]), or null when it has none. */
    fun ext3NativeType(type: String): Int? = table.extTypes[type]?.get(2)?.takeIf { it >= 0 }

    /** The native type of an effect's EXT4 group (its values [28..34]), or null when it has none. */
    fun ext4NativeType(type: String): Int? = table.extTypes[type]?.get(3)?.takeIf { it >= 0 }

    /**
     * An Alight point (x, y) packed into one float: two 12-bit signed fields,
     * (x + 2048) * 4096 + (y + 2048) — exact in a float for |x|, |y| <= 2047 (the components are
     * rounded to whole units). fxParseOps' owner folds the group into q[], and the renderer
     * unpacks it (fxFourColorGradient in renderer_effects.cpp).
     */
    fun packPoint(x: Float, y: Float): Float = FxDefsNative.nativePackPoint(x, y)

    /**
     * The enabled effects as the flat array the C++ engine reads (groups of 8 floats: type, p0..p6), or null when there
     * is nothing to run. Max [MAX_EFFECTS] effects. Motion Blur is NOT emitted here: it needs the layer's motion at
     * the frame time, so [com.example.engine.MotionBlurEval.resolve] rewrites it to directional / radial blurs first
     * (Motion Blur 4 to its def's three MB4_* stages); (use that at every per-frame call site); the Repeat family is NOT emitted either: the draw
     * loops draw the layer once per copy ([com.example.engine.RepeatEval.copiesOf] for overlay
     * layers, per-copy placements for picture / group layers). A layer with only Motion Blur or only a Repeat still counts
     * in [hasEffects].
     */
    fun toNative(effects: List<ClipFx>): FloatArray? {
        val on = effects.filter {
            it.enabled && def(it.type) != null && it.type != MOTION_BLUR && it.type != MOTION_BLUR4 && it.type != REPEAT &&
                it.type != REPEAT_RADIAL && it.type != REPEAT_SCATTER && it.type != REPEAT_LINE
        }.take(MAX_EFFECTS)
        if (on.isEmpty()) return null
        val out = ArrayList<Float>(on.size * 8)
        for (fx in on) {
            val d = def(fx.type)!!
            out += d.nativeType.toFloat()
            for (k in 0 until 7) out += fx.values.getOrNull(k) ?: d.params.getOrNull(k)?.default ?: 0f
            // An extended effect's second group rides right behind its main one; the engine's
            // fxParseOps folds it into the same op (it never counts as an effect of its own).
            val ext = extNativeType(fx.type)
            if (ext != null) {
                val extra = extraValues(fx.type)
                out += ext.toFloat()
                for (k in 0 until 7) out += fx.values.getOrNull(7 + k) ?: extra.getOrNull(k) ?: 0f
            }
            // A third group (EXT2, the extrude tier) rides behind the EXT group; fxParseOps
            // folds it into the same op's r[] the same way.
            val ext2 = ext2NativeType(fx.type)
            if (ext2 != null) {
                val extra = extraValues(fx.type)
                out += ext2.toFloat()
                for (k in 0 until 7) out += fx.values.getOrNull(14 + k) ?: extra.getOrNull(7 + k) ?: 0f
            }
            // A fourth group (EXT3, bccfilmglow) rides behind EXT2; fxParseOps folds it
            // into the same op's s[] the same way.
            val ext3 = ext3NativeType(fx.type)
            if (ext3 != null) {
                val extra = extraValues(fx.type)
                out += ext3.toFloat()
                for (k in 0 until 7) out += fx.values.getOrNull(21 + k) ?: extra.getOrNull(14 + k) ?: 0f
            }
            // A fifth group (EXT4, bccfilmglow) rides behind EXT3; fxParseOps folds it
            // into the same op's t[] the same way.
            val ext4 = ext4NativeType(fx.type)
            if (ext4 != null) {
                val extra = extraValues(fx.type)
                out += ext4.toFloat()
                for (k in 0 until 7) out += fx.values.getOrNull(28 + k) ?: extra.getOrNull(21 + k) ?: 0f
            }
        }
        return out.toFloatArray()
    }

    /** True when the layer has any enabled effect, Motion Blur and Repeat included (they are resolved elsewhere, so [toNative] skips them). */
    fun hasEffects(effects: List<ClipFx>): Boolean = effects.any { it.enabled && def(it.type) != null }

    /**
     * Eyedropper for Chroma Key: [key hue 0..360, key strength 0..100, key brightness 0..100, brightness match 0..100] that make the key colour equal the picked colour (ANY colour)
     * ([argb] 0xAARRGGBB). Runs fxKeyFromRgb in cpp/engine/fx_plan.cpp (host-tested there) — that C++ copy is now the only one.
     */
    fun keyFromRgb(argb: Long): FloatArray = FxDefsNative.nativeKeyFromRgb(argb)

    /** A swatch colour (0xAARRGGBB) for the key hue / strength sliders of Chroma Key. */
    fun keySwatchArgb(hue: Float, strength: Float, brightness: Float = 100f): Long =
        FxDefsNative.nativeKeySwatchArgb(hue, strength, brightness)

    /** Film grain pattern number of a timeline time: it changes 24 times a second, the same in the preview and the export. */
    fun grainFrame(timeMs: Long): Int = FxDefsNative.nativeGrainFrame(timeMs)
}
