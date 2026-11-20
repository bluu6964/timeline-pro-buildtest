package com.example.engine

/**
 * JNI bridge for the C++ effect definition table (fx_defs.cpp): the 167 effect
 * definitions (ids, native types, names, categories, blurbs, slider defs,
 * EXT-group defaults and group types) plus the pure helpers ported from
 * ClipFxLibrary (packPoint, keyFromRgb, keySwatchArgb, grainFrame).
 *
 * The table used to be a Kotlin literal list in ClipFxLibrary; it was
 * generated into C++ (fx_defs_gen.py) so the C++ side is the single source of
 * truth. There is NO Kotlin copy of the table any more — [com.example.model.ClipFxLibrary]
 * builds its FxDef list from these accessors on first use.
 *
 * Native is the only path, like the other post-234 bridges: a genuinely
 * missing libam_engine.so fails loudly at the first call. [isAvailable] stays
 * as a read-only probe (the UiMathNative precedent — no throwing init gate, so
 * a host JVM test that touches this object without the library does not break
 * in <init>).
 */
object FxDefsNative {
    init {
        // Trigger DecoderPool's library load on first touch of this bridge (it
        // is the one place that calls System.loadLibrary), so a table load can
        // never race it when ClipFxLibrary happens to be the first native
        // touch. Not a gate: when the library is genuinely absent this still
        // completes, [isAvailable] reports false, and the first external call
        // fails loudly — the post-234 rule.
        DecoderPool.isAvailable
    }

    val isAvailable: Boolean get() = try { DecoderPool.isAvailable } catch (_: Throwable) { false }

    @JvmStatic external fun nativeCount(): Int
    @JvmStatic external fun nativeIdAt(index: Int): String
    @JvmStatic external fun nativeTypeAt(index: Int): Int
    @JvmStatic external fun nativeNameAt(index: Int): String
    @JvmStatic external fun nativeCategoryAt(index: Int): String
    @JvmStatic external fun nativeBlurbAt(index: Int): String
    @JvmStatic external fun nativeParamCount(index: Int): Int
    @JvmStatic external fun nativeParamName(index: Int, param: Int): String
    @JvmStatic external fun nativeParamMin(index: Int, param: Int): Float
    @JvmStatic external fun nativeParamMax(index: Int, param: Int): Float
    @JvmStatic external fun nativeParamDefault(index: Int, param: Int): Float
    @JvmStatic external fun nativeParamStep(index: Int, param: Int): Float
    @JvmStatic external fun nativeParamUnit(index: Int, param: Int): String
    @JvmStatic external fun nativeExtraCount(index: Int): Int
    @JvmStatic external fun nativeExtraAt(index: Int, k: Int): Float
    /** [ext, ext2, ext3, ext4] native group types of a def; -1 = no such group. */
    @JvmStatic external fun nativeExtTypesAt(index: Int): IntArray
    @JvmStatic external fun nativePackPoint(x: Float, y: Float): Float
    /** [key hue 0..360, key strength 0..100, key brightness 0..100, brightness match 0..100] for a picked 0xAARRGGBB colour. */
    @JvmStatic external fun nativeKeyFromRgb(argb: Long): FloatArray
    @JvmStatic external fun nativeKeySwatchArgb(hue: Float, strength: Float, brightness: Float): Long
    @JvmStatic external fun nativeGrainFrame(timeMs: Long): Int
}
