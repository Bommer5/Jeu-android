package com.bommer.autoclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClickPlannerTest {

    private val zone = Area(100f, 200f, 500f, 600f)

    @Test
    fun randomTapsStayInsideZone() {
        val p = ClickPlanner(Random(1))
        repeat(500) {
            p.next(zone, ZonePattern.RANDOM, 10, 50f).forEach { t -> assertTrue(zone.contains(t.x, t.y)) }
        }
    }

    @Test
    fun randomTapsCoverTheWholeZone() {
        val p = ClickPlanner(Random(2))
        val taps = (0 until 2000).flatMap { p.next(zone, ZonePattern.RANDOM, 1, 50f) }
        // Chaque quart du cadre reçoit des clics.
        val midX = (zone.left + zone.right) / 2
        val midY = (zone.top + zone.bottom) / 2
        assertTrue(taps.any { it.x < midX && it.y < midY })
        assertTrue(taps.any { it.x > midX && it.y < midY })
        assertTrue(taps.any { it.x < midX && it.y > midY })
        assertTrue(taps.any { it.x > midX && it.y > midY })
    }

    @Test
    fun gridVisitsEveryCellOncePerSweep() {
        val p = ClickPlanner()
        val (cols, rows) = p.gridSize(zone, 100f)
        assertEquals(4, cols)
        assertEquals(4, rows)
        val sweep = (0 until cols * rows).flatMap { p.next(zone, ZonePattern.GRID, 1, 100f) }
        assertEquals(16, sweep.toSet().size)
        sweep.forEach { assertTrue(zone.contains(it.x, it.y)) }
        // Le balayage recommence au début
        assertEquals(sweep.first(), p.next(zone, ZonePattern.GRID, 1, 100f).first())
    }

    @Test
    fun multipleSimultaneousTapsAreCappedAtTen() {
        val p = ClickPlanner()
        assertEquals(ClickPlanner.MAX_TAPS, p.next(zone, ZonePattern.GRID, 50, 20f).size)
        assertEquals(ClickPlanner.MAX_TAPS, p.next(zone, ZonePattern.RANDOM, 50, 20f).size)
    }

    @Test
    fun excludedAreaIsNeverClicked() {
        val panel = Area(100f, 200f, 300f, 600f) // moitié gauche
        val p = ClickPlanner(Random(3))
        repeat(300) {
            p.next(zone, ZonePattern.RANDOM, 5, 40f, listOf(panel)).forEach { assertTrue(!panel.contains(it.x, it.y)) }
            p.next(zone, ZonePattern.GRID, 5, 40f, listOf(panel)).forEach { assertTrue(!panel.contains(it.x, it.y)) }
        }
    }

    @Test
    fun degenerateZoneGivesNoTaps() {
        assertTrue(ClickPlanner().next(Area(0f, 0f, 0f, 0f), ZonePattern.RANDOM, 3, 10f).isEmpty())
    }
}
