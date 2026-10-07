package com.bommer.stacktower

import com.bommer.stacktower.game.Axis
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.StackGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackGameTest {

    private fun game(mode: GameMode = GameMode.CLASSIC, boosters: Set<Booster> = emptySet()) =
        StackGame(mode, seed = 1, boosters = boosters).apply { start() }

    /** Place le bloc mobile à [offset] du dessus de la tour, sur son axe de déplacement. */
    private fun StackGame.align(offset: Float = 0f) {
        val m = mover!!
        if (m.axis == Axis.X) m.x = topBlock.x + offset else m.z = topBlock.z + offset
    }

    @Test
    fun axesAlternate() {
        val g = game()
        val first = g.mover!!.axis
        g.align(); g.tap()
        assertTrue(first != g.mover!!.axis)
    }

    @Test
    fun perfectKeepsSizeAndCountsCombo() {
        val g = game()
        repeat(2) {
            g.align(0.05f)
            assertEquals(StackGame.Placement.PERFECT, g.tap())
        }
        assertEquals(2, g.score)
        assertEquals(2, g.combo)
        assertEquals(StackGame.START_SIZE, g.topBlock.w, 1e-4f)
        assertEquals(StackGame.START_SIZE, g.topBlock.d, 1e-4f)
    }

    @Test
    fun offsetTrimsAlongMovingAxisOnly() {
        val g = game()
        val axis = g.mover!!.axis
        g.align(1.5f)
        assertEquals(StackGame.Placement.CUT, g.tap())
        val b = g.topBlock
        if (axis == Axis.X) {
            assertEquals(3.5f, b.w, 1e-4f); assertEquals(5f, b.d, 1e-4f)
        } else {
            assertEquals(3.5f, b.d, 1e-4f); assertEquals(5f, b.w, 1e-4f)
        }
        assertEquals(1, g.debris.size)
        assertEquals(0, g.combo)
    }

    @Test
    fun comboGrowsBlockBack() {
        val g = game()
        g.align(2f); g.tap()
        g.align(2f); g.tap()
        val area = g.topBlock.w * g.topBlock.d
        repeat(4) { g.align(); g.tap() }
        assertTrue(g.topBlock.w * g.topBlock.d > area)
    }

    @Test
    fun missEndsClassicAndAllowsOneContinue() {
        val g = game()
        g.align(10f)
        assertEquals(StackGame.Placement.MISS, g.tap())
        assertEquals(StackGame.State.GAME_OVER, g.state)
        assertTrue(g.continueRun())
        g.align(-10f)
        g.tap()
        assertFalse(g.continueRun())
    }

    @Test
    fun shieldSavesFirstMiss() {
        val g = game(boosters = setOf(Booster.SHIELD))
        g.align(10f)
        assertEquals(StackGame.Placement.SAVED, g.tap())
        assertEquals(StackGame.State.PLAYING, g.state)
        g.align(10f)
        assertEquals(StackGame.Placement.MISS, g.tap())
    }

    @Test
    fun zenNeverEnds() {
        val g = game(GameMode.ZEN)
        repeat(5) { g.align(10f); assertEquals(StackGame.Placement.SAVED, g.tap()) }
        assertEquals(StackGame.State.PLAYING, g.state)
        assertFalse(g.canContinue)
    }

    @Test
    fun chronoEndsWhenTimeRunsOut() {
        val g = game(GameMode.CHRONO)
        repeat(61 * 20) { g.update(1f / 20f) }
        assertEquals(StackGame.State.GAME_OVER, g.state)
        assertTrue(g.events.any { it.type == StackGame.EventType.TIME_UP })
    }

    @Test
    fun headStartBeginsHigher() {
        val g = game(boosters = setOf(Booster.HEAD_START))
        assertEquals(StackGame.HEAD_START_ROWS, g.score)
        assertEquals(StackGame.HEAD_START_ROWS + 1, g.mover!!.row)
    }

    @Test
    fun pauseFreezesMover() {
        val g = game()
        g.pause()
        val before = g.mover!!.x to g.mover!!.z
        repeat(30) { g.update(1f / 60f) }
        assertEquals(before, g.mover!!.x to g.mover!!.z)
        assertEquals(null, g.tap())
        g.resume()
        repeat(30) { g.update(1f / 60f) }
        assertTrue(before != (g.mover!!.x to g.mover!!.z))
    }

    @Test
    fun moverStaysInRange() {
        val g = game()
        repeat(3000) { g.update(1f / 60f) }
        val m = g.mover!!
        val pos = if (m.axis == Axis.X) m.x - g.topBlock.x else m.z - g.topBlock.z
        assertTrue(kotlin.math.abs(pos) <= StackGame.RANGE + 1e-3f)
    }
}
