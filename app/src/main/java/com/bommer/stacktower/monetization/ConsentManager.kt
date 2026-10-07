package com.bommer.stacktower.monetization

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Consentement RGPD via Google UMP (obligatoire pour diffuser des pubs AdMob en Europe).
 */
class ConsentManager(activity: Activity) {

    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

    val canRequestAds: Boolean get() = info.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Demande/rafraîchit le consentement puis appelle [onReady] si les pubs peuvent être chargées. */
    fun gather(activity: Activity, onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) Log.w(TAG, "Formulaire de consentement : ${error.message}")
                if (info.canRequestAds()) onReady()
            }
        }, { error ->
            Log.w(TAG, "Mise à jour du consentement : ${error.message}")
            if (info.canRequestAds()) onReady()
        })
        // Consentement déjà donné lors d'une session précédente : on n'attend pas.
        if (info.canRequestAds()) onReady()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Options de confidentialité : ${error.message}")
        }
    }

    private companion object {
        const val TAG = "ConsentManager"
    }
}
