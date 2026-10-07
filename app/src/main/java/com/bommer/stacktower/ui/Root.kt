package com.bommer.stacktower.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.bommer.stacktower.R
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.game.BackgroundFx
import com.bommer.stacktower.game.BlockSkin
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.StackGame
import com.bommer.stacktower.game.Themes
import com.bommer.stacktower.ui.design.LocalFeedback
import com.bommer.stacktower.ui.screens.DailyRewardDialog
import com.bommer.stacktower.ui.screens.GameLayer
import com.bommer.stacktower.ui.screens.HomeScreen
import com.bommer.stacktower.ui.screens.NoticeHost
import com.bommer.stacktower.ui.screens.ProgressScreen
import com.bommer.stacktower.ui.screens.SettingsScreen
import com.bommer.stacktower.ui.screens.ShopScreen
import com.bommer.stacktower.ui.screens.ShopTab
import com.bommer.stacktower.ui.screens.WheelScreen
import kotlin.math.abs
import kotlin.random.Random

enum class Route { HOME, GAME, SHOP, PROGRESS, WHEEL, SETTINGS }

/** Tour qui se construit toute seule derrière le menu. */
private class DemoTower {
    private val random = Random(System.nanoTime())
    var game = fresh()
        private set
    private var aimError = 0f
    private var cooldown = 0.6f

    private fun fresh() = StackGame(GameMode.ZEN, random.nextLong()).also { it.start() }

    fun update(dt: Float) {
        game.update(dt)
        cooldown -= dt
        val m = game.mover ?: return
        val top = game.topBlock
        val delta = if (m.axis == com.bommer.stacktower.game.Axis.X) m.x - top.x else m.z - top.z
        if (cooldown <= 0f && abs(delta - aimError) < 0.12f) {
            game.tap()
            cooldown = 0.35f
            aimError = if (random.nextFloat() < 0.6f) 0f else (random.nextFloat() - 0.5f) * 1.2f
        }
        if (game.score > 45 || game.topBlock.w < 1f || game.topBlock.d < 1f) game = fresh()
    }
}

@Composable
fun StackTowerRoot(c: AppController) {
    val profile: Profile = c.profile.collectAsState().value ?: return
    val rewardedReady by c.ads.rewardedReady.collectAsState()
    val adsReady by c.ads.ready.collectAsState()
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.lilita_one) }

    var route by remember { mutableStateOf(Route.HOME) }
    var shopTab by remember { mutableStateOf(ShopTab.THEMES) }
    var progressTab by remember { mutableIntStateOf(0) }
    var showDaily by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(GameMode.byId(profile.lastMode)) }
    var boosters by remember { mutableStateOf(emptySet<Booster>()) }
    var game by remember { mutableStateOf<StackGame?>(null) }

    val demo = remember { DemoTower() }
    val renderer = remember { GameRenderer() }
    var frame by remember { mutableLongStateOf(0L) }

    // Boucle d'animation unique, synchronisée sur l'écran.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else (t - last) / 1_000_000_000f
                last = t
                val g = game
                if (g != null) g.update(dt) else demo.update(dt)
                frame = t
            }
        }
    }

    // Cadeau du jour proposé automatiquement à l'ouverture.
    LaunchedEffect(Unit) {
        if (c.current.dailyRewardDay != c.today() && c.current.gamesPlayed > 0) showDaily = true
    }

    fun startGame(m: GameMode, b: Set<Booster>) {
        game = c.newGame(m, b)
        boosters = emptySet()
        route = Route.GAME
    }

    fun openShop(tab: ShopTab) {
        shopTab = tab
        route = Route.SHOP
    }

    BackHandler(enabled = route != Route.HOME && route != Route.GAME) { route = Route.HOME }

    val options = RenderOptions(
        theme = Themes.byId(profile.theme),
        skin = BlockSkin.byId(profile.skin),
        fx = BackgroundFx.byId(profile.fx),
        particles = profile.settings.particles,
        screenShake = profile.settings.screenShake,
        bestRow = game?.let { if (it.mode == GameMode.ZEN) 0 else profile.bestFor(it.mode) } ?: 0,
        typeface = typeface,
    )

    CompositionLocalProvider(LocalFeedback provides c) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                frame // redessine à chaque image
                with(renderer) { render(game ?: demo.game, options) }
            }

            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "route",
            ) { r ->
                when (r) {
                    Route.HOME -> HomeScreen(
                        c = c,
                        profile = profile,
                        mode = mode,
                        onModeChange = { mode = it },
                        boosters = boosters,
                        onBoostersChange = { boosters = it },
                        onPlay = { startGame(mode, boosters) },
                        onShop = ::openShop,
                        onProgress = { progressTab = it; route = Route.PROGRESS },
                        onWheel = { route = Route.WHEEL },
                        onSettings = { route = Route.SETTINGS },
                        onDaily = { showDaily = true },
                    )

                    Route.GAME -> game?.let { g ->
                        GameLayer(
                            c = c,
                            game = g,
                            profile = profile,
                            rewardedReady = rewardedReady,
                            onRestart = { startGame(g.mode, emptySet()) },
                            onExit = {
                                game = null
                                route = Route.HOME
                            },
                        )
                    }

                    Route.SHOP -> ShopScreen(c, profile, shopTab, { shopTab = it }, rewardedReady, renderer) { route = Route.HOME }
                    Route.PROGRESS -> ProgressScreen(c, profile, progressTab, { progressTab = it }) { route = Route.HOME }
                    Route.WHEEL -> WheelScreen(c, profile, rewardedReady) { route = Route.HOME }
                    Route.SETTINGS -> SettingsScreen(c, profile) { route = Route.HOME }
                }
            }

            if (showDaily) DailyRewardDialog(c, profile, rewardedReady) { showDaily = false }

            NoticeHost(c)

            // Bannière : jamais pendant la partie pour ne pas gêner le joueur.
            if (adsReady && !profile.adsRemoved && route != Route.GAME) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) { BannerAd() }
            }
        }
    }
}
