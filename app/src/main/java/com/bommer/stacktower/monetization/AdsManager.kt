package com.bommer.stacktower.monetization

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.bommer.stacktower.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestion AdMob : interstitiels (fréquence plafonnée) et vidéos récompensées.
 * Les pubs ne sont demandées qu'une fois le consentement RGPD obtenu (voir [ConsentManager]).
 */
class AdsManager(private val context: Context) {

    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var lastInterstitialAt = 0L
    private var gamesSinceInterstitial = 0

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    private val _ready = MutableStateFlow(false)
    /** Vrai quand le SDK est initialisé et que les bannières peuvent être affichées. */
    val ready: StateFlow<Boolean> = _ready

    /** Désactivé quand le joueur a acheté « Supprimer les pubs » (sauf vidéos volontaires). */
    var interstitialsEnabled = true

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        // Initialisation hors du thread principal (recommandé par Google pour éviter les ANR).
        Thread {
            MobileAds.initialize(context) {
                mainHandler.post {
                    _ready.value = true
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }.start()
    }

    private fun loadInterstitial() {
        if (!_ready.value || interstitial != null || loadingInterstitial || !interstitialsEnabled) return
        loadingInterstitial = true
        InterstitialAd.load(context, BuildConfig.AD_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitiel non chargé : ${error.message}")
                    loadingInterstitial = false
                }
            })
    }

    private fun loadRewarded() {
        if (!_ready.value || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(context, BuildConfig.AD_REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    loadingRewarded = false
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Vidéo récompensée non chargée : ${error.message}")
                    loadingRewarded = false
                }
            })
    }

    /**
     * Appelé à chaque fin de partie. Affiche un interstitiel au plus toutes les
     * [GAMES_BETWEEN_INTERSTITIALS] parties et [MIN_INTERVAL_MS] ms, pour ne pas faire fuir les joueurs.
     */
    fun onGameFinished(activity: Activity, onDone: () -> Unit) {
        gamesSinceInterstitial++
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val due = gamesSinceInterstitial >= GAMES_BETWEEN_INTERSTITIALS &&
            (lastInterstitialAt == 0L || now - lastInterstitialAt >= MIN_INTERVAL_MS)
        if (!interstitialsEnabled || ad == null || !due) {
            loadInterstitial()
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                loadInterstitial()
                onDone()
            }
        }
        gamesSinceInterstitial = 0
        lastInterstitialAt = now
        ad.show(activity)
    }

    /** Affiche une vidéo récompensée ; [onReward] n'est appelé que si la vidéo a été regardée. */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit = {}): Boolean {
        val ad = rewarded ?: run { loadRewarded(); return false }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                _rewardedReady.value = false
                loadRewarded()
                if (earned) onReward()
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                _rewardedReady.value = false
                loadRewarded()
                onClosed()
            }
        }
        ad.show(activity) { earned = true }
        return true
    }

    companion object {
        private const val TAG = "AdsManager"
        const val GAMES_BETWEEN_INTERSTITIALS = 3
        const val MIN_INTERVAL_MS = 90_000L
    }
}
