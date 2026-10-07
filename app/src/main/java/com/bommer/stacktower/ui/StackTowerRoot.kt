package com.bommer.stacktower.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bommer.stacktower.BuildConfig
import com.bommer.stacktower.data.PlayerData
import com.bommer.stacktower.data.PlayerRepository
import com.bommer.stacktower.game.SoundFx
import com.bommer.stacktower.game.StackGame
import com.bommer.stacktower.game.Themes
import com.bommer.stacktower.monetization.AdsManager
import com.bommer.stacktower.monetization.BillingManager
import com.bommer.stacktower.ui.GameRenderer.drawGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Actions déléguées à l'activité (pubs, achats, persistance). */
interface AppActions {
    fun recordGame(score: Int, coins: Int, newGame: Boolean)
    fun addCoins(amount: Int)
    fun afterGame(onDone: () -> Unit)
    fun showRewarded(onReward: () -> Unit): Boolean
    fun buyTheme(id: String, price: Int, onResult: (Boolean) -> Unit)
    fun selectTheme(id: String)
    fun buyProduct(productId: String): Boolean
    fun setSound(on: Boolean)
    fun setVibration(on: Boolean)
    fun claimDaily(onResult: (Int) -> Unit)
    val privacyOptionsRequired: Boolean
    fun showPrivacyOptions()
}

enum class Screen { MENU, PLAYING, GAME_OVER, SHOP }

@Composable
fun StackTowerRoot(
    repository: PlayerRepository,
    ads: AdsManager,
    billing: BillingManager,
    sound: SoundFx,
    actions: AppActions,
) {
    val player by repository.data.collectAsState(initial = PlayerData())
    val adsReady by ads.ready.collectAsState()
    val rewardedReady by ads.rewardedReady.collectAsState()
    val products by billing.products.collectAsState()
    val theme = Themes.byId(player.selectedTheme)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val game = remember { StackGame() }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var frame by remember { mutableLongStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var bestAtStart by remember { mutableIntStateOf(0) }
    var coinsRecorded by remember { mutableIntStateOf(0) }
    var doubled by remember { mutableStateOf(false) }
    var popupText by remember { mutableStateOf("") }
    var popupKey by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    sound.enabled = player.soundOn

    fun haptic(kind: Int) {
        if (player.vibrationOn) view.performHapticFeedback(kind)
    }

    fun startRun() {
        game.reset()
        game.start()
        score = 0
        coinsRecorded = 0
        doubled = false
        bestAtStart = player.best
        screen = Screen.PLAYING
    }

    fun onGameOver() {
        val earned = game.coinsEarned
        actions.recordGame(game.score, earned - coinsRecorded, newGame = !game.continued)
        coinsRecorded = earned
        scope.launch {
            delay(700)
            screen = Screen.GAME_OVER
        }
    }

    fun handleTap() {
        when (screen) {
            Screen.MENU -> startRun()
            Screen.PLAYING -> when (game.tap()) {
                StackGame.Placement.PERFECT -> {
                    sound.perfect(game.combo)
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    popupText = if (game.combo > 1) "PARFAIT ×${game.combo}" else "PARFAIT"
                    popupKey++
                    score = game.score
                }
                StackGame.Placement.CUT -> {
                    sound.place()
                    haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    score = game.score
                }
                StackGame.Placement.MISS -> {
                    sound.fail()
                    haptic(HapticFeedbackConstants.LONG_PRESS)
                    onGameOver()
                }
                null -> Unit
            }
            else -> Unit
        }
    }

    val onTap by rememberUpdatedState { handleTap() }

    // Boucle de jeu synchronisée sur l'affichage.
    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) game.update((t - last) / 1_000_000_000f)
                last = t
                frame = t
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(theme.bgBottom))
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { onTap() })
                }
        ) {
            frame // lecture de l'état : redessine à chaque frame
            drawGame(game, theme)
        }

        val onLight = theme.id == "candy" || theme.id == "mono"
        val textColor = if (onLight) Color(0xFF222222) else Color.White

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (screen) {
                Screen.MENU -> MenuOverlay(
                    player = player,
                    textColor = textColor,
                    onShop = { screen = Screen.SHOP },
                    onSound = { actions.setSound(!player.soundOn) },
                    onVibration = { actions.setVibration(!player.vibrationOn) },
                    showPrivacy = actions.privacyOptionsRequired,
                    onPrivacy = actions::showPrivacyOptions,
                    onDaily = {
                        actions.claimDaily { reward ->
                            if (reward > 0) {
                                sound.coin()
                                toast = "Cadeau du jour : +$reward pièces !"
                            }
                        }
                    },
                )

                Screen.PLAYING -> PlayingOverlay(
                    score = score,
                    textColor = textColor,
                    popupText = popupText,
                    popupKey = popupKey,
                )

                Screen.GAME_OVER -> GameOverOverlay(
                    score = game.score,
                    best = maxOf(player.best, game.score),
                    newBest = game.score > bestAtStart && bestAtStart > 0,
                    coins = game.coinsEarned,
                    doubled = doubled,
                    canContinue = game.canContinue && rewardedReady,
                    canDouble = !doubled && rewardedReady && game.coinsEarned > 0,
                    onContinue = {
                        actions.showRewarded {
                            if (game.continueRun()) screen = Screen.PLAYING
                        }
                    },
                    onDouble = {
                        actions.showRewarded {
                            actions.addCoins(game.coinsEarned)
                            doubled = true
                            sound.coin()
                        }
                    },
                    onRetry = { actions.afterGame { startRun() } },
                    onMenu = {
                        actions.afterGame {
                            game.reset()
                            score = 0
                            screen = Screen.MENU
                        }
                    },
                )

                Screen.SHOP -> ShopOverlay(
                    player = player,
                    rewardedReady = rewardedReady,
                    removeAdsPrice = products[BuildConfig.IAP_REMOVE_ADS]?.oneTimePurchaseOfferDetails?.formattedPrice,
                    coinPackPrice = products[BuildConfig.IAP_COIN_PACK]?.oneTimePurchaseOfferDetails?.formattedPrice,
                    onBuyTheme = { t ->
                        actions.buyTheme(t.id, t.price) { ok ->
                            if (ok) sound.coin() else toast = "Pas assez de pièces"
                        }
                    },
                    onSelectTheme = { actions.selectTheme(it.id) },
                    onFreeCoins = {
                        actions.showRewarded {
                            actions.addCoins(FREE_COINS_REWARD)
                            sound.coin()
                            toast = "+$FREE_COINS_REWARD pièces !"
                        }
                    },
                    onBuyCoins = {
                        if (!actions.buyProduct(BuildConfig.IAP_COIN_PACK)) toast = "Boutique Google Play indisponible"
                    },
                    onRemoveAds = {
                        if (!actions.buyProduct(BuildConfig.IAP_REMOVE_ADS)) toast = "Boutique Google Play indisponible"
                    },
                    onBack = { screen = Screen.MENU },
                )
            }

            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            ) {
                Text(
                    toast ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        // Bannière en bas des menus (jamais pendant la partie pour ne pas gêner le joueur).
        if (adsReady && !player.adsRemoved && screen != Screen.PLAYING) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                BannerAd()
            }
        }
    }
}

const val FREE_COINS_REWARD = 50

@Composable
private fun MenuOverlay(
    player: PlayerData,
    textColor: Color,
    onShop: () -> Unit,
    onSound: () -> Unit,
    onVibration: () -> Unit,
    showPrivacy: Boolean,
    onPrivacy: () -> Unit,
    onDaily: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val dailyAvailable = player.lastDailyDay != LocalDate.now().toEpochDay()

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Record : ${player.best}", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            CoinChip(player.coins)
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "STACK\nTOWER",
                color = textColor,
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Touchez pour jouer",
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(pulse),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dailyAvailable) {
                PillButton("🎁  Cadeau du jour", Color(0xFFFFC107), Color(0xFF3A2A00), onDaily)
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundButton("🛒", onShop)
                RoundButton(if (player.soundOn) "🔊" else "🔇", onSound)
                RoundButton(if (player.vibrationOn) "📳" else "📴", onVibration)
                if (showPrivacy) RoundButton("🔒", onPrivacy)
            }
        }
    }
}

@Composable
private fun PlayingOverlay(score: Int, textColor: Color, popupText: String, popupKey: Int) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(popupKey) {
        if (popupKey > 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(800))
        }
    }
    Box(Modifier.fillMaxSize()) {
        Text(
            "$score",
            color = textColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp),
        )
        if (anim.value < 1f) {
            Text(
                popupText,
                color = Color(0xFFFFE082),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .graphicsLayer {
                        alpha = 1f - anim.value
                        translationY = -60f * anim.value
                        val s = 1f + 0.3f * (1f - anim.value)
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    best: Int,
    newBest: Boolean,
    coins: Int,
    doubled: Boolean,
    canContinue: Boolean,
    canDouble: Boolean,
    onContinue: () -> Unit,
    onDouble: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    val appear = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(250)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .scale(appear.value)
                .padding(24.dp)
                .background(Color(0xE6101225), RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (newBest) "NOUVEAU RECORD !" else "PARTIE TERMINÉE", color = if (newBest) Color(0xFFFFD54F) else Color.White,
                fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("$score", color = Color.White, fontWeight = FontWeight.Black, fontSize = 80.sp)
            Text("Record : $best", color = Color(0xFFB0B4D8), fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Coin(18)
                Spacer(Modifier.size(6.dp))
                Text(
                    "+${if (doubled) coins * 2 else coins}",
                    color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (canContinue) {
                PillButton("▶  Continuer (vidéo)", Color(0xFF00C853), Color.White, onContinue)
                Spacer(Modifier.height(10.dp))
            }
            if (canDouble) {
                PillButton("▶  Pièces ×2 (vidéo)", Color(0xFFFFC107), Color(0xFF3A2A00), onDouble)
                Spacer(Modifier.height(10.dp))
            }
            PillButton("Rejouer", Color.White, Color(0xFF1B1D3A), onRetry)
            Spacer(Modifier.height(10.dp))
            Text(
                "Menu",
                color = Color(0xFFB0B4D8),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures { onMenu() } }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
fun PillButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun RoundButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .background(Color(0x33FFFFFF), CircleShape)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp)
    }
}

@Composable
fun Coin(sizeDp: Int) {
    Canvas(Modifier.size(sizeDp.dp)) {
        drawCircle(Color(0xFFFFB300))
        drawCircle(Color(0xFFFFD54F), radius = size.minDimension * 0.36f)
    }
}

@Composable
fun CoinChip(coins: Int) {
    Row(
        Modifier
            .background(Color(0x55000000), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Coin(18)
        Spacer(Modifier.size(6.dp))
        Text("$coins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
