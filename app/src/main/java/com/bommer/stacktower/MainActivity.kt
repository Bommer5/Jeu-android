package com.bommer.stacktower

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.bommer.stacktower.game.SoundFx
import com.bommer.stacktower.monetization.ConsentManager
import com.bommer.stacktower.ui.AppController
import com.bommer.stacktower.ui.StackTowerRoot

class MainActivity : ComponentActivity() {

    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as StackTowerApp

        val consent = ConsentManager(this)
        consent.gather(this) { app.ads.initialize() }
        app.billing.connect()

        controller = AppController(this, lifecycleScope, SoundFx(this), consent)
        // L'écran de démarrage reste affiché jusqu'au chargement de la sauvegarde.
        splash.setKeepOnScreenCondition { controller.profile.value == null }

        setContent { StackTowerRoot(controller) }
    }

    override fun onResume() {
        super.onResume()
        controller.sound.foreground = true
    }

    override fun onPause() {
        controller.sound.foreground = false
        super.onPause()
    }

    override fun onDestroy() {
        controller.sound.release()
        super.onDestroy()
    }
}
