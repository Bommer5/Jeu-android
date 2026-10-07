package com.bommer.stacktower.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "player")

data class PlayerData(
    val best: Int = 0,
    val coins: Int = 0,
    val gamesPlayed: Int = 0,
    val ownedThemes: Set<String> = setOf("neon"),
    val selectedTheme: String = "neon",
    val adsRemoved: Boolean = false,
    val soundOn: Boolean = true,
    val vibrationOn: Boolean = true,
    val lastDailyDay: Long = -1,
    val dailyStreak: Int = 0,
)

class PlayerRepository(private val context: Context) {

    private object K {
        val BEST = intPreferencesKey("best")
        val COINS = intPreferencesKey("coins")
        val GAMES = intPreferencesKey("games")
        val OWNED = stringSetPreferencesKey("owned_themes")
        val SELECTED = stringPreferencesKey("selected_theme")
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val DAILY_DAY = longPreferencesKey("daily_day")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
    }

    val data: Flow<PlayerData> = context.dataStore.data.map { p ->
        PlayerData(
            best = p[K.BEST] ?: 0,
            coins = p[K.COINS] ?: 0,
            gamesPlayed = p[K.GAMES] ?: 0,
            ownedThemes = (p[K.OWNED] ?: emptySet()) + "neon",
            selectedTheme = p[K.SELECTED] ?: "neon",
            adsRemoved = p[K.ADS_REMOVED] ?: false,
            soundOn = p[K.SOUND] ?: true,
            vibrationOn = p[K.VIBRATION] ?: true,
            lastDailyDay = p[K.DAILY_DAY] ?: -1,
            dailyStreak = p[K.DAILY_STREAK] ?: 0,
        )
    }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    /** Enregistre un résultat. [newGame] vaut false après un « continuer » (même partie). */
    suspend fun recordGame(score: Int, coins: Int, newGame: Boolean) = update { p ->
        if (newGame) p[K.GAMES] = (p[K.GAMES] ?: 0) + 1
        if (score > (p[K.BEST] ?: 0)) p[K.BEST] = score
        p[K.COINS] = (p[K.COINS] ?: 0) + coins
    }

    suspend fun addCoins(amount: Int) = update { p -> p[K.COINS] = (p[K.COINS] ?: 0) + amount }

    /** Achète un thème si le joueur a assez de pièces. Retourne true en cas de succès. */
    suspend fun buyTheme(id: String, price: Int): Boolean {
        var ok = false
        update { p ->
            val coins = p[K.COINS] ?: 0
            val owned = p[K.OWNED] ?: emptySet()
            if (id in owned) {
                ok = true
            } else if (coins >= price) {
                p[K.COINS] = coins - price
                p[K.OWNED] = owned + id
                p[K.SELECTED] = id
                ok = true
            }
        }
        return ok
    }

    suspend fun selectTheme(id: String) = update { p -> p[K.SELECTED] = id }
    suspend fun setAdsRemoved(removed: Boolean) = update { p -> p[K.ADS_REMOVED] = removed }
    suspend fun setSound(on: Boolean) = update { p -> p[K.SOUND] = on }
    suspend fun setVibration(on: Boolean) = update { p -> p[K.VIBRATION] = on }

    /** Réclame le cadeau quotidien. Retourne le nombre de pièces gagnées (0 si déjà réclamé). */
    suspend fun claimDaily(today: Long): Int {
        var reward = 0
        update { p ->
            val last = p[K.DAILY_DAY] ?: -1
            if (last == today) return@update
            val streak = if (last == today - 1) (p[K.DAILY_STREAK] ?: 0) + 1 else 1
            reward = dailyReward(streak)
            p[K.DAILY_DAY] = today
            p[K.DAILY_STREAK] = streak
            p[K.COINS] = (p[K.COINS] ?: 0) + reward
        }
        return reward
    }

    companion object {
        fun dailyReward(streak: Int): Int = 20 + (minOf(streak, 7) - 1) * 10
    }
}
