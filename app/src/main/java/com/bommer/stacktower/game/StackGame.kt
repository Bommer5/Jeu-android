package com.bommer.stacktower.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Moteur du jeu, indépendant d'Android (testable en JVM pur).
 *
 * Unités « monde » : le terrain fait [FIELD_WIDTH] de large, chaque bloc fait 1 de haut.
 * La ligne 0 est le socle ; l'axe Y monte.
 */
class StackGame(private val random: Random = Random.Default) {

    enum class State { READY, PLAYING, GAME_OVER }
    enum class Placement { PERFECT, CUT, MISS }

    data class Block(val x: Float, val width: Float, val row: Int)

    class Mover(var x: Float, var width: Float, var dir: Float, val row: Int)

    class Debris(
        var x: Float, var y: Float, val width: Float, val row: Int,
        var vx: Float, var vy: Float, var rot: Float, val rotSpeed: Float,
    )

    class Ripple(val x: Float, val width: Float, val row: Int, var age: Float = 0f)

    class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val row: Int, var life: Float, val maxLife: Float,
    )

    var state = State.READY
        private set
    val blocks = ArrayList<Block>()
    var mover: Mover? = null
        private set
    val debris = ArrayList<Debris>()
    val ripples = ArrayList<Ripple>()
    val particles = ArrayList<Particle>()

    var score = 0
        private set
    var combo = 0
        private set
    var perfects = 0
        private set
    var bestCombo = 0
        private set
    var continued = false
        private set

    /** Position (en lignes) de la caméra, lissée. */
    var camY = 0f
        private set

    /** 0 = vue de jeu, 1 = vue d'ensemble de la tour (fin de partie). */
    var overview = 0f
        private set

    /** Temps écoulé, utile pour les animations du rendu. */
    var time = 0f
        private set

    private var spawnFromLeft = true

    val topBlock: Block get() = blocks.last()

    val coinsEarned: Int get() = perfects + score / 5

    val speed: Float get() = min(MAX_SPEED, BASE_SPEED + score * SPEED_STEP)

    init {
        reset()
    }

    fun reset() {
        blocks.clear()
        debris.clear()
        ripples.clear()
        particles.clear()
        blocks += Block((FIELD_WIDTH - START_WIDTH) / 2f, START_WIDTH, 0)
        mover = null
        score = 0
        combo = 0
        perfects = 0
        bestCombo = 0
        continued = false
        spawnFromLeft = true
        camY = 0f
        overview = 0f
        state = State.READY
    }

    fun start() {
        if (state == State.PLAYING) return
        if (state == State.GAME_OVER) reset()
        state = State.PLAYING
        spawnMover()
    }

    /** Le joueur touche l'écran : on pose le bloc en mouvement. */
    fun tap(): Placement? {
        if (state != State.PLAYING) return null
        val m = mover ?: return null
        val prev = topBlock

        val offset = m.x - prev.x
        if (abs(offset) <= PERFECT_TOLERANCE) {
            combo++
            perfects++
            bestCombo = max(bestCombo, combo)
            var width = prev.width
            var x = prev.x
            if (combo >= COMBO_GROW_THRESHOLD && width < START_WIDTH) {
                val grown = min(START_WIDTH, width + GROW_AMOUNT)
                x -= (grown - width) / 2f
                width = grown
            }
            placeBlock(Block(x, width, m.row))
            ripples += Ripple(x, width, m.row)
            burst(x + width / 2f, m.row, 10 + min(combo, 10) * 2)
            return Placement.PERFECT
        }

        val start = max(m.x, prev.x)
        val end = min(m.x + m.width, prev.x + prev.width)
        val overlap = end - start
        combo = 0

        if (overlap <= 0f) {
            debris += Debris(m.x, m.row.toFloat(), m.width, m.row, m.dir * speed * 0.3f, 0f, 0f,
                (random.nextFloat() - 0.5f) * 120f)
            mover = null
            state = State.GAME_OVER
            return Placement.MISS
        }

        // Morceau qui dépasse et tombe.
        if (m.x < prev.x) {
            debris += Debris(m.x, m.row.toFloat(), prev.x - m.x, m.row, -1.5f, 0f, 0f, -90f - random.nextFloat() * 90f)
        } else {
            debris += Debris(end, m.row.toFloat(), m.x + m.width - end, m.row, 1.5f, 0f, 0f, 90f + random.nextFloat() * 90f)
        }
        placeBlock(Block(start, overlap, m.row))
        return Placement.CUT
    }

    /** Relance la partie après une vidéo récompensée (une seule fois par partie). */
    fun continueRun(): Boolean {
        if (state != State.GAME_OVER || continued) return false
        continued = true
        state = State.PLAYING
        spawnMover()
        return true
    }

    val canContinue: Boolean get() = state == State.GAME_OVER && !continued

    private fun placeBlock(b: Block) {
        blocks += b
        score++
        spawnMover()
    }

    private fun spawnMover() {
        val top = topBlock
        val w = top.width
        val x = if (spawnFromLeft) MIN_X else maxX(w)
        mover = Mover(x, w, if (spawnFromLeft) 1f else -1f, top.row + 1)
        spawnFromLeft = !spawnFromLeft
    }

    private fun maxX(width: Float) = FIELD_WIDTH - width - MIN_X

    private fun burst(cx: Float, row: Int, count: Int) {
        repeat(count) {
            val a = random.nextFloat() * Math.PI.toFloat() * 2f
            val s = 2f + random.nextFloat() * 5f
            val life = 0.5f + random.nextFloat() * 0.5f
            particles += Particle(cx, row + 0.5f, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s + 2f, row, life, life)
        }
    }

    fun update(dtRaw: Float) {
        val dt = min(dtRaw, 1f / 20f)
        time += dt

        mover?.let { m ->
            if (state == State.PLAYING) {
                m.x += m.dir * speed * dt
                val hi = maxX(m.width)
                if (m.x > hi) { m.x = hi; m.dir = -1f }
                if (m.x < MIN_X) { m.x = MIN_X; m.dir = 1f }
            }
        }

        val it = debris.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.vy -= GRAVITY * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.rot += d.rotSpeed * dt
            if (d.y < camY - 30f) it.remove()
        }
        ripples.removeAll { r -> r.age += dt; r.age > RIPPLE_DURATION }
        particles.removeAll { p ->
            p.life -= dt
            p.vy -= GRAVITY * 0.35f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life <= 0f
        }

        val targetCam = topBlock.row.toFloat()
        camY += (targetCam - camY) * min(1f, dt * 6f)
        val targetOverview = if (state == State.GAME_OVER) 1f else 0f
        overview += (targetOverview - overview) * min(1f, dt * 2.5f)
    }

    companion object {
        const val FIELD_WIDTH = 10f
        const val START_WIDTH = 6f
        const val MIN_X = -2.5f
        const val PERFECT_TOLERANCE = 0.15f
        const val COMBO_GROW_THRESHOLD = 3
        const val GROW_AMOUNT = 0.25f
        const val BASE_SPEED = 5.5f
        const val SPEED_STEP = 0.12f
        const val MAX_SPEED = 13f
        const val GRAVITY = 30f
        const val RIPPLE_DURATION = 0.6f
    }
}
