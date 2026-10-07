package com.bommer.stacktower.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.R

object AppFonts {
    val Title = FontFamily(Font(R.font.lilita_one))
    val Body = FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal),
        Font(R.font.nunito_semibold, FontWeight.SemiBold),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
        Font(R.font.nunito_black, FontWeight.Black),
    )
}

object Palette {
    val Ink = Color(0xFF0E1022)
    val Panel = Color(0xF2171A35)
    val PanelLight = Color(0x1FFFFFFF)
    val Stroke = Color(0x33FFFFFF)
    val TextDim = Color(0xFFAEB3DA)
    val Gold = Color(0xFFFFC94A)
    val GoldDark = Color(0xFFE09A00)
    val Green = Color(0xFF2FD47A)
    val GreenDark = Color(0xFF169751)
    val Blue = Color(0xFF4C7BFF)
    val Purple = Color(0xFF8A5CFF)
    val Pink = Color(0xFFFF4F8B)
    val Red = Color(0xFFFF5B5B)
    val Orange = Color(0xFFFF9443)
}

/** Couleurs d'un bouton de jeu : dégradé + « socle » plus sombre pour l'effet 3D. */
data class ButtonColors(val top: Color, val bottom: Color, val base: Color, val content: Color)

object Buttons {
    val Primary = ButtonColors(Color(0xFF6FE7A0), Color(0xFF2FC56E), Color(0xFF1A8A4A), Color.White)
    val Gold = ButtonColors(Color(0xFFFFE07A), Color(0xFFFFB82E), Color(0xFFC07800), Color(0xFF4A2C00))
    val Blue = ButtonColors(Color(0xFF7FA2FF), Color(0xFF4C6FFF), Color(0xFF2B41B8), Color.White)
    val Purple = ButtonColors(Color(0xFFB08CFF), Color(0xFF7C4DFF), Color(0xFF4B23B5), Color.White)
    val Pink = ButtonColors(Color(0xFFFF8DB4), Color(0xFFFF4F8B), Color(0xFFB8265A), Color.White)
    val Ghost = ButtonColors(Color(0x40FFFFFF), Color(0x26FFFFFF), Color(0x40000000), Color.White)
    val Disabled = ButtonColors(Color(0xFF6B6F8A), Color(0xFF55597A), Color(0xFF34374F), Color(0xFFCDD0E6))
}

/** Retour sensoriel (son + vibration) partagé par tous les composants cliquables. */
interface Feedback {
    fun click()
}

val LocalFeedback = staticCompositionLocalOf<Feedback> { object : Feedback { override fun click() {} } }

fun titleStyle(size: TextUnit, color: Color = Color.White, shadow: Boolean = true) = TextStyle(
    fontFamily = AppFonts.Title,
    fontSize = size,
    color = color,
    shadow = if (shadow) Shadow(Color(0x66000000), Offset(0f, 6f), 0f) else null,
)

fun bodyStyle(size: TextUnit, weight: FontWeight = FontWeight.Bold, color: Color = Color.White) = TextStyle(
    fontFamily = AppFonts.Body,
    fontSize = size,
    fontWeight = weight,
    color = color,
)

/** Modificateur cliquable avec effet « pression » (rétrécit) et son de clic. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, sound: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.93f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    val fb = LocalFeedback.current
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            if (sound) fb.click()
            onClick()
        }
}

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = Buttons.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 60.dp,
    fontSize: TextUnit = 22.sp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = if (enabled) colors else Buttons.Disabled
    val shape = RoundedCornerShape(height / 2.6f)
    Box(modifier.height(height + 6.dp).bouncyClick(enabled) { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = 6.dp)
                .background(c.base, shape)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(c.top, c.bottom)))
                .border(2.dp, Color.White.copy(alpha = 0.18f), shape)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = c.content, modifier = Modifier.size(height * 0.45f))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = titleStyle(fontSize, c.content, shadow = false), maxLines = 1)
            trailing?.invoke(this)
        }
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    badge: Boolean = false,
    size: Dp = 58.dp,
    tint: Color = Color.White,
    bg: Color = Color(0x33000000),
) {
    Column(modifier.bouncyClick { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.32f))
                    .background(bg)
                    .border(1.5.dp, Palette.Stroke, RoundedCornerShape(size * 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.5f))
            }
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(16.dp)
                        .background(Palette.Red, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = bodyStyle(12.sp, FontWeight.ExtraBold), maxLines = 1)
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    color: Color = Palette.Panel,
    radius: Dp = 24.dp,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(1.5.dp, Palette.Stroke, RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

@Composable
fun CoinIcon(size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Palette.GoldDark, r)
        drawCircle(Palette.Gold, r * 0.86f, center = center.copy(y = center.y - r * 0.06f))
        drawCircle(Color(0xFFFFE9A6), r * 0.5f, center = center.copy(y = center.y - r * 0.06f), style = Stroke(r * 0.14f))
    }
}

/** Compteur de pièces animé. */
@Composable
fun CoinCounter(coins: Int, modifier: Modifier = Modifier, onPlus: (() -> Unit)? = null) {
    val shown by animateIntAsState(coins, tween(600), label = "coins")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x55000000))
            .border(1.5.dp, Palette.Stroke, RoundedCornerShape(50))
            .then(if (onPlus != null) Modifier.bouncyClick { onPlus() } else Modifier)
            .padding(start = 8.dp, end = if (onPlus != null) 6.dp else 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinIcon(22.dp)
        Spacer(Modifier.width(8.dp))
        Text("$shown", style = titleStyle(18.sp, shadow = false))
        if (onPlus != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .background(Palette.Green, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("+", style = titleStyle(16.sp, shadow = false)) }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Green, height: Dp = 10.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color(0x40000000))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.8f), color)))
        )
    }
}

/** Anneau de progression (badge de niveau). */
@Composable
fun LevelBadge(level: Int, progress: Float, size: Dp = 54.dp) {
    val p by animateFloatAsState(progress, tween(800), label = "lvl")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = this.size.minDimension * 0.11f
            drawCircle(Color(0x66000000))
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, style = Stroke(sw),
                topLeft = Offset(sw / 2, sw / 2), size = this.size.copy(this.size.width - sw, this.size.height - sw))
            drawArc(Brush.sweepGradient(listOf(Palette.Gold, Palette.Orange, Palette.Gold)), -90f, 360f * p, false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2), size = this.size.copy(this.size.width - sw, this.size.height - sw))
        }
        Text("$level", style = titleStyle((size.value * 0.38f).sp, shadow = false))
    }
}

/** Sélecteur d'onglets en forme de pilule. */
@Composable
fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x40000000))
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.Transparent)
                    .bouncyClick { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, maxLines = 1,
                    style = bodyStyle(13.sp, FontWeight.Black, if (sel) Palette.Ink else Color.White),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color = Color.White, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = bodyStyle(13.sp, FontWeight.Black, fg), maxLines = 1)
    }
}

/** Fond assombri plein écran pour les fenêtres modales. */
@Composable
fun Scrim(onDismiss: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xB3000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Empêche un clic de traverser vers le fond (ex. : contenu d'une fenêtre modale). */
@Composable
fun Modifier.absorbClicks(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
