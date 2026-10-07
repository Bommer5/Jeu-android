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
import com.bommer.stacktower.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Achats intégrés Google Play :
 *  - [BuildConfig.IAP_REMOVE_ADS] : non consommable, supprime les interstitiels et bannières ;
 *  - [BuildConfig.IAP_COIN_PACK] : consommable, ajoute [COIN_PACK_AMOUNT] pièces.
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
        val ids = listOf(BuildConfig.IAP_REMOVE_ADS, BuildConfig.IAP_COIN_PACK)
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
        if (BuildConfig.IAP_REMOVE_ADS in products) {
            onRemoveAds()
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { }
            }
        }
        if (BuildConfig.IAP_COIN_PACK in products) {
            // On crédite seulement après une consommation réussie, pour éviter tout double crédit.
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r, _ ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) onCoins(COIN_PACK_AMOUNT * purchase.quantity)
            }
        }
    }

    fun release() = client.endConnection()

    companion object {
        private const val TAG = "BillingManager"
        const val COIN_PACK_AMOUNT = 1000
    }
}
