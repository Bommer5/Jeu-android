package com.bommer.stacktower

import android.app.Application
import com.bommer.stacktower.data.ProfileStore
import com.bommer.stacktower.monetization.AdsManager
import com.bommer.stacktower.monetization.BillingManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class StackTowerApp : Application() {

    val scope = MainScope()
    lateinit var store: ProfileStore
        private set
    lateinit var ads: AdsManager
        private set
    lateinit var billing: BillingManager
        private set

    private val _purchases = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Messages à afficher après un achat réussi. */
    val purchases: SharedFlow<String> = _purchases

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        ads = AdsManager(this)
        billing = BillingManager(
            this,
            onRemoveAds = {
                scope.launch {
                    val wasRemoved = store.update { if (it.adsRemoved) null else it.copy(adsRemoved = true) } != null
                    if (wasRemoved) _purchases.tryEmit("Publicités supprimées. Merci !")
                }
            },
            onCoins = { amount ->
                scope.launch {
                    store.update { it.copy(coins = it.coins + amount) }
                    _purchases.tryEmit("+$amount pièces. Merci !")
                }
            },
        )
        billing.connect()
        scope.launch {
            store.profile.collect { ads.interstitialsEnabled = !it.adsRemoved }
        }
    }
}
