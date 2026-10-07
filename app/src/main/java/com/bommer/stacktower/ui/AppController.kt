package com.bommer.stacktower.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import com.bommer.stacktower.StackTowerApp
import com.bommer.stacktower.data.Profile
import com.bommer.stacktower.data.Progression
import com.bommer.stacktower.data.RunOutcome
import com.bommer.stacktower.data.RunResult
import com.bommer.stacktower.game.Booster
import com.bommer.stacktower.game.GameMode
import com.bommer.stacktower.game.SoundFx
import com.bommer.stacktower.game.StackGame
import com.bommer.stacktower.monetization.ConsentManager
import com.bommer.stacktower.ui.design.Feedback
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Notification éphémère affichée en haut de l'écran. */
data class Notice(val id: Long, val kind: Kind, val title: String, val subtitle: String = "") {
    enum class Kind { INFO, COINS, ACHIEVEMENT, LEVEL, ERROR }
}

/**
 * Point d'entrée unique de l'interface vers les données, la pub, les achats et le son.
 * Vit aussi longtemps que l'activité.
 */
class AppController(
    private val activity: ComponentActivity,
    private val scope: CoroutineScope,
    val sound: SoundFx,
    val consent: ConsentManager,
) : Feedback {

    private val app = activity.application as StackTowerApp
    val ads = app.ads
    val billing = app.billing
    private val store = app.store

    /** null tant que le profil n'est pas chargé (l'écran de démarrage reste affiché). */
    val profile: StateFlow<Profile?> = store.profile
        .map<Profile, Profile?> { it }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val current: Profile get() = profile.value ?: Profile()

    val notices = mutableStateListOf<Notice>()
    private var noticeId = 0L

    init {
        scope.launch {
            profile.collect { p ->
                if (p != null) {
                    sound.enabled = p.settings.sound
                    sound.musicEnabled = p.settings.music
                }
            }
        }
        scope.launch { app.purchases.collect { notify(Notice.Kind.COINS, it) } }
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    fun notify(kind: Notice.Kind, title: String, subtitle: String = "") {
        notices += Notice(++noticeId, kind, title, subtitle)
    }

    fun dismiss(n: Notice) {
        notices.remove(n)
    }

    /** Modifie le profil ; [onDone] reçoit le nouveau profil, ou null si la transformation a été refusée. */
    fun update(transform: (Profile) -> Profile?, onDone: (Profile?) -> Unit = {}) {
        scope.launch { onDone(store.update(transform)) }
    }

    // --- Retour sensoriel ----------------------------------------------------------------------

    override fun click() {
        sound.click()
        vibrate(8)
    }

    fun vibrate(ms: Long, strong: Boolean = false) {
        if (!current.settings.vibration) return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            activity.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Vibrator::class.java)
        }
        val amp = if (strong) 255 else 90
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, amp)) }
    }

    // --- Parties --------------------------------------------------------------------------------

    /** Crée une partie en consommant les bonus choisis (s'ils sont en stock). */
    fun newGame(mode: GameMode, wanted: Set<Booster>): StackGame {
        val p = current
        val used = wanted.filter { p.boosterCount(it) > 0 }.toSet()
        if (used.isNotEmpty() || p.lastMode != mode.id) {
            update({ Progression.consumeBoosters(it, used).copy(lastMode = mode.id) })
        }
        val seed = if (mode == GameMode.DAILY) today() * 1_000_003L else System.nanoTime()
        return StackGame(mode, seed, used).also { it.start() }
    }

    /** Enregistre le résultat d'une partie et affiche succès / niveaux gagnés. */
    fun finishRun(game: StackGame, bonusCoins: Int, onOutcome: (RunOutcome) -> Unit = {}) {
        val result = RunResult(
            mode = game.mode,
            score = game.score,
            perfects = game.perfects,
            bestCombo = game.bestCombo,
            coins = game.coinsEarned,
            xp = game.xpEarned,
            playTimeSec = game.playTime.toInt(),
            day = today(),
        )
        var outcome: RunOutcome? = null
        update({ p -> Progression.applyRun(p, result, bonusCoins).also { outcome = it }.profile }) {
            val o = outcome ?: return@update
            if (o.levelsGained > 0) {
                sound.levelUp()
                notify(Notice.Kind.LEVEL, "Niveau ${o.profile.level} !", "+${o.levelCoins} pièces")
            }
            o.achievements.forEach { a -> notify(Notice.Kind.ACHIEVEMENT, a.title, "Succès débloqué · +${a.reward} pièces") }
            maybeAskReview(o)
            onOutcome(o)
        }
    }

    // --- Publicité ------------------------------------------------------------------------------

    fun showRewarded(onReward: () -> Unit) {
        val shown = ads.showRewarded(activity, onReward = onReward, onClosed = { sound.foreground = true })
        if (shown) sound.foreground = false
        else notify(Notice.Kind.ERROR, "Vidéo indisponible", "Réessaie dans un instant")
    }

    fun afterGame(onDone: () -> Unit) = ads.onGameFinished(activity, onDone)

    fun showPrivacyOptions() = consent.showPrivacyOptions(activity)

    // --- Achats ---------------------------------------------------------------------------------

    fun buy(productId: String) {
        if (!billing.buy(activity, productId)) {
            notify(Notice.Kind.ERROR, "Boutique indisponible", "Vérifie ta connexion Google Play")
        }
    }

    fun restorePurchases() {
        billing.restore()
        notify(Notice.Kind.INFO, "Achats restaurés")
    }

    // --- Partage, avis --------------------------------------------------------------------------

    private val storeUrl get() = "https://play.google.com/store/apps/details?id=${activity.packageName}"

    fun shareScore(score: Int, mode: GameMode) {
        val text = "J'ai empilé $score blocs en mode ${mode.title} sur Stack Tower ! Tu peux faire mieux ? $storeUrl"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(intent, "Partager"))
    }

    fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Viens jouer à Stack Tower avec moi ! $storeUrl")
        activity.startActivity(Intent.createChooser(intent, "Partager"))
    }

    fun rateApp() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
        try {
            activity.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)))
        }
    }

    fun openUrl(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** Demande un avis Play Store une seule fois, après un beau record (moment de satisfaction). */
    private fun maybeAskReview(o: RunOutcome) {
        val p = o.profile
        if (p.reviewAsked || !o.newBest || p.best < 20 || p.gamesPlayed < 8) return
        update({ it.copy(reviewAsked = true) })
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
        }
    }

    companion object {
        /** À remplacer par l'URL publique de docs/privacy-policy.md. */
        const val PRIVACY_URL = "https://github.com/Bommer5/Jeu-android/blob/main/docs/privacy-policy.md"
    }
}
