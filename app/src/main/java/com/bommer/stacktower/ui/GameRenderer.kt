package com.bommer.stacktower.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.bommer.stacktower.game.BackgroundFx
import com.bommer.stacktower.game.BlockSkin
import com.bommer.stacktower.game.GameTheme
import com.bommer.stacktower.game.StackGame
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Options de rendu choisies par le joueur. */
data class RenderOptions(
    val theme: GameTheme,
    val skin: BlockSkin,
    val fx: BackgroundFx,
    val particles: Boolean,
    val screenShake: Boolean,
    /** Étage du record à matérialiser (0 = aucun). */
    val bestRow: Int,
    val typeface: Typeface? = null,
)

/** Dessine la tour en 3D isométrique dans un Canvas Compose. Une instance par écran (réutilise ses objets). */
class GameRenderer {

    private val path = Path()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }

    // Projection courante
    private var cx = 0f
    private var cy = 0f
    private var unit = 1f
    private var camX = 0f
    private var camY = 0f
    private var camZ = 0f

    private fun px(x: Float, z: Float) = cx + (x - camX - (z - camZ)) * COS * unit
    private fun py(x: Float, y: Float, z: Float) = cy + ((x - camX) + (z - camZ)) * SIN * unit - (y - camY) * unit * BLOCK_H

    fun DrawScope.render(game: StackGame, o: RenderOptions) {
        val w = size.width
        val h = size.height
        val theme = o.theme

        drawBackground(o.theme, o.fx, game.time, game.camY, w, h)

        // Caméra : vue de jeu → vue d'ensemble en fin de partie.
        val top = game.topBlock.row + 1f
        val baseUnit = w / 15.5f
        val fitUnit = min(baseUnit, h * 0.55f / (top + 6f))
        val ov = game.overview
        unit = baseUnit + (fitUnit - baseUnit) * ov
        camX = game.camX * (1f - ov)
        camZ = game.camZ * (1f - ov)
        camY = (game.camY + 1f) * (1f - ov) + (top / 2f) * ov
        cx = w / 2f
        cy = h * (0.44f + 0.12f * ov)

        val shakeAmp = if (o.screenShake) game.shake * unit * 0.35f else 0f
        val sx = sin(game.time * 70f) * shakeAmp
        val sy = sin(game.time * 53f + 1f) * shakeAmp

        translate(sx, sy) {
            // Halo lumineux derrière la tour
            drawCircle(
                Brush.radialGradient(
                    listOf(blockColor(theme, game.topBlock.row).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, cy), radius = w * 0.75f,
                ),
                radius = w * 0.75f, center = Offset(cx, cy),
            )

            // Socle : colonne qui s'enfonce sous l'écran.
            val base = game.blocks.first()
            drawBox(base.x, base.z, base.w, base.d, -60f, 0f, blockColor(theme, 0).darken(0.75f), o.skin, 1f)

            // Débris situés derrière la tour (côté caméra opposé)
            for (d in game.debris) if (d.x0 + d.dx + d.z0 + d.dz < game.topBlock.x + game.topBlock.z) drawDebris(d, o)

            val minRow = camY - h / (unit * BLOCK_H) - 2
            val maxRow = camY + h / (unit * BLOCK_H) + 2
            for (b in game.blocks) {
                if (b.row < minRow || b.row > maxRow) continue
                drawBox(b.x, b.z, b.w, b.d, b.row.toFloat(), b.row + 1f, blockColor(theme, b.row), o.skin, 1f)
            }

            if (o.bestRow > 0 && o.bestRow > game.topBlock.row - 2) drawRecordMarker(o, game.time)

            game.mover?.let { m ->
                drawBox(m.x, m.z, m.w, m.d, m.row.toFloat(), m.row + 1f, blockColor(theme, m.row), o.skin, 1f)
            }

            for (d in game.debris) if (d.x0 + d.dx + d.z0 + d.dz >= game.topBlock.x + game.topBlock.z) drawDebris(d, o)

            for (r in game.ripples) {
                val t = r.age / StackGame.RIPPLE_DURATION
                val g = 0.15f + t * 1.4f
                val b = r.block
                outline(b.x - g, b.z - g, b.w + 2 * g, b.d + 2 * g, b.row + 1f)
                drawPath(path, Color.White.copy(alpha = (1f - t) * 0.9f), style = Stroke(width = unit * 0.12f * (1f - t) + 1.5f))
            }

            if (o.particles) {
                for (p in game.particles) {
                    val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = blockColor(theme, p.row + 4).lighten(0.4f).copy(alpha = a),
                        radius = unit * 0.11f * (0.4f + a),
                        center = Offset(px(p.x, p.z), py(p.x, p.y, p.z)),
                    )
                }
            }
        }

        if (game.flash > 0f) drawRect(Color.White.copy(alpha = game.flash * 0.12f))
    }

    private fun DrawScope.drawDebris(d: StackGame.Debris, o: RenderOptions) {
        drawBox(
            d.x0 + d.dx, d.z0 + d.dz, d.w, d.d, d.y, d.y + 1f,
            blockColor(o.theme, d.row), o.skin, d.alpha.coerceIn(0f, 1f),
        )
    }

    // --- Blocs --------------------------------------------------------------------------------

    private fun DrawScope.drawBox(
        x: Float, z: Float, w: Float, d: Float, bottom: Float, top: Float,
        color: Color, skin: BlockSkin, alpha: Float,
    ) {
        if (w <= 0.01f || d <= 0.01f || alpha <= 0f) return
        val x1 = x + w
        val z1 = z + d
        val topC = color.lighten(0.18f)
        val rightC = color
        val leftC = color.darken(0.7f)

        val fillAlpha = when (skin) {
            BlockSkin.GLASS -> 0.42f
            BlockSkin.NEON -> 1f
            else -> 1f
        } * alpha

        fun face(f: () -> Unit, c: Color, gradientTo: Color? = null) {
            path.reset(); f()
            val fill = if (skin == BlockSkin.NEON) c.darken(0.22f) else c
            if (gradientTo != null && skin != BlockSkin.NEON) {
                val b = path.getBounds()
                drawPath(path, Brush.verticalGradient(listOf(fill, gradientTo), startY = b.top, endY = b.bottom), alpha = fillAlpha)
            } else {
                drawPath(path, fill, alpha = fillAlpha)
            }
        }

        // Face droite (x = x1)
        face({
            path.moveTo(px(x1, z), py(x1, top, z)); path.lineTo(px(x1, z1), py(x1, top, z1))
            path.lineTo(px(x1, z1), py(x1, bottom, z1)); path.lineTo(px(x1, z), py(x1, bottom, z)); path.close()
        }, rightC, rightC.darken(0.85f))
        // Face gauche (z = z1)
        face({
            path.moveTo(px(x, z1), py(x, top, z1)); path.lineTo(px(x1, z1), py(x1, top, z1))
            path.lineTo(px(x1, z1), py(x1, bottom, z1)); path.lineTo(px(x, z1), py(x, bottom, z1)); path.close()
        }, leftC, leftC.darken(0.85f))
        // Dessus
        face({ topPath(x, z, x1, z1, top) }, topC)

        when (skin) {
            BlockSkin.CLASSIC -> {
                // Arêtes lumineuses discrètes
                edgeLines(x, z, x1, z1, top, Color.White.copy(alpha = 0.35f * alpha), unit * 0.035f)
            }
            BlockSkin.GLASS -> {
                edgeLines(x, z, x1, z1, top, Color.White.copy(alpha = 0.8f * alpha), unit * 0.04f)
                verticalEdges(x, z, x1, z1, bottom, top, Color.White.copy(alpha = 0.55f * alpha), unit * 0.03f)
                topPath(x + w * 0.1f, z + d * 0.1f, x + w * 0.35f, z1 - d * 0.1f, top)
                drawPath(path, Color.White.copy(alpha = 0.25f * alpha))
            }
            BlockSkin.NEON -> {
                val glow = color.lighten(0.3f)
                topPath(x, z, x1, z1, top)
                drawPath(path, glow.copy(alpha = 0.25f * alpha), style = Stroke(unit * 0.22f))
                edgeLines(x, z, x1, z1, top, glow.copy(alpha = alpha), unit * 0.07f)
                verticalEdges(x, z, x1, z1, bottom, top, glow.copy(alpha = alpha), unit * 0.06f)
                drawLine(glow.copy(alpha = alpha), Offset(px(x, z1), py(x, bottom, z1)), Offset(px(x1, z1), py(x1, bottom, z1)), unit * 0.06f)
                drawLine(glow.copy(alpha = alpha), Offset(px(x1, z1), py(x1, bottom, z1)), Offset(px(x1, z), py(x1, bottom, z)), unit * 0.06f)
            }
            BlockSkin.STRIPES -> {
                val mid = bottom + (top - bottom) * 0.5f
                val stripe = Color.White.copy(alpha = 0.28f * alpha)
                val sw = unit * BLOCK_H * (top - bottom).coerceAtMost(1f) * 0.22f
                drawLine(stripe, Offset(px(x, z1), py(x, mid, z1)), Offset(px(x1, z1), py(x1, mid, z1)), sw)
                drawLine(stripe, Offset(px(x1, z1), py(x1, mid, z1)), Offset(px(x1, z), py(x1, mid, z)), sw)
                edgeLines(x, z, x1, z1, top, Color.White.copy(alpha = 0.3f * alpha), unit * 0.03f)
            }
            BlockSkin.GEM -> {
                val ix = w * 0.22f
                val iz = d * 0.22f
                topPath(x + ix, z + iz, x1 - ix, z1 - iz, top)
                drawPath(path, Color.White.copy(alpha = 0.3f * alpha))
                // Facettes : diagonales du dessus
                val c = Color.White.copy(alpha = 0.35f * alpha)
                drawLine(c, Offset(px(x, z), py(x, top, z)), Offset(px(x + ix, z + iz), py(x + ix, top, z + iz)), unit * 0.03f)
                drawLine(c, Offset(px(x1, z), py(x1, top, z)), Offset(px(x1 - ix, z + iz), py(x1 - ix, top, z + iz)), unit * 0.03f)
                drawLine(c, Offset(px(x1, z1), py(x1, top, z1)), Offset(px(x1 - ix, z1 - iz), py(x1 - ix, top, z1 - iz)), unit * 0.03f)
                drawLine(c, Offset(px(x, z1), py(x, top, z1)), Offset(px(x + ix, z1 - iz), py(x + ix, top, z1 - iz)), unit * 0.03f)
                edgeLines(x, z, x1, z1, top, Color.White.copy(alpha = 0.6f * alpha), unit * 0.035f)
            }
        }
    }

    private fun topPath(x: Float, z: Float, x1: Float, z1: Float, y: Float) {
        path.reset()
        path.moveTo(px(x, z), py(x, y, z)); path.lineTo(px(x1, z), py(x1, y, z))
        path.lineTo(px(x1, z1), py(x1, y, z1)); path.lineTo(px(x, z1), py(x, y, z1)); path.close()
    }

    private fun outline(x: Float, z: Float, w: Float, d: Float, y: Float) = topPath(x, z, x + w, z + d, y)

    /** Les trois arêtes avant du dessus. */
    private fun DrawScope.edgeLines(x: Float, z: Float, x1: Float, z1: Float, y: Float, c: Color, sw: Float) {
        drawLine(c, Offset(px(x, z1), py(x, y, z1)), Offset(px(x1, z1), py(x1, y, z1)), sw, StrokeCap.Round)
        drawLine(c, Offset(px(x1, z1), py(x1, y, z1)), Offset(px(x1, z), py(x1, y, z)), sw, StrokeCap.Round)
    }

    private fun DrawScope.verticalEdges(x: Float, z: Float, x1: Float, z1: Float, bottom: Float, top: Float, c: Color, sw: Float) {
        drawLine(c, Offset(px(x1, z1), py(x1, top, z1)), Offset(px(x1, z1), py(x1, bottom, z1)), sw)
        drawLine(c, Offset(px(x, z1), py(x, top, z1)), Offset(px(x, z1), py(x, bottom, z1)), sw)
        drawLine(c, Offset(px(x1, z), py(x1, top, z)), Offset(px(x1, z), py(x1, bottom, z)), sw)
    }

    private fun DrawScope.drawRecordMarker(o: RenderOptions, time: Float) {
        val y = o.bestRow + 1f
        val half = 6.5f
        outline(camX - half, camZ - half, half * 2, half * 2, y)
        val pulse = 0.55f + 0.25f * sin(time * 3f)
        drawPath(
            path, Color(0xFFFFD54F).copy(alpha = pulse),
            style = Stroke(width = unit * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.4f, unit * 0.25f))),
        )
        textPaint.color = Color(0xFFFFD54F).copy(alpha = pulse).toArgb()
        textPaint.textSize = unit * 0.55f
        textPaint.typeface = o.typeface
        val tx = px(camX + half, camZ - half)
        val ty = py(camX + half, y, camZ - half)
        drawContext.canvas.nativeCanvas.drawText("RECORD ${o.bestRow}", tx - unit * 2.2f, ty - unit * 0.25f, textPaint)
    }

    // --- Décor --------------------------------------------------------------------------------

    /** Mini-tour pour les aperçus de la boutique. */
    fun DrawScope.renderPreview(theme: GameTheme, skin: BlockSkin, fx: BackgroundFx, time: Float) {
        val w = size.width
        val h = size.height
        drawBackground(theme, fx, time, 0f, w, h)
        unit = min(w / 8.5f, h / 6.5f)
        cx = w / 2f
        cy = h * 0.5f
        camX = 0f
        camZ = 0f
        camY = 2.6f
        drawBox(-2f, -2f, 4f, 4f, -10f, 0f, blockColor(theme, 0).darken(0.75f), skin, 1f)
        for (r in 0 until 5) {
            val s = 4f - r * 0.3f
            val off = sin(time * 1.5f + r) * 0.15f
            drawBox(-s / 2f + off, -s / 2f, s, s, r.toFloat(), r + 1f, blockColor(theme, r * 2), skin, 1f)
        }
    }

    private fun DrawScope.drawBackground(theme: GameTheme, fx: BackgroundFx, time: Float, camHeight: Float, w: Float, h: Float) {
        // Plus on monte, plus le ciel s'assombrit (vers l'espace).
        val altitude = min(camHeight / 160f, 1f) * if (theme.lightBackground) 0.35f else 0.7f
        val top = Color(theme.bgTop).mix(Color(0xFF02020A), altitude)
        val bottom = Color(theme.bgBottom).mix(Color(theme.bgTop), altitude * 0.6f)
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val t = time
        val drift = camHeight * 6f
        when (fx) {
            BackgroundFx.NONE -> Unit
            BackgroundFx.STARS -> repeat(70) { i ->
                val x = hash(i, 1) * w
                val y = (hash(i, 2) * h + drift * (0.3f + hash(i, 3))) % h
                val tw = 0.35f + 0.65f * abs(sin(t * (0.6f + hash(i, 4) * 2f) + i))
                drawCircle(Color.White.copy(alpha = tw * (0.4f + altitude)), radius = 1f + hash(i, 5) * 2.4f, center = Offset(x, y))
            }
            BackgroundFx.BUBBLES -> repeat(26) { i ->
                val speed = 30f + hash(i, 2) * 60f
                val x = hash(i, 1) * w + sin(t * 1.2f + i) * 14f
                val y = h + 40f - ((t * speed + hash(i, 3) * (h + 80f) + drift) % (h + 80f))
                val r = 6f + hash(i, 4) * 18f
                drawCircle(Color.White.copy(alpha = 0.25f), radius = r, center = Offset(x, y), style = Stroke(2f))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.25f, center = Offset(x - r * 0.35f, y - r * 0.35f))
            }
            BackgroundFx.SNOW -> repeat(80) { i ->
                val speed = 25f + hash(i, 2) * 50f
                val x = (hash(i, 1) * w + sin(t * 0.8f + i) * 20f + w) % w
                val y = ((t * speed + hash(i, 3) * h - drift) % h + h) % h
                drawCircle(Color.White.copy(alpha = 0.5f + hash(i, 5) * 0.4f), radius = 1.5f + hash(i, 4) * 3f, center = Offset(x, y))
            }
            BackgroundFx.FIREFLIES -> repeat(22) { i ->
                val x = hash(i, 1) * w + sin(t * (0.3f + hash(i, 2)) + i) * 60f
                val y = hash(i, 3) * h + sin(t * (0.25f + hash(i, 4)) + i * 2f) * 50f
                val a = 0.3f + 0.7f * abs(sin(t * (1f + hash(i, 5)) + i))
                val r = 18f + hash(i, 6) * 10f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFF59D).copy(alpha = a * 0.8f), Color.Transparent), center = Offset(x, y), radius = r),
                    radius = r, center = Offset(x, y),
                )
            }
            BackgroundFx.CONFETTI -> repeat(45) { i ->
                val speed = 40f + hash(i, 2) * 70f
                val x = (hash(i, 1) * w + sin(t + i) * 30f + w) % w
                val y = ((t * speed + hash(i, 3) * h - drift) % h + h) % h
                val c = Color.hsv(hash(i, 4) * 360f, 0.7f, 1f, 0.75f)
                rotate(t * 120f * (hash(i, 5) - 0.5f) * 4f + i * 40f, pivot = Offset(x, y)) {
                    drawRect(c, topLeft = Offset(x - 5f, y - 3f), size = Size(10f, 6f))
                }
            }
        }
    }

    companion object {
        private const val COS = 0.866f
        private const val SIN = 0.5f
        private const val BLOCK_H = 1.0f

        fun blockColor(theme: GameTheme, row: Int): Color {
            if (theme.saturation == 0f) {
                val v = (theme.value + (row % 8) * 0.06f).coerceIn(0f, 1f)
                return Color.hsv(0f, 0f, v)
            }
            val hue = ((theme.hueStart + row * theme.hueStep) % 360f + 360f) % 360f
            return Color.hsv(hue, theme.saturation, theme.value.coerceIn(0f, 1f))
        }

        /** Pseudo-aléatoire stable dans [0, 1). */
        private fun hash(i: Int, salt: Int): Float {
            var x = i * 374761393 + salt * 668265263
            x = (x xor (x ushr 13)) * 1274126177
            x = x xor (x ushr 16)
            return (x and 0xFFFFFF) / 16777216f
        }

        fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
        fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
        fun Color.mix(o: Color, t: Float) = Color(
            red + (o.red - red) * t, green + (o.green - green) * t, blue + (o.blue - blue) * t, alpha + (o.alpha - alpha) * t,
        )
    }
}
