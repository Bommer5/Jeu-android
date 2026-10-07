package com.bommer.stacktower.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Modes de jeu. [unlockLevel] : niveau joueur requis. */
enum class GameMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val unlockLevel: Int,
    val baseSpeed: Float,
    val speedStep: Float,
    val maxSpeed: Float,
    val tolerance: Float,
    val canGrow: Boolean,
    val coinMultiplier: Float,
) {
    CLASSIC("classic", "Classique", "Monte le plus haut possible", 1, 5.0f, 0.10f, 12f, 0.16f, true, 1f),
    DAILY("daily", "Défi du jour", "La même tour pour tout le monde", 1, 5.0f, 0.10f, 12f, 0.16f, true, 1.5f),
    ZEN("zen", "Zen", "Sans pression, aucune fin", 1, 4.0f, 0f, 4f, 0.20f, true, 0.5f),
    CHRONO("chrono", "Chrono", "60 secondes, un max de blocs", 3, 6.0f, 0.08f, 12f, 0.16f, true, 1.2f),
    HARD("hard", "Expert", "Plus rapide, aucune marge", 6, 7.5f, 0.12f, 15f, 0.08f, false, 2f);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Bonus achetables avec des pièces, consommés au lancement d'une partie. */
enum class Booster(val id: String, val title: String, val description: String, val price: Int) {
    SLOW("slow", "Ralenti", "Blocs 30 % plus lents pendant 25 étages", 120),
    SHIELD("shield", "Bouclier", "Annule ta première erreur", 180),
    HEAD_START("head", "Départ lancé", "Commence directement à 10 étages", 150);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class Axis { X, Z }

/**
 * Moteur du jeu en 3D isométrique, indépendant d'Android (testable en JVM pur).
 *
 * Repère monde : X et Z au sol, chaque bloc fait 1 de haut ; la ligne 0 est le socle.
 */
class StackGame(
    val mode: GameMode = GameMode.CLASSIC,
    seed: Long = Random.nextLong(),
    boosters: Set<Booster> = emptySet(),
) {
    enum class State { READY, PLAYING, PAUSED, GAME_OVER }
    enum class Placement { PERFECT, CUT, MISS, SAVED }

    enum class EventType { PLACED, PERFECT, MISS, SAVED, MILESTONE, TIME_UP, GAME_OVER }
    data class Event(val type: EventType, val value: Int = 0)

    data class Block(val x: Float, val z: Float, val w: Float, val d: Float, val row: Int)

    class Mover(
        var x: Float, var z: Float, val w: Float, val d: Float,
        val axis: Axis, var dir: Float, val row: Int, val speedFactor: Float,
    )

    class Debris(
        val x0: Float, val z0: Float, val w: Float, val d: Float, val row: Int,
        var dx: Float = 0f, var dz: Float = 0f, var y: Float = row.toFloat(),
        var vx: Float = 0f, var vz: Float = 0f, var vy: Float = 0f, var alpha: Float = 1f,
    )

    class Ripple(val block: Block, var age: Float = 0f)

    class Particle(
        var x: Float, var y: Float, var z: Float,
        var vx: Float, var vy: Float, var vz: Float,
        var life: Float, val maxLife: Float, val row: Int,
    )

    private val random = Random(seed)
    private val boosters = boosters.toMutableSet()

    var state = State.READY
        private set
    val blocks = ArrayList<Block>()
    var mover: Mover? = null
        private set
    val debris = ArrayList<Debris>()
    val ripples = ArrayList<Ripple>()
    val particles = ArrayList<Particle>()
    val events = ArrayDeque<Event>()

    var score = 0
        private set
    var combo = 0
        private set
    var perfects = 0
        private set
    var bestCombo = 0
        private set
    var misses = 0
        private set
    var continued = false
        private set
    var shields = if (Booster.SHIELD in boosters) 1 else 0
        private set

    /** Temps restant (mode Chrono uniquement). */
    var timeLeft = if (mode == GameMode.CHRONO) CHRONO_TIME else 0f
        private set

    var time = 0f
        private set
    var playTime = 0f
        private set

    // Caméra lissée
    var camY = 0f
        private set
    var camX = 0f
        private set
    var camZ = 0f
        private set
    /** 0 = vue de jeu, 1 = vue d'ensemble de la tour (fin de partie). */
    var overview = 0f
        private set
    var shake = 0f
        private set
    var flash = 0f
        private set

    val topBlock: Block get() = blocks.last()

    val coinsEarned: Int get() = ((perfects + score / 5) * mode.coinMultiplier).toInt()
    val xpEarned: Int get() = score + perfects * 2

    val canContinue: Boolean
        get() = state == State.GAME_OVER && !continued && mode != GameMode.ZEN

    private val headStart = Booster.HEAD_START in boosters

    init {
        val start = -START_SIZE / 2f
        blocks += Block(start, start, START_SIZE, START_SIZE, 0)
        if (headStart) {
            for (r in 1..HEAD_START_ROWS) blocks += Block(start, start, START_SIZE, START_SIZE, r)
            score = HEAD_START_ROWS
        }
        camY = topBlock.row.toFloat()
    }

    fun start() {
        if (state != State.READY) return
        state = State.PLAYING
        spawnMover()
    }

    fun pause() {
        if (state == State.PLAYING) state = State.PAUSED
    }

    fun resume() {
        if (state == State.PAUSED) state = State.PLAYING
    }

    /** Vitesse actuelle du bloc mobile (unités/s). */
    fun currentSpeed(m: Mover? = mover): Float {
        var s = min(mode.maxSpeed, mode.baseSpeed + score * mode.speedStep)
        if (m != null) s *= m.speedFactor
        if (Booster.SLOW in boosters && score < SLOW_ROWS) s *= 0.7f
        return s
    }

    /** Le joueur touche l'écran : on pose le bloc en mouvement. */
    fun tap(): Placement? {
        if (state != State.PLAYING) return null
        val m = mover ?: return null
        val prev = topBlock
        val delta = if (m.axis == Axis.X) m.x - prev.x else m.z - prev.z

        if (abs(delta) <= mode.tolerance) {
            combo++
            perfects++
            bestCombo = max(bestCombo, combo)
            var x = prev.x
            var z = prev.z
            var w = prev.w
            var d = prev.d
            if (mode.canGrow && combo >= COMBO_GROW_THRESHOLD) {
                if (m.axis == Axis.X && w < START_SIZE) {
                    val g = min(START_SIZE, w + GROW_AMOUNT); x -= (g - w) / 2f; w = g
                } else if (m.axis == Axis.Z && d < START_SIZE) {
                    val g = min(START_SIZE, d + GROW_AMOUNT); z -= (g - d) / 2f; d = g
                }
            }
            val b = Block(x, z, w, d, m.row)
            if (mode == GameMode.CHRONO) timeLeft = min(CHRONO_TIME, timeLeft + 1f)
            flash = min(1f, 0.35f + combo * 0.1f)
            ripples += Ripple(b)
            burst(b, 14 + min(combo, 12) * 3)
            events += Event(EventType.PERFECT, combo)
            place(b)
            return Placement.PERFECT
        }

        combo = 0
        val size = if (m.axis == Axis.X) m.w else m.d
        val overlap = size - abs(delta)

        if (overlap <= 0f) {
            debris += Debris(m.x, m.z, m.w, m.d, m.row, vx = if (m.axis == Axis.X) m.dir * 2f else 0f,
                vz = if (m.axis == Axis.Z) m.dir * 2f else 0f)
            misses++
            shake = 1f
            val forgiven = mode == GameMode.ZEN || mode == GameMode.CHRONO || shields > 0
            if (forgiven) {
                if (mode != GameMode.ZEN && mode != GameMode.CHRONO) shields--
                if (mode == GameMode.CHRONO) timeLeft = max(0f, timeLeft - CHRONO_PENALTY)
                events += Event(EventType.SAVED)
                spawnMover()
                return Placement.SAVED
            }
            mover = null
            state = State.GAME_OVER
            events += Event(EventType.MISS)
            events += Event(EventType.GAME_OVER)
            return Placement.MISS
        }

        val placed: Block
        if (m.axis == Axis.X) {
            val nx = max(m.x, prev.x)
            placed = Block(nx, prev.z, overlap, prev.d, m.row)
            if (delta > 0) debris += Debris(nx + overlap, prev.z, delta, prev.d, m.row, vx = 1.5f)
            else debris += Debris(m.x, prev.z, -delta, prev.d, m.row, vx = -1.5f)
        } else {
            val nz = max(m.z, prev.z)
            placed = Block(prev.x, nz, prev.w, overlap, m.row)
            if (delta > 0) debris += Debris(prev.x, nz + overlap, prev.w, delta, m.row, vz = 1.5f)
            else debris += Debris(prev.x, m.z, prev.w, -delta, m.row, vz = -1.5f)
        }
        events += Event(EventType.PLACED)
        place(placed)
        return Placement.CUT
    }

    /** Relance la partie après une vidéo récompensée (une seule fois par partie). */
    fun continueRun(): Boolean {
        if (!canContinue) return false
        continued = true
        if (mode == GameMode.CHRONO) timeLeft = CONTINUE_TIME
        state = State.PLAYING
        spawnMover()
        return true
    }

    /** Termine volontairement une partie (Zen). */
    fun finish() {
        if (state == State.GAME_OVER) return
        mover = null
        state = State.GAME_OVER
        events += Event(EventType.GAME_OVER)
    }

    private fun place(b: Block) {
        blocks += b
        score++
        if (score % 10 == 0) events += Event(EventType.MILESTONE, score)
        spawnMover()
    }

    private fun spawnMover() {
        val top = topBlock
        val row = top.row + 1
        val axis = if (row % 2 == 1) Axis.X else Axis.Z
        val fromNeg = (row / 2) % 2 == 0
        val offset = if (fromNeg) -RANGE else RANGE
        val factor = if (mode == GameMode.DAILY) 0.8f + random.nextFloat() * 0.55f else 1f
        mover = Mover(
            x = if (axis == Axis.X) top.x + offset else top.x,
            z = if (axis == Axis.Z) top.z + offset else top.z,
            w = top.w, d = top.d, axis = axis, dir = if (fromNeg) 1f else -1f, row = row, speedFactor = factor,
        )
    }

    private fun burst(b: Block, count: Int) {
        val cx = b.x + b.w / 2f
        val cz = b.z + b.d / 2f
        repeat(count) {
            val a = random.nextFloat() * PI2
            val s = 1.5f + random.nextFloat() * 4f
            val life = 0.5f + random.nextFloat() * 0.6f
            // Les particules partent des bords du bloc.
            val ex = cx + cos(a) * b.w / 2f
            val ez = cz + sin(a) * b.d / 2f
            particles += Particle(ex, b.row + 1f, ez, cos(a) * s, 2f + random.nextFloat() * 4f, sin(a) * s, life, life, b.row)
        }
    }

    fun update(dtRaw: Float) {
        val dt = min(dtRaw, 1f / 20f)
        time += dt

        if (state == State.PLAYING) {
            playTime += dt
            mover?.let { m ->
                val top = topBlock
                val base = if (m.axis == Axis.X) top.x else top.z
                var pos = (if (m.axis == Axis.X) m.x else m.z) + m.dir * currentSpeed(m) * dt
                if (pos > base + RANGE) { pos = base + RANGE; m.dir = -1f }
                if (pos < base - RANGE) { pos = base - RANGE; m.dir = 1f }
                if (m.axis == Axis.X) m.x = pos else m.z = pos
            }
            if (mode == GameMode.CHRONO) {
                timeLeft -= dt
                if (timeLeft <= 0f) {
                    timeLeft = 0f
                    mover = null
                    state = State.GAME_OVER
                    events += Event(EventType.TIME_UP)
                    events += Event(EventType.GAME_OVER)
                }
            }
        }

        val it = debris.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.vy -= GRAVITY * dt
            d.y += d.vy * dt
            d.dx += d.vx * dt
            d.dz += d.vz * dt
            if (d.y < d.row - 6f) d.alpha -= dt * 2f
            if (d.alpha <= 0f) it.remove()
        }
        ripples.removeAll { r -> r.age += dt; r.age > RIPPLE_DURATION }
        particles.removeAll { p ->
            p.life -= dt
            p.vy -= GRAVITY * 0.3f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.life <= 0f
        }

        val top = topBlock
        val k = min(1f, dt * 5f)
        camY += (top.row - camY) * k
        camX += (top.x + top.w / 2f - camX) * k
        camZ += (top.z + top.d / 2f - camZ) * k
        val targetOverview = if (state == State.GAME_OVER) 1f else 0f
        overview += (targetOverview - overview) * min(1f, dt * 2f)
        shake = max(0f, shake - dt * 3f)
        flash = max(0f, flash - dt * 2.5f)
    }

    companion object {
        const val START_SIZE = 5f
        const val RANGE = 7f
        const val COMBO_GROW_THRESHOLD = 3
        const val GROW_AMOUNT = 0.3f
        const val GRAVITY = 30f
        const val RIPPLE_DURATION = 0.7f
        const val CHRONO_TIME = 60f
        const val CHRONO_PENALTY = 5f
        const val CONTINUE_TIME = 15f
        const val SLOW_ROWS = 25
        const val HEAD_START_ROWS = 10
        private const val PI2 = (Math.PI * 2).toFloat()
    }
}
