package com.example.model

import com.example.engine.ValueLawsNative

/** How a blend mode combines the layer with what lies beneath it. */
enum class BlendKind {
    /** Plain "source over". */
    NORMAL,

    /** Separable / non-separable colour blend (Multiply, Screen, Hue, ...). */
    COLOR,

    /** The layer cuts the layers beneath it (Alpha Matte, Luma, ...). */
    MATTE
}

/**
 * One blending mode. [shaderId] is the number the export shader switches on, so the preview
 * (Compose BlendMode, see ui/util/LayerBlend.kt) and the export (GLSL, see engine/BlendEffect.kt)
 * show the same look.
 */
data class BlendSpec(
    val name: String,
    val category: String,
    val shaderId: Int,
    val kind: BlendKind
) {
    val isNormal: Boolean get() = kind == BlendKind.NORMAL
}

object BlendModes {
    const val NORMAL = "Normal"

    // The spec table is native DATA since build 247 (value_laws.cpp — the
    // fx_defs precedent): names, categories, shader ids and kinds cross once
    // and the list is built here, in the table's own order. (The Alight
    // Motion notes on the recovered modes — Linear Burn / Darker Color /
    // Lighter Color / Soft Overlay / Subtract / Diff have no Compose
    // equivalent, the canvas falls back to SrcOver while the native preview
    // / export shader implements them exactly — live with the table there.)
    val categories: List<String> = ValueLawsNative.nativeBlendCategoryList().toList()

    val all: List<BlendSpec> = run {
        val names = ValueLawsNative.nativeBlendNames()
        val cats = ValueLawsNative.nativeBlendCategories()
        val ids = ValueLawsNative.nativeBlendShaderIds()
        val kinds = ValueLawsNative.nativeBlendKinds()
        List(names.size) { i ->
            BlendSpec(names[i], cats[i], ids[i], BlendKind.values()[kinds[i]])
        }
    }

    private val byName: Map<String, BlendSpec> = all.associateBy { it.name }

    /** Unknown names (old projects) fall back to Normal. */
    fun find(name: String?): BlendSpec = byName[name] ?: all.first()

    fun inCategory(category: String): List<BlendSpec> = all.filter { it.category == category }
}

/** True when the layer needs any blending work (opacity below 100 % or a blend mode). */
val TimelineClip.hasBlending: Boolean
    get() = opacity < 99.9f || !BlendModes.find(blendMode).isNormal

/** Opacity as 0..1. */
val TimelineClip.opacityFraction: Float
    get() = (opacity / 100f).coerceIn(0f, 1f)
