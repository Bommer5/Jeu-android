package com.bommer.stacktower.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Achats intégrés Google Play :
 *  - [REMOVE_ADS] : non consommable, supprime les interstitiels et bannières ;
 *  - [COIN_PACKS] : consommables, ajoutent des pièces.
 * Les identifiants doivent être créés à l'identique dans la Play Console.
 */
class BillingManager(
    context: Context,
    private val onRemoveAds: () -> Unit,
    private val onCoins: (Int) -> Unit,
) : PurchasesUpdatedListener {

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Billing indisponible : ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Une nouvelle tentative aura lieu au prochain connect().
            }
        })
    }

    private fun queryProducts() {
        val ids = listOf(REMOVE_ADS) + COIN_PACKS.map { it.productId }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = details.productDetailsList.associateBy { it.productId }
            }
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach(::handle)
        }
    }

    fun priceOf(productId: String): String? =
        _products.value[productId]?.oneTimePurchaseOfferDetails?.formattedPrice

    /** Lance l'achat. Retourne false si le produit n'est pas (encore) disponible. */
    fun buy(activity: Activity, productId: String): Boolean {
        val details = _products.value[productId] ?: run { connect(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle)
    }

    private fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val products = purchase.products
        if (REMOVE_ADS in products) {
            onRemoveAds()
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { }
            }
        }
        val coins = COIN_PACKS.filter { it.productId in products }.sumOf { it.coins }
        if (coins > 0) {
            // On crédite seulement après une consommation réussie, pour éviter tout double crédit.
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r, _ ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) onCoins(coins * purchase.quantity)
            }
        }
    }

    /** Re-synchronise les achats (bouton « Restaurer les achats »). */
    fun restore() {
        if (client.isReady) restorePurchases() else connect()
    }

    fun release() = client.endConnection()

    data class CoinPack(val productId: String, val coins: Int, val label: String, val bestValue: Boolean = false)

    companion object {
        private const val TAG = "BillingManager"
        const val REMOVE_ADS = "remove_ads"
        val COIN_PACKS = listOf(
            CoinPack("coins_500", 500, "Poignée de pièces"),
            CoinPack("coins_1500", 1500, "Sac de pièces"),
            CoinPack("coins_5000", 5000, "Coffre au trésor", bestValue = true),
        )
    }
}
