package com.example

import com.example.model.LayerType
import com.example.model.ShapeBoolean
import com.example.model.ShapeCatalog
import com.example.model.SheetGeometry
import com.example.model.TimelineClip
import com.example.model.primitiveShapeRings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Build 256 — pins the 100-kind shape catalog ([ShapeCatalog]): the exact
 * count, unique patterns / names, no collision with the legacy pattern
 * names (old projects keep their historical renderers), and the geometry
 * invariants every renderer relies on — each kind generates a non-empty
 * outline of at least 3 points per subpath, stays inside (or sanely near)
 * the unit box, is a SIMPLE outline (no self-crossings — the medal-ribbon
 * bowtie class of bug), has real filled area under the even-odd rule, and
 * is deterministic (the generators are pure; known kinds' outputs are
 * pinned to their analytic values). Plus the new picker panel's grid law
 * ([SheetGeometry.ShapeGrid]).
 */
class ShapeCatalogTest {

    private val legacy = setOf(
        "CIRCLE", "OVAL", "ELLIPSE", "RECT", "RECTANGLE", "SQUARE", "ROUNDED",
        "ROUNDED_SQUARE", "ROUNDED_RECT", "WIDE_ROUNDED_RECT", "TRIANGLE",
        "PENTAGON", "STAR", "PATH"
    )

    @Test
    fun exactlyOneHundredKinds() {
        assertEquals(100, ShapeCatalog.all.size)
    }

    @Test
    fun patternsAndNamesUniqueAndPrefixed() {
        val patterns = ShapeCatalog.all.map { it.pattern }
        val names = ShapeCatalog.all.map { it.name }
        assertEquals(100, patterns.toSet().size)
        assertEquals(100, names.toSet().size)
        for (p in patterns) {
            assertTrue("pattern $p lacks the SH_ prefix", p.startsWith("SH_"))
            assertTrue("pattern $p collides with a legacy kind", p !in legacy)
        }
    }

    @Test
    fun categoryCounts() {
        val counts = ShapeCatalog.all.groupingBy { it.category }.eachCount()
        assertEquals(12, counts[ShapeCatalog.Category.BASICS])
        assertEquals(12, counts[ShapeCatalog.Category.POLYGONS])
        assertEquals(12, counts[ShapeCatalog.Category.STARS])
        assertEquals(12, counts[ShapeCatalog.Category.ARROWS])
        assertEquals(14, counts[ShapeCatalog.Category.SYMBOLS])
        assertEquals(12, counts[ShapeCatalog.Category.BADGES])
        assertEquals(12, counts[ShapeCatalog.Category.NATURE])
        assertEquals(14, counts[ShapeCatalog.Category.DECOR])
        assertEquals(100, ShapeCatalog.inCategory(null).size)
        for (c in ShapeCatalog.categories) {
            assertEquals(counts[c], ShapeCatalog.inCategory(c).size)
        }
    }

    @Test
    fun lookupByPattern() {
        assertEquals("Heart", ShapeCatalog.byPattern("SH_HEART")?.name)
        assertEquals("Heart", ShapeCatalog.byPattern("sh_heart")?.name)
        assertNull(ShapeCatalog.byPattern("CIRCLE"))
        assertNull(ShapeCatalog.byPattern("PATH"))
        assertNull(ShapeCatalog.byPattern("NOPE"))
        assertNull(ShapeCatalog.byPattern(null))
    }

    private fun area(pts: FloatArray): Double {
        var a = 0.0
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += pts[i * 2].toDouble() * pts[j * 2 + 1] - pts[j * 2].toDouble() * pts[i * 2 + 1]
        }
        return a / 2.0
    }

    @Test
    fun everyKindGeneratesInsideTheBox() {
        for (def in ShapeCatalog.all) {
            assertTrue("${def.pattern} has no subpaths", def.subs.isNotEmpty())
            var flatCount = 0
            for (s in def.subs) {
                assertTrue("${def.pattern} subpath under 3 points", s.count >= 3)
                assertEquals(0, s.pts.size % 2)
                flatCount += s.count
                for (v in s.pts) {
                    assertTrue("${def.pattern} non-finite point", v.isFinite())
                    assertTrue("${def.pattern} point $v escapes the unit box", abs(v) <= 1.02f)
                }
            }
            assertEquals(def.totalPoints, flatCount)
            assertEquals(def.totalPoints * 2, def.flat.size)
        }
    }

    @Test
    fun everyKindHasRealFilledArea() {
        for (def in ShapeCatalog.all) {
            var filled = 0.0
            for (s in def.subs) {
                val a = abs(area(s.pts))
                assertTrue("${def.pattern} degenerate subpath (area $a)", a > 0.005)
                filled += if (s.hole) -a else a
            }
            assertTrue("${def.pattern} filled area $filled too small", filled > 0.05)
        }
    }

    /** Proper crossing of segments (a,b) and (c,d): interiors meet. */
    private fun crosses(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double
    ): Boolean {
        fun ori(px: Double, py: Double, qx: Double, qy: Double, rx: Double, ry: Double) =
            (qx - px) * (ry - py) - (qy - py) * (rx - px)
        val o1 = ori(cx, cy, dx, dy, ax, ay)
        val o2 = ori(cx, cy, dx, dy, bx, by)
        val o3 = ori(ax, ay, bx, by, cx, cy)
        val o4 = ori(ax, ay, bx, by, dx, dy)
        return (o1 > 0) != (o2 > 0) && (o3 > 0) != (o4 > 0) &&
            abs(o1) > 1e-12 && abs(o2) > 1e-12 && abs(o3) > 1e-12 && abs(o4) > 1e-12
    }

    @Test
    fun noSubpathCrossesItself() {
        for (def in ShapeCatalog.all) {
            for (s in def.subs) {
                val n = s.count
                for (i in 0 until n) {
                    val i2 = (i + 1) % n
                    for (j in i + 1 until n) {
                        if (j == i2 || (i == 0 && j == n - 1)) continue // adjacent (incl. wrap)
                        val j2 = (j + 1) % n
                        assertTrue(
                            "${def.pattern} subpath self-crosses at segments $i/$j",
                            !crosses(
                                s.pts[i * 2].toDouble(), s.pts[i * 2 + 1].toDouble(),
                                s.pts[i2 * 2].toDouble(), s.pts[i2 * 2 + 1].toDouble(),
                                s.pts[j * 2].toDouble(), s.pts[j * 2 + 1].toDouble(),
                                s.pts[j2 * 2].toDouble(), s.pts[j2 * 2 + 1].toDouble()
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun knownKindsPinToAnalyticGeometry() {
        // Square: the four box corners, in walk order.
        val square = ShapeCatalog.byPattern("SH_SQUARE")!!
        assertEquals(4, square.totalPoints)
        assertEquals(-1f, square.flat[0]); assertEquals(-1f, square.flat[1])
        assertEquals(1f, square.flat[2]); assertEquals(1f, square.flat[7])
        // Circle: 64 samples, every point on radius 1.
        val circle = ShapeCatalog.byPattern("SH_CIRCLE")!!
        assertEquals(64, circle.totalPoints)
        for (i in 0 until 64) {
            val r = hypot(circle.flat[i * 2].toDouble(), circle.flat[i * 2 + 1].toDouble())
            assertEquals("circle point $i off radius", 1.0, r, 0.002)
        }
        // Star: outer tips on radius 1, valleys on 0.45.
        val star = ShapeCatalog.byPattern("SH_STAR5")!!
        assertEquals(10, star.totalPoints)
        for (i in 0 until 10) {
            val r = hypot(star.flat[i * 2].toDouble(), star.flat[i * 2 + 1].toDouble())
            assertEquals("star point $i", if (i % 2 == 0) 1.0 else 0.45, r, 0.002)
        }
        // Ring: an outer disc and a flagged, smaller hole.
        val ring = ShapeCatalog.byPattern("SH_RING")!!
        assertEquals(2, ring.subs.size)
        assertTrue(ring.subs[1].hole)
        assertTrue(abs(area(ring.subs[1].pts)) < abs(area(ring.subs[0].pts)))
        // Determinism: the flat feed is exactly the subpaths concatenated.
        for (def in ShapeCatalog.all) {
            var o = 0
            for (s in def.subs) {
                for (k in s.pts.indices) assertEquals(s.pts[k], def.flat[o + k])
                o += s.pts.size
            }
        }
    }

    @Test
    fun booleanRingsFollowTheCatalog() {
        fun clip(pattern: String) = TimelineClip(
            id = "x", name = "x", type = LayerType.SHAPE,
            startMs = 0L, durationMs = 1000L, color = 0xFFFF3377,
            shapePattern = pattern
        )
        // Square: one positive ring of area 4.
        val square = primitiveShapeRings(clip("SH_SQUARE"))
        assertEquals(1, square.size)
        assertEquals(4.0, ShapeBoolean.regionArea(square), 1e-9)
        // Ring: outer positive, hole negative; net = the 64-gon annulus area.
        val ring = primitiveShapeRings(clip("SH_RING"))
        assertEquals(2, ring.size)
        assertTrue(ShapeBoolean.signedArea(ring[0]) > 0)
        assertTrue(ShapeBoolean.signedArea(ring[1]) < 0)
        val want = Math.PI * (0.97 * 0.97 - 0.58 * 0.58)
        assertEquals(want, ShapeBoolean.regionArea(ring), 0.02)
        // Pause: two separate solid rings.
        assertEquals(2, primitiveShapeRings(clip("SH_PAUSE")).size)
        // Every kind yields rings whose net area matches its even-odd fill area.
        for (def in ShapeCatalog.all) {
            val rings = primitiveShapeRings(clip(def.pattern))
            var filled = 0.0
            for (s in def.subs) filled += if (s.hole) -abs(area(s.pts)) else abs(area(s.pts))
            assertEquals("${def.pattern} boolean area", filled, ShapeBoolean.regionArea(rings), 1e-6)
        }
    }

    // ── Picker panel law ────────────────────────────────────────────────

    @Test
    fun shapeGridMetrics() {
        val g = SheetGeometry.ShapeGrid
        val d = 2f
        // Sheet 720 px wide: content 720 - 2*12dp*2 = 672; tile (672 - 3*8*2)/4 = 156.
        assertEquals(672f, g.contentWidthPx(720f, d), 0f)
        assertEquals(156f, g.tilePx(672f, d), 0f)
        // Card: tile + 4dp gap + 2 label lines of 20 px = 156 + 8 + 40 = 204.
        assertEquals(204f, g.cardHeightPx(156f, 20f, 2, d), 0f)
        assertEquals(25, g.rowsFor(100))
        assertEquals(0, g.rowsFor(0))
        assertEquals(1, g.rowsFor(4))
        assertEquals(2, g.rowsFor(5))
    }

    @Test
    fun shapeGridGlyphMapping() {
        val g = SheetGeometry.ShapeGrid
        // Glyph box at (10, 20), edge 100: unit -1 -> 10 + 100*(0.5-0.44) = 16,
        // unit 0 -> 60, unit +1 -> 104; same for y with the box's own origin.
        assertEquals(16f, g.glyphX(10f, 100f, -1f), 0f)
        assertEquals(60f, g.glyphX(10f, 100f, 0f), 0f)
        assertEquals(104f, g.glyphX(10f, 100f, 1f), 0f)
        assertEquals(26f, g.glyphY(20f, 100f, -1f), 0f)
        assertEquals(114f, g.glyphY(20f, 100f, 1f), 0f)
    }
}
