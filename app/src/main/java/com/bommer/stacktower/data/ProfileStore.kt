package com.bommer.stacktower.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "player")

/** Persistance du [Profile] (JSON dans DataStore, sauvegarde automatique Android incluse). */
class ProfileStore(private val context: Context) {

    private val key = stringPreferencesKey("profile_v2")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val profile: Flow<Profile> = context.dataStore.data.map { prefs -> decode(prefs[key]) }

    private fun decode(raw: String?): Profile = raw?.let {
        runCatching { json.decodeFromString(Profile.serializer(), it) }
            .onFailure { e -> Log.e("ProfileStore", "Profil illisible", e) }
            .getOrNull()
    } ?: Profile()

    /**
     * Modifie le profil de façon atomique. Si [transform] renvoie null, rien n'est écrit.
     * Retourne le profil résultant (ou null).
     */
    suspend fun update(transform: (Profile) -> Profile?): Profile? {
        var result: Profile? = null
        context.dataStore.edit { prefs ->
            val next = transform(decode(prefs[key]))
            if (next != null) {
                prefs[key] = json.encodeToString(Profile.serializer(), next)
                result = next
            }
        }
        return result
    }
}
