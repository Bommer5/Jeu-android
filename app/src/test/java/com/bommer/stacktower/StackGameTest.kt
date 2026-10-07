package com.bommer.stacktower

import com.bommer.stacktower.game.StackGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StackGameTest {

    private fun game() = StackGame(Random(1)).apply { start() }

    /** Avance le jeu jusqu'à ce que le bloc mobile soit aligné sur le dessus de la tour. */
    private fun StackGame.alignMover(offset: Float = 0f) {
        mover!!.x = topBlock.x + offset
    }

    @Test
    fun perfectPlacementKeepsWidthAndCountsCombo() {
        val g = game()
        repeat(2) {
            g.alignMover(0.05f)
            assertEquals(StackGame.Placement.PERFECT, g.tap())
        }
        assertEquals(2, g.score)
        assertEquals(2, g.combo)
        assertEquals(StackGame.START_WIDTH, g.topBlock.width, 1e-4f)
    }

    @Test
    fun offsetPlacementTrimsBlock() {
        val g = game()
        g.alignMover(1.5f)
        assertEquals(StackGame.Placement.CUT, g.tap())
        assertEquals(StackGame.START_WIDTH - 1.5f, g.topBlock.width, 1e-4f)
        assertEquals(0, g.combo)
        assertEquals(1, g.debris.size)
    }

    @Test
    fun comboGrowsBlockBack() {
        val g = game()
        g.alignMover(2f)
        g.tap()
        val narrow = g.topBlock.width
        repeat(3) {
            g.alignMover()
            g.tap()
        }
        assertTrue(g.topBlock.width > narrow)
    }

    @Test
    fun missEndsGameAndCanContinueOnce() {
        val g = game()
        g.alignMover(10f)
        assertEquals(StackGame.Placement.MISS, g.tap())
        assertEquals(StackGame.State.GAME_OVER, g.state)
        assertTrue(g.continueRun())
        assertEquals(StackGame.State.PLAYING, g.state)
        g.alignMover(-10f)
        g.tap()
        assertFalse(g.continueRun())
    }

    @Test
    fun moverStaysWithinBounds() {
        val g = game()
        repeat(2000) { g.update(1f / 60f) }
        val m = g.mover!!
        assertTrue(m.x >= StackGame.MIN_X - 1e-3f)
        assertTrue(m.x + m.width <= StackGame.FIELD_WIDTH - StackGame.MIN_X + 1e-3f)
    }
}
