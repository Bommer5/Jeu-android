package com.bommer.autoclicker

import kotlin.math.hypot

/** Contour fermé tracé au doigt (coordonnées écran). */
class Polygon(val points: List<Tap>) {

    val bounds: Area = if (points.isEmpty()) Area(0f, 0f, 0f, 0f) else Area(
        points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
    )

    val isValid: Boolean get() = points.size >= 3 && bounds.width >= 10f && bounds.height >= 10f

    /** Test du point dans le polygone (lancer de rayon, règle pair-impair). */
    fun contains(x: Float, y: Float): Boolean {
        if (!isValid || !bounds.contains(x, y)) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    fun serialize(): String = points.joinToString(";") { "${it.x.toInt()},${it.y.toInt()}" }

    companion object {
        fun parse(s: String?): Polygon? {
            if (s.isNullOrBlank()) return null
            val pts = s.split(";").mapNotNull { p ->
                val xy = p.split(",")
                if (xy.size == 2) xy[0].toFloatOrNull()?.let { x -> xy[1].toFloatOrNull()?.let { y -> Tap(x, y) } } else null
            }
            return Polygon(pts).takeIf { it.isValid }
        }

        /** Allège un tracé : garde un point tous les [minDist] pixels (au plus [maxPoints] points). */
        fun simplify(raw: List<Tap>, minDist: Float, maxPoints: Int = 400): List<Tap> {
            if (raw.size < 3) return raw
            val out = ArrayList<Tap>()
            out += raw.first()
            for (p in raw) {
                val last = out.last()
                if (hypot(p.x - last.x, p.y - last.y) >= minDist) out += p
            }
            if (out.size > maxPoints) {
                val step = out.size.toFloat() / maxPoints
                return List(maxPoints) { out[(it * step).toInt()] }
            }
            return out
        }
    }
}
