package com.bommer.stacktower.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.StackGame
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.design.Buttons
import com.bommer.stacktower.ui.design.CoinIcon
import com.bommer.stacktower.ui.design.GameButton
import com.bommer.stacktower.ui.design.GlassCard
import com.bommer.stacktower.ui.design.IconBubble
import com.bommer.stacktower.ui.design.Palette
import com.bommer.stacktower.ui.design.Pill
import com.bommer.stacktower.ui.design.ProgressBar
import com.bommer.stacktower.ui.design.Scrim
import com.bommer.stacktower.ui.design.absorbClicks
import com.bommer.stacktower.ui.design.bodyStyle
import com.bommer.stacktower.ui.design.titleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { PLAYING, PAUSED, OVER }

@Composable
fun GameLayer(
    c: AppController,
    game: StackGame,
    profile: Profile,
    rewardedReady: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember(game) { mutableStateOf(Phase.PLAYING) }
    var score by remember(game) { mutableIntStateOf(game.score) }
    var timeLeft by remember(game) { mutableFloatStateOf(game.timeLeft) }
    var shields by remember(game) { mutableIntStateOf(game.shields) }
    var popup by remember(game) { mutableStateOf("") }
    var popupColor by remember(game) { mutableStateOf(Color.White) }
    var popupKey by remember(game) { mutableIntStateOf(0) }
    var doubled by remember(game) { mutableStateOf(false) }
    var finalized by remember(game) { mutableStateOf(false) }
    val bestBefore = remember(game) { profile.bestFor(game.mode) }
    val scoreBump = remember(game) { Animatable(1f) }
    val showTutorial = !profile.tutorialDone && game.mode == GameMode.CLASSIC && score < 3 && phase == Phase.PLAYING

    fun finalizeRun() {
        if (finalized) return
        finalized = true
        c.finishRun(game, bonusCoins = if (doubled) game.coinsEarned else 0)
    }

    fun showPopup(text: String, color: Color) {
        popup = text
        popupColor = color
        popupKey++
    }

    // Traitement des événements du moteur (sons, vibrations, textes).
    LaunchedEffect(game) {
        while (true) {
            withFrameNanos { }
            timeLeft = game.timeLeft
            while (game.events.isNotEmpty()) {
                val e = game.events.removeFirst()
                when (e.type) {
                    StackGame.EventType.PERFECT -> {
                        c.sound.perfect(e.value)
                        c.vibrate(18)
                        val (label, color) = when {
                            e.value >= 10 -> "INCROYABLE ×${e.value}" to Color(0xFFFF6BD6)
                            e.value >= 5 -> "GÉNIAL ×${e.value}" to Color(0xFF7DF9FF)
                            e.value > 1 -> "PARFAIT ×${e.value}" to Palette.Gold
                            else -> "PARFAIT" to Palette.Gold
                        }
                        showPopup(label, color)
                    }
                    StackGame.EventType.PLACED -> {
                        c.sound.place()
                        c.vibrate(10)
                    }
                    StackGame.EventType.SAVED -> {
                        c.sound.fail()
                        c.vibrate(60, strong = true)
                        shields = game.shields
                        showPopup(
                            when (game.mode) {
                                GameMode.CHRONO -> "−5 s"
                                GameMode.ZEN -> "Raté !"
                                else -> "BOUCLIER !"
                            },
                            Palette.Red,
                        )
                    }
                    StackGame.EventType.MILESTONE -> {
                        c.sound.coin()
                        showPopup("${e.value} ÉTAGES !", Color(0xFF8CFFB5))
                    }
                    StackGame.EventType.MISS -> {
                        c.sound.fail()
                        c.vibrate(120, strong = true)
                    }
                    StackGame.EventType.TIME_UP -> {
                        c.sound.fail()
                        showPopup("TEMPS ÉCOULÉ", Palette.Red)
                    }
                    StackGame.EventType.GAME_OVER -> scope.launch {
                        delay(900)
                        if (game.state == StackGame.State.GAME_OVER) phase = Phase.OVER
                    }
                }
                if (game.score != score) {
                    score = game.score
                    scope.launch {
                        scoreBump.snapTo(1.25f)
                        scoreBump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                }
            }
        }
    }

    // Fin du tutoriel
    LaunchedEffect(score) {
        if (!profile.tutorialDone && score >= 3) c.update({ it.copy(tutorialDone = true) })
    }

    // Pause automatique quand l'app passe en arrière-plan ; on enregistre une partie terminée.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPhase by rememberUpdatedState(phase)
    DisposableEffect(lifecycleOwner, game) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && currentPhase == Phase.PLAYING && game.state == StackGame.State.PLAYING) {
                game.pause()
                phase = Phase.PAUSED
            }
            if (event == Lifecycle.Event.ON_STOP && currentPhase == Phase.OVER) finalizeRun()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        when (phase) {
            Phase.PLAYING -> { game.pause(); phase = Phase.PAUSED }
            Phase.PAUSED -> { game.resume(); phase = Phase.PLAYING }
            Phase.OVER -> { finalizeRun(); c.afterGame(onExit) }
        }
    }

    val lightBg = com.bommer.stacktower.game.Themes.byId(profile.theme).lightBackground
    val hudColor = if (lightBg) Color(0xFF1E2140) else Color.White

    Box(Modifier.fillMaxSize()) {
        // Zone de jeu : toucher n'importe où pose le bloc.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(game) {
                    detectTapGestures(onPress = { if (game.state == StackGame.State.PLAYING) game.tap() })
                }
        )

        if (phase != Phase.OVER) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                IconBubble(Icons.Rounded.Pause, onClick = { game.pause(); phase = Phase.PAUSED }, size = 48.dp,
                    modifier = Modifier.align(Alignment.TopStart))

                Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score",
                        style = titleStyle(84.sp, hudColor),
                        modifier = Modifier.scale(scoreBump.value),
                    )
                    if (game.mode != GameMode.ZEN && bestBefore > 0) {
                        Text("Record $bestBefore", style = bodyStyle(14.sp, FontWeight.ExtraBold, hudColor.copy(alpha = 0.7f)))
                    }
                    if (game.mode == GameMode.CHRONO) {
                        Spacer(Modifier.height(10.dp))
                        val low = timeLeft < 10f
                        ProgressBar(timeLeft / StackGame.CHRONO_TIME, Modifier.width(180.dp),
                            color = if (low) Palette.Red else Palette.Blue, height = 12.dp)
                        Text("${timeLeft.toInt()} s", style = titleStyle(20.sp, if (low) Palette.Red else hudColor, shadow = false))
                    }
                }

                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Pill(game.mode.title, Color(0x55000000))
                    if (shields > 0) {
                        Spacer(Modifier.height(6.dp))
                        Pill("×$shields", Color(0xCCFFB300), Color(0xFF3A2400)) {
                            Icon(Icons.Rounded.Shield, null, tint = Color(0xFF3A2400), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                ComboPopup(popup, popupColor, popupKey, Modifier.align(Alignment.TopCenter).padding(top = 170.dp))

                if (showTutorial) TutorialHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp))
            }
        }

        if (phase == Phase.PAUSED) {
            PauseMenu(
                c = c,
                profile = profile,
                zen = game.mode == GameMode.ZEN,
                onResume = { game.resume(); phase = Phase.PLAYING },
                onFinishZen = { game.finish() ; phase = Phase.PLAYING },
                onRestart = {
                    if (game.score > 0) { game.finish(); finalizeRun() }
                    onRestart()
                },
                onQuit = {
                    if (game.score > 0) { game.finish(); finalizeRun() }
                    onExit()
                },
            )
        }

        AnimatedVisibility(phase == Phase.OVER, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut()) {
            GameOverPanel(
                c = c,
                game = game,
                profile = profile,
                bestBefore = bestBefore,
                doubled = doubled,
                rewardedReady = rewardedReady,
                onContinue = {
                    c.showRewarded {
                        if (game.continueRun()) phase = Phase.PLAYING
                    }
                },
                onDouble = {
                    c.showRewarded {
                        doubled = true
                        c.sound.coin()
                    }
                },
                onRetry = {
                    finalizeRun()
                    c.afterGame(onRestart)
                },
                onMenu = {
                    finalizeRun()
                    c.afterGame(onExit)
                },
            )
        }
    }
}

@Composable
private fun ComboPopup(text: String, color: Color, key: Int, modifier: Modifier) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    if (anim.value < 1f) {
        Text(
            text,
            style = titleStyle(34.sp, color),
            modifier = modifier.graphicsLayer {
                val t = anim.value
                alpha = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
                translationY = -90f * t
                val s = if (t < 0.15f) 0.6f + t / 0.15f * 0.6f else 1.2f - (t - 0.15f) * 0.25f
                scaleX = s
                scaleY = s
            },
        )
    }
}

@Composable
private fun TutorialHint(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "tuto").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tuto",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.TouchApp, null, tint = Color.White,
            modifier = Modifier.size(64.dp).scale(0.85f + 0.15f * t).alpha(0.7f + 0.3f * t),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Touche l'écran quand le bloc\nest pile au-dessus de la tour",
            style = bodyStyle(17.sp, FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color(0x99000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text("3 parfaits d'affilée = le bloc grandit !", style = bodyStyle(13.sp, FontWeight.Bold, Palette.Gold))
    }
}

@Composable
private fun PauseMenu(
    c: AppController,
    profile: Profile,
    zen: Boolean,
    onResume: () -> Unit,
    onFinishZen: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
) {
    Scrim(onDismiss = onResume) {
        GlassCard(Modifier.widthIn(max = 380.dp).padding(24.dp).absorbClicks()) {
            Text("PAUSE", style = titleStyle(40.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val s = profile.settings
                IconBubble(if (s.sound) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    { c.update({ it.copy(settings = it.settings.copy(sound = !s.sound)) }) }, label = "Sons")
                IconBubble(if (s.music) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                    { c.update({ it.copy(settings = it.settings.copy(music = !s.music)) }) }, label = "Musique")
                IconBubble(Icons.Rounded.Vibration,
                    { c.update({ it.copy(settings = it.settings.copy(vibration = !s.vibration)) }) },
                    label = if (s.vibration) "Vibr. on" else "Vibr. off")
            }
            Spacer(Modifier.height(20.dp))
            GameButton("Reprendre", onResume, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            Spacer(Modifier.height(10.dp))
            if (zen) {
                GameButton("Terminer", onFinishZen, Modifier.fillMaxWidth(), Buttons.Gold, Icons.Rounded.Flag)
                Spacer(Modifier.height(10.dp))
            }
            GameButton("Recommencer", onRestart, Modifier.fillMaxWidth(), Buttons.Blue, Icons.Rounded.Refresh)
            Spacer(Modifier.height(10.dp))
            GameButton("Menu", onQuit, Modifier.fillMaxWidth(), Buttons.Ghost, Icons.Rounded.Home)
        }
    }
}

@Composable
private fun GameOverPanel(
    c: AppController,
    game: StackGame,
    profile: Profile,
    bestBefore: Int,
    doubled: Boolean,
    rewardedReady: Boolean,
    onContinue: () -> Unit,
    onDouble: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    val newBest = game.score > bestBefore && game.score > 0
    val coins = if (doubled) game.coinsEarned * 2 else game.coinsEarned
    val pulse by rememberInfiniteTransition(label = "go").animateFloat(
        1f, 1.05f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "go",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .absorbClicks()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(Modifier.widthIn(max = 420.dp).padding(20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (newBest) {
                    Text("NOUVEAU RECORD !", style = titleStyle(28.sp, Palette.Gold), modifier = Modifier.scale(pulse))
                } else {
                    Text(game.mode.title.uppercase(), style = titleStyle(24.sp, Palette.TextDim))
                }
                Text("${game.score}", style = titleStyle(96.sp))
                Text(
                    if (game.mode == GameMode.ZEN) "étages" else "Record : ${maxOf(bestBefore, game.score)}",
                    style = bodyStyle(15.sp, FontWeight.ExtraBold, Palette.TextDim),
                )
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("Parfaits", "${game.perfects}")
                    Stat("Combo max", "${game.bestCombo}")
                    Stat("XP", "+${game.xpEarned}")
                }
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("+$coins", style = titleStyle(32.sp, Palette.Gold))
                    if (doubled) {
                        Spacer(Modifier.width(8.dp))
                        Pill("×2", Palette.Green)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val lvlProgress = Progression.levelProgress(profile.xp + game.xpEarned)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Niv. ${Progression.levelFor(profile.xp + game.xpEarned)}", style = bodyStyle(13.sp, FontWeight.Black))
                    Spacer(Modifier.width(10.dp))
                    ProgressBar(lvlProgress, Modifier.weight(1f), Palette.Gold)
                }
                Spacer(Modifier.height(18.dp))

                if (game.canContinue && rewardedReady) {
                    GameButton("Continuer", onContinue, Modifier.fillMaxWidth().scale(pulse), Buttons.Primary,
                        Icons.Rounded.PlayArrow, trailing = { Spacer(Modifier.width(8.dp)); Pill("PUB", Color(0x33000000)) })
                    Spacer(Modifier.height(10.dp))
                }
                if (!doubled && rewardedReady && game.coinsEarned > 0) {
                    GameButton("Pièces ×2", onDouble, Modifier.fillMaxWidth(), Buttons.Gold,
                        trailing = { Spacer(Modifier.width(8.dp)); Pill("PUB", Color(0x33000000), Color(0xFF4A2C00)) })
                    Spacer(Modifier.height(10.dp))
                }
                GameButton("Rejouer", onRetry, Modifier.fillMaxWidth(), Buttons.Blue, Icons.Rounded.Refresh)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconBubble(Icons.Rounded.Home, onMenu, label = "Menu")
                    IconBubble(Icons.Rounded.Share, { c.shareScore(game.score, game.mode) }, label = "Partager")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = titleStyle(24.sp, shadow = false))
        Text(label, style = bodyStyle(12.sp, FontWeight.Bold, Palette.TextDim))
    }
}
