package com.bommer.stacktower.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.data.Missions
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.data.Wheel
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.Themes
import com.bommer.stacktower.monetization.BillingManager
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.CoinCounter
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.IconBubble
import com.bommer.stacktower.ui.design.LevelBadge
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.ProgressBar
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.bouncyClick
import com.bommer.stacktower.ui.design.titleStyle

fun GameMode.icon(): ImageVector = when (this) {
    GameMode.CLASSIC -> Icons.Rounded.Layers
    GameMode.DAILY -> Icons.Rounded.Today
    GameMode.ZEN -> Icons.Rounded.SelfImprovement
    GameMode.CHRONO -> Icons.Rounded.Timer
    GameMode.HARD -> Icons.Rounded.LocalFireDepartment
}

fun GameMode.accent(): Color = when (this) {
    GameMode.CLASSIC -> Palette.Blue
    GameMode.DAILY -> Palette.Orange
    GameMode.ZEN -> Palette.Green
    GameMode.CHRONO -> Palette.Purple
    GameMode.HARD -> Palette.Red
}

fun Booster.icon(): ImageVector = when (this) {
    Booster.SLOW -> Icons.Rounded.SlowMotionVideo
    Booster.SHIELD -> Icons.Rounded.Shield
    Booster.HEAD_START -> Icons.Rounded.Upgrade
}

@Composable
fun HomeScreen(
    c: AppController,
    profile: Profile,
    mode: GameMode,
    onModeChange: (GameMode) -> Unit,
    boosters: Set<Booster>,
    onBoostersChange: (Set<Booster>) -> Unit,
    onPlay: () -> Unit,
    onShop: (ShopTab) -> Unit,
    onProgress: (Int) -> Unit,
    onWheel: () -> Unit,
    onSettings: () -> Unit,
    onDaily: () -> Unit,
) {
    val today = c.today()
    val dailyAvailable = Progression.canClaimDaily(profile, today)
    val wheelAvailable = Wheel.hasFreeSpin(profile, today)
    val missionsReady = Missions.ensureDay(profile, today).let { p ->
        Missions.forDay(today).any { it.id !in p.missionsClaimed && Missions.progress(p, it) >= it.target }
    }
    val locked = profile.level < mode.unlockLevel
    val light = Themes.byId(profile.theme).lightBackground
    val titleColor = if (light) Color(0xFF1E2140) else Color.White

    val anim = rememberInfiniteTransition(label = "home")
    val float by anim.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "float")
    val pulse by anim.animateFloat(1f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color.Transparent, Color(0x99000000))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        // Barre du haut : niveau + pièces
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.bouncyClick { onProgress(2) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(profile.level, Progression.levelProgress(profile.xp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Niveau ${profile.level}", style = titleStyle(18.sp, shadow = false))
                    ProgressBar(Progression.levelProgress(profile.xp), Modifier.width(90.dp), Palette.Gold, 8.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            CoinCounter(profile.coins, onPlus = { onShop(ShopTab.COINS) })
        }

        // Titre
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
                .offset(y = float.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("STACK", style = titleStyle(60.sp, titleColor), lineHeight = 58.sp)
            Text("TOWER", style = titleStyle(60.sp, Palette.Gold), lineHeight = 58.sp, modifier = Modifier.offset(y = (-14).dp))
        }

        // Raccourcis latéraux
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBubble(Icons.Rounded.CardGiftcard, onDaily, label = "Cadeau", badge = dailyAvailable,
                tint = if (dailyAvailable) Palette.Gold else Color.White)
            IconBubble(Icons.Rounded.Casino, onWheel, label = "Roue", badge = wheelAvailable)
            IconBubble(Icons.Rounded.EmojiEvents, { onProgress(0) }, label = "Missions", badge = missionsReady)
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBubble(Icons.Rounded.Storefront, { onShop(ShopTab.THEMES) }, label = "Boutique")
            IconBubble(Icons.Rounded.Settings, onSettings, label = "Réglages")
            if (!profile.adsRemoved) {
                IconBubble(Icons.Rounded.Block, { c.buy(BillingManager.REMOVE_ADS) }, label = "Sans pub", tint = Palette.Pink)
            }
        }

        // Bas de l'écran : mode, bonus, jouer
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = if (profile.adsRemoved) 16.dp else 66.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeSelector(profile, mode, onModeChange, today)
            Spacer(Modifier.height(12.dp))
            BoosterRow(profile, boosters, onBoostersChange) { onShop(ShopTab.BOOSTERS) }
            Spacer(Modifier.height(14.dp))
            GameButton(
                text = if (locked) "Niveau ${mode.unlockLevel} requis" else "JOUER",
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .scale(if (locked) 1f else pulse),
                colors = Buttons.Primary,
                icon = if (locked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
                enabled = !locked,
                height = 70.dp,
                fontSize = if (locked) 22.sp else 32.sp,
            )
        }
    }
}

@Composable
private fun ModeSelector(profile: Profile, mode: GameMode, onChange: (GameMode) -> Unit, today: Long) {
    val modes = GameMode.entries
    val index = modes.indexOf(mode)
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, { onChange(modes[(index - 1 + modes.size) % modes.size]) },
                size = 40.dp, bg = Color.Transparent)
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val dir = if (modes.indexOf(targetState) > modes.indexOf(initialState)) 1 else -1
                    (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "mode",
            ) { m ->
                val locked = profile.level < m.unlockLevel
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(m.accent(), m.accent().copy(alpha = 0.6f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (locked) Icons.Rounded.Lock else m.icon(), null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = titleStyle(22.sp, shadow = false))
                        Text(
                            if (locked) "Débloqué au niveau ${m.unlockLevel}" else m.subtitle,
                            style = bodyStyle(13.sp, FontWeight.Bold, Palette.TextDim), maxLines = 1,
                        )
                        val best = when (m) {
                            GameMode.DAILY -> if (profile.dailyChallengeDay == today) "Aujourd'hui : ${profile.dailyChallengeBest}" else "Pas encore joué aujourd'hui"
                            else -> "Record : ${profile.bestFor(m)}"
                        }
                        Text(best, style = bodyStyle(13.sp, FontWeight.Black, Palette.Gold))
                    }
                }
            }
            IconBubble(Icons.AutoMirrored.Rounded.KeyboardArrowRight, { onChange(modes[(index + 1) % modes.size]) },
                size = 40.dp, bg = Color.Transparent)
        }
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEach { m ->
                Box(
                    Modifier
                        .size(if (m == mode) 18.dp else 7.dp, 7.dp)
                        .clip(CircleShape)
                        .background(if (m == mode) Color.White else Color(0x55FFFFFF))
                )
            }
        }
    }
}

@Composable
private fun BoosterRow(profile: Profile, selected: Set<Booster>, onChange: (Set<Booster>) -> Unit, onBuy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Booster.entries.forEach { b ->
            val count = profile.boosterCount(b)
            val on = b in selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Palette.Gold.copy(alpha = 0.9f) else Color(0x55000000))
                    .border(1.5.dp, if (on) Color.White else Palette.Stroke, RoundedCornerShape(16.dp))
                    .bouncyClick {
                        if (count == 0) onBuy() else onChange(if (on) selected - b else selected + b)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) Color(0xFF3A2400) else Color.White
                Icon(b.icon(), null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = bodyStyle(12.sp, FontWeight.Black, fg), maxLines = 1)
                    Text(
                        if (count == 0) "Acheter" else if (on) "Activé" else "×$count",
                        style = bodyStyle(11.sp, FontWeight.Bold, fg.copy(alpha = 0.75f)),
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}
