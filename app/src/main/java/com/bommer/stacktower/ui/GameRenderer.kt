package com.bommer.stacktower.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.bommer.stacktower.game.GameTheme
import com.bommer.stacktower.game.StackGame
import kotlin.math.max
import kotlin.math.min

/** Dessine la tour, les débris et les effets dans un Canvas Compose. */
object GameRenderer {

    fun blockColor(theme: GameTheme, row: Int): Color {
        val hue = ((theme.hueStart + row * theme.hueStep) % 360f + 360f) % 360f
        val value = if (theme.saturation == 0f) {
            // Thème monochrome : alternance de gris.
            theme.value + (row % 6) * 0.08f
        } else theme.value
        return Color.hsv(hue, theme.saturation, value.coerceIn(0f, 1f))
    }

    fun DrawScope.drawGame(game: StackGame, theme: GameTheme) {
        val w = size.width
        val h = size.height

        // Fond en dégradé qui évolue légèrement avec la hauteur.
        val shift = (min(game.camY, 200f) / 200f) * 0.25f
        val top = lerp(Color(theme.bgTop), Color.Black, shift)
        val bottom = Color(theme.bgBottom)
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val baseUnit = w / (StackGame.FIELD_WIDTH + 3f)
        val rows = game.topBlock.row + 4f
        val fitUnit = min(baseUnit, h * 0.6f / rows)
        val unit = baseUnit + (fitUnit - baseUnit) * game.overview
        val anchorY = h * (0.6f + 0.2f * game.overview)
        val camY = game.camY * (1f - game.overview)
        val originX = (w - StackGame.FIELD_WIDTH * unit) / 2f
        val depth = unit * 0.35f

        fun sx(x: Float) = originX + x * unit
        fun sy(row: Float) = anchorY - (row - camY) * unit

        // Socle : colonne qui descend jusqu'en bas de l'écran.
        val base = game.blocks.first()
        val baseTop = sy(0f)
        if (baseTop < h) {
            val c = blockColor(theme, 0)
            drawRect(
                Brush.verticalGradient(listOf(c.darken(0.75f), c.darken(0.35f)), startY = baseTop, endY = h),
                topLeft = Offset(sx(base.x), baseTop + unit),
                size = Size(base.width * unit, max(0f, h - baseTop - unit)),
            )
        }

        // Ne dessiner que les blocs visibles.
        for (b in game.blocks) {
            val y = sy(b.row + 1f)
            if (y > h + unit || y < -unit * 2) continue
            drawBlock(sx(b.x), y, b.width * unit, unit, depth, blockColor(theme, b.row))
        }

        game.mover?.let { m ->
            drawBlock(sx(m.x), sy(m.row + 1f), m.width * unit, unit, depth, blockColor(theme, m.row))
        }

        for (d in game.debris) {
            val bw = d.width * unit
            val x = sx(d.x)
            val y = sy(d.y + 1f)
            rotate(d.rot, pivot = Offset(x + bw / 2f, y + unit / 2f)) {
                drawBlock(x, y, bw, unit, depth, blockColor(theme, d.row).copy(alpha = 0.9f))
            }
        }

        for (r in game.ripples) {
            val t = r.age / StackGame.RIPPLE_DURATION
            val grow = unit * 0.9f * t
            drawRoundRect(
                color = Color.White.copy(alpha = (1f - t) * 0.8f),
                topLeft = Offset(sx(r.x) - grow, sy(r.row + 1f) - grow - depth),
                size = Size(r.width * unit + grow * 2, unit + depth + grow * 2),
                cornerRadius = CornerRadius(unit * 0.15f + grow * 0.2f),
                style = Stroke(width = unit * 0.12f * (1f - t) + 1f),
            )
        }

        for (p in game.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(
                color = blockColor(theme, p.row + 3).copy(alpha = a),
                radius = unit * 0.12f * (0.4f + a),
                center = Offset(sx(p.x), sy(p.y)),
            )
        }
    }

    /** Bloc en pseudo-3D : face avant + dessus plus clair. */
    private fun DrawScope.drawBlock(x: Float, y: Float, w: Float, h: Float, depth: Float, color: Color) {
        if (w <= 0f) return
        val r = CornerRadius(min(h, w) * 0.12f)
        // Ombre douce
        drawRoundRect(Color.Black.copy(alpha = 0.18f * color.alpha), Offset(x + h * 0.08f, y + h * 0.12f), Size(w, h), r)
        // Dessus
        drawRoundRect(color.lighten(0.25f), Offset(x, y - depth), Size(w, depth + h * 0.5f), r)
        // Face avant
        drawRoundRect(
            Brush.verticalGradient(listOf(color, color.darken(0.8f)), startY = y, endY = y + h),
            Offset(x, y), Size(w, h), r,
        )
        // Reflet
        drawRect(Color.White.copy(alpha = 0.12f * color.alpha), Offset(x + w * 0.04f, y + h * 0.12f), Size(w * 0.92f, h * 0.1f))
    }

    private fun lerp(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        a.alpha + (b.alpha - a.alpha) * t,
    )

    private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
    private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
}
