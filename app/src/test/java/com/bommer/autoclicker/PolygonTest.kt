package com.bommer.autoclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PolygonTest {

    /** Triangle : sommet en haut au centre, base en bas. */
    private val triangle = Polygon(listOf(Tap(200f, 0f), Tap(400f, 400f), Tap(0f, 400f)))

    /** Forme en « L » (concave). */
    private val lShape = Polygon(
        listOf(Tap(0f, 0f), Tap(100f, 0f), Tap(100f, 300f), Tap(300f, 300f), Tap(300f, 400f), Tap(0f, 400f))
    )

    @Test
    fun containsInsideAndOutside() {
        assertTrue(triangle.contains(200f, 300f))
        assertFalse(triangle.contains(20f, 20f))   // coin haut gauche de la boîte, hors triangle
        assertFalse(triangle.contains(500f, 200f))
        assertTrue(lShape.contains(50f, 50f))
        assertTrue(lShape.contains(250f, 350f))
        assertFalse(lShape.contains(250f, 100f))   // creux du L
    }

    @Test
    fun randomTapsStayInsideFreeShape() {
        val p = ClickPlanner(Random(4))
        repeat(400) {
            val taps = p.next(lShape, ZonePattern.RANDOM, 10, 20f)
            assertEquals(10, taps.size)
            taps.forEach { assertTrue(lShape.contains(it.x, it.y)) }
        }
    }

    @Test
    fun gridSweepOnlyHitsInsideFreeShape() {
        val p = ClickPlanner()
        val taps = (0 until 200).flatMap { p.next(triangle, ZonePattern.GRID, 1, 25f) }
        assertTrue(taps.isNotEmpty())
        taps.forEach { assertTrue(triangle.contains(it.x, it.y)) }
    }

    @Test
    fun serializeRoundTrip() {
        val back = Polygon.parse(lShape.serialize())
        assertNotNull(back)
        assertEquals(lShape.points.size, back!!.points.size)
        assertNull(Polygon.parse(""))
        assertNull(Polygon.parse("1,1;2,2"))
    }

    @Test
    fun simplifyDropsClosePoints() {
        val raw = (0..1000).map { Tap(it.toFloat(), 0f) }
        val s = Polygon.simplify(raw, 10f)
        assertTrue(s.size in 95..105)
        assertTrue(Polygon.simplify(raw, 0.5f, maxPoints = 50).size <= 50)
    }
}
