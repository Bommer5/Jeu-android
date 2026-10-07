package com.bommer.stacktower.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.bommer.stacktower.R
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Wheel
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.Notice
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.titleStyle
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.random.Random

@Composable
fun WheelScreen(c: AppController, profile: Profile, rewardedReady: Boolean, onBack: () -> Unit) {
    val today = c.today()
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.lilita_one) }
    val segment = 360f / Wheel.prizes.size

    // Petit « tic » sonore à chaque case franchie.
    LaunchedEffect(Unit) {
        var lastIndex = 0
        snapshotFlow { rotation.value }.collect { r ->
            val i = floor(r / segment).toInt()
            if (i != lastIndex && spinning) c.sound.tick()
            lastIndex = i
        }
    }

    fun spin(viaAd: Boolean) {
        if (spinning) return
        spinning = true
        val index = Wheel.pick(Random(System.nanoTime()))
        val current = rotation.value
        val target = current - (current % 360f) + 360f * 6 + (360f - (index * segment + segment / 2f))
        scope.launch {
            rotation.animateTo(target, tween(4800, easing = CubicBezierEasing(0.12f, 0f, 0.08f, 1f)))
            val prize = Wheel.prizes[index]
            c.update({ Wheel.grant(it, today, index, viaAd) }) {
                c.sound.levelUp()
                val label = prize.booster?.let { "Bonus ${it.title} !" } ?: "+${prize.coins} pièces !"
                c.notify(Notice.Kind.COINS, "Gagné : $label")
                spinning = false
            }
        }
    }

    val free = Wheel.hasFreeSpin(profile, today)
    val adLeft = Wheel.adSpinsLeft(profile, today)

    ScreenScaffold("Roue de la fortune", profile.coins, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = if (profile.adsRemoved) 16.dp else 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Un tour gratuit chaque jour, et jusqu'à ${Wheel.MAX_AD_SPINS} tours bonus avec une vidéo.",
                style = bodyStyle(14.sp, FontWeight.Bold, Palette.TextDim),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val r = size.minDimension / 2f * 0.92f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // Anneau extérieur
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A2C7A), Color(0xFF1A1340)), center, r * 1.09f), r * 1.09f, center)
                    rotate(rotation.value, center) {
                        Wheel.prizes.forEachIndexed { i, p ->
                            drawArc(
                                Color(p.color), startAngle = -90f + i * segment, sweepAngle = segment, useCenter = true,
                                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                            )
                            drawArc(
                                Color.White.copy(alpha = 0.25f), startAngle = -90f + i * segment, sweepAngle = segment, useCenter = true,
                                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(3f),
                            )
                        }
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.typeface = typeface
                            textAlign = Paint.Align.CENTER
                            textSize = r * 0.13f
                            color = Color.White.toArgb()
                            setShadowLayer(6f, 0f, 3f, Color(0x88000000).toArgb())
                        }
                        Wheel.prizes.forEachIndexed { i, p ->
                            rotate(i * segment + segment / 2f, center) {
                                drawContext.canvas.nativeCanvas.drawText(p.label, center.x, center.y - r * 0.62f, paint)
                            }
                        }
                    }
                    // Ampoules
                    repeat(16) { k ->
                        val a = Math.toRadians((k * 22.5).toDouble())
                        val on = ((rotation.value / 20f).toInt() + k) % 2 == 0
                        drawCircle(
                            if (on) Color(0xFFFFF3B0) else Color(0xFFB08A2E), r * 0.035f,
                            Offset(center.x + (r * 1.045f * kotlin.math.cos(a)).toFloat(), center.y + (r * 1.045f * kotlin.math.sin(a)).toFloat()),
                        )
                    }
                    // Moyeu
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE07A), Color(0xFFE09A00)), center, r * 0.16f), r * 0.16f, center)
                    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.16f, center, style = Stroke(4f))
                    // Pointeur
                    val tip = Path().apply {
                        moveTo(center.x - r * 0.1f, center.y - r * 1.12f)
                        lineTo(center.x + r * 0.1f, center.y - r * 1.12f)
                        lineTo(center.x, center.y - r * 0.9f)
                        close()
                    }
                    drawPath(tip, Palette.Red)
                    drawPath(tip, Color.White, style = Stroke(4f))
                }
            }
            Spacer(Modifier.height(20.dp))
            when {
                free -> GameButton("TOURNER", { spin(false) }, Modifier.fillMaxWidth(0.8f), Buttons.Primary,
                    Icons.Rounded.Casino, enabled = !spinning, height = 64.dp)
                adLeft > 0 -> GameButton("TOURNER ($adLeft)", { c.showRewarded { spin(true) } }, Modifier.fillMaxWidth(0.8f),
                    Buttons.Gold, Icons.Rounded.OndemandVideo, enabled = !spinning && rewardedReady, height = 64.dp)
                else -> Text("Reviens demain pour un nouveau tour !", style = titleStyle(20.sp, Palette.TextDim, shadow = false))
            }
        }
    }
}
