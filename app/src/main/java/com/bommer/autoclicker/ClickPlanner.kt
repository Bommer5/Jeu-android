package com.bommer.autoclicker

import kotlin.math.max
import kotlin.random.Random

/** Rectangle en pixels écran (indépendant d'Android pour pouvoir être testé). */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

data class Tap(val x: Float, val y: Float)

/**
 * Choisit où cliquer dans le cadre à chaque cycle.
 *  - [ZonePattern.RANDOM] : positions aléatoires uniformes dans le cadre ;
 *  - [ZonePattern.GRID] : balayage de tout le cadre selon une grille (ligne par ligne, en serpentin),
 *    pour que chaque endroit du cadre finisse par être cliqué.
 * Les points tombant dans une zone exclue (le panneau de contrôle) sont évités.
 */
class ClickPlanner(private val random: Random = Random.Default) {

    private var gridIndex = 0

    fun reset() {
        gridIndex = 0
    }

    fun next(
        zone: Area,
        pattern: ZonePattern,
        count: Int,
        stepPx: Float,
        exclude: List<Area> = emptyList(),
    ): List<Tap> {
        if (zone.width <= 1f || zone.height <= 1f) return emptyList()
        val n = count.coerceIn(1, MAX_TAPS)
        return when (pattern) {
            ZonePattern.RANDOM -> randomTaps(zone, n, exclude)
            ZonePattern.GRID -> gridTaps(zone, n, stepPx, exclude)
        }
    }

    private fun excluded(x: Float, y: Float, exclude: List<Area>) = exclude.any { it.contains(x, y) }

    private fun randomTaps(zone: Area, n: Int, exclude: List<Area>): List<Tap> {
        val out = ArrayList<Tap>(n)
        repeat(n) {
            for (attempt in 0 until 25) {
                val x = zone.left + MARGIN + random.nextFloat() * (zone.width - 2 * MARGIN)
                val y = zone.top + MARGIN + random.nextFloat() * (zone.height - 2 * MARGIN)
                if (!excluded(x, y, exclude)) {
                    out += Tap(x, y)
                    break
                }
            }
        }
        return out
    }

    /** Nombre de colonnes / lignes de la grille. */
    fun gridSize(zone: Area, stepPx: Float): Pair<Int, Int> {
        val step = max(stepPx, 4f)
        val cols = max(1, (zone.width / step).toInt())
        val rows = max(1, (zone.height / step).toInt())
        return cols to rows
    }

    private fun gridTaps(zone: Area, n: Int, stepPx: Float, exclude: List<Area>): List<Tap> {
        val (cols, rows) = gridSize(zone, stepPx)
        val total = cols * rows
        val cellW = zone.width / cols
        val cellH = zone.height / rows
        val out = ArrayList<Tap>(n)
        var scanned = 0
        while (out.size < n && scanned < total) {
            val i = gridIndex % total
            gridIndex = (gridIndex + 1) % total
            scanned++
            val r = i / cols
            val cInRow = i % cols
            val c = if (r % 2 == 0) cInRow else cols - 1 - cInRow // serpentin
            val x = zone.left + (c + 0.5f) * cellW
            val y = zone.top + (r + 0.5f) * cellH
            if (!excluded(x, y, exclude)) out += Tap(x, y)
        }
        return out
    }

    companion object {
        /** Limite Android du nombre de doigts simultanés dans un geste. */
        const val MAX_TAPS = 10
        private const val MARGIN = 2f
    }
}
