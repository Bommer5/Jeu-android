package com.bommer.stacktower

import android.app.Application
import com.bommer.stacktower.data.PlayerRepository
import com.bommer.stacktower.monetization.AdsManager
import com.bommer.stacktower.monetization.BillingManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StackTowerApp : Application() {

    val scope = MainScope()
    lateinit var repository: PlayerRepository
        private set
    lateinit var ads: AdsManager
        private set
    lateinit var billing: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = PlayerRepository(this)
        ads = AdsManager(this)
        billing = BillingManager(
            this,
            onRemoveAds = { scope.launch { repository.setAdsRemoved(true) } },
            onCoins = { amount -> scope.launch { repository.addCoins(amount) } },
        )
        billing.connect()
        scope.launch {
            repository.data.collectLatest { ads.interstitialsEnabled = !it.adsRemoved }
        }
    }
}
