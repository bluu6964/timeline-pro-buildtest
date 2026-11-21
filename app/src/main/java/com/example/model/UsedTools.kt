package com.example.model

import com.example.engine.ValueLawsNative

/**
 * Which tools of the clip tool row have actually been USED on a layer (build 166 —
 * Sarowar's directive 2026-11-13 ~20:22, item 5): when the layer is selected, the tool
 * buttons of the used tools read WHITE / bright in the B/W chrome, the unused ones stay
 * grey, so what is applied to the layer is visible at a glance.
 *
 * The rule is state-based and conservative — a tool counts as used exactly when the
 * clip carries a non-default trace of it:
 *  - "effects":    at least one effect in [TimelineClip.effects].
 *  - "animations": an In or Out animation is set (animIn / animOut).
 *  - "color":      the fill is not the plain default — a gradient / image / video fill
 *                  (fillMode != FILL_SOLID), a removed fill (FILL_NONE), or a fill /
 *                  text colour other than the default white.
 *  - "transform":  any keyframes, or a non-default transform (moved, scaled, turned,
 *                  3D-turned or depth-moved from the layer's resting state).
 *  - "crop":       the crop window is smaller than the whole picture on either axis.
 *  - "blend":      a blend mode other than Normal, or an opacity other than 100 %.
 *  - "masks":      at least one mask in [TimelineClip.masks].
 *  - "volume":     a volume other than 100 %, or the layer is muted.
 *  - "cutout":     a finished cutout is stored on the layer (cutoutDir set).
 *  - "parent":     the layer has a parent link.
 * Every other tool (track, remove, stabilize, face tools, freeze,
 * rename, edit, group tools ...) is an action without a readable stored state on the
 * clip, so it never reports used. Pure and unit tested (UsedToolsTest).
 */
object UsedTools {

    /** The default fill / text colour of a fresh layer (white). */
    const val DEFAULT_FILL: Long = 0xFFFFFFFFL

    fun usedToolsOf(clip: TimelineClip): Set<String> {
        // The rule is a native bitmask since build 247 (value_laws.cpp); the
        // clip's object reads (counts, null checks, the blend-mode string
        // compare) stay here, the threshold rules run there.
        val mask = ValueLawsNative.nativeUsedToolsMask(
            clip.effects.size,
            clip.animIn != null || clip.animOut != null,
            clip.fillMode != FILL_SOLID,
            clip.textColor,
            clip.keyframes.isNotEmpty(),
            clip.transformX, clip.transformY, clip.rotationDeg, clip.scalePercent,
            clip.rotation3dX, clip.rotation3dY, clip.transformZ,
            clip.cropWidthPercent, clip.cropHeightPercent,
            clip.blendMode != "Normal",
            clip.opacity,
            clip.masks.size,
            clip.volume, clip.isMuted,
            clip.cutoutDir != null,
            clip.parent != null
        )
        val out = LinkedHashSet<String>()
        if (mask and 1 != 0) out += "effects"
        if (mask and 2 != 0) out += "animations"
        if (mask and 4 != 0) out += "color"
        if (mask and 8 != 0) out += "transform"
        if (mask and 16 != 0) out += "crop"
        if (mask and 32 != 0) out += "blend"
        if (mask and 64 != 0) out += "masks"
        if (mask and 128 != 0) out += "volume"
        if (mask and 256 != 0) out += "cutout"
        if (mask and 512 != 0) out += "parent"
        return out
    }
}
