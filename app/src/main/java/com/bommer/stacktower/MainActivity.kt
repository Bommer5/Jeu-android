package com.bommer.stacktower

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.bommer.stacktower.game.SoundFx
import com.bommer.stacktower.monetization.ConsentManager
import com.bommer.stacktower.ui.AppActions
import com.bommer.stacktower.ui.StackTowerRoot
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var sound: SoundFx
    private lateinit var consent: ConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as StackTowerApp
        val repo = app.repository

        sound = SoundFx(this)
        consent = ConsentManager(this)
        consent.gather(this) { app.ads.initialize() }
        app.billing.connect()

        val actions = object : AppActions {
            override fun recordGame(score: Int, coins: Int, newGame: Boolean) {
                app.scope.launch { repo.recordGame(score, coins, newGame) }
            }

            override fun addCoins(amount: Int) {
                app.scope.launch { repo.addCoins(amount) }
            }

            override fun afterGame(onDone: () -> Unit) = app.ads.onGameFinished(this@MainActivity, onDone)

            override fun showRewarded(onReward: () -> Unit): Boolean =
                app.ads.showRewarded(this@MainActivity, onReward)

            override fun buyTheme(id: String, price: Int, onResult: (Boolean) -> Unit) {
                lifecycleScope.launch { onResult(repo.buyTheme(id, price)) }
            }

            override fun selectTheme(id: String) {
                lifecycleScope.launch { repo.selectTheme(id) }
            }

            override fun buyProduct(productId: String): Boolean = app.billing.buy(this@MainActivity, productId)

            override fun setSound(on: Boolean) {
                lifecycleScope.launch { repo.setSound(on) }
            }

            override fun setVibration(on: Boolean) {
                lifecycleScope.launch { repo.setVibration(on) }
            }

            override fun claimDaily(onResult: (Int) -> Unit) {
                lifecycleScope.launch { onResult(repo.claimDaily(LocalDate.now().toEpochDay())) }
            }

            override val privacyOptionsRequired: Boolean get() = consent.privacyOptionsRequired

            override fun showPrivacyOptions() = consent.showPrivacyOptions(this@MainActivity)
        }

        setContent {
            StackTowerRoot(
                repository = repo,
                ads = app.ads,
                billing = app.billing,
                sound = sound,
                actions = actions,
            )
        }
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
