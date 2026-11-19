package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.model.BoolPt
import com.example.model.GRAD_LINEAR
import com.example.model.GRAD_SWEEP
import com.example.model.Keyframe
import com.example.model.PATH_OP_CLOSE
import com.example.model.PATH_OP_MOVE
import com.example.model.ShapeBoolean
import com.example.model.packColor
import com.example.model.unpackColor
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device check of the native engine (decoder pool -> SurfaceTexture -> OpenGL -> pixels)
 * plus deterministic ground-truth verification of all Step 1A, Step 1B, and Step 2A C++ modules.
 * Call from a background thread, e.g. a debug button:
 *
 *     Thread { Log.i("AmSelfTest", EngineSelfTest.run(context, videoUri)) }.start()
 *     Thread { Log.i("AmSelfTest", EngineSelfTest.runNativeComputeCheck()) }.start()
 *
 * Test 1: one clip drawn at 3 times. Test 2: the same file opened 6 times (more layers than hardware
 * decoders) and all 6 drawn into one frame - this is the situation that crashed the Media3 export.
 * Test 3: native C++ compute modules (LK tracker, audio DSP, GIF, scopes, scene math, booleans, canvas gesture).
 */
object EngineSelfTest {

    private class Stats(val nonBlack: Float, val meanLuma: Float, val center: String)

    private fun analyse(buf: ByteArray, w: Int, h: Int): Stats {
        var nb = 0
        var luma = 0L
        for (i in 0 until w * h) {
            val r = buf[i * 4].toInt() and 0xFF
            val g = buf[i * 4 + 1].toInt() and 0xFF
            val b = buf[i * 4 + 2].toInt() and 0xFF
            if (r > 8 || g > 8 || b > 8) nb++
            luma += (r * 299 + g * 587 + b * 114) / 1000
        }
        val c = ((h / 2) * w + w / 2) * 4
        val center = "rgb(${buf[c].toInt() and 0xFF},${buf[c + 1].toInt() and 0xFF},${buf[c + 2].toInt() and 0xFF})"
        return Stats(nb.toFloat() / (w * h), luma.toFloat() / (w * h), center)
    }

    /**
     * Standalone on-device verification of all migrated C++17 compute modules (no video file needed).
     * Verifies Step 1A (LK tracker, audio beat/duck/noise, GIF quantize+LZW, video scopes) and
     * Step 1B (CanvasSnap, SpatialAlign, TextOnPath, MaskGeometry, ShapeBoolean, CanvasGesture FSM).
     */
    fun runNativeComputeCheck(): String {
        val out = StringBuilder()
        var passed = 0
        var failed = 0
        fun check(name: String, ok: Boolean, detail: String = "") {
            if (ok) {
                passed++
                val msg = "PASS [$name]${if (detail.isNotEmpty()) " $detail" else ""}"
                out.appendLine(msg)
                Log.i("AmSelfTest", msg)
            } else {
                failed++
                val msg = "FAIL [$name]${if (detail.isNotEmpty()) " $detail" else ""}"
                out.appendLine(msg)
                Log.e("AmSelfTest", msg)
            }
        }

        if (!DecoderPool.isAvailable) {
            return "FAIL: libam_engine.so could not be loaded"
        }

        // 1. LK Circle Motion Tracker
        try {
            val w = 96
            val h = 96
            fun makeFrame(ox: Float, oy: Float): FloatArray {
                val g = FloatArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val u = x - ox
                        val v = y - oy
                        g[y * w + x] = (
                            128f +
                                55f * sin(u * 0.45f) * sin(v * 0.45f) +
                                35f * sin((u + v) * 0.25f)
                            ).coerceIn(0f, 255f)
                    }
                }
                return g
            }
            val ptr = LkTrackerNative.nativeCreate(60)
            try {
                val st = FloatArray(6)
                val okStart = LkTrackerNative.nativeStart(ptr, makeFrame(0f, 0f), w, h, 48f, 48f, 20f, st)
                val okStep = okStart && LkTrackerNative.nativeStep(ptr, makeFrame(5f, -3f), w, h, st)
                val errX = abs(st[0] - 53f)
                val errY = abs(st[1] - 45f)
                check(
                    "LKTracker",
                    okStep && errX < 1.5f && errY < 1.5f,
                    "tracked=(${"%.2f".format(st[0])},${"%.2f".format(st[1])}) expected=(53,-45->45)"
                )
            } finally {
                LkTrackerNative.nativeDestroy(ptr)
            }
        } catch (e: Throwable) {
            check("LKTracker", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 2. Audio Beat Detection + Sidechain Ducking + Noise Reduction
        try {
            val sr = 44100
            val samples = FloatArray(sr * 4)
            // 120 BPM clicks every 0.5 s
            for (b in 0 until 8) {
                val base = b * (sr / 2)
                for (i in 0 until 1200) {
                    val idx = base + i
                    if (idx < samples.size) {
                        val env = (1f - i / 1200f)
                        samples[idx] = env * sin(2.0 * PI * 180.0 * i / sr).toFloat()
                    }
                }
            }
            val bpmOut = DoubleArray(1)
            val beats = AudioAnalysisNative.nativeDetectBeats(samples, sr, bpmOut)
            check(
                "BeatDetect",
                beats != null && beats.size >= 4 && bpmOut[0] in 110.0..130.0,
                "beats=${beats?.size ?: 0} bpm=${"%.1f".format(bpmOut[0])}"
            )

            val gains = AudioAnalysisNative.nativeDuckGainsForLevels(
                floatArrayOf(0f, 0.85f, 0.85f, 0.85f, 0f),
                -12f,
                0.2f,
                20L
            )
            check(
                "Ducking",
                gains.size == 5 && gains[0] > 0.95f && gains[3] < 0.6f,
                "g0=${"%.2f".format(gains.getOrElse(0) { -1f })} g3=${"%.2f".format(gains.getOrElse(3) { -1f })}"
            )

            val tone = FloatArray(4096) { i -> 0.4f * sin(2.0 * PI * 440.0 * i / sr).toFloat() }
            val acc = AudioAnalysisNative.nativeNoiseAccCreate()
            val prof = FloatArray(257)
            val frames = try {
                AudioAnalysisNative.nativeNoiseAccPushMono(acc, tone)
                AudioAnalysisNative.nativeNoiseAccFinish(acc, prof)
            } finally {
                AudioAnalysisNative.nativeNoiseAccDestroy(acc)
            }
            val cleaned = AudioAnalysisNative.nativeNoiseProcessMono(tone, prof, 1.0f)
            fun rms(a: FloatArray): Double = sqrt(a.fold(0.0) { s, v -> s + v * v } / a.size.coerceAtLeast(1))
            val inRms = rms(tone)
            val outRms = rms(cleaned)
            check(
                "NoiseReduction",
                frames > 0 && cleaned.size == tone.size && outRms < inRms * 0.5,
                "frames=$frames rms=${"%.3f".format(inRms)}->${"%.3f".format(outRms)}"
            )
        } catch (e: Throwable) {
            check("AudioAnalysis", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 3. GIF89a Quantizer + LZW
        try {
            val w = 16
            val h = 16
            val rgba = ByteArray(w * h * 4)
            for (i in 0 until w * h) {
                val c = i % 4
                rgba[i * 4 + 0] = if (c == 0 || c == 3) 255.toByte() else 0
                rgba[i * 4 + 1] = if (c == 1 || c == 3) 255.toByte() else 0
                rgba[i * 4 + 2] = if (c == 2 || c == 3) 255.toByte() else 0
                rgba[i * 4 + 3] = 255.toByte()
            }
            val indices = ByteArray(w * h)
            val pal = GifNative.nativeQuantize(rgba, w, h, 16, false, indices)
            val lzw = GifNative.nativeLzwCompress(indices, 4)
            check(
                "GifEngine",
                pal != null && pal.isNotEmpty() && lzw != null && lzw.isNotEmpty(),
                "pal=${pal?.size ?: 0} lzwBytes=${lzw?.size ?: 0}"
            )
        } catch (e: Throwable) {
            check("GifEngine", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 4. Video Scope Stats
        try {
            val w = 32
            val h = 32
            val argb = IntArray(w * h) { 0xFFFFFFFF.toInt() }
            val meta = IntArray(4)
            val hr = IntArray(256)
            val hg = IntArray(256)
            val hb = IntArray(256)
            val hy = IntArray(256)
            val vec = IntArray(128 * 128)
            val wave = ScopeStatsNative.nativeAnalyze(argb, w, h, meta, hr, hg, hb, hy, vec)
            check(
                "ScopeStats",
                wave != null && wave.isNotEmpty() && hy[255] == w * h && hr[255] == w * h,
                "luma255=${hy[255]}/${w * h}"
            )
        } catch (e: Throwable) {
            check("ScopeStats", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 5. Scene Math Suite (CanvasSnap, SpatialAlign, TextOnPath, MaskGeometry)
        try {
            val snapOut = FloatArray(6)
            SceneMathNative.nativeCanvasSnap(
                503f, 300f, 453f, 250f, 553f, 350f,
                1000f, 600f, FloatArray(0), 8f, snapOut
            )
            val snapOk = abs(snapOut[0] - (-3f)) < 0.01f && snapOut[2] != 0f && abs(snapOut[3] - 500f) < 0.01f

            val alignOut = SceneMathNative.nativeSpatialDeltas(
                floatArrayOf(10f, 0f, 30f, 20f, 50f, 0f, 70f, 20f),
                SceneMathNative.ACTION_ALIGN_LEFT
            )
            val alignOk = alignOut.size == 3 && alignOut[0].toInt() == 1 && abs(alignOut[1] - (-40f)) < 0.01f

            val textOut = SceneMathNative.nativeLayoutTextOnPath(
                floatArrayOf(0f, 0f, 100f, 0f),
                floatArrayOf(0f, 100f),
                100f,
                floatArrayOf(20f, 30f, 80f),
                0.1f
            )
            val textOk = textOut.size == 15 &&
                abs(textOut[1] - 10f) < 0.01f &&
                abs(textOut[6] - 30f) < 0.01f &&
                abs(textOut[11] - 60f) < 0.01f &&
                textOut[4] != 0f && textOut[14] != 0f

            val sq = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
            val maskFlat = SceneMathNative.nativeFlattenMaskControlPoints(sq, true, 32)
            val pipIn = SceneMathNative.nativePointInPolygon(0f, 0f, sq)
            val pipOut = SceneMathNative.nativePointInPolygon(2f, 0f, sq)
            val maskOk = maskFlat.size >= 24 && pipIn && !pipOut

            check(
                "SceneMath",
                snapOk && alignOk && textOk && maskOk,
                "snap=$snapOk align=$alignOk text=$textOk mask=$maskOk"
            )
        } catch (e: Throwable) {
            check("SceneMath", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 6. Shape Booleans (Union / Intersect / Subtract / XOR + Winding)
        try {
            val sqA = listOf(listOf(BoolPt(0.0, 0.0), BoolPt(10.0, 0.0), BoolPt(10.0, 10.0), BoolPt(0.0, 10.0)))
            val sqB = listOf(listOf(BoolPt(5.0, 0.0), BoolPt(15.0, 0.0), BoolPt(15.0, 10.0), BoolPt(5.0, 10.0)))
            val (sA, cA) = ShapeBooleanNative.packRegion(sqA)
            val (sB, cB) = ShapeBooleanNative.packRegion(sqB)
            val uArea = ShapeBoolean.regionArea(
                ShapeBooleanNative.unpackRegion(ShapeBooleanNative.nativeCombine(sA, cA, sB, cB, ShapeBooleanNative.OP_UNION))
            )
            val iArea = ShapeBoolean.regionArea(
                ShapeBooleanNative.unpackRegion(ShapeBooleanNative.nativeCombine(sA, cA, sB, cB, ShapeBooleanNative.OP_INTERSECT))
            )
            val subArea = ShapeBoolean.regionArea(
                ShapeBooleanNative.unpackRegion(ShapeBooleanNative.nativeCombine(sA, cA, sB, cB, ShapeBooleanNative.OP_SUBTRACT))
            )
            val xorArea = ShapeBoolean.regionArea(
                ShapeBooleanNative.unpackRegion(ShapeBooleanNative.nativeCombine(sA, cA, sB, cB, ShapeBooleanNative.OP_XOR))
            )
            val wnIn = ShapeBooleanNative.nativeWindingNumber(2.5, 5.0, sA, cA)
            val wnOut = ShapeBooleanNative.nativeWindingNumber(20.0, 5.0, sA, cA)
            val ok = abs(uArea - 150.0) < 0.1 &&
                abs(iArea - 50.0) < 0.1 &&
                abs(subArea - 50.0) < 0.1 &&
                abs(xorArea - 100.0) < 0.1 &&
                wnIn == 1 && wnOut == 0
            check(
                "ShapeBoolean",
                ok,
                "U=${"%.1f".format(uArea)} I=${"%.1f".format(iArea)} S=${"%.1f".format(subArea)} X=${"%.1f".format(xorArea)}"
            )
        } catch (e: Throwable) {
            check("ShapeBoolean", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 7. Canvas Gesture FSM (hit-test, double-tap, drag + depthFactor + commit)
        try {
            val h = CanvasGestureNative.nativeCreate()
            try {
                CanvasGestureNative.nativeSetConfig(h, 10f, 80f, 300L, 400L)
                val kinds = intArrayOf(CanvasGestureNative.KIND_TEXT)
                val geom = FloatArray(CanvasGestureNative.ITEM_FLOAT_STRIDE).apply {
                    this[0] = 120f; this[1] = 60f
                    this[2] = 120f; this[3] = 60f
                    this[6] = 1f; this[8] = 0.5f; this[9] = 0.5f
                    this[14] = 10f; this[15] = 20f; this[16] = 2f
                }
                val miss = CanvasGestureNative.nativeOnDown(h, 10f, 10f, 1000L, 400f, 400f, -1, kinds, geom)
                val tap1Down = CanvasGestureNative.nativeOnDown(h, 200f, 200f, 1000L, 400f, 400f, -1, kinds, geom)
                val tap1Up = CanvasGestureNative.nativeOnUp(h, 1060L, true)
                val armed = tap1Up.size >= 5 && tap1Up[0].toInt() == CanvasGestureNative.EV_ARM_SINGLE_TAP
                val tap2Down = CanvasGestureNative.nativeOnDown(h, 202f, 201f, 1150L, 400f, 400f, 0, kinds, geom)
                val tap2Up = CanvasGestureNative.nativeOnUp(h, 1200L, true)
                var gotDoubleTap = false
                var ei = 0
                while (ei + 4 < tap2Up.size) {
                    if (tap2Up[ei].toInt() == CanvasGestureNative.EV_DOUBLE_TAP && tap2Up[ei + 1].toInt() == 0) {
                        gotDoubleTap = true
                    }
                    ei += CanvasGestureNative.EVENT_STRIDE
                }
                val dragDown = CanvasGestureNative.nativeOnDown(h, 200f, 200f, 2000L, 400f, 400f, -1, kinds, geom)
                val dragEvs = CanvasGestureNative.nativeOnMove(h, 40f, -20f)
                var movedX = Float.NaN
                var movedY = Float.NaN
                ei = 0
                while (ei + 4 < dragEvs.size) {
                    if (dragEvs[ei].toInt() == CanvasGestureNative.EV_MOVE) {
                        movedX = dragEvs[ei + 2].toFloat()
                        movedY = dragEvs[ei + 3].toFloat()
                    }
                    ei += CanvasGestureNative.EVENT_STRIDE
                }
                val commitEvs = CanvasGestureNative.nativeOnUp(h, 2200L, true)
                val committed = commitEvs.size >= 5 && commitEvs[0].toInt() == CanvasGestureNative.EV_COMMIT
                val dragOk = dragDown && abs(movedX - 30f) < 0.01f && abs(movedY - 10f) < 0.01f && committed
                check(
                    "CanvasGesture",
                    !miss && tap1Down && armed && tap2Down && gotDoubleTap && dragOk,
                    "doubleTap=$gotDoubleTap drag=($movedX,$movedY) commit=$committed"
                )
            } finally {
                CanvasGestureNative.nativeDestroy(h)
            }
        } catch (e: Throwable) {
            check("CanvasGesture", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 8. Step 2A: Keyframe Easing, Color Track, Speed Integral & SpeedRamp Table
        try {
            val easeVal = FrameEvalNative.nativeEasingApply(0.42f, 0f, 0.58f, 1f, 0.5f)
            val easeOk = abs(easeVal - 0.5f) < 0.01f

            val (c0a, c0b) = packColor(0xFF000000L)
            val (c1a, c1b) = packColor(0xFFFFFFFFL)
            val (cTimes, cVals) = FrameEvalNative.packKeyframes(
                listOf(Keyframe(0L, c0a, c0b), Keyframe(1000L, c1a, c1b))
            )
            val cOut = FloatArray(2)
            FrameEvalNative.nativeKeyframeValueAt(cTimes, cVals, 500L, true, cOut)
            val midColor = unpackColor(cOut[0], cOut[1])
            val midR = ((midColor shr 16) and 0xFFL).toInt()
            val colorOk = midR in 126..129

            val (sTimes, sVals) = FrameEvalNative.packKeyframes(
                listOf(Keyframe(0L, 1.0f), Keyframe(1000L, 3.0f))
            )
            val srcOff = FrameEvalNative.nativeSpeedSourceOffsetMs(sTimes, sVals, 1000L)
            val ramp = FrameEvalNative.nativeSpeedRampTable(sTimes, sVals, 1000L)
            val speedOk = abs(srcOff - 2000L) <= 20L && ramp != null && ramp.size >= 90 && ramp.first() < 1.1f && ramp.last() > 2.9f

            check(
                "KeyframesAndSpeed",
                easeOk && colorOk && speedOk,
                "ease=${"%.3f".format(easeVal)} midR=$midR srcOff=$srcOff rampN=${ramp?.size ?: 0}"
            )
        } catch (e: Throwable) {
            check("KeyframesAndSpeed", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 9. Step 2A: AmFxEval Tail Track & MotionBlurEval (Pos / Radial / MB4)
        try {
            val track = floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 0f, 0f,
                2f,
                0f, 10f, 0.3333f, 0.3333f, 0.6667f, 0.6667f,
                1f, 30f, 0.3333f, 0.3333f, 0.6667f, 0.6667f
            )
            val fxOut = FloatArray(1)
            val fxOk = FrameEvalNative.nativeAmFxTrackValueAt(track, 0.5f, AmFxEval.TAIL_START, fxOut) &&
                abs(fxOut[0] - 20f) < 0.05f

            val mbPos = FloatArray(2)
            val mbPosOk = FrameEvalNative.nativeMotionBlurPos(0f, 0f, 100f, 0f, 0f, 1000f, 1f, mbPos) &&
                abs(mbPos[0] - 0f) < 0.1f && abs(mbPos[1] - 67.5f) < 0.5f

            val mbRad = FloatArray(2)
            val mbRadOk = FrameEvalNative.nativeMotionBlurRadial(179f, 100f, -179f, 100f, 1f, true, true, mbRad) &&
                mbRad[0] > 5f && mbRad[1] < 1f

            val mb4Out = FloatArray(4)
            val mb4Mask = FrameEvalNative.nativeMotionBlur4(
                0f, 0f, 0f, 100f,
                50f, 0f, 10f, 120f,
                1000f, true, true, true, mb4Out
            )
            val mb4Ok = mb4Mask == 7 && abs(mb4Out[3] - 10f) < 0.05f

            check(
                "AmFxAndMotionBlur",
                fxOk && mbPosOk && mbRadOk && mb4Ok,
                "fx=${"%.1f".format(fxOut[0])} mbLen=${"%.1f".format(mbPos[1])} mb4Mask=$mb4Mask"
            )
        } catch (e: Throwable) {
            check("AmFxAndMotionBlur", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 10. Step 2A: RepeatEval (Plain / Radial / Scatter / Line / GroupCopyOffset)
        try {
            val alt = LongArray(2)
            val plain = FrameEvalNative.nativeRepeatEval(
                FrameEvalNative.REPEAT_MODE_PLAIN,
                floatArrayOf(3f, 20f, 10f, 15f, 100f, 50f),
                0f, 0f, 0f, 100f, 100f,
                0f, 1f, 0f, 0L, 1000L, 0L, alt
            )
            val plainOk = plain != null && plain.size == 15 &&
                abs(plain[10] - 40f) < 0.01f && abs(plain[11] - 20f) < 0.01f && abs(plain[14] - 25f) < 0.05f

            val scatterA = FrameEvalNative.nativeRepeatEval(
                FrameEvalNative.REPEAT_MODE_SCATTER,
                floatArrayOf(5f, 200f, 10f, -5f, 42f, 20f, 100f, 0f, 0f, 0f, 1f, 1f),
                0f, 0f, 0f, 100f, 100f,
                0f, 1f, 0f, 0L, 1000L, 500L, alt
            )
            val scatterB = FrameEvalNative.nativeRepeatEval(
                FrameEvalNative.REPEAT_MODE_SCATTER,
                floatArrayOf(5f, 200f, 10f, -5f, 42f, 20f, 100f, 0f, 0f, 0f, 1f, 1f),
                0f, 0f, 0f, 100f, 100f,
                0f, 1f, 0f, 0L, 1000L, 500L, alt
            )
            val scatterOk = scatterA != null && scatterB != null && scatterA.contentEquals(scatterB) && scatterA.size == 25

            val gOut = FloatArray(2)
            FrameEvalNative.nativeGroupCopyOffset(100f, 0f, 10f, 0f, 90f, 1f, 2f, gOut)
            val groupOk = abs(gOut[0] - 120f) < 0.05f && abs(gOut[1] - (-100f)) < 0.05f

            check(
                "RepeatEval",
                plainOk && scatterOk && groupOk,
                "plain=$plainOk scatterDeterministic=$scatterOk group=(${"%.1f".format(gOut[0])},${"%.1f".format(gOut[1])})"
            )
        } catch (e: Throwable) {
            check("RepeatEval", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 11. Step 2A: TextAnimEval, TextProgressEval & CounterEval
        try {
            val taOut = FloatArray(2)
            FrameEvalNative.nativeTextAnimValueAt(
                floatArrayOf(0f, 1f),
                floatArrayOf(0f, 10f, 100f, 30f),
                2,
                byteArrayOf(0, 1),
                floatArrayOf(0f, 0f, 0f, 0f, 0.42f, 0f, 0.58f, 1f),
                false, 0.5f, taOut
            )
            val taOk = abs(taOut[0] - 50f) < 0.5f && abs(taOut[1] - 20f) < 0.5f

            val tpOut = IntArray(2)
            FrameEvalNative.nativeTextProgressVisibleRange(12, 0f, 0.378f, tpOut)
            val tpOk = tpOut[0] == 0 && tpOut[1] == 4

            val counted = FrameEvalNative.nativeCounterSubstitute("মোট $1,000.50 এবং -5", 10.0, 2.0)
            val counterOk = counted == "মোট $2,011.00 এবং 0"

            check(
                "TextAndCounterEval",
                taOk && tpOk && counterOk,
                "tp=${tpOut[0]}..${tpOut[1]} counted='$counted'"
            )
        } catch (e: Throwable) {
            check("TextAndCounterEval", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 12. Step 2A: Parametric Outlines (Pie / Arc / Callout) & GradGeometry
        try {
            val pieSweep = FrameEvalNative.nativePieArcSweepDegrees(0.0, 90.0)
            val pieSegs = FrameEvalNative.nativePieSegments(0.0, 90.0, 1.0)?.let { FrameEvalNative.unpackSegments(it) }
            val arcSegs = FrameEvalNative.nativeArcSegments(0.0, 180.0, 1.0, false)?.let { FrameEvalNative.unpackSegments(it) }
            val calloutSegs = FrameEvalNative.nativeCalloutSegments(100.0, 60.0, 140.0, 10.0, 12.0, 24.0)
                ?.let { FrameEvalNative.unpackSegments(it) }
            val pts = FloatArray(4)
            val inv = FloatArray(2)
            FrameEvalNative.nativeGradEndpoints(GRAD_LINEAR, 0.5f, 0.5f, 35f, 1.4f, pts)
            FrameEvalNative.nativeGradAngleScale(GRAD_LINEAR, pts[0], pts[1], pts[2], pts[3], inv)
            val swPts = FloatArray(4)
            val swInv = FloatArray(2)
            FrameEvalNative.nativeGradEndpoints(GRAD_SWEEP, 0.5f, 0.5f, 125f, 0.8f, swPts)
            FrameEvalNative.nativeGradAngleScale(GRAD_SWEEP, swPts[0], swPts[1], swPts[2], swPts[3], swInv)

            val ok = abs(pieSweep - 270.0) < 0.01 &&
                pieSegs != null && pieSegs.first().op == PATH_OP_MOVE && pieSegs.last().op == PATH_OP_CLOSE &&
                arcSegs != null && arcSegs.size == 3 &&
                calloutSegs != null && calloutSegs.size == 13 &&
                abs(inv[0] - 35f) < 0.05f && abs(inv[1] - 1.4f) < 0.01f &&
                abs(swInv[0] - 125f) < 0.05f && abs(swInv[1] - 0.8f) < 0.01f
            check(
                "ParametricOutlinesAndGrad",
                ok,
                "pieSweep=${"%.1f".format(pieSweep)} pieSegs=${pieSegs?.size ?: 0} callout=${calloutSegs?.size ?: 0}"
            )
        } catch (e: Throwable) {
            check("ParametricOutlinesAndGrad", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 13. Step 2A: ProjectDocNative (Chain Layout, Bounded History Ring, XML Sanitize/Esc/Float)
        try {
            val chainStart = ProjectDocNative.nativeSolveChainStart(
                longArrayOf(0L, 1000L),
                longArrayOf(1000L, 2500L),
                500L,
                800L
            )
            val chainOk = chainStart == 2500L

            val hh = ProjectDocNative.nativeHistoryCreate(2)
            val histOk = try {
                ProjectDocNative.nativeHistoryPush(hh, "one".toByteArray())
                ProjectDocNative.nativeHistoryPush(hh, "two".toByteArray())
                ProjectDocNative.nativeHistoryPush(hh, "three".toByteArray())
                val sz = ProjectDocNative.nativeHistorySize(hh)
                val p1 = ProjectDocNative.nativeHistoryPop(hh)?.toString(Charsets.UTF_8)
                val p2 = ProjectDocNative.nativeHistoryPop(hh)?.toString(Charsets.UTF_8)
                val p3 = ProjectDocNative.nativeHistoryPop(hh)
                sz == 2 && p1 == "three" && p2 == "two" && p3 == null
            } finally {
                ProjectDocNative.nativeHistoryDestroy(hh)
            }

            val esc = ProjectDocNative.nativeXmlEscAttr("A\u0001<B>&\"C\"\uFFFF")
            val fmt0 = ProjectDocNative.nativeFormatCorpusFloat(-0.0000001f)
            val fmt1 = ProjectDocNative.nativeFormatCorpusFloat(540.0f)
            val fmt2 = ProjectDocNative.nativeFormatCorpusFloat(0.169692f)
            val xmlOk = esc == "A &lt;B&gt;&amp;&quot;C&quot; " &&
                fmt0 == "0" && fmt1 == "540" && fmt2 == "0.169692"

            check(
                "ProjectDocEngine",
                chainOk && histOk && xmlOk,
                "chainStart=$chainStart hist=$histOk xml=$xmlOk"
            )
        } catch (e: Throwable) {
            check("ProjectDocEngine", false, "${e::class.java.simpleName}: ${e.message}")
        }

        // 14. Step 3: Native GLES3 Hotspot UI Renderer (Scopes + Easing + Vector Cmds offscreen FBO)
        try {
            val uiGl = GlThread("am-uigl-selftest")
            val counts = IntArray(4)
            val uiOk = try {
                uiGl.call {
                    val h = UiGlNative.nativeCreate()
                    if (h == 0L) false else try {
                        UiGlNative.nativeSelfTestOffscreen(h, 160, 120, counts)
                    } finally {
                        UiGlNative.nativeDestroy(h)
                    }
                }
            } finally {
                uiGl.close()
            }
            val pxOk = uiOk && counts[0] > 200 && counts[1] > 100 && counts[2] > 200 && counts[3] > 200
            check(
                "UiGlHotspotRenderer",
                pxOk,
                "hist=${counts[0]} waveVec=${counts[1]} easing=${counts[2]} vec=${counts[3]}"
            )
        } catch (e: Throwable) {
            check("UiGlHotspotRenderer", false, "${e::class.java.simpleName}: ${e.message}")
        }

        val summary = "NATIVE COMPUTE: $passed passed, $failed failed"
        out.appendLine(summary)
        Log.i("AmSelfTest", summary)
        return out.toString()
    }

    fun run(context: Context, uri: Uri): String {
        val out = StringBuilder()
        fun log(s: String) {
            out.appendLine(s)
            Log.i("AmSelfTest", s)
        }
        if (!DecoderPool.isAvailable) return "FAIL: libam_engine.so could not be loaded (is the native build part of the app?)"
        val gl = GlThread("am-selftest")
        val pool = DecoderPool(maxHardware = 3, maxSoftware = 2)
        try {
            gl.call {
                val r = NativeRenderer.create()
                if (r == null) {
                    log("FAIL: no OpenGL ES 3 / EGL renderer")
                    return@call
                }
                try {
                    val w = 360
                    val h = 640
                    if (!r.setOffscreen(w, h)) {
                        log("FAIL: offscreen output not available")
                        return@call
                    }
                    val buf = ByteArray(w * h * 4)

                    // ---- Test 1: one clip, three times
                    val one = VideoLayerSource.open(context, uri, r, pool)
                    if (one == null) {
                        log("FAIL: video could not be opened")
                        return@call
                    }
                    val c = one.clip
                    log("clip ${c.width}x${c.height} rot=${c.rotation} fps=${c.fps} dur=${c.durationUs / 1000} ms")
                    val dur = if (c.durationUs > 0) c.durationUs else 3_000_000L
                    for (t in longArrayOf(0L, dur / 4, dur / 2)) {
                        val t0 = SystemClock.elapsedRealtime()
                        val st = one.seek(t)
                        r.beginFrame()
                        r.drawLayer(one.textureId, true, one.texMatrix, one.contentWidth, one.contentHeight, c.rotation)
                        r.readPixels(0, 0, w, h, buf)
                        r.endFrame()
                        val s = analyse(buf, w, h)
                        log("T1 seek ${t / 1000} ms -> status=$st pts=${c.ptsUs / 1000} ms, ${SystemClock.elapsedRealtime() - t0} ms, " +
                            "non-black=${"%.0f".format(s.nonBlack * 100)}% luma=${"%.0f".format(s.meanLuma)} center=${s.center}")
                    }
                    one.close()

                    // ---- Test 2: 6 layers of the same file on 3 hardware decoders
                    val n = 6
                    val layers = (0 until n).mapNotNull { VideoLayerSource.open(context, uri, r, pool) }
                    if (layers.size < n) log("WARN: only ${layers.size}/$n layers opened")
                    var failures = 0
                    val t0 = SystemClock.elapsedRealtime()
                    for (round in 0 until 3) {
                        r.beginFrame(0.1f, 0.1f, 0.1f, 1f)
                        layers.forEachIndexed { i, l ->
                            var st: Int
                            var tries = 0
                            do {
                                st = l.seek((dur / 8) * (i + round))
                                if (st == DecoderPool.BUSY) SystemClock.sleep(5)
                            } while (st == DecoderPool.BUSY && ++tries < 100)
                            if (st < 0 && st != DecoderPool.END_OF_STREAM) failures++
                            val p = LayerParams().apply {
                                scaleX = 0.45f; scaleY = 0.45f
                                offsetX = if (i % 2 == 0) -w * 0.25f else w * 0.25f
                                offsetY = (i / 2 - 1) * h * 0.3f
                            }
                            r.drawLayer(l.textureId, true, l.texMatrix, l.contentWidth, l.contentHeight, l.drawRotation, p)
                        }
                        r.readPixels(0, 0, w, h, buf)
                        r.endFrame()
                        val s = analyse(buf, w, h)
                        log("T2 round $round: non-black=${"%.0f".format(s.nonBlack * 100)}% luma=${"%.0f".format(s.meanLuma)}")
                    }
                    val ps = pool.stats()
                    log("T2 ${SystemClock.elapsedRealtime() - t0} ms, failures=$failures | decoders hw ${ps.activeHw}/${ps.maxHw}" +
                        " sw ${ps.activeSw}/${ps.maxSw}, evictions=${ps.evictions}, fallbacks=${ps.fallbacks}, errors=${ps.errors}")
                    layers.forEach { it.close() }
                    log(if (failures == 0) "RESULT: OK" else "RESULT: FAILED ($failures seeks failed)")
                } finally {
                    r.close()
                }
            }
            out.append(runNativeComputeCheck())
            val tmpAtomic = File(context.cacheDir, "am_selftest_atomic.json")
            try {
                val payload1 = """{"v":1,"title":"বাংলা"}""".toByteArray(Charsets.UTF_8)
                val payload2 = """{"v":2,"title":"updated"}""".toByteArray(Charsets.UTF_8)
                val w1 = ProjectDocNative.nativeWriteAtomic(tmpAtomic.absolutePath, payload1)
                val w2 = ProjectDocNative.nativeWriteAtomic(tmpAtomic.absolutePath, payload2)
                val cur = ProjectDocNative.nativeReadFile(tmpAtomic.absolutePath)?.toString(Charsets.UTF_8)
                val bak = ProjectDocNative.nativeReadFile(tmpAtomic.absolutePath + ".bak")?.toString(Charsets.UTF_8)
                val ioOk = w1 && w2 && cur == """{"v":2,"title":"updated"}""" && bak == """{"v":1,"title":"বাংলা"}"""
                log(if (ioOk) "PASS [ProjectStoreAtomicIO] current+bak verified" else "FAIL [ProjectStoreAtomicIO] cur=$cur bak=$bak")
            } finally {
                tmpAtomic.delete()
                File(tmpAtomic.absolutePath + ".bak").delete()
                File(tmpAtomic.absolutePath + ".tmp").delete()
            }
        } catch (e: Throwable) {
            log("FAIL: ${e::class.java.simpleName}: ${e.message}")
        } finally {
            pool.close()
            gl.close()
        }
        return out.toString()
    }
}
